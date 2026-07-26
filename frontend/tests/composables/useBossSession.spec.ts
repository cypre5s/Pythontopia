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

  it('后端判定失败时，即使本地样例通过也不扣血不推进', async () => {
    apiMocks.submitBossAttempt.mockResolvedValue({
      problemId: 'p-1',
      passed: false,
      passedTestCases: 1,
      totalTestCases: 3,
      score: 0,
      feedback: '隐藏用例 WA',
    });
    pyodideMock.run.mockResolvedValue({ stdout: '5\n', stderr: '' });
    const s = useBossSession({ chapter: 1 });
    await s.startStage();
    s.code.value = 'print(5)';

    await s.submit();

    expect(s.phase.value).toBe('failed');
    expect(s.bossHp.value).toBe(100);
    expect(s.lastResult.value?.passed).toBe(false);
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

  // ── A 方案：回合制对攻（玩家血量 / 反击 / 暴击 / 狂暴阶段） ──
  it('初始 playerHp 100 + bossPhase 1 + 有限时', () => {
    const s = useBossSession({ chapter: 1 });
    expect(s.playerHp.value).toBe(100);
    expect(s.bossPhase.value).toBe(1);
    expect(s.timeLeftSec.value).toBeGreaterThan(0);
  });

  it('答错 → boss 反击扣玩家血，phase=failed', async () => {
    apiMocks.submitBossAttempt.mockResolvedValue({
      problemId: 'p-1',
      passed: false,
      passedTestCases: 0,
      totalTestCases: 3,
      score: 0,
      feedback: 'WA',
    });
    pyodideMock.run.mockResolvedValue({ stdout: 'x\n', stderr: '' });
    const s = useBossSession({ chapter: 1 });
    await s.startStage();
    s.code.value = 'print(1)';
    await s.submit();
    expect(s.phase.value).toBe('failed');
    expect(s.playerHp.value).toBeLessThan(100);
    expect(s.recentPlayerDamage.value).toBeGreaterThan(0);
  });

  it('通过 2 阶段后 bossHp≤50 → bossPhase 进入 2（狂暴）', async () => {
    const s = useBossSession({ chapter: 1 });
    for (let stage = 1; stage <= 2; stage += 1) {
      s.stage.value = stage;
      await s.startStage();
      s.code.value = 'fake';
      await s.submit();
      if (stage < 2) await s.advanceStage();
    }
    expect(s.bossHp.value).toBe(50);
    expect(s.bossPhase.value).toBe(2);
  });

  it('限时内快答（计时器未走）= 暴击 lastHitCrit', async () => {
    const s = useBossSession({ chapter: 1 });
    await s.startStage();
    s.code.value = 'fake';
    await s.submit();
    expect(s.lastHitCrit.value).toBe(true);
  });

  it('reset 重置 playerHp / lastHitCrit / recentPlayerDamage', async () => {
    const s = useBossSession({ chapter: 1 });
    await s.startStage();
    s.code.value = 'fake';
    await s.submit();
    s.reset();
    expect(s.playerHp.value).toBe(100);
    expect(s.lastHitCrit.value).toBe(false);
    expect(s.recentPlayerDamage.value).toBeNull();
  });

  it('getBossMove bossPhase=2 返回狂暴二阶段文案', () => {
    const phase1 = getBossMove(1, 1, 1);
    const phase2 = getBossMove(1, 1, 2);
    expect(phase1?.name).toBe('算珠初震');
    expect(phase2?.name).toBe('算珠崩裂');
    expect(phase2?.preview).toContain('算珠');
  });

  it('bossHp≤50 后 currentMove 响应式切换狂暴文案（无需等 startStage）', async () => {
    const s = useBossSession({ chapter: 1 });
    await s.startStage();
    s.bossHp.value = 50;
    expect(s.bossPhase.value).toBe(2);
    expect(s.currentMove.value?.name).toBe('算珠崩裂');
  });

  it('答错致 HP 归零触发 playerKnockedDown 并回满血', async () => {
    vi.useFakeTimers();
    apiMocks.submitBossAttempt.mockResolvedValue({
      problemId: 'p-1',
      passed: false,
      passedTestCases: 0,
      totalTestCases: 3,
      score: 0,
      feedback: 'WA',
    });
    pyodideMock.run.mockResolvedValue({ stdout: 'x\n', stderr: '' });
    const s = useBossSession({ chapter: 1 });
    await s.startStage();
    s.playerHp.value = 10;
    s.code.value = 'print(1)';
    await s.submit();
    expect(s.playerHp.value).toBe(100);
    expect(s.playerKnockedDown.value).toBe(true);
    await vi.advanceTimersByTimeAsync(1700);
    expect(s.playerKnockedDown.value).toBe(false);
  });

  // ── 判题服务不可用：绝不拿 Pyodide 本地样例冒充正式判题 ──
  it('后端判题失败时不推进 stage / 不扣血，置 error 态且 lastResult=null', async () => {
    apiMocks.submitBossAttempt.mockRejectedValue(new Error('502 Bad Gateway'));
    // 本地 Pyodide 跑出与样例一致的输出（修复前会被当成通过并推进战斗）
    pyodideMock.run.mockResolvedValue({ stdout: '5\n', stderr: '' });
    const s = useBossSession({ chapter: 1 });
    await s.startStage();
    s.code.value = 'print(5)';

    await s.submit();

    expect(s.phase.value).toBe('error');
    expect(s.lastResult.value).toBeNull();
    expect(s.errorMessage.value).toContain('判题服务暂不可用');
    expect(s.clearedStages.value).toBe(0);
    expect(s.bossHp.value).toBe(100);
    expect(s.playerHp.value).toBe(100);
    expect(s.stage.value).toBe(1);
    expect(s.recentDamage.value).toBeNull();
  });

  it('最后一阶段后端不可用时不得直接 victory', async () => {
    const s = useBossSession({ chapter: 1 });
    s.stage.value = 4;
    await s.startStage();
    apiMocks.submitBossAttempt.mockRejectedValue(new Error('ECONNREFUSED'));
    pyodideMock.run.mockResolvedValue({ stdout: '5\n', stderr: '' });
    s.code.value = 'print(5)';

    await s.submit();

    expect(s.phase.value).toBe('error');
    expect(s.clearedStages.value).toBe(0);
    expect(s.bossHp.value).toBe(100);
  });

  it('倒计时归零设置 stageTimedOut', async () => {
    const s = useBossSession({ chapter: 1 });
    await s.startStage();
    s.timeLeftSec.value = 1;
    s.tickSecond();
    expect(s.stageTimedOut.value).toBe(true);
    expect(s.timeLeftSec.value).toBe(0);
  });
});
