from __future__ import annotations

from copy import deepcopy
from typing import Any


SUPPORTED_PROMPT_VARIANTS = {"baseline", "protocol_patch_v1"}


def apply_prompt_variant(case: dict[str, Any], variant: str | None) -> dict[str, Any]:
    normalized = variant or "baseline"
    if normalized == "baseline":
        return deepcopy(case)
    if normalized != "protocol_patch_v1":
        raise ValueError(f"unsupported prompt variant: {variant}")

    patched = deepcopy(case)
    patched["system"] = f"{case['system']}\n\n{_protocol_patch_v1(case)}"
    return patched


def _protocol_patch_v1(case: dict[str, Any]) -> str:
    evidence_ids = [str(item) for item in case.get("evidence_ids", [])]
    if evidence_ids:
        grounding_contract = (
            "Evidence contract: include an `evidence_ids` JSON array containing every required evidence id exactly: "
            f"{', '.join(evidence_ids)}."
        )
    else:
        grounding_contract = (
            'Evidence contract: no page evidence is required; include "evidence_ids": [] and '
            '"grounding_note": "common_knowledge" in the JSON object.'
        )

    return "\n".join(
        [
            "Candidate prompt variant: protocol_patch_v1.",
            "Return only one valid JSON object. Use double-quoted JSON keys and strings.",
            "Do not wrap the JSON in Markdown or code fences. Do not add prose before or after the object.",
            "Preserve the agent role and the requested output fields from the original instruction.",
            grounding_contract,
            "Tutoring safety contract: provide hints, probes, plans, or redactions; do not provide a directly submittable full answer.",
        ]
    )
