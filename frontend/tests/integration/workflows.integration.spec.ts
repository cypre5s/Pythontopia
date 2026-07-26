// R-13 Phase 9 复杂工作流集成测试（目标 40 用例）。
//
// 覆盖玩家端到端流程的多步骤组合：
//   - 登录 → 路由跳转 → bootstrap → activeSlot/HUD 联动
//   - sleep 推一天 → festival 触发 → calendar 同步
//   - 快速旅行 → minimap 数据切换
//   - NPC 对话 open/close → SFX → dialog store 状态机
//   - settings open → toggle slot → 设置不抢占 activeSlot
//   - reset 状态机
//   - 404 → 倒计时 → 跳回 /world 联动

import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import { afterEach, beforeEach, describe, expect, it, vi, type MockInstance } from 'vitest';
import { defineComponent, h } from 'vue';
import { router as appRouter } from '@/app/router';
import { useSessionStore } from '@stores/session';
import { useHudStore } from '@stores/hud';
import { useCalendarStore } from '@stores/calendar';
import { useWorldStore } from '@stores/world';
import { useDialogStore } from '@stores/dialog';
import {
  __resetMinimapCacheForTest,
  useWorldMinimap,
  type UseWorldMinimapReturn,
} from '@/composables/useWorldMinimap';
import HudShell from '@/components/hud/HudShell.vue';
import FestivalRibbon from '@/components/hud/FestivalRibbon.vue';
import type { WorldCalendarDto } from '@/api/dto/WorldCalendarDto';

const ROUTER_PUSH = vi.fn();
vi.mock('vue-router', async () => {
  const actual = await vi.importActual<typeof import('vue-router')>('vue-router');
  return {
    ...actual,
    useRouter: () => ({ push: ROUTER_PUSH }),
  };
});

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

function makeRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/world', name: 'world', component: { template: '<div />' } },
      { path: '/library', name: 'library', component: { template: '<div />' } },
    ],
  });
}

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

function loginSession(): void {
  const session = useSessionStore();
  session.$patch({
    token: 'wf-token',
    expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
    user: { id: 1, username: 'wf-user', displayName: 'WF', role: 'STUDENT' },
  });
}

describe('workflows.integration · 登录态 + 路由', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    ROUTER_PUSH.mockReset();
  });

  it('登录 → 已登录访问 /login → 跳 /world', async () => {
    loginSession();
    await appRouter.push('/login');
    expect(appRouter.currentRoute.value.path).toBe('/world');
  });

  it('未登录访问 /workshop → 跳 /login + redirect query', async () => {
    await appRouter.push('/workshop');
    expect(appRouter.currentRoute.value.name).toBe('login');
    expect(appRouter.currentRoute.value.query.redirect).toBe('/workshop');
  });

  it('登录 → bootstrap 数据 → world store player 写入', () => {
    loginSession();
    const world = useWorldStore();
    world.player = {
      playerStateId: 1,
      avatarKey: 'student_default',
      mapKey: 'town_hub',
      tileX: 25,
      tileY: 20,
      direction: 'DOWN',
    };
    expect(world.player.mapKey).toBe('town_hub');
  });

  it('登录 → logout → token 清空 → isAuthenticated=false', () => {
    const session = useSessionStore();
    loginSession();
    expect(session.isAuthenticated).toBe(true);
    session.logout();
    expect(session.isAuthenticated).toBe(false);
    expect(session.token).toBeNull();
  });
});

describe('workflows.integration · HUD + Settings + 工具', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('打开 map slot → 再打开 settings → 两个都开 + 互不影响', () => {
    const hud = useHudStore();
    hud.toggleSlot('map');
    hud.openSettings();
    expect(hud.activeSlot).toBe('map');
    expect(hud.settingsOpen).toBe(true);
  });

  it('打开 settings → 再切 slot → activeSlot 切换 settings 仍开', () => {
    const hud = useHudStore();
    hud.openSettings();
    hud.toggleSlot('map');
    expect(hud.activeSlot).toBe('map');
    expect(hud.settingsOpen).toBe(true);
  });

  it('Esc 优先关 settings 再关 slot', async () => {
    const hud = useHudStore();
    const wrapper = mount(HudShell, { global: { plugins: [makeRouter()] }, attachTo: document.body });
    hud.toggleSlot('quest');
    hud.openSettings();
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(hud.settingsOpen).toBe(false);
    expect(hud.activeSlot).toBe('quest');
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(hud.activeSlot).toBeNull();
    wrapper.unmount();
  });

  it('reset 清空所有 HUD 状态', () => {
    const hud = useHudStore();
    hud.toggleSlot('skill');
    hud.openSettings();
    hud.syncMeta({ energy: 50 });
    hud.setVisibility('top', 'hidden');
    hud.reset();
    expect(hud.activeSlot).toBeNull();
    expect(hud.settingsOpen).toBe(false);
    expect(hud.meta.energy).toBe(100);
    expect(hud.topBar).toBe('shown');
  });

  it.each([
    ['quest', 'isQuestOpen'],
    ['dialog', 'isDialogOpen'],
    ['map', 'isMapOpen'],
    ['skill', 'isSkillOpen'],
  ] as const)('slot %s 切到 → getter %s 为 true', (slot, getter) => {
    const hud = useHudStore();
    hud.toggleSlot(slot);
    expect(hud[getter]).toBe(true);
  });
});

describe('workflows.integration · Calendar + Festival 联动', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it.each([1, 14, 28, 35, 56, 60, 70, 84, 90, 100, 112])(
    '推 day=%i → festival ribbon 显示',
    async (d) => {
      const cal = useCalendarStore();
      cal.setBootstrap(makeCal(d), []);
      const wrapper = mount(FestivalRibbon);
      await flushPromises();
      expect(wrapper.find('[data-testid="festival-ribbon"]').exists()).toBe(true);
      wrapper.unmount();
    },
  );

  it('day=15 非节日 → ribbon 隐藏（R-14：只在当天节日显示）', async () => {
    // FestivalRibbon 读 hud.meta.dayIndex（R-12 起数据源由 calendar 切到 hud）。
    const hud = useHudStore();
    hud.syncMeta({ dayIndex: 15 });
    const wrapper = mount(FestivalRibbon);
    await flushPromises();
    expect(wrapper.find('[data-testid="festival-ribbon"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('Sleep 推一天 → day+1 → calendar 同步', () => {
    const cal = useCalendarStore();
    cal.setBootstrap(makeCal(10), []);
    cal.setBootstrap(makeCal(11), []);
    expect(cal.today?.dayIndex).toBe(11);
  });

  it('pushEvents 增量追加新事件', () => {
    const cal = useCalendarStore();
    cal.setBootstrap(makeCal(1), []);
    cal.pushEvents([
      { id: 100, eventType: 'NPC_GREET', payload: {}, isProcessed: false, createdAt: '' },
    ]);
    expect(cal.events.length).toBe(1);
    cal.pushEvents([
      { id: 100, eventType: 'NPC_GREET', payload: {}, isProcessed: false, createdAt: '' },
      { id: 101, eventType: 'KC_UP', payload: {}, isProcessed: false, createdAt: '' },
    ]);
    expect(cal.events.length).toBe(2);
  });
});

// R-14 方案A：世界收缩为单一 town_hub，外围图（mine_south / farm_north /
// campus_east / port_west）从主线与导航中弃用。但旧存档里的 player.mapKey 仍会
// 带着这些 key 从后端回来，必须被归一到 town_hub，否则 minimap 会整块消失。
//
// 这组用例原先绕 FastTravelPanel 验证（点 hub 行把 player 写回 town_hub）；
// 单 hub 下快速旅行入口已整体休眠（唯一路线就是玩家所在地 = 死路，见
// src/data/fastTravel.ts 的 FAST_TRAVEL_ENABLED），面板不再挂出来。
// 真正有价值的「旧存档别名归一」能力现在活在 useWorldMinimap 的别名表里，
// 故改为直接验证它，而不是绕一个已被 gate 掉的组件。
describe('workflows.integration · 旧存档地图别名归一到 town_hub', () => {
  let fetchSpy: MockInstance<typeof globalThis.fetch>;

  beforeEach(() => {
    setActivePinia(createPinia());
    __resetMinimapCacheForTest();
    fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url =
        typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url;
      if (url.endsWith('/assets/maps/index.json')) {
        return {
          ok: true,
          json: async () => ({ maps: [{ key: 'town_hub', label: 'Pythontopia 小镇' }] }),
        } as unknown as Response;
      }
      if (url.endsWith('/assets/maps/town_hub.json')) {
        return {
          ok: true,
          json: async () => ({
            width: 2,
            height: 2,
            tilewidth: 16,
            layers: [
              { type: 'tilelayer', name: 'ground', width: 2, height: 2, data: [1, 1, 1, 1] },
            ],
          }),
        } as unknown as Response;
      }
      // 外围图 JSON 已随 R-14 删除：真去拉必然 404。
      return { ok: false, json: async () => ({}) } as unknown as Response;
    });
  });

  afterEach(() => {
    fetchSpy.mockRestore();
    __resetMinimapCacheForTest();
  });

  function mountMinimapHost(): { hook: UseWorldMinimapReturn; unmount: () => void } {
    const handle: { hook: UseWorldMinimapReturn } = {
      hook: null as unknown as UseWorldMinimapReturn,
    };
    const Host = defineComponent({
      setup() {
        handle.hook = useWorldMinimap();
        return () => h('div');
      },
    });
    const wrapper = mount(Host);
    return { hook: handle.hook, unmount: () => wrapper.unmount() };
  }

  it.each([
    'mine_south',
    'farm_north',
    'campus_east',
    'port_west',
  ])('旧存档 mapKey=%s 被归一到 town_hub（minimap 不消失）', async (legacyKey) => {
    const world = useWorldStore();
    world.player = {
      playerStateId: 1,
      avatarKey: 'x',
      mapKey: legacyKey,
      tileX: 3,
      tileY: 4,
      direction: 'DOWN',
    };
    const { hook, unmount } = mountMinimapHost();
    await flushPromises();
    await flushPromises();

    expect(hook.data.value?.mapKey).toBe('town_hub');
    // 归一必须发生在 fetch 之前：绝不能去拉已删除的外围图 JSON。
    const requested = fetchSpy.mock.calls.map(([input]) => String(input));
    expect(requested).toContain('/assets/maps/town_hub.json');
    expect(requested.some((url) => url.includes(legacyKey))).toBe(false);
    // 玩家坐标照常同步，不因别名归一而丢失。
    expect(hook.playerX.value).toBe(3);
    expect(hook.playerY.value).toBe(4);
    unmount();
  });
});

describe('workflows.integration · Dialog store 状态机', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('openNpcKey 有值即 isOpen=true（visible 仍受树加载控制）', () => {
    const dlg = useDialogStore();
    dlg.openNpcKey = 'mayor_py';
    expect(dlg.isOpen).toBe(true);
    expect(dlg.visible).toBe(false);
  });

  it('close 后 openNpcKey/visible/tree 全部清空', () => {
    const dlg = useDialogStore();
    dlg.openNpcKey = 'mayor_py';
    dlg.close();
    expect(dlg.openNpcKey).toBeNull();
    expect(dlg.visible).toBe(false);
    expect(dlg.tree).toBeNull();
  });

  it('markTypingDone 后 chooseBranch 未知 choiceId 是 no-op', () => {
    const dlg = useDialogStore();
    dlg.openNpcKey = 'mayor_py';
    dlg.markTypingDone();
    dlg.chooseBranch('any');
    // 没有 currentNode，chooseBranch 会找不到 → 不抛错
    expect(dlg.isOpen).toBe(true);
  });
});
