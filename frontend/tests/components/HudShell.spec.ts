import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter } from 'vue-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import HudShell from '@/components/hud/HudShell.vue';
import QuickslotPanelHost from '@/components/hud/QuickslotPanelHost.vue';
import { useDialogStore } from '@stores/dialog';
import { useHudStore, type HudQuickSlot } from '@stores/hud';

// R-15：dock 删「任务」槽（与冒险地图重复）→ 学习 quickslot 仅剩 dialog/map/skill 三槽。
const SLOT_KEYS: readonly HudQuickSlot[] = [
  'dialog',
  'map',
  'skill',
];
const HUD_SLOT_BUTTONS = SLOT_KEYS.length + 1; // 三个 quickslot + 「指南」工具入口。

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/world', name: 'world', component: { template: '<div/>' } },
    { path: '/workshop', name: 'workshop', component: { template: '<div/>' } },
    { path: '/mainline-map', name: 'mainline-map', component: { template: '<div/>' } },
  ],
});

function mountShell(initialPath = '/workshop') {
  void router.push(initialPath);
  return mount(HudShell, { global: { plugins: [router] } });
}

describe('HudShell', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('挂载后渲染 3 个 quickslot + 指南按钮，不再有「任务」槽', () => {
    const wrapper = mountShell();
    for (const key of SLOT_KEYS) {
      const btn = wrapper.find(`[data-testid="hud-slot-${key}"]`);
      expect(btn.exists()).toBe(true);
      expect(btn.element.tagName).toBe('BUTTON');
    }
    expect(wrapper.find('[data-testid="hud-slot-manual"]').exists()).toBe(true);
    expect(wrapper.findAll('button.hud-slot')).toHaveLength(HUD_SLOT_BUTTONS);
    // 「任务」槽已删除。
    expect(wrapper.find('[data-testid="hud-slot-quest"]').exists()).toBe(false);
  });

  it('点击「地图」槽直达冒险地图(/mainline-map)，不切 activeSlot 面板', async () => {
    const hud = useHudStore();
    const push = vi.spyOn(router, 'push');
    const wrapper = mountShell();
    await wrapper.get('[data-testid="hud-slot-map"]').trigger('click');
    expect(push).toHaveBeenCalledWith('/mainline-map');
    expect(hud.activeSlot).toBeNull();
    push.mockRestore();
  });

  it('顶部状态条按 store.meta 渲染：天气 / Day / 时段 / 季节', () => {
    const hud = useHudStore();
    hud.syncMeta({
      weather: 'RAINY',
      dayIndex: 12,
      timeOfDay: 'EVENING',
      season: 'AUTUMN',
    });
    const wrapper = mountShell();
    expect(wrapper.get('[data-testid="hud-weather"]').text()).toContain('RAINY');
    expect(wrapper.get('[data-testid="hud-day"]').text()).toContain('12');
    // 时段/季节以像素图标呈现，语义信息在 title 里（time/season 无文本 value span）。
    expect(wrapper.get('[data-testid="hud-time"]').attributes('title')).toContain('EVENING');
    expect(wrapper.get('[data-testid="hud-season"]').attributes('title')).toContain('AUTUMN');
  });

  it('点击 dialog 按钮：store.activeSlot === "dialog" 且按钮带 active class + aria-pressed', async () => {
    const hud = useHudStore();
    const wrapper = mountShell();
    const btn = wrapper.get('[data-testid="hud-slot-dialog"]');
    await btn.trigger('click');
    expect(hud.activeSlot).toBe('dialog');
    expect(btn.classes()).toContain('hud-slot--active');
    expect(btn.attributes('aria-pressed')).toBe('true');
  });

  it('再次点击同一按钮：activeSlot 回到 null 并清除 active class', async () => {
    const hud = useHudStore();
    const wrapper = mountShell();
    const btn = wrapper.get('[data-testid="hud-slot-skill"]');
    await btn.trigger('click');
    expect(hud.activeSlot).toBe('skill');
    await btn.trigger('click');
    expect(hud.activeSlot).toBeNull();
    expect(btn.classes()).not.toContain('hud-slot--active');
    expect(btn.attributes('aria-pressed')).toBe('false');
  });

  it('bottomBar = hidden 时整条底部栏不渲染（5 个按钮全部消失，hud-bottom 也消失）', async () => {
    const hud = useHudStore();
    const wrapper = mountShell();
    hud.setVisibility('bottom', 'hidden');
    await flushPromises();
    expect(wrapper.find('[data-testid="hud-bottom"]').exists()).toBe(false);
    for (const key of SLOT_KEYS) {
      expect(wrapper.find(`[data-testid="hud-slot-${key}"]`).exists()).toBe(false);
    }
  });

  it('syncMeta 后顶部状态条 DOM 同步更新（响应式）', async () => {
    const hud = useHudStore();
    const wrapper = mountShell();
    hud.syncMeta({ dayIndex: 1, weather: 'SUNNY' });
    await flushPromises();
    expect(wrapper.get('[data-testid="hud-day"]').text()).toContain('1');
    expect(wrapper.get('[data-testid="hud-weather"]').text()).toContain('SUNNY');

    hud.syncMeta({ dayIndex: 9, weather: 'SNOWY', energy: 35, masteryAvg: 72 });
    await flushPromises();
    expect(wrapper.get('[data-testid="hud-day"]').text()).toContain('9');
    expect(wrapper.get('[data-testid="hud-weather"]').text()).toContain('SNOWY');
    const energyBar = wrapper.get('[data-testid="hud-energy"]');
    expect(energyBar.attributes('aria-valuenow')).toBe('35');
    const masteryBar = wrapper.get('[data-testid="hud-mastery"]');
    expect(masteryBar.attributes('aria-valuenow')).toBe('72');
  });

  it('topBar = hidden 时顶部状态条不渲染，但底部快捷栏不受影响', async () => {
    const hud = useHudStore();
    const wrapper = mountShell();
    hud.setVisibility('top', 'hidden');
    await flushPromises();
    expect(wrapper.find('[data-testid="hud-top"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="hud-bottom"]').exists()).toBe(true);
    expect(wrapper.findAll('button.hud-slot')).toHaveLength(HUD_SLOT_BUTTONS);
  });

  it('依次点击不同 slot：互斥单选，只有最后一个按钮 active', async () => {
    const hud = useHudStore();
    const wrapper = mountShell();
    await wrapper.get('[data-testid="hud-slot-dialog"]').trigger('click');
    await wrapper.get('[data-testid="hud-slot-skill"]').trigger('click');
    expect(hud.activeSlot).toBe('skill');
    expect(
      wrapper.get('[data-testid="hud-slot-dialog"]').classes(),
    ).not.toContain('hud-slot--active');
    expect(
      wrapper.get('[data-testid="hud-slot-skill"]').classes(),
    ).toContain('hud-slot--active');
  });

  it('小镇内单按 A 不跳转无尽远征（A 留给左移）', async () => {
    const push = vi.spyOn(router, 'push');
    mountShell('/world');
    window.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'a', bubbles: true, cancelable: true }),
    );
    expect(push).not.toHaveBeenCalledWith('/expedition');
    push.mockRestore();
  });

  it('Shift+A 打开无尽远征', async () => {
    const push = vi.spyOn(router, 'push');
    mountShell('/workshop');
    window.dispatchEvent(
      new KeyboardEvent('keydown', {
        key: 'A',
        shiftKey: true,
        bubbles: true,
        cancelable: true,
      }),
    );
    expect(push).toHaveBeenCalledWith('/expedition');
    push.mockRestore();
  });

  it('对话历史面板渲染当前 NPC 节点（名字 / 文本 / 提示）', async () => {
    // R-12 主题化：对话历史面板由内联黑字改为像素主题（CSS 变量 --pyt-text-*），
    // 不再有内联 color:#111。此处改为校验对话节点内容正确渲染。
    const hud = useHudStore();
    const dialog = useDialogStore();
    dialog.npcKey = 'mayor_py';
    dialog.npcName = '镇长 Py';
    dialog.tree = {
      npcKey: 'mayor_py',
      rootNodeId: 'greet',
      nodes: {
        greet: {
          id: 'greet',
          text: '欢迎来到 Pythontopia',
          conditions: [],
          choices: [],
        },
      },
    };
    dialog.currentNodeId = 'greet';
    dialog.history = ['greet'];

    const wrapper = mount(QuickslotPanelHost);
    hud.toggleSlot('dialog');
    await flushPromises();

    const npcLine = wrapper.get('.qslot-dialog-npc-line');
    expect(npcLine.text()).toContain('镇长 Py');
    expect(npcLine.get('b').text()).toBe('镇长 Py');
    expect(wrapper.get('.qslot-dialog-text').text()).toContain('欢迎来到 Pythontopia');
    expect(wrapper.find('.qslot-hint').exists()).toBe(true);
  });
});
