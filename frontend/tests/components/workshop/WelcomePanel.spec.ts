import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import WelcomePanel from '@/components/workshop/WelcomePanel.vue';

describe('WelcomePanel', () => {
  it('渲染百晓头像、标题与 3 个快捷提问 chips（完整文本）', () => {
    const wrapper = mount(WelcomePanel);

    expect(wrapper.find('[data-testid="workshop-avatar"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('想学什么直接说');

    const chips = wrapper.findAll('[data-testid="welcome-quick-chip"]');
    expect(chips).toHaveLength(3);
    expect(chips.map((c) => c.text())).toEqual([
      '我想学循环，先从哪里开始？',
      '解释一下这段代码为什么这么写',
      '出一道函数的小练习',
    ]);
  });

  it('点击快捷 chip → emit quick(完整文本，含 / 命令前缀)', async () => {
    const wrapper = mount(WelcomePanel);
    const chips = wrapper.findAll('[data-testid="welcome-quick-chip"]');

    await chips[1]?.trigger('click');
    expect(wrapper.emitted('quick')).toEqual([['解释一下这段代码为什么这么写']]);

    await chips[2]?.trigger('click');
    expect(wrapper.emitted('quick')).toEqual([
      ['解释一下这段代码为什么这么写'],
      ['出一道函数的小练习'],
    ]);
  });

  it('默认不展示能力网格', () => {
    const wrapper = mount(WelcomePanel);

    expect(wrapper.find('[data-testid="welcome-abilities"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="welcome-open-capabilities"]').exists()).toBe(true);
  });

  it('点击后按 6 个能力组展示 capability cards', async () => {
    const wrapper = mount(WelcomePanel);

    await wrapper.get('[data-testid="welcome-open-capabilities"]').trigger('click');

    const cards = wrapper.findAll('[data-testid="welcome-capability-card"]');
    expect(cards).toHaveLength(6);
    expect(wrapper.text()).toContain('问一问');
    expect(wrapper.text()).toContain('考考我');
    expect(wrapper.text()).toContain('生成资料');
    expect(wrapper.text()).toContain('练一练');
    expect(wrapper.text()).toContain('规划学习');
    expect(wrapper.text()).toContain('找内容/带路');
  });
});
