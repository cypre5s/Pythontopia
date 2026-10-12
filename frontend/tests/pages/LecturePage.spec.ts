import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter } from 'vue-router';

const apiMocks = vi.hoisted(() => ({
  listChapterVideos: vi.fn(),
  listWorkshopResources: vi.fn(),
  getWorkshopResource: vi.fn(),
  consumeWorkshopResource: vi.fn(),
}));

vi.mock('@/api/courseVideo', () => ({
  listChapterVideos: apiMocks.listChapterVideos,
  videoStreamUrl: (id: number) => `/api/course/videos/${id}`,
}));

vi.mock('@/api/workshop', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/workshop')>();
  return {
    ...actual,
    listWorkshopResources: apiMocks.listWorkshopResources,
    getWorkshopResource: apiMocks.getWorkshopResource,
    consumeWorkshopResource: apiMocks.consumeWorkshopResource,
  };
});

import LecturePage from '@/pages/LecturePage.vue';

describe('LecturePage resource evidence', () => {
  afterEach(() => {
    document.body.innerHTML = '';
  });

  beforeEach(() => {
    setActivePinia(createPinia());
    apiMocks.listChapterVideos.mockReset().mockResolvedValue([]);
    apiMocks.listWorkshopResources.mockReset().mockResolvedValue([
      { id: 77, kcCodes: ['PY43-KC-001'] },
    ]);
    apiMocks.getWorkshopResource.mockReset().mockResolvedValue({
      id: 77,
      title: '第一章讲义',
      kcCodes: ['PY43-KC-001'],
      content: { sections: [] },
      evidencePageIds: [],
      groundingStatus: 'PASSED',
    });
    apiMocks.consumeWorkshopResource.mockReset().mockResolvedValue({
      resourceId: 77,
      kcCodes: ['PY43-KC-001'],
      readRecorded: true,
    });
  });

  it('records the owned handout consumption when opening its PPT', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/lecture/:chapter', component: LecturePage, props: { chapter: 1 } }],
    });
    await router.push('/lecture/1');
    await router.isReady();
    const wrapper = mount(LecturePage, {
      props: { chapter: 1 },
      global: {
        plugins: [router],
        stubs: {
          AppLayout: { template: '<div><slot /></div>' },
          BilibiliVideoCards: { template: '<div />' },
          PptView: { template: '<div data-testid="ppt-view" />' },
          TutorPanel: { template: '<div />' },
        },
      },
    });

    await flushPromises();
    await wrapper.get('[data-testid="lecture-goto-ppt"]').trigger('click');
    await flushPromises();

    expect(apiMocks.consumeWorkshopResource).toHaveBeenCalledWith(77);
    expect(document.body.querySelector('[data-testid="ppt-view"]')).not.toBeNull();
  });
});
