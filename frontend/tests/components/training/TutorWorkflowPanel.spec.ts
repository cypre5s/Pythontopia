import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import { createPinia, setActivePinia } from 'pinia';
import { useLearningStore } from '@stores/learning';
import { runTrace } from '@/services/codeTheater/trace';
import type { Trace } from '@/services/codeTheater/types';
import TutorWorkflowPanel from '@/components/training/TutorWorkflowPanel.vue';

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock('@/services/codeTheater/trace', () => ({ runTrace: vi.fn() }));

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
    uploadTutorWorkflowImage: vi.fn(async () => ({
      attachmentId: 'd0162eb8-8c60-4d85-a3da-3aa3f11460ba',
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
import { getActiveWorkflowSession, recordTutorWorkflowFeedback, uploadTutorWorkflowImage } from '@/api/tutorWorkflow';
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

function mountPanel(props: { initialEventRequest?: { event: 'EXECUTION_TRACE'; nonce: number } } = {}) {
  const pinia = createPinia();
  setActivePinia(pinia);
  return mount(TutorWorkflowPanel, {
    props: { problemId: 476, kcCodes: ['PY43-KC-016'], submissionId: 21, ...props },
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
    vi.mocked(runTrace).mockReset();
    vi.mocked(recordTutorWorkflowFeedback).mockClear();
    vi.mocked(uploadTutorWorkflowImage).mockClear();
    vi.mocked(getActiveWorkflowSession).mockClear();
  });

  it('uses the browser trace for EXECUTION_TRACE and keeps it out of workflow feedback', async () => {
    const code = 'radius = int(input())\nprint(radius)';
    const trace: Trace = {
      version: 1,
      source: code,
      events: [
        { step: 0, line: 1, kind: 'assign', varName: 'radius', value: '3', scope: 'global' },
        { step: 1, line: 2, kind: 'output', text: '3' },
      ],
      stdout: '3\n',
      truncated: false,
      stepCount: 2,
    };
    vi.mocked(runTrace).mockResolvedValue(trace);
    const wrapper = mountPanel();
    const learning = useLearningStore();
    learning.setTrainingCode(code);
    learning.training.problem = {
      id: 476,
      packId: 43,
      kcCode: 'PY43-KC-016',
      title: '读取并输出数字',
      statementMarkdown: '',
      inputDescription: '',
      outputDescription: '',
      difficulty: 'easy',
      samples: [{ input: '  3 \n4\n', output: '3' }],
    };
    await flushPromises();
    await wrapper.get('.tw-tools__toggle').trigger('click');
    const traceAction = wrapper.get('.tw-tools__menu').findAll('button')
      .find((button) => button.text().includes('执行轨迹讲解'));
    expect(traceAction).toBeDefined();
    await traceAction!.trigger('click');
    await flushPromises();

    expect(runTrace).toHaveBeenCalledWith(code, { stdin: '  3 \n4\n' });
    expect(getStream().start).not.toHaveBeenCalled();
    expect(recordTutorWorkflowFeedback).not.toHaveBeenCalled();
    expect(wrapper.get('[data-testid="execution-trace-local"]').text())
      .toContain('浏览器本地预览');
    expect(wrapper.get('[data-testid="execution-trace-local"]').text())
      .toContain('不用于判题或掌握度');
    expect(wrapper.get('[data-card="execution_trace"]').text()).toContain('变量 radius = 3');
    expect(wrapper.get('[data-card="execution_trace"]').text()).toContain('实际输出：3');
    expect(wrapper.get('[data-card="execution_trace"]').element.textContent).toContain('  3 \n4\n');
    expect(wrapper.find('[data-testid="card-helpful"]').exists()).toBe(false);
    expect(recordTutorWorkflowFeedback).not.toHaveBeenCalled();
  });

  it('shows a clear message and skips server fallback when there is no code', async () => {
    mountPanel({ initialEventRequest: { event: 'EXECUTION_TRACE', nonce: 1 } });
    await flushPromises();

    expect(runTrace).not.toHaveBeenCalled();
    expect(getStream().start).not.toHaveBeenCalled();
    expect(useToastStore().message).toContain('请先在编辑器中编写 Python 代码');
  });

  it('shows a local execution error without falling back to the workflow server', async () => {
    vi.mocked(runTrace).mockRejectedValue(new Error('Pyodide unavailable'));
    mountPanel({ initialEventRequest: { event: 'EXECUTION_TRACE', nonce: 1 } });
    useLearningStore().setTrainingCode('print(1)');
    await flushPromises();

    expect(runTrace).toHaveBeenCalledWith('print(1)', { stdin: '' });
    expect(getStream().start).not.toHaveBeenCalled();
    expect(useToastStore().message).toContain('本地执行轨迹生成失败');
    expect(useToastStore().message).toContain('Pyodide unavailable');
  });

  afterEach(() => vi.unstubAllGlobals());

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
    await wrapper.get('[data-testid="coach-recommended-action"]').trigger('click');
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
    await wrapper.get('[data-testid="coach-recommended-action"]').trigger('click');
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

    await wrapper.get('[data-testid="coach-recommended-action"]').trigger('click');
    await flushPromises();

    expect(recordTutorWorkflowFeedback).toHaveBeenCalledWith('wf-test', {
      eventType: 'ACTION_CLICKED',
      actionKey: 'problem_guide',
      payload: { event: 'READING' },
    });
    expect(getStream().start).toHaveBeenCalledTimes(1);
  });

  it('retries an interrupted workflow with the same idempotency key and payload', async () => {
    vi.stubGlobal('crypto', { randomUUID: () => 'request-1' });
    const stream = getStream();
    stream.start.mockImplementationOnce(async () => {
      stream.status.value = 'error';
      stream.error.value = { message: '连接中断' };
    });

    const wrapper = mountPanel();
    await flushPromises();
    await wrapper.get('[data-testid="coach-recommended-action"]').trigger('click');
    await flushPromises();

    expect(wrapper.get('[data-testid="tutor-workflow-retry"]').text()).toContain('重试');
    await wrapper.get('[data-testid="tutor-workflow-retry"]').trigger('click');
    await flushPromises();

    const firstRequest = stream.start.mock.calls[0]?.[0] as Record<string, unknown>;
    const retryRequest = stream.start.mock.calls[1]?.[0] as Record<string, unknown>;
    expect(firstRequest).toMatchObject({ requestId: 'request-1', problemId: 476, event: 'READING' });
    expect(retryRequest).toEqual(firstRequest);
    expect(wrapper.find('[data-testid="tutor-workflow-retry"]').exists()).toBe(false);
  });

  it('records helpful card feedback without blocking the workflow UI', async () => {
    const wrapper = mountPanel();
    await flushPromises();
    await wrapper.get('[data-testid="coach-recommended-action"]').trigger('click');
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

  it('asks the tutor to re-explain a card after negative feedback', async () => {
    const wrapper = mountPanel();
    await flushPromises();
    await wrapper.get('[data-testid="coach-recommended-action"]').trigger('click');
    await flushPromises();

    await wrapper.get('[data-testid="card-not-helpful"]').trigger('click');
    await flushPromises();

    expect(getStream().start).toHaveBeenLastCalledWith(expect.objectContaining({
      event: 'AGENT_FEEDBACK',
      eventData: { message: '@card:card-reading-1 这张“题目导读”没有解决我的问题，请换一种方式解释。' },
    }));
  });

  it('does not interpret unassessed KC defaults as learner ability in the welcome', async () => {
    const wrapper = mountPanel();
    const learning = useLearningStore();
    learning.field.hydrated = true;
    learning.field.masterySnapshot = [
      {
        kcCode: 'PY43-KC-016', masteryScore: 0.5, confidence: 0,
        lastEventAt: null, latestSource: 'bootstrap_default', assessed: false,
      },
    ];
    await nextTick();

    const welcome = wrapper.get('[data-testid="tutor-welcome-evidence"]').text();
    expect(welcome).toContain('暂无已验证的独立练习记录');
    expect(welcome).not.toContain('50%');
  });

  it('personalizes the welcome with the lowest verified KC mastery for this problem', async () => {
    const wrapper = mountPanel();
    const learning = useLearningStore();
    learning.field.hydrated = true;
    learning.field.masterySnapshot = [
      {
        kcCode: 'PY43-KC-016', masteryScore: 0.24, confidence: 0.8,
        lastEventAt: null, latestSource: 'verified_practice', assessed: true,
      },
    ];
    await nextTick();

    const welcome = wrapper.get('[data-testid="tutor-welcome-evidence"]').text();
    expect(welcome).toContain('程序运行原理');
    expect(welcome).toContain('24%');
  });

  it('keeps learner ability unknown before mastery data has loaded', () => {
    const wrapper = mountPanel();

    expect(wrapper.get('[data-testid="tutor-welcome-evidence"]').text())
      .toContain('学情数据尚未载入');
  });

  it('shows a generated episode plan and sends the server phase when starting it', async () => {
    const plan = {
      plan_id: 'ep-wf-test',
      session_id: 'wf-test',
      problem_id: 476,
      user_id: 7,
      objective: '先读懂题目，再独立完成练习。',
      trigger_event: 'READING',
      trigger_verdict: '',
      revision: 0,
      current_step_id: 's1',
      steps: [{
        step_id: 's1', event: 'READING', tool_name: 'read_problem', title: '先把题读透',
        goal: '明确输入输出。', student_visible_reason: '先一起看清题目。',
        stop_condition: '能复述题意。', status: 'ACTIVE', depends_on: [],
        expected_evidence: 'CHECKPOINT_COMPLETED',
      }, {
        step_id: 's2', event: 'IDEATING', tool_name: 'ideate_solution', title: '理一下思路',
        goal: '拆分解题步骤。', student_visible_reason: '再理顺思路。',
        stop_condition: '说出解题步骤。', status: 'PENDING', depends_on: ['s1'],
        expected_evidence: 'CHECKPOINT_COMPLETED',
      }],
      status: 'RECOMMENDED',
      stop_policy: 'WAIT_FOR_CLICK',
      revision_reason: '',
      created_by: 'coach',
      evidence_summary: 'verdict=NONE',
    } as const;
    vi.mocked(getActiveWorkflowSession).mockResolvedValueOnce({
      sessionKey: 'wf-test', problemId: 476, phase: 'READING', seq: 0, cards: [], episodePlan: plan,
    });
    const stream = getStream();
    stream.start.mockImplementationOnce(async () => {
      stream.result.value = {
        session_id: 'wf-test', event: 'PLAN_START', phase: 'READING', card_key: null,
        card: null, available_actions: [], episode_plan: { ...plan, status: 'ACTIVE', revision: 1 }, seq: 0,
      };
      stream.status.value = 'done';
    });
    const wrapper = mountPanel();
    await flushPromises();
    expect(wrapper.get('[data-testid="tutor-episode-plan"]').text()).toContain('先把题读透');

    await wrapper.get('[data-testid="plan-start"]').trigger('click');
    await flushPromises();

    expect(stream.start).toHaveBeenCalledWith(expect.objectContaining({
      problemId: 476,
      event: 'PLAN_START',
      requestId: expect.any(String),
      eventData: {
        plan_id: 'ep-wf-test', revision: 0, reason: '学生开始已推荐的学习计划',
        trigger_source: 'student', current_phase: 'READING',
      },
    }));
  });

  it('sends an attached image with the CHAT event', async () => {
    const wrapper = mountPanel();
    await flushPromises();
    const file = new File([new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10])], 'question.png', { type: 'image/png' });
    const input = wrapper.get('[data-testid="tw-chat-image-input"]');
    Object.defineProperty(input.element, 'files', { value: [file], configurable: true });
    await input.trigger('change');
    await flushPromises();
    expect(wrapper.find('[data-testid="tw-chat-images"]').exists()).toBe(true);
    await wrapper.get('.tw-chat__input').setValue('请看这张图');
    expect(wrapper.get('.tw-chat__send').attributes('disabled')).toBeUndefined();
    await wrapper.get('.tw-chat').trigger('submit');
    await flushPromises();

    expect(uploadTutorWorkflowImage).toHaveBeenCalledWith('wf-test', file);
    expect(getStream().start).toHaveBeenCalledWith(expect.objectContaining({
      event: 'CHAT',
      eventData: expect.objectContaining({
        message: '请看这张图',
        attachment_ids: ['d0162eb8-8c60-4d85-a3da-3aa3f11460ba'],
      }),
    }));
    expect(getStream().start).toHaveBeenCalledWith(expect.objectContaining({
      eventData: expect.not.objectContaining({ images: expect.anything() }),
    }));
  });

  it('sends the current editor code with the CHAT event', async () => {
    const wrapper = mountPanel();
    useLearningStore().setTrainingCode('print("hello")');
    await flushPromises();

    await wrapper.get('.tw-chat__input').setValue('请解释当前代码');
    await wrapper.get('.tw-chat').trigger('submit');
    await flushPromises();

    expect(getStream().start).toHaveBeenCalledWith(expect.objectContaining({
      event: 'CHAT',
      eventData: expect.objectContaining({
        message: '请解释当前代码',
        code: 'print("hello")',
      }),
    }));
  });

  it('keeps oversized editor code out of tutor requests', async () => {
    const wrapper = mountPanel();
    useLearningStore().setTrainingCode('x'.repeat(20_001));
    await flushPromises();

    await wrapper.get('.tw-chat__input').setValue('解释当前代码');
    await wrapper.get('.tw-chat').trigger('submit');
    await flushPromises();

    expect(getStream().start).not.toHaveBeenCalled();
  });

  it('keeps the text and image draft when attachment upload fails', async () => {
    vi.mocked(uploadTutorWorkflowImage).mockRejectedValueOnce(new Error('upload failed'));
    const wrapper = mountPanel();
    await flushPromises();
    const file = new File([new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10])], 'question.png', { type: 'image/png' });
    const input = wrapper.get('[data-testid="tw-chat-image-input"]');
    Object.defineProperty(input.element, 'files', { value: [file], configurable: true });
    await input.trigger('change');
    await flushPromises();
    await wrapper.get('.tw-chat__input').setValue('请看这张图');
    await wrapper.get('.tw-chat').trigger('submit');
    await flushPromises();

    expect((wrapper.get('.tw-chat__input').element as HTMLTextAreaElement).value).toBe('请看这张图');
    expect(wrapper.find('[data-testid="tw-chat-images"]').exists()).toBe(true);
    expect(getStream().start).not.toHaveBeenCalled();
  });

  it('reuses successful image uploads when a sibling attachment needs retry', async () => {
    vi.mocked(uploadTutorWorkflowImage)
      .mockResolvedValueOnce({ attachmentId: 'attachment-1' })
      .mockRejectedValueOnce(new Error('temporary upload failure'))
      .mockResolvedValueOnce({ attachmentId: 'attachment-2' });
    const wrapper = mountPanel();
    await flushPromises();
    const first = new File([new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10])], 'one.png', { type: 'image/png' });
    const second = new File([new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10])], 'two.png', { type: 'image/png' });
    const input = wrapper.get('[data-testid="tw-chat-image-input"]');
    Object.defineProperty(input.element, 'files', { value: [first, second], configurable: true });
    await input.trigger('change');
    await flushPromises();
    await wrapper.get('.tw-chat__input').setValue('请对照两张图');

    await wrapper.get('.tw-chat').trigger('submit');
    await flushPromises();
    expect(getStream().start).not.toHaveBeenCalled();

    await wrapper.get('.tw-chat').trigger('submit');
    await flushPromises();

    expect(uploadTutorWorkflowImage).toHaveBeenCalledTimes(3);
    expect(uploadTutorWorkflowImage).toHaveBeenNthCalledWith(1, 'wf-test', first);
    expect(uploadTutorWorkflowImage).toHaveBeenNthCalledWith(2, 'wf-test', second);
    expect(uploadTutorWorkflowImage).toHaveBeenNthCalledWith(3, 'wf-test', second);
    expect(getStream().start).toHaveBeenCalledWith(expect.objectContaining({
      eventData: expect.objectContaining({ attachment_ids: ['attachment-1', 'attachment-2'] }),
    }));
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
    await wrapper.get('.tw-think__toggle:not(.tw-think__stop)').trigger('click');
    await nextTick();
    expect(progress.text()).toContain('正在判断你卡在哪里');
    expect(progress.text()).toContain('正在选择下一步提示');
    expect(progress.text()).toContain('正在生成导学卡');
    expect(progress.text()).toContain('正在检查是否过度提示');
    expect(progress.text()).not.toContain('coach.decide');
  });
});
