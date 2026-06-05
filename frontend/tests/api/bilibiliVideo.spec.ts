import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const httpMock = vi.hoisted(() => ({
  get: vi.fn(),
}));

vi.mock('@/api/http', async () => {
  const actual = await vi.importActual<typeof import('@/api/http')>('@/api/http');
  return { ...actual, http: httpMock };
});

import { ApiClientError } from '@/api/http';
import { searchBilibili } from '@/api/bilibiliVideo';

beforeEach(() => {
  httpMock.get.mockReset();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('searchBilibili', () => {
  it('returns the DTO list and forwards trimmed keyword + limit params', async () => {
    httpMock.get.mockResolvedValueOnce({
      data: [
        {
          bvid: 'BV1xx411c7XD',
          title: 'for 循环',
          author: 'UP主',
          description: '讲解 for 循环与 range 的用法',
          coverUrl: 'https://i2.hdslb.com/x.jpg',
          durationSec: 754,
          play: 12345,
          danmaku: 678,
          pubdate: 1600000000,
          url: 'https://www.bilibili.com/video/BV1xx411c7XD',
        },
      ],
    });

    const videos = await searchBilibili('  for循环 Python  ', 6);

    expect(httpMock.get).toHaveBeenCalledWith('/api/videos/bilibili', {
      params: { keyword: 'for循环 Python', limit: 6 },
      suppressNetworkToast: true,
    });
    expect(videos).toHaveLength(1);
    expect(videos[0].bvid).toBe('BV1xx411c7XD');
    expect(videos[0].coverUrl).toBe('https://i2.hdslb.com/x.jpg');
    expect(videos[0].description).toBe('讲解 for 循环与 range 的用法');
  });

  it('defaults limit to 8', async () => {
    httpMock.get.mockResolvedValueOnce({ data: [] });
    await searchBilibili('列表');
    expect(httpMock.get).toHaveBeenCalledWith('/api/videos/bilibili', {
      params: { keyword: '列表', limit: 8 },
      suppressNetworkToast: true,
    });
  });

  it('returns [] without hitting the network when keyword is blank', async () => {
    const videos = await searchBilibili('   ');
    expect(videos).toEqual([]);
    expect(httpMock.get).not.toHaveBeenCalled();
  });

  it('degrades to [] on generic request failure (no throw)', async () => {
    httpMock.get.mockRejectedValueOnce(new Error('network'));
    const videos = await searchBilibili('for循环');
    expect(videos).toEqual([]);
  });

  it('rethrows VIDEO_SEARCH_UNAVAILABLE so the UI can show service error', async () => {
    httpMock.get.mockRejectedValueOnce(
      new ApiClientError(
        { code: 'VIDEO_SEARCH_UNAVAILABLE', message: 'B站视频检索服务不可用', details: [] },
        502,
      ),
    );
    await expect(searchBilibili('for循环')).rejects.toMatchObject({
      code: 'VIDEO_SEARCH_UNAVAILABLE',
    });
  });

  it('returns [] when the backend payload is not an array', async () => {
    httpMock.get.mockResolvedValueOnce({ data: { oops: true } });
    const videos = await searchBilibili('for循环');
    expect(videos).toEqual([]);
  });
});
