import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/api/http', () => ({
  http: { get: vi.fn(), post: vi.fn() },
}));

import { http } from '@/api/http';
import {
  listWorkshopChatHistoryTurns,
  rememberWorkshopChatHistoryTurn,
  resetWorkshopChatHistoryCacheForTest,
  type WorkshopChatHistoryTurnDto,
} from '@/api/workshopHistory';

const firstTurn: WorkshopChatHistoryTurnDto = {
  id: '1',
  conversationId: 'conv-1',
  userText: '什么是 while 语句',
  intent: 'ANSWER',
  responseKind: 'answer',
  toolLabel: '课件问答',
  responseText: 'while 会在条件为真时重复执行。',
  responsePayload: {},
  evidencePageIds: [12],
  groundingStatus: 'PASSED',
  createdAt: '2026-06-19T00:00:00Z',
};

const secondTurn: WorkshopChatHistoryTurnDto = {
  ...firstTurn,
  id: '2',
  conversationId: 'conv-2',
  userText: '我想学循环，从哪开始',
  intent: 'PLAN',
  responseKind: 'plan',
  toolLabel: '规划学习',
  responseText: '先理解重复执行，再练 for/while。',
  createdAt: '2026-06-19T00:01:00Z',
};

afterEach(() => {
  resetWorkshopChatHistoryCacheForTest();
  vi.clearAllMocks();
});

describe('workshopHistory · 会话内历史缓存', () => {
  it('相同 limit 在缓存有效期内只请求一次历史接口', async () => {
    vi.mocked(http.get).mockResolvedValue({ data: [firstTurn] } as never);

    const first = await listWorkshopChatHistoryTurns(50, { now: () => 1000 });
    const second = await listWorkshopChatHistoryTurns(50, { now: () => 2000 });

    expect(first).toEqual([firstTurn]);
    expect(second).toEqual([firstTurn]);
    expect(vi.mocked(http.get)).toHaveBeenCalledTimes(1);
  });

  it('返回数组副本，调用方改写不污染缓存', async () => {
    vi.mocked(http.get).mockResolvedValue({ data: [firstTurn] } as never);

    const first = await listWorkshopChatHistoryTurns(50, { now: () => 1000 });
    first.pop();
    const second = await listWorkshopChatHistoryTurns(50, { now: () => 2000 });

    expect(second).toHaveLength(1);
    expect(second[0]?.id).toBe('1');
  });

  it('force 刷新会绕过缓存重新请求数据库接口', async () => {
    vi.mocked(http.get)
      .mockResolvedValueOnce({ data: [firstTurn] } as never)
      .mockResolvedValueOnce({ data: [secondTurn, firstTurn] } as never);

    await listWorkshopChatHistoryTurns(50, { now: () => 1000 });
    const refreshed = await listWorkshopChatHistoryTurns(50, { force: true, now: () => 2000 });

    expect(refreshed.map((turn) => turn.id)).toEqual(['2', '1']);
    expect(vi.mocked(http.get)).toHaveBeenCalledTimes(2);
  });

  it('新保存的 turn 会合并进缓存，避免重挂载后立刻查库', async () => {
    vi.mocked(http.get).mockResolvedValue({ data: [firstTurn] } as never);

    await listWorkshopChatHistoryTurns(50, { now: () => 1000 });
    rememberWorkshopChatHistoryTurn(secondTurn, 50, { now: () => 1500 });
    const cached = await listWorkshopChatHistoryTurns(50, { now: () => 2000 });

    expect(cached.map((turn) => turn.id)).toEqual(['2', '1']);
    expect(vi.mocked(http.get)).toHaveBeenCalledTimes(1);
  });
});
