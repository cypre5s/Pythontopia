// 编程题渲染器单测：禁用粘贴并计数（paste-blocked）、Pyodide 本地预检、review 模式展示参考解。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const pyodideMock = vi.hoisted(() => ({ run: vi.fn() }));

vi.mock('@/game/adapters/pyodide', () => ({
  getPyodideRunner: () => ({
    init: vi.fn().mockResolvedValue(undefined),
    run: pyodideMock.run,
    dispose: vi.fn(),
  }),
}));

import CodingQuestion from '@/components/chaptertest/CodingQuestion.vue';
import type {
  ChapterTestCodingQuestionDto,
  ChapterTestResultItemDto,
} from '@/api/chapterTest';

const QUESTION: ChapterTestCodingQuestionDto = {
  qid: 'p1',
  type: 'CODING',
  score: 30,
  title: '两数之和',
  statementMarkdown: '读入 a b 输出 a+b',
  samples: [{ input: '2 3', output: '5' }],
};

beforeEach(() => {
  pyodideMock.run.mockReset();
  pyodideMock.run.mockResolvedValue({ stdout: '5\n', stderr: '', durationMs: 3, isError: false });
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('CodingQuestion', () => {
  it('blocks paste and emits paste-blocked', async () => {
    const wrapper = mount(CodingQuestion, { props: { question: QUESTION, index: 4, code: '' } });
    const textarea = wrapper.find('[data-testid="coding-input-p1"]');
    await textarea.trigger('paste');
    await textarea.trigger('paste');
    expect(wrapper.emitted('paste-blocked')).toHaveLength(2);
  });

  it('runs Pyodide local preview against the first sample and shows stdout', async () => {
    const wrapper = mount(CodingQuestion, {
      props: { question: QUESTION, index: 4, code: 'print(5)' },
    });
    await wrapper.find('[data-testid="coding-preview-p1"]').trigger('click');
    await flushPromises();
    expect(pyodideMock.run).toHaveBeenCalledWith('print(5)', '2 3');
    expect(wrapper.find('[data-testid="coding-preview-out-p1"]').text()).toContain('5');
  });

  it('does not show answers/explain until reviewItem is provided', () => {
    const wrapper = mount(CodingQuestion, { props: { question: QUESTION, index: 4, code: '' } });
    expect(wrapper.find('[data-testid="result-item-p1"]').exists()).toBe(false);
  });

  it('shows reference solution + case stats in review mode', () => {
    const reviewItem: ChapterTestResultItemDto = {
      qid: 'p1',
      type: 'CODING',
      score: 30,
      awarded: 20,
      correct: false,
      explain: '边界用例未过',
      referenceSolution: 'a,b=map(int,input().split());print(a+b)',
      passedTestCases: 2,
      totalTestCases: 3,
    };
    const wrapper = mount(CodingQuestion, {
      props: { question: QUESTION, index: 4, code: 'print(5)', reviewItem },
    });
    const review = wrapper.find('[data-testid="result-item-p1"]');
    expect(review.exists()).toBe(true);
    expect(review.text()).toContain('2/3');
    expect(wrapper.text()).toContain('a,b=map(int,input().split())');
    // review 模式禁用编辑。
    expect(wrapper.find('[data-testid="coding-input-p1"]').attributes('disabled')).toBeDefined();
  });
});
