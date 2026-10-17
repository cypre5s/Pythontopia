import { flushPromises, shallowMount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import TutorWorkflowPanel from '../TutorWorkflowPanel.vue';

const {
  streamStart,
  streamAbort,
  getActiveWorkflowSession,
  getEarlierWorkflowCards,
  submitToServer,
  toastShow,
  streamConfig,
} = vi.hoisted(() => ({
  streamStart: vi.fn(),
  streamAbort: vi.fn(),
  getActiveWorkflowSession: vi.fn(),
  getEarlierWorkflowCards: vi.fn(),
  submitToServer: vi.fn(),
  toastShow: vi.fn(),
  streamConfig: {
    completeRequest: false,
    holdRequest: false,
    resolveRequest: null as (() => void) | null,
    result: null as unknown,
  },
}));

vi.mock('@/composables/useSseStream', async () => {
  const { ref } = await import('vue');
  return {
    useSseStream: () => {
      const status = ref('idle');
      const result = ref<unknown>(null);
      return {
        status,
        result,
        error: ref(null),
        traces: ref([]),
        start: async (...args: unknown[]) => {
        streamStart(...args);
        if (streamConfig.holdRequest) {
          status.value = 'streaming';
          await new Promise<void>((resolve) => {
            streamConfig.resolveRequest = resolve;
          });
        }
        if (streamConfig.completeRequest) {
          status.value = 'done';
          result.value = streamConfig.result ?? {
            session_id: 'session-42', phase: 'READING', episode_plan: null,
            card: null, card_key: null, seq: 0, available_actions: [],
          };
        }
        },
        abort: () => {
          streamAbort();
          status.value = 'done';
          streamConfig.resolveRequest?.();
          streamConfig.resolveRequest = null;
        },
      };
    },
  };
});
vi.mock('@/api/tutorWorkflow', () => ({
  getActiveWorkflowSession,
  getEarlierWorkflowCards,
  recordTutorWorkflowFeedback: vi.fn(),
  uploadTutorWorkflowImage: vi.fn(),
}));
vi.mock('@stores/learning', () => ({
  useLearningStore: () => ({
    field: { hydrated: false },
    training: { code: 'print(4)', problem: null, submitPhase: 'idle' },
    submitToServer,
  }),
}));
vi.mock('@stores/toast', () => ({ useToastStore: () => ({ show: toastShow }) }));
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock('@/services/codeTheater/trace', () => ({ runTrace: vi.fn() }));

function savePendingRequest(overrides: Record<string, unknown> = {}): string {
  const raw = JSON.stringify({
    version: 1,
    problemId: 42,
    sessionKey: 'session-42',
    savedAt: Date.now(),
    request: {
      event: 'CHAT',
      requestId: '2c95c24f-76d7-4fa0-bf6e-d0ff45731f64',
      eventData: { message: '请解释这道题' },
    },
    ...overrides,
  });
  sessionStorage.setItem('pyt:tutor-workflow:pending:42', raw);
  return raw;
}

describe('TutorWorkflowPanel interrupted request recovery', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.clearAllMocks();
    streamConfig.completeRequest = false;
    streamConfig.holdRequest = false;
    streamConfig.resolveRequest = null;
    streamConfig.result = null;
    getActiveWorkflowSession.mockResolvedValue({
      sessionKey: 'session-42',
      problemId: 42,
      phase: 'READING',
      seq: 0,
      cards: [],
      episodePlan: null,
    });
    getEarlierWorkflowCards.mockResolvedValue({ cards: [], nextCursor: null });
  });

  it('offers explicit recovery after refresh and reuses the original request ID', async () => {
    savePendingRequest();

    const wrapper = shallowMount(TutorWorkflowPanel, {
      props: { problemId: 42, kcCodes: [] },
      global: { stubs: { RouterLink: true } },
    });
    await flushPromises();

    expect(wrapper.get('[data-testid="tutor-workflow-retry"]').text()).toContain('继续上次请求');
    await wrapper.get('[data-testid="tutor-workflow-retry"]').trigger('click');
    await flushPromises();

    expect(streamStart).toHaveBeenCalledWith({
      problemId: 42,
      event: 'CHAT',
      requestId: '2c95c24f-76d7-4fa0-bf6e-d0ff45731f64',
      eventData: { message: '请解释这道题' },
    });
  });

  it('loads older cards before the current history without duplicating or dropping cards', async () => {
    getActiveWorkflowSession.mockResolvedValueOnce({
      sessionKey: 'session-42',
      problemId: 42,
      phase: 'READING',
      seq: 2,
      cards: [
        {
          seq: 2,
          event: 'CHAT',
          cardKey: 'ai_reply',
          payload: {
            card_id: 'latest-card',
            content: '新回答',
            user_message: '较新的提问',
            referenced_card_ids: [],
          },
          createdAt: '2026-10-12T12:00:02Z',
        },
      ],
      olderCardsCursor: { seq: 1, id: 41 },
      episodePlan: null,
    });
    getEarlierWorkflowCards.mockResolvedValueOnce({
      cards: [
        {
          seq: 1,
          event: 'CHAT',
          cardKey: 'ai_reply',
          payload: {
            card_id: 'older-card',
            content: '旧回答',
            user_message: '较早的提问',
            referenced_card_ids: [],
          },
          createdAt: '2026-10-12T12:00:01Z',
        },
      ],
      nextCursor: null,
    });
    const wrapper = shallowMount(TutorWorkflowPanel, {
      props: { problemId: 42, kcCodes: [] },
      global: { stubs: { RouterLink: true } },
    });
    await flushPromises();

    const loadOlderButton = wrapper.get('[data-testid="tutor-workflow-load-older"]');
    expect(loadOlderButton.text()).toContain('更早');
    await loadOlderButton.trigger('click');
    await flushPromises();

    expect(getEarlierWorkflowCards).toHaveBeenCalledWith('session-42', { seq: 1, id: 41 });
    expect(wrapper.findAll('.tw-card__title')).toHaveLength(2);
    const history = wrapper.findAll('.tw-card__user').map((node) => node.text());
    expect(history).toEqual(['🙋 较早的提问', '🙋 较新的提问']);
    expect(wrapper.find('[data-testid="tutor-workflow-load-older"]').exists()).toBe(false);
  });

  it('clears a saved request when the active Tutor session no longer matches', async () => {
    savePendingRequest();
    getActiveWorkflowSession.mockResolvedValueOnce({
      sessionKey: 'different-user-session',
      problemId: 42,
      phase: 'READING',
      seq: 0,
      cards: [],
      episodePlan: null,
    });

    const wrapper = shallowMount(TutorWorkflowPanel, {
      props: { problemId: 42, kcCodes: [] },
      global: { stubs: { RouterLink: true } },
    });
    await flushPromises();

    expect(wrapper.find('[data-testid="tutor-workflow-retry"]').exists()).toBe(false);
    expect(sessionStorage.getItem('pyt:tutor-workflow:pending:42')).toBeNull();
  });

  it('clears a saved request after its short recovery window', async () => {
    savePendingRequest({ savedAt: Date.now() - 25 * 60 * 1000 - 1 });

    const wrapper = shallowMount(TutorWorkflowPanel, {
      props: { problemId: 42, kcCodes: [] },
      global: { stubs: { RouterLink: true } },
    });
    await flushPromises();

    expect(wrapper.find('[data-testid="tutor-workflow-retry"]').exists()).toBe(false);
    expect(sessionStorage.getItem('pyt:tutor-workflow:pending:42')).toBeNull();
  });

  it('lets the student discard a saved request without replaying it', async () => {
    savePendingRequest();
    const wrapper = shallowMount(TutorWorkflowPanel, {
      props: { problemId: 42, kcCodes: [] },
      global: { stubs: { RouterLink: true } },
    });
    await flushPromises();

    await wrapper.get('[data-testid="tutor-workflow-discard"]').trigger('click');

    expect(sessionStorage.getItem('pyt:tutor-workflow:pending:42')).toBeNull();
    expect(wrapper.find('[data-testid="tutor-workflow-retry"]').exists()).toBe(false);
    expect(streamStart).not.toHaveBeenCalled();
  });

  it('stores a new request before streaming so a disconnect can be resumed', async () => {
    const wrapper = shallowMount(TutorWorkflowPanel, {
      props: { problemId: 42, kcCodes: [] },
      global: { stubs: { RouterLink: true } },
    });
    await flushPromises();
    await wrapper.get('[data-testid="plan-recommend"]').trigger('click');
    await flushPromises();

    const stored = JSON.parse(sessionStorage.getItem('pyt:tutor-workflow:pending:42') ?? 'null');
    const sent = vi.mocked(streamStart).mock.calls[0]?.[0] as Record<string, unknown>;
    expect(stored.request).toEqual({
      event: 'PLAN_RECOMMEND', requestId: sent.requestId,
    });
    expect(stored.sessionKey).toBe('session-42');
  });

  it('requires formal submission for an active transfer step', async () => {
    getActiveWorkflowSession.mockResolvedValueOnce({
      sessionKey: 'session-42',
      problemId: 42,
      phase: 'AC_REVIEW',
      seq: 0,
      cards: [],
      episodePlan: {
        plan_id: 'plan-1',
        status: 'ACTIVE',
        objective: '巩固知识点',
        current_step_id: 'transfer-1',
        revision: 1,
        steps: [{
          step_id: 'transfer-1',
          event: 'TRANSFER',
          title: '迁移练习',
          goal: '练习变式题',
          student_visible_reason: '迁移练习',
          stop_condition: '说出准备如何尝试',
          status: 'ACTIVE',
          depends_on: [],
          expected_evidence: 'TRANSFER_ATTEMPT',
        }],
      },
    });
    const wrapper = shallowMount(TutorWorkflowPanel, {
      props: { problemId: 42, kcCodes: [] },
      global: { stubs: { RouterLink: true } },
    });
    await flushPromises();

    expect(wrapper.find('.tw-plan__response-label').exists()).toBe(false);
    expect(wrapper.find('.tw-plan__stop').text()).toContain('正式提交后自动推进');
  });

  it('lets the student stop a running request and keeps it available to resume', async () => {
    streamConfig.holdRequest = true;
    const wrapper = shallowMount(TutorWorkflowPanel, {
      props: { problemId: 42, kcCodes: [] },
      global: { stubs: { RouterLink: true } },
    });
    await flushPromises();

    await wrapper.get('[data-testid="plan-recommend"]').trigger('click');
    await flushPromises();
    expect(wrapper.find('[data-testid="tutor-workflow-stop"]').exists()).toBe(true);

    await wrapper.get('[data-testid="tutor-workflow-stop"]').trigger('click');
    await flushPromises();

    expect(streamAbort).toHaveBeenCalledOnce();
    expect(wrapper.find('[data-testid="tutor-workflow-retry"]').exists()).toBe(true);
    expect(sessionStorage.getItem('pyt:tutor-workflow:pending:42')).not.toBeNull();
  });

  it('clears the saved request after a successful result', async () => {
    streamConfig.completeRequest = true;
    const wrapper = shallowMount(TutorWorkflowPanel, {
      props: { problemId: 42, kcCodes: [] },
      global: { stubs: { RouterLink: true } },
    });
    await flushPromises();
    await wrapper.get('[data-testid="plan-recommend"]').trigger('click');
    await flushPromises();

    expect(sessionStorage.getItem('pyt:tutor-workflow:pending:42')).toBeNull();
    expect(wrapper.find('[data-testid="tutor-workflow-retry"]').exists()).toBe(false);
  });

  it('does not append a duplicate card when a completed request is replayed after refresh', async () => {
    const card = {
      card_id: 'reply-1', content: '已完成的回复', referenced_card_ids: [],
      user_message: '请解释这道题',
    };
    const snapshot = {
      sessionKey: 'session-42',
      problemId: 42,
      phase: 'READING',
      seq: 1,
      cards: [{ cardKey: 'ai_reply', payload: card, seq: 1, createdAt: '2026-10-15T00:00:00Z' }],
      episodePlan: null,
    };
    getActiveWorkflowSession.mockResolvedValueOnce(snapshot);
    streamConfig.completeRequest = true;
    streamConfig.result = {
      session_id: 'session-42', phase: 'READING', episode_plan: null,
      card, card_key: 'ai_reply', seq: 1, available_actions: [],
    };
    savePendingRequest();

    const wrapper = shallowMount(TutorWorkflowPanel, {
      props: { problemId: 42, kcCodes: [] },
      global: { stubs: { RouterLink: true } },
    });
    await flushPromises();
    await wrapper.get('[data-testid="tutor-workflow-retry"]').trigger('click');
    await flushPromises();

    expect(wrapper.findAll('.tw-card')).toHaveLength(1);
    expect(wrapper.findAll('.tw-card__user')).toHaveLength(1);
    expect(wrapper.get('.tw-card__user').text()).toContain('请解释这道题');
  });
});

describe('Tutor transfer formal submission', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.clearAllMocks();
    getActiveWorkflowSession.mockResolvedValue({
      sessionKey: 'wf-7-42-cafe',
      problemId: 42,
      phase: 'TRANSFER',
      seq: 3,
      cards: [{
        seq: 3,
        event: 'TRANSFER',
        cardKey: 'transfer_problem',
        payload: {
          problem_id: 500,
          kc_code: 'PY43-KC-016',
          title: '审核变式题',
          description: '读取边长并输出正方形面积',
          input_description: '一个浮点数',
          output_description: '面积保留 2 位',
          samples: [{ input: '2', output: '4.00' }],
          hint: '先看输入输出关系',
          target_kcs: ['PY43-KC-016'],
        },
      }],
      episodePlan: null,
    });
    getEarlierWorkflowCards.mockResolvedValue({ cards: [], nextCursor: null });
  });

  it('submits the server-bound transfer problem through the formal judge', async () => {
    const wrapper = shallowMount(TutorWorkflowPanel, {
      props: { problemId: 42, kcCodes: ['PY43-KC-016'] },
      global: { stubs: { RouterLink: true } },
    });
    await flushPromises();

    const submitButton = wrapper.find('[data-testid="submit-transfer"]');
    expect(submitButton.exists()).toBe(true);
    await submitButton.trigger('click');
    await flushPromises();

    expect(submitToServer).toHaveBeenCalledWith({
      problemId: 500,
      code: 'print(4)',
      source: 'TRAINING',
      tutorWorkflowSessionKey: 'wf-7-42-cafe',
    });
  });

  it('refreshes the workflow plan after a formally judged transfer submission', async () => {
    const wrapper = shallowMount(TutorWorkflowPanel, {
      props: { problemId: 42, kcCodes: ['PY43-KC-016'] },
      global: { stubs: { RouterLink: true } },
    });
    await flushPromises();
    const initialFetches = getActiveWorkflowSession.mock.calls.length;
    getActiveWorkflowSession.mockResolvedValueOnce({
      sessionKey: 'session-42',
      problemId: 42,
      phase: 'TRANSFER',
      seq: 4,
      cards: [],
      episodePlan: {
        plan_id: 'plan-1',
        session_id: 'session-42',
        problem_id: 42,
        user_id: 7,
        objective: '完成迁移题',
        trigger_event: 'TRANSFER',
        trigger_verdict: 'AC',
        revision: 2,
        current_step_id: '',
        steps: [],
        status: 'COMPLETED',
        stop_policy: 'DONE',
        revision_reason: '',
        created_by: 'coach',
        evidence_summary: '正式判题通过',
      },
    });
    submitToServer.mockResolvedValue({ judgeResult: { verdict: 'AC' } });

    await wrapper.get('[data-testid="submit-transfer"]').trigger('click');
    await flushPromises();

    expect(getActiveWorkflowSession).toHaveBeenCalledTimes(initialFetches + 1);
    expect(wrapper.get('[data-testid="tutor-episode-plan"]').text()).toContain('已完成');
  });
});
