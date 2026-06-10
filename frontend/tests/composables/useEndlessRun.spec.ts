import { describe, it, expect, vi, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useEndlessRun } from '@/composables/useEndlessRun';

const apiMocks = vi.hoisted(() => ({
  requestBossProblem: vi.fn(),
  submitBossAttempt: vi.fn(),
  prefetchBossProblem: vi.fn(),
}));
vi.mock('@/api/boss', () => ({
  requestBossProblem: apiMocks.requestBossProblem,
  submitBossAttempt: apiMocks.submitBossAttempt,
  prefetchBossProblem: apiMocks.prefetchBossProblem,
}));

const pyodideMock = vi.hoisted(() => ({ run: vi.fn() }));
vi.mock('@/game/adapters/pyodide', () => ({
  getPyodideRunner: () => ({
    init: vi.fn().mockResolvedValue(undefined),
    run: pyodideMock.run,
    dispose: vi.fn(),
  }),
}));

const PROBLEM = {
  problemId: 'p-endless-1',
  chapter: 1,
  stage: 1,
  difficulty: 2,
  kcCode: 'PY43-KC-001',
  statement: 'add a b',
  hints: [],
  sampleInput: '2 3',
  sampleOutput: '5',
  totalTestCases: 3,
  reviewed: false,
  aiGenerated: false,
};

describe('useEndlessRun', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    window.localStorage.clear();
    apiMocks.requestBossProblem.mockReset().mockResolvedValue(PROBLEM);
    apiMocks.prefetchBossProblem.mockReset().mockResolvedValue(undefined);
    apiMocks.submitBossAttempt.mockReset().mockResolvedValue({
      problemId: 'p-endless-1',
      passed: false,
      passedTestCases: 1,
      totalTestCases: 3,
      score: 0,
      feedback: '隐藏用例 WA',
    });
    pyodideMock.run.mockReset().mockResolvedValue({ stdout: '5\n', stderr: '' });
  });

  it('后端判定失败时，即使本地样例通过也保持 failed', async () => {
    const run = useEndlessRun();
    await run.startRun();
    run.code.value = 'print(5)';

    await run.submit();

    expect(run.phase.value).toBe('failed');
    expect(run.lastResult.value?.passed).toBe(false);
    expect(run.streak.value).toBe(0);
    expect(run.history.value[0]?.passed).toBe(false);
  });
});
