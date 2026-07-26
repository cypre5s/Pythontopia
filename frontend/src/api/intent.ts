import { http } from "./http";
import type { WorkshopResourceType } from "./dto/Workshop";
import type {
  WorkshopCapabilityNamespace,
  WorkshopToolRouteSource,
  WorkshopRenderMode,
} from "@/components/workshop/toolRouting";

// 百晓工坊「意图路由」前端契约。后端 POST /api/intent/route（Java 代理 → Python IntentRouterAgent），
// 单次分类（非流式）：把一句话判定成该调用的工具并锚定 KC，前端据此分派到既有导师 / 工坊 SSE 管线。
// 2026-06 深度优化：扩为 17 工具分发表 + 多轮上下文路由（recentTurns / lastIntent / sessionCorrections / scope）。

/**
 * 17 工具分发表（与 graph `schemas/intent.py` 的 IntentKind 全集一一对应）：
 *   - tutor_sse：ASK 答疑 / ASKME 考考我 / EXPLAIN_CODE 讲代码
 *   - bugmine：FIX_BUG 复用真实失败提交与 DebugAgent 调试链
 *     （`/api/bug-mine/sessions`，不经过 `/api/tutor/ask`）；
 *   - workshop_sse：6 个资源类型（与 WorkshopResourceType 一一对应，工坊生成管线）；
 *   - orchestrator_sse：PLAN 学习规划（/api/orchestrator/turn/stream）；
 *   - backend tool：BROWSE 联网查证（外部内容，非课件）；
 *   - frontend（零 LLM）：VIDEO B站精选讲解 / REVIEW 记忆矿脉回声复习 /
 *     PROGRESS 学情报告 / NAVIGATE 带路 / MY_STUDY 找我的资料。
 */
export type IntentKind =
  | "ASK"
  | "ASKME"
  | "EXPLAIN_CODE"
  | "FIX_BUG"
  | "VIDEO"
  | "REVIEW"
  | "BROWSE"
  | "PLAN"
  | "PROGRESS"
  | "NAVIGATE"
  | "MY_STUDY"
  | WorkshopResourceType;

/** 课程 KC 目录条目：前端为 @ 提及菜单本就全量加载，路由时透传给后端做语义锚定。 */
export interface CourseKcRef {
  code: string;
  name: string;
}

/** 多轮上下文里的一轮消息摘要（≤6 轮，时间正序；content 已在前端截断）。 */
export interface IntentTurnRef {
  role: "user" | "assistant";
  content: string;
}

/** 本会话内学生的「改派纠错」记录（≤3 条 FIFO），让路由器学会该学生的口头习惯。 */
export interface IntentCorrectionRef {
  message: string;
  correctedIntent: string;
}

/** 当前页面/编辑器事实；画像、掌握度和判题结论不允许由前端声明。 */
export interface WorkshopInteractionContextRef {
  sessionId?: string;
  currentScene?: string;
  currentStage?: string;
  currentProblemId?: number;
  currentCode?: string;
  latestSubmissionId?: number;
  selectedPageIds?: number[];
  errorText?: string;
  frontendAction?: string;
  currentResourceId?: number;
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
  scope?: "default" | "quiz";
  /** 触发来源：普通输入、slash、能力启动器或上下文建议。 */
  source?: WorkshopToolRouteSource;
  /** 能力启动器选择的 6 类分组；仅作为路由候选空间 hint，不等同于 forcedIntent。 */
  capabilityNamespace?: WorkshopCapabilityNamespace;
  /** 学生显式选择的工具；仅跳过语义消歧，仍必须通过 scope、上下文与策略门。 */
  requestedIntent?: IntentKind;
  interaction?: WorkshopInteractionContextRef;
}

export type WorkshopTaskFamily =
  | "CONCEPT_QA"
  | "COURSEWARE_QA"
  | "DEBUG"
  | "GUIDE"
  | "RESOURCE"
  | "PRACTICE"
  | "MINDMAP"
  | "PATH"
  | "MULTI_TASK"
  | "CHAT"
  | "UNKNOWN";

export type WorkshopRouteTopology =
  | "DIRECT"
  | "SINGLE_AGENT_TOOL"
  | "SEQUENTIAL"
  | "PARALLEL"
  | "PLANNER_EXECUTOR";

export interface WorkshopRoutePersonalization {
  strategy:
    | "STANDARD"
    | "SCAFFOLD_FIRST"
    | "REMEDIATION_FIRST"
    | "VISUAL_FIRST";
  /** 仅供审计，不直接展示，避免把内部画像字段暴露给学生。 */
  signalsUsed: string[];
  /** 服务端生成的学生可读解释。 */
  explanations: string[];
  writebackMode: "DOMAIN_EVENTS_ONLY";
}

/** C3 有界 ToT + A* 的公开候选路径；只含公开路径与数值成本，不含模型隐藏推理。 */
export interface WorkshopRouteSearchCandidate {
  candidateId: string;
  /** 学生可读的路线名（如「规划诊断优先」），UI 只展示该字段。 */
  label: string;
  agentPath: string[];
  toolPath: string[];
  depth: number;
  gCost: number;
  hCost: number;
  penalty: number;
  totalCost: number;
}

/** 仅 C3 决策携带；候选恰好 3 条，selected 为 A* 依 total_cost 选出的最优项。 */
export interface WorkshopRouteSearch {
  strategy: "BOUNDED_TOT_ASTAR";
  candidates: WorkshopRouteSearchCandidate[];
  selectedCandidateId: string;
  maxDepth: number;
  scoreFormula: string;
  replanPolicy: { maxCandidateSwitches: number };
}

export interface WorkshopRouteDecision {
  routeId: string;
  version: number;
  task: {
    family: WorkshopTaskFamily;
    subtype: string;
    /** Supervisor 编译 TaskPlan 时使用的有序子意图；单任务也保留自身意图。 */
    subIntents: IntentKind[];
    complexity: "C0" | "C1" | "C2" | "C3";
    confidence: number;
    needsClarification: boolean;
    clarificationQuestion: string;
  };
  orchestration: {
    topology: WorkshopRouteTopology;
    primaryAgent: string | null;
    supportingAgents: string[];
  };
  tools: { required: string[]; optional: string[]; forbidden: string[] };
  policies: {
    maxToolCalls: number;
    maxCallsPerTool: number;
    maxAgentCalls: number;
    maxPlanningDepth: number;
    maxParallelAgents: number;
    maxReplans: number;
    requireGrounding: boolean;
    requireSafety: boolean;
    allowCommonKnowledge: boolean;
    readBeforeWrite: boolean;
  };
  output: {
    schemaName: string;
    renderMode: WorkshopRenderMode;
    requireEvidence: boolean;
  };
  fallback: {
    onLowConfidence: string;
    onMissingContext: string;
    onNoEvidence: string;
    onToolFailure: string;
    onModelFailure: string;
    onGuardFailure: string;
  };
  /** 新 Backend 总会返回；保留可选以兼容滚动部署期间的旧响应。 */
  personalization?: WorkshopRoutePersonalization;
  /** 仅 C3 复杂规划请求携带；C0-C2 与旧响应均为空。 */
  search?: WorkshopRouteSearch | null;
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
  riskClass: "read_only" | "navigational" | "generative";
  renderMode: WorkshopRenderMode;
  reasonCode:
    | "explicit_command"
    | "contextual_match"
    | "missing_required_context"
    | "multi_intent"
    | "safety_constrained"
    | "fallback"
    | "legacy_response"
    | "graph_unavailable";
  /** 新 Graph 总会返回；保留可选以兼容滚动部署期间的旧 Backend。 */
  routeDecision?: WorkshopRouteDecision;
  /** Backend 签发的路由执行令牌；Orchestrator 据此校验决策未被前端篡改。 */
  routeExecutionToken?: string;
}

const RESOURCE_INTENTS: ReadonlySet<IntentKind> = new Set<IntentKind>([
  "HANDOUT",
  "MINDMAP",
  "PRACTICE",
  "CODE_LAB",
  "READING",
  "STORYBOARD",
]);

/** 仅 6 个资源类型为真（可直接喂给工坊生成接口）；新增的管家/导师类意图一律为假。 */
export function isResourceIntent(
  intent: IntentKind,
): intent is WorkshopResourceType {
  return RESOURCE_INTENTS.has(intent);
}

export async function routeIntent(
  request: IntentRouteRequest,
): Promise<IntentRouteResponse> {
  const response = await http.post<IntentRouteResponse>(
    "/api/intent/route",
    request,
  );
  return response.data;
}
