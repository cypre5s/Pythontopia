import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter } from 'vue-router';
import AdminOperationsPage from '@/pages/admin/AdminOperationsPage.vue';

const api = vi.hoisted(() => ({ fetchAiRuntimeOperations: vi.fn() }));

vi.mock('@/api/aiRuntime', () => ({
  fetchAiRuntimeOperations: api.fetchAiRuntimeOperations,
}));

const snapshot = {
  subject: {
    scope: 'PLATFORM' as const,
    label: '全平台学习证据',
    learnerCount: 6,
    evidenceLearnerCount: 4,
  },
  loops: [
    {
      loopType: 'LEARNER',
      label: '学习闭环',
      status: 'PASS' as const,
      latestTraceId: 'trace-123',
      nextAction: '继续检查学习证据',
      evidenceRefs: ['page:1', 'submission:2'],
    },
  ],
  governance: [
    {
      key: 'grounding',
      label: '课件依据检查',
      status: 'WARN' as const,
      action: '复核证据覆盖',
      reviewRoute: '/teacher/review',
      evidenceRefs: ['page:1'],
    },
    {
      key: 'evidence',
      label: '证据不足检查',
      status: 'INSUFFICIENT_EVIDENCE' as const,
      action: '补充真实证据',
      reviewRoute: '',
      evidenceRefs: [],
    },
  ],
};

function mountOperationsPage() {
  const component = { render: () => null };
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/workshop', name: 'workshop', component },
      { path: '/teacher/review', name: 'teacher-review', component },
      { path: '/admin/problem-reviews', name: 'admin-problem-reviews', component },
    ],
  });
  return mount(AdminOperationsPage, { global: { plugins: [router] } });
}

describe('AdminOperationsPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    api.fetchAiRuntimeOperations.mockResolvedValue(snapshot);
  });

  it('renders counts, loops and governance from the operations API', async () => {
    const wrapper = mountOperationsPage();
    await flushPromises();

    expect(api.fetchAiRuntimeOperations).toHaveBeenCalledOnce();
    expect(wrapper.findAll('.admin-stat-card strong').map((item) => item.text())).toEqual(['6', '4']);
    expect(wrapper.text()).toContain('67% 已有学习证据');
    expect(wrapper.text()).toContain('继续检查学习证据');
    expect(wrapper.text()).toContain('复核证据覆盖');
    expect(wrapper.text()).toContain('2 项证据');
    expect(wrapper.findAll('.admin-panel').length).toBe(3);
    expect(wrapper.get('.admin-operations > .admin-panel h2').text()).toBe('AI 运维概览');
    expect(wrapper.get('.admin-operations > .admin-panel .admin-button').text()).toBe('刷新数据');
    expect(wrapper.get('.status-pill--insufficient-evidence').text()).toBe('证据不足');
    expect(wrapper.get('.status-pill--insufficient-evidence').classes()).toContain('admin-status--muted');
    expect(wrapper.get('.status-pill--pass').classes()).toContain('admin-status--positive');
    expect(wrapper.get('a[href="/teacher/review"]').text()).toBe('/teacher/review');
  });

  it('shows a real API error and retries the request', async () => {
    api.fetchAiRuntimeOperations
      .mockRejectedValueOnce(new Error('服务暂不可用'))
      .mockResolvedValueOnce(snapshot);
    const wrapper = mountOperationsPage();
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain('服务暂不可用');
    await wrapper.get('button').trigger('click');
    await flushPromises();

    expect(api.fetchAiRuntimeOperations).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('活跃学习者');
  });

  it('hides an old snapshot when a refresh fails', async () => {
    api.fetchAiRuntimeOperations
      .mockResolvedValueOnce(snapshot)
      .mockRejectedValueOnce(new Error('刷新失败'));
    const wrapper = mountOperationsPage();
    await flushPromises();
    expect(wrapper.text()).toContain('活跃学习者');

    await wrapper.get('[data-testid="admin-operations-refresh"]').trigger('click');
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain('刷新失败');
    expect(wrapper.text()).not.toContain('活跃学习者');
  });

  it('keeps the full operations text available when list rows are truncated', async () => {
    const nextAction = '继续检查学习证据'.repeat(20);
    const action = '复核证据覆盖情况'.repeat(20);
    const reviewRoute = `/admin/${'review/'.repeat(20)}`;
    const traceId = `trace-${'1234567890'.repeat(8)}`;
    api.fetchAiRuntimeOperations.mockResolvedValueOnce({
      ...snapshot,
      loops: [{ ...snapshot.loops[0], nextAction, latestTraceId: traceId }],
      governance: [{ ...snapshot.governance[0], action, reviewRoute }],
    });

    const wrapper = mountOperationsPage();
    await flushPromises();

    const descriptions = wrapper.findAll('.operation-row__body span');
    const details = wrapper.findAll('.operation-row__body small');
    expect(descriptions[0].attributes('title')).toBe(nextAction);
    expect(descriptions[1].attributes('title')).toBe(action);
    expect(details[0].attributes('title')).toBe(`Trace ${traceId}`);
    expect(details[1].attributes('title')).toBe(`关联页面：${reviewRoute}`);
    expect(details[1].find('a').exists()).toBe(false);
  });
});
