import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import ClosedLoopCard from '@/components/arena/ClosedLoopCard.vue';
import type { BossProblemDto } from '@/api/dto/Boss';

function problem(overrides: Partial<BossProblemDto> = {}): BossProblemDto {
  return {
    problemId: 'p-closed-loop',
    chapter: 1,
    stage: 2,
    difficulty: 2,
    kcCode: 'PY43-KC-001',
    statement: [
      '调试闭环：读入两个整数，输出它们的和。',
      '',
      '【错误代码】',
      'a, b = input().split()',
      'print(a + b)',
      '',
      '【报错现象】',
      '输入 2 3 时输出 23，不是 5。',
    ].join('\n'),
    starterCode: 'a, b = input().split()\nprint(a + b)\n',
    buggyCode: 'a, b = input().split()\nprint(a + b)\n',
    hints: [],
    sampleInput: '2 3',
    sampleOutput: '5',
    totalTestCases: 3,
    reviewed: false,
    aiGenerated: false,
    ...overrides,
  };
}

const PythonCodeEditorStub = {
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: `
    <textarea
      data-testid="code-editor"
      :value="modelValue"
      @input="$emit('update:modelValue', $event.target.value)"
    />
  `,
};

function mountCard(code = '') {
  return mount(ClosedLoopCard, {
    props: {
      problem: problem(),
      code,
    },
    global: {
      stubs: {
        PythonCodeEditor: PythonCodeEditorStub,
      },
    },
  });
}

describe('ClosedLoopCard', () => {
  it('keeps the problem statement visible on the left with distinct content blocks', () => {
    const wrapper = mountCard();

    expect(wrapper.get('[data-testid="closed-loop-problem"]').text()).toContain('调试闭环');
    expect(wrapper.get('[data-testid="closed-loop-block-code"]').text()).toContain('a, b = input().split()');
    expect(wrapper.get('[data-testid="closed-loop-sample-input"]').text()).toContain('2 3');
    expect(wrapper.get('[data-testid="closed-loop-sample-output"]').text()).toContain('5');
  });

  it('unlocks the fix editor only after a valid cause judgement, while allowing a return to stage 2', async () => {
    const wrapper = mountCard('a, b = input().split()\nprint(a + b)\n');

    expect(wrapper.get('[data-testid="closed-loop-step-fix"]').attributes('disabled')).toBeDefined();
    await wrapper.get('[data-testid="closed-loop-step-locate"]').trigger('click');
    await wrapper.get('[data-testid="closed-loop-cause"]').setValue('不知道');
    await wrapper.get('[data-testid="closed-loop-check-cause"]').trigger('click');
    expect(wrapper.get('[data-testid="closed-loop-cause-feedback"]').text()).toContain('还差一步');
    expect(wrapper.find('[data-testid="code-editor"]').exists()).toBe(false);

    await wrapper.get('[data-testid="closed-loop-cause"]').setValue('第 1 行错误，因为 input().split() 得到字符串，需要转成 int。');
    await wrapper.get('[data-testid="closed-loop-check-cause"]').trigger('click');

    expect(wrapper.get('[data-testid="closed-loop-step-fix"]').attributes('disabled')).toBeUndefined();
    expect(wrapper.get('[data-testid="code-editor"]').exists()).toBe(true);

    await wrapper.get('[data-testid="closed-loop-step-locate"]').trigger('click');
    expect(wrapper.get('[data-testid="closed-loop-cause"]').exists()).toBe(true);
  });

  it('edits repair code through the shared Python editor surface', async () => {
    const wrapper = mountCard('a, b = input().split()\nprint(a + b)\n');

    await wrapper.get('[data-testid="closed-loop-step-locate"]').trigger('click');
    await wrapper.get('[data-testid="closed-loop-cause"]').setValue('第 1 行错误，因为 a 和 b 是字符串。');
    await wrapper.get('[data-testid="closed-loop-check-cause"]').trigger('click');
    await wrapper.get('[data-testid="code-editor"]').setValue('a, b = map(int, input().split())\nprint(a + b)\n');

    expect(wrapper.emitted('update:code')?.at(-1)?.[0]).toBe('a, b = map(int, input().split())\nprint(a + b)\n');
  });
});
