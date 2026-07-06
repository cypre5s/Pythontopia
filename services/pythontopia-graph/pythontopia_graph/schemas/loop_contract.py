"""Frozen runtime loop contract.

This module captures the five shared objects from the next-gen Agentic report:
EventEnvelope, LoopCheckpoint, AgentToolCall, LoopResult, and EvalRecord.
They are intentionally small Pydantic models so graph/runtime code, AG-UI
emission, and offline eval gates can validate the same audit fields.
"""

from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field

SCHEMA_VERSION = "loop-contract.v1"

LoopType = Literal["LEARNER", "DEBUG", "RESOURCE", "WORLD", "IMPROVE"]
LoopRunStatus = Literal["SUCCESS", "PARTIAL", "FAILED", "REJECTED", "NO_CHANGE"]
ToolCallStatus = Literal["SUCCESS", "FAILED", "TIMEOUT", "SKIPPED"]


class ContractModel(BaseModel):
    """Base config: top-level contract objects reject accidental field drift."""

    model_config = ConfigDict(extra="forbid")


class EvidenceRef(ContractModel):
    """A compact reference to a real learning or source artifact."""

    type: str = Field(min_length=1, max_length=64)
    id: int | str
    label: str = Field(default="", max_length=200)


class LoopScore(ContractModel):
    """A numeric metric attached to loop results or eval records."""

    name: str = Field(min_length=1, max_length=128)
    value: float


class EventEnvelope(ContractModel):
    """Normalized observation event handed to a loop."""

    event_id: str = Field(min_length=1, max_length=128)
    event_type: str = Field(min_length=1, max_length=128)
    user_id: int = Field(ge=1)
    session_id: str = Field(min_length=1, max_length=128)
    trace_id: str = Field(min_length=1, max_length=128)
    timestamp: str = Field(min_length=1, max_length=64)
    context: dict[str, Any] = Field(default_factory=dict)
    payload: dict[str, Any] = Field(default_factory=dict)
    evidence_refs: list[EvidenceRef]
    schema_version: str = Field(default=SCHEMA_VERSION, pattern=r"^loop-contract\.v\d+$")


class LoopCheckpoint(ContractModel):
    """Recoverable loop execution checkpoint."""

    loop_id: str = Field(min_length=1, max_length=128)
    loop_type: LoopType
    thread_id: str = Field(min_length=1, max_length=160)
    policy_version: str = Field(min_length=1, max_length=128)
    step_index: int = Field(ge=0)
    state: dict[str, Any] = Field(default_factory=dict)
    pending_human_input: bool = False
    resume_token: str = Field(default="", max_length=256)
    expires_at: str = Field(min_length=1, max_length=64)
    schema_version: str = Field(default=SCHEMA_VERSION, pattern=r"^loop-contract\.v\d+$")


class AgentToolCall(ContractModel):
    """Auditable tool invocation in a loop run."""

    run_id: str = Field(min_length=1, max_length=128)
    tool_name: str = Field(min_length=1, max_length=128)
    args_hash: str = Field(min_length=1, max_length=128)
    result_ref: str = Field(default="", max_length=256)
    timeout_ms: int = Field(ge=0)
    status: ToolCallStatus = "SUCCESS"
    latency_ms: int = Field(default=0, ge=0)
    schema_version: str = Field(default=SCHEMA_VERSION, pattern=r"^loop-contract\.v\d+$")


class LoopResult(ContractModel):
    """Terminal loop result carried by AG-UI RUN_FINISHED.result."""

    run_id: str = Field(min_length=1, max_length=128)
    loop_type: LoopType
    status: LoopRunStatus
    result: dict[str, Any]
    scores: list[LoopScore]
    artifacts: list[dict[str, Any]]
    next_actions: list[str]
    policy_version: str = Field(min_length=1, max_length=128)
    trace_id: str = Field(min_length=1, max_length=128)
    evidence_refs: list[EvidenceRef]
    schema_version: str = Field(pattern=r"^loop-contract\.v\d+$")


class EvalRecord(ContractModel):
    """Offline/online evaluation record for a policy and dataset item."""

    dataset_id: str = Field(min_length=1, max_length=128)
    item_id: str = Field(min_length=1, max_length=128)
    loop_type: LoopType
    policy_version: str = Field(min_length=1, max_length=128)
    trace_id: str = Field(min_length=1, max_length=128)
    metrics: list[LoopScore]
    passed: bool
    schema_version: str = Field(pattern=r"^loop-contract\.v\d+$")


def validate_loop_result_payload(payload: LoopResult | dict[str, Any]) -> LoopResult:
    """Validate a terminal loop result before streaming or persisting it."""

    if isinstance(payload, LoopResult):
        return payload
    return LoopResult.model_validate(payload)
