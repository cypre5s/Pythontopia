// 章测 API 客户端单测：URL / 请求体契约（后端 A1 线格式权威）、线格式 ↔ 前端 DTO 映射、
// 整洁的 404/空 latest 空态、辅助映射。
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
  serializeAnswer,
} from '@/api/chapterTest';
import type { ChapterTestSubmitRequest } from '@/api/chapterTest';
import { ApiClientError } from '@/api/http';

// 后端 ChapterTestPaperDto 线格式（record 序列化：testId 为数字，无 startedAt/totalScore）。
const PAPER_WIRE = {
  testId: 7,
  chapter: 3,
  attemptNo: 1,
  status: 'IN_PROGRESS',
  timeLimitSec: 1800,
  deadlineAt: '2026-06-10T00:30:00Z',
  items: [
    {
      qid: 'c1', type: 'CHOICE', score: 8, stem: '题',
      title: null, statementMarkdown: null, options: ['甲', '乙'], samples: [],
    },
    {
      qid: 'p1', type: 'CODING', score: 30, stem: null,
      title: '求和', statementMarkdown: '读入两个数输出和',
      options: [], samples: [{ input: '1 2', output: '3' }],
    },
  ],
};

// 后端 ChapterTestResultDto 线格式（awardedScore/maxScore/correctAnswer/stars/camelCase integrity）。
const RESULT_WIRE = {
  testId: 7,
  chapter: 3,
  attemptNo: 1,
  status: 'SUBMITTED',
  score: 72,
  maxScore: 100,
  passed: true,
  stars: 2,
  overtime: false,
  submittedAt: '2026-06-10T00:20:00Z',
  items: [
    {
      qid: 'c1', type: 'CHOICE', correct: true, awardedScore: 8, maxScore: 8,
      yourAnswer: 'B', correctAnswer: 'B', explain: '因为如此', passedCases: null, totalCases: null,
    },
    {
      qid: 'j1', type: 'JUDGE', correct: false, awardedScore: 0, maxScore: 5,
      yourAnswer: 'false', correctAnswer: 'true', explain: '判断解析', passedCases: null, totalCases: null,
    },
    {
      qid: 'f1', type: 'FILL', correct: true, awardedScore: 5, maxScore: 5,
      yourAnswer: 'range', correctAnswer: 'range / range()', explain: '填空解析',
      passedCases: null, totalCases: null,
    },
    {
      qid: 'p1', type: 'CODING', correct: false, awardedScore: 15, maxScore: 30,
      yourAnswer: 'print(1)', correctAnswer: 'print(sum(map(int, input().split())))',
      explain: '编程解析', passedCases: 2, totalCases: 4,
    },
  ],
  integrity: { pasteBlocked: 2, blurEvents: 1, overtime: false },
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

  it('quiz mode uses /quiz endpoints while final mode keeps /test endpoints', async () => {
    httpMock.post
      .mockResolvedValueOnce({ data: { status: 'GENERATING' } })
      .mockResolvedValueOnce({ data: PAPER_WIRE })
      .mockResolvedValueOnce({ data: RESULT_WIRE });
    httpMock.get.mockResolvedValueOnce({ data: null });

    await prepareTest(2, 'quiz');
    await startTest(2, 'quiz');
    await submitTest(
      2,
      '7',
      {
        answers: [],
        integrity: { paste_blocked: 0, blur_events: 0, overtime: false },
      },
      'quiz',
    );
    await getLatestTest(2, 'quiz');

    expect(httpMock.post).toHaveBeenNthCalledWith(
      1,
      '/api/mainline/chapters/2/quiz/prepare',
      {},
    );
    expect(httpMock.post).toHaveBeenNthCalledWith(
      2,
      '/api/mainline/chapters/2/quiz/start',
      {},
    );
    expect(httpMock.post).toHaveBeenNthCalledWith(
      3,
      '/api/mainline/chapters/2/quiz/7/submit',
      {
        answers: {},
        integrity: { pasteBlocked: 0, blurEvents: 0, overtime: false },
      },
    );
    expect(httpMock.get).toHaveBeenCalledWith('/api/mainline/chapters/2/quiz/latest');
  });

  it('startTest POSTs .../test/start，把线格式映射成去答案考卷', async () => {
    httpMock.post.mockResolvedValueOnce({ data: PAPER_WIRE });
    const paper = await startTest(3);
    expect(httpMock.post).toHaveBeenCalledWith('/api/mainline/chapters/3/test/start', {});
    expect(paper.testId).toBe('7'); // BIGSERIAL → string
    expect(paper.deadlineAt).toBe('2026-06-10T00:30:00Z');
    // 题面映射为判别联合，且不得含任何答案字段。
    const [choice, coding] = paper.items;
    expect(choice).toMatchObject({ qid: 'c1', type: 'CHOICE', stem: '题', options: ['甲', '乙'] });
    expect(coding).toMatchObject({
      qid: 'p1', type: 'CODING', title: '求和', statementMarkdown: '读入两个数输出和',
    });
    expect(choice as Record<string, unknown>).not.toHaveProperty('answer');
    expect(choice as Record<string, unknown>).not.toHaveProperty('explain');
  });

  it('submitTest 把判别联合答案序列化为后端 qid→string Map + camelCase integrity', async () => {
    httpMock.post.mockResolvedValueOnce({ data: RESULT_WIRE });
    const payload: ChapterTestSubmitRequest = {
      answers: [
        { qid: 'c1', type: 'CHOICE', choice: 'B' },
        { qid: 'j1', type: 'JUDGE', judgement: false },
        { qid: 'f1', type: 'FILL', blanks: ['range'] },
        { qid: 'p1', type: 'CODING', code: 'print(1)' },
      ],
      integrity: { paste_blocked: 2, blur_events: 1, overtime: false },
    };
    const res = await submitTest(3, '7', payload);
    expect(httpMock.post).toHaveBeenCalledWith(
      '/api/mainline/chapters/3/test/7/submit',
      {
        answers: { c1: 'B', j1: 'false', f1: 'range', p1: 'print(1)' },
        integrity: { pasteBlocked: 2, blurEvents: 1, overtime: false },
      },
    );
    expect(res.score).toBe(72);
    expect(res.chapterStars).toBe(2);
    // integrity 映射回前端 snake_case 镜像。
    expect(res.integrity).toEqual({ paste_blocked: 2, blur_events: 1, overtime: false });
    // 逐题映射：score=满分、awarded=实得分、按题型回填正确答案字段。
    expect(res.items[0]).toMatchObject({ qid: 'c1', score: 8, awarded: 8, correctChoice: 'B' });
    expect(res.items[1]).toMatchObject({ qid: 'j1', correctJudgement: true });
    expect(res.items[2]?.acceptedAnswers).toEqual(['range', 'range()']);
    expect(res.items[3]).toMatchObject({
      qid: 'p1', passedTestCases: 2, totalTestCases: 4,
      referenceSolution: 'print(sum(map(int, input().split())))',
    });
  });

  it('serializeAnswer 覆盖四种题型', () => {
    expect(serializeAnswer({ qid: 'c', type: 'CHOICE', choice: 'D' })).toBe('D');
    expect(serializeAnswer({ qid: 'j', type: 'JUDGE', judgement: true })).toBe('true');
    expect(serializeAnswer({ qid: 'f', type: 'FILL', blanks: ['a', 'b'] })).toBe('a b');
    expect(serializeAnswer({ qid: 'p', type: 'CODING', code: 'x=1' })).toBe('x=1');
  });

  it('getLatestTest returns the result when present', async () => {
    httpMock.get.mockResolvedValueOnce({ data: RESULT_WIRE });
    const res = await getLatestTest(3);
    expect(httpMock.get).toHaveBeenCalledWith('/api/mainline/chapters/3/test/latest');
    expect(res?.score).toBe(72);
    expect(res?.testId).toBe('7');
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
