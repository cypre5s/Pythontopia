"""资源生成因材施教：认知风格 + 易错点 → 注入 6 个资源 Agent 的 system 指令。

directive 映射对齐 TutorAgent._cognitive_style_directive；集成断言走 _prompt（不调用
真实 LLM），确认个性化 system 消息落在最终 user 消息之前。
"""

from __future__ import annotations

from pythontopia_graph.agents.code_lab_agent import CodeLabAgent
from pythontopia_graph.agents.handout_agent import HandoutAgent
from pythontopia_graph.agents.personalization import (
    personalization_messages,
    resource_personalization_directive,
)
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


# ---- directive 映射 -------------------------------------------------------


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


# ---- 空 / 未知风格 -------------------------------------------------------


def test_empty_snapshot_returns_none() -> None:
    assert resource_personalization_directive(_snapshot("")) is None
    assert resource_personalization_directive(_snapshot("   ")) is None
    assert resource_personalization_directive(_snapshot("unknown")) is None


def test_blank_error_patterns_ignored() -> None:
    assert resource_personalization_directive(_snapshot("", ["", "  "])) is None


# ---- 易错点 --------------------------------------------------------------


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


# ---- personalization_messages -------------------------------------------


def test_personalization_messages_shape() -> None:
    msgs = personalization_messages(_snapshot("visual"))
    assert len(msgs) == 1
    assert msgs[0]["role"] == "system"
    assert "图解" in msgs[0]["content"]


def test_personalization_messages_empty_when_no_signal() -> None:
    assert personalization_messages(_snapshot("")) == []


# ---- 集成：注入位置在最终 user 消息之前 ----------------------------------


def test_handout_prompt_injects_personalization_before_user() -> None:
    agent = HandoutAgent(provider=None)  # _prompt 不触发 provider
    messages = agent._prompt(_request("visual", ["缩进"]), EVIDENCE)
    assert messages[-1]["role"] == "user"
    assert messages[-2]["role"] == "system"
    assert "图解" in messages[-2]["content"]
    assert "缩进" in messages[-2]["content"]
    system_text = " ".join(m["content"] for m in messages if m["role"] == "system")
    assert "图解" in system_text and "缩进" in system_text


def test_code_lab_prompt_injects_personalization_before_user() -> None:
    agent = CodeLabAgent(provider=None)  # _prompt 不触发 provider
    messages = agent._prompt(_request("trial", ["边界条件"]))
    assert messages[-1]["role"] == "user"
    assert messages[-2]["role"] == "system"
    directive = messages[-2]["content"]
    assert "最小可运行" in directive or "示例" in directive
    assert "边界条件" in directive


def test_handout_prompt_skips_personalization_when_empty() -> None:
    agent = HandoutAgent(provider=None)
    messages = agent._prompt(_request("", []), EVIDENCE)
    assert messages[-1]["role"] == "user"
    system_text = " ".join(m["content"] for m in messages if m["role"] == "system")
    assert "易错点" not in system_text
