import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import DebugCard from '@/components/arena/DebugCard.vue';
import BugMineCard from '@/components/arena/BugMineCard.vue';
import FillBlankCard from '@/components/arena/FillBlankCard.vue';
import type { BossProblemDto } from '@/api/dto/Boss';

function problem(overrides: Partial<BossProblemDto> = {}): BossProblemDto {
  return {
    problemId: 'p-debug',
    chapter: 1,
    stage: 1,
    difficulty: 2,
    kcCode: 'PY43-KC-001',
    statement: '请修复下面的程序，而不是把题干当成代码。',
    hints: [],
    sampleInput: '2 3',
    sampleOutput: '5',
    totalTestCases: 3,
    reviewed: false,
    aiGenerated: false,
    ...overrides,
  };
}

describe('arena bug-oriented cards', () => {
  it('reconstructs the Web fill-blank marker as executable student code', async () => {
    const wrapper = mount(FillBlankCard, {
      props: {
        problem: problem({
          statement: [
            '# 变量与运算符：将两个整数分别存入变量，再求和输出',
            '___ = int(input())',
            '___ = int(input())',
            'print(___ + ___)',
          ].join('\n'),
        }),
        code: '',
      },
    });

    expect(wrapper.findAll('[data-testid^="blank-"]')).toHaveLength(4);
    await wrapper.get('[data-testid="blank-0"]').setValue('a');
    await wrapper.get('[data-testid="blank-1"]').setValue('b');
    await wrapper.get('[data-testid="blank-2"]').setValue('a');
    await wrapper.get('[data-testid="blank-3"]').setValue('b');

    expect(wrapper.emitted('update:code')?.at(-1)?.[0]).toBe(
      '# 变量与运算符：将两个整数分别存入变量，再求和输出\na = int(input())\nb = int(input())\nprint(a + b)',
    );
  });

  it('DebugCard renders the buggy code from the problem payload', () => {
    const wrapper = mount(DebugCard, {
      props: {
        problem: problem({
          buggyCode: 'a, b = input().split()\nprint(a + b)\n',
          starterCode: 'a, b = input().split()\nprint(a + b)\n',
        }),
        code: '',
      },
    });

    expect(wrapper.find('.buggy-code').text()).toContain('a, b = input().split()');
    expect(wrapper.find('.buggy-code').text()).not.toContain('请修复下面的程序');
  });

  it('BugMineCard keeps the prompt and editable buggy code separate', () => {
    vi.useFakeTimers();
    const wrapper = mount(BugMineCard, {
      props: {
        problem: problem({
          statement: '30 秒内修复总和程序。',
          buggyCode: 'nums = input().split()\nprint(nums[0] + nums[1])\n',
          starterCode: 'nums = input().split()\nprint(nums[0] + nums[1])\n',
        }),
        code: '',
      },
    });

    expect(wrapper.find('.question').text()).toContain('30 秒内修复');
    expect((wrapper.find('[data-testid="bug-mine-code"]').element as HTMLTextAreaElement).value)
      .toContain('nums = input().split()');
    expect((wrapper.find('[data-testid="bug-mine-code"]').element as HTMLTextAreaElement).value)
      .not.toContain('30 秒内修复');
    wrapper.unmount();
    vi.useRealTimers();
  });

  it('BugMineCard accepts a relaxed timer for roguelike contexts', async () => {
    vi.useFakeTimers();
    const onTimeout = vi.fn();
    const wrapper = mount(BugMineCard, {
      props: {
        problem: problem({
          statement: '限时内修复总和程序。',
          buggyCode: 'nums = input().split()\nprint(nums[0] + nums[1])\n',
          starterCode: 'nums = input().split()\nprint(nums[0] + nums[1])\n',
        }),
        code: '',
        timeLimitSeconds: 75,
        onTimeout,
      },
    });

    expect(wrapper.find('[role="timer"]').text()).toContain('75s');
    expect(wrapper.find('.hint').text()).toContain('限时 75 秒');
    await vi.advanceTimersByTimeAsync(74_000);
    expect(onTimeout).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1_000);
    expect(onTimeout).toHaveBeenCalledTimes(1);
    wrapper.unmount();
    vi.useRealTimers();
  });
});
