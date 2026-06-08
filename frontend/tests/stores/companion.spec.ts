import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ref, nextTick } from 'vue';
import { createPinia, setActivePinia } from 'pinia';
import type { BugMineEchoCandidateDto } from '@/api/dto/BugMine';

const startSpy = vi.fn(async () => {});
const tokens = ref('');
const result = ref<unknown>(null);
const status = ref('idle');

vi.mock('@/composables/useSseStream', () => ({
  useSseStream: () => ({
    traces: ref([]),
    tokens,
    result,
    error: ref(null),
    status,
    plan: ref(null),
    abort: vi.fn(),
    start: startSpy,
  }),
}));

vi.mock('@/api/bugMine', () => ({
  getBugMineEchoes: vi.fn(async () => [] as BugMineEchoCandidateDto[]),
}));

vi.mock('@/api/insight', () => ({ getInsights: vi.fn() }));

import { useCompanionStore } from '@/stores/companion';
import { getBugMineEchoes } from '@/api/bugMine';
import { getInsights } from '@/api/insight';
import type { LearnerInsightDto } from '@/api/dto/Insight';

function makeEcho(over: Partial<BugMineEchoCandidateDto> = {}): BugMineEchoCandidateDto {
  return {
    kcCode: 'PY43-KC-016',
    problemId: 5,
    problemTitle: '圆面积',
    masteryScore: 0.4,
    confidence: 0.5,
    decayRisk: 0.7,
    minePriority: 0.8,
    recommendationReason: '到期复习',
    ...over,
  };
}

function makeInsight(over: Partial<LearnerInsightDto> = {}): LearnerInsightDto {
  return {
    id: 101,
    text: '列表别名可变性反复出错',
    dimensionKey: 'error_pattern',
    tags: ['list', 'mutability'],
    evidenceEventIds: [9001, 9002],
    confidence: 0.82,
    source: 'GRAPH',
    createdAt: '2026-06-08T10:00:00Z',
    ...over,
  };
}

describe('companion store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    startSpy.mockClear();
    tokens.value = '';
    result.value = null;
    status.value = 'idle';
    vi.mocked(getBugMineEchoes).mockResolvedValue([]);
    vi.mocked(getInsights).mockResolvedValue([]);
  });

  it('sends question + kcCodes + context to /api/tutor/ask', async () => {
    const store = useCompanionStore();
    await store.ask('什么是列表推导', { kcCodes: ['PY43-KC-016'], surface: 'training', context: 'c' });
    expect(startSpy).toHaveBeenCalledTimes(1);
    expect(startSpy.mock.calls[0][0]).toMatchObject({
      question: '什么是列表推导',
      kcCodes: ['PY43-KC-016'],
      context: 'c',
    });
  });

  it('accumulates streamed tokens into the last assistant message', async () => {
    const store = useCompanionStore();
    await store.ask('hi', { kcCodes: [], surface: 'world', context: '' });
    tokens.value = '部分答案';
    await nextTick();
    const last = store.messages[store.messages.length - 1];
    expect(last.role).toBe('assistant');
    expect(last.content).toBe('部分答案');
  });

  it('caps images at 3', async () => {
    const store = useCompanionStore();
    await store.ask('看图', { kcCodes: [], surface: 'world', context: '' }, ['a', 'b', 'c', 'd']);
    expect((startSpy.mock.calls[0][0] as { images: string[] }).images).toHaveLength(3);
  });

  it('forwards a numeric classroomId for teacher copilot context', async () => {
    const store = useCompanionStore();
    await store.ask('本班哪个知识点最弱', {
      kcCodes: [],
      surface: 'classroom',
      context: '教师视角',
      classroomId: '12',
    });
    expect(startSpy.mock.calls[0][0]).toMatchObject({ classroomId: 12 });
  });

  it('omits classroomId when absent (student / non-classroom)', async () => {
    const store = useCompanionStore();
    await store.ask('什么是字典', { kcCodes: [], surface: 'world', context: '', classroomId: null });
    expect(startSpy.mock.calls[0][0]).not.toHaveProperty('classroomId');
  });

  it('builds a nudge from the top due echo', async () => {
    vi.mocked(getBugMineEchoes).mockResolvedValue([makeEcho()]);
    const store = useCompanionStore();
    await store.loadNudge();
    expect(store.nudge?.kcCode).toBe('PY43-KC-016');
    expect(store.nudge?.text).toContain('复习');
    expect(store.nudge?.text).toContain('圆面积');
    store.dismissNudge();
    expect(store.nudge).toBeNull();
  });

  it('shows no nudge when nothing is due', async () => {
    vi.mocked(getBugMineEchoes).mockResolvedValue([]);
    const store = useCompanionStore();
    await store.loadNudge();
    expect(store.nudge).toBeNull();
  });

  it('does not re-pop a nudge after it was dismissed', async () => {
    vi.mocked(getBugMineEchoes).mockResolvedValue([makeEcho()]);
    const store = useCompanionStore();
    await store.loadNudge();
    store.dismissNudge();
    await store.loadNudge();
    expect(store.nudge).toBeNull();
  });

  it('loads the latest insight and asks for exactly one', async () => {
    vi.mocked(getInsights).mockResolvedValue([makeInsight()]);
    const store = useCompanionStore();
    await store.loadInsight();
    expect(getInsights).toHaveBeenCalledWith(1);
    expect(store.latestInsight?.id).toBe(101);
    expect(store.latestInsight?.text).toBe('列表别名可变性反复出错');
  });

  it('does not re-pop the same insight id after it was dismissed', async () => {
    vi.mocked(getInsights).mockResolvedValue([makeInsight()]);
    const store = useCompanionStore();
    await store.loadInsight();
    expect(store.latestInsight?.id).toBe(101);
    store.dismissInsight();
    expect(store.latestInsight).toBeNull();
    await store.loadInsight();
    expect(store.latestInsight).toBeNull();
  });

  it('still surfaces a different insight after dismissing a previous one', async () => {
    vi.mocked(getInsights).mockResolvedValue([makeInsight()]);
    const store = useCompanionStore();
    await store.loadInsight();
    store.dismissInsight();
    vi.mocked(getInsights).mockResolvedValue([makeInsight({ id: 202, text: '函数返回值理解偏差' })]);
    await store.loadInsight();
    expect(store.latestInsight?.id).toBe(202);
  });

  it('shows no insight when none is available (no nagging)', async () => {
    vi.mocked(getInsights).mockResolvedValue([]);
    const store = useCompanionStore();
    await store.loadInsight();
    expect(store.latestInsight).toBeNull();
  });

  it('swallows insight load failures and stays null', async () => {
    vi.mocked(getInsights).mockRejectedValue(new Error('boom'));
    const store = useCompanionStore();
    await expect(store.loadInsight()).resolves.toBeUndefined();
    expect(store.latestInsight).toBeNull();
  });
});
