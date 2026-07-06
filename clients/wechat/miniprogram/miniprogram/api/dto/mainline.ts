// R-15 主线域（mainline）契约 DTO（小程序端镜像 frontend/src/api/mainline.ts）。
//
// 后端权威：地图 / 星级 / stage 三件套（看·学·练）全部以后端为准，本端只做
// 「typed DTO + thin fetch + 打点」，不做星级公式 / 解锁 / 补星等业务判定。
// 字段命名严格对齐 web 端（REST 层 camelCase），不自创字段。

/** 章末大测试状态（overview 内含 `NONE` = 尚未创建测试记录）。 */
export type ChapterTestStatus =
  | 'NONE'
  | 'PENDING'
  | 'GENERATING'
  | 'READY'
  | 'IN_PROGRESS'
  | 'SUBMITTED'
  | 'EXPIRED'
  | 'FAILED';

/** 小节「练」客观题题型（OJ 编程题不在此）。 */
export type PracticeQuestionType = 'CHOICE' | 'JUDGE' | 'FILL';

/**
 * 星级构成明细（后端 chapter_star.breakdown 镜像）。
 * test/stage/mastery 为各路径贡献星数（0/1），recovered 为已生效补星来源标签集合。
 */
export interface StarBreakdownDto {
  test: number;
  stage: number;
  mastery: number;
  recovered: string[];
}

/** 本章补星完成情况（overview.recovery）。 */
export interface RecoveryStatusDto {
  /** 错题复盘补星是否已发放（本章 bug-mine RESOLVED ≥ 3）。 */
  errorbookDone: boolean;
  /** 无尽挑战补星是否已发放。 */
  endlessDone: boolean;
}

/**
 * 小节（stage）在 overview 内的进度视图。
 * 注意：pagesRead / examplesDone 在 overview 内是**计数**（用于「X / required」进度条），
 * 而非 id 数组；id 集合只在 stage 学习页按需取（StageDetailDto / StageProgressDto）。
 */
export interface MainlineStageDto {
  id: number;
  index: number;
  title: string;
  videoWatchedSec: number;
  requiredVideoSec: number;
  pagesRead: number;
  requiredPages: number;
  examplesDone: number;
  requiredExamples: number;
  completed: boolean;
}

/** 大章节（chapter）在 overview 内的视图。 */
export interface MainlineChapterDto {
  chapter: number;
  title: string;
  /** 是否已解锁（后端权威）。 */
  unlocked: boolean;
  /** 0–3★（chapter_star 权威）。 */
  stars: number;
  /** 章末大测试历史最高分；从未参试为 null。 */
  bestTestScore: number | null;
  stages: MainlineStageDto[];
  testStatus: ChapterTestStatus;
  starBreakdown: StarBreakdownDto;
  recovery: RecoveryStatusDto;
}

/** GET /api/mainline/overview 响应（冒险地图一次拉齐）。 */
export interface MainlineOverviewDto {
  totalStars: number;
  chapters: MainlineChapterDto[];
}

/** 小节「看」内置课程视频条目（字节流走 /api/course/videos/{id}）。 */
export interface StageVideoDto {
  id: number;
  title: string;
  durationSec: number;
}

/** 小节「练」OJ 编程题展示条目（id 为 course_problem.id，走训练场判题）。 */
export interface StageExampleDto {
  id: number;
  displayNo: string;
  displayName: string;
  kcCode: string;
}

/**
 * 小节「练」客观题题面（下发学生端，**不含正确答案**）。
 * - CHOICE：options 为选项文本（单选）；
 * - JUDGE：options 为空（前端用「正确 / 错误」）；
 * - FILL：blankCount 个填空，options 为空。
 */
export interface StagePracticeQuestionDto {
  id: number;
  kcCode: string;
  type: PracticeQuestionType;
  ordinal: number;
  stem: string;
  options: string[];
  blankCount: number;
}

/** 客观题判分结果（作答后回传正确答案 + 讲解，供自检；不提前泄题）。 */
export interface PracticeAnswerResultDto {
  questionId: number;
  correct: boolean;
  correctAnswers: string[];
  explanation: string;
}

/**
 * stage 学习页详情（GET /api/mainline/stages/{stageId}）：
 * 静态定义（kcCodes / videoKeyword / coursewarePageIds / exampleProblemIds）+ 当前用户已完成 id 集合。
 */
export interface StageDetailDto {
  id: number;
  chapter: number;
  chapterTitle: string;
  index: number;
  title: string;
  kcCodes: string[];
  videoKeyword: string;
  coursewarePageIds: number[];
  exampleProblemIds: number[];
  requiredVideoSec: number;
  requiredPages: number;
  requiredExamples: number;
  videoWatchedSec: number;
  pagesReadIds: number[];
  examplesDoneIds: number[];
  completed: boolean;
  /** 看：本章内置课程视频。 */
  videos?: StageVideoDto[];
  /** 练：本小节 OJ 编程题。 */
  examples?: StageExampleDto[];
  /** 练：本小节客观题题面。 */
  questions?: StagePracticeQuestionDto[];
  /** 练：需答对的客观题数。 */
  requiredQuestions?: number;
  /** 练：已答对的客观题 id 集合。 */
  questionsCorrectIds?: number[];
}

/**
 * stage 三件套打点端点的统一返回（video-progress / page-read / example-done 共用）。
 * 字段为后端重算后的该 stage 最新进度。
 */
export interface StageProgressDto {
  stageId: number;
  chapter: number;
  index: number;
  title: string;
  videoWatchedSec: number;
  requiredVideoSec: number;
  pagesRead: number;
  requiredPages: number;
  examplesDone: number;
  requiredExamples: number;
  completed: boolean;
  pagesReadIds?: number[];
  examplesDoneIds?: number[];
}

/** 无尽模式轮次小结上报体。 */
export interface EndlessSummaryInput {
  chapter: number;
  correct: number;
  total: number;
  streak: number;
}

/** 补星类端点返回（无尽小结 / 错题复盘）：本次是否发放了一颗补星（幂等，后端权威）。 */
export interface StarAwardResultDto {
  starAwarded: boolean;
}
