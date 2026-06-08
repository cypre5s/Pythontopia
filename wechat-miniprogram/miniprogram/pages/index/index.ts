import { getMastery } from '../../api/learner';
import { completeQuest, getTodayQuests, skipQuest, startQuest } from '../../api/quests';
import { getMyClassroomQuests } from '../../api/classroom';
import { ClassroomQuest } from '../../api/dto/classroom';
import { MasterySnapshot } from '../../api/dto/learner';
import { Quest, QuestStatus } from '../../api/dto/quests';
import { isLoggedIn, getUser } from '../../store/session';
import { ApiError } from '../../utils/request';
import { percentWidth, toPercent } from '../../utils/format';
import { MINE_ROUTE } from '../../config';

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
  todayQuests: QuestVM[];
  classQuests: ClassQuestVM[];
  mastery: MasteryVM[];
  masteryAvg: string;
  masteryCount: number;
  masteredCount: number;
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
    const [todayRes, classRes, masteryRes] = await Promise.allSettled([
      getTodayQuests(),
      getMyClassroomQuests(),
      getMastery(),
    ]);

    const patch: Partial<IndexData> = { loading: false };
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

    const failed = [todayRes, classRes, masteryRes].find((r) => r.status === 'rejected');
    if (failed && failed.status === 'rejected') {
      const reason: unknown = failed.reason;
      patch.errorMsg = reason instanceof ApiError ? reason.message : '部分内容加载失败';
    }
    this.setData(patch);
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
