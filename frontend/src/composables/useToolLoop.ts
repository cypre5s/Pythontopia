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

  /**
   * 传输层失败（fetch 抛错 / `!response.ok` / 空 body）**不会**产生 RUN_ERROR 事件——
   * `useSseStream` 只置自己的 `error`/`status`。若不把它并进对外状态，服务端 500/401 时
   * 面板会照旧显示上一轮的工具步骤和「完成」态，学生完全看不到出错。
   */
  const status = computed<LoopRunStatus>(() => {
    if (sse.error.value !== null || sse.status.value === 'error') {
      return 'error';
    }
    return loop.status.value;
  });

  const runError = computed<string | null>(() => {
    if (loop.runError.value !== null) {
      return loop.runError.value;
    }
    const transport = sse.error.value;
    if (!transport) {
      return null;
    }
    return transport.message || transport.code;
  });

  function stop(): void {
    sse.abort();
    loop.stop();
  }

  async function start(body: ToolLoopRequest): Promise<void> {
    // 重跑先复位：loopSlice 的 RUN_STARTED 只在 status==='idle' 时推进，
    // 不复位就会带着上一轮的 steps/「完成」态继续跑。
    loop.reset();
    await sse.start(body);
  }

  return {
    plan: computed(() => loop.plan.value),
    status,
    runError,
    result: sse.result,
    start,
    stop,
  };
}
