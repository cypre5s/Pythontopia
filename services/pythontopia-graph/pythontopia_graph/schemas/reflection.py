"""ReflectionAgent（学情反思器）的结构化输出 schema。

记忆条目来自后端记忆流（learning_event_log）；ReflectionAgent 据此综合学习建议，
每条建议强制引用输入里出现过的 event_id（防幻觉），dimension_key 仅限 error_pattern。
Insight 的字段约束本身即第一道过滤闸（空证据/非法维度
直接触发 ValidationError 而被丢弃）。
"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field

# Reflection outputs are recommendations only and may describe observed error patterns.
InsightDimensionKey = Literal["error_pattern"]


class MemoryItem(BaseModel):
    """单条记忆（后端检索的 learning_event_log 行投影）。"""

    event_id: int
    event_type: str
    kc_codes: list[str] = Field(default_factory=list)
    importance: int = Field(default=1, ge=1, le=5)
    created_at: str
    summary: str = ""


class Insight(BaseModel):
    """一条 grounded 学情洞察；evidence_event_ids 必须非空（强制引用证据）。"""

    text: str = Field(min_length=2, max_length=200)
    dimension_key: InsightDimensionKey
    tags: list[str] = Field(default_factory=list)
    evidence_event_ids: list[int] = Field(min_length=1)
    confidence: float = Field(default=0.5, ge=0.0, le=1.0)


class ReflectionInput(BaseModel):
    user_id: int
    memories: list[MemoryItem] = Field(default_factory=list)


class ReflectionOutput(BaseModel):
    insights: list[Insight] = Field(default_factory=list)
