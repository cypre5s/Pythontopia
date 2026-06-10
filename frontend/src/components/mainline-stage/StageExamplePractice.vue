<script setup lang="ts">
/**
 * StageExamplePractice —— 主线小章节「练」：完成 stage 的 example_problem_ids。
 *
 * 每题提供「去训练场」外链（新标签打开 `/training?problemId=...`，走真实判题），完成后
 * 调用 `postExampleDone(stageId, problemId, submissionId?)` 打点；亦给「标记完成」兜底。
 * 完成 required_example_count 道即「练」过线。AI 导学陪练仅给入口链接（跳已合并的百晓工坊
 * `/workshop`），本组件**不**实现导学逻辑。
 */
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import { postExampleDone } from '@/api/mainline';
import type { StageTaskProgress } from './stageTypes';

const props = defineProps<{
  stageId: number;
  problemIds: number[];
  requiredExamples: number;
  doneProblemIds: number[];
}>();

const emit = defineEmits<{ (e: 'progress', p: StageTaskProgress): void }>();

const router = useRouter();
const done = ref<Set<number>>(new Set(props.doneProblemIds));
const pending = ref<Set<number>>(new Set());

const requiredCount = computed<number>(() =>
  props.requiredExamples > 0 ? props.requiredExamples : props.problemIds.length,
);
const doneCount = computed<number>(() => props.problemIds.filter((id) => done.value.has(id)).length);
const passed = computed<boolean>(() => requiredCount.value > 0 && doneCount.value >= requiredCount.value);

function trainingHref(problemId: number): string {
  return router.resolve({ path: '/training', query: { problemId: String(problemId) } }).href;
}

const tutorHref = computed<string>(() => router.resolve({ path: '/workshop' }).href);

function isDone(problemId: number): boolean {
  return done.value.has(problemId);
}

async function markDone(problemId: number, submissionId?: number): Promise<void> {
  if (done.value.has(problemId) || pending.value.has(problemId)) return;
  pending.value = new Set(pending.value).add(problemId);
  try {
    // submissionId 缺省时只传两参，便于打点接口幂等且与服务端「无提交证据」语义一致。
    // 只 await 成功；用已知 problemId 乐观更新（不读返回体，规避 DTO 形状差异）。
    if (submissionId === undefined) {
      await postExampleDone(props.stageId, problemId);
    } else {
      await postExampleDone(props.stageId, problemId, submissionId);
    }
    done.value = new Set(done.value).add(problemId);
    emit('progress', { stageId: props.stageId, doneProblemIds: [...done.value] });
  } catch {
    // 静默：可再次「标记完成」重试（服务端幂等）。
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
        >⌨</span> 练 · 例题实战
      </h3>
      <span
        v-if="passed"
        class="stage-practice__passed"
        data-testid="stage-practice-passed"
      >已过线 ✓</span>
      <span
        v-else
        class="stage-practice__count"
        data-testid="stage-practice-count"
      >{{ doneCount }} / {{ requiredCount }} 题</span>
    </header>

    <p class="stage-practice__hint">
      去训练场完成例题（真实判题入库），完成 {{ requiredCount }} 道过线。卡壳可叫上
      <a
        class="stage-practice__tutor"
        data-testid="stage-practice-tutor"
        :href="tutorHref"
        target="_blank"
        rel="noopener noreferrer"
      >AI 导学陪练</a>。
    </p>

    <ul
      v-if="problemIds.length > 0"
      class="stage-practice__list"
    >
      <li
        v-for="(problemId, idx) in problemIds"
        :key="problemId"
        class="stage-practice__item"
        :class="{ 'stage-practice__item--done': isDone(problemId) }"
      >
        <span class="stage-practice__name">
          <span
            class="stage-practice__check"
            aria-hidden="true"
          >{{ isDone(problemId) ? '✓' : '○' }}</span>
          例题 {{ idx + 1 }}（#{{ problemId }}）
        </span>
        <span class="stage-practice__actions">
          <a
            class="stage-practice__open"
            :data-testid="`ex-train-${problemId}`"
            :href="trainingHref(problemId)"
            target="_blank"
            rel="noopener noreferrer"
          >去训练场 →</a>
          <button
            type="button"
            class="stage-practice__mark"
            :data-testid="`ex-mark-${problemId}`"
            :disabled="isDone(problemId)"
            @click="markDone(problemId)"
          >标记完成</button>
        </span>
      </li>
    </ul>
    <p
      v-else
      class="stage-practice__empty"
    >
      本小节暂未关联例题。
    </p>
  </section>
</template>

<style scoped>
.stage-practice {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
}
.stage-practice__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pyt-space-2);
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
.stage-practice__passed {
  font-size: var(--pyt-font-size-xs);
  padding: 2px 8px;
  background: var(--pyt-color-success-bg);
  color: var(--pyt-color-success);
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-full);
  white-space: nowrap;
}
.stage-practice__count {
  font-size: var(--pyt-font-size-xs);
  font-family: var(--pyt-font-pixel-mono), monospace;
  color: var(--pyt-color-text-muted);
}
.stage-practice__hint {
  margin: 0;
  font-size: var(--pyt-font-size-xs);
  line-height: 1.5;
  color: var(--pyt-color-text-muted);
}
.stage-practice__tutor {
  color: var(--pyt-color-accent);
  text-decoration: underline;
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
  font-family: var(--pyt-font-pixel-mono), monospace;
  color: var(--pyt-color-success);
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
