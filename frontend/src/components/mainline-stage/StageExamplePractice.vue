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
    <header class="stage-practice__head">
      <h3 class="stage-practice__title">
        <span
          class="stage-practice__icon"
          aria-hidden="true"
        >⌨</span> 练 · 客观题 &amp; 实战
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
        客观题自检（答对 {{ quizRequired }} 道）
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
        编程实战（去训练场真实判题，完成 {{ ojRequired }} 道）
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
.stage-practice {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-3);
}
.stage-practice__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pyt-space-2);
  flex-wrap: wrap;
}
.stage-practice__title {
  margin: 0;
  font-family: var(--pyt-font-pixel), sans-serif;
  font-size: var(--pyt-font-size-lg);
  color: var(--pyt-color-text-primary);
}
.stage-practice__icon {
  color: var(--pyt-color-accent);
}
.stage-practice__status {
  display: inline-flex;
  align-items: center;
  gap: var(--pyt-space-2);
  flex-wrap: wrap;
}
.stage-practice__count {
  font-size: var(--pyt-font-size-xs);
  font-family: var(--pyt-font-pixel-mono), monospace;
  color: var(--pyt-color-text-muted);
}
.stage-practice__passed {
  font-size: var(--pyt-font-size-xs);
  padding: 2px 8px;
  background: var(--pyt-color-success-bg);
  color: var(--pyt-color-success);
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-full);
  white-space: nowrap;
}
.stage-practice__block {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
}
.stage-practice__subtitle {
  margin: 0;
  font-size: var(--pyt-font-size-sm);
  font-weight: 700;
  color: var(--pyt-color-text-hint);
}
.stage-practice__quiz {
  display: grid;
  gap: var(--pyt-space-2);
}
.stage-practice__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: var(--pyt-space-1);
}
.stage-practice__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pyt-space-2);
  padding: var(--pyt-space-2);
  background: var(--pyt-color-bg-soft);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
}
.stage-practice__item--done {
  background: var(--pyt-color-success-bg);
  border-color: var(--pyt-color-success);
}
.stage-practice__name {
  display: inline-flex;
  align-items: center;
  gap: var(--pyt-space-2);
  min-width: 0;
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-primary);
}
.stage-practice__check {
  flex: 0 0 auto;
  font-family: var(--pyt-font-pixel-mono), monospace;
  color: var(--pyt-color-success);
}
.stage-practice__label {
  min-width: 0;
}
.stage-practice__no {
  font-family: var(--pyt-font-pixel-mono), monospace;
  color: var(--pyt-color-text-muted);
  margin-right: var(--pyt-space-1);
}
.stage-practice__kc {
  margin-left: var(--pyt-space-1);
  font-size: var(--pyt-font-size-xs);
  font-family: var(--pyt-font-pixel-mono), monospace;
  color: var(--pyt-color-text-muted);
}
.stage-practice__actions {
  display: inline-flex;
  align-items: center;
  gap: var(--pyt-space-2);
  flex: 0 0 auto;
}
.stage-practice__open {
  font-size: var(--pyt-font-size-xs);
  text-decoration: underline;
  color: var(--pyt-color-info);
}
.stage-practice__mark {
  font-family: var(--pyt-font-pixel), monospace;
  font-size: var(--pyt-font-size-xs);
  padding: 2px 10px;
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
  color: var(--pyt-color-text-primary);
  cursor: pointer;
  box-shadow: 0 2px 0 var(--pyt-color-border);
}
.stage-practice__mark:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  box-shadow: none;
}
.stage-practice__mark:not(:disabled):active {
  transform: translateY(2px);
  box-shadow: none;
}
.stage-practice__empty {
  margin: 0;
  padding: var(--pyt-space-3);
  text-align: center;
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-hint);
  background: var(--pyt-color-bg-soft);
  border: 1px dashed var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
}
</style>
