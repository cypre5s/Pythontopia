// 章末大测试会话编排 composable（R-15 §4.2 / §4.3）。
//
// 职责：拉考卷 / 维护本地作答状态 / 统计防作弊计数 / 组装并提交 {answers, integrity} / 持有判分结果。
// 不做任何答案校验（判分在服务端），也绝不在提交前展示答案。
import { computed, onScopeDispose, reactive, ref } from 'vue';
import type { ComputedRef, Ref } from 'vue';
import {
  getLatestTest,
  prepareTest,
  startTest,
  submitTest,
} from '@/api/chapterTest';
import type {
  ChapterTestAnswerDto,
  ChapterTestIntegrityDto,
  ChapterTestPaperDto,
  ChapterTestQuestionDto,
  ChapterTestResultDto,
  ChapterTestStatus,
  ChapterTestSubmitRequest,
} from '@/api/chapterTest';

export type ExamPhase =
  | 'idle'
  | 'preparing'
  | 'ready'
  | 'starting'
  | 'in_progress'
  | 'submitting'
  | 'submitted'
  | 'error';

/** 单题本地作答状态（按题型取用对应字段）。 */
export interface LocalAnswerState {
  /** CHOICE：所选项字母 "A".."D"，未选为 null。 */
  choice: string | null;
  /** JUDGE：true/false，未选为 null。 */
  judgement: boolean | null;
  /** FILL：各空位文本。 */
  blanks: string[];
  /** CODING：源代码。 */
  code: string;
}

/**
 * 解析填空题空位：按连续 2+ 下划线切分题干。
 * 返回 segments（文本片段）与 blanks（空位数，至少 1）。
 * UI 渲染与本地状态初始化共用此函数，保证「输入框数量 == 状态数组长度」。
 */
export function splitFillSegments(stem: string): { segments: string[]; blanks: number } {
  const segments = stem.split(/_{2,}/);
  const blanks = Math.max(segments.length - 1, 1);
  return { segments, blanks };
}

/** 仅取空位数（供状态初始化）。 */
export function countFillBlanks(stem: string): number {
  return splitFillSegments(stem).blanks;
}

export interface ChapterTestExam {
  phase: Ref<ExamPhase>;
  paper: Ref<ChapterTestPaperDto | null>;
  result: Ref<ChapterTestResultDto | null>;
  latestResult: Ref<ChapterTestResultDto | null>;
  errorMessage: Ref<string>;
  prepareStatus: Ref<ChapterTestStatus | null>;
  answers: Record<string, LocalAnswerState>;
  pasteBlocked: Ref<number>;
  blurEvents: Ref<number>;
  overtime: Ref<boolean>;
  isActive: ComputedRef<boolean>;
  answeredCount: ComputedRef<number>;
  totalCount: ComputedRef<number>;
  loadLatest: () => Promise<void>;
  prepare: () => Promise<void>;
  start: () => Promise<void>;
  submitNow: () => Promise<void>;
  setChoice: (qid: string, letter: string) => void;
  setJudge: (qid: string, value: boolean) => void;
  setBlank: (qid: string, index: number, value: string) => void;
  setCode: (qid: string, value: string) => void;
  recordPaste: () => void;
  recordBlur: () => void;
  markOvertime: () => void;
}

export function useChapterTestExam(chapter: number): ChapterTestExam {
  const phase = ref<ExamPhase>('idle');
  const paper = ref<ChapterTestPaperDto | null>(null);
  const result = ref<ChapterTestResultDto | null>(null);
  const latestResult = ref<ChapterTestResultDto | null>(null);
  const errorMessage = ref<string>('');
  const prepareStatus = ref<ChapterTestStatus | null>(null);

  const pasteBlocked = ref<number>(0);
  const blurEvents = ref<number>(0);
  const overtime = ref<boolean>(false);

  const answers = reactive<Record<string, LocalAnswerState>>({});

  function initAnswers(items: ChapterTestQuestionDto[]): void {
    for (const key of Object.keys(answers)) {
      delete answers[key];
    }
    for (const item of items) {
      answers[item.qid] = {
        choice: null,
        judgement: null,
        blanks: item.type === 'FILL' ? Array.from({ length: countFillBlanks(item.stem) }, () => '') : [],
        code: '',
      };
    }
  }

  const isActive = computed<boolean>(() => phase.value === 'in_progress');
  const totalCount = computed<number>(() => paper.value?.items.length ?? 0);
  const answeredCount = computed<number>(() => {
    const items = paper.value?.items ?? [];
    let count = 0;
    for (const item of items) {
      const a = answers[item.qid];
      if (!a) continue;
      if (item.type === 'CHOICE' && a.choice !== null) count += 1;
      else if (item.type === 'JUDGE' && a.judgement !== null) count += 1;
      else if (item.type === 'FILL' && a.blanks.some((b) => b.trim().length > 0)) count += 1;
      else if (item.type === 'CODING' && a.code.trim().length > 0) count += 1;
    }
    return count;
  });

  function buildIntegrity(): ChapterTestIntegrityDto {
    return {
      paste_blocked: pasteBlocked.value,
      blur_events: blurEvents.value,
      overtime: overtime.value,
    };
  }

  function buildAnswers(): ChapterTestAnswerDto[] {
    const items = paper.value?.items ?? [];
    const out: ChapterTestAnswerDto[] = [];
    for (const item of items) {
      const a = answers[item.qid];
      if (!a) continue;
      switch (item.type) {
        case 'CHOICE':
          if (a.choice !== null) {
            out.push({ qid: item.qid, type: 'CHOICE', choice: a.choice });
          }
          break;
        case 'JUDGE':
          if (a.judgement !== null) {
            out.push({ qid: item.qid, type: 'JUDGE', judgement: a.judgement });
          }
          break;
        case 'FILL':
          out.push({ qid: item.qid, type: 'FILL', blanks: [...a.blanks] });
          break;
        case 'CODING':
          out.push({ qid: item.qid, type: 'CODING', code: a.code });
          break;
      }
    }
    return out;
  }

  function errText(error: unknown): string {
    return error instanceof Error && error.message ? error.message : '章测服务异常，请稍后重试';
  }

  async function loadLatest(): Promise<void> {
    try {
      latestResult.value = await getLatestTest(chapter);
    } catch {
      // 历史成绩拉取失败不阻塞开考流程，静默忽略。
      latestResult.value = null;
    }
  }

  // R3：GENERATING 期间轮询 prepare（后端幂等只返回状态，不重复触发生成），
  // 个性化卷就绪即转 ready——避免学生在生成窗口点「开始」直接吃 golden 兜底卷。
  // 上限 ~24s（8 次 × 3s），到点仍未就绪则停留 preparing（start 自然走 golden 兜底，不卡考试）。
  const PREPARE_POLL_MAX = 8;
  const PREPARE_POLL_INTERVAL_MS = 3000;
  let preparePollTimer: ReturnType<typeof setTimeout> | null = null;

  function stopPreparePoll(): void {
    if (preparePollTimer !== null) {
      clearTimeout(preparePollTimer);
      preparePollTimer = null;
    }
  }

  function schedulePreparePoll(attempt: number): void {
    if (attempt > PREPARE_POLL_MAX) return;
    stopPreparePoll();
    preparePollTimer = setTimeout(() => {
      void (async () => {
        // 学生已手动开考/离开准备态则停止轮询。
        if (phase.value !== 'preparing') return;
        try {
          const res = await prepareTest(chapter);
          prepareStatus.value = res.status;
          if (res.status === 'READY') {
            phase.value = 'ready';
            return;
          }
        } catch {
          // 轮询失败静默（best-effort）：保持 preparing，start 仍可走兜底。
        }
        schedulePreparePoll(attempt + 1);
      })();
    }, PREPARE_POLL_INTERVAL_MS);
  }

  async function prepare(): Promise<void> {
    phase.value = 'preparing';
    try {
      const res = await prepareTest(chapter);
      prepareStatus.value = res.status;
      phase.value = res.status === 'READY' ? 'ready' : 'preparing';
      if (phase.value === 'preparing') {
        schedulePreparePoll(1);
      }
    } catch (error) {
      errorMessage.value = errText(error);
      phase.value = 'error';
    }
  }

  // 调用方组件卸载时清理轮询定时器（composable 随 effectScope 销毁）。
  onScopeDispose(stopPreparePoll);

  async function start(): Promise<void> {
    stopPreparePoll();
    phase.value = 'starting';
    try {
      const p = await startTest(chapter);
      paper.value = p;
      initAnswers(p.items);
      result.value = null;
      pasteBlocked.value = 0;
      blurEvents.value = 0;
      overtime.value = false;
      phase.value = 'in_progress';
    } catch (error) {
      errorMessage.value = errText(error);
      phase.value = 'error';
    }
  }

  async function submitNow(): Promise<void> {
    const p = paper.value;
    if (!p) return;
    // 幂等：避免「到点自动交卷」与「手动交卷」重复提交。
    if (phase.value === 'submitting' || phase.value === 'submitted') return;
    phase.value = 'submitting';
    const payload: ChapterTestSubmitRequest = {
      answers: buildAnswers(),
      integrity: buildIntegrity(),
    };
    try {
      result.value = await submitTest(chapter, p.testId, payload);
      latestResult.value = result.value;
      phase.value = 'submitted';
    } catch (error) {
      errorMessage.value = errText(error);
      phase.value = 'error';
    }
  }

  function setChoice(qid: string, letter: string): void {
    const a = answers[qid];
    if (a) a.choice = letter;
  }
  function setJudge(qid: string, value: boolean): void {
    const a = answers[qid];
    if (a) a.judgement = value;
  }
  function setBlank(qid: string, index: number, value: string): void {
    const a = answers[qid];
    if (a && index >= 0 && index < a.blanks.length) {
      a.blanks[index] = value;
    }
  }
  function setCode(qid: string, value: string): void {
    const a = answers[qid];
    if (a) a.code = value;
  }

  function recordPaste(): void {
    pasteBlocked.value += 1;
  }
  function recordBlur(): void {
    blurEvents.value += 1;
  }
  function markOvertime(): void {
    overtime.value = true;
  }

  return {
    phase,
    paper,
    result,
    latestResult,
    errorMessage,
    prepareStatus,
    answers,
    pasteBlocked,
    blurEvents,
    overtime,
    isActive,
    answeredCount,
    totalCount,
    loadLatest,
    prepare,
    start,
    submitNow,
    setChoice,
    setJudge,
    setBlank,
    setCode,
    recordPaste,
    recordBlur,
    markOvertime,
  };
}
