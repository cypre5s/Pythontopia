"""P1-dspy-compile 测试矩阵（§3.5 + §4 Stream P 第 1-2+4-5 条 · TDD）。

覆盖（全部不依赖真实 dspy 安装，缺包仍须跑绿；真 dspy 分支用 importorskip）：

1. programs 结构化签名输出与 GuideCoachDecision / PRACTICE schema 对齐。
2. compile 冒烟（mock 优化器替身、小 trainset）能导出 ``artifacts/{name}.json``，
   且导出产物可被兄弟单元 P2 的 ``optimize/loader.load_compiled`` 直接解析（契约对账）。
3. 缺 dspy 包 → 清晰降级（OptimizeDependencyError，不静默、不崩主链路）。
4. GEPA 反馈型 metric：复用 golden（导学）+ grounding/safety（出题）作 feedback。
5. 离线 compile 仅经 scripts/优化器替身跑；运行时绝不触发（本测试从不调用真实 GEPA）。
"""

from __future__ import annotations

import json
import sys
from types import SimpleNamespace
from typing import Any

import pytest

from pythontopia_graph.optimize import compile as compile_mod
from pythontopia_graph.optimize import programs
from pythontopia_graph.schemas.tutor_workflow import GuideCoachDecision


# ─────────────────────────────────────────────────────────────────────────────
# 测试替身：模拟「已编译 dspy 程序」与「优化器」，使 compile 导出管线在无 dspy 下可测。
# program_to_artifact 仅依赖 named_predictors() / signature.{instructions,input_fields,
# output_fields} / predictor.demos 这一窄鸭子接口，故替身可零依赖复现。
# ─────────────────────────────────────────────────────────────────────────────


class _FakeSignature:
    instructions = "INSTR"
    # 顺序即导出顺序（dict 在 3.7+ 保序）。
    input_fields = {"requested_event": object(), "allowed_events": object()}
    output_fields = {"next_event": object(), "reason_summary": object()}


class _FakePredictor:
    def __init__(self) -> None:
        self.signature = _FakeSignature()
        # demo 用纯 dict（Mapping 分支）；含一个签名外的 extra 字段，应被丢弃。
        self.demos = [
            {
                "requested_event": "CHAT",
                "allowed_events": ["CHAT"],
                "next_event": "CHAT",
                "reason_summary": "ok",
                "extra": "should_be_dropped",
            }
        ]


class _FakeProgram:
    def named_predictors(self) -> list[tuple[str, _FakePredictor]]:
        return [("decide", _FakePredictor())]


class _FakeOptimizer:
    """替身 GEPA：记录 compile 入参，原样返回 student（不触碰任何 LM）。"""

    def __init__(self) -> None:
        self.calls: list[tuple[Any, Any]] = []

    def compile(self, student: Any, *, trainset: Any, valset: Any = None, **_: Any) -> Any:
        self.calls.append((trainset, valset))
        return student


# ─────────────────────────────────────────────────────────────────────────────
# 1. programs 结构化签名输出与 schema 对齐（pure，无 dspy）
# ─────────────────────────────────────────────────────────────────────────────


def test_program_names_frozen() -> None:
    assert programs.PROGRAM_NAMES == ("guide_coach_decision", "practice_generate")


def test_guide_coach_output_fields_subset_of_decision_schema() -> None:
    out_names = {spec.name for spec in programs.GUIDE_COACH_OUTPUT_FIELDS}
    assert out_names <= set(GuideCoachDecision.model_fields)
    # 核心决策字段必须出现（结构化对齐，不退化成自由文本）。
    assert {
        "next_event",
        "tool_name",
        "hint_level",
        "action_goal",
        "stop_condition",
        "reason_summary",
        "confidence",
    } <= out_names


def test_guide_coach_input_fields_cover_decision_context() -> None:
    in_names = {spec.name for spec in programs.GUIDE_COACH_INPUT_FIELDS}
    assert {"requested_event", "allowed_events"} <= in_names


def test_practice_fields_align_with_practice_content() -> None:
    out_names = {spec.name for spec in programs.PRACTICE_OUTPUT_FIELDS}
    assert "problems" in out_names
    in_names = {spec.name for spec in programs.PRACTICE_INPUT_FIELDS}
    assert {"kc_codes"} <= in_names


def test_build_program_rejects_unknown_name() -> None:
    with pytest.raises(ValueError, match="unknown program"):
        programs.build_program("nope")


# ─────────────────────────────────────────────────────────────────────────────
# 2. 产物形状 + 导出管线 + 与 P2 loader 契约对账
# ─────────────────────────────────────────────────────────────────────────────


def test_build_artifact_has_loader_contract_shape() -> None:
    art = programs.build_artifact(
        "guide_coach_decision",
        instruction="sys",
        demos=[{"role": "user", "content": "{}"}],
    )
    assert art["name"] == "guide_coach_decision"
    assert art["instruction"] == "sys"
    assert isinstance(art["demos"], list)
    assert art["schema_version"] >= 1


def test_program_to_artifact_extracts_instruction_and_chat_demos() -> None:
    art = programs.program_to_artifact("guide_coach_decision", _FakeProgram())
    assert art["instruction"] == "INSTR"
    # demos 为成对的 chat message（user=inputs / assistant=outputs），与 P2 loader 推荐格式一致。
    demos = art["demos"]
    assert [d["role"] for d in demos] == ["user", "assistant"]
    assert json.loads(demos[0]["content"]) == {
        "requested_event": "CHAT",
        "allowed_events": ["CHAT"],
    }
    assert json.loads(demos[1]["content"]) == {"next_event": "CHAT", "reason_summary": "ok"}
    # 签名外字段不得泄漏进 demo。
    assert "extra" not in demos[0]["content"]
    assert "extra" not in demos[1]["content"]


def test_write_read_artifact_roundtrip(tmp_path: Any) -> None:
    art = programs.build_artifact("practice_generate", instruction="i", demos=[])
    path = compile_mod.write_artifact(art, tmp_path / "practice_generate.json")
    assert path.exists()
    assert compile_mod.read_artifact(path) == art


def test_artifact_path_defaults_into_optimize_artifacts() -> None:
    path = compile_mod.artifact_path("practice_generate")
    assert path.name == "practice_generate.json"
    assert path.parent.name == "artifacts"
    assert path.parent.parent.name == "optimize"
    # .gitkeep 保证目录存在。
    assert path.parent.is_dir()


@pytest.mark.parametrize("name", ["guide_coach_decision", "practice_generate"])
def test_compile_and_export_smoke_writes_artifact(name: str, tmp_path: Any) -> None:
    optimizer = _FakeOptimizer()
    path = compile_mod.compile_and_export(
        name,
        program=_FakeProgram(),
        optimizer=optimizer,
        trainset=[{"x": 1}],
        valset=[{"x": 1}],
        artifacts_dir=tmp_path,
    )
    assert path == tmp_path / f"{name}.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    assert data["name"] == name
    assert data["instruction"] == "INSTR"
    assert isinstance(data["demos"], list) and data["demos"]
    assert data["metadata"]["optimizer"] == "GEPA"
    # 优化器确被调用且拿到 trainset/valset（离线 compile 入口连通）。
    assert optimizer.calls == [([{"x": 1}], [{"x": 1}])]


@pytest.mark.parametrize("name", ["guide_coach_decision", "practice_generate"])
def test_exported_artifact_loadable_by_p2_loader(name: str, tmp_path: Any) -> None:
    """P1 导出 → P2 loader 解析的端到端契约对账（loader 只读引用，不修改）。"""
    from pythontopia_graph.optimize import loader

    compile_mod.compile_and_export(
        name,
        program=_FakeProgram(),
        optimizer=_FakeOptimizer(),
        trainset=[{}],
        valset=[{}],
        artifacts_dir=tmp_path,
    )
    compiled = loader.load_compiled(name, artifacts_dir=tmp_path)
    assert compiled is not None
    assert compiled.instruction == "INSTR"
    assert isinstance(compiled.demos, list) and compiled.demos
    assert all("role" in d and "content" in d for d in compiled.demos)


# ─────────────────────────────────────────────────────────────────────────────
# 3. 缺 dspy → 清晰降级（强制把 dspy 设为 None，env 有无 dspy 行为一致）
# ─────────────────────────────────────────────────────────────────────────────


def test_build_program_without_dspy_raises_clear_error(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setitem(sys.modules, "dspy", None)
    with pytest.raises(programs.OptimizeDependencyError) as excinfo:
        programs.build_program("guide_coach_decision")
    message = str(excinfo.value).lower()
    assert "dspy" in message
    assert "optimize" in message  # 提示安装 .[optimize] extra


def test_build_signature_without_dspy_raises(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setitem(sys.modules, "dspy", None)
    with pytest.raises(programs.OptimizeDependencyError):
        programs.build_practice_signature()


def test_build_gepa_without_dspy_raises(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setitem(sys.modules, "dspy", None)
    with pytest.raises(programs.OptimizeDependencyError):
        compile_mod.build_gepa(metric=lambda *a, **k: 0.0, reflection_lm=object())


# ─────────────────────────────────────────────────────────────────────────────
# 4. GEPA 反馈型 metric — 导学（golden）
# ─────────────────────────────────────────────────────────────────────────────


def test_guide_coach_scenarios_nonempty_with_label_fields() -> None:
    scenarios = compile_mod.guide_coach_scenarios()
    assert scenarios
    sample = scenarios[0]
    assert "expected_events" in sample and "forbidden_events" in sample


def test_guide_coach_metric_rewards_expected_event() -> None:
    metric = compile_mod.make_guide_coach_metric()
    gold = {"expected_events": ["EXECUTION_TRACE", "ERROR_FEEDBACK"], "forbidden_events": ["SKELETON"]}
    pred = SimpleNamespace(next_event="ERROR_FEEDBACK", reason_summary="先定位错因。", hint_level=1)
    result = metric(gold, pred)
    assert 0.0 <= result.score <= 1.0
    assert result.score >= 0.9
    assert result.feedback  # 反馈型 metric：必须带文本反馈供 GEPA 反思


def test_guide_coach_metric_zero_on_forbidden_event() -> None:
    metric = compile_mod.make_guide_coach_metric()
    gold = {"expected_events": ["ERROR_FEEDBACK"], "forbidden_events": ["SKELETON", "TRANSFER"]}
    pred = SimpleNamespace(next_event="SKELETON", reason_summary="给骨架。", hint_level=1)
    result = metric(gold, pred)
    assert result.score == 0.0
    assert "forbidden" in result.feedback.lower() or "禁止" in result.feedback


def test_guide_coach_metric_partial_when_offlist_but_not_forbidden() -> None:
    score, feedback = compile_mod.score_guide_coach(
        {"expected_events": ["ERROR_FEEDBACK"], "forbidden_events": ["SKELETON"]},
        SimpleNamespace(next_event="CHAT", reason_summary="先聊聊。", hint_level=1),
    )
    assert 0.0 < score < 1.0
    assert feedback


def test_guide_coach_metric_zero_on_chain_of_thought_leak() -> None:
    metric = compile_mod.make_guide_coach_metric()
    gold = {"expected_events": ["ERROR_FEEDBACK"], "forbidden_events": []}
    leaked = SimpleNamespace(
        next_event="ERROR_FEEDBACK",
        reason_summary="chain-of-thought: step1 then step2",
        hint_level=1,
    )
    assert metric(gold, leaked).score == 0.0
    leaked_zh = SimpleNamespace(
        next_event="ERROR_FEEDBACK", reason_summary="逐步推理：先……再……", hint_level=1
    )
    assert metric(gold, leaked_zh).score == 0.0


def test_guide_coach_metric_penalizes_missing_reason() -> None:
    score, _ = compile_mod.score_guide_coach(
        {"expected_events": ["ERROR_FEEDBACK"], "forbidden_events": []},
        SimpleNamespace(next_event="ERROR_FEEDBACK", reason_summary="", hint_level=1),
    )
    assert score < 1.0


# ─────────────────────────────────────────────────────────────────────────────
# 4'. GEPA 反馈型 metric — 出题（grounding/safety）
# ─────────────────────────────────────────────────────────────────────────────

_GOOD_PROBLEM = {
    "title": "练习：循环求和",
    "statement_md": "围绕循环知识点，读入 n 并输出 1..n 的和，请独立完成。",
    "hints": ["先想清楚循环边界"],
    "sample_io": [{"input": "3", "output": "6"}],
    "kc_codes": ["PY43-KC-016"],
    "difficulty": "basic",
}


def test_practice_metric_rewards_safe_grounded_problem() -> None:
    metric = compile_mod.make_practice_metric()
    gold = {"kc_codes": ["PY43-KC-016"], "target_difficulty": "basic"}
    pred = SimpleNamespace(problems=[dict(_GOOD_PROBLEM)])
    result = metric(gold, pred)
    assert result.score >= 0.9
    assert result.feedback


def test_practice_metric_zero_on_answer_leak() -> None:
    metric = compile_mod.make_practice_metric()
    gold = {"kc_codes": ["PY43-KC-016"], "target_difficulty": "basic"}
    leaked = dict(_GOOD_PROBLEM, hints=["完整答案：print(sum(range(1, n + 1)))"])
    result = metric(gold, SimpleNamespace(problems=[leaked]))
    assert result.score == 0.0
    assert result.feedback


def test_practice_metric_zero_on_schema_violation() -> None:
    score, feedback = compile_mod.score_practice(
        {"kc_codes": ["PY43-KC-016"]},
        SimpleNamespace(problems=[{"title": "x"}]),  # 缺 statement_md / sample_io
    )
    assert score == 0.0
    assert "schema" in feedback.lower() or "字段" in feedback


def test_metric_callables_accept_gepa_signature() -> None:
    """metric 必须兼容 GEPA 调用签名 (gold, pred, trace, pred_name, pred_trace)。"""
    metric = compile_mod.make_guide_coach_metric()
    out = metric(
        {"expected_events": ["CHAT"], "forbidden_events": []},
        SimpleNamespace(next_event="CHAT", reason_summary="聊。", hint_level=0),
        None,
        "decide",
        None,
    )
    assert hasattr(out, "score") and hasattr(out, "feedback")


def test_default_metric_dispatch() -> None:
    assert callable(compile_mod.default_metric("guide_coach_decision"))
    assert callable(compile_mod.default_metric("practice_generate"))
    with pytest.raises(ValueError):
        compile_mod.default_metric("nope")


# ─────────────────────────────────────────────────────────────────────────────
# 5. 真 dspy 分支（未安装则整组跳过；仅作结构校验，不跑真 GEPA）
# ─────────────────────────────────────────────────────────────────────────────


def test_build_signatures_with_dspy_align_schema() -> None:
    pytest.importorskip("dspy")
    sig = programs.build_guide_coach_signature()
    assert set(sig.output_fields) == {spec.name for spec in programs.GUIDE_COACH_OUTPUT_FIELDS}
    practice_sig = programs.build_practice_signature()
    assert "problems" in practice_sig.output_fields


def test_build_program_with_dspy_has_single_predictor() -> None:
    pytest.importorskip("dspy")
    program = programs.build_program("guide_coach_decision")
    predictors = list(program.named_predictors())
    assert len(predictors) == 1
