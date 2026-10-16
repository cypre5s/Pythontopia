import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { AiRuntimeOperations } from '@/api/aiRuntime';
import AdminOperationsPage from '../AdminOperationsPage.vue';

const { fetchAiRuntimeOperations } = vi.hoisted(() => ({
  fetchAiRuntimeOperations: vi.fn(),
}));

vi.mock('@/api/aiRuntime', () => ({ fetchAiRuntimeOperations }));

const operations: AiRuntimeOperations = {
  subject: { scope: 'PLATFORM', label: '平台', learnerCount: 12, evidenceLearnerCount: 8 },
  loops: [{
    loopType: 'LEARNER',
    label: '学生学习闭环',
    status: 'PASS',
    latestTraceId: 'open:student-learning-loop',
    nextAction: '继续学习',
    evidenceRefs: ['submission:104', 'resource:208'],
  }],
  governance: [],
};

beforeEach(() => {
  vi.clearAllMocks();
  fetchAiRuntimeOperations.mockResolvedValue(operations);
});

describe('AdminOperationsPage', () => {
  it('labels the backend operation key without presenting it as a trace id', async () => {
    const wrapper = mount(AdminOperationsPage);
    await flushPromises();

    const operationKey = wrapper.get('.operation-row__body small');
    expect(operationKey.text()).toBe('关联入口：open:student-learning-loop');
    expect(operationKey.attributes('title')).toBe('关联入口：open:student-learning-loop');
  });

  it('makes evidence references expandable without relying on a hover tooltip', async () => {
    const wrapper = mount(AdminOperationsPage);
    await flushPromises();

    const evidence = wrapper.get('details.operation-row__evidence');
    expect(evidence.get('summary').text()).toBe('2 项证据');
    expect(evidence.text()).toContain('submission:104');
    expect(evidence.text()).toContain('resource:208');
    expect(evidence.attributes('title')).toBeUndefined();
  });

  it('provides a manual refresh action and disables it while loading', async () => {
    let resolveRefresh!: (value: AiRuntimeOperations) => void;
    fetchAiRuntimeOperations
      .mockResolvedValueOnce(operations)
      .mockReturnValueOnce(new Promise<AiRuntimeOperations>((resolve) => { resolveRefresh = resolve; }));
    const wrapper = mount(AdminOperationsPage);
    await flushPromises();

    const refresh = wrapper.get('[data-testid="admin-operations-refresh"]');
    await refresh.trigger('click');

    expect(fetchAiRuntimeOperations).toHaveBeenCalledTimes(2);
    expect(refresh.attributes('disabled')).toBeDefined();

    resolveRefresh(operations);
    await flushPromises();
    expect(wrapper.get('[data-testid="admin-operations-refresh"]').attributes('disabled')).toBeUndefined();
  });

  it('recovers from an initial load failure when the admin retries', async () => {
    fetchAiRuntimeOperations
      .mockRejectedValueOnce(new Error('运维数据暂不可用'))
      .mockResolvedValueOnce(operations);
    const wrapper = mount(AdminOperationsPage);
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain('读取失败：运维数据暂不可用');

    await wrapper.get('.admin-operations__error button').trigger('click');
    await flushPromises();

    expect(fetchAiRuntimeOperations).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(wrapper.get('.admin-operations__stats').text()).toContain('12');
  });
});
