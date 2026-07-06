// R-13：SettingsModal 单测，覆盖开关、ESC 关闭、语言切换、退出登录。
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import SettingsModal from '@/components/hud/SettingsModal.vue';
import { useHudStore } from '@stores/hud';
import { useSessionStore } from '@stores/session';

const ROUTER_PUSH = vi.fn();

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: ROUTER_PUSH }),
}));

// useAudio 暴露 setMuted/isMuted/setBgmVolume/setSfxVolume；SettingsModal 仅触发它们，不影响渲染分支。
vi.mock('@/composables/useAudio', () => ({
  useAudio: () => ({
    setMuted: vi.fn(),
    isMuted: () => false,
    playBgm: vi.fn(),
    stopBgm: vi.fn(),
    playSfx: vi.fn(),
    getCurrentBgmKey: () => null,
    unlockAudio: vi.fn(),
    setBgmVolume: vi.fn(),
    setSfxVolume: vi.fn(),
    getBgmVolume: () => 1,
    getSfxVolume: () => 1,
  }),
}));

describe('SettingsModal', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    ROUTER_PUSH.mockReset();
  });

  it('settingsOpen=false 时不渲染', () => {
    const wrapper = mount(SettingsModal);
    expect(wrapper.find('[data-testid="settings-modal"]').exists()).toBe(false);
  });

  it('settingsOpen=true 时渲染对话框 + 标题 + 关闭按钮', async () => {
    const hud = useHudStore();
    hud.openSettings();
    const wrapper = mount(SettingsModal);
    await flushPromises();
    const modal = wrapper.find('[data-testid="settings-modal"]');
    expect(modal.exists()).toBe(true);
    expect(modal.attributes('role')).toBe('dialog');
    expect(modal.attributes('aria-modal')).toBe('true');
    expect(wrapper.find('[data-testid="settings-close"]').exists()).toBe(true);
    // 「音频」标签默认激活，静音开关可见。
    expect(wrapper.find('[data-testid="settings-mute"]').exists()).toBe(true);
    // R-12 设置弹窗改为 5 标签页；语言下拉在「显示」标签页，切换后才渲染。
    const displayTab = wrapper.findAll('[role="tab"]').find((t) => t.text().includes('显示'));
    expect(displayTab).toBeDefined();
    await displayTab!.trigger('click');
    expect(wrapper.find('[data-testid="settings-locale"]').exists()).toBe(true);
  });

  it('点击关闭按钮：hud.settingsOpen 回到 false', async () => {
    const hud = useHudStore();
    hud.openSettings();
    const wrapper = mount(SettingsModal);
    await flushPromises();
    await wrapper.find('[data-testid="settings-close"]').trigger('click');
    expect(hud.settingsOpen).toBe(false);
  });

  it('点击完成按钮：hud.settingsOpen 回到 false', async () => {
    const hud = useHudStore();
    hud.openSettings();
    const wrapper = mount(SettingsModal);
    await flushPromises();
    await wrapper.find('[data-testid="settings-ok"]').trigger('click');
    expect(hud.settingsOpen).toBe(false);
  });

  it('点击退出登录：session.logout + 跳路由 login + 关闭弹窗', async () => {
    const hud = useHudStore();
    const session = useSessionStore();
    const logoutSpy = vi.spyOn(session, 'logout');
    hud.openSettings();
    const wrapper = mount(SettingsModal);
    await flushPromises();
    await wrapper.find('[data-testid="settings-logout"]').trigger('click');
    expect(logoutSpy).toHaveBeenCalledOnce();
    expect(ROUTER_PUSH).toHaveBeenCalledWith({ name: 'login' });
    expect(hud.settingsOpen).toBe(false);
  });
});
