"""TutorAgent 认知风格分支：profile_brief 命中 visual/logical/trial → 注入对应首屏讲法指令。"""

from __future__ import annotations

from pythontopia_graph.agents.tutor_agent import TutorAgent


def _system_text(profile_brief: str) -> str:
    agent = TutorAgent(provider=None)  # _build_messages 不调用 provider
    messages = agent._build_messages("怎么写 for 循环", profile_brief, [], [])
    return " ".join(m["content"] for m in messages if m["role"] == "system")


def test_visual_style_directive() -> None:
    assert "图解" in _system_text("认知风格: visual; 易错: indent")


def test_logical_style_directive() -> None:
    text = _system_text("认知风格: logical")
    assert "推理链" in text or "步骤" in text


def test_trial_style_directive() -> None:
    text = _system_text("认知风格: trial")
    assert "最小可运行" in text or "示例" in text


def test_no_style_when_absent() -> None:
    text = _system_text("")
    assert "图解" not in text and "推理链" not in text and "最小可运行" not in text
