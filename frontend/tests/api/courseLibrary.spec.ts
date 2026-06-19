import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/api/http', () => ({
  http: { post: vi.fn() },
  ApiClientError: class ApiClientError extends Error {
    status?: number;
  },
}));

import { http } from '@/api/http';
import { resetCourseLibraryCacheForTest, searchCourseLibrary } from '@/api/courseLibrary';

const ragResponse = {
  data: {
    hits: [{ page_id: 1, page_no: 12, text: 'input() 读取一行输入', score: 0.9 }],
  },
};

afterEach(() => {
  resetCourseLibraryCacheForTest();
  vi.clearAllMocks();
});

describe('searchCourseLibrary · RAG 结果会话内缓存', () => {
  it('相同查询第二次命中内存缓存，不再调用后端', async () => {
    vi.mocked(http.post).mockResolvedValue(ragResponse as never);

    const first = await searchCourseLibrary('input函数', { topK: 6 });
    const second = await searchCourseLibrary('input函数', { topK: 6 });

    expect(first).toEqual(second);
    expect(first).toHaveLength(1);
    expect(first[0]!.pageNo).toBe(12);
    expect(vi.mocked(http.post)).toHaveBeenCalledTimes(1);
  });

  it('reset 后缓存清空，会重新调用后端', async () => {
    vi.mocked(http.post).mockResolvedValue(ragResponse as never);

    await searchCourseLibrary('列表', {});
    resetCourseLibraryCacheForTest();
    await searchCourseLibrary('列表', {});

    expect(vi.mocked(http.post)).toHaveBeenCalledTimes(2);
  });

  it('不同 topK 视为不同查询，各自缓存', async () => {
    vi.mocked(http.post).mockResolvedValue(ragResponse as never);

    await searchCourseLibrary('for循环', { topK: 6 });
    await searchCourseLibrary('for循环', { topK: 12 });

    expect(vi.mocked(http.post)).toHaveBeenCalledTimes(2);
  });

  it('返回数组副本，调用方改写不污染缓存', async () => {
    vi.mocked(http.post).mockResolvedValue(ragResponse as never);

    const a = await searchCourseLibrary('字典', {});
    a.pop();
    const b = await searchCourseLibrary('字典', {});

    expect(b).toHaveLength(1);
  });
});
