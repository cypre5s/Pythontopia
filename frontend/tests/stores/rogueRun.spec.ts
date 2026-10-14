import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import type { BossProblemDto } from '@/api/dto/Boss';
import { ApiClientError } from '@/api/http';

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
  performExpeditionAction: vi.fn(),
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
  performExpeditionAction: frontierApiMock.performExpeditionAction,
  fetchExpeditionRun: frontierApiMock.fetchExpeditionRun,
  saveExpeditionRun: frontierApiMock.saveExpeditionRun,
  deleteExpeditionRun: frontierApiMock.deleteExpeditionRun,
  submitExpeditionScore: frontierApiMock.submitExpeditionScore,
  fetchExpeditionLeaderboard: frontierApiMock.fetchExpeditionLeaderboard,
}));

import { useRogueRunStore } from '@/stores/rogueRun';
import { generateRogueMap } from '@/data/rogueMap';
import { computeLayeredDifficulty, NODE_KIND_POOLS } from '@/data/rogueConfig';
import type { ExpeditionRunAction, ExpeditionRunSnapshot } from '@/api/expedition';

let mockServerRun: ExpeditionRunSnapshot | null = null;

function copyRun(run: ExpeditionRunSnapshot): ExpeditionRunSnapshot {
  return {
    ...run,
    nodes: run.nodes.map((node) => ({
      ...node,
      next: [...node.next],
      rewardChoices: node.rewardChoices ? [...node.rewardChoices] : undefined,
    })),
    relics: [...run.relics],
    history: run.history.map((entry) => ({ ...entry })),
  };
}

function setServerNode(id: string, update: (node: ExpeditionRunSnapshot['nodes'][number]) => void): void {
  const node = mockServerRun?.nodes.find((item) => item.id === id);
  if (node) update(node);
}

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
  mockServerRun = null;
  judgeMock.fetchProblem.mockReset().mockResolvedValue(problem());
  judgeMock.judge.mockReset();
  judgeMock.prefetchProblem.mockReset().mockResolvedValue({ phase: 'pending', attempts: 1 });
  judgeMock.retryPrefetch.mockReset().mockResolvedValue({ phase: 'pending', attempts: 2 });
  judgeMock.getPrefetchState.mockReset().mockReturnValue({ phase: 'idle', attempts: 0 });
  judgeMock.clearPrefetch.mockReset();
  // 默认使用后端权威画像/掌握度前沿；聚焦章节不足地图节点时循环这一真实排序。
  frontierApiMock.createExpeditionRun.mockReset().mockImplementation(
    async ({ focusChapter, isDaily }: { focusChapter: number | null; isDaily: boolean }) => {
      mockServerRun = serverRun(focusChapter, 'PY43-KC-001', 2, isDaily);
      return copyRun(mockServerRun);
    },
  );
  frontierApiMock.performExpeditionAction.mockReset().mockImplementation(
    async (command: ExpeditionRunAction) => {
      const snapshot = copyRun(mockServerRun ?? serverRun());
      const nodeId = 'nodeId' in command ? command.nodeId : null;
      const node = nodeId ? snapshot.nodes.find((item) => item.id === nodeId) : null;
      if (command.action === 'ENTER_NODE' && node) {
        snapshot.currentNodeId = node.id;
        if (node.kind === 'rest' || node.kind === 'chest') node.state = 'cleared';
        if (node.kind === 'chest') {
          node.rewardChoices = ['sturdy_notes', 'calm_mind', 'combo_scholar'];
        }
        snapshot.phase = node.kind === 'event' ? 'event'
          : node.kind === 'chest' ? 'reward'
            : node.kind === 'rest' ? 'map' : 'challenge';
        if (node.kind === 'rest') snapshot.hp = Math.min(snapshot.maxHp, snapshot.hp + 1);
      } else if (command.action === 'EVENT_REVIEW' && node) {
        snapshot.phase = 'map';
        node.state = 'cleared';
        snapshot.hp = Math.min(snapshot.maxHp, snapshot.hp + 1);
      } else if ((command.action === 'EVENT_STUDY' || command.action === 'EVENT_STEADY') && node) {
        snapshot.phase = 'challenge';
      } else if (command.action === 'CLAIM_RELIC' && node) {
        snapshot.phase = 'map';
        node.state = 'cleared';
        delete node.rewardChoices;
        if (!snapshot.relics.includes(command.relicId)) snapshot.relics.push(command.relicId);
      } else if (command.action === 'SETTLE_CHALLENGE' && node) {
        const latest = judgeMock.judge.mock.results.at(-1)?.value;
        const judged = await Promise.resolve(latest as ReturnType<typeof judgeMock.judge> | undefined);
        const passed = judged?.passed === true;
        const at = new Date().toISOString();
        snapshot.history.unshift({
          nodeId: node.id, kcCode: node.kcCode, kind: node.kind, passed,
          score: passed ? judged.score : 0, at,
        });
        if (passed) {
          snapshot.score += judged.score;
          snapshot.verifiedDepth = (snapshot.verifiedDepth ?? 0) + 1;
          snapshot.momentum += 1;
          if (node.kind === 'boss' && (node.bossPhase ?? 1) < 3) {
            node.bossPhase = (node.bossPhase ?? 1) + 1;
            snapshot.phase = 'challenge';
          } else {
            node.state = 'cleared';
            snapshot.phase = node.kind === 'boss' ? 'over' : node.kind === 'elite' ? 'reward' : 'map';
            if (node.kind === 'elite' && snapshot.phase === 'reward') {
              node.rewardChoices = ['sturdy_notes', 'calm_mind', 'combo_scholar'];
            }
            for (const nextId of node.next) {
              const next = snapshot.nodes.find((item) => item.id === nextId);
              if (next?.state === 'locked') next.state = 'available';
            }
          }
        } else {
          snapshot.hp = Math.max(0, snapshot.hp - 2);
          snapshot.momentum = 0;
          snapshot.phase = snapshot.hp === 0 ? 'over' : 'challenge';
        }
      } else if (command.action === 'FINISH') {
        snapshot.phase = 'over';
      }
      mockServerRun = copyRun(snapshot);
      return copyRun(snapshot);
    },
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
    localStorage.setItem(
      'pyt_rogue_daily_v1',
      new Date(Date.now() + 8 * 60 * 60 * 1000).toISOString().slice(0, 10),
    );
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

  it('uses the server run id and node binding when warming its first battle', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);

    const start = run.nodes.find((n) => n.layer === 0);
    expect(judgeMock.prefetchProblem).toHaveBeenCalledWith(
      expect.objectContaining({
        kc: start?.kcCode,
        kind: start?.challengeKind,
        expeditionRunId: EXPEDITION_RUN_ID,
        expeditionNodeId: start?.id,
      }),
    );
  });

  it('sends the current run id when fetching and settling a challenge', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
    const start = run.nodes.find((n) => n.layer === 0);
    await run.enterNode(start!.id);

    expect(judgeMock.fetchProblem).toHaveBeenCalledWith(
      expect.objectContaining({
        kc: start?.kcCode,
        expeditionRunId: EXPEDITION_RUN_ID,
        expeditionNodeId: start?.id,
      }),
    );

    run.finishRun();
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(frontierApiMock.submitExpeditionScore).toHaveBeenCalledWith({ runId: EXPEDITION_RUN_ID });
  });

  it('uses the difficulty returned with the issued question for display and score', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
    const start = run.nodes.find((node) => node.layer === 0)!;
    run.momentum = 3;
    if (mockServerRun) mockServerRun.momentum = 3;
    await run.enterNode(start.id);

    expect(judgeMock.fetchProblem).toHaveBeenCalledWith(expect.objectContaining({ difficulty: 3 }));
    expect(run.activeDifficulty).toBe(problem().difficulty);

    judgeMock.judge.mockResolvedValue({
      problemId: 'p1', passed: true, passedTestCases: 1, totalTestCases: 1, score: 80, feedback: 'ok',
    });
    await run.submitChallenge();

    expect(run.score).toBe(26);
  });

  it('enters a node through the server action and applies its canonical snapshot', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
    const start = run.nodes.find((node) => node.layer === 0)!;
    const canonical = serverRun();
    canonical.phase = 'challenge';
    canonical.currentNodeId = start.id;
    frontierApiMock.performExpeditionAction.mockResolvedValueOnce(canonical);

    await run.enterNode(start.id);

    expect(frontierApiMock.performExpeditionAction).toHaveBeenCalledWith({
      runId: EXPEDITION_RUN_ID,
      action: 'ENTER_NODE',
      nodeId: start.id,
    });
    expect(run.phase).toBe('challenge');
    expect(run.currentNodeId).toBe(start.id);
    expect(run.nodes.find((node) => node.id === start.id)?.state).toBe('available');
  });

  it('settles a judged challenge from the server ledger and only then returns to the map', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
    const start = run.nodes.find((node) => node.layer === 0)!;
    await run.enterNode(start.id);
    judgeMock.judge.mockResolvedValue({
      problemId: 'p1', passed: true, passedTestCases: 1, totalTestCases: 1, score: 80, feedback: 'ok',
    });
    const canonical = serverRun();
    canonical.phase = 'map';
    canonical.currentNodeId = start.id;
    canonical.score = 80;
    canonical.verifiedDepth = 1;
    canonical.history = [{ nodeId: start.id, kcCode: start.kcCode, kind: start.kind, passed: true, score: 80, at: new Date().toISOString() }];
    canonical.nodes = canonical.nodes.map((node) =>
      node.id === start.id ? { ...node, state: 'cleared' } : node,
    );
    frontierApiMock.performExpeditionAction.mockResolvedValueOnce(canonical);

    await run.submitChallenge();

    expect(frontierApiMock.performExpeditionAction).toHaveBeenLastCalledWith({
      runId: EXPEDITION_RUN_ID,
      action: 'SETTLE_CHALLENGE',
      nodeId: start.id,
      problemId: 'p1',
    });
    expect(run.phase).toBe('challenge');
    expect(run.challengeResult?.passed).toBe(true);
    expect(run.score).toBe(start.difficulty * 10);
    expect(run.verifiedScore).toBe(80);
    expect(run.nodes.find((node) => node.id === start.id)?.state).toBe('cleared');

    await run.continueAfterChallenge();
    expect(run.phase).toBe('map');
  });

  it('does not finish a run while its challenge is still being judged', async () => {
    let resolveJudge!: (value: {
      problemId: string;
      passed: boolean;
      passedTestCases: number;
      totalTestCases: number;
      score: number;
      feedback: string;
    }) => void;
    judgeMock.judge.mockReturnValue(new Promise((resolve) => { resolveJudge = resolve; }));

    const run = useRogueRunStore();
    await run.startRun(null);
    const start = run.nodes.find((node) => node.layer === 0)!;
    await run.enterNode(start.id);

    const submission = run.submitChallenge();
    await run.finishRun();

    expect(frontierApiMock.performExpeditionAction).not.toHaveBeenCalledWith(
      expect.objectContaining({ action: 'FINISH' }),
    );

    resolveJudge({
      problemId: 'p1', passed: true, passedTestCases: 1, totalTestCases: 1, score: 80, feedback: 'ok',
    });
    await submission;

    expect(frontierApiMock.performExpeditionAction).toHaveBeenLastCalledWith({
      runId: EXPEDITION_RUN_ID, action: 'SETTLE_CHALLENGE', nodeId: start.id, problemId: 'p1',
    });
  });

  it('retries settlement for the same judged problem without requesting or charging for a new one', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
    const start = run.nodes.find((node) => node.layer === 0)!;
    await run.enterNode(start.id);
    judgeMock.judge.mockResolvedValue({
      problemId: 'p1', passed: true, passedTestCases: 1, totalTestCases: 1, score: 80, feedback: 'ok',
    });
    const canonical = serverRun();
    canonical.phase = 'map';
    canonical.currentNodeId = start.id;
    canonical.score = 80;
    canonical.verifiedDepth = 1;
    canonical.nodes = canonical.nodes.map((node) =>
      node.id === start.id ? { ...node, state: 'cleared' } : node,
    );
    frontierApiMock.performExpeditionAction
      .mockRejectedValueOnce(new Error('temporary settlement failure'))
      .mockResolvedValueOnce(canonical);

    await run.submitChallenge();

    expect(run.settlementPending).toBe(true);
    expect(run.challengeResult?.problemId).toBe('p1');
    await run.finishRun();
    expect(frontierApiMock.performExpeditionAction).not.toHaveBeenCalledWith(
      expect.objectContaining({ action: 'FINISH' }),
    );
    await run.submitChallenge();
    await run.continueAfterChallenge();

    expect(run.settlementPending).toBe(false);
    expect(run.phase).toBe('map');
    expect(judgeMock.judge).toHaveBeenCalledTimes(1);
    expect(judgeMock.fetchProblem).toHaveBeenCalledTimes(1);
    expect(frontierApiMock.performExpeditionAction.mock.calls.slice(-2).map(([action]) => action)).toEqual([
      expect.objectContaining({ action: 'SETTLE_CHALLENGE', problemId: 'p1' }),
      expect.objectContaining({ action: 'SETTLE_CHALLENGE', problemId: 'p1' }),
    ]);
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
    frontierApiMock.createExpeditionRun.mockResolvedValueOnce({ ...serverRun(), runId: nextRunId });

    run.finishRun();
    const restart = run.startRun(null);
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(frontierApiMock.createExpeditionRun).toHaveBeenCalledTimes(1);
    expect(frontierApiMock.performExpeditionAction).toHaveBeenCalledWith({
      runId: EXPEDITION_RUN_ID, action: 'FINISH',
    });

    finishSettlement({ entries: [], selfRank: null, selfBest: 0, inClass: false });
    await restart;

    expect(frontierApiMock.submitExpeditionScore).toHaveBeenCalledTimes(1);
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
    expect(run.score).toBe(start!.difficulty * 10);
    expect(run.verifiedScore).toBe(80);
    expect(run.momentum).toBe(1);
    expect(run.nodes.find((n) => n.id === start!.id)?.state).toBe('cleared');
    run.continueAfterChallenge();
    expect(run.phase).toBe('map');
    const unlocked = (start!.next ?? []).every(
      (id) => run.nodes.find((n) => n.id === id)?.state === 'available',
    );
    expect(unlocked).toBe(true);
  });

  it('applies relic score bonuses to game points, while verified points remain ledger-backed', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
    run.relics = ['combo_scholar', 'quick_thinker'];
    if (mockServerRun) mockServerRun.relics = [...run.relics];
    const start = run.nodes.find((n) => n.layer === 0)!;
    judgeMock.judge.mockResolvedValue({
      problemId: 'p1', passed: true, passedTestCases: 1, totalTestCases: 1, score: 80, feedback: 'ok',
    });

    await run.enterNode(start.id);
    for (let second = 0; second < 30; second += 1) run.tickSecond();
    const expected = Math.round(run.activeDifficulty * 10 * 1.2 * 1.5);
    await run.submitChallenge();

    expect(run.score).toBe(expected);
    expect(run.verifiedScore).toBe(80);
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

  it('records node entry with the server before requesting its challenge', async () => {
    let resolveProblem: ((value: BossProblemDto) => void) | undefined;
    judgeMock.fetchProblem.mockReturnValueOnce(new Promise((resolve) => {
      resolveProblem = resolve;
    }));

    const run = useRogueRunStore();
    await run.startRun(null);
    frontierApiMock.performExpeditionAction.mockClear();
    const start = run.nodes.find((n) => n.layer === 0);

    const entering = run.enterNode(start!.id);
    await new Promise((resolve) => setTimeout(resolve, 0));

    const persisted = JSON.parse(localStorage.getItem('pyt_rogue_run_v1@u:anon') ?? '{}');
    expect(persisted.phase).toBe('challenge');
    expect(persisted.currentNodeId).toBe(start!.id);
    expect(frontierApiMock.performExpeditionAction).toHaveBeenCalledWith(expect.objectContaining({
      runId: EXPEDITION_RUN_ID,
      action: 'ENTER_NODE',
      nodeId: start!.id,
    }));
    expect(judgeMock.fetchProblem).toHaveBeenCalledWith(expect.objectContaining({ expeditionNodeId: start!.id }));

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

  it('keeps client-edited score out of local recovery snapshots marked server verified', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
    run.hp = 3;
    run.score = 999_999;
    run.saveCheckpoint();
    const preview = JSON.parse(localStorage.getItem('pyt_rogue_run_v1@u:anon') ?? '{}');
    expect(preview).toMatchObject({ hp: 3, score: 0, verifiedDepth: 0 });
    expect(frontierApiMock.saveExpeditionRun).not.toHaveBeenCalled();
  });

  it('waits for an in-flight server action before deleting the run on abort', async () => {
    let resolveAction: ((value: ExpeditionRunSnapshot) => void) | undefined;
    frontierApiMock.performExpeditionAction.mockImplementationOnce(() => new Promise((resolve) => {
      resolveAction = resolve;
    }));
    const run = useRogueRunStore();
    await run.startRun(null);
    const start = run.nodes.find((node) => node.layer === 0)!;
    const entering = run.enterNode(start.id);
    await Promise.resolve();
    const aborting = run.abort();

    expect(frontierApiMock.deleteExpeditionRun).not.toHaveBeenCalled();
    resolveAction?.(serverRun());
    await Promise.all([entering, aborting]);

    expect(frontierApiMock.deleteExpeditionRun).toHaveBeenCalledTimes(1);
  });

  it('sends event review to the server and applies its canonical snapshot', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
    const node = run.nodes.find((n) => n.layer === 0);
    node!.kind = 'event';
    setServerNode(node!.id, (serverNode) => { serverNode.kind = 'event'; });
    run.hp = 3;
    if (mockServerRun) mockServerRun.hp = 3;
    await run.enterNode(node!.id);
    await run.chooseEvent('review');

    expect(frontierApiMock.performExpeditionAction).toHaveBeenLastCalledWith({
      runId: EXPEDITION_RUN_ID, action: 'EVENT_REVIEW', nodeId: node!.id,
    });
    expect(run.phase).toBe('map');
    expect(run.hp).toBe(4);
    expect(run.nodes.find((n) => n.id === node!.id)?.state).toBe('cleared');
    expect(run.attempts).toBe(0);
    expect(run.score).toBe(node!.difficulty * 5);
    expect(run.verifiedScore).toBe(0);
  });

  it('sends event study to the server and uses its challenge snapshot', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
    const node = run.nodes.find((n) => n.layer === 0);
    node!.kind = 'event';
    setServerNode(node!.id, (serverNode) => { serverNode.kind = 'event'; });
    const d0 = node!.difficulty;
    judgeMock.fetchProblem.mockResolvedValue(problem());
    await run.enterNode(node!.id);
    await run.chooseEvent('study');

    expect(frontierApiMock.performExpeditionAction).toHaveBeenLastCalledWith({
      runId: EXPEDITION_RUN_ID, action: 'EVENT_STUDY', nodeId: node!.id,
    });
    expect(node!.difficulty).toBe(d0);
    expect(judgeMock.fetchProblem).toHaveBeenLastCalledWith(
      expect.objectContaining({ difficulty: Math.min(5, d0 + 1) }),
    );
    expect(run.activeDifficulty).toBe(problem().difficulty);
    expect(run.phase).toBe('challenge');
  });

  it('keeps the study event difficulty when retrying a failed challenge', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
    const node = run.nodes.find((n) => n.layer === 0)!;
    node.kind = 'event';
    setServerNode(node.id, (serverNode) => { serverNode.kind = 'event'; });
    await run.enterNode(node.id);
    await run.chooseEvent('study');
    const studyDifficulty = run.activeDifficulty;
    judgeMock.judge.mockResolvedValue({
      problemId: 'p1', passed: false, passedTestCases: 0, totalTestCases: 1, score: 0, feedback: 'retry',
    });

    await run.submitChallenge();
    await run.retryChallenge();

    expect(judgeMock.retryPrefetch).toHaveBeenCalledWith(expect.objectContaining({
      difficulty: studyDifficulty,
    }));
    expect(judgeMock.fetchProblem).toHaveBeenLastCalledWith(expect.objectContaining({
      difficulty: studyDifficulty,
    }));
    expect(run.activeDifficulty).toBe(studyDifficulty);
  });

  it('applies the event study score multiplier only to the local game score', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
    const node = run.nodes.find((n) => n.layer === 0)!;
    node.kind = 'event';
    setServerNode(node.id, (serverNode) => { serverNode.kind = 'event'; });
    judgeMock.judge.mockResolvedValue({
      problemId: 'p1', passed: true, passedTestCases: 1, totalTestCases: 1, score: 80, feedback: 'ok',
    });

    await run.enterNode(node.id);
    await run.chooseEvent('study');
    const expected = Math.round(run.activeDifficulty * 10 * 1.5);
    await run.submitChallenge();

    expect(run.score).toBe(expected);
    expect(run.verifiedScore).toBe(80);
  });

  it('uses the server response for rest nodes without requesting a problem', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
    const node = run.nodes.find((n) => n.layer === 0);
    node!.kind = 'rest';
    setServerNode(node!.id, (serverNode) => { serverNode.kind = 'rest'; });
    run.hp = 2;
    if (mockServerRun) mockServerRun.hp = 2;

    await run.enterNode(node!.id);

    expect(judgeMock.fetchProblem).not.toHaveBeenCalled();
    expect(run.phase).toBe('map');
    expect(run.hp).toBe(3);
    expect(run.nodes.find((n) => n.id === node!.id)?.state).toBe('cleared');
  });

  it('claims a chest relic through the server and applies its canonical snapshot', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
    const node = run.nodes.find((n) => n.layer === 0);
    node!.kind = 'chest';
    setServerNode(node!.id, (serverNode) => { serverNode.kind = 'chest'; });

    await run.enterNode(node!.id);

    expect(judgeMock.fetchProblem).not.toHaveBeenCalled();
    expect(run.phase).toBe('reward');
    expect(run.rewardChoices).toEqual(['sturdy_notes', 'calm_mind', 'combo_scholar']);
    expect(run.relics).toHaveLength(0); // not granted until chosen
    const picked = run.rewardChoices[0]!;
    await run.chooseReward(picked);
    expect(frontierApiMock.performExpeditionAction).toHaveBeenLastCalledWith({
      runId: EXPEDITION_RUN_ID, action: 'CLAIM_RELIC', nodeId: node!.id, relicId: picked,
    });
    expect(run.phase).toBe('map');
    expect(run.relics).toEqual([picked]);
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

  it('requests momentum difficulty but displays the issued question difficulty', async () => {
    judgeMock.judge.mockResolvedValue({
      problemId: 'p1', passed: true, passedTestCases: 1, totalTestCases: 1, score: 80, feedback: 'ok',
    });
    const run = useRogueRunStore();
    await run.startRun(null);
    // 直接把气势拉到 3，进入任一可战节点应出更高难度题。
    run.momentum = 3;
    if (mockServerRun) mockServerRun.momentum = 3;
    const start = run.nodes.find((n) => n.layer === 0);
    await run.enterNode(start!.id);
    // base 2 + 气势(>=3) +1 is requested; server-issued difficulty remains authoritative.
    expect(run.activeDifficulty).toBe(problem().difficulty);
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
    setServerNode(node!.id, (serverNode) => {
      serverNode.kind = 'boss';
      serverNode.kcCode = 'PY43-KC-001';
      serverNode.bossPhase = 1;
    });
    judgeMock.fetchProblem.mockResolvedValue(problem());
    judgeMock.judge.mockResolvedValue({
      problemId: 'p1', passed: true, passedTestCases: 1, totalTestCases: 1, score: 80, feedback: 'ok',
    });
    await run.enterNode(node!.id);
    await run.submitChallenge();
    expect(run.bossPhase).toBe(2);
    expect(run.nodes.find((n) => n.id === node!.id)?.state).toBe('available');
    await run.continueAfterChallenge();
    expect(judgeMock.retryPrefetch).toHaveBeenLastCalledWith(expect.objectContaining({
      kc: node!.kcCode,
      expeditionRunId: EXPEDITION_RUN_ID,
      expeditionNodeId: node!.id,
    }));
    expect(judgeMock.retryPrefetch.mock.invocationCallOrder.at(-1))
      .toBeLessThan(judgeMock.fetchProblem.mock.invocationCallOrder.at(-1)!);
    await run.submitChallenge();
    expect(run.bossPhase).toBe(3);
    await run.continueAfterChallenge();
    await run.submitChallenge();
    expect(run.nodes.find((n) => n.id === node!.id)?.state).toBe('cleared');
    await run.continueAfterChallenge();
    expect(run.phase).toBe('over');
    expect(run.isVictory).toBe(true);
  });

  it('clears the prior Boss question when loading the next phase fails', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
    const node = run.nodes.find((item) => item.layer === 0)!;
    node.kind = 'boss';
    node.kcCode = 'PY43-KC-001';
    setServerNode(node.id, (serverNode) => {
      serverNode.kind = 'boss';
      serverNode.kcCode = 'PY43-KC-001';
      serverNode.bossPhase = 1;
    });
    await run.enterNode(node.id);
    judgeMock.judge.mockResolvedValue({
      problemId: 'p1', passed: true, passedTestCases: 1, totalTestCases: 1, score: 80, feedback: 'ok',
    });
    await run.submitChallenge();
    judgeMock.fetchProblem.mockRejectedValueOnce(new Error('phase unavailable'));

    await run.continueAfterChallenge();

    expect(run.activeProblem).toBeNull();
    expect(run.errorMessage).toBe('phase unavailable');
  });

  it('resumes an already claimed daily run instead of deleting it and claiming again', async () => {
    const dailyRun = serverRun(null, 'PY43-KC-001', 2, true);
    frontierApiMock.fetchExpeditionRun.mockResolvedValueOnce(dailyRun);
    const run = useRogueRunStore();

    await run.startDailyRun();

    expect(run.phase).toBe('map');
    expect(run.isDaily).toBe(true);
    expect(frontierApiMock.deleteExpeditionRun).not.toHaveBeenCalled();
    expect(frontierApiMock.createExpeditionRun).not.toHaveBeenCalled();
  });

  it('does not replace an active regular run when the daily challenge is selected', async () => {
    frontierApiMock.fetchExpeditionRun.mockResolvedValueOnce(serverRun());
    const run = useRogueRunStore();

    await run.startDailyRun();

    expect(run.phase).toBe('idle');
    expect(run.errorMessage).toContain('先继续或弃掉当前远征');
    expect(frontierApiMock.deleteExpeditionRun).not.toHaveBeenCalled();
    expect(frontierApiMock.createExpeditionRun).not.toHaveBeenCalled();
  });

  it('recovers score settlement for a terminal run after a page reload', async () => {
    const terminal = serverRun();
    terminal.phase = 'over';
    terminal.currentNodeId = terminal.nodes.find((node) => node.kind === 'boss')?.id ?? null;
    const finalBoss = terminal.nodes.find((node) => node.id === terminal.currentNodeId);
    if (finalBoss) finalBoss.state = 'cleared';
    terminal.score = 120;
    const board = { entries: [], selfRank: null, selfBest: 120, inClass: false };
    frontierApiMock.fetchExpeditionRun.mockResolvedValue(terminal);
    frontierApiMock.submitExpeditionScore
      .mockRejectedValueOnce(new Error('temporary network failure'))
      .mockResolvedValueOnce(board);

    const firstPage = useRogueRunStore();
    await firstPage.refreshSavedRun();
    expect(firstPage.hasSavedRun).toBe(true);
    expect(firstPage.savedRunSummary?.settlementPending).toBe(true);
    await firstPage.resumeSavedRun();
    expect(firstPage.phase).toBe('over');
    expect(firstPage.isVictory).toBe(true);

    setActivePinia(createPinia());
    const reloadedPage = useRogueRunStore();
    await reloadedPage.refreshSavedRun();
    expect(await reloadedPage.resumeSavedRun()).toBe(true);
    expect(reloadedPage.phase).toBe('over');
    expect(frontierApiMock.submitExpeditionScore).toHaveBeenCalledTimes(2);
    expect(frontierApiMock.deleteExpeditionRun).not.toHaveBeenCalled();
  });

  it('daily run marks today done after the run ends and blocks a second daily', async () => {
    const run = useRogueRunStore();
    expect(run.dailyAvailable).toBe(true);
    await run.startDailyRun();
    expect(run.isDaily).toBe(true);
    expect(run.phase).toBe('map');
    await run.finishRun();
    expect(run.phase).toBe('over');
    expect(run.dailyAvailable).toBe(false);
    expect(frontierApiMock.createExpeditionRun).toHaveBeenCalledWith({
      focusChapter: null,
      isDaily: true,
    });
  });

  it('marks a daily challenge unavailable when the server says this account already claimed it', async () => {
    frontierApiMock.createExpeditionRun.mockRejectedValueOnce(
      new ApiClientError(
        {
          code: 'EXPEDITION_DAILY_ALREADY_CLAIMED',
          message: '今日挑战已完成，请明天再来',
          details: [],
          traceId: '',
          timestamp: new Date().toISOString(),
        },
        409,
      ),
    );
    const run = useRogueRunStore();

    await run.startDailyRun();

    expect(run.dailyAvailable).toBe(false);
    expect(run.errorMessage).toContain('今日挑战已完成');
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

  it('retries server lookup before resuming a local-only snapshot preview', async () => {
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
    frontierApiMock.fetchExpeditionRun.mockResolvedValueOnce(serverRun(4, 'PY43-KC-031'));

    expect(frontierApiMock.createExpeditionRun).not.toHaveBeenCalled();
    expect(run.phase).toBe('idle');
    expect(run.hasSavedRun).toBe(true);
    expect(run.savedRunSummary?.focusChapter).toBe(4);
    expect(run.savedRunSummary?.serverVerified).toBe(false);
    expect(run.savedRunSummary?.verifiedScore).toBe(0);
    expect(run.savedRunSummary?.verifiedDepth).toBe(0);

    expect(await run.resumeSavedRun()).toBe(true);

    expect(run.focusChapter).toBe(4);
    expect(run.nodes).not.toHaveLength(0);
    expect(frontierApiMock.fetchExpeditionRun).toHaveBeenCalledTimes(2);
    expect(run.savedRunSummary?.serverVerified).toBe(true);
  });

  it('shows score and passed depth only from the server ledger', async () => {
    const verified = serverRun();
    verified.score = 95;
    verified.verifiedDepth = 1;
    frontierApiMock.fetchExpeditionRun.mockResolvedValueOnce(verified);
    const run = useRogueRunStore();
    await run.refreshSavedRun();

    expect(run.savedRunSummary).toMatchObject({
      verifiedScore: 95,
      verifiedDepth: 1,
      serverVerified: true,
    });
    expect(await run.resumeSavedRun()).toBe(true);
    expect(run.verifiedScore).toBe(95);
    expect(run.verifiedDepth).toBe(1);
    run.score = 999_999;
    run.saveCheckpoint();
    expect(run.savedRunSummary?.verifiedScore).toBe(95);
  });

  it('can re-enter the interrupted current challenge after resuming to the map', async () => {
    const interrupted = serverRun();
    const node = interrupted.nodes.find((item) => item.layer === 0)!;
    interrupted.phase = 'challenge';
    interrupted.currentNodeId = node.id;
    node.state = 'current';
    mockServerRun = copyRun(interrupted);
    frontierApiMock.fetchExpeditionRun.mockResolvedValueOnce(copyRun(interrupted));
    const run = useRogueRunStore();

    await run.refreshSavedRun();
    expect(await run.resumeSavedRun()).toBe(true);
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(judgeMock.prefetchProblem).not.toHaveBeenCalledWith(expect.objectContaining({
      expeditionNodeId: node.id,
    }));
    await run.enterNode(node.id);

    expect(run.phase).toBe('challenge');
    expect(judgeMock.fetchProblem).toHaveBeenCalledWith(expect.objectContaining({
      expeditionRunId: EXPEDITION_RUN_ID,
      expeditionNodeId: node.id,
    }));
  });

  it('reconciles a committed action after its response is lost without replaying it', async () => {
    const run = useRogueRunStore();
    await run.startRun(null);
    const committed = serverRun();
    committed.phase = 'over';
    mockServerRun = copyRun(committed);
    frontierApiMock.performExpeditionAction.mockRejectedValueOnce(new Error('response lost'));
    frontierApiMock.fetchExpeditionRun.mockResolvedValueOnce(copyRun(committed));

    await run.finishRun();

    expect(run.phase).toBe('over');
    expect(frontierApiMock.performExpeditionAction).toHaveBeenCalledTimes(1);
    expect(frontierApiMock.fetchExpeditionRun).toHaveBeenCalled();
  });

  it('restores reward choices when resuming a run at the reward phase', async () => {
    const interrupted = serverRun();
    const node = interrupted.nodes.find((item) => item.layer === 0)!;
    node.kind = 'chest';
    node.state = 'cleared';
    node.rewardChoices = ['sturdy_notes', 'calm_mind', 'combo_scholar'];
    interrupted.currentNodeId = node.id;
    interrupted.phase = 'reward';
    mockServerRun = copyRun(interrupted);
    frontierApiMock.fetchExpeditionRun.mockResolvedValueOnce(copyRun(interrupted));
    const run = useRogueRunStore();

    await run.refreshSavedRun();

    expect(await run.resumeSavedRun()).toBe(true);
    expect(run.phase).toBe('reward');
    expect(run.rewardChoices.length).toBeGreaterThan(0);
  });

  it('can re-open the interrupted event after resuming to the map', async () => {
    const interrupted = serverRun();
    const node = interrupted.nodes.find((item) => item.layer === 0)!;
    node.kind = 'event';
    node.state = 'current';
    interrupted.currentNodeId = node.id;
    interrupted.phase = 'event';
    mockServerRun = copyRun(interrupted);
    frontierApiMock.fetchExpeditionRun.mockResolvedValueOnce(copyRun(interrupted));
    const run = useRogueRunStore();

    await run.refreshSavedRun();
    expect(await run.resumeSavedRun()).toBe(true);
    await run.enterNode(node.id);

    expect(run.phase).toBe('event');
    expect(run.activeNode?.id).toBe(node.id);
    expect(run.activeEventText).not.toBe('');
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

    await run.startRun(1, false, 'PY43-KC-042');

    expect(run.phase).toBe('map');
    expect(run.focusChapter).toBe(1);
    expect(run.nodes[0]?.kcCode).not.toBe('PY43-KC-039');
    expect(frontierApiMock.createExpeditionRun).toHaveBeenCalledWith({
      focusChapter: 1,
      isDaily: false,
      preferredKcCode: 'PY43-KC-042',
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
    expect(frontierApiMock.performExpeditionAction).toHaveBeenCalledWith({
      runId: EXPEDITION_RUN_ID, action: 'FINISH',
    });

    await run.startRun(null);

    expect(frontierApiMock.submitExpeditionScore).toHaveBeenCalledTimes(2);
    expect(frontierApiMock.createExpeditionRun).toHaveBeenCalledTimes(2);
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

  it('does not double-report after a failed challenge ends the server run', async () => {
    frontierApiMock.submitExpeditionScore.mockResolvedValue({
      entries: [], selfRank: null, selfBest: 0, inClass: false,
    });
    const run = useRogueRunStore();
    await run.startRun(null);
    const start = run.nodes.find((node) => node.layer === 0)!;
    run.hp = 0;
    if (mockServerRun) mockServerRun.hp = 0;
    judgeMock.judge.mockResolvedValue({
      problemId: 'p1', passed: false, passedTestCases: 0, totalTestCases: 1, score: 0, feedback: 'wa',
    });
    await run.enterNode(start.id);
    await run.submitChallenge();
    await new Promise((resolve) => setTimeout(resolve, 0));
    await run.continueAfterChallenge();
    expect(run.phase).toBe('over');
    expect(frontierApiMock.submitExpeditionScore).toHaveBeenCalledTimes(1);
    await run.finishRun();
    expect(frontierApiMock.submitExpeditionScore).toHaveBeenCalledTimes(1);
  });
});
