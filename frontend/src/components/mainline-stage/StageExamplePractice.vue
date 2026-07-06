<script setup lang="ts">
/**
 * StageExamplePractice —— 主线小章节「练」：站内客观题 inline 答题 + OJ 跳训练场。
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
import { useToastStore } from '@stores/toast';
import type { StageTaskProgress } from './stageTypes';
import PracticeQuestionCard from './practice/PracticeQuestionCard.vue';

const props = defineProps<{
  stageId: number;
  questions: StagePracticeQuestionDto[];
  requiredQuestions: number;
  correctQuestionIds: number[];
  examples: StageExampleDto[];
  requiredExamples: number;
  doneProblemIds: number[];
}>();

const emit = defineEmits<{ (e: 'progress', p: StageTaskProgress): void }>();

const router = useRouter();
const toast = useToastStore();
const correctSet = ref<Set<number>>(new Set(props.correctQuestionIds));
const doneSet = ref<Set<number>>(new Set(props.doneProblemIds));
const pending = ref<Set<number>>(new Set());

// ——— 客观题维度 ———
const sortedQuestions = computed<StagePracticeQuestionDto[]>(() =>
  [...props.questions].sort((a, b) => a.ordinal - b.ordinal),
);
const quizRequired = computed<number>(() =>
  props.requiredQuestions > 0 ? props.requiredQuestions : props.questions.length,
);
const quizCorrect = computed<number>(
  () => props.questions.filter((q) => correctSet.value.has(q.id)).length,
);
const quizPassed = computed<boolean>(() => quizCorrect.value >= quizRequired.value);

// ——— OJ 维度 ———
const ojRequired = computed<number>(() =>
  props.requiredExamples > 0 ? props.requiredExamples : props.examples.length,
);
const ojDone = computed<number>(() => props.examples.filter((e) => doneSet.value.has(e.id)).length);
const ojPassed = computed<boolean>(() => ojDone.value >= ojRequired.value);

// 两维都达标且至少有一项要求 → 「练」过线（空练习卡不误判过线）。
const passed = computed<boolean>(
  () => quizPassed.value && ojPassed.value && quizRequired.value + ojRequired.value > 0,
);

function isQuestionDone(questionId: number): boolean {
  return correctSet.value.has(questionId);
}

function isExampleDone(problemId: number): boolean {
  return doneSet.value.has(problemId);
}

function trainingHref(problemId: number): string {
  return router.resolve({ path: '/training', query: { problemId: String(problemId) } }).href;
}

/** 子卡答对回流：计入 correctSet 并向上 emit 客观题维度进度。 */
function onQuestionCorrect(questionId: number): void {
  if (correctSet.value.has(questionId)) return;
  correctSet.value = new Set(correctSet.value).add(questionId);
  emit('progress', { stageId: props.stageId, correctQuestionIds: [...correctSet.value] });
}

async function markExampleDone(problemId: number): Promise<void> {
  if (doneSet.value.has(problemId) || pending.value.has(problemId)) return;
  pending.value = new Set(pending.value).add(problemId);
  try {
    // 只 await 成功；用已知 problemId 乐观更新（不读返回体，规避 DTO 形状差异）。
    await postExampleDone(props.stageId, problemId);
    doneSet.value = new Set(doneSet.value).add(problemId);
    emit('progress', { stageId: props.stageId, doneProblemIds: [...doneSet.value] });
  } catch (error) {
    // R2 evidence 收紧后，无通过提交的「标记完成」会被 400 拒绝——把原因说给学生听，
    // 引导先去训练场判题通过（其余错误也提示，可重试，服务端幂等）。
    const message =
      error instanceof Error && error.message
        ? error.message
        : '标记失败，请稍后重试';
    toast.show(message, 'error');
  } finally {
    const next = new Set(pending.value);
    next.delete(problemId);
    pending.value = next;
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
          v-for="(q, idx) in sortedQuestions"
          :key="q.id"
          :stage-id="stageId"
          :question="q"
          :index="idx + 1"
          :done="isQuestionDone(q.id)"
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
          v-for="ex in examples"
          :key="ex.id"
          class="stage-practice__item"
          :class="{ 'stage-practice__item--done': isExampleDone(ex.id) }"
        >
          <span class="stage-practice__name">
            <span
              class="stage-practice__check"
              aria-hidden="true"
            >{{ isExampleDone(ex.id) ? '✓' : '○' }}</span>
            <span class="stage-practice__label">
              <span class="stage-practice__no">{{ ex.displayNo }}</span>
              {{ ex.displayName }}
              <span
                v-if="ex.kcCode"
                class="stage-practice__kc"
              >{{ ex.kcCode }}</span>
            </span>
          </span>
          <span class="stage-practice__actions">
            <a
              class="stage-practice__open"
              :data-testid="`ex-train-${ex.id}`"
              :href="trainingHref(ex.id)"
              target="_blank"
              rel="noopener noreferrer"
            >去训练场 →</a>
            <button
              type="button"
              class="stage-practice__mark"
              :data-testid="`ex-mark-${ex.id}`"
              :disabled="isExampleDone(ex.id)"
              @click="markExampleDone(ex.id)"
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
