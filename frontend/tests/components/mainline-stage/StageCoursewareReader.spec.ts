import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

// 「学」改造为站内整章 PPT(PDF)精读：只 mock 批量打点端点 postPagesRead。
vi.mock('@/api/mainline', () => ({
  postPagesRead: vi.fn(),
}));

// 锚定问答取当前页文本用 getDocumentPage；StageContextTutor 内部的 useSseStream 也 mock 掉，
// 避免触达 fixture/网络（问答交互细节在 StageContextTutor 自身单测覆盖，这里只验证已挂载并接线）。
vi.mock('@/api/courseLibrary', () => ({
  getDocumentPage: vi.fn(),
}));

// 用轻量 stub 顶替 pdfjs 渲染件，避免 jsdom 加载真实 PDF / worker（pdfjs 4.7 顶层
// import 依赖 Promise.withResolvers，jsdom 下会炸）。组件本体的翻页/计数/上报逻辑
// 不依赖 PDF 是否真正渲染，故 stub 出 docId/pageNo 即可。
vi.mock('@/components/library/PdfPageView.vue', () => ({
  default: {
    name: 'PdfPageView',
    props: {
      docId: { type: Number, required: true },
      pageNo: { type: Number, required: true },
    },
    template: '<div data-testid="pdf-stub">doc{{ docId }}-p{{ pageNo }}</div>',
  },
}));

import StageCoursewareReader from '@/components/mainline-stage/StageCoursewareReader.vue';
import { postPagesRead } from '@/api/mainline';
import type { StageProgressDto } from '@/api/mainline';
import { getDocumentPage } from '@/api/courseLibrary';

// 与 A3 真实 StageProgressDto 对齐（组件只 await 成功、不读返回体）。
function makeStageProgress(over: Partial<StageProgressDto> = {}): StageProgressDto {
  return {
    stageId: 7,
    chapter: 1,
    index: 1,
    title: '初识 Python',
    videoWatchedSec: 0,
    requiredVideoSec: 240,
    pagesRead: 0,
    requiredPages: 3,
    examplesDone: 0,
    requiredExamples: 0,
    completed: false,
    ...over,
  };
}

interface ReaderProps {
  stageId: number;
  chapter: number;
  docId: number;
  pageIds: number[];
  requiredPages: number;
  readPageIds: number[];
  kcCodes?: string[];
}

function mountReader(props: ReaderProps) {
  return mount(StageCoursewareReader, { props });
}

const BASE: ReaderProps = {
  stageId: 7,
  chapter: 1,
  docId: 186,
  pageIds: [101, 102, 103],
  requiredPages: 3,
  readPageIds: [],
};

describe('StageCoursewareReader（学 · 站内整章 PPT 精读）', () => {
  beforeEach(() => {
    vi.mocked(postPagesRead).mockResolvedValue(
      makeStageProgress({ pagesRead: 3, pagesReadIds: [101, 102, 103] }),
    );
    vi.mocked(getDocumentPage).mockResolvedValue(null);
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('初始计数为「已读 0 / requiredPages 页」，并嵌入站内 PDF 阅读器', () => {
    const wrapper = mountReader({ ...BASE });

    expect(wrapper.find('[data-testid="stage-courseware-count"]').text()).toContain('0 / 3');
    expect(wrapper.find('[data-testid="stage-courseware-viewer"]').exists()).toBe(true);
    // PdfPageView 收到 docId=186 与首页 pageNo=1。
    expect(wrapper.find('[data-testid="pdf-stub"]').text()).toContain('doc186-p1');
  });

  it('初始已读交集计入计数（已读 1 / 3）', () => {
    const wrapper = mountReader({ ...BASE, readPageIds: [101] });
    expect(wrapper.find('[data-testid="stage-courseware-count"]').text()).toContain('1 / 3');
    expect(wrapper.find('[data-testid="stage-courseware-passed"]').exists()).toBe(false);
  });

  it('未翻到末页时「标记整章已读」禁用；翻到末页后启用', async () => {
    const wrapper = mountReader({ ...BASE });
    const mark = () => wrapper.find('[data-testid="stage-courseware-mark-all"]');

    expect(mark().attributes('disabled')).toBeDefined();

    await wrapper.find('[data-testid="stage-courseware-last"]').trigger('click');

    // 翻到末页后 pageNo 同步、按钮启用。
    expect(wrapper.find('[data-testid="pdf-stub"]').text()).toContain('doc186-p3');
    expect(mark().attributes('disabled')).toBeUndefined();
  });

  it('翻到末页点击「标记整章已读」→ 批量上报全部 pageIds → emit progress → 过线', async () => {
    const wrapper = mountReader({ ...BASE });

    await wrapper.find('[data-testid="stage-courseware-last"]').trigger('click');
    await wrapper.find('[data-testid="stage-courseware-mark-all"]').trigger('click');
    await flushPromises();

    expect(vi.mocked(postPagesRead)).toHaveBeenCalledTimes(1);
    expect(vi.mocked(postPagesRead)).toHaveBeenCalledWith(7, [101, 102, 103]);

    const events = wrapper.emitted('progress');
    expect(events).toBeTruthy();
    expect(events?.[0]?.[0]).toMatchObject({ stageId: 7, readPageIds: [101, 102, 103] });

    // 过线徽标出现，计数徽标隐藏。
    expect(wrapper.find('[data-testid="stage-courseware-passed"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="stage-courseware-count"]').exists()).toBe(false);
  });

  it('打点失败时不过线、不 emit，可再次重试', async () => {
    vi.mocked(postPagesRead).mockRejectedValueOnce(new Error('network down'));
    const wrapper = mountReader({ ...BASE });

    await wrapper.find('[data-testid="stage-courseware-last"]').trigger('click');
    await wrapper.find('[data-testid="stage-courseware-mark-all"]').trigger('click');
    await flushPromises();

    expect(wrapper.emitted('progress')).toBeFalsy();
    expect(wrapper.find('[data-testid="stage-courseware-passed"]').exists()).toBe(false);

    // 重试成功 → 过线。
    vi.mocked(postPagesRead).mockResolvedValueOnce(
      makeStageProgress({ pagesRead: 3, pagesReadIds: [101, 102, 103] }),
    );
    await wrapper.find('[data-testid="stage-courseware-mark-all"]').trigger('click');
    await flushPromises();
    expect(wrapper.find('[data-testid="stage-courseware-passed"]').exists()).toBe(true);
  });

  it('初始已读满整章 → 直接显示已过线', () => {
    const wrapper = mountReader({ ...BASE, readPageIds: [101, 102, 103] });
    expect(wrapper.find('[data-testid="stage-courseware-passed"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="stage-courseware-mark-all"]').exists()).toBe(false);
  });

  it('pageIds 为空时显示占位，不渲染阅读器与标记按钮', () => {
    const wrapper = mountReader({
      ...BASE,
      pageIds: [],
      requiredPages: 0,
    });
    expect(wrapper.find('[data-testid="stage-courseware-viewer"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="stage-courseware-mark-all"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('本小节暂未关联课件');
  });

  describe('锚定当前页的百晓助教问答（升级原关键词检索）', () => {
    it('挂载锚定问答组件（StageContextTutor），并传入本节 KC', () => {
      const wrapper = mountReader({ ...BASE, kcCodes: ['PY43-KC-006'] });
      expect(wrapper.find('[data-testid="stage-context-tutor"]').exists()).toBe(true);
      expect(wrapper.find('[data-testid="stage-context-tutor-input"]').exists()).toBe(true);
    });

    it('不再渲染旧的关键词检索入口', () => {
      const wrapper = mountReader({ ...BASE });
      expect(wrapper.find('[data-testid="stage-courseware-search-toggle"]').exists()).toBe(false);
      expect(wrapper.find('[data-testid="stage-courseware-search-input"]').exists()).toBe(false);
    });
  });
});
