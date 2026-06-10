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
    /**
     * 单一事实源：当前应聚焦的子任务 = 「当前章第一个未完成子任务」。
     * 全章 5 任务都完成时返回 null（=「全章完成 → 挑战 boss」兜底，见 bossPending）。
     * 左侧学习目标面板、HUD 进度条、地图标记、ProbeCard 全部消费此 getter，
     * 杜绝旧实现「指引用状态扫描、地图/进度条用指针」两套逻辑乱序时指向不同任务。
     */
    activeQuest(): MainSubQuest | null {
      const chapter = this.progress.currentChapter;
      if (chapter < 1 || chapter > 7) return null;
      const done = new Set(this.progress.completedSubQuests);
      return listSubQuestsByChapter(chapter).find((s) => !done.has(s.id)) ?? null;
    },
    /** 本章 5 任务全部完成、但章末 boss 未击败 → 该挑战 boss（activeQuest 为 null 时的兜底语义）。 */
    bossPending(): boolean {
      const chapter = this.progress.currentChapter;
      if (chapter < 1 || chapter > 7) return false;
      const done = new Set(this.progress.completedSubQuests);
      return (
        listSubQuestsByChapter(chapter).every((s) => done.has(s.id))
        && !this.progress.defeatedBosses.includes(chapter)
      );
    },
    /** `activeQuest` 的兼容别名（旧组件 / 测试按此名读取当前子任务）。 */
    currentSubQuest(): MainSubQuest | null {
      return this.activeQuest;
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
      const cur = this.progress.currentChapter;
      const done = new Set(this.progress.completedSubQuests);
      // 状态判定与 activeQuest 同源：当前章焦点 = activeQuest，其之前可做、之后锁定。
      const active = this.activeQuest;
      const activeId = active?.id ?? null;
      const activeIndex = active?.index ?? Number.POSITIVE_INFINITY;
      for (let chapter = 1; chapter <= 7; chapter += 1) {
        const subs = listSubQuestsByChapter(chapter);
        const items: MainSubQuestViewItem[] = subs.map((s) => {
          let status: MainSubQuestViewItem['status'] = 'locked';
          if (done.has(s.id)) {
            status = 'completed';
          } else if (chapter < cur) {
            // 已通关旧章里残留的未完成项（理论上罕见）→ 可回头补做。
            status = 'available';
          } else if (chapter === cur) {
            if (s.id === activeId) status = 'in_progress';
            else if (s.index < activeIndex) status = 'available';
            else status = 'locked';
          }
          return { ...s, status };
        });
        const completed = items.filter((s) => s.status === 'completed').length;
        const boss = MAIN_QUEST_BOSSES.find((b) => b.chapter === chapter);
        if (!boss) continue;
        // 命名统一：章节标题用 chapters.ts 章节名（非 boss 名）；boss 名只在 boss 区域出现。
        const chapterName = CHAPTERS.find((c) => c.order === chapter)?.name ?? boss.name;
        chapters.push({
          chapter,
          title: chapterName,
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
     * 本章「测」锚点：来自 kc_mastery 子任务的 { kcCode, target(0..1) }。
     * 每个可测章（2–7）只锚定 1 个有判题真题的 KC（与「练」同一 KC，练完即测）；
     * 第 1 章为理论导入章，无锚点（返回空数组）。
     */
    chapterMasteryAnchors() {
      return (chapter: number): { kcCode: string; target: number }[] => {
        const out: { kcCode: string; target: number }[] = [];
        for (const q of MAIN_QUESTS) {
          if (q.chapter !== chapter) continue;
          if (q.evidenceRequired.type !== 'kc_mastery') continue;
          out.push({
            kcCode: q.evidenceRequired.kcCode,
            target: q.evidenceRequired.thresholdScore / 100,
          });
        }
        return out;
      };
    },
    /**
     * 本章锚点 KC 平均掌握度（0..1）。分母改为「本章主线锚点 KC」（而非全章 KC）：
     * 旧实现用全章 KC 做分母，但每章只刷 1 个锚点 KC，平均永远到不了 0.7，
     * 导致「测·70%」恒红、advanceChapter 基本失效。无锚点（第 1 章理论章）→ 1（视为无需测）。
     * 掌握度读 learning store 的 masterySnapshot（GET /api/learner/mastery / bootstrap）。
     */
    chapterMasteryAvg() {
      return (chapter: number): number => {
        const anchors = this.chapterMasteryAnchors(chapter);
        if (anchors.length === 0) return 1;
        const snaps = useLearningStore().field.masterySnapshot;
        const sum = anchors.reduce((acc, a) => {
          const snap = snaps.find((s) => s.kcCode === a.kcCode);
          return acc + (snap?.masteryScore ?? 0);
        }, 0);
        return sum / anchors.length;
      };
    },
    /** 本章「测」达标目标（0..1）：取锚点阈值最大值；无锚点（第 1 章）→ 0。 */
    chapterMasteryTarget() {
      return (chapter: number): number => {
        const anchors = this.chapterMasteryAnchors(chapter);
        if (anchors.length === 0) return 0;
        return Math.max(...anchors.map((a) => a.target));
      };
    },
    /**
     * 章节达标：本章「必做题」（submission）全过 且 锚点 KC 平均掌握度 ≥ 锚点阈值。
     * 第 1 章（无 submission、无锚点的纯理论章）以「5 任务全完成」为达标，
     * 避免被判恒达标后被空跳过。达标后解锁进阶 Hub（Boss / 进阶难题 / 工坊），也可跳过进下一章。
     */
    chapterStandardMet() {
      return (chapter: number): boolean => {
        const required = MAIN_QUESTS.filter(
          (q) => q.chapter === chapter && q.evidenceRequired.type === 'submission',
        );
        const anchors = this.chapterMasteryAnchors(chapter);
        if (required.length === 0 && anchors.length === 0) {
          return listSubQuestsByChapter(chapter).every((q) =>
            this.progress.completedSubQuests.includes(q.id),
          );
        }
        const allRequiredDone = required.every((q) =>
          this.progress.completedSubQuests.includes(q.id),
        );
        return (
          allRequiredDone
          && this.chapterMasteryAvg(chapter) >= this.chapterMasteryTarget(chapter)
        );
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

    // ─────────────────── evidence 自动检测链路（单一事实源） ───────────────────
    // 5 类 onXxx 钩子只允许命中「当前 activeQuest」（=当前章第一个未完成子任务）：
    //   - 杜绝跨章预完成：旧实现遍历全部 35 任务命中即完成，使钩子在 bootstrap/刷新时
    //     批量 onMasteryReached 跨章乱序完成、整体进度虚高、与指针/左侧脱节；
    //   - 杜绝章内乱序跳跃：只推进当前焦点任务，指引=进度=地图标记三者天然一致。
    // 全章完成（activeQuest 为 null，boss 待打）时一律不再前移，等 defeatBoss 推进章节。

    /** dialog store close 时调：当前 activeQuest 为 dialog_complete 且 npcKey 匹配 → 完成。 */
    onDialogClose(npcKey: string): MainSubQuest | null {
      syncEventToBackend('dialog_complete', { npcKey });
      const q = this.activeQuest;
      if (!q) return null;
      const ev = q.evidenceRequired;
      if (ev.type === 'dialog_complete' && ev.npcKey === npcKey) {
        return this.completeSubQuest(q.id);
      }
      return null;
    },

    /** learning store createResource 成功时调：当前 activeQuest 为 resource_consumed 且类型 / KC 匹配 → 完成。 */
    onResourceCreated(resourceType: string, kcCodes: readonly string[]): MainSubQuest | null {
      syncEventToBackend('resource_consumed', { resourceType, kcCodes: [...kcCodes] });
      const q = this.activeQuest;
      if (!q) return null;
      const ev = q.evidenceRequired;
      if (ev.type !== 'resource_consumed') return null;
      if (ev.resourceType !== resourceType) return null;
      // 资源 KC 与子任务 kcCodes 任一重合即可。
      if (!q.kcCodes.some((c) => kcCodes.includes(c))) return null;
      return this.completeSubQuest(q.id);
    },

    /** Arena/Endless/Boss/训练 submission 通过时调：当前 activeQuest 为 submission 且 problemId / KC 匹配 → 完成。 */
    onSubmissionPassed(payload: { problemId?: number | string; kcCode?: string }): MainSubQuest | null {
      syncEventToBackend('submission_passed', payload);
      const q = this.activeQuest;
      if (!q) return null;
      const ev = q.evidenceRequired;
      if (ev.type !== 'submission') return null;
      // problemId=0 是 placeholder，命中任一通过即视作完成（带 KC 时更精确）。
      if (ev.problemId === 0) {
        if (!payload.kcCode || q.kcCodes.includes(payload.kcCode)) {
          return this.completeSubQuest(q.id);
        }
        return null;
      }
      if (
        typeof payload.problemId !== 'undefined'
        && String(ev.problemId) === String(payload.problemId)
      ) {
        return this.completeSubQuest(q.id);
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

    /** 掌握度达阈值时调：当前 activeQuest 为 kc_mastery 且 KC / 阈值匹配 → 完成。 */
    onMasteryReached(kcCode: string, score: number): MainSubQuest | null {
      const q = this.activeQuest;
      if (!q) return null;
      const ev = q.evidenceRequired;
      if (ev.type !== 'kc_mastery') return null;
      if (ev.kcCode !== kcCode) return null;
      if (score < ev.thresholdScore) return null;
      return this.completeSubQuest(q.id);
    },

    /** WorldScene 玩家走到指定 tile 时调（容差 ±1 tile）：当前 activeQuest 为 building_visit 且到点 → 完成。 */
    onBuildingVisited(mapKey: string, tileX: number, tileY: number): MainSubQuest | null {
      const q = this.activeQuest;
      if (!q) return null;
      const ev = q.evidenceRequired;
      if (ev.type !== 'building_visit') return null;
      if (ev.mapKey !== mapKey) return null;
      if (Math.abs(ev.tileX - tileX) <= 1 && Math.abs(ev.tileY - tileY) <= 1) {
        return this.completeSubQuest(q.id);
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
