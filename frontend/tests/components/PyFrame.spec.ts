import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import PyFrame, {
  type PyFrameTail,
  type PyFrameTone,
  type PyFrameVariant,
} from '@/components/ui/PyFrame.vue';

type ImageHandler = ((event: Event) => void) | null;

interface FakeImageInstance {
  onload: ImageHandler;
  onerror: ImageHandler;
  src: string;
}

function installImage(
  emit: 'load' | 'error' | 'none',
): FakeImageInstance[] {
  const created: FakeImageInstance[] = [];

  class FakeImage implements FakeImageInstance {
    public onload: ImageHandler = null;
    public onerror: ImageHandler = null;
    private _src = '';

    public constructor() {
      created.push(this);
    }

    public get src(): string {
      return this._src;
    }

    public set src(value: string) {
      this._src = value;
      if (emit === 'none') return;
      queueMicrotask(() => {
        if (emit === 'error') {
          this.onerror?.(new Event('error'));
        } else {
          this.onload?.(new Event('load'));
        }
      });
    }
  }

  vi.stubGlobal('Image', FakeImage as unknown as typeof Image);
  return created;
}

const ALL_VARIANTS: readonly PyFrameVariant[] = [
  'panel',
  'dialog',
  'button',
  'card',
  'modal',
  'tooltip',
] as const;

const ALL_TONES: readonly PyFrameTone[] = [
  'default',
  'primary',
  'success',
  'warning',
  'danger',
] as const;

const TAILS: readonly Exclude<PyFrameTail, 'none'>[] = [
  'top',
  'right',
  'bottom',
  'left',
] as const;

const VARIANT_ROLE: Readonly<Record<PyFrameVariant, string>> = {
  panel: 'region',
  dialog: 'dialog',
  button: 'button',
  card: 'article',
  modal: 'dialog',
  tooltip: 'tooltip',
};

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('PyFrame', () => {
  it('默认 variant=panel 渲染 region role + 透传 slot 内容 + base class', () => {
    installImage('load');
    const wrapper = mount(PyFrame, {
      slots: { default: '<span class="probe">面板正文</span>' },
    });

    const root = wrapper.get('[data-testid="py-frame"]');
    expect(root.element.tagName).toBe('DIV');
    expect(root.attributes('role')).toBe('region');
    expect(root.classes()).toEqual(
      expect.arrayContaining([
        'py-frame',
        'py-frame--panel',
        'py-frame--tone-default',
      ]),
    );
    expect(root.attributes('data-variant')).toBe('panel');
    expect(wrapper.find('.probe').exists()).toBe(true);
    expect(wrapper.text()).toContain('面板正文');
  });

  it.each(ALL_VARIANTS)(
    'variant=%s 渲染时带 py-frame--%s class 与正确切片样式',
    (variant) => {
      installImage('load');
      const wrapper = mount(PyFrame, {
        props: { variant },
        slots: { default: 'X' },
      });
      const root = wrapper.get('[data-testid="py-frame"]');
      expect(root.classes()).toContain(`py-frame--${variant}`);
      expect(root.attributes('style')).toContain('border-image-source');
      expect(root.attributes('data-variant')).toBe(variant);
    },
  );

  it.each(ALL_TONES)(
    'tone=%s 时 root 元素带 py-frame--tone-%s class',
    (tone) => {
      installImage('load');
      const wrapper = mount(PyFrame, { props: { tone } });
      const root = wrapper.get('[data-testid="py-frame"]');
      expect(root.classes()).toContain(`py-frame--tone-${tone}`);
      expect(root.attributes('data-tone')).toBe(tone);
    },
  );

  it('button state=hover/pressed 时分别切换 class，并切换 PNG source 路径', async () => {
    installImage('load');
    const wrapper = mount(PyFrame, {
      props: { variant: 'button', state: 'default' },
      slots: { default: '提交' },
    });
    const root = wrapper.get('[data-testid="py-frame"]');
    expect(root.element.tagName).toBe('BUTTON');
    expect(root.classes()).toContain('py-frame--state-default');
    expect(root.attributes('style')).toContain('frame_button_default.png');

    await wrapper.setProps({ state: 'hover' });
    await flushPromises();
    expect(root.classes()).toContain('py-frame--state-hover');
    expect(root.classes()).not.toContain('py-frame--state-default');
    expect(root.attributes('style')).toContain('frame_button_hover.png');

    await wrapper.setProps({ state: 'pressed' });
    await flushPromises();
    expect(root.classes()).toContain('py-frame--state-pressed');
    expect(root.attributes('style')).toContain('frame_button_pressed.png');
  });

  it.each(TAILS)(
    'dialog variant + tail=%s 时渲染 tail span 并带 py-frame--tail-%s class',
    (tail) => {
      installImage('load');
      const wrapper = mount(PyFrame, {
        props: { variant: 'dialog', tail },
        slots: { default: 'Hello' },
      });
      const root = wrapper.get('[data-testid="py-frame"]');
      expect(root.classes()).toContain(`py-frame--tail-${tail}`);
      const tailNode = wrapper.get('[data-testid="py-frame-tail"]');
      expect(tailNode.classes()).toContain(`py-frame__tail--${tail}`);
      expect(tailNode.attributes('aria-hidden')).toBe('true');
    },
  );

  it('tail=none（默认）或非 dialog variant 时不渲染 tail span', () => {
    installImage('load');
    const noneTail = mount(PyFrame, {
      props: { variant: 'dialog', tail: 'none' },
    });
    expect(noneTail.find('[data-testid="py-frame-tail"]').exists()).toBe(false);

    const nonDialog = mount(PyFrame, {
      props: { variant: 'panel', tail: 'top' },
    });
    expect(nonDialog.find('[data-testid="py-frame-tail"]').exists()).toBe(false);
  });

  it('PNG 加载失败（Image emit error）时 root 元素带 py-frame--fallback class', async () => {
    installImage('error');
    const wrapper = mount(PyFrame, { props: { variant: 'panel' } });
    await flushPromises();
    const root = wrapper.get('[data-testid="py-frame"]');
    expect(root.classes()).toContain('py-frame--fallback');
  });

  it('PNG 加载成功时 root 元素不带 py-frame--fallback class', async () => {
    installImage('load');
    const wrapper = mount(PyFrame, { props: { variant: 'panel' } });
    await flushPromises();
    const root = wrapper.get('[data-testid="py-frame"]');
    expect(root.classes()).not.toContain('py-frame--fallback');
  });

  it.each(ALL_VARIANTS)(
    'role 属性按 variant=%s 推断为 %s',
    (variant) => {
      installImage('load');
      const wrapper = mount(PyFrame, { props: { variant } });
      const root = wrapper.get('[data-testid="py-frame"]');
      expect(root.attributes('role')).toBe(VARIANT_ROLE[variant]);
    },
  );

  it('button variant 时根元素是 <button> 且 type="button"，其他 variant 是 <div>', () => {
    installImage('load');
    const btn = mount(PyFrame, { props: { variant: 'button' } });
    const btnRoot = btn.get('[data-testid="py-frame"]');
    expect(btnRoot.element.tagName).toBe('BUTTON');
    expect(btnRoot.attributes('type')).toBe('button');

    for (const variant of ALL_VARIANTS) {
      if (variant === 'button') continue;
      const w = mount(PyFrame, { props: { variant } });
      const root = w.get('[data-testid="py-frame"]');
      expect(root.element.tagName).toBe('DIV');
      expect(root.attributes('type')).toBeUndefined();
    }
  });
});
