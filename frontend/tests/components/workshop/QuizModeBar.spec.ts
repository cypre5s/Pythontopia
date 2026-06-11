import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import QuizModeBar from '@/components/workshop/QuizModeBar.vue';

describe('QuizModeBar', () => {
  it('渲染「✓ 考核模式 · 第 2/3 轮」与轮次进度点（2 实 1 空）', () => {
    const wrapper = mount(QuizModeBar, { props: { round: 2, total: 3 } });

    expect(wrapper.text()).toContain('✓ 考核模式 · 第 2/3 轮');

    const dots = wrapper.findAll('[data-testid="quiz-dot"]');
    expect(dots).toHaveLength(3);
    const filled = dots.filter((d) => d.classes().includes('quizbar__dot--filled'));
    expect(filled).toHaveLength(2);
    expect(dots.map((d) => d.text()).join('')).toBe('●●○');
  });

  it('kcLabel 给定时显示 KC 徽章；缺省不渲染', () => {
    const withKc = mount(QuizModeBar, {
      props: { round: 1, total: 3, kcLabel: 'for 循环与遍历' },
    });
    expect(withKc.get('[data-testid="quiz-kc-badge"]').text()).toBe('for 循环与遍历');

    const without = mount(QuizModeBar, { props: { round: 1, total: 3 } });
    expect(without.find('[data-testid="quiz-kc-badge"]').exists()).toBe(false);
  });

  it('点击「退出考核」→ emit exit', async () => {
    const wrapper = mount(QuizModeBar, { props: { round: 1, total: 3 } });

    await wrapper.get('[data-testid="quiz-exit"]').trigger('click');
    expect(wrapper.emitted('exit')).toHaveLength(1);
  });

  it('边界：round=0 全空点；round 超过 total 全实点（不越界报错）', () => {
    const zero = mount(QuizModeBar, { props: { round: 0, total: 3 } });
    expect(
      zero.findAll('[data-testid="quiz-dot"]').filter((d) => d.classes().includes('quizbar__dot--filled')),
    ).toHaveLength(0);

    const over = mount(QuizModeBar, { props: { round: 9, total: 3 } });
    const dots = over.findAll('[data-testid="quiz-dot"]');
    expect(dots).toHaveLength(3);
    expect(dots.filter((d) => d.classes().includes('quizbar__dot--filled'))).toHaveLength(3);
  });
});
