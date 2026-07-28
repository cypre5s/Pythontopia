from __future__ import annotations

import random
from collections.abc import AsyncIterator

from fastapi.testclient import TestClient

from pythontopia_graph.agents.feynman import bug_bank as bug_bank_mod
from pythontopia_graph.agents.feynman.bug_bank import (
    all_samples,
    bug_line_indices as bank_bug_line_indices,
    distinct_tags,
    samples_for_chapter,
    samples_for_tag,
    validate_sample,
)
from pythontopia_graph.agents.feynman_tutee_agent import (
    FeynmanTuteeAgent,
    bug_line_indices,
)
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.main import app
from pythontopia_graph.schemas.feynman import (
    FeynmanBugExposureInput,
    FeynmanTuteeInput,
    Misconception,
)


class JsonProvider(LlmProvider):
    def __init__(self, payload: dict) -> None:
        self.payload = payload
        self.calls = 0

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        self.calls += 1
        return LlmResponse(content_json=self.payload)

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        yield ""


class RaisingProvider(LlmProvider):
    async def chat(self, messages, **kwargs):  # type: ignore[override]
        raise RuntimeError("provider unavailable")

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        yield ""


def _misconception(tag: str = "off_by_one_range") -> Misconception:
    return Misconception(tag=tag, evidence_event_ids=[501], resolved=False)


def _input(
    misconceptions: list[Misconception] | None = None,
    teaching: str | None = None,
) -> FeynmanTuteeInput:
    return FeynmanTuteeInput(
        kc_code="PY43-KC-021",
        misconceptions=misconceptions or [_misconception()],
        student_teaching=teaching,
    )


async def test_injected_misconception_makes_corresponding_mistake() -> None:
    # 初始暴露：学弟中性描述代码做了什么（不忏悔），误解隐含在 produced_code 里。
    provider = JsonProvider(
        {
            "reply_md": "我写了个循环，用 range(1, 5) 从 1 累加到 5，最后打印总和。",
            "updated_misconceptions": [
                {"tag": "off_by_one_range", "evidence_event_ids": [501], "resolved": False}
            ],
            "produced_code": "for i in range(1, 5):\n    print(i)",
        }
    )

    out = await FeynmanTuteeAgent(provider).step(_input())

    # tag 透传、初始未解决、误解体现在代码里；且不得出现忏悔式措辞。
    assert out.updated_misconceptions[0].tag == "off_by_one_range"
    assert out.updated_misconceptions[0].resolved is False
    assert "range" in (out.produced_code or "")
    assert "我以为" not in out.reply_md


async def test_correct_teaching_resolves_matching_misconception() -> None:
    provider = RaisingProvider()

    out = await FeynmanTuteeAgent(provider).step(
        _input(teaching="range 的右端点不包含，range(1, 5) 只到 4；要包含 5 就写 range(1, 6)。")
    )

    assert out.updated_misconceptions[0].resolved is True
    assert "明白" in out.reply_md
    assert out.produced_code is not None


async def test_wrong_teaching_does_not_resolve_misconception() -> None:
    provider = RaisingProvider()

    out = await FeynmanTuteeAgent(provider).step(
        _input(teaching="range 会自动包含最后一个数，所以不用改结束值。")
    )

    assert out.updated_misconceptions[0].resolved is False
    assert "还是有点困惑" in out.reply_md
    assert out.produced_code is None


async def test_provider_failure_uses_rule_fallback() -> None:
    provider = RaisingProvider()

    out = await FeynmanTuteeAgent(provider).step(_input())

    assert "累加" in out.reply_md or "range" in out.reply_md
    assert out.updated_misconceptions[0].tag == "off_by_one_range"
    assert out.updated_misconceptions[0].resolved is False


async def test_teaching_is_evaluated_by_llm_for_arbitrary_tag() -> None:
    # 任意真实误解标签（非规则脚本硬编码的 3 个）在「被教学」回合也必须经 LLM 评估，
    # 否则学弟永远学不会该误解（修复前 teach 回合恒走规则脚本，未知 tag 永不 resolved）。
    provider = JsonProvider(
        {
            "reply_md": "我懂了：默认可变参数只会创建一次。",
            "updated_misconceptions": [
                {"tag": "mutable_default_arg", "evidence_event_ids": [777], "resolved": True}
            ],
            "produced_code": "def f(x, acc=None):\n    acc = acc or []",
        }
    )
    inp = _input(
        misconceptions=[Misconception(tag="mutable_default_arg", evidence_event_ids=[777])],
        teaching="默认参数在函数定义时只创建一次，所以应当用 None 哨兵再在函数体内新建列表。",
    )

    out = await FeynmanTuteeAgent(provider).step(inp)

    assert provider.calls == 1
    assert out.updated_misconceptions[0].tag == "mutable_default_arg"
    assert out.updated_misconceptions[0].resolved is True


async def test_resolved_llm_output_without_code_does_not_create_dead_taught_state() -> None:
    provider = JsonProvider(
        {
            "reply_md": "我懂了，这次会按上限包含关系来修正。",
            "updated_misconceptions": [
                {"tag": "WA", "evidence_event_ids": [636], "resolved": True}
            ],
            "produced_code": None,
        }
    )
    inp = _input(
        misconceptions=[Misconception(tag="WA", evidence_event_ids=[636])],
        teaching="循环右端要包含上限，并累加每一个满足条件的偶数。",
    )

    out = await FeynmanTuteeAgent(provider).step(inp)

    assert out.updated_misconceptions[0].resolved is False
    assert out.produced_code is None


async def test_initial_step_forces_misconception_unresolved() -> None:
    # 初始暴露阶段（无教学）即使 LLM 误标 resolved=true，也必须被强制改回 false。
    # reply_md 用中性的代码描述（不忏悔），但 resolved 强制收敛逻辑不变。
    provider = JsonProvider(
        {
            "reply_md": "我写了个循环，用 range(1, 5) 从 1 累加到 5。",
            "updated_misconceptions": [
                {"tag": "off_by_one_range", "evidence_event_ids": [501], "resolved": True}
            ],
            "produced_code": "for i in range(1, 5):\n    print(i)",
        }
    )

    out = await FeynmanTuteeAgent(provider).step(_input())

    assert out.updated_misconceptions[0].resolved is False


def test_feynman_route_is_registered() -> None:
    app.state.llm_provider = JsonProvider(
        {
            "reply_md": "我让用户输入年龄，然后把年龄加一后打印出来。",
            "updated_misconceptions": [
                {"tag": "input_type_string", "evidence_event_ids": [601], "resolved": False}
            ],
            "produced_code": "age = input('age: ')\nprint(age + 1)",
        }
    )
    client = TestClient(app)

    response = client.post(
        "/agents/feynman/tutee/step",
        json={
            "kc_code": "PY43-KC-004",
            "misconceptions": [
                {"tag": "input_type_string", "evidence_event_ids": [601], "resolved": False}
            ],
            "student_teaching": None,
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["updated_misconceptions"][0]["tag"] == "input_type_string"
    assert "我以为" not in body["reply_md"]
    del app.state.llm_provider


# ── 代码找茬（bug-exposure） ──────────────────────────────────────────────

def test_bug_line_indices_diffs_replaced_line() -> None:
    buggy = "a = [1, 2]\nb = a\nb.append(3)\nprint(a)"
    fixed = "a = [1, 2]\nb = a.copy()\nb.append(3)\nprint(a)"
    assert bug_line_indices(buggy, fixed) == [1]


async def test_expose_bug_canned_tag_marks_real_bug_line() -> None:
    # 已知误解 tag 走 bug 库确定性样本（注入固定 seed 确定化变体），不应触达 LLM
    # （用 RaisingProvider 兜底证明）。bug 行号严格由 buggy/fixed 行级 diff 得出（零伪造）。
    out = await FeynmanTuteeAgent(RaisingProvider(), rng=random.Random(0)).expose_bug(
        FeynmanBugExposureInput(
            kc_code="PY43-KC-021",
            misconceptions=[_misconception("off_by_one_range")],
        )
    )

    assert out.source == "real_misconception"
    assert out.misconception_tag == "off_by_one_range"
    assert "range(" in (out.buggy_code or "")
    # bug_line_indices 必须等于对 buggy/fixed 重新做行级 diff 的结果，且至少标出一行。
    assert out.bug_line_indices == bug_line_indices(out.buggy_code or "", out.fixed_code or "")
    assert out.bug_line_indices
    assert out.hint_md


async def test_expose_bug_unknown_tag_uses_llm_and_diffs() -> None:
    provider = JsonProvider(
        {
            "buggy_code": "x = 1\nprint(x + '1')",
            "fixed_code": "x = 1\nprint(x + 1)",
            "hint_md": "类型对不上",
        }
    )

    out = await FeynmanTuteeAgent(provider).expose_bug(
        FeynmanBugExposureInput(
            kc_code="PY43-KC-099",
            misconceptions=[_misconception("some_unmapped_tag")],
        )
    )

    assert provider.calls == 1
    assert out.source == "real_misconception"
    assert out.bug_line_indices == [1]
    assert "x + '1'" in (out.buggy_code or "")


async def test_expose_bug_without_history_falls_back_to_generic() -> None:
    out = await FeynmanTuteeAgent(RaisingProvider()).expose_bug(
        FeynmanBugExposureInput(kc_code="PY43-KC-021", misconceptions=[])
    )

    assert out.source == "generic_beginner_bug"
    assert out.buggy_code is not None
    assert out.bug_line_indices  # 至少标出一行 bug


async def test_expose_bug_non_courseware_concept_uses_topic_in_prompt() -> None:
    """课件外概念（非 pack KC）+ topic：LLM 生成贴该概念的 bug，且把该主题带进 prompt。"""

    class CapturingProvider(LlmProvider):
        def __init__(self, payload: dict) -> None:
            self.payload = payload
            self.last_messages: list[dict[str, str]] | None = None

        async def chat(self, messages, **kwargs):  # type: ignore[override]
            self.last_messages = messages
            return LlmResponse(content_json=self.payload)

        async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
            yield ""

    provider = CapturingProvider(
        {
            "buggy_code": "import re\nm = re.match('a', 'ba')\nprint(m.group())",
            "fixed_code": "import re\nm = re.search('a', 'ba')\nprint(m.group())",
            "hint_md": "match 从头匹配还是任意位置？",
        }
    )

    out = await FeynmanTuteeAgent(provider).expose_bug(
        FeynmanBugExposureInput(kc_code="正则表达式", topic="正则表达式", misconceptions=[])
    )

    # 课件外概念（chapter 未知）走不到 bank，但带 topic 仍能由 LLM 现生贴该概念的 bug。
    assert out.source == "generic_beginner_bug"
    assert out.buggy_code is not None
    assert out.bug_line_indices
    blob = " ".join(m.get("content", "") for m in (provider.last_messages or []))
    assert "正则表达式" in blob


def test_canned_tag_for_kc_maps_chapter_to_relevant_bug() -> None:
    # 兜底 bug 不再按 kc_code 哈希乱挑，而是按章节主题挑：循环 / 数据结构 / 类型各取对应陷阱。
    pick = FeynmanTuteeAgent._canned_tag_for_kc
    assert pick("PY43-KC-031") == "off_by_one_range"  # 第四章 流程自动化（for 循环）
    assert pick("PY43-KC-040") == "list_alias_mutation"  # 第五章 数据结构（列表）
    assert pick("PY43-KC-013") == "input_type_string"  # 第二章 控制台输入与类型转换
    assert pick("PY43-KC-019") == "input_type_string"  # 第三章 数值类型与运算
    assert pick("PY43-KC-999") == "off_by_one_range"  # 未知 KC 回退最通用陷阱


async def test_generic_fallback_is_chapter_relevant() -> None:
    # 选「for 循环与遍历」(KC-031, 第四章) 无真实误解史时，兜底应从该章节 bug 库抽题，
    # 即返回的 tag 必属于第四章题库（chapter-relevant），而非随机跨章陷阱。
    out = await FeynmanTuteeAgent(RaisingProvider(), rng=random.Random(1)).expose_bug(
        FeynmanBugExposureInput(kc_code="PY43-KC-031", misconceptions=[])
    )

    assert out.source == "generic_beginner_bug"
    chapter4_tags = {s.tag for s in samples_for_chapter(4)}
    assert out.misconception_tag in chapter4_tags
    assert out.bug_line_indices


def test_bug_exposure_route_returns_bug_lines() -> None:
    client = TestClient(app)

    response = client.post(
        "/agents/feynman/bug-exposure",
        json={
            "kc_code": "PY43-KC-004",
            "misconceptions": [
                {"tag": "input_type_string", "evidence_event_ids": [601], "resolved": False}
            ],
        },
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["source"] == "real_misconception"
    assert payload["misconception_tag"] == "input_type_string"
    # 路由层不注入 seed（随机变体），故断言「非空真实 bug 行」而非固定下标。
    assert payload["bug_line_indices"]
    assert payload["bug_line_indices"] == bug_line_indices(
        payload["buggy_code"], payload["fixed_code"]
    )


# ── TASK A：初始暴露中性化（去「我以为」忏悔） ───────────────────────────────

def test_initial_prompt_requires_neutral_voice_and_forbids_confession() -> None:
    # 白盒：初始暴露（student_teaching=None）的系统提示必须要求中性描述、禁止忏悔/点破 bug。
    agent = FeynmanTuteeAgent(RaisingProvider())
    messages = agent._messages(_input())
    system = "\n".join(m["content"] for m in messages if m["role"] == "system")
    assert "我以为" in system  # 以“禁止词清单”的形式出现
    assert "严禁" in system or "不得" in system
    assert "中性" in system
    # 末轮 user 指令要求把（带误解的）代码放进 produced_code，但不点破。
    user = [m for m in messages if m["role"] == "user"][-1]["content"]
    assert "produced_code" in user


def test_teaching_prompt_keeps_evaluation_voice() -> None:
    # 教学回合（student_teaching 已给）不加初始中性化指令，沿用评估口吻。
    agent = FeynmanTuteeAgent(RaisingProvider())
    messages = agent._messages(_input(teaching="range 右端点不包含，要包含 5 应写 range(1, 6)。"))
    system = "\n".join(m["content"] for m in messages if m["role"] == "system")
    assert "中性" not in system
    user = [m for m in messages if m["role"] == "user"][-1]["content"]
    assert "range(1, 6)" in user  # 教学原文作为 user 轮


# ── TASK B：bug 库（bug_bank） ──────────────────────────────────────────────

def test_bank_has_rich_coverage() -> None:
    samples = all_samples()
    assert len(samples) >= 24  # 任务要求至少 ~24，实际更多
    assert len(distinct_tags()) >= 12  # 丰富的错误种类
    for chapter in range(1, 8):  # 覆盖全部 7 章
        assert samples_for_chapter(chapter), f"chapter {chapter} 无样本"


def test_every_curated_sample_is_valid_and_diffable() -> None:
    # 每个样本：6-14 行、buggy/fixed 仅 bug 行不同、行级 diff 至少一行、均可 compile()。
    for sample in all_samples():
        problems = validate_sample(sample)
        assert not problems, f"{sample.tag}: {problems}"
        buggy_lines = sample.buggy_code.split("\n")
        fixed_lines = sample.fixed_code.split("\n")
        assert 6 <= len(buggy_lines) <= 14
        assert len(buggy_lines) == len(fixed_lines)
        indices = bug_line_indices(sample.buggy_code, sample.fixed_code)
        assert indices  # 至少一行 bug
        per_line_diff = {
            i for i in range(len(buggy_lines)) if buggy_lines[i] != fixed_lines[i]
        }
        assert set(indices) == per_line_diff  # 仅在 bug 行不同（其余逐字一致）
        compile(sample.buggy_code, f"<buggy:{sample.tag}>", "exec")
        compile(sample.fixed_code, f"<fixed:{sample.tag}>", "exec")
    # re-export 的 bug_line_indices 与库内实现是同一函数。
    assert bug_line_indices is bank_bug_line_indices


def test_samples_for_tag_and_chapter_filters() -> None:
    off = samples_for_tag("off_by_one_range")
    assert off and all(s.tag == "off_by_one_range" for s in off)
    assert len(off) >= 2  # 同一类型多个变体 → 「换一局」有变化
    ch5 = samples_for_chapter(5)
    assert ch5 and all(s.chapter == 5 for s in ch5)
    assert samples_for_tag("definitely_not_a_real_tag") == []


async def test_expose_bug_uses_bank_for_known_tag() -> None:
    # 真实误解 tag 命中 bug 库 → 直接出库样本（不触达 LLM，RaisingProvider 证明），
    # seed 注入使变体确定化。
    out = await FeynmanTuteeAgent(RaisingProvider(), rng=random.Random(7)).expose_bug(
        FeynmanBugExposureInput(
            kc_code="PY43-KC-021",
            misconceptions=[_misconception("mutable_default_arg")],
        )
    )

    assert out.source == "real_misconception"
    assert out.misconception_tag == "mutable_default_arg"
    assert out.bug_line_indices == bug_line_indices(out.buggy_code or "", out.fixed_code or "")
    assert out.bug_line_indices
    # 出库样本同样满足「可 compile + 仅 bug 行不同」不变量。
    compile(out.buggy_code or "", "<buggy>", "exec")
    compile(out.fixed_code or "", "<fixed>", "exec")


async def test_expose_bug_known_tag_variants_are_seed_dependent() -> None:
    # 不同 seed 可抽到 off_by_one_range 的不同变体（库里有多个变体时，「换一局」才有变化）。
    async def pick(seed: int) -> str:
        out = await FeynmanTuteeAgent(RaisingProvider(), rng=random.Random(seed)).expose_bug(
            FeynmanBugExposureInput(
                kc_code="PY43-KC-031",
                misconceptions=[_misconception("off_by_one_range")],
            )
        )
        return out.buggy_code or ""

    variants = {await pick(seed) for seed in range(12)}
    assert len(variants) >= 2  # 至少能抽到两个不同变体


def test_generated_json_loader_is_failsoft(tmp_path) -> None:  # type: ignore[no-untyped-def]
    # 缺文件 → []；损坏 JSON → []；不存在的目录 → []，绝不抛。
    assert bug_bank_mod._load_generated(tmp_path / "nope.json") == []
    garbage = tmp_path / "garbage.json"
    garbage.write_text("{not json", encoding="utf-8")
    assert bug_bank_mod._load_generated(garbage) == []


def test_generated_json_loader_validates_and_augments(tmp_path) -> None:  # type: ignore[no-untyped-def]
    # 合法样本被载入；不合格样本（过短/不 diff）被丢弃——只融合可玩样本。
    good = {
        "tag": "off_by_one_range",
        "chapter": 4,
        "topic": "range 边界",
        "difficulty": "easy",
        "buggy_code": "# sum 1..5\ntotal = 0\nfor i in range(1, 5):\n    total += i\nprint(\"15?\")\nprint(total)",
        "fixed_code": "# sum 1..5\ntotal = 0\nfor i in range(1, 6):\n    total += i\nprint(\"15?\")\nprint(total)",
        "hint_md": "range 右端点？",
    }
    too_short = {**good, "buggy_code": "a = 1\nb = 2", "fixed_code": "a = 1\nb = 3"}
    not_diff = {**good, "fixed_code": good["buggy_code"]}  # buggy == fixed
    path = tmp_path / "feynman_bug_bank.generated.json"
    import json as _json

    path.write_text(
        _json.dumps({"samples": [good, too_short, not_diff]}, ensure_ascii=False),
        encoding="utf-8",
    )
    loaded = bug_bank_mod._load_generated(path)
    assert len(loaded) == 1
    assert loaded[0].tag == "off_by_one_range"
    assert not validate_sample(loaded[0])
