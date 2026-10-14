import { describe, it, expect, vi, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useEndlessRun } from '@/composables/useEndlessRun';

const apiMocks = vi.hoisted(() => ({
  newBossProblemRequestId: vi.fn(() => '00000000-0000-4000-8000-000000000001'),
  requestBossProblem: vi.fn(),
  submitBossAttempt: vi.fn(),
  prefetchBossProblem: vi.fn(),
  fetchBossPrefetchStatus: vi.fn(),
}));
vi.mock('@/api/boss', () => ({
  newBossProblemRequestId: apiMocks.newBossProblemRequestId,
  requestBossProblem: apiMocks.requestBossProblem,
  submitBossAttempt: apiMocks.submitBossAttempt,
  prefetchBossProblem: apiMocks.prefetchBossProblem,
  fetchBossPrefetchStatus: apiMocks.fetchBossPrefetchStatus,
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
    vi.restoreAllMocks();
    setActivePinia(createPinia());
    window.localStorage.clear();
    apiMocks.requestBossProblem.mockReset().mockResolvedValue(PROBLEM);
    apiMocks.prefetchBossProblem.mockReset().mockResolvedValue({ status: 'READY' });
    apiMocks.fetchBossPrefetchStatus.mockReset().mockResolvedValue({ status: 'READY' });
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

  it('忽略旧的无 owner 无尽历史键', () => {
    window.localStorage.setItem(
      'pyt_endless_history_v1',
      JSON.stringify([{ problemId: 'legacy-problem' }]),
    );
    window.localStorage.setItem(
      'pythontopia.user',
      JSON.stringify({ id: 11, username: 'alice', displayName: 'A', role: 'STUDENT' }),
    );
    setActivePinia(createPinia());

    expect(useEndlessRun().history.value).toEqual([]);
  });

  it('无尽历史只写当前用户，B 账号不得读到 A 的答题记录', async () => {
    window.localStorage.setItem(
      'pythontopia.user',
      JSON.stringify({ id: 11, username: 'alice', displayName: 'A', role: 'STUDENT' }),
    );
    setActivePinia(createPinia());
    const alice = useEndlessRun();
    await alice.startRun();
    await alice.submit();

    expect(JSON.parse(
      window.localStorage.getItem('pyt_endless_history_v1@u:alice') ?? '[]',
    )).toHaveLength(1);

    window.localStorage.setItem(
      'pythontopia.user',
      JSON.stringify({ id: 22, username: 'bob', displayName: 'B', role: 'STUDENT' }),
    );
    setActivePinia(createPinia());

    expect(useEndlessRun().history.value).toEqual([]);
  });

  it('reuses the exact prefetched request after timeout and retry', async () => {
    const random = vi.spyOn(Math, 'random')
      .mockReturnValueOnce(0)
      .mockReturnValueOnce(0)
      .mockReturnValueOnce(0.5)
      .mockReturnValueOnce(0.99)
      .mockReturnValueOnce(0.99);
    const run = useEndlessRun();
    await run.startRun();
    await run.submit();
    const [prefetchedChapter, prefetchedRequest] = apiMocks.prefetchBossProblem.mock.calls[0] as [
      number,
      Record<string, unknown>,
    ];
    apiMocks.requestBossProblem.mockRejectedValueOnce(new Error('timeout'));

    await run.nextProblem();
    apiMocks.requestBossProblem.mockResolvedValueOnce({ ...PROBLEM, problemId: 'p-endless-next' });
    await run.retry();

    expect(apiMocks.requestBossProblem.mock.calls[1]).toEqual([
      prefetchedChapter,
      prefetchedRequest,
      { allowGoldenFallback: false },
    ]);
    expect(apiMocks.requestBossProblem.mock.calls[2]).toEqual(apiMocks.requestBossProblem.mock.calls[1]);
    random.mockRestore();
  });

  it('waits for an in-flight prefetch before requesting the next problem', async () => {
    vi.useFakeTimers();
    let resolvePrefetch!: (value: { status: string }) => void;
    apiMocks.prefetchBossProblem.mockReturnValueOnce(new Promise((resolve) => {
      resolvePrefetch = resolve;
    }));
    apiMocks.fetchBossPrefetchStatus
      .mockResolvedValueOnce({ status: 'PENDING' })
      .mockResolvedValueOnce({ status: 'READY' });
    const run = useEndlessRun();
    await run.startRun();
    await run.submit();

    const nextProblem = run.nextProblem();
    await Promise.resolve();
    expect(apiMocks.requestBossProblem).toHaveBeenCalledTimes(1);

    resolvePrefetch({ status: 'PENDING' });
    await vi.runAllTimersAsync();
    await nextProblem;

    expect(apiMocks.fetchBossPrefetchStatus).toHaveBeenCalledTimes(2);
    expect(apiMocks.requestBossProblem).toHaveBeenCalledTimes(2);
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

  it('判题服务不可用时不按本地公开样例冒充通过（防离线刷关 / arena 离线通关）', async () => {
    const run = useEndlessRun();
    await run.startRun();
    run.code.value = 'print(5)';
    // 本地公开样例恰好「通过」，但后端判题服务抛错（不可达）。
    pyodideMock.run.mockResolvedValueOnce({ stdout: '5\n', stderr: '' });
    apiMocks.submitBossAttempt.mockRejectedValueOnce(new Error('network down'));

    await run.submit();

    // 绝不冒充通过：phase=error、无结果、连胜不变、历史不写（arena 不会 markCleared）。
    expect(run.phase.value).toBe('error');
    expect(run.lastResult.value).toBeNull();
    expect(run.streak.value).toBe(0);
    expect(run.history.value.length).toBe(0);
    expect(run.errorMessage.value).toContain('判题服务暂不可用');
  });

  it('下一题获取失败后重试取题，不重交旧题或重置本局进度', async () => {
    const nextProblem = { ...PROBLEM, problemId: 'p-endless-2' };
    apiMocks.requestBossProblem
      .mockResolvedValueOnce(PROBLEM)
      .mockRejectedValueOnce(new Error('next question timeout'))
      .mockResolvedValueOnce(nextProblem);
    apiMocks.submitBossAttempt.mockResolvedValueOnce({
      problemId: PROBLEM.problemId,
      passed: true,
      passedTestCases: 3,
      totalTestCases: 3,
      score: 20,
      feedback: '通过',
    });
    const run = useEndlessRun();
    await run.startRun();
    await run.submit();
    const score = run.totalScore.value;

    await run.nextProblem();
    expect(run.phase.value).toBe('error');
    expect(run.problem.value).toBeNull();
    expect(run.attempts.value).toBe(1);
    expect(run.totalScore.value).toBe(score);

    await run.retry();

    expect(apiMocks.requestBossProblem).toHaveBeenCalledTimes(3);
    expect(apiMocks.submitBossAttempt).toHaveBeenCalledTimes(1);
    expect(run.problem.value?.problemId).toBe(nextProblem.problemId);
    expect(run.attempts.value).toBe(1);
    expect(run.totalScore.value).toBe(score);
  });

  it('评测中重复提交只走一次正式判题（手动提交 + BugMineCard 30s 定时器同帧触发）', async () => {
    const run = useEndlessRun();
    await run.startRun();
    run.code.value = 'print(5)';

    // BugMineCard 的 timeout 直接 emit → onSubmit，绕过按钮 :disabled；
    // t≈29s 手点提交 + 30s 定时器 = 同一题两次 submit()。
    const manual = run.submit();
    const timeout = run.submit();
    await Promise.all([manual, timeout]);

    expect(apiMocks.submitBossAttempt).toHaveBeenCalledTimes(1);
    expect(run.attempts.value).toBe(1);
    expect(run.history.value.length).toBe(1);
  });

  it('把纵深难度交给后端，并以服务端画像合成后的难度为真源', async () => {
    apiMocks.requestBossProblem.mockResolvedValueOnce({ ...PROBLEM, difficulty: 4 });
    const run = useEndlessRun();

    await run.startRun();

    expect(apiMocks.requestBossProblem).toHaveBeenCalledWith(
      expect.any(Number),
      expect.objectContaining({ difficulty: 2 }),
      { allowGoldenFallback: false },
    );
    expect(run.problem.value?.difficulty).toBe(4);
    expect(run.currentDifficulty.value).toBe(4);
  });
});
