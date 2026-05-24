/**
 * R-13 §-1 续：useBossSession 交互深挖 spec。
 *
 * 覆盖：
 *   1. getBossMove(chapter, stage) 返回 7 章 × 4 阶段共 28 个招式；越界返回 null。
 *   2. 初始 bossHp = 100；currentMove 是 stage 1 的招式。
 *   3. startStage 切换 stage 时 currentMove 同步切换。
 *   4. submit 通过时扣 damageOnDefeat 血量；victory 触发条件。
 *   5. recentDamage 设值 + 1.2s 后清空（用 fake timer 模拟）。
 *   6. reset 重置 bossHp 100 + currentMove 回到 stage 1。
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useBossSession, getBossMove } from '@/composables/useBossSession';

const apiMocks = vi.hoisted(() => ({
  requestBossProblem: vi.fn(),
  submitBossAttempt: vi.fn(),
}));
vi.mock('@/api/boss', () => ({
  requestBossProblem: apiMocks.requestBossProblem,
  submitBossAttempt: apiMocks.submitBossAttempt,
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
  problemId: 'p-1',
  chapter: 1,
  stage: 1,
  difficulty: 1,
  kcCode: 'PY43-KC-001',
  statement: 'add a b',
  hints: [],
  sampleInput: '2 3',
  sampleOutput: '5',
  totalTestCases: 3,
  reviewed: false,
  aiGenerated: false,
};

describe('useBossSession (R-13 §-1 BossPage 交互深挖)', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    apiMocks.requestBossProblem.mockReset().mockResolvedValue(PROBLEM);
    apiMocks.submitBossAttempt.mockReset().mockResolvedValue({
      problemId: 'p-1',
      passed: true,
      passedTestCases: 3,
      totalTestCases: 3,
      score: 90,
      feedback: 'ok',
    });
    pyodideMock.run.mockReset().mockResolvedValue({ stdout: '5\n', stderr: '' });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('getBossMove: 7 章 × 4 阶段全覆盖', () => {
    for (let c = 1; c <= 7; c += 1) {
      for (let s = 1; s <= 4; s += 1) {
        const m = getBossMove(c, s);
        expect(m, `chapter ${c} stage ${s}`).not.toBeNull();
        expect(m!.name).toBeTruthy();
        expect(m!.damageOnDefeat).toBeGreaterThan(0);
      }
    }
  });

  it('getBossMove: 越界返回 null', () => {
    expect(getBossMove(0, 1)).toBeNull();
    expect(getBossMove(8, 1)).toBeNull();
    expect(getBossMove(1, 0)).toBeNull();
    expect(getBossMove(1, 5)).toBeNull();
  });

  it('初始 bossHp 100 + currentMove 是 stage 1 招式', () => {
    const s = useBossSession({ chapter: 1 });
    expect(s.bossHp.value).toBe(100);
    expect(s.currentMove.value?.name).toBe('算珠初震');
  });

  it('startStage 切换 stage 时 currentMove 同步切换', async () => {
    const s = useBossSession({ chapter: 1 });
    s.stage.value = 3;
    await s.startStage();
    expect(s.currentMove.value?.name).toBe('木珠迷阵');
  });

  it('submit 通过时 bossHp 扣 damageOnDefeat（25）', async () => {
    const s = useBossSession({ chapter: 1 });
    await s.startStage();
    s.code.value = 'a, b = map(int, input().split()); print(a+b)';
    await s.submit();
    expect(s.bossHp.value).toBe(75);
    expect(s.recentDamage.value).toBe(25);
  });

  it('4 阶段全通 bossHp 减到 0 + victory', async () => {
    const s = useBossSession({ chapter: 1 });
    for (let stage = 1; stage <= 4; stage += 1) {
      s.stage.value = stage;
      await s.startStage();
      s.code.value = 'fake';
      await s.submit();
      if (stage < 4) {
        await s.advanceStage();
      }
    }
    expect(s.bossHp.value).toBe(0);
    expect(s.phase.value).toBe('victory');
  });

  it('recentDamage 1200ms 后自动清空', async () => {
    vi.useFakeTimers();
    const s = useBossSession({ chapter: 1 });
    await s.startStage();
    s.code.value = 'fake';
    await s.submit();
    expect(s.recentDamage.value).toBe(25);
    await vi.advanceTimersByTimeAsync(1300);
    expect(s.recentDamage.value).toBeNull();
  });

  it('reset 重置 bossHp 100 + currentMove 回 stage 1 招式', async () => {
    const s = useBossSession({ chapter: 1 });
    s.stage.value = 3;
    await s.startStage();
    s.code.value = 'fake';
    await s.submit();
    expect(s.bossHp.value).toBe(75);
    s.reset();
    expect(s.bossHp.value).toBe(100);
    expect(s.stage.value).toBe(1);
    expect(s.currentMove.value?.name).toBe('算珠初震');
    expect(s.recentDamage.value).toBeNull();
  });
});
