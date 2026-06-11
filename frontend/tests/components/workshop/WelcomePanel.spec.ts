import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import WelcomePanel from '@/components/workshop/WelcomePanel.vue';
import { SLASH_COMMANDS } from '@/composables/useSlashCommand';

describe('WelcomePanel', () => {
  it('渲染百晓头像、标题与 4 个快捷提问 chips（完整文本）', () => {
    const wrapper = mount(WelcomePanel);

    expect(wrapper.find('[data-testid="workshop-avatar"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('想学什么直接说');

    const chips = wrapper.findAll('[data-testid="welcome-quick-chip"]');
    expect(chips).toHaveLength(4);
    expect(chips.map((c) => c.text())).toEqual([
      '什么是列表推导式？',
      '/考考我 循环',
      '出几道函数的练习',
      '我学得怎么样？',
    ]);
  });

  it('点击快捷 chip → emit quick(完整文本，含 / 命令前缀)', async () => {
    const wrapper = mount(WelcomePanel);
    const chips = wrapper.findAll('[data-testid="welcome-quick-chip"]');

    await chips[1]?.trigger('click');
    expect(wrapper.emitted('quick')).toEqual([['/考考我 循环']]);

    await chips[3]?.trigger('click');
    expect(wrapper.emitted('quick')).toEqual([['/考考我 循环'], ['我学得怎么样？']]);
  });

  it('「能力一览」按 SLASH_COMMANDS 实际内容渲染 icon+label+hint 与分组名', () => {
    const wrapper = mount(WelcomePanel);
    const abilities = wrapper.get('[data-testid="welcome-abilities"]');

    // 按数组实际内容渲染：WP5 后续扩充命令后本断言依然成立（动态对账）。
    const cells = abilities.findAll('.welcome__cell');
    expect(cells).toHaveLength(SLASH_COMMANDS.length);

    const text = abilities.text();
    for (const cmd of SLASH_COMMANDS) {
      expect(text).toContain(cmd.label);
      expect(text).toContain(cmd.hint);
    }
    for (const group of new Set(SLASH_COMMANDS.map((c) => c.group))) {
      expect(text).toContain(group);
    }
  });

  it('能力一览默认折叠（details 无 open 属性）', () => {
    const wrapper = mount(WelcomePanel);
    expect(wrapper.get('[data-testid="welcome-abilities"]').attributes('open')).toBeUndefined();
  });
});
