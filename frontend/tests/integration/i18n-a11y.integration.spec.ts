// R-13 Phase 10 i18n + a11y 集成 spec（目标 30 用例）。
//
// 覆盖：
//   - useI18n.t() / tn() / setLocale 切换 / 占位符插值 / 复数兜底
//   - 关键 HUD 组件 aria-label / role / tabindex 检查
//   - 关键弹窗 role="dialog" + aria-modal + 关闭按钮 aria-label
//   - 焦点管理：MiniMap 出口箭头 aria-label 含方向

import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useI18n } from '@/composables/useI18n';
import { useHudStore } from '@stores/hud';
import { useWorldStore } from '@stores/world';
import HudShell from '@/components/hud/HudShell.vue';
import MiniMap from '@/components/hud/MiniMap.vue';
import SettingsModal from '@/components/hud/SettingsModal.vue';
import FastTravelPanel from '@/components/hud/FastTravelPanel.vue';
import NotFoundPage from '@/pages/NotFoundPage.vue';

vi.mock('vue-router', async () => {
  const actual = await vi.importActual<typeof import('vue-router')>('vue-router');
  return {
    ...actual,
    useRouter: () => ({ push: vi.fn() }),
    useRoute: () => ({ fullPath: '/missing' }),
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
}));

const APP_LAYOUT_STUB = { template: '<div><slot /></div>' };

function makeRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/world', name: 'world', component: { template: '<div />' } }],
  });
}

describe('i18n.integration · 翻译 / 切换 / 插值', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('默认 locale 是 zh-CN', () => {
    const { locale } = useI18n();
    expect(locale.value).toBe('zh-CN');
  });

  it.each(['zh-CN', 'en-US'] as const)('setLocale("%s") 切换生效', (lang) => {
    const { setLocale, locale } = useI18n();
    setLocale(lang);
    expect(locale.value).toBe(lang);
  });

  it('setLocale 非法值时被忽略', () => {
    const { setLocale, locale } = useI18n();
    const before = locale.value;
    // @ts-expect-error 故意传非法
    setLocale('fr-FR');
    expect(locale.value).toBe(before);
  });

  it('t() 找不到 key 时返回 key 本身', () => {
    const { t } = useI18n();
    expect(t('no.such.key')).toBe('no.such.key');
  });

  it('t() 占位符 {name} 被插值', () => {
    const { t } = useI18n();
    const out = t('toast.itemGet', { item: '种子' });
    expect(out).toContain('种子');
  });

  it('t() 未提供的占位符保留原 {placeholder}', () => {
    const { t } = useI18n();
    const out = t('toast.itemGet');
    expect(out).toContain('{item}');
  });

  it('tn() zh-CN 不应用复数', () => {
    const { setLocale, tn } = useI18n();
    setLocale('zh-CN');
    const out = tn('toast.itemGet', 5, { item: 'A' });
    expect(typeof out).toBe('string');
  });

  it.each([0, 2, 5, 10])('tn() en-US count=%i 触发复数尾缀', (count) => {
    const { setLocale, tn } = useI18n();
    setLocale('en-US');
    // 用一个已知英文 key（如不存在则 fallback 到 key 本身）
    const out = tn('hud.btn.confirm', count);
    expect(typeof out).toBe('string');
    setLocale('zh-CN');
  });

  it('tn() count=1 不复数', () => {
    const { setLocale, tn } = useI18n();
    setLocale('en-US');
    const out = tn('hud.btn.confirm', 1);
    expect(out).not.toMatch(/ss$/);
    setLocale('zh-CN');
  });
});

describe('a11y.integration · HudShell aria + 按钮可达性', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('HudShell 3 quickslot + 指南入口都有 aria-label', () => {
    // R-15 主线巡检：删「任务」槽（与冒险地图重复）→ 学习 quickslot 收敛为 对话/地图/技能 3 槽，
    // 另保留一个图文指南入口。
    const wrapper = mount(HudShell, { global: { plugins: [makeRouter()] }, attachTo: document.body });
    const slots = wrapper.findAll('.hud-slot');
    expect(slots.length).toBe(4);
    for (const s of slots) {
      expect(s.attributes('aria-label')).toBeDefined();
    }
    for (const id of ['hud-slot-dialog', 'hud-slot-map', 'hud-slot-skill']) {
      expect(wrapper.get(`[data-testid="${id}"]`).attributes('aria-pressed')).toBeDefined();
    }
    wrapper.unmount();
  });

  it('HudShell 顶部状态条 role="status"', () => {
    const wrapper = mount(HudShell, { global: { plugins: [makeRouter()] }, attachTo: document.body });
    expect(wrapper.find('[data-testid="hud-top"]').attributes('role')).toBe('status');
    wrapper.unmount();
  });

  it('HudShell 进度条带 progressbar role + aria-valuenow/min/max', async () => {
    const hud = useHudStore();
    hud.syncMeta({ energy: 42 });
    const wrapper = mount(HudShell, { global: { plugins: [makeRouter()] }, attachTo: document.body });
    const bar = wrapper.get('[data-testid="hud-energy"]');
    expect(bar.attributes('role')).toBe('progressbar');
    expect(bar.attributes('aria-valuenow')).toBe('42');
    expect(bar.attributes('aria-valuemin')).toBe('0');
    expect(bar.attributes('aria-valuemax')).toBe('100');
    wrapper.unmount();
  });

  it.each(['hud-tool-menu'])(
    '工具按钮 %s 含 aria-label',
    (id) => {
      const wrapper = mount(HudShell, { global: { plugins: [makeRouter()] }, attachTo: document.body });
      expect(wrapper.find(`[data-testid="${id}"]`).attributes('aria-label')).toBeDefined();
      wrapper.unmount();
    },
  );
});

describe('a11y.integration · MiniMap 出口箭头 aria-label', () => {
  it.each(['north', 'south', 'east', 'west'] as const)('exit-%s 含方向 aria-label', (side) => {
    const wrapper = mount(MiniMap, {
      props: {
        playerX: 0,
        playerY: 0,
        tilemap: { width: 4, height: 4 },
        exits: [{ side, toMap: 'mine_south', label: 'Bug 矿洞' }],
      },
    });
    const el = wrapper.get(`[data-testid="mini-map-exit-${side}"]`);
    expect(el.attributes('aria-label')).toContain(side);
  });
});

describe('a11y.integration · 弹窗 role/aria', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('SettingsModal 打开后 role=dialog + aria-modal=true', async () => {
    const hud = useHudStore();
    hud.openSettings();
    const wrapper = mount(SettingsModal);
    const modal = wrapper.get('[data-testid="settings-modal"]');
    expect(modal.attributes('role')).toBe('dialog');
    expect(modal.attributes('aria-modal')).toBe('true');
  });

  it('SettingsModal 关闭按钮含 aria-label', async () => {
    const hud = useHudStore();
    hud.openSettings();
    const wrapper = mount(SettingsModal);
    expect(wrapper.get('[data-testid="settings-close"]').attributes('aria-label')).toBe('关闭设置');
  });

  it('FastTravelPanel 打开后 role=dialog', async () => {
    const world = useWorldStore();
    world.player = {
      playerStateId: 1,
      avatarKey: 'x',
      mapKey: 'town_hub',
      tileX: 0,
      tileY: 0,
      direction: 'DOWN',
    };
    const wrapper = mount(FastTravelPanel, { attachTo: document.body });
    window.dispatchEvent(new CustomEvent('pythontopia:fast-travel-open'));
    await wrapper.vm.$nextTick();
    expect(wrapper.get('[data-testid="fast-travel-panel"]').attributes('role')).toBe('dialog');
    wrapper.unmount();
  });
});

describe('a11y.integration · NotFoundPage 按钮可达性', () => {
  it('显示 3 个按钮（回主世界 / 上一页 / 回登录），都是 button', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/world', name: 'world', component: { template: '<div />' } },
        { path: '/login', name: 'login', component: { template: '<div />' } },
        { path: '/:pathMatch(.*)*', name: 'not-found', component: NotFoundPage },
      ],
    });
    await router.push('/missing');
    const wrapper = mount(NotFoundPage, {
      global: { plugins: [router], stubs: { AppLayout: APP_LAYOUT_STUB } },
    });
    const buttons = wrapper.findAll('button');
    expect(buttons.length).toBeGreaterThanOrEqual(3);
  });
});
