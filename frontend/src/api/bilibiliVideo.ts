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

// ───────────────────────────── 视频「概述 + 关键时间点」 ─────────────────────────────
//
// 后端端点（BilibiliInsightController，需鉴权 / axios 带 JWT）：
//   POST /api/videos/bilibili/insight  body={bvid,question,kcCodes} → BilibiliInsightDto
// 取数与 LLM 概述在 pythontopia-graph（VideoInsightAgent + timeline_grounding）。
//
// 防幻觉红线（与「不伪造课件页码」同级）：`keyPoints[].tSec` 只能来自真实
// outline / 分 P 时间轴，后端 `timeline_grounding` 已强校验剔除非法点；这里再做一层防御性
// 归一（丢弃 tSec 非法 / label 为空的脏数据），确保前端**绝不**渲染编造或坏掉的跳转点。

/** 关键时间点来源（审计用）：`ai_conclusion`=官方 AI 总结；`pages`=分 P 时间轴。 */
export type BilibiliKeyPointSource = 'ai_conclusion' | 'pages';

/** 概述依据：`ai_conclusion` / `pages` / `metadata_only`（仅标题简介、无可用时间轴）。 */
export type BilibiliInsightBasis = 'ai_conclusion' | 'pages' | 'metadata_only';

export interface BilibiliKeyPoint {
  /** 跳转秒数：来自真实 outline / 分 P 时间轴（后端强校验，前端再过滤一层防脏）。 */
  tSec: number;
  /** 该时间点讲了什么（基于真实 outline 概括，不编造）。 */
  label: string;
  /** 时间点来源（审计用）。 */
  source: BilibiliKeyPointSource;
}

export interface BilibiliInsightDto {
  /** B 站视频稳定标识。 */
  bvid: string;
  /** 针对学生问题的简要概述；LLM 不可用 / 取数失败时可能为空串（前端给中性占位、不伪造）。 */
  overview: string;
  /** 关键时间点（真实时间轴；拿不到则为空，**绝不编造秒数**）。 */
  keyPoints: BilibiliKeyPoint[];
  /** 概述依据（审计用）。 */
  basedOn: BilibiliInsightBasis;
  /** best-effort 降级标记：true 时前端只显示概述 + 中性提示，不伪造时间点。 */
  degraded: boolean;
}

function asPlainObject(value: unknown): Record<string, unknown> {
  return typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : {};
}

function normalizeKeyPoint(raw: unknown): BilibiliKeyPoint | null {
  const obj = asPlainObject(raw);
  const tSec = obj.tSec;
  const label = obj.label;
  // 防御性过滤：tSec 必须是 ≥0 的有限数，label 必须非空。否则丢弃（不渲染坏跳转点）。
  if (typeof tSec !== 'number' || !Number.isFinite(tSec) || tSec < 0) return null;
  if (typeof label !== 'string' || label.trim().length === 0) return null;
  const source: BilibiliKeyPointSource = obj.source === 'ai_conclusion' ? 'ai_conclusion' : 'pages';
  return { tSec: Math.floor(tSec), label: label.trim(), source };
}

function normalizeInsight(bvid: string, raw: unknown): BilibiliInsightDto {
  const obj = asPlainObject(raw);
  const keyPointsRaw = Array.isArray(obj.keyPoints) ? obj.keyPoints : [];
  const keyPoints = keyPointsRaw
    .map(normalizeKeyPoint)
    .filter((kp): kp is BilibiliKeyPoint => kp !== null);
  const basedOnRaw = obj.basedOn;
  const basedOn: BilibiliInsightBasis =
    basedOnRaw === 'ai_conclusion' || basedOnRaw === 'pages' ? basedOnRaw : 'metadata_only';
  return {
    bvid: typeof obj.bvid === 'string' && obj.bvid.length > 0 ? obj.bvid : bvid,
    overview: typeof obj.overview === 'string' ? obj.overview : '',
    keyPoints,
    basedOn,
    degraded: obj.degraded === true,
  };
}

/**
 * 拉取单个 B 站视频的「基于学生问题的概述 + 关键时间点」。
 *
 * - `question` 缺省时由调用方用关键词兜底（见 `BilibiliVideoCards`）。
 * - 失败语义：任何失败抛错，由调用方显示「AI 讲解暂不可用 + 重试」内联占位；
 *   不把失败伪装成「空概述」（吞错会误导学生以为视频没内容）。
 * - 用 `suppressNetworkToast`：本请求自带内联降级，不该把全局网络 toast 弹给学生。
 */
export async function fetchBilibiliInsight(
  bvid: string,
  question = '',
  kcCodes: string[] = [],
): Promise<BilibiliInsightDto> {
  const res = await http.post<unknown>(
    '/api/videos/bilibili/insight',
    { bvid, question, kcCodes },
    { suppressNetworkToast: true },
  );
  return normalizeInsight(bvid, res.data);
}
