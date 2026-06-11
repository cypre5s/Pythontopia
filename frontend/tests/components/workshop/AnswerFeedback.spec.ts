import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import AnswerFeedback from '@/components/workshop/AnswerFeedback.vue';

describe('AnswerFeedback', () => {
  it('渲染 👍/👎 两个常显小按钮，messageId 写在根节点供定位', () => {
    const wrapper = mount(AnswerFeedback, { props: { messageId: 'm-1' } });

    expect(wrapper.attributes('data-message-id')).toBe('m-1');
    expect(wrapper.get('[data-testid="feedback-up"]').text()).toBe('👍');
    expect(wrapper.get('[data-testid="feedback-down"]').text()).toBe('👎');
    expect(wrapper.find('[data-testid="feedback-done"]').exists()).toBe(false);
  });

  it('点 👍 → emit feedback(true)，按钮变实心 + 「已记录」', async () => {
    const wrapper = mount(AnswerFeedback, { props: { messageId: 'm-1' } });

    await wrapper.get('[data-testid="feedback-up"]').trigger('click');

    expect(wrapper.emitted('feedback')).toEqual([[true]]);
    expect(wrapper.get('[data-testid="feedback-up"]').classes()).toContain(
      'feedback__btn--active',
    );
    expect(wrapper.get('[data-testid="feedback-done"]').text()).toBe('已记录');
  });

  it('点 👎 → emit feedback(false)', async () => {
    const wrapper = mount(AnswerFeedback, { props: { messageId: 'm-2' } });

    await wrapper.get('[data-testid="feedback-down"]').trigger('click');

    expect(wrapper.emitted('feedback')).toEqual([[false]]);
    expect(wrapper.get('[data-testid="feedback-down"]').classes()).toContain(
      'feedback__btn--active',
    );
  });

  it('已反馈后再点任一按钮无效（不再 emit）', async () => {
    const wrapper = mount(AnswerFeedback, { props: { messageId: 'm-3' } });

    await wrapper.get('[data-testid="feedback-down"]').trigger('click');
    await wrapper.get('[data-testid="feedback-down"]').trigger('click');
    await wrapper.get('[data-testid="feedback-up"]').trigger('click');

    expect(wrapper.emitted('feedback')).toEqual([[false]]);
    // 已反馈态下两个按钮均禁用（hover 不再提亮、点击无效）。
    expect(wrapper.get('[data-testid="feedback-up"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-testid="feedback-down"]').attributes('disabled')).toBeDefined();
  });
});
