/**
 * loopSlice 单元测试 —— Agentic Loop 前端状态机（F2-loop-ui）。
 *
 * 覆盖测试矩阵：
 *   ① 状态机迁移：pending → active → done / failed，且终态前向单调（不回退）；
 *   ② SSE 事件 → 步骤累积：消费既有 orchestrator AG-UI 事件（loop_plan / loop_step /
 *      RUN_STARTED / RUN_FINISHED / RUN_ERROR）驱动状态机；
 *   ③ 失败 / 降级 / 边界：脏事件 fail-soft 忽略不抛、达上界有界、stop 可中断、failRun 兜底；
 *   ④ syncFromEvents 增量消费累积事件数组（Phase 2 与 useSseStream.events 绑定的形态）。
 *
 * 引用类型一律来自 @/stores/learning/loopTypes（冻结契约，禁止重定义）。
 */
import { describe, expect, it } from 'vitest';
import { createLoopSlice } from '@/stores/learning/loopSlice';
import type { LoopStep } from '@/stores/learning/loopTypes';
import type { AguiEvent } from '@/composables/agui';

function step(partial: Partial<LoopStep> & Pick<LoopStep, 'id' | 'phase' | 'status'>): LoopStep {
  return { label: partial.label ?? partial.phase, ...partial };
}

function customEvent(name: string, value: unknown): AguiEvent {
  return { type: 'CUSTOM', name, value };
}

describe('loopSlice —— 状态机 ①', () => {
  it('startPlan 初始化有界计划：全 pending、streaming、maxSteps 不小于步数', () => {
    const loop = createLoopSlice();
    loop.startPlan({
      steps: [
        step({ id: 's1', phase: 'plan', status: 'pending', label: '规划' }),
        step({ id: 's2', phase: 'act', status: 'pending', label: '执行' }),
      ],
      maxSteps: 4,
    });

    expect(loop.status.value).toBe('streaming');
    expect(loop.plan.value.bounded).toBe(true);
    expect(loop.plan.value.steps).toHaveLength(2);
    expect(loop.steps.value.every((s) => s.status === 'pending')).toBe(true);
    expect(loop.maxSteps.value).toBeGreaterThanOrEqual(2);
  });

  it('pending → active → done 前向迁移并合并产物字段', () => {
    const loop = createLoopSlice();
    loop.startPlan({ steps: [step({ id: 's1', phase: 'retrieve', status: 'pending', label: '查证' })] });

    loop.markActive('s1');
    expect(loop.activeStep.value?.id).toBe('s1');
    expect(loop.steps.value[0]?.status).toBe('active');

    loop.markDone('s1', { durationMs: 120, reasonSummary: '命中 3 段课件', toolName: 'HANDOUT' });
    const done = loop.steps.value[0];
    expect(done?.status).toBe('done');
    expect(done?.durationMs).toBe(120);
    expect(done?.reasonSummary).toBe('命中 3 段课件');
    expect(done?.toolName).toBe('HANDOUT');
    expect(loop.activeStep.value).toBeNull();
  });

  it('pending → active → failed 记录错误', () => {
    const loop = createLoopSlice();
    loop.startPlan({ steps: [step({ id: 's1', phase: 'act', status: 'pending' })] });

    loop.markActive('s1');
    loop.markFailed('s1', { error: '浏览器步骤超时' });
    expect(loop.steps.value[0]?.status).toBe('failed');
    expect(loop.steps.value[0]?.error).toBe('浏览器步骤超时');
  });

  it('终态前向单调：done 之后再来 active/pending 被忽略（防乱序闪烁）', () => {
    const loop = createLoopSlice();
    loop.startPlan({ steps: [step({ id: 's1', phase: 'compose', status: 'pending' })] });
    loop.markActive('s1');
    loop.markDone('s1');

    loop.markActive('s1'); // 乱序迟到的 STEP_STARTED
    expect(loop.steps.value[0]?.status).toBe('done');
    loop.applyStepUpdate(step({ id: 's1', phase: 'compose', status: 'pending' }));
    expect(loop.steps.value[0]?.status).toBe('done');
  });

  it('applyStepUpdate 合并不丢已存在的可选字段（update 省略时保留）', () => {
    const loop = createLoopSlice();
    loop.startPlan({ steps: [step({ id: 's1', phase: 'retrieve', status: 'pending' })] });
    loop.markActive('s1', { toolName: 'BROWSE' });
    loop.markDone('s1', { durationMs: 50 }); // 不再带 toolName
    expect(loop.steps.value[0]?.toolName).toBe('BROWSE');
    expect(loop.steps.value[0]?.durationMs).toBe(50);
  });

  it('finishRun → done / isComplete；failRun → error 并兜底标记进行中步骤为 failed', () => {
    const loop = createLoopSlice();
    loop.startPlan({ steps: [step({ id: 's1', phase: 'act', status: 'pending' })] });
    loop.markActive('s1');
    loop.failRun('上游 500');
    expect(loop.status.value).toBe('error');
    expect(loop.isFailed.value).toBe(true);
    expect(loop.runError.value).toBe('上游 500');
    expect(loop.steps.value[0]?.status).toBe('failed');
    expect(loop.steps.value[0]?.error).toContain('上游 500');

    const ok = createLoopSlice();
    ok.startPlan({ steps: [step({ id: 's1', phase: 'compose', status: 'pending' })] });
    ok.markDone('s1');
    ok.finishRun();
    expect(ok.status.value).toBe('done');
    expect(ok.isComplete.value).toBe(true);
  });

  it('stop() 可中断：interrupted=true、streaming 终止、isStreaming=false', () => {
    const loop = createLoopSlice();
    loop.startPlan({ steps: [step({ id: 's1', phase: 'compose', status: 'pending' })] });
    loop.markActive('s1');
    expect(loop.isStreaming.value).toBe(true);

    loop.stop();
    expect(loop.interrupted.value).toBe(true);
    expect(loop.isStreaming.value).toBe(false);
  });

  it('markStepStatus 对不存在的步骤是 no-op（不创建幽灵步骤）', () => {
    const loop = createLoopSlice();
    loop.startPlan({ steps: [step({ id: 's1', phase: 'plan', status: 'pending' })] });
    loop.markDone('ghost');
    expect(loop.steps.value).toHaveLength(1);
  });

  it('reset 清空回 idle', () => {
    const loop = createLoopSlice();
    loop.startPlan({ steps: [step({ id: 's1', phase: 'plan', status: 'pending' })] });
    loop.markActive('s1');
    loop.reset();
    expect(loop.status.value).toBe('idle');
    expect(loop.steps.value).toHaveLength(0);
    expect(loop.runError.value).toBeNull();
    expect(loop.interrupted.value).toBe(false);
  });
});

describe('loopSlice —— SSE 事件消费 ②', () => {
  it('RUN_STARTED → streaming；loop_plan → 步骤；loop_step → 状态机；RUN_FINISHED → done', () => {
    const loop = createLoopSlice();

    loop.ingestSseEvent({ type: 'RUN_STARTED' });
    expect(loop.status.value).toBe('streaming');

    loop.ingestSseEvent(
      customEvent('loop_plan', {
        maxSteps: 4,
        steps: [
          { id: 's1', phase: 'retrieve', label: '查证课件', status: 'pending' },
          { id: 's2', phase: 'compose', label: '组织作答', status: 'pending' },
        ],
      }),
    );
    expect(loop.steps.value).toHaveLength(2);
    expect(loop.maxSteps.value).toBe(4);

    loop.ingestSseEvent(
      customEvent('loop_step', {
        id: 's1',
        phase: 'retrieve',
        label: '查证课件',
        status: 'active',
        toolName: 'HANDOUT',
      }),
    );
    expect(loop.activeStep.value?.id).toBe('s1');
    expect(loop.activeStep.value?.toolName).toBe('HANDOUT');

    loop.ingestSseEvent(
      customEvent('loop_step', {
        id: 's1',
        phase: 'retrieve',
        label: '查证课件',
        status: 'done',
        durationMs: 88,
        citations: [{ kind: 'courseware', ref: '12', label: '第 12 页' }],
        reasonSummary: '命中输入函数定义页',
      }),
    );
    const s1 = loop.steps.value.find((s) => s.id === 's1');
    expect(s1?.status).toBe('done');
    expect(s1?.citations?.[0]?.kind).toBe('courseware');
    expect(s1?.reasonSummary).toBe('命中输入函数定义页');

    loop.ingestSseEvent({ type: 'RUN_FINISHED', result: { ok: true } });
    expect(loop.status.value).toBe('done');
  });

  it('RUN_ERROR → error 态 + runError', () => {
    const loop = createLoopSlice();
    loop.ingestSseEvent(
      customEvent('loop_plan', { steps: [{ id: 's1', phase: 'act', label: '执行', status: 'active' }] }),
    );
    loop.ingestSseEvent({ type: 'RUN_ERROR', code: 'UPSTREAM', message: '编排失败' });
    expect(loop.status.value).toBe('error');
    expect(loop.runError.value).toBe('编排失败');
    expect(loop.steps.value[0]?.status).toBe('failed');
  });

  it('脏事件 fail-soft：缺 id / 非对象 / 未知 CUSTOM 一律忽略不抛', () => {
    const loop = createLoopSlice();
    loop.startPlan({ steps: [step({ id: 's1', phase: 'plan', status: 'pending' })] });

    expect(() => {
      loop.ingestSseEvent(customEvent('loop_step', null));
      loop.ingestSseEvent(customEvent('loop_step', { phase: 'act', status: 'active' })); // 缺 id
      loop.ingestSseEvent(customEvent('loop_step', { id: 's9', phase: 'bogus', status: 'active' })); // 非法 phase
      loop.ingestSseEvent(customEvent('unknown_custom', { foo: 1 }));
    }).not.toThrow();

    expect(loop.steps.value).toHaveLength(1);
    expect(loop.steps.value[0]?.status).toBe('pending');
  });

  it('syncFromEvents ④：只消费累积数组的新增尾部，重复调用安全', () => {
    const loop = createLoopSlice();
    const events: AguiEvent[] = [];

    events.push({ type: 'RUN_STARTED' });
    loop.syncFromEvents(events);
    expect(loop.status.value).toBe('streaming');

    events.push(
      customEvent('loop_plan', {
        maxSteps: 3,
        steps: [{ id: 's1', phase: 'retrieve', label: '查证', status: 'pending' }],
      }),
    );
    loop.syncFromEvents(events);
    loop.syncFromEvents(events); // 重复调用不应回放重置
    expect(loop.steps.value).toHaveLength(1);

    events.push(customEvent('loop_step', { id: 's1', phase: 'retrieve', label: '查证', status: 'active' }));
    loop.syncFromEvents(events);
    expect(loop.activeStep.value?.id).toBe('s1');
  });
});
