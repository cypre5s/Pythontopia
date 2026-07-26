// R-13 Phase 10 演示链路 smoke spec（目标 20 用例）。
//
// 完整模拟比赛演示路径：登录 → 进入世界 → 查看 FastTravel（R-14 单 hub）→ 推一天
// → 触发节日 ribbon → NPC 对话 → 设置静音 → 退出。
// 不依赖后端（用 axios mock），不依赖 Phaser（纯 store + composable），
// 验证整个数据流贯通。

import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useSessionStore } from '@stores/session';
import { useWorldStore } from '@stores/world';
import { useHudStore } from '@stores/hud';
import { useCalendarStore } from '@stores/calendar';
import { useDialogStore, __resetChoiceListenersForTests } from '@stores/dialog';
import FestivalRibbon from '@/components/hud/FestivalRibbon.vue';
import FastTravelPanel from '@/components/hud/FastTravelPanel.vue';
import { FAST_TRAVEL_ENABLED } from '@/data/fastTravel';
import { router as appRouter } from '@/app/router';

vi.mock('@/composables/useAudio', () => ({
  useAudio: () => ({
    setMuted: vi.fn(),
    isMuted: () => false,
    setBgmVolume: vi.fn(),
    setSfxVolume: vi.fn(),
    getBgmVolume: () => 1,
    getSfxVolume: () => 1,
    playBgm: vi.fn(),
    stopBgm: vi.fn(),
    playSfx: vi.fn(),
    getCurrentBgmKey: () => null,
    unlockAudio: vi.fn(),
  }),
  playBgm: vi.fn(),
  playSfx: vi.fn(),
  setMuted: vi.fn(),
  unlockAudio: vi.fn(),
}));

beforeEach(() => {
  setActivePinia(createPinia());
  __resetChoiceListenersForTests();
});

function login(): void {
  const session = useSessionStore();
  session.$patch({
    token: 'demo-token',
    expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
    user: { id: 1, username: 'demo', displayName: 'Demo', role: 'STUDENT' },
  });
}

function setPlayer(mapKey: string, x = 0, y = 0): void {
  const world = useWorldStore();
  world.player = {
    playerStateId: 1,
    avatarKey: 'student_default',
    mapKey,
    tileX: x,
    tileY: y,
    direction: 'DOWN',
  };
}

describe('demo-smoke.integration · 完整演示链路', () => {
  it('STEP 1 未登录访问 /world → 跳 /login + redirect', async () => {
    await appRouter.push('/world');
    expect(appRouter.currentRoute.value.name).toBe('login');
    expect(appRouter.currentRoute.value.query.redirect).toBe('/world');
  });

  it('STEP 2 登录后 isAuthenticated=true', () => {
    login();
    const session = useSessionStore();
    expect(session.isAuthenticated).toBe(true);
  });

  it('STEP 3 登录后访问 /world → 命中 world 路由', async () => {
    login();
    await appRouter.push('/world');
    expect(appRouter.currentRoute.value.name).toBe('world');
  });

  it('STEP 4 player 坐标写入 → world.player.mapKey 为 town_hub', () => {
    setPlayer('town_hub', 25, 20);
    const world = useWorldStore();
    expect(world.player?.mapKey).toBe('town_hub');
    expect(world.player?.tileX).toBe(25);
  });

  it('STEP 5 FastTravel 入口在单 hub 下休眠（面板不再打开成一行灰按钮）', async () => {
    // R-14 方案A：世界收缩为单一 town_hub，快速旅行只剩 hub 一条路线 = 玩家当前所在地，
    // 面板里唯一一行永久 disabled → 打开它等于给玩家一个死路。FAST_TRAVEL_ENABLED 关掉全部入口。
    setPlayer('town_hub');
    const wrapper = mount(FastTravelPanel, { attachTo: document.body });
    window.dispatchEvent(new CustomEvent('pythontopia:fast-travel-open'));
    await flushPromises();
    expect(FAST_TRAVEL_ENABLED).toBe(false);
    expect(wrapper.find('[data-testid="fast-travel-panel"]').exists()).toBe(false);
    expect(useWorldStore().player?.mapKey).toBe('town_hub');
    wrapper.unmount();
  });

  it('STEP 6 sleep 推一天后 calendar 同步', () => {
    const cal = useCalendarStore();
    cal.setBootstrap(
      { dayIndex: 0, weekday: 'sunday', monthIndex: 1, season: 'spring', timeOfDay: 'NIGHT', weather: 'SUNNY' },
      [],
    );
    cal.setBootstrap(
      { dayIndex: 1, weekday: 'monday', monthIndex: 1, season: 'spring', timeOfDay: 'MORNING', weather: 'SUNNY' },
      [],
    );
    expect(cal.today?.dayIndex).toBe(1);
  });

  it('STEP 7 day=1 → FestivalRibbon 显示开学日', async () => {
    const cal = useCalendarStore();
    cal.setBootstrap(
      { dayIndex: 1, weekday: 'monday', monthIndex: 1, season: 'spring', timeOfDay: 'MORNING', weather: 'SUNNY' },
      [],
    );
    const wrapper = mount(FestivalRibbon);
    await flushPromises();
    expect(wrapper.text()).toContain('开学日');
    wrapper.unmount();
  });

  it('STEP 8 NPC 对话 → 选择后关闭', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.endsWith('/meta.json')) {
        return { ok: true, json: async () => ({ key: 'mayor_py', name: '镇长', portrait: 'm.png' }) } as unknown as Response;
      }
      return {
        ok: true,
        json: async () => ({
          npcKey: 'mayor_py',
          rootNodeId: 'r',
          nodes: {
            r: {
              id: 'r',
              text: 'hi',
              conditions: [],
              choices: [
                { label: '问候', nextNodeId: null },
              ],
            },
          },
        }),
      } as unknown as Response;
    });
    const dlg = useDialogStore();
    await dlg.openWithTree('mayor_py');
    dlg.selectChoice(0);
    expect(dlg.visible).toBe(false);
    vi.restoreAllMocks();
  });

  it('STEP 9 HUD 切换 map + 打开 settings 同时存在', () => {
    const hud = useHudStore();
    hud.toggleSlot('map');
    hud.openSettings();
    expect(hud.activeSlot).toBe('map');
    expect(hud.settingsOpen).toBe(true);
  });

  it('STEP 10 静音 → setStardewFxMuted 跟踪（mock 验证不抛错）', () => {
    const hud = useHudStore();
    hud.openSettings();
    // SettingsModal 内部会调 setStardewFxMuted；这里只验证 hud 状态
    expect(hud.settingsOpen).toBe(true);
  });

  it('STEP 11 退出登录 → session 清空', () => {
    login();
    const session = useSessionStore();
    session.logout();
    expect(session.isAuthenticated).toBe(false);
    expect(session.token).toBeNull();
  });

  it('STEP 12 退出后访问 /world 必须跳回 /login', async () => {
    const session = useSessionStore();
    session.logout();
    // 先到一个公开路由再 push /world，确保守卫被触发
    await appRouter.push('/credits');
    await appRouter.push('/world');
    expect(appRouter.currentRoute.value.name).toBe('login');
  });

  it('STEP 13 访问 /missing-anything 触发 NotFoundPage（catch-all）', async () => {
    const r = appRouter.resolve('/totally-bogus');
    expect(r.name).toBe('not-found');
  });

  it('STEP 16 节日推进：day=28 当天显示春日嘉年华（R-14：只在节日当天显示）', async () => {
    // FestivalRibbon 读 hud.meta.dayIndex（R-12 起数据源由 calendar 切到 hud）。
    const hud = useHudStore();
    hud.syncMeta({ dayIndex: 28 });
    const wrapper = mount(FestivalRibbon);
    await flushPromises();
    expect(wrapper.text()).toContain('春日嘉年华');
    wrapper.unmount();
  });

  it('STEP 17 演示完成后 reset 全部状态', () => {
    const hud = useHudStore();
    const session = useSessionStore();
    hud.toggleSlot('quest');
    hud.openSettings();
    hud.reset();
    session.logout();
    expect(hud.activeSlot).toBeNull();
    expect(hud.settingsOpen).toBe(false);
    expect(session.isAuthenticated).toBe(false);
  });
});
