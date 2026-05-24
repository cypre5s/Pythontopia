import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const httpMock = vi.hoisted(() => ({
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

import { requestBossProblem, submitBossAttempt } from '@/api/boss';

beforeEach(() => {
  httpMock.post.mockReset();
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
    expect(httpMock.post).toHaveBeenCalledWith('/api/world/boss/1/problem', { stage: 1 });
    expect(problem.problemId).toBe('ai-1');
    expect(problem.aiGenerated).toBe(true);
  });

  it('falls back to local golden problem on 4xx error', async () => {
    httpMock.post.mockRejectedValueOnce(new Error('404 not found'));
    const problem = await requestBossProblem(1, 1);
    expect(problem.problemId).toContain('golden-c1-s1');
    expect(problem.aiGenerated).toBe(false);
    expect(problem.totalTestCases).toBeGreaterThan(0);
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
