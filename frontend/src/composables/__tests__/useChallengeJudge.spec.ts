import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { BossProblemDto, BossProblemRequest } from '@/api/dto/Boss';

const mocks = vi.hoisted(() => ({
  fetchBossPrefetchStatus: vi.fn(),
  newBossProblemRequestId: vi.fn(),
  prefetchBossProblem: vi.fn(),
  requestBossProblem: vi.fn(),
  submitBossAttempt: vi.fn(),
}));

vi.mock('@/api/boss', () => mocks);
vi.mock('@/composables/useProfileSignals', () => ({
  useProfileSignals: () => ({ signals: { value: {} }, hasAnySignal: { value: false } }),
}));

import { useChallengeJudge } from '@/composables/useChallengeJudge';

const ARGS = {
  kc: 'PY43-KC-001',
  kind: 'dual_track',
  difficulty: 2,
  expeditionRunId: '123e4567-e89b-12d3-a456-426614174000',
  expeditionNodeId: 'L0-1',
} as const;

const PROBLEM: BossProblemDto = {
  problemId: 'issue-test',
  chapter: 1,
  stage: 2,
  difficulty: 2,
  kcCode: ARGS.kc,
  statement: '给定整数并输出结果。',
  hints: [],
  sampleInput: '2',
  sampleOutput: '2',
  totalTestCases: 1,
  reviewed: true,
  aiGenerated: true,
};

describe('useChallengeJudge', () => {
  let nextRequestId: number;

  beforeEach(() => {
    vi.clearAllMocks();
    nextRequestId = 0;
    mocks.newBossProblemRequestId.mockImplementation(() => `expedition-request-${++nextRequestId}`);
  });

  it('reuses the same request id after an uncertain on-demand timeout without starting a second prefetch', async () => {
    mocks.requestBossProblem
      .mockRejectedValueOnce(new Error('request timeout'))
      .mockResolvedValueOnce(PROBLEM);
    const challenge = useChallengeJudge();

    await expect(challenge.fetchProblem(ARGS)).rejects.toThrow('request timeout');
    const firstPayload = mocks.requestBossProblem.mock.calls[0]?.[1] as BossProblemRequest;
    const firstRequestId = firstPayload.requestId;

    const retryState = await challenge.retryPrefetch(ARGS);
    expect(retryState.phase).toBe('idle');
    expect(mocks.prefetchBossProblem).not.toHaveBeenCalled();

    await expect(challenge.fetchProblem(ARGS)).resolves.toEqual(PROBLEM);
    const secondPayload = mocks.requestBossProblem.mock.calls[1]?.[1] as BossProblemRequest;

    expect(firstRequestId).toBe('expedition-request-1');
    expect(secondPayload.requestId).toBe(firstRequestId);
    expect(mocks.newBossProblemRequestId).toHaveBeenCalledTimes(1);
  });

  it('uses a fresh request id for a new problem after the previous issue completed', async () => {
    mocks.requestBossProblem.mockResolvedValue(PROBLEM);
    const challenge = useChallengeJudge();

    await challenge.fetchProblem(ARGS);
    await challenge.fetchProblem(ARGS);

    const firstPayload = mocks.requestBossProblem.mock.calls[0]?.[1] as BossProblemRequest;
    const secondPayload = mocks.requestBossProblem.mock.calls[1]?.[1] as BossProblemRequest;
    expect(firstPayload.requestId).not.toBe(secondPayload.requestId);
  });
});
