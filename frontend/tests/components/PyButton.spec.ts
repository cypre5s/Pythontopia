import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import PyButton from '@/components/ui/PyButton.vue';

describe('PyButton', () => {
  it('渲染默认 slot 文案，默认 primary + md + type=button', () => {
    const wrapper = mount(PyButton, { slots: { default: '生成' } });
    const btn = wrapper.get('[data-testid="py-button"]');
    expect(btn.text()).toBe('生成');
    expect(btn.classes()).toContain('py-btn--primary');
    expect(btn.classes()).toContain('py-btn--md');
    expect(btn.attributes('type')).toBe('button');
  });

  it('variant / size / block 映射到 class', () => {
    const wrapper = mount(PyButton, {
      props: { variant: 'ghost', size: 'sm', block: true },
      slots: { default: 'x' },
    });
    const btn = wrapper.get('[data-testid="py-button"]');
    expect(btn.classes()).toContain('py-btn--ghost');
    expect(btn.classes()).toContain('py-btn--sm');
    expect(btn.classes()).toContain('py-btn--block');
  });

  it('点击触发 click 事件', async () => {
    const wrapper = mount(PyButton, { slots: { default: 'go' } });
    await wrapper.get('[data-testid="py-button"]').trigger('click');
    expect(wrapper.emitted('click')).toHaveLength(1);
  });

  it('disabled 时按钮禁用且不触发 click', async () => {
    const wrapper = mount(PyButton, { props: { disabled: true }, slots: { default: 'go' } });
    const btn = wrapper.get('[data-testid="py-button"]');
    expect(btn.attributes('disabled')).toBeDefined();
    await btn.trigger('click');
    expect(wrapper.emitted('click')).toBeUndefined();
  });

  it('type=submit 透传到原生按钮', () => {
    const wrapper = mount(PyButton, { props: { type: 'submit' }, slots: { default: 'ok' } });
    expect(wrapper.get('[data-testid="py-button"]').attributes('type')).toBe('submit');
  });
});
