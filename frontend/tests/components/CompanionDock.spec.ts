import { describe, it, expect, beforeEach, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia, type Pinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import { nextTick, ref } from 'vue';

const startSpy = vi.fn(async () => {});
const traces = ref<Array<{ node: string; phase?: 'start' | 'finish'; status: string; elapsed_ms: number }>>([]);
const tokens = ref('');
const result = ref<unknown>(null);
const status = ref('idle');

vi.mock('@/composables/useSseStream', () => ({
  useSseStream: () => ({
    traces,
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

import CompanionDock from '@/components/companion/CompanionDock.vue';
import { useCompanionStore } from '@/stores/companion';
import { useSessionStore } from '@/stores/session';
import { getBugMineEchoes } from '@/api/bugMine';
import { getInsights } from '@/api/insight';
import type { BugMineEchoCandidateDto } from '@/api/dto/BugMine';
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

let pinia: Pinia;

function mountDock() {
  const router: Router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/training', name: 'training', component: { template: '<div />' } },
      { path: '/library', name: 'library', component: { template: '<div />' } },
      { path: '/bug-mine', name: 'bug-mine', component: { template: '<div />' } },
    ],
  });
  return mount(CompanionDock, { global: { plugins: [pinia, router] } });
}

describe('CompanionDock', () => {
  beforeEach(() => {
    pinia = createPinia();
    setActivePinia(pinia);
    startSpy.mockClear();
    traces.value = [];
    tokens.value = '';
    result.value = null;
    status.value = 'idle';
    vi.mocked(getBugMineEchoes).mockResolvedValue([]);
    vi.mocked(getInsights).mockResolvedValue([]);
  });

  it('shows the fab collapsed and opens the panel on click', async () => {
    const wrapper = mountDock();
    expect(wrapper.find('[data-testid="companion-fab"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="companion-panel"]').exists()).toBe(false);
    await wrapper.find('[data-testid="companion-fab"]').trigger('click');
    expect(wrapper.find('[data-testid="companion-panel"]').exists()).toBe(true);
  });

  it('sends a question through the store', async () => {
    const wrapper = mountDock();
    const store = useCompanionStore();
    store.open = true;
    await flushPromises();
    await wrapper.find('[data-testid="companion-input"]').setValue('什么是字典');
    await wrapper.find('[data-testid="companion-send"]').trigger('submit');
    await flushPromises();
    expect(startSpy).toHaveBeenCalled();
    expect(store.messages.some((m) => m.role === 'user' && m.content === '什么是字典')).toBe(true);
  });

  it('navigates to the bug mine via the action chip', async () => {
    const wrapper = mountDock();
    const store = useCompanionStore();
    store.open = true;
    await flushPromises();
    const router = wrapper.vm.$router;
    const pushSpy = vi.spyOn(router, 'push');
    await wrapper.find('[data-testid="companion-action-mine"]').trigger('click');
    expect(pushSpy).toHaveBeenCalledWith({ path: '/bug-mine' });
  });

  it('does not expose browser or server speech recognition controls', async () => {
    const wrapper = mountDock();
    useCompanionStore().open = true;
    await flushPromises();

    expect(wrapper.find('[data-testid="companion-mic"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="companion-record"]').exists()).toBe(false);
  });

  it('adds pasted images as thumbnails and sends them with the question', async () => {
    class FakeFileReader {
      result: string | ArrayBuffer | null = 'data:image/png;base64,abc';
      onload: (() => void) | null = null;
      readAsDataURL = vi.fn(() => {
        this.onload?.();
      });
    }
    vi.stubGlobal('FileReader', FakeFileReader);

    const wrapper = mountDock();
    const store = useCompanionStore();
    store.open = true;
    await flushPromises();

    const file = new File(['png'], 'question.png', { type: 'image/png' });
    await wrapper.find('[data-testid="companion-panel"]').trigger('paste', {
      clipboardData: {
        items: [{ kind: 'file', type: 'image/png', getAsFile: () => file }],
      },
    });
    await flushPromises();

    expect(wrapper.find('[data-testid="companion-thumb-0"]').exists()).toBe(true);
    await wrapper.find('[data-testid="companion-input"]').setValue('看这张图');
    await wrapper.find('[data-testid="companion-send"]').trigger('submit');
    await flushPromises();

    expect(startSpy.mock.calls[0][0]).toMatchObject({
      question: '看这张图',
      images: ['data:image/png;base64,abc'],
    });
    vi.unstubAllGlobals();
  });

  it('pops a dismissable review nudge and routes to the bug mine', async () => {
    vi.mocked(getBugMineEchoes).mockResolvedValue([makeEcho()]);
    const wrapper = mountDock();
    await flushPromises();

    const nudge = wrapper.find('[data-testid="companion-nudge"]');
    expect(nudge.exists()).toBe(true);
    expect(nudge.text()).toContain('圆面积');

    const router = wrapper.vm.$router;
    const pushSpy = vi.spyOn(router, 'push');
    await wrapper.find('[data-testid="companion-nudge-go"]').trigger('click');
    expect(pushSpy).toHaveBeenCalledWith({ path: '/bug-mine' });
  });

  it('dismisses the nudge on close', async () => {
    vi.mocked(getBugMineEchoes).mockResolvedValue([makeEcho()]);
    const wrapper = mountDock();
    await flushPromises();
    expect(wrapper.find('[data-testid="companion-nudge"]').exists()).toBe(true);
    await wrapper.find('[data-testid="companion-nudge-close"]').trigger('click');
    expect(wrapper.find('[data-testid="companion-nudge"]').exists()).toBe(false);
  });

  it('shows a verified badge when code_runner finishes', async () => {
    const wrapper = mountDock();
    const store = useCompanionStore();
    store.open = true;
    store.messages.push({ role: 'assistant', content: '已结合真实输出分析。', evidencePageIds: [] });
    traces.value = [{ node: 'code_runner', phase: 'finish', status: 'ok', elapsed_ms: 8 }];
    await nextTick();

    expect(wrapper.find('[data-testid="companion-verified"]').exists()).toBe(true);
  });

  it('pops an insight bubble and reveals evidence clues on demand', async () => {
    vi.mocked(getInsights).mockResolvedValue([makeInsight()]);
    const wrapper = mountDock();
    await flushPromises();

    const insight = wrapper.find('[data-testid="companion-insight"]');
    expect(insight.exists()).toBe(true);
    expect(insight.text()).toContain('列表别名可变性反复出错');

    // 证据默认收起，点开后才出现并带上 evidenceEventId 线索。
    expect(wrapper.find('[data-testid="companion-insight-evidence"]').exists()).toBe(false);
    await wrapper.find('[data-testid="companion-insight-evidence-toggle"]').trigger('click');
    const evidence = wrapper.find('[data-testid="companion-insight-evidence"]');
    expect(evidence.exists()).toBe(true);
    expect(evidence.text()).toContain('9001');
  });

  it('dismisses the insight bubble on close', async () => {
    vi.mocked(getInsights).mockResolvedValue([makeInsight()]);
    const wrapper = mountDock();
    await flushPromises();
    expect(wrapper.find('[data-testid="companion-insight"]').exists()).toBe(true);

    await wrapper.find('[data-testid="companion-insight-close"]').trigger('click');
    expect(wrapper.find('[data-testid="companion-insight"]').exists()).toBe(false);
  });

  it('shows no insight bubble when none is available', async () => {
    vi.mocked(getInsights).mockResolvedValue([]);
    const wrapper = mountDock();
    await flushPromises();
    expect(wrapper.find('[data-testid="companion-insight"]').exists()).toBe(false);
  });

  it('does not request student-only signals for teacher sessions', async () => {
    const session = useSessionStore();
    session.user = {
      id: 77,
      username: 'teacher77',
      displayName: '教师 77',
      role: 'TEACHER',
    };
    vi.mocked(getBugMineEchoes).mockClear();
    vi.mocked(getInsights).mockClear();

    mountDock();
    await flushPromises();

    expect(getBugMineEchoes).not.toHaveBeenCalled();
    expect(getInsights).not.toHaveBeenCalled();
  });
});
