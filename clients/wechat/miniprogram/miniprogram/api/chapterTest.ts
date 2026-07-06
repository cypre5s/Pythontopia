// 章末大测试（chapter test）小程序访问层（镜像 frontend/src/api/chapterTest.ts）。
//
// 后端为唯一权威，REST DTO 层 camelCase，但 submit 请求体 / 结果 items 的线格式与前端 DTO
// 字段名不同，故本层做「线格式 ↔ 前端 DTO」映射，页面层不感知差异：
//   - submit answers 是 `qid → 字符串` Map（非判别联合数组）；integrity 为 camelCase。
//   - 结果 items 字段名为 awardedScore/maxScore/correctAnswer/passedCases/totalCases，星级在顶层 stars。

import { ApiError, request } from '../utils/request';
import {
  ChapterTestPaperDto,
  ChapterTestPrepareDto,
  ChapterTestQuestionDto,
  ChapterTestQuestionType,
  ChapterTestResultDto,
  ChapterTestResultItemDto,
  ChapterTestSampleDto,
  ChapterTestStatus,
  ChapterTestSubmitRequest,
  ChapterTestAnswerDto,
} from './dto/chapterTest';

function isNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404;
}

// --- 后端线格式 ------------------------------------------------------------

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
      // CHOICE 及未知类型兜底按选择题渲染（防御性降级）。
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

/** 后端 JUDGE 正确答案文本 → 布尔；无法解析返回 undefined。 */
function parseJudgement(text: string | null): boolean | undefined {
  if (text === null) {
    return undefined;
  }
  const v = text.trim().toLowerCase();
  if (['true', 't', '1', 'yes', 'y', '对', '正确', '是'].indexOf(v) >= 0) {
    return true;
  }
  if (['false', 'f', '0', 'no', 'n', '错', '错误', '否'].indexOf(v) >= 0) {
    return false;
  }
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
      if (raw.correctAnswer) {
        item.correctChoice = raw.correctAnswer;
      }
      break;
    case 'JUDGE':
      item.correctJudgement = parseJudgement(raw.correctAnswer);
      break;
    case 'FILL':
      // 后端把候选集合 join(' / ') 成单串下发。
      if (raw.correctAnswer) {
        item.acceptedAnswers = raw.correctAnswer.split(' / ');
      }
      break;
    case 'CODING':
      if (raw.correctAnswer) {
        item.referenceSolution = raw.correctAnswer;
      }
      if (raw.passedCases !== null) {
        item.passedTestCases = raw.passedCases;
      }
      if (raw.totalCases !== null) {
        item.totalTestCases = raw.totalCases;
      }
      break;
  }
  return item;
}

function mapResult(raw: ResultWire | null): ChapterTestResultDto | null {
  if (!raw) {
    return null;
  }
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
      return answer.blanks.join(' ');
    case 'CODING':
      return answer.code;
  }
}

/** 触发本章章测预生成；返回当前状态机状态。 */
export function prepareTest(chapter: number): Promise<ChapterTestPrepareDto> {
  return request<ChapterTestPrepareDto, Record<string, never>>(
    `/mainline/chapters/${chapter}/test/prepare`,
    { method: 'POST', data: {} },
  );
}

/** 开考：下发去答案考卷 + deadlineAt。 */
export async function startTest(chapter: number): Promise<ChapterTestPaperDto> {
  const raw = await request<PaperWire, Record<string, never>>(
    `/mainline/chapters/${chapter}/test/start`,
    { method: 'POST', data: {} },
  );
  return mapPaper(raw);
}

/** 交卷：上送答案 + 防作弊计数；返回判分 + 解析。 */
export async function submitTest(
  chapter: number,
  testId: string,
  payload: ChapterTestSubmitRequest,
): Promise<ChapterTestResultDto> {
  const answers: Record<string, string> = {};
  payload.answers.forEach((answer) => {
    answers[answer.qid] = serializeAnswer(answer);
  });
  const body = {
    answers,
    integrity: {
      pasteBlocked: payload.integrity.paste_blocked,
      blurEvents: payload.integrity.blur_events,
      overtime: payload.integrity.overtime,
    },
  };
  const raw = await request<ResultWire, typeof body>(
    `/mainline/chapters/${chapter}/test/${testId}/submit`,
    { method: 'POST', data: body },
  );
  const result = mapResult(raw);
  if (!result) {
    throw new ApiError(0, 'EMPTY_RESPONSE', '章测判分响应为空', [], '');
  }
  return result;
}

/** 拉取本章最近一次已提交结果；尚无记录（404 / 空）→ null。 */
export async function getLatestTest(chapter: number): Promise<ChapterTestResultDto | null> {
  try {
    const raw = await request<ResultWire | null>(`/mainline/chapters/${chapter}/test/latest`);
    return mapResult(raw ?? null);
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
