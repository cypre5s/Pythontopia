// 章末大测试（chapter test）契约 DTO（小程序端镜像 frontend/src/api/chapterTest.ts）。
//
// 安全要点（与后端对齐）：
//   - 后端唯一权威：题目预生成入库，考试期间零 AI 调用。
//   - start 下发**去答案**题面（CHOICE 不带 answer、JUDGE/FILL 不带 answer、
//     CODING 不带 reference_solution / hidden_test_cases / evidence_page_ids）。
//   - submit 仅在提交后返回每题解析 + 正确答案 + 判分明细。
// 本文件只声明前端可见 DTO；后端线格式 → 前端 DTO 的映射在 api/chapterTest.ts。

/** 章测四种题型。 */
export type ChapterTestQuestionType = 'CHOICE' | 'JUDGE' | 'FILL' | 'CODING';

/** chapter_test.status 状态机（不含 overview 专用的 NONE）。 */
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
  /** 题内唯一 id（如 "c1"/"j1"/"f1"/"p1"）。 */
  qid: string;
  /** 该题分值。 */
  score: number;
  /** 关联知识点编码（可选）。 */
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

/** 填空题（去答案）。题干含连续下划线占位。 */
export interface ChapterTestFillQuestionDto extends ChapterTestQuestionBaseDto {
  type: 'FILL';
  stem: string;
  blankCount?: number;
}

/** 编程题（去答案）。 */
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

/** start 返回的去答案考卷 + 截止时间。 */
export interface ChapterTestPaperDto {
  /** chapter_test.id（用 string 兼容 BIGSERIAL，避免 JS number 精度问题）。 */
  testId: string;
  chapter: number;
  attemptNo: number;
  status: ChapterTestStatus;
  timeLimitSec: number;
  /** ISO 时间：倒计时锚点，到点自动交卷。 */
  deadlineAt: string;
  /** 去答案题面列表。 */
  items: ChapterTestQuestionDto[];
}

/** prepare：触发预生成，返回当前状态。 */
export interface ChapterTestPrepareDto {
  status: ChapterTestStatus;
}

/** 防作弊计数（线格式镜像 chapter_test.integrity 的 snake_case key）。 */
export interface ChapterTestIntegrityDto {
  /** 编程题输入框被拦截的粘贴次数。 */
  paste_blocked: number;
  /** 考试期间页面失焦次数。 */
  blur_events: number;
  /** 是否超时（到点自动交卷 / 截止后提交）。 */
  overtime: boolean;
}

/**
 * 提交答案（判别联合，后端按 type 分派判分）。
 *   - CHOICE：所选项字母 "A".."D"。
 *   - JUDGE：true/false。
 *   - FILL：各空位文本。
 *   - CODING：源代码（正式判题在服务端沙箱）。
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
  score: number;
  awarded: number;
  correct: boolean;
  explain: string;
  kcCode?: string;
  evidencePageIds?: number[];
  correctChoice?: string;
  correctJudgement?: boolean;
  acceptedAnswers?: string[];
  referenceSolution?: string;
  passedTestCases?: number;
  totalTestCases?: number;
}

/** submit / latest 返回的判分结果。 */
export interface ChapterTestResultDto {
  testId: string;
  chapter: number;
  attemptNo: number;
  status: ChapterTestStatus;
  score: number;
  maxScore: number;
  passed: boolean;
  submittedAt: string;
  integrity: ChapterTestIntegrityDto;
  items: ChapterTestResultItemDto[];
  /** 提交后本章星级（可选）。 */
  chapterStars?: number;
}
