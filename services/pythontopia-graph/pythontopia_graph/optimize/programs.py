"""DSPy 程序定义 + 冻结产物形状（P1-dspy-compile · §3.5 / §4 Stream P 第 1 条）。

把既有手写 prompt 的两类决策包成 **结构化签名** 的 ``dspy.Module``，供离线 GEPA 编译：

- ``guide_coach_decision``：AI 导学助手的下一步教学动作选择，输出对齐
  :class:`pythontopia_graph.schemas.tutor_workflow.GuideCoachDecision`。
- ``practice_generate``：百晓工坊的练习题生成，输出对齐
  :class:`pythontopia_graph.schemas.resource.PracticeContent`（``problems``）。

设计红线（§0 / §3.5）：

- **dspy lazy-import**：本模块导入零依赖 dspy；只有真正构造签名 / 程序时才 ``import dspy``，
  缺包则抛 :class:`OptimizeDependencyError`（清晰降级，不静默、不崩主链路）。
- **离线 only**：本模块只负责「定义程序 + 产物形状」；真正的 GEPA 编译在
  :mod:`pythontopia_graph.optimize.compile` 且仅经 ``scripts/optimize_*.py`` 离线触发，
  运行时绝不调用。
- **产物契约**与兄弟单元 P2 ``optimize/loader.py`` 同源：顶层 ``instruction`` (str) +
  ``demos`` (list[dict]，推荐 chat message ``{"role","content"}``) + ``metadata``。
"""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass
from typing import Any

import json

from pydantic import BaseModel

from pythontopia_graph.schemas.resource import ProblemSpec

# 冻结：仅这两个程序名（与 P2 ``loader.COMPILED_PROMPT_NAMES`` 对账）。
PROGRAM_NAMES: tuple[str, ...] = ("guide_coach_decision", "practice_generate")

# 产物 JSON schema 版本（向后兼容演进用；loader 只取 instruction/demos/metadata，忽略其余）。
ARTIFACT_SCHEMA_VERSION = 1


class OptimizeDependencyError(RuntimeError):
    """离线优化依赖（dspy）缺失。清晰降级：仅离线 scripts 需要，运行时永不触发。"""


@dataclass(frozen=True)
class FieldSpec:
    """签名字段规格（单一事实源：既驱动 dspy 签名构造，也供测试做 schema 对齐校验）。"""

    name: str
    desc: str
    annotation: Any = str
    role: str = "input"  # "input" | "output"


# ─────────────────────────────────────────────────────────────────────────────
# guide_coach_decision 签名（对齐 GuideCoachDecision + GuideCoachAgent 决策上下文）
# ─────────────────────────────────────────────────────────────────────────────

GUIDE_COACH_INSTRUCTION = (
    "你是 Pythontopia 的 GuideCoachAgent（AI 导学助手的有界管理者）。"
    "只在 allowed_events 白名单内，为学生选择下一步教育动作；不生成导学卡、不改学生代码、"
    "不绕过工作流阶段合法性。优先依据判题 verdict 与连错次数选择最省认知成本的动作："
    "CE→错因诊断；WA 早期→执行轨迹/第一层错因；连错≥3→降负担（可视化/拼装/复盘）；AC→先复盘再迁移。"
    "严禁输出完整可提交代码、隐藏测试、原始 chain-of-thought 或逐步隐性推理；"
    "reason_summary 只写一句可审计摘要。"
)

GUIDE_COACH_INPUT_FIELDS: tuple[FieldSpec, ...] = (
    FieldSpec("requested_event", "学生请求的工作流事件（如 ERROR_FEEDBACK / AC_REVIEW）", str),
    FieldSpec("allowed_events", "本步合法的事件白名单；只能从中选择 next_event", list),
    FieldSpec("student_state", "学生状态快照（阶段/连错/提示等级/挫败度等）", dict),
    FieldSpec("problem", "题目上下文（标题/题面/KC 等）", dict),
    FieldSpec("diagnosis", "判题证据（verdict/通过用例/连错次数等）；可为空", dict),
    FieldSpec("profile_brief", "学生画像简述（个性化策略提示）", str),
    FieldSpec("recent_cards", "近期已出导学卡（避免重复堆叠提示）", list),
)

GUIDE_COACH_OUTPUT_FIELDS: tuple[FieldSpec, ...] = (
    FieldSpec("next_event", "从 allowed_events 中选定的下一步事件", str, role="output"),
    FieldSpec("tool_name", "该事件对应的工具名（如 diagnose_submission）", str, role="output"),
    FieldSpec("hint_level", "提示强度 0..3（越大越具体，禁直接给完整答案）", int, role="output"),
    FieldSpec("diagnosis_label", "错误归类标签（如 compile_error/wrong_answer），无则空", str, role="output"),
    FieldSpec("student_state_label", "学生状态标签（如 repeated_error/normal）", str, role="output"),
    FieldSpec("action_goal", "该动作的教学目标（一句话）", str, role="output"),
    FieldSpec("stop_condition", "判断本步完成的可观测停止条件", str, role="output"),
    FieldSpec("confidence", "对该决策的置信度 0..1", float, role="output"),
    FieldSpec("reason_summary", "一句可审计摘要；禁原始 CoT / 逐步推理", str, role="output"),
)


# ─────────────────────────────────────────────────────────────────────────────
# practice_generate 签名（对齐 PracticeContent + PracticeAgent 出题约束）
# ─────────────────────────────────────────────────────────────────────────────

PRACTICE_INSTRUCTION = (
    "你是 Pythontopia 百晓工坊的 PracticeAgent。围绕给定 KC 生成 1-3 道 Python 练习题，"
    "难度对齐 target_difficulty（弱→basic 建信心，临界→hard 促成长）。每题必须含 sample_io，"
    "可在 hints 给思路提示。严禁输出 hidden_test_cases / answer_key / secret_test / 完整答案 / "
    "标准答案等任何可直接提交的解；只产出题面与提示，不泄答。"
)

PRACTICE_INPUT_FIELDS: tuple[FieldSpec, ...] = (
    FieldSpec("kc_codes", "目标知识点编码列表（如 PY43-KC-016）", list),
    FieldSpec("target_difficulty", "目标难度 basic/medium/hard；可为空", str),
    FieldSpec("profile_snapshot", "学生画像快照（专业/自评等级/认知风格/易错点）", dict),
    FieldSpec("evidence_context", "课件证据上下文摘要（带页标签，仅供取材，不得伪造页码）", str),
    FieldSpec("focus_hint", "出题侧重提示；可为空", str),
    FieldSpec("agent_strategy_directive", "团队/自进化策略提示；可为空", str),
)

PRACTICE_OUTPUT_FIELDS: tuple[FieldSpec, ...] = (
    FieldSpec(
        "problems",
        "1-3 道题；每题含 title/statement_md/hints/sample_io/kc_codes/difficulty，禁泄答",
        list[ProblemSpec],
        role="output",
    ),
)


_SIGNATURE_SPECS: dict[str, tuple[str, tuple[FieldSpec, ...], tuple[FieldSpec, ...]]] = {
    "guide_coach_decision": (
        GUIDE_COACH_INSTRUCTION,
        GUIDE_COACH_INPUT_FIELDS,
        GUIDE_COACH_OUTPUT_FIELDS,
    ),
    "practice_generate": (
        PRACTICE_INSTRUCTION,
        PRACTICE_INPUT_FIELDS,
        PRACTICE_OUTPUT_FIELDS,
    ),
}


def _require_dspy() -> Any:
    """惰性导入 dspy；缺包 → :class:`OptimizeDependencyError`（清晰降级）。"""
    try:
        import dspy  # type: ignore[import-not-found]  # noqa: PLC0415
    except ImportError as exc:  # pragma: no cover - 真 dspy 环境不会进入
        raise OptimizeDependencyError(
            "DSPy 未安装：离线 GEPA 编译需 `pip install -e '.[optimize]'`（dspy>=3.2,<4）。"
            "运行时无需 dspy（provider 经 optimize/loader.py 读冻结产物即可）。"
        ) from exc
    return dspy


def _build_signature(name: str) -> Any:
    """据 :data:`_SIGNATURE_SPECS` 程序化构造 dspy.Signature（typed inputs/outputs）。"""
    if name not in _SIGNATURE_SPECS:
        raise ValueError(f"unknown program: {name}")
    dspy = _require_dspy()
    instruction, input_specs, output_specs = _SIGNATURE_SPECS[name]
    fields: dict[str, Any] = {}
    for spec in input_specs:
        fields[spec.name] = (spec.annotation, dspy.InputField(desc=spec.desc))
    for spec in output_specs:
        fields[spec.name] = (spec.annotation, dspy.OutputField(desc=spec.desc))
    return dspy.Signature(fields, instruction)


def build_guide_coach_signature() -> Any:
    """构造 guide_coach_decision 的 dspy.Signature（需 dspy）。"""
    return _build_signature("guide_coach_decision")


def build_practice_signature() -> Any:
    """构造 practice_generate 的 dspy.Signature（需 dspy）。"""
    return _build_signature("practice_generate")


def build_program(name: str) -> Any:
    """构造单 predictor 的 ``dspy.Module``（需 dspy；离线编译用，运行时绝不调用）。

    使用单一 :class:`dspy.Predict`，使 GEPA 优化目标聚焦于「指令 + few-shot demos」，
    导出后由 :func:`program_to_artifact` 抽取为 ``{instruction, demos}`` 冻结产物。
    """
    if name not in PROGRAM_NAMES:
        raise ValueError(f"unknown program: {name}")
    dspy = _require_dspy()
    signature = _build_signature(name)

    class _CompiledProgram(dspy.Module):  # type: ignore[misc, name-defined]
        def __init__(self) -> None:
            super().__init__()
            self.predict = dspy.Predict(signature)

        def forward(self, **kwargs: Any) -> Any:
            return self.predict(**kwargs)

    program = _CompiledProgram()
    program.__class__.__name__ = f"{name}_program"
    return program


# ─────────────────────────────────────────────────────────────────────────────
# 冻结产物形状（与 P2 loader 契约同源；纯 Python，无 dspy 依赖）
# ─────────────────────────────────────────────────────────────────────────────


def build_artifact(
    name: str,
    *,
    instruction: str,
    demos: list[dict[str, Any]],
    signature: dict[str, Any] | None = None,
    metadata: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """构造规范产物 dict（顶层 instruction + demos + metadata；loader 直接可解析）。"""
    return {
        "schema_version": ARTIFACT_SCHEMA_VERSION,
        "name": name,
        "instruction": instruction or "",
        "demos": list(demos or []),
        "signature": signature or {},
        "metadata": metadata or {},
    }


def program_to_artifact(
    name: str, program: Any, *, metadata: dict[str, Any] | None = None
) -> dict[str, Any]:
    """从（已编译的）dspy 程序抽取 ``{instruction, demos}`` 规范产物。

    仅依赖窄鸭子接口（``named_predictors`` / ``signature.instructions`` /
    ``signature.{input,output}_fields`` / ``predictor.demos``），故测试替身可零依赖复现。
    demos 导出为成对 chat message（user=inputs / assistant=outputs），与 P2 loader 推荐格式一致。
    """
    predictors = list(program.named_predictors())
    if not predictors:
        raise ValueError(f"program {name!r} has no predictors to export")
    pred_name, predictor = predictors[0]
    signature = predictor.signature
    instruction = str(getattr(signature, "instructions", "") or "")
    input_keys = list(getattr(signature, "input_fields", {}) or {})
    output_keys = list(getattr(signature, "output_fields", {}) or {})
    raw_demos = list(getattr(predictor, "demos", []) or [])
    demos: list[dict[str, Any]] = []
    for demo in raw_demos:
        demos.extend(_demo_to_messages(demo, input_keys, output_keys))
    sig_meta = {"predictor": pred_name, "inputs": input_keys, "outputs": output_keys}
    md = {"demo_count": len(demos), "predictor_count": len(predictors)}
    if metadata:
        md.update(metadata)
    return build_artifact(name, instruction=instruction, demos=demos, signature=sig_meta, metadata=md)


def _demo_to_messages(
    demo: Any, input_keys: list[str], output_keys: list[str]
) -> list[dict[str, str]]:
    data = _demo_data(demo)
    inputs = {key: _jsonify(data[key]) for key in input_keys if key in data}
    outputs = {key: _jsonify(data[key]) for key in output_keys if key in data}
    messages: list[dict[str, str]] = []
    if inputs:
        messages.append({"role": "user", "content": json.dumps(inputs, ensure_ascii=False)})
    if outputs:
        messages.append(
            {"role": "assistant", "content": json.dumps(outputs, ensure_ascii=False)}
        )
    return messages


def _demo_data(demo: Any) -> dict[str, Any]:
    to_dict = getattr(demo, "toDict", None)
    if callable(to_dict):
        try:
            return dict(to_dict())
        except Exception:  # noqa: BLE001 - 鸭子接口兜底，转下个分支
            pass
    if isinstance(demo, Mapping):
        return dict(demo)
    store = getattr(demo, "_store", None)
    if isinstance(store, Mapping):
        return dict(store)
    if hasattr(demo, "__dict__"):
        return dict(vars(demo))
    return {}


def _jsonify(value: Any) -> Any:
    if isinstance(value, BaseModel):
        return value.model_dump(mode="json")
    if isinstance(value, Mapping):
        return {str(key): _jsonify(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_jsonify(item) for item in value]
    if isinstance(value, (str, int, float, bool)) or value is None:
        return value
    return str(value)
