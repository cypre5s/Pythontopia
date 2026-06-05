// 课程包 43（Python语言基础）的图书馆/题库/示例库前端访问层。
//
// 后端真实端点（CoursePackController）目前仅暴露：
//   GET /api/course/pack
//   GET /api/course/chapters
//   GET /api/course/kcs[?chapterId=]
//   GET /api/course/pages?kcCode=
//   GET /api/course/problems/{id}
//
// 后端暂未提供、用 fixture fallback 兜底的端点：
//   GET /api/course/documents                 → fixture: /data/course43/documents.json
//   GET /api/course/problems                  → fixture: /data/course43/problems.json
//   GET /api/course/examples[?kcCode=]        → fixture: /data/course43/examples.json
//   GET /api/course/pages/{docId}/{pageNo}    → fixture: /data/course43/pages-{docId}.json
//   POST /api/rag/query  (学生角色)           → fixture: 关键字全文检索
//
// 学生提交进度（NOT_TRIED / PASSED / FAILED）通过 `GET /api/submissions` (listSubmissions)
// 聚合，避免新增专用端点。

import { http, ApiClientError } from './http';
import { listSubmissions } from './submissions';
import type {
  CourseChapterSummary,
  CourseDocumentSummary,
  CourseExampleSummary,
  CourseKcSummary,
  CourseLibraryManifest,
  CoursePageDetail,
  CourseProblemSummary,
  LibrarySearchHit,
  PlayerProblemProgress,
  PlayerProblemStatus,
} from './dto/Library';
import type { ChapterDto } from './dto/ChapterDto';
import type { SubmissionDto } from './dto/Submission';

const FIXTURE_BASE = '/data/course43';
const RAG_TOP_K = 6;
const RAG_SNIPPET_LEN = 180;

const documentsCache = new Map<number, CoursePageDetail[]>();
let documentSummariesCache: CourseDocumentSummary[] | null = null;
let problemSummariesCache: CourseProblemSummary[] | null = null;
let exampleSummariesCache: CourseExampleSummary[] | null = null;
let chaptersSummariesCache: CourseChapterSummary[] | null = null;
let kcSummariesCache: CourseKcSummary[] | null = null;
let manifestCache: CourseLibraryManifest | null = null;

interface FetchLike {
  (input: string, init?: RequestInit): Promise<Response>;
}

function getFetch(): FetchLike {
  if (typeof fetch !== 'function') {
    throw new Error('fetch is not available in the current runtime');
  }
  return fetch as FetchLike;
}

async function loadFixture<T>(name: string): Promise<T> {
  const f = getFetch();
  const response = await f(`${FIXTURE_BASE}/${name}`, {
    headers: { Accept: 'application/json' },
    cache: 'force-cache',
  });
  if (!response.ok) {
    throw new Error(`Failed to load fixture ${name}: HTTP ${response.status}`);
  }
  return (await response.json()) as T;
}

export async function loadManifest(): Promise<CourseLibraryManifest> {
  if (manifestCache) {
    return manifestCache;
  }
  manifestCache = await loadFixture<CourseLibraryManifest>('manifest.json');
  return manifestCache;
}

export async function listCourseDocuments(): Promise<CourseDocumentSummary[]> {
  if (documentSummariesCache) {
    return documentSummariesCache;
  }
  const docs = await loadFixture<CourseDocumentSummary[]>('documents.json');
  documentSummariesCache = docs.slice().sort((a, b) => a.sortOrder - b.sortOrder);
  return documentSummariesCache;
}

export async function listAllProblems(): Promise<CourseProblemSummary[]> {
  if (problemSummariesCache) {
    return problemSummariesCache;
  }
  problemSummariesCache = await loadFixture<CourseProblemSummary[]>('problems.json');
  return problemSummariesCache;
}

export async function listAllExamples(): Promise<CourseExampleSummary[]> {
  if (exampleSummariesCache) {
    return exampleSummariesCache;
  }
  exampleSummariesCache = await loadFixture<CourseExampleSummary[]>('examples.json');
  return exampleSummariesCache;
}

export async function listChapterFallbacks(): Promise<ChapterDto[]> {
  if (chaptersSummariesCache) {
    return chaptersSummariesCache.map(toChapterDto);
  }
  chaptersSummariesCache = await loadFixture<CourseChapterSummary[]>('chapters.json');
  return chaptersSummariesCache.map(toChapterDto);
}

function toChapterDto(chapter: CourseChapterSummary): ChapterDto {
  return {
    chapterId: chapter.chapterId,
    orderIndex: chapter.orderIndex,
    title: chapter.title,
    summaryMarkdown: chapter.summary,
  };
}

export async function listKcFallbacks(): Promise<CourseKcSummary[]> {
  if (kcSummariesCache) {
    return kcSummariesCache;
  }
  kcSummariesCache = await loadFixture<CourseKcSummary[]>('kcs.json');
  return kcSummariesCache;
}

async function loadDocumentPages(documentId: number): Promise<CoursePageDetail[]> {
  const cached = documentsCache.get(documentId);
  if (cached) {
    return cached;
  }
  const pages = await loadFixture<CoursePageDetail[]>(`pages-${documentId}.json`);
  documentsCache.set(documentId, pages);
  return pages;
}

export async function listDocumentPages(documentId: number): Promise<CoursePageDetail[]> {
  return loadDocumentPages(documentId);
}

export async function getDocumentPage(
  documentId: number,
  pageNo: number,
): Promise<CoursePageDetail | null> {
  const pages = await loadDocumentPages(documentId);
  return pages.find((page) => page.pageNo === pageNo) ?? null;
}

export interface RagSearchOptions {
  topK?: number;
  kcCodes?: string[];
  documentId?: number;
}

export async function searchCourseLibrary(
  query: string,
  options: RagSearchOptions = {},
): Promise<LibrarySearchHit[]> {
  const trimmed = query.trim();
  if (!trimmed) {
    return [];
  }
  const topK = Math.max(1, Math.min(options.topK ?? RAG_TOP_K, 20));

  // 学生角色无法直接调 /api/rag/query（限教师/管理员），先尝试后端，403/失败时回退本地关键字检索。
  try {
    const response = await http.post<{
      hits: Array<{
        page_id?: number;
        pageId?: number;
        page_no?: number;
        pageNo?: number;
        chapter_id?: number | null;
        kc_codes?: string[];
        text: string;
        score: number;
      }>;
    }>(`/api/rag/query`, {
      query: trimmed,
      top_k: topK,
      kc_codes: options.kcCodes ?? null,
    });
    const hits = response.data.hits ?? [];
    if (hits.length > 0) {
      return hits.map((hit) => ({
        pageId: hit.pageId ?? hit.page_id ?? 0,
        documentId: 0,
        pageNo: hit.pageNo ?? hit.page_no ?? 0,
        title: '',
        snippet: hit.text,
        score: hit.score,
      }));
    }
  } catch (error) {
    if (!(error instanceof ApiClientError) || (error.status !== 403 && error.status !== 401)) {
      // 真实异常（非鉴权问题）继续抛出，让上层 toast 提示；学生鉴权失败则静默降级。
      throw error;
    }
  }

  return localKeywordSearch(trimmed, topK, options);
}

async function localKeywordSearch(
  query: string,
  topK: number,
  options: RagSearchOptions,
): Promise<LibrarySearchHit[]> {
  const docs = await listCourseDocuments();
  const docFilter = options.documentId ?? null;
  const lowerQuery = query.toLowerCase();
  const terms = Array.from(new Set(lowerQuery.split(/\s+/).filter(Boolean)));
  const hits: LibrarySearchHit[] = [];

  for (const doc of docs) {
    if (docFilter !== null && doc.documentId !== docFilter) {
      continue;
    }
    const pages = await loadDocumentPages(doc.documentId);
    for (const page of pages) {
      const haystack = (page.contentMarkdown || '').toLowerCase();
      if (!haystack) continue;
      let score = 0;
      for (const term of terms) {
        if (!term) continue;
        const occurrences = haystack.split(term).length - 1;
        if (occurrences > 0) {
          score += occurrences * (term.length >= 2 ? 2 : 1);
        }
      }
      if (score > 0) {
        hits.push({
          pageId: page.pageId,
          documentId: doc.documentId,
          pageNo: page.pageNo,
          title: page.title || doc.title,
          snippet: extractSnippet(page.contentMarkdown, query),
          score,
        });
      }
    }
  }

  hits.sort((a, b) => b.score - a.score);
  return hits.slice(0, topK);
}

function extractSnippet(content: string, query: string): string {
  if (!content) {
    return '';
  }
  const lower = content.toLowerCase();
  const idx = lower.indexOf(query.toLowerCase());
  if (idx === -1) {
    return content.slice(0, RAG_SNIPPET_LEN);
  }
  const start = Math.max(0, idx - 40);
  const end = Math.min(content.length, idx + RAG_SNIPPET_LEN);
  const prefix = start > 0 ? '…' : '';
  const suffix = end < content.length ? '…' : '';
  return `${prefix}${content.slice(start, end)}${suffix}`;
}

export async function loadPlayerProblemProgress(
  problemIds: number[],
): Promise<Map<number, PlayerProblemProgress>> {
  const ids = Array.from(new Set(problemIds.filter((id) => Number.isFinite(id) && id > 0)));
  const map = new Map<number, PlayerProblemProgress>();
  if (ids.length === 0) {
    return map;
  }
  let submissions: SubmissionDto[] = [];
  try {
    submissions = await listSubmissions({ limit: 200 });
  } catch (error) {
    void error;
    return map;
  }
  for (const id of ids) {
    map.set(id, {
      problemId: id,
      status: 'NOT_TRIED',
      attempts: 0,
      lastSubmittedAt: null,
    });
  }
  for (const submission of submissions) {
    const existing = map.get(submission.problemId);
    if (!existing) continue;
    existing.attempts += 1;
    if (!existing.lastSubmittedAt || submission.submittedAt > existing.lastSubmittedAt) {
      existing.lastSubmittedAt = submission.submittedAt;
    }
    existing.status = mergeStatus(existing.status, submission.status);
  }
  return map;
}

function mergeStatus(prev: PlayerProblemStatus, incoming: SubmissionDto['status']): PlayerProblemStatus {
  if (prev === 'PASSED') {
    return 'PASSED';
  }
  if (incoming === 'PASSED') {
    return 'PASSED';
  }
  if (incoming === 'FAILED' || incoming === 'ERROR') {
    return 'FAILED';
  }
  return prev;
}

export function resetCourseLibraryCacheForTest(): void {
  documentSummariesCache = null;
  problemSummariesCache = null;
  exampleSummariesCache = null;
  chaptersSummariesCache = null;
  kcSummariesCache = null;
  manifestCache = null;
  documentsCache.clear();
}
