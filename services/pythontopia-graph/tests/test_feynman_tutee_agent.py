from __future__ import annotations

from collections.abc import AsyncIterator

from fastapi.testclient import TestClient

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
    provider = JsonProvider(
        {
            "reply_md": "我以为 range(1, 5) 会包含 5，所以循环次数会多一次。",
            "updated_misconceptions": [
                {"tag": "off_by_one_range", "evidence_event_ids": [501], "resolved": False}
            ],
            "produced_code": "for i in range(1, 5):\n    print(i)",
        }
    )

    out = await FeynmanTuteeAgent(provider).step(_input())

    assert "包含 5" in out.reply_md
    assert out.updated_misconceptions[0].tag == "off_by_one_range"
    assert out.updated_misconceptions[0].resolved is False
    assert "range" in (out.produced_code or "")


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

    assert "range" in out.reply_md
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


async def test_initial_step_forces_misconception_unresolved() -> None:
    # 初始暴露阶段（无教学）即使 LLM 误标 resolved=true，也必须被强制改回 false。
    provider = JsonProvider(
        {
            "reply_md": "我以为 range(1, 5) 会包含 5。",
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
            "reply_md": "我以为 input() 得到的就是数字，所以直接相加。",
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
    assert response.json()["updated_misconceptions"][0]["tag"] == "input_type_string"
    del app.state.llm_provider


# ── 代码找茬（bug-exposure） ──────────────────────────────────────────────

def test_bug_line_indices_diffs_replaced_line() -> None:
    buggy = "a = [1, 2]\nb = a\nb.append(3)\nprint(a)"
    fixed = "a = [1, 2]\nb = a.copy()\nb.append(3)\nprint(a)"
    assert bug_line_indices(buggy, fixed) == [1]


async def test_expose_bug_canned_tag_marks_real_bug_line() -> None:
    # canned tag 走确定性样本，不应触达 LLM（用 RaisingProvider 兜底证明）。
    out = await FeynmanTuteeAgent(RaisingProvider()).expose_bug(
        FeynmanBugExposureInput(
            kc_code="PY43-KC-021",
            misconceptions=[_misconception("off_by_one_range")],
        )
    )

    assert out.source == "real_misconception"
    assert out.misconception_tag == "off_by_one_range"
    assert "range(1, 5)" in (out.buggy_code or "")
    assert out.bug_line_indices == [0]
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


def test_canned_tag_for_kc_maps_chapter_to_relevant_bug() -> None:
    # 兜底 bug 不再按 kc_code 哈希乱挑，而是按章节主题挑：循环 / 数据结构 / 类型各取对应陷阱。
    pick = FeynmanTuteeAgent._canned_tag_for_kc
    assert pick("PY43-KC-031") == "off_by_one_range"  # 第四章 流程自动化（for 循环）
    assert pick("PY43-KC-040") == "list_alias_mutation"  # 第五章 数据结构（列表）
    assert pick("PY43-KC-013") == "input_type_string"  # 第二章 控制台输入与类型转换
    assert pick("PY43-KC-019") == "input_type_string"  # 第三章 数值类型与运算
    assert pick("PY43-KC-999") == "off_by_one_range"  # 未知 KC 回退最通用陷阱


async def test_generic_fallback_is_chapter_relevant() -> None:
    # 选「for 循环与遍历」(KC-031, 第四章) 无样本时，兜底应给「循环边界」bug 而非随机类型陷阱。
    out = await FeynmanTuteeAgent(RaisingProvider()).expose_bug(
        FeynmanBugExposureInput(kc_code="PY43-KC-031", misconceptions=[])
    )

    assert out.source == "generic_beginner_bug"
    assert out.misconception_tag == "off_by_one_range"
    assert "range(1, 5)" in (out.buggy_code or "")


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
    assert payload["bug_line_indices"] == [0]
