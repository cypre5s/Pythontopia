/**
 * notional machine 映射：把每类 `TraceEvent` 纯函数转成舞台 `RenderCommand[]`
 * （C-mappers 归属，见契约 §5.4 与「notional machine 映射表」）。
 *
 * 红线：
 * - 纯函数：仅依赖入参 `event` / `ctx`，无外部状态、无副作用。
 * - 只依赖 types.ts，不 import trace/** 或 game/**，可独立单测。
 * - 每条 RenderCommand.step 对齐 `event.step`；时长以 `ctx.defaultDurationMs` 起步。
 *
 * 每个 mapper 签名都是契约里的 `TraceMapper`（入参是 `TraceEvent` 联合），
 * 进入时先按 `kind` 收窄；若被错误注册到别的 kind 上，则安全回退为 `move_to_line`，
 * 不抛错以免打断演出。
 */

import type {
  ListOpEvent,
  RenderAction,
  TraceMapper,
} from '@/services/codeTheater/types';
import { command, type RenderPayload } from '@/services/codeTheater/mappers/helpers';

/**
 * 兜底映射：`line` 及任何未覆盖 kind → 单条 `move_to_line`
 * （傀儡只是走到该行、当前行发光）。
 */
export const fallbackMapper: TraceMapper = (event, ctx) => [
  command(event.step, 'move_to_line', { line: event.line, kind: event.kind }, ctx),
];

/** `assign` → `set_var`（牌面翻新：旧值翻成新值）。 */
export const assignMapper: TraceMapper = (event, ctx) => {
  if (event.kind !== 'assign') return fallbackMapper(event, ctx);
  const payload: RenderPayload = {
    line: event.line,
    varName: event.varName,
    value: event.value,
    scope: event.scope,
  };
  // 上一值来自只读上下文（引擎在应用本次赋值前的快照），用于展示「旧→新」。
  const previous = ctx.variables[event.varName];
  if (previous !== undefined) payload.previousValue = previous;
  return [command(event.step, 'set_var', payload, ctx)];
};

/** `loop_iter` → `walk_loop`（绕跑道一圈、计数 +1）；for 循环变量额外翻牌展示当前值。 */
export const loopIterMapper: TraceMapper = (event, ctx) => {
  if (event.kind !== 'loop_iter') return fallbackMapper(event, ctx);
  const lap: RenderPayload = {
    line: event.line,
    loopId: event.loopId,
    loopType: event.loopType,
    iter: event.iter, // 0 基轮次
    lap: event.iter + 1, // 1 基「已跑圈数」= 计数 +1
  };
  if (event.varName !== undefined) lap.varName = event.varName;
  if (event.value !== undefined) lap.value = event.value;

  const commands = [command(event.step, 'walk_loop', lap, ctx)];
  // 循环变量值：把本轮循环变量的新值在牌面翻新（与 assign 同语义）。
  if (event.varName !== undefined && event.value !== undefined) {
    commands.push(
      command(
        event.step,
        'set_var',
        {
          line: event.line,
          varName: event.varName,
          value: event.value,
          scope: 'local',
        },
        ctx,
      ),
    );
  }
  return commands;
};

/** `branch` → `take_branch`（岔路口按 result 选路，走向 lineTaken）。 */
export const branchMapper: TraceMapper = (event, ctx) => {
  if (event.kind !== 'branch') return fallbackMapper(event, ctx);
  return [
    command(
      event.step,
      'take_branch',
      {
        line: event.line,
        cond: event.cond,
        result: event.result,
        lineTaken: event.lineTaken,
      },
      ctx,
    ),
  ];
};

/** `call` → `enter_call`（走进调用侧屋、栈 +1，携带实参与调用后栈深）。 */
export const callMapper: TraceMapper = (event, ctx) => {
  if (event.kind !== 'call') return fallbackMapper(event, ctx);
  return [
    command(
      event.step,
      'enter_call',
      {
        line: event.line,
        funcName: event.funcName,
        // payload 仅承载基元：实参列表拍平成一行可读文本，并保留个数。
        args: event.args.join(', '),
        argCount: event.args.length,
        depth: event.depth,
      },
      ctx,
    ),
  ];
};

/** `return` → `exit_call`（带返回值走出侧屋、栈 -1）。 */
export const returnMapper: TraceMapper = (event, ctx) => {
  if (event.kind !== 'return') return fallbackMapper(event, ctx);
  return [
    command(
      event.step,
      'exit_call',
      {
        line: event.line,
        funcName: event.funcName,
        value: event.value,
        depth: event.depth,
      },
      ctx,
    ),
  ];
};

/** 列表操作 → 动作三选一：增（push）/删（pop）/定位（index）。 */
const LIST_OP_ACTION: Readonly<Record<ListOpEvent['op'], RenderAction>> = {
  append: 'list_push',
  insert: 'list_push',
  pop: 'list_pop',
  remove: 'list_pop',
  index: 'list_index',
  set: 'list_index', // 先走到第 i 箱再改值（值随 payload 一并带出）
};

/** `list_op` → `list_push` / `list_pop` / `list_index`（长箱 / 移箱 / 走到第 i 箱）。 */
export const listOpMapper: TraceMapper = (event, ctx) => {
  if (event.kind !== 'list_op') return fallbackMapper(event, ctx);
  const payload: RenderPayload = {
    line: event.line,
    op: event.op,
    target: event.target,
    lengthAfter: event.lengthAfter,
  };
  if (event.position !== undefined) payload.position = event.position;
  if (event.value !== undefined) payload.value = event.value;
  return [command(event.step, LIST_OP_ACTION[event.op], payload, ctx)];
};

/** `dict_op` → `dict_set`（格子墙写 key→value；get/del 经 payload.op 区分）。 */
export const dictOpMapper: TraceMapper = (event, ctx) => {
  if (event.kind !== 'dict_op') return fallbackMapper(event, ctx);
  const payload: RenderPayload = {
    line: event.line,
    op: event.op,
    target: event.target,
    key: event.key,
  };
  if (event.value !== undefined) payload.value = event.value;
  return [command(event.step, 'dict_set', payload, ctx)];
};

/** `output` → `speak`（公告板播报 print 内容）。 */
export const outputMapper: TraceMapper = (event, ctx) => {
  if (event.kind !== 'output') return fallbackMapper(event, ctx);
  return [
    command(
      event.step,
      'speak',
      {
        line: event.line,
        channel: 'output',
        text: event.text,
      },
      ctx,
    ),
  ];
};

/** `input` → `speak`（NPC 递纸条：展示 prompt 与读到的值）。 */
export const inputMapper: TraceMapper = (event, ctx) => {
  if (event.kind !== 'input') return fallbackMapper(event, ctx);
  return [
    command(
      event.step,
      'speak',
      {
        line: event.line,
        channel: 'input',
        prompt: event.prompt,
        value: event.value,
      },
      ctx,
    ),
  ];
};

/** `exception` → `stumble`（在该行绊倒冒火花）。 */
export const exceptionMapper: TraceMapper = (event, ctx) => {
  if (event.kind !== 'exception') return fallbackMapper(event, ctx);
  return [
    command(
      event.step,
      'stumble',
      {
        line: event.line,
        excType: event.excType,
        message: event.message,
      },
      ctx,
    ),
  ];
};

/** `line` → `move_to_line`（与兜底同逻辑，显式给出便于注册表自洽）。 */
export const lineMapper: TraceMapper = fallbackMapper;
