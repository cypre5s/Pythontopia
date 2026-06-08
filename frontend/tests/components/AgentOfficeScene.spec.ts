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
