// R-15 主线（mainline）Pinia store。
//
// 后端权威：本 store 仅缓存 `GET /api/mainline/overview` 的快照 + 维护「当前选中章节」
// 这类纯 UI 状态，不在前端做星级公式 / 解锁 / 补星判定（全部由 backend 决定，见
// docs/plans/r15-mainline-rebuild-plan.md §5/§9）。打点写接口返回该 stage 最新进度后，
// 就地 patch overview；补星类接口（无尽 / 错题）发放后直接 reload overview 取权威星级，
// 避免本地推算与后端漂移。

import { defineStore } from 'pinia';
import {
  getMainlineOverview,
  postEndlessSummary,
  postExampleDone,
  postPageRead,
  postRecoveryErrorbook,
  postVideoProgress,
  type EndlessSummaryInput,
  type MainlineChapterDto,
  type MainlineOverviewDto,
  type MainlineStageDto,
  type StageProgressDto,
} from '@api/mainline';

/** 关卡地图节点三态（锁定 / 进行中 / 已通关），ChapterMapScene 与节点列表共用。 */
export type MainlineNodeState = 'locked' | 'in-progress' | 'cleared';

/**
 * 章节节点状态（单一事实源，地图 Phaser 场景与 Vue 节点列表共用，杜绝两套判定漂移）：
 *   - 未解锁 → locked
 *   - 已解锁且 ≥1★（已通过章末测试）→ cleared
 *   - 已解锁但 0★ → in-progress
 */
export function chapterNodeState(ch: Pick<MainlineChapterDto, 'unlocked' | 'stars'>): MainlineNodeState {
  if (!ch.unlocked) return 'locked';
  return ch.stars >= 1 ? 'cleared' : 'in-progress';
}

/** 把星数夹到合法区间 0..max（默认 3），并向下取整，供徽章/场景安全渲染。 */
export function clampStars(stars: number, max = 3): number {
  if (!Number.isFinite(stars)) return 0;
  return Math.max(0, Math.min(max, Math.floor(stars)));
}

/**
 * 某 stage 三件套是否全部过线（看 + 学 + 练），用于进度条与「可考试」提示。
 *
 * 只信后端 `completed`：服务端的完成判定还包含 overview 里没有的「客观题全对」维度
 * （MainlinePracticePort），本地用 video/pages/examples 三个计数推导会出现
 * 「显示 ✓ 但 2★ 永远不发」的假完成。overview 与打点回包的 completed 均为服务端实时计算，
 * 无滞后，不需要本地兜底推导。
 */
export function isStageComplete(stage: MainlineStageDto): boolean {
  return stage.completed;
}

/** 本章 stage 完成数 / 总数（地图与章节面板共用）。 */
export function chapterStageProgress(ch: MainlineChapterDto): { done: number; total: number } {
  const total = ch.stages.length;
  const done = ch.stages.filter((s) => isStageComplete(s)).length;
  return { done, total };
}

interface MainlineState {
  overview: MainlineOverviewDto | null;
  loading: boolean;
  error: string | null;
  /** 地图上当前展开的章节（节点点击 / 默认聚焦），null = 未选。 */
  selectedChapter: number | null;
}

export const useMainlineStore = defineStore('mainline', {
  state: (): MainlineState => ({
    overview: null,
    loading: false,
    error: null,
    selectedChapter: null,
  }),

  getters: {
    chapters(state): MainlineChapterDto[] {
      return state.overview?.chapters ?? [];
    },
    totalStars(state): number {
      return state.overview?.totalStars ?? 0;
    },
    isLoaded(state): boolean {
      return state.overview !== null;
    },
    chapterByNumber() {
      return (chapter: number): MainlineChapterDto | null =>
        this.chapters.find((c) => c.chapter === chapter) ?? null;
    },
    selectedChapterView(): MainlineChapterDto | null {
      if (this.selectedChapter === null) return null;
      return this.chapterByNumber(this.selectedChapter);
    },
    nodeStateOf() {
      return (chapter: number): MainlineNodeState => {
        const ch = this.chapterByNumber(chapter);
        return ch ? chapterNodeState(ch) : 'locked';
      };
    },
    /** 第一个可操作（已解锁）章节，地图默认聚焦它；全锁定时回退第 1 章。 */
    firstActionableChapter(): number {
      const unlocked = this.chapters.filter((c) => c.unlocked);
      if (unlocked.length === 0) return this.chapters[0]?.chapter ?? 1;
      // 优先未满星的已解锁章；都满星则取最后一个已解锁章。
      const inProgress = unlocked.find((c) => c.stars < 3);
      return (inProgress ?? unlocked[unlocked.length - 1])!.chapter;
    },
  },

  actions: {
    /** 拉取地图总览。force=false 且已有数据时直接复用（避免地图重复闪烁）。 */
    async load(force = false): Promise<void> {
      if (!force && this.overview) return;
      this.loading = true;
      this.error = null;
      try {
        this.overview = await getMainlineOverview();
        if (this.selectedChapter === null) {
          this.selectedChapter = this.firstActionableChapter;
        }
      } catch (error) {
        this.error = error instanceof Error ? error.message : String(error);
      } finally {
        this.loading = false;
      }
    },

    /** 强制重新拉取（补星 / 考试提交后取权威星级）。 */
    async reload(): Promise<void> {
      await this.load(true);
    },

    selectChapter(chapter: number | null): void {
      this.selectedChapter = chapter;
    },

    /**
     * 把某 stage 的打点返回就地合并进 overview（不动星级/解锁等后端权威字段）。
     *
     * ⚠️ 本方法与下面三个 `report*` 目前**没有调用方**：三件套子组件
     * （StageVideoWatcher / StageCoursewareReader / StageExamplePractice）直接调 `@api/mainline`
     * 打点、并刻意「不读返回体」，于是 overview 缓存不会被更新——学生完成小节后若不经
     * `/mainline-map` 回主界面，HUD「下一步」仍指向已完成的小节。修复需要让那三个组件改走
     * 本 store（组件不在本次改动范围内，见交付报告的跨模块待办），因此这里保留合并入口。
     */
    patchStage(progress: StageProgressDto): void {
      if (!this.overview) return;
      const chapters = this.overview.chapters.map((ch) => {
        if (ch.chapter !== progress.chapter) return ch;
        const stages = ch.stages.map((s) => {
          if (s.id !== progress.stageId) return s;
          return {
            ...s,
            videoWatchedSec: progress.videoWatchedSec,
            requiredVideoSec: progress.requiredVideoSec,
            pagesRead: progress.pagesRead,
            requiredPages: progress.requiredPages,
            examplesDone: progress.examplesDone,
            requiredExamples: progress.requiredExamples,
            completed: progress.completed,
          };
        });
        return { ...ch, stages };
      });
      this.overview = { ...this.overview, chapters };
    },

    async reportVideoProgress(stageId: number, deltaSec: number): Promise<StageProgressDto> {
      const progress = await postVideoProgress(stageId, deltaSec);
      this.patchStage(progress);
      return progress;
    },

    async reportPageRead(stageId: number, pageId: number): Promise<StageProgressDto> {
      const progress = await postPageRead(stageId, pageId);
      this.patchStage(progress);
      return progress;
    },

    async reportExampleDone(
      stageId: number,
      problemId: number,
      submissionId?: number,
    ): Promise<StageProgressDto> {
      const progress = await postExampleDone(stageId, problemId, submissionId);
      this.patchStage(progress);
      return progress;
    },

    /** 远征 run 补星申请；发放补星则 reload 取权威星级。返回是否补星。 */
    async submitEndlessSummary(input: EndlessSummaryInput): Promise<boolean> {
      const result = await postEndlessSummary(input);
      if (result.starAwarded) {
        await this.reload();
      }
      return result.starAwarded;
    },

    /** 申领错题复盘补星；发放则 reload。返回是否补星。 */
    async claimErrorbookRecovery(chapter: number): Promise<boolean> {
      const result = await postRecoveryErrorbook(chapter);
      if (result.starAwarded) {
        await this.reload();
      }
      return result.starAwarded;
    },

    /** 测试 / QA：清空缓存。 */
    reset(): void {
      this.overview = null;
      this.loading = false;
      this.error = null;
      this.selectedChapter = null;
    },
  },
});
