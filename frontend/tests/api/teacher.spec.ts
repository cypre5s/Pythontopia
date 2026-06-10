import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// 单测对 http 层 mock：只验证 R-15 主线看板 4 个 fetcher 的 URL 契约与返回透传，绝不直连真实后端。
const httpMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
}));

vi.mock('@/api/http', () => ({
  http: httpMock,
}));

import {
  fetchChapterTests,
  fetchEndlessActivity,
  fetchMainlineProgress,
  fetchMainlineStars,
} from '@/api/teacher';

beforeEach(() => {
  httpMock.get.mockReset();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('fetchMainlineStars', () => {
  it('GET /dashboard/mainline-stars 并透传 data', async () => {
    httpMock.get.mockResolvedValueOnce({
      data: { totalStudents: 3, chapters: [], topStudents: [] },
    });
    const dto = await fetchMainlineStars(9);
    expect(httpMock.get).toHaveBeenCalledWith(
      '/api/teacher/classrooms/9/dashboard/mainline-stars',
    );
    expect(dto.totalStudents).toBe(3);
  });
});

describe('fetchChapterTests', () => {
  it('GET /dashboard/chapter-tests 并透传 data', async () => {
    httpMock.get.mockResolvedValueOnce({
      data: { totalStudents: 4, chapters: [] },
    });
    const dto = await fetchChapterTests(9);
    expect(httpMock.get).toHaveBeenCalledWith(
      '/api/teacher/classrooms/9/dashboard/chapter-tests',
    );
    expect(dto.totalStudents).toBe(4);
  });
});

describe('fetchEndlessActivity', () => {
  it('GET /dashboard/endless-activity 并透传 data', async () => {
    httpMock.get.mockResolvedValueOnce({
      data: {
        periodStart: '2026-06-04',
        periodEnd: '2026-06-10',
        totalStudents: 5,
        activeStudentCount: 2,
        recoveryRoundTotal: 3,
        recoveryStarTotal: 3,
        accuracyPct: null,
        daily: [],
        chapters: [],
      },
    });
    const dto = await fetchEndlessActivity(9);
    expect(httpMock.get).toHaveBeenCalledWith(
      '/api/teacher/classrooms/9/dashboard/endless-activity',
    );
    expect(dto.recoveryStarTotal).toBe(3);
    expect(dto.accuracyPct).toBeNull();
  });
});

describe('fetchMainlineProgress', () => {
  it('GET /dashboard/mainline-progress 并透传 data', async () => {
    httpMock.get.mockResolvedValueOnce({
      data: { totalStudents: 2, totalStages: 7, chapterStageCounts: [], members: [] },
    });
    const dto = await fetchMainlineProgress(9);
    expect(httpMock.get).toHaveBeenCalledWith(
      '/api/teacher/classrooms/9/dashboard/mainline-progress',
    );
    expect(dto.totalStages).toBe(7);
  });
});
