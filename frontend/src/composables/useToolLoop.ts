import { computed, watch, type ComputedRef, type Ref } from 'vue';
import { useSseStream } from '@/composables/useSseStream';
import { createLoopSlice, type LoopRunStatus } from '@/stores/learning/loopSlice';
import type { LoopPlan } from '@/stores/learning/loopTypes';
import {
  TOOL_LOOP_STREAM_URL,
  type ToolLoopHybridResult,
  type ToolLoopRequest,
} from '@/api/toolLoop';

/**
 * useToolLoop —— 混合工具编排（Phase F）的前端消费胶水。
 *
 * 复用既有 Agentic Loop 渲染机器：`useSseStream` 把后端 SSE 归一化为 AG-UI 事件流，
 * 增量喂入 `createLoopSlice()`（消费 CUSTOM/loop_step、RUN_FINISHED…），对外暴露
 * `LoopPlan` 供 `LoopProgress.vue` 渲染 + 终态 `HybridResult`（含最终答复/澄清/工具轨迹）。
 *
 * 与 GuideOverlay 的 orchestrator 接线同范式，仅指向 tool-loop 端点；脏/乱序事件由
 * loopSlice fail-soft 兜底，TS strict 无 any。
 */
export interface ToolLoopController {
  plan: ComputedRef<LoopPlan>;
  status: ComputedRef<LoopRunStatus>;
  runError: ComputedRef<string | null>;
  result: Ref<ToolLoopHybridResult | null>;
  start: (body: ToolLoopRequest) => Promise<void>;
  stop: () => void;
}

export function useToolLoop(url: string = TOOL_LOOP_STREAM_URL): ToolLoopController {
  const sse = useSseStream<ToolLoopHybridResult>(url);
  const loop = createLoopSlice();

  // 归一化 AG-UI 事件流增量喂入 loopSlice（loop_step/RUN_FINISHED/RUN_ERROR…）。
  watch(
    () => sse.events.value,
    (events) => loop.syncFromEvents(events),
  );

  function stop(): void {
    sse.abort();
    loop.stop();
  }

  return {
    plan: computed(() => loop.plan.value),
    status: computed(() => loop.status.value),
    runError: computed(() => loop.runError.value),
    result: sse.result,
    start: (body: ToolLoopRequest) => sse.start(body),
    stop,
  };
}
