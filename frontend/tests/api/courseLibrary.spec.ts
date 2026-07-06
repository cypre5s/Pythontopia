import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/api/http', () => ({
  http: { post: vi.fn() },
  ApiClientError: class ApiClientError extends Error {
    status?: number;
  },
}));

import { http } from '@/api/http';
import {
  answerCourseware,
  resetCourseLibraryCacheForTest,
  searchCourseLibrary,
} from '@/api/courseLibrary';

const ragResponse = {
  data: {
    hits: [{ page_id: 1, page_no: 12, text: 'input() 读取一行输入', score: 0.9 }],
  },
};

afterEach(() => {
  resetCourseLibraryCacheForTest();
  vi.clearAllMocks();
  localStorage.clear();
  vi.unstubAllGlobals();
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

  it('学生身份直接使用本地课件检索，不请求教师专用 RAG 端点', async () => {
    localStorage.setItem('pythontopia.user', JSON.stringify({ role: 'STUDENT' }));
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.endsWith('/documents.json')) {
          return new Response(
            JSON.stringify([
              {
                documentId: 1,
                packId: 43,
                sortOrder: 1,
                filename: 'chapter-1.pdf',
                title: '第1章 Python概述',
                pageCount: 1,
              },
            ]),
            { status: 200, headers: { 'Content-Type': 'application/json' } },
          );
        }
        if (url.endsWith('/pages-1.json')) {
          return new Response(
            JSON.stringify([
              {
                pageId: 101,
                documentId: 1,
                chapterId: 1,
                pageNo: 12,
                chunkIndex: 0,
                title: 'input函数',
                contentMarkdown: 'input函数用于读取一行输入。',
                excerpt: 'input函数用于读取一行输入。',
              },
            ]),
            { status: 200, headers: { 'Content-Type': 'application/json' } },
          );
        }
        return new Response('{}', { status: 404 });
      }),
    );

    const hits = await searchCourseLibrary('input函数', { topK: 6 });

    expect(vi.mocked(http.post)).not.toHaveBeenCalled();
    expect(hits).toHaveLength(1);
    expect(hits[0]!.pageNo).toBe(12);
    expect(hits[0]!.snippet).toContain('input函数');
  });
});

describe('answerCourseware · GraphRAG trace 归一化', () => {
  it('把后端 snake_case graph_trace 转成前端 camelCase DTO', async () => {
    vi.mocked(http.post).mockResolvedValue({
      data: {
        answer_md: '字典词频统计需要先掌握 get/items/keys/values。[页 62]',
        citations: [
          {
            page_id: 4201,
            page_no: 62,
            chapter_id: 5,
            quote: '字典 get、items、keys、values 是完成词频统计前需要掌握的基本操作。',
          },
        ],
        no_evidence: false,
        route: 'graph',
        retrieval_mode: 'graph',
        subgraph: [
          {
            from_kc: 'PY43-KC-041',
            to_kc: 'PY43-KC-042',
            relation: 'co_occur',
          },
        ],
        graph_trace: {
          route: 'graph',
          retrieval_mode: 'graph',
          top_k: 6,
          hit_count: 3,
          direct_hit_count: 2,
          supplemental_hit_count: 1,
          prerequisite_hit_count: 0,
          co_occur_hit_count: 1,
          supplemental_hit_limit: 2,
          supplemental_hit_ratio: 0.3333,
          subgraph_edge_count: 1,
          expanded_kc_count: 1,
          relation_counts: { co_occur: 1 },
          noise_guard: 'supplemental_hits_capped_at_top_k_div_3',
        },
      },
    } as never);

    const answer = await answerCourseware('为什么字典词频统计要先理解 get items keys values', {
      topK: 6,
      kcCodes: ['PY43-KC-041'],
    });

    expect(answer.route).toBe('graph');
    expect(answer.retrievalMode).toBe('graph');
    expect(answer.subgraph[0]).toEqual({
      fromKc: 'PY43-KC-041',
      toKc: 'PY43-KC-042',
      relation: 'co_occur',
    });
    expect(answer.graphTrace?.coOccurHitCount).toBe(1);
    expect(answer.graphTrace?.supplementalHitLimit).toBe(2);
    expect(answer.graphTrace?.relationCounts.co_occur).toBe(1);
  });
});
