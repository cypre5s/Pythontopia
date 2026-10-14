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
  loops: [],
  governance: [],
};

beforeEach(() => {
  vi.clearAllMocks();
  fetchAiRuntimeOperations.mockResolvedValue(operations);
});

describe('AdminOperationsPage', () => {
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
});
