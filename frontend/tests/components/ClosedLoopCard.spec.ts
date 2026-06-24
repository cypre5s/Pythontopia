import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ClosedLoopCard from '@/components/arena/ClosedLoopCard.vue';
import type { BossProblemDto } from '@/api/dto/Boss';

function problem(statement: string): BossProblemDto {
  return {
    problemId: 'p1',
    chapter: 1,
    stage: 1,
    difficulty: 2,
    kcCode: 'PY43-KC-001',
    statement,
    hints: [],
    sampleInput: '',
    sampleOutput: '',
    totalTestCases: 1,
    reviewed: true,
    aiGenerated: true,
  };
}

describe('ClosedLoopCard 题面分段渲染', () => {
  it('把【错误代码】标签后的代码抽成独立代码框，与题干/样例分开', () => {
    const statement = [
      '给花园浇水，输入 3 和 4 应输出 7，但程序出错了：',
      '【错误代码】',
      'a = input()',
      'b = input()',
      'print(a + b)',
      '【输入格式】',
      '两行整数',
    ].join('\n');
    const wrapper = mount(ClosedLoopCard, { props: { problem: problem(statement), code: '' } });

    const codeBlocks = wrapper.findAll('.cl-code');
    expect(codeBlocks).toHaveLength(1);
    expect(codeBlocks[0]!.text()).toContain('print(a + b)');

    // 代码不再混进题面文字段；「输入格式」作为分块标题保留。
    const proseText = wrapper.findAll('.cl-prose').map((p) => p.text()).join(' ');
    expect(proseText).toContain('给花园浇水');
    expect(proseText).not.toContain('print(a + b)');
    const labels = wrapper.findAll('.cl-block__label').map((label) => label.text());
    expect(labels).toContain('输入格式');
  });

  it('同样支持 ``` 围栏代码块，且不渲染围栏符号', () => {
    const statement = '看下面代码：\n```python\nx = 1\nprint(x)\n```\n找出问题。';
    const wrapper = mount(ClosedLoopCard, { props: { problem: problem(statement), code: '' } });

    const codeBlocks = wrapper.findAll('.cl-code');
    expect(codeBlocks).toHaveLength(1);
    expect(codeBlocks[0]!.text()).toContain('print(x)');
    expect(codeBlocks[0]!.text()).not.toContain('```');
    const proseText = wrapper.findAll('.cl-prose').map((p) => p.text()).join(' ');
    expect(proseText).toContain('找出问题');
  });

  it('纯文本题面（无代码块）回退为单个文字段', () => {
    const wrapper = mount(ClosedLoopCard, {
      props: { problem: problem('这是一道纯文字描述的题，没有代码块。'), code: '' },
    });
    expect(wrapper.findAll('.cl-code')).toHaveLength(0);
    expect(wrapper.find('.cl-prose').text()).toContain('纯文字描述');
  });
});
