import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const apiMocks = vi.hoisted(() => ({
  fetchAiRuntimeSummary: vi.fn(),
}));

vi.mock('@/api/aiRuntime', () => ({
  fetchAiRuntimeSummary: apiMocks.fetchAiRuntimeSummary,
}));

import AiRuntimeSummaryPanel from '@/components/workshop/AiRuntimeSummaryPanel.vue';

function sampleSummary() {
  return {
    protocol: {
      mcpToolCount: 6,
      toolsWithEvidenceFieldCount: 2,
      mcpResourceCount: 3,
      aguiEventCount: 14,
      skillPackageCount: 4,
      evidenceSkillPackageCount: 4,
      toolsWithEvidenceFields: ['workshop.resource.check.submit', 'learner.recommendations.read'],
      evidenceSkillPackages: ['evidence_grounded_workshop', 'learner_twin_memory'],
    },
    evidence: {
      nodeCount: 18,
      edgeCount: 24,
      touchedKcCount: 4,
      groundedPageCount: 5,
      checkCoverageRate: 0.75,
      graphCompletenessScore: 0.66,
      graphMemoryScore: 0.82,
      directSignalCount: 2,
      propagatedSignalCount: 3,
      retainedEvidenceCount: 6,
    },
    learnerTwin: {
      memoryEventCount: 9,
      failedPracticeCount: 3,
      succeededPracticeCount: 4,
      insightCount: 1,
      masteryKcCount: 1,
      resourceRunCount: 1,
    },
    externalResearch: {
      allowedDomains: ['docs.python.org', 'peps.python.org', 'pypi.org'],
      defaultMaxSteps: 6,
      citationPolicy: '外部内容，非课件',
    },
    gates: [
      {
        key: 'protocol.evidence_fields',
        label: 'MCP 工具声明证据字段',
        status: 'PASS',
        value: 2,
        threshold: 2,
        evidenceRefs: ['workshop.resource.check.submit'],
      },
      {
        key: 'protocol.skill_packages',
        label: 'AI 能力包声明证据合同',
        status: 'PASS',
        value: 4,
        threshold: 3,
        evidenceRefs: ['evidence_grounded_workshop', 'learner_twin_memory'],
      },
      {
        key: 'evidence.graph_completeness',
        label: '学习证据图完整度',
        status: 'PASS',
        value: 0.66,
        threshold: 0.45,
        evidenceRefs: ['learner.evidence.graph'],
      },
    ],
  };
}

describe('AiRuntimeSummaryPanel', () => {
  beforeEach(() => {
    apiMocks.fetchAiRuntimeSummary.mockReset();
  });

  it('renders protocol, evidence, learner twin and external research gates', async () => {
    apiMocks.fetchAiRuntimeSummary.mockResolvedValue(sampleSummary());

    const wrapper = mount(AiRuntimeSummaryPanel);
    await flushPromises();

    expect(wrapper.find('[data-testid="runtime-summary"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('MCP 工具 6');
    expect(wrapper.text()).toContain('能力包 4');
    expect(wrapper.text()).toContain('证据图 0.66');
    expect(wrapper.text()).toContain('记忆事件 9');
    expect(wrapper.text()).toContain('外部内容，非课件');
    expect(wrapper.text()).toContain('MCP 工具声明证据字段');
    expect(wrapper.text()).toContain('AI 能力包声明证据合同');
    expect(wrapper.text()).toContain('learner.evidence.graph');
  });

  it('marks insufficient evidence without pretending the gate passed', async () => {
    const summary = sampleSummary();
    summary.gates = [
      {
        key: 'evidence.graph_completeness',
        label: '学习证据图完整度',
        status: 'INSUFFICIENT_EVIDENCE',
        value: 0,
        threshold: 0.45,
        evidenceRefs: ['learner.evidence.graph'],
      },
    ];
    apiMocks.fetchAiRuntimeSummary.mockResolvedValue(summary);

    const wrapper = mount(AiRuntimeSummaryPanel);
    await flushPromises();

    expect(wrapper.text()).toContain('证据不足');
    expect(wrapper.find('[data-status="INSUFFICIENT_EVIDENCE"]').exists()).toBe(true);
  });
});
