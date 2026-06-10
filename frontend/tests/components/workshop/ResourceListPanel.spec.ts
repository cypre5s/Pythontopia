import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ResourceListPanel from '@/components/workshop/ResourceListPanel.vue';
import { useLearningStore } from '@stores/learning';
import { useWorkshopUiStore } from '@stores/workshopUi';

const push = vi.fn();

vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
}));

vi.mock('@/api/workshop', () => ({
  listWorkshopResources: vi.fn(async () => []),
  createWorkshopResource: vi.fn(),
  getWorkshopResource: vi.fn(),
  submitWorkshopFeedback: vi.fn(),
}));

vi.mock('@/api/field', () => ({
  bootstrapCurriculum: vi.fn(),
  fetchMastery: vi.fn(async () => []),
}));

vi.mock('@/composables/useStardewFx', () => ({
  playUiClick: vi.fn(),
}));

describe('ResourceListPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    push.mockReset();
  });

  it('opens resource details through workshop UI store without writing id to URL', async () => {
    const learning = useLearningStore();
    const workshopUi = useWorkshopUiStore();
    learning.resources.list = [
      {
        id: 42,
        title: '变量讲义',
        type: 'HANDOUT',
        kcCodes: ['PY43-KC-010'],
        summary: 'summary',
        safetyStatus: 'PASSED',
        groundingStatus: 'PASSED',
        feedbackStatus: 'NONE',
        createdAt: '2026-06-01T00:00:00Z',
      },
    ];

    const wrapper = mount(ResourceListPanel);

    await wrapper.get('.review__btn').trigger('click');

    expect(workshopUi.openResourceId).toBe(42);
    expect(push).not.toHaveBeenCalledWith({ path: '/workshop', query: { id: '42' } });
  });
});
