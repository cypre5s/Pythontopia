/**
 * R-13 §2.3-6：TeacherReviewPage 单元测试。
 *
 * 覆盖：
 *   - 渲染基本结构 + role 警告兜底；
 *   - 拉题后写入审核队列 + counts 准确；
 *   - approve / reject 写 localStorage；
 *   - 角色不足时按钮禁用但页面仍可看。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { useSessionStore } from '@stores/session';
import TeacherReviewPage from '@/pages/teacher/TeacherReviewPage.vue';
import type { BossProblemDto } from '@/api/dto/Boss';

const runtimeMocks = vi.hoisted(() => ({
  fetchAiRuntimeOperations: vi.fn(),
}));

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock('@/app/AppLayout.vue', () => ({
  default: {
    name: 'AppLayout',
    template: '<div><slot /></div>',
  },
}));

vi.mock('@/api/boss', () => ({
  requestBossProblem: vi.fn(),
}));

vi.mock('@/api/aiRuntime', () => ({
  fetchAiRuntimeOperations: runtimeMocks.fetchAiRuntimeOperations,
}));

vi.mock('@stores/toast', () => ({
  useToastStore: () => ({
    show: vi.fn(),
    push: vi.fn(),
  }),
}));

import { requestBossProblem } from '@/api/boss';

const sampleProblem = (overrides: Partial<BossProblemDto> = {}): BossProblemDto => ({
  problemId: 'ai-c1-s1-py43_kc_001',
  chapter: 1,
  stage: 1,
  difficulty: 2,
  kcCode: 'PY43-KC-001',
  statement: '读入两个整数 a b，输出 a+b。' + '内容'.repeat(20),
  hints: ['提示一', '提示二'],
  sampleInput: '2 3',
  sampleOutput: '5',
  totalTestCases: 3,
  reviewed: true,
  aiGenerated: true,
  ...overrides,
});

describe('TeacherReviewPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    if (typeof window !== 'undefined') {
      window.localStorage.clear();
    }
    vi.mocked(requestBossProblem).mockReset();
    runtimeMocks.fetchAiRuntimeOperations.mockReset();
    runtimeMocks.fetchAiRuntimeOperations.mockResolvedValue({
      subject: {
        scope: 'PLATFORM',
        label: '全平台学习证据',
        learnerCount: 6,
        evidenceLearnerCount: 4,
      },
      loops: [
        {
          loopType: 'IMPROVE',
          label: '系统演化闭环',
          status: 'PASS',
          latestTraceId: 'trace-improve',
          nextAction: 'complete:system_evolution_loop',
          evidenceRefs: ['agentic-m3-system-evolution-v1'],
        },
      ],
      governance: [
        {
          key: 'policy_canary_review',
          label: '策略灰度审核',
          status: 'PASS',
          action: '查看 canary dry-run 与 rollback 状态',
          reviewRoute: '/teacher/review',
          evidenceRefs: ['agentic-m3-system-evolution-v1'],
        },
      ],
    });
  });

  afterEach(() => {
    if (typeof window !== 'undefined') {
      window.localStorage.clear();
    }
  });

  it('renders page header + empty queue message when no entries', () => {
    const wrapper = mount(TeacherReviewPage);
    expect(wrapper.find('[data-testid="teacher-review-page"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('教师审核');
    expect(wrapper.text()).toContain('队列为空');
  });

  it('renders API-backed runtime operations for teacher governance', async () => {
    const session = useSessionStore();
    session.user = { id: 2, username: 'teach', displayName: '老师', role: 'TEACHER' };
    const wrapper = mount(TeacherReviewPage);
    await flushPromises();

    expect(runtimeMocks.fetchAiRuntimeOperations).toHaveBeenCalledTimes(1);
    expect(wrapper.find('[data-testid="runtime-operations"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('全平台学习证据');
    expect(wrapper.text()).toContain('系统演化闭环');
    expect(wrapper.text()).toContain('策略灰度审核');
  });

  it('shows role warning when current user is STUDENT', async () => {
    const session = useSessionStore();
    session.user = { id: 1, username: 'qa', displayName: 'QA', role: 'STUDENT' };
    const wrapper = mount(TeacherReviewPage);
    await flushPromises();
    expect(wrapper.find('[data-testid="teacher-review-role-warning"]').exists()).toBe(true);
  });

  it('hides role warning when current user is TEACHER', async () => {
    const session = useSessionStore();
    session.user = { id: 2, username: 'teach', displayName: '老师', role: 'TEACHER' };
    const wrapper = mount(TeacherReviewPage);
    await flushPromises();
    expect(wrapper.find('[data-testid="teacher-review-role-warning"]').exists()).toBe(false);
  });

  it('fetches a problem and adds to queue', async () => {
    vi.mocked(requestBossProblem).mockResolvedValue(sampleProblem());
    const session = useSessionStore();
    session.user = { id: 2, username: 'teach', displayName: '老师', role: 'TEACHER' };
    const wrapper = mount(TeacherReviewPage);

    await wrapper.find('[data-testid="teacher-review-fetch"]').trigger('click');
    await flushPromises();

    expect(requestBossProblem).toHaveBeenCalledWith(1, 1);
    expect(wrapper.findAll('.list-item')).toHaveLength(1);
    expect(wrapper.find('[data-testid="teacher-review-counts"]').text()).toContain('总 1');
    expect(wrapper.find('[data-testid="teacher-review-counts"]').text()).toContain('待审 1');
  });

  it('approves selected entry and persists to localStorage', async () => {
    vi.mocked(requestBossProblem).mockResolvedValue(sampleProblem());
    const session = useSessionStore();
    session.user = { id: 2, username: 'teach', displayName: '老师', role: 'TEACHER' };
    const wrapper = mount(TeacherReviewPage);

    await wrapper.find('[data-testid="teacher-review-fetch"]').trigger('click');
    await flushPromises();

    await wrapper.find('[data-testid="teacher-review-approve"]').trigger('click');
    await flushPromises();

    expect(wrapper.find('[data-testid="teacher-review-counts"]').text()).toContain('通过 1');
    const stored = JSON.parse(window.localStorage.getItem('pyt_teacher_review_decisions_v1') ?? '[]');
    expect(stored).toHaveLength(1);
    expect(stored[0].decision).toBe('approved');
  });

  it('rejects with reason and writes to localStorage', async () => {
    vi.mocked(requestBossProblem).mockResolvedValue(sampleProblem());
    const session = useSessionStore();
    session.user = { id: 2, username: 'teach', displayName: '老师', role: 'TEACHER' };
    const wrapper = mount(TeacherReviewPage);

    await wrapper.find('[data-testid="teacher-review-fetch"]').trigger('click');
    await flushPromises();

    const reasonInput = wrapper.find('[data-testid="teacher-review-reject-reason"]');
    await reasonInput.setValue('题目超纲，章 1 不应有 for 循环');

    await wrapper.find('[data-testid="teacher-review-reject"]').trigger('click');
    await flushPromises();

    expect(wrapper.find('[data-testid="teacher-review-counts"]').text()).toContain('驳回 1');
    const stored = JSON.parse(window.localStorage.getItem('pyt_teacher_review_decisions_v1') ?? '[]');
    expect(stored[0].decision).toBe('rejected');
    expect(stored[0].reason).toContain('超纲');
  });

  it('approve / reject buttons disabled for STUDENT', async () => {
    vi.mocked(requestBossProblem).mockResolvedValue(sampleProblem());
    const session = useSessionStore();
    session.user = { id: 1, username: 'qa', displayName: 'QA', role: 'STUDENT' };
    const wrapper = mount(TeacherReviewPage);

    await wrapper.find('[data-testid="teacher-review-fetch"]').trigger('click');
    await flushPromises();

    const approveBtn = wrapper.find('[data-testid="teacher-review-approve"]');
    const rejectBtn = wrapper.find('[data-testid="teacher-review-reject"]');
    expect((approveBtn.element as HTMLButtonElement).disabled).toBe(true);
    expect((rejectBtn.element as HTMLButtonElement).disabled).toBe(true);
  });

  it('restores queue from localStorage on mount', async () => {
    const persisted = [
      {
        problem: sampleProblem({ problemId: 'persisted-1' }),
        decision: 'approved',
        fetchedAt: '2026-05-25T00:00:00.000Z',
        decidedAt: '2026-05-25T00:01:00.000Z',
      },
    ];
    window.localStorage.setItem('pyt_teacher_review_decisions_v1', JSON.stringify(persisted));

    const wrapper = mount(TeacherReviewPage);
    await flushPromises();
    expect(wrapper.findAll('.list-item')).toHaveLength(1);
    expect(wrapper.text()).toContain('PY43-KC-001');
    expect(wrapper.find('[data-testid="teacher-review-counts"]').text()).toContain('通过 1');
  });

  it('clear all empties the queue and storage', async () => {
    vi.mocked(requestBossProblem).mockResolvedValue(sampleProblem());
    const session = useSessionStore();
    session.user = { id: 2, username: 'teach', displayName: '老师', role: 'TEACHER' };
    const wrapper = mount(TeacherReviewPage);

    await wrapper.find('[data-testid="teacher-review-fetch"]').trigger('click');
    await flushPromises();

    await wrapper.find('[data-testid="teacher-review-clear"]').trigger('click');
    await flushPromises();

    expect(wrapper.findAll('.list-item')).toHaveLength(0);
    const stored = JSON.parse(window.localStorage.getItem('pyt_teacher_review_decisions_v1') ?? '[]');
    expect(stored).toHaveLength(0);
  });
});
