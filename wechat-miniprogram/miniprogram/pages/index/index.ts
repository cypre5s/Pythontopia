// 学习首页 = 主线冒险地图 hub（R-15，镜像 web MainlineMapPage + 公告板）。
//
// 一屏聚合：主线章节冒险地图（星级 / 解锁 / 小节三件套进度 → 跳小节学习页 / 章末测试）、
// 今日任务、掌握度概览、Bug 矿洞入口、班级任务。主线进度后端权威，本页只读渲染 + 入口跳转。

import { getMainlineOverview } from '../../api/mainline';
import { getMastery } from '../../api/learner';
import { completeQuest, getTodayQuests, skipQuest, startQuest } from '../../api/quests';
import { getMyClassroomQuests } from '../../api/classroom';
import { MainlineChapterDto, MainlineStageDto, ChapterTestStatus } from '../../api/dto/mainline';
import { ClassroomQuest } from '../../api/dto/classroom';
import { MasterySnapshot } from '../../api/dto/learner';
import { Quest, QuestStatus } from '../../api/dto/quests';
import { isLoggedIn, getUser } from '../../store/session';
import { ApiError } from '../../utils/request';
import { percentWidth, toPercent } from '../../utils/format';
import { MINE_ROUTE, STAGE_ROUTE, TEST_ROUTE } from '../../config';

const STATUS_LABEL: Record<QuestStatus, string> = {
  OPEN: '待开始',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  SKIPPED: '已跳过',
  EXPIRED: '已过期',
};

const STATUS_CLASS: Record<QuestStatus, string> = {
  OPEN: 'badge--open',
  IN_PROGRESS: 'badge--progress',
  COMPLETED: 'badge--done',
  SKIPPED: 'badge--skip',
  EXPIRED: 'badge--fail',
};

const TEST_STATUS_LABEL: Record<ChapterTestStatus, string> = {
  NONE: '未测试',
  PENDING: '生成中',
  GENERATING: '生成中',
  READY: '可开考',
  IN_PROGRESS: '进行中',
  SUBMITTED: '已交卷',
  EXPIRED: '已过期',
  FAILED: '生成失败',
};

interface StageRowVM {
  id: number;
  indexNo: number;
  title: string;
  completed: boolean;
  progress: string;
}

interface ChapterVM {
  chapter: number;
  title: string;
  unlocked: boolean;
  stars: number;
  starsText: string;
  bestText: string;
  testLabel: string;
  stageDoneText: string;
  expanded: boolean;
  stages: StageRowVM[];
}

interface QuestVM {
  id: number;
  title: string;
  description: string;
  kcCode: string;
  statusLabel: string;
  statusClass: string;
  canStart: boolean;
  canComplete: boolean;
  canSkip: boolean;
  firstProblemId: number | null;
}

interface ClassQuestVM {
  id: number;
  title: string;
  description: string;
  kcText: string;
  statusLabel: string;
  firstProblemId: number | null;
}

interface MasteryVM {
  kcCode: string;
  percent: string;
  width: string;
}

interface IndexData {
  greeting: string;
  loading: boolean;
  errorMsg: string;
  totalStars: number;
  chapters: ChapterVM[];
  todayQuests: QuestVM[];
  classQuests: ClassQuestVM[];
  mastery: MasteryVM[];
  masteryAvg: string;
  masteryCount: number;
  masteredCount: number;
}

function starsText(stars: number): string {
  const n = Math.max(0, Math.min(3, stars));
  return '★'.repeat(n) + '☆'.repeat(3 - n);
}

function stageProgress(s: MainlineStageDto): string {
  const watch = s.requiredVideoSec > 0 && s.videoWatchedSec >= s.requiredVideoSec ? '看✓' : '看·';
  const read = s.requiredPages === 0 || s.pagesRead >= s.requiredPages ? '学✓' : `学${s.pagesRead}/${s.requiredPages}`;
  const ex = s.requiredExamples === 0 || s.examplesDone >= s.requiredExamples ? '练✓' : `练${s.examplesDone}/${s.requiredExamples}`;
  return `${watch} · ${read} · ${ex}`;
}

function toChapterVM(c: MainlineChapterDto): ChapterVM {
  const stages: StageRowVM[] = c.stages.map((s) => ({
    id: s.id,
    indexNo: s.index,
    title: s.title,
    completed: s.completed,
    progress: stageProgress(s),
  }));
  const doneCount = stages.filter((s) => s.completed).length;
  return {
    chapter: c.chapter,
    title: c.title,
    unlocked: c.unlocked,
    stars: c.stars,
    starsText: starsText(c.stars),
    bestText: c.bestTestScore === null ? '未测试' : `测试最高 ${c.bestTestScore}`,
    testLabel: TEST_STATUS_LABEL[c.testStatus],
    stageDoneText: `${doneCount}/${stages.length} 小节`,
    expanded: false,
    stages,
  };
}

function toQuestVM(quest: Quest): QuestVM {
  const problems = quest.targetProblemIds ?? [];
  return {
    id: quest.id,
    title: quest.title,
    description: quest.description ?? '',
    kcCode: quest.kcCode ?? '',
    statusLabel: STATUS_LABEL[quest.status],
    statusClass: STATUS_CLASS[quest.status],
    canStart: quest.status === 'OPEN',
    canComplete: quest.status === 'IN_PROGRESS',
    canSkip: quest.status === 'OPEN' || quest.status === 'IN_PROGRESS',
    firstProblemId: problems.length > 0 ? problems[0] : null,
  };
}

function toClassQuestVM(quest: ClassroomQuest): ClassQuestVM {
  const problems = quest.targetProblemIds ?? [];
  const status = quest.studentQuestStatus ?? quest.status;
  return {
    id: quest.id,
    title: quest.title,
    description: quest.description ?? '',
    kcText: quest.kcCodes.join('、'),
    statusLabel: status,
    firstProblemId: problems.length > 0 ? problems[0] : null,
  };
}

function summarizeMastery(snapshots: MasterySnapshot[]): {
  mastery: MasteryVM[];
  masteryAvg: string;
  masteryCount: number;
  masteredCount: number;
} {
  if (snapshots.length === 0) {
    return { mastery: [], masteryAvg: '0%', masteryCount: 0, masteredCount: 0 };
  }
  const sorted = [...snapshots].sort((a, b) => b.masteryScore - a.masteryScore);
  const top = sorted.slice(0, 6).map((s) => ({
    kcCode: s.kcCode,
    percent: toPercent(s.masteryScore),
    width: percentWidth(s.masteryScore),
  }));
  const sum = snapshots.reduce((acc, s) => acc + s.masteryScore, 0);
  const mastered = snapshots.filter((s) => s.masteryScore >= 0.8).length;
  return {
    mastery: top,
    masteryAvg: toPercent(sum / snapshots.length),
    masteryCount: snapshots.length,
    masteredCount: mastered,
  };
}

Page({
  data: {
    greeting: '',
    loading: false,
    errorMsg: '',
    totalStars: 0,
    chapters: [],
    todayQuests: [],
    classQuests: [],
    mastery: [],
    masteryAvg: '0%',
    masteryCount: 0,
    masteredCount: 0,
  } as IndexData,

  onShow() {
    if (!isLoggedIn()) {
      wx.reLaunch({ url: '/pages/login/login' });
      return;
    }
    const user = getUser();
    this.setData({ greeting: user ? `欢迎回来，${user.displayName}` : '欢迎回来' });
    this.loadAll();
  },

  onPullDownRefresh() {
    this.loadAll().then(() => wx.stopPullDownRefresh());
  },

  async loadAll() {
    this.setData({ loading: true, errorMsg: '' });
    const [overviewRes, todayRes, classRes, masteryRes] = await Promise.allSettled([
      getMainlineOverview(),
      getTodayQuests(),
      getMyClassroomQuests(),
      getMastery(),
    ]);

    const patch: Partial<IndexData> = { loading: false };
    if (overviewRes.status === 'fulfilled') {
      patch.totalStars = overviewRes.value.totalStars;
      patch.chapters = overviewRes.value.chapters.map(toChapterVM);
    }
    if (todayRes.status === 'fulfilled') {
      patch.todayQuests = todayRes.value.map(toQuestVM);
    }
    if (classRes.status === 'fulfilled') {
      patch.classQuests = classRes.value.map(toClassQuestVM);
    }
    if (masteryRes.status === 'fulfilled') {
      const summary = summarizeMastery(masteryRes.value);
      patch.mastery = summary.mastery;
      patch.masteryAvg = summary.masteryAvg;
      patch.masteryCount = summary.masteryCount;
      patch.masteredCount = summary.masteredCount;
    }

    const failed = [overviewRes, todayRes, classRes, masteryRes].find((r) => r.status === 'rejected');
    if (failed && failed.status === 'rejected') {
      const reason: unknown = failed.reason;
      patch.errorMsg = reason instanceof ApiError ? reason.message : '部分内容加载失败';
    }
    this.setData(patch);
  },

  onToggleChapter(e: WechatMiniprogram.CustomEvent) {
    const dataset = e.currentTarget.dataset as { idx?: string | number };
    const idx = Number(dataset.idx);
    const chapters = this.data.chapters.slice();
    const cur = chapters[idx];
    if (!cur || !cur.unlocked) {
      return;
    }
    chapters[idx] = { ...cur, expanded: !cur.expanded };
    this.setData({ chapters });
  },

  onGoStage(e: WechatMiniprogram.CustomEvent) {
    const dataset = e.currentTarget.dataset as { id?: string | number };
    const stageId = Number(dataset.id);
    if (!stageId) {
      return;
    }
    wx.navigateTo({ url: `${STAGE_ROUTE}?stageId=${stageId}` });
  },

  onGoTest(e: WechatMiniprogram.CustomEvent) {
    const dataset = e.currentTarget.dataset as { chapter?: string | number };
    const chapter = Number(dataset.chapter);
    if (!chapter) {
      return;
    }
    wx.navigateTo({ url: `${TEST_ROUTE}?chapter=${chapter}` });
  },

  questIdFrom(e: WechatMiniprogram.CustomEvent): number {
    const dataset = e.currentTarget.dataset as { id?: string | number };
    return Number(dataset.id);
  },

  async onStart(e: WechatMiniprogram.CustomEvent) {
    await this.runAction(() => startQuest(this.questIdFrom(e)), '已开始');
  },

  async onComplete(e: WechatMiniprogram.CustomEvent) {
    await this.runAction(() => completeQuest(this.questIdFrom(e)), '已完成');
  },

  async onSkip(e: WechatMiniprogram.CustomEvent) {
    await this.runAction(() => skipQuest(this.questIdFrom(e)), '已跳过');
  },

  async runAction(action: () => Promise<unknown>, okText: string) {
    try {
      await action();
      wx.showToast({ title: okText, icon: 'success' });
      await this.loadAll();
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : '操作失败';
      wx.showToast({ title: msg, icon: 'none' });
    }
  },

  onGoPractice(e: WechatMiniprogram.CustomEvent) {
    const dataset = e.currentTarget.dataset as { problemId?: string | number; questId?: string | number };
    const problemId = Number(dataset.problemId);
    if (!problemId) {
      return;
    }
    const questId = dataset.questId ? Number(dataset.questId) : null;
    const query = questId ? `?problemId=${problemId}&questId=${questId}` : `?problemId=${problemId}`;
    wx.navigateTo({ url: `/pages/practice/detail${query}` });
  },

  goMine() {
    wx.navigateTo({ url: MINE_ROUTE });
  },
});
