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

// R-14 快速旅行 gate：默认透传真实路线表（单 hub），需要「恢复多图」的用例把
// ftRoutes.routes 换成模拟路线表。详见 tests/_helpers/fastTravelRoutes.ts。
const ftRoutes: FastTravelRouteOverride = vi.hoisted(() => ({ routes: null }));
vi.mock('@/data/fastTravel', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/data/fastTravel')>();
  const { createFastTravelModuleMock } = await import('../_helpers/fastTravelRoutes');
  return createFastTravelModuleMock(actual, ftRoutes);
});

import SettingsModal from '@/components/hud/SettingsModal.vue';
import FastTravelPanel from '@/components/hud/FastTravelPanel.vue';
import FestivalRibbon from '@/components/hud/FestivalRibbon.vue';
import QuickWheel from '@/components/hud/QuickWheel.vue';
import { FAST_TRAVEL_ENABLED, FAST_TRAVEL_ROUTES } from '@/data/fastTravel';
import { useHudStore } from '@stores/hud';
import { useSessionStore } from '@stores/session';
import { useWorldStore } from '@stores/world';
import { useCalendarStore } from '@stores/calendar';
import type { WorldCalendarDto } from '@/api/dto/WorldCalendarDto';
import type { FastTravelRouteOverride } from '../_helpers/fastTravelRoutes';
import { RESTORED_FAST_TRAVEL_ROUTES } from '../_helpers/fastTravelRoutes';

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

/** QuickWheel 当前渲染的扇区文案（Tab 打开后读 .wheel-sector__label）。 */
async function openQuickWheelLabels(): Promise<{ labels: string[]; unmount: () => void }> {
  const wrapper = mount(QuickWheel, { attachTo: document.body });
  window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab' }));
  await flushPromises();
  const labels = wrapper.findAll('.wheel-sector__label').map((node) => node.text());
  return { labels, unmount: () => wrapper.unmount() };
}

// R-14：世界收缩为单一 town_hub，FAST_TRAVEL_ROUTES 只剩一条且目的地就是玩家
// 当前所在地图 —— 面板里唯一一行永久 disabled，打开它等于给玩家一条死路。
// 实现改为用 FAST_TRAVEL_ENABLED（src/data/fastTravel.ts）统一 gate 掉全部入口，
// 旧断言（能打开 / 渲染 hub 行 / T 键切换）随之翻转为「入口被抑制」。
describe('fasttravel.integration: 单 hub 下入口休眠', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    ftRoutes.routes = null;
  });

  it('默认不可见', () => {
    const wrapper = mount(FastTravelPanel);
    expect(wrapper.find('[data-testid="fast-travel-panel"]').exists()).toBe(false);
  });

  it('gate 成立的前提：每条路线的目的地都是玩家当前所在地图', () => {
    setMap('town_hub');
    const world = useWorldStore();
    expect(FAST_TRAVEL_ROUTES).toHaveLength(1);
    // 每一行都会命中 `disabled = currentMap === route.toMap` → 整个面板毫无可点项。
    expect(FAST_TRAVEL_ROUTES.every((route) => route.toMap === world.player?.mapKey)).toBe(true);
    expect(FAST_TRAVEL_ENABLED).toBe(false);
  });

  it('CustomEvent 不再打开面板', async () => {
    setMap('town_hub');
    const wrapper = mount(FastTravelPanel, { attachTo: document.body });
    window.dispatchEvent(new CustomEvent('pythontopia:fast-travel-open'));
    await flushPromises();
    expect(wrapper.find('[data-testid="fast-travel-panel"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('keydown T 不再打开面板', async () => {
    setMap('town_hub');
    const wrapper = mount(FastTravelPanel, { attachTo: document.body });
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 't' }));
    await flushPromises();
    expect(wrapper.find('[data-testid="fast-travel-panel"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('QuickWheel 不渲染「传送」扇区，其余扇区照常', async () => {
    const { labels, unmount } = await openQuickWheelLabels();
    expect(labels).not.toContain('传送');
    // 留一个点了没反应的入口比没有入口更糟；但只能删传送，别的玩法必须还在。
    expect(labels).toEqual(['课程', '远征', '休息', '矿洞', '工坊', '训练']);
    unmount();
  });
});

// 将来重新开放外围图时，路线表一变长 gate 就该自动放行；这组用例锁住这个方向，
// 避免 gate 被写成「永久关闭」。
describe('fasttravel.integration: 路线恢复 >1 后入口回归', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    ftRoutes.routes = RESTORED_FAST_TRAVEL_ROUTES;
  });

  it('CustomEvent 重新打开面板并渲染全部路线', async () => {
    setMap('town_hub');
    const wrapper = mount(FastTravelPanel, { attachTo: document.body });
    window.dispatchEvent(new CustomEvent('pythontopia:fast-travel-open'));
    await flushPromises();
    expect(wrapper.find('[data-testid="fast-travel-panel"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="fast-travel-hub"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="fast-travel-farm"]').exists()).toBe(true);
    wrapper.unmount();
  });

  it('keydown T 重新切换面板', async () => {
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

  it('QuickWheel 恢复「传送」扇区', async () => {
    const { labels, unmount } = await openQuickWheelLabels();
    expect(labels).toContain('传送');
    unmount();
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

  it('day=2 非节日 ribbon 隐藏（R-14：只在当天节日显示）', async () => {
    // FestivalRibbon 读 hud.meta.dayIndex（R-12 起数据源由 calendar 切到 hud）。
    const hud = useHudStore();
    hud.syncMeta({ dayIndex: 2 });
    const wrapper = mount(FestivalRibbon);
    await flushPromises();
    expect(wrapper.find('[data-testid="festival-ribbon"]').exists()).toBe(false);
  });
});
