import { http } from './http';
import type { WorkshopResourceType } from './dto/Workshop';
import type {
  WorkshopCapabilityNamespace,
  WorkshopToolRouteSource,
  WorkshopRenderMode,
} from '@/components/workshop/toolRouting';

// 百晓工坊「意图路由」前端契约。后端 POST /api/intent/route（Java 代理 → Python IntentRouterAgent），
// 单次分类（非流式）：把一句话判定成该调用的工具并锚定 KC，前端据此分派到既有导师 / 工坊 SSE 管线。
// 2026-06 深度优化：扩为 17 工具分发表 + 多轮上下文路由（recentTurns / lastIntent / sessionCorrections / scope）。

/**
 * 17 工具分发表（与 graph `schemas/intent.py` 的 IntentKind 全集一一对应）：
 *   - tutor_sse：ASK 答疑 / ASKME 考考我 / EXPLAIN_CODE 讲代码 / FIX_BUG 修报错
 *     （共用 /api/tutor/ask，差异在 context 前缀 directive）；
 *   - workshop_sse：6 个资源类型（与 WorkshopResourceType 一一对应，工坊生成管线）；
 *   - orchestrator_sse：PLAN 学习规划（/api/orchestrator/turn/stream）；
 *   - backend tool：BROWSE 联网查证（外部内容，非课件）；
 *   - frontend（零 LLM）：VIDEO B站精选讲解 / REVIEW 记忆矿脉回声复习 /
 *     PROGRESS 学情报告 / NAVIGATE 带路 / MY_STUDY 找我的资料。
 */
export type IntentKind =
  | 'ASK'
  | 'ASKME'
  | 'EXPLAIN_CODE'
  | 'FIX_BUG'
  | 'VIDEO'
  | 'REVIEW'
  | 'BROWSE'
  | 'PLAN'
  | 'PROGRESS'
  | 'NAVIGATE'
  | 'MY_STUDY'
  | WorkshopResourceType;

/** 课程 KC 目录条目：前端为 @ 提及菜单本就全量加载，路由时透传给后端做语义锚定。 */
export interface CourseKcRef {
  code: string;
  name: string;
}

/** 多轮上下文里的一轮消息摘要（≤6 轮，时间正序；content 已在前端截断）。 */
export interface IntentTurnRef {
  role: 'user' | 'assistant';
  content: string;
}

/** 本会话内学生的「改派纠错」记录（≤3 条 FIFO），让路由器学会该学生的口头习惯。 */
export interface IntentCorrectionRef {
  message: string;
  correctedIntent: string;
}

export interface IntentRouteRequest {
  message: string;
  /** 学生手动 @ 指定的 KC（最高优先级）。 */
  manualKcCodes?: string[];
  courseKcs?: CourseKcRef[];
  currentChapter?: number;
  recentKcCodes?: string[];
  /** 最近 ≤6 条消息摘要（时间正序），供指代消解（「再来几道」「把刚才的画成导图」）。 */
  recentTurns?: IntentTurnRef[];
  /** 上一条 assistant 消息已执行的意图。 */
  lastIntent?: string;
  /** 上一条 assistant 消息锚定的 KC。 */
  lastKcCodes?: string[];
  /** 本会话纠错记录（≤3 条 FIFO）。 */
  sessionCorrections?: IntentCorrectionRef[];
  /** 路由范围：考核模式下收窄为 quiz 工具集（ASK/REVIEW）。 */
  scope?: 'default' | 'quiz';
  /** 触发来源：普通输入、slash、能力启动器或上下文建议。 */
  source?: WorkshopToolRouteSource;
  /** 能力启动器选择的 6 类分组；仅作为路由候选空间 hint，不等同于 forcedIntent。 */
  capabilityNamespace?: WorkshopCapabilityNamespace;
}

export interface IntentRouteResponse {
  intent: IntentKind;
  kcCodes: string[];
  focusHint: string;
  confidence: number;
  reason: string;
  /** NAVIGATE 专用地标（library/arena/bugmine/world/workshop），其它意图为空串。 */
  target: string;
  /** 组合意图的次级动作（如「讲完再出题」），可空。 */
  secondaryIntent: IntentKind | null;
  namespace: WorkshopCapabilityNamespace;
  candidateIntents: IntentKind[];
  needsClarification: boolean;
  clarificationPrompt: string;
  riskClass: 'read_only' | 'navigational' | 'generative';
  renderMode: WorkshopRenderMode;
  reasonCode:
    | 'explicit_command'
    | 'contextual_match'
    | 'missing_required_context'
    | 'multi_intent'
    | 'safety_constrained'
    | 'fallback';
}

const RESOURCE_INTENTS: ReadonlySet<IntentKind> = new Set<IntentKind>([
  'HANDOUT',
  'MINDMAP',
  'PRACTICE',
  'CODE_LAB',
  'READING',
  'STORYBOARD',
]);

/** 仅 6 个资源类型为真（可直接喂给工坊生成接口）；新增的管家/导师类意图一律为假。 */
export function isResourceIntent(intent: IntentKind): intent is WorkshopResourceType {
  return RESOURCE_INTENTS.has(intent);
}

export async function routeIntent(
  request: IntentRouteRequest,
): Promise<IntentRouteResponse> {
  const response = await http.post<IntentRouteResponse>('/api/intent/route', request);
  return response.data;
}
