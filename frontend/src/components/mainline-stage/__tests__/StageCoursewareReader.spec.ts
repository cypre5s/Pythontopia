import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { CoursePageDetail } from '@/api/dto/Library';
import StageCoursewareReader from '../StageCoursewareReader.vue';

const api = vi.hoisted(() => ({
  listDocumentPages: vi.fn(),
  getDocumentPage: vi.fn(),
  postPagesRead: vi.fn(),
}));

vi.mock('@/api/courseLibrary', () => ({
  listDocumentPages: api.listDocumentPages,
  getDocumentPage: api.getDocumentPage,
}));
vi.mock('@/api/mainline', () => ({ postPagesRead: api.postPagesRead }));

function coursePage(pageId = 71): CoursePageDetail {
  return {
    pageId,
    documentId: 186,
    chapterId: 1,
    pageNo: 2,
    chunkIndex: 0,
    title: '课件页',
    contentMarkdown: '内容',
    excerpt: '内容',
  };
}

function mountReader() {
  return mount(StageCoursewareReader, {
    props: {
      stageId: 9,
      chapter: 1,
      docId: 186,
      pageIds: [71],
      requiredPages: 1,
      readPageIds: [],
    },
    global: {
      stubs: {
        PdfPageView: { template: '<div data-testid="pdf-page-stub" />' },
        StageContextTutor: true,
      },
    },
  });
}

describe('StageCoursewareReader request feedback', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    api.listDocumentPages.mockResolvedValue([coursePage()]);
    api.getDocumentPage.mockResolvedValue(coursePage());
    api.postPagesRead.mockResolvedValue({});
  });

  it('shows loading while course-page mapping is pending, then opens the reader', async () => {
    let resolveRequest!: (pages: CoursePageDetail[]) => void;
    api.listDocumentPages.mockReturnValue(new Promise((resolve) => {
      resolveRequest = resolve;
    }));

    const wrapper = mountReader();
    await wrapper.vm.$nextTick();

    expect(wrapper.get('[role="status"]').text()).toContain('正在加载课件页码');
    expect(wrapper.find('[data-testid="stage-courseware-map-error"]').exists()).toBe(false);

    resolveRequest([coursePage()]);
    await flushPromises();

    expect(wrapper.find('[data-testid="stage-courseware-viewer"]').exists()).toBe(true);
  });

  it('shows a mapping error and retries successfully', async () => {
    api.listDocumentPages
      .mockRejectedValueOnce(new Error('页码服务暂不可用'))
      .mockResolvedValueOnce([coursePage()]);

    const wrapper = mountReader();
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain('页码服务暂不可用');
    expect(wrapper.get('[data-testid="stage-courseware-map-retry"]')).toBeTruthy();
    expect(wrapper.find('[data-testid="stage-courseware-viewer"]').exists()).toBe(false);

    await wrapper.get('[data-testid="stage-courseware-map-retry"]').trigger('click');
    await flushPromises();

    expect(api.listDocumentPages).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="stage-courseware-viewer"]').exists()).toBe(true);
  });

  it('keeps reading progress unchanged on failure and retries the real write', async () => {
    api.postPagesRead
      .mockRejectedValueOnce(new Error('进度暂时无法保存'))
      .mockResolvedValueOnce({});

    const wrapper = mountReader();
    await flushPromises();

    await wrapper.get('[data-testid="stage-courseware-mark-all"]').trigger('click');
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain('进度暂时无法保存');
    expect(wrapper.get('[data-testid="stage-courseware-count"]').text()).toContain('已读 0 / 1 页');
    expect(wrapper.emitted('progress')).toBeUndefined();
    expect(wrapper.get('[data-testid="stage-courseware-mark-all"]').text()).toContain('重试');

    await wrapper.get('[data-testid="stage-courseware-mark-all"]').trigger('click');
    await flushPromises();

    expect(api.postPagesRead).toHaveBeenCalledTimes(2);
    expect(wrapper.get('[data-testid="stage-courseware-passed"]').text()).toContain('已过线');
    expect(wrapper.emitted('progress')).toHaveLength(1);
  });
});
