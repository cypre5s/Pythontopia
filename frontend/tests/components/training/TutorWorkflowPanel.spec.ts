import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import { createPinia, setActivePinia } from 'pinia';
import TutorWorkflowPanel from '@/components/training/TutorWorkflowPanel.vue';

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock('@/api/tutorWorkflow', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/tutorWorkflow')>();
  return {
    ...actual,
    getActiveWorkflowSession: vi.fn(async () => ({
      sessionKey: 'wf-test',
      problemId: 476,
      phase: 'READING',
      seq: 0,
      cards: [],
    })),
  };
});

vi.mock('@/composables/useSseStream', async () => {
  const { ref } = await import('vue');
  const status = ref<'idle' | 'connecting' | 'streaming' | 'done' | 'error'>('idle');
  const result = ref<Record<string, unknown> | null>(null);
  const error = ref<{ message: string } | null>(null);
  const traces = ref<unknown[]>([]);
  const tokens = ref('');
  const plan = ref<unknown>(null);
  const events = ref<unknown[]>([]);
  const state = ref<Record<string, unknown>>({});
  const start = vi.fn(async () => {
    result.value = {
      session_id: 'wf-test',
      event: 'READING',
      phase: 'READING',
      card_key: null,
      card: null,
      seq: 1,
      available_actions: [
        {
          key: 'execution_trace',
          label: '执行轨迹讲解',
          event: 'EXECUTION_TRACE',
          recommended: true,
          reason: '先看变量如何变化，再定位第一处偏差。',
        },
        {
          key: 'knowledge_review',
          label: '知识点复盘',
          event: 'KNOWLEDGE_REVIEW',
          recommended: false,
          reason: '',
        },
      ],
    };
    status.value = 'done';
  });
  const abort = vi.fn();
  const streamState = { status, result, error, traces, tokens, plan, events, state, start, abort };
  return { useSseStream: () => streamState };
});

import { useSseStream } from '@/composables/useSseStream';

interface MockStream {
  status: { value: 'idle' | 'connecting' | 'streaming' | 'done' | 'error' };
  result: { value: Record<string, unknown> | null };
  error: { value: { message: string } | null };
  traces: { value: unknown[] };
  tokens: { value: string };
  plan: { value: unknown };
  events: { value: unknown[] };
  state: { value: Record<string, unknown> };
  start: ReturnType<typeof vi.fn>;
}

function getStream(): MockStream {
  return useSseStream('/api/tutor/workflow/runs') as unknown as MockStream;
}

function mountPanel() {
  const pinia = createPinia();
  setActivePinia(pinia);
  return mount(TutorWorkflowPanel, {
    props: { problemId: 476, kcCodes: ['PY43-KC-016'], submissionId: 21 },
    global: {
      plugins: [pinia],
      stubs: {
        MarkdownView: true,
        RouterLink: true,
      },
    },
  });
}

describe('TutorWorkflowPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    const stream = getStream();
    stream.status.value = 'idle';
    stream.result.value = null;
    stream.error.value = null;
    stream.traces.value = [];
    stream.tokens.value = '';
    stream.plan.value = null;
    stream.events.value = [];
    stream.state.value = {};
    stream.start.mockClear();
  });

  it('highlights the coach recommended next action without hiding other actions', async () => {
    const wrapper = mountPanel();
    await flushPromises();

    await wrapper.get('[data-testid="tw-actions"] button').trigger('click');
    await flushPromises();
    await nextTick();

    const recommended = wrapper.get('[data-testid="coach-recommended-action"]');
    expect(recommended.text()).toContain('推荐下一步');
    expect(recommended.text()).toContain('执行轨迹讲解');
    expect(recommended.text()).toContain('先看变量如何变化');
    expect(wrapper.get('[data-testid="tw-actions"]').text()).toContain('知识点复盘');
  });
});
