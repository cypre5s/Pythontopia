import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import PlanCard from '@/components/workshop/PlanCard.vue';

const STEPS = [
  { id: 's1', phase: 'watch', title: '看：for 循环讲解视频', detail: '先建立直觉', panel: null },
  { id: 's2', phase: 'learn', title: '学：生成讲义', detail: '重点看遍历写法', panel: 'handout' },
  { id: 's3', phase: 'practice', title: '练：做 2 道题', detail: '紧扣讲义要点', panel: 'arena' },
  { id: 's4', phase: 'assess', title: '测：口头小测', detail: '3 轮考核', panel: null },
];

describe('PlanCard', () => {
  it('渲染标题「📋 你的学习计划」与按序的步骤列表', () => {
    const wrapper = mount(PlanCard, { props: { steps: STEPS } });

    expect(wrapper.text()).toContain('📋 你的学习计划');
    const steps = wrapper.findAll('[data-testid="plan-step"]');
    expect(steps).toHaveLength(4);
    expect(steps[0]?.text()).toContain('看：for 循环讲解视频');
    expect(steps[0]?.text()).toContain('先建立直觉');
    expect(steps[3]?.text()).toContain('测：口头小测');
  });

  it('阶段徽章映射 watch=看 / learn=学 / practice=练 / assess=测', () => {
    const wrapper = mount(PlanCard, { props: { steps: STEPS } });

    const badges = wrapper.findAll('.plan__badge');
    expect(badges.map((b) => b.text())).toEqual(['看', '学', '练', '测']);
    expect(badges[0]?.classes()).toContain('plan__badge--watch');
    expect(badges[3]?.classes()).toContain('plan__badge--assess');
  });

  it('未知 phase 原样显示（不报错）', () => {
    const wrapper = mount(PlanCard, {
      props: {
        steps: [{ id: 'x', phase: 'review', title: '复盘', detail: '', panel: null }],
      },
    });

    expect(wrapper.get('.plan__badge').text()).toBe('review');
    expect(wrapper.get('.plan__badge').classes()).toContain('plan__badge--other');
  });

  it('panel 非空才渲染「前往 →」，点击 emit open(panel)', async () => {
    const wrapper = mount(PlanCard, { props: { steps: STEPS } });

    const opens = wrapper.findAll('[data-testid="plan-open"]');
    expect(opens).toHaveLength(2); // s2 / s3；panel=null 的 s1 / s4 不出按钮

    await opens[0]?.trigger('click');
    expect(wrapper.emitted('open')).toEqual([['handout']]);

    await opens[1]?.trigger('click');
    expect(wrapper.emitted('open')).toEqual([['handout'], ['arena']]);
  });

  it('steps 为空仍渲染卡壳（零步骤不崩）', () => {
    const wrapper = mount(PlanCard, { props: { steps: [] } });

    expect(wrapper.find('[data-testid="plan-card"]').exists()).toBe(true);
    expect(wrapper.findAll('[data-testid="plan-step"]')).toHaveLength(0);
  });

  it('按 status 点亮步骤：done 打勾降权 / active 进行 / failed 叉 / pending 无图标', () => {
    const wrapper = mount(PlanCard, {
      props: {
        steps: [
          { id: 's1', phase: 'watch', title: '看视频', detail: '', panel: null, status: 'done' },
          { id: 's2', phase: 'learn', title: '生成讲义', detail: '', panel: null, status: 'active' },
          { id: 's3', phase: 'practice', title: '做题', detail: '', panel: null, status: 'failed' },
          { id: 's4', phase: 'assess', title: '小测', detail: '', panel: null, status: 'pending' },
        ],
      },
    });

    const steps = wrapper.findAll('[data-testid="plan-step"]');
    expect(steps[0]?.classes()).toContain('plan__step--done');
    expect(steps[1]?.classes()).toContain('plan__step--active');
    expect(steps[2]?.classes()).toContain('plan__step--failed');
    expect(steps[3]?.classes()).toContain('plan__step--pending');

    // pending 不出图标 → 仅 done/active/failed 三个进度图标。
    const icons = wrapper.findAll('[data-testid="plan-step-status"]');
    expect(icons).toHaveLength(3);
    expect(steps[0]?.text()).toContain('✓');
    expect(steps[1]?.text()).toContain('▶');
    expect(steps[2]?.text()).toContain('✗');
  });

  it('无 status 字段（历史/静态卡）默认 pending、不出进度图标', () => {
    const wrapper = mount(PlanCard, { props: { steps: STEPS } });

    const steps = wrapper.findAll('[data-testid="plan-step"]');
    expect(steps[0]?.classes()).toContain('plan__step--pending');
    expect(wrapper.findAll('[data-testid="plan-step-status"]')).toHaveLength(0);
  });
});
