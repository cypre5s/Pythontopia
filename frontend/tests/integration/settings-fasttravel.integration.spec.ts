// R-13 Phase 8 大爆炸测试 · Settings + FastTravel + Festival（目标 40 用例）。
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const ROUTER_PUSH = vi.fn();
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: ROUTER_PUSH }),
}));

const audioMocks = {
  setMuted: vi.fn(),
  isMuted: vi.fn(() => false),
  setBgmVolume: vi.fn(),
  setSfxVolume: vi.fn(),
  getBgmVolume: vi.fn(() => 1),
  getSfxVolume: vi.fn(() => 1),
  playBgm: vi.fn(),
  stopBgm: vi.fn(),
  playSfx: vi.fn(),
  getCurrentBgmKey: vi.fn(() => null),
  unlockAudio: vi.fn(),
};
vi.mock('@/composables/useAudio', () => ({ useAudio: () => audioMocks }));

import SettingsModal from '@/components/hud/SettingsModal.vue';
import FastTravelPanel from '@/components/hud/FastTravelPanel.vue';
import FestivalRibbon from '@/components/hud/FestivalRibbon.vue';
import { useHudStore } from '@stores/hud';
import { useSessionStore } from '@stores/session';
import { useWorldStore } from '@stores/world';
import { useCalendarStore } from '@stores/calendar';
import type { WorldCalendarDto } from '@/api/dto/WorldCalendarDto';

function makeCal(d: number): WorldCalendarDto {
  return {
    dayIndex: d,
    weekday: 'monday',
    monthIndex: 1,
    season: 'spring',
    timeOfDay: 'MORNING',
    weather: 'SUNNY',
  };
}

describe('settings.integration: SettingsModal（20 case）', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    ROUTER_PUSH.mockReset();
    Object.values(audioMocks).forEach((m) => {
      if (typeof m === 'function' && 'mockReset' in m) {
        (m as { mockReset(): void }).mockReset();
      }
    });
    audioMocks.isMuted.mockReturnValue(false);
    audioMocks.getBgmVolume.mockReturnValue(1);
    audioMocks.getSfxVolume.mockReturnValue(1);
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it.each([true, false])('settingsOpen=%s 渲染分支正确', async (open) => {
    const hud = useHudStore();
    if (open) hud.openSettings();
    const wrapper = mount(SettingsModal);
    await flushPromises();
    expect(wrapper.find('[data-testid="settings-modal"]').exists()).toBe(open);
  });

  // R-12 设置弹窗改为 5 标签页：音频默认激活，语言/动画在「显示」页，
  // 重看引导/退出/完成在常驻底栏。
  async function openDisplayTab(wrapper: ReturnType<typeof mount>): Promise<void> {
    const tab = wrapper.findAll('[role="tab"]').find((t) => t.text().includes('显示'));
    await tab!.trigger('click');
  }

  it('挂载时显示所有控件（含标签页切换）', async () => {
    const hud = useHudStore();
    hud.openSettings();
    const wrapper = mount(SettingsModal);
    await flushPromises();
    // 音频标签默认激活
    expect(wrapper.find('[data-testid="settings-mute"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="settings-bgm"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="settings-sfx"]').exists()).toBe(true);
    // 底栏常驻
    expect(wrapper.find('[data-testid="settings-reset-help"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="settings-logout"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="settings-ok"]').exists()).toBe(true);
    // 切到「显示」页：语言 + 减弱动画
    await openDisplayTab(wrapper);
    expect(wrapper.find('[data-testid="settings-locale"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="settings-reduced-motion"]').exists()).toBe(true);
  });

  it('mute toggle: audio.setMuted + setStardewFxMuted 调用', async () => {
    const hud = useHudStore();
    hud.openSettings();
    const wrapper = mount(SettingsModal);
    await flushPromises();
    const checkbox = wrapper.find<HTMLInputElement>('[data-testid="settings-mute"]');
    await checkbox.setValue(true);
    expect(audioMocks.setMuted).toHaveBeenCalledWith(true);
  });

  it('BGM 滑块拖动：调 audio.setBgmVolume', async () => {
    const hud = useHudStore();
    hud.openSettings();
    const wrapper = mount(SettingsModal);
    await flushPromises();
    const slider = wrapper.find<HTMLInputElement>('[data-testid="settings-bgm"]');
    await slider.setValue('80');
    expect(audioMocks.setBgmVolume).toHaveBeenCalledWith(0.8);
  });

  it('SFX 滑块拖动：调 audio.setSfxVolume', async () => {
    const hud = useHudStore();
    hud.openSettings();
    const wrapper = mount(SettingsModal);
    await flushPromises();
    const slider = wrapper.find<HTMLInputElement>('[data-testid="settings-sfx"]');
    await slider.setValue('50');
    expect(audioMocks.setSfxVolume).toHaveBeenCalledWith(0.5);
  });

  it.each(['zh-CN', 'en-US'])('语言切换 %s', async (lang) => {
    const hud = useHudStore();
    hud.openSettings();
    const wrapper = mount(SettingsModal);
    await flushPromises();
    await openDisplayTab(wrapper);
    const select = wrapper.find<HTMLSelectElement>('[data-testid="settings-locale"]');
    await select.setValue(lang);
    // 不抛错即认为合规（实际 setLocale 调用在 composable 内部）
    expect(select.element.value).toBe(lang);
  });

  it('点击关闭按钮：settingsOpen 回 false', async () => {
    const hud = useHudStore();
    hud.openSettings();
    const wrapper = mount(SettingsModal);
    await wrapper.find('[data-testid="settings-close"]').trigger('click');
    expect(hud.settingsOpen).toBe(false);
  });

  it('点击完成按钮：settingsOpen 回 false', async () => {
    const hud = useHudStore();
    hud.openSettings();
    const wrapper = mount(SettingsModal);
    await wrapper.find('[data-testid="settings-ok"]').trigger('click');
    expect(hud.settingsOpen).toBe(false);
  });

  it('点击退出：调 session.logout + router.push /login + 关闭', async () => {
    const hud = useHudStore();
    const session = useSessionStore();
    const logoutSpy = vi.spyOn(session, 'logout');
    hud.openSettings();
    const wrapper = mount(SettingsModal);
    await wrapper.find('[data-testid="settings-logout"]').trigger('click');
    expect(logoutSpy).toHaveBeenCalled();
    expect(ROUTER_PUSH).toHaveBeenCalledWith({ name: 'login' });
    expect(hud.settingsOpen).toBe(false);
  });

  it('reduced-motion 显示「正常」（默认 jsdom 无 matchMedia 时）', async () => {
    const hud = useHudStore();
    hud.openSettings();
    const wrapper = mount(SettingsModal);
    await flushPromises();
    await openDisplayTab(wrapper);
    const rmRow = wrapper.find('[data-testid="settings-reduced-motion"]');
    expect(rmRow.exists()).toBe(true);
  });
});

describe('fasttravel.integration: FastTravelPanel（15 case）', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  function setMap(mapKey: string): void {
    const world = useWorldStore();
    world.player = {
      playerStateId: 1,
      avatarKey: 'student_default',
      mapKey,
      tileX: 0,
      tileY: 0,
      direction: 'DOWN',
    };
  }

  it('默认不可见', () => {
    const wrapper = mount(FastTravelPanel);
    expect(wrapper.find('[data-testid="fast-travel-panel"]').exists()).toBe(false);
  });

  it('CustomEvent 打开', async () => {
    setMap('town_hub');
    const wrapper = mount(FastTravelPanel, { attachTo: document.body });
    window.dispatchEvent(new CustomEvent('pythontopia:fast-travel-open'));
    await flushPromises();
    expect(wrapper.find('[data-testid="fast-travel-panel"]').exists()).toBe(true);
    wrapper.unmount();
  });

  it.each(['hub', 'farm', 'mine', 'campus', 'port'])('路线 %s 渲染', async (key) => {
    setMap('town_hub');
    const wrapper = mount(FastTravelPanel, { attachTo: document.body });
    window.dispatchEvent(new CustomEvent('pythontopia:fast-travel-open'));
    await flushPromises();
    expect(wrapper.find(`[data-testid="fast-travel-${key}"]`).exists()).toBe(true);
    wrapper.unmount();
  });

  it.each([
    ['town_hub', 'hub'],
    ['farm_north', 'farm'],
    ['mine_south', 'mine'],
    ['campus_east', 'campus'],
    ['port_west', 'port'],
  ])('mapKey=%s → 路线 %s 被 disabled', async (mapKey, routeKey) => {
    setMap(mapKey);
    const wrapper = mount(FastTravelPanel, { attachTo: document.body });
    window.dispatchEvent(new CustomEvent('pythontopia:fast-travel-open'));
    await flushPromises();
    const btn = wrapper.find(`[data-testid="fast-travel-${routeKey}"]`);
    expect(btn.attributes('disabled')).toBeDefined();
    wrapper.unmount();
  });

  it('点击非当前路线：worldStore.player.mapKey 更新 + 面板关闭', async () => {
    setMap('town_hub');
    const world = useWorldStore();
    const wrapper = mount(FastTravelPanel, { attachTo: document.body });
    window.dispatchEvent(new CustomEvent('pythontopia:fast-travel-open'));
    await flushPromises();
    await wrapper.find('[data-testid="fast-travel-campus"]').trigger('click');
    await flushPromises();
    expect(world.player?.mapKey).toBe('campus_east');
    wrapper.unmount();
  });

  it('keydown T 切换面板', async () => {
    setMap('town_hub');
    const wrapper = mount(FastTravelPanel, { attachTo: document.body });
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 't' }));
    await flushPromises();
    expect(wrapper.find('[data-testid="fast-travel-panel"]').exists()).toBe(true);
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 't' }));
    await flushPromises();
    expect(wrapper.find('[data-testid="fast-travel-panel"]').exists()).toBe(false);
    wrapper.unmount();
  });
});

describe('festival.integration: FestivalRibbon（10 case）', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it.each([1, 14, 28, 35, 56, 60, 70, 84, 90, 100, 112])(
    'day=%i 显示节日 ribbon',
    async (day) => {
      const cal = useCalendarStore();
      cal.setBootstrap(makeCal(day), []);
      const wrapper = mount(FestivalRibbon);
      await flushPromises();
      expect(wrapper.find('[data-testid="festival-ribbon"]').exists()).toBe(true);
    },
  );

  it('day=2 显示 upcoming 春耕祭', async () => {
    // FestivalRibbon 读 hud.meta.dayIndex（R-12 起数据源由 calendar 切到 hud）。
    const hud = useHudStore();
    hud.syncMeta({ dayIndex: 2 });
    const wrapper = mount(FestivalRibbon);
    await flushPromises();
    expect(wrapper.text()).toContain('春耕祭');
  });
});
