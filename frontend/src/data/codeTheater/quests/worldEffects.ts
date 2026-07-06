/**
 * 世界条件 → `world_effect` 渲染指令（纯函数）。
 *
 * 这是 quest 层与舞台层（C-stage-render）的接缝：把「世界现状」翻译成可序列化的
 * `RenderCommand[]`（契约 §5.3），由舞台据此把每个目标演成「已完成 / 仍待完成」。
 * 只 import 共享类型，不触碰渲染实现。
 */

import type { RenderCommand } from '@/services/codeTheater/types';
import type { WorldCheckResult, WorldGoal } from './types';

export interface WorldEffectOptions {
  /** 每条世界变化指令的播放时长（毫秒）。 */
  readonly durationMs?: number;
  /** 起始 step（与 Trace 末尾对齐时由调用方传入）。 */
  readonly startStep?: number;
}

/** 单个目标的可见状态：已完成 / 仍待完成。 */
export type TargetEffectState = 'done' | 'pending';

/**
 * 为世界目标的每个目标生成一条 `world_effect` 指令（coverage / sequence 通用）。
 * payload 仅承载可序列化基元（string | number | boolean），避免把对象/any 渗进契约层。
 *
 * 两种目标发出的 payload **保持同构**：固定四键 `{ goal, targetNoun, target, state }`，
 * 含义与 coverage 完全一致（舞台渲染层依赖这套统一约定，勿改这四键的语义）。sequence
 * 额外多带一个可选 `order`（这是第几步，1 基），供舞台演出先后；coverage 不带该键。
 *
 * @example 点亮任务结果 → 舞台把第 1~3 盏路灯点亮、第 4 盏保持熄灭闪问号。
 */
export function toWorldEffectCommands(
  goal: WorldGoal,
  result: WorldCheckResult,
  options: WorldEffectOptions = {},
): RenderCommand[] {
  const durationMs = options.durationMs ?? 400;
  const startStep = options.startStep ?? 0;
  const satisfied = new Set<number>(result.satisfied);

  return goal.targets.map((target, index): RenderCommand => {
    const state: TargetEffectState = satisfied.has(target) ? 'done' : 'pending';
    // 固定四键与 coverage 同构；sequence 再追加「第几步」。
    const payload: Record<string, string | number | boolean> = {
      goal: goal.kind,
      targetNoun: goal.targetNoun,
      target,
      state,
    };
    // 同时下发动词/量词，舞台横幅才能演成「全部点亮！N 盏路灯」而非泛化的「全部完成！N 个路灯」
    // （消费端 CodeTheaterScene.scanWorldTargets 读 actionVerb/unit；缺省时回退 actionName/「个」）。
    const verb = goal.actionVerb ?? goal.actionName;
    if (verb) payload.actionVerb = verb;
    if (goal.unit) payload.unit = goal.unit;
    if (goal.kind === 'sequence') {
      payload.order = index + 1;
    }
    return {
      step: startStep + index,
      action: 'world_effect',
      payload,
      durationMs,
    };
  });
}
