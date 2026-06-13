/**
 * 原始记录类型 —— Python 插桩 harness（在 Pyodide 内执行）与 TS `buildTrace` 之间的
 * **信任边界**契约。harness 以 JSON 形式吐出 `RawTracePayload`，`buildTrace` 再把它
 * 整形（normalize）成 `frontend/src/services/codeTheater/types.ts` 的权威 `Trace`。
 *
 * 设计要点：
 * - 这些类型描述「来自 Python 的松散 JSON」，因此 `buildTrace` 不信任它们的字段完整性，
 *   会逐条防御式校验并对缺字段/未知 kind 优雅降级为 `line` 事件。
 * - 原始记录**不含 step**（step 由 `buildTrace` 自增分配），但带 1 基 `line`。
 * - 与 `types.ts` 的 `TraceEvent` 字段同名对齐，方便整形时直通拷贝。
 */

export type RawScope = 'global' | 'local';

export interface RawLineRecord {
  kind: 'line';
  line: number;
}

export interface RawAssignRecord {
  kind: 'assign';
  line: number;
  varName: string;
  /** 已在 Python 侧 repr 过的可显示字符串。 */
  value: string;
  scope: RawScope;
}

export interface RawLoopIterRecord {
  kind: 'loop_iter';
  line: number;
  loopId: string;
  loopType: 'for' | 'while';
  iter: number;
  varName?: string;
  value?: string;
}

export interface RawBranchRecord {
  kind: 'branch';
  line: number;
  cond: string;
  result: boolean;
  lineTaken: number;
}

export interface RawCallRecord {
  kind: 'call';
  line: number;
  funcName: string;
  args: string[];
  depth: number;
}

export interface RawReturnRecord {
  kind: 'return';
  line: number;
  funcName: string;
  value: string;
  depth: number;
}

export interface RawListOpRecord {
  kind: 'list_op';
  line: number;
  op: 'index' | 'append' | 'pop' | 'insert' | 'set' | 'remove';
  target: string;
  position?: number;
  value?: string;
  lengthAfter: number;
}

export interface RawDictOpRecord {
  kind: 'dict_op';
  line: number;
  op: 'get' | 'set' | 'del';
  target: string;
  key: string;
  value?: string;
}

export interface RawOutputRecord {
  kind: 'output';
  line: number;
  text: string;
}

export interface RawInputRecord {
  kind: 'input';
  line: number;
  prompt: string;
  value: string;
}

export interface RawExceptionRecord {
  kind: 'exception';
  line: number;
  excType: string;
  message: string;
}

export type RawTraceRecord =
  | RawLineRecord
  | RawAssignRecord
  | RawLoopIterRecord
  | RawBranchRecord
  | RawCallRecord
  | RawReturnRecord
  | RawListOpRecord
  | RawDictOpRecord
  | RawOutputRecord
  | RawInputRecord
  | RawExceptionRecord;

/** harness 一次执行的完整原始产出（sentinel 包裹的 JSON 反序列化结果）。 */
export interface RawTracePayload {
  /** 执行顺序的原始事件序列（不含 step）。 */
  records: RawTraceRecord[];
  /** 学生程序自身的 stdout 全文（与 trace JSON 分离，避免污染 sentinel 解析）。 */
  stdout: string;
  /** harness 侧是否因步数预算（死循环防护）被截断。 */
  truncated: boolean;
}
