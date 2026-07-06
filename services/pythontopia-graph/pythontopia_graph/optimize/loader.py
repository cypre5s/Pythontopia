"""DSPy 冻结产物加载器（P2-dspy-loader · §3.5）。

运行时 **fail-soft** 读取离线 GEPA 编译产物（``optimize/artifacts/{name}.json``），
**绝不**在运行时调用 ``dspy.compile`` / GEPA（仅离线 ``scripts/optimize_*.py`` 跑）。
缺文件 / 缺目录 / 损坏 JSON / 不兼容结构 → ``None``；provider 侧据此回退现有手写
prompt（行为不变，见 :func:`pythontopia_graph.llm.provider.apply_compiled_prompt`）。

产物 JSON 契约（与兄弟单元 P1 ``optimize/compile.py`` 的导出同源 · §3.5；消费侧冻结接口
为 ``CompiledPrompt(instruction, demos)``）。

规范格式（推荐 P1 导出）::

    {
      "name": "guide_coach_decision",
      "instruction": "<GEPA 优化后的系统指令>",
      "demos": [
        {"role": "user", "content": "..."},
        {"role": "assistant", "content": "..."}
      ],
      "metadata": {"optimizer": "GEPA", "compiled_at": "..."}
    }

兼容格式（DSPy ``program.save()`` 原生嵌套 predictor 状态）亦可识别：取首个含
``signature.instructions`` 的 predictor 的 instructions 作 ``instruction``、其 ``demos``
作 ``demos``。任何解不出非空 ``instruction`` 的结构 → ``None``（fail-soft）。
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

# §3.5 冻结：仅这两个名字可加载；白名单同时充当任意路径读取的护栏（越界即 None，绝不读盘）。
COMPILED_PROMPT_NAMES: tuple[str, ...] = ("guide_coach_decision", "practice_generate")

_ARTIFACTS_DIR = Path(__file__).resolve().parent / "artifacts"


@dataclass(frozen=True)
class CompiledPrompt:
    """provider 侧消费的冻结产物（§3.5 冻结接口：instruction + demos）。"""

    instruction: str
    demos: list[dict[str, Any]] = field(default_factory=list)
    name: str = ""
    metadata: dict[str, Any] = field(default_factory=dict)


def default_artifacts_dir() -> Path:
    """默认产物目录（``optimize/artifacts/``）。"""
    return _ARTIFACTS_DIR


def artifact_path(name: str, *, base_dir: Path | None = None) -> Path:
    """给定产物名对应的 JSON 路径（不保证存在）。"""
    base = base_dir if base_dir is not None else _ARTIFACTS_DIR
    return base / f"{name}.json"


def load_compiled(name: str, *, artifacts_dir: Path | None = None) -> CompiledPrompt | None:
    """读取冻结产物；缺 / 损坏 / 不兼容 → ``None``（fail-soft，绝不抛、绝不调 dspy）。

    Args:
        name: 产物名，必须在 :data:`COMPILED_PROMPT_NAMES` 内，否则直接 ``None``。
        artifacts_dir: 可选目录覆盖（测试用）；缺省读默认 ``optimize/artifacts/``。
    """
    if name not in COMPILED_PROMPT_NAMES:
        return None
    raw = _read_json(artifact_path(name, base_dir=artifacts_dir))
    if raw is None:
        return None
    return _to_compiled_prompt(name, raw)


def _read_json(path: Path) -> Any | None:
    try:
        text = path.read_text(encoding="utf-8")
    except OSError:
        # 缺文件 / 缺目录 / 权限等一律 fail-soft（FileNotFoundError 亦属 OSError）。
        return None
    try:
        return json.loads(text)
    except (ValueError, TypeError):
        return None


def _to_compiled_prompt(name: str, raw: Any) -> CompiledPrompt | None:
    if not isinstance(raw, dict):
        return None
    instruction = _extract_instruction(raw)
    if not instruction:
        return None
    metadata = raw.get("metadata")
    return CompiledPrompt(
        instruction=instruction,
        demos=_extract_demos(raw),
        name=name,
        metadata=metadata if isinstance(metadata, dict) else {},
    )


def _extract_instruction(raw: dict[str, Any]) -> str:
    # 规范格式：顶层 instruction。
    direct = raw.get("instruction")
    if isinstance(direct, str) and direct.strip():
        return direct
    # 兼容：顶层 signature.instructions（DSPy 单 predictor 直存）。
    top = _signature_instructions(raw.get("signature"))
    if top:
        return top
    # 兼容：DSPy program.save() 嵌套 predictor 状态（取首个含指令者）。
    for value in raw.values():
        if isinstance(value, dict):
            nested = _signature_instructions(value.get("signature"))
            if nested:
                return nested
    return ""


def _signature_instructions(signature: Any) -> str:
    if isinstance(signature, dict):
        instructions = signature.get("instructions")
        if isinstance(instructions, str) and instructions.strip():
            return instructions
    return ""


def _extract_demos(raw: dict[str, Any]) -> list[dict[str, Any]]:
    # 规范格式：顶层 demos。
    direct = raw.get("demos")
    if isinstance(direct, list):
        return [demo for demo in direct if isinstance(demo, dict)]
    # 兼容：嵌套 predictor 的 demos。
    for value in raw.values():
        if isinstance(value, dict) and isinstance(value.get("demos"), list):
            return [demo for demo in value["demos"] if isinstance(demo, dict)]
    return []
