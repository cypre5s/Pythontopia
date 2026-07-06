"""BROWSE 工具冻结契约（Phase 0 预置）。

B1 实现 executor、S1 调用、B2 落库、F 渲染，均**只读引用本文件**，禁止各自重定义。
所有字段向后兼容；新增一律可选 + 默认值。外部抓取内容只进带标签 context，不进课件引用。
"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field

BrowseAction = Literal["navigate", "read", "search", "stop"]


class BrowseRequest(BaseModel):
    goal: str = Field(min_length=1, max_length=500)
    # 仅安全墙 allowlist 交集（§3.4）；空 = 拒绝发起。
    allowed_domains: list[str] = Field(default_factory=list)
    max_steps: int = Field(default=6, ge=1, le=12)  # 有界
    user_id: int = Field(ge=1)


class BrowseStep(BaseModel):
    index: int = Field(ge=0)
    action: BrowseAction
    url: str = ""
    summary: str = ""  # 该步可读摘要（去敏，不含原始 DOM / 脚本 / 隐藏域 / PII）


class BrowseResult(BaseModel):
    steps: list[BrowseStep] = Field(default_factory=list)
    final_summary: str = ""
    # 命中 url，带标签注入 context（"外部内容，非课件"），不进课件引用。
    citations: list[str] = Field(default_factory=list)
    truncated: bool = False  # 触 max_steps
    error: str = ""  # fail-fast 时填；steps 仍返回已走部分
