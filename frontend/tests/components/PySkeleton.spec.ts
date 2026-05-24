import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import PySkeleton, {
  type PySkeletonVariant,
} from '@/components/ui/PySkeleton.vue';

const ALL_VARIANTS: readonly PySkeletonVariant[] = ['text', 'circle', 'rect'] as const;

interface FakeMql {
  matches: boolean;
  media: string;
  onchange: null;
  addEventListener: ReturnType<typeof vi.fn>;
  removeEventListener: ReturnType<typeof vi.fn>;
  addListener: ReturnType<typeof vi.fn>;
  removeListener: ReturnType<typeof vi.fn>;
  dispatchEvent: ReturnType<typeof vi.fn>;
}

function installMatchMedia(matches: boolean): FakeMql {
  const mql: FakeMql = {
    matches,
    media: '(prefers-reduced-motion: reduce)',
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  };
  vi.stubGlobal(
    'matchMedia',
    vi.fn().mockImplementation(() => mql),
  );
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    writable: true,
    value: vi.fn().mockImplementation(() => mql),
  });
  return mql;
}

beforeEach(() => {
  installMatchMedia(false);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('PySkeleton', () => {
  it('默认渲染：variant=text, count=1，root 元素带 role=status + aria-busy=true', () => {
    const wrapper = mount(PySkeleton);
    const root = wrapper.get('[data-testid="py-skeleton"]');
    expect(root.attributes('role')).toBe('status');
    expect(root.attributes('aria-busy')).toBe('true');
    expect(root.attributes('aria-live')).toBe('polite');
    expect(root.attributes('data-variant')).toBe('text');
    expect(root.attributes('data-count')).toBe('1');
    const items = wrapper.findAll('[data-testid="py-skeleton-item"]');
    expect(items).toHaveLength(1);
    expect(root.classes()).toContain('py-skeleton--text');
  });

  it.each(ALL_VARIANTS)(
    'variant=%s 时 root 元素带 py-skeleton--%s class，且子项数等于 count',
    (variant) => {
      const wrapper = mount(PySkeleton, {
        props: { variant, count: 3 },
      });
      const root = wrapper.get('[data-testid="py-skeleton"]');
      expect(root.classes()).toContain(`py-skeleton--${variant}`);
      expect(root.attributes('data-variant')).toBe(variant);
      const items = wrapper.findAll('[data-testid="py-skeleton-item"]');
      expect(items).toHaveLength(3);
    },
  );

  it('width=数字时自动加 px 单位；字符串原样保留', () => {
    const numeric = mount(PySkeleton, {
      props: { variant: 'rect', width: 120, height: 16, count: 1 },
    });
    const numItem = numeric.get('[data-testid="py-skeleton-item"]');
    expect(numItem.attributes('style')).toContain('width: 120px');
    expect(numItem.attributes('style')).toContain('height: 16px');

    const literal = mount(PySkeleton, {
      props: { variant: 'rect', width: '50%', height: '2rem' },
    });
    const litItem = literal.get('[data-testid="py-skeleton-item"]');
    expect(litItem.attributes('style')).toContain('width: 50%');
    expect(litItem.attributes('style')).toContain('height: 2rem');
  });

  it('variant=text 且 count>1 时，最后一项宽度通过 calc(* 0.75) 收窄（浏览器可简化为 calc(75%)）', () => {
    const wrapper = mount(PySkeleton, {
      props: { variant: 'text', count: 4, width: '100%' },
    });
    const items = wrapper.findAll('[data-testid="py-skeleton-item"]');
    expect(items).toHaveLength(4);
    expect(items[0].attributes('style')).toContain('width: 100%');
    const lastStyle = items[3].attributes('style') ?? '';
    expect(lastStyle).toMatch(/calc\((100% \* 0\.75|75%)\)/);
    expect(lastStyle).not.toBe(items[0].attributes('style'));
  });

  it('variant=circle 时强制 1:1（用 height 作为直径）并 border-radius 50%', () => {
    const wrapper = mount(PySkeleton, {
      props: { variant: 'circle', width: 999, height: 32, count: 2 },
    });
    const items = wrapper.findAll('[data-testid="py-skeleton-item"]');
    for (const item of items) {
      const style = item.attributes('style') ?? '';
      expect(style).toContain('width: 32px');
      expect(style).toContain('height: 32px');
      expect(style).toContain('border-radius: 50%');
      expect(style).not.toContain('999');
    }
  });

  it('count=0 / 负数 / NaN 时降级为 1 个，保证至少有一个占位', () => {
    const zero = mount(PySkeleton, { props: { count: 0 } });
    expect(zero.findAll('[data-testid="py-skeleton-item"]')).toHaveLength(1);
    expect(zero.get('[data-testid="py-skeleton"]').attributes('data-count')).toBe('1');

    const negative = mount(PySkeleton, { props: { count: -3 } });
    expect(negative.findAll('[data-testid="py-skeleton-item"]')).toHaveLength(1);

    const nan = mount(PySkeleton, { props: { count: Number.NaN } });
    expect(nan.findAll('[data-testid="py-skeleton-item"]')).toHaveLength(1);
  });

  it('prefers-reduced-motion=reduce 时 root 元素带 py-skeleton--reduced-motion class', () => {
    installMatchMedia(true);
    const wrapper = mount(PySkeleton, { props: { count: 2 } });
    const root = wrapper.get('[data-testid="py-skeleton"]');
    expect(root.classes()).toContain('py-skeleton--reduced-motion');
  });

  it('prefers-reduced-motion=no-preference 时不带 reduced-motion class', () => {
    installMatchMedia(false);
    const wrapper = mount(PySkeleton, { props: { count: 2 } });
    const root = wrapper.get('[data-testid="py-skeleton"]');
    expect(root.classes()).not.toContain('py-skeleton--reduced-motion');
  });

  it('每个 item 带 aria-hidden=true（仅装饰），不被屏读器读出', () => {
    const wrapper = mount(PySkeleton, { props: { count: 3 } });
    const items = wrapper.findAll('[data-testid="py-skeleton-item"]');
    for (const item of items) {
      expect(item.attributes('aria-hidden')).toBe('true');
    }
  });
});
