// 由 fixture 投影出的库阅读器 / 题库 / 示例库 DTO，来源 data/course-pack-43/json/*
// （经 frontend/scripts/sync-course43.mjs 生成）。
// 后端目前仅提供 GET /api/course/pack|chapters|kcs|pages|problems/{id}，列表 / 取页接口尚未上线。

export interface CourseDocumentSummary {
  documentId: number;
  packId: number;
  sortOrder: number;
  filename: string;
  title: string;
  pageCount: number;
}

export interface CourseChapterSummary {
  chapterId: number;
  packId: number;
  orderIndex: number;
  title: string;
  summary: string;
}

export interface CourseKcSummary {
  kcId: number;
  packId: number;
  kcCode: string;
  kcName: string;
  chapterId: number;
  chapterOrder: number;
  difficulty: string | null;
  summary: string;
  pageRefs: number[];
}

export type CourseProblemDifficulty = 'Low' | 'Medium' | 'High';

export interface CourseProblemSample {
  input: string;
  output: string;
}

export interface CourseProblemSummary {
  problemId: number;
  packId: number;
  kcCode: string;
  title: string;
  difficulty: CourseProblemDifficulty;
  statementMarkdown: string;
  inputDescription: string;
  outputDescription: string;
  sourceLabel: string;
  sourcePages: number[];
  teachingExplanation: string;
  commonMistakes: string[];
  samples: CourseProblemSample[];
}

export interface CourseExampleSummary {
  exampleId: number;
  packId: number;
  documentId: number;
  kcCode: string;
  title: string;
  code: string;
  explanationMarkdown: string;
  inputDescription: string;
  outputDescription: string;
  pageRefs: number[];
}

export interface CoursePageDetail {
  pageId: number;
  documentId: number;
  chapterId: number | null;
  pageNo: number;
  chunkIndex: number;
  title: string;
  contentMarkdown: string;
  excerpt: string;
}

export interface CourseLibraryManifest {
  packId: number;
  name: string;
  generatedAt: string;
  documentCount: number;
  chapterCount: number;
  kcCount: number;
  problemCount: number;
  exampleCount: number;
  pageCount: number;
  documents: Array<{
    documentId: number;
    title: string;
    pageCount: number;
  }>;
}

export interface LibrarySearchHit {
  pageId: number;
  documentId: number;
  pageNo: number;
  title: string;
  snippet: string;
  score: number;
}

export type PlayerProblemStatus = 'NOT_TRIED' | 'PASSED' | 'FAILED';

export interface PlayerProblemProgress {
  problemId: number;
  status: PlayerProblemStatus;
  attempts: number;
  lastSubmittedAt: string | null;
}
