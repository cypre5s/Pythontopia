import { beforeEach, describe, expect, it, vi } from 'vitest';

const httpMock = vi.hoisted(() => ({
  get: vi.fn(),
}));

vi.mock('@/api/http', () => ({
  http: httpMock,
}));

import { fetchAiRuntimeOperations, fetchAiRuntimeSummary } from '@/api/aiRuntime';

beforeEach(() => {
  httpMock.get.mockReset();
});

describe('aiRuntime api', () => {
  it('fetches runtime summary through /api/ai/runtime/summary', async () => {
    httpMock.get.mockResolvedValueOnce({ data: { gates: [] } });

    const dto = await fetchAiRuntimeSummary();

    expect(httpMock.get).toHaveBeenCalledWith('/api/ai/runtime/summary');
    expect(dto.gates).toEqual([]);
  });

  it('fetches runtime operations through /api/ai/runtime/operations', async () => {
    httpMock.get.mockResolvedValueOnce({
      data: {
        loops: [
          {
            loopType: 'RESOURCE',
            label: '资源生成闭环',
            status: 'PASS',
            latestTraceId: 'trace-resource',
            nextAction: 'evaluate:resource_feedback_or_check',
            evidenceRefs: ['resource_feedback_or_check'],
          },
        ],
        governance: [
          {
            key: 'policy_canary_review',
            label: '策略灰度审核',
            status: 'PASS',
            action: '查看 canary dry-run 与 rollback 状态',
            reviewRoute: '/teacher/review',
            evidenceRefs: ['agentic-m3-system-evolution-v1'],
          },
        ],
      },
    });

    const dto = await fetchAiRuntimeOperations();

    expect(httpMock.get).toHaveBeenCalledWith('/api/ai/runtime/operations');
    expect(dto.loops[0].loopType).toBe('RESOURCE');
    expect(dto.governance[0].key).toBe('policy_canary_review');
  });
});
