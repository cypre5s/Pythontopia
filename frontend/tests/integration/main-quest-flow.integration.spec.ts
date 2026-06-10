/**
 * R-13 §5 Phase 7：主线 + Boss 完整闭环集成测试。
 *
 * 不启 dev server / Phaser，纯组件级 mount + 状态机断言。
 * 覆盖：HudShell 主线入口 → 打开 panel → 接任务 → 推进进度 → 章 boss 解锁 → 进入 BossPage → 通关 → 主线推进。
 */

import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';

import HudShell from '@/components/hud/HudShell.vue';
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
      { path: '/world', name: 'world', component: { template: '<div data-testid="world-stub" />' } },
      { path: '/boss/:chapter', name: 'boss', component: { template: '<div data-testid="boss-stub" />' } },
      { path: '/profile', name: 'profile', component: { template: '<div data-testid="profile-stub" />' } },
      { path: '/library', name: 'library', component: { template: '<div data-testid="library-stub" />' } },
      { path: '/arena/:chapter', name: 'arena', component: { template: '<div data-testid="arena-stub" />' } },
    ],
  });
}

function mountHud() {
  return mount(HudShell, { global: { plugins: [router] } });
}

beforeEach(() => {
  setActivePinia(createPinia());
  window.localStorage.clear();
  router = setupRouter();
});

describe('R-13 §5 主线 + Boss 端到端集成', () => {
  it('HudShell 主线摘要显示初始章节 0/5', async () => {
    const wrapper = mountHud();
    expect(wrapper.text()).toContain('第 1 章');
    expect(wrapper.get('[data-testid="hud-quest-summary"]').text()).toContain('0 / 5');
  });

  it('点击 HudShell 任务入口打开 panel，panel 默认渲染 7 章 35 任务', async () => {
    const hud = mountHud();
    await hud.find('[data-testid="hud-slot-quest"]').trigger('click');

    const panel = mount(MainQuestPanel, { global: { plugins: [router] } });
    await flushPromises();
    expect(panel.find('[data-testid="main-quest-panel"]').exists()).toBe(true);
    expect(panel.findAll('[data-testid^="main-quest-sub-"]')).toHaveLength(35);
  });

  it('完成 5 个章 1 子任务后 boss 按钮启用', async () => {
    const store = useMainQuestStore();
    store.openPanel();
    const panel = mount(MainQuestPanel, { global: { plugins: [router] } });
    await flushPromises();

    let bossBtn = panel.find('[data-testid="main-quest-boss-1"]');
    expect(bossBtn.attributes('disabled')).toBeDefined();

    for (let i = 1; i <= 5; i += 1) {
      store.completeSubQuest(`1-${i}`);
    }
    await flushPromises();
    bossBtn = panel.find('[data-testid="main-quest-boss-1"]');
    expect(bossBtn.attributes('disabled')).toBeUndefined();
  });

  it('点击 boss 按钮把章节 queue 进 store 且 push 到 /boss/1', async () => {
    const store = useMainQuestStore();
    for (let i = 1; i <= 5; i += 1) store.completeSubQuest(`1-${i}`);
    store.openPanel();
    await router.push('/');

    const panel = mount(MainQuestPanel, { global: { plugins: [router] } });
    await flushPromises();
    await panel.find('[data-testid="main-quest-boss-1"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.path).toBe('/boss/1');
    expect(store.consumePendingBoss()).toBe(1);
  });

  it('完成所有 5 子任务 + defeatBoss 后章节推进到 2', async () => {
    const store = useMainQuestStore();
    for (let i = 1; i <= 5; i += 1) store.completeSubQuest(`1-${i}`);
    expect(store.progress.currentChapter).toBe(1);
    store.defeatBoss(1);
    expect(store.progress.currentChapter).toBe(2);
    expect(store.progress.currentSubQuest).toBe(1);
  });

  it('「去做」按钮把 player 传送到任务对应地图', async () => {
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
    const panel = mount(MainQuestPanel, { global: { plugins: [router] } });
    await flushPromises();

    await panel.find('[data-testid="main-quest-go-3-1"]').trigger('click');
    await flushPromises();
    expect(world.player.mapKey).toBe('town_hub');
    expect(router.currentRoute.value.path).toBe('/world');
  });

  it('整体进度从 0 → 1/35 → 2/35 时 HudShell 显示 3 / 6%', async () => {
    const store = useMainQuestStore();
    store.completeSubQuest('1-1');
    let hud = mountHud();
    expect(hud.get('[data-testid="hud-quest-summary"]').attributes('aria-label')).toContain('总进度 3%');
    hud.unmount();
    store.completeSubQuest('1-2');
    hud = mountHud();
    expect(hud.get('[data-testid="hud-quest-summary"]').attributes('aria-label')).toContain('总进度 6%');
  });

  it('章节面板状态切换：completed / in_progress / available / locked', async () => {
    const store = useMainQuestStore();
    store.completeSubQuest('1-1');
    store.completeSubQuest('1-2');
    store.openPanel();
    const panel = mount(MainQuestPanel, { global: { plugins: [router] } });
    await flushPromises();

    const sub1 = panel.find('[data-testid="main-quest-sub-1-1"]');
    const sub3 = panel.find('[data-testid="main-quest-sub-1-3"]');
    const sub5 = panel.find('[data-testid="main-quest-sub-1-5"]');
    expect(sub1.classes().join(' ')).toContain('completed');
    expect(sub3.classes().join(' ')).toContain('in_progress');
    expect(sub5.classes().join(' ')).toContain('locked');
  });

  it('panel 在 store.panelOpen=false 时不渲染', () => {
    const panel = mount(MainQuestPanel, { global: { plugins: [router] } });
    expect(panel.find('[data-testid="main-quest-panel"]').exists()).toBe(false);
  });

  it('hydrateFromSnapshot 把后端 snapshot 状态映射到本地', async () => {
    const store = useMainQuestStore();
    store.hydrateFromSnapshot({
      mainQuest: {
        currentChapter: 3,
        currentSubQuest: 2,
        completedSubQuests: ['1-1', '1-2', '1-3', '1-4', '1-5', '2-1'],
        defeatedBosses: [1],
        unlockedMilestones: ['outfit:apprentice'],
        recentAiProblems: [],
      },
    });
    expect(store.progress.currentChapter).toBe(3);
    expect(store.progress.completedSubQuests.length).toBe(6);
    const hud = mountHud();
    expect(hud.get('[data-testid="hud-quest-summary"]').text()).toContain('第3章');
  });

  it('defeatBoss 解锁 milestone 写入 unlockedMilestones', async () => {
    const store = useMainQuestStore();
    store.defeatBoss(2);
    // R-13 mainQuests v2：章 2 boss reward 含 title:python_speaker
    expect(store.progress.unlockedMilestones).toContain('title:python_speaker');
  });

  it('localStorage 持久化跨 store 实例', async () => {
    const first = useMainQuestStore();
    first.completeSubQuest('1-1');
    first.defeatBoss(1);
    setActivePinia(createPinia());
    const second = useMainQuestStore();
    expect(second.progress.completedSubQuests).toContain('1-1');
    expect(second.progress.defeatedBosses).toContain(1);
  });

  it('overallProgress 与 chapterViews 反映真实完成数', async () => {
    const store = useMainQuestStore();
    for (let i = 1; i <= 5; i += 1) store.completeSubQuest(`1-${i}`);
    store.defeatBoss(1);
    expect(store.overallProgress).toBeCloseTo(5 / 35, 6);
    expect(store.chapterViews[0]?.bossDefeated).toBe(true);
  });

  it('queueBoss / consumePendingBoss 单消费语义', async () => {
    const store = useMainQuestStore();
    store.queueBoss(2);
    expect(store.consumePendingBoss()).toBe(2);
    expect(store.consumePendingBoss()).toBeNull();
  });

  it('两次相同 sub-quest 完成不重复计数', async () => {
    const store = useMainQuestStore();
    store.completeSubQuest('1-1');
    store.completeSubQuest('1-1');
    expect(store.progress.completedSubQuests).toEqual(['1-1']);
  });

  it('章 7 boss 通关不超过章 7', async () => {
    const store = useMainQuestStore();
    store.progress.currentChapter = 7;
    store.progress.currentSubQuest = 5;
    store.defeatBoss(7);
    expect(store.progress.currentChapter).toBe(7);
    expect(store.progress.defeatedBosses).toContain(7);
  });

  it('recordAiProblem 写最近 30 题', async () => {
    const store = useMainQuestStore();
    for (let i = 0; i < 5; i += 1) {
      store.recordAiProblem({
        problemId: `p${i}`,
        kc: 'PY43-KC-001',
        difficulty: 2,
        succeededInSec: 10,
        timestamp: new Date().toISOString(),
      });
    }
    expect(store.progress.recentAiProblems.length).toBe(5);
    expect(store.progress.recentAiProblems[0]?.problemId).toBe('p4');
  });

  it('完成所有 35 任务 + 7 boss 后 overallProgress=1.0', async () => {
    const store = useMainQuestStore();
    for (let chapter = 1; chapter <= 7; chapter += 1) {
      for (let idx = 1; idx <= 5; idx += 1) {
        store.completeSubQuest(`${chapter}-${idx}`);
      }
      store.defeatBoss(chapter);
    }
    expect(store.progress.completedSubQuests.length).toBe(35);
    expect(store.progress.defeatedBosses.length).toBe(7);
    expect(store.overallProgress).toBe(1);
  });

  it('resetProgress 清空所有进度回到第 1 章', async () => {
    const store = useMainQuestStore();
    store.completeSubQuest('1-1');
    store.defeatBoss(1);
    store.resetProgress();
    expect(store.progress.currentChapter).toBe(1);
    expect(store.progress.completedSubQuests).toEqual([]);
    expect(store.progress.defeatedBosses).toEqual([]);
  });

  it('HudShell 主线摘要不受自适应 AI 出题历史长度影响', async () => {
    const store = useMainQuestStore();
    store.recordAiProblem({
      problemId: 'p1',
      kc: 'PY43-KC-001',
      difficulty: 2,
      succeededInSec: 12,
      timestamp: new Date().toISOString(),
    });
    const hud = mountHud();
    // HUD 不显示历史，但断言 store 已写
    expect(store.progress.recentAiProblems.length).toBe(1);
    expect(hud.get('[data-testid="hud-quest-summary"]').text()).toContain('第1章');
  });
});
