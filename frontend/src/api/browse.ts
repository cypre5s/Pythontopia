// 联网查证（BROWSE）前端访问层（契约 §3.1 / §4「Stream B2-browse-backend」前端部分）。
//
// 后端 REST（BrowseController，前缀 /api，JWT，requireStudent；DTO 由兄弟单元 B2a 落地）：
//   POST /api/browse/runs        body {goal, maxSteps?} → BrowseRunDto（服务端注入 allowlist、调 graph、落 V170）
//   GET  /api/browse/runs/{id}   → BrowseRunDetailDto（含 steps[]、citations[]）
//
// 红线（契约 §0 / §3.8 / §8.6）：BROWSE 命中的全部 url / 摘要都是**外部内容，非课件**，
// 只走带标签的展示通道（顶部 banner + 引用分区统一标注），绝不混入 packId=43 课件 evidence / 引用。
// 本模块仅负责取数与纯展示辅助；不产出任何课件证据，不做判题。

import { http } from './http';

// ============ DTO（camelCase JSON，镜像后端 BrowseRunDto / BrowseRunDetailDto） ============

/** 浏览步骤动作（镜像 graph BrowseStep.action 的 Literal）。 */
export type BrowseStepAction = 'navigate' | 'read' | 'search' | 'stop';

/**
 * 浏览会话状态（镜像 browse_run.status，Java 枚举 → 大写）。
 * 注：精确字符串集合是与 B2a 的 Phase 2 对账点；未知值在展示层优雅降级（见 browseRunStatusLabel/Tone）。
 */
export type BrowseRunStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'TRUNCATED' | 'FAILED';

/** 状态色调（供组件映射徽章配色；不靠颜色单通道，文字标签为主）。 */
export type BrowseStatusTone = 'pending' | 'active' | 'done' | 'warn' | 'failed';

/** 创建浏览会话请求体（maxSteps 可选，缺省由服务端注入有界上限）。 */
export interface BrowseRunCreateRequest {
  goal: string;
  /** 有界步数上限（契约 §3.1 默认 6）；省略则用服务端默认。 */
  maxSteps?: number;
}

/** 一步浏览（镜像 graph BrowseStep / browse_step；idx → index）。 */
export interface BrowseStepDto {
  index: number;
  action: BrowseStepAction;
  /** 该步命中的外部 url；read/search/stop 等可为空串。 */
  url: string;
  /** 该步可读摘要（已去敏，外部内容）。 */
  summary: string;
}

/** 浏览会话概要（POST 创建 / 列表返回；镜像 browse_run 去 user_id）。 */
export interface BrowseRunDto {
  id: number;
  goal: string;
  status: BrowseRunStatus;
  /** 整体可读结论（外部内容，非课件）。 */
  finalSummary: string;
  /** 是否触 max_steps 提前截断（结果可能不完整）。 */
  truncated: boolean;
  /** fail-fast 错误原因；无错误为空串。 */
  error: string;
  createdAt: string;
}

/** 浏览会话详情（含步骤与命中来源 url）。 */
export interface BrowseRunDetailDto extends BrowseRunDto {
  steps: BrowseStepDto[];
  /** 命中 url（全部为外部链接，统一标注「外部内容，非课件」）。 */
  citations: string[];
}

// ============ REST 端点 ============

/** 创建一次联网查证会话（服务端注入安全墙 allowlist、调 graph BROWSE、落 V170）。 */
export async function createBrowseRun(request: BrowseRunCreateRequest): Promise<BrowseRunDto> {
  const res = await http.post<BrowseRunDto>('/api/browse/runs', request);
  return res.data;
}

/** 取某次浏览会话详情（含步骤时间线与命中来源）。 */
export async function getBrowseRun(id: number): Promise<BrowseRunDetailDto> {
  const res = await http.get<BrowseRunDetailDto>(`/api/browse/runs/${id}`);
  return res.data;
}

// ============ 纯展示辅助（无副作用，易测；红线：外部内容标注单一事实源） ============

/** 外部内容统一标注文案（契约 §3.8；顶部 banner 与引用分区共用单一事实源）。 */
export const BROWSE_EXTERNAL_NOTICE = '外部内容，非课件';

const STEP_ACTION_LABELS: Record<string, string> = {
  navigate: '打开',
  read: '阅读',
  search: '搜索',
  stop: '结束',
};

/** 浏览动作 → 中文标签（未知动作回退原值，永不抛）。 */
export function browseStepActionLabel(action: string): string {
  return STEP_ACTION_LABELS[action] ?? action;
}

const RUN_STATUS_LABELS: Record<string, string> = {
  PENDING: '排队中',
  RUNNING: '联网查证中',
  SUCCESS: '查证完成',
  TRUNCATED: '已达步数上限',
  FAILED: '查证失败',
};

/** 会话状态 → 中文标签（未知状态回退原值）。 */
export function browseRunStatusLabel(status: string): string {
  return RUN_STATUS_LABELS[status] ?? status;
}

const RUN_STATUS_TONES: Record<string, BrowseStatusTone> = {
  PENDING: 'pending',
  RUNNING: 'active',
  SUCCESS: 'done',
  TRUNCATED: 'warn',
  FAILED: 'failed',
};

/** 会话状态 → 配色色调（未知状态回退 pending）。 */
export function browseRunStatusTone(status: string): BrowseStatusTone {
  return RUN_STATUS_TONES[status] ?? 'pending';
}

const URL_LABEL_MAX = 48;

/** 仅 http(s) 视为可点安全链接（防 javascript:/data: 注入，契约 §8.6）。 */
export function isSafeBrowseUrl(url: string): boolean {
  if (!url) return false;
  try {
    const parsed = new URL(url);
    return parsed.protocol === 'http:' || parsed.protocol === 'https:';
  } catch {
    return false;
  }
}

/** 友好链接文案：host + 路径，超长截断；非法 url 回退原串（截断）。 */
export function browseUrlLabel(url: string): string {
  if (!url) return '';
  const truncate = (s: string): string =>
    s.length > URL_LABEL_MAX ? `${s.slice(0, URL_LABEL_MAX - 1)}…` : s;
  try {
    const parsed = new URL(url);
    const path = parsed.pathname === '/' ? '' : parsed.pathname;
    return truncate(`${parsed.host}${path}`);
  } catch {
    return truncate(url);
  }
}
