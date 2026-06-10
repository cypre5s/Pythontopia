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
  /** ISO 时间。 */
  startedAt: string;
  /** ISO 时间：倒计时锚点，到点自动交卷。 */
  deadlineAt: string;
  /** 满分（默认 100）。 */
  totalScore: number;
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

/** 触发本章章测预生成（§4.1）；返回当前状态机状态。 */
export async function prepareTest(chapter: number): Promise<ChapterTestPrepareDto> {
  const response = await http.post<ChapterTestPrepareDto>(
    `/api/mainline/chapters/${chapter}/test/prepare`,
    {},
  );
  return response.data;
}

/** 开考：下发去答案考卷 + deadlineAt（§4.2）。 */
export async function startTest(chapter: number): Promise<ChapterTestPaperDto> {
  const response = await http.post<ChapterTestPaperDto>(
    `/api/mainline/chapters/${chapter}/test/start`,
    {},
  );
  return response.data;
}

/** 交卷：上送答案 + 防作弊计数；返回判分 + 解析（§4.2）。 */
export async function submitTest(
  chapter: number,
  testId: string,
  payload: ChapterTestSubmitRequest,
): Promise<ChapterTestResultDto> {
  const response = await http.post<ChapterTestResultDto>(
    `/api/mainline/chapters/${chapter}/test/${testId}/submit`,
    payload,
  );
  return response.data;
}

/** 拉取本章最近一次已提交结果；尚无记录（404 / 空）→ null。 */
export async function getLatestTest(
  chapter: number,
): Promise<ChapterTestResultDto | null> {
  try {
    const response = await http.get<ChapterTestResultDto | null>(
      `/api/mainline/chapters/${chapter}/test/latest`,
    );
    return response.data ?? null;
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
