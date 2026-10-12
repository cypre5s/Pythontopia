import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
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
      reviewRoute: '/admin/problem-reviews',
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

describe('AdminOperationsPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    api.fetchAiRuntimeOperations.mockResolvedValue(snapshot);
  });

  it('renders counts, loops and governance from the operations API', async () => {
    const wrapper = mount(AdminOperationsPage);
    await flushPromises();

    expect(api.fetchAiRuntimeOperations).toHaveBeenCalledOnce();
    expect(wrapper.findAll('.admin-stat-card strong').map((item) => item.text())).toEqual(['6', '4']);
    expect(wrapper.text()).toContain('67% 已有学习证据');
    expect(wrapper.text()).toContain('继续检查学习证据');
    expect(wrapper.text()).toContain('复核证据覆盖');
    expect(wrapper.text()).toContain('2 项证据');
    expect(wrapper.get('h1').text()).toBe('AI 运维概览');
    expect(wrapper.get('.status-pill--insufficient-evidence').text()).toBe('证据不足');
  });

  it('shows a real API error and retries the request', async () => {
    api.fetchAiRuntimeOperations
      .mockRejectedValueOnce(new Error('服务暂不可用'))
      .mockResolvedValueOnce(snapshot);
    const wrapper = mount(AdminOperationsPage);
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain('服务暂不可用');
    await wrapper.get('button').trigger('click');
    await flushPromises();

    expect(api.fetchAiRuntimeOperations).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('活跃学习者');
  });
});
