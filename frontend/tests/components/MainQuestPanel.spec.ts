import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import MainQuestPanel from '@/components/hud/MainQuestPanel.vue';
import { useMainQuestStore } from '@stores/mainQuest';
import { useWorldStore } from '@stores/world';

vi.mock('@/composables/useStardewFx', () => ({
  playUiClick: vi.fn(),
  playUiHover: vi.fn(),
  playCoinJingle: vi.fn(),
  playChime: vi.fn(),
}));

let router: Router;

function setupRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/world', component: { template: '<div data-testid="world-stub"/>' } },
      { path: '/boss/:chapter', component: { template: '<div data-testid="boss-stub"/>' } },
    ],
  });
}

describe('MainQuestPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    window.localStorage.clear();
    router = setupRouter();
  });

  it('hidden when store.panelOpen=false', () => {
    const wrapper = mount(MainQuestPanel, { global: { plugins: [router] } });
    expect(wrapper.find('[data-testid="main-quest-panel"]').exists()).toBe(false);
  });

  it('visible when store.panelOpen=true; renders 7 chapters + 35 sub-quests', async () => {
    const store = useMainQuestStore();
    store.openPanel();
    const wrapper = mount(MainQuestPanel, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.find('[data-testid="main-quest-panel"]').exists()).toBe(true);
    const subItems = wrapper.findAll('[data-testid^="main-quest-sub-"]');
    expect(subItems.length).toBe(35);
  });

  it('clicking close button closes panel', async () => {
    const store = useMainQuestStore();
    store.openPanel();
    const wrapper = mount(MainQuestPanel, { global: { plugins: [router] } });
    await wrapper.find('button.mq-card__close').trigger('click');
    expect(store.panelOpen).toBe(false);
  });

  it('clicking outside the card (backdrop) closes panel', async () => {
    const store = useMainQuestStore();
    store.openPanel();
    const wrapper = mount(MainQuestPanel, { global: { plugins: [router] } });
    await wrapper.find('[data-testid="main-quest-panel"]').trigger('click');
    expect(store.panelOpen).toBe(false);
  });

  it('「去做」 button teleports player to mapped fast-travel target and pushes /world', async () => {
    const world = useWorldStore();
    world.player = {
      playerStateId: 1,
      avatarKey: 'student',
      mapKey: 'town_hub',
      tileX: 25,
      tileY: 20,
      direction: 'DOWN',
    };
    const store = useMainQuestStore();
    store.openPanel();

    await router.push('/');
    const wrapper = mount(MainQuestPanel, { global: { plugins: [router] } });
    await flushPromises();

    // R-13 mainQuests v2：章 2 farm_north 任务在 2-5（理解程序运行方式）
    await wrapper.find('[data-testid="main-quest-go-2-5"]').trigger('click');
    await flushPromises();

    expect(world.player.mapKey).toBe('farm_north');
    expect(router.currentRoute.value.path).toBe('/world');
    expect(store.panelOpen).toBe(false);
  });

  it('「完成」 sub-quest 「去做」 按钮变 disabled', async () => {
    const store = useMainQuestStore();
    store.completeSubQuest('1-1');
    store.openPanel();
    const wrapper = mount(MainQuestPanel, { global: { plugins: [router] } });
    await flushPromises();

    const go11 = wrapper.find('[data-testid="main-quest-go-1-1"]');
    expect(go11.attributes('disabled')).toBeDefined();
    expect(go11.text()).toContain('已完成');
  });

  it('章 Boss 按钮：未完成 5 任务时 disabled，完成后 enabled', async () => {
    const store = useMainQuestStore();
    store.openPanel();
    const wrapper = mount(MainQuestPanel, { global: { plugins: [router] } });
    await flushPromises();

    const bossBtn = wrapper.find('[data-testid="main-quest-boss-1"]');
    expect(bossBtn.attributes('disabled')).toBeDefined();

    for (let i = 1; i <= 5; i += 1) {
      store.completeSubQuest(`1-${i}`);
    }
    await flushPromises();
    const bossBtn2 = wrapper.find('[data-testid="main-quest-boss-1"]');
    expect(bossBtn2.attributes('disabled')).toBeUndefined();
  });

  it('点击 boss 按钮把章号 queue 进 store 并 push /boss/{chapter}', async () => {
    const store = useMainQuestStore();
    for (let i = 1; i <= 5; i += 1) store.completeSubQuest(`1-${i}`);
    store.openPanel();
    await router.push('/');
    const wrapper = mount(MainQuestPanel, { global: { plugins: [router] } });
    await flushPromises();

    await wrapper.find('[data-testid="main-quest-boss-1"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.path).toBe('/boss/1');
    expect(store.consumePendingBoss()).toBe(1);
  });

  it('整体进度百分比正确显示', async () => {
    const store = useMainQuestStore();
    store.completeSubQuest('1-1');
    store.completeSubQuest('1-2');
    store.openPanel();
    const wrapper = mount(MainQuestPanel, { global: { plugins: [router] } });
    await flushPromises();
    // 2/35 ≈ 6%
    expect(wrapper.text()).toContain('6%');
  });

  it('章 progress mini bar 反映该章完成数', async () => {
    const store = useMainQuestStore();
    store.completeSubQuest('1-1');
    store.completeSubQuest('1-2');
    store.openPanel();
    const wrapper = mount(MainQuestPanel, { global: { plugins: [router] } });
    await flushPromises();
    expect(wrapper.text()).toContain('2/5');
  });
});
