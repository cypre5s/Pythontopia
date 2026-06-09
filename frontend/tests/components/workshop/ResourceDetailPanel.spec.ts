/**
 * ResourceDetailPanel × GlassBox 决策玻璃盒挂载单测（P7.1 验收）。
 *
 * 背景：GlassBox.vue 此前为孤儿组件（无人引用）。本测验证它已被挂载在「工坊资源详情」
 * 这一真实决策点上，使「任意决策点可展开为什么」成立，并消费既有 GET /api/learner/decisions。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import type { WorkshopResourceDto } from '@/api/dto/Workshop';

const httpMocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
}));

vi.mock('@/api/http', () => ({
  http: httpMocks,
  setTokenProvider: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
  setNetworkErrorHandler: vi.fn(),
  __resetNetworkErrorCooldownForTest: vi.fn(),
  ApiClientError: class ApiClientError extends Error {},
}));

import ResourceDetailPanel from '@/components/workshop/ResourceDetailPanel.vue';
import GlassBox from '@/components/companion/GlassBox.vue';
import { useLearningStore } from '@stores/learning';

const RESOURCE: WorkshopResourceDto = {
  id: 7,
  userId: 1,
  packId: 43,
  kcCodes: ['PY43-KC-001'],
  chapterId: null,
  type: 'HANDOUT',
  title: '测试讲义',
  contentFormat: 'STRUCT_JSON',
  content: { sections: [] },
  evidencePageIds: [],
  summary: '摘要',
  safetyStatus: 'PASSED',
  groundingStatus: 'PASSED',
  feedbackStatus: 'NONE',
  feedbackNote: null,
  createdAt: '2026-06-01T00:00:00Z',
};

// 内容子视图非本测关注点，桩掉以聚焦决策盒挂载；GlassBox 保持真实组件。
const CONTENT_STUBS = {
  HandoutView: true,
  MindMapView: true,
  PracticeList: true,
  CodeLabView: true,
  ReadingList: true,
  StoryboardView: true,
  PptView: true,
};

function mountPanel(): ReturnType<typeof mount> {
  const learning = useLearningStore();
  learning.resources.current = { ...RESOURCE };
  return mount(ResourceDetailPanel, {
    // resourceId=null：immediate watcher 不触发 openResource（直接用已注入的 current）。
    props: { resourceId: null },
    global: { stubs: CONTENT_STUBS },
  });
}

describe('ResourceDetailPanel · 决策玻璃盒挂载（P7.1）', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    httpMocks.get.mockReset().mockResolvedValue({ data: [] });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('在资源详情内渲染 GlassBox 决策盒，并消费 GET /api/learner/decisions', async () => {
    const wrapper = mountPanel();
    await flushPromises();

    expect(wrapper.find('[data-testid="resource-detail"]').exists()).toBe(true);

    // 决策盒存在，且 GlassBox 渲染在其内部（真实组件，非桩）。
    const box = wrapper.find('[data-testid="resource-decision-box"]');
    expect(box.exists()).toBe(true);
    expect(box.text()).toContain('为什么这样生成');

    const glassBox = wrapper.findComponent(GlassBox);
    expect(glassBox.exists()).toBe(true);
    expect(box.findComponent(GlassBox).exists()).toBe(true);
    // GlassBox 实际渲染其根容器。
    expect(wrapper.find('.gb').exists()).toBe(true);

    // 使用既有决策端点（无需后端改动）。
    expect(httpMocks.get).toHaveBeenCalledWith('/api/learner/decisions');
  });

  it('GlassBox 把真实决策记录展示在决策盒内（证明挂载的是可工作的真实组件）', async () => {
    httpMocks.get.mockResolvedValueOnce({
      data: [
        {
          agent_name: 'ResourceDesignerAgent',
          scenario: '生成讲义',
          status: 'SUCCESS',
          evidence_page_ids: [12, 13],
          trace: '[]',
          ts: '2026-06-01T00:00:00Z',
        },
      ],
    });

    const wrapper = mountPanel();
    await flushPromises();

    const box = wrapper.find('[data-testid="resource-decision-box"]');
    expect(box.text()).toContain('ResourceDesignerAgent');
    expect(box.text()).toContain('生成讲义');
  });
});
