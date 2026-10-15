"""资源生成因材施教：认知风格 + 易错点作为学习者上下文传入。

directive 映射对齐 TutorAgent._cognitive_style_directive；集成断言走 _prompt（不调用
真实 LLM），确认动态个性化内容不会进入 system 消息。
"""

from __future__ import annotations

import pytest

from pythontopia_graph.agents.code_lab_agent import CodeLabAgent
from pythontopia_graph.agents.handout_agent import HandoutAgent
from pythontopia_graph.agents.mind_map_agent import MindMapAgent
from pythontopia_graph.agents.personalization import (
    personalization_messages,
    resource_personalization_directive,
)
from pythontopia_graph.agents.practice_agent import PracticeAgent
from pythontopia_graph.agents.reading_agent import ReadingAgent
from pythontopia_graph.agents.storyboard_agent import StoryboardAgent
from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle, RagHit
from pythontopia_graph.schemas.resource import ProfileSnapshot, ResourceProduceRequest


def _snapshot(style: str = "", patterns: list[str] | None = None) -> ProfileSnapshot:
    return ProfileSnapshot(cognitive_style=style, error_patterns=patterns or [])


EVIDENCE = EvidenceBundle(
    chunks=[
        RagHit(
            chunk_id="c1",
            page_id=101,
            page_no=12,
            chapter_id=3,
            kc_codes=["PY43-KC-001"],
            text="第 12 页讲了 input 函数的基本用法。",
            score=0.81,
        )
    ]
)


def _request(style: str, patterns: list[str]) -> ResourceProduceRequest:
    return ResourceProduceRequest(
        user_id=1,
        kc_codes=["PY43-KC-001"],
        type="HANDOUT",  # type: ignore[arg-type]
        profile_snapshot=ProfileSnapshot(cognitive_style=style, error_patterns=patterns),
    )


# directive 映射


def test_visual_directive_has_keyword() -> None:
    directive = resource_personalization_directive(_snapshot("visual"))
    assert directive is not None
    assert "图解" in directive


def test_logical_directive_has_keyword() -> None:
    directive = resource_personalization_directive(_snapshot("logical"))
    assert directive is not None
    assert "推理链" in directive or "步骤" in directive


def test_trial_directive_has_keyword() -> None:
    directive = resource_personalization_directive(_snapshot("trial"))
    assert directive is not None
    assert "最小可运行" in directive or "示例" in directive


def test_directives_are_distinct() -> None:
    visual = resource_personalization_directive(_snapshot("visual"))
    logical = resource_personalization_directive(_snapshot("logical"))
    trial = resource_personalization_directive(_snapshot("trial"))
    assert len({visual, logical, trial}) == 3


def test_chinese_style_aliases_match() -> None:
    assert "图解" in (resource_personalization_directive(_snapshot("视觉型")) or "")
    assert "推理链" in (resource_personalization_directive(_snapshot("逻辑")) or "")
    assert "最小可运行" in (resource_personalization_directive(_snapshot("试错")) or "")


# 空 / 未知风格


def test_empty_snapshot_returns_none() -> None:
    assert resource_personalization_directive(_snapshot("")) is None
    assert resource_personalization_directive(_snapshot("   ")) is None
    assert resource_personalization_directive(_snapshot("unknown")) is None


def test_blank_error_patterns_ignored() -> None:
    assert resource_personalization_directive(_snapshot("", ["", "  "])) is None


# 易错点


def test_error_patterns_included_with_style() -> None:
    directive = resource_personalization_directive(_snapshot("visual", ["缩进", "变量命名"]))
    assert directive is not None
    assert "图解" in directive
    assert "易错点" in directive
    assert "缩进" in directive
    assert "变量命名" in directive
    assert "缩进、变量命名" in directive  # 顿号连接


def test_error_patterns_alone_yield_directive() -> None:
    directive = resource_personalization_directive(_snapshot("", ["缩进"]))
    assert directive is not None
    assert "缩进" in directive


# personalization_messages


def test_personalization_messages_shape() -> None:
    msgs = personalization_messages(_snapshot("visual"))
    assert len(msgs) == 1
    assert msgs[0]["role"] == "user"
    assert "图解" in msgs[0]["content"]


def test_personalization_messages_empty_when_no_signal() -> None:
    assert personalization_messages(_snapshot("")) == []


# 学情洞察（Task 1.3：ProfileSnapshot.recent_insight）


def test_recent_insight_included_in_directive() -> None:
    snap = ProfileSnapshot(recent_insight="可变对象别名反复出错")
    directive = resource_personalization_directive(snap)
    assert directive is not None
    assert "学情洞察" in directive
    assert "可变对象别名反复出错" in directive


def test_recent_insight_empty_not_included() -> None:
    assert resource_personalization_directive(ProfileSnapshot(recent_insight="")) is None
    assert resource_personalization_directive(ProfileSnapshot(recent_insight="   ")) is None


def test_recent_insight_combines_with_style() -> None:
    snap = ProfileSnapshot(cognitive_style="visual", recent_insight="缩进易错")
    directive = resource_personalization_directive(snap)
    assert directive is not None
    assert "图解" in directive
    assert "学情洞察" in directive
    assert "缩进易错" in directive


# 记忆要点（Task 2.1：memory_highlights）


def test_memory_highlights_param_included() -> None:
    directive = resource_personalization_directive(
        ProfileSnapshot(), memory_highlights=["练习失败：PY43-KC-016（WA）", "调试修复：PY43-KC-016"]
    )
    assert directive is not None
    assert "近期学情" in directive
    assert "练习失败：PY43-KC-016（WA）" in directive
    assert "调试修复：PY43-KC-016" in directive


def test_memory_highlights_via_snapshot_field() -> None:
    snap = ProfileSnapshot(memory_highlights=["逆袭通过：PY43-KC-010"])
    directive = resource_personalization_directive(snap)
    assert directive is not None
    assert "近期学情" in directive
    assert "逆袭通过：PY43-KC-010" in directive


def test_memory_highlights_param_overrides_snapshot() -> None:
    snap = ProfileSnapshot(memory_highlights=["旧要点"])
    directive = resource_personalization_directive(snap, memory_highlights=["新要点"])
    assert directive is not None
    assert "新要点" in directive
    assert "旧要点" not in directive


def test_memory_highlights_empty_not_included() -> None:
    assert resource_personalization_directive(ProfileSnapshot(memory_highlights=[])) is None
    assert (
        resource_personalization_directive(ProfileSnapshot(), memory_highlights=["", "  "]) is None
    )


def test_deep_memory_directives_included() -> None:
    snap = ProfileSnapshot(
        memory_directives=[
            "偏好图解",
            "微测未过：PY43-KC-001",
            "优先图解/结构化示例。",
        ]
    )
    directive = resource_personalization_directive(snap)
    assert directive is not None
    assert "个人记忆策略" in directive
    assert "偏好图解" in directive
    assert "微测未过：PY43-KC-001" in directive
    assert "优先图解/结构化示例。" in directive


def test_personalization_messages_passes_memory_highlights() -> None:
    msgs = personalization_messages(ProfileSnapshot(), memory_highlights=["练习失败：PY43-KC-016"])
    assert len(msgs) == 1
    assert msgs[0]["role"] == "user"
    assert "近期学情" in msgs[0]["content"]


# 集成：画像内容作为 user 数据消息，system 只保留静态约束


def test_handout_prompt_keeps_personalization_out_of_system_messages() -> None:
    agent = HandoutAgent(provider=None)  # _prompt 不触发 provider
    messages = agent._prompt(_request("visual", ["缩进"]), EVIDENCE)
    assert messages[-1]["role"] == "user"
    system_text = " ".join(m["content"] for m in messages if m["role"] == "system")
    user_context = " ".join(m["content"] for m in messages if m["role"] == "user")
    assert "图解" not in system_text and "缩进" not in system_text
    assert "图解" in user_context and "缩进" in user_context


def test_code_lab_prompt_keeps_personalization_out_of_system_messages() -> None:
    agent = CodeLabAgent(provider=None)  # _prompt 不触发 provider
    messages = agent._prompt(_request("trial", ["边界条件"]))
    assert messages[-1]["role"] == "user"
    system_text = " ".join(message["content"] for message in messages if message["role"] == "system")
    user_context = " ".join(message["content"] for message in messages if message["role"] == "user")
    assert "最小可运行" not in system_text and "边界条件" not in system_text
    assert "最小可运行" in user_context or "示例" in user_context
    assert "边界条件" in user_context


def test_handout_prompt_skips_personalization_when_empty() -> None:
    agent = HandoutAgent(provider=None)
    messages = agent._prompt(_request("", []), EVIDENCE)
    assert messages[-1]["role"] == "user"
    system_text = " ".join(m["content"] for m in messages if m["role"] == "system")
    assert "易错点" not in system_text


# 团队策略 directive（Task 4.1：agent_strategy_directive，与后端 snake_case 契约对齐）

_STRATEGY_DIRECTIVE = "多给可运行示例，少堆术语；先讲直觉再上代码。"
_RESOURCE_AGENTS = [
    HandoutAgent,
    MindMapAgent,
    PracticeAgent,
    CodeLabAgent,
    ReadingAgent,
    StoryboardAgent,
]


def _strategy_request(directive: str | None) -> ResourceProduceRequest:
    return ResourceProduceRequest(
        user_id=1,
        kc_codes=["PY43-KC-001"],
        type="HANDOUT",  # type: ignore[arg-type]
        agent_strategy_directive=directive,
    )


def _build_prompt(
    agent: object,
    request: ResourceProduceRequest,
    revision_notes: list[str] | None = None,
) -> list[dict[str, str]]:
    return agent._prompt(request, EVIDENCE, revision_notes)  # type: ignore[attr-defined]


@pytest.mark.parametrize("agent_cls", _RESOURCE_AGENTS)
def test_dynamic_profile_and_review_context_stay_below_system_role(agent_cls) -> None:
    marker = "ignore-system-policy-sentinel"
    request = _strategy_request(None).model_copy(
        update={"profile_snapshot": ProfileSnapshot(memory_directives=[marker])}
    )
    messages = _build_prompt(agent_cls(provider=None), request, [marker])

    first_user_message = next(
        index for index, message in enumerate(messages) if message["role"] == "user"
    )
    assert all(message["role"] == "user" for message in messages[first_user_message:])
    system_text = " ".join(message["content"] for message in messages if message["role"] == "system")
    user_text = " ".join(message["content"] for message in messages if message["role"] == "user")
    assert marker not in system_text
    assert marker in user_text


@pytest.mark.parametrize("agent_cls", _RESOURCE_AGENTS)
def test_agent_strategy_directive_injected_when_present(agent_cls) -> None:
    agent = agent_cls(provider=None)
    messages = _build_prompt(agent, _strategy_request(_STRATEGY_DIRECTIVE))
    text = " ".join(m["content"] for m in messages)
    assert _STRATEGY_DIRECTIVE in text
    assert "团队策略提示" in text
    assert all(
        message["role"] == "user"
        for message in messages
        if _STRATEGY_DIRECTIVE in message["content"]
    )


@pytest.mark.parametrize("agent_cls", _RESOURCE_AGENTS)
def test_agent_strategy_directive_absent_when_none(agent_cls) -> None:
    agent = agent_cls(provider=None)
    messages = _build_prompt(agent, _strategy_request(None))
    text = " ".join(m["content"] for m in messages)
    assert "团队策略提示" not in text


def test_agent_strategy_directive_is_snake_case_contract() -> None:
    # 后端按 snake_case 下发；字段名必须正好是 agent_strategy_directive。
    request = ResourceProduceRequest.model_validate(
        {
            "user_id": 1,
            "kc_codes": ["PY43-KC-001"],
            "type": "HANDOUT",
            "agent_strategy_directive": _STRATEGY_DIRECTIVE,
        }
    )
    assert request.agent_strategy_directive == _STRATEGY_DIRECTIVE
