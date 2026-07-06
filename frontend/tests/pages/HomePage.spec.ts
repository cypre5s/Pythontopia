/**
 * HomePage「今日推荐」卡片单测（P3.3 验收）。
 *
 * 覆盖：
 *   1. 推荐 API（GET /api/learner/recommendations → RecommendationDto[]）返回数据时，
 *      卡片渲染标题 + 知识点 + **推荐理由（reason，"为什么推这个"）**；
 *   2. 推荐为空时，渲染空态文案，不渲染任何推荐卡片。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import type { RecommendationDto } from '@/api/recommendations';

const apiMocks = vi.hoisted(() => ({
  getRecommendations: vi.fn(),
  listMyClassrooms: vi.fn(),
  fetchMastery: vi.fn(),
  getTodayQuests: vi.fn(),
}));

vi.mock('@/api/recommendations', () => ({
  getRecommendations: apiMocks.getRecommendations,
}));

vi.mock('@/api/classroom', () => ({
  listMyClassrooms: apiMocks.listMyClassrooms,
  createClassroom: vi.fn(),
  joinClassroom: vi.fn(),
  listClassroomMembers: vi.fn(),
  removeClassroomMember: vi.fn(),
}));

vi.mock('@/api/field', () => ({
  fetchMastery: apiMocks.fetchMastery,
  bootstrapCurriculum: vi.fn(),
}));

vi.mock('@/api/quests', () => ({
  getTodayQuests: apiMocks.getTodayQuests,
  completeQuest: vi.fn(),
  skipQuest: vi.fn(),
  startQuest: vi.fn(),
  listQuests: vi.fn(),
}));

vi.mock('@/composables/useAudio', () => ({
  playSfx: vi.fn(),
}));

import HomePage from '@/pages/HomePage.vue';
import { useSessionStore } from '@stores/session';

const APP_LAYOUT_STUB = { template: '<div data-testid="layout-stub"><slot /></div>' };

const RECS: RecommendationDto[] = [
  {
    resourceId: 101,
    title: 'for 循环讲义',
    type: 'HANDOUT',
    kcCode: 'PY43-KC-012',
    kcName: 'for 循环',
    reason: '你在「for 循环」练习连续两题超时，先补这份讲义',
  },
  {
    resourceId: 102,
    title: '列表切片小测',
    type: 'PRACTICE',
    kcCode: 'PY43-KC-020',
    kcName: '列表切片',
    reason: '掌握度仅 40%，建议巩固',
  },
];

function setupRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: HomePage },
      { path: '/workshop', component: { template: '<div data-testid="workshop-stub"/>' } },
      { path: '/world', component: { template: '<div data-testid="world-stub"/>' } },
      { path: '/tutor', component: { template: '<div data-testid="tutor-stub"/>' } },
      { path: '/login', component: { template: '<div data-testid="login-stub"/>' } },
    ],
  });
}

async function mountHome(): Promise<ReturnType<typeof mount>> {
  const router = setupRouter();
  await router.push('/');
  await router.isReady();
  const wrapper = mount(HomePage, {
    global: {
      plugins: [router],
      stubs: { AppLayout: APP_LAYOUT_STUB },
    },
  });
  await flushPromises();
  return wrapper;
}

describe('HomePage · 今日推荐卡片', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    // 登录态：仪表盘（含今日推荐）仅在已登录时渲染。
    const session = useSessionStore();
    session.token = 'test-token';
    session.expiresAt = new Date(Date.now() + 3_600_000).toISOString();

    Object.values(apiMocks).forEach((fn) => fn.mockReset());
    apiMocks.listMyClassrooms.mockResolvedValue([]);
    apiMocks.fetchMastery.mockResolvedValue([]);
    apiMocks.getTodayQuests.mockResolvedValue([]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('推荐 API 返回数据 → 渲染卡片标题、知识点与推荐理由（为什么推这个）', async () => {
    apiMocks.getRecommendations.mockResolvedValue(RECS);

    const wrapper = await mountHome();

    expect(apiMocks.getRecommendations).toHaveBeenCalledTimes(1);

    const cards = wrapper.findAll('.rec-card');
    expect(cards.length).toBe(2);

    // 第一张卡：标题 + 知识点 + 推荐理由都渲染。
    const first = cards[0]!;
    expect(first.find('.rec-card__title').text()).toBe('for 循环讲义');
    expect(first.find('.rec-card__kc').text()).toBe('for 循环');
    expect(first.find('.rec-card__reason').text()).toBe(
      '你在「for 循环」练习连续两题超时，先补这份讲义',
    );

    // 推荐理由（reason）是验收硬要求：整段「为什么推这个」文案必须出现在卡片区域。
    const section = wrapper.find('.recommend-section');
    expect(section.text()).toContain('你在「for 循环」练习连续两题超时，先补这份讲义');
    expect(section.text()).toContain('掌握度仅 40%，建议巩固');
    // 不再显示加载/空态。
    expect(section.text()).not.toContain('加载推荐中');
    expect(section.text()).not.toContain('暂无推荐资源');
  });

  it('推荐为空 → 渲染空态文案且不渲染任何推荐卡片', async () => {
    apiMocks.getRecommendations.mockResolvedValue([]);

    const wrapper = await mountHome();

    expect(apiMocks.getRecommendations).toHaveBeenCalledTimes(1);
    expect(wrapper.findAll('.rec-card').length).toBe(0);

    const section = wrapper.find('.recommend-section');
    expect(section.text()).toContain('暂无推荐资源');
  });
});
