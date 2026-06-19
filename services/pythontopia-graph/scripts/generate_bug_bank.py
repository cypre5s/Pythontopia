#!/usr/bin/env python3
"""离线大规模生成「代码找茬」bug 题库（以 LLM + 学生真实错误库为知识底座）。

本脚本**仅离线运行**，运行时（FastAPI/agent）绝不导入它——它不属于 ``pythontopia_graph`` 包。
它用 :func:`pythontopia_graph.llm.provider.provider_from_env` 的真实大模型，按「误解/错误类型
分类（taxonomy）」和（可选）「学生真实错误频次表」批量生成 bug 样本，逐条严格校验后**合并**写入
``pythontopia_graph/data/feynman_bug_bank.generated.json``。运行期的 :mod:`...feynman.bug_bank`
若发现该文件，则在 curated 题库之上追加融合（缺文件/损坏一律 fail-soft 忽略）。

每个生成样本都必须通过 :func:`...feynman.bug_bank.validate_sample`：
* ``buggy_code`` / ``fixed_code`` 行数均在 ``[MIN_LINES, MAX_LINES]`` 内、且行数相等；
* 二者仅在 bug 行不同、其余逐字一致（→ 行级 diff 至少标出 1 行 bug，零伪造）；
* 二者均能 ``compile()`` 通过（合法 Python）。

幂等：重复运行只会**新增**去重后的样本（按 ``(tag, buggy_code)`` 去重），不会重复堆叠；
也会跳过与 curated 题库重复的 buggy_code。

用法::

    # 1) 先配好 OpenAI 兼容（DeepSeek）凭证（见 deploy/.env 的 PYT_OPENAI_*）
    export PYT_LLM_PROVIDER=openai_compat
    # 2) 生成约 60 条，合并进默认题库文件
    python3 scripts/generate_bug_bank.py --count 60
    # 3) 按学生真实错误频次表加权生成（list of {"tag","count"}）
    python3 scripts/generate_bug_bank.py --count 80 --freq ./real_errors.json
    # 4) 指定输出文件与随机种子（可复现）
    python3 scripts/generate_bug_bank.py --count 40 --out /tmp/bank.json --seed 7

依赖：仅需 ``pip install -e .``（httpx 等核心依赖）。无 key/无网时 provider 退化，校验会全部失败
→ 脚本报告生成 0 条并退出码 0（不写空污染）。**测试不得对接真实 LLM 调本脚本**。
"""

from __future__ import annotations

import argparse
import asyncio
import json
import random
import re
import sys
from pathlib import Path
from typing import Any

# 让脚本能 import 到 pythontopia_graph（脚本不在包内）。
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from pythontopia_graph.agents.feynman.bug_bank import (  # noqa: E402
    MAX_LINES,
    MIN_LINES,
    BugSample,
    all_samples,
    validate_sample,
)
from pythontopia_graph.llm.provider import (  # noqa: E402
    LlmProvider,
    LlmResponse,
    provider_from_env,
)

_DEFAULT_OUT = (
    Path(__file__).resolve().parents[1]
    / "pythontopia_graph"
    / "data"
    / "feynman_bug_bank.generated.json"
)

# 误解/错误类型分类（知识底座）：tag → (章节 1-7, 中文主题, 难度)。覆盖任务列举的全部类别。
# 既给 LLM 明确的“要复现哪种初学者错误 + 属于哪一章 + 主题”，也用于无频次表时轮转生成。
_TAXONOMY: tuple[dict[str, Any], ...] = (
    {"tag": "swap_without_temp", "chapter": 1, "topic": "变量交换（算法思维）", "difficulty": "easy"},
    {"tag": "precedence_average", "chapter": 1, "topic": "运算符优先级与平均值", "difficulty": "easy"},
    {"tag": "fencepost_off_by_one", "chapter": 1, "topic": "栅栏问题（计数偏差）", "difficulty": "medium"},
    {"tag": "input_type_string", "chapter": 2, "topic": "input() 返回字符串与类型转换", "difficulty": "easy"},
    {"tag": "str_int_compare", "chapter": 2, "topic": "字符串与数字比较", "difficulty": "medium"},
    {"tag": "shadow_builtin_list", "chapter": 2, "topic": "变量名覆盖内置 list", "difficulty": "medium"},
    {"tag": "int_float_division", "chapter": 3, "topic": "整除与真除法", "difficulty": "easy"},
    {"tag": "string_immutable", "chapter": 3, "topic": "字符串不可变", "difficulty": "medium"},
    {"tag": "float_equality", "chapter": 3, "topic": "浮点数相等判断", "difficulty": "hard"},
    {"tag": "string_concat_int", "chapter": 3, "topic": "字符串拼接数字", "difficulty": "easy"},
    {"tag": "modulo_sign", "chapter": 3, "topic": "取模与整除的取整方向", "difficulty": "hard"},
    {"tag": "off_by_one_range", "chapter": 4, "topic": "range 边界（右端点不含/起点漏首）", "difficulty": "easy"},
    {"tag": "is_vs_eq", "chapter": 4, "topic": "is 与 == 的区别", "difficulty": "medium"},
    {"tag": "and_or_precedence", "chapter": 4, "topic": "逻辑或与真值陷阱", "difficulty": "medium"},
    {"tag": "augmented_vs_reassign", "chapter": 4, "topic": "累加误写成赋值", "difficulty": "easy"},
    {"tag": "accumulation_reset", "chapter": 4, "topic": "累加变量被覆盖/重置", "difficulty": "medium"},
    {"tag": "while_infinite", "chapter": 4, "topic": "while 更新写错导致死循环", "difficulty": "medium"},
    {"tag": "break_continue_misuse", "chapter": 4, "topic": "break 与 continue 混用", "difficulty": "medium"},
    {"tag": "indentation_scope", "chapter": 4, "topic": "缩进影响语句归属", "difficulty": "medium"},
    {"tag": "list_alias_mutation", "chapter": 5, "topic": "列表别名与拷贝", "difficulty": "medium"},
    {"tag": "mutable_default_arg", "chapter": 5, "topic": "可变默认参数共享", "difficulty": "hard"},
    {"tag": "dict_keyerror", "chapter": 5, "topic": "字典缺省键 KeyError 与 .get", "difficulty": "medium"},
    {"tag": "list_index_out_of_range", "chapter": 5, "topic": "遍历下标越界", "difficulty": "medium"},
    {"tag": "slice_off_by_one", "chapter": 5, "topic": "切片右端点偏差", "difficulty": "easy"},
    {"tag": "list_append_return", "chapter": 5, "topic": "append 返回值为 None", "difficulty": "medium"},
    {"tag": "nested_list_alias", "chapter": 5, "topic": "二维列表乘法别名", "difficulty": "hard"},
    {"tag": "file_mode_wrong", "chapter": 6, "topic": "文件打开模式用错", "difficulty": "medium"},
    {"tag": "csv_sum_string", "chapter": 6, "topic": "从文本求和忘记转数字", "difficulty": "medium"},
    {"tag": "wrong_aggregation_init", "chapter": 6, "topic": "求最值初始值不当", "difficulty": "hard"},
    {"tag": "missing_return", "chapter": 7, "topic": "函数缺少返回值", "difficulty": "easy"},
    {"tag": "premature_return", "chapter": 7, "topic": "循环内过早 return", "difficulty": "hard"},
    {"tag": "global_assignment", "chapter": 7, "topic": "函数内修改全局变量", "difficulty": "hard"},
    {"tag": "shadow_builtin_sum", "chapter": 7, "topic": "变量名覆盖内置 sum", "difficulty": "medium"},
)

_TAXONOMY_BY_TAG: dict[str, dict[str, Any]] = {spec["tag"]: spec for spec in _TAXONOMY}


def _parse_args(argv: list[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="离线生成代码找茬 bug 题库")
    parser.add_argument("--count", type=int, default=40, help="本次目标生成样本数（去重前）")
    parser.add_argument("--out", default=str(_DEFAULT_OUT), help="输出/合并的 JSON 题库路径")
    parser.add_argument(
        "--freq",
        default=None,
        help="可选：学生真实错误频次表 JSON（list of {tag, count}），按频次加权生成",
    )
    parser.add_argument(
        "--attempts", type=int, default=3, help="每个目标的最多重试次数（生成不合格则重试）"
    )
    parser.add_argument("--seed", type=int, default=None, help="随机种子（可复现目标轮转）")
    parser.add_argument(
        "--model-tier", default=None, choices=[None, "fast"], help="可选模型档位（fast=快档）"
    )
    return parser.parse_args(argv)


def _load_freq(path: str | None) -> dict[str, int]:
    """读取真实错误频次表（list of {tag, count}）→ {tag: count}；缺文件/损坏返回空。"""
    if not path:
        return {}
    try:
        raw = json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, ValueError, TypeError):
        return {}
    out: dict[str, int] = {}
    if isinstance(raw, list):
        for item in raw:
            if isinstance(item, dict) and isinstance(item.get("tag"), str):
                try:
                    out[item["tag"]] = max(0, int(item.get("count", 0)))
                except (TypeError, ValueError):
                    continue
    return out


def _build_targets(count: int, freq: dict[str, int], seed: int | None) -> list[dict[str, Any]]:
    """构造长度约 ``count`` 的目标序列（每项是一个 taxonomy spec）。

    有频次表：按 count 加权（频次高的 tag 多生成几个变体），仅取 taxonomy 内已知的 tag；
    无频次表：在整张 taxonomy 上轮转，直到凑够 count。结果用 seed 打乱以分散同 tag 的连续请求。
    """
    rng = random.Random(seed)
    targets: list[dict[str, Any]] = []
    known_freq = {tag: c for tag, c in freq.items() if tag in _TAXONOMY_BY_TAG and c > 0}
    if known_freq:
        total = sum(known_freq.values())
        for tag, c in known_freq.items():
            share = max(1, round(count * c / total))
            targets.extend(_TAXONOMY_BY_TAG[tag] for _ in range(share))
    else:
        i = 0
        while len(targets) < count:
            targets.append(_TAXONOMY[i % len(_TAXONOMY)])
            i += 1
    rng.shuffle(targets)
    return targets[:count] if len(targets) > count else targets


def _strip_json_fence(text: str) -> str:
    fence = re.match(r"^\s*```(?:json)?\s*(.+?)\s*```\s*$", text, re.DOTALL)
    return fence.group(1) if fence else text


def _coerce_json(response: LlmResponse) -> dict[str, Any]:
    if response.content_json is not None:
        return response.content_json
    if response.content_text:
        try:
            parsed = json.loads(_strip_json_fence(response.content_text))
        except json.JSONDecodeError:
            return {}
        if isinstance(parsed, dict):
            return parsed
    return {}


def _messages_for(spec: dict[str, Any]) -> list[dict[str, str]]:
    schema = (
        '{"buggy_code":"含 bug 的多行 Python 代码","fixed_code":"仅改动 bug 行后的正确代码",'
        '"hint_md":"一句苏格拉底式提示（不直接给答案/不点出行号）"}'
    )
    system = (
        "你是 Pythontopia 费曼反演里出「代码找茬」题的助教。请围绕给定的初学者误解，写一段贴近"
        f"真实练习的 Python 代码（{MIN_LINES}-{MAX_LINES} 行，可含注释/print），并给出正确版本。"
        "只输出一个 JSON 对象，不要多余文字，严格遵循结构：" + schema + "。硬性要求："
        f"1) buggy_code 与 fixed_code 行数都在 {MIN_LINES}-{MAX_LINES} 行且行数相同；"
        "2) 两者必须仅在『体现该误解的 bug 行』不同、其余行逐字一致（便于行级 diff 定位 bug 行）；"
        "3) 两段代码都必须是合法 Python（能被 compile 解析）；"
        "4) 不要编造隐藏测试或题目，也不要在注释里写出答案；"
        "5) 代码贴合所给章节主题，bug 紧扣误解类型。"
    )
    user = (
        f"误解类型 tag = {spec['tag']}\n"
        f"所属章节 = 第 {spec['chapter']} 章\n"
        f"知识点主题 = {spec['topic']}\n"
        f"难度 = {spec['difficulty']}\n"
        "请生成一题。"
    )
    return [
        {"role": "system", "content": system},
        {"role": "user", "content": user},
    ]


async def _chat_json(
    provider: LlmProvider, messages: list[dict[str, str]], model_tier: str | None
) -> dict[str, Any]:
    kwargs: dict[str, Any] = {"temperature": 0.6, "response_format": {"type": "json_object"}}
    if model_tier:
        kwargs["model_tier"] = model_tier
    try:
        response = await provider.chat(messages, **kwargs)
    except TypeError:
        response = await provider.chat(messages)
    return _coerce_json(response)


def _sample_from_data(data: dict[str, Any], spec: dict[str, Any]) -> BugSample | None:
    buggy = data.get("buggy_code")
    fixed = data.get("fixed_code")
    hint = data.get("hint_md")
    if not isinstance(buggy, str) or not isinstance(fixed, str):
        return None
    if not isinstance(hint, str) or not hint.strip():
        hint = "对照正确写法，想想哪一行把规则用错了？"
    try:
        sample = BugSample(
            tag=str(spec["tag"]),
            chapter=int(spec["chapter"]),
            topic=str(spec["topic"]),
            buggy_code=buggy.strip("\n"),
            fixed_code=fixed.strip("\n"),
            hint_md=hint.strip(),
            difficulty=spec.get("difficulty", "medium"),
        )
    except Exception:  # noqa: BLE001 - 字段非法直接判废
        return None
    return sample if not validate_sample(sample) else None


async def _generate_one(
    provider: LlmProvider, spec: dict[str, Any], attempts: int, model_tier: str | None
) -> BugSample | None:
    for _ in range(max(1, attempts)):
        try:
            data = await _chat_json(provider, _messages_for(spec), model_tier)
        except Exception:  # noqa: BLE001 - 单题失败不拖垮整批
            continue
        sample = _sample_from_data(data, spec)
        if sample is not None:
            return sample
    return None


def _existing_keys() -> set[tuple[str, str]]:
    """curated 题库已有的 (tag, buggy_code)，避免生成与精选库重复的样本。"""
    return {(s.tag, s.buggy_code) for s in all_samples()}


def _read_existing_generated(out_path: Path) -> list[dict[str, Any]]:
    try:
        raw = json.loads(out_path.read_text(encoding="utf-8"))
    except (OSError, ValueError, TypeError):
        return []
    items = raw.get("samples") if isinstance(raw, dict) else raw
    return [item for item in items if isinstance(item, dict)] if isinstance(items, list) else []


def _merge_samples(
    existing: list[dict[str, Any]], fresh: list[BugSample], skip: set[tuple[str, str]]
) -> list[dict[str, Any]]:
    """合并（幂等）：按 (tag, buggy_code) 去重，跳过 curated 已有样本。"""
    merged: list[dict[str, Any]] = []
    seen: set[tuple[str, str]] = set(skip)
    for item in existing:
        key = (str(item.get("tag", "")), str(item.get("buggy_code", "")))
        if key in seen:
            continue
        seen.add(key)
        merged.append(item)
    for sample in fresh:
        key = (sample.tag, sample.buggy_code)
        if key in seen:
            continue
        seen.add(key)
        merged.append(sample.model_dump(mode="json"))
    return merged


async def _run(args: argparse.Namespace) -> int:
    out_path = Path(args.out)
    freq = _load_freq(args.freq)
    targets = _build_targets(args.count, freq, args.seed)
    if not targets:
        print("[generate_bug_bank] 没有可用目标（taxonomy 为空？）", file=sys.stderr)
        return 2

    provider = provider_from_env()
    print(f"[generate_bug_bank] 目标 {len(targets)} 题，provider={type(provider).__name__}")

    fresh: list[BugSample] = []
    for idx, spec in enumerate(targets, start=1):
        sample = await _generate_one(provider, spec, args.attempts, args.model_tier)
        status = "ok" if sample is not None else "skip(invalid)"
        print(f"  [{idx}/{len(targets)}] {spec['tag']:<24} -> {status}")
        if sample is not None:
            fresh.append(sample)

    existing = _read_existing_generated(out_path)
    merged = _merge_samples(existing, fresh, _existing_keys())

    out_path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "version": 1,
        "note": "由 scripts/generate_bug_bank.py 离线生成；运行期 bug_bank 会在 curated 之上融合。",
        "samples": merged,
    }
    out_path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(
        f"[generate_bug_bank] 本次有效 {len(fresh)} 题；合并去重后题库共 {len(merged)} 题；"
        f"已写入 {out_path}"
    )
    return 0


def main(argv: list[str] | None = None) -> int:
    args = _parse_args(argv)
    return asyncio.run(_run(args))


if __name__ == "__main__":
    raise SystemExit(main())
