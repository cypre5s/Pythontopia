import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it } from 'vitest';
import HudShell from '@/components/hud/HudShell.vue';
import { useHudStore, type HudQuickSlot } from '@stores/hud';

const SLOT_KEYS: readonly HudQuickSlot[] = [
  'quest',
  'dialog',
  'inventory',
  'map',
  'skill',
];

function mountShell() {
  return mount(HudShell);
}

describe('HudShell', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('挂载后渲染 5 个 quickslot 按钮（quest/dialog/inventory/map/skill）', () => {
    const wrapper = mountShell();
    for (const key of SLOT_KEYS) {
      const btn = wrapper.find(`[data-testid="hud-slot-${key}"]`);
      expect(btn.exists()).toBe(true);
      expect(btn.element.tagName).toBe('BUTTON');
    }
    expect(wrapper.findAll('button.hud-slot')).toHaveLength(SLOT_KEYS.length);
  });

  it('顶部状态条按 store.meta 渲染：天气 / Day / 时段 / 季节 / 金币', () => {
    const hud = useHudStore();
    hud.syncMeta({
      weather: 'RAINY',
      dayIndex: 12,
      timeOfDay: 'EVENING',
      season: 'AUTUMN',
      coin: 88,
    });
    const wrapper = mountShell();
    expect(wrapper.get('[data-testid="hud-weather"]').text()).toContain('RAINY');
    expect(wrapper.get('[data-testid="hud-day"]').text()).toContain('12');
    expect(wrapper.get('[data-testid="hud-time"]').text()).toContain('EVENING');
    expect(wrapper.get('[data-testid="hud-season"]').text()).toContain('AUTUMN');
    expect(wrapper.get('[data-testid="hud-coin"]').text()).toContain('88');
  });

  it('点击 quest 按钮：store.activeSlot === "quest" 且按钮带 active class + aria-pressed', async () => {
    const hud = useHudStore();
    const wrapper = mountShell();
    const btn = wrapper.get('[data-testid="hud-slot-quest"]');
    await btn.trigger('click');
    expect(hud.activeSlot).toBe('quest');
    expect(btn.classes()).toContain('hud-slot--active');
    expect(btn.attributes('aria-pressed')).toBe('true');
  });

  it('再次点击同一按钮：activeSlot 回到 null 并清除 active class', async () => {
    const hud = useHudStore();
    const wrapper = mountShell();
    const btn = wrapper.get('[data-testid="hud-slot-inventory"]');
    await btn.trigger('click');
    expect(hud.activeSlot).toBe('inventory');
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
    expect(wrapper.findAll('button.hud-slot')).toHaveLength(SLOT_KEYS.length);
  });

  it('依次点击不同 slot：互斥单选，只有最后一个按钮 active', async () => {
    const hud = useHudStore();
    const wrapper = mountShell();
    await wrapper.get('[data-testid="hud-slot-quest"]').trigger('click');
    await wrapper.get('[data-testid="hud-slot-map"]').trigger('click');
    expect(hud.activeSlot).toBe('map');
    expect(
      wrapper.get('[data-testid="hud-slot-quest"]').classes(),
    ).not.toContain('hud-slot--active');
    expect(
      wrapper.get('[data-testid="hud-slot-map"]').classes(),
    ).toContain('hud-slot--active');
  });
});
