import { mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import { defineComponent, h, type Ref } from 'vue';
import { useReducedMotion } from '@/composables/useReducedMotion';

interface MockMediaQueryList {
  matches: boolean;
  media: string;
  onchange: null;
  addEventListener: Mock;
  removeEventListener: Mock;
  addListener: Mock;
  removeListener: Mock;
  dispatchEvent: Mock;
}

interface MqlOverrides {
  initial?: boolean;
  legacyOnly?: boolean;
}

interface MqlHandle {
  mql: MockMediaQueryList;
  emit: (matches: boolean) => void;
  emitLegacy: (matches: boolean) => void;
}

function installMatchMedia(overrides: MqlOverrides = {}): MqlHandle {
  const { initial = false, legacyOnly = false } = overrides;

  const listeners = new Set<(event: MediaQueryListEvent) => void>();
  const legacyListeners = new Set<(event: MediaQueryListEvent) => void>();

  const mql: MockMediaQueryList = {
    matches: initial,
    media: '(prefers-reduced-motion: reduce)',
    onchange: null,
    addEventListener: vi.fn((_type: string, handler: (event: MediaQueryListEvent) => void) => {
      listeners.add(handler);
    }),
    removeEventListener: vi.fn((_type: string, handler: (event: MediaQueryListEvent) => void) => {
      listeners.delete(handler);
    }),
    addListener: vi.fn((handler: (event: MediaQueryListEvent) => void) => {
      legacyListeners.add(handler);
    }),
    removeListener: vi.fn((handler: (event: MediaQueryListEvent) => void) => {
      legacyListeners.delete(handler);
    }),
    dispatchEvent: vi.fn(() => true),
  };

  if (legacyOnly) {
    // 模拟 Safari < 14：完全没有 addEventListener / removeEventListener。
    // 通过删除属性让 typeof 守卫走入 legacy 分支。
    delete (mql as Partial<MockMediaQueryList>).addEventListener;
    delete (mql as Partial<MockMediaQueryList>).removeEventListener;
  }

  const matchMediaMock = vi.fn(() => mql) as unknown as typeof window.matchMedia;
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    writable: true,
    value: matchMediaMock,
  });

  return {
    mql,
    emit: (matches: boolean) => {
      mql.matches = matches;
      const event = { matches, media: mql.media } as MediaQueryListEvent;
      for (const handler of listeners) {
        handler(event);
      }
    },
    emitLegacy: (matches: boolean) => {
      mql.matches = matches;
      const event = { matches, media: mql.media } as MediaQueryListEvent;
      for (const handler of legacyListeners) {
        handler(event);
      }
    },
  };
}

function uninstallMatchMedia(): void {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    writable: true,
    value: undefined,
  });
}

function makeProbe(): {
  Probe: ReturnType<typeof defineComponent>;
  getRef: () => Ref<boolean> | null;
} {
  let captured: Ref<boolean> | null = null;
  const Probe = defineComponent({
    name: 'ReducedMotionProbe',
    setup() {
      captured = useReducedMotion();
      return () => h('div', { 'data-testid': 'probe' }, String(captured?.value));
    },
  });
  return { Probe, getRef: () => captured };
}

describe('useReducedMotion', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    uninstallMatchMedia();
  });

  it('matchMedia.matches=true 时初值为 true 并以 reduce 查询字符串调用 matchMedia', () => {
    const handle = installMatchMedia({ initial: true });
    const { Probe, getRef } = makeProbe();

    const wrapper = mount(Probe);

    expect(getRef()?.value).toBe(true);
    expect(wrapper.get('[data-testid="probe"]').text()).toBe('true');
    expect(window.matchMedia).toHaveBeenCalledWith('(prefers-reduced-motion: reduce)');
    expect(handle.mql.addEventListener).toHaveBeenCalledWith('change', expect.any(Function));
  });

  it('matchMedia.matches=false 时初值为 false（不偶然返回 truthy）', () => {
    installMatchMedia({ initial: false });
    const { Probe, getRef } = makeProbe();

    const wrapper = mount(Probe);

    expect(getRef()?.value).toBe(false);
    expect(wrapper.get('[data-testid="probe"]').text()).toBe('false');
  });

  it('系统在运行时切换 reduce 状态时，ref 同步响应（false → true → false）', async () => {
    const handle = installMatchMedia({ initial: false });
    const { Probe, getRef } = makeProbe();

    const wrapper = mount(Probe);
    expect(getRef()?.value).toBe(false);

    handle.emit(true);
    await wrapper.vm.$nextTick();
    expect(getRef()?.value).toBe(true);
    expect(wrapper.get('[data-testid="probe"]').text()).toBe('true');

    handle.emit(false);
    await wrapper.vm.$nextTick();
    expect(getRef()?.value).toBe(false);
    expect(wrapper.get('[data-testid="probe"]').text()).toBe('false');
  });

  it('组件卸载后调用 removeEventListener 移除同一个 handler，避免长会话泄漏', () => {
    const handle = installMatchMedia({ initial: false });
    const { Probe } = makeProbe();

    const wrapper = mount(Probe);
    expect(handle.mql.addEventListener).toHaveBeenCalledTimes(1);
    const addedHandler = handle.mql.addEventListener.mock.calls[0]?.[1];

    wrapper.unmount();

    expect(handle.mql.removeEventListener).toHaveBeenCalledTimes(1);
    const removedHandler = handle.mql.removeEventListener.mock.calls[0]?.[1];
    expect(removedHandler).toBe(addedHandler);
  });

  it('卸载后再 emit change 事件，旧 ref 不再被更新（监听器确已解绑）', async () => {
    const handle = installMatchMedia({ initial: false });
    const { Probe, getRef } = makeProbe();

    const wrapper = mount(Probe);
    const refBeforeUnmount = getRef();
    expect(refBeforeUnmount?.value).toBe(false);

    wrapper.unmount();

    handle.emit(true);
    await Promise.resolve();
    expect(refBeforeUnmount?.value).toBe(false);
  });

  it('重复挂载两个组件：各拿独立 ref + 各自挂载/卸载监听器互不影响', async () => {
    const handle = installMatchMedia({ initial: false });
    const probeA = makeProbe();
    const probeB = makeProbe();

    const wrapperA = mount(probeA.Probe);
    const wrapperB = mount(probeB.Probe);

    expect(handle.mql.addEventListener).toHaveBeenCalledTimes(2);
    expect(probeA.getRef()).not.toBe(probeB.getRef());
    expect(probeA.getRef()?.value).toBe(false);
    expect(probeB.getRef()?.value).toBe(false);

    handle.emit(true);
    await wrapperA.vm.$nextTick();
    await wrapperB.vm.$nextTick();
    expect(probeA.getRef()?.value).toBe(true);
    expect(probeB.getRef()?.value).toBe(true);

    wrapperA.unmount();
    expect(handle.mql.removeEventListener).toHaveBeenCalledTimes(1);

    handle.emit(false);
    await wrapperB.vm.$nextTick();
    expect(probeB.getRef()?.value).toBe(false);

    wrapperB.unmount();
    expect(handle.mql.removeEventListener).toHaveBeenCalledTimes(2);
  });

  it('SSR / 无 matchMedia 环境下 fail-safe 返回 false 且不抛错', () => {
    uninstallMatchMedia();
    const { Probe, getRef } = makeProbe();

    expect(() => mount(Probe)).not.toThrow();
    expect(getRef()?.value).toBe(false);
  });

  it('Safari < 14 仅有 addListener / removeListener 时走 legacy 分支并能响应 change', async () => {
    const handle = installMatchMedia({ initial: false, legacyOnly: true });
    const { Probe, getRef } = makeProbe();

    const wrapper = mount(Probe);
    expect(handle.mql.addListener).toHaveBeenCalledTimes(1);
    const legacyHandler = handle.mql.addListener.mock.calls[0]?.[0];

    handle.emitLegacy(true);
    await wrapper.vm.$nextTick();
    expect(getRef()?.value).toBe(true);

    wrapper.unmount();
    expect(handle.mql.removeListener).toHaveBeenCalledTimes(1);
    expect(handle.mql.removeListener.mock.calls[0]?.[0]).toBe(legacyHandler);
  });
});
