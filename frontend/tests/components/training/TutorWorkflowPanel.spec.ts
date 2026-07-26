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
    recordTutorWorkflowFeedback: vi.fn(async () => undefined),
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
      card_key: 'problem_guide',
      card: {
        card_id: 'card-reading-1',
        problem_restatement: '题目要求读取半径并输出面积。',
        input_output_focus: '关注浮点输入输出。',
        key_observation: '面积公式需要半径平方。',
        starter_questions: ['半径在哪里读入？'],
        related_kcs: ['PY43-KC-016'],
        teaching_goal: '读懂题意。',
        checkpoint_prompt: '你能说出输入和输出吗？',
        reflection_prompt: '这题最容易漏哪一步？',
      },
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
import { recordTutorWorkflowFeedback } from '@/api/tutorWorkflow';
import { useToastStore } from '@stores/toast';

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
  abort: ReturnType<typeof vi.fn>;
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
    stream.abort.mockClear();
    vi.mocked(recordTutorWorkflowFeedback).mockClear();
  });

  it('aborts the in-flight stream on unmount and stays silent afterwards', async () => {
    const stream = getStream();
    let releaseRun: (() => void) | null = null;
    stream.start.mockImplementationOnce(async () => {
      stream.status.value = 'connecting';
      await new Promise<void>((resolve) => {
        releaseRun = resolve;
      });
      // useSseStream.abort() 会把在途状态置成 done，但 result 仍为空。
      stream.status.value = 'done';
    });

    const wrapper = mountPanel();
    await flushPromises();
    await wrapper.get('[data-testid="tw-actions"] button').trigger('click');
    await nextTick();

    wrapper.unmount();
    expect(stream.abort).toHaveBeenCalled();

    releaseRun?.();
    await flushPromises();

    // 已销毁的面板不得再对着别的页面弹「导学助手暂时没响应」。
    const toast = useToastStore();
    expect(toast.message).toBe('');
    expect(toast.visible).toBe(false);
  });

  it('highlights the coach recommended next action and tucks tools into a collapsible drawer', async () => {
    const wrapper = mountPanel();
    await flushPromises();

    // 初始主推荐是「题目导读」；点它触发一次 run，返回 execution_trace(recommended)+knowledge_review。
    await wrapper.get('[data-testid="tw-actions"] button').trigger('click');
    await flushPromises();
    await nextTick();

    const recommended = wrapper.get('[data-testid="coach-recommended-action"]');
    expect(recommended.text()).toContain('推荐下一步');
    expect(recommended.text()).toContain('执行轨迹讲解');
    expect(recommended.text()).toContain('先看变量如何变化');

    // 辅助工具默认收纳，不直接铺成一排按钮；展开「学习工具」后才出现知识点复盘。
    expect(wrapper.get('[data-testid="tw-actions"]').text()).not.toContain('知识点复盘');
    await wrapper.get('.tw-tools__toggle').trigger('click');
    await nextTick();
    expect(wrapper.get('[data-testid="tw-actions"]').text()).toContain('知识点复盘');
  });

  it('records action click feedback before running the selected tool', async () => {
    const wrapper = mountPanel();
    await flushPromises();

    await wrapper.get('[data-testid="tw-actions"] button').trigger('click');
    await flushPromises();

    expect(recordTutorWorkflowFeedback).toHaveBeenCalledWith('wf-test', {
      eventType: 'ACTION_CLICKED',
      actionKey: 'problem_guide',
      payload: { event: 'READING' },
    });
    expect(getStream().start).toHaveBeenCalledTimes(1);
  });

  it('records helpful card feedback without blocking the workflow UI', async () => {
    const wrapper = mountPanel();
    await flushPromises();
    await wrapper.get('[data-testid="tw-actions"] button').trigger('click');
    await flushPromises();

    await wrapper.get('[data-testid="card-helpful"]').trigger('click');
    await flushPromises();

    expect(recordTutorWorkflowFeedback).toHaveBeenCalledWith('wf-test', {
      eventType: 'CARD_HELPFUL',
      cardKey: 'problem_guide',
      payload: { cardId: 'card-reading-1', seq: 1 },
    });
    expect(wrapper.get('[data-testid="card-feedback-state"]').text()).toContain('已记录');
  });

  it('renders bounded coach progress from trace nodes without exposing raw internals', async () => {
    const stream = getStream();
    stream.status.value = 'streaming';
    stream.traces.value = [
      { node: 'coach.load_state', phase: 'start', status: 'running', elapsed_ms: 0 },
      { node: 'coach.decide', phase: 'start', status: 'running', elapsed_ms: 1 },
      { node: 'coach.tool.execution_trace', phase: 'start', status: 'running', elapsed_ms: 2 },
      { node: 'coach.guardrail', phase: 'start', status: 'running', elapsed_ms: 3 },
    ];

    const wrapper = mountPanel();
    await flushPromises();

    const progress = wrapper.get('[data-testid="coach-progress"]');
    // 思考过程默认只露一行「思考中…」，详情收纳；展开后才显示各节点（用户要求缩小收纳）。
    expect(progress.text()).toContain('百晓思考中');
    expect(progress.text()).not.toContain('正在判断你卡在哪里');
    await wrapper.get('.tw-think__toggle').trigger('click');
    await nextTick();
    expect(progress.text()).toContain('正在判断你卡在哪里');
    expect(progress.text()).toContain('正在选择下一步提示');
    expect(progress.text()).toContain('正在生成导学卡');
    expect(progress.text()).toContain('正在检查是否过度提示');
    expect(progress.text()).not.toContain('coach.decide');
  });
});
