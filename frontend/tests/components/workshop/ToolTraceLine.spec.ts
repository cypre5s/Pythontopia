import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import ToolTraceLine from '@/components/workshop/ToolTraceLine.vue';

const TRACES = [
  { node: 'evidence_fetcher', status: 'ok', elapsed_ms: 320 },
  { node: 'TutorAgent', status: 'ok', elapsed_ms: 2100, note: null },
  { node: 'TutorAgent.revise', status: 'retry', elapsed_ms: 900 },
  { node: 'grounding_critic', status: 'error', elapsed_ms: 80, note: 'fallback: 关键词路由' },
  { node: 'mystery_node', status: 'ok', elapsed_ms: 10, note: 'budget_skipped' },
];

describe('ToolTraceLine', () => {
  it('traces 为空 → 整行不渲染（v-if）', () => {
    const wrapper = mount(ToolTraceLine, { props: { traces: [] } });
    expect(wrapper.find('[data-testid="tool-trace-line"]').exists()).toBe(false);
  });

  it('summary 显示「⚙ 工具调用 ×N」+ 总耗时，默认折叠（渐进披露）', () => {
    const wrapper = mount(ToolTraceLine, { props: { traces: TRACES } });

    const details = wrapper.get('[data-testid="tool-trace-line"]');
    expect(details.attributes('open')).toBeUndefined();
    const summary = details.get('summary');
    expect(summary.text()).toContain('⚙ 工具调用 ×5');
    expect(summary.text()).toContain('3.4s'); // 320+2100+900+80+10 = 3410ms
  });

  it('node 中文化映射（含 TutorAgent.revise），未知 node 原样显示', () => {
    const wrapper = mount(ToolTraceLine, { props: { traces: TRACES } });

    const rows = wrapper.findAll('[data-testid="tool-trace-row"]');
    expect(rows).toHaveLength(5);
    expect(rows[0]?.text()).toContain('证据检索');
    expect(rows[1]?.text()).toContain('导师作答');
    expect(rows[2]?.text()).toContain('导师返工');
    expect(rows[3]?.text()).toContain('引用审查');
    expect(rows[4]?.text()).toContain('mystery_node');
  });

  it('状态点分色：ok 绿 / retry 黄 / error 红', () => {
    const wrapper = mount(ToolTraceLine, { props: { traces: TRACES } });

    const rows = wrapper.findAll('[data-testid="tool-trace-row"]');
    expect(rows[0]?.get('.trace__dot').classes()).toContain('trace__dot--ok');
    expect(rows[2]?.get('.trace__dot').classes()).toContain('trace__dot--retry');
    expect(rows[3]?.get('.trace__dot').classes()).toContain('trace__dot--error');
  });

  it('耗时逐条展示（ms）', () => {
    const wrapper = mount(ToolTraceLine, { props: { traces: TRACES } });

    const rows = wrapper.findAll('[data-testid="tool-trace-row"]');
    expect(rows[0]?.text()).toContain('320ms');
    expect(rows[1]?.text()).toContain('2100ms');
  });

  it('note 含 fallback / budget_skipped → 显示「已降级 / 预算跳过」徽', () => {
    const wrapper = mount(ToolTraceLine, { props: { traces: TRACES } });

    const rows = wrapper.findAll('[data-testid="tool-trace-row"]');
    expect(rows[3]?.find('[data-testid="tool-trace-badge"]').text()).toBe('已降级');
    expect(rows[4]?.find('[data-testid="tool-trace-badge"]').text()).toBe('预算跳过');
    // 无治理关键词的行不出徽章
    expect(rows[0]?.find('[data-testid="tool-trace-badge"]').exists()).toBe(false);
  });

  it('普通 note 以小字原样披露（不与徽章重复）', () => {
    const wrapper = mount(ToolTraceLine, {
      props: { traces: [{ node: 'debate', status: 'ok', elapsed_ms: 50, note: '复核通过' }] },
    });

    const row = wrapper.get('[data-testid="tool-trace-row"]');
    expect(row.text()).toContain('辩论复核');
    expect(row.text()).toContain('复核通过');
    expect(row.find('[data-testid="tool-trace-badge"]').exists()).toBe(false);
  });
});
