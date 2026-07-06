import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';

vi.mock('@/api/mainline', () => ({
  answerPracticeQuestion: vi.fn(),
  postExampleDone: vi.fn(),
}));

import StageExamplePractice from '@/components/mainline-stage/StageExamplePractice.vue';
import { answerPracticeQuestion, postExampleDone } from '@/api/mainline';
import type {
  PracticeAnswerResultDto,
  StageExampleDto,
  StagePracticeQuestionDto,
  StageProgressDto,
} from '@/api/mainline';

const STAGE_ID = 7;

function makeQuestion(over: Partial<StagePracticeQuestionDto> = {}): StagePracticeQuestionDto {
  return {
    id: 11,
    kcCode: 'KC-IO-01',
    type: 'CHOICE',
    ordinal: 1,
    stem: '下列哪个函数用于输出？',
    options: ['print', 'input', 'len', 'type'],
    blankCount: 0,
    ...over,
  };
}

function makeExample(over: Partial<StageExampleDto> = {}): StageExampleDto {
  return {
    id: 201,
    displayNo: 'P1-01',
    displayName: '两数之和',
    kcCode: 'KC-OP-02',
    ...over,
  };
}

function makeAnswer(over: Partial<PracticeAnswerResultDto> = {}): PracticeAnswerResultDto {
  return {
    questionId: 11,
    correct: true,
    correctAnswers: [],
    explanation: '',
    ...over,
  };
}

function makeStageProgress(over: Partial<StageProgressDto> = {}): StageProgressDto {
  return {
    stageId: STAGE_ID,
    chapter: 2,
    index: 1,
    title: '变量与输入',
    videoWatchedSec: 0,
    requiredVideoSec: 240,
    pagesRead: 0,
    requiredPages: 0,
    examplesDone: 1,
    requiredExamples: 1,
    completed: false,
    examplesDoneIds: [201],
    ...over,
  };
}

function createTestRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/training', name: 'training', component: { template: '<div />' } },
    ],
  });
}

interface MountProps {
  stageId: number;
  questions: StagePracticeQuestionDto[];
  requiredQuestions: number;
  correctQuestionIds: number[];
  examples: StageExampleDto[];
  requiredExamples: number;
  doneProblemIds: number[];
}

function mountPractice(over: Partial<MountProps>, router: Router) {
  const props: MountProps = {
    stageId: STAGE_ID,
    questions: [],
    requiredQuestions: 0,
    correctQuestionIds: [],
    examples: [],
    requiredExamples: 0,
    doneProblemIds: [],
    ...over,
  };
  return mount(StageExamplePractice, { props, global: { plugins: [router] } });
}

describe('StageExamplePractice（练 · 站内客观题 + OJ 跳训练场）', () => {
  let router: Router;

  beforeEach(async () => {
    // 组件内 useToastStore（R2 evidence 拒绝提示）需要活动 Pinia。
    setActivePinia(createPinia());
    router = createTestRouter();
    await router.push('/');
    await router.isReady();
    vi.mocked(answerPracticeQuestion).mockResolvedValue(makeAnswer({ correct: true }));
    vi.mocked(postExampleDone).mockResolvedValue(makeStageProgress());
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('客观题按 ordinal 排序渲染（共 4 题）', () => {
    const questions = [
      makeQuestion({ id: 11, ordinal: 2 }),
      makeQuestion({ id: 12, ordinal: 1, type: 'JUDGE', options: [] }),
      makeQuestion({ id: 13, ordinal: 4, type: 'FILL', options: [], blankCount: 2 }),
      makeQuestion({ id: 14, ordinal: 3 }),
    ];
    const wrapper = mountPractice({ questions, requiredQuestions: 4 }, router);

    const cards = wrapper.findAll('.pq-card');
    expect(cards).toHaveLength(4);
    expect(cards.map((c) => c.attributes('data-testid'))).toEqual([
      'practice-q-12',
      'practice-q-11',
      'practice-q-14',
      'practice-q-13',
    ]);
    expect(wrapper.find('[data-testid="stage-practice-quiz-count"]').text()).toContain('0 / 4');
  });

  it('CHOICE 答对：提交选项文本→展示参考答案/讲解、计入并 emit correctQuestionIds', async () => {
    vi.mocked(answerPracticeQuestion).mockResolvedValue(
      makeAnswer({ correct: true, correctAnswers: ['print'], explanation: 'print 用于输出。' }),
    );
    const wrapper = mountPractice(
      { questions: [makeQuestion({ id: 11 })], requiredQuestions: 1 },
      router,
    );

    await wrapper.find('[data-testid="practice-q-11-opt-0"]').trigger('click');
    await wrapper.find('[data-testid="practice-q-11-submit"]').trigger('click');
    await flushPromises();

    expect(vi.mocked(answerPracticeQuestion)).toHaveBeenCalledWith(STAGE_ID, 11, ['print']);

    const result = wrapper.find('[data-testid="practice-q-11-result"]');
    expect(result.exists()).toBe(true);
    expect(result.text()).toContain('答对了');
    expect(result.text()).toContain('print');
    expect(result.text()).toContain('print 用于输出。');

    const events = wrapper.emitted('progress');
    expect(events).toBeTruthy();
    expect(events?.[0]?.[0]).toMatchObject({ stageId: STAGE_ID, correctQuestionIds: [11] });

    // 答对后锁题：提交按钮禁用（幂等）。
    expect(wrapper.find('[data-testid="practice-q-11-submit"]').attributes('disabled')).toBeDefined();
  });

  it('CHOICE 答错：展示讲解供自检、不计入、不 emit、可重试', async () => {
    vi.mocked(answerPracticeQuestion).mockResolvedValue(
      makeAnswer({ correct: false, correctAnswers: ['print'], explanation: '应使用 print。' }),
    );
    const wrapper = mountPractice(
      { questions: [makeQuestion({ id: 11 })], requiredQuestions: 1 },
      router,
    );

    await wrapper.find('[data-testid="practice-q-11-opt-1"]').trigger('click');
    await wrapper.find('[data-testid="practice-q-11-submit"]').trigger('click');
    await flushPromises();

    const result = wrapper.find('[data-testid="practice-q-11-result"]');
    expect(result.text()).toContain('答错了');
    expect(result.text()).toContain('应使用 print。');

    expect(wrapper.emitted('progress')).toBeFalsy();
    // 未过线：客观题计数仍为 0 / 1，可再次提交。
    expect(wrapper.find('[data-testid="stage-practice-quiz-count"]').text()).toContain('0 / 1');
    expect(wrapper.find('[data-testid="practice-q-11-submit"]').attributes('disabled')).toBeUndefined();
  });

  it('JUDGE 提交映射为中文文本（错误 → ["错误"]）', async () => {
    vi.mocked(answerPracticeQuestion).mockResolvedValue(
      makeAnswer({ questionId: 12, correct: false, correctAnswers: ['正确'], explanation: '该说法成立。' }),
    );
    const wrapper = mountPractice(
      {
        questions: [makeQuestion({ id: 12, type: 'JUDGE', options: [], stem: '字符串不可变。' })],
        requiredQuestions: 1,
      },
      router,
    );

    await wrapper.find('[data-testid="practice-q-12-judge-false"]').trigger('click');
    await wrapper.find('[data-testid="practice-q-12-submit"]').trigger('click');
    await flushPromises();

    expect(vi.mocked(answerPracticeQuestion)).toHaveBeenCalledWith(STAGE_ID, 12, ['错误']);
    expect(wrapper.find('[data-testid="practice-q-12-result"]').text()).toContain('答错了');
  });

  it('FILL 提交按空位顺序，且空位未填满时禁用提交', async () => {
    vi.mocked(answerPracticeQuestion).mockResolvedValue(makeAnswer({ questionId: 13, correct: true }));
    const wrapper = mountPractice(
      {
        questions: [
          makeQuestion({ id: 13, type: 'FILL', options: [], blankCount: 2, stem: '补全语句' }),
        ],
        requiredQuestions: 1,
      },
      router,
    );

    const submit = () => wrapper.find('[data-testid="practice-q-13-submit"]');
    expect(submit().attributes('disabled')).toBeDefined();

    await wrapper.find('[data-testid="practice-q-13-blank-0"]').setValue('for');
    expect(submit().attributes('disabled')).toBeDefined(); // 仍有空位未填

    await wrapper.find('[data-testid="practice-q-13-blank-1"]').setValue('range');
    expect(submit().attributes('disabled')).toBeUndefined();

    await submit().trigger('click');
    await flushPromises();

    expect(vi.mocked(answerPracticeQuestion)).toHaveBeenCalledWith(STAGE_ID, 13, ['for', 'range']);
    expect(wrapper.emitted('progress')?.[0]?.[0]).toMatchObject({
      stageId: STAGE_ID,
      correctQuestionIds: [13],
    });
  });

  it('初始已答对的客观题渲染为完成态（计数命中、提交禁用）', () => {
    const wrapper = mountPractice(
      {
        questions: [makeQuestion({ id: 11 }), makeQuestion({ id: 12, ordinal: 2, type: 'JUDGE', options: [] })],
        requiredQuestions: 2,
        correctQuestionIds: [11],
      },
      router,
    );

    expect(wrapper.find('[data-testid="stage-practice-quiz-count"]').text()).toContain('1 / 2');
    expect(wrapper.find('[data-testid="practice-q-11-submit"]').attributes('disabled')).toBeDefined();
    expect(wrapper.find('[data-testid="practice-q-11-result"]').text()).toContain('已答对');
    expect(wrapper.find('[data-testid="practice-q-12-submit"]').attributes('disabled')).toBeDefined();
  });

  it('OJ 列表渲染统一编号/命名/KC，且「去训练场」外链指向 /training?problemId=', () => {
    const wrapper = mountPractice(
      {
        examples: [makeExample({ id: 201, displayNo: 'P1-01', displayName: '两数之和', kcCode: 'KC-OP-02' })],
        requiredExamples: 1,
      },
      router,
    );

    const item = wrapper.find('.stage-practice__item');
    expect(item.text()).toContain('P1-01');
    expect(item.text()).toContain('两数之和');
    expect(item.text()).toContain('KC-OP-02');

    const train = wrapper.find('[data-testid="ex-train-201"]');
    expect(train.exists()).toBe(true);
    expect(train.attributes('target')).toBe('_blank');
    const href = train.attributes('href') ?? '';
    expect(href).toContain('/training');
    expect(href).toContain('problemId=201');
    expect(wrapper.find('[data-testid="stage-practice-oj-count"]').text()).toContain('0 / 1');
  });

  it('OJ「标记完成」调 postExampleDone(stageId, problemId)（两参）并 emit doneProblemIds', async () => {
    const wrapper = mountPractice(
      { examples: [makeExample({ id: 201 }), makeExample({ id: 202 })], requiredExamples: 1 },
      router,
    );

    await wrapper.find('[data-testid="ex-mark-201"]').trigger('click');
    await flushPromises();

    expect(vi.mocked(postExampleDone)).toHaveBeenCalledTimes(1);
    expect(vi.mocked(postExampleDone)).toHaveBeenCalledWith(STAGE_ID, 201);
    expect(vi.mocked(postExampleDone).mock.calls[0]).toHaveLength(2);

    expect(wrapper.emitted('progress')?.[0]?.[0]).toMatchObject({
      stageId: STAGE_ID,
      doneProblemIds: [201],
    });
    expect(wrapper.find('[data-testid="ex-mark-201"]').attributes('disabled')).toBeDefined();
  });

  it('客观题全对 + OJ 完成 → 「练」过线（两维都达标才显示 ✓）', async () => {
    const questions = [
      makeQuestion({ id: 11, ordinal: 1, type: 'CHOICE', options: ['print', 'input'] }),
      makeQuestion({ id: 12, ordinal: 2, type: 'JUDGE', options: [] }),
      makeQuestion({ id: 13, ordinal: 3, type: 'FILL', options: [], blankCount: 1 }),
    ];
    const wrapper = mountPractice(
      {
        questions,
        requiredQuestions: 3,
        examples: [makeExample({ id: 201 })],
        requiredExamples: 1,
      },
      router,
    );

    // 全部答对（mock 默认 correct:true）。
    await wrapper.find('[data-testid="practice-q-11-opt-0"]').trigger('click');
    await wrapper.find('[data-testid="practice-q-11-submit"]').trigger('click');
    await wrapper.find('[data-testid="practice-q-12-judge-true"]').trigger('click');
    await wrapper.find('[data-testid="practice-q-12-submit"]').trigger('click');
    await wrapper.find('[data-testid="practice-q-13-blank-0"]').setValue('range');
    await wrapper.find('[data-testid="practice-q-13-submit"]').trigger('click');
    await flushPromises();

    // 客观题达标但 OJ 未完成 → 尚未过线。
    expect(wrapper.find('[data-testid="stage-practice-quiz-count"]').text()).toContain('3 / 3');
    expect(wrapper.find('[data-testid="stage-practice-passed"]').exists()).toBe(false);

    await wrapper.find('[data-testid="ex-mark-201"]').trigger('click');
    await flushPromises();

    expect(wrapper.find('[data-testid="stage-practice-oj-count"]').text()).toContain('1 / 1');
    expect(wrapper.find('[data-testid="stage-practice-passed"]').exists()).toBe(true);
  });

  it('空 questions / examples 显示占位', () => {
    const wrapper = mountPractice({ questions: [], examples: [] }, router);
    expect(wrapper.find('[data-testid="stage-practice-quiz-empty"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="stage-practice-oj-empty"]').exists()).toBe(true);
    // 无任何要求时不误判过线。
    expect(wrapper.find('[data-testid="stage-practice-passed"]').exists()).toBe(false);
  });
});
