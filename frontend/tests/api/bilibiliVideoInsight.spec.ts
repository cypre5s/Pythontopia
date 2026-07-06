import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const httpMock = vi.hoisted(() => ({
  post: vi.fn(),
}));

vi.mock('@/api/http', async () => {
  const actual = await vi.importActual<typeof import('@/api/http')>('@/api/http');
  return { ...actual, http: httpMock };
});

import { fetchBilibiliInsight } from '@/api/bilibiliVideo';

beforeEach(() => {
  httpMock.post.mockReset();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('fetchBilibiliInsight', () => {
  it('POSTs {bvid,question,kcCodes} and suppresses the global network toast', async () => {
    httpMock.post.mockResolvedValueOnce({
      data: { bvid: 'BV1', overview: 'ov', keyPoints: [], basedOn: 'metadata_only', degraded: false },
    });

    await fetchBilibiliInsight('BV1', '怎么用 for', ['KC_A']);

    expect(httpMock.post).toHaveBeenCalledWith(
      '/api/videos/bilibili/insight',
      { bvid: 'BV1', question: '怎么用 for', kcCodes: ['KC_A'] },
      { suppressNetworkToast: true },
    );
  });

  it('defaults question to "" and kcCodes to [] when omitted', async () => {
    httpMock.post.mockResolvedValueOnce({ data: {} });

    await fetchBilibiliInsight('BV1');

    expect(httpMock.post).toHaveBeenCalledWith(
      '/api/videos/bilibili/insight',
      { bvid: 'BV1', question: '', kcCodes: [] },
      { suppressNetworkToast: true },
    );
  });

  it('maps the camelCase payload into BilibiliInsightDto', async () => {
    httpMock.post.mockResolvedValueOnce({
      data: {
        bvid: 'BV1xx411c7XD',
        overview: '这视频讲 for 循环',
        keyPoints: [
          { tSec: 12, label: 'for 基本写法', source: 'ai_conclusion' },
          { tSec: 90, label: 'range step', source: 'pages' },
        ],
        basedOn: 'ai_conclusion',
        degraded: false,
      },
    });

    const dto = await fetchBilibiliInsight('BV1xx411c7XD', 'q');

    expect(dto).toEqual({
      bvid: 'BV1xx411c7XD',
      overview: '这视频讲 for 循环',
      keyPoints: [
        { tSec: 12, label: 'for 基本写法', source: 'ai_conclusion' },
        { tSec: 90, label: 'range step', source: 'pages' },
      ],
      basedOn: 'ai_conclusion',
      degraded: false,
    });
  });

  it('drops fabricated / malformed time points (anti-hallucination defense)', async () => {
    httpMock.post.mockResolvedValueOnce({
      data: {
        bvid: 'BV1',
        overview: 'ov',
        keyPoints: [
          { tSec: 30, label: '合法点', source: 'ai_conclusion' },
          { tSec: -5, label: '负秒数', source: 'ai_conclusion' },
          { tSec: Number.NaN, label: 'NaN 秒', source: 'pages' },
          { tSec: 40, label: '   ', source: 'pages' },
          { tSec: '50', label: '字符串秒数', source: 'pages' },
          { tSec: 60.7, label: '小数取整 + 未知来源兜底', source: 'foobar' },
        ],
        basedOn: 'pages',
        degraded: false,
      },
    });

    const dto = await fetchBilibiliInsight('BV1');

    expect(dto.keyPoints).toEqual([
      { tSec: 30, label: '合法点', source: 'ai_conclusion' },
      { tSec: 60, label: '小数取整 + 未知来源兜底', source: 'pages' },
    ]);
  });

  it('coerces unknown basedOn to metadata_only and only strict true counts as degraded', async () => {
    httpMock.post.mockResolvedValueOnce({
      data: { bvid: 'BV1', overview: '', keyPoints: 'nope', basedOn: 'weird', degraded: 'yes' },
    });

    const dto = await fetchBilibiliInsight('BV1');

    expect(dto.basedOn).toBe('metadata_only');
    expect(dto.keyPoints).toEqual([]);
    expect(dto.degraded).toBe(false);
  });

  it('falls back to the requested bvid + empty fields for a non-object payload', async () => {
    httpMock.post.mockResolvedValueOnce({ data: null });

    const dto = await fetchBilibiliInsight('BV_REQ');

    expect(dto).toEqual({
      bvid: 'BV_REQ',
      overview: '',
      keyPoints: [],
      basedOn: 'metadata_only',
      degraded: false,
    });
  });

  it('rethrows request failures (fail-fast, no silent degrade in the client)', async () => {
    httpMock.post.mockRejectedValueOnce(new Error('graph unavailable'));
    await expect(fetchBilibiliInsight('BV1', 'q')).rejects.toThrow('graph unavailable');
  });
});
