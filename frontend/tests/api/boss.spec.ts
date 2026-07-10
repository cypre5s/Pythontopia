import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const httpMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}));

vi.mock('@/api/http', () => ({
  http: httpMock,
  ApiClientError: class ApiClientError extends Error {
    status: number;
    constructor(message: string, status: number) {
      super(message);
      this.status = status;
    }
  },
}));

import {
  fetchBossPrefetchStatus,
  prefetchBossProblem,
  requestBossProblem,
  submitBossAttempt,
} from '@/api/boss';

beforeEach(() => {
  httpMock.post.mockReset();
  httpMock.get.mockReset();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('requestBossProblem', () => {
  it('returns backend payload when /api/world/boss returns success', async () => {
    httpMock.post.mockResolvedValueOnce({
      data: {
        problemId: 'ai-1',
        chapter: 1,
        stage: 1,
        difficulty: 2,
        kcCode: 'PY43-KC-001',
        statement: 'AI generated',
        hints: [],
        sampleInput: '1',
        sampleOutput: '1',
        totalTestCases: 3,
        reviewed: true,
        aiGenerated: true,
      },
    });

    const problem = await requestBossProblem(1, 1);
    expect(httpMock.post).toHaveBeenCalledWith(
      '/api/world/boss/1/problem',
      { stage: 1 },
      expect.objectContaining({ suppressNetworkToast: true, timeout: 15_000 }),
    );
    expect(problem.problemId).toBe('ai-1');
    expect(problem.aiGenerated).toBe(true);
  });

  it('normalizes backend bug payloads that only include a prompt into editable code', async () => {
    httpMock.post.mockResolvedValueOnce({
      data: {
        problemId: 'ai-bug-1',
        chapter: 1,
        stage: 2,
        difficulty: 2,
        kcCode: 'PY43-KC-001',
        statement: '修复求和程序：读入两个数，输出和。',
        hints: [],
        sampleInput: '2 3',
        sampleOutput: '5',
        totalTestCases: 3,
        reviewed: true,
        aiGenerated: true,
      },
    });

    const problem = await requestBossProblem(1, {
      stage: 2,
      preferredKind: 'bug_mine',
      preferredKc: 'PY43-KC-001',
    });

    expect(problem.problemId).toBe('ai-bug-1');
    expect(problem.buggyCode).toContain('input');
    expect(problem.starterCode).toBe(problem.buggyCode);
    expect(problem.statement).toContain('修复求和程序');
    expect(problem.statement).not.toBe(problem.buggyCode);
  });

  it('uses backend buggy code when starter code is present but empty', async () => {
    httpMock.post.mockResolvedValueOnce({
      data: {
        problemId: 'ai-bug-2',
        chapter: 2,
        stage: 3,
        difficulty: 3,
        kcCode: 'PY43-KC-008',
        statement: '修复循环累计程序。',
        starterCode: '',
        buggyCode: 'total = 0\nfor n in nums:\n    total = n\nprint(total)\n',
        hints: [],
        sampleInput: '1 2 3',
        sampleOutput: '6',
        totalTestCases: 3,
        reviewed: true,
        aiGenerated: true,
      },
    });

    const problem = await requestBossProblem(2, {
      stage: 3,
      preferredKind: 'debug',
      preferredKc: 'PY43-KC-008',
    });

    expect(problem.buggyCode).toContain('total = n');
    expect(problem.starterCode).toBe(problem.buggyCode);
  });

  it('leaves dual-track backend starter code empty when no starter is expected', async () => {
    httpMock.post.mockResolvedValueOnce({
      data: {
        problemId: 'ai-code-1',
        chapter: 1,
        stage: 1,
        difficulty: 1,
        kcCode: 'PY43-KC-001',
        statement: '读入两个整数并输出和。',
        starterCode: '',
        hints: [],
        sampleInput: '1 2',
        sampleOutput: '3',
        totalTestCases: 3,
        reviewed: true,
        aiGenerated: true,
      },
    });

    const problem = await requestBossProblem(1, {
      stage: 1,
      preferredKind: 'dual_track',
      preferredKc: 'PY43-KC-001',
    });

    expect(problem.starterCode).toBe('');
    expect(problem.buggyCode).toBeUndefined();
  });

  it('falls back to local golden problem on 4xx error', async () => {
    httpMock.post.mockRejectedValueOnce(new Error('404 not found'));
    const problem = await requestBossProblem(1, 1);
    expect(problem.problemId).toContain('golden-c1-s1');
    expect(problem.aiGenerated).toBe(false);
    expect(problem.totalTestCases).toBeGreaterThan(0);
  });

  it('strict expedition mode rejects backend/local golden fallback explicitly', async () => {
    httpMock.post.mockRejectedValueOnce(new Error('graph down'));
    await expect(
      requestBossProblem(
        1,
        { stage: 2, preferredKc: 'PY43-KC-001', difficulty: 2 },
        { allowGoldenFallback: false },
      ),
    ).rejects.toThrow('graph down');
  });

  it('strict expedition mode rejects an unreviewed response even when it has a problem id', async () => {
    httpMock.post.mockResolvedValueOnce({
      data: {
        problemId: 'golden-c1-s2', chapter: 1, stage: 2, difficulty: 2,
        kcCode: 'PY43-KC-001', statement: 'fallback', hints: [], sampleInput: '',
        sampleOutput: '', totalTestCases: 3, reviewed: false, aiGenerated: false,
      },
    });
    await expect(
      requestBossProblem(1, { stage: 2 }, { allowGoldenFallback: false }),
    ).rejects.toThrow('未通过 AI 审查');
  });

  it('falls back when backend returns malformed body (no problemId)', async () => {
    httpMock.post.mockResolvedValueOnce({ data: { foo: 'bar' } });
    const problem = await requestBossProblem(2, 1);
    expect(problem.problemId).toContain('golden-c2');
    expect(problem.aiGenerated).toBe(false);
  });

  it('chapter without templates returns generic golden fallback', async () => {
    httpMock.post.mockRejectedValueOnce(new Error('network'));
    const problem = await requestBossProblem(7, 2);
    expect(problem.chapter).toBe(7);
    expect(problem.stage).toBe(2);
    expect(problem.kcCode).toMatch(/^PY43-KC-\d{3}$/);
  });

  it('debug golden fallback includes buggy starter code separate from the prompt', async () => {
    httpMock.post.mockRejectedValueOnce(new Error('network'));

    const problem = await requestBossProblem(1, {
      stage: 2,
      preferredKind: 'debug',
      preferredKc: 'PY43-KC-002',
    });

    expect(problem.statement).toContain('修复');
    expect(problem.buggyCode).toContain('print');
    expect(problem.starterCode).toBe(problem.buggyCode);
    expect(problem.statement).not.toBe(problem.buggyCode);
  });

  it('bug mine golden fallback includes code that can be edited in-place', async () => {
    httpMock.post.mockRejectedValueOnce(new Error('network'));

    const problem = await requestBossProblem(1, {
      stage: 2,
      preferredKind: 'bug_mine',
      preferredKc: 'PY43-KC-001',
    });

    expect(problem.statement).toContain('30 秒');
    expect(problem.buggyCode).toContain('input');
    expect(problem.starterCode).toBe(problem.buggyCode);
  });
});

describe('boss prefetch status', () => {
  it('returns persisted PENDING and queries the exact difficulty dimension', async () => {
    const pending = {
      jobId: 9, status: 'PENDING', attempts: 1, errorMessage: null,
      retryable: false, updatedAt: '2026-07-10T00:00:00Z',
    } as const;
    httpMock.post.mockResolvedValueOnce({ data: pending });
    httpMock.get.mockResolvedValueOnce({ data: { ...pending, status: 'READY' } });
    const req = {
      stage: 2, preferredKc: 'PY43-KC-001', preferredKind: 'dual_track', difficulty: 2,
    };

    await expect(prefetchBossProblem(1, req)).resolves.toEqual(pending);
    await expect(fetchBossPrefetchStatus(1, req)).resolves.toMatchObject({ status: 'READY' });
    expect(httpMock.get).toHaveBeenCalledWith('/api/world/boss/1/prefetch', {
      params: {
        preferredKc: 'PY43-KC-001',
        preferredKind: 'dual_track',
        arenaLevel: undefined,
        difficulty: 2,
      },
      suppressNetworkToast: true,
    });
  });
});

describe('submitBossAttempt', () => {
  it('forwards POST and returns backend dto when ok', async () => {
    httpMock.post.mockResolvedValueOnce({
      data: {
        problemId: 'ai-1',
        passed: true,
        passedTestCases: 3,
        totalTestCases: 3,
        score: 95,
        feedback: 'PASS',
      },
    });
    const result = await submitBossAttempt({
      problemId: 'ai-1',
      chapter: 1,
      stage: 1,
      code: 'print(1)',
      elapsedSec: 5,
    });
    expect(httpMock.post).toHaveBeenCalledWith(
      '/api/world/boss/1/attempt',
      expect.objectContaining({ problemId: 'ai-1', code: 'print(1)' }),
    );
    expect(result.passed).toBe(true);
    expect(result.score).toBe(95);
  });

  it('returns mock failed result on backend error so caller can use Pyodide verdict', async () => {
    httpMock.post.mockRejectedValueOnce(new Error('graph down'));
    const result = await submitBossAttempt({
      problemId: 'ai-1',
      chapter: 1,
      stage: 1,
      code: 'print(1)',
      elapsedSec: 5,
    });
    expect(result.passed).toBe(false);
    expect(result.feedback).toContain('mock');
  });
});
