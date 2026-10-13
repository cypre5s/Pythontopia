import { beforeEach, describe, expect, it, vi } from 'vitest';

const httpMock = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock('@/api/http', async () => {
  const actual = await vi.importActual<typeof import('@/api/http')>('@/api/http');
  return { ...actual, http: httpMock };
});

import { listChapterVideos } from '@/api/courseVideo';

beforeEach(() => {
  httpMock.get.mockReset();
});

describe('listChapterVideos', () => {
  it('rethrows request failures so the page can show an error and retry', async () => {
    httpMock.get.mockRejectedValueOnce(new Error('network unavailable'));

    await expect(listChapterVideos(1)).rejects.toThrow('network unavailable');
  });

  it('returns an empty list for a successful non-array payload', async () => {
    httpMock.get.mockResolvedValueOnce({ data: { unavailable: true } });

    await expect(listChapterVideos(1)).resolves.toEqual([]);
  });
});
