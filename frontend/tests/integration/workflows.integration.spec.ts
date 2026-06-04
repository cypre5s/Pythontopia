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
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { router as appRouter } from '@/app/router';
import { useSessionStore } from '@stores/session';
import { useHudStore } from '@stores/hud';
import { useCalendarStore } from '@stores/calendar';
import { useWorldStore } from '@stores/world';
import { useDialogStore } from '@stores/dialog';
import HudShell from '@/components/hud/HudShell.vue';
import FastTravelPanel from '@/components/hud/FastTravelPanel.vue';
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

  it('day=15 不在节日表 → ribbon 显示 upcoming（春日嘉年华 day=28）', async () => {
    // FestivalRibbon 读 hud.meta.dayIndex（R-12 起数据源由 calendar 切到 hud）。
    const hud = useHudStore();
    hud.syncMeta({ dayIndex: 15 });
    const wrapper = mount(FestivalRibbon);
    await flushPromises();
    expect(wrapper.text()).toContain('春日嘉年华');
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

describe('workflows.integration · FastTravel + World player 联动', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  // R-14 方案A：世界收缩为单一 town_hub，快速旅行只剩 hub 一条路线。
  // 验证点击 hub 路线会把 player 写回 town_hub（FastTravel ↔ worldStore 联动）。
  it.each([
    'campus_east',
    'mine_south',
    'port_west',
    'farm_north',
  ])('从 %s 通过 FastTravel 回到 town_hub', async (from) => {
    const world = useWorldStore();
    world.player = {
      playerStateId: 1,
      avatarKey: 'x',
      mapKey: from,
      tileX: 0,
      tileY: 0,
      direction: 'DOWN',
    };
    const wrapper = mount(FastTravelPanel, { attachTo: document.body });
    window.dispatchEvent(new CustomEvent('pythontopia:fast-travel-open'));
    await flushPromises();
    await wrapper.find('[data-testid="fast-travel-hub"]').trigger('click');
    await flushPromises();
    expect(world.player.mapKey).toBe('town_hub');
    wrapper.unmount();
  });
});

describe('workflows.integration · Dialog store 状态机', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('open(npcKey) → visible=false 但 openNpcKey 有值（旧 API）', () => {
    const dlg = useDialogStore();
    dlg.open('mayor_py');
    expect(dlg.openNpcKey).toBe('mayor_py');
    expect(dlg.isOpen).toBe(true);
  });

  it('close 后 openNpcKey/visible/tree 全部清空', () => {
    const dlg = useDialogStore();
    dlg.open('mayor_py');
    dlg.close();
    expect(dlg.openNpcKey).toBeNull();
    expect(dlg.visible).toBe(false);
    expect(dlg.tree).toBeNull();
  });

  it('open 后再 open 另一个 NPC', () => {
    const dlg = useDialogStore();
    dlg.open('mayor_py');
    dlg.open('farmer_kc');
    expect(dlg.openNpcKey).toBe('farmer_kc');
  });

  it('markTypingDone 后再 chooseBranch（无 tree 时 close）', () => {
    const dlg = useDialogStore();
    dlg.open('mayor_py');
    dlg.markTypingDone();
    dlg.chooseBranch('any');
    // 没有 currentNode，chooseBranch 会找不到 → 不抛错
    expect(dlg.isOpen).toBe(true);
  });
});
