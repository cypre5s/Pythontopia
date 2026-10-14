"""GEPA 离线编译 + 反馈型 metric + 产物导出（P1-dspy-compile · §3.5 / §4 Stream P 第 2 条）。

职责：
1. **反馈型 metric**（GEPA 核心）：返回 ``score + feedback``，供 GEPA 反思改写指令。
   - guide_coach：复用 ``test_guide_coach_golden`` 的 golden 期望（expected/forbidden 事件、
     hint_level 上界、reason_summary 禁 CoT）作监督信号。
   - practice：复用既有 ``grounding_critic`` / ``safety_critic`` 作 feedback（防泄答 / 防幻觉）。
2. **trainset 构造**：从 golden fixture / 内置样本生成 dspy.Example（需 dspy；离线 scripts 用）。
3. **GEPA 编译 + 导出**：跑 ``dspy.GEPA(...).compile(...)``，把优化后的程序经
   :func:`pythontopia_graph.optimize.programs.program_to_artifact` 冻结到 ``artifacts/{name}.json``。

红线：**离线 only**——本模块仅经 ``scripts/optimize_*.py`` 触发；运行时绝不 import/调用。
metric 与 trainset 的 *纯逻辑* 不依赖 dspy（可单测）；dspy 仅在构造 Example/GEPA 时惰性导入。
"""

from __future__ import annotations

import json
import os
from urllib.parse import urlsplit
from collections.abc import Callable, Mapping
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle, RagHit
from pythontopia_graph.nodes.grounding_critic import grounding_critic
from pythontopia_graph.nodes.safety_critic import safety_critic
from pythontopia_graph.llm.provider import DEEPSEEK_CHAT_MODELS
from pythontopia_graph.optimize.programs import (
    OptimizeDependencyError,
    PROGRAM_NAMES,
    ARTIFACT_SCHEMA_VERSION,
    _require_dspy,
    build_program,
    program_to_artifact,
)
from pythontopia_graph.schemas.resource import PracticeContent, ResourceDraft

_ARTIFACTS_DIR = Path(__file__).resolve().parent / "artifacts"
_GOLDEN_PATH = (
    Path(__file__).resolve().parents[2] / "tests" / "fixtures" / "guide_coach_golden.json"
)

# reason_summary 禁泄漏的原始推理痕迹标记（§0 红线 + guide_coach 既有约束）。
_COT_MARKERS: tuple[str, ...] = (
    "chain-of-thought",
    "chain of thought",
    "hidden reasoning",
    "思维链",
    "逐步推理",
)


# ─────────────────────────────────────────────────────────────────────────────
# 反馈型分数容器（dspy 在场→dspy.Prediction；缺 dspy→本地 dataclass，便于单测）
# ─────────────────────────────────────────────────────────────────────────────


@dataclass(frozen=True)
class ScoreWithFeedback:
    """GEPA 反馈型 metric 返回值（``score`` 浮点 + ``feedback`` 文本）。"""

    score: float
    feedback: str


def _score_with_feedback(score: float, feedback: str) -> Any:
    score = max(0.0, min(1.0, float(score)))
    try:
        import dspy  # type: ignore[import-not-found]  # noqa: PLC0415

        return dspy.Prediction(score=score, feedback=feedback)
    except ImportError:
        return ScoreWithFeedback(score=score, feedback=feedback)


def _get(obj: Any, key: str, default: Any = None) -> Any:
    if obj is None:
        return default
    if isinstance(obj, Mapping):
        return obj.get(key, default)
    return getattr(obj, key, default)


# ─────────────────────────────────────────────────────────────────────────────
# guide_coach metric（golden 监督 + 防 CoT）
# ─────────────────────────────────────────────────────────────────────────────


def score_guide_coach(gold: Any, pred: Any) -> tuple[float, str]:
    """对一条导学决策打分并给出可改进反馈（纯逻辑，无 dspy）。"""
    expected = list(_get(gold, "expected_events", []) or [])
    forbidden = list(_get(gold, "forbidden_events", []) or [])
    next_event = str(_get(pred, "next_event", "") or "")
    reason = str(_get(pred, "reason_summary", "") or "")
    hint_level = _get(pred, "hint_level", 0)

    lowered = reason.lower()
    if any(marker in lowered for marker in _COT_MARKERS):
        return 0.0, (
            "reason_summary 泄漏了 chain-of-thought / 逐步推理：只允许一句可审计摘要，"
            "禁止任何原始/隐性逐步推理。"
        )
    if not next_event:
        return 0.0, "必须给出 next_event：从 allowed_events 白名单内选择下一步教学动作。"
    if next_event in forbidden:
        return 0.0, (
            f"next_event={next_event} 命中 forbidden（禁止）事件；"
            f"应改选期望集合内动作：{expected or '（见 allowed_events）'}。"
        )

    score = 1.0
    notes: list[str] = []
    if expected and next_event not in expected:
        score = 0.5
        notes.append(f"next_event={next_event} 不在 expected_events {expected} 内，请优先选期望动作。")
    else:
        notes.append(f"next_event={next_event} 命中期望，方向正确。")

    if not reason.strip():
        score = min(score, 0.7)
        notes.append("缺少 reason_summary：补一句可审计的选择理由（非 CoT）。")
    if not isinstance(hint_level, int) or not (0 <= hint_level <= 3):
        score = min(score, 0.6)
        notes.append("hint_level 必须是 0..3 的整数；禁止直接给完整答案（高 hint 仍非答案）。")

    return score, " ".join(notes)


def make_guide_coach_metric() -> Callable[..., Any]:
    """构造 GEPA 兼容的 guide_coach 反馈 metric。"""

    def metric(
        gold: Any,
        pred: Any,
        trace: Any = None,
        pred_name: str | None = None,
        pred_trace: Any = None,
    ) -> Any:
        score, feedback = score_guide_coach(gold, pred)
        return _score_with_feedback(score, feedback)

    return metric


# ─────────────────────────────────────────────────────────────────────────────
# practice metric（grounding/safety 监督 + 防泄答）
# ─────────────────────────────────────────────────────────────────────────────


def _coerce_practice(problems: Any) -> PracticeContent:
    if isinstance(problems, PracticeContent):
        return problems
    if isinstance(problems, Mapping) and "problems" in problems:
        return PracticeContent.model_validate(problems)
    return PracticeContent.model_validate({"problems": problems})


def _evidence_from_gold(gold: Any) -> EvidenceBundle:
    raw = _get(gold, "evidence", None)
    if isinstance(raw, EvidenceBundle):
        return raw
    if isinstance(raw, Mapping):
        try:
            return EvidenceBundle.model_validate(raw)
        except Exception:  # noqa: BLE001 - 证据可选；解析失败按无证据（通识）处理
            return EvidenceBundle()
    if isinstance(raw, list):
        return EvidenceBundle(chunks=[RagHit(**hit) for hit in raw if isinstance(hit, Mapping)])
    return EvidenceBundle()


def score_practice(gold: Any, pred: Any) -> tuple[float, str]:
    """对一份练习题草稿打分并给出可改进反馈（复用 grounding/safety；纯逻辑，无 dspy）。"""
    problems = _get(pred, "problems", None)
    try:
        practice = _coerce_practice(problems)
    except Exception as exc:  # noqa: BLE001 - schema 违规即低分 + 反馈，交 GEPA 反思修复
        return 0.0, (
            f"输出不满足 PracticeContent schema：{exc}。每题须含 title、statement_md(≥10 字)、"
            "至少一个 sample_io、kc_codes、difficulty(basic/medium/hard)。"
        )

    draft = ResourceDraft(
        title=f"练习题：{','.join((_get(gold, 'kc_codes', []) or ['KC'])[:3])}",
        content_format="STRUCT_JSON",
        practice=practice,
    )
    safety = safety_critic(draft)
    if not safety.passed:
        return 0.0, "safety 未通过（疑似泄答/违规内容）：" + "；".join(safety.reasons)

    grounding = grounding_critic(draft, _evidence_from_gold(gold))
    score = 1.0
    notes: list[str] = []
    if not grounding.passed:
        score = min(score, 0.3)
        notes.append("grounding 未通过（引用了证据外页码/章节，疑似伪造）：" + "；".join(grounding.reasons))

    target = str(_get(gold, "target_difficulty", "") or "")
    if target:
        mismatched = [p.title for p in practice.problems if p.difficulty != target]
        if mismatched:
            score = min(score, 0.8)
            notes.append(f"难度未对齐 target_difficulty={target}：{mismatched} 偏离，请贴合目标难度。")

    if score >= 1.0:
        notes.append("练习通过 safety/grounding，含 sample_io、无泄答，难度对齐。")
    return score, " ".join(notes) if notes else "练习通过审查。"


def make_practice_metric() -> Callable[..., Any]:
    """构造 GEPA 兼容的 practice 反馈 metric。"""

    def metric(
        gold: Any,
        pred: Any,
        trace: Any = None,
        pred_name: str | None = None,
        pred_trace: Any = None,
    ) -> Any:
        score, feedback = score_practice(gold, pred)
        return _score_with_feedback(score, feedback)

    return metric


def default_metric(name: str) -> Callable[..., Any]:
    """按程序名取默认反馈 metric。"""
    if name == "guide_coach_decision":
        return make_guide_coach_metric()
    if name == "practice_generate":
        return make_practice_metric()
    raise ValueError(f"unknown program: {name}")


# ─────────────────────────────────────────────────────────────────────────────
# trainset（golden / 内置样本 → dspy.Example；纯数据部分可单测）
# ─────────────────────────────────────────────────────────────────────────────


def guide_coach_scenarios() -> list[dict[str, Any]]:
    """读取 guide_coach golden 场景（只读复用 tests/fixtures，作监督信号源）。"""
    scenarios: list[dict[str, Any]] = json.loads(_GOLDEN_PATH.read_text(encoding="utf-8"))
    return scenarios


def _scenario_to_inputs(scenario: dict[str, Any]) -> dict[str, Any]:
    verdict = scenario.get("verdict", "")
    requested = "AC_REVIEW" if verdict == "AC" else "ERROR_FEEDBACK"
    return {
        "requested_event": requested,
        "allowed_events": list(scenario.get("allowed_events", [])),
        "student_state": {
            "phase": "CODING",
            "latest_verdict": verdict,
            "consecutive_errors": scenario.get("consecutive_errors", 0),
            "hint_level": 0,
        },
        "problem": {
            "problem_id": 42,
            "title": "循环求和",
            "statement": "输入 n，输出 1 到 n 的和。",
            "kc_codes": ["PY43-KC-016"],
        },
        "diagnosis": {
            "verdict": verdict,
            "passed_cases": scenario.get("passed_cases", 0),
            "total_cases": scenario.get("total_cases", 0),
            "consecutive_errors": scenario.get("consecutive_errors", 0),
        },
        "profile_brief": "",
        "recent_cards": [],
    }


def build_guide_coach_trainset() -> list[Any]:
    """构造 guide_coach 的 dspy.Example 列表（需 dspy；离线 scripts 用）。"""
    dspy = _require_dspy()
    input_keys = list(_scenario_to_inputs({}).keys())
    examples: list[Any] = []
    for scenario in guide_coach_scenarios():
        payload = _scenario_to_inputs(scenario)
        payload["expected_events"] = list(scenario.get("expected_events", []))
        payload["forbidden_events"] = list(scenario.get("forbidden_events", []))
        examples.append(dspy.Example(**payload).with_inputs(*input_keys))
    return examples


# 内置出题样本（不依赖真实课件页：grounding 对无 page_refs 的练习恒通过；监督聚焦防泄答 + 难度对齐）。
PRACTICE_TRAIN_SPECS: tuple[dict[str, Any], ...] = (
    {"kc_codes": ["PY43-KC-001"], "target_difficulty": "basic", "self_rated_level": 1},
    {"kc_codes": ["PY43-KC-016"], "target_difficulty": "medium", "self_rated_level": 3},
    {"kc_codes": ["PY43-KC-032"], "target_difficulty": "hard", "self_rated_level": 5},
)


def _practice_spec_to_inputs(spec: dict[str, Any]) -> dict[str, Any]:
    return {
        "kc_codes": list(spec.get("kc_codes", [])),
        "target_difficulty": spec.get("target_difficulty", ""),
        "profile_snapshot": {
            "major": "OTHER",
            "python_self_rated_level": spec.get("self_rated_level", 2),
        },
        "evidence_context": "",
        "focus_hint": "",
        "agent_strategy_directive": "",
    }


def build_practice_trainset() -> list[Any]:
    """构造 practice 的 dspy.Example 列表（需 dspy；离线 scripts 用）。"""
    dspy = _require_dspy()
    input_keys = list(_practice_spec_to_inputs({}).keys())
    examples: list[Any] = []
    for spec in PRACTICE_TRAIN_SPECS:
        payload = _practice_spec_to_inputs(spec)
        payload["kc_codes_gold"] = list(spec.get("kc_codes", []))
        examples.append(dspy.Example(**payload).with_inputs(*input_keys))
    return examples


def build_trainset(name: str) -> list[Any]:
    """按程序名构造 trainset（需 dspy）。"""
    if name == "guide_coach_decision":
        return build_guide_coach_trainset()
    if name == "practice_generate":
        return build_practice_trainset()
    raise ValueError(f"unknown program: {name}")


# ─────────────────────────────────────────────────────────────────────────────
# 产物 IO
# ─────────────────────────────────────────────────────────────────────────────


def artifact_path(name: str, artifacts_dir: Path | str | None = None) -> Path:
    """产物 JSON 路径（缺省 ``optimize/artifacts/{name}.json``，与 P2 loader 同址）。"""
    base = Path(artifacts_dir) if artifacts_dir is not None else _ARTIFACTS_DIR
    return base / f"{name}.json"


def write_artifact(artifact: dict[str, Any], path: Path | str) -> Path:
    """落盘产物 JSON（UTF-8 / 保留中文 / 缩进）。"""
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(artifact, ensure_ascii=False, indent=2), encoding="utf-8")
    return target


def read_artifact(path: Path | str) -> dict[str, Any]:
    """读取产物 JSON。"""
    data: dict[str, Any] = json.loads(Path(path).read_text(encoding="utf-8"))
    return data


# ─────────────────────────────────────────────────────────────────────────────
# GEPA 编译入口（离线 only；优化器可注入以便测试替身）
# ─────────────────────────────────────────────────────────────────────────────


def build_gepa(
    *,
    metric: Callable[..., Any],
    reflection_lm: Any,
    auto: str | None = "light",
    max_full_evals: int | None = None,
    num_threads: int = 4,
    reflection_minibatch_size: int = 3,
    track_stats: bool = True,
    **extra: Any,
) -> Any:
    """构造 ``dspy.GEPA`` 优化器（需 dspy；reflection_lm 必填）。

    预算二选一：``auto`` ∈ {light,medium,heavy} 或 ``max_full_evals``（互斥，传 max_full_evals 时
    auto 置空）。本函数仅在离线 scripts 调用，运行时绝不触发。
    """
    if reflection_lm is None:
        raise ValueError("GEPA requires a reflection_lm (a configured dspy.LM)")
    dspy = _require_dspy()
    kwargs: dict[str, Any] = {
        "metric": metric,
        "reflection_lm": reflection_lm,
        "num_threads": num_threads,
        "reflection_minibatch_size": reflection_minibatch_size,
        "track_stats": track_stats,
        **extra,
    }
    if max_full_evals is not None:
        kwargs["max_full_evals"] = max_full_evals
    else:
        kwargs["auto"] = auto
    return dspy.GEPA(**kwargs)


def compile_and_export(
    name: str,
    *,
    trainset: Any,
    valset: Any | None = None,
    program: Any = None,
    optimizer: Any = None,
    metric: Callable[..., Any] | None = None,
    reflection_lm: Any = None,
    artifacts_dir: Path | str | None = None,
    metadata: dict[str, Any] | None = None,
    gepa_kwargs: dict[str, Any] | None = None,
) -> Path:
    """离线编译并导出冻结产物到 ``artifacts/{name}.json``，返回路径。

    - ``program`` / ``optimizer`` 缺省时惰性用 dspy 构造；测试可注入替身（无需 dspy）。
    - 优化器接口约定：``optimizer.compile(program, trainset=..., valset=...) -> compiled_program``。
    - 仅离线触发（scripts / 测试替身）；运行时绝不调用。
    """
    if name not in PROGRAM_NAMES:
        raise ValueError(f"unknown program: {name}")
    if program is None:
        program = build_program(name)
    if optimizer is None:
        optimizer = build_gepa(
            metric=metric or default_metric(name),
            reflection_lm=reflection_lm,
            **(gepa_kwargs or {}),
        )

    compile_kwargs: dict[str, Any] = {"trainset": trainset}
    if valset is not None:
        compile_kwargs["valset"] = valset
    compiled = optimizer.compile(program, **compile_kwargs)

    export_meta = {
        "optimizer": "GEPA",
        "schema_version": ARTIFACT_SCHEMA_VERSION,
        "compiled_at": datetime.now(timezone.utc).isoformat(),
        "train_size": _safe_len(trainset),
        "val_size": _safe_len(valset),
    }
    if metadata:
        export_meta.update(metadata)

    artifact = program_to_artifact(name, compiled, metadata=export_meta)
    return write_artifact(artifact, artifact_path(name, artifacts_dir))


def _safe_len(obj: Any) -> int:
    try:
        return len(obj)
    except TypeError:
        return 0


# ─────────────────────────────────────────────────────────────────────────────
# 离线运行编排（仅 scripts/optimize_*.py 调用；构造 LM + 跑 GEPA + 导出）
# ─────────────────────────────────────────────────────────────────────────────


def _resolve_openai_env(
    model: str | None = None, base_url: str | None = None, api_key: str | None = None
) -> tuple[str | None, str | None, str | None]:
    """解析 OpenAI 兼容（DeepSeek 等）三件套，env 命名精度对齐既有 provider 约定。"""
    base_url = (
        base_url
        or os.environ.get("PYT_OPENAI_BASE_URL")
        or os.environ.get("OPENAI_BASE_URL")
        or os.environ.get("LLM_BASE_URL")
    )
    api_key = (
        api_key
        or os.environ.get("PYT_OPENAI_API_KEY")
        or os.environ.get("OPENAI_API_KEY")
        or os.environ.get("LLM_API_KEY")
    )
    model = (
        model
        or os.environ.get("PYT_OPENAI_MODEL")
        or os.environ.get("OPENAI_MODEL")
        or os.environ.get("LLM_MODEL")
    )
    return model, base_url, api_key


def build_openai_lm(
    model: str | None = None,
    *,
    base_url: str | None = None,
    api_key: str | None = None,
    temperature: float = 1.0,
    max_tokens: int = 8000,
    **extra: Any,
) -> Any:
    """构造 OpenAI 兼容 ``dspy.LM``（需 dspy；离线 scripts 用）。"""
    model, base_url, api_key = _resolve_openai_env(model, base_url, api_key)
    if not (model and base_url and api_key):
        raise ValueError(
            "缺少 OpenAI 兼容 LLM 配置：需 base_url/api_key/model"
            "（CLI --base-url/--task-model 或环境变量 PYT_OPENAI_*）。"
        )
    endpoint = urlsplit(base_url)
    try:
        valid_deepseek_url = (
            endpoint.scheme == "https"
            and endpoint.hostname == "api.deepseek.com"
            and endpoint.port in (None, 443)
            and endpoint.username is None
            and endpoint.password is None
        )
    except ValueError:
        valid_deepseek_url = False
    if not valid_deepseek_url:
        raise ValueError("Offline LLM API must use DeepSeek at https://api.deepseek.com")
    if model not in DEEPSEEK_CHAT_MODELS:
        raise ValueError(f"Unsupported DeepSeek model: {model}")
    dspy = _require_dspy()
    return dspy.LM(
        f"openai/{model}",
        api_base=base_url,
        api_key=api_key,
        temperature=temperature,
        max_tokens=max_tokens,
        **extra,
    )


def run_offline_compile(
    name: str,
    *,
    task_lm: Any,
    reflection_lm: Any,
    auto: str | None = "light",
    max_full_evals: int | None = None,
    artifacts_dir: Path | str | None = None,
    metadata: dict[str, Any] | None = None,
) -> Path:
    """离线编排：配置 task LM → 构造 trainset → 跑 GEPA → 导出冻结产物。仅离线触发。"""
    if name not in PROGRAM_NAMES:
        raise ValueError(f"unknown program: {name}")
    dspy = _require_dspy()
    dspy.configure(lm=task_lm)
    trainset = build_trainset(name)
    optimizer = build_gepa(
        metric=default_metric(name),
        reflection_lm=reflection_lm,
        auto=auto,
        max_full_evals=max_full_evals,
    )
    return compile_and_export(
        name,
        program=build_program(name),
        optimizer=optimizer,
        trainset=trainset,
        valset=trainset,  # 小样本离线集：复用 trainset 作 Pareto valset
        artifacts_dir=artifacts_dir,
        metadata=metadata,
    )


__all__ = [
    "ScoreWithFeedback",
    "OptimizeDependencyError",
    "score_guide_coach",
    "make_guide_coach_metric",
    "score_practice",
    "make_practice_metric",
    "default_metric",
    "guide_coach_scenarios",
    "build_guide_coach_trainset",
    "build_practice_trainset",
    "build_trainset",
    "artifact_path",
    "write_artifact",
    "read_artifact",
    "build_gepa",
    "compile_and_export",
    "build_openai_lm",
    "run_offline_compile",
]
