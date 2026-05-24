/**
 * R-13 §-1 续：ArenaPage 单元测试。
 *
 * 覆盖：
 *   1. mount /arena/1 渲染 7 章 tabs + 10 关阶梯
 *   2. 默认选中 1-1，第 1 关解锁，第 2..10 关锁住（state.totalCleared=0）
 *   3. 点击第 2 关：未解锁不触发 select
 *   4. markCleared 1-1 后第 2 关可解锁
 *   5. 点击「进入」第 1 关：跳转 /endless?chapter=1&kc=PY43-KC-001&arenaLevel=1-1
 *      并 markAttempt（attempts +1）
 *   6. 点击「挑战 BOSS」（1-10）：跳转 /boss/1（需 1-9 都通关后才可点）
 *   7. 章节切换 tab：路由跳到 /arena/2，selectedLevelId 重置到 2-1
 *   8. 显示总进度 0/70；通关一关后变 1/70
 */
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { createRouter, createMemoryHistory, type Router } from 'vue-router';
import ArenaPage from '@/pages/ArenaPage.vue';
import { useArenaStore } from '@/stores/arena';

const APP_LAYOUT_STUB = { template: '<div data-testid="layout-stub"><slot /></div>' };

function setupRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/arena/:chapter', component: ArenaPage },
      { path: '/world', component: { template: '<div data-testid="world-stub"/>' } },
      { path: '/endless', component: { template: '<div data-testid="endless-stub"/>' } },
      { path: '/boss/:chapter', component: { template: '<div data-testid="boss-stub"/>' } },
    ],
  });
}

async function mountAt(chapter: number) {
  const router = setupRouter();
  await router.push(`/arena/${chapter}`);
  await router.isReady();
  const wrapper = mount(ArenaPage, {
    global: { plugins: [router], stubs: { AppLayout: APP_LAYOUT_STUB } },
  });
  return { wrapper, router };
}

describe('ArenaPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    if (typeof window !== 'undefined') {
      window.localStorage.removeItem('pyt_arena_v1');
    }
  });

  it('renders 7 chapter tabs and 10 level steps for chapter 1', async () => {
    const { wrapper } = await mountAt(1);
    const tabs = wrapper.findAll('[data-testid^="arena-chapter-"]');
    expect(tabs).toHaveLength(7);
    const steps = wrapper.findAll('[data-testid^="arena-step-1-"]');
    expect(steps).toHaveLength(10);
    expect(wrapper.find('[data-testid="arena-detail"]').text()).toContain('中国计算往事');
  });

  it('only level 1 is unlocked initially', async () => {
    const { wrapper } = await mountAt(1);
    const step1 = wrapper.find('[data-testid="arena-step-1-1"]');
    const step2 = wrapper.find('[data-testid="arena-step-1-2"]');
    expect(step1.classes()).not.toContain('arena-step--locked');
    expect(step2.classes()).toContain('arena-step--locked');
  });

  it('clicking a locked level does not change selection', async () => {
    const { wrapper } = await mountAt(1);
    expect(wrapper.find('[data-testid="arena-detail"]').text()).toContain('中国计算往事');
    await wrapper.find('[data-testid="arena-step-1-5"]').trigger('click');
    expect(wrapper.find('[data-testid="arena-detail"]').text()).toContain('中国计算往事');
  });

  it('unlocks level 2 after marking level 1 cleared', async () => {
    const arena = useArenaStore();
    arena.markCleared('1-1', 30);
    const { wrapper } = await mountAt(1);
    const step2 = wrapper.find('[data-testid="arena-step-1-2"]');
    expect(step2.classes()).not.toContain('arena-step--locked');
  });

  it('entering level 1 routes to /endless with chapter+kc+arenaLevel', async () => {
    const { wrapper, router } = await mountAt(1);
    await wrapper.find('[data-testid="arena-enter"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.path).toBe('/endless');
    expect(router.currentRoute.value.query).toMatchObject({
      chapter: '1',
      kc: 'PY43-KC-001',
      arenaLevel: '1-1',
    });
    const arena = useArenaStore();
    expect(arena.getProgress('1-1').attempts).toBe(1);
  });

  it('entering boss (1-10) routes to /boss/1 after 1-9 cleared', async () => {
    const arena = useArenaStore();
    for (let i = 1; i <= 9; i += 1) {
      arena.markCleared(`1-${i}`, 20);
    }
    const { wrapper, router } = await mountAt(1);
    await wrapper.find('[data-testid="arena-step-1-10"]').trigger('click');
    await wrapper.find('[data-testid="arena-enter"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.path).toBe('/boss/1');
  });

  it('boss button is disabled (locked) until 1-9 cleared', async () => {
    const { wrapper } = await mountAt(1);
    await wrapper.find('[data-testid="arena-step-1-10"]').trigger('click');
    // 第 10 关锁住，selectedLevel 不变（仍是 1-1），所以 enter 仍跳 endless 不跳 boss
    expect(wrapper.find('[data-testid="arena-detail"]').text()).toContain('中国计算往事');
  });

  it('chapter switch updates route and selection', async () => {
    const { wrapper, router } = await mountAt(1);
    await wrapper.find('[data-testid="arena-chapter-3"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.path).toBe('/arena/3');
    expect(wrapper.find('[data-testid="arena-detail"]').text()).toContain('0 与 1 的计算思维');
  });

  it('progress chip shows X / 10 per chapter and total / 70', async () => {
    const arena = useArenaStore();
    arena.markCleared('1-1', 20);
    arena.markCleared('3-1', 25);
    const { wrapper } = await mountAt(1);
    expect(wrapper.find('[data-testid="arena-progress"]').text()).toContain('1 / 10');
    expect(wrapper.text()).toContain('2 / 70');
  });
});
