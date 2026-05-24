import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import PyToast, {
  type PyToastPosition,
  type PyToastType,
} from '@/components/ui/PyToast.vue';

const ALL_TYPES: readonly PyToastType[] = [
  'success',
  'error',
  'info',
  'warning',
] as const;

const TYPE_ICON: Readonly<Record<PyToastType, string>> = {
  success: '✓',
  error: '✗',
  info: 'ℹ',
  warning: '⚠',
};

const TYPE_LIVE: Readonly<Record<PyToastType, 'polite' | 'assertive'>> = {
  success: 'polite',
  info: 'polite',
  warning: 'assertive',
  error: 'assertive',
};

const POSITIONS: readonly PyToastPosition[] = ['top', 'bottom'] as const;

beforeEach(() => {
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
});

describe('PyToast', () => {
  it('默认渲染：role=alert + aria-atomic + message 文本 + 关闭按钮，且未触发 close', () => {
    const wrapper = mount(PyToast, {
      props: { message: '已保存', type: 'success' },
    });
    const root = wrapper.get('[data-testid="py-toast"]');
    expect(root.attributes('role')).toBe('alert');
    expect(root.attributes('aria-atomic')).toBe('true');
    expect(root.attributes('data-type')).toBe('success');
    expect(root.attributes('data-position')).toBe('top');
    expect(wrapper.get('[data-testid="py-toast-message"]').text()).toBe('已保存');
    expect(wrapper.get('[data-testid="py-toast-close"]').attributes('aria-label')).toBe('关闭通知');
    expect(wrapper.emitted('close')).toBeUndefined();
  });

  it.each(ALL_TYPES)(
    'type=%s 时带正确 class / icon / aria-live',
    (type) => {
      const wrapper = mount(PyToast, {
        props: { message: '消息', type },
      });
      const root = wrapper.get('[data-testid="py-toast"]');
      expect(root.classes()).toContain(`py-toast--${type}`);
      expect(root.attributes('aria-live')).toBe(TYPE_LIVE[type]);
      const iconNode = wrapper.find('.py-toast__icon');
      expect(iconNode.exists()).toBe(true);
      expect(iconNode.text()).toBe(TYPE_ICON[type]);
      expect(iconNode.attributes('aria-hidden')).toBe('true');
      expect(iconNode.attributes('data-icon')).toBe(type);
    },
  );

  it.each(POSITIONS)('position=%s 时 root 带 py-toast--%s class 与 data-position', (position) => {
    const wrapper = mount(PyToast, {
      props: { message: 'X', type: 'info', position },
    });
    const root = wrapper.get('[data-testid="py-toast"]');
    expect(root.classes()).toContain(`py-toast--${position}`);
    expect(root.attributes('data-position')).toBe(position);
  });

  it('duration 默认 3000ms 后自动 emit close 并卸载 DOM', async () => {
    const wrapper = mount(PyToast, {
      props: { message: '提示', type: 'info' },
    });
    expect(wrapper.find('[data-testid="py-toast"]').exists()).toBe(true);
    expect(wrapper.emitted('close')).toBeUndefined();

    vi.advanceTimersByTime(2999);
    await flushPromises();
    expect(wrapper.emitted('close')).toBeUndefined();

    vi.advanceTimersByTime(1);
    await flushPromises();
    expect(wrapper.emitted('close')).toHaveLength(1);
    expect(wrapper.find('[data-testid="py-toast"]').exists()).toBe(false);
  });

  it('自定义 duration=500ms 时按指定时长触发 close', async () => {
    const wrapper = mount(PyToast, {
      props: { message: 'X', type: 'warning', duration: 500 },
    });
    vi.advanceTimersByTime(500);
    await flushPromises();
    expect(wrapper.emitted('close')).toHaveLength(1);
  });

  it('duration<=0 时不会自动 close（持久 toast）', async () => {
    const wrapper = mount(PyToast, {
      props: { message: 'X', type: 'error', duration: 0 },
    });
    vi.advanceTimersByTime(10_000);
    await flushPromises();
    expect(wrapper.emitted('close')).toBeUndefined();
    expect(wrapper.find('[data-testid="py-toast"]').exists()).toBe(true);
  });

  it('点击关闭按钮立即 emit close 并卸载 DOM；不再触发自动 close', async () => {
    const wrapper = mount(PyToast, {
      props: { message: 'X', type: 'success', duration: 5000 },
    });
    await wrapper.get('[data-testid="py-toast-close"]').trigger('click');
    expect(wrapper.emitted('close')).toHaveLength(1);
    expect(wrapper.find('[data-testid="py-toast"]').exists()).toBe(false);

    vi.advanceTimersByTime(10_000);
    await flushPromises();
    expect(wrapper.emitted('close')).toHaveLength(1);
  });

  it('卸载组件时清理 timer，不再 emit close', async () => {
    const wrapper = mount(PyToast, {
      props: { message: 'X', type: 'success', duration: 1000 },
    });
    wrapper.unmount();
    vi.advanceTimersByTime(5000);
    await flushPromises();
    expect(wrapper.emitted('close')).toBeUndefined();
  });

  it('props.duration 变化时重启 timer（以新时长为准）', async () => {
    const wrapper = mount(PyToast, {
      props: { message: 'X', type: 'info', duration: 5000 },
    });
    vi.advanceTimersByTime(1000);
    await wrapper.setProps({ duration: 200 });
    vi.advanceTimersByTime(200);
    await flushPromises();
    expect(wrapper.emitted('close')).toHaveLength(1);
  });
});
