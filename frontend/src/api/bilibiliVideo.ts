// 「B站精选讲解」外部视频卡片前端访问层。
//
// 后端端点（BilibiliVideoController，源无关，需鉴权 / axios 带 JWT）：
//   GET /api/videos/bilibili?keyword=...&limit=8  → BilibiliVideoDto[]
// 取数实现在 pythontopia-graph（bilibili-api-python，GPL，隔离在 Python 服务）。
//
// 设计：纯展示型外部资源（封面缩略图 + 跳转 B 站），不进课件 grounding。
// 失败语义：空关键词 → []；其余任何失败（网络 / 鉴权过期 / 风控 / 后端未起）一律抛错，
// 由卡片显示「检索暂不可用 + 重试」。绝不把失败伪装成「暂无结果」（吞错会误导学生换关键词）。

import { http } from './http';

export interface BilibiliVideoDto {
  /** B 站视频稳定标识，如 `BV1xx411c7XD`。 */
  bvid: string;
  /** 标题（后端已去除 `<em>` 高亮标签）。 */
  title: string;
  /** UP 主昵称。 */
  author: string;
  /**
   * UP 主填写的视频简介（后端已去标签 / 反转义 / 截断）。仅作为后端生成 `aiSummary` 的素材，
   * **前端不再直接展示**（保留字段以备调试）。
   */
  description: string;
  /**
   * 「AI 速览」一句话：后端按 标题 + 简介 + 时长 让 LLM 概括「这视频讲了啥 / 为什么值得看」，
   * 只概括已有信息、防幻觉。LLM 不可用 / 超时时为空串（卡片给中性占位并标注 AI，不伪造）。
   */
  aiSummary: string;
  /** 封面缩略图（已统一 `https:`，配合 `referrerpolicy=no-referrer` 规避防盗链）。 */
  coverUrl: string;
  /** 时长（秒）。 */
  durationSec: number;
  /** 播放量。 */
  play: number;
  /** 弹幕数。 */
  danmaku: number;
  /** 发布时间（Unix 秒）。 */
  pubdate: number;
  /** 视频外链 `https://www.bilibili.com/video/{bvid}`。 */
  url: string;
}

/** 按关键词检索 B 站视频；空关键词返回 []；任何失败抛错（卡片显示错误占位 + 重试）。 */
export async function searchBilibili(keyword: string, limit = 8): Promise<BilibiliVideoDto[]> {
  const trimmed = keyword.trim();
  if (!trimmed) {
    return [];
  }
  const res = await http.get<BilibiliVideoDto[]>('/api/videos/bilibili', {
    params: { keyword: trimmed, limit },
    suppressNetworkToast: true,
  });
  return Array.isArray(res.data) ? res.data : [];
}
