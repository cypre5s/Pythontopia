from __future__ import annotations

import math
from collections.abc import Mapping, Sequence
from typing import Any


class RsageLengthPreflightError(ValueError):
    def __init__(self, report: Mapping[str, Any]) -> None:
        self.report = dict(report)
        super().__init__(str(report.get("message_zh", "长度预检失败")))


def run_rsage_length_preflight(
    *,
    sft_rows: Sequence[Mapping[str, Any]],
    preference_rows: Sequence[Mapping[str, Any]],
    tokenizer: Any,
    max_seq_length: int,
) -> dict[str, Any]:
    if isinstance(max_seq_length, bool) or not isinstance(max_seq_length, int) or max_seq_length <= 0:
        raise ValueError("max_seq_length 必须是正整数")
    if not sft_rows and not preference_rows:
        raise ValueError("长度预检至少需要一条 SFT 或偏好样本")

    eos_token = getattr(tokenizer, "eos_token", None)
    if not isinstance(eos_token, str) or not eos_token:
        raise ValueError("tokenizer 缺少有效的 eos_token，无法严格复现训练序列")

    samples = [
        _measure_sft_row(row, tokenizer=tokenizer, eos_token=eos_token)
        for row in sft_rows
    ]
    samples.extend(
        _measure_preference_row(row, tokenizer=tokenizer, eos_token=eos_token)
        for row in preference_rows
    )
    overlength_details = [
        dict(sample) for sample in samples if sample["length_tokens"] > max_seq_length
    ]
    report = {
        "format": "rsage_tokenizer_length_preflight_v1",
        "passed": not overlength_details,
        "max_seq_length": max_seq_length,
        "sample_count": len(samples),
        "length_tokens": _length_summary(
            [int(sample["length_tokens"]) for sample in samples]
        ),
        "by_kind": {
            "sft": _kind_summary(samples, "sft"),
            "preference": _kind_summary(samples, "preference"),
        },
        "samples": samples,
        "overlength_count": len(overlength_details),
        "overlength_ids": [str(sample["id"]) for sample in overlength_details],
        "overlength_details": overlength_details,
    }
    report["message_zh"] = _chinese_summary(report)
    if overlength_details:
        raise RsageLengthPreflightError(report)
    return report


def _measure_sft_row(
    row: Mapping[str, Any],
    *,
    tokenizer: Any,
    eos_token: str,
) -> dict[str, Any]:
    row_id = _row_id(row, "SFT")
    if "messages" not in row:
        raise ValueError(f"SFT 样本 {row_id} 缺少 messages")
    messages = _message_list(row["messages"], row_id=row_id, field="messages")
    if not messages or messages[-1].get("role") != "assistant":
        raise ValueError(f"SFT 样本 {row_id} 必须以 assistant 消息结束")

    prompt_text = _render_generation_prompt(tokenizer, messages[:-1], row_id=row_id)
    completion = str(messages[-1].get("content", ""))
    length = _token_count(tokenizer, prompt_text + _with_eos(completion, eos_token), row_id)
    return {
        "id": row_id,
        "kind": "sft",
        "length_tokens": length,
    }


def _measure_preference_row(
    row: Mapping[str, Any],
    *,
    tokenizer: Any,
    eos_token: str,
) -> dict[str, Any]:
    row_id = _row_id(row, "偏好")
    if "prompt" not in row:
        raise ValueError(f"偏好样本 {row_id} 缺少 prompt")
    prompt = _message_list(row["prompt"], row_id=row_id, field="prompt")
    for field in ("chosen", "rejected"):
        if field not in row:
            raise ValueError(f"偏好样本 {row_id} 缺少 {field}")

    prompt_text = _render_generation_prompt(tokenizer, prompt, row_id=row_id)
    chosen_length = _token_count(
        tokenizer,
        prompt_text + _with_eos(str(row["chosen"]), eos_token),
        row_id,
    )
    rejected_length = _token_count(
        tokenizer,
        prompt_text + _with_eos(str(row["rejected"]), eos_token),
        row_id,
    )
    return {
        "id": row_id,
        "kind": "preference",
        "length_tokens": max(chosen_length, rejected_length),
        "chosen_length_tokens": chosen_length,
        "rejected_length_tokens": rejected_length,
    }


def _row_id(row: Mapping[str, Any], kind: str) -> str:
    row_id = str(row.get("id", "")).strip()
    if not row_id:
        raise ValueError(f"{kind}样本缺少非空 id")
    return row_id


def _message_list(value: Any, *, row_id: str, field: str) -> list[Mapping[str, Any]]:
    if isinstance(value, (str, bytes)) or not isinstance(value, Sequence):
        raise ValueError(f"样本 {row_id} 的 {field} 必须是消息数组")
    messages = list(value)
    if any(not isinstance(message, Mapping) for message in messages):
        raise ValueError(f"样本 {row_id} 的 {field} 含非对象消息")
    return messages


def _render_generation_prompt(tokenizer: Any, messages: Sequence[Mapping[str, Any]], *, row_id: str) -> str:
    try:
        return str(
            tokenizer.apply_chat_template(
                list(messages),
                tokenize=False,
                add_generation_prompt=True,
            )
        )
    except Exception as exc:
        raise ValueError(f"样本 {row_id} 无法应用训练 chat template：{exc}") from exc


def _with_eos(text: str, eos_token: str) -> str:
    return text if text.endswith(eos_token) else text + eos_token


def _token_count(tokenizer: Any, text: str, row_id: str) -> int:
    try:
        encoded = tokenizer(text=text)
    except Exception as exc:
        raise ValueError(f"样本 {row_id} 无法按训练 tokenizer 编码：{exc}") from exc
    input_ids = encoded.get("input_ids") if isinstance(encoded, Mapping) else getattr(encoded, "input_ids", None)
    if input_ids is None:
        raise ValueError(f"样本 {row_id} 的 tokenizer 结果缺少 input_ids")
    if isinstance(input_ids, Sequence) and input_ids and isinstance(input_ids[0], Sequence):
        if len(input_ids) != 1:
            raise ValueError(f"样本 {row_id} 的 tokenizer 返回了多个序列")
        input_ids = input_ids[0]
    try:
        return len(input_ids)
    except TypeError as exc:
        raise ValueError(f"样本 {row_id} 的 input_ids 不可计数") from exc


def _kind_summary(samples: Sequence[Mapping[str, Any]], kind: str) -> dict[str, Any]:
    lengths = [int(sample["length_tokens"]) for sample in samples if sample["kind"] == kind]
    return {
        "sample_count": len(lengths),
        **_length_summary(lengths),
    }


def _length_summary(lengths: Sequence[int]) -> dict[str, int | float | None]:
    if not lengths:
        return {"min": None, "max": None, "p50": None, "p95": None}
    return {
        "min": min(lengths),
        "max": max(lengths),
        "p50": _percentile(lengths, 50),
        "p95": _percentile(lengths, 95),
    }


def _percentile(values: Sequence[int], percentile: int) -> float:
    ordered = sorted(values)
    if len(ordered) == 1:
        return float(ordered[0])
    rank = (len(ordered) - 1) * percentile / 100
    lower = math.floor(rank)
    upper = math.ceil(rank)
    value = ordered[lower] + (ordered[upper] - ordered[lower]) * (rank - lower)
    return round(float(value), 2)


def _chinese_summary(report: Mapping[str, Any]) -> str:
    stats = report["length_tokens"]
    if report["passed"]:
        return (
            f"R-SAGE 长度预检通过：{report['sample_count']} 个样本均未超过 "
            f"max_seq_length={report['max_seq_length']}；最短 {stats['min']} token，"
            f"最长 {stats['max']} token，P50={stats['p50']}，P95={stats['p95']}。"
        )
    ids = "、".join(report["overlength_ids"])
    return (
        f"R-SAGE 长度预检失败：{report['overlength_count']} 个样本超过 "
        f"max_seq_length={report['max_seq_length']}；超长 ID：{ids}；"
        f"实测最长 {stats['max']} token。未执行截断，必须停止训练。"
    )
