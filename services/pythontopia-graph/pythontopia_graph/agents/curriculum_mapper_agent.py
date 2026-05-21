from __future__ import annotations

import hashlib
import json
from typing import Any

from pythontopia_graph.schemas.curriculum import (
    MapperInput,
    MapperOutput,
    PlanStep,
    TileAssignment,
)


class CurriculumMapperAgent:
    """Deterministic KC -> field tile mapper.

    This agent **does not** call the LLM. It exists as a LangGraph-style single
    node so that future iterations may layer optional LLM optimization on top
    while keeping the deterministic baseline reproducible.
    """

    DEFAULT_UNLOCK_COUNT = 8

    def map(self, input_data: MapperInput) -> MapperOutput:
        sorted_kcs = sorted(
            input_data.kcs,
            key=lambda kc: (kc.chapter_order, kc.difficulty, kc.kc_code),
        )

        unlock_count = self._unlock_count(input_data, len(sorted_kcs))
        plan_steps: list[PlanStep] = []
        tile_assignments: list[TileAssignment] = []
        unlocked: list[str] = []
        for index, kc in enumerate(sorted_kcs):
            row = index // input_data.row_width
            column = index % input_data.row_width
            tile_x = input_data.field_origin_x + column
            tile_y = input_data.field_origin_y + row
            plan_steps.append(
                PlanStep(
                    order=index + 1,
                    kc_code=kc.kc_code,
                    chapter_order=kc.chapter_order,
                    rationale=self._rationale(kc, index, input_data),
                )
            )
            tile_assignments.append(
                TileAssignment(
                    kc_code=kc.kc_code,
                    tile_x=tile_x,
                    tile_y=tile_y,
                    chapter_id=kc.chapter_id,
                )
            )
            if index < unlock_count:
                unlocked.append(kc.kc_code)

        signature_payload: dict[str, Any] = {
            "user_id": input_data.user_id,
            "row_width": input_data.row_width,
            "field_origin_x": input_data.field_origin_x,
            "field_origin_y": input_data.field_origin_y,
            "unlock_count": unlock_count,
            "tile_assignments": [t.model_dump() for t in tile_assignments],
            "plan_steps": [p.model_dump() for p in plan_steps],
            "unlocked_kcs": unlocked,
        }
        signature = hashlib.sha256(
            json.dumps(signature_payload, sort_keys=True, ensure_ascii=False).encode("utf-8")
        ).hexdigest()
        return MapperOutput(
            plan_steps=plan_steps,
            tile_assignments=tile_assignments,
            unlocked_kcs=unlocked,
            deterministic_signature=signature,
        )

    def _unlock_count(self, input_data: MapperInput, total: int) -> int:
        base = self.DEFAULT_UNLOCK_COUNT
        if input_data.profile_summary.python_self_rated_level <= 1:
            base = min(total, self.DEFAULT_UNLOCK_COUNT)
            base = max(3, min(self.DEFAULT_UNLOCK_COUNT, base))
        elif input_data.profile_summary.python_self_rated_level >= 4:
            base = min(total, self.DEFAULT_UNLOCK_COUNT + 3)
        return min(total, base)

    def _rationale(
        self, kc: Any, index: int, input_data: MapperInput
    ) -> str:
        if kc.kc_code in input_data.profile_summary.target_kc_codes:
            return f"目标 KC，优先级提升至第 {index + 1} 步"
        if input_data.profile_summary.python_self_rated_level >= 4 and index < 3:
            return f"自评较强，跳过题目难度低于 {kc.difficulty} 的导入"
        return f"按 chapter_order={kc.chapter_order} difficulty={kc.difficulty} 顺序排序"
