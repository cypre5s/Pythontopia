import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const httpMock = vi.hoisted(() => ({
  post: vi.fn(),
}));

vi.mock('@/api/http', async () => {
  const actual = await vi.importActual<typeof import('@/api/http')>('@/api/http');
  return { ...actual, http: httpMock };
});

import { transcribeAudio } from '@/api/media';

beforeEach(() => {
  httpMock.post.mockReset();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('transcribeAudio', () => {
  it('uploads the recording as multipart form-data and returns the transcript', async () => {
    httpMock.post.mockResolvedValueOnce({ data: { text: '什么是列表推导', degraded: false } });

    const blob = new Blob(['fake-audio'], { type: 'audio/webm' });
    const res = await transcribeAudio(blob);

    expect(res).toEqual({ text: '什么是列表推导', degraded: false });
    expect(httpMock.post).toHaveBeenCalledTimes(1);
    const [url, body, config] = httpMock.post.mock.calls[0];
    expect(url).toBe('/api/media/asr');
    expect(body).toBeInstanceOf(FormData);
    expect((body as FormData).has('audio')).toBe(true);
    // 后台/可选请求：转写失败不应弹全局网络 toast（由 UI 自行提示改用键盘）。
    expect(config).toMatchObject({ suppressNetworkToast: true });
  });

  it('passes through a degraded response when server ASR is unavailable', async () => {
    httpMock.post.mockResolvedValueOnce({ data: { text: '', degraded: true } });
    const res = await transcribeAudio(new Blob(['x'], { type: 'audio/webm' }));
    expect(res.degraded).toBe(true);
    expect(res.text).toBe('');
  });
});
