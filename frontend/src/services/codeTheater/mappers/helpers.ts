/**
 * 代码剧场 mapper 层共享纯工具（C-mappers 归属）。
 *
 * 仅提供「构造单条 RenderCommand」与「按动作微调时长」两件事，
 * 不持有任何可变状态，保证所有 mapper 同输入同输出（见契约 §5.4）。
 * 只依赖 types.ts，不引入 trace/** 或 game/**。
 */

import type {
  MapContext,
  RenderAction,
  RenderCommand,
} from '@/services/codeTheater/types';

/** RenderCommand.payload 的别名，便于各 mapper 增量构造。 */
export type RenderPayload = RenderCommand['payload'];

/**
 * 动作时长系数：以 `ctx.defaultDurationMs` 为「起步」基准（系数 1），
 * 对耗时更长的舞台动作做轻微放大（契约「durationMs 可按动作微调」）。
 * 未列出的动作默认系数 1。
 */
export const DURATION_MULTIPLIERS: Readonly<Partial<Record<RenderAction, number>>> = {
  walk_loop: 1.25, // 绕跑道一圈比单步走更久
  enter_call: 1.2, // 走进侧屋
  exit_call: 1.2, // 走出侧屋
  speak: 1.5, // 公告板/对话给观众阅读时间
  stumble: 1.4, // 绊倒 + 冒火花的戏剧停顿
};

/** 计算某动作在当前上下文下的播放时长（毫秒，取整）。 */
export function durationFor(ctx: MapContext, action: RenderAction): number {
  const multiplier = DURATION_MULTIPLIERS[action] ?? 1;
  return Math.round(ctx.defaultDurationMs * multiplier);
}

/**
 * 构造一条 RenderCommand：step 对齐入参事件、动作时长按 `durationFor` 微调。
 * 这是 mapper 唯一的指令出口，确保字段与契约 §5.3 完全一致。
 */
export function command(
  step: number,
  action: RenderAction,
  payload: RenderPayload,
  ctx: MapContext,
): RenderCommand {
  return {
    step,
    action,
    payload,
    durationMs: durationFor(ctx, action),
  };
}
