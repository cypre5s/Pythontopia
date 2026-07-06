// 选择 / 判断 / 填空渲染器单测：作答事件、提交前不泄答案、review 模式正确高亮 + 解析。
import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import ChoiceQuestion from '@/components/chaptertest/ChoiceQuestion.vue';
import JudgeQuestion from '@/components/chaptertest/JudgeQuestion.vue';
import FillQuestion from '@/components/chaptertest/FillQuestion.vue';
import type {
  ChapterTestChoiceQuestionDto,
  ChapterTestFillQuestionDto,
  ChapterTestJudgeQuestionDto,
  ChapterTestResultItemDto,
} from '@/api/chapterTest';

const CHOICE: ChapterTestChoiceQuestionDto = {
  qid: 'c1',
  type: 'CHOICE',
  score: 8,
  stem: '以下哪个用于遍历？',
  options: ['print', 'for', 'def', 'import'],
};

const JUDGE: ChapterTestJudgeQuestionDto = {
  qid: 'j1',
  type: 'JUDGE',
  score: 5,
  stem: 'Python 是动态类型语言。',
};

const FILL: ChapterTestFillQuestionDto = {
  qid: 'f1',
  type: 'FILL',
  score: 6,
  stem: '用 ____ 生成 0..n，用 ____ 求长度。',
};

describe('ChoiceQuestion', () => {
  it('emits select with the option letter (index→letter)', async () => {
    const wrapper = mount(ChoiceQuestion, {
      props: { question: CHOICE, index: 1, selected: null },
    });
    await wrapper.find('[data-testid="choice-opt-c1-B"]').trigger('click');
    expect(wrapper.emitted('select')?.[0]).toEqual(['B']);
  });

  it('renders no review/answer markers before submit', () => {
    const wrapper = mount(ChoiceQuestion, {
      props: { question: CHOICE, index: 1, selected: 'B' },
    });
    expect(wrapper.find('[data-testid="result-item-c1"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="choice-opt-c1-A"]').classes()).not.toContain('ct-opt--correct');
  });

  it('highlights correct + wrong and disables picking in review mode', async () => {
    const reviewItem: ChapterTestResultItemDto = {
      qid: 'c1',
      type: 'CHOICE',
      score: 8,
      awarded: 0,
      correct: false,
      explain: 'for 才是遍历语句',
      correctChoice: 'B',
    };
    const wrapper = mount(ChoiceQuestion, {
      props: { question: CHOICE, index: 1, selected: 'A', reviewItem },
    });
    expect(wrapper.find('[data-testid="choice-opt-c1-B"]').classes()).toContain('ct-opt--correct');
    expect(wrapper.find('[data-testid="choice-opt-c1-A"]').classes()).toContain('ct-opt--wrong');
    expect(wrapper.find('[data-testid="result-item-c1"]').text()).toContain('for 才是遍历语句');
    await wrapper.find('[data-testid="choice-opt-c1-C"]').trigger('click');
    expect(wrapper.emitted('select')).toBeUndefined();
  });
});

describe('JudgeQuestion', () => {
  it('emits select with the boolean value', async () => {
    const wrapper = mount(JudgeQuestion, {
      props: { question: JUDGE, index: 2, selected: null },
    });
    await wrapper.find('[data-testid="judge-true-j1"]').trigger('click');
    expect(wrapper.emitted('select')?.[0]).toEqual([true]);
  });

  it('shows correct answer + explain only in review mode', () => {
    const reviewItem: ChapterTestResultItemDto = {
      qid: 'j1',
      type: 'JUDGE',
      score: 5,
      awarded: 5,
      correct: true,
      explain: '确实是动态类型',
      correctJudgement: true,
    };
    const noReview = mount(JudgeQuestion, { props: { question: JUDGE, index: 2, selected: true } });
    expect(noReview.find('[data-testid="result-item-j1"]').exists()).toBe(false);

    const wrapper = mount(JudgeQuestion, {
      props: { question: JUDGE, index: 2, selected: true, reviewItem },
    });
    expect(wrapper.find('[data-testid="result-item-j1"]').text()).toContain('确实是动态类型');
  });
});

describe('FillQuestion', () => {
  it('renders one input per blank and emits change(index, value)', async () => {
    const wrapper = mount(FillQuestion, {
      props: { question: FILL, index: 3, blanks: ['', ''] },
    });
    expect(wrapper.find('[data-testid="fill-input-f1-0"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="fill-input-f1-1"]').exists()).toBe(true);
    await wrapper.find('[data-testid="fill-input-f1-0"]').setValue('range');
    const changes = wrapper.emitted('change');
    expect(changes?.[0]).toEqual([0, 'range']);
  });

  it('shows accepted answers only in review mode', () => {
    const reviewItem: ChapterTestResultItemDto = {
      qid: 'f1',
      type: 'FILL',
      score: 6,
      awarded: 3,
      correct: false,
      explain: '注意 range 与 len',
      acceptedAnswers: ['range', 'len'],
    };
    const wrapper = mount(FillQuestion, {
      props: { question: FILL, index: 3, blanks: ['range', 'size'], reviewItem },
    });
    expect(wrapper.find('[data-testid="result-item-f1"]').text()).toContain('range / len');
  });
});
