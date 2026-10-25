import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@stores/session', () => ({
  useSessionStore: () => ({ token: 'test-token' }),
}));

import { useSseStream } from '@/composables/useSseStream';

/** 构造一个非 2xx 响应；body 为 undefined 时模拟非 JSON（如 nginx 502 HTML）。 */
function errorResponse(status: number, statusText: string, body?: unknown) {
  return {
    ok: false,
    status,
    statusText,
    json: async () => {
      if (body === undefined) throw new SyntaxError('Unexpected token <');
      return body;
    },
  } as unknown as Response;
}

describe('useSseStream 非 2xx 错误投影', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  // 回归：流式端点报错时后端回的是统一错误体，必须把 code/message 透出来。
  // 否则「今日 AI 请求额度已达 30 次」会退化成 HTTP_429 / Too Many Requests，学生无从处理。
  it('优先使用后端错误体里的 code 与 message', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        errorResponse(429, 'Too Many Requests', {
          code: 'TUTOR_DAILY_LIMIT_REACHED',
          message: '今日 AI 请求额度已达 30 次，请明天再试',
          details: [],
          traceId: 'trace-test',
        }),
      ),
    );
    const stream = useSseStream('/api/tutor/workflow/runs');

    await stream.start({ problemId: 'p-1' });

    expect(stream.status.value).toBe('error');
    expect(stream.error.value).toEqual({
      code: 'TUTOR_DAILY_LIMIT_REACHED',
      message: '今日 AI 请求额度已达 30 次，请明天再试',
    });
  });

  it('响应体不是 JSON 时回退到 HTTP 状态描述', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(errorResponse(502, 'Bad Gateway')));
    const stream = useSseStream('/api/tutor/workflow/runs');

    await stream.start({ problemId: 'p-1' });

    expect(stream.status.value).toBe('error');
    expect(stream.error.value).toEqual({ code: 'HTTP_502', message: 'Bad Gateway' });
  });
});
