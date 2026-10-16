import { createPinia } from 'pinia';
import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import ChallengeWorkspace from '@/components/challenge/ChallengeWorkspace.vue';
import type { BossProblemDto } from '@/api/dto/Boss';

const objectiveProblem: BossProblemDto = {
  problemId: 'objective-boss-1',
  chapter: 2,
  stage: 2,
  difficulty: 2,
  kcCode: 'PY43-KC-008',
  statement: '选择正确说法。',
  hints: [],
  sampleInput: '',
  sampleOutput: '',
  totalTestCases: 1,
  reviewed: true,
  aiGenerated: false,
  questionType: 'CHOICE',
  options: ['正确答案', '错误答案'],
  blankCount: 0,
};

describe('ChallengeWorkspace objective Boss question', () => {
  it('shows choices and emits the selected answer without rendering a code editor', async () => {
    const wrapper = mount(ChallengeWorkspace, {
      props: {
        problem: objectiveProblem,
        kind: 'dual_track',
        code: '',
        answers: [],
        title: 'Boss',
      },
      global: {
        plugins: [createPinia()],
        stubs: ['KindRouter', 'PythonCodeEditor', 'JudgeResultPanel'],
      },
    });

    expect(wrapper.get('[data-testid="objective-boss-question"]').text()).toContain('选择正确说法。');
    expect(wrapper.find('[data-testid="challenge-code"]').exists()).toBe(false);
    await wrapper.get('[data-testid="objective-option-0"]').setValue();

    expect(wrapper.emitted('update:answers')?.at(-1)).toEqual([['正确答案']]);
  });
});
