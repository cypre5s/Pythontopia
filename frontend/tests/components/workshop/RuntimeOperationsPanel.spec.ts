import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const apiMocks = vi.hoisted(() => ({
  fetchAiRuntimeOperations: vi.fn(),
}));

vi.mock('@/api/aiRuntime', () => ({
  fetchAiRuntimeOperations: apiMocks.fetchAiRuntimeOperations,
}));

import RuntimeOperationsPanel from '@/components/workshop/RuntimeOperationsPanel.vue';

function sampleOperations() {
  return {
    loops: [
      {
        loopType: 'LEARNER',
        label: '学生学习闭环',
        status: 'PASS',
        latestTraceId: 'trace-learner',
        nextAction: 'complete:student_learning_loop',
        evidenceRefs: ['learner.evidence.graph'],
      },
      {
        loopType: 'RESOURCE',
        label: '资源生成闭环',
        status: 'INSUFFICIENT_EVIDENCE',
        latestTraceId: '',
        nextAction: 'evaluate:resource_feedback_or_check',
        evidenceRefs: [],
      },
    ],
    governance: [
      {
        key: 'world_patch_review',
        label: '世界补丁审查',
        status: 'WARN',
        action: '检查 town_hub patch 与 rollback',
        reviewRoute: '/teacher/review',
        evidenceRefs: ['world-loop-proposal'],
      },
    ],
  };
}

describe('RuntimeOperationsPanel', () => {
  beforeEach(() => {
    apiMocks.fetchAiRuntimeOperations.mockReset();
  });

  it('renders API-backed loop operations and governance items', async () => {
    apiMocks.fetchAiRuntimeOperations.mockResolvedValue(sampleOperations());

    const wrapper = mount(RuntimeOperationsPanel);
    await flushPromises();

    expect(apiMocks.fetchAiRuntimeOperations).toHaveBeenCalled();
    expect(wrapper.find('[data-testid="runtime-operations"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('学生学习闭环');
    expect(wrapper.text()).toContain('资源生成闭环');
    expect(wrapper.text()).toContain('证据不足');
    expect(wrapper.text()).toContain('世界补丁审查');
    expect(wrapper.text()).toContain('检查 town_hub patch 与 rollback');
  });

  it('does not render fake pass when operations are insufficient', async () => {
    const payload = sampleOperations();
    payload.governance[0].status = 'INSUFFICIENT_EVIDENCE';
    apiMocks.fetchAiRuntimeOperations.mockResolvedValue(payload);

    const wrapper = mount(RuntimeOperationsPanel);
    await flushPromises();

    expect(wrapper.find('[data-status="INSUFFICIENT_EVIDENCE"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('证据不足');
  });
});
