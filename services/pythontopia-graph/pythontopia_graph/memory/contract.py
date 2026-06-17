"""记忆 brief 冻结契约（Phase 0 预置）。

后端 `/api/learner/memory/brief` 聚合产出（M1），graph 侧 brief_client（M2）取回，
S1 经 ``OrchestratorRequest.context['memory_brief']`` 消费。只读引用，禁止重定义。
"""

from __future__ import annotations

from pydantic import BaseModel, Field


class MemoryBrief(BaseModel):
    # 时序压缩后的稳定事实（Zep 时序图 / Mem0 召回）。
    facts: list[str] = Field(default_factory=list)
    weak_kcs: list[str] = Field(default_factory=list)  # 近期反复受阻 KC
    style_notes: list[str] = Field(default_factory=list)  # 认知风格 / 偏好（来自 8 维画像）
    as_of: str = ""  # ISO 时间戳；体现时序，缺省空串
