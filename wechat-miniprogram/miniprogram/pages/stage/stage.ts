// 主线小节学习页（看 / 学 / 练）—— R-15 核心闭环（镜像 web frontend StageLearnPage）。
//
// 看：内置课程视频，<video> 播放并按节流上报观看时长（postVideoProgress，后端限频）。
// 学：课件页图片逐页阅读，标记已读（postPageRead / 批量 postPagesRead）。
// 练：站内客观题（CHOICE/JUDGE/FILL，answerPracticeQuestion）+ OJ 例题（跳训练场判题）。
//
// 所有进度/星级判定均后端权威，本页只展示进度并打点。

import {
  answerPracticeQuestion,
  getStageDetail,
  postPageRead,
  postPagesRead,
  postVideoProgress,
} from '../../api/mainline';
import { pageImageUrl, videoStreamUrl } from '../../api/course';
import { StageDetailDto, StagePracticeQuestionDto, StageProgressDto } from '../../api/dto/mainline';
import { ApiError } from '../../utils/request';
import { percentWidth } from '../../utils/format';

const TYPE_LABEL: Record<StagePracticeQuestionDto['type'], string> = {
  CHOICE: '单选题',
  JUDGE: '判断题',
  FILL: '填空题',
};

function optionLetter(index: number): string {
  return String.fromCharCode(65 + index);
}

interface VideoVM {
  id: number;
  title: string;
  durationLabel: string;
  url: string;
}

interface PageVM {
  pageId: number;
  imageUrl: string;
  read: boolean;
}

interface OptionVM {
  letter: string;
  text: string;
  cls: string;
}

interface BlankVM {
  i: number;
  value: string;
}

interface QuestionVM {
  id: number;
  no: number;
  type: StagePracticeQuestionDto['type'];
  typeLabel: string;
  stem: string;
  options: OptionVM[];
  blanks: BlankVM[];
  blankCount: number;
  judgeValue: '' | 'T' | 'F';
  judgeTrueCls: string;
  judgeFalseCls: string;
  pending: boolean;
  answeredCorrect: boolean;
  showResult: boolean;
  resultOk: boolean;
  correctAnswersText: string;
  explanation: string;
  canSubmit: boolean;
}

interface ExampleVM {
  id: number;
  displayNo: string;
  displayName: string;
  kcCode: string;
  done: boolean;
}

interface StageData {
  stageId: number;
  loading: boolean;
  errorMsg: string;
  chapter: number;
  chapterTitle: string;
  title: string;
  indexNo: number;
  completed: boolean;
  // 看
  videos: VideoVM[];
  videoWatchedSec: number;
  requiredVideoSec: number;
  videoPct: string;
  videoWidth: string;
  videoDone: boolean;
  // 学
  pages: PageVM[];
  pagesReadCount: number;
  requiredPages: number;
  studyPct: string;
  studyWidth: string;
  studyDone: boolean;
  // 练（客观题）
  questions: QuestionVM[];
  questionsCorrect: number;
  requiredQuestions: number;
  practiceDone: boolean;
  // 练（OJ 例题）
  examples: ExampleVM[];
  examplesDoneCount: number;
  requiredExamples: number;
  examplesDone: boolean;
}

function durationLabel(sec: number): string {
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  return `${m}:${s < 10 ? '0' : ''}${s}`;
}

function buildOptionVMs(q: StagePracticeQuestionDto): OptionVM[] {
  if (q.type !== 'CHOICE') {
    return [];
  }
  return q.options.map((text, idx) => ({ letter: optionLetter(idx), text, cls: '' }));
}

function buildQuestionVM(q: StagePracticeQuestionDto, no: number, correct: boolean): QuestionVM {
  const blankCount = q.type === 'FILL' ? Math.max(1, q.blankCount) : 0;
  return {
    id: q.id,
    no,
    type: q.type,
    typeLabel: TYPE_LABEL[q.type],
    stem: q.stem,
    options: buildOptionVMs(q),
    blanks: Array.from({ length: blankCount }, (_unused, i) => ({ i, value: '' })),
    blankCount,
    judgeValue: '',
    judgeTrueCls: '',
    judgeFalseCls: '',
    pending: false,
    answeredCorrect: correct,
    showResult: correct,
    resultOk: correct,
    correctAnswersText: '',
    explanation: '',
    canSubmit: false,
  };
}

Page({
  reloadOnShow: false,

  data: {
    stageId: 0,
    loading: false,
    errorMsg: '',
    chapter: 0,
    chapterTitle: '',
    title: '',
    indexNo: 0,
    completed: false,
    videos: [],
    videoWatchedSec: 0,
    requiredVideoSec: 0,
    videoPct: '0%',
    videoWidth: '0%',
    videoDone: false,
    pages: [],
    pagesReadCount: 0,
    requiredPages: 0,
    studyPct: '0%',
    studyWidth: '0%',
    studyDone: false,
    questions: [],
    questionsCorrect: 0,
    requiredQuestions: 0,
    practiceDone: false,
    examples: [],
    examplesDoneCount: 0,
    requiredExamples: 0,
    examplesDone: false,
  } as StageData,

  // 视频观看时长节流上报（非 data 实例态，避免每帧 setData）。
  videoLast: {} as Record<number, number>,
  videoPending: 0,
  videoFlushTimes: [] as number[],

  onLoad(query: Record<string, string | undefined>) {
    const stageId = Number(query.stageId);
    if (!stageId) {
      this.setData({ errorMsg: '缺少小节 ID' });
      return;
    }
    this.setData({ stageId });
    this.load();
  },

  onShow() {
    // 从训练场提交 OJ 例题返回时刷新「练」进度。
    if (this.reloadOnShow) {
      this.reloadOnShow = false;
      this.load();
    }
  },

  onUnload() {
    this.flushVideo();
  },

  async load() {
    const stageId = this.data.stageId;
    this.setData({ loading: true, errorMsg: '' });
    try {
      const d = await getStageDetail(stageId);
      this.applyDetail(d);
      this.setData({ loading: false });
      wx.setNavigationBarTitle({ title: `${d.index}. ${d.title}` });
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : '小节加载失败';
      this.setData({ loading: false, errorMsg: msg });
    }
  },

  applyDetail(d: StageDetailDto) {
    const videos: VideoVM[] = (d.videos ?? []).map((v) => ({
      id: v.id,
      title: v.title,
      durationLabel: durationLabel(v.durationSec),
      url: videoStreamUrl(v.id),
    }));
    const readSet = new Set(d.pagesReadIds);
    const pages: PageVM[] = d.coursewarePageIds.map((pageId) => ({
      pageId,
      imageUrl: pageImageUrl(pageId),
      read: readSet.has(pageId),
    }));
    const correctSet = new Set(d.questionsCorrectIds ?? []);
    const sortedQuestions = [...(d.questions ?? [])].sort((a, b) => a.ordinal - b.ordinal);
    const questions: QuestionVM[] = sortedQuestions.map((q, i) =>
      buildQuestionVM(q, i + 1, correctSet.has(q.id)),
    );
    const doneSet = new Set(d.examplesDoneIds);
    const examples: ExampleVM[] = (d.examples ?? []).map((ex) => ({
      id: ex.id,
      displayNo: ex.displayNo,
      displayName: ex.displayName,
      kcCode: ex.kcCode,
      done: doneSet.has(ex.id),
    }));

    const pagesReadCount = pages.filter((p) => p.read).length;
    const questionsCorrect = questions.filter((q) => q.answeredCorrect).length;
    const requiredQuestions = d.requiredQuestions ?? questions.length;
    const examplesDoneCount = examples.filter((ex) => ex.done).length;

    this.setData({
      chapter: d.chapter,
      chapterTitle: d.chapterTitle,
      title: d.title,
      indexNo: d.index,
      completed: d.completed,
      videos,
      videoWatchedSec: d.videoWatchedSec,
      requiredVideoSec: d.requiredVideoSec,
      videoPct: this.ratio(d.videoWatchedSec, d.requiredVideoSec),
      videoWidth: this.ratioWidth(d.videoWatchedSec, d.requiredVideoSec),
      videoDone: d.requiredVideoSec > 0 && d.videoWatchedSec >= d.requiredVideoSec,
      pages,
      pagesReadCount,
      requiredPages: d.requiredPages,
      studyPct: this.ratio(pagesReadCount, d.requiredPages),
      studyWidth: this.ratioWidth(pagesReadCount, d.requiredPages),
      studyDone: d.requiredPages > 0 && pagesReadCount >= d.requiredPages,
      questions,
      questionsCorrect,
      requiredQuestions,
      practiceDone: requiredQuestions === 0 || questionsCorrect >= requiredQuestions,
      examples,
      examplesDoneCount,
      requiredExamples: d.requiredExamples,
      examplesDone: d.requiredExamples === 0 || examplesDoneCount >= d.requiredExamples,
    });
  },

  ratio(done: number, total: number): string {
    if (total <= 0) {
      return '已完成';
    }
    return `${Math.min(done, total)} / ${total}`;
  },

  ratioWidth(done: number, total: number): string {
    if (total <= 0) {
      return '100%';
    }
    return percentWidth(done / total);
  },

  // --- 看：视频时长节流上报 -------------------------------------------------

  onVideoTimeUpdate(e: WechatMiniprogram.CustomEvent<{ currentTime: number }>) {
    const dataset = e.currentTarget.dataset as { idx?: string | number };
    const idx = Number(dataset.idx);
    const t = e.detail.currentTime;
    const last = this.videoLast[idx];
    if (typeof last === 'number') {
      const delta = t - last;
      // 仅累计正向小步进（忽略快进/拖动/换视频造成的跳变）。
      if (delta > 0 && delta < 5) {
        this.videoPending += delta;
      }
    }
    this.videoLast[idx] = t;
    if (this.videoPending >= 30) {
      this.flushVideo();
    }
  },

  onVideoPause() {
    this.flushVideo();
  },

  onVideoEnded() {
    this.flushVideo();
  },

  flushVideo() {
    const pending = Math.floor(this.videoPending);
    if (pending < 1 || this.data.stageId === 0) {
      return;
    }
    // 限频：后端单次 ≤120s、每分钟 ≤2 次。本端先自限以免被拒。
    const now = Date.now();
    this.videoFlushTimes = this.videoFlushTimes.filter((ts) => now - ts < 60_000);
    if (this.videoFlushTimes.length >= 2) {
      return;
    }
    const deltaSec = Math.min(120, pending);
    this.videoPending -= deltaSec;
    this.videoFlushTimes.push(now);
    postVideoProgress(this.data.stageId, deltaSec)
      .then((p) => this.applyProgress(p))
      .catch(() => {
        // 打点失败静默，不打断观看。
      });
  },

  // --- 学：课件页阅读 -------------------------------------------------------

  onMarkPage(e: WechatMiniprogram.CustomEvent) {
    const dataset = e.currentTarget.dataset as { pageId?: string | number };
    const pageId = Number(dataset.pageId);
    if (!pageId) {
      return;
    }
    postPageRead(this.data.stageId, pageId)
      .then((p) => {
        this.markPageRead(pageId);
        this.applyProgress(p);
      })
      .catch((err: unknown) => {
        const msg = err instanceof ApiError ? err.message : '标记失败';
        wx.showToast({ title: msg, icon: 'none' });
      });
  },

  previewPage(e: WechatMiniprogram.CustomEvent) {
    const dataset = e.currentTarget.dataset as { url?: string };
    const url = dataset.url ?? '';
    if (!url) {
      return;
    }
    const urls = this.data.pages.map((p) => p.imageUrl);
    wx.previewImage({ urls, current: url });
  },

  onMarkAllPages() {
    const ids = this.data.pages.filter((p) => !p.read).map((p) => p.pageId);
    if (ids.length === 0) {
      return;
    }
    postPagesRead(this.data.stageId, ids)
      .then((p) => {
        const pages = this.data.pages.map((pg) => ({ ...pg, read: true }));
        this.setData({ pages });
        this.applyProgress(p);
        wx.showToast({ title: '已全部标记', icon: 'success' });
      })
      .catch((err: unknown) => {
        const msg = err instanceof ApiError ? err.message : '标记失败';
        wx.showToast({ title: msg, icon: 'none' });
      });
  },

  markPageRead(pageId: number) {
    const pages = this.data.pages.map((p) => (p.pageId === pageId ? { ...p, read: true } : p));
    this.setData({ pages });
  },

  applyProgress(p: StageProgressDto) {
    this.setData({
      videoWatchedSec: p.videoWatchedSec,
      videoPct: this.ratio(p.videoWatchedSec, p.requiredVideoSec),
      videoWidth: this.ratioWidth(p.videoWatchedSec, p.requiredVideoSec),
      videoDone: p.requiredVideoSec > 0 && p.videoWatchedSec >= p.requiredVideoSec,
      pagesReadCount: p.pagesRead,
      studyPct: this.ratio(p.pagesRead, p.requiredPages),
      studyWidth: this.ratioWidth(p.pagesRead, p.requiredPages),
      studyDone: p.requiredPages > 0 && p.pagesRead >= p.requiredPages,
      examplesDoneCount: p.examplesDone,
      examplesDone: p.requiredExamples === 0 || p.examplesDone >= p.requiredExamples,
      completed: p.completed,
    });
  },

  // --- 练：站内客观题 -------------------------------------------------------

  onPickChoice(e: WechatMiniprogram.CustomEvent) {
    const dataset = e.currentTarget.dataset as { q?: string | number; opt?: string | number };
    const qi = Number(dataset.q);
    const oi = Number(dataset.opt);
    const q = this.data.questions[qi];
    if (!q || q.answeredCorrect) {
      return;
    }
    const options = q.options.map((o, idx) => ({ ...o, cls: idx === oi ? 'is-picked' : '' }));
    this.patchQuestion(qi, { options, showResult: false, canSubmit: true });
  },

  onPickJudge(e: WechatMiniprogram.CustomEvent) {
    const dataset = e.currentTarget.dataset as { q?: string | number; val?: string };
    const qi = Number(dataset.q);
    const val = dataset.val === 'T' ? 'T' : 'F';
    const q = this.data.questions[qi];
    if (!q || q.answeredCorrect) {
      return;
    }
    this.patchQuestion(qi, {
      judgeValue: val,
      judgeTrueCls: val === 'T' ? 'is-picked' : '',
      judgeFalseCls: val === 'F' ? 'is-picked' : '',
      showResult: false,
      canSubmit: true,
    });
  },

  onBlankInput(e: WechatMiniprogram.CustomEvent<{ value: string }>) {
    const dataset = e.currentTarget.dataset as { q?: string | number; blank?: string | number };
    const qi = Number(dataset.q);
    const bi = Number(dataset.blank);
    const q = this.data.questions[qi];
    if (!q || q.answeredCorrect) {
      return;
    }
    const blanks = q.blanks.map((b) => (b.i === bi ? { ...b, value: e.detail.value } : b));
    const canSubmit = blanks.slice(0, q.blankCount).every((b) => b.value.trim().length > 0);
    this.patchQuestion(qi, { blanks, showResult: false, canSubmit });
  },

  buildAnswers(q: QuestionVM): string[] {
    switch (q.type) {
      case 'CHOICE': {
        const picked = q.options.find((o) => o.cls.indexOf('is-picked') >= 0);
        return picked ? [picked.text] : [];
      }
      case 'JUDGE':
        return q.judgeValue ? [q.judgeValue === 'T' ? '正确' : '错误'] : [];
      case 'FILL':
        return q.blanks.slice(0, q.blankCount).map((b) => b.value);
      default:
        return [];
    }
  },

  async onSubmitQuestion(e: WechatMiniprogram.CustomEvent) {
    const dataset = e.currentTarget.dataset as { q?: string | number };
    const qi = Number(dataset.q);
    const q = this.data.questions[qi];
    if (!q || q.answeredCorrect || q.pending || !q.canSubmit) {
      return;
    }
    this.patchQuestion(qi, { pending: true });
    try {
      const res = await answerPracticeQuestion(this.data.stageId, q.id, this.buildAnswers(q));
      const patch: Partial<QuestionVM> = {
        pending: false,
        showResult: true,
        resultOk: res.correct,
        correctAnswersText: res.correctAnswers.join(' / '),
        explanation: res.explanation,
        answeredCorrect: res.correct,
        canSubmit: false,
      };
      if (q.type === 'CHOICE') {
        patch.options = q.options.map((o) => {
          const isCorrect = res.correctAnswers.indexOf(o.text) >= 0;
          const picked = o.cls.indexOf('is-picked') >= 0;
          let cls = picked ? 'is-picked' : '';
          if (isCorrect) {
            cls = 'is-correct';
          } else if (picked && !res.correct) {
            cls = 'is-wrong';
          }
          return { ...o, cls };
        });
      }
      this.patchQuestion(qi, patch);
      this.recomputePractice();
    } catch (err) {
      this.patchQuestion(qi, { pending: false });
      const msg = err instanceof ApiError ? err.message : '判分失败';
      wx.showToast({ title: msg, icon: 'none' });
    }
  },

  patchQuestion(qi: number, patch: Partial<QuestionVM>) {
    const questions = this.data.questions.slice();
    questions[qi] = { ...questions[qi], ...patch };
    this.setData({ questions });
  },

  recomputePractice() {
    const questionsCorrect = this.data.questions.filter((q) => q.answeredCorrect).length;
    this.setData({
      questionsCorrect,
      practiceDone: this.data.requiredQuestions === 0 || questionsCorrect >= this.data.requiredQuestions,
    });
  },

  // --- 练：OJ 例题（跳训练场判题，回写 example-done 由训练场带 stageId 处理） -------

  onGoExample(e: WechatMiniprogram.CustomEvent) {
    const dataset = e.currentTarget.dataset as { id?: string | number };
    const problemId = Number(dataset.id);
    if (!problemId) {
      return;
    }
    this.reloadOnShow = true;
    wx.navigateTo({
      url: `/pages/practice/detail?problemId=${problemId}&stageId=${this.data.stageId}`,
    });
  },

  goTest() {
    wx.navigateTo({ url: `/pages/test/test?chapter=${this.data.chapter}` });
  },
});
