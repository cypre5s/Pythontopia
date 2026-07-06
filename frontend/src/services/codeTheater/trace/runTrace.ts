/**
 * `runTrace` —— 把「真实 Python → 结构化 Trace」串起来的装配层。
 *
 * 流程：`buildHarnessProgram(code)` 组装插桩程序 → 经 Pyodide 适配层执行 →
 * 从真实 stdout 的 sentinel 块解析 `RawTracePayload` → 纯函数 `buildTrace` 整形为 `Trace`。
 *
 * 通过 `RunTraceOptions.runner` 注入适配器以便单测隔离 Pyodide（适配层本体只读、不改）。
 * Pyodide 不可用 / harness 崩溃时抛 `TraceExecutionError` 给出清晰错误，**绝不静默假装**。
 *
 * 红线：本能力是「即时预览/看执行」，不是判题；掌握度唯一权威仍是服务端 submission。
 */
import { getPyodideRunner } from '@game/adapters/pyodide';
import type { PyodideRunner, PyodideRunResult } from '@game/adapters/pyodide';
import type { Trace } from '../types';
import { buildTrace } from './buildTrace';
import { TRACE_SENTINEL_BEGIN, TRACE_SENTINEL_END, buildHarnessProgram } from './harness';
import type { RawTracePayload, RawTraceRecord } from './rawTypes';

/** 轨迹执行失败（Pyodide 不可用 / harness 崩溃 / 输出不可解析）。 */
export class TraceExecutionError extends Error {
  constructor(message: string, options?: ErrorOptions) {
    super(message, options);
    this.name = 'TraceExecutionError';
  }
}

export interface RunTraceOptions {
  /** 注入自定义 Pyodide runner（默认走 `getPyodideRunner()` 单例）；单测据此隔离。 */
  runner?: PyodideRunner;
  /** 传给学生程序的 stdin（按行切分，每行供一次 `input()`）。 */
  stdin?: string;
}

/**
 * 防御式把 harness JSON 反序列化结果收敛为 `RawTracePayload`（顶层结构容错）。
 * 逐条记录的字段校验交给 `buildTrace`。
 */
export function parseRawPayload(value: unknown): RawTracePayload {
  if (typeof value !== 'object' || value === null) {
    return { records: [], stdout: '', truncated: false };
  }
  const obj = value as Record<string, unknown>;
  const records = Array.isArray(obj.records) ? (obj.records as RawTraceRecord[]) : [];
  const stdout = typeof obj.stdout === 'string' ? obj.stdout : '';
  const truncated = obj.truncated === true;
  return { records, stdout, truncated };
}

/** 从真实 stdout 抠出 sentinel 包裹的 trace JSON。找不到 / 解析失败返回 null。 */
function extractPayload(stdout: string): RawTracePayload | null {
  const begin = stdout.lastIndexOf(TRACE_SENTINEL_BEGIN);
  const end = stdout.lastIndexOf(TRACE_SENTINEL_END);
  if (begin === -1 || end === -1 || end <= begin) return null;
  const json = stdout.slice(begin + TRACE_SENTINEL_BEGIN.length, end).trim();
  let parsed: unknown;
  try {
    parsed = JSON.parse(json);
  } catch {
    return null;
  }
  return parseRawPayload(parsed);
}

/**
 * 执行学生代码并产出结构化 `Trace`（用于代码剧场动画，不用于判题）。
 *
 * @throws {TraceExecutionError} Pyodide 执行失败或输出不可解析。
 */
export async function runTrace(code: string, options: RunTraceOptions = {}): Promise<Trace> {
  const runner = options.runner ?? getPyodideRunner();
  const program = buildHarnessProgram(code);

  let result: PyodideRunResult;
  try {
    result = await runner.run(program, options.stdin ?? '');
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    throw new TraceExecutionError(
      `代码剧场无法运行：Pyodide 执行插桩 harness 失败（${detail}）。` +
        `这是即时预览能力问题，不影响服务端判题。`,
      { cause: error },
    );
  }

  const payload = extractPayload(result.stdout);
  if (payload === null) {
    const stderr = result.stderr.trim();
    throw new TraceExecutionError(
      '代码剧场无法解析执行轨迹：未在 Pyodide 输出中找到 trace 数据（harness 可能崩溃）。' +
        (stderr.length > 0 ? ` stderr：${stderr}` : ''),
    );
  }

  return buildTrace(payload, code);
}
