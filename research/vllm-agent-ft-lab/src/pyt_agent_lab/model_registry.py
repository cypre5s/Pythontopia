from __future__ import annotations

import json
from dataclasses import dataclass
from enum import Enum
from pathlib import Path
from typing import Any


class ModelState(str, Enum):
    TRAINED = "trained"
    GOLDEN_PASSED = "golden_passed"
    STRATIFIED_PASSED = "stratified_passed"
    FULL_EVAL_PASSED = "full_eval_passed"
    BLIND_REVIEW_PASSED = "blind_review_passed"
    CHAMPION = "champion"
    REJECTED = "rejected"
    RETIRED = "retired"


_ALLOWED_TRANSITIONS = {
    ModelState.TRAINED: {ModelState.GOLDEN_PASSED, ModelState.REJECTED},
    ModelState.GOLDEN_PASSED: {ModelState.STRATIFIED_PASSED, ModelState.REJECTED},
    ModelState.STRATIFIED_PASSED: {ModelState.FULL_EVAL_PASSED, ModelState.REJECTED},
    ModelState.FULL_EVAL_PASSED: {ModelState.BLIND_REVIEW_PASSED, ModelState.REJECTED},
    ModelState.BLIND_REVIEW_PASSED: {ModelState.CHAMPION, ModelState.REJECTED},
    ModelState.CHAMPION: {ModelState.RETIRED},
    ModelState.REJECTED: set(),
    ModelState.RETIRED: set(),
}


@dataclass(frozen=True)
class RegistryEntry:
    model_id: str
    adapter_path: str
    state: ModelState
    schema_version: str
    evidence: list[str]

    @classmethod
    def from_dict(cls, value: dict[str, Any]) -> "RegistryEntry":
        return cls(
            model_id=str(value["model_id"]),
            adapter_path=str(value["adapter_path"]),
            state=ModelState(value["state"]),
            schema_version=str(value["schema_version"]),
            evidence=[str(item) for item in value.get("evidence", [])],
        )

    def to_dict(self) -> dict[str, Any]:
        return {
            "model_id": self.model_id,
            "adapter_path": self.adapter_path,
            "state": self.state.value,
            "schema_version": self.schema_version,
            "evidence": self.evidence,
        }


class ModelRegistry:
    def __init__(self, entries: list[RegistryEntry] | None = None) -> None:
        entries = entries or []
        self._entries = {entry.model_id: entry for entry in entries}
        if len(self._entries) != len(entries):
            raise ValueError("duplicate model_id in registry")
        if sum(entry.state == ModelState.CHAMPION for entry in entries) > 1:
            raise ValueError("registry may contain only one champion")

    @classmethod
    def load(cls, path: str | Path) -> "ModelRegistry":
        payload = json.loads(Path(path).read_text(encoding="utf-8"))
        return cls([RegistryEntry.from_dict(item) for item in payload["models"]])

    def transition(
        self, model_id: str, state: ModelState, *, evidence: str
    ) -> RegistryEntry:
        current = self._entries[model_id]
        if state not in _ALLOWED_TRANSITIONS[current.state]:
            raise ValueError(f"invalid model transition: {current.state} -> {state}")
        if state == ModelState.CHAMPION:
            champion = next(
                (item for item in self._entries.values() if item.state == ModelState.CHAMPION),
                None,
            )
            if champion is not None:
                raise ValueError(f"retire current champion first: {champion.model_id}")
        updated = RegistryEntry(
            model_id=current.model_id,
            adapter_path=current.adapter_path,
            state=state,
            schema_version=current.schema_version,
            evidence=[*current.evidence, evidence],
        )
        self._entries[model_id] = updated
        return updated

    def dump(self, path: str | Path) -> None:
        target = Path(path)
        target.parent.mkdir(parents=True, exist_ok=True)
        payload = {
            "registry_version": 1,
            "models": [
                entry.to_dict() for entry in sorted(self._entries.values(), key=lambda x: x.model_id)
            ],
        }
        target.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
