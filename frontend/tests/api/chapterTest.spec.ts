// 章测 API 客户端单测：URL / 请求体契约（§9）、整洁的 404/空 latest 空态、辅助映射。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const httpMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}));

vi.mock('@/api/http', async () => {
  const actual = await vi.importActual<typeof import('@/api/http')>('@/api/http');
  return { ...actual, http: httpMock };
});

import {
  prepareTest,
  startTest,
  submitTest,
  getLatestTest,
  optionLetter,
  letterToIndex,
} from '@/api/chapterTest';
import type {
  ChapterTestPaperDto,
  ChapterTestResultDto,
  ChapterTestSubmitRequest,
} from '@/api/chapterTest';
import { ApiClientError } from '@/api/http';

const PAPER: ChapterTestPaperDto = {
  testId: 't-7',
  chapter: 3,
  attemptNo: 1,
  status: 'IN_PROGRESS',
  timeLimitSec: 1800,
  startedAt: '2026-06-10T00:00:00Z',
  deadlineAt: '2026-06-10T00:30:00Z',
  totalScore: 100,
  items: [
    { qid: 'c1', type: 'CHOICE', score: 8, stem: '题', options: ['甲', '乙'] },
  ],
};

const RESULT: ChapterTestResultDto = {
  testId: 't-7',
  chapter: 3,
  attemptNo: 1,
  status: 'SUBMITTED',
  score: 72,
  maxScore: 100,
  passed: true,
  submittedAt: '2026-06-10T00:20:00Z',
  integrity: { paste_blocked: 0, blur_events: 0, overtime: false },
  items: [],
};

beforeEach(() => {
  httpMock.get.mockReset();
  httpMock.post.mockReset();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('chapterTest api client', () => {
  it('prepareTest POSTs /api/mainline/chapters/{ch}/test/prepare', async () => {
    httpMock.post.mockResolvedValueOnce({ data: { status: 'GENERATING' } });
    const res = await prepareTest(5);
    expect(httpMock.post).toHaveBeenCalledWith('/api/mainline/chapters/5/test/prepare', {});
    expect(res.status).toBe('GENERATING');
  });

  it('startTest POSTs .../test/start and returns answer-stripped paper', async () => {
    httpMock.post.mockResolvedValueOnce({ data: PAPER });
    const paper = await startTest(3);
    expect(httpMock.post).toHaveBeenCalledWith('/api/mainline/chapters/3/test/start', {});
    expect(paper.testId).toBe('t-7');
    expect(paper.deadlineAt).toBe('2026-06-10T00:30:00Z');
    // 题面不得含任何答案字段。
    const first = paper.items[0];
    expect(first).toBeDefined();
    expect(first as Record<string, unknown>).not.toHaveProperty('answer');
    expect(first as Record<string, unknown>).not.toHaveProperty('explain');
  });

  it('submitTest POSTs {answers, integrity} to .../test/{testId}/submit', async () => {
    httpMock.post.mockResolvedValueOnce({ data: RESULT });
    const payload: ChapterTestSubmitRequest = {
      answers: [
        { qid: 'c1', type: 'CHOICE', choice: 'B' },
        { qid: 'p1', type: 'CODING', code: 'print(1)' },
      ],
      integrity: { paste_blocked: 2, blur_events: 1, overtime: false },
    };
    const res = await submitTest(3, 't-7', payload);
    expect(httpMock.post).toHaveBeenCalledWith(
      '/api/mainline/chapters/3/test/t-7/submit',
      payload,
    );
    expect(res.score).toBe(72);
  });

  it('getLatestTest returns the result when present', async () => {
    httpMock.get.mockResolvedValueOnce({ data: RESULT });
    const res = await getLatestTest(3);
    expect(httpMock.get).toHaveBeenCalledWith('/api/mainline/chapters/3/test/latest');
    expect(res?.score).toBe(72);
  });

  it('getLatestTest maps empty body (null data) → null', async () => {
    httpMock.get.mockResolvedValueOnce({ data: null });
    await expect(getLatestTest(3)).resolves.toBeNull();
  });

  it('getLatestTest maps wrapped 404 ApiClientError → null', async () => {
    httpMock.get.mockRejectedValueOnce(
      new ApiClientError(
        { code: 'NOT_FOUND', message: '尚无章测记录', details: [], traceId: 't', timestamp: '' },
        404,
      ),
    );
    await expect(getLatestTest(3)).resolves.toBeNull();
  });

  it('getLatestTest rethrows non-404 errors', async () => {
    httpMock.get.mockRejectedValueOnce(
      new ApiClientError(
        { code: 'INTERNAL_ERROR', message: 'boom', details: [], traceId: 't', timestamp: '' },
        500,
      ),
    );
    await expect(getLatestTest(3)).rejects.toBeInstanceOf(ApiClientError);
  });

  it('optionLetter / letterToIndex round-trip', () => {
    expect(optionLetter(0)).toBe('A');
    expect(optionLetter(1)).toBe('B');
    expect(letterToIndex('A')).toBe(0);
    expect(letterToIndex('c')).toBe(2);
  });
});
