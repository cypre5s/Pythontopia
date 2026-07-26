/**
 * useToolLoop 测试 —— 混合工具编排前端消费胶水（Phase F）。
 *
 * mock useSseStream 控制 AG-UI 事件流，验证：CUSTOM/loop_step 喂入 loopSlice、
 * RUN_FINISHED 置完成、result 透传、stop 触发 abort。无 any。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { effectScope, nextTick, ref, type Ref } from 'vue';
import type { AguiEvent } from '@/composables/agui';
import type { ToolLoopHybridResult } from '@/api/toolLoop';

interface MockSse {
  events: Ref<AguiEvent[]>;
  result: Ref<ToolLoopHybridResult | null>;
  // 传输层错误（fetch 失败 / !response.ok / 空 body）不会产生 RUN_ERROR 事件，
  // useToolLoop 必须直接读这两个才能把「服务端 500」暴露给用户。
  error: Ref<{ code: string; message: string } | null>;
  status: Ref<'idle' | 'connecting' | 'streaming' | 'done' | 'error'>;
  start: ReturnType<typeof vi.fn>;
  abort: ReturnType<typeof vi.fn>;
}

const mockSse: MockSse = {
  events: ref<AguiEvent[]>([]),
  result: ref<ToolLoopHybridResult | null>(null),
  error: ref<{ code: string; message: string } | null>(null),
  status: ref<'idle' | 'connecting' | 'streaming' | 'done' | 'error'>('idle'),
  start: vi.fn(async () => {}),
  abort: vi.fn(),
};

vi.mock('@/composables/useSseStream', () => ({
  useSseStream: () => mockSse,
}));

import { useToolLoop, type ToolLoopController } from '@/composables/useToolLoop';

function runInScope(): { ctrl: ToolLoopController; stop: () => void } {
  const scope = effectScope();
  let ctrl!: ToolLoopController;
  scope.run(() => {
    ctrl = useToolLoop();
  });
  return { ctrl, stop: () => scope.stop() };
}

describe('useToolLoop', () => {
  beforeEach(() => {
    mockSse.events.value = [];
    mockSse.result.value = null;
    mockSse.error.value = null;
    mockSse.status.value = 'idle';
    mockSse.start.mockClear();
    mockSse.abort.mockClear();
  });

  it('feeds CUSTOM loop_step events into the loop slice', async () => {
    const { ctrl, stop } = runInScope();
    mockSse.events.value = [
      {
        type: 'CUSTOM',
        name: 'loop_step',
        value: {
          id: 'tool-0',
          phase: 'act',
          label: 'make_resource',
          status: 'done',
          toolName: 'make_resource',
          reasonSummary: '已生成讲义',
        },
      },
    ];
    await nextTick();
    expect(ctrl.plan.value.steps).toHaveLength(1);
    expect(ctrl.plan.value.steps[0]?.toolName).toBe('make_resource');
    expect(ctrl.plan.value.steps[0]?.status).toBe('done');
    stop();
  });

  it('marks run done on RUN_FINISHED', async () => {
    const { ctrl, stop } = runInScope();
    mockSse.events.value = [{ type: 'RUN_FINISHED', result: {} }];
    await nextTick();
    expect(ctrl.status.value).toBe('done');
    stop();
  });

  it('surfaces RUN_ERROR as run error', async () => {
    const { ctrl, stop } = runInScope();
    mockSse.events.value = [{ type: 'RUN_ERROR', code: 'TOOL_LOOP_FAILED', message: '失败了' }];
    await nextTick();
    expect(ctrl.status.value).toBe('error');
    expect(ctrl.runError.value).toBe('失败了');
    stop();
  });

  it('exposes result ref and start/stop wiring', () => {
    const { ctrl, stop } = runInScope();
    expect(typeof ctrl.start).toBe('function');
    ctrl.stop();
    expect(mockSse.abort).toHaveBeenCalledTimes(1);
    stop();
  });
});
