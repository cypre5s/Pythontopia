import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import type { BossProblemDto } from '@/api/dto/Boss';

const judgeMock = vi.hoisted(() => ({
  fetchProblem: vi.fn(),
  judge: vi.fn(),
  prefetchProblem: vi.fn(),
  retryPrefetch: vi.fn(),
  getPrefetchState: vi.fn(),
  clearPrefetch: vi.fn(),
}));
const frontierApiMock = vi.hoisted(() => ({
  createExpeditionRun: vi.fn(),
  fetchExpeditionRun: vi.fn(),
  saveExpeditionRun: vi.fn(),
  deleteExpeditionRun: vi.fn(),
  submitExpeditionScore: vi.fn(),
  fetchExpeditionLeaderboard: vi.fn(),
}));
const EXPEDITION_RUN_ID = '123e4567-e89b-12d3-a456-426614174000';

vi.mock('@/composables/useChallengeJudge', () => ({
  isJudgeUnavailable: () => false,
  useChallengeJudge: () => ({
    fetchProblem: judgeMock.fetchProblem,
    judge: judgeMock.judge,
    prefetchProblem: judgeMock.prefetchProblem,
    retryPrefetch: judgeMock.retryPrefetch,
    getPrefetchState: judgeMock.getPrefetchState,
    clearPrefetch: judgeMock.clearPrefetch,
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
vi.mock('@/api/expedition', () => ({
  createExpeditionRun: frontierApiMock.createExpeditionRun,
  fetchExpeditionRun: frontierApiMock.fetchExpeditionRun,
  saveExpeditionRun: frontierApiMock.saveExpeditionRun,
  deleteExpeditionRun: frontierApiMock.deleteExpeditionRun,
  submitExpeditionScore: frontierApiMock.submitExpeditionScore,
  fetchExpeditionLeaderboard: frontierApiMock.fetchExpeditionLeaderboard,
}));

import { useRogueRunStore } from '@/stores/rogueRun';
import { generateRogueMap } from '@/data/rogueMap';
import { computeLayeredDifficulty, NODE_KIND_POOLS } from '@/data/rogueConfig';
import type { ExpeditionRunSnapshot } from '@/api/expedition';

function serverRun(
  focusChapter: number | null = null,
  kcCode = 'PY43-KC-001',
  difficulty = 2,
  isDaily = false,
): ExpeditionRunSnapshot {
  const map = generateRogueMap({
    rng: () => 0.4,
    resolveChallenge: (kind, layer) => {
      if (kind === 'rest' || kind === 'chest') {
        return { kcCode: null, challengeKind: null, difficulty: 0 };
      }
      const pool = NODE_KIND_POOLS[kind];
      let resolvedDifficulty = computeLayeredDifficulty(difficulty, layer);
      if (kind === 'elite') resolvedDifficulty = Math.min(5, resolvedDifficulty + 1);
      if (kind === 'boss') resolvedDifficulty = Math.min(5, Math.max(3, resolvedDifficulty + 1));
      return {
        kcCode,
        challengeKind: pool[Math.floor(0.4 * pool.length)] ?? 'dual_track',
        difficulty: resolvedDifficulty,
      };
    },
  });
  return {
    runId: EXPEDITION_RUN_ID,
    phase: 'map',
    focusChapter,
    nodes: map.nodes,
    layers: map.layers,
    currentNodeId: null,
    hp: 5,
    maxHp: 5,
    momentum: 0,
    bestMomentum: 0,
    score: 0,
    verifiedDepth: 0,
    relics: [],
    isDaily,
    history: [],
  };
}

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
  judgeMock.prefetchProblem.mockReset().mockResolvedValue({ phase: 'pending', attempts: 1 });
  judgeMock.retryPrefetch.mockReset().mockResolvedValue({ phase: 'pending', attempts: 2 });
  judgeMock.getPrefetchState.mockReset().mockReturnValue({ phase: 'idle', attempts: 0 });
  judgeMock.clearPrefetch.mockReset();
  // 默认使用后端权威画像/掌握度前沿；聚焦章节不足地图节点时循环这一真实排序。
  frontierApiMock.createExpeditionRun.mockReset().mockImplementation(
    async ({ focusChapter, isDaily }: { focusChapter: number | null; isDaily: boolean }) =>
      serverRun(focusChapter, 'PY43-KC-001', 2, isDaily),
  );
  // 默认没有后端活跃 run；startRun 新开局。
  frontierApiMock.fetchExpeditionRun.mockReset().mockResolvedValue(null);
  frontierApiMock.saveExpeditionRun.mockReset().mockResolvedValue({ runId: EXPEDITION_RUN_ID });
  frontierApiMock.deleteExpeditionRun.mockReset().mockResolvedValue(undefined);
  // 排行榜：默认不上报/拉到空（best-effort）。具体用例自行覆盖返回值。
  frontierApiMock.submitExpeditionScore.mockReset().mockResolvedValue(null);
  frontierApiMock.fetchExpeditionLeaderboard.mockReset().mockResolvedValue(null);
});

describe('rogueRun store', () => {
  it('只把本地远征存档写入当前用户命名空间', async () => {
    localStorage.setItem(
      'pythontopia.user',
      JSON.stringify({ id: 11, username: 'alice', displayName: 'A', role: 'STUDENT' }),
    );
    localStorage.setItem('pyt_rogue_run_v1', 'legacy-must-not-be-touched');
    setActivePinia(createPinia());

    const run = useRogueRunStore();
    await run.startRun(null);

    expect(JSON.parse(localStorage.getItem('pyt_rogue_run_v1@u:alice') ?? '{}')).toMatchObject({
      phase: 'map',
    });
    expect(localStorage.getItem('pyt_rogue_run_v1')).toBe('legacy-must-not-be-touched');
  });

  it('同一浏览器的 B 账号不会从本地兜底读到 A 的远征存档', async () => {
    localStorage.setItem(
      'pythontopia.user',
      JSON.stringify({ id: 11, username: 'alice', displayName: 'A', role: 'STUDENT' }),
    );
    setActivePinia(createPinia());
    const alice = useRogueRunStore();
    await alice.startRun(null);

    localStorage.setItem(
      'pythontopia.user',
      JSON.stringify({ id: 22, username: 'bob', displayName: 'B', role: 'STUDENT' }),
    );
    setActivePinia(createPinia());
    frontierApiMock.fetchExpeditionRun.mockRejectedValueOnce(new Error('offline'));
    const bob = useRogueRunStore();

    expect(await bob.refreshSavedRun()).toBe(false);
    expect(bob.hasSavedRun).toBe(false);
  });

  it('每日挑战完成日期也按用户隔离，不读旧的无 owner 键', () => {
    localStorage.setItem('pyt_rogue_daily_v1', new Date().toISOString().slice(0, 10));
    localStorage.setItem(
      'pythontopia.user',
      JSON.stringify({ id: 11, username: 'alice', displayName: 'A', role: 'STUDENT' }),
    );
    setActivePinia(createPinia());

    expect(useRogueRunStore().dailyAvailable).toBe(true);
  });

  it('startRun builds a map with an available start node', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
    expect(run.phase).toBe('map');
    expect(run.nodes.length).toBeGreaterThan(0);
    const start = run.nodes.find((n) => n.layer === 0);
    expect(start?.kind).toBe('battle');
    expect(start?.state).toBe('available');
  });

  it('prefetches the available start battle node after landing on the map', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
    await new Promise((resolve) => setTimeout(resolve, 0)); // flush phase watcher（落地图触发预取）
    expect(judgeMock.clearPrefetch).toHaveBeenCalled();
    const start = run.nodes.find((n) => n.layer === 0);
    expect(judgeMock.prefetchProblem).toHaveBeenCalledWith(
      expect.objectContaining({ kc: start?.kcCode, kind: start?.challengeKind }),
    );
  });

  it('persists the new run id before warming its first battle', async () => {
    const run = useRogueRunStore();
    frontierApiMock.saveExpeditionRun.mockImplementationOnce(async () => {
      expect(judgeMock.prefetchProblem).not.toHaveBeenCalled();
      return { runId: EXPEDITION_RUN_ID };
    });

    await run.startRun(null);

    const start = run.nodes.find((n) => n.layer === 0);
    expect(judgeMock.prefetchProblem).toHaveBeenCalledWith(
      expect.objectContaining({
        kc: start?.kcCode,
        kind: start?.challengeKind,
        expeditionRunId: EXPEDITION_RUN_ID,
      }),
    );
  });

  it('sends the current run id when fetching and settling a challenge', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
    const start = run.nodes.find((n) => n.layer === 0);
    await run.enterNode(start!.id);

    expect(judgeMock.fetchProblem).toHaveBeenCalledWith(
      expect.objectContaining({ kc: start?.kcCode, expeditionRunId: EXPEDITION_RUN_ID }),
    );

    run.finishRun();
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(frontierApiMock.submitExpeditionScore).toHaveBeenCalledWith({ runId: EXPEDITION_RUN_ID });
  });

  it('does not accept client score fields in the leaderboard settlement payload', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
    run.score = 999_999;
    run.finishRun();
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(frontierApiMock.submitExpeditionScore).toHaveBeenCalledWith({ runId: EXPEDITION_RUN_ID });
    expect(frontierApiMock.submitExpeditionScore.mock.calls[0]?.[0]).not.toHaveProperty('score');
  });

  it('settles a finished run before creating the next server-owned run', async () => {
    const nextRunId = '223e4567-e89b-12d3-a456-426614174000';
    frontierApiMock.saveExpeditionRun
      .mockResolvedValueOnce({ runId: EXPEDITION_RUN_ID })
      .mockResolvedValueOnce({ runId: nextRunId });
    let finishSettlement!: (value: {
      entries: never[];
      selfRank: null;
      selfBest: number;
      inClass: boolean;
    }) => void;
    frontierApiMock.submitExpeditionScore.mockImplementationOnce(
      () => new Promise((resolve) => { finishSettlement = resolve; }),
    );
    const run = useRogueRunStore();
    await run.startRun(null);

    run.finishRun();
    const restart = run.startRun(null);
    await Promise.resolve();
    expect(frontierApiMock.createExpeditionRun).toHaveBeenCalledTimes(1);
    expect(frontierApiMock.saveExpeditionRun).toHaveBeenCalledTimes(1);

    finishSettlement({ entries: [], selfRank: null, selfBest: 0, inClass: false });
    await restart;

    expect(frontierApiMock.submitExpeditionScore).toHaveBeenCalledTimes(1);
    expect(frontierApiMock.saveExpeditionRun).toHaveBeenCalledTimes(2);
    expect(Object.values(localStorage).join('')).toContain(nextRunId);
  });

  it('passing a battle awards score + momentum and clears the node, unlocking next', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
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
    const unlocked = (start!.next ?? []).every(
      (id) => run.nodes.find((n) => n.id === id)?.state === 'available',
    );
    expect(unlocked).toBe(true);
  });

  it('failing a battle costs HP gently (resilience 0.5 → -2) and resets momentum', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
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

  it('initialises challenge editor from the fetched starter code', async () => {
    judgeMock.fetchProblem.mockResolvedValueOnce({
      ...problem(),
      starterCode: 'total = 0\nfor item in items:\n    total += item\nprint(total)\n',
      buggyCode: 'total = 0\nfor item in items:\n    total = item\nprint(total)\n',
    });
    const run = useRogueRunStore();
    await run.startRun(null);
    const start = run.nodes.find((n) => n.layer === 0);

    await run.enterNode(start!.id);

    expect(run.phase).toBe('challenge');
    expect(run.activeCode).toContain('total = 0');
    expect(run.activeCode).toContain('total += item');
  });

  it('initialises challenge editor from buggy code when starter code is empty', async () => {
    judgeMock.fetchProblem.mockResolvedValueOnce({
      ...problem(),
      starterCode: '',
      buggyCode: 'total = 0\nfor item in items:\n    total = item\nprint(total)\n',
    });
    const run = useRogueRunStore();
    await run.startRun(null);
    const start = run.nodes.find((n) => n.layer === 0);

    await run.enterNode(start!.id);

    expect(run.phase).toBe('challenge');
    expect(run.activeCode).toContain('total = item');
  });

  it('persists challenge entry before waiting for a generated problem', async () => {
    let resolveProblem: ((value: BossProblemDto) => void) | undefined;
    judgeMock.fetchProblem.mockReturnValueOnce(new Promise((resolve) => {
      resolveProblem = resolve;
    }));

    const run = useRogueRunStore();
    await run.startRun(null);
    frontierApiMock.saveExpeditionRun.mockClear();
    const start = run.nodes.find((n) => n.layer === 0);

    const entering = run.enterNode(start!.id);
    await Promise.resolve();
    await Promise.resolve();

    const persisted = JSON.parse(localStorage.getItem('pyt_rogue_run_v1@u:anon') ?? '{}');
    expect(persisted.phase).toBe('challenge');
    expect(persisted.currentNodeId).toBe(start!.id);
    expect(frontierApiMock.saveExpeditionRun).toHaveBeenCalledWith(
      expect.objectContaining({
        phase: 'challenge',
        currentNodeId: start!.id,
      }),
    );

    resolveProblem?.(problem());
    await entering;
  });

  it('ignores a late problem response after the run is aborted and a new run starts', async () => {
    let resolveProblem: ((value: BossProblemDto) => void) | undefined;
    judgeMock.fetchProblem.mockReturnValueOnce(new Promise((resolve) => {
      resolveProblem = resolve;
    }));
    const run = useRogueRunStore();
    await run.startRun(null);
    const start = run.nodes.find((node) => node.layer === 0);
    const entering = run.enterNode(start!.id);

    await run.abort();
    await run.startRun(null);
    resolveProblem?.(problem());
    await entering;

    expect(run.phase).toBe('map');
    expect(run.activeProblem).toBeNull();
    expect(run.activeCode).toBe('');
    expect(run.loading).toBe(false);
  });

  it('serializes snapshot writes and sends the latest snapshot with the server run id', async () => {
    let resolveInitialSave: ((value: { runId: string }) => void) | undefined;
    frontierApiMock.saveExpeditionRun
      .mockImplementationOnce(() => new Promise((resolve) => {
        resolveInitialSave = resolve;
      }))
      .mockResolvedValueOnce({ runId: EXPEDITION_RUN_ID, score: 0, verifiedDepth: 0 });
    const run = useRogueRunStore();
    const starting = run.startRun(null);
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(frontierApiMock.saveExpeditionRun).toHaveBeenCalledTimes(1);
    run.hp = 3;
    run.saveCheckpoint();
    expect(frontierApiMock.saveExpeditionRun).toHaveBeenCalledTimes(1);

    resolveInitialSave?.({ runId: EXPEDITION_RUN_ID });
    await starting;
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(frontierApiMock.saveExpeditionRun).toHaveBeenCalledTimes(2);
    expect(frontierApiMock.saveExpeditionRun).toHaveBeenLastCalledWith(
      expect.objectContaining({ hp: 3, runId: EXPEDITION_RUN_ID }),
    );
  });

  it('waits for an in-flight save before deleting the run on abort', async () => {
    let resolveSave: ((value: { runId: string }) => void) | undefined;
    frontierApiMock.saveExpeditionRun.mockImplementationOnce(() => new Promise((resolve) => {
      resolveSave = resolve;
    }));
    const run = useRogueRunStore();
    const starting = run.startRun(null);
    await new Promise((resolve) => setTimeout(resolve, 0));
    const aborting = run.abort();

    expect(frontierApiMock.deleteExpeditionRun).not.toHaveBeenCalled();
    resolveSave?.({ runId: EXPEDITION_RUN_ID });
    await Promise.all([starting, aborting]);

    expect(frontierApiMock.deleteExpeditionRun).toHaveBeenCalledTimes(1);
  });

  it('relic 沉静心法(calm_mind) softens damage from -2 to -1', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
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
    const baseRun = useRogueRunStore();
    await baseRun.startRun(null);
    const baseStart = baseRun.nodes.find((n) => n.layer === 0);
    await baseRun.enterNode(baseStart!.id);
    await baseRun.submitChallenge();
    const baseScore = baseRun.score;

    setActivePinia(createPinia());
    const relicRun = useRogueRunStore();
    await relicRun.startRun(null);
    relicRun.relics.push('combo_scholar');
    const relicStart = relicRun.nodes.find((n) => n.layer === 0);
    await relicRun.enterNode(relicStart!.id);
    await relicRun.submitChallenge();
    expect(relicRun.score).toBeGreaterThan(baseScore);
  });

  it('event 复盘(review) heals + scores + clears node, returns to map without judging', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
    const node = run.nodes.find((n) => n.layer === 0);
    node!.kcCode = 'PY43-KC-001';
    run.hp = 3;
    const score0 = run.score;
    run.activeNode = node!;
    run.phase = 'event';
    await run.chooseEvent('review');
    expect(run.phase).toBe('map');
    expect(run.hp).toBe(4);
    expect(run.score).toBeGreaterThan(score0);
    expect(run.nodes.find((n) => n.id === node!.id)?.state).toBe('cleared');
    expect(run.attempts).toBe(0);
  });

  it('event 钻研(study) raises one challenge without rewriting the server map', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
    const node = run.nodes.find((n) => n.layer === 0);
    node!.kcCode = 'PY43-KC-001';
    const d0 = node!.difficulty;
    run.activeNode = node!;
    run.phase = 'event';
    judgeMock.fetchProblem.mockResolvedValue(problem());
    await run.chooseEvent('study');
    expect(node!.difficulty).toBe(d0);
    expect(run.activeDifficulty).toBe(Math.min(5, d0 + 1));
    expect(run.phase).toBe('challenge');
  });

  it('rest node heals and clears in-place without fetching a problem', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
    const node = run.nodes.find((n) => n.layer === 0);
    node!.kind = 'rest';
    run.hp = 2;

    await run.enterNode(node!.id);

    expect(judgeMock.fetchProblem).not.toHaveBeenCalled();
    expect(run.phase).toBe('map');
    expect(run.hp).toBeGreaterThan(2);
    expect(run.nodes.find((n) => n.id === node!.id)?.state).toBe('cleared');
  });

  it('chest node opens a three-choice reward; claiming returns to map with the relic', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
    const node = run.nodes.find((n) => n.layer === 0);
    node!.kind = 'chest';

    await run.enterNode(node!.id);

    expect(judgeMock.fetchProblem).not.toHaveBeenCalled();
    expect(run.phase).toBe('reward');
    expect(run.rewardChoices.length).toBeGreaterThan(0);
    expect(run.rewardChoices.length).toBeLessThanOrEqual(3);
    expect(run.relics).toHaveLength(0); // not granted until chosen
    expect(run.nodes.find((n) => n.id === node!.id)?.state).toBe('cleared');

    const picked = run.rewardChoices[0]!;
    run.chooseReward(picked);
    expect(run.phase).toBe('map');
    expect(run.relics).toEqual([picked]);
  });

  it('relic 急救包(first_aid) shields the first mistake, then later mistakes cost HP', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
    run.relics.push('first_aid');
    const start = run.nodes.find((n) => n.layer === 0);
    const hp0 = run.hp;
    judgeMock.judge.mockResolvedValue({
      problemId: 'p1', passed: false, passedTestCases: 0, totalTestCases: 1, score: 0, feedback: 'wa',
    });
    await run.enterNode(start!.id);
    await run.submitChallenge();
    expect(run.hp).toBe(hp0); // first failure shielded

    await run.retryChallenge();
    await run.submitChallenge();
    expect(run.hp).toBe(hp0 - 2); // second failure costs HP normally
  });

  it('relic 急智(quick_thinker) boosts score when solved quickly', async () => {
    judgeMock.judge.mockResolvedValue({
      problemId: 'p1', passed: true, passedTestCases: 1, totalTestCases: 1, score: 80, feedback: 'ok',
    });
    const baseRun = useRogueRunStore();
    await baseRun.startRun(null);
    const baseStart = baseRun.nodes.find((n) => n.layer === 0);
    await baseRun.enterNode(baseStart!.id);
    await baseRun.submitChallenge();
    const baseScore = baseRun.score;

    setActivePinia(createPinia());
    const fastRun = useRogueRunStore();
    await fastRun.startRun(null);
    fastRun.relics.push('quick_thinker');
    const fastStart = fastRun.nodes.find((n) => n.layer === 0);
    await fastRun.enterNode(fastStart!.id);
    await fastRun.submitChallenge();
    expect(fastRun.score).toBeGreaterThan(baseScore);
  });

  it('clearing a node at the half-way layer heals 1 HP (milestone)', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
    const halfway = Math.floor((run.layers - 1) / 2);
    const node = run.nodes.find((n) => n.layer === halfway);
    expect(node).toBeDefined();
    node!.kind = 'chest';
    node!.state = 'available';
    run.hp = run.maxHp - 2;

    await run.enterNode(node!.id);

    // chest 不直接回血，唯一来源是半程里程碑 +1。
    expect(run.hp).toBe(run.maxHp - 1);
  });

  it('rejects a structurally corrupt saved run instead of resuming into a crash', async () => {
    frontierApiMock.fetchExpeditionRun.mockResolvedValueOnce(null);
    localStorage.setItem('pyt_rogue_run_v1@u:anon', JSON.stringify({
      phase: 'map',
      focusChapter: 1,
      nodes: [{ id: 'L0-0', layer: 0 }], // missing col/kind/state/next → invalid
      layers: 1,
      hp: 3,
      maxHp: 5,
      momentum: 0,
      bestMomentum: 0,
      score: 0,
      relics: [],
      isDaily: false,
      history: [],
    }));

    const run = useRogueRunStore();
    const ok = await run.refreshSavedRun();

    expect(ok).toBe(false);
    expect(run.hasSavedRun).toBe(false);
  });

  it('raises in-run difficulty on a winning streak (momentum feeds back into the next fight)', async () => {
    judgeMock.judge.mockResolvedValue({
      problemId: 'p1', passed: true, passedTestCases: 1, totalTestCases: 1, score: 80, feedback: 'ok',
    });
    const run = useRogueRunStore();
    await run.startRun(null);
    // 直接把气势拉到 3，进入任一可战节点应出更高难度题。
    run.momentum = 3;
    const start = run.nodes.find((n) => n.layer === 0);
    await run.enterNode(start!.id);
    // base 2 + 气势(>=3) +1 = 3。
    expect(run.activeDifficulty).toBe(3);
    expect(judgeMock.fetchProblem).toHaveBeenLastCalledWith(
      expect.objectContaining({ difficulty: 3 }),
    );
  });

  it('boss requires beating bossPhasesTotal phases before victory', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
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

  it('daily run marks today done after the run ends and blocks a second daily', async () => {
    const run = useRogueRunStore();
    expect(run.dailyAvailable).toBe(true);
    await run.startDailyRun();
    expect(run.isDaily).toBe(true);
    expect(run.phase).toBe('map');
    run.hp = 0;
    run.activeNode = run.nodes.find((n) => n.layer === 0) ?? null;
    run.phase = 'challenge';
    run.continueAfterChallenge();
    expect(run.phase).toBe('over');
    expect(run.dailyAvailable).toBe(false);
    expect(frontierApiMock.createExpeditionRun).toHaveBeenCalledWith({
      focusChapter: null,
      isDaily: true,
    });
  });

  it('uses the server-issued map and challenge values when starting a run', async () => {
    frontierApiMock.createExpeditionRun.mockResolvedValueOnce(serverRun(null, 'PY43-KC-042', 4));
    const run = useRogueRunStore();
    await run.startRun(null);
    // 起点 battle 是第一个挑战节点，应消费后端首个 pick。
    const start = run.nodes.find((n) => n.layer === 0);
    expect(start?.kcCode).toBe('PY43-KC-042');
    expect(start?.difficulty).toBe(4);
  });

  it('keeps the run idle and reports an error when server-side run creation fails', async () => {
    frontierApiMock.createExpeditionRun.mockRejectedValueOnce(new Error('network'));
    const run = useRogueRunStore();
    await run.startRun(null);
    expect(run.phase).toBe('idle');
    expect(run.nodes).toHaveLength(0);
    expect(run.errorMessage).toContain('network');
  });

  it('shows an active backend run as a selectable save before continuing', async () => {
    frontierApiMock.fetchExpeditionRun.mockResolvedValueOnce({
      phase: 'map',
      focusChapter: 3,
      nodes: [
        {
          id: 'L0-0',
          layer: 0,
          col: 0,
          kind: 'battle',
          kcCode: 'PY43-KC-017',
          challengeKind: 'dual_track',
          difficulty: 4,
          next: [],
          state: 'available',
        },
      ],
      layers: 1,
      currentNodeId: 'L0-0',
      hp: 4,
      maxHp: 6,
      momentum: 2,
      bestMomentum: 3,
      score: 88,
      relics: ['calm_mind'],
      isDaily: false,
      history: [],
    });

    const run = useRogueRunStore();
    await run.refreshSavedRun();

    expect(frontierApiMock.createExpeditionRun).not.toHaveBeenCalled();
    expect(run.phase).toBe('idle');
    expect(run.hasSavedRun).toBe(true);
    expect(run.savedRunSummary?.focusChapter).toBe(3);

    await run.resumeSavedRun();

    expect(run.phase).toBe('map');
    expect(run.focusChapter).toBe(3);
    expect(run.nodes[0]?.kcCode).toBe('PY43-KC-017');
    expect(run.hp).toBe(4);
    expect(run.maxHp).toBe(6);
    expect(run.relics).toEqual(['calm_mind']);
  });

  it('falls back to localStorage when loading a selectable save and backend is unreachable', async () => {
    frontierApiMock.fetchExpeditionRun.mockRejectedValueOnce(new Error('offline'));
    localStorage.setItem('pyt_rogue_run_v1@u:anon', JSON.stringify({
      phase: 'map',
      focusChapter: 4,
      nodes: [
        {
          id: 'L0-0',
          layer: 0,
          col: 0,
          kind: 'battle',
          kcCode: 'PY43-KC-031',
          challengeKind: 'dual_track',
          difficulty: 2,
          next: [],
          state: 'available',
        },
      ],
      layers: 1,
      currentNodeId: null,
      hp: 3,
      maxHp: 5,
      momentum: 1,
      bestMomentum: 1,
      score: 42,
      relics: [],
      isDaily: false,
      history: [],
    }));

    const run = useRogueRunStore();
    await run.refreshSavedRun();

    expect(frontierApiMock.createExpeditionRun).not.toHaveBeenCalled();
    expect(run.phase).toBe('idle');
    expect(run.hasSavedRun).toBe(true);
    expect(run.savedRunSummary?.focusChapter).toBe(4);
    expect(run.savedRunSummary?.serverVerified).toBe(false);
    expect(run.savedRunSummary?.verifiedScore).toBe(0);
    expect(run.savedRunSummary?.verifiedDepth).toBe(0);

    await run.resumeSavedRun();

    expect(run.focusChapter).toBe(4);
    expect(run.nodes[0]?.kcCode).toBe('PY43-KC-031');
    expect(run.score).toBe(42);
    expect(run.verifiedScore).toBe(0);
    expect(run.verifiedDepth).toBe(0);
    expect(frontierApiMock.saveExpeditionRun).not.toHaveBeenCalled();
  });

  it('shows score and passed depth only from the server ledger', async () => {
    frontierApiMock.saveExpeditionRun
      .mockResolvedValueOnce({ runId: EXPEDITION_RUN_ID, score: 0, verifiedDepth: 0 })
      .mockResolvedValueOnce({ runId: EXPEDITION_RUN_ID, score: 95, verifiedDepth: 1 });
    const run = useRogueRunStore();
    await run.startRun(null);
    run.score = 999_999;
    run.saveCheckpoint();
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(run.score).toBe(999_999); // local experience score remains separate
    expect(run.verifiedScore).toBe(95);
    expect(run.verifiedDepth).toBe(1);
    expect(run.savedRunSummary).toMatchObject({
      verifiedScore: 95,
      verifiedDepth: 1,
      serverVerified: true,
    });
  });

  it('starts a fresh run instead of silently auto-resuming a saved run', async () => {
    frontierApiMock.fetchExpeditionRun.mockResolvedValueOnce({
      phase: 'map',
      focusChapter: 5,
      nodes: [
        {
          id: 'L0-0',
          layer: 0,
          col: 0,
          kind: 'battle',
          kcCode: 'PY43-KC-039',
          challengeKind: 'dual_track',
          difficulty: 4,
          next: [],
          state: 'available',
        },
      ],
      layers: 1,
      currentNodeId: 'L0-0',
      hp: 4,
      maxHp: 5,
      momentum: 1,
      bestMomentum: 1,
      score: 99,
      relics: [],
      isDaily: false,
      history: [],
    });
    const run = useRogueRunStore();
    await run.refreshSavedRun();

    await run.startRun(1);

    expect(run.phase).toBe('map');
    expect(run.focusChapter).toBe(1);
    expect(run.nodes[0]?.kcCode).not.toBe('PY43-KC-039');
    expect(frontierApiMock.createExpeditionRun).toHaveBeenCalledWith({
      focusChapter: 1,
      isDaily: false,
    });
  });

  it('finishRun settles the run and reports the score to the class leaderboard once', async () => {
    const board = {
      entries: [{ rank: 1, userId: 7, displayName: '我', bestScore: 120, bestDepth: 0, self: true }],
      selfRank: 1,
      selfBest: 120,
      inClass: true,
    };
    frontierApiMock.submitExpeditionScore.mockResolvedValue(board);
    const run = useRogueRunStore();
    await run.startRun(null);
    run.score = 120;
    run.bestMomentum = 2;

    run.finishRun();
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(run.phase).toBe('over');
    expect(frontierApiMock.submitExpeditionScore).toHaveBeenCalledTimes(1);
    expect(frontierApiMock.submitExpeditionScore).toHaveBeenCalledWith(
      { runId: EXPEDITION_RUN_ID },
    );
    expect(run.leaderboard).toEqual(board);

    // 已结算后再点「结束本局」不重复上报。
    run.finishRun();
    expect(frontierApiMock.submitExpeditionScore).toHaveBeenCalledTimes(1);
  });

  it('does not open a new run when settlement returns null and retries on the next start', async () => {
    const board = { entries: [], selfRank: null, selfBest: 0, inClass: false };
    frontierApiMock.submitExpeditionScore
      .mockResolvedValueOnce(null)
      .mockResolvedValueOnce(board);
    const run = useRogueRunStore();
    await run.startRun(null);

    run.finishRun();
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(frontierApiMock.saveExpeditionRun).toHaveBeenCalledTimes(1);

    await run.startRun(null);

    expect(frontierApiMock.submitExpeditionScore).toHaveBeenCalledTimes(2);
    expect(frontierApiMock.saveExpeditionRun).toHaveBeenCalledTimes(2);
  });

  it('loadLeaderboard populates the class leaderboard state (best-effort)', async () => {
    const board = {
      entries: [
        { rank: 1, userId: 1, displayName: '甲', bestScore: 300, bestDepth: 6, self: false },
        { rank: 2, userId: 7, displayName: '我', bestScore: 150, bestDepth: 3, self: true },
      ],
      selfRank: 2,
      selfBest: 150,
      inClass: true,
    };
    frontierApiMock.fetchExpeditionLeaderboard.mockResolvedValueOnce(board);
    const run = useRogueRunStore();
    await run.loadLeaderboard();
    expect(run.leaderboard).toEqual(board);
  });

  it('连点「再试一次」只出一题（retryChallenge 并发守卫，防重复 LLM 计费）', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
    const start = run.nodes.find((n) => n.layer === 0);
    judgeMock.judge.mockResolvedValue({
      problemId: 'p1', passed: false, passedTestCases: 0, totalTestCases: 1, score: 0, feedback: 'wa',
    });
    await run.enterNode(start!.id);
    await run.submitChallenge();
    expect(run.challengeResult?.passed).toBe(false);

    judgeMock.retryPrefetch.mockClear();
    judgeMock.fetchProblem.mockClear();

    // 双击：两次调用在同一帧发出（第二次必须被 loading 守卫吞掉）。
    const first = run.retryChallenge();
    const second = run.retryChallenge();
    await Promise.all([first, second]);

    expect(judgeMock.retryPrefetch).toHaveBeenCalledTimes(1);
    expect(judgeMock.fetchProblem).toHaveBeenCalledTimes(1);
    expect(run.loading).toBe(false);
  });

  it('does not double-report when a run settles via HP loss then finishRun', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
    run.hp = 0;
    run.activeNode = run.nodes.find((n) => n.layer === 0) ?? null;
    run.phase = 'challenge';
    run.continueAfterChallenge(); // hp 0 → 结算并上报一次
    expect(run.phase).toBe('over');
    expect(frontierApiMock.submitExpeditionScore).toHaveBeenCalledTimes(1);
    run.finishRun(); // 已 over，提前返回，不重复上报
    expect(frontierApiMock.submitExpeditionScore).toHaveBeenCalledTimes(1);
  });
});
