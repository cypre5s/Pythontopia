import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import TeachBackCard from '@/components/arena/TeachBackCard.vue';
import type { BossProblemDto } from '@/api/dto/Boss';

const problem: BossProblemDto = {
  problemId: 'teach-1',
  chapter: 1,
  stage: 1,
  difficulty: 2,
  kcCode: 'PY43-KC-001',
  statement: '解释抽象是什么。',
  hints: [],
  sampleInput: '',
  sampleOutput: '',
  totalTestCases: 0,
  reviewed: true,
  aiGenerated: false,
};

describe('TeachBackCard', () => {
  it('NPC 台词走设计令牌而非散落 hex 内联色', () => {
    const wrapper = mount(TeachBackCard, {
      props: { problem, code: '' },
    });

    const says = wrapper.get('.npc-says');
    // 台词正常渲染（可读性由 .npc-says 的 var(--pyt-color-text-primary) 令牌保证）。
    expect(says.text()).toContain('解释一下');
    // 不再用内联 hardcoded 颜色（统一令牌化，NFR-CMP/可维护性）。
    expect(says.attributes('style')).toBeUndefined();
  });
});
