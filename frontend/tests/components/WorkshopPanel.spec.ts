import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import WorkshopPanel from '@/components/workshop/WorkshopPanel.vue';
import { useLearningStore } from '@stores/learning';

// R-13 §-1：WorkshopPanel 改造后不再受 unlockedCrops 限制，
// 改为通过 /api/course/chapters + /api/course/kcs 拉课程包 43 全部 65 KC。
const apiMocks = vi.hoisted(() => ({
  createWorkshopResource: vi.fn(),
  listWorkshopResources: vi.fn(),
  getWorkshopResource: vi.fn(),
  submitWorkshopFeedback: vi.fn(),
  listChapters: vi.fn(),
  listKnowledgeComponents: vi.fn(),
}));

vi.mock('@/api/workshop', () => ({
  createWorkshopResource: apiMocks.createWorkshopResource,
  listWorkshopResources: apiMocks.listWorkshopResources,
  getWorkshopResource: apiMocks.getWorkshopResource,
  submitWorkshopFeedback: apiMocks.submitWorkshopFeedback,
}));

vi.mock('@/api/course', () => ({
  listChapters: apiMocks.listChapters,
  listKnowledgeComponents: apiMocks.listKnowledgeComponents,
  getCoursePack: vi.fn(),
  listKcPages: vi.fn(),
  getProblem: vi.fn(),
}));

let router: Router;

function setupRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/workshop', component: { template: '<div data-testid="workshop-placeholder"/>' } },
    ],
  });
}

const CHAPTERS = [
  { chapterId: 179, orderIndex: 1, title: '第一章：计算工具', summaryMarkdown: null },
  { chapterId: 180, orderIndex: 2, title: '第二章：Python', summaryMarkdown: null },
  { chapterId: 181, orderIndex: 3, title: '第三章：信息表示', summaryMarkdown: null },
];

const KCS = [
  {
    kcId: 1,
    kcCode: 'PY43-KC-001',
    kcName: '计算思维',
    chapterId: 179,
    chapterOrder: 1,
    difficulty: 'basic',
    summaryMarkdown: '入门 KC',
  },
  {
    kcId: 2,
    kcCode: 'PY43-KC-002',
    kcName: 'print 函数',
    chapterId: 179,
    chapterOrder: 1,
    difficulty: 'basic',
    summaryMarkdown: '打印第一行',
  },
  {
    kcId: 3,
    kcCode: 'PY43-KC-010',
    kcName: 'list 基础',
    chapterId: 180,
    chapterOrder: 2,
    difficulty: 'basic',
    summaryMarkdown: '列表入门',
  },
  {
    kcId: 4,
    kcCode: 'PY43-KC-030',
    kcName: '二进制',
    chapterId: 181,
    chapterOrder: 3,
    difficulty: 'basic',
    summaryMarkdown: '十进制 → 二进制',
  },
];

describe('WorkshopPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    router = setupRouter();
    apiMocks.createWorkshopResource.mockReset();
    apiMocks.listWorkshopResources.mockReset();
    apiMocks.listChapters.mockReset();
    apiMocks.listKnowledgeComponents.mockReset();
    apiMocks.createWorkshopResource.mockResolvedValue({
      runId: 42,
      resourceId: 7,
      status: 'SUCCESS',
    });
    apiMocks.listWorkshopResources.mockResolvedValue([]);
    apiMocks.listChapters.mockResolvedValue(CHAPTERS);
    apiMocks.listKnowledgeComponents.mockResolvedValue(KCS);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('loads chapters + 65 KC catalog on mount and renders chapter groups', async () => {
    await router.push('/workshop');
    const wrapper = mount(WorkshopPanel, { global: { plugins: [router] } });
    await flushPromises();

    expect(apiMocks.listChapters).toHaveBeenCalledWith(43);
    expect(apiMocks.listKnowledgeComponents).toHaveBeenCalledWith(43);
    expect(wrapper.find('[data-testid="kc-chapter-list"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('4 个 KC');
    expect(wrapper.text()).toContain('第 1 章');
    expect(wrapper.text()).toContain('第 2 章');
    expect(wrapper.text()).toContain('第 3 章');
  });

  it('allows selecting unlocked AND locked KC (no unlock gate)', async () => {
    const learning = useLearningStore();
    learning.field.crops = [];

    await router.push('/workshop');
    const wrapper = mount(WorkshopPanel, { global: { plugins: [router] } });
    await flushPromises();

    const kcCheckbox = wrapper.find('[data-testid="kc-checkbox-PY43-KC-001"]');
    expect(kcCheckbox.exists()).toBe(true);
    await kcCheckbox.setValue(true);

    expect(wrapper.find('[data-testid="selected-count"]').text()).toContain('已选 1');
  });

  it('renders unlocked star marker for crops player has unlocked', async () => {
    const learning = useLearningStore();
    learning.field.crops = [
      {
        tileX: 2,
        tileY: 2,
        kcCode: 'PY43-KC-002',
        status: 'SEED',
        unlocked: true,
        masteryScore: 0.5,
        growthStage: 1,
      },
    ];

    await router.push('/workshop');
    const wrapper = mount(WorkshopPanel, { global: { plugins: [router] } });
    await flushPromises();

    const chip = wrapper.find('[data-testid="kc-checkbox-PY43-KC-002"]').element.closest('label');
    expect(chip?.className).toContain('kc-chip--unlocked');
  });

  it('expands collapsed chapters on click to show new KC checkboxes', async () => {
    await router.push('/workshop');
    const wrapper = mount(WorkshopPanel, { global: { plugins: [router] } });
    await flushPromises();

    // 第三章默认折叠（只展开前两章）
    expect(wrapper.find('[data-testid="kc-checkbox-PY43-KC-030"]').exists()).toBe(false);

    const chapter3Head = wrapper
      .findAll('button.chapter-head')
      .find((btn) => btn.text().includes('第 3 章'));
    expect(chapter3Head).toBeDefined();
    await chapter3Head!.trigger('click');
    await flushPromises();

    expect(wrapper.find('[data-testid="kc-checkbox-PY43-KC-030"]').exists()).toBe(true);
  });

  it('submits the create request with selected KCs and pushed type', async () => {
    await router.push('/workshop');
    const wrapper = mount(WorkshopPanel, { global: { plugins: [router] } });
    await flushPromises();

    await wrapper.find('[data-testid="kc-checkbox-PY43-KC-001"]').setValue(true);
    await wrapper.find('[data-testid="kc-checkbox-PY43-KC-002"]').setValue(true);
    await wrapper.find('[data-testid="type-PRACTICE"]').setValue();

    const submitBtn = wrapper.find('button.primary');
    expect(submitBtn.attributes('disabled')).toBeUndefined();
    await submitBtn.trigger('click');
    await flushPromises();

    expect(apiMocks.createWorkshopResource).toHaveBeenCalledTimes(1);
    expect(apiMocks.createWorkshopResource.mock.calls[0]?.[0]).toMatchObject({
      kcCodes: ['PY43-KC-001', 'PY43-KC-002'],
      type: 'PRACTICE',
    });
    expect(router.currentRoute.value.query.id).toBe('7');
  });

  it('disables submit button when no KC selected', async () => {
    await router.push('/workshop');
    const wrapper = mount(WorkshopPanel, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.find('button.primary').attributes('disabled')).toBe('');
  });

  it('shows error hint when catalog load fails', async () => {
    apiMocks.listChapters.mockRejectedValue(new Error('网络中断'));
    await router.push('/workshop');
    const wrapper = mount(WorkshopPanel, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.text()).toContain('网络中断');
    expect(wrapper.find('[data-testid="kc-chapter-list"]').exists()).toBe(false);
  });
});
