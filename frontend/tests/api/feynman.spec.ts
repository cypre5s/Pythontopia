import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/api/http', () => ({
  http: { post: vi.fn() },
}));

import { http } from '@/api/http';
import { judgeFeynmanSession, teachFeynmanSession } from '@/api/feynman';

describe('feynman API timeouts', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(http.post).mockResolvedValue({ data: {} });
  });

  it('allows the Graph-backed teaching request to outlive the backend read timeout', async () => {
    await teachFeynmanSession(11, 'range 的右端点不包含');

    expect(http.post).toHaveBeenCalledWith(
      '/api/feynman/sessions/11/teach',
      { explanation: 'range 的右端点不包含' },
      { timeout: 35_000 },
    );
  });

  it('allows the real judge to finish up to ten five-second cases', async () => {
    await judgeFeynmanSession(11);

    expect(http.post).toHaveBeenCalledWith(
      '/api/feynman/sessions/11/judge',
      {},
      { timeout: 60_000 },
    );
  });
});
