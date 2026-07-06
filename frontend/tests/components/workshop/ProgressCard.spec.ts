import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import ProgressCard from '@/components/workshop/ProgressCard.vue';

// 7 个 KC，覆盖三个色档与「未学」（kcDisplayName 静态映射可直接断言中文名）。
const SNAPSHOT = [
  { kcCode: 'PY43-KC-010', masteryScore: 0.92 }, // 变量与运算符 · high
  { kcCode: 'PY43-KC-011', masteryScore: 0.81 }, // 基本数据类型 · high
  { kcCode: 'PY43-KC-031', masteryScore: 0.66 }, // for 循环与遍历 · mid
  { kcCode: 'PY43-KC-033', masteryScore: 0.45 }, // 分支结构 · mid
  { kcCode: 'PY43-KC-040', masteryScore: 0.3 }, // 列表基本操作 · low
  { kcCode: 'PY43-KC-042', masteryScore: 0.12 }, // 字典基本操作 · low
  { kcCode: 'PY43-KC-048', masteryScore: 0 }, // Python 文件操作 · 未学
];

describe('ProgressCard', () => {
  it('Top3 按掌握度降序取最好 3 个（KC 中文名 + 百分比）', () => {
    const wrapper = mount(ProgressCard, { props: { snapshot: SNAPSHOT } });

    const rows = wrapper.findAll('[data-testid="progress-top-row"]');
    expect(rows).toHaveLength(3);
    expect(rows[0]?.text()).toContain('变量与运算符');
    expect(rows[0]?.text()).toContain('92%');
    expect(rows[1]?.text()).toContain('基本数据类型');
    expect(rows[2]?.text()).toContain('for 循环与遍历');
  });

  it('Bottom3 取最弱 3 个（不与 Top3 重复，最弱在前），行尾带「去练 →」', () => {
    const wrapper = mount(ProgressCard, { props: { snapshot: SNAPSHOT } });

    const rows = wrapper.findAll('[data-testid="progress-bottom-row"]');
    expect(rows).toHaveLength(3);
    expect(rows[0]?.text()).toContain('Python 文件操作');
    expect(rows[1]?.text()).toContain('字典基本操作');
    expect(rows[2]?.text()).toContain('列表基本操作');
    expect(wrapper.findAll('[data-testid="progress-practice"]')).toHaveLength(3);
  });

  it('进度条按掌握档位分色：<0.4 红 / 0.4–0.7 黄 / >0.7 绿', () => {
    const wrapper = mount(ProgressCard, { props: { snapshot: SNAPSHOT } });

    const topBars = wrapper.findAll('[data-testid="progress-top-row"] .progress__bar');
    expect(topBars[0]?.classes()).toContain('progress__bar--high'); // 0.92
    expect(topBars[2]?.classes()).toContain('progress__bar--mid'); // 0.66

    const bottomBars = wrapper.findAll('[data-testid="progress-bottom-row"] .progress__bar');
    expect(bottomBars[0]?.classes()).toContain('progress__bar--low'); // 0
    expect(bottomBars[2]?.classes()).toContain('progress__bar--low'); // 0.3
  });

  it('score≈0 的 KC 保留展示但标「未学」', () => {
    const wrapper = mount(ProgressCard, { props: { snapshot: SNAPSHOT } });

    const unlearned = wrapper.findAll('[data-testid="progress-unlearned"]');
    expect(unlearned).toHaveLength(1);
    const firstBottom = wrapper.findAll('[data-testid="progress-bottom-row"]')[0];
    expect(firstBottom?.text()).toContain('未学');
  });

  it('点击「去练 →」→ emit practice(kcCode)', async () => {
    const wrapper = mount(ProgressCard, { props: { snapshot: SNAPSHOT } });

    await wrapper.findAll('[data-testid="progress-practice"]')[0]?.trigger('click');
    expect(wrapper.emitted('practice')).toEqual([['PY43-KC-048']]);
  });

  it('snapshot 为空 → 渲染空态文案，无任何行', () => {
    const wrapper = mount(ProgressCard, { props: { snapshot: [] } });

    expect(wrapper.find('[data-testid="progress-empty"]').exists()).toBe(true);
    expect(wrapper.findAll('[data-testid="progress-top-row"]')).toHaveLength(0);
    expect(wrapper.findAll('[data-testid="progress-bottom-row"]')).toHaveLength(0);
  });

  it('条目 ≤3 时全部进 Top3，「待加强」组整组不渲染', () => {
    const wrapper = mount(ProgressCard, {
      props: {
        snapshot: [
          { kcCode: 'PY43-KC-010', masteryScore: 0.8 },
          { kcCode: 'PY43-KC-031', masteryScore: 0.2 },
        ],
      },
    });

    expect(wrapper.findAll('[data-testid="progress-top-row"]')).toHaveLength(2);
    expect(wrapper.findAll('[data-testid="progress-bottom-row"]')).toHaveLength(0);
    expect(wrapper.findAll('[data-testid="progress-practice"]')).toHaveLength(0);
  });
});
