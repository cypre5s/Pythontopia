import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import ChapterNodeList from '@/components/mainline/ChapterNodeList.vue';
import type { MainlineChapterDto } from '@api/mainline';

function chapter(overrides: Partial<MainlineChapterDto> = {}): MainlineChapterDto {
  return {
    chapter: 1,
    title: '计算思维',
    unlocked: true,
    stars: 0,
    bestTestScore: null,
    stages: [],
    testStatus: 'NONE',
    starBreakdown: { test: 0, stage: 0, mastery: 0, recovered: [] },
    recovery: { errorbookDone: false, endlessDone: false },
    ...overrides,
  };
}

const CHAPTERS: MainlineChapterDto[] = [
  chapter({ chapter: 1, title: '计算思维', unlocked: true, stars: 3 }),
  chapter({ chapter: 2, title: '语言基础', unlocked: true, stars: 0 }),
  chapter({ chapter: 3, title: '信息表示', unlocked: false, stars: 0 }),
];

describe('ChapterNodeList', () => {
  it('renders one node per chapter with the correct state', () => {
    const wrapper = mount(ChapterNodeList, { props: { chapters: CHAPTERS, selected: null } });
    expect(wrapper.findAll('[data-testid^="mainline-node-btn-"]')).toHaveLength(3);
    expect(wrapper.get('[data-testid="mainline-node-1"]').attributes('data-state')).toBe('cleared');
    expect(wrapper.get('[data-testid="mainline-node-2"]').attributes('data-state')).toBe('in-progress');
    expect(wrapper.get('[data-testid="mainline-node-3"]').attributes('data-state')).toBe('locked');
  });

  it('shows a star badge reflecting each chapter stars', () => {
    const wrapper = mount(ChapterNodeList, { props: { chapters: CHAPTERS, selected: null } });
    const node1 = wrapper.get('[data-testid="mainline-node-1"]');
    expect(node1.get('[data-testid="mainline-star-badge"]').attributes('data-filled')).toBe('3');
  });

  it('marks the selected chapter and emits select on click', async () => {
    const wrapper = mount(ChapterNodeList, { props: { chapters: CHAPTERS, selected: 2 } });
    expect(wrapper.get('[data-testid="mainline-node-2"]').classes()).toContain('node--selected');
    expect(wrapper.get('[data-testid="mainline-node-2"]').attributes('aria-current')).toBe('true');

    await wrapper.get('[data-testid="mainline-node-btn-3"]').trigger('click');
    expect(wrapper.emitted('select')?.[0]).toEqual([3]);
  });
});
