/**
 * 代码剧场 mapper 层公共出口（C-mappers 归属）。
 *
 * 对外提供：
 * - `mapperRegistry`：`Partial<Record<TraceEventKind, TraceMapper>>`，kind → 纯函数 mapper。
 * - `mapEvent`：单事件映射入口，注册表缺省 kind 自动回退 `move_to_line`（契约 §5.4）。
 * - 各 kind 的具名 mapper 与 helpers，便于独立单测/复用。
 *
 * 集成点（Phase 2）：stage / 引擎按 `import { mapEvent } from '@/services/codeTheater/mappers'`
 * 逐个 `trace.events` 调用即可拿到 `RenderCommand[]`；ctx 的 variables/callDepth 累积由引擎负责。
 */

import type {
  MapContext,
  MapperRegistry,
  RenderCommand,
  TraceEvent,
} from '@/services/codeTheater/types';
import {
  assignMapper,
  branchMapper,
  callMapper,
  dictOpMapper,
  exceptionMapper,
  fallbackMapper,
  inputMapper,
  lineMapper,
  listOpMapper,
  loopIterMapper,
  outputMapper,
  returnMapper,
} from '@/services/codeTheater/mappers/eventMappers';

/** kind → mapper 注册表。覆盖全部 11 个 kind；缺省 kind 仍由 `mapEvent` 回退。 */
export const mapperRegistry: MapperRegistry = {
  line: lineMapper,
  assign: assignMapper,
  loop_iter: loopIterMapper,
  branch: branchMapper,
  call: callMapper,
  return: returnMapper,
  list_op: listOpMapper,
  dict_op: dictOpMapper,
  output: outputMapper,
  input: inputMapper,
  exception: exceptionMapper,
};

/**
 * 单事件 → 渲染指令序列。注册表未命中（未来新增 kind）时回退 `fallbackMapper`，
 * 保证契约「未覆盖 kind → 单条 move_to_line」恒成立。纯函数。
 */
export function mapEvent(
  event: TraceEvent,
  ctx: MapContext,
  registry: MapperRegistry = mapperRegistry,
): RenderCommand[] {
  const mapper = registry[event.kind] ?? fallbackMapper;
  return mapper(event, ctx);
}

export {
  assignMapper,
  branchMapper,
  callMapper,
  dictOpMapper,
  exceptionMapper,
  fallbackMapper,
  inputMapper,
  lineMapper,
  listOpMapper,
  loopIterMapper,
  outputMapper,
  returnMapper,
} from '@/services/codeTheater/mappers/eventMappers';
export {
  command,
  durationFor,
  DURATION_MULTIPLIERS,
  type RenderPayload,
} from '@/services/codeTheater/mappers/helpers';
