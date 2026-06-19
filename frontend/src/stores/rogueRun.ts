// 无尽远征（Roguelike）run 状态机（Pinia）。
//
// 一局 run：知识前沿生成分支地图 → 玩家沿出边选节点 → 战斗节点出题真判题 →
// 对得分/攒气势、错按韧性温和扣血（掌握度收益持久保留）→ HP 归零结束、打通 boss 胜利。
// 掌握度 / 达标后端权威（submitBossAttempt 入库）；本 store 只持久化 run 进度（localStorage）。
// 详见 docs/plans/2026-06-19-endless-roguelike-rebuild.md。

import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import { generateRogueMap } from '@/data/rogueMap';
import {
  EXPEDITION_MAX_HP,
  NODE_KIND_POOLS,
  REST_HEAL,
} from '@/data/rogueConfig';
import { ROGUE_RELICS } from '@/data/rogueRelics';
import { pickEventScenario, type RogueEventChoice } from '@/data/rogueEvents';
import { kcDisplayName } from '@/data/kcNames';
import { useKcFrontier } from '@/composables/useKcFrontier';
import { useChallengeJudge, isJudgeUnavailable } from '@/composables/useChallengeJudge';
import { useProfileSignals } from '@/composables/useProfileSignals';
import {
  deleteExpeditionRun,
  fetchExpeditionRun,
  requestFrontierPicks,
  saveExpeditionRun,
  type ExpeditionRunSnapshot,
  type FrontierPick,
} from '@/api/expedition';
import { useMainlineStore } from '@stores/mainline';
import { useToastStore } from '@stores/toast';
import type { BossAttemptResultDto, BossProblemDto } from '@/api/dto/Boss';
import type { ArenaLevelKind } from '@/types/arena';
import type {
  RogueHistoryEntry,
  RogueNode,
  RogueNodeKind,
  RogueRunPhase,
} from '@/types/rogue';

const PERSIST_KEY = 'pyt_rogue_run_v1';
const DAILY_KEY = 'pyt_rogue_daily_v1';
// 无尽补星门槛（与 EndlessPage / 后端 MainlineRecoveryService 一致）。
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
    return window.localStorage.getItem(DAILY_KEY);
  } catch {
    return null;
  }
}
function saveDailyDate(date: string): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(DAILY_KEY, date);
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

/** 续局允许的 phase（中途答题/事件刷新统一回退到地图）。 */
function isResumablePhase(p: RogueRunPhase): boolean {
  return p === 'map' || p === 'challenge' || p === 'event';
}

function loadPersisted(): PersistShape | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = window.localStorage.getItem(PERSIST_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as PersistShape;
    if (!parsed || !Array.isArray(parsed.nodes)) return null;
    return parsed;
  } catch {
    return null;
  }
}

export const useRogueRunStore = defineStore('rogueRun', () => {
  const frontier = useKcFrontier();
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
  const score = ref(0);
  const relics = ref<string[]>([]);
  const history = ref<RogueHistoryEntry[]>([]);

  const hasRelic = (id: string): boolean => relics.value.includes(id);

  // 当前进入的节点 + 题目（不持久化：刷新中途答题安全退回地图）。
  const activeNode = ref<RogueNode | null>(null);
  const activeProblem = ref<BossProblemDto | null>(null);
  const activeCode = ref('');
  const challengeResult = ref<BossAttemptResultDto | null>(null);
  const loading = ref(false);
  const elapsedSec = ref(0);
  const errorMessage = ref('');
  const summaryReported = ref(false);
  // 概念事件：进入 event 节点先选应对；study 选项把本题得分 ×eventScoreMult。
  const activeEventText = ref('');
  const eventScoreMult = ref(1);
  // BOSS 多阶段进度（连过 bossPhasesTotal 道才通关）。
  const bossPhase = ref(1);
  const bossPhasesTotal = ref(BOSS_PHASES);
  // 每日挑战：当日弱项 + 日期种子稳定地图；每天一次（记录完成日期）。
  const isDaily = ref(false);
  const dailyDoneDate = ref<string | null>(loadDailyDate());
  const dailyAvailable = computed(() => dailyDoneDate.value !== todayStr());

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
  const depth = computed(() => nodes.value.filter((n) => n.state === 'cleared' && n.challengeKind).length);
  const nodeById = (id: string): RogueNode | undefined => nodes.value.find((n) => n.id === id);

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
    };
  }

  /**
   * 持久化 run 进度：后端权威（saveExpeditionRun）+ localStorage 兜底。
   * 后端失败/不可达只吞掉（best-effort，绝不阻塞玩家）；本地写入隐私模式禁写也忽略。
   * idle（弃局/未开局）不镜像后端——交由 abort() 显式 DELETE 清活跃 run。
   */
  function persist(): void {
    const snap = snapshot();
    if (typeof window !== 'undefined') {
      try {
        window.localStorage.setItem(PERSIST_KEY, JSON.stringify(snap));
      } catch {
        /* 隐私模式禁写，忽略 */
      }
    }
    if (phase.value !== 'idle') {
      void saveExpeditionRun(snap).catch(() => {
        /* 后端不可达：本地已兜底，忽略 */
      });
    }
  }

  /** 把一份快照灌入 store（中途答题/事件刷新 → 退回地图）；供 hydrate / 续局复用。 */
  function applySnapshot(p: PersistShape): void {
    phase.value = isResumablePhase(p.phase) && p.phase !== 'map' ? 'map' : p.phase;
    focusChapter.value = p.focusChapter ?? null;
    nodes.value = p.nodes;
    layers.value = p.layers;
    currentNodeId.value = p.currentNodeId;
    hp.value = p.hp;
    maxHp.value = p.maxHp ?? EXPEDITION_MAX_HP;
    momentum.value = p.momentum ?? 0;
    bestMomentum.value = p.bestMomentum ?? 0;
    score.value = p.score ?? 0;
    relics.value = Array.isArray(p.relics) ? p.relics : [];
    isDaily.value = p.isDaily ?? false;
    history.value = Array.isArray(p.history) ? p.history : [];
    clearActive();
  }

  function hydrate(): void {
    const p = loadPersisted();
    if (p) applySnapshot(p);
  }

  /**
   * 续局：后端权威优先（fetchExpeditionRun），不可达回退 localStorage。命中可续 run（有节点 +
   * phase 可续）则灌入 store 返回 true；无则 false（调用方新开局）。绝不阻塞玩家。
   */
  async function tryResumeRun(): Promise<boolean> {
    let snap: ExpeditionRunSnapshot | null = null;
    try {
      snap = await fetchExpeditionRun();
    } catch {
      snap = loadPersisted();
    }
    if (!snap || !Array.isArray(snap.nodes) || snap.nodes.length === 0) return false;
    if (!isResumablePhase(snap.phase)) return false;
    applySnapshot(snap); // applySnapshot 已把可续 phase 归一到 'map'
    persist();
    return true;
  }

  /** 跨设备进页：本地无进行中的局时，静默拉取后端活跃 run 续上（供页面 onMounted 调）。 */
  async function resumeFromBackend(): Promise<boolean> {
    if (phase.value !== 'idle') return false;
    try {
      const snap = await fetchExpeditionRun();
      if (snap && Array.isArray(snap.nodes) && snap.nodes.length > 0 && isResumablePhase(snap.phase)) {
        applySnapshot(snap); // applySnapshot 已把可续 phase 归一到 'map'
        return true;
      }
    } catch {
      /* 后端不可达：保持 idle，玩家可手动开始 */
    }
    return false;
  }

  /** 获得一件未拥有的学习增益（宝箱 / 精英通关）；集齐则给提示不重复发。 */
  function grantRelic(): void {
    const owned = new Set(relics.value);
    const candidates = ROGUE_RELICS.filter((r) => !owned.has(r.id));
    if (candidates.length === 0) {
      toast.show('已集齐全部学习增益', 'info');
      return;
    }
    const relic = candidates[Math.floor(Math.random() * candidates.length)];
    if (!relic) return;
    relics.value = [...relics.value, relic.id];
    if (relic.id === 'sturdy_notes') {
      maxHp.value += 1;
      hp.value += 1;
    }
    toast.show(`获得学习增益：${relic.icon} ${relic.name}`, 'success');
  }

  function resolveChallenge(
    kind: RogueNodeKind,
    _layer: number,
    usedKcs: Set<string>,
    queue: FrontierPick[],
  ) {
    if (kind === 'rest' || kind === 'chest') {
      return { kcCode: null, challengeKind: null as ArenaLevelKind | null, difficulty: 0 };
    }
    // 优先用后端前沿选择器的 pick（权威 + 间隔重复）；用尽则回退本地 useKcFrontier。
    const pick = queue.shift();
    let kc: string;
    let difficulty: number;
    if (pick) {
      kc = pick.kcCode;
      difficulty = pick.difficulty;
    } else {
      kc = frontier.pickKc(focusChapter.value, usedKcs);
      difficulty = frontier.difficultyForKc(kc);
    }
    usedKcs.add(kc);
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
    opts: { rng?: () => number; daily?: boolean; fresh?: boolean } = {},
  ): Promise<void> {
    // 续局：每日挑战（种子稳定地图）与显式「弃局重开」(fresh) 不续局，其余先尝试续后端/本地活跃 run。
    if (!opts.daily && !opts.fresh) {
      const resumed = await tryResumeRun();
      if (resumed) return;
    }
    focusChapter.value = focus;
    // 后端权威「知识前沿选择器」（下沉）：取一批候选；失败/超时回退本地 useKcFrontier，绝不阻塞玩家。
    const recent = history.value
      .map((h) => h.kcCode)
      .filter((k): k is string => !!k)
      .slice(0, 12);
    let picks: FrontierPick[] = [];
    try {
      picks = await requestFrontierPicks({
        focusChapter: focus,
        recentKcs: recent,
        count: 28,
        ...(hasAnySignal.value ? { profileSignals: signals.value } : {}),
      });
    } catch {
      picks = [];
    }
    const queue = [...picks];
    const usedKcs = new Set<string>();
    const map = generateRogueMap({
      rng: opts.rng,
      resolveChallenge: (kind, layer) => resolveChallenge(kind, layer, usedKcs, queue),
    });
    nodes.value = map.nodes;
    layers.value = map.layers;
    currentNodeId.value = null;
    hp.value = EXPEDITION_MAX_HP;
    maxHp.value = EXPEDITION_MAX_HP;
    momentum.value = 0;
    bestMomentum.value = 0;
    score.value = 0;
    relics.value = [];
    history.value = [];
    bossPhase.value = 1;
    isDaily.value = opts.daily ?? false;
    summaryReported.value = false;
    clearActive();
    phase.value = 'map';
    persist();
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
    activeNode.value = null;
    activeProblem.value = null;
    activeCode.value = '';
    challengeResult.value = null;
    elapsedSec.value = 0;
    errorMessage.value = '';
    activeEventText.value = '';
    eventScoreMult.value = 1;
  }

  /** 标记节点通关：解锁出边、同层其它可选节点置为绕过（不可回头）。 */
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
  }

  /** 拉取并进入某节点的判题（战斗/精英/抓bug/boss，以及事件选择后的挑战）。 */
  async function beginChallenge(node: RogueNode): Promise<void> {
    activeNode.value = node;
    activeCode.value = '';
    challengeResult.value = null;
    errorMessage.value = '';
    elapsedSec.value = 0;
    phase.value = 'challenge';
    loading.value = true;
    try {
      activeProblem.value = await challenge.fetchProblem({
        kc: node.kcCode ?? '',
        kind: node.challengeKind,
        difficulty: node.difficulty,
      });
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '出题失败，请重试';
    } finally {
      loading.value = false;
    }
  }

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
      grantRelic();
      persist();
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
    const base = node.difficulty * 10;
    const momentumBonus = Math.round(base * momentum.value * 0.1);
    const kindMult = node.kind === 'elite' || node.kind === 'boss' ? 1.5 : 1;
    const relicMult = hasRelic('combo_scholar') ? 1.2 : 1;
    return Math.round((base + momentumBonus) * kindMult * relicMult * eventScoreMult.value);
  }

  async function submitChallenge(): Promise<void> {
    const node = activeNode.value;
    const problem = activeProblem.value;
    if (!node || !problem || loading.value) return;
    loading.value = true;
    errorMessage.value = '';
    try {
      const result = await challenge.judge(problem, activeCode.value, elapsedSec.value);
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
        // 精英通关给一件学习增益（roguelike 成长）。
        if (node.kind === 'elite') grantRelic();
      } else {
        const resilience = signals.value.resilience ?? 0.5;
        // 温和：低韧性扣得更少，避免逼脆弱的学生；「沉静心法」再 -1（至少扣 1）。
        let loss = resilience < 0.4 ? 1 : 2;
        if (hasRelic('calm_mind')) loss = Math.max(1, loss - 1);
        hp.value = Math.max(0, hp.value - loss);
        // 「复盘回气」：气势减半而非清零。
        momentum.value = hasRelic('second_wind') ? Math.floor(momentum.value / 2) : 0;
      }
      void maybeReportStar();
      persist();
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '判题失败，请重试';
    } finally {
      loading.value = false;
    }
  }

  /** 战斗结算后：返回地图；若刚通关 boss → 胜利；HP 归零 → 结束。 */
  function continueAfterChallenge(): void {
    const node = activeNode.value;
    const wasBoss = node?.kind === 'boss';
    const passed = challengeResult.value?.passed ?? false;
    clearActive();
    if (hp.value <= 0) {
      phase.value = 'over';
    } else if (wasBoss && passed) {
      phase.value = 'cleared';
    } else {
      phase.value = 'map';
    }
    // 每日挑战：本局收尾即算「今日已挑战」（无论胜负，给予参与认定）。
    if ((phase.value === 'over' || phase.value === 'cleared') && isDaily.value) {
      const d = todayStr();
      dailyDoneDate.value = d;
      saveDailyDate(d);
    }
    persist();
  }

  /** 战斗失败后重试同一节点（重新出一道同 KC/题型/难度的题，事件加成保留）。 */
  async function retryChallenge(): Promise<void> {
    const node = activeNode.value;
    if (!node) return;
    await beginChallenge(node);
  }

  /** 弃局：回到起始页 + 清后端活跃 run 与本地缓存（避免下次 startRun 续到废局）。 */
  async function abort(): Promise<void> {
    phase.value = 'idle';
    clearActive();
    if (typeof window !== 'undefined') {
      try {
        window.localStorage.removeItem(PERSIST_KEY);
      } catch {
        /* 隐私模式禁写，忽略 */
      }
    }
    await deleteExpeditionRun().catch(() => {
      /* 后端不可达：本地已清，忽略 */
    });
  }

  function tickSecond(): void {
    if (phase.value === 'challenge' && !loading.value) elapsedSec.value += 1;
  }

  async function maybeReportStar(): Promise<void> {
    const chapter = focusChapter.value;
    if (summaryReported.value || chapter === null) return;
    const correct = passedCount.value;
    const total = attempts.value;
    if (total <= 0 || correct < STAR_MIN_CORRECT || correct / total < STAR_MIN_ACCURACY) return;
    summaryReported.value = true;
    try {
      const awarded = await mainline.submitEndlessSummary({
        chapter,
        correct,
        total,
        streak: bestMomentum.value,
      });
      if (awarded) toast.show(`第 ${chapter} 章远征补星 +1 ★`, 'success');
    } catch {
      summaryReported.value = false;
    }
  }

  hydrate();

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
    relics,
    isDaily,
    dailyAvailable,
    bossPhase,
    bossPhasesTotal,
    history,
    activeNode,
    activeProblem,
    activeCode,
    challengeResult,
    loading,
    elapsedSec,
    errorMessage,
    activeEventText,
    nodesByLayer,
    passedCount,
    attempts,
    depth,
    startRun,
    startDailyRun,
    resumeFromBackend,
    enterNode,
    chooseEvent,
    submitChallenge,
    continueAfterChallenge,
    retryChallenge,
    abort,
    tickSecond,
  };
});
