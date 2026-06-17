"""P2-dspy-loader 测试矩阵（§3.5 / §4 Stream P 第 3、5 条）。

覆盖 DSPy 冻结产物加载器 + provider 侧 loader hook 的完整矩阵：

① 缺文件 / 缺目录 → ``None`` → provider 回退现有手写 prompt（行为不变）
② 读合法产物 → ``CompiledPrompt(instruction + demos)``
③ 损坏 / 非 dict / 空 instruction JSON → ``None``，绝不抛
④ provider hook 在无产物时**完全不改变**现有调用（返回同一 messages 对象）
⑤ 产物存在时用冻结 instruction + demos（替换系统提示 + 注入 demos，保留真实对话轮次）

红线：运行时绝不调用 ``dspy.compile`` / GEPA；loader 只读 ``optimize/artifacts/*.json``（fail-soft）。
"""

from __future__ import annotations

import json
from pathlib import Path

import pytest

from pythontopia_graph.llm.provider import apply_compiled_prompt
from pythontopia_graph.optimize.loader import (
    COMPILED_PROMPT_NAMES,
    CompiledPrompt,
    load_compiled,
)


def _write_artifact(artifacts_dir: Path, name: str, payload: object) -> Path:
    artifacts_dir.mkdir(parents=True, exist_ok=True)
    path = artifacts_dir / f"{name}.json"
    path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
    return path


# ── loader ① 缺文件 / 缺目录 → None ────────────────────────────────────────


def test_load_compiled_missing_file_returns_none(tmp_path: Path) -> None:
    assert load_compiled("guide_coach_decision", artifacts_dir=tmp_path) is None


def test_load_compiled_missing_dir_returns_none(tmp_path: Path) -> None:
    missing = tmp_path / "nope"
    assert load_compiled("practice_generate", artifacts_dir=missing) is None


def test_load_compiled_default_artifacts_dir_is_none_until_compiled() -> None:
    # 不传 artifacts_dir → 读真实 optimize/artifacts/；当前未离线编译 → None（不崩）。
    for name in COMPILED_PROMPT_NAMES:
        assert load_compiled(name) is None


# ── loader ② 合法产物 → CompiledPrompt ─────────────────────────────────────


@pytest.mark.parametrize("name", COMPILED_PROMPT_NAMES)
def test_load_compiled_valid_flat_artifact(tmp_path: Path, name: str) -> None:
    _write_artifact(
        tmp_path,
        name,
        {
            "name": name,
            "instruction": "冻结后的优化系统指令。",
            "demos": [
                {"role": "user", "content": "示例输入"},
                {"role": "assistant", "content": "示例输出"},
            ],
            "metadata": {"optimizer": "GEPA"},
        },
    )
    compiled = load_compiled(name, artifacts_dir=tmp_path)
    assert isinstance(compiled, CompiledPrompt)
    assert compiled.instruction == "冻结后的优化系统指令。"
    assert compiled.demos == [
        {"role": "user", "content": "示例输入"},
        {"role": "assistant", "content": "示例输出"},
    ]
    assert compiled.name == name
    assert compiled.metadata == {"optimizer": "GEPA"}


def test_load_compiled_demos_default_empty_when_absent(tmp_path: Path) -> None:
    _write_artifact(tmp_path, "guide_coach_decision", {"instruction": "只有指令"})
    compiled = load_compiled("guide_coach_decision", artifacts_dir=tmp_path)
    assert compiled is not None
    assert compiled.demos == []
    assert compiled.metadata == {}


def test_load_compiled_filters_non_dict_demos(tmp_path: Path) -> None:
    _write_artifact(
        tmp_path,
        "guide_coach_decision",
        {
            "instruction": "x",
            "demos": [{"role": "user", "content": "ok"}, "junk", 42, None],
        },
    )
    compiled = load_compiled("guide_coach_decision", artifacts_dir=tmp_path)
    assert compiled is not None
    assert compiled.demos == [{"role": "user", "content": "ok"}]


def test_load_compiled_dspy_native_nested_artifact(tmp_path: Path) -> None:
    # 兼容 DSPy program.save() 原生嵌套 predictor 状态。
    _write_artifact(
        tmp_path,
        "practice_generate",
        {
            "predict": {
                "signature": {"instructions": "DSPy 原生导出的指令"},
                "demos": [{"question": "q", "answer": "a"}],
            }
        },
    )
    compiled = load_compiled("practice_generate", artifacts_dir=tmp_path)
    assert compiled is not None
    assert compiled.instruction == "DSPy 原生导出的指令"
    assert compiled.demos == [{"question": "q", "answer": "a"}]


def test_load_compiled_top_level_signature_instructions(tmp_path: Path) -> None:
    _write_artifact(
        tmp_path,
        "guide_coach_decision",
        {"signature": {"instructions": "顶层签名指令"}},
    )
    compiled = load_compiled("guide_coach_decision", artifacts_dir=tmp_path)
    assert compiled is not None
    assert compiled.instruction == "顶层签名指令"


# ── loader ③ 损坏 / 非法结构 → None 不抛 ───────────────────────────────────


def test_load_compiled_corrupt_json_returns_none(tmp_path: Path) -> None:
    tmp_path.mkdir(parents=True, exist_ok=True)
    (tmp_path / "guide_coach_decision.json").write_text("{not valid,,,", encoding="utf-8")
    assert load_compiled("guide_coach_decision", artifacts_dir=tmp_path) is None


def test_load_compiled_non_dict_json_returns_none(tmp_path: Path) -> None:
    _write_artifact(tmp_path, "guide_coach_decision", [1, 2, 3])
    assert load_compiled("guide_coach_decision", artifacts_dir=tmp_path) is None


def test_load_compiled_empty_instruction_returns_none(tmp_path: Path) -> None:
    _write_artifact(tmp_path, "guide_coach_decision", {"instruction": "   ", "demos": []})
    assert load_compiled("guide_coach_decision", artifacts_dir=tmp_path) is None


def test_load_compiled_missing_instruction_returns_none(tmp_path: Path) -> None:
    _write_artifact(
        tmp_path,
        "guide_coach_decision",
        {"demos": [{"role": "user", "content": "x"}]},
    )
    assert load_compiled("guide_coach_decision", artifacts_dir=tmp_path) is None


# ── loader 白名单：未知 name → None（防任意路径读取，绝不读盘）─────────────


@pytest.mark.parametrize("bad_name", ["", "random", "../provider", "guide_coach", "PRACTICE"])
def test_load_compiled_unknown_name_returns_none(tmp_path: Path, bad_name: str) -> None:
    assert load_compiled(bad_name, artifacts_dir=tmp_path) is None


# ── provider hook ④ 无产物 → 原样返回（identity，完全不改变现有调用）──────


def test_apply_compiled_prompt_noop_without_artifact_is_identity(tmp_path: Path) -> None:
    messages = [
        {"role": "system", "content": "手写系统提示"},
        {"role": "user", "content": "payload"},
    ]
    result = apply_compiled_prompt(messages, "guide_coach_decision", artifacts_dir=tmp_path)
    assert result is messages


def test_apply_compiled_prompt_unknown_name_is_identity(tmp_path: Path) -> None:
    messages = [{"role": "user", "content": "payload"}]
    result = apply_compiled_prompt(messages, "not_a_prompt", artifacts_dir=tmp_path)
    assert result is messages


# ── provider hook ⑤ 命中产物 → 冻结 instruction + demos ─────────────────────


def test_apply_compiled_prompt_uses_frozen_instruction_and_demos(tmp_path: Path) -> None:
    _write_artifact(
        tmp_path,
        "guide_coach_decision",
        {
            "instruction": "冻结指令：只在 allowed_events 内选择。",
            "demos": [
                {"role": "user", "content": "demo-in"},
                {"role": "assistant", "content": "demo-out"},
            ],
        },
    )
    messages = [
        {"role": "system", "content": "手写系统提示（应被替换）"},
        {"role": "user", "content": "真实 payload"},
    ]
    result = apply_compiled_prompt(messages, "guide_coach_decision", artifacts_dir=tmp_path)

    assert result is not messages
    assert result == [
        {"role": "system", "content": "冻结指令：只在 allowed_events 内选择。"},
        {"role": "user", "content": "demo-in"},
        {"role": "assistant", "content": "demo-out"},
        {"role": "user", "content": "真实 payload"},
    ]
    # 原 messages 不被原地修改（无副作用）。
    assert messages[0] == {"role": "system", "content": "手写系统提示（应被替换）"}


def test_apply_compiled_prompt_drops_all_existing_system_messages(tmp_path: Path) -> None:
    _write_artifact(tmp_path, "practice_generate", {"instruction": "冻结指令"})
    messages = [
        {"role": "system", "content": "sys1"},
        {"role": "user", "content": "u1"},
        {"role": "system", "content": "sys2"},
        {"role": "assistant", "content": "a1"},
    ]
    result = apply_compiled_prompt(messages, "practice_generate", artifacts_dir=tmp_path)
    assert result == [
        {"role": "system", "content": "冻结指令"},
        {"role": "user", "content": "u1"},
        {"role": "assistant", "content": "a1"},
    ]


def test_apply_compiled_prompt_renders_input_output_demos(tmp_path: Path) -> None:
    _write_artifact(
        tmp_path,
        "practice_generate",
        {
            "instruction": "冻结指令",
            "demos": [{"inputs": "出一道循环题", "outputs": "题目 JSON"}],
        },
    )
    messages = [{"role": "user", "content": "请出题"}]
    result = apply_compiled_prompt(messages, "practice_generate", artifacts_dir=tmp_path)
    assert result == [
        {"role": "system", "content": "冻结指令"},
        {"role": "user", "content": "出一道循环题"},
        {"role": "assistant", "content": "题目 JSON"},
        {"role": "user", "content": "请出题"},
    ]


def test_apply_compiled_prompt_stringifies_structured_demo_values(tmp_path: Path) -> None:
    _write_artifact(
        tmp_path,
        "practice_generate",
        {
            "instruction": "i",
            "demos": [{"input": {"topic": "for"}, "output": {"q": "..."}}],
        },
    )
    result = apply_compiled_prompt(
        [{"role": "user", "content": "go"}], "practice_generate", artifacts_dir=tmp_path
    )
    assert result[0] == {"role": "system", "content": "i"}
    assert result[1]["role"] == "user"
    assert json.loads(result[1]["content"]) == {"topic": "for"}
    assert result[2]["role"] == "assistant"
    assert json.loads(result[2]["content"]) == {"q": "..."}
    assert result[3] == {"role": "user", "content": "go"}


def test_apply_compiled_prompt_skips_malformed_demos(tmp_path: Path) -> None:
    _write_artifact(
        tmp_path,
        "guide_coach_decision",
        {
            "instruction": "冻结指令",
            "demos": [
                {"role": "user"},  # 缺 content
                {"content": "无 role"},  # 缺 role
                {"role": "user", "content": 123},  # content 非 str
                {"role": "tool", "content": "非法 role"},  # role 不在白名单
                {"role": "user", "content": "保留我"},
            ],
        },
    )
    result = apply_compiled_prompt(
        [{"role": "user", "content": "q"}], "guide_coach_decision", artifacts_dir=tmp_path
    )
    assert result == [
        {"role": "system", "content": "冻结指令"},
        {"role": "user", "content": "保留我"},
        {"role": "user", "content": "q"},
    ]


def test_apply_compiled_prompt_skips_unrenderable_demos(tmp_path: Path) -> None:
    # DSPy example 风格 demo（无 role/content、无 inputs/outputs）→ 不注入消息，仅替换指令。
    _write_artifact(
        tmp_path,
        "guide_coach_decision",
        {"instruction": "i", "demos": [{"question": "q", "answer": "a"}]},
    )
    result = apply_compiled_prompt(
        [{"role": "user", "content": "go"}], "guide_coach_decision", artifacts_dir=tmp_path
    )
    assert result == [
        {"role": "system", "content": "i"},
        {"role": "user", "content": "go"},
    ]
