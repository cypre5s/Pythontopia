// 章末大测试页（R-15）—— prepare → start → 答题（倒计时） → submit → result。
//
// 后端唯一权威：题目预生成入库、考试期零 AI；start 下发去答案题面、submit 才回解析。
// 本页负责：触发预生成并轮询至 READY、渲染四类题型、倒计时到点自动交卷、上报防作弊计数。

import {
  getLatestTest,
  optionLetter,
  prepareTest,
  startTest,
  submitTest,
} from '../../api/chapterTest';
import {
  ChapterTestAnswerDto,
  ChapterTestPaperDto,
  ChapterTestQuestionDto,
  ChapterTestResultDto,
  ChapterTestResultItemDto,
} from '../../api/dto/chapterTest';
import { ApiError } from '../../utils/request';

const TYPE_LABEL: Record<ChapterTestQuestionDto['type'], string> = {
  CHOICE: '单选题',
  JUDGE: '判断题',
  FILL: '填空题',
  CODING: '编程题',
};

type Phase = 'intro' | 'preparing' | 'exam' | 'submitting' | 'result';

interface OptionVM {
  letter: string;
  text: string;
  cls: string;
}

interface BlankVM {
  i: number;
  value: string;
}

interface SampleVM {
  no: number;
  input: string;
  output: string;
}

interface ItemVM {
  qid: string;
  no: number;
  type: ChapterTestQuestionDto['type'];
  typeLabel: string;
  score: number;
  stem: string;
  title: string;
  statement: string;
  options: OptionVM[];
  judgeValue: '' | 'T' | 'F';
  judgeTrueCls: string;
  judgeFalseCls: string;
  blanks: BlankVM[];
  code: string;
  samples: SampleVM[];
  // 结果态
  graded: boolean;
  correct: boolean;
  awarded: number;
  explain: string;
  correctText: string;
  passedCases: string;
}

interface TestData {
  chapter: number;
  phase: Phase;
  errorMsg: string;
  statusHint: string;
  // 历史成绩
  hasLatest: boolean;
  latestScore: number;
  latestMax: number;
  latestPassed: boolean;
  latestStars: number;
  // 考卷
  testId: string;
  attemptNo: number;
  items: ItemVM[];
  countdown: string;
  overtime: boolean;
  // 结果
  resultScore: number;
  resultMax: number;
  resultPassed: boolean;
  resultStars: number;
}

function countBlanks(stem: string): number {
  const matches = stem.match(/_{2,}/g);
  return matches && matches.length > 0 ? matches.length : 1;
}

function toItemVM(q: ChapterTestQuestionDto, no: number): ItemVM {
  const base: ItemVM = {
    qid: q.qid,
    no,
    type: q.type,
    typeLabel: TYPE_LABEL[q.type],
    score: q.score,
    stem: '',
    title: '',
    statement: '',
    options: [],
    judgeValue: '',
    judgeTrueCls: '',
    judgeFalseCls: '',
    blanks: [],
    code: '',
    samples: [],
    graded: false,
    correct: false,
    awarded: 0,
    explain: '',
    correctText: '',
    passedCases: '',
  };
  switch (q.type) {
    case 'CHOICE':
      base.stem = q.stem;
      base.options = q.options.map((text, idx) => ({ letter: optionLetter(idx), text, cls: '' }));
      break;
    case 'JUDGE':
      base.stem = q.stem;
      break;
    case 'FILL': {
      base.stem = q.stem;
      const n = q.blankCount && q.blankCount > 0 ? q.blankCount : countBlanks(q.stem);
      base.blanks = Array.from({ length: n }, (_unused, i) => ({ i, value: '' }));
      break;
    }
    case 'CODING':
      base.title = q.title;
      base.statement = q.statementMarkdown;
      base.samples = q.samples.map((s, i) => ({ no: i + 1, input: s.input, output: s.output }));
      base.code = '# 在此编写 Python 代码\n';
      break;
  }
  return base;
}

function pad2(n: number): string {
  return n < 10 ? `0${n}` : `${n}`;
}

Page({
  timer: 0,
  deadlineMs: 0,
  blurEvents: 0,
  cancelled: false,

  data: {
    chapter: 0,
    phase: 'intro',
    errorMsg: '',
    statusHint: '',
    hasLatest: false,
    latestScore: 0,
    latestMax: 100,
    latestPassed: false,
    latestStars: 0,
    testId: '',
    attemptNo: 0,
    items: [],
    countdown: '',
    overtime: false,
    resultScore: 0,
    resultMax: 100,
    resultPassed: false,
    resultStars: 0,
  } as TestData,

  onLoad(query: Record<string, string | undefined>) {
    const chapter = Number(query.chapter);
    if (!chapter) {
      this.setData({ errorMsg: '缺少章节参数' });
      return;
    }
    this.setData({ chapter });
    wx.setNavigationBarTitle({ title: `第 ${chapter} 章 · 章末测试` });
    this.loadLatest();
  },

  onHide() {
    // 考试期间切后台/离开页面计为一次失焦（防作弊计数，best-effort）。
    if (this.data.phase === 'exam') {
      this.blurEvents += 1;
    }
  },

  onUnload() {
    this.cancelled = true;
    this.clearTimer();
  },

  clearTimer() {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = 0;
    }
  },

  async loadLatest() {
    try {
      const latest = await getLatestTest(this.data.chapter);
      if (this.cancelled) {
        return;
      }
      if (latest) {
        this.setData({
          hasLatest: true,
          latestScore: latest.score,
          latestMax: latest.maxScore,
          latestPassed: latest.passed,
          latestStars: latest.chapterStars ?? 0,
        });
      }
    } catch {
      // 历史成绩拉取失败不阻断开考。
    }
  },

  async onStart() {
    if (this.data.phase === 'preparing') {
      return;
    }
    this.setData({ phase: 'preparing', errorMsg: '', statusHint: '正在准备试卷…' });
    try {
      await this.prepareUntilReady();
      const paper = await startTest(this.data.chapter);
      if (this.cancelled) {
        return;
      }
      this.beginExam(paper);
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : '试卷准备失败，请稍后重试';
      this.setData({ phase: 'intro', errorMsg: msg, statusHint: '' });
    }
  },

  async prepareUntilReady() {
    const maxAttempts = 30;
    for (let i = 0; i < maxAttempts; i += 1) {
      const res = await prepareTest(this.data.chapter);
      if (res.status === 'READY' || res.status === 'IN_PROGRESS') {
        return;
      }
      if (res.status === 'FAILED' || res.status === 'EXPIRED') {
        throw new ApiError(0, 'TEST_NOT_READY', '试卷生成失败，请稍后重试', [], '');
      }
      this.setData({ statusHint: `正在生成试卷…（${i + 1}）` });
      await new Promise<void>((resolve) => setTimeout(resolve, 2000));
    }
    throw new ApiError(0, 'TEST_TIMEOUT', '试卷生成超时，请稍后重试', [], '');
  },

  beginExam(paper: ChapterTestPaperDto) {
    this.blurEvents = 0;
    this.deadlineMs = new Date(paper.deadlineAt).getTime();
    const items = paper.items.map((q, i) => toItemVM(q, i + 1));
    this.setData({
      phase: 'exam',
      statusHint: '',
      testId: paper.testId,
      attemptNo: paper.attemptNo,
      items,
      overtime: false,
    });
    this.tick();
    this.clearTimer();
    this.timer = setInterval(() => this.tick(), 1000) as unknown as number;
  },

  tick() {
    const remainMs = this.deadlineMs - Date.now();
    if (remainMs <= 0) {
      this.setData({ countdown: '00:00', overtime: true });
      this.clearTimer();
      if (this.data.phase === 'exam') {
        this.submit(true);
      }
      return;
    }
    const total = Math.floor(remainMs / 1000);
    const m = Math.floor(total / 60);
    const s = total % 60;
    this.setData({ countdown: `${pad2(m)}:${pad2(s)}` });
  },

  // --- 答题交互 ------------------------------------------------------------

  onPickChoice(e: WechatMiniprogram.CustomEvent) {
    const dataset = e.currentTarget.dataset as { q?: string | number; opt?: string | number };
    const qi = Number(dataset.q);
    const oi = Number(dataset.opt);
    const item = this.data.items[qi];
    if (!item) {
      return;
    }
    const options = item.options.map((o, idx) => ({ ...o, cls: idx === oi ? 'is-picked' : '' }));
    this.patchItem(qi, { options });
  },

  onPickJudge(e: WechatMiniprogram.CustomEvent) {
    const dataset = e.currentTarget.dataset as { q?: string | number; val?: string };
    const qi = Number(dataset.q);
    const val = dataset.val === 'T' ? 'T' : 'F';
    this.patchItem(qi, {
      judgeValue: val,
      judgeTrueCls: val === 'T' ? 'is-picked' : '',
      judgeFalseCls: val === 'F' ? 'is-picked' : '',
    });
  },

  onBlankInput(e: WechatMiniprogram.CustomEvent<{ value: string }>) {
    const dataset = e.currentTarget.dataset as { q?: string | number; blank?: string | number };
    const qi = Number(dataset.q);
    const bi = Number(dataset.blank);
    const item = this.data.items[qi];
    if (!item) {
      return;
    }
    const blanks = item.blanks.map((b) => (b.i === bi ? { ...b, value: e.detail.value } : b));
    this.patchItem(qi, { blanks });
  },

  onCodeInput(e: WechatMiniprogram.CustomEvent<{ value: string }>) {
    const dataset = e.currentTarget.dataset as { q?: string | number };
    const qi = Number(dataset.q);
    this.patchItem(qi, { code: e.detail.value });
  },

  patchItem(qi: number, patch: Partial<ItemVM>) {
    const cur = this.data.items[qi];
    if (!cur) {
      return;
    }
    // 路径化 setData：只下发被改的那道题，避免全卷重序列化。
    this.setData({ [`items[${qi}]`]: { ...cur, ...patch } });
  },

  buildAnswer(item: ItemVM): ChapterTestAnswerDto {
    switch (item.type) {
      case 'CHOICE': {
        const pickedIdx = item.options.findIndex((o) => o.cls.indexOf('is-picked') >= 0);
        return { qid: item.qid, type: 'CHOICE', choice: pickedIdx >= 0 ? optionLetter(pickedIdx) : '' };
      }
      case 'JUDGE':
        return { qid: item.qid, type: 'JUDGE', judgement: item.judgeValue === 'T' };
      case 'FILL':
        return { qid: item.qid, type: 'FILL', blanks: item.blanks.map((b) => b.value) };
      case 'CODING':
        return { qid: item.qid, type: 'CODING', code: item.code };
    }
  },

  onSubmitTap() {
    if (this.data.phase !== 'exam') {
      return;
    }
    wx.showModal({
      title: '确认交卷',
      content: '交卷后将判分并锁定本次作答，确定吗？',
      success: (res) => {
        if (res.confirm) {
          this.submit(false);
        }
      },
    });
  },

  async submit(overtime: boolean) {
    if (this.data.phase === 'submitting' || this.data.phase === 'result') {
      return;
    }
    this.clearTimer();
    this.setData({ phase: 'submitting', statusHint: '正在判分…' });
    const answers = this.data.items.map((item) => this.buildAnswer(item));
    try {
      const result = await submitTest(this.data.chapter, this.data.testId, {
        answers,
        integrity: {
          paste_blocked: 0,
          blur_events: this.blurEvents,
          overtime: overtime || this.data.overtime,
        },
      });
      if (this.cancelled) {
        return;
      }
      this.applyResult(result);
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : '交卷失败，请重试';
      this.setData({ phase: 'exam', statusHint: '', errorMsg: msg });
    }
  },

  applyResult(result: ChapterTestResultDto) {
    const byQid: Record<string, ChapterTestResultItemDto> = {};
    result.items.forEach((it) => {
      byQid[it.qid] = it;
    });
    const items = this.data.items.map((item) => {
      const r = byQid[item.qid];
      if (!r) {
        return item;
      }
      return { ...item, ...this.gradeItem(item, r) };
    });
    this.setData({
      phase: 'result',
      statusHint: '',
      errorMsg: '',
      items,
      resultScore: result.score,
      resultMax: result.maxScore,
      resultPassed: result.passed,
      resultStars: result.chapterStars ?? 0,
      hasLatest: true,
      latestScore: result.score,
      latestMax: result.maxScore,
      latestPassed: result.passed,
      latestStars: result.chapterStars ?? 0,
    });
  },

  gradeItem(item: ItemVM, r: ChapterTestResultItemDto): Partial<ItemVM> {
    const patch: Partial<ItemVM> = {
      graded: true,
      correct: r.correct,
      awarded: r.awarded,
      explain: r.explain,
    };
    switch (item.type) {
      case 'CHOICE': {
        const correctLetter = r.correctChoice ?? '';
        patch.correctText = correctLetter ? `正确选项：${correctLetter}` : '';
        patch.options = item.options.map((o) => {
          const isCorrect = o.letter === correctLetter;
          const picked = o.cls.indexOf('is-picked') >= 0;
          let cls = picked ? 'is-picked' : '';
          if (isCorrect) {
            cls = 'is-correct';
          } else if (picked && !r.correct) {
            cls = 'is-wrong';
          }
          return { ...o, cls };
        });
        break;
      }
      case 'JUDGE':
        patch.correctText =
          r.correctJudgement === undefined
            ? ''
            : `正确答案：${r.correctJudgement ? '正确' : '错误'}`;
        break;
      case 'FILL':
        patch.correctText = r.acceptedAnswers && r.acceptedAnswers.length > 0
          ? `参考答案：${r.acceptedAnswers.join(' / ')}`
          : '';
        break;
      case 'CODING':
        if (r.passedTestCases !== undefined && r.totalTestCases !== undefined) {
          patch.passedCases = `通过用例 ${r.passedTestCases} / ${r.totalTestCases}`;
        }
        if (r.referenceSolution) {
          patch.correctText = r.referenceSolution;
        }
        break;
    }
    return patch;
  },

  onBackMap() {
    wx.navigateBack();
  },
});
