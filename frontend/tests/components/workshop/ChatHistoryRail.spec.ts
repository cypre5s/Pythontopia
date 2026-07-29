import { afterEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

vi.mock('@/api/http', () => ({
  http: { get: vi.fn(), post: vi.fn() },
}));

vi.mock('@/composables/useStardewFx', () => ({
  playUiClick: vi.fn(),
}));

import { http } from '@/api/http';
import ChatHistoryRail from '@/components/workshop/ChatHistoryRail.vue';
import {
  resetWorkshopChatHistoryCacheForTest,
  type WorkshopChatHistoryTurnDto,
} from '@/api/workshopHistory';

const baseTurn: WorkshopChatHistoryTurnDto = {
  id: '1',
  conversationId: 'conv-1',
  userText: '什么是 if 语句',
  intent: 'ANSWER',
  responseKind: 'answer',
  toolLabel: '课件问答',
  responseText: 'if 用来做条件分支。',
  responsePayload: {},
  evidencePageIds: [18],
  groundingStatus: 'PASSED',
  createdAt: '2026-06-19T00:00:00Z',
};

afterEach(() => {
  resetWorkshopChatHistoryCacheForTest();
  vi.clearAllMocks();
});

describe('ChatHistoryRail · 历史列表缓存', () => {
  it('重挂载历史栏时复用缓存，只有手动刷新才重新请求历史接口', async () => {
    vi.mocked(http.get)
      .mockResolvedValueOnce({ data: [baseTurn] } as never)
      .mockResolvedValueOnce({
        data: [
          {
            ...baseTurn,
            id: '2',
            conversationId: 'conv-2',
            userText: '什么是 while 语句',
            createdAt: '2026-06-19T00:01:00Z',
          },
          baseTurn,
        ],
      } as never);

    const first = mount(ChatHistoryRail, {
      props: { embeddedOpen: true, userId: 101 },
    });
    await flushPromises();
    expect(first.text()).toContain('什么是 if 语句');
    expect(vi.mocked(http.get)).toHaveBeenCalledTimes(1);
    first.unmount();

    const second = mount(ChatHistoryRail, {
      props: { embeddedOpen: true, userId: 101 },
    });
    await flushPromises();
    expect(second.text()).toContain('什么是 if 语句');
    expect(vi.mocked(http.get)).toHaveBeenCalledTimes(1);

    await second.get('[data-testid="workshop-history-refresh"]').trigger('click');
    await flushPromises();
    expect(second.text()).toContain('什么是 while 语句');
    expect(vi.mocked(http.get)).toHaveBeenCalledTimes(2);
  });

  it('切换 userId 立即清空旧列表，并拒绝旧用户晚到结果覆盖新用户', async () => {
    let resolveFirst:
      | ((value: { data: WorkshopChatHistoryTurnDto[] }) => void)
      | undefined;
    let resolveSecond:
      | ((value: { data: WorkshopChatHistoryTurnDto[] }) => void)
      | undefined;
    vi.mocked(http.get)
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveFirst = resolve;
          }),
      )
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveSecond = resolve;
          }),
      );

    const wrapper = mount(ChatHistoryRail, {
      props: { embeddedOpen: true, userId: 101 },
    });
    await Promise.resolve();
    expect(vi.mocked(http.get)).toHaveBeenCalledTimes(1);

    await wrapper.setProps({ userId: 202 });
    await Promise.resolve();
    expect(vi.mocked(http.get)).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).not.toContain(baseTurn.userText);

    const secondUserTurn: WorkshopChatHistoryTurnDto = {
      ...baseTurn,
      id: '2',
      conversationId: 'conv-2',
      userText: 'B 用户自己的循环问题',
    };
    resolveSecond?.({ data: [secondUserTurn] });
    await flushPromises();
    expect(wrapper.text()).toContain(secondUserTurn.userText);

    resolveFirst?.({ data: [baseTurn] });
    await flushPromises();
    expect(wrapper.text()).toContain(secondUserTurn.userText);
    expect(wrapper.text()).not.toContain(baseTurn.userText);
  });
});
