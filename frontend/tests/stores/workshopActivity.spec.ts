/**
 * workshopActivity 共享「智能体活动」状态测试（伴学向导悬浮窗 ⇄ 百晓工坊联动）。
 *
 * 覆盖：
 *   1. 向导（GuideOverlay）发起的 orchestrator 活动写入共享状态（工坊据此点亮思考链），
 *      start 返回自增「运行令牌」供发起方后续镜像/收尾校验；
 *   2. 旧签名（不带令牌）镜像保持兼容——向导现有调用不受影响；
 *   3. 过期的流不会覆盖已有的新活动：旧令牌的 traces / plan / finish 一律忽略；
 *   4. 新活动开始后旧会话残留数据被忽略：start 清场 + 晚到残留不回灌；
 *   5. kind 串台保护与 idle 复位（既有行为回归）。
 */
import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useWorkshopActivityStore } from '@stores/workshopActivity';
import type { SsePlan, SseTraceEvent } from '@/composables/useSseStream';

const TRACE_A: SseTraceEvent = { node: 'understand', status: 'ok', elapsed_ms: 3 };
const TRACE_B: SseTraceEvent = { node: 'decompose', status: 'ok', elapsed_ms: 7 };
const PLAN_A: SsePlan = {
  type: 'plan',
  nodes: [{ node: 'resource_designer', agent_label: '资源设计师', kind: 'agent' }],
};
const PLAN_B: SsePlan = {
  type: 'plan',
  nodes: [{ node: 'path_planner', agent_label: '路径规划师', kind: 'agent' }],
};

describe('workshopActivity 共享活动状态', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('向导发起的 orchestrator 活动进入共享状态，start 返回自增运行令牌', () => {
    const store = useWorkshopActivityStore();
    expect(store.status).toBe('idle');
    expect(store.hasRun).toBe(false);

    // 模拟伴学悬浮窗（GuideOverlay）发起一轮协同规划。
    const token = store.start('orchestrator', '协同规划：我想学循环');

    expect(typeof token).toBe('number');
    expect(store.kind).toBe('orchestrator');
    expect(store.status).toBe('streaming');
    expect(store.label).toBe('协同规划：我想学循环');
    expect(store.isBusy).toBe(true);
    expect(store.hasRun).toBe(true);
    expect(store.startedAt).toBeGreaterThan(0);
    expect(store.seq).toBe(token);

    // 再开一轮：令牌严格自增（防回绕串台）。
    const token2 = store.start('orchestrator', '协同规划：再来一轮');
    expect(token2).toBeGreaterThan(token);
    expect(store.seq).toBe(token2);
  });

  it('旧签名（不带令牌）的镜像保持兼容：向导现有调用照常生效', () => {
    const store = useWorkshopActivityStore();
    store.start('orchestrator', '协同规划：学函数');

    // GuideOverlay 现网调用形如 syncTraces('orchestrator', tr) / syncPlan('orchestrator', p)。
    store.syncTraces('orchestrator', [TRACE_A]);
    store.syncPlan('orchestrator', PLAN_A);
    expect(store.traces).toEqual([TRACE_A]);
    expect(store.plan).toEqual(PLAN_A);
    expect(store.activeNode).toBe('understand');

    store.finish(true);
    expect(store.status).toBe('done');
  });

  it('过期的流不会覆盖已有的新活动（旧令牌 traces/plan/finish 全部忽略）', () => {
    const store = useWorkshopActivityStore();

    // 第一轮：工坊 PLAN 持令牌镜像。
    const stale = store.start('orchestrator', '学习规划');
    store.syncTraces('orchestrator', [TRACE_A], stale);
    expect(store.traces).toEqual([TRACE_A]);

    // 向导接管开新一轮：start 清场。
    const fresh = store.start('orchestrator', '协同规划：向导新任务');
    expect(store.traces).toEqual([]);
    expect(store.plan).toBeNull();

    // 旧流还在吐数据：携带过期令牌 → 一律忽略，不得写花新活动。
    store.syncTraces('orchestrator', [TRACE_A], stale);
    store.syncPlan('orchestrator', PLAN_A, stale);
    expect(store.traces).toEqual([]);
    expect(store.plan).toBeNull();

    // 旧流收尾也不得把新活动标记完结。
    store.finish(false, stale);
    expect(store.status).toBe('streaming');
    expect(store.label).toBe('协同规划：向导新任务');

    // 新一轮持有效令牌：正常镜像、正常收尾。
    store.syncTraces('orchestrator', [TRACE_B], fresh);
    store.syncPlan('orchestrator', PLAN_B, fresh);
    expect(store.traces).toEqual([TRACE_B]);
    expect(store.plan).toEqual(PLAN_B);
    store.finish(true, fresh);
    expect(store.status).toBe('done');
  });

  it('新活动开始后旧会话残留数据被忽略（start 清场 + 晚到残留不回灌）', () => {
    const store = useWorkshopActivityStore();

    // 旧会话：完整跑完一轮并归档。
    const old = store.start('orchestrator', '旧会话规划');
    store.syncTraces('orchestrator', [TRACE_A], old);
    store.syncPlan('orchestrator', PLAN_A, old);
    store.finish(true, old);

    // 新活动开始：旧会话的 plan/traces 立即清场。
    const next = store.start('orchestrator', '新会话规划');
    expect(store.plan).toBeNull();
    expect(store.traces).toEqual([]);

    // 旧会话残留数据晚到（如慢请求最后一帧）：被忽略。
    store.syncPlan('orchestrator', PLAN_A, old);
    store.syncTraces('orchestrator', [TRACE_A], old);
    store.finish(false, old);
    expect(store.plan).toBeNull();
    expect(store.traces).toEqual([]);
    expect(store.status).toBe('streaming');
    expect(store.seq).toBe(next);
  });

  it('kind 不匹配或已完工时不接受镜像；idle 复位全部字段（回归）', () => {
    const store = useWorkshopActivityStore();
    const token = store.start('orchestrator', '协同规划');

    // 另一条 kind 的流（资源生成）不得写入 orchestrator 活动——即使令牌正确。
    store.syncTraces('resource', [TRACE_A], token);
    store.syncTraces('ask', [TRACE_A]);
    expect(store.traces).toEqual([]);

    // 完工后（非 streaming）一律不再接受镜像。
    store.finish(true, token);
    store.syncTraces('orchestrator', [TRACE_A], token);
    store.syncTraces('orchestrator', [TRACE_A]);
    expect(store.traces).toEqual([]);

    store.idle();
    expect(store.status).toBe('idle');
    expect(store.kind).toBeNull();
    expect(store.label).toBe('');
    expect(store.plan).toBeNull();
    expect(store.traces).toEqual([]);
    expect(store.startedAt).toBe(0);
  });
});
