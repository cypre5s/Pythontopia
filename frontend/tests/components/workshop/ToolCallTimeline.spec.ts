import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import ToolCallTimeline from '@/components/workshop/ToolCallTimeline.vue';
import type { SseTraceEvent } from '@/composables/useSseStream';

function t(partial: Partial<SseTraceEvent> & { node: string }): SseTraceEvent {
  return { status: 'ok', elapsed_ms: 0, ...partial };
}

describe('ToolCallTimeline', () => {
  it('start+finish 配对为一个完成工具卡（✓ + 中文名 + 耗时）', () => {
    const traces = [
      t({ node: 'evidence_fetcher', phase: 'start', status: 'running', elapsed_ms: 0 }),
      t({ node: 'evidence_fetcher', phase: 'finish', status: 'ok', elapsed_ms: 120 }),
    ];
    const wrapper = mount(ToolCallTimeline, { props: { traces, live: true } });

    const steps = wrapper.findAll('[data-testid="tool-call-step"]');
    expect(steps).toHaveLength(1);
    expect(steps[0]?.classes()).toContain('timeline__step--ok');
    expect(steps[0]?.text()).toContain('证据检索');
    expect(steps[0]?.text()).toContain('120ms');
  });

  it('live 下只有 start（无 finish）→ 运行中', () => {
    const traces = [t({ node: 'TutorAgent', phase: 'start', status: 'running', elapsed_ms: 0 })];
    const wrapper = mount(ToolCallTimeline, { props: { traces, live: true } });

    const step = wrapper.get('[data-testid="tool-call-step"]');
    expect(step.classes()).toContain('timeline__step--running');
    expect(step.text()).toContain('运行中');
  });

  it('fail/retry 状态正确映射', () => {
    const traces = [
      t({ node: 'grounding_critic', phase: 'finish', status: 'retry', elapsed_ms: 5 }),
      t({ node: 'safety_critic', phase: 'finish', status: 'fail', elapsed_ms: 3 }),
    ];
    const wrapper = mount(ToolCallTimeline, { props: { traces } });

    const steps = wrapper.findAll('[data-testid="tool-call-step"]');
    expect(steps[0]?.classes()).toContain('timeline__step--retry');
    expect(steps[1]?.classes()).toContain('timeline__step--error');
  });

  it('step_id 区分同 node 的多步（dispatch 多 agent），agent_label 优先', () => {
    const traces = [
      t({ node: 'dispatch.X', step_id: 's1', phase: 'start', status: 'running', elapsed_ms: 0, agent_label: '讲义写手' }),
      t({ node: 'dispatch.X', step_id: 's1', phase: 'finish', status: 'ok', elapsed_ms: 10, agent_label: '讲义写手' }),
      t({ node: 'dispatch.Y', step_id: 's2', phase: 'start', status: 'running', elapsed_ms: 0, agent_label: '练习设计师' }),
      t({ node: 'dispatch.Y', step_id: 's2', phase: 'finish', status: 'ok', elapsed_ms: 20, agent_label: '练习设计师' }),
    ];
    const wrapper = mount(ToolCallTimeline, { props: { traces, live: true } });

    const steps = wrapper.findAll('[data-testid="tool-call-step"]');
    expect(steps).toHaveLength(2);
    expect(steps[0]?.text()).toContain('讲义写手');
    expect(steps[1]?.text()).toContain('练习设计师');
  });

  it('note 只显示学生可理解的备用方式，不暴露内部诊断词', () => {
    const traces = [
      t({ node: 'tutor_diagram', phase: 'finish', status: 'skipped', elapsed_ms: 0, note: 'budget_skipped' }),
    ];
    const wrapper = mount(ToolCallTimeline, { props: { traces } });

    expect(wrapper.get('[data-testid="tool-call-badge"]').text()).toBe('已跳过非必要步骤');
    expect(wrapper.text()).not.toContain('budget_skipped');
  });

  it('非 live 完成快照：残留 running 兜底为完成（历史回放不转圈）', () => {
    const traces = [t({ node: 'TutorAgent', phase: 'start', status: 'running', elapsed_ms: 0 })];
    const wrapper = mount(ToolCallTimeline, { props: { traces, live: false } });

    const step = wrapper.get('[data-testid="tool-call-step"]');
    expect(step.classes()).toContain('timeline__step--ok');
    expect(step.text()).not.toContain('运行中');
  });

  it('展示工具 result 摘要（证据命中段数 / 看图识别进度+字数）', () => {
    const traces = [
      t({ node: 'evidence_fetcher', phase: 'finish', status: 'ok', elapsed_ms: 100, chunks: 5 }),
      t({ node: 'image_understanding', phase: 'finish', status: 'ok', elapsed_ms: 50, index: 2, total: 3, chars: 88 }),
    ];
    const wrapper = mount(ToolCallTimeline, { props: { traces } });

    const results = wrapper.findAll('[data-testid="tool-call-result"]');
    expect(results[0]?.text()).toContain('命中 5 段课件');
    expect(results[1]?.text()).toContain('第 2/3 张图');
    expect(results[1]?.text()).toContain('识别 88 字');
  });

  it('traces 为空不渲染', () => {
    const wrapper = mount(ToolCallTimeline, { props: { traces: [] } });

    expect(wrapper.find('[data-testid="tool-call-timeline"]').exists()).toBe(false);
  });
});
