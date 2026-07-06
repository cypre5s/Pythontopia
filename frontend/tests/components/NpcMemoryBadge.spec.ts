import { mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import NpcMemoryBadge from '@/components/npc/NpcMemoryBadge.vue';
import type { MemoryItemDto } from '@/api/npc';

function makeMemory(overrides: Partial<MemoryItemDto> = {}): MemoryItemDto {
  return {
    eventId: 42,
    eventType: 'practice_failed',
    summaryZh: '练习失败：变量命名把赋值号写反了',
    kcCodes: ['PY43-KC-001'],
    createdAt: '2026-06-09T08:00:00Z',
    importance: 4,
    recencyScore: 0.82,
    importanceScore: 0.8,
    relevanceScore: 0.9,
    totalScore: 0.84,
    ...overrides,
  };
}

function installMatchMedia(matches: boolean): void {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    writable: true,
    value: vi.fn(() => ({
      matches,
      media: '(prefers-reduced-motion: reduce)',
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(() => true),
    })),
  });
}

afterEach(() => {
  vi.restoreAllMocks();
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    writable: true,
    value: undefined,
  });
});

describe('NpcMemoryBadge', () => {
  it('渲染可展开记忆来源与 recency / importance / relevance 玻璃盒分解', () => {
    const wrapper = mount(NpcMemoryBadge, {
      props: {
        npcName: '出题官 Q',
        memories: [makeMemory()],
        citedEventIds: [42],
      },
    });

    expect(wrapper.get('[data-testid="npc-memory-badge"]').text()).toContain('出题官 Q 记得你');
    expect(wrapper.get('[data-testid="npc-memory-count"]').text()).toContain('1');
    const item = wrapper.get('[data-testid="npc-memory-item-42"]');
    expect(item.text()).toContain('练习失败：变量命名把赋值号写反了');
    expect(item.text()).toContain('practice_failed');
    expect(item.text()).toContain('PY43-KC-001');
    expect(item.text()).toContain('近期 82%');
    expect(item.text()).toContain('重要 80%');
    expect(item.text()).toContain('相关 90%');
    expect(item.text()).toContain('event #42');
    expect(item.classes()).toContain('npc-memory-badge__item--cited');
  });

  it('无记忆时显示空态并保持状态语义', () => {
    const wrapper = mount(NpcMemoryBadge, {
      props: {
        npcName: '馆员 Doc',
        memories: [],
        citedEventIds: [],
      },
    });

    const empty = wrapper.get('[data-testid="npc-memory-empty"]');
    expect(empty.attributes('role')).toBe('status');
    expect(empty.text()).toContain('还没有可引用的真实记忆');
  });

  it('系统偏好 reduced-motion 时暴露降级标记，供样式关闭动画', () => {
    installMatchMedia(true);

    const wrapper = mount(NpcMemoryBadge, {
      props: {
        npcName: '复盘贤者',
        memories: [makeMemory({ eventId: 7 })],
        citedEventIds: [],
      },
    });

    expect(wrapper.get('[data-testid="npc-memory-badge"]').attributes('data-motion')).toBe('reduced');
  });
});
