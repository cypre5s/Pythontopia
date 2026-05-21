import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import WorkshopPanel from '@/components/workshop/WorkshopPanel.vue';
import { useLearningStore } from '@stores/learning';

const apiMocks = vi.hoisted(() => ({
  createWorkshopResource: vi.fn(),
  listWorkshopResources: vi.fn(),
  getWorkshopResource: vi.fn(),
  submitWorkshopFeedback: vi.fn(),
}));

vi.mock('@/api/workshop', () => apiMocks);

let router: Router;

function setupRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/workshop', component: { template: '<div data-testid="workshop-placeholder"/>' } },
    ],
  });
}

describe('WorkshopPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    router = setupRouter();
    apiMocks.createWorkshopResource.mockReset();
    apiMocks.listWorkshopResources.mockReset();
    apiMocks.createWorkshopResource.mockResolvedValue({
      runId: 42,
      resourceId: 7,
      status: 'SUCCESS',
    });
    apiMocks.listWorkshopResources.mockResolvedValue([]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('selects KC, picks type, submits and navigates to detail', async () => {
    const learning = useLearningStore();
    learning.field.crops = [
      {
        tileX: 2,
        tileY: 2,
        kcCode: 'PY43-KC-001',
        status: 'SEED',
        unlocked: true,
        masteryScore: 0,
        growthStage: 0,
      },
    ];

    await router.push('/workshop');
    const wrapper = mount(WorkshopPanel, {
      global: {
        plugins: [router],
      },
    });
    await flushPromises();

    const kcCheckbox = wrapper.find('input[type="checkbox"]');
    await kcCheckbox.setValue(true);
    const typeRadio = wrapper.find('[data-testid="type-PRACTICE"]');
    await typeRadio.setValue();

    await wrapper.find('button.primary').trigger('click');
    await flushPromises();

    expect(apiMocks.createWorkshopResource).toHaveBeenCalledWith({
      kcCodes: ['PY43-KC-001'],
      type: 'PRACTICE',
      focusHint: undefined,
    });
    expect(router.currentRoute.value.query.id).toBe('7');
  });

  it('shows empty hint when no KC is unlocked', async () => {
    const wrapper = mount(WorkshopPanel, {
      global: {
        plugins: [router],
      },
    });
    expect(wrapper.text()).toContain('请先去知识田');
  });
});
