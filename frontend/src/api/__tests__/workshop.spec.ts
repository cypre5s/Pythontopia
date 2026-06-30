import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/api/http', () => ({
  http: { get: vi.fn(), post: vi.fn() },
}));

import { http } from '@/api/http';
import {
  getWorkshopResourceEffectiveness,
  type WorkshopResourceEffectivenessDto,
} from '@/api/workshop';

beforeEach(() => {
  vi.clearAllMocks();
});

describe('workshop.ts · REST 访问层', () => {
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
