import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import type { BossPrefetchStatusDto, BossProblemDto } from '@/api/dto/Boss';

const apiMock = vi.hoisted(() => ({
  prefetchBossProblem: vi.fn(),
  fetchBossPrefetchStatus: vi.fn(),
  requestBossProblem: vi.fn(),
  submitBossAttempt: vi.fn(),
}));

vi.mock('@/api/boss', () => apiMock);

import { useChallengeJudge } from '@/composables/useChallengeJudge';

const ARGS = { kc: 'PY43-KC-001', kind: 'dual_track', difficulty: 2 } as const;
const PROBLEM: BossProblemDto = {
  problemId: 'ai-1',
  chapter: 1,
  stage: 2,
  difficulty: 2,
  kcCode: ARGS.kc,
  statement: '题目',
  hints: [],
  sampleInput: '1',
  sampleOutput: '1',
  totalTestCases: 3,
  reviewed: true,
  aiGenerated: true,
};

function status(
  value: BossPrefetchStatusDto['status'],
  overrides: Partial<BossPrefetchStatusDto> = {},
): BossPrefetchStatusDto {
  return {
    jobId: value === 'IDLE' ? null : 7,
    status: value,
    attempts: value === 'IDLE' ? 0 : 1,
    errorMessage: null,
    retryable: value === 'FAILED' || value === 'IDLE',
    updatedAt: '2026-07-10T00:00:00Z',
    ...overrides,
  };
}

beforeEach(() => {
  setActivePinia(createPinia());
  vi.clearAllMocks();
  apiMock.requestBossProblem.mockResolvedValue(PROBLEM);
});

describe('useChallengeJudge preload state', () => {
  it('keeps accepted PENDING distinct from READY until the server confirms readiness', async () => {
    apiMock.prefetchBossProblem.mockResolvedValueOnce(status('PENDING'));
    apiMock.fetchBossPrefetchStatus
      .mockResolvedValueOnce(status('PENDING'))
      .mockResolvedValueOnce(status('READY'));
    const judge = useChallengeJudge();

    await expect(judge.prefetchProblem(ARGS)).resolves.toMatchObject({ phase: 'pending' });
    expect(judge.getPrefetchState(ARGS).phase).toBe('pending');
    await expect(judge.refreshPrefetch(ARGS)).resolves.toMatchObject({ phase: 'pending' });
    await expect(judge.refreshPrefetch(ARGS)).resolves.toMatchObject({ phase: 'ready' });
  });

  it('records failure evidence and retries the exact same difficulty dimension', async () => {
    apiMock.prefetchBossProblem
      .mockResolvedValueOnce(status('FAILED', { errorMessage: 'graph timeout' }))
      .mockResolvedValueOnce(status('PENDING', { attempts: 2 }));
    const judge = useChallengeJudge();

    await expect(judge.prefetchProblem(ARGS)).resolves.toMatchObject({
      phase: 'failed', errorMessage: 'graph timeout',
    });
    await expect(judge.retryPrefetch(ARGS)).resolves.toMatchObject({ phase: 'pending', attempts: 2 });
    expect(apiMock.prefetchBossProblem).toHaveBeenLastCalledWith(
      1,
      expect.objectContaining({ preferredKc: ARGS.kc, difficulty: 2 }),
    );
  });

  it('fetches expedition problems in strict mode so local golden data cannot masquerade as ready', async () => {
    const judge = useChallengeJudge();
    await expect(judge.fetchProblem(ARGS)).resolves.toEqual(PROBLEM);
    expect(apiMock.requestBossProblem).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ preferredKc: ARGS.kc, difficulty: 2 }),
      { allowGoldenFallback: false },
    );
  });

  it('forwards the active expedition run id to the server-issued problem', async () => {
    const judge = useChallengeJudge();
    const expeditionRunId = '123e4567-e89b-12d3-a456-426614174000';

    await judge.fetchProblem({ ...ARGS, expeditionRunId });

    expect(apiMock.requestBossProblem).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ preferredKc: ARGS.kc, expeditionRunId }),
      { allowGoldenFallback: false },
    );
  });
});
