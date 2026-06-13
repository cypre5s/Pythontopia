/**
 * 代码剧场轨迹引擎（C-trace-engine）对外入口。
 *
 * 集成点（供 Phase 2 编排接线）：
 * - `runTrace(code)` → `Trace`：UI/Scene 拿到真实执行轨迹。
 * - `buildTrace(rawRecords, source)`：纯函数，便于离线/测试整形。
 * - 下游 C-mappers 把 `Trace.events` 逐个映射为 `RenderCommand[]`，
 *   C-stage-render 顺序消费。三方均从 `../types` import 权威类型，本模块不重定义。
 */
export { buildTrace } from './buildTrace';
export { runTrace, parseRawPayload, TraceExecutionError } from './runTrace';
export type { RunTraceOptions } from './runTrace';
export {
  buildHarnessProgram,
  toBase64Utf8,
  TRACE_SENTINEL_BEGIN,
  TRACE_SENTINEL_END,
} from './harness';
export type {
  RawScope,
  RawTracePayload,
  RawTraceRecord,
  RawLineRecord,
  RawAssignRecord,
  RawLoopIterRecord,
  RawBranchRecord,
  RawCallRecord,
  RawReturnRecord,
  RawListOpRecord,
  RawDictOpRecord,
  RawOutputRecord,
  RawInputRecord,
  RawExceptionRecord,
} from './rawTypes';
