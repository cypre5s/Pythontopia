"""DebugAgent 结构化输出 schema。"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field, model_validator


class TraceStep(BaseModel):
    line: int = Field(ge=0)
    locals: dict[str, str] = Field(default_factory=dict)


class VariableTrace(BaseModel):
    steps: list[TraceStep] = Field(default_factory=list, max_length=200)
    truncated: bool = False


class CounterExampleCard(BaseModel):
    stdin: str = Field(max_length=2048)
    expected_stdout: str = Field(max_length=2048)
    your_stdout: str = Field(max_length=2048)
    diff_summary: str = Field(max_length=512)
    redacted: bool = False


class WrongAssumptionCard(BaseModel):
    you_thought: str = Field(max_length=512)
    actually: str = Field(max_length=512)
    evidence_kc_codes: list[str] = Field(default_factory=list)


class HintLayer(BaseModel):
    layer: Literal[1, 2, 3]
    headline: str = Field(max_length=200)
    body: str = Field(max_length=1200)
    related_pages: list[int] = Field(default_factory=list)


class RetryAdvice(BaseModel):
    summary: str = Field(max_length=512)
    expected_kc_codes: list[str] = Field(default_factory=list)


class DebugAgentInput(BaseModel):
    user_id: int = Field(ge=1)
    submission_id: int = Field(ge=1)
    problem_id: int = Field(ge=1)
    kc_codes: list[str] = Field(default_factory=list)
    verdict: Literal["WA", "RE", "TLE", "CE", "MLE", "SYSTEM_ERROR"]
    code: str = Field(max_length=8192)
    counter_example: CounterExampleCard
    variable_trace: VariableTrace = Field(default_factory=VariableTrace)
    rag_pages: list[int] = Field(default_factory=list)


class DebugAgentOutput(BaseModel):
    counter_example: CounterExampleCard
    variable_trace_summary: VariableTrace
    wrong_assumption: WrongAssumptionCard
    hint_layers: list[HintLayer]
    retry_advice: RetryAdvice
    safety_passed: bool = True
    safety_reasons: list[str] = Field(default_factory=list)

    @model_validator(mode="after")
    def _three_hint_layers(self) -> "DebugAgentOutput":
        layers = sorted({layer.layer for layer in self.hint_layers})
        if layers != [1, 2, 3]:
            raise ValueError("DebugAgent must emit exactly three hint layers (1,2,3)")
        return self
