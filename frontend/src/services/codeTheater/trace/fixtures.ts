/**
 * `buildTrace` 单测固定装置（原始记录 fixture）。
 *
 * 这些 `RawTracePayload` 取材自真实 harness（CPython settrace）的产出形状，
 * 经裁剪以保持断言简洁，覆盖契约要求的：顺序执行 / for+range / 分支 / 死循环截断。
 * 测试以「raw fixture → 期望 Trace」断言（期望值写在 `buildTrace.test.ts`）。
 */
import { MAX_TRACE_STEPS } from '../types';
import type { RawTracePayload, RawTraceRecord } from './rawTypes';

/**
 * 顺序执行 + 赋值 + 输出：
 * ```python
 * x = 5      # line 1
 * y = x + 2  # line 2
 * print(y)   # line 3
 * ```
 */
export const sequentialRaw: RawTracePayload = {
  records: [
    { kind: 'line', line: 1 },
    { kind: 'assign', line: 1, varName: 'x', value: '5', scope: 'global' },
    { kind: 'line', line: 2 },
    { kind: 'assign', line: 2, varName: 'y', value: '7', scope: 'global' },
    { kind: 'line', line: 3 },
    { kind: 'output', line: 3, text: '7' },
  ],
  stdout: '7\n',
  truncated: false,
};

/**
 * for + range 两轮：
 * ```python
 * for i in range(2):  # line 1
 *     print(i)        # line 2
 * ```
 * loop_iter 挂在循环头行（line 1），每轮带循环变量值；末轮回到 line 1 为耗尽。
 */
export const forLoopRaw: RawTracePayload = {
  records: [
    { kind: 'line', line: 1 },
    { kind: 'loop_iter', line: 1, loopId: 'for@1', loopType: 'for', iter: 0, varName: 'i', value: '0' },
    { kind: 'line', line: 2 },
    { kind: 'output', line: 2, text: '0' },
    { kind: 'line', line: 1 },
    { kind: 'loop_iter', line: 1, loopId: 'for@1', loopType: 'for', iter: 1, varName: 'i', value: '1' },
    { kind: 'line', line: 2 },
    { kind: 'output', line: 2, text: '1' },
    { kind: 'line', line: 1 },
  ],
  stdout: '0\n1\n',
  truncated: false,
};

/**
 * 分支（if/else，条件为假走 else）：
 * ```python
 * x = 2               # line 1
 * if x > 3:           # line 2
 *     print("big")    # line 3
 * else:
 *     print("small")  # line 5
 * ```
 */
export const branchRaw: RawTracePayload = {
  records: [
    { kind: 'line', line: 1 },
    { kind: 'assign', line: 1, varName: 'x', value: '2', scope: 'global' },
    { kind: 'line', line: 2 },
    { kind: 'branch', line: 2, cond: 'x > 3', result: false, lineTaken: 5 },
    { kind: 'line', line: 5 },
    { kind: 'output', line: 5, text: 'small' },
  ],
  stdout: 'small\n',
  truncated: false,
};

/**
 * 死循环：构造一个超出步数预算的原始记录序列（模拟 harness 侧已硬中断并置 truncated）。
 * 记录数 = MAX_TRACE_STEPS + extra，断言 buildTrace 把事件截断到 MAX_TRACE_STEPS。
 */
export function makeOverBudgetRaw(extra = 5): RawTracePayload {
  const total = MAX_TRACE_STEPS + extra;
  const records: RawTraceRecord[] = [];
  for (let index = 0; index < total; index += 1) {
    const line = (index % 2) + 1;
    if (index % 2 === 0) {
      records.push({ kind: 'line', line });
    } else {
      records.push({ kind: 'assign', line, varName: 'n', value: String(index), scope: 'global' });
    }
  }
  return { records, stdout: '', truncated: true };
}
