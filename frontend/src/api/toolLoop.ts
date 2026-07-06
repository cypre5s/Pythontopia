// 混合工具编排（Phase F 接线）前端契约类型。
//
// 前端经 useSseStream POST /api/orchestrator/tool-loop/stream（Java 代理 → graph
// /agents/orchestrator/tool-loop/stream，PYT_TOOL_LOOP flag-gated）。
// SSE 事件：CUSTOM(loop_step，LoopStep 形状) / result / error（与 useSseStream 既有契约一致）。
// result 载荷是 graph HybridResult 的 model_dump（snake_case），故此处字段用 snake_case。

/** 单步工具执行记录（镜像 graph ToolLoopStep.model_dump）。 */
export interface ToolLoopStepRecord {
  step: number;
  tool_name: string;
  ok: boolean;
  status: string;
  summary: string;
}

/** 有界工具调用循环终态（镜像 graph ToolLoopResult）。 */
export interface ToolLoopResult {
  final_text: string;
  steps: ToolLoopStepRecord[];
  tool_trail: string[];
  stop_reason: string;
  rounds: number;
}

/** /tool-loop[/stream] 的 result 事件载荷（镜像 graph HybridResult）。 */
export interface ToolLoopHybridResult {
  intent: string;
  seed_tool: string | null;
  needs_clarification: boolean;
  clarification: string;
  loop: ToolLoopResult | null;
}

/** 请求体（camelCase；Java 代理转 snake_case 给 graph）。 */
export interface ToolLoopRequest {
  sessionId: string;
  message: string;
  courseKcCodes?: string[];
}

/** SSE 端点（Java 代理路径）。 */
export const TOOL_LOOP_STREAM_URL = '/api/orchestrator/tool-loop/stream';
