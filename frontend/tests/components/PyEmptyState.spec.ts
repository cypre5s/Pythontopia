import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import PyEmptyState from '@/components/ui/PyEmptyState.vue';
import zhCN from '@/i18n/zh-CN.json';

afterEach(() => {
  vi.restoreAllMocks();
});

describe('PyEmptyState', () => {
  it('默认渲染：role=status + aria-live=polite + 必填 title + 默认无 description / 无 icon 时显示占位三点', () => {
    const wrapper = mount(PyEmptyState, {
      props: { title: '背包空空的' },
    });
    const root = wrapper.get('[data-testid="py-empty-state"]');
    expect(root.attributes('role')).toBe('status');
    expect(root.attributes('aria-live')).toBe('polite');
    expect(wrapper.get('[data-testid="py-empty-state-title"]').text()).toBe('背包空空的');
    expect(wrapper.find('[data-testid="py-empty-state-description"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="py-empty-state-icon"]').exists()).toBe(false);
    const placeholders = wrapper.findAll('.py-empty-state__placeholder-dot');
    expect(placeholders).toHaveLength(3);
    expect(wrapper.find('[data-testid="py-empty-state-actions"]').exists()).toBe(false);
  });

  it('description prop 显式传入时优先渲染（不依赖 i18n 回退）', () => {
    const wrapper = mount(PyEmptyState, {
      props: {
        title: '错题本是空的',
        description: '写错也没关系，先去做题积累',
      },
    });
    const desc = wrapper.get('[data-testid="py-empty-state-description"]');
    expect(desc.text()).toBe('写错也没关系，先去做题积累');
  });

  it('未传 description 但 i18nKey=inventory 时自动从 i18n empty.inventory 取值', () => {
    const expected = (zhCN as { empty: Record<string, string> }).empty.inventory;
    const wrapper = mount(PyEmptyState, {
      props: { title: '背包空空的', i18nKey: 'inventory' },
    });
    const desc = wrapper.get('[data-testid="py-empty-state-description"]');
    expect(desc.text()).toBe(expected);
    const root = wrapper.get('[data-testid="py-empty-state"]');
    expect(root.attributes('data-i18n-key')).toBe('inventory');
  });

  it('i18nKey 指向不存在的 key 时优雅降级为不渲染 description', () => {
    const wrapper = mount(PyEmptyState, {
      props: { title: 'X', i18nKey: 'definitely_not_a_key_in_empty_namespace' },
    });
    expect(wrapper.find('[data-testid="py-empty-state-description"]').exists()).toBe(false);
  });

  it('icon 是 emoji / 文字时渲染 <span> + aria-hidden=true，且不渲染占位三点', () => {
    const wrapper = mount(PyEmptyState, {
      props: { title: '背包空空的', icon: '📭' },
    });
    const iconWrap = wrapper.get('[data-testid="py-empty-state-icon"]');
    expect(iconWrap.exists()).toBe(true);
    expect(wrapper.find('.py-empty-state__placeholder-dot').exists()).toBe(false);
    const glyph = wrapper.get('.py-empty-state__icon-glyph');
    expect(glyph.element.tagName).toBe('SPAN');
    expect(glyph.text()).toBe('📭');
    expect(glyph.attributes('aria-hidden')).toBe('true');
    expect(wrapper.find('.py-empty-state__icon-img').exists()).toBe(false);
  });

  it('icon 是图片 URL（.png / 绝对路径 / http）时渲染 <img> + alt="" + aria-hidden', () => {
    const wrapper = mount(PyEmptyState, {
      props: { title: 'X', icon: '/assets/pixel/icons/icon_quest.png' },
    });
    const img = wrapper.get('.py-empty-state__icon-img');
    expect(img.element.tagName).toBe('IMG');
    expect(img.attributes('src')).toBe('/assets/pixel/icons/icon_quest.png');
    expect(img.attributes('alt')).toBe('');
    expect(img.attributes('aria-hidden')).toBe('true');
    expect(wrapper.find('.py-empty-state__icon-glyph').exists()).toBe(false);
  });

  it('提供默认 slot 时渲染 actions 容器；slot 内容可被点击触发父级 handler', async () => {
    const onClick = vi.fn();
    const wrapper = mount(PyEmptyState, {
      props: { title: '背包空空的' },
      slots: {
        default: `<button type="button" data-testid="cta">去采集</button>`,
      },
      attrs: {
        onClick,
      },
    });
    const actions = wrapper.get('[data-testid="py-empty-state-actions"]');
    expect(actions.exists()).toBe(true);
    const cta = wrapper.get('[data-testid="cta"]');
    expect(cta.text()).toBe('去采集');
    await cta.trigger('click');
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('description prop 优先于 i18nKey 取值（显式 > 隐式）', () => {
    const wrapper = mount(PyEmptyState, {
      props: {
        title: 'X',
        description: '自定义副文案',
        i18nKey: 'inventory',
      },
    });
    expect(wrapper.get('[data-testid="py-empty-state-description"]').text()).toBe(
      '自定义副文案',
    );
  });

  it('title 始终作为 <h3> 渲染（不被 i18nKey 覆盖），保证语义结构稳定', () => {
    const wrapper = mount(PyEmptyState, {
      props: { title: '背包空空的', i18nKey: 'inventory' },
    });
    const title = wrapper.get('[data-testid="py-empty-state-title"]');
    expect(title.element.tagName).toBe('H3');
    expect(title.text()).toBe('背包空空的');
  });
});
