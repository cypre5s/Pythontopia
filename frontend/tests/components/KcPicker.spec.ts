import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import KcPicker from '@/components/feynman/KcPicker.vue';

describe('KcPicker', () => {
  it('聚焦后展示全部 65 个知识点，且只显示中文名不显示编码', async () => {
    const wrapper = mount(KcPicker);
    await wrapper.get('[data-testid="kc-picker-input"]').trigger('focus');

    const options = wrapper.findAll('[role="option"]');
    expect(options.length).toBe(65);
    const listText = wrapper.get('[data-testid="kc-picker-list"]').text();
    expect(listText).toContain('for 循环与遍历');
    expect(listText).not.toContain('PY43-KC-031');
  });

  it('按中文关键词模糊筛选知识点', async () => {
    const wrapper = mount(KcPicker);
    await wrapper.get('[data-testid="kc-picker-input"]').setValue('循环');

    const options = wrapper.findAll('[role="option"]');
    expect(options.length).toBeGreaterThan(0);
    expect(options.length).toBeLessThan(65);
    expect(wrapper.get('[data-testid="kc-picker-list"]').text()).toContain('for 循环与遍历');
  });

  it('支持按 KC 编码搜索（列表仍只显示中文名）', async () => {
    const wrapper = mount(KcPicker);
    await wrapper.get('[data-testid="kc-picker-input"]').setValue('031');

    expect(wrapper.get('[data-testid="kc-picker-list"]').text()).toContain('for 循环与遍历');
  });

  it('选中某项时 emit select 对应的 KC 编码', async () => {
    const wrapper = mount(KcPicker);
    await wrapper.get('[data-testid="kc-picker-input"]').setValue('for 循环');

    const option = wrapper
      .findAll('[role="option"]')
      .find((node) => node.text().includes('for 循环与遍历'));
    expect(option).toBeTruthy();
    await option!.trigger('mousedown');

    expect(wrapper.emitted('select')?.[0]).toEqual(['PY43-KC-031']);
  });
});
