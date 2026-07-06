from __future__ import annotations

from pydantic import BaseModel, Field


class EmbedRequest(BaseModel):
    # 批量上限保护：记忆/费曼场景单次向量化文本数有限，避免超大请求拖垮单 worker 服务。
    texts: list[str] = Field(default_factory=list, max_length=64)


class EmbedResponse(BaseModel):
    model: str
    dim: int
    vectors: list[list[float]] = Field(default_factory=list)
