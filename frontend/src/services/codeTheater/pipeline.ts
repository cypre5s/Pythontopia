/**
 * 代码剧场端到端管线（Phase 2 集成接缝）：把 C-trace-engine / C-mappers / C-quest
 * 三个并发产物串成一条可运行的流。
 *
 *   学生 Python ─runTrace─▶ Trace ─traceToRenderCommands(逐事件累积 MapContext + mapEvent)─▶ RenderCommand[]
 *                                 └─(具身任务)─ checkQuestWorldGoal ─▶ toWorldEffectCommands 追加 world_effect
 *
 * 关键职责：契约 §5.4 规定「mapper 只读 ctx，variables/callDepth 的累积由引擎负责」——
 * 本模块就是那个「引擎」，在每个事件映射后推进累积状态。`traceToRenderCommands` /
 * `buildQuestResult` 为纯函数（不依赖 Pyodide），可离线单测；`runCodeForQuest` 负责跑真实执行。
 */

import { mapEvent } from '@/services/codeTheater/mappers';
import { runTrace, type RunTraceOptions } from '@/services/codeTheater/trace';
import type { MapContext, RenderCommand, Trace } from '@/services/codeTheater/types';
import {
  checkQuestWorldGoal,
  toWorldEffectCommands,
  type CodeTheaterQuest,
  type WorldCheckResult,
} from '@/data/codeTheater/quests';

/** 每条渲染指令的默认时长（引擎层基准；具体 mapper 可按动作微调）。 */
export const DEFAULT_MAP_DURATION_MS = 600;

export interface TraceToCommandsOptions {
  readonly defaultDurationMs?: number;
}

/**
 * 逐事件累积 `MapContext`（variables / callDepth）并映射为完整 `RenderCommand` 时间轴。
 * 纯函数：同一 `Trace` 恒得同一指令序列，便于离线单测（无需 Pyodide）。
 */
export function traceToRenderCommands(
  trace: Trace,
  options: TraceToCommandsOptions = {},
): RenderCommand[] {
  const defaultDurationMs = options.defaultDurationMs ?? DEFAULT_MAP_DURATION_MS;
  const variables: Record<string, string> = {};
  let callDepth = 0;
  const commands: RenderCommand[] = [];

  for (const event of trace.events) {
    // ctx 反映「事件发生前」的累积状态（mapper 只读）。
    const ctx: MapContext = {
      variables: { ...variables },
      callDepth,
      defaultDurationMs,
    };
    commands.push(...mapEvent(event, ctx));

    // 引擎职责：映射之后推进累积状态。
    if (event.kind === 'assign') {
      variables[event.varName] = event.value;
    } else if (event.kind === 'loop_iter' && event.varName !== undefined) {
      variables[event.varName] = event.value ?? '';
    } else if (event.kind === 'call' || event.kind === 'return') {
      callDepth = event.depth;
    }
  }
  return commands;
}

export interface QuestRunResult {
  readonly trace: Trace;
  readonly commands: RenderCommand[];
  readonly world: WorldCheckResult;
}

/**
 * 纯整形：已有 `Trace` 时复用（便于单测）。
 * Trace + 具身任务 → 完整渲染指令时间轴（含追加在末尾的 world_effect）+ 世界判定结果。
 * coverage / sequence 型 worldGoal 都追加 world_effect（payload 同构，舞台统一消费）；
 * 世界判定仅用于「学习预览」，掌握度仍走服务端判题。
 */
export function buildQuestResult(trace: Trace, quest: CodeTheaterQuest): QuestRunResult {
  const commands = traceToRenderCommands(trace);
  const world = checkQuestWorldGoal(quest, trace);
  const goalKind = quest.worldGoal.kind;
  if (goalKind === 'coverage' || goalKind === 'sequence') {
    commands.push(
      ...toWorldEffectCommands(quest.worldGoal, world, { startStep: trace.stepCount }),
    );
  }
  return { trace, commands, world };
}

/**
 * 跑一段学生代码并按具身任务的「世界条件」补出 world_effect 指令。
 * 需要 Pyodide 运行环境（经 C-trace-engine 的 `runTrace`）；失败按 runTrace 既有错误语义抛出，
 * 调用方（Vue 页）负责兜底提示，绝不静默假装成功。
 */
export async function runCodeForQuest(
  code: string,
  quest: CodeTheaterQuest,
): Promise<QuestRunResult> {
  const trace = await runTrace(code);
  return buildQuestResult(trace, quest);
}

/** 自由可视化结果：只有执行轨迹 + 渲染指令，无具身任务/世界目标。 */
export interface FreeRunResult {
  readonly trace: Trace;
  readonly commands: RenderCommand[];
}

/**
 * 自由可视化：跑任意一段学生代码（如训练场里 WA/RE 的提交），只把执行过程整形成
 * 可演出的渲染指令，不绑定任何具身任务/世界目标。`stdin` 透传给 `input()`。
 * 同样是「学习预览」，掌握度仍以服务端判题为准。
 */
export async function runFreeVisualization(
  code: string,
  stdin = '',
  options: Pick<RunTraceOptions, 'runner'> = {},
): Promise<FreeRunResult> {
  const trace = await runTrace(code, { stdin, runner: options.runner });
  return { trace, commands: traceToRenderCommands(trace) };
}
