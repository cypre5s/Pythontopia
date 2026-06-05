import { getMyClassroomQuests, getMyClassrooms, joinClassroom } from '../../api/classroom';
import { Classroom, ClassroomQuest } from '../../api/dto/classroom';
import { isLoggedIn } from '../../store/session';
import { ApiError } from '../../utils/request';

interface ClassQuestVM {
  id: number;
  title: string;
  description: string;
  kcText: string;
  statusLabel: string;
  firstProblemId: number | null;
}

interface ClassroomData {
  loading: boolean;
  errorMsg: string;
  classrooms: Classroom[];
  quests: ClassQuestVM[];
  joinCode: string;
  joining: boolean;
}

function toClassQuestVM(quest: ClassroomQuest): ClassQuestVM {
  const problems = quest.targetProblemIds ?? [];
  return {
    id: quest.id,
    title: quest.title,
    description: quest.description ?? '',
    kcText: quest.kcCodes.join('、'),
    statusLabel: quest.studentQuestStatus ?? quest.status,
    firstProblemId: problems.length > 0 ? problems[0] : null,
  };
}

Page({
  data: {
    loading: false,
    errorMsg: '',
    classrooms: [],
    quests: [],
    joinCode: '',
    joining: false,
  } as ClassroomData,

  onShow() {
    if (!isLoggedIn()) {
      wx.reLaunch({ url: '/pages/login/login' });
      return;
    }
    this.load();
  },

  onPullDownRefresh() {
    this.load().then(() => wx.stopPullDownRefresh());
  },

  async load() {
    this.setData({ loading: true, errorMsg: '' });
    const [roomsRes, questsRes] = await Promise.allSettled([
      getMyClassrooms(),
      getMyClassroomQuests(),
    ]);
    const patch: Partial<ClassroomData> = { loading: false };
    if (roomsRes.status === 'fulfilled') {
      patch.classrooms = roomsRes.value;
    } else {
      const reason: unknown = roomsRes.reason;
      patch.errorMsg = reason instanceof ApiError ? reason.message : '班级加载失败';
    }
    if (questsRes.status === 'fulfilled') {
      patch.quests = questsRes.value.map(toClassQuestVM);
    }
    this.setData(patch);
  },

  onJoinInput(e: WechatMiniprogram.CustomEvent<{ value: string }>) {
    this.setData({ joinCode: e.detail.value.toUpperCase() });
  },

  async onJoin() {
    const joinCode = this.data.joinCode.trim();
    if (!/^[A-Z0-9]{6}$/.test(joinCode)) {
      wx.showToast({ title: '邀请码为 6 位大写字母/数字', icon: 'none' });
      return;
    }
    this.setData({ joining: true });
    try {
      const room = await joinClassroom({ joinCode });
      wx.showToast({ title: `已加入 ${room.name}`, icon: 'success' });
      this.setData({ joinCode: '' });
      await this.load();
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : '加入失败';
      wx.showToast({ title: msg, icon: 'none' });
    } finally {
      this.setData({ joining: false });
    }
  },

  onGoPractice(e: WechatMiniprogram.CustomEvent) {
    const ds = e.currentTarget.dataset as { problemId?: string | number };
    const problemId = Number(ds.problemId);
    if (!problemId) {
      return;
    }
    wx.navigateTo({ url: `/pages/practice/detail?problemId=${problemId}` });
  },
});
