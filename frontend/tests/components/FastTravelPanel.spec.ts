// R-13 Phase 6 迭代 28：FastTravelPanel spec（R-14 起对齐「入口休眠」）。
//
// 旧断言（CustomEvent 能打开面板 / 渲染 hub 行 / T 键切换）已随 R-14 过时：
// 世界收缩为单一 town_hub 后 FAST_TRAVEL_ROUTES 只剩一条，且它的目的地就是玩家
// 当前所在地图 —— 面板里唯一一行永久 disabled，打开面板 = 一个什么都点不了的死路。
// 实现（src/data/fastTravel.ts）因此加了
// `FAST_TRAVEL_ENABLED = FAST_TRAVEL_ROUTES.length > 1` 并据此 gate 掉面板打开与 T 键，
// 本 spec 随之改为断言「入口被抑制」。
//
// 同时用 mock 路线表补上「路线恢复到 >1 时入口回归」：将来恢复多图，这条 gate
// 有测试兜底，不会被误删也不会被误锁死。
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { FastTravelRouteOverride } from '../_helpers/fastTravelRoutes';

const ftRoutes: FastTravelRouteOverride = vi.hoisted(() => ({ routes: null }));

vi.mock('@/data/fastTravel', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/data/fastTravel')>();
  const { createFastTravelModuleMock } = await import('../_helpers/fastTravelRoutes');
  return createFastTravelModuleMock(actual, ftRoutes);
});

import FastTravelPanel from '@/components/hud/FastTravelPanel.vue';
import { FAST_TRAVEL_ENABLED, FAST_TRAVEL_ROUTES } from '@/data/fastTravel';
import { RESTORED_FAST_TRAVEL_ROUTES } from '../_helpers/fastTravelRoutes';
import { useWorldStore } from '@stores/world';

function setWorldPlayer(mapKey: string): void {
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

beforeEach(() => {
  setActivePinia(createPinia());
  // 默认透传真实路线表（R-14 单 hub）；需要多路线的 describe 自行覆盖。
  ftRoutes.routes = null;
});

describe('FastTravelPanel · R-14 单 hub：入口休眠', () => {
  it('默认不可见', () => {
    const wrapper = mount(FastTravelPanel);
    expect(wrapper.find('[data-testid="fast-travel-panel"]').exists()).toBe(false);
  });

  it('gate 成立的前提：唯一路线的目的地就是玩家当前所在地图', () => {
    setWorldPlayer('town_hub');
    const world = useWorldStore();
    expect(FAST_TRAVEL_ROUTES).toHaveLength(1);
    // 每条路线都指向玩家所在地 → 面板里每一行都会被 disabled → 打开面板毫无意义。
    expect(FAST_TRAVEL_ROUTES.every((route) => route.toMap === world.player?.mapKey)).toBe(true);
    expect(FAST_TRAVEL_ENABLED).toBe(false);
  });

  it('CustomEvent 不再打开面板（避免只有一行灰按钮的死路）', async () => {
    setWorldPlayer('town_hub');
    const wrapper = mount(FastTravelPanel, { attachTo: document.body });
    await flushPromises();
    window.dispatchEvent(new CustomEvent('pythontopia:fast-travel-open'));
    await flushPromises();
    expect(wrapper.find('[data-testid="fast-travel-panel"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('keydown T 不再打开面板', async () => {
    setWorldPlayer('town_hub');
    const wrapper = mount(FastTravelPanel, { attachTo: document.body });
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 't' }));
    await flushPromises();
    expect(wrapper.find('[data-testid="fast-travel-panel"]').exists()).toBe(false);
    // 大写 T 走同一分支，同样被 gate
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'T' }));
    await flushPromises();
    expect(wrapper.find('[data-testid="fast-travel-panel"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('单 hub：外围图路线已从路线表移除', () => {
    const keys = FAST_TRAVEL_ROUTES.map((route) => route.key);
    expect(keys).not.toContain('farm');
    expect(keys).not.toContain('mine');
    expect(keys).not.toContain('campus');
    expect(keys).not.toContain('port');
  });
});

describe('FastTravelPanel · 路线恢复到 >1：入口回归', () => {
  beforeEach(() => {
    // 模拟未来重新开放外围图：路线表一变长，gate 自动放行。
    ftRoutes.routes = RESTORED_FAST_TRAVEL_ROUTES;
  });

  it('FAST_TRAVEL_ENABLED 随路线表恢复为 true', () => {
    expect(FAST_TRAVEL_ENABLED).toBe(true);
  });

  it('CustomEvent 打开面板并渲染全部路线', async () => {
    setWorldPlayer('town_hub');
    const wrapper = mount(FastTravelPanel, { attachTo: document.body });
    await flushPromises();
    window.dispatchEvent(new CustomEvent('pythontopia:fast-travel-open'));
    await flushPromises();
    expect(wrapper.find('[data-testid="fast-travel-panel"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="fast-travel-hub"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="fast-travel-farm"]').exists()).toBe(true);
    wrapper.unmount();
  });

  it('keydown T 恢复开关面板', async () => {
    setWorldPlayer('town_hub');
    const wrapper = mount(FastTravelPanel, { attachTo: document.body });
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 't' }));
    await flushPromises();
    expect(wrapper.find('[data-testid="fast-travel-panel"]').exists()).toBe(true);
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 't' }));
    await flushPromises();
    expect(wrapper.find('[data-testid="fast-travel-panel"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('玩家所在地图那一行被 disabled，其他路线可点', async () => {
    setWorldPlayer('town_hub');
    const wrapper = mount(FastTravelPanel, { attachTo: document.body });
    window.dispatchEvent(new CustomEvent('pythontopia:fast-travel-open'));
    await flushPromises();
    expect(wrapper.find('[data-testid="fast-travel-hub"]').attributes('disabled')).toBeDefined();
    expect(wrapper.find('[data-testid="fast-travel-farm"]').attributes('disabled')).toBeUndefined();
    wrapper.unmount();
  });

  it('点击可用路线 → worldStore.player 写到目标地图并关闭面板', async () => {
    setWorldPlayer('town_hub');
    const world = useWorldStore();
    const wrapper = mount(FastTravelPanel, { attachTo: document.body });
    window.dispatchEvent(new CustomEvent('pythontopia:fast-travel-open'));
    await flushPromises();
    await wrapper.find('[data-testid="fast-travel-farm"]').trigger('click');
    await flushPromises();
    expect(world.player?.mapKey).toBe('farm_north');
    expect(world.player?.tileX).toBe(10);
    expect(world.player?.tileY).toBe(8);
    expect(wrapper.find('[data-testid="fast-travel-panel"]').exists()).toBe(false);
    wrapper.unmount();
  });
});
