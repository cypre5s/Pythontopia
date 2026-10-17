"""B站视频「基于学生问题的概述 + 关键时间点」契约 schema（阶段0 冻结）。

链路：前端在工坊点视频卡「AI 讲解 / 看点」→ 后端 ``GET/POST /api/videos/bilibili/insight``
代理 → graph ``POST /agents/video/bilibili/insight``。graph 取视频真实元数据（标题 / 简介 /
分 P pages / 官方 AI 总结 outline）+ 学生画像，由 ``VideoInsightAgent`` 产出概述与关键时间点。

防幻觉红线（与"不伪造课件页码"同级）：``key_points`` 的 ``t_sec`` **只能**来自真实
outline / 分 P 时间轴；``timeline_grounding`` 节点剔除任何不在真实时间集合里的时间点。拿不到
真实时间轴时 ``key_points`` 为空、仅给 ``overview``（**绝不编造秒数**）。
"""

from __future__ import annotations

from typing import Annotated, Literal

from pydantic import BaseModel, Field

from pythontopia_graph.schemas.resource import ProfileSnapshot


class VideoInsightRequest(BaseModel):
    """单个 B 站视频的概述请求。"""

    bvid: str = Field(min_length=1, max_length=32)
    # 学生的问题 / 学习意图：概述与看点排序都围绕它展开（空则给中性概述）。
    question: str = Field(default="", max_length=512)
    kc_codes: list[Annotated[str, Field(max_length=32)]] = Field(
        default_factory=list, max_length=10
    )
    # 画像快照：影响概述措辞、看点优先级（复用资源生成同一 ProfileSnapshot）。
    profile_snapshot: ProfileSnapshot = Field(default_factory=ProfileSnapshot)


class VideoKeyPoint(BaseModel):
    """一个关键时间点（可点击跳 ``https://www.bilibili.com/video/{bvid}?t={t_sec}``）。"""

    # 跳转秒数：必须来自真实 outline / 分 P 时间轴（防幻觉，timeline_grounding 强校验）。
    t_sec: int = Field(ge=0)
    # 该时间点讲了什么（基于真实 outline 文本概括，不编造）。
    label: str = Field(min_length=1, max_length=120)
    # 时间点来源（审计用）：ai_conclusion=官方 AI 总结；pages=分 P。
    source: Literal["ai_conclusion", "pages"]


class VideoInsightResponse(BaseModel):
    """视频概述结果。"""

    bvid: str
    # 针对学生问题的简要概述（这视频讲了啥 / 是否对口 / 建议看哪段）。
    overview: str = Field(default="")
    # 关键时间点（真实时间轴；拿不到则为空，不编造）。
    key_points: list[VideoKeyPoint] = Field(default_factory=list)
    # 概述依据：ai_conclusion / pages / metadata_only（仅标题简介、无可用时间轴）。
    based_on: Literal["ai_conclusion", "pages", "metadata_only"] = "metadata_only"
    # best-effort 降级标记：LLM 不可用 / 取数失败时 True，前端给中性占位、不伪造。
    degraded: bool = False
