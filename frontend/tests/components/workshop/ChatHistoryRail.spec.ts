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
      props: { ownerId: 7, embeddedOpen: true },
    });
    await flushPromises();
    expect(first.text()).toContain('什么是 if 语句');
    expect(vi.mocked(http.get)).toHaveBeenCalledTimes(1);
    first.unmount();

    const second = mount(ChatHistoryRail, {
      props: { ownerId: 7, embeddedOpen: true },
    });
    await flushPromises();
    expect(second.text()).toContain('什么是 if 语句');
    expect(vi.mocked(http.get)).toHaveBeenCalledTimes(1);

    await second.get('[data-testid="workshop-history-refresh"]').trigger('click');
    await flushPromises();
    expect(second.text()).toContain('什么是 while 语句');
    expect(vi.mocked(http.get)).toHaveBeenCalledTimes(2);
  });

  it('切换 owner 立即清空旧列表，且旧请求晚到不得覆盖新账号', async () => {
    let resolveOwnerA: ((value: { data: WorkshopChatHistoryTurnDto[] }) => void) | undefined;
    const ownerBTurn: WorkshopChatHistoryTurnDto = {
      ...baseTurn,
      id: 'b-1',
      conversationId: 'conv-b',
      userText: 'B 账号的唯一问题',
    };
    vi.mocked(http.get)
      .mockReturnValueOnce(new Promise((resolve) => {
        resolveOwnerA = resolve;
      }))
      .mockResolvedValueOnce({ data: [ownerBTurn] } as never);

    const wrapper = mount(ChatHistoryRail, {
      props: { ownerId: 101, embeddedOpen: true },
    });
    await Promise.resolve();

    await wrapper.setProps({ ownerId: 202 });
    await flushPromises();

    expect(wrapper.text()).toContain('B 账号的唯一问题');
    expect(wrapper.text()).not.toContain('什么是 if 语句');

    resolveOwnerA?.({ data: [baseTurn] });
    await flushPromises();

    expect(wrapper.text()).toContain('B 账号的唯一问题');
    expect(wrapper.text()).not.toContain('什么是 if 语句');
    expect(vi.mocked(http.get)).toHaveBeenCalledTimes(2);
  });
});
