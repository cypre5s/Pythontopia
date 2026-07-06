import { completeQuest } from '../../api/quests';
import { postExampleDone } from '../../api/mainline';
import { createSubmission, getPublicProblem, getSubmission } from '../../api/submissions';
import { PublicProblem, SubmissionDetail, SubmissionStatus, Verdict } from '../../api/dto/submissions';
import { ApiError } from '../../utils/request';
import { MarkdownBlock, parseMarkdown } from '../../utils/markdown';

interface StatementBlock extends MarkdownBlock {
  id: number;
}

interface SampleVM {
  index: number;
  input: string;
  output: string;
}

interface DetailData {
  problemId: number;
  questId: number | null;
  stageId: number | null;
  loading: boolean;
  errorMsg: string;
  problem: PublicProblem | null;
  statementBlocks: StatementBlock[];
  samples: SampleVM[];
  code: string;
  submitting: boolean;
  polling: boolean;
  resultStatus: SubmissionStatus | '';
  verdict: Verdict | '';
  passedCases: number;
  totalCases: number;
  failureReason: string;
  resultClass: string;
  lastSubmissionId: number | null;
}

const DEFAULT_CODE = '# 在这里编写 Python 代码\n';

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

function toStatementBlocks(markdown: string): StatementBlock[] {
  return parseMarkdown(markdown).map((block, index) => ({ ...block, id: index }));
}

function toSamples(problem: PublicProblem): SampleVM[] {
  const raw = problem.samples;
  if (!Array.isArray(raw)) {
    return [];
  }
  return raw.map((sample, index) => ({
    index: index + 1,
    input: sample.input ?? '',
    output: sample.output ?? sample.expected_stdout ?? '',
  }));
}

Page({
  cancelled: false,

  data: {
    problemId: 0,
    questId: null,
    stageId: null,
    loading: false,
    errorMsg: '',
    problem: null,
    statementBlocks: [],
    samples: [],
    code: DEFAULT_CODE,
    submitting: false,
    polling: false,
    resultStatus: '',
    verdict: '',
    passedCases: 0,
    totalCases: 0,
    failureReason: '',
    resultClass: '',
    lastSubmissionId: null,
  } as DetailData,

  onLoad(query: Record<string, string | undefined>) {
    this.cancelled = false;
    const problemId = Number(query.problemId);
    const questId = query.questId ? Number(query.questId) : null;
    const stageId = query.stageId ? Number(query.stageId) : null;
    this.setData({ problemId, questId, stageId });
    if (!problemId) {
      this.setData({ errorMsg: '缺少题目 ID' });
      return;
    }
    this.load(problemId);
  },

  onUnload() {
    this.cancelled = true;
  },

  async load(problemId: number) {
    this.setData({ loading: true, errorMsg: '' });
    try {
      const problem = await getPublicProblem(problemId);
      this.setData({
        problem,
        loading: false,
        statementBlocks: toStatementBlocks(problem.statementMarkdown),
        samples: toSamples(problem),
      });
      wx.setNavigationBarTitle({ title: problem.title });
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : '题目加载失败';
      this.setData({ loading: false, errorMsg: msg });
    }
  },

  onCodeInput(e: WechatMiniprogram.CustomEvent<{ value: string }>) {
    this.setData({ code: e.detail.value });
  },

  async onSubmit() {
    if (this.data.submitting) {
      return;
    }
    if (this.data.code.trim().length === 0) {
      wx.showToast({ title: '请输入代码', icon: 'none' });
      return;
    }
    this.setData({
      submitting: true,
      polling: true,
      errorMsg: '',
      resultStatus: '',
      verdict: '',
      failureReason: '',
      resultClass: '',
    });
    try {
      const submission = await createSubmission({
        problemId: this.data.problemId,
        code: this.data.code,
        source: 'TRAINING',
      });
      this.setData({ lastSubmissionId: submission.id });
      await this.poll(submission.id);
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : '提交失败';
      this.setData({ submitting: false, polling: false, errorMsg: msg });
    }
  },

  async poll(submissionId: number) {
    const maxAttempts = 40;
    for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
      await delay(1000);
      if (this.cancelled) {
        return;
      }
      let detail: SubmissionDetail;
      try {
        detail = await getSubmission(submissionId);
      } catch (e) {
        const msg = e instanceof ApiError ? e.message : '查询判题结果失败';
        this.setData({ submitting: false, polling: false, errorMsg: msg });
        return;
      }
      const status = detail.submission.status;
      if (status === 'PASSED' || status === 'FAILED' || status === 'ERROR') {
        this.applyResult(detail);
        return;
      }
    }
    this.setData({ submitting: false, polling: false, errorMsg: '判题超时，请稍后重试' });
  },

  applyResult(detail: SubmissionDetail) {
    const status = detail.submission.status;
    const judge = detail.judgeResult;
    this.setData({
      submitting: false,
      polling: false,
      resultStatus: status,
      verdict: judge ? judge.verdict : '',
      passedCases: judge ? judge.passedCases : 0,
      totalCases: judge ? judge.totalCases : 0,
      failureReason: judge && judge.failureReason ? judge.failureReason : '',
      resultClass: status === 'PASSED' ? 'result--pass' : 'result--fail',
    });
    // 由小节「练」跳入且本题通过：回写 example-done 三件套打点（后端再次校验 PASSED+本人）。
    if (status === 'PASSED' && this.data.stageId) {
      postExampleDone(this.data.stageId, detail.submission.problemId, detail.submission.id)
        .then(() => {
          wx.showToast({ title: '已计入小节练习', icon: 'success' });
        })
        .catch(() => {
          // 打点失败静默，不影响判题展示。
        });
    }
  },

  async onCompleteQuest() {
    const questId = this.data.questId;
    const submissionId = this.data.lastSubmissionId;
    if (!questId || !submissionId) {
      return;
    }
    try {
      await completeQuest(questId, { submissionId });
      wx.showToast({ title: '任务已完成', icon: 'success' });
      setTimeout(() => {
        wx.navigateBack();
      }, 800);
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : '完成任务失败';
      wx.showToast({ title: msg, icon: 'none' });
    }
  },
});
