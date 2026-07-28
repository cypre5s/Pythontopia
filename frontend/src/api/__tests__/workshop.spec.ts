import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/api/http', () => ({
  http: { get: vi.fn(), post: vi.fn() },
}));

import { http } from '@/api/http';
import {
  createWorkshopResource,
  getWorkshopResourceEffectiveness,
  type WorkshopResourceEffectivenessDto,
} from '@/api/workshop';

beforeEach(() => {
  vi.clearAllMocks();
});

describe('workshop.ts · REST 访问层', () => {
  it('createWorkshopResource：覆盖后端资源图专用读取窗口', async () => {
    vi.mocked(http.post).mockResolvedValue({
      data: { runId: 42, resourceId: 7, status: 'SUCCESS' },
    });

    await createWorkshopResource({
      kcCodes: ['PY43-KC-031'],
      type: 'HANDOUT',
      focusHint: '公网资源生成',
    });

    expect(http.post).toHaveBeenCalledWith(
      '/api/workshop/resources',
      {
        kcCodes: ['PY43-KC-031'],
        type: 'HANDOUT',
        focusHint: '公网资源生成',
      },
      { timeout: 190_000 },
    );
  });

  it('getWorkshopResourceEffectiveness：GET resource effectiveness signal', async () => {
    const body: WorkshopResourceEffectivenessDto = {
      resourceId: 7,
      feedbackStatus: 'LIKED',
      adoptedCount: 1,
      rejectedCount: 0,
      checkCount: 2,
      passedCheckCount: 2,
      failedCheckCount: 0,
      passRate: 1,
      masteryDeltaSum: 0.1,
      evidencePageIds: [101],
      signal: 'EFFECTIVE',
      evaluationHook: 'resource_feedback_or_check',
    };
    vi.mocked(http.get).mockResolvedValue({ data: body });

    const result = await getWorkshopResourceEffectiveness(7);

    expect(result.signal).toBe('EFFECTIVE');
    expect(http.get).toHaveBeenCalledWith('/api/workshop/resources/7/effectiveness');
  });
});
