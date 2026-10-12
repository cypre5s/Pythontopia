// 无尽远征（Roguelike）run 状态机（Pinia）。
//
// 一局 run：知识前沿生成分支地图 → 玩家沿出边选节点 → 战斗节点出题真判题 →
// 对得分/攒气势、错按韧性温和扣血（掌握度收益持久保留）→ HP 归零结束、打通 boss 胜利。
// 掌握度 / 达标后端权威（submitBossAttempt 入库）；本 store 只持久化 run 进度（localStorage）。
// 详见 docs/plans/2026-06-19-endless-roguelike-rebuild.md。

import { computed, ref, watch } from 'vue';
import { defineStore } from 'pinia';
import { generateRogueMap } from '@/data/rogueMap';
import {
  EXPEDITION_MAX_HP,
  NODE_KIND_POOLS,
  REST_HEAL,
  computeLayeredDifficulty,
  computeRunDifficulty,
} from '@/data/rogueConfig';
import { ROGUE_RELICS, relicById } from '@/data/rogueRelics';
import { pickEventScenario, type RogueEventChoice } from '@/data/rogueEvents';
import { kcDisplayName } from '@/data/kcNames';
import { useChallengeJudge, isJudgeUnavailable } from '@/composables/useChallengeJudge';
import { useProfileSignals } from '@/composables/useProfileSignals';
import {
  deleteExpeditionRun,
  fetchExpeditionLeaderboard,
  fetchExpeditionRun,
  requestFrontierPicks,
  saveExpeditionRun,
  submitExpeditionScore,
  type ExpeditionLeaderboard,
  type ExpeditionRunSnapshot,
  type FrontierPick,
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

const PERSIST_KEY = 'pyt_rogue_run_v1';
const DAILY_KEY = 'pyt_rogue_daily_v1';
// 本地补星提示门槛；服务端仍按活动 run 的正式判题记录独立复核。
const STAR_MIN_CORRECT = 8;
const STAR_MIN_ACCURACY = 0.8;
// BOSS 多阶段：连过 N 道才算击穿知识闸（过关高潮）。
const BOSS_PHASES = 3;

function todayStr(): string {
  return new Date().toISOString().slice(0, 10);
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
/** 确定性 RNG（LCG）：每日挑战按日期种子生成稳定的当日地图。 */
function makeLcg(seed: number): () => number {
  let s = (seed >>> 0) || 1;
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 0xffffffff;
  };
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
  lastPlayedAt: string | null;
}

/** 续局允许的 phase（中途答题/事件/领增益统一回退到地图，避免存档因瞬时态被判废）。 */
function isResumablePhase(p: RogueRunPhase): boolean {
  return p === 'map' || p === 'challenge' || p === 'event' || p === 'reward';
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
  const { signals, hasAnySignal } = useProfileSignals();
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
  const score = ref(0);
  const verifiedScore = ref(0);
  const verifiedDepth = ref(0);
  const relics = ref<string[]>([]);
  const history = ref<RogueHistoryEntry[]>([]);
  const savedRun = ref<PersistShape | null>(loadPersisted());
  const savedRunVerified = ref(false);
  let serverPersistenceEnabled = true;
  let challengeGeneration = 0;
  let persistenceGeneration = 0;
  let snapshotVersion = 0;
  let persistQueue: Promise<void> = Promise.resolve();

  const hasRelic = (id: string): boolean => relics.value.includes(id);

  // 当前进入的节点 + 题目（不持久化：刷新中途答题安全退回地图）。
  const activeNode = ref<RogueNode | null>(null);
  const activeProblem = ref<BossProblemDto | null>(null);
  // 本次挑战实际难度（= 基准 node.difficulty 经局内气势/连败自适应后的值；驱动出题与计分）。
  const activeDifficulty = ref(0);
  const activeCode = ref('');
  const challengeResult = ref<BossAttemptResultDto | null>(null);
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
  const hasSavedRun = computed(() => isUsableSavedRun(savedRun.value));
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

  /**
   * 持久化 run 进度：后端权威（saveExpeditionRun）+ localStorage 兜底。
   * 后端失败/不可达只吞掉（best-effort，绝不阻塞玩家）；本地写入隐私模式禁写也忽略。
   * idle（弃局/未开局）不镜像后端——交由 abort() 显式 DELETE 清活跃 run。
   */
  async function persist(): Promise<void> {
    const snap = snapshot();
    const version = ++snapshotVersion;
    const generation = persistenceGeneration;
    if (typeof window !== 'undefined') {
      try {
        window.localStorage.setItem(userScopedKey(PERSIST_KEY), JSON.stringify(snap));
      } catch {
        /* 隐私模式禁写，忽略 */
      }
    }
    if (isResumablePhase(phase.value)) {
      savedRun.value = snap;
      savedRunVerified.value = false;
    }
    if (!isResumablePhase(phase.value) || !serverPersistenceEnabled) return;
    persistQueue = persistQueue.then(async () => {
      if (
        generation !== persistenceGeneration
        || !serverPersistenceEnabled
        || !isResumablePhase(phase.value)
      ) return;
      try {
        const saved = await saveExpeditionRun({ ...snap, runId: runId.value ?? snap.runId });
        if (!saved?.runId || generation !== persistenceGeneration) return;
        runId.value = saved.runId;
        if (typeof saved.score === 'number') verifiedScore.value = saved.score;
        if (typeof saved.verifiedDepth === 'number') verifiedDepth.value = saved.verifiedDepth;
        if (version !== snapshotVersion) return;
        const updated = {
          ...snap,
          runId: saved.runId,
          score: typeof saved.score === 'number' ? saved.score : score.value,
          verifiedDepth: typeof saved.verifiedDepth === 'number' ? saved.verifiedDepth : 0,
        };
        savedRun.value = updated;
        savedRunVerified.value =
          typeof saved.score === 'number' && typeof saved.verifiedDepth === 'number';
        if (typeof window !== 'undefined') {
          try {
            window.localStorage.setItem(userScopedKey(PERSIST_KEY), JSON.stringify(updated));
          } catch {
            /* 隐私模式禁写，忽略 */
          }
        }
      } catch {
        /* 后端不可达：本地已兜底，忽略 */
      }
    });
    await persistQueue;
  }

  /** 用户显式保存当前进度：用于“保存并返回小镇”。 */
  function saveCheckpoint(): void {
    if (phase.value === 'idle') return;
    persist();
    toast.show('远征已存档，下次可从存档继续', 'success');
  }

  /** 把一份快照灌入 store（中途答题/事件刷新 → 退回地图）；供 hydrate / 续局复用。 */
  function applySnapshot(p: PersistShape, serverVerified: boolean): void {
    phase.value = isResumablePhase(p.phase) && p.phase !== 'map' ? 'map' : p.phase;
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
    // 续局统一回退到地图：BOSS 多阶段不跨会话恢复（重进 BOSS 由 enterNode 重置为第 1 阶段），
    // 这里显式归零避免上一局的 bossPhase 残留被渲染。
    bossPhase.value = 1;
    clearActive();
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
    persistenceGeneration += 1;
    await persistQueue;
    const loaded = isUsableSavedRun(savedRun.value)
      ? { snapshot: savedRun.value, serverVerified: savedRunVerified.value }
      : await loadSavedSnapshot();
    const snap = loaded.snapshot;
    if (!isUsableSavedRun(snap)) {
      savedRun.value = null;
      savedRunVerified.value = false;
      return false;
    }
    savedRun.value = snap;
    savedRunVerified.value = loaded.serverVerified;
    serverPersistenceEnabled = loaded.serverVerified;
    applySnapshot(snap, loaded.serverVerified); // applySnapshot 已把可续 phase 归一到 'map'
    await persist();
    return true;
  }

  /** 把一件学习增益落到 run 上（仅厚实笔记有即时数值效果，其余在计分/扣血/回血时按需读取）。 */
  function applyRelic(id: string): void {
    if (relics.value.includes(id)) return;
    relics.value = [...relics.value, id];
    if (id === 'sturdy_notes') {
      maxHp.value += 1;
      hp.value += 1;
    }
    const relic = relicById(id);
    if (relic) toast.show(`获得学习增益：${relic.icon} ${relic.name}`, 'success');
  }

  /**
   * 开启「三选一」学习增益（宝箱 / 精英通关触发）：从未拥有的增益里抽最多 3 个候选交玩家抉择，
   * 把「领奖」从随机发放升级为 roguelike 式取舍。已集齐则折算少量得分、直接回地图。
   */
  function openReward(): void {
    const owned = new Set(relics.value);
    const pool = ROGUE_RELICS.filter((r) => !owned.has(r.id)).map((r) => r.id);
    if (pool.length === 0) {
      score.value += 30;
      toast.show('已集齐全部学习增益 · 折算 +30 分', 'info');
      rewardChoices.value = [];
      phase.value = 'map';
      persist();
      return;
    }
    rewardChoices.value = sampleN(pool, 3);
    phase.value = 'reward';
    persist();
  }

  /** 三选一领取：选中的增益生效并回到地图（未选中的弃掉）。 */
  function chooseReward(id: string): void {
    if (phase.value !== 'reward') return;
    if (rewardChoices.value.includes(id)) applyRelic(id);
    rewardChoices.value = [];
    phase.value = 'map';
    persist();
  }

  function resolveChallenge(
    kind: RogueNodeKind,
    layer: number,
    picks: readonly FrontierPick[],
    cursor: { value: number },
  ) {
    if (kind === 'rest' || kind === 'chest') {
      return { kcCode: null, challengeKind: null as ArenaLevelKind | null, difficulty: 0 };
    }
    // 全部挑战节点只消费后端权威前沿；聚焦章节 KC 少于地图节点时循环使用该真实排序，
    // 不再静默换成前端默认 KC / 默认掌握度。
    if (picks.length === 0) {
      throw new Error('没有可用的画像学习前沿');
    }
    const pick = picks[cursor.value % picks.length];
    cursor.value += 1;
    if (!pick || !pick.kcCode || !Number.isFinite(pick.difficulty)) {
      throw new Error('学习前沿数据不完整');
    }
    const kc = pick.kcCode;
    let difficulty = computeLayeredDifficulty(pick.difficulty, layer);
    const pool = NODE_KIND_POOLS[kind];
    const challengeKind = (pool.length > 0
      ? pool[Math.floor(Math.random() * pool.length)]
      : 'dual_track') as ArenaLevelKind;
    if (kind === 'elite') difficulty = Math.min(5, difficulty + 1);
    if (kind === 'boss') difficulty = Math.min(5, Math.max(3, difficulty + 1));
    return { kcCode: kc, challengeKind, difficulty };
  }

  async function startRun(
    focus: number | null,
    opts: { rng?: () => number; daily?: boolean } = {},
  ): Promise<void> {
    if (starting.value) return;
    persistenceGeneration += 1;
    challengeGeneration += 1;
    loading.value = false;
    starting.value = true;
    errorMessage.value = '';
    focusChapter.value = focus;
    try {
      await persistQueue;
      if (phase.value === 'over' || phase.value === 'cleared') {
        if (!(await reportScore())) {
          errorMessage.value = '本局成绩暂未结算，请重试后再开新局';
          toast.show(errorMessage.value, 'error');
          return;
        }
      } else if (phase.value === 'idle') {
        const existing = await fetchExpeditionRun();
        if (existing?.runId) await deleteExpeditionRun();
      }
      // 后端同时读取 kc_mastery + learner_profile；失败时保持 idle 并明确提示，不用前端假数据造图。
      const picks = await requestFrontierPicks({
        focusChapter: focus,
        count: 40,
        ...(hasAnySignal.value ? { profileSignals: signals.value } : {}),
      });
      if (picks.length === 0) throw new Error('服务端未返回画像学习前沿');
      const cursor = { value: 0 };
      const map = generateRogueMap({
        rng: opts.rng,
        resolveChallenge: (kind, layer) => resolveChallenge(kind, layer, picks, cursor),
      });
      runId.value = null;
      nodes.value = map.nodes;
      layers.value = map.layers;
      currentNodeId.value = null;
      hp.value = EXPEDITION_MAX_HP;
      maxHp.value = EXPEDITION_MAX_HP;
      momentum.value = 0;
      bestMomentum.value = 0;
      score.value = 0;
      verifiedScore.value = 0;
      verifiedDepth.value = 0;
      savedRunVerified.value = false;
      relics.value = [];
      history.value = [];
      bossPhase.value = 1;
      isDaily.value = opts.daily ?? false;
      summaryReported.value = false;
      scoreReported.value = false;
      challenge.clearPrefetch();
      clearActive();
      serverPersistenceEnabled = true;
      phase.value = 'map';
      await persist();
      prefetchAvailable();
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '画像学习前沿加载失败';
      phase.value = 'idle';
      toast.show(`远征暂未开始：${errorMessage.value}`, 'error');
    } finally {
      starting.value = false;
    }
  }

  /** 每日挑战：当日一次，弱项 + 日期种子稳定地图。 */
  async function startDailyRun(): Promise<void> {
    if (!dailyAvailable.value) {
      toast.show('今日挑战已完成，明天再来', 'info');
      return;
    }
    const seed = Number(todayStr().replace(/-/g, ''));
    await startRun(null, { rng: makeLcg(seed), daily: true });
  }

  function clearActive(): void {
    challengeGeneration += 1;
    activeNode.value = null;
    activeProblem.value = null;
    activeDifficulty.value = 0;
    activeCode.value = '';
    challengeResult.value = null;
    elapsedSec.value = 0;
    errorMessage.value = '';
    activeEventText.value = '';
    eventScoreMult.value = 1;
    rewardChoices.value = [];
    loading.value = false;
  }

  /** 标记节点通关：解锁出边、同层其它可选节点置为绕过（不可回头）、过半程小回血。 */
  function markCleared(node: RogueNode): void {
    node.state = 'cleared';
    for (const sib of nodes.value) {
      if (sib.layer === node.layer && sib.id !== node.id && sib.state === 'available') {
        sib.state = 'bypassed';
      }
    }
    for (const id of node.next) {
      const target = nodeById(id);
      if (target && target.state === 'locked') target.state = 'available';
    }
    maybeMilestone(node);
  }

  /** 首次到达「半程层」时小回 1 点体力（每层至多通关 1 个节点 → 天然只触发一次；续局不重复）。 */
  function maybeMilestone(node: RogueNode): void {
    const halfway = Math.floor((layers.value - 1) / 2);
    if (halfway <= 0 || node.layer !== halfway) return;
    const otherCleared = nodes.value.some(
      (n) => n.layer === halfway && n.id !== node.id && n.state === 'cleared',
    );
    if (otherCleared) return;
    if (hp.value < maxHp.value) {
      hp.value = Math.min(maxHp.value, hp.value + 1);
      toast.show('已过半程 · 恢复 1 点体力', 'success');
    }
  }

  /** 拉取并进入某节点的判题（战斗/精英/抓bug/boss，以及事件选择后的挑战）。 */
  async function beginChallenge(node: RogueNode): Promise<void> {
    const requestGeneration = ++challengeGeneration;
    activeNode.value = node;
    activeCode.value = '';
    challengeResult.value = null;
    errorMessage.value = '';
    elapsedSec.value = 0;
    phase.value = 'challenge';
    loading.value = true;
    // 局内自适应：连胜抬难度、近期连败温和降（不改地图基准 node.difficulty，只影响本次出题/计分）。
    const recentFailures = history.value.slice(0, 2).filter((h) => !h.passed).length;
    activeDifficulty.value = computeRunDifficulty(node.difficulty, momentum.value, recentFailures);
    persist();
    try {
      const problem = await challenge.fetchProblem({
        kc: node.kcCode ?? '',
        kind: node.challengeKind,
        difficulty: activeDifficulty.value,
        expeditionRunId: runId.value,
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
    if (!node || node.state !== 'available') return;
    currentNodeId.value = id;

    // 非战斗节点（营地 / 宝箱）即时结算，不出题。
    if (node.kind === 'rest') {
      const heal = REST_HEAL + (hasRelic('focus_lens') ? 1 : 0);
      hp.value = Math.min(maxHp.value, hp.value + heal);
      markCleared(node);
      toast.show(`营地休整：恢复 ${heal} 点体力`, 'success');
      persist();
      return;
    }
    if (node.kind === 'chest') {
      markCleared(node);
      openReward();
      return;
    }

    // 概念事件：先选应对（钻研/稳练/复盘），再决定是否开打。
    if (node.kind === 'event') {
      activeNode.value = node;
      eventScoreMult.value = 1;
      activeEventText.value = pickEventScenario(kcDisplayName(node.kcCode ?? ''));
      phase.value = 'event';
      persist();
      return;
    }

    // 战斗 / 精英 / 抓 bug / boss：直接出题（boss 多阶段从第 1 阶段起）。
    if (node.kind === 'boss') bossPhase.value = 1;
    await beginChallenge(node);
  }

  /** 事件节点三选一：钻研(难度+1，得分×1.5) / 稳练(常规) / 复盘(不战，回血+小分直接过)。 */
  async function chooseEvent(choice: RogueEventChoice): Promise<void> {
    const node = activeNode.value;
    if (!node || phase.value !== 'event') return;
    if (choice === 'review') {
      const heal = 1 + (hasRelic('focus_lens') ? 1 : 0);
      hp.value = Math.min(maxHp.value, hp.value + heal);
      score.value += node.difficulty * 5;
      markCleared(node);
      clearActive();
      phase.value = 'map';
      persist();
      return;
    }
    if (choice === 'study') {
      node.difficulty = Math.min(5, node.difficulty + 1);
      eventScoreMult.value = 1.5;
    } else {
      eventScoreMult.value = 1;
    }
    await beginChallenge(node);
  }

  function scoreFor(node: RogueNode): number {
    // 计分以「本次实际难度」为底（连胜抬难度后通关给更高回报）；未进战斗时回退基准难度。
    const effective = activeDifficulty.value > 0 ? activeDifficulty.value : node.difficulty;
    const base = effective * 10;
    const momentumBonus = Math.round(base * momentum.value * 0.1);
    const kindMult = node.kind === 'elite' || node.kind === 'boss' ? 1.5 : 1;
    const relicMult = hasRelic('combo_scholar') ? 1.2 : 1;
    // 「急智」：30 秒内通关额外 +50% 得分（奖励快速作答，不影响判题真源）。
    const speedMult = hasRelic('quick_thinker') && elapsedSec.value <= 30 ? 1.5 : 1;
    return Math.round((base + momentumBonus) * kindMult * relicMult * speedMult * eventScoreMult.value);
  }

  async function submitChallenge(): Promise<void> {
    const node = activeNode.value;
    const problem = activeProblem.value;
    if (!node || !problem || loading.value) return;
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
      history.value = [
        { nodeId: node.id, kcCode: node.kcCode, kind: node.kind, passed: result.passed, score: result.passed ? scoreFor(node) : 0, at: new Date().toISOString() },
        ...history.value,
      ].slice(0, 50);

      if (result.passed) {
        score.value += scoreFor(node);
        momentum.value += 1;
        if (momentum.value > bestMomentum.value) bestMomentum.value = momentum.value;
        // BOSS 多阶段：未到最后一阶段则进入下一阶段、不结算，连续作战。
        if (node.kind === 'boss' && bossPhase.value < bossPhasesTotal.value) {
          bossPhase.value += 1;
          toast.show(`BOSS 阶段 ${bossPhase.value - 1}/${bossPhasesTotal.value} 通过，继续！`, 'success');
          void maybeReportStar();
          persist();
          await beginChallenge(node);
          return;
        }
        markCleared(node);
        // 精英通关的学习增益走「三选一」（continueAfterChallenge 里玩家看完判题结果再选）。
      } else {
        const resilience = signals.value.resilience ?? 0.5;
        // 温和：低韧性扣得更少，避免逼脆弱的学生；「沉静心法」再 -1（至少扣 1）。
        let loss = resilience < 0.4 ? 1 : 2;
        if (hasRelic('calm_mind')) loss = Math.max(1, loss - 1);
        // 「急救包」：本局首次答错不扣血。用 history 里此前的失败数判定（已含本次失败条目 → 排除自身），
        // 从持久化历史派生 → 续局也不会被重置而重复触发。
        const priorMistakes = history.value.slice(1).filter((h) => !h.passed).length;
        if (hasRelic('first_aid') && priorMistakes === 0) {
          loss = 0;
          toast.show('急救包：本局首次失误免于扣体力', 'info');
        }
        hp.value = Math.max(0, hp.value - loss);
        // 「复盘回气」：气势减半而非清零。
        momentum.value = hasRelic('second_wind') ? Math.floor(momentum.value / 2) : 0;
      }
      void maybeReportStar();
      persist();
    } catch (error) {
      if (requestGeneration === challengeGeneration) {
        errorMessage.value = error instanceof Error ? error.message : '判题失败，请重试';
      }
    } finally {
      if (requestGeneration === challengeGeneration) loading.value = false;
    }
  }

  /** 战斗结算后：返回地图；若刚通关 boss → 胜利；HP 归零 → 结束。 */
  function continueAfterChallenge(): void {
    const node = activeNode.value;
    const wasBoss = node?.kind === 'boss';
    const wasElite = node?.kind === 'elite';
    const passed = challengeResult.value?.passed ?? false;
    clearActive();
    if (hp.value <= 0) {
      phase.value = 'over';
    } else if (wasBoss && passed) {
      phase.value = 'cleared';
    } else if (wasElite && passed) {
      // 精英通关 → 三选一领增益（openReward 内部置 phase='reward' 并 persist；集齐则回地图）。
      openReward();
    } else {
      phase.value = 'map';
    }
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
  function finishRun(): void {
    if (phase.value === 'idle' || phase.value === 'over' || phase.value === 'cleared') return;
    clearActive();
    phase.value = 'over';
    if (isDaily.value) {
      const d = todayStr();
      dailyDoneDate.value = d;
      saveDailyDate(d);
    }
    // 主动结束也算一局有效成绩：上报班级排行榜（reportScore 内部 scoreReported 防重复）。
    void reportScore();
    persist();
  }

  /** 战斗失败后重试同一节点（重新出一道同 KC/题型/难度的题，事件加成保留）。 */
  async function retryChallenge(): Promise<void> {
    const node = activeNode.value;
    // 与 submitChallenge 同款并发守卫：重复点击「再试一次」会重复触发 LLM 出题（计费）
    // 且后到的 beginChallenge 会覆盖 activeProblem / activeCode。
    if (!node || loading.value) return;
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
        });
      }
    } catch {
      // 预取失败不阻断重试：beginChallenge 会走现场出题路径。
    }
    // beginChallenge 内部 finally 会把 loading 归位。
    await beginChallenge(node);
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
    });
  }

  /** 弃局：回到起始页 + 清后端活跃 run 与本地缓存（避免下次 startRun 续到废局）。 */
  async function abort(): Promise<void> {
    persistenceGeneration += 1;
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
    await persistQueue;
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
    history,
    activeNode,
    activeProblem,
    activeDifficulty,
    activeCode,
    challengeResult,
    loading,
    starting,
    elapsedSec,
    errorMessage,
    activeEventText,
    rewardChoices,
    hasSavedRun,
    savedRunSummary,
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
