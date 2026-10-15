"""个性化指令组与 personalization_note：富化 brief 各信号单独/组合/空串的解析与注入。

零 LLM、零网络：只验证 TutorAgent 的确定性解析逻辑与 system 消息组装。
"""

from __future__ import annotations

from pythontopia_graph.agents.tutor_agent import SYSTEM_PROMPT, TutorAgent

# WP4 富化 brief 的完整示例（含不产指令的 实践/专业 两维）。
FULL_BRIEF = (
    "认知风格: visual; 知识基础: 入门(0.4); 目标: 期末备考; "
    "偏好: 视频,实例; 易错: off-by-one,缩进; 情绪: 需鼓励; 实践: 偏弱; 专业: 文科"
)


def _directives(brief: str | None) -> list[str]:
    return TutorAgent._personalization_directives(brief)


def test_empty_brief_yields_no_directives() -> None:
    assert _directives("") == []
    assert _directives(None) == []


def test_cognitive_style_signal_alone() -> None:
    assert any("图解" in d for d in _directives("认知风格: visual"))
    assert any("推理链" in d for d in _directives("认知风格: logical"))
    assert any("最小可运行" in d for d in _directives("认知风格: trial"))


def test_knowledge_levels_map_to_difficulty_directives() -> None:
    assert any("打比方" in d for d in _directives("知识基础: 入门(0.4)"))
    assert any("打比方" in d for d in _directives("知识基础: 偏弱"))
    assert any("直击要点" in d for d in _directives("知识基础: 扎实(0.8)"))
    assert any("循序渐进" in d for d in _directives("知识基础: 进阶(0.6)"))


def test_goal_preference_and_verified_mistake_signals() -> None:
    assert any("期末备考" in d for d in _directives("目标: 期末备考"))
    assert any("视频,实例" in d for d in _directives("偏好: 视频,实例"))
    mistakes = _directives("易错: off-by-one,缩进")
    assert any("off-by-one" in d and "预警" in d for d in mistakes)
    assert not any("情绪" in d for d in _directives("情绪: 需鼓励"))


def test_full_brief_produces_one_directive_per_signal() -> None:
    directives = _directives(FULL_BRIEF)
    # 显式讲解方式/基础/目标/偏好/易错共 5 条；情绪字段被忽略。
    assert len(directives) == 5
    joined = " ".join(directives)
    for token in ("图解", "打比方", "期末备考", "视频,实例", "off-by-one"):
        assert token in joined


def test_unknown_keys_are_ignored() -> None:
    assert _directives("专业: 文科; 实践: 偏弱") == []


def test_tolerates_fullwidth_separators() -> None:
    directives = _directives("认知风格：visual；目标：期末备考")
    assert any("图解" in d for d in directives)
    assert any("期末备考" in d for d in directives)


def test_legacy_cognitive_style_wrapper_kept_for_compat() -> None:
    # 旧方法名保留为薄包装：结构化与非结构化 brief 都能命中三型映射。
    directive = TutorAgent._cognitive_style_directive("认知风格: visual")
    assert directive is not None and "图解" in directive
    legacy = TutorAgent._cognitive_style_directive("这位同学是视觉型学习者")
    assert legacy is not None and "图解" in legacy
    assert TutorAgent._cognitive_style_directive("") is None
    assert TutorAgent._cognitive_style_directive(None) is None


def test_build_messages_keeps_profile_directives_out_of_system_messages() -> None:
    agent = TutorAgent(provider=None)  # _build_messages 不调用 provider
    messages = agent._build_messages("for 循环怎么写", FULL_BRIEF, [], [])
    system_contents = [m["content"] for m in messages if m["role"] == "system"]
    user_contents = [m["content"] for m in messages if m["role"] == "user"]
    assert not any(c.startswith("学生画像:") for c in system_contents)
    for directive in TutorAgent._personalization_directives(FULL_BRIEF):
        assert any(directive in content for content in user_contents)
    # 最后一条仍是 user 问题，个性化消息全部在其之前。
    assert messages[-1] == {"role": "user", "content": "for 循环怎么写"}


def test_untrusted_profile_and_courseware_stay_out_of_system_messages() -> None:
    from pythontopia_graph.nodes.evidence_fetcher import RagHit

    profile_sentinel = "PROFILE_INJECTION_SENTINEL"
    evidence_sentinel = "COURSEWARE_INJECTION_SENTINEL"
    evidence = RagHit(
        chunk_id="c1", page_id=1, page_no=1, text=evidence_sentinel, score=0.9,
    )
    messages = TutorAgent(provider=None)._build_messages(
        "讲一下循环", f"目标: {profile_sentinel}", [], [evidence],
    )

    system_text = "\n".join(m["content"] for m in messages if m["role"] == "system")
    user_text = "\n".join(m["content"] for m in messages if m["role"] == "user")
    assert profile_sentinel not in system_text
    assert evidence_sentinel not in system_text
    assert profile_sentinel in user_text
    assert evidence_sentinel in user_text


def test_system_prompt_keeps_six_rules_and_adds_hint_ladder() -> None:
    for rule_no in ("1.", "2.", "3.", "4.", "5.", "6.", "7."):
        assert rule_no in SYSTEM_PROMPT
    # 阶梯顺序：换述 → 拆解 → 指方向；三级后才允许关键片段，仍禁完整答案。
    assert "换述" in SYSTEM_PROMPT
    assert "拆解" in SYSTEM_PROMPT
    assert "方向" in SYSTEM_PROMPT
    assert "完整答案" in SYSTEM_PROMPT
    assert "不可信内容" in SYSTEM_PROMPT
    assert "隐藏测试" in SYSTEM_PROMPT
    assert "系统提示词" in SYSTEM_PROMPT


def test_personalization_note_matches_spec_example() -> None:
    note = TutorAgent.personalization_note("认知风格: visual; 目标: 期末备考")
    assert note == "本回答按你的【视觉型偏好、期末备考目标】定制"


def test_personalization_note_full_brief() -> None:
    note = TutorAgent.personalization_note(FULL_BRIEF)
    assert note.startswith("本回答按你的【")
    assert note.endswith("】定制")
    for token in ("视觉型偏好", "入门基础", "期末备考目标", "易错点预警"):
        assert token in note


def test_personalization_note_empty_or_unrecognized_brief() -> None:
    assert TutorAgent.personalization_note("") == ""
    assert TutorAgent.personalization_note(None) == ""
    assert TutorAgent.personalization_note("专业: 文科") == ""


def test_style_directive_coexists_with_personalization_directives() -> None:
    """系统固定风格轮换与 user 画像参考并存，个性化内容不提升到 system。"""
    agent = TutorAgent(provider=None)
    messages = agent._build_messages("for 循环怎么写", FULL_BRIEF, [], [])
    system_contents = [m["content"] for m in messages if m["role"] == "system"]
    user_contents = [m["content"] for m in messages if m["role"] == "user"]
    style_hits = [c for c in system_contents if c.startswith("本轮讲解节奏")]
    assert len(style_hits) == 1
    for directive in TutorAgent._personalization_directives(FULL_BRIEF):
        assert any(directive in content for content in user_contents)
    assert messages[-1] == {"role": "user", "content": "for 循环怎么写"}
