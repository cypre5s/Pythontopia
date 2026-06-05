// 「B站精选讲解」外部视频卡片前端访问层。
//
// 后端端点（BilibiliVideoController，源无关，需鉴权 / axios 带 JWT）：
//   GET /api/videos/bilibili?keyword=...&limit=8  → BilibiliVideoDto[]
// 取数实现在 pythontopia-graph（bilibili-api-python，GPL，隔离在 Python 服务）。
//
// 设计：纯展示型外部资源（封面缩略图 + 跳转 B 站），不进课件 grounding。任何失败
// （网络 / 风控 / 后端未起）都降级成空数组，调用方据此显示占位文案、绝不白屏。

import { ApiClientError, http } from './http';

export interface BilibiliVideoDto {
  /** B 站视频稳定标识，如 `BV1xx411c7XD`。 */
  bvid: string;
  /** 标题（后端已去除 `<em>` 高亮标签）。 */
  title: string;
  /** UP 主昵称。 */
  author: string;
  /**
   * UP 主填写的视频简介（后端已去标签 / 反转义 / 截断），作为「这个视频讲什么」的真实讲解。
   * UP 主未填写时为空串（卡片给中性占位，不伪造内容）。
   */
  description: string;
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

/** 按关键词检索 B 站视频；空关键词返回 []；服务不可用抛错供卡片展示错误占位。 */
export async function searchBilibili(keyword: string, limit = 8): Promise<BilibiliVideoDto[]> {
  const trimmed = keyword.trim();
  if (!trimmed) {
    return [];
  }
  try {
    const res = await http.get<BilibiliVideoDto[]>('/api/videos/bilibili', {
      params: { keyword: trimmed, limit },
      suppressNetworkToast: true,
    });
    return Array.isArray(res.data) ? res.data : [];
  } catch (err) {
    // 检索服务明确不可用时不要静默伪装成「暂无结果」。
    if (err instanceof ApiClientError && err.code === 'VIDEO_SEARCH_UNAVAILABLE') {
      throw err;
    }
    return [];
  }
}
