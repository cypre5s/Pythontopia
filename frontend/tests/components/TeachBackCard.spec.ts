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
  it('uses black ink for NPC dialog copy', () => {
    const wrapper = mount(TeachBackCard, {
      props: { problem, code: '' },
    });

    expect(wrapper.get('.npc-says').attributes('style')).toContain('color: rgb(17, 17, 17)');
  });
});
