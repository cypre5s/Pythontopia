import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import type { BossProblemDto } from '@/api/dto/Boss';

const judgeMock = vi.hoisted(() => ({ fetchProblem: vi.fn(), judge: vi.fn() }));

vi.mock('@/composables/useChallengeJudge', () => ({
  isJudgeUnavailable: () => false,
  useChallengeJudge: () => ({ fetchProblem: judgeMock.fetchProblem, judge: judgeMock.judge }),
}));
vi.mock('@/composables/useKcFrontier', () => ({
  useKcFrontier: () => ({
    masteryOf: () => 0,
    frontier: () => ['PY43-KC-001'],
    pickKc: () => 'PY43-KC-001',
    difficultyForKc: () => 2,
  }),
}));
vi.mock('@/composables/useProfileSignals', () => ({
  useProfileSignals: () => ({
    signals: { value: { resilience: 0.5 } },
    hasAnySignal: { value: false },
    weakKcs: { value: [] },
  }),
}));
vi.mock('@stores/mainline', () => ({
  useMainlineStore: () => ({ submitEndlessSummary: vi.fn(async () => false) }),
}));

import { useRogueRunStore } from '@/stores/rogueRun';

function problem(): BossProblemDto {
  return {
    problemId: 'p1', chapter: 1, stage: 1, difficulty: 2, kcCode: 'PY43-KC-001',
    statement: 's', hints: [], sampleInput: '', sampleOutput: '', totalTestCases: 1,
    reviewed: true, aiGenerated: true,
  };
}

beforeEach(() => {
  setActivePinia(createPinia());
  localStorage.clear();
  judgeMock.fetchProblem.mockReset().mockResolvedValue(problem());
  judgeMock.judge.mockReset();
});

describe('rogueRun store', () => {
  it('startRun builds a map with an available start node', () => {
    const run = useRogueRunStore();
    run.startRun(null);
    expect(run.phase).toBe('map');
    expect(run.nodes.length).toBeGreaterThan(0);
    const start = run.nodes.find((n) => n.layer === 0);
    expect(start?.kind).toBe('battle');
    expect(start?.state).toBe('available');
  });

  it('passing a battle awards score + momentum and clears the node, unlocking next', async () => {
    const run = useRogueRunStore();
    run.startRun(null);
    const start = run.nodes.find((n) => n.layer === 0);
    expect(start).toBeDefined();
    judgeMock.judge.mockResolvedValue({
      problemId: 'p1', passed: true, passedTestCases: 1, totalTestCases: 1, score: 80, feedback: 'ok',
    });
    await run.enterNode(start!.id);
    expect(run.phase).toBe('challenge');
    expect(run.activeProblem?.problemId).toBe('p1');
    await run.submitChallenge();
    expect(run.score).toBeGreaterThan(0);
    expect(run.momentum).toBe(1);
    expect(run.nodes.find((n) => n.id === start!.id)?.state).toBe('cleared');
    run.continueAfterChallenge();
    expect(run.phase).toBe('map');
    // 出边节点应解锁
    const unlocked = (start!.next ?? []).every(
      (id) => run.nodes.find((n) => n.id === id)?.state === 'available',
    );
    expect(unlocked).toBe(true);
  });

  it('failing a battle costs HP gently (resilience 0.5 → -2) and resets momentum', async () => {
    const run = useRogueRunStore();
    run.startRun(null);
    const start = run.nodes.find((n) => n.layer === 0);
    const hp0 = run.hp;
    judgeMock.judge.mockResolvedValue({
      problemId: 'p1', passed: false, passedTestCases: 0, totalTestCases: 1, score: 0, feedback: 'wa',
    });
    await run.enterNode(start!.id);
    await run.submitChallenge();
    expect(run.hp).toBe(hp0 - 2);
    expect(run.momentum).toBe(0);
    expect(run.nodes.find((n) => n.id === start!.id)?.state).not.toBe('cleared');
  });

  it('relic 沉静心法(calm_mind) softens damage from -2 to -1', async () => {
    const run = useRogueRunStore();
    run.startRun(null);
    run.relics.push('calm_mind');
    const start = run.nodes.find((n) => n.layer === 0);
    const hp0 = run.hp;
    judgeMock.judge.mockResolvedValue({
      problemId: 'p1', passed: false, passedTestCases: 0, totalTestCases: 1, score: 0, feedback: 'wa',
    });
    await run.enterNode(start!.id);
    await run.submitChallenge();
    expect(run.hp).toBe(hp0 - 1);
  });

  it('relic 连题学者(combo_scholar) boosts first-clear score over the no-relic baseline', async () => {
    judgeMock.judge.mockResolvedValue({
      problemId: 'p1', passed: true, passedTestCases: 1, totalTestCases: 1, score: 80, feedback: 'ok',
    });
    // 基线（无增益）：battle 难度 2 → base 20, momentum 0 → 20 分。
    const baseRun = useRogueRunStore();
    baseRun.startRun(null);
    const baseStart = baseRun.nodes.find((n) => n.layer === 0);
    await baseRun.enterNode(baseStart!.id);
    await baseRun.submitChallenge();
    const baseScore = baseRun.score;

    // 全新一局 + 增益：同样首通一关，分数应更高（×1.2）。
    setActivePinia(createPinia());
    const relicRun = useRogueRunStore();
    relicRun.startRun(null);
    relicRun.relics.push('combo_scholar');
    const relicStart = relicRun.nodes.find((n) => n.layer === 0);
    await relicRun.enterNode(relicStart!.id);
    await relicRun.submitChallenge();
    expect(relicRun.score).toBeGreaterThan(baseScore);
  });

  it('event 复盘(review) heals + scores + clears node, returns to map without judging', async () => {
    const run = useRogueRunStore();
    run.startRun(null);
    const node = run.nodes.find((n) => n.layer === 0);
    node!.kcCode = 'PY43-KC-001';
    run.hp = 3;
    const score0 = run.score;
    run.activeNode = node!;
    run.phase = 'event';
    await run.chooseEvent('review');
    expect(run.phase).toBe('map');
    expect(run.hp).toBe(4); // 回 1 血
    expect(run.score).toBeGreaterThan(score0);
    expect(run.nodes.find((n) => n.id === node!.id)?.state).toBe('cleared');
    expect(run.attempts).toBe(0); // 复盘不算判题尝试
  });

  it('event 钻研(study) bumps difficulty by 1 and starts a challenge', async () => {
    const run = useRogueRunStore();
    run.startRun(null);
    const node = run.nodes.find((n) => n.layer === 0);
    node!.kcCode = 'PY43-KC-001';
    const d0 = node!.difficulty;
    run.activeNode = node!;
    run.phase = 'event';
    judgeMock.fetchProblem.mockResolvedValue(problem());
    await run.chooseEvent('study');
    expect(node!.difficulty).toBe(Math.min(5, d0 + 1));
    expect(run.phase).toBe('challenge');
  });

  it('boss requires beating bossPhasesTotal phases before victory', async () => {
    const run = useRogueRunStore();
    run.startRun(null);
    const node = run.nodes.find((n) => n.layer === 0);
    node!.kind = 'boss';
    node!.kcCode = 'PY43-KC-001';
    node!.challengeKind = 'multi_round';
    run.activeNode = node!;
    run.activeProblem = problem();
    run.phase = 'challenge';
    run.bossPhase = 1;
    judgeMock.fetchProblem.mockResolvedValue(problem());
    judgeMock.judge.mockResolvedValue({
      problemId: 'p1', passed: true, passedTestCases: 1, totalTestCases: 1, score: 80, feedback: 'ok',
    });
    await run.submitChallenge();
    expect(run.bossPhase).toBe(2);
    expect(run.nodes.find((n) => n.id === node!.id)?.state).not.toBe('cleared');
    await run.submitChallenge();
    expect(run.bossPhase).toBe(3);
    await run.submitChallenge();
    expect(run.nodes.find((n) => n.id === node!.id)?.state).toBe('cleared');
    run.continueAfterChallenge();
    expect(run.phase).toBe('cleared');
  });

  it('daily run marks today done after the run ends and blocks a second daily', () => {
    const run = useRogueRunStore();
    expect(run.dailyAvailable).toBe(true);
    run.startDailyRun();
    expect(run.isDaily).toBe(true);
    expect(run.phase).toBe('map');
    // 模拟本局体力耗尽收尾。
    run.hp = 0;
    run.activeNode = run.nodes.find((n) => n.layer === 0) ?? null;
    run.phase = 'challenge';
    run.continueAfterChallenge();
    expect(run.phase).toBe('over');
    expect(run.dailyAvailable).toBe(false);
  });
});
