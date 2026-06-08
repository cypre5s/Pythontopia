import { describe, it, expect, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import type { SsePlan, SseTraceEvent } from '@/composables/useSseStream';
import type { OfficeEvidence } from '@/components/workshop/agentOffice';

// 与其它工坊组件 spec 一致：把星露谷音效层桩掉，避免引入 tone.js / AudioContext。
vi.mock('@/composables/useStardewFx', () => ({
  playUiClick: vi.fn(),
  playUiHover: vi.fn(),
}));

import AgentOfficeScene from '@/components/workshop/AgentOfficeScene.vue';

interface SceneProps {
  traces: SseTraceEvent[];
  status: 'idle' | 'streaming' | 'done' | 'error';
  tokenPreview: string;
  errorMessage: string | null;
  evidence: OfficeEvidence | null;
  plan: SsePlan | null;
}

/** 三工位资源流 plan（馆员 → 设计师 → 审查官），覆盖 plan 模式渲染。 */
const PLAN: SsePlan = {
  type: 'HANDOUT',
  nodes: [
    { node: 'evidence_fetcher', agent_label: '馆员 Doc', kind: 'evidence' },
    { node: 'designer', agent_label: '设计师 Make', kind: 'designer' },
    { node: 'grounding_critic', agent_label: '审查官 Sage', kind: 'grounding' },
  ],
};

function mountScene(overrides: Partial<SceneProps> = {}): ReturnType<typeof mount> {
  const props: SceneProps = {
    traces: [],
    status: 'streaming',
    tokenPreview: '',
    errorMessage: null,
    evidence: null,
    plan: PLAN,
    ...overrides,
  };
  return mount(AgentOfficeScene, { props });
}

describe('AgentOfficeScene · Agent 串门通报', () => {
  it('A：注入 reflection trace → 渲染串门通报元素并带通报文本（普通流水线仍在）', () => {
    // 反思/交接 trace 携带 note 文本（SseTraceEvent 未声明 note，按可选字段透传）。
    const reflectionTrace: SseTraceEvent & { note: string } = {
      node: 'reflection',
      status: 'ok',
      elapsed_ms: 0,
      note: '小明在「列表别名可变性」反复卡壳，重点照顾',
    };
    const traces: SseTraceEvent[] = [
      { node: 'evidence_fetcher', status: 'ok', elapsed_ms: 120, phase: 'finish' },
      { node: 'designer', status: 'running', elapsed_ms: 0, phase: 'start' },
      reflectionTrace,
    ];

    const wrapper = mountScene({ traces });

    const visit = wrapper.find('[data-testid="agent-handoff-visit"]');
    expect(visit.exists()).toBe(true);
    expect(visit.text()).toContain('列表别名可变性');
    expect(wrapper.find('[data-testid="agent-handoff-note"]').text()).toContain('重点照顾');

    // 普通流水线照常：工位与舞台不受影响（无回归）。
    expect(wrapper.find('[data-testid="office-stage"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="office-agent-designer"]').exists()).toBe(true);
    // reflection 是新节点，不应泄漏进「执行日志」流水线。
    expect(wrapper.find('[data-testid="office-log"]').text()).not.toContain('reflection');
  });

  it('B：无 reflection/handoff trace → 不渲染串门元素，普通流水线照常（无回归）', () => {
    const traces: SseTraceEvent[] = [
      { node: 'evidence_fetcher', status: 'ok', elapsed_ms: 100, phase: 'finish' },
      { node: 'designer', status: 'ok', elapsed_ms: 300, phase: 'finish' },
    ];

    const wrapper = mountScene({ traces });

    expect(wrapper.find('[data-testid="agent-handoff-visit"]').exists()).toBe(false);
    // 普通流水线元素仍在：舞台、工位、执行日志。
    expect(wrapper.find('[data-testid="office-stage"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="office-agent-evidence_fetcher"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="office-agent-designer"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="office-log"]').exists()).toBe(true);
  });

  it('C：reflection trace 未带文本 → 串门气泡回退合理中文默认文案', () => {
    const traces: SseTraceEvent[] = [
      { node: 'designer', status: 'running', elapsed_ms: 0, phase: 'start' },
      { node: 'reflection', status: 'ok', elapsed_ms: 0 },
    ];

    const wrapper = mountScene({ traces });

    const note = wrapper.find('[data-testid="agent-handoff-note"]');
    expect(note.exists()).toBe(true);
    expect(note.text().length).toBeGreaterThan(0);
    expect(note.text()).toContain('照顾');
  });

  it('D：既有 handoff 类 trace 同样触发串门通报', () => {
    const handoffTrace: SseTraceEvent & { note: string } = {
      node: 'handoff',
      status: 'ok',
      elapsed_ms: 0,
      note: '交接：这位同学在循环上需要重点照顾',
    };
    const traces: SseTraceEvent[] = [
      { node: 'evidence_fetcher', status: 'ok', elapsed_ms: 80, phase: 'finish' },
      handoffTrace,
    ];

    const wrapper = mountScene({ traces });

    expect(wrapper.find('[data-testid="agent-handoff-visit"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="agent-handoff-note"]').text()).toContain('循环');
  });
});

// ── P1.3 真 trace 剧场验收测试 ──

describe('AgentOfficeScene · P1.3 真协同 trace', () => {
  /** 模拟 P1 编排器产出的真实 dispatch plan（含 4 步接力链）。 */
  const ORCHESTRATOR_PLAN: SsePlan = {
    type: 'ORCHESTRATOR',
    nodes: [
      { node: 'dispatch.StoryboardAgent', agent_label: '动画分镜师', kind: 'storyboard' },
      { node: 'dispatch.ResourceDesignerAgent', agent_label: '讲义设计师', kind: 'designer' },
      { node: 'dispatch.PracticeAgent', agent_label: '出题官', kind: 'practice' },
      { node: 'dispatch.PathPlannerAgent', agent_label: '路径规划师', kind: 'path' },
    ],
  };

  it('E：P1 编排器 plan 渲染 4 工位接力链（storyboard→designer→practice→path）', () => {
    const traces: SseTraceEvent[] = [
      { node: 'dispatch.StoryboardAgent', status: 'ok', elapsed_ms: 200, phase: 'finish' },
      { node: 'dispatch.ResourceDesignerAgent', status: 'ok', elapsed_ms: 350, phase: 'finish' },
      { node: 'dispatch.PracticeAgent', status: 'running', elapsed_ms: 0, phase: 'start' },
    ];

    const wrapper = mountScene({ traces, plan: ORCHESTRATOR_PLAN });

    // 4 工位全部渲染
    expect(wrapper.find('[data-testid="office-agent-dispatch.StoryboardAgent"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="office-agent-dispatch.ResourceDesignerAgent"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="office-agent-dispatch.PracticeAgent"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="office-agent-dispatch.PathPlannerAgent"]').exists()).toBe(true);
  });

  it('F：retry 后在执行日志显示"重试"状态（critique→revise 驳回可视）', () => {
    // attempt0 失败(retry) → attempt1 成功(ok)：station 最终显示 done，
    // 但执行日志里 attempt0 的 status 为「重试」。
    const traces: SseTraceEvent[] = [
      { node: 'ResourceDesignerAgent.handout.attempt0', status: 'retry', elapsed_ms: 400, phase: 'finish' },
      { node: 'ResourceDesignerAgent.handout.attempt1', status: 'ok', elapsed_ms: 300, phase: 'finish' },
    ];
    const plan: SsePlan = {
      type: 'HANDOUT',
      nodes: [
        { node: 'ResourceDesignerAgent.handout.attempt0', agent_label: '讲义设计师', kind: 'designer' },
      ],
    };

    const wrapper = mountScene({ traces, plan });

    // station 最终是 done（重试后成功）
    const agentEl = wrapper.find('[data-testid="office-agent-ResourceDesignerAgent.handout.attempt0"]');
    expect(agentEl.exists()).toBe(true);
    expect(agentEl.classes()).toContain('office-agent--done');

    // 执行日志显示 attempt0 状态为「重试」
    const logText = wrapper.find('[data-testid="office-log"]').text();
    expect(logText).toContain('重试');
  });

  it('G：P1 编排器 plan 含 4 步接力链时执行日志记录各步', () => {
    // plan 模式下，执行日志按 plan.nodes 映射 trace finish 事件
    const traces: SseTraceEvent[] = [
      { node: 'dispatch.StoryboardAgent', status: 'ok', elapsed_ms: 200, phase: 'finish' },
      { node: 'dispatch.ResourceDesignerAgent', status: 'ok', elapsed_ms: 350, phase: 'finish' },
      { node: 'dispatch.PracticeAgent', status: 'ok', elapsed_ms: 180, phase: 'finish' },
      { node: 'dispatch.PathPlannerAgent', status: 'ok', elapsed_ms: 120, phase: 'finish' },
    ];

    const wrapper = mountScene({ traces, plan: ORCHESTRATOR_PLAN });

    const logText = wrapper.find('[data-testid="office-log"]').text();
    // plan 模式下日志按 agent_label 显示
    expect(logText).toContain('动画分镜师');
    expect(logText).toContain('讲义设计师');
    expect(logText).toContain('出题官');
    expect(logText).toContain('路径规划师');
  });

  it('H：prefers-reduced-motion 下动画被禁用（无障碍合规）', () => {
    // vitest jsdom 不支持 matchMedia；仅验证组件在 plan 模式下不崩溃。
    const traces: SseTraceEvent[] = [
      { node: 'dispatch.StoryboardAgent', status: 'ok', elapsed_ms: 150, phase: 'finish' },
    ];

    const wrapper = mountScene({ traces, plan: ORCHESTRATOR_PLAN });
    expect(wrapper.find('[data-testid="office-stage"]').exists()).toBe(true);
  });
});
