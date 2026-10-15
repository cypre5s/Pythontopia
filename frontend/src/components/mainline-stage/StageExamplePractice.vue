<script setup lang="ts">
/**
 * StageExamplePractice —— 主线小章节「练」：站内客观题、课程 OJ 与 AI 补充编程题。
 *
 * 两个达标维度：
 * - 客观题区：渲染 `questions`（选/判/填，按 ordinal），逐题在 `PracticeQuestionCard` 内
 *   即时判分自检；答对 `requiredQuestions` 道（通常 4，全对）即客观题达标。
 * - OJ 区：渲染 `examples`（统一编号/命名/KC），「去训练场 →」走真实判题，完成后
 *   `postExampleDone` 打点；完成 `requiredExamples` 道（通常 1）即 OJ 达标。
 *
 * 两维都达标 → 「练」过线。子组件答对 / 打点成功后用已知 id 乐观更新并向上 emit
 * 自己负责的维度（correctQuestionIds / doneProblemIds），学习页据此合并刷新总览。
 */
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import { postExampleDone } from '@/api/mainline';
import type { StageExampleDto, StagePracticeQuestionDto } from '@/api/mainline';
import type { BossAttemptResultDto, BossProblemDto } from '@/api/dto/Boss';
import type { ChallengeResult } from '@/api/dto/CodeChallenge';
import { useChallengeJudge } from '@/composables/useChallengeJudge';
import { useLearningStore } from '@stores/learning';
import { useToastStore } from '@stores/toast';
import type { StageTaskProgress } from './stageTypes';
import ChallengeWorkspace from '@/components/challenge/ChallengeWorkspace.vue';
import PracticeQuestionCard from './practice/PracticeQuestionCard.vue';

const props = defineProps<{
  stageId: number;
  kcCodes: string[];
  questions: StagePracticeQuestionDto[];
  requiredQuestions: number;
  correctQuestionIds: number[];
  examples: StageExampleDto[];
  requiredExamples: number;
  doneProblemIds: number[];
}>();

const emit = defineEmits<{ (e: 'progress', p: StageTaskProgress): void }>();

const router = useRouter();
const challenge = useChallengeJudge();
const learning = useLearningStore();
const toast = useToastStore();
const correctQuestionIdSet = ref<Set<number>>(new Set(props.correctQuestionIds));
const completedProblemIdSet = ref<Set<number>>(new Set(props.doneProblemIds));
const pendingProblemIds = ref<Set<number>>(new Set());

// ——— 客观题维度 ———
const sortedQuestions = computed<StagePracticeQuestionDto[]>(() =>
  [...props.questions].sort(
    (leftQuestion, rightQuestion) => leftQuestion.ordinal - rightQuestion.ordinal,
  ),
);
const quizRequired = computed<number>(() =>
  props.requiredQuestions > 0 ? props.requiredQuestions : props.questions.length,
);
const quizCorrect = computed<number>(
  () => props.questions.filter((question) => correctQuestionIdSet.value.has(question.id)).length,
);
const quizPassed = computed<boolean>(() => quizCorrect.value >= quizRequired.value);

// ——— OJ 维度 ———
const ojRequired = computed<number>(() =>
  props.requiredExamples > 0 ? props.requiredExamples : props.examples.length,
);
const ojDone = computed<number>(
  () => props.examples.filter((example) => completedProblemIdSet.value.has(example.id)).length,
);
const ojPassed = computed<boolean>(() => ojDone.value >= ojRequired.value);
const missingExampleKcCodes = computed<string[]>(() =>
  [...new Set(props.kcCodes)].filter(
    (kcCode) => !props.examples.some((example) => example.kcCode === kcCode),
  ),
);
const generatingAiKcCode = ref<string | null>(null);
const judgingAiPractice = ref(false);
const aiPracticeProblem = ref<BossProblemDto | null>(null);
const aiPracticeKcCode = ref<string | null>(null);
const aiPracticeCode = ref('');
const aiPracticeStartedAt = ref(0);
const aiPracticeResult = ref<ChallengeResult | null>(null);
const aiPracticeError = ref<string | null>(null);

// 两维都达标且至少有一项要求 → 「练」过线（空练习卡不误判过线）。
const passed = computed<boolean>(
  () => quizPassed.value && ojPassed.value && quizRequired.value + ojRequired.value > 0,
);

function isQuestionDone(questionId: number): boolean {
  return correctQuestionIdSet.value.has(questionId);
}

function isExampleDone(problemId: number): boolean {
  return completedProblemIdSet.value.has(problemId);
}

function trainingHref(problemId: number): string {
  return router.resolve({ path: '/training', query: { problemId: String(problemId) } }).href;
}

async function generateAiPractice(kcCode: string): Promise<void> {
  if (generatingAiKcCode.value !== null) return;
  generatingAiKcCode.value = kcCode;
  aiPracticeKcCode.value = kcCode;
  aiPracticeProblem.value = null;
  aiPracticeResult.value = null;
  aiPracticeError.value = null;
  try {
    const problem = await challenge.fetchProblem({ kc: kcCode, kind: null, difficulty: 1 });
    if (problem.kcCode !== kcCode) {
      throw new Error('返回题目的知识点与当前选择不一致');
    }
    aiPracticeProblem.value = problem;
    aiPracticeCode.value = problem.starterCode ?? '';
    aiPracticeStartedAt.value = Date.now();
  } catch {
    aiPracticeError.value = 'AI 编程练习暂不可用，请稍后重试。';
  } finally {
    generatingAiKcCode.value = null;
  }
}

function onAiPracticeCodeChange(code: string): void {
  aiPracticeCode.value = code;
  aiPracticeResult.value = null;
}

async function submitAiPractice(): Promise<void> {
  const problem = aiPracticeProblem.value;
  if (!problem || judgingAiPractice.value) return;
  judgingAiPractice.value = true;
  aiPracticeError.value = null;
  try {
    const attempt: BossAttemptResultDto = await challenge.judge(
      problem,
      aiPracticeCode.value,
      Math.max(0, Math.floor((Date.now() - aiPracticeStartedAt.value) / 1000)),
    );
    aiPracticeResult.value = {
      passed: attempt.passed,
      passedCases: attempt.passedTestCases,
      totalCases: attempt.totalTestCases,
      feedback: attempt.feedback,
      ...(attempt.failureFirstHint ? { firstHint: attempt.failureFirstHint } : {}),
    };
    try {
      await learning.refreshAfterEvent(problem.kcCode);
    } catch {
      // A refresh failure must not hide a server-judged result.
    }
  } catch {
    aiPracticeError.value = '服务端判题暂不可用，答案未判定，请重试。';
  } finally {
    judgingAiPractice.value = false;
  }
}

/** 子卡答对回流：计入正确题号集合并向上 emit 客观题维度进度。 */
function onQuestionCorrect(questionId: number): void {
  if (correctQuestionIdSet.value.has(questionId)) return;
  correctQuestionIdSet.value = new Set(correctQuestionIdSet.value).add(questionId);
  emit('progress', {
    stageId: props.stageId,
    correctQuestionIds: [...correctQuestionIdSet.value],
  });
}

async function markExampleDone(problemId: number): Promise<void> {
  if (completedProblemIdSet.value.has(problemId) || pendingProblemIds.value.has(problemId)) return;
  pendingProblemIds.value = new Set(pendingProblemIds.value).add(problemId);
  try {
    // 只 await 成功；用已知 problemId 乐观更新（不读返回体，规避 DTO 形状差异）。
    await postExampleDone(props.stageId, problemId);
    completedProblemIdSet.value = new Set(completedProblemIdSet.value).add(problemId);
    emit('progress', { stageId: props.stageId, doneProblemIds: [...completedProblemIdSet.value] });
  } catch (error) {
    // R2 evidence 收紧后，无通过提交的「标记完成」会被 400 拒绝——把原因说给学生听，
    // 引导先去训练场判题通过（其余错误也提示，可重试，服务端幂等）。
    const message =
      error instanceof Error && error.message
        ? error.message
        : '标记失败，请稍后重试';
    toast.show(message, 'error');
  } finally {
    const nextPendingProblemIds = new Set(pendingProblemIds.value);
    nextPendingProblemIds.delete(problemId);
    pendingProblemIds.value = nextPendingProblemIds;
  }
}
</script>

<template>
  <section
    class="stage-practice"
    data-testid="stage-practice"
  >
    <!-- 学习通作业式顶栏：标题 + 答题进度 -->
    <header class="stage-practice__head">
      <h3 class="stage-practice__title">
        练 · 题目练习
      </h3>
      <div class="stage-practice__status">
        <span
          v-if="questions.length > 0"
          class="stage-practice__count"
          data-testid="stage-practice-quiz-count"
        >客观题 {{ quizCorrect }} / {{ quizRequired }}</span>
        <span
          v-if="examples.length > 0"
          class="stage-practice__count"
          data-testid="stage-practice-oj-count"
        >OJ {{ ojDone }} / {{ ojRequired }}</span>
        <span
          v-if="passed"
          class="stage-practice__passed"
          data-testid="stage-practice-passed"
        >已过线 ✓</span>
      </div>
    </header>

    <!-- 客观题区 -->
    <div class="stage-practice__block">
      <h4 class="stage-practice__subtitle">
        一、客观题 <span
          v-if="questions.length > 0"
          class="stage-practice__subnote"
        >（共 {{ questions.length }} 题，答对 {{ quizRequired }} 题过线）</span>
      </h4>
      <div
        v-if="questions.length > 0"
        class="stage-practice__quiz"
      >
        <PracticeQuestionCard
          v-for="(question, questionIndex) in sortedQuestions"
          :key="question.id"
          :stage-id="stageId"
          :question="question"
          :index="questionIndex + 1"
          :done="isQuestionDone(question.id)"
          @correct="onQuestionCorrect"
        />
      </div>
      <p
        v-else
        class="stage-practice__empty"
        data-testid="stage-practice-quiz-empty"
      >
        本小节暂无客观题。
      </p>
    </div>

    <!-- OJ 区 -->
    <div class="stage-practice__block">
      <h4 class="stage-practice__subtitle">
        二、编程实战 <span
          v-if="examples.length > 0"
          class="stage-practice__subnote"
        >（去训练场真实判题，完成 {{ ojRequired }} 道）</span>
      </h4>
      <ul
        v-if="examples.length > 0"
        class="stage-practice__list"
      >
        <li
          v-for="example in examples"
          :key="example.id"
          class="stage-practice__item"
          :class="{ 'stage-practice__item--done': isExampleDone(example.id) }"
        >
          <span class="stage-practice__name">
            <span
              class="stage-practice__check"
              aria-hidden="true"
            >{{ isExampleDone(example.id) ? '✓' : '○' }}</span>
            <span class="stage-practice__label">
              <span class="stage-practice__no">{{ example.displayNo }}</span>
              {{ example.displayName }}
              <span
                v-if="example.kcCode"
                class="stage-practice__kc"
              >{{ example.kcCode }}</span>
            </span>
          </span>
          <span class="stage-practice__actions">
            <a
              class="stage-practice__open"
              :data-testid="`ex-train-${example.id}`"
              :href="trainingHref(example.id)"
              target="_blank"
              rel="noopener noreferrer"
            >去训练场 →</a>
            <button
              type="button"
              class="stage-practice__mark"
              :data-testid="`ex-mark-${example.id}`"
              :disabled="isExampleDone(example.id)"
              @click="markExampleDone(example.id)"
            >标记完成</button>
          </span>
        </li>
      </ul>
      <p
        v-else
        class="stage-practice__empty"
        data-testid="stage-practice-oj-empty"
      >
        本小节暂无编程题。
      </p>
      <template v-if="missingExampleKcCodes.length > 0">
        <p class="stage-practice__empty">
          以下知识点暂无主线编程题，可申请 AI 补充练习；题目通过现有质量审查后，才会提供给学生并进入共享题库：
        </p>
        <ul class="stage-practice__list">
          <li
            v-for="kcCode in missingExampleKcCodes"
            :key="kcCode"
            class="stage-practice__item"
          >
            <span class="stage-practice__name">{{ kcCode }}</span>
            <button
              type="button"
              class="stage-practice__mark"
              :data-testid="`stage-missing-oj-practice-${kcCode}`"
              :disabled="generatingAiKcCode !== null"
              @click="generateAiPractice(kcCode)"
            >
              {{ generatingAiKcCode === kcCode ? '生成中…' : '生成 AI 编程练习 →' }}
            </button>
          </li>
        </ul>
        <p
          v-if="aiPracticeError"
          class="stage-practice__empty"
          role="alert"
          data-testid="stage-ai-practice-error"
        >
          {{ aiPracticeError }}
        </p>
        <div
          v-if="aiPracticeProblem"
          class="stage-practice__ai-workspace"
          data-testid="stage-ai-practice-workspace"
        >
          <ChallengeWorkspace
            :problem="aiPracticeProblem"
            :kind="null"
            :code="aiPracticeCode"
            title="AI 编程补充练习"
            :kc-code="aiPracticeKcCode"
            :difficulty="aiPracticeProblem.difficulty"
            :ai-generated="aiPracticeProblem.aiGenerated"
            :result="aiPracticeResult"
            @update:code="onAiPracticeCodeChange"
          >
            <template #actions>
              <button
                type="button"
                class="stage-practice__mark"
                data-testid="stage-ai-practice-submit"
                :disabled="judgingAiPractice"
                @click="submitAiPractice"
              >
                {{ judgingAiPractice ? '判题中…' : '提交并判题' }}
              </button>
            </template>
          </ChallengeWorkspace>
          <p
            v-if="aiPracticeResult"
            data-testid="stage-ai-practice-result"
            role="status"
          >
            {{ aiPracticeResult.feedback }}
          </p>
        </div>
      </template>
    </div>
  </section>
</template>

<style scoped>
/* 学习通作业式白纸风：白底细线、灰字蓝点缀，整卷一张纸，不用像素字/重边框/大色块。 */
.stage-practice {
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #e6e8ec;
  border-radius: 4px;
  font-family: var(--pyt-font-read), sans-serif;
}
.stage-practice__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pyt-space-2);
  flex-wrap: wrap;
  padding: 14px 18px;
  border-bottom: 1px solid #ececec;
}
.stage-practice__title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #333;
}
.stage-practice__status {
  display: inline-flex;
  align-items: center;
  gap: var(--pyt-space-3);
  flex-wrap: wrap;
}
.stage-practice__count {
  font-size: 12px;
  color: #8a8a8a;
}
.stage-practice__passed {
  font-size: 12px;
  padding: 1px 10px;
  background: #f2faf4;
  color: #1fa657;
  border: 1px solid #b9e4c7;
  border-radius: 999px;
  white-space: nowrap;
}
.stage-practice__block {
  display: flex;
  flex-direction: column;
}
.stage-practice__subtitle {
  margin: 0;
  padding: 12px 18px 8px;
  font-size: 14px;
  font-weight: 700;
  color: #333;
  background: #fafbfc;
  border-bottom: 1px solid #f0f1f3;
}
.stage-practice__subnote {
  font-size: 12px;
  font-weight: 400;
  color: #9aa0a8;
}
.stage-practice__quiz {
  display: grid;
}
.stage-practice__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
}
.stage-practice__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pyt-space-2);
  padding: 12px 18px;
  background: #fff;
  border-bottom: 1px solid #ececec;
}
.stage-practice__item:last-child {
  border-bottom: 0;
}
.stage-practice__item--done {
  background: #fafdfb;
}
.stage-practice__name {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  font-size: 14px;
  color: #333;
}
.stage-practice__check {
  flex: 0 0 auto;
  color: #1fa657;
}
.stage-practice__label {
  min-width: 0;
}
.stage-practice__no {
  color: #8a8a8a;
  margin-right: 6px;
  font-size: 13px;
}
.stage-practice__kc {
  margin-left: 6px;
  font-size: 12px;
  color: #9aa0a8;
}
.stage-practice__actions {
  display: inline-flex;
  align-items: center;
  gap: var(--pyt-space-3);
  flex: 0 0 auto;
}
.stage-practice__open {
  font-size: 13px;
  text-decoration: none;
  color: #3a7bd5;
}
.stage-practice__open:hover {
  text-decoration: underline;
}
.stage-practice__mark {
  font-family: var(--pyt-font-read), sans-serif;
  font-size: 13px;
  min-height: 0;
  padding: 3px 12px;
  background: #fff;
  border: 1px solid #d4d8df;
  border-radius: 4px;
  box-shadow: none;
  color: #555;
  cursor: pointer;
}
.stage-practice__mark:active {
  transform: none;
  box-shadow: none;
}
.stage-practice__mark:hover:not(:disabled) {
  border-color: #3a7bd5;
  color: #3a7bd5;
}
.stage-practice__mark:disabled {
  background: #f7f8fa;
  color: #9aa0a8;
  cursor: not-allowed;
}
.stage-practice__empty {
  margin: 0;
  padding: 18px;
  text-align: center;
  font-size: 13px;
  color: #9aa0a8;
}
</style>
