// 章末大测试（chapter test）前端 API + 契约 DTO（R-15 §9 / §3 / §4.2）。
//
// 设计要点（与 A1 backend 对齐时务必复核「最终报告」里的 reconcile 清单）：
//   - 后端为唯一权威：题目「预生成入库」，考试期间零 AI 调用。
//   - `start` 下发 **去答案** 题面（answer-stripped）：CHOICE 不带 answer，JUDGE 不带 answer，
//     FILL 不带 answers，CODING 不带 reference_solution / hidden_test_cases / evidence_page_ids。
//     绝不在提交前向客户端透出任何答案 / 解析。
//   - `submit` 仅在提交后由后端返回每题 **解析（explain）+ 正确答案 + 判分明细**。
//   - 题面内部 JSONB（生成端=graph，判分端=backend）用 snake_case（§3）；
//     但 REST DTO 层沿用前端既有 camelCase 约定（如 §9 的 videoWatchedSec / requiredVideoSec）。
//   - 唯一例外：`integrity` 子对象的线格式直接镜像 `chapter_test.integrity` JSONB
//     的 snake_case key（`paste_blocked` / `blur_events` / `overtime`，见 §3 / §4.3），
//     以便后端原样落库、教师端原样读出。
import { ApiClientError, http } from './http';

/** 章测四种题型（§3 题面 JSON 契约）。 */
export type ChapterTestQuestionType = 'CHOICE' | 'JUDGE' | 'FILL' | 'CODING';
export type ChapterAssessmentMode = 'quiz' | 'test';

/** chapter_test.status 状态机（§3 表定义）。 */
export type ChapterTestStatus =
  | 'PENDING'
  | 'GENERATING'
  | 'READY'
  | 'IN_PROGRESS'
  | 'SUBMITTED'
  | 'EXPIRED'
  | 'FAILED';

/** CODING 题公开样例（隐藏用例已被后端剥离）。 */
export interface ChapterTestSampleDto {
  input: string;
  output: string;
}

interface ChapterTestQuestionBaseDto {
  /** 题内唯一 id（§3 qid，如 "c1"/"j1"/"f1"/"p1"）。 */
  qid: string;
  /** 该题分值。 */
  score: number;
  /** 关联知识点编码（可选，用于 mastery 回写 / 展示）。 */
  kcCode?: string;
}

/** 选择题（去答案）。 */
export interface ChapterTestChoiceQuestionDto extends ChapterTestQuestionBaseDto {
  type: 'CHOICE';
  stem: string;
  /** 选项正文（顺序与后端一致；提交时用「序号→字母」映射回 A/B/C/D）。 */
  options: string[];
}

/** 判断题（去答案）。 */
export interface ChapterTestJudgeQuestionDto extends ChapterTestQuestionBaseDto {
  type: 'JUDGE';
  stem: string;
}

/** 填空题（去答案）。题干含连续下划线占位（`__`+）。 */
export interface ChapterTestFillQuestionDto extends ChapterTestQuestionBaseDto {
  type: 'FILL';
  stem: string;
  /** 可选：后端声明的空位数（仅作参考；渲染以题干解析为准，保证状态与 UI 同步）。 */
  blankCount?: number;
}

/** 编程题（去答案：无 hidden_test_cases / reference_solution）。 */
export interface ChapterTestCodingQuestionDto extends ChapterTestQuestionBaseDto {
  type: 'CODING';
  title: string;
  statementMarkdown: string;
  samples: ChapterTestSampleDto[];
}

export type ChapterTestQuestionDto =
  | ChapterTestChoiceQuestionDto
  | ChapterTestJudgeQuestionDto
  | ChapterTestFillQuestionDto
  | ChapterTestCodingQuestionDto;

/** `start` 返回的去答案考卷 + 截止时间。 */
export interface ChapterTestPaperDto {
  /** chapter_test.id（用 string 兼容 BIGSERIAL，避免 JS number 精度问题）。 */
  testId: string;
  chapter: number;
  attemptNo: number;
  status: ChapterTestStatus;
  timeLimitSec: number;
  /** ISO 时间（后端 start 响应不下发，仅本地兼容字段）。 */
  startedAt?: string;
  /** ISO 时间：倒计时锚点，到点自动交卷。 */
  deadlineAt: string;
  /** 满分（默认 100；后端 start 响应不下发）。 */
  totalScore?: number;
  /** 去答案题面列表。 */
  items: ChapterTestQuestionDto[];
}

/** `prepare`：触发预生成，返回当前状态。 */
export interface ChapterTestPrepareDto {
  status: ChapterTestStatus;
}

/**
 * 防作弊计数（§4.3）。线格式镜像 `chapter_test.integrity` JSONB 的 snake_case key，
 * 后端原样落库、教师端原样读出。
 */
export interface ChapterTestIntegrityDto {
  /** 编程题输入框被拦截的粘贴次数。 */
  paste_blocked: number;
  /** 考试期间页面失焦（visibilitychange→hidden）次数。 */
  blur_events: number;
  /** 是否超时（到点自动交卷 / 截止后提交）。 */
  overtime: boolean;
}

/**
 * 提交答案（判别联合，后端按 type 分派判分）。
 *   - CHOICE：所选项字母 "A".."D"（由序号映射，见 optionLetter）。
 *   - JUDGE：true/false。
 *   - FILL：各空位文本（长度 = 题干空位数）。
 *   - CODING：源代码（正式判题在服务端沙箱，Pyodide 仅本地预检）。
 */
export type ChapterTestAnswerDto =
  | { qid: string; type: 'CHOICE'; choice: string }
  | { qid: string; type: 'JUDGE'; judgement: boolean }
  | { qid: string; type: 'FILL'; blanks: string[] }
  | { qid: string; type: 'CODING'; code: string };

export interface ChapterTestSubmitRequest {
  answers: ChapterTestAnswerDto[];
  integrity: ChapterTestIntegrityDto;
}

/** 提交后每题判分明细 + 解析（仅提交后返回）。 */
export interface ChapterTestResultItemDto {
  qid: string;
  type: ChapterTestQuestionType;
  /** 该题满分。 */
  score: number;
  /** 实得分。 */
  awarded: number;
  correct: boolean;
  /** 解析（提交后才下发）。 */
  explain: string;
  kcCode?: string;
  /** 证据课件页（提交后才下发，避免提示答案位置）。 */
  evidencePageIds?: number[];
  /** CHOICE 正确项字母。 */
  correctChoice?: string;
  /** JUDGE 正确答案。 */
  correctJudgement?: boolean;
  /** FILL 可接受答案集合（归一化）。 */
  acceptedAnswers?: string[];
  /** CODING 参考解（提交后教学用）。 */
  referenceSolution?: string;
  /** CODING 通过用例数。 */
  passedTestCases?: number;
  /** CODING 用例总数。 */
  totalTestCases?: number;
}

/** `submit` / `latest` 返回的判分结果。 */
export interface ChapterTestResultDto {
  testId: string;
  chapter: number;
  attemptNo: number;
  status: ChapterTestStatus;
  /** 本次得分。 */
  score: number;
  /** 满分（默认 100）。 */
  maxScore: number;
  /** 是否通过（默认 ≥60，权威以后端为准）。 */
  passed: boolean;
  submittedAt: string;
  integrity: ChapterTestIntegrityDto;
  items: ChapterTestResultItemDto[];
  /** 提交后本章星级（由 B1 ChapterStarService 计算；可选）。 */
  chapterStars?: number;
}

function isNotFound(error: unknown): boolean {
  // http 拦截器把 AxiosError 统一包成 ApiClientError（status 在顶层）。
  if (error instanceof ApiClientError) {
    return error.status === 404;
  }
  if (typeof error === 'object' && error !== null && 'response' in error) {
    const { response } = error as { response?: { status?: number } };
    return response?.status === 404;
  }
  return false;
}

// --- 后端线格式适配层 -------------------------------------------------------
// 后端为唯一权威（A1 实现，见 backend mainline/dto/Chapter*Dto.java）：
//   - submit 请求体 answers 是 `qid → 字符串` Map（非判别联合数组）；integrity 为
//     camelCase {pasteBlocked, blurEvents}（overtime 由服务端按 deadline 判定）。
//   - 结果 items 字段名为 awardedScore/maxScore/correctAnswer/passedCases/totalCases，
//     星级在顶层 `stars`。本层把线格式映射回上面的前端 DTO，组件层不感知差异。

/** 后端 QuestionView 线格式（start 下发，去答案）。 */
interface PaperItemWire {
  qid: string;
  type: string | null;
  score: number;
  stem: string | null;
  title: string | null;
  statementMarkdown: string | null;
  options: string[] | null;
  samples: ChapterTestSampleDto[] | null;
}

interface PaperWire {
  testId: number;
  chapter: number;
  attemptNo: number;
  status: ChapterTestStatus;
  timeLimitSec: number;
  deadlineAt: string;
  items: PaperItemWire[];
}

/** 后端 QuestionResultView 线格式（submit/latest 下发）。 */
interface ResultItemWire {
  qid: string;
  type: string | null;
  correct: boolean;
  awardedScore: number;
  maxScore: number;
  yourAnswer: string | null;
  correctAnswer: string | null;
  explain: string | null;
  passedCases: number | null;
  totalCases: number | null;
}

interface ResultWire {
  testId: number;
  chapter: number;
  attemptNo: number;
  status: ChapterTestStatus;
  score: number | null;
  maxScore: number | null;
  passed: boolean;
  stars: number;
  overtime: boolean;
  submittedAt: string | null;
  items: ResultItemWire[] | null;
  integrity: { pasteBlocked?: number; blurEvents?: number; overtime?: boolean } | null;
}

function mapPaperItem(raw: PaperItemWire): ChapterTestQuestionDto {
  const base = { qid: raw.qid, score: raw.score };
  switch (raw.type) {
    case 'CODING':
      return {
        ...base,
        type: 'CODING',
        title: raw.title ?? '',
        statementMarkdown: raw.statementMarkdown ?? raw.stem ?? '',
        samples: raw.samples ?? [],
      };
    case 'JUDGE':
      return { ...base, type: 'JUDGE', stem: raw.stem ?? '' };
    case 'FILL':
      return { ...base, type: 'FILL', stem: raw.stem ?? '' };
    default:
      // CHOICE 及未知类型兜底按选择题渲染（未知类型不应出现，防御性降级）。
      return { ...base, type: 'CHOICE', stem: raw.stem ?? '', options: raw.options ?? [] };
  }
}

function mapPaper(raw: PaperWire): ChapterTestPaperDto {
  return {
    testId: String(raw.testId),
    chapter: raw.chapter,
    attemptNo: raw.attemptNo,
    status: raw.status,
    timeLimitSec: raw.timeLimitSec,
    deadlineAt: raw.deadlineAt,
    items: (raw.items ?? []).map(mapPaperItem),
  };
}

/** 后端 JUDGE 正确答案文本（"true"/"false"）→ 布尔；无法解析返回 undefined。 */
function parseJudgement(text: string | null): boolean | undefined {
  if (text === null) return undefined;
  const v = text.trim().toLowerCase();
  if (['true', 't', '1', 'yes', 'y', '对', '正确', '是'].includes(v)) return true;
  if (['false', 'f', '0', 'no', 'n', '错', '错误', '否'].includes(v)) return false;
  return undefined;
}

function mapResultItem(raw: ResultItemWire): ChapterTestResultItemDto {
  const type = (raw.type ?? 'CHOICE') as ChapterTestQuestionType;
  const item: ChapterTestResultItemDto = {
    qid: raw.qid,
    type,
    score: raw.maxScore,
    awarded: raw.awardedScore,
    correct: raw.correct,
    explain: raw.explain ?? '',
  };
  switch (type) {
    case 'CHOICE':
      if (raw.correctAnswer) item.correctChoice = raw.correctAnswer;
      break;
    case 'JUDGE':
      item.correctJudgement = parseJudgement(raw.correctAnswer);
      break;
    case 'FILL':
      // 后端把候选集合 join(' / ') 成单串下发（ChapterTestJudge.correctAnswerText）。
      if (raw.correctAnswer) item.acceptedAnswers = raw.correctAnswer.split(' / ');
      break;
    case 'CODING':
      if (raw.correctAnswer) item.referenceSolution = raw.correctAnswer;
      if (raw.passedCases !== null) item.passedTestCases = raw.passedCases;
      if (raw.totalCases !== null) item.totalTestCases = raw.totalCases;
      break;
  }
  return item;
}

function mapResult(raw: ResultWire | null): ChapterTestResultDto | null {
  if (!raw) return null;
  return {
    testId: String(raw.testId),
    chapter: raw.chapter,
    attemptNo: raw.attemptNo,
    status: raw.status,
    score: raw.score ?? 0,
    maxScore: raw.maxScore ?? 100,
    passed: raw.passed,
    submittedAt: raw.submittedAt ?? '',
    integrity: {
      paste_blocked: raw.integrity?.pasteBlocked ?? 0,
      blur_events: raw.integrity?.blurEvents ?? 0,
      overtime: raw.integrity?.overtime ?? raw.overtime ?? false,
    },
    items: (raw.items ?? []).map(mapResultItem),
    chapterStars: raw.stars,
  };
}

/** 判别联合答案 → 后端 `qid → 字符串` 线格式。 */
export function serializeAnswer(answer: ChapterTestAnswerDto): string {
  switch (answer.type) {
    case 'CHOICE':
      return answer.choice;
    case 'JUDGE':
      return answer.judgement ? 'true' : 'false';
    case 'FILL':
      // 后端 normFill 归一化会去掉全部空白后与候选集比对；题面为单空（候选集 = 同义写法），
      // 多空时以空格连接等价于按序拼接。
      return answer.blanks.join(' ');
    case 'CODING':
      return answer.code;
  }
}

function assessmentPath(mode: ChapterAssessmentMode): string {
  return mode === 'quiz' ? 'quiz' : 'test';
}

/** 触发本章测评预生成（§4.1）；返回当前状态机状态。 */
export async function prepareTest(
  chapter: number,
  mode: ChapterAssessmentMode = 'test',
): Promise<ChapterTestPrepareDto> {
  const response = await http.post<ChapterTestPrepareDto>(
    `/api/mainline/chapters/${chapter}/${assessmentPath(mode)}/prepare`,
    {},
  );
  return response.data;
}

/** 开考：下发去答案考卷 + deadlineAt（§4.2）。 */
export async function startTest(
  chapter: number,
  mode: ChapterAssessmentMode = 'test',
): Promise<ChapterTestPaperDto> {
  const response = await http.post<PaperWire>(
    `/api/mainline/chapters/${chapter}/${assessmentPath(mode)}/start`,
    {},
  );
  return mapPaper(response.data);
}

/** 交卷：上送答案 + 防作弊计数；返回判分 + 解析（§4.2）。 */
export async function submitTest(
  chapter: number,
  testId: string,
  payload: ChapterTestSubmitRequest,
  mode: ChapterAssessmentMode = 'test',
): Promise<ChapterTestResultDto> {
  const answers: Record<string, string> = {};
  for (const answer of payload.answers) {
    answers[answer.qid] = serializeAnswer(answer);
  }
  const response = await http.post<ResultWire>(
    `/api/mainline/chapters/${chapter}/${assessmentPath(mode)}/${testId}/submit`,
    {
      answers,
      integrity: {
        pasteBlocked: payload.integrity.paste_blocked,
        blurEvents: payload.integrity.blur_events,
        // R4：客户端自报「倒计时归零自动交卷」，教师端可见（服务端拒收口径仍会强制覆盖 true）。
        overtime: payload.integrity.overtime,
      },
    },
  );
  const result = mapResult(response.data);
  if (!result) {
    throw new Error('章测判分响应为空');
  }
  return result;
}

/** 拉取本章最近一次已提交结果；尚无记录（404 / 空）→ null。 */
export async function getLatestTest(
  chapter: number,
  mode: ChapterAssessmentMode = 'test',
): Promise<ChapterTestResultDto | null> {
  try {
    const response = await http.get<ResultWire | null>(
      `/api/mainline/chapters/${chapter}/${assessmentPath(mode)}/latest`,
    );
    return mapResult(response.data ?? null);
  } catch (error) {
    if (isNotFound(error)) {
      return null;
    }
    throw error;
  }
}

/** 选项序号 → 字母（0→A、1→B…），CHOICE 提交用。 */
export function optionLetter(index: number): string {
  return String.fromCharCode(65 + index);
}

/** 选项字母 → 序号（"A"→0…），结果回显高亮用。 */
export function letterToIndex(letter: string): number {
  return letter.toUpperCase().charCodeAt(0) - 65;
}
