/**
 * 世界条件 → `world_effect` 渲染指令（纯函数）。
 *
 * 这是 quest 层与舞台层（C-stage-render）的接缝：把「世界现状」翻译成可序列化的
 * `RenderCommand[]`（契约 §5.3），由舞台据此把每块地演成「浇透变绿 / 仍干裂」。
 * 只 import 共享类型，不触碰渲染实现。
 */

import type { RenderCommand } from '@/services/codeTheater/types';
import type { CoverageWorldGoal, WorldCheckResult } from './types';

export interface WorldEffectOptions {
  /** 每条世界变化指令的播放时长（毫秒）。 */
  readonly durationMs?: number;
  /** 起始 step（与 Trace 末尾对齐时由调用方传入）。 */
  readonly startStep?: number;
}

/** 单块地的可见状态：浇透 / 仍干。 */
export type FieldEffectState = 'watered' | 'dry';

/**
 * 为覆盖型目标的每个目标生成一条 `world_effect` 指令。
 * payload 仅承载可序列化基元（string | number | boolean），避免把对象/any 渗进契约层。
 *
 * @example 浇灌任务结果 → 舞台把第 1~3 块染绿、第 4 块保持干裂闪问号。
 */
export function toWorldEffectCommands(
  goal: CoverageWorldGoal,
  result: WorldCheckResult,
  options: WorldEffectOptions = {},
): RenderCommand[] {
  const durationMs = options.durationMs ?? 400;
  const startStep = options.startStep ?? 0;
  const satisfied = new Set<number>(result.satisfied);

  return goal.targets.map((target, index): RenderCommand => {
    const state: FieldEffectState = satisfied.has(target) ? 'watered' : 'dry';
    return {
      step: startStep + index,
      action: 'world_effect',
      payload: {
        goal: goal.kind,
        targetNoun: goal.targetNoun,
        target,
        state,
      },
      durationMs,
    };
  });
}
