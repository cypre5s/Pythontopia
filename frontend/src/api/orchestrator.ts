// 学习总管 OrchestratorAgent 的前端契约类型。
//
// 前端通过 useSseStream 直接 POST /api/orchestrator/turn/stream（Java 代理 → Python graph）。
// SSE 事件：trace / token / result / error（与 useSseStream 既有契约一致）。
// result 载荷是 Python OrchestratorResponse 的 model_dump（snake_case），故此处字段用 snake_case。

/** 规范学习闭环阶段：看 / 学 / 练 / 测。 */
export type OrchestratorPhase = 'watch' | 'learn' | 'practice' | 'assess';

export interface OrchestratorPlanStep {
  id: string;
  phase: OrchestratorPhase;
  title: string;
  detail: string;
  /** 负责该步的专家 Agent 名（资源/练习/路径/导学/调试…）。 */
  agent: string;
  kc_codes: string[];
  /** 直达学习面板的简称：lecture/library/training/workshop/tutor/problems/examples/review。 */
  panel: string | null;
  status: 'pending' | 'active' | 'done' | 'skipped';
  /** phase=learn 时的资源类型（HANDOUT/MINDMAP/...）。 */
  resource_type: string | null;
}

export interface OrchestratorDispatchResult {
  step_id: string;
  agent: string;
  ok: boolean;
  summary: string;
  note: string | null;
}

export interface OrchestratorProfileSignal {
  /** 8 维画像 key（professional_background / learning_goal / cognitive_style ...）。 */
  dimension: string;
  value: string;
  confidence: number;
}

export interface OrchestratorTraceStep {
  node: string;
  elapsed_ms: number;
  status: string;
  note?: string | null;
}

export interface OrchestratorCheckResult {
  passed: boolean;
  reasons?: string[];
}

/** /agents/orchestrator/turn(/stream) 的 result 事件载荷。 */
export interface OrchestratorResult {
  reply_md: string;
  /** 目标模糊时的澄清追问；非空 + needs_user_input=true 表示需要学生先回答。 */
  clarifying_question: string | null;
  needs_user_input: boolean;
  plan: OrchestratorPlanStep[];
  dispatch_results: OrchestratorDispatchResult[];
  /** 对话即画像：本轮抽取到的画像特征更新。 */
  profile_updates: OrchestratorProfileSignal[];
  trace: OrchestratorTraceStep[];
  done: boolean;
  safety_result?: OrchestratorCheckResult;
  grounding_result?: OrchestratorCheckResult;
}

export interface OrchestratorTurnMessage {
  role: 'user' | 'assistant';
  content: string;
}

/** POST /api/orchestrator/turn/stream 的请求体（camelCase；Java 代理转 snake_case 给 Python）。 */
export interface OrchestratorTurnRequest {
  sessionId: string;
  message: string;
  history: OrchestratorTurnMessage[];
  currentChapter?: number;
  recentKcCodes?: string[];
  recentFailures?: string[];
  profileBrief?: string;
  courseKcCodes?: string[];
}
