import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/api/http', () => ({
  http: { get: vi.fn(), post: vi.fn() },
}));

import { http } from '@/api/http';
import {
  createWorkshopResource,
  getWorkshopResourceEffectiveness,
  submitWorkshopResourceCheck,
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

  it('reuses a check idempotency key after an ambiguous failure and rotates it after success', async () => {
    vi.spyOn(crypto, 'randomUUID')
      .mockReturnValueOnce('00000000-0000-4000-8000-000000000001')
      .mockReturnValueOnce('00000000-0000-4000-8000-000000000002');
    vi.mocked(http.post)
      .mockRejectedValueOnce(new Error('network timeout'))
      .mockResolvedValue({ data: { checkId: 1, status: 'PASSED' } });
    const request = {
      kcCode: 'PY43-KC-001',
      evidenceType: 'MICRO_QUIZ' as const,
      answerText: 'input 函数读取用户输入',
    };

    await expect(submitWorkshopResourceCheck(7, request)).rejects.toThrow('network timeout');
    await submitWorkshopResourceCheck(7, request);
    await submitWorkshopResourceCheck(7, request);

    expect(http.post).toHaveBeenNthCalledWith(
      1,
      '/api/workshop/resources/7/checks',
      request,
      { headers: { 'Idempotency-Key': '00000000-0000-4000-8000-000000000001' } },
    );
    expect(http.post).toHaveBeenNthCalledWith(
      2,
      '/api/workshop/resources/7/checks',
      request,
      { headers: { 'Idempotency-Key': '00000000-0000-4000-8000-000000000001' } },
    );
    expect(http.post).toHaveBeenNthCalledWith(
      3,
      '/api/workshop/resources/7/checks',
      request,
      { headers: { 'Idempotency-Key': '00000000-0000-4000-8000-000000000002' } },
    );
  });

  it('starts a fresh check attempt when the payload changes after a failed request', async () => {
    vi.spyOn(crypto, 'randomUUID')
      .mockReturnValueOnce('00000000-0000-4000-8000-000000000003')
      .mockReturnValueOnce('00000000-0000-4000-8000-000000000004');
    vi.mocked(http.post).mockRejectedValueOnce(new Error('network timeout'));
    const firstRequest = {
      kcCode: 'PY43-KC-001',
      evidenceType: 'MICRO_QUIZ' as const,
      answerText: 'first answer',
    };
    const changedRequest = { ...firstRequest, answerText: 'changed answer' };

    await expect(submitWorkshopResourceCheck(8, firstRequest)).rejects.toThrow('network timeout');
    await submitWorkshopResourceCheck(8, changedRequest);

    expect(http.post).toHaveBeenNthCalledWith(
      1,
      '/api/workshop/resources/8/checks',
      firstRequest,
      { headers: { 'Idempotency-Key': '00000000-0000-4000-8000-000000000003' } },
    );
    expect(http.post).toHaveBeenNthCalledWith(
      2,
      '/api/workshop/resources/8/checks',
      changedRequest,
      { headers: { 'Idempotency-Key': '00000000-0000-4000-8000-000000000004' } },
    );
  });

  it('keeps a failed payload key when another payload for that resource succeeds concurrently', async () => {
    vi.spyOn(crypto, 'randomUUID')
      .mockReturnValueOnce('00000000-0000-4000-8000-000000000005')
      .mockReturnValueOnce('00000000-0000-4000-8000-000000000006');
    vi.mocked(http.post)
      .mockRejectedValueOnce(new Error('network timeout'))
      .mockResolvedValue({ data: { checkId: 2, status: 'PASSED' } });
    const firstRequest = {
      kcCode: 'PY43-KC-001',
      evidenceType: 'MICRO_QUIZ' as const,
      answerText: 'first answer',
    };
    const changedRequest = { ...firstRequest, answerText: 'changed answer' };
    const failedAttempt = submitWorkshopResourceCheck(9, firstRequest);
    const failedAssertion = expect(failedAttempt).rejects.toThrow('network timeout');
    const changedAttempt = submitWorkshopResourceCheck(9, changedRequest);

    await changedAttempt;
    await failedAssertion;
    await submitWorkshopResourceCheck(9, firstRequest);

    expect(http.post).toHaveBeenNthCalledWith(
      1,
      '/api/workshop/resources/9/checks',
      firstRequest,
      { headers: { 'Idempotency-Key': '00000000-0000-4000-8000-000000000005' } },
    );
    expect(http.post).toHaveBeenNthCalledWith(
      2,
      '/api/workshop/resources/9/checks',
      changedRequest,
      { headers: { 'Idempotency-Key': '00000000-0000-4000-8000-000000000006' } },
    );
    expect(http.post).toHaveBeenNthCalledWith(
      3,
      '/api/workshop/resources/9/checks',
      firstRequest,
      { headers: { 'Idempotency-Key': '00000000-0000-4000-8000-000000000005' } },
    );
  });
});
