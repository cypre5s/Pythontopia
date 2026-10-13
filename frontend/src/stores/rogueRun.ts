// 无尽远征（Roguelike）run 状态机（Pinia）。
//
// 一局 run：知识前沿生成分支地图 → 玩家沿出边选节点 → 战斗节点出题真判题 →
// 对得分/攒气势、错按韧性温和扣血（掌握度收益持久保留）→ HP 归零结束、打通 boss 胜利。
// 掌握度 / 达标后端权威（submitBossAttempt 入库）；本 store 只持久化 run 进度（localStorage）。
// 详见 docs/plans/2026-06-19-endless-roguelike-rebuild.md。

import { computed, ref, watch } from 'vue';
import { defineStore } from 'pinia';
import {
  EXPEDITION_MAX_HP,
  computeRunDifficulty,
} from '@/data/rogueConfig';
import { ROGUE_RELICS, relicById } from '@/data/rogueRelics';
import { pickEventScenario, type RogueEventChoice } from '@/data/rogueEvents';
import { kcDisplayName } from '@/data/kcNames';
import { ApiClientError } from '@/api/http';
import { useChallengeJudge, isJudgeUnavailable } from '@/composables/useChallengeJudge';
import {
  createExpeditionRun,
  deleteExpeditionRun,
  fetchExpeditionLeaderboard,
  fetchExpeditionRun,
  performExpeditionAction,
  submitExpeditionScore,
  type ExpeditionLeaderboard,
  type ExpeditionRunAction,
  type ExpeditionRunSnapshot,
} from '@/api/expedition';
import { useMainlineStore } from '@stores/mainline';
import { userScopedKey } from '@stores/session';
import { useToastStore } from '@stores/toast';
import type { BossAttemptResultDto, BossProblemDto } from '@/api/dto/Boss';
import type { ArenaLevelKind } from '@/types/arena';
import type {
  RogueHistoryEntry,
  RogueNode,
  RogueNodeKind,
  RogueNodeState,
  RogueRunPhase,
} from '@/types/rogue';

type ExpeditionRunActionInput = ExpeditionRunAction extends infer Action
  ? Action extends ExpeditionRunAction ? Omit<Action, 'runId'> : never
  : never;

const PERSIST_KEY = 'pyt_rogue_run_v1';
const DAILY_KEY = 'pyt_rogue_daily_v1';
// 本地补星提示门槛；服务端仍按活动 run 的正式判题记录独立复核。
const STAR_MIN_CORRECT = 8;
const STAR_MIN_ACCURACY = 0.8;
// BOSS 多阶段：连过 N 道才算击穿知识闸（过关高潮）。
const BOSS_PHASES = 3;

function todayStr(): string {
  // Keep the daily boundary aligned with the backend's Asia/Shanghai date.
  return new Date(Date.now() + 8 * 60 * 60 * 1000).toISOString().slice(0, 10);
}
function loadDailyDate(): string | null {
  if (typeof window === 'undefined') return null;
  try {
    return window.localStorage.getItem(userScopedKey(DAILY_KEY));
  } catch {
    return null;
  }
}
function saveDailyDate(date: string): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(userScopedKey(DAILY_KEY), date);
  } catch {
    /* 隐私模式禁写，忽略 */
  }
}
// run 可持久化快照：与后端 expedition_run 同构（api/expedition.ts 单一事实源）。
type PersistShape = ExpeditionRunSnapshot;

export interface RogueRunSaveSummary {
  focusChapter: number | null;
  hp: number;
  maxHp: number;
  verifiedScore: number;
  verifiedDepth: number;
  serverVerified: boolean;
  relicCount: number;
  isDaily: boolean;
  settlementPending: boolean;
  lastPlayedAt: string | null;
}

/** 续局允许从地图、挑战、事件或领奖阶段恢复。 */
function isResumablePhase(p: RogueRunPhase): boolean {
  return p === 'map' || p === 'challenge' || p === 'event' || p === 'reward' || p === 'over';
}

/** 从数组里无放回随机取最多 n 个（Fisher–Yates 部分洗牌）。 */
function sampleN<T>(arr: readonly T[], n: number): T[] {
  const copy = [...arr];
  for (let i = copy.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    [copy[i], copy[j]] = [copy[j] as T, copy[i] as T];
  }
  return copy.slice(0, Math.max(0, n));
}

/**
 * 可被真判通过的题型（后端只做代码沙箱判题）。续局时把历史存档里**不可代码真判**的旧题型
 * （short_answer / pdf_question / fill_blank / multi_round / teach_back）就地迁移为 dual_track，
 * 避免老存档恢复后撞上「写散文 / 多段拼接 → 当代码跑 → 必败卡死」的节点（与新局 NODE_KIND_POOLS 对齐）。
 */
const SAFE_CHALLENGE_KINDS: ReadonlySet<ArenaLevelKind> = new Set<ArenaLevelKind>([
  'dual_track',
  'closed_loop',
  'debug',
  'bug_mine',
]);
function sanitizeRunNodes(nodes: RogueNode[]): RogueNode[] {
  return nodes.map((n) =>
    n.challengeKind && !SAFE_CHALLENGE_KINDS.has(n.challengeKind)
      ? { ...n, challengeKind: 'dual_track' as ArenaLevelKind }
      : n,
  );
}

const VALID_NODE_KINDS: ReadonlySet<RogueNodeKind> = new Set<RogueNodeKind>([
  'battle', 'elite', 'event', 'bug', 'chest', 'rest', 'boss',
]);
const VALID_NODE_STATES: ReadonlySet<RogueNodeState> = new Set<RogueNodeState>([
  'locked', 'available', 'current', 'cleared', 'bypassed',
]);

/** 逐节点结构校验：损坏 / 半截存档（缺 next、state 非法等）不可信，丢弃回退新局。 */
function isValidRogueNode(value: unknown): value is RogueNode {
  if (!value || typeof value !== 'object') return false;
  const n = value as Record<string, unknown>;
  return (
    typeof n.id === 'string'
    && typeof n.layer === 'number' && Number.isFinite(n.layer)
    && typeof n.col === 'number' && Number.isFinite(n.col)
    && typeof n.difficulty === 'number' && Number.isFinite(n.difficulty)
    && VALID_NODE_KINDS.has(n.kind as RogueNodeKind)
    && VALID_NODE_STATES.has(n.state as RogueNodeState)
    && Array.isArray(n.next) && n.next.every((x) => typeof x === 'string')
    && (n.kcCode === null || typeof n.kcCode === 'string')
    && (n.challengeKind === null || typeof n.challengeKind === 'string')
    && (n.bossPhase === undefined || (
      n.kind === 'boss' && Number.isInteger(n.bossPhase)
      && (n.bossPhase as number) >= 1 && (n.bossPhase as number) <= BOSS_PHASES
    ))
  );
}

/** 整份快照结构校验（顶层标量 + 每个节点）：任一不合法即视为不可用存档。 */
function isValidSnapshot(value: unknown): value is PersistShape {
  if (!value || typeof value !== 'object') return false;
  const s = value as Record<string, unknown>;
  if (typeof s.phase !== 'string') return false;
  if (typeof s.hp !== 'number' || !Number.isFinite(s.hp)) return false;
  if (typeof s.layers !== 'number' || !Number.isFinite(s.layers)) return false;
  if (!Array.isArray(s.nodes) || s.nodes.length === 0) return false;
  return s.nodes.every(isValidRogueNode);
}

function loadPersisted(): PersistShape | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = window.localStorage.getItem(userScopedKey(PERSIST_KEY));
    if (!raw) return null;
    const parsed = JSON.parse(raw) as unknown;
    return isValidSnapshot(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

export const useRogueRunStore = defineStore('rogueRun', () => {
  const challenge = useChallengeJudge();
  const mainline = useMainlineStore();
  const toast = useToastStore();

  const phase = ref<RogueRunPhase>('idle');
  const focusChapter = ref<number | null>(null);
  const nodes = ref<RogueNode[]>([]);
  const layers = ref(0);
  const currentNodeId = ref<string | null>(null);
  const hp = ref(EXPEDITION_MAX_HP);
  const maxHp = ref(EXPEDITION_MAX_HP);
  const momentum = ref(0);
  const bestMomentum = ref(0);
  const runId = ref<string | null>(null);
  // Session-only game points; verifiedScore is the ledger-backed class score.
  const score = ref(0);
  const verifiedScore = ref(0);
  const verifiedDepth = ref(0);
  const relics = ref<string[]>([]);
  const history = ref<RogueHistoryEntry[]>([]);
  const savedRun = ref<PersistShape | null>(loadPersisted());
  const savedRunVerified = ref(false);
  let serverPersistenceEnabled = true;
  let challengeGeneration = 0;
  let actionQueue: Promise<void> = Promise.resolve();
  const scoredProblemIds = new Set<string>();
  const pendingGameScore = new Map<string, number>();
  const rewardBonusNodeIds = new Set<string>();

  // 当前进入的节点 + 题目（不持久化：刷新中途答题安全退回地图）。
  const activeNode = ref<RogueNode | null>(null);
  const activeProblem = ref<BossProblemDto | null>(null);
  const activeBossPhase = ref(1);
  // 本次挑战实际难度（= 基准 node.difficulty 经局内气势/连败自适应后的值；驱动出题与计分）。
  const activeDifficulty = ref(0);
  const activeCode = ref('');
  const challengeResult = ref<BossAttemptResultDto | null>(null);
  const settlementPending = ref(false);
  const nextPhase = ref<RogueRunPhase | null>(null);
  const loading = ref(false);
  const starting = ref(false);
  const elapsedSec = ref(0);
  const errorMessage = ref('');
  const summaryReported = ref(false);
  // 概念事件：进入 event 节点先选应对；study 选项把本题得分 ×eventScoreMult。
  const activeEventText = ref('');
  const eventScoreMult = ref(1);
  // 宝箱 / 精英通关的「三选一」学习增益候选（reward 阶段，瞬时态不入快照）。
  const rewardChoices = ref<string[]>([]);
  // BOSS 多阶段进度（连过 bossPhasesTotal 道才通关）。
  const bossPhase = ref(1);
  const bossPhasesTotal = ref(BOSS_PHASES);
  // 每日挑战：当日弱项 + 日期种子稳定地图；每天一次（记录完成日期）。
  const isDaily = ref(false);
  const dailyDoneDate = ref<string | null>(loadDailyDate());
  const dailyAvailable = computed(() => dailyDoneDate.value !== todayStr());
  // 班级排行榜（最高单局得分）：结算上报 + 起始页拉取；本局成绩只上报一次。
  const leaderboard = ref<ExpeditionLeaderboard | null>(null);
  const scoreReported = ref(false);
  let scoreReportTask: Promise<boolean> | null = null;

  // ── 派生 ──
  const nodesByLayer = computed<RogueNode[][]>(() => {
    const out: RogueNode[][] = Array.from({ length: layers.value }, () => []);
    for (const n of nodes.value) {
      (out[n.layer] ??= []).push(n);
    }
    return out.map((row) => [...row].sort((a, b) => a.col - b.col));
  });
  const passedCount = computed(() => history.value.filter((h) => h.passed).length);
  const attempts = computed(() => history.value.length);
  const nodeById = (id: string): RogueNode | undefined => nodes.value.find((n) => n.id === id);
  const hasRelic = (id: string): boolean => relics.value.includes(id);
  const hasSavedRun = computed(() => isUsableSavedRun(savedRun.value));
  const isVictory = computed(() => phase.value === 'cleared' || (
    phase.value === 'over'
    && nodes.value.some((node) =>
      node.id === currentNodeId.value && node.kind === 'boss' && node.state === 'cleared',
    )
  ));
  const savedRunSummary = computed<RogueRunSaveSummary | null>(() => {
    const snap = savedRun.value;
    if (!isUsableSavedRun(snap)) return null;
    return {
      focusChapter: snap.focusChapter ?? null,
      hp: snap.hp,
      maxHp: snap.maxHp ?? EXPEDITION_MAX_HP,
      verifiedScore: savedRunVerified.value ? snap.score : 0,
      verifiedDepth: savedRunVerified.value ? snap.verifiedDepth ?? 0 : 0,
      serverVerified: savedRunVerified.value,
      relicCount: Array.isArray(snap.relics) ? snap.relics.length : 0,
      isDaily: snap.isDaily ?? false,
      settlementPending: snap.phase === 'over',
      lastPlayedAt: snap.history[0]?.at ?? null,
    };
  });

  function isUsableSavedRun(snap: PersistShape | null): snap is PersistShape {
    return isValidSnapshot(snap) && isResumablePhase(snap.phase);
  }

  function initialCodeFor(problem: BossProblemDto): string {
    return problem.starterCode?.trim() ? problem.starterCode : problem.buggyCode ?? '';
  }

  function snapshot(): PersistShape {
    return {
      phase: phase.value,
      focusChapter: focusChapter.value,
      nodes: nodes.value,
      layers: layers.value,
      currentNodeId: currentNodeId.value,
      hp: hp.value,
      maxHp: maxHp.value,
      momentum: momentum.value,
      bestMomentum: bestMomentum.value,
      score: score.value,
      relics: relics.value,
      isDaily: isDaily.value,
      history: history.value,
      runId: runId.value,
    };
  }

  /** Server actions persist canonical state; localStorage is only a recovery preview. */
  function persist(): void {
    const snap = snapshot();
    if (serverPersistenceEnabled) {
      snap.score = verifiedScore.value;
      snap.verifiedDepth = verifiedDepth.value;
    }
    if (typeof window !== 'undefined') {
      try {
        window.localStorage.setItem(userScopedKey(PERSIST_KEY), JSON.stringify(snap));
      } catch {
        /* 隐私模式禁写，忽略 */
      }
    }
    if (isResumablePhase(phase.value)) {
      savedRun.value = snap;
      savedRunVerified.value = serverPersistenceEnabled;
    }
  }

  /** 用户显式保存本地恢复预览；服务端进度已由每个动作即时持久化。 */
  function saveCheckpoint(): void {
    if (phase.value === 'idle') return;
    persist();
    toast.show('远征已存档，下次可从存档继续', 'success');
  }

  /** 把一份快照灌入 store；中断的挑战和事件回到地图，领奖保留原阶段。 */
  function applySnapshot(
    p: PersistShape,
    serverVerified: boolean,
    resume = false,
    clearTransient = true,
  ): void {
    phase.value = resume && (p.phase === 'challenge' || p.phase === 'event') ? 'map' : p.phase;
    runId.value = p.runId ?? null;
    focusChapter.value = p.focusChapter ?? null;
    // 老存档可能含已弃用的不可判题型，续局前就地迁移到可真判题型，避免恢复即卡死。
    nodes.value = sanitizeRunNodes(p.nodes);
    layers.value = p.layers;
    currentNodeId.value = p.currentNodeId;
    hp.value = p.hp;
    maxHp.value = p.maxHp ?? EXPEDITION_MAX_HP;
    momentum.value = p.momentum ?? 0;
    bestMomentum.value = p.bestMomentum ?? 0;
    score.value = p.score ?? 0;
    verifiedScore.value = serverVerified ? p.score : 0;
    verifiedDepth.value = serverVerified ? p.verifiedDepth ?? 0 : 0;
    relics.value = Array.isArray(p.relics) ? p.relics : [];
    isDaily.value = p.isDaily ?? false;
    history.value = Array.isArray(p.history) ? p.history : [];
    const currentBoss = p.nodes.find((node) => node.id === p.currentNodeId && node.kind === 'boss');
    bossPhase.value = currentBoss?.bossPhase ?? 1;
    if (clearTransient) clearActive();
  }

  async function loadSavedSnapshot(): Promise<{ snapshot: PersistShape | null; serverVerified: boolean }> {
    try {
      const snap = await fetchExpeditionRun();
      if (snap) return { snapshot: snap, serverVerified: true };
    } catch {
      // Continue with the local map snapshot, but never treat its score/depth as verified.
    }
    return { snapshot: loadPersisted(), serverVerified: false };
  }

  /** 只刷新“可继续的存档预览”，不直接灌入 run，让玩家自己选择是否继续。 */
  async function refreshSavedRun(): Promise<boolean> {
    const loaded = await loadSavedSnapshot();
    const snap = loaded.snapshot;
    savedRun.value = isUsableSavedRun(snap) ? snap : null;
    savedRunVerified.value = !!savedRun.value && loaded.serverVerified;
    return hasSavedRun.value;
  }

  /** 玩家点“继续上次远征”后才恢复存档。 */
  async function resumeSavedRun(): Promise<boolean> {
    const loaded = isUsableSavedRun(savedRun.value)
      ? { snapshot: savedRun.value, serverVerified: savedRunVerified.value }
      : await loadSavedSnapshot();
    const snap = loaded.snapshot;
    if (!isUsableSavedRun(snap)) {
      savedRun.value = null;
      savedRunVerified.value = false;
      return false;
    }
    if (!loaded.serverVerified) {
      errorMessage.value = '本地存档仅用于预览；请连接服务端后继续远征';
      toast.show(errorMessage.value, 'error');
      return false;
    }
    savedRun.value = snap;
    savedRunVerified.value = loaded.serverVerified;
    serverPersistenceEnabled = loaded.serverVerified;
    scoreReported.value = false;
    summaryReported.value = false;
    applySnapshot(snap, loaded.serverVerified, true);
    if (phase.value === 'reward') openReward();
    persist();
    if (phase.value === 'over') {
      if (isDaily.value) {
        dailyDoneDate.value = todayStr();
        saveDailyDate(dailyDoneDate.value);
      }
      await reportScore();
    }
    return true;
  }

  async function performAction(
    action: ExpeditionRunActionInput,
    preserveChallenge = false,
  ): Promise<ExpeditionRunSnapshot | null> {
    if (!serverPersistenceEnabled || !runId.value) {
      errorMessage.value = '远征服务端状态不可用，请重新连接后继续';
      return null;
    }
    const currentRunId = runId.value;
    let result: ExpeditionRunSnapshot | null = null;
    const task = actionQueue.then(async () => {
      if (currentRunId !== runId.value) return;
      try {
        const canonical = await performExpeditionAction({ ...action, runId: currentRunId } as ExpeditionRunAction);
        if (currentRunId !== runId.value) return;
        const gameScore = score.value;
        const challengeView = preserveChallenge
          ? {
              nodeId: activeNode.value?.id,
              problem: activeProblem.value,
              difficulty: activeDifficulty.value,
              code: activeCode.value,
              result: challengeResult.value,
              elapsed: elapsedSec.value,
            }
          : null;
        applySnapshot(canonical, true, false, !preserveChallenge);
        score.value = gameScore;
        if (challengeView) {
          phase.value = 'challenge';
          activeNode.value = challengeView.nodeId ? nodeById(challengeView.nodeId) ?? null : null;
          activeProblem.value = challengeView.problem;
          activeDifficulty.value = challengeView.difficulty;
          activeCode.value = challengeView.code;
          challengeResult.value = challengeView.result;
          elapsedSec.value = challengeView.elapsed;
          nextPhase.value = canonical.phase;
        }
        savedRun.value = canonical;
        savedRunVerified.value = true;
        persist();
        result = canonical;
      } catch (error) {
        errorMessage.value = error instanceof Error ? error.message : '远征操作失败，请重试';
        toast.show(errorMessage.value, 'error');
      }
    });
    actionQueue = task.then(() => undefined, () => undefined);
    await task;
    return result;
  }

  /**
   * 开启「三选一」学习增益（宝箱 / 精英通关触发）：从未拥有的增益里抽最多 3 个候选交玩家抉择，
   * 把「领奖」从随机发放升级为 roguelike 式取舍。已集齐则折算少量得分、直接回地图。
   */
  function openReward(): void {
    const owned = new Set(relics.value);
    const pool = ROGUE_RELICS.filter((r) => !owned.has(r.id)).map((r) => r.id);
    if (pool.length === 0) {
      rewardChoices.value = [];
      const nodeId = currentNodeId.value;
      if (nodeId && !rewardBonusNodeIds.has(nodeId)) {
        rewardBonusNodeIds.add(nodeId);
        score.value += 30;
        toast.show('已集齐全部学习增益 · 折算 +30 分', 'info');
      }
      return;
    }
    rewardChoices.value = sampleN(pool, 3);
  }

  /** 三选一领取：选中的增益生效并回到地图（未选中的弃掉）。 */
  async function chooseReward(id: string): Promise<void> {
    if (phase.value !== 'reward' || !rewardChoices.value.includes(id) || !currentNodeId.value) return;
    const canonical = await performAction({
      action: 'CLAIM_RELIC', nodeId: currentNodeId.value, relicId: id,
    });
    if (canonical) {
      rewardChoices.value = [];
      if (relics.value.includes(id)) toast.show(`获得学习增益：${relicById(id)?.icon ?? ''} ${relicById(id)?.name ?? id}`, 'success');
    }
  }

  async function startRun(
    focus: number | null,
    daily = false,
  ): Promise<void> {
    if (starting.value) return;
    challengeGeneration += 1;
    loading.value = false;
    starting.value = true;
    errorMessage.value = '';
    focusChapter.value = focus;
    try {
      await actionQueue;
      if (phase.value === 'over' || phase.value === 'cleared') {
        if (!(await reportScore())) {
          errorMessage.value = '本局成绩暂未结算，请重试后再开新局';
          toast.show(errorMessage.value, 'error');
          return;
        }
      } else if (phase.value === 'idle') {
        const existing = await fetchExpeditionRun();
        if (existing?.runId && existing.phase === 'over') {
          serverPersistenceEnabled = true;
          scoreReported.value = false;
          summaryReported.value = false;
          applySnapshot(existing, true);
          if (existing.isDaily) {
            dailyDoneDate.value = todayStr();
            saveDailyDate(dailyDoneDate.value);
          }
          if (!(await reportScore())) {
            errorMessage.value = '本局成绩暂未结算，请重试后再开新局';
            toast.show(errorMessage.value, 'error');
            return;
          }
          if (daily && existing.isDaily) return;
        } else if (existing?.runId && daily) {
          if (!existing.isDaily) {
            throw new Error('已有普通远征，请先继续或弃掉当前远征，再开始每日挑战');
          }
          scoreReported.value = false;
          summaryReported.value = false;
          serverPersistenceEnabled = true;
          savedRun.value = existing;
          savedRunVerified.value = true;
          applySnapshot(existing, true, true);
          if (existing.phase === 'reward') openReward();
          persist();
          return;
        } else if (existing?.runId) {
          await deleteExpeditionRun();
        }
      }
      // 前沿和地图由服务端一次性签发，客户端不再决定本局的节点拓扑与挑战配置。
      const started = await createExpeditionRun({ focusChapter: focus, isDaily: daily });
      if (!started.runId) throw new Error('服务端未签发远征 run');
      runId.value = started.runId;
      nodes.value = started.nodes;
      layers.value = started.layers;
      currentNodeId.value = started.currentNodeId;
      hp.value = started.hp;
      maxHp.value = started.maxHp;
      momentum.value = started.momentum;
      bestMomentum.value = started.bestMomentum;
      score.value = started.score;
      verifiedScore.value = started.score;
      verifiedDepth.value = started.verifiedDepth ?? 0;
      scoredProblemIds.clear();
      pendingGameScore.clear();
      rewardBonusNodeIds.clear();
      savedRunVerified.value = true;
      relics.value = started.relics;
      history.value = started.history;
      bossPhase.value = 1;
      isDaily.value = started.isDaily;
      summaryReported.value = false;
      scoreReported.value = false;
      challenge.clearPrefetch();
      clearActive();
      serverPersistenceEnabled = true;
      phase.value = 'map';
      persist();
      prefetchAvailable();
    } catch (error) {
      if (daily && error instanceof ApiClientError && error.code === 'EXPEDITION_DAILY_ALREADY_CLAIMED') {
        const date = todayStr();
        dailyDoneDate.value = date;
        saveDailyDate(date);
        errorMessage.value = '今日挑战已完成，请明天再来';
      } else {
        errorMessage.value = error instanceof Error ? error.message : '画像学习前沿加载失败';
      }
      phase.value = 'idle';
      toast.show(`远征暂未开始：${errorMessage.value}`, 'error');
    } finally {
      starting.value = false;
    }
  }

  /** 每日挑战：当日一次；地图种子与初始状态由服务端决定。 */
  async function startDailyRun(): Promise<void> {
    if (!dailyAvailable.value) {
      toast.show('今日挑战已完成，明天再来', 'info');
      return;
    }
    await startRun(null, true);
  }

  function clearActive(): void {
    challengeGeneration += 1;
    activeNode.value = null;
    activeProblem.value = null;
    activeDifficulty.value = 0;
    activeCode.value = '';
    challengeResult.value = null;
    settlementPending.value = false;
    nextPhase.value = null;
    elapsedSec.value = 0;
    errorMessage.value = '';
    activeEventText.value = '';
    eventScoreMult.value = 1;
    rewardChoices.value = [];
    pendingGameScore.clear();
    loading.value = false;
  }

  /** 拉取并进入某节点的判题（战斗/精英/抓bug/boss，以及事件选择后的挑战）。 */
  async function beginChallenge(
    node: RogueNode,
    difficultyBonus = 0,
    difficultyOverride?: number,
  ): Promise<void> {
    const requestGeneration = ++challengeGeneration;
    activeNode.value = node;
    activeBossPhase.value = node.kind === 'boss' ? bossPhase.value : 1;
    activeCode.value = '';
    challengeResult.value = null;
    settlementPending.value = false;
    nextPhase.value = null;
    errorMessage.value = '';
    elapsedSec.value = 0;
    phase.value = 'challenge';
    loading.value = true;
    // 局内自适应和事件加成只影响当前题，不改服务端签发的地图配置。
    const recentFailures = history.value.slice(0, 2).filter((h) => !h.passed).length;
    activeDifficulty.value = Math.min(5, difficultyOverride ?? (
      computeRunDifficulty(node.difficulty, momentum.value, recentFailures) + difficultyBonus
    ));
    persist();
    try {
      const problem = await challenge.fetchProblem({
        kc: node.kcCode ?? '',
        kind: node.challengeKind,
        difficulty: activeDifficulty.value,
        expeditionRunId: runId.value,
        expeditionNodeId: node.id,
      });
      if (requestGeneration !== challengeGeneration) return;
      activeProblem.value = problem;
      activeCode.value = initialCodeFor(activeProblem.value);
    } catch (error) {
      if (requestGeneration === challengeGeneration) {
        errorMessage.value = error instanceof Error ? error.message : '出题失败，请重试';
      }
    } finally {
      if (requestGeneration === challengeGeneration) loading.value = false;
    }
  }

  /**
   * 预取所有「可前往的战斗类节点」的题目：落到地图时调用，玩家点哪个都秒开。
   * 难度按当前气势/连败算，与 beginChallenge 同口径 → 进入时命中缓存。
   * 跳过事件节点（「钻研」会临时 +1 难度，预取的难度会对不上）与营地/宝箱（本就不出题）。
   */
  function prefetchAvailable(): void {
    if (phase.value !== 'map') return;
    const recentFailures = history.value.slice(0, 2).filter((h) => !h.passed).length;
    for (const node of nodes.value) {
      if (node.state !== 'available') continue;
      if (!node.kcCode || !node.challengeKind || node.kind === 'event') continue;
      const difficulty = computeRunDifficulty(node.difficulty, momentum.value, recentFailures);
      void challenge.prefetchProblem({
        kc: node.kcCode,
        kind: node.challengeKind,
        difficulty,
        expeditionRunId: runId.value,
        expeditionNodeId: node.id,
      });
    }
  }

  // 落到地图（开局 / 通关返回 / 营地 / 宝箱 / 事件复盘后）→ 预取可达节点题目，减少「正在出题…」等待。
  watch(phase, (p) => {
    if (p === 'map' && !starting.value) prefetchAvailable();
  });

  async function enterNode(id: string): Promise<void> {
    if (phase.value !== 'map') return;
    const node = nodeById(id);
    const isCurrentNode = node?.state === 'current' && currentNodeId.value === id;
    if (!node || (node.state !== 'available' && !isCurrentNode)) return;
    const previousHp = hp.value;
    const canonical = await performAction({ action: 'ENTER_NODE', nodeId: id });
    const entered = nodeById(id);
    if (!canonical || phase.value !== canonical.phase || !entered || currentNodeId.value !== id) return;

    // 非战斗节点（营地 / 宝箱）即时结算，不出题。
    if (entered.kind === 'rest') {
      if (canonical.hp > previousHp) toast.show(`营地休整：恢复 ${canonical.hp - previousHp} 点体力`, 'success');
      return;
    }
    if (entered.kind === 'chest') {
      openReward();
      return;
    }

    // 概念事件：先选应对（钻研/稳练/复盘），再决定是否开打。
    if (entered.kind === 'event') {
      activeNode.value = entered;
      eventScoreMult.value = 1;
      activeEventText.value = pickEventScenario(kcDisplayName(entered.kcCode ?? ''));
      return;
    }

    // 战斗 / 精英 / 抓 bug / boss：直接出题（boss 多阶段从第 1 阶段起）。
    await beginChallenge(entered);
  }

  /** 事件节点三选一：钻研(难度+1，得分×1.5) / 稳练(常规) / 复盘(不战，回血+小分直接过)。 */
  async function chooseEvent(choice: RogueEventChoice): Promise<void> {
    const node = activeNode.value;
    if (!node || phase.value !== 'event') return;
    const action = choice === 'review'
      ? 'EVENT_REVIEW'
      : choice === 'study' ? 'EVENT_STUDY' : 'EVENT_STEADY';
    const canonical = await performAction({ action, nodeId: node.id });
    if (!canonical || phase.value !== canonical.phase || currentNodeId.value !== node.id) return;
    if (choice === 'review') {
      score.value += node.difficulty * 5;
      persist();
      return;
    }
    if (choice === 'study') {
      eventScoreMult.value = 1.5;
    } else {
      eventScoreMult.value = 1;
    }
    const current = nodeById(node.id);
    if (current) await beginChallenge(current, choice === 'study' ? 1 : 0);
  }

  function scoreFor(node: RogueNode): number {
    const effective = activeDifficulty.value > 0 ? activeDifficulty.value : node.difficulty;
    const base = effective * 10;
    const momentumBonus = Math.round(base * momentum.value * 0.1);
    const kindMult = node.kind === 'elite' || node.kind === 'boss' ? 1.5 : 1;
    const relicMult = hasRelic('combo_scholar') ? 1.2 : 1;
    const speedMult = hasRelic('quick_thinker') && elapsedSec.value <= 30 ? 1.5 : 1;
    return Math.round((base + momentumBonus) * kindMult * relicMult * speedMult * eventScoreMult.value);
  }

  function applySettledGameScore(problemId: string): void {
    const points = pendingGameScore.get(problemId) ?? 0;
    if (points > 0 && !scoredProblemIds.has(problemId)) {
      score.value += points;
      scoredProblemIds.add(problemId);
    }
    pendingGameScore.delete(problemId);
    persist();
  }

  async function submitChallenge(): Promise<void> {
    const node = activeNode.value;
    const problem = activeProblem.value;
    if (!node || !problem || loading.value || settlementPending.value) return;
    const requestGeneration = challengeGeneration;
    loading.value = true;
    errorMessage.value = '';
    try {
      const result = await challenge.judge(problem, activeCode.value, elapsedSec.value);
      if (requestGeneration !== challengeGeneration) return;
      if (isJudgeUnavailable(result)) {
        errorMessage.value = '判题服务暂不可用，本次不计分，请稍后重试。';
        return;
      }
      challengeResult.value = result;
      pendingGameScore.set(problem.problemId, result.passed ? scoreFor(node) : 0);
      settlementPending.value = true;
      const canonical = await performAction({
        action: 'SETTLE_CHALLENGE', nodeId: node.id, problemId: problem.problemId,
      }, true);
      if (!canonical) {
        settlementPending.value = true;
        return;
      }
      settlementPending.value = false;
      applySettledGameScore(problem.problemId);
      void maybeReportStar();
      if (canonical.phase === 'over' || canonical.phase === 'cleared') {
        if (isDaily.value) {
          const date = todayStr();
          dailyDoneDate.value = date;
          saveDailyDate(date);
        }
        void reportScore();
      }
    } catch (error) {
      if (requestGeneration === challengeGeneration) {
        errorMessage.value = error instanceof Error ? error.message : '判题失败，请重试';
      }
    } finally {
      if (requestGeneration === challengeGeneration) loading.value = false;
    }
  }

  /** 战斗结算后：返回地图；若刚通关 boss → 胜利；HP 归零 → 结束。 */
  async function continueAfterChallenge(): Promise<void> {
    const node = activeNode.value;
    const result = challengeResult.value;
    if (!node || !result) return;
    if (settlementPending.value && activeProblem.value) {
      if (loading.value) return;
      loading.value = true;
      const canonical = await performAction({
        action: 'SETTLE_CHALLENGE', nodeId: node.id, problemId: activeProblem.value.problemId,
      }, true);
      loading.value = false;
      if (!canonical) return;
      settlementPending.value = false;
      applySettledGameScore(activeProblem.value.problemId);
    }
    const destination = nextPhase.value;
    const wasBoss = node?.kind === 'boss';
    const wasElite = node?.kind === 'elite';
    const passed = result.passed;
    if (!destination) return;
    if (wasBoss && passed && destination === 'challenge') {
      toast.show(`BOSS 阶段 ${activeBossPhase.value}/${bossPhasesTotal.value} 通过，继续！`, 'success');
      const current = nodeById(node.id);
      if (current) await beginChallenge(current);
      return;
    }
    clearActive();
    phase.value = wasElite && passed && destination === 'reward' ? 'reward' : destination;
    if (phase.value === 'reward') openReward();
    else if (wasElite && passed && destination === 'map') openReward();
    // 本局收尾：上报班级排行榜成绩（每局一次）；每日挑战记「今日已挑战」（无论胜负）。
    if (phase.value === 'over' || phase.value === 'cleared') {
      if (isDaily.value) {
        const d = todayStr();
        dailyDoneDate.value = d;
        saveDailyDate(d);
      }
      void reportScore();
    }
    persist();
  }

  /**
   * 主动结束本局：直接进结算页（掌握度/判题结果已实时入库，永不回滚）。
   * 区别于 abort（弃局清后端活跃 run + 本地缓存、回到起始页）。每日挑战照常算「今日已挑战」。
   */
  async function finishRun(): Promise<void> {
    if (phase.value === 'idle' || phase.value === 'over' || phase.value === 'cleared') return;
    const canonical = await performAction({ action: 'FINISH' });
    if (!canonical) return;
    if (isDaily.value) {
      const d = todayStr();
      dailyDoneDate.value = d;
      saveDailyDate(d);
    }
    // 主动结束也算一局有效成绩：上报班级排行榜（reportScore 内部 scoreReported 防重复）。
    await reportScore();
  }

  /** 战斗失败后重试同一节点（重新出一道同 KC/题型/难度的题，事件加成保留）。 */
  async function retryChallenge(): Promise<void> {
    const node = activeNode.value;
    // 与 submitChallenge 同款并发守卫：重复点击「再试一次」会重复触发 LLM 出题（计费）
    // 且后到的 beginChallenge 会覆盖 activeProblem / activeCode。
    if (!node || loading.value || settlementPending.value) return;
    const difficulty = activeDifficulty.value > 0 ? activeDifficulty.value : node.difficulty;
    // 预取先于 beginChallenge，其间 loading 必须已置位，否则守卫留有空窗（双击仍会出两题）。
    loading.value = true;
    try {
      if (node.kcCode) {
        await challenge.retryPrefetch({
          kc: node.kcCode,
          kind: node.challengeKind,
          difficulty,
          expeditionRunId: runId.value,
          expeditionNodeId: node.id,
        });
      }
    } catch {
      // 预取失败不阻断重试：beginChallenge 会走现场出题路径。
    }
    // beginChallenge 内部 finally 会把 loading 归位。
    await beginChallenge(node, 0, difficulty);
  }

  /** UI/诊断按 node 查询预加载状态；ready 只来自后端 READY。 */
  function preloadStateForNode(id: string) {
    const node = nodeById(id);
    if (!node?.kcCode || !node.challengeKind) return null;
    const recentFailures = history.value.slice(0, 2).filter((h) => !h.passed).length;
    const difficulty = computeRunDifficulty(node.difficulty, momentum.value, recentFailures);
    return challenge.getPrefetchState({
      kc: node.kcCode,
      kind: node.challengeKind,
      difficulty,
      expeditionRunId: runId.value,
      expeditionNodeId: node.id,
    });
  }

  /** 弃局：回到起始页 + 清后端活跃 run 与本地缓存（避免下次 startRun 续到废局）。 */
  async function abort(): Promise<void> {
    phase.value = 'idle';
    clearActive();
    if (typeof window !== 'undefined') {
      try {
        window.localStorage.removeItem(userScopedKey(PERSIST_KEY));
      } catch {
        /* 隐私模式禁写，忽略 */
      }
    }
    savedRun.value = null;
    runId.value = null;
    await actionQueue;
    await deleteExpeditionRun().catch(() => {
      /* 后端不可达：本地已清，忽略 */
    });
  }

  function tickSecond(): void {
    if (phase.value === 'challenge' && !loading.value) elapsedSec.value += 1;
  }

  /** 起始/结算页拉取班级排行榜（best-effort，不阻塞）。 */
  async function loadLeaderboard(): Promise<void> {
    const board = await fetchExpeditionLeaderboard();
    if (board) leaderboard.value = board;
  }

  /** 本局结束上报成绩；只有服务端返回结算结果才允许开始下一局。 */
  async function reportScore(): Promise<boolean> {
    if (scoreReportTask) return scoreReportTask;
    if (scoreReported.value) return true;
    const currentRunId = runId.value;
    if (!currentRunId) {
      scoreReported.value = true;
      return true;
    }
    scoreReported.value = true;
    scoreReportTask = submitExpeditionScore({ runId: currentRunId })
      .then((board) => {
        if (!board) {
          scoreReported.value = false;
          return false;
        }
        leaderboard.value = board;
        return true;
      })
      .catch(() => {
        scoreReported.value = false;
        return false;
      })
      .finally(() => {
        scoreReportTask = null;
      });
    return scoreReportTask;
  }

  async function maybeReportStar(): Promise<void> {
    const chapter = focusChapter.value;
    const currentRunId = runId.value;
    if (summaryReported.value || chapter === null || currentRunId === null) return;
    const correct = passedCount.value;
    const total = attempts.value;
    if (total <= 0 || correct < STAR_MIN_CORRECT || correct / total < STAR_MIN_ACCURACY) return;
    summaryReported.value = true;
    try {
      const awarded = await mainline.submitEndlessSummary({
        chapter,
        runId: currentRunId,
      });
      if (awarded) toast.show(`第 ${chapter} 章远征补星 +1 ★`, 'success');
    } catch {
      summaryReported.value = false;
    }
  }

  return {
    phase,
    focusChapter,
    nodes,
    layers,
    currentNodeId,
    hp,
    maxHp,
    momentum,
    bestMomentum,
    score,
    verifiedScore,
    verifiedDepth,
    relics,
    isDaily,
    dailyAvailable,
    bossPhase,
    bossPhasesTotal,
    activeBossPhase,
    history,
    activeNode,
    activeProblem,
    activeDifficulty,
    activeCode,
    challengeResult,
    settlementPending,
    loading,
    starting,
    elapsedSec,
    errorMessage,
    activeEventText,
    rewardChoices,
    hasSavedRun,
    savedRunSummary,
    isVictory,
    nodesByLayer,
    passedCount,
    attempts,
    startRun,
    startDailyRun,
    refreshSavedRun,
    resumeSavedRun,
    saveCheckpoint,
    enterNode,
    chooseEvent,
    submitChallenge,
    continueAfterChallenge,
    chooseReward,
    finishRun,
    retryChallenge,
    preloadStateForNode,
    abort,
    tickSecond,
    leaderboard,
    loadLeaderboard,
  };
});
