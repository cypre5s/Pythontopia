// 终局演出：GraduationPage 守卫与渲染测试。
// R-15：毕业条件 = 7 章全部 ≥1★（全部通过章末测试），星级取后端权威 mainline overview。
import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import GraduationPage from '@/pages/GraduationPage.vue';
import { useMainlineStore } from '@/stores/mainline';
import { useLearningStore } from '@/stores/learning';
import type { MainlineChapterDto, MainlineOverviewDto } from '@/api/mainline';

vi.mock('@/utils/celebrate', () => ({
  celebrateLevelUp: vi.fn(),
}));

function makeChapter(chapter: number, stars: number): MainlineChapterDto {
  return {
    chapter,
    title: `第${chapter}章`,
    unlocked: true,
    stars,
    bestTestScore: stars >= 1 ? 80 : null,
    stages: [],
    testStatus: 'NONE',
    starBreakdown: { test: 0, stage: 0, mastery: 0, recovered: [] },
    recovery: { errorbookDone: false, endlessDone: false },
  };
}

function makeOverview(starsPerChapter: number[]): MainlineOverviewDto {
  const chapters = starsPerChapter.map((s, i) => makeChapter(i + 1, s));
  return { totalStars: starsPerChapter.reduce((a, b) => a + b, 0), chapters };
}

function createTestRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/world', name: 'world', component: { template: '<div />' } },
      { path: '/profile-room', name: 'profile-room', component: { template: '<div />' } },
      { path: '/graduation', name: 'graduation', component: GraduationPage },
    ],
  });
}

describe('GraduationPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
  });

  it('未达毕业条件（存在 <1★ 章节）经 URL 直入 → 重定向回 /world', async () => {
    const router = createTestRouter();
    await router.push('/graduation');
    await router.isReady();

    // 预置 overview，load() 因已有数据早返回（不发 HTTP）。
    const mainline = useMainlineStore();
    mainline.overview = makeOverview([1, 1, 1, 0, 0, 0, 0]);

    const wrapper = mount(GraduationPage, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.find('[data-testid="graduation-page"]').exists()).toBe(false);
    expect(router.currentRoute.value.path).toBe('/world');
  });

  it('7 章全部 ≥1★ → 渲染毕业典礼与星级回顾', async () => {
    const router = createTestRouter();
    await router.push('/graduation');
    await router.isReady();

    const mainline = useMainlineStore();
    mainline.overview = makeOverview([3, 2, 1, 1, 1, 2, 3]); // 总星 13，7 章全通过
    const learning = useLearningStore();
    learning.field.masterySnapshot = [
      { kcCode: 'PY43-KC-001', masteryScore: 0.6 },
      { kcCode: 'PY43-KC-002', masteryScore: 0.2 },
    ] as never;

    const wrapper = mount(GraduationPage, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.find('[data-testid="graduation-page"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('毕业典礼');
    expect(wrapper.find('[data-testid="graduation-stars"]').text()).toBe('13 / 21');
    expect(wrapper.find('[data-testid="graduation-chapters"]').text()).toBe('7 / 7');
    expect(wrapper.find('[data-testid="graduation-kcs"]').text()).toBe('1 / 65');

    await wrapper.find('[data-testid="graduation-back"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.path).toBe('/world');
  });
});
