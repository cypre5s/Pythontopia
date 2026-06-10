// 章末大测试组件 + 组合式 API 桶导出（R-15 A4）。
export { default as ChapterTestQuestion } from './ChapterTestQuestion.vue';
export { default as ChoiceQuestion } from './ChoiceQuestion.vue';
export { default as JudgeQuestion } from './JudgeQuestion.vue';
export { default as FillQuestion } from './FillQuestion.vue';
export { default as CodingQuestion } from './CodingQuestion.vue';
export { default as ExamCountdown } from './ExamCountdown.vue';
export { default as ResultPanel } from './ResultPanel.vue';

export {
  useChapterTestExam,
  splitFillSegments,
  countFillBlanks,
} from './useChapterTestExam';
export type { ChapterTestExam, ExamPhase, LocalAnswerState } from './useChapterTestExam';

export {
  EXAM_BLOCKED_PATH_PREFIXES,
  isExamBlockedPath,
  isExamActive,
  setExamActive,
  useExamActiveState,
  useExamRouteGuard,
  createExamRouteGuard,
} from './useExamRouteGuard';
export type { ExamRouteGuardOptions } from './useExamRouteGuard';
