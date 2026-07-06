import { describe, it, expect, beforeEach, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import type { MemoryItemDto } from '@/api/dto/Memory';

vi.mock('@/api/memory', () => ({
  getMemory: vi.fn(),
}));

import AgentNotebook from '@/components/agents/AgentNotebook.vue';
import { getMemory } from '@/api/memory';

function makeItem(over: Partial<MemoryItemDto> = {}): MemoryItemDto {
  return {
    eventId: 1,
    eventType: 'practice_failed',
    summaryZh: '练习失败：程序运行原理（WA）',
    kcCodes: ['PY43-KC-016'],
    createdAt: '2026-06-08T00:00:00Z',
    importance: 4,
    recencyScore: 0.82,
    importanceScore: 0.8,
    relevanceScore: 1,
    totalScore: 0.86,
    ...over,
  };
}

describe('AgentNotebook', () => {
  beforeEach(() => {
    vi.mocked(getMemory).mockReset();
  });

  it('renders one row per memory item with star rating equal to importance', async () => {
    vi.mocked(getMemory).mockResolvedValue([
      makeItem({ eventId: 1, importance: 4 }),
      makeItem({ eventId: 2, importance: 1, summaryZh: '消费资源：讲义' }),
    ]);

    const wrapper = mount(AgentNotebook);
    await flushPromises();

    const rows = wrapper.findAll('[data-testid="notebook-item"]');
    expect(rows).toHaveLength(2);
    expect(getMemory).toHaveBeenCalledTimes(1);

    const firstStars = rows[0]?.find('[data-testid="notebook-stars"]');
    expect(firstStars?.attributes('data-importance')).toBe('4');
    const secondStars = rows[1]?.find('[data-testid="notebook-stars"]');
    expect(secondStars?.attributes('data-importance')).toBe('1');

    expect(wrapper.text()).toContain('练习失败');
    expect(wrapper.text()).toContain('消费资源');
  });

  it('expands an item to reveal recency / importance / relevance score bars', async () => {
    vi.mocked(getMemory).mockResolvedValue([makeItem()]);

    const wrapper = mount(AgentNotebook);
    await flushPromises();

    // 折叠时不渲染分数分解。
    expect(wrapper.find('[data-testid="notebook-scores"]').exists()).toBe(false);

    await wrapper.find('[data-testid="notebook-scores-toggle"]').trigger('click');

    expect(wrapper.find('[data-testid="notebook-scores"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="notebook-score-recency"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="notebook-score-importance"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="notebook-score-relevance"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="notebook-score-recency"]').text()).toContain('82%');
  });

  it('shows a friendly empty state and no rows when memory is empty', async () => {
    vi.mocked(getMemory).mockResolvedValue([]);

    const wrapper = mount(AgentNotebook);
    await flushPromises();

    expect(wrapper.find('[data-testid="notebook-empty"]').exists()).toBe(true);
    expect(wrapper.findAll('[data-testid="notebook-item"]')).toHaveLength(0);
  });

  it('degrades to the empty state without throwing when the API fails', async () => {
    vi.mocked(getMemory).mockRejectedValue(new Error('network'));

    const wrapper = mount(AgentNotebook);
    await flushPromises();

    expect(wrapper.find('[data-testid="notebook-empty"]').exists()).toBe(true);
    expect(wrapper.findAll('[data-testid="notebook-item"]')).toHaveLength(0);
  });

  it('emits close when the close button is clicked', async () => {
    vi.mocked(getMemory).mockResolvedValue([makeItem()]);

    const wrapper = mount(AgentNotebook);
    await flushPromises();

    await wrapper.find('[data-testid="notebook-close"]').trigger('click');
    expect(wrapper.emitted('close')).toBeTruthy();
  });
});
