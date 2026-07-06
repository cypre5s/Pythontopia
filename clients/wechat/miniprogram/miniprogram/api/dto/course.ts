export interface CoursePack {
  packId: number;
  name: string;
  primaryLanguage: string;
  documentCount: number;
  pageCount: number;
  chapterCount: number;
  kcCount: number;
  exampleCount: number;
  problemCount: number;
}

export interface Chapter {
  chapterId: number;
  orderIndex: number;
  title: string;
  summaryMarkdown: string | null;
}

export interface Kc {
  kcId: number;
  kcCode: string;
  kcName: string;
  chapterId: number;
  chapterOrder: number;
  difficulty: string | null;
  summaryMarkdown: string | null;
}

export interface CoursePage {
  pageId: number;
  documentId: number;
  chapterId: number;
  pageNo: number;
  title: string | null;
  contentMarkdown: string | null;
  excerpt: string | null;
}

export interface CourseProblem {
  problemId: number;
  kcCode: string;
  title: string;
  statementMarkdown: string | null;
  inputDescription: string | null;
  outputDescription: string | null;
  difficulty: string | null;
  sourceLabel: string | null;
}

export interface CourseVideo {
  id: number;
  chapter: number;
  title: string;
  durationSec: number | null;
  kcCodes: string[];
  available: boolean;
}
