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
});
