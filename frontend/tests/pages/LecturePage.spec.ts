import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter } from 'vue-router';
import type { CourseVideoDto } from '@/api/courseVideo';

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

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise;
  });
  return { promise, resolve };
}

async function mountLecturePage(chapter: number) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/lecture/:chapter', component: LecturePage }],
  });
  await router.push(`/lecture/${chapter}`);
  await router.isReady();
  return mount(LecturePage, {
    props: { chapter },
    global: {
      plugins: [router],
      stubs: {
        AppLayout: { template: '<div><slot /></div>' },
        BilibiliVideoCards: {
          emits: ['opened'],
          template: '<button data-testid="external-video-open" @click="$emit(\'opened\')" />',
        },
        PptView: { template: '<div data-testid="ppt-view" />' },
        TutorPanel: { template: '<div />' },
      },
    },
  });
}

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
    expect(wrapper.get('.lecture-done').text()).toContain('本次已观看');
  });

  it('labels chapter video completion as session-only instead of saved learning progress', async () => {
    apiMocks.listChapterVideos.mockResolvedValueOnce([{
      id: 11,
      chapter: 1,
      title: '第一章讲解',
      durationSec: 100,
      kcCodes: ['PY43-KC-001'],
      available: true,
    }]);
    const wrapper = await mountLecturePage(1);
    await flushPromises();

    const video = wrapper.get('[data-testid="lecture-video"]');
    Object.defineProperty(video.element, 'duration', { configurable: true, value: 100 });
    Object.defineProperty(video.element, 'currentTime', { configurable: true, value: 91 });
    await video.trigger('timeupdate');

    const status = wrapper.get('.lecture-done');
    expect(status.text()).toContain('本次已观看');
    expect(status.attributes('title')).toContain('不会保存到学习档案');
  });

  it('does not count opening an external video as viewing', async () => {
    const wrapper = await mountLecturePage(1);
    await flushPromises();

    await wrapper.get('[data-testid="external-video-open"]').trigger('click');

    expect(wrapper.find('.lecture-done').exists()).toBe(false);
  });

  it('does not show completed learning when handout consumption fails', async () => {
    apiMocks.consumeWorkshopResource.mockRejectedValueOnce(new Error('offline'));
    const wrapper = await mountLecturePage(1);
    await flushPromises();

    await wrapper.get('[data-testid="lecture-goto-ppt"]').trigger('click');
    await flushPromises();

    expect(document.body.querySelector('[data-testid="ppt-view"]')).not.toBeNull();
    expect(wrapper.find('.lecture-done').exists()).toBe(false);
  });

  it('keeps the new chapter videos when an older request finishes later', async () => {
    const firstRequest = deferred<CourseVideoDto[]>();
    const secondRequest = deferred<CourseVideoDto[]>();
    apiMocks.listChapterVideos
      .mockReturnValueOnce(firstRequest.promise)
      .mockReturnValueOnce(secondRequest.promise);
    const wrapper = await mountLecturePage(1);

    expect(apiMocks.listChapterVideos).toHaveBeenNthCalledWith(1, 1);
    await wrapper.setProps({ chapter: 2 });
    expect(apiMocks.listChapterVideos).toHaveBeenNthCalledWith(2, 2);

    secondRequest.resolve([{
      id: 22,
      chapter: 2,
      title: '第二章视频',
      durationSec: 90,
      kcCodes: [],
      available: true,
    }]);
    await flushPromises();
    expect(wrapper.get('[data-testid="lecture-video"]').attributes('src')).toBe('/api/course/videos/22');

    firstRequest.resolve([{
      id: 11,
      chapter: 1,
      title: '第一章视频',
      durationSec: 60,
      kcCodes: [],
      available: true,
    }]);
    await flushPromises();

    expect(wrapper.get('[data-testid="lecture-video"]').attributes('src')).toBe('/api/course/videos/22');
    expect(wrapper.get('.lecture-vtitle').text()).toBe('第二章视频');
  });

  it('shows a retryable error when loading videos fails', async () => {
    apiMocks.listChapterVideos
      .mockRejectedValueOnce(new Error('network unavailable'))
      .mockResolvedValueOnce([]);
    const wrapper = await mountLecturePage(1);

    await flushPromises();

    expect(wrapper.get('[data-testid="lecture-video-error"]').text()).toContain('加载失败');
    expect(wrapper.find('[data-testid="lecture-empty"]').exists()).toBe(false);

    await wrapper.get('[data-testid="lecture-video-retry"]').trigger('click');
    await flushPromises();

    expect(apiMocks.listChapterVideos).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[data-testid="lecture-video-error"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="lecture-empty"]').exists()).toBe(true);
  });
});
