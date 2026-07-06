import { closeSession, getSession, retrySubmit, revealCard, variantSubmit } from '../../api/bugMine';
import {
  BugMineCard,
  BugMineCardType,
  BugMineSession,
  BugMineSessionStatus,
  CounterExamplePayload,
  HintLayerPayload,
  VariableTracePayload,
  WrongAssumptionPayload,
} from '../../api/dto/bugMine';
import { getSubmission } from '../../api/submissions';
import { PublicProblem, SubmissionDetail, SubmissionStatus, Verdict } from '../../api/dto/submissions';
import { MarkdownBlock, parseMarkdown } from '../../utils/markdown';
import { ApiError } from '../../utils/request';

const STATUS_LABEL: Record<BugMineSessionStatus, string> = {
  OPEN: '进行中',
  RESOLVED: '已解决',
  ABANDONED: '已放弃',
};

const CARD_LABEL: Record<BugMineCardType, string> = {
  COUNTER_EXAMPLE: '反例',
  VARIABLE_TRACE: '变量轨迹',
  WRONG_ASSUMPTION: '错误假设',
  HINT_LAYER_1: '分层提示 · 第 1 层',
  HINT_LAYER_2: '分层提示 · 第 2 层',
  HINT_LAYER_3: '分层提示 · 第 3 层',
};

// 与后端 depthForCard 对齐：解锁某卡片所需的最小深度层。
const CARD_DEPTH: Record<BugMineCardType, number> = {
  COUNTER_EXAMPLE: 2,
  VARIABLE_TRACE: 3,
  WRONG_ASSUMPTION: 4,
  HINT_LAYER_1: 4,
  HINT_LAYER_2: 4,
  HINT_LAYER_3: 4,
};

type ResultKind = '' | 'retry' | 'variant';

interface StatementBlock extends MarkdownBlock {
  id: number;
}

interface SampleVM {
  index: number;
  input: string;
  output: string;
}

interface DepthStepVM {
  layer: number;
  title: string;
  description: string;
  unlocked: boolean;
  current: boolean;
}

interface CardRow {
  label: string;
  value: string;
  code: boolean;
}

interface CardVM {
  id: number;
  typeLabel: string;
  requiredDepth: number;
  revealed: boolean;
  locked: boolean;
  rows: CardRow[];
}

interface SessionData {
  id: number;
  loading: boolean;
  errorMsg: string;
  statusLabel: string;
  depthLayer: number;
  depthTarget: number;
  guidance: string;
  masteryConfirmed: boolean;
  isOpen: boolean;
  variantTitle: string;
  variantUnlocked: boolean;
  problemTitle: string;
  kcCode: string;
  statementBlocks: StatementBlock[];
  samples: SampleVM[];
  depthSteps: DepthStepVM[];
  cards: CardVM[];
  retryCode: string;
  retrySubmitting: boolean;
  variantCode: string;
  variantSubmitting: boolean;
  resultKind: ResultKind;
  resultStatus: SubmissionStatus | '';
  verdict: Verdict | '';
  passedCases: number;
  totalCases: number;
  failureReason: string;
  resultClass: string;
  revealingId: number;
  closing: boolean;
}

const DEFAULT_CODE = '# 不看提示，先按题意重写一次\n';

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

function toStatementBlocks(markdown: string | null): StatementBlock[] {
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

function buildRows(card: BugMineCard): CardRow[] {
  const rows: CardRow[] = [];
  if (card.cardType === 'COUNTER_EXAMPLE') {
    const p = card.payload as CounterExamplePayload;
    if (p.redacted) {
      rows.push({ label: '说明', value: '反例已隐藏，避免直接给出答案。', code: false });
    }
    if (p.stdin) {
      rows.push({ label: '输入', value: p.stdin, code: true });
    }
    if (p.expected_stdout) {
      rows.push({ label: '期望输出', value: p.expected_stdout, code: true });
    }
    if (p.your_stdout) {
      rows.push({ label: '你的输出', value: p.your_stdout, code: true });
    }
    if (p.diff_summary) {
      rows.push({ label: '差异', value: p.diff_summary, code: false });
    }
  } else if (card.cardType === 'VARIABLE_TRACE') {
    const p = card.payload as VariableTracePayload;
    const steps = p.steps ?? [];
    const text = steps
      .slice(0, 50)
      .map((step) => {
        const locals = step.locals ?? {};
        const kv = Object.keys(locals)
          .map((key) => `${key}=${locals[key]}`)
          .join(', ');
        return `行 ${step.line}${kv ? '  ' + kv : ''}`;
      })
      .join('\n');
    rows.push({ label: '逐行变量', value: text || '（无轨迹）', code: true });
    if (p.truncated) {
      rows.push({ label: '提示', value: '轨迹过长已截断。', code: false });
    }
  } else if (card.cardType === 'WRONG_ASSUMPTION') {
    const p = card.payload as WrongAssumptionPayload;
    if (p.you_thought) {
      rows.push({ label: '你以为', value: p.you_thought, code: false });
    }
    if (p.actually) {
      rows.push({ label: '实际上', value: p.actually, code: false });
    }
    const kcs = p.evidence_kc_codes ?? [];
    if (kcs.length > 0) {
      rows.push({ label: '相关知识点', value: kcs.join('、'), code: false });
    }
  } else {
    const p = card.payload as HintLayerPayload;
    if (p.headline) {
      rows.push({ label: '要点', value: p.headline, code: false });
    }
    if (p.body) {
      rows.push({ label: '展开', value: p.body, code: false });
    }
    const pages = p.related_pages ?? [];
    if (pages.length > 0) {
      rows.push({ label: '课件页', value: pages.join('、'), code: false });
    }
  }
  return rows;
}

function toCardVM(card: BugMineCard, depthLayer: number): CardVM {
  const requiredDepth = CARD_DEPTH[card.cardType];
  return {
    id: card.id,
    typeLabel: CARD_LABEL[card.cardType],
    requiredDepth,
    revealed: card.revealed,
    locked: !card.revealed && depthLayer < requiredDepth,
    rows: card.revealed ? buildRows(card) : [],
  };
}

Page({
  cancelled: false,

  data: {
    id: 0,
    loading: false,
    errorMsg: '',
    statusLabel: '',
    depthLayer: 0,
    depthTarget: 0,
    guidance: '',
    masteryConfirmed: false,
    isOpen: false,
    variantTitle: '',
    variantUnlocked: false,
    problemTitle: '',
    kcCode: '',
    statementBlocks: [],
    samples: [],
    depthSteps: [],
    cards: [],
    retryCode: DEFAULT_CODE,
    retrySubmitting: false,
    variantCode: DEFAULT_CODE,
    variantSubmitting: false,
    resultKind: '',
    resultStatus: '',
    verdict: '',
    passedCases: 0,
    totalCases: 0,
    failureReason: '',
    resultClass: '',
    revealingId: 0,
    closing: false,
  } as SessionData,

  onLoad(query: Record<string, string | undefined>) {
    this.cancelled = false;
    const id = Number(query.id);
    this.setData({ id });
    if (!id) {
      this.setData({ errorMsg: '缺少会话 ID' });
      return;
    }
    this.load();
  },

  onUnload() {
    this.cancelled = true;
  },

  async load() {
    this.setData({ loading: true, errorMsg: '' });
    try {
      const session = await getSession(this.data.id);
      this.applySession(session);
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : '矿洞会话加载失败';
      this.setData({ loading: false, errorMsg: msg });
    }
  },

  applySession(session: BugMineSession) {
    const problem = session.problem;
    const variantUnlocked = session.depthLayer >= 5 && session.variantProblem !== null;
    const variantTitle = session.variantProblem
      ? session.variantProblem.title ?? '同 KC 变式题'
      : '';
    this.setData({
      loading: false,
      statusLabel: STATUS_LABEL[session.status],
      depthLayer: session.depthLayer,
      depthTarget: session.profileDepthTarget,
      guidance: session.profileGuidance ?? '',
      masteryConfirmed: session.masteryConfirmed,
      isOpen: session.status === 'OPEN',
      variantTitle,
      variantUnlocked,
      problemTitle: problem ? problem.title : '',
      kcCode: problem ? problem.kcCode : '',
      statementBlocks: problem ? toStatementBlocks(problem.statementMarkdown) : [],
      samples: problem ? toSamples(problem) : [],
      depthSteps: session.depthSteps.map((step) => ({
        layer: step.layer,
        title: step.title,
        description: step.description,
        unlocked: step.unlocked,
        current: step.layer === session.depthLayer,
      })),
      cards: session.cards.map((card) => toCardVM(card, session.depthLayer)),
    });
    wx.setNavigationBarTitle({ title: problem ? problem.title : 'Bug 矿洞' });
  },

  async onReveal(e: WechatMiniprogram.CustomEvent) {
    const ds = e.currentTarget.dataset as { id?: string | number };
    const cardId = Number(ds.id);
    if (!cardId || this.data.revealingId !== 0) {
      return;
    }
    this.setData({ revealingId: cardId });
    try {
      await revealCard(this.data.id, cardId);
      await this.load();
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : '解锁失败';
      wx.showToast({ title: msg, icon: 'none' });
    } finally {
      this.setData({ revealingId: 0 });
    }
  },

  onRetryInput(e: WechatMiniprogram.CustomEvent<{ value: string }>) {
    this.setData({ retryCode: e.detail.value });
  },

  onVariantInput(e: WechatMiniprogram.CustomEvent<{ value: string }>) {
    this.setData({ variantCode: e.detail.value });
  },

  async onRetrySubmit() {
    if (this.data.retrySubmitting) {
      return;
    }
    if (this.data.retryCode.trim().length === 0) {
      wx.showToast({ title: '请输入代码', icon: 'none' });
      return;
    }
    this.setData({
      retrySubmitting: true,
      resultKind: 'retry',
      resultStatus: '',
      verdict: '',
      failureReason: '',
      resultClass: '',
    });
    try {
      const submission = await retrySubmit(this.data.id, this.data.retryCode);
      await this.poll(submission.id, 'retry');
    } catch (e) {
      this.setData({ retrySubmitting: false });
      const msg = e instanceof ApiError ? e.message : '提交失败';
      wx.showToast({ title: msg, icon: 'none' });
    }
  },

  async onVariantSubmit() {
    if (this.data.variantSubmitting) {
      return;
    }
    if (this.data.variantCode.trim().length === 0) {
      wx.showToast({ title: '请输入代码', icon: 'none' });
      return;
    }
    this.setData({
      variantSubmitting: true,
      resultKind: 'variant',
      resultStatus: '',
      verdict: '',
      failureReason: '',
      resultClass: '',
    });
    try {
      const submission = await variantSubmit(this.data.id, this.data.variantCode);
      await this.poll(submission.id, 'variant');
    } catch (e) {
      this.setData({ variantSubmitting: false });
      const msg = e instanceof ApiError ? e.message : '提交失败';
      wx.showToast({ title: msg, icon: 'none' });
    }
  },

  async poll(submissionId: number, kind: ResultKind) {
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
        this.setData({ retrySubmitting: false, variantSubmitting: false });
        const msg = e instanceof ApiError ? e.message : '查询判题结果失败';
        wx.showToast({ title: msg, icon: 'none' });
        return;
      }
      const status = detail.submission.status;
      if (status === 'PASSED' || status === 'FAILED' || status === 'ERROR') {
        this.applyResult(detail, kind);
        await this.load();
        return;
      }
    }
    this.setData({ retrySubmitting: false, variantSubmitting: false });
    wx.showToast({ title: '判题超时，请稍后重试', icon: 'none' });
  },

  applyResult(detail: SubmissionDetail, kind: ResultKind) {
    const status = detail.submission.status;
    const judge = detail.judgeResult;
    this.setData({
      retrySubmitting: false,
      variantSubmitting: false,
      resultKind: kind,
      resultStatus: status,
      verdict: judge ? judge.verdict : '',
      passedCases: judge ? judge.passedCases : 0,
      totalCases: judge ? judge.totalCases : 0,
      failureReason: judge && judge.failureReason ? judge.failureReason : '',
      resultClass: status === 'PASSED' ? 'result--pass' : 'result--fail',
    });
  },

  async onResolve() {
    await this.closeWith('RESOLVED');
  },

  async onAbandon() {
    await this.closeWith('ABANDONED');
  },

  async closeWith(status: BugMineSessionStatus) {
    if (this.data.closing) {
      return;
    }
    this.setData({ closing: true });
    try {
      await closeSession(this.data.id, status);
      wx.showToast({ title: status === 'RESOLVED' ? '已解决' : '已放弃', icon: 'success' });
      setTimeout(() => wx.navigateBack(), 800);
    } catch (e) {
      this.setData({ closing: false });
      const msg = e instanceof ApiError ? e.message : '关闭失败';
      wx.showToast({ title: msg, icon: 'none' });
    }
  },
});
