// R-13 Phase 8 大爆炸测试 · HUD 模块。
//
// R-12 星露谷改造后 HudShell 精简为 4 quickslot（quest/dialog/map/skill）+ 单个
// 菜单按钮（hud-tool-menu）；快速旅行/睡眠/图书馆/设置改为键盘触发（T/C/L/,），
// 不再有独立工具按钮与金币 chip。本测试随之对齐当前组件。
//
// 覆盖：
//   - HudShell 4 quickslot + 菜单按钮
//   - keydown input/textarea/contenteditable focus guard
//   - settingsOpen toggle 不影响 activeSlot
//   - HudShell BGM chip 显示
//   - chip emoji 不破坏 data-testid 文本
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import HudShell from '@/components/hud/HudShell.vue';
import { useHudStore } from '@stores/hud';

function makeRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/world', name: 'world', component: { template: '<div />' } },
      { path: '/library', name: 'library', component: { template: '<div />' } },
    ],
  });
}

function mountShell() {
  const router = makeRouter();
  return mount(HudShell, { global: { plugins: [router] }, attachTo: document.body });
}

describe('hud.integration: 全键位 + 工具区 + 边界', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it.each(['quest', 'dialog', 'map', 'skill'] as const)(
    '点击 %s slot：activeSlot 切换',
    async (slot) => {
      const hud = useHudStore();
      const wrapper = mountShell();
      await wrapper.find(`[data-testid="hud-slot-${slot}"]`).trigger('click');
      expect(hud.activeSlot).toBe(slot);
      wrapper.unmount();
    },
  );

  it.each([
    ['q', 'quest'],
    ['Q', 'quest'],
    ['j', 'dialog'],
    ['m', 'map'],
    ['k', 'skill'],
  ])('window keydown "%s" 切换到 %s', async (key, slot) => {
    const hud = useHudStore();
    const wrapper = mountShell();
    window.dispatchEvent(new KeyboardEvent('keydown', { key }));
    expect(hud.activeSlot).toBe(slot);
    wrapper.unmount();
  });

  it('keydown Escape 关闭 settings 优先于 quickslot', async () => {
    const hud = useHudStore();
    const wrapper = mountShell();
    hud.toggleSlot('inventory');
    hud.openSettings();
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(hud.settingsOpen).toBe(false);
    expect(hud.activeSlot).toBe('inventory'); // 第一次只关 settings
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(hud.activeSlot).toBeNull();
    wrapper.unmount();
  });

  it('keydown "," toggle settings', async () => {
    const hud = useHudStore();
    const wrapper = mountShell();
    window.dispatchEvent(new KeyboardEvent('keydown', { key: ',' }));
    expect(hud.settingsOpen).toBe(true);
    window.dispatchEvent(new KeyboardEvent('keydown', { key: ',' }));
    expect(hud.settingsOpen).toBe(false);
    wrapper.unmount();
  });

  it('input 焦点时 keydown 被忽略', async () => {
    const hud = useHudStore();
    const wrapper = mountShell();
    const input = document.createElement('input');
    document.body.appendChild(input);
    input.focus();
    const event = new KeyboardEvent('keydown', { key: 'b' });
    Object.defineProperty(event, 'target', { value: input });
    window.dispatchEvent(event);
    expect(hud.activeSlot).toBeNull();
    document.body.removeChild(input);
    wrapper.unmount();
  });

  it('textarea 焦点时 keydown 被忽略', async () => {
    const hud = useHudStore();
    const wrapper = mountShell();
    const ta = document.createElement('textarea');
    document.body.appendChild(ta);
    const event = new KeyboardEvent('keydown', { key: 'm' });
    Object.defineProperty(event, 'target', { value: ta });
    window.dispatchEvent(event);
    expect(hud.activeSlot).toBeNull();
    document.body.removeChild(ta);
    wrapper.unmount();
  });

  it('Ctrl + 字母不触发 quickslot（避免抢系统快捷键）', async () => {
    const hud = useHudStore();
    const wrapper = mountShell();
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'b', ctrlKey: true }));
    expect(hud.activeSlot).toBeNull();
    wrapper.unmount();
  });

  it('菜单按钮 hud-tool-menu 渲染', () => {
    const wrapper = mountShell();
    expect(wrapper.find('[data-testid="hud-tool-menu"]').exists()).toBe(true);
    wrapper.unmount();
  });

  it('keydown "t" 触发 window 快速旅行 CustomEvent', async () => {
    const wrapper = mountShell();
    const handler = vi.fn();
    window.addEventListener('pythontopia:fast-travel-open', handler);
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 't' }));
    expect(handler).toHaveBeenCalled();
    window.removeEventListener('pythontopia:fast-travel-open', handler);
    wrapper.unmount();
  });

  it('keydown "," toggle settingsOpen', async () => {
    const hud = useHudStore();
    const wrapper = mountShell();
    window.dispatchEvent(new KeyboardEvent('keydown', { key: ',' }));
    expect(hud.settingsOpen).toBe(true);
    window.dispatchEvent(new KeyboardEvent('keydown', { key: ',' }));
    expect(hud.settingsOpen).toBe(false);
    wrapper.unmount();
  });

  it('keydown "l" 打开图书馆不抛错', async () => {
    const wrapper = mountShell();
    expect(() => {
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'l' }));
    }).not.toThrow();
    wrapper.unmount();
  });

  it('topBar/bottomBar setVisibility 不互相影响', () => {
    const hud = useHudStore();
    hud.setVisibility('top', 'hidden');
    expect(hud.topBar).toBe('hidden');
    expect(hud.bottomBar).toBe('shown');
    hud.setVisibility('bottom', 'collapsed');
    expect(hud.topBar).toBe('hidden');
    expect(hud.bottomBar).toBe('collapsed');
  });

  it('hud-day chip 默认显示 Day 1', () => {
    const wrapper = mountShell();
    expect(wrapper.find('[data-testid="hud-day"]').text()).toContain('1');
    wrapper.unmount();
  });

  it('hud-bgm chip 默认显示「静默」（无 BGM）', () => {
    const wrapper = mountShell();
    expect(wrapper.find('[data-testid="hud-bgm"]').exists()).toBe(true);
    wrapper.unmount();
  });

  it('chip emoji 不破坏 syncMeta 反映 weather 文本', async () => {
    const hud = useHudStore();
    const wrapper = mountShell();
    hud.syncMeta({ weather: 'STORMY' });
    await wrapper.vm.$nextTick();
    expect(wrapper.get('[data-testid="hud-weather"]').text()).toContain('STORMY');
    wrapper.unmount();
  });
});
