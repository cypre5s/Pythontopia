from __future__ import annotations

from pydantic import ValidationError

from pythontopia_graph.llm.provider import LlmProvider
from pythontopia_graph.schemas.profile import (
    EvidenceAnchor,
    GroundingResult,
    ProfileAgentInput,
    ProfileAgentOutput,
    ProfileDraft,
    SafetyResult,
)


MAX_PROFILE_TURNS = 30


async def run_profile_graph(provider: LlmProvider, input_data: ProfileAgentInput) -> ProfileAgentOutput:
    if len(input_data.last_turn_history) > MAX_PROFILE_TURNS:
        return ProfileAgentOutput(
            next_question=None,
            profile_draft=None,
            done=True,
            dimensions_filled=[],
            evidence_anchors=[],
            safety_result=SafetyResult(passed=True),
            grounding_result=GroundingResult(passed=False, reasons=["MAX_TURNS_EXCEEDED"]),
        )
    messages = node_question_planner(input_data)
    response = await provider.chat(messages)
    safety = node_safety(response.content_json)
    if not safety.passed:
        return ProfileAgentOutput(
            next_question=None,
            profile_draft=None,
            done=True,
            dimensions_filled=[],
            evidence_anchors=[],
            safety_result=safety,
            grounding_result=GroundingResult(passed=False, reasons=["SAFETY_FAILED"]),
        )
    draft = node_response_parser(response.content_json)
    grounding = node_grounding_critic(draft, input_data)
    if not grounding.passed:
        return ProfileAgentOutput(
            next_question="我需要再确认一下：你刚才提到的知识点能对应到课程目录中的哪个主题？",
            profile_draft=None,
            done=False,
            dimensions_filled=draft.filled_dimensions,
            evidence_anchors=node_evidence(input_data),
            safety_result=safety,
            grounding_result=grounding,
        )
    done = len(draft.filled_dimensions) == 8
    return ProfileAgentOutput(
        next_question=None if done else "还差一些信息：你更偏好视频、文字、思维导图还是代码练习？",
        profile_draft=draft if done else None,
        done=done,
        dimensions_filled=draft.filled_dimensions,
        evidence_anchors=node_evidence(input_data),
        safety_result=safety,
        grounding_result=grounding,
    )


def node_question_planner(input_data: ProfileAgentInput) -> list[dict[str, str]]:
    kc_summary = ", ".join(item.kc_code for item in input_data.course_kc_index[:12])
    return [
        {
            "role": "system",
            "content": (
                "你是 Pythontopia ProfileAgent。只输出 JSON。"
                "必须覆盖 8 维画像，KC code 只能来自给定列表。"
            ),
        },
        {"role": "system", "content": f"课程 KC 列表: {kc_summary}"},
        {"role": "user", "content": input_data.last_user_message},
    ]


def node_response_parser(content_json: dict[str, object] | None) -> ProfileDraft:
    if content_json is None or "profile_draft" not in content_json:
        raise ValueError("ProfileAgent provider returned no profile_draft")
    try:
        return ProfileDraft.model_validate(content_json["profile_draft"])
    except ValidationError as exc:
        raise ValueError("ProfileAgent provider returned invalid profile_draft") from exc


def node_grounding_critic(draft: ProfileDraft, input_data: ProfileAgentInput) -> GroundingResult:
    valid_codes = {item.kc_code for item in input_data.course_kc_index}
    referenced: list[str] = []
    if draft.learning_goal is not None:
        referenced.extend(draft.learning_goal.target_kc_codes)
    if draft.error_patterns is not None:
        referenced.extend(draft.error_patterns.frequent_kc_codes)
    invalid = [code for code in referenced if code not in valid_codes]
    if invalid:
        return GroundingResult(
            passed=False,
            reasons=[f"Unknown KC code: {code}" for code in invalid],
        )
    return GroundingResult(passed=True)


def node_safety(content_json: dict[str, object] | None) -> SafetyResult:
    if content_json is not None and content_json.get("safety_failed") is True:
        return SafetyResult(passed=False, reasons=[str(content_json.get("reason", "SAFETY_FAILED"))])
    return SafetyResult(passed=True)


def node_evidence(input_data: ProfileAgentInput) -> list[EvidenceAnchor]:
    return [
        EvidenceAnchor(
            source="interview",
            ref=input_data.interview_session_id,
            summary="画像字段来自当前画像屋访谈回答。",
        ),
        EvidenceAnchor(
            source="profile_schema",
            ref="docs/profile_schema.md",
            summary="画像维度遵循 Pythontopia 8 维定义。",
        ),
    ]
