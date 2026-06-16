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


# ── 教学风格轮换 / packId=43 语境（TDD 新增） ──


def test_system_prompt_anchors_pack43_beginner_context() -> None:
    """答疑 SYSTEM_PROMPT 必须显式锚定 packId=43《Python语言基础》与初学者语境。"""
    from pythontopia_graph.agents.tutor_agent import SYSTEM_PROMPT

    assert "packId=43" in SYSTEM_PROMPT
    assert "初学者" in SYSTEM_PROMPT


def _history_of(turns: int) -> list[dict[str, str]]:
    """构造 turns 轮（user+assistant 成对）答疑历史。"""
    history: list[dict[str, str]] = []
    for index in range(turns):
        history.append({"role": "user", "content": f"第{index}问"})
        history.append({"role": "assistant", "content": f"第{index}答"})
    return history


def _style_directive(profile_brief: str, turns: int) -> str:
    """取 _build_messages 注入的那条风格指令（要求恰好一条）。"""
    agent = TutorAgent(provider=None)
    messages = agent._build_messages("怎么写 for 循环", profile_brief, _history_of(turns), [])
    hits = [
        m["content"]
        for m in messages
        if m["role"] == "system" and m["content"].startswith("本轮讲解节奏")
    ]
    assert len(hits) == 1
    return hits[0]


def test_style_directive_rotates_between_adjacent_turns() -> None:
    """相邻两轮答疑的讲解风格指令不得相同（开场/结构/收尾随之变化）。"""
    d0 = _style_directive("认知风格: visual", 0)
    d1 = _style_directive("认知风格: visual", 1)
    d2 = _style_directive("认知风格: visual", 2)
    assert d0 != d1 and d1 != d2


def test_style_rotation_respects_emotion_profile() -> None:
    """情绪需鼓励的画像：轮换候选必须包含鼓励复盘式风格。"""
    from pythontopia_graph.agents.orchestrator_agent import (
        REPLY_STYLE_DIRECTIVES,
        style_candidates,
    )

    brief = "情绪: 需鼓励"
    seen = {_style_directive(brief, turn) for turn in range(3)}
    expected = {REPLY_STYLE_DIRECTIVES[key] for key in style_candidates(brief)}
    assert seen == expected
    assert REPLY_STYLE_DIRECTIVES["encouraging_recap"] in seen


def test_style_directive_hides_internal_identifiers() -> None:
    """风格指令必须是自然中文，不携带 step_by_step 等内部模式标识符。"""
    from pythontopia_graph.agents.orchestrator_agent import REPLY_STYLE_KEYS

    directive = _style_directive("认知风格: visual", 0)
    assert not any(key in directive for key in REPLY_STYLE_KEYS)
    assert any("\u4e00" <= ch <= "\u9fff" for ch in directive)
