// R-13 §5 Phase 2：主线进度 Pinia store。
//
// 持久化策略：本会话不做 V120 migration（按 §3 计划），先用 localStorage 把 progress
// 序列化下来，跨刷新仍生效。后端 SaveSlot.snapshot JSONB 接入留 R-14（届时改 save 钩子）。

import { defineStore } from 'pinia';
import {
  MAIN_QUESTS,
  MAIN_QUEST_BOSSES,
  findMainQuestById,
  listSubQuestsByChapter,
} from '@/data/mainQuests';
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
    };
  } catch {
    return null;
  }
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
    /* private mode */
  }
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
          this.progress = parsed;
          persist(parsed);
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
      // 推进当前指针
      if (this.progress.currentChapter === quest.chapter) {
        if (this.progress.currentSubQuest === quest.index) {
          if (quest.index < 5) {
            this.progress.currentSubQuest = quest.index + 1;
          } else {
            this.progress.currentSubQuest = 5;
          }
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
      if (chapter < 7) {
        this.progress.currentChapter = chapter + 1;
        this.progress.currentSubQuest = 1;
      } else {
        this.progress.currentChapter = 7;
        this.progress.currentSubQuest = 5;
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
    recordAiProblem(entry: RecentAiProblemEntry): void {
      const next = [entry, ...this.progress.recentAiProblems].slice(0, MAX_AI_LOG);
      this.progress.recentAiProblems = next;
      persist(this.progress);
    },

    // ─────────────────── R-13 §-1 critical fix：evidence 自动检测链路 ───────────────────
    // 此前 completeSubQuest() 无任何外部调用，35 个主线任务的 evidence 触发链路全断 —
    // 玩家与 NPC 对话、用工坊生成资源、提交代码后任务永远卡住。
    // 5 类 onXxx 钩子分别由 dialog store / learning store / boss session / world scene 调用，
    // 自动遍历未完成的主线任务找 evidence 匹配项并 markComplete。

    /** dialog store close 时调：找 evidenceRequired.type==='dialog_complete' 且 npcKey 匹配的子任务。 */
    onDialogClose(npcKey: string): MainSubQuest | null {
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
      for (const q of MAIN_QUESTS) {
        if (this.progress.completedSubQuests.includes(q.id)) continue;
        const ev = q.evidenceRequired;
        if (ev.type !== 'submission') continue;
        // problemId=0 是 placeholder，命中任一通过即视作完成（早期演示阶段宽松）
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
