import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import ClarifyBubble from '@/components/workshop/ClarifyBubble.vue';

const OPTIONS = [
  { intent: 'ASK', label: '问答', icon: '?' },
  { intent: 'PRACTICE', label: '练习题', icon: '✪' },
  { intent: 'PLAN', label: '学习规划', icon: '☷' },
];

describe('ClarifyBubble', () => {
  it('渲染澄清问题与全部候选 chips（icon+label）', () => {
    const wrapper = mount(ClarifyBubble, { props: { options: OPTIONS } });

    expect(wrapper.text()).toContain('我不太确定你想要什么——你是想？');
    const chips = wrapper.findAll('.clarify__chip');
    expect(chips).toHaveLength(3);
    expect(chips[0]?.text()).toContain('?');
    expect(chips[0]?.text()).toContain('问答');
    expect(chips[2]?.text()).toContain('学习规划');
  });

  it('点击候选 chip → emit pick(intent)', async () => {
    const wrapper = mount(ClarifyBubble, { props: { options: OPTIONS } });

    await wrapper.get('[data-testid="clarify-option-PRACTICE"]').trigger('click');
    expect(wrapper.emitted('pick')).toEqual([['PRACTICE']]);
  });

  it('reason 非空显示路由理由小字；缺省不渲染', () => {
    const withReason = mount(ClarifyBubble, {
      props: { options: OPTIONS, reason: '命中多个关键词' },
    });
    expect(withReason.get('[data-testid="clarify-reason"]').text()).toContain('命中多个关键词');

    const without = mount(ClarifyBubble, { props: { options: OPTIONS } });
    expect(without.find('[data-testid="clarify-reason"]').exists()).toBe(false);
  });

  it('options 为空仍渲染澄清问题，零 chips 不崩', () => {
    const wrapper = mount(ClarifyBubble, { props: { options: [] } });

    expect(wrapper.text()).toContain('我不太确定');
    expect(wrapper.findAll('.clarify__chip')).toHaveLength(0);
  });
});
