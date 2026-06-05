// 主线进度 Pinia store。
//
// 持久化策略（双写）：
//   - localStorage（`pyt_main_quest_progress_v1`）：离线 / 未登录兜底，跨刷新即时生效。
//   - 后端 `save_slot.snapshot.mainQuest`（V120 契约）：登录后 debounced 同步，跨设备跟随。
// 进入世界 bootstrap 时由 world.syncFromSlot 调 hydrateFromSnapshot 回灌后端快照。

import { defineStore } from 'pinia';
import {
  MAIN_QUESTS,
  MAIN_QUEST_BOSSES,
  findMainQuestById,
  listSubQuestsByChapter,
} from '@/data/mainQuests';
import { CHAPTERS, formatKcNumber } from '@/data/chapters';
import { useLearningStore } from '@stores/learning';
import { useSessionStore } from '@stores/session';
import { createSaveSlot, saveSlotSnapshot } from '@/api/saveSlot';
import {
  mainQuestEmptyProgress,
  type MainQuestBoss,
  type MainQuestChapterView,
  type MainQuestProgress,
  type MainSubQuest,
  type MainSubQuestViewItem,
  type RecentAiProblemEntry,
} from '@/types/mainQuest';

const PERSIST_KEY = 'pyt_main_quest_progress_v1';

function syncEventToBackend(eventType: string, payload: Record<string, unknown>): void {
  void import('@/api/learningEvent').then((mod) => {
    mod.postLearningEvent({ eventType, payload }).catch(() => {
      // 后端不可达时静默忽略，localStorage 仍兜底
    });
  });
}
const MAX_AI_LOG = 30;

function safeParse(raw: string | null): MainQuestProgress | null {
  if (!raw) return null;
  try {
    const obj = JSON.parse(raw);
    if (typeof obj !== 'object' || obj === null) return null;
    const p = obj as Partial<MainQuestProgress>;
    return {
      currentChapter: typeof p.currentChapter === 'number' ? p.currentChapter : 1,
      currentSubQuest: typeof p.currentSubQuest === 'number' ? p.currentSubQuest : 1,
      completedSubQuests: Array.isArray(p.completedSubQuests)
        ? p.completedSubQuests.filter((s): s is string => typeof s === 'string')
        : [],
      defeatedBosses: Array.isArray(p.defeatedBosses)
        ? p.defeatedBosses.filter((n): n is number => typeof n === 'number')
        : [],
      unlockedMilestones: Array.isArray(p.unlockedMilestones)
        ? p.unlockedMilestones.filter((s): s is string => typeof s === 'string')
        : [],
      recentAiProblems: Array.isArray(p.recentAiProblems)
        ? p.recentAiProblems.filter(
            (e): e is RecentAiProblemEntry =>
              typeof e === 'object' &&
              e !== null &&
              typeof (e as RecentAiProblemEntry).problemId === 'string',
          )
        : [],
      consumedMedia: Array.isArray(p.consumedMedia)
        ? p.consumedMedia.filter((s): s is string => typeof s === 'string')
        : [],
    };
  } catch {
    return null;
  }
}

/**
 * 单调合并两份主线进度（hydrateFromSnapshot 把后端快照并入本地时用）。
 *
 * 避免「离线先行的本地进度被后端旧快照覆盖」。教育进度天然只增
 * （不会 un-complete 一个任务 / un-defeat 一个 boss），所以合并语义为：
 *   - completed / defeated / milestone / consumedMedia 取并集
 *   - 章节指针取「更靠前」的一方（章号优先，同章取更大子任务序号）
 *   - AI 题日志按时间倒序去重，保留最新 MAX_AI_LOG 条
 * 真正想清零进度请走显式的 resetProgress。
 */
function mergeProgress(local: MainQuestProgress, incoming: MainQuestProgress): MainQuestProgress {
  const union = (a: readonly string[], b: readonly string[]): string[] =>
    Array.from(new Set([...a, ...b]));
  const unionNum = (a: readonly number[], b: readonly number[]): number[] =>
    Array.from(new Set([...a, ...b])).sort((x, y) => x - y);

  let currentChapter: number;
  let currentSubQuest: number;
  if (incoming.currentChapter > local.currentChapter) {
    currentChapter = incoming.currentChapter;
    currentSubQuest = incoming.currentSubQuest;
  } else if (incoming.currentChapter < local.currentChapter) {
    currentChapter = local.currentChapter;
    currentSubQuest = local.currentSubQuest;
  } else {
    currentChapter = local.currentChapter;
    currentSubQuest = Math.max(local.currentSubQuest, incoming.currentSubQuest);
  }

  const combinedAi = [...local.recentAiProblems, ...incoming.recentAiProblems].sort((a, b) =>
    (b.timestamp ?? '').localeCompare(a.timestamp ?? ''),
  );
  const seen = new Set<string>();
  const recentAiProblems: RecentAiProblemEntry[] = [];
  for (const entry of combinedAi) {
    if (seen.has(entry.problemId)) continue;
    seen.add(entry.problemId);
    recentAiProblems.push(entry);
    if (recentAiProblems.length >= MAX_AI_LOG) break;
  }

  return {
    currentChapter,
    currentSubQuest,
    completedSubQuests: union(local.completedSubQuests, incoming.completedSubQuests),
    defeatedBosses: unionNum(local.defeatedBosses, incoming.defeatedBosses),
    unlockedMilestones: union(local.unlockedMilestones, incoming.unlockedMilestones),
    recentAiProblems,
    consumedMedia: union(local.consumedMedia, incoming.consumedMedia),
  };
}

function readPersisted(): MainQuestProgress {
  if (typeof window === 'undefined') return mainQuestEmptyProgress();
  try {
    return safeParse(window.localStorage.getItem(PERSIST_KEY)) ?? mainQuestEmptyProgress();
  } catch {
    return mainQuestEmptyProgress();
  }
}

function persist(progress: MainQuestProgress): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(PERSIST_KEY, JSON.stringify(progress));
  } catch {
    /* 隐私模式 */
  }
  scheduleSnapshotSync(progress);
}

// ───────────────── 后端存档槽同步（主线进度跨设备跟随） ─────────────────
// 每次 persist 都 debounce 推送一次 snapshot 到 `save_slot.snapshot.mainQuest`；
// 仅在已登录时真正发请求，未登录 / 离线时静默兜底 localStorage（与 syncEventToBackend 一致）。
// hydrateFromSnapshot 期间用 suppressSnapshotSync 抑制回灌→回写的回声。

const SNAPSHOT_SYNC_DEBOUNCE_MS = 1000;
let snapshotSyncTimer: ReturnType<typeof setTimeout> | null = null;
let suppressSnapshotSync = false;

function scheduleSnapshotSync(progress: MainQuestProgress): void {
  if (suppressSnapshotSync) return;
  if (typeof window === 'undefined') return;
  if (snapshotSyncTimer) clearTimeout(snapshotSyncTimer);
  snapshotSyncTimer = setTimeout(() => {
    snapshotSyncTimer = null;
    void pushSnapshotToBackend(progress);
  }, SNAPSHOT_SYNC_DEBOUNCE_MS);
}

async function pushSnapshotToBackend(progress: MainQuestProgress): Promise<void> {
  try {
    if (!useSessionStore().isAuthenticated) return;
    // world 必须动态 import：world.ts 静态依赖本 store（hydrate 回灌），避免静态环。
    const { useWorldStore } = await import('@stores/world');
    const world = useWorldStore();
    let slotIndex = world.activeSlot?.slotIndex;
    if (slotIndex === undefined) {
      // 新用户尚无存档槽：创建 slot 0（后端首槽自动激活），失败仍退回 slot 0 尝试写入。
      try {
        world.activeSlot = await createSaveSlot(0, '自动存档');
        slotIndex = world.activeSlot.slotIndex;
      } catch {
        slotIndex = 0;
      }
    }
    await saveSlotSnapshot(slotIndex, { version: 1, mainQuest: progress });
  } catch {
    // 未登录 / 后端不可达：localStorage 已兜底，静默忽略。
  }
}

/** 仅供测试：清掉挂起的同步计时器，避免跨用例泄漏。 */
export function __resetSnapshotSyncForTests(): void {
  if (snapshotSyncTimer) clearTimeout(snapshotSyncTimer);
  snapshotSyncTimer = null;
  suppressSnapshotSync = false;
}

export interface MainQuestStoreState {
  progress: MainQuestProgress;
  /** 主线面板可见性（HUD chip 切换）。 */
  panelOpen: boolean;
  /** 当玩家通过 mainQuest:open-boss 事件被引导跳转时，记录目标章。 */
  pendingBossChapter: number | null;
}

export const useMainQuestStore = defineStore('mainQuest', {
  state: (): MainQuestStoreState => ({
    progress: readPersisted(),
    panelOpen: false,
    pendingBossChapter: null,
  }),

  getters: {
    /** 当前要做的子任务（如果当前章有 boss 击败且 currentSubQuest>5，则提示进入下一章）。 */
    currentSubQuest(): MainSubQuest | null {
      const { currentChapter, currentSubQuest } = this.progress;
      if (currentChapter < 1) return null;
      return findMainQuestById(`${currentChapter}-${currentSubQuest}`);
    },
    isCompleted(): (id: string) => boolean {
      return (id: string) => this.progress.completedSubQuests.includes(id);
    },
    isBossDefeated(): (chapter: number) => boolean {
      return (chapter: number) => this.progress.defeatedBosses.includes(chapter);
    },
    /**
     * 章节视图：每章 5 任务 + boss + 进度百分比。
     * 用于 MainQuestPanel 主视图。
     */
    chapterViews(): MainQuestChapterView[] {
      const chapters: MainQuestChapterView[] = [];
      for (let chapter = 1; chapter <= 7; chapter += 1) {
        const subs = listSubQuestsByChapter(chapter);
        const items: MainSubQuestViewItem[] = subs.map((s) => {
          let status: MainSubQuestViewItem['status'] = 'locked';
          if (this.progress.completedSubQuests.includes(s.id)) {
            status = 'completed';
          } else if (
            this.progress.currentChapter === chapter &&
            this.progress.currentSubQuest === s.index
          ) {
            status = 'in_progress';
          } else if (
            chapter < this.progress.currentChapter ||
            (chapter === this.progress.currentChapter &&
              s.index <= this.progress.currentSubQuest)
          ) {
            status = 'available';
          }
          return { ...s, status };
        });
        const completed = items.filter((s) => s.status === 'completed').length;
        const boss = MAIN_QUEST_BOSSES.find((b) => b.chapter === chapter);
        if (!boss) continue;
        chapters.push({
          chapter,
          title: boss.name,
          subQuests: items,
          boss,
          progress: completed / Math.max(1, items.length),
          bossDefeated: this.progress.defeatedBosses.includes(chapter),
        });
      }
      return chapters;
    },
    overallProgress(): number {
      const total = MAIN_QUESTS.length;
      return total === 0 ? 0 : this.progress.completedSubQuests.length / total;
    },

    // ── 章节达标判定（双条件） ──
    /** 某章覆盖的全部 KC code（PY43-KC-NNN，由 chapters.ts 的 start/endKc 推导）。 */
    chapterKcCodes() {
      return (chapter: number): string[] => {
        const ch = CHAPTERS.find((c) => c.order === chapter);
        if (!ch) return [];
        const codes: string[] = [];
        for (let n = ch.startKc; n <= ch.endKc; n += 1) {
          codes.push(formatKcNumber(n));
        }
        return codes;
      };
    },
    /**
     * 本章 KC 平均掌握度（0..1）。分母用「本章全部 KC 数」，缺快照的 KC 记 0，
     * 避免「只练了 2 个 KC 就平均达标」的虚高。掌握度读 learning store 的 masterySnapshot
     * （来自 GET /api/learner/mastery / bootstrap）。
     */
    chapterMasteryAvg() {
      return (chapter: number): number => {
        const codes = this.chapterKcCodes(chapter);
        if (codes.length === 0) return 0;
        const wanted = new Set(codes);
        const snaps = useLearningStore().field.masterySnapshot.filter((s) =>
          wanted.has(s.kcCode),
        );
        const sum = snaps.reduce((acc, s) => acc + (s.masteryScore ?? 0), 0);
        return sum / codes.length;
      };
    },
    /**
     * 章节达标：本章「必做题」（submission 类子任务）全过 且 本章 KC 平均掌握度 ≥ 0.7。
     * 达标后解锁进阶 Hub（Boss / 进阶难题 / 工坊），也可直接跳过进下一章。
     */
    chapterStandardMet() {
      return (chapter: number): boolean => {
        const required = MAIN_QUESTS.filter(
          (q) => q.chapter === chapter && q.evidenceRequired.type === 'submission',
        );
        const allRequiredDone = required.every((q) =>
          this.progress.completedSubQuests.includes(q.id),
        );
        return allRequiredDone && this.chapterMasteryAvg(chapter) >= 0.7;
      };
    },
    /** 当前章是否达标（HUD/面板便捷读取）。 */
    currentChapterStandardMet(): boolean {
      return this.chapterStandardMet(this.progress.currentChapter);
    },

    /** 本章是否已消费过多模态讲解（视频 / AI 动画 / 图解）。 */
    chapterMediaConsumed() {
      return (chapter: number): boolean =>
        this.progress.consumedMedia.includes(`ch${chapter}`);
    },
    /**
     * 章节「完整通关」软门槛：达标（做题 + 掌握度）之外，再叠加「看过本章多模态讲解」。
     * 不硬阻塞章节推进（advanceChapter 仍只看 chapterStandardMet），仅用于面板的完整度提示与激励，
     * 把多模态消费真正纳入主线学习闭环。
     */
    chapterFullyComplete() {
      return (chapter: number): boolean =>
        this.chapterStandardMet(chapter) && this.chapterMediaConsumed(chapter);
    },
  },

  actions: {
    hydrateFromPersist(): void {
      this.progress = readPersisted();
    },
    hydrateFromSnapshot(raw: unknown): void {
      if (typeof raw !== 'object' || raw === null) return;
      const obj = raw as { mainQuest?: unknown };
      if (obj.mainQuest && typeof obj.mainQuest === 'object') {
        const parsed = safeParse(JSON.stringify(obj.mainQuest));
        if (parsed) {
          // 单调合并而非覆盖：离线先行的本地进度不会被后端旧快照回灌冲掉（见 mergeProgress）。
          // 写 localStorage 但抑制反向 snapshot 同步，避免 load→write 回声。
          const merged = mergeProgress(this.progress, parsed);
          suppressSnapshotSync = true;
          this.progress = merged;
          persist(merged);
          suppressSnapshotSync = false;
        }
      }
    },
    togglePanel(): void {
      this.panelOpen = !this.panelOpen;
    },
    openPanel(): void {
      this.panelOpen = true;
    },
    closePanel(): void {
      this.panelOpen = false;
    },
    /**
     * NPC 通过 dialog 的 `quest_recommend` 效果「发任务」。
     * - `questKey` 对齐 mainQuests id（如 "1-1"）；未知/自由串则仅打开主线面板做引导。
     * - 只打开面板做引导、不前移指针，避免借 NPC 对话越过 boss 跳关。
     * - 完成仍由各任务自身的 evidence 钩子负责，本动作只做「接取/聚焦」。
     */
    acceptQuest(questKey: string): MainSubQuest | null {
      syncEventToBackend('quest_accepted', { questKey });
      // NPC 的 quest_recommend 只做「打开主线面板做引导」，绝不前移指针。
      // 指针严格由 completeSubQuest（顺序完成）与 defeatBoss（章节）推进，
      // 杜绝玩家通过和后面章节/后面任务的 NPC 对话「跳关」提前进入下一阶段。
      const quest = findMainQuestById(questKey);
      this.openPanel();
      return quest ?? null;
    },
    /**
     * 标记一个子任务完成。
     * - 写 completedSubQuests（去重）
     * - 推进 currentSubQuest 到下一个未完成的；本章 5 完成后允许触发 boss
     */
    completeSubQuest(id: string): MainSubQuest | null {
      const quest = findMainQuestById(id);
      if (!quest) return null;
      if (this.progress.completedSubQuests.includes(id)) {
        return quest;
      }
      this.progress.completedSubQuests = [...this.progress.completedSubQuests, id];
      // 指针推进到「本章第一个未完成子任务」（封顶 5，等 boss）：
      // - 只在完成的是本章任务时重算，跨章的乱序预完成不动当前章指针；
      // - 只允许前移、跳过乱序已完成的任务，绝不回退，也绝不停在已完成任务上。
      if (this.progress.currentChapter === quest.chapter) {
        const done = new Set(this.progress.completedSubQuests);
        let frontier = 1;
        while (frontier < 5 && done.has(`${quest.chapter}-${frontier}`)) {
          frontier += 1;
        }
        if (frontier > this.progress.currentSubQuest) {
          this.progress.currentSubQuest = frontier;
        }
      }
      persist(this.progress);
      return quest;
    },
    /**
     * 标记某章 boss 通关。
     * - 写 defeatedBosses
     * - 推进 currentChapter / currentSubQuest 到下一章 1
     * - 解锁 storyboard / outfit 等里程碑
     */
    defeatBoss(chapter: number): MainQuestBoss | null {
      const boss = MAIN_QUEST_BOSSES.find((b) => b.chapter === chapter);
      if (!boss) return null;
      if (!this.progress.defeatedBosses.includes(chapter)) {
        this.progress.defeatedBosses = [...this.progress.defeatedBosses, chapter];
      }
      // 仅当击败的是「当前所在章」的 boss 时才推进章节指针：
      // 回头重打旧 boss 不会让章节回退，越级打后面 boss 也不会跳章。
      // （击杀记录 defeatedBosses 与里程碑解锁始终生效，不受此限。）
      if (chapter === this.progress.currentChapter) {
        if (chapter < 7) {
          this.progress.currentChapter = chapter + 1;
          this.progress.currentSubQuest = 1;
        } else {
          this.progress.currentSubQuest = 5;
        }
      }
      const milestoneRefs = boss.rewards
        .filter((r) => r.kind === 'item')
        .map((r) => r.ref)
        .filter((ref): ref is string => typeof ref === 'string');
      const existing = new Set(this.progress.unlockedMilestones);
      milestoneRefs.forEach((ref) => existing.add(ref));
      this.progress.unlockedMilestones = Array.from(existing);
      persist(this.progress);
      return boss;
    },
    /**
     * 达标后「跳过进阶」直接进入下一章。
     * 进阶（Boss / 进阶难题 / 工坊）是可选的，不阻塞章节推进；本动作只前移章节指针，
     * 不标记 boss 已击败（boss 仍可日后回来挑战）。仅在已达标时由 Hub 调用。
     */
    advanceChapter(): boolean {
      const ch = this.progress.currentChapter;
      if (ch >= 7) return false;
      if (!this.chapterStandardMet(ch)) return false;
      this.progress.currentChapter = ch + 1;
      this.progress.currentSubQuest = 1;
      persist(this.progress);
      return true;
    },
    recordAiProblem(entry: RecentAiProblemEntry): void {
      const next = [entry, ...this.progress.recentAiProblems].slice(0, MAX_AI_LOG);
      this.progress.recentAiProblems = next;
      persist(this.progress);
    },

    // ─────────────────── evidence 自动检测链路 ───────────────────
    // 5 类 onXxx 钩子分别由 dialog store / learning store / boss session / world scene 调用，
    // 自动遍历未完成的主线任务找 evidence 匹配项并 markComplete。

    /** dialog store close 时调：找 evidenceRequired.type==='dialog_complete' 且 npcKey 匹配的子任务。 */
    onDialogClose(npcKey: string): MainSubQuest | null {
      syncEventToBackend('dialog_complete', { npcKey });
      for (const q of MAIN_QUESTS) {
        if (this.progress.completedSubQuests.includes(q.id)) continue;
        const ev = q.evidenceRequired;
        if (ev.type === 'dialog_complete' && ev.npcKey === npcKey) {
          return this.completeSubQuest(q.id);
        }
      }
      return null;
    },

    /** learning store createResource 成功时调：找 evidenceRequired.type==='resource_consumed' 匹配的子任务。 */
    onResourceCreated(resourceType: string, kcCodes: readonly string[]): MainSubQuest | null {
      syncEventToBackend('resource_consumed', { resourceType, kcCodes: [...kcCodes] });
      for (const q of MAIN_QUESTS) {
        if (this.progress.completedSubQuests.includes(q.id)) continue;
        const ev = q.evidenceRequired;
        if (ev.type !== 'resource_consumed') continue;
        if (ev.resourceType !== resourceType) continue;
        // 资源 KC 与子任务 kcCodes 任一重合即可
        if (q.kcCodes.some((c) => kcCodes.includes(c))) {
          return this.completeSubQuest(q.id);
        }
      }
      return null;
    },

    /** Arena/Endless/Boss submission 通过时调：找 evidenceRequired.type==='submission' 匹配的子任务。 */
    onSubmissionPassed(payload: { problemId?: number | string; kcCode?: string }): MainSubQuest | null {
      syncEventToBackend('submission_passed', payload);
      for (const q of MAIN_QUESTS) {
        if (this.progress.completedSubQuests.includes(q.id)) continue;
        const ev = q.evidenceRequired;
        if (ev.type !== 'submission') continue;
        // problemId=0 是 placeholder，命中任一通过即视作完成
        if (ev.problemId === 0) {
          if (!payload.kcCode || q.kcCodes.includes(payload.kcCode)) {
            return this.completeSubQuest(q.id);
          }
        } else if (typeof payload.problemId !== 'undefined' && String(ev.problemId) === String(payload.problemId)) {
          return this.completeSubQuest(q.id);
        }
      }
      return null;
    },

    /**
     * 章节学习页消费多模态讲解（看完视频 / AI 动画 / 浏览知识点图解）时调。
     * - 上报后端 media_consumed 事件（→ 画像 learning_preference 回流 + 审计）
     * - 标记本章 consumedMedia（软门槛 chapterFullyComplete 用）
     * 幂等：同章重复消费只记一次。
     */
    onMediaConsumed(payload: {
      mediaType: string;
      chapter: number;
      kcCodes?: readonly string[];
    }): void {
      syncEventToBackend('media_consumed', {
        mediaType: payload.mediaType,
        chapter: payload.chapter,
        kcCodes: payload.kcCodes ? [...payload.kcCodes] : [],
      });
      const key = `ch${payload.chapter}`;
      if (!this.progress.consumedMedia.includes(key)) {
        this.progress.consumedMedia = [...this.progress.consumedMedia, key];
        persist(this.progress);
      }
    },

    /** 学习掌握度达阈值时调：找 evidenceRequired.type==='kc_mastery' 匹配。 */
    onMasteryReached(kcCode: string, score: number): MainSubQuest | null {
      for (const q of MAIN_QUESTS) {
        if (this.progress.completedSubQuests.includes(q.id)) continue;
        const ev = q.evidenceRequired;
        if (ev.type !== 'kc_mastery') continue;
        if (ev.kcCode !== kcCode) continue;
        if (score >= ev.thresholdScore) {
          return this.completeSubQuest(q.id);
        }
      }
      return null;
    },

    /** WorldScene 玩家走到指定 tile 时调（容差 ±1 tile）。 */
    onBuildingVisited(mapKey: string, tileX: number, tileY: number): MainSubQuest | null {
      for (const q of MAIN_QUESTS) {
        if (this.progress.completedSubQuests.includes(q.id)) continue;
        const ev = q.evidenceRequired;
        if (ev.type !== 'building_visit') continue;
        if (ev.mapKey !== mapKey) continue;
        if (Math.abs(ev.tileX - tileX) <= 1 && Math.abs(ev.tileY - tileY) <= 1) {
          return this.completeSubQuest(q.id);
        }
      }
      return null;
    },

    /** 测试 / QA：彻底清空进度。 */
    resetProgress(): void {
      this.progress = mainQuestEmptyProgress();
      persist(this.progress);
    },
    /** HUD 触发：把当前章 boss 挂上待跳转标记，BossPage 启动时清。 */
    queueBoss(chapter: number): void {
      this.pendingBossChapter = chapter;
    },
    consumePendingBoss(): number | null {
      const next = this.pendingBossChapter;
      this.pendingBossChapter = null;
      return next;
    },
  },
});
