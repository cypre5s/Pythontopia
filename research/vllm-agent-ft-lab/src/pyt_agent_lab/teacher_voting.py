from __future__ import annotations

import json
import os
import re
import statistics
import time
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Mapping, Protocol, Sequence

from .baseline import OpenAICompatClient
from .rsage_data import EVENT_INTENTS, ValidationResult, stable_fingerprint, validate_teacher_vote


REVIEWER_ROLES = (
    "教学策略审查者",
    "课程证据审查者",
    "安全边界审查者",
)

_SECRET_PATTERNS = (
    re.compile(r"(?i)(bearer\s+)[A-Za-z0-9._~+/=-]{8,}"),
    re.compile(r"\bsk-[A-Za-z0-9_-]{6,}\b"),
)


class TeacherBackend(Protocol):
    model_name: str

    def review(self, case: Mapping[str, Any], reviewer_role: str, attempt: int) -> str:
        """返回一位教师对单个案例的 JSON 投票。"""


@dataclass(frozen=True)
class CheckedTeacherResponse:
    parsed_vote: dict[str, Any] | None
    validation: ValidationResult


@dataclass(frozen=True)
class TeacherVotingSummary:
    total_count: int
    processed_count: int
    accepted_count: int
    excluded_count: int
    skipped_count: int
    request_count: int

    @property
    def message_zh(self) -> str:
        return (
            f"教师投票完成：处理 {self.processed_count} 条，通过 {self.accepted_count} 条，"
            f"排除 {self.excluded_count} 条，断点跳过 {self.skipped_count} 条，"
            f"共发起 {self.request_count} 次教师请求"
        )


class OpenAICompatibleTeacherBackend:
    def __init__(
        self,
        *,
        base_url: str,
        api_key: str,
        model: str,
        timeout: int = 180,
        transport_retries: int = 2,
        proxy_url: str | None = None,
        max_tokens: int = 2400,
    ) -> None:
        self.model_name = model
        self._max_tokens = max_tokens
        self._client = OpenAICompatClient(
            base_url,
            api_key,
            model,
            timeout=timeout,
            retries=transport_retries,
            proxy_url=proxy_url,
        )

    def review(self, case: Mapping[str, Any], reviewer_role: str, attempt: int) -> str:
        del attempt
        return self._client.chat(
            build_teacher_messages(case, reviewer_role),
            temperature=0.15,
            max_tokens=self._max_tokens,
            response_format={"type": "json_object"},
            enable_thinking=False,
        )


class FixtureTeacherBackend:
    """用于脚本演示和单元测试的确定性教师，不代表真实教师效果。"""

    model_name = "固定规则教师-演示版"

    def review(self, case: Mapping[str, Any], reviewer_role: str, attempt: int) -> str:
        del attempt
        preferred = str(case.get("event_intent") or "SCAFFOLD")
        if preferred not in EVENT_INTENTS:
            preferred = "SCAFFOLD"
        secondary = "ASK" if preferred != "ASK" else "SCAFFOLD"
        forbidden = next(intent for intent in EVENT_INTENTS if intent not in {preferred, secondary})
        evidence_ids = [
            str(item["evidence_id"])
            for item in case.get("evidence_refs", [])
            if isinstance(item, Mapping) and item.get("evidence_id")
        ]
        role_index = REVIEWER_ROLES.index(reviewer_role) if reviewer_role in REVIEWER_ROLES else 0
        vote = {
            "misconception_type": f"{case.get('failure_axis', '教学动作')}对应的初学者误区",
            "action_goal": f"围绕{case.get('kc_name', case.get('kc_code', '当前知识点'))}给出最小充分教学动作",
            "admissible_intents": [preferred, secondary],
            "preferred_intent": preferred,
            "forbidden_intents": [forbidden],
            "preference_confidence": round(0.78 + role_index * 0.04, 2),
            "hint_level": min(role_index, 2),
            "evidence_refs": evidence_ids[:2],
            "answer_leak": False,
            "safety_flags": [],
            "rationale": f"{reviewer_role}依据课程证据和最小提示原则完成演示投票。",
        }
        return json.dumps(vote, ensure_ascii=False, sort_keys=True)


def build_teacher_messages(case: Mapping[str, Any], reviewer_role: str) -> list[dict[str, str]]:
    schema = {
        "misconception_type": "非空中文字符串",
        "action_goal": "非空中文字符串",
        "admissible_intents": ["七类教学动作中的一至三项"],
        "preferred_intent": "七类教学动作中的一项",
        "forbidden_intents": ["七类教学动作中的零至多项"],
        "preference_confidence": "0.5 至 1.0",
        "hint_level": "0 至 3 的整数",
        "evidence_refs": ["只能从案例白名单选择"],
        "answer_leak": False,
        "safety_flags": [],
        "rationale": "仅供审计的简短依据",
    }
    bounded_case = {
        "case_id": case.get("case_id"),
        "chapter_id": case.get("chapter_id"),
        "kc_code": case.get("kc_code"),
        "kc_name": case.get("kc_name"),
        "kc_summary": case.get("kc_summary"),
        "student_input": case.get("student_input"),
        "candidate_intent": case.get("event_intent"),
        "failure_axis": case.get("failure_axis"),
        "learner_profile": case.get("learner_profile", {}),
        "evidence_refs": [
            {
                "evidence_id": item.get("evidence_id"),
                "excerpt": str(item.get("excerpt", ""))[:700],
            }
            for item in case.get("evidence_refs", [])
            if isinstance(item, Mapping)
        ],
    }
    return [
        {
            "role": "system",
            "content": (
                "你是 Pythontopia 的独立中文教师评审，当前角色是“"
                f"{reviewer_role}”。只评审下一教学动作，不解答学生题目，不生成可直接提交的答案，"
                "不猜测隐藏测试。只能引用案例提供的课程证据编号。"
                f"教学动作全集为：{', '.join(EVENT_INTENTS)}。"
                "请只输出一个 JSON 对象，不要输出 Markdown。"
            ),
        },
        {
            "role": "user",
            "content": (
                "请独立标注下面案例。输出字段约束：\n"
                f"{json.dumps(schema, ensure_ascii=False, sort_keys=True)}\n"
                "案例：\n"
                f"{json.dumps(bounded_case, ensure_ascii=False, sort_keys=True)}"
            ),
        },
    ]


def verify_teacher_response(raw_response: str, case: Mapping[str, Any]) -> CheckedTeacherResponse:
    try:
        parsed = _parse_json_object(raw_response)
    except (TypeError, ValueError, json.JSONDecodeError) as exc:
        return CheckedTeacherResponse(
            parsed_vote=None,
            validation=ValidationResult(False, (f"教师响应无法解析为 JSON 对象：{exc}",)),
        )

    validation = validate_teacher_vote(parsed, case)
    extra_errors = list(validation.errors)
    audit_text = " ".join(
        str(parsed.get(key, "")) for key in ("action_goal", "rationale", "misconception_type")
    )
    if "```" in audit_text:
        extra_errors.append("教师审计文本包含代码块，存在直接答案泄露风险")
    if len(raw_response) > 12000:
        extra_errors.append("教师响应长度超过审计上限")
    return CheckedTeacherResponse(
        parsed_vote=parsed,
        validation=ValidationResult(not extra_errors, tuple(extra_errors)),
    )


def aggregate_teacher_votes(
    case: Mapping[str, Any],
    vote_records: Sequence[Mapping[str, Any]],
    *,
    case_prompt_fingerprint: str | None = None,
) -> dict[str, Any]:
    case_id = str(case.get("case_id") or "未知案例")
    valid_records = [
        record
        for record in vote_records
        if isinstance(record.get("parsed_vote"), Mapping)
        and isinstance(record.get("verifier"), Mapping)
        and record["verifier"].get("passed") is True
    ]
    common = {
        "format": "rsage_teacher_label_v1",
        "case_id": case_id,
        "case_prompt_fingerprint": case_prompt_fingerprint or _case_prompt_fingerprint(case),
        "valid_vote_count": len(valid_records),
        "total_reviewer_count": len(vote_records),
        "reviewer_roles": [str(record.get("reviewer_role", "")) for record in vote_records],
    }
    if len(valid_records) < 2:
        return {
            **common,
            "status": "excluded",
            "exclusion_code": "insufficient_valid_votes",
            "message_zh": f"案例 {case_id} 因有效教师投票不足两票而排除",
        }

    votes = [dict(record["parsed_vote"]) for record in valid_records]
    counts = Counter(str(vote["preferred_intent"]) for vote in votes)
    top_count = max(counts.values())
    winners = sorted(intent for intent, count in counts.items() if count == top_count)
    if len(winners) != 1 or top_count < 2:
        return {
            **common,
            "status": "excluded",
            "exclusion_code": "unresolved_tie",
            "vote_counts": dict(sorted(counts.items())),
            "message_zh": f"案例 {case_id} 因教师首选动作平票且无法形成多数而排除",
        }

    preferred = winners[0]
    majority_votes = [vote for vote in votes if vote["preferred_intent"] == preferred]
    representative = max(
        majority_votes,
        key=lambda vote: (float(vote.get("preference_confidence", 0.5)), json.dumps(vote, sort_keys=True)),
    )
    threshold = len(votes) // 2 + 1
    admissible = _majority_list(votes, "admissible_intents", threshold)
    if preferred not in admissible:
        admissible.insert(0, preferred)
    forbidden = [
        intent
        for intent in _majority_list(votes, "forbidden_intents", threshold)
        if intent not in admissible
    ]
    evidence_refs = _majority_list(votes, "evidence_refs", threshold)
    if not evidence_refs:
        evidence_refs = list(representative.get("evidence_refs", []))
    confidence = statistics.fmean(float(vote["preference_confidence"]) for vote in majority_votes)
    hint_level = int(statistics.median_low(int(vote["hint_level"]) for vote in majority_votes))
    return {
        **common,
        "status": "accepted",
        "misconception_type": representative["misconception_type"],
        "action_goal": representative["action_goal"],
        "admissible_intents": admissible,
        "preferred_intent": preferred,
        "forbidden_intents": forbidden,
        "preference_q": top_count / len(votes),
        "mean_majority_confidence": round(confidence, 6),
        "hint_level": hint_level,
        "evidence_refs": evidence_refs,
        "answer_leak": False,
        "safety_flags": sorted(
            {
                str(flag)
                for vote in votes
                for flag in vote.get("safety_flags", [])
                if str(flag)
            }
        ),
        "vote_counts": dict(sorted(counts.items())),
        "message_zh": f"案例 {case_id} 已通过三教师多数投票",
    }


def run_teacher_voting(
    cases: Sequence[Mapping[str, Any]],
    backend: TeacherBackend,
    *,
    raw_votes_path: str | Path,
    labels_path: str | Path,
    max_attempts: int = 3,
    retry_base_seconds: float = 1.0,
    retry_max_seconds: float = 8.0,
    resume: bool = True,
    reviewer_roles: Sequence[str] = REVIEWER_ROLES,
    secret_values: Sequence[str] = (),
    event_callback: Callable[[Mapping[str, Any]], None] | None = None,
    reviewer_concurrency: int = 1,
) -> TeacherVotingSummary:
    if max_attempts <= 0:
        raise ValueError("每位教师的最大尝试次数必须为正整数")
    if len(reviewer_roles) != 3 or len(set(reviewer_roles)) != 3:
        raise ValueError("教师投票必须配置三个互不重复的评审角色")
    if reviewer_concurrency < 1 or reviewer_concurrency > len(reviewer_roles):
        raise ValueError("教师并发数必须在 1 到 3 之间")

    raw_path = Path(raw_votes_path)
    label_path = Path(labels_path)
    completed = load_completed_case_fingerprints(label_path) if resume else {}
    accepted_count = 0
    excluded_count = 0
    skipped_count = 0
    processed_count = 0
    request_count = 0

    for case in cases:
        case_id = str(case.get("case_id") or "")
        case_fingerprint = _case_prompt_fingerprint(
            case,
            reviewer_roles=reviewer_roles,
            teacher_model=str(backend.model_name),
        )
        if case_id in completed:
            previous_fingerprint = completed[case_id]
            if previous_fingerprint != case_fingerprint:
                raise ValueError(
                    f"断点续跑案例 {case_id} 的提示指纹发生变化，拒绝复用旧教师标签"
                )
            skipped_count += 1
            _emit(
                event_callback,
                {
                    "event": "case_skipped",
                    "case_id": case_id,
                    "message_zh": f"断点续跑：已跳过完成案例 {case_id}",
                },
            )
            continue

        if reviewer_concurrency == 1:
            role_results = [
                _review_role_with_retries(
                    case,
                    role,
                    backend,
                    max_attempts=max_attempts,
                    retry_base_seconds=retry_base_seconds,
                    retry_max_seconds=retry_max_seconds,
                    secret_values=secret_values,
                )
                for role in reviewer_roles
            ]
        else:
            with ThreadPoolExecutor(
                max_workers=reviewer_concurrency,
                thread_name_prefix="rsage-teacher",
            ) as executor:
                futures = {
                    role: executor.submit(
                        _review_role_with_retries,
                        case,
                        role,
                        backend,
                        max_attempts=max_attempts,
                        retry_base_seconds=retry_base_seconds,
                        retry_max_seconds=retry_max_seconds,
                        secret_values=secret_values,
                    )
                    for role in reviewer_roles
                }
                role_results = [futures[role].result() for role in reviewer_roles]

        accepted_vote_records: list[dict[str, Any]] = []
        for records, final_record, role_request_count in role_results:
            request_count += role_request_count
            for record in records:
                append_jsonl(raw_path, record)
                _emit(event_callback, record)
            if final_record is not None:
                accepted_vote_records.append(final_record)

        label = aggregate_teacher_votes(
            case,
            accepted_vote_records,
            case_prompt_fingerprint=case_fingerprint,
        )
        label["timestamp_utc"] = datetime.now(timezone.utc).isoformat()
        label["model"] = str(backend.model_name)
        append_jsonl(label_path, label)
        _emit(event_callback, label)
        processed_count += 1
        if label["status"] == "accepted":
            accepted_count += 1
        else:
            excluded_count += 1

    return TeacherVotingSummary(
        total_count=len(cases),
        processed_count=processed_count,
        accepted_count=accepted_count,
        excluded_count=excluded_count,
        skipped_count=skipped_count,
        request_count=request_count,
    )


def _review_role_with_retries(
    case: Mapping[str, Any],
    role: str,
    backend: TeacherBackend,
    *,
    max_attempts: int,
    retry_base_seconds: float,
    retry_max_seconds: float,
    secret_values: Sequence[str],
) -> tuple[list[dict[str, Any]], dict[str, Any] | None, int]:
    case_id = str(case.get("case_id") or "")
    records: list[dict[str, Any]] = []
    final_record: dict[str, Any] | None = None
    for attempt in range(1, max_attempts + 1):
        started = time.perf_counter()
        raw_response = ""
        parsed_vote: dict[str, Any] | None = None
        errors: tuple[str, ...] = ()
        passed = False
        try:
            raw_response = backend.review(case, role, attempt)
            checked = verify_teacher_response(raw_response, case)
            parsed_vote = checked.parsed_vote
            errors = checked.validation.errors
            passed = checked.validation.passed
        except Exception as exc:  # noqa: BLE001 - provider failures are archived per attempt
            errors = (f"教师请求失败：{redact_secrets(str(exc), secret_values)}",)
        latency_ms = int((time.perf_counter() - started) * 1000)
        final_record = {
            "format": "rsage_teacher_raw_vote_v1",
            "timestamp_utc": datetime.now(timezone.utc).isoformat(),
            "case_id": case_id,
            "reviewer_role": role,
            "attempt": attempt,
            "model": str(backend.model_name),
            "latency_ms": latency_ms,
            "raw_response": redact_secrets(raw_response, secret_values),
            "parsed_vote": parsed_vote,
            "verifier": {"passed": passed, "errors": list(errors)},
            "message_zh": (
                f"案例 {case_id} 的{role}第 {attempt} 次投票已通过核验"
                if passed
                else f"案例 {case_id} 的{role}第 {attempt} 次投票未通过核验"
            ),
        }
        records.append(final_record)
        if passed:
            break
        if attempt < max_attempts and retry_base_seconds > 0:
            time.sleep(min(retry_base_seconds * (2 ** (attempt - 1)), retry_max_seconds))
    return records, final_record, len(records)


def load_completed_case_ids(path: str | Path) -> set[str]:
    return set(load_completed_case_fingerprints(path))


def load_completed_case_fingerprints(path: str | Path) -> dict[str, str]:
    target = Path(path)
    if not target.exists():
        return {}
    completed: dict[str, str] = {}
    for line in target.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        try:
            row = json.loads(line)
        except json.JSONDecodeError:
            continue
        case_id = row.get("case_id") if isinstance(row, Mapping) else None
        if isinstance(case_id, str) and case_id:
            fingerprint = row.get("case_prompt_fingerprint")
            if not isinstance(fingerprint, str) or not fingerprint:
                raise ValueError(f"旧教师标签 {case_id} 缺少案例提示指纹，不能安全断点续跑")
            previous = completed.get(case_id)
            if previous is not None and previous != fingerprint:
                raise ValueError(f"教师标签文件中案例 {case_id} 存在冲突提示指纹")
            completed[case_id] = fingerprint
    return completed


def _case_prompt_fingerprint(
    case: Mapping[str, Any],
    *,
    reviewer_roles: Sequence[str] = REVIEWER_ROLES,
    teacher_model: str = "",
) -> str:
    """绑定教师真正看到的消息、模型和核验契约，不能只信任上游提示指纹。"""

    return stable_fingerprint(
        {
            "format": "rsage_teacher_prompt_identity_v2",
            "upstream_prompt_fingerprint": str(case.get("prompt_fingerprint") or ""),
            "teacher_model": teacher_model,
            "verifier_contract": "rsage_teacher_vote_v1",
            "reviewer_messages": [
                {
                    "reviewer_role": str(role),
                    "messages": build_teacher_messages(case, str(role)),
                }
                for role in reviewer_roles
            ],
        }
    )


def append_jsonl(path: str | Path, row: Mapping[str, Any]) -> None:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(dict(row), ensure_ascii=False, sort_keys=True) + "\n"
    with target.open("a", encoding="utf-8") as handle:
        handle.write(payload)
        handle.flush()
        os.fsync(handle.fileno())


def load_jsonl(path: str | Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for line_number, line in enumerate(Path(path).read_text(encoding="utf-8").splitlines(), start=1):
        if not line.strip():
            continue
        try:
            row = json.loads(line)
        except json.JSONDecodeError as exc:
            raise ValueError(f"第 {line_number} 行不是合法 JSON：{exc}") from exc
        if not isinstance(row, dict):
            raise ValueError(f"第 {line_number} 行必须是 JSON 对象")
        rows.append(row)
    return rows


def load_key_value_env(path: str | Path) -> dict[str, str]:
    target = Path(path)
    if not target.exists():
        return {}
    values: dict[str, str] = {}
    for raw_line in target.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        if line.startswith("export "):
            line = line[7:].lstrip()
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in {'"', "'"}:
            value = value[1:-1]
        if key:
            values[key] = value
    return values


def resolve_teacher_settings(
    env_file: str | Path,
    *,
    base_url: str | None = None,
    api_key: str | None = None,
    model: str | None = None,
) -> dict[str, str]:
    file_values = load_key_value_env(env_file)

    def pick(explicit: str | None, *keys: str) -> str:
        if explicit:
            return explicit
        for key in keys:
            if os.environ.get(key):
                return str(os.environ[key])
            if file_values.get(key):
                return file_values[key]
        return ""

    settings = {
        "base_url": pick(base_url, "PYT_OPENAI_BASE_URL", "OPENAI_BASE_URL"),
        "api_key": pick(api_key, "PYT_OPENAI_API_KEY", "OPENAI_API_KEY"),
        "model": pick(model, "PYT_OPENAI_MODEL", "OPENAI_MODEL"),
    }
    missing = [name for name, value in settings.items() if not value]
    if missing:
        labels = {"base_url": "接口地址", "api_key": "接口密钥", "model": "教师模型"}
        raise ValueError("缺少教师配置：" + "、".join(labels[name] for name in missing))
    return settings


def redact_secrets(value: str, secrets: Sequence[str] = ()) -> str:
    redacted = str(value)
    for secret in sorted({str(item) for item in secrets if str(item)}, key=len, reverse=True):
        redacted = redacted.replace(secret, "[已脱敏]")
    for pattern in _SECRET_PATTERNS:
        if pattern.groups:
            redacted = pattern.sub(lambda match: f"{match.group(1)}[已脱敏]", redacted)
        else:
            redacted = pattern.sub("[已脱敏]", redacted)
    return redacted


def _parse_json_object(raw_response: str) -> dict[str, Any]:
    if not isinstance(raw_response, str) or not raw_response.strip():
        raise ValueError("响应为空")
    text = raw_response.strip()
    if text.startswith("```"):
        lines = text.splitlines()
        if lines and lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        text = "\n".join(lines).strip()
    start = text.find("{")
    end = text.rfind("}")
    if start < 0 or end < start:
        raise ValueError("未找到 JSON 对象边界")
    parsed = json.loads(text[start : end + 1])
    if not isinstance(parsed, dict):
        raise ValueError("响应顶层必须是 JSON 对象")
    return parsed


def _majority_list(votes: Sequence[Mapping[str, Any]], key: str, threshold: int) -> list[str]:
    counts: Counter[str] = Counter()
    for vote in votes:
        values = vote.get(key, [])
        if isinstance(values, list):
            counts.update({str(item) for item in values if str(item)})
    return sorted(item for item, count in counts.items() if count >= threshold)


def _emit(callback: Callable[[Mapping[str, Any]], None] | None, event: Mapping[str, Any]) -> None:
    if callback is not None:
        callback(event)
