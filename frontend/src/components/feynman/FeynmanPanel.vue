<script setup lang="ts">
import { computed, ref } from 'vue';
import { storeToRefs } from 'pinia';
import { useReducedMotion } from '@/composables/useReducedMotion';
import { useFeynmanStore } from '@/stores/feynman';
import BugHunterGame from '@/components/feynman/BugHunterGame.vue';
import type { FeynmanJudgeResultDto } from '@/api/feynman';

const props = defineProps<{
  kcCode: string;
}>();

const emit = defineEmits<{ (e: 'change-kc'): void }>();

const store = useFeynmanStore();
const {
  currentSession,
  turns,
  loading,
  teaching,
  judging,
  error,
  latestProducedCode,
  latestJudgeResult,
} = storeToRefs(store);
const teachingText = ref('');
const prefersReducedMotion = useReducedMotion();

const canTeach = computed(
  () => currentSession.value !== null && teachingText.value.trim().length > 0 && !teaching.value,
);

const canJudge = computed(
  () =>
    currentSession.value !== null && latestProducedCode.value.trim().length > 0 && !judging.value,
);

const misconceptions = computed(() => currentSession.value?.tuteeState.misconceptions ?? []);

const judgeText = computed(() => {
  const result = latestJudgeResult.value;
  if (!result) {
    return '等待学弟提交可判题代码';
  }
  return result.messageZh;
});

const misconceptionSummary = computed(() => misconceptions.value.map((item) => item.tag).join('、'));
const latestTurn = computed(() => turns.value.at(-1) ?? null);
// 错误猎人找到的 bug 提示接入讲解（B）：把那处 bug 的提示带进「讲明白」环节，形成闭环。
const teachHint = computed(() => store.bugHunt?.hintMd ?? '');

function judgeClass(result: FeynmanJudgeResultDto | null): string {
  if (!result) {
    return 'feynman-panel__judge--pending';
  }
  return `feynman-panel__judge--${result.status.toLowerCase()}`;
}

async function start(): Promise<void> {
  try {
    await store.startSession(props.kcCode);
  } catch {
    // 错误已写入 store.error 并在面板内提示。
  }
}

async function submitTeaching(): Promise<void> {
  const explanation = teachingText.value.trim();
  if (!canTeach.value || explanation.length === 0) {
    return;
  }
  try {
    await store.teach(explanation);
  } catch {
    // 错误已写入 store.error 并在面板内提示，这里吞掉以避免未处理的 promise rejection。
  }
}

async function submitJudge(): Promise<void> {
  if (!canJudge.value) {
    return;
  }
  try {
    await store.judge();
  } catch {
    // 同上：错误经 store.error 呈现。
  }
}
</script>

<template>
  <section
    class="feynman-panel"
    :class="{ 'feynman-panel--reduced-motion': prefersReducedMotion }"
    :data-motion="prefersReducedMotion ? 'reduced' : 'default'"
    data-testid="feynman-panel"
  >
    <header class="feynman-panel__bar">
      <div class="feynman-panel__bar-title">
        <span class="feynman-panel__eyebrow">费曼反演</span>
        <h2 class="feynman-panel__title">教会 AI 学弟 · {{ kcCode }}</h2>
      </div>
      <p
        v-if="currentSession"
        class="feynman-panel__misc"
        data-testid="feynman-tutee-state"
      >
        <span class="feynman-panel__misc-tag">学弟误区</span>
        <span class="feynman-panel__misc-text">{{
          misconceptionSummary || '此 KC 暂无真实错误史，不编造误解'
        }}</span>
      </p>
      <div class="feynman-panel__bar-actions">
        <button
          type="button"
          class="feynman-panel__ghost"
          @click="emit('change-kc')"
        >
          换知识点
        </button>
        <button
          type="button"
          class="feynman-panel__start"
          data-testid="feynman-start"
          :disabled="loading"
          @click="start"
        >
          {{ currentSession ? '重新开始' : '开始费曼反演' }}
        </button>
      </div>
    </header>

    <p
      v-if="error"
      class="feynman-panel__error"
      role="alert"
    >
      {{ error }}
    </p>

    <p
      v-if="!currentSession"
      class="feynman-panel__intro-hint"
    >
      点「开始费曼反演」召唤会犯你同款错的 AI 学弟：先玩错误猎人揪出它代码里的 bug，再用自己的话讲明白、跑真判题出师。
    </p>

    <div
      v-else
      class="feynman-panel__stage"
    >
      <div class="feynman-panel__game">
        <BugHunterGame />
      </div>

      <div class="feynman-panel__flow">
        <article
          class="feynman-panel__card feynman-panel__dialog"
          data-testid="feynman-dialog"
        >
          <h3>学弟对话</h3>
          <p
            v-if="turns.length === 0"
            class="feynman-panel__muted"
          >
            学弟会在你讲解后复述自己的理解。
          </p>
          <template v-else>
            <div
              v-for="turn in turns"
              :key="turn.id"
              class="feynman-panel__turn"
            >
              <strong>{{ turn.role === 'TUTEE' ? 'AI 学弟' : '我' }}</strong>
              <p>{{ turn.content }}</p>
            </div>
            <p
              v-if="latestTurn?.taughtFix"
              class="feynman-panel__fix"
              data-testid="feynman-fix"
            >
              <span class="feynman-panel__fix-label">学弟吸收的修正点</span>
              {{ latestTurn.taughtFix }}
            </p>
          </template>
        </article>

        <article class="feynman-panel__card">
          <h3>讲明白</h3>
          <p
            v-if="teachHint"
            class="feynman-panel__teach-hint"
            data-testid="feynman-teach-hint"
          >
            错误猎人那处 bug：{{ teachHint }} —— 用一句话讲清它为什么错，再让学弟改对跑真判题。
          </p>
          <textarea
            v-model="teachingText"
            class="feynman-panel__teach-input"
            data-testid="feynman-teach-input"
            placeholder="用自己的话解释概念、指出学弟错在哪里，但不要直接贴完整答案。"
            rows="4"
          />
          <button
            type="button"
            class="feynman-panel__teach-submit"
            data-testid="feynman-teach-submit"
            :disabled="!canTeach"
            @click="submitTeaching"
          >
            {{ teaching ? '学弟思考中...' : '讲给学弟听' }}
          </button>
        </article>

        <article
          class="feynman-panel__card feynman-panel__verdict"
          :class="judgeClass(latestJudgeResult)"
        >
          <h3>学弟代码 · 真判题</h3>
          <pre
            class="feynman-panel__code"
            data-testid="feynman-code"
          ><code>{{ latestProducedCode || '等待学弟写出可判题代码' }}</code></pre>
          <p
            class="feynman-panel__judge-msg"
            data-testid="feynman-judge"
          >
            {{ judgeText }}
          </p>
          <button
            type="button"
            class="feynman-panel__teach-submit"
            data-testid="feynman-judge-submit"
            :disabled="!canJudge"
            @click="submitJudge"
          >
            {{ judging ? '判题中...' : '真判题裁定' }}
          </button>
        </article>
      </div>
    </div>
  </section>
</template>

<style scoped>
.feynman-panel {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-3);
  height: 100%;
  min-height: 0;
  color: var(--pyt-color-text-primary);
  font-family: var(--pyt-font-read);
}

/* ── 顶部紧凑信息条：标题 / 学弟误区 / 操作（替代原大 header + 学弟状态卡）── */
.feynman-panel__bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  flex-shrink: 0;
  gap: var(--pyt-space-2) var(--pyt-space-4);
  padding: var(--pyt-space-2) var(--pyt-space-3);
  border: 2px solid var(--pyt-color-border-strong);
  border-left: 4px solid var(--pyt-color-gold);
  border-radius: var(--pyt-radius-md);
  background: var(--pyt-color-bg-elevated);
  box-shadow: var(--pyt-shadow-card);
}

.feynman-panel__bar-title {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}

.feynman-panel__eyebrow {
  font-family: var(--pyt-font-pixel);
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
}

.feynman-panel__title {
  margin: 0;
  font-family: var(--pyt-font-pixel);
  font-size: clamp(14px, 1.6vw, 17px);
  color: var(--pyt-color-text-primary);
}

.feynman-panel__misc {
  display: inline-flex;
  align-items: center;
  gap: var(--pyt-space-2);
  margin: 0;
  min-width: 0;
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-hint);
}

.feynman-panel__misc-tag {
  flex-shrink: 0;
  padding: 1px 8px;
  border: 1px solid var(--pyt-color-warning);
  border-radius: var(--pyt-radius-full);
  background: var(--pyt-color-warning-bg);
  font-family: var(--pyt-font-pixel);
  font-size: var(--pyt-font-size-xs);
}

.feynman-panel__bar-actions {
  display: flex;
  gap: var(--pyt-space-2);
  margin-left: auto;
}

.feynman-panel__ghost,
.feynman-panel__start,
.feynman-panel__teach-submit {
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-sm);
  padding: var(--pyt-space-2) var(--pyt-space-3);
  font-family: var(--pyt-font-pixel);
  font-size: var(--pyt-font-size-sm);
  cursor: pointer;
  transition: transform var(--pyt-motion-base) ease;
}

.feynman-panel__ghost {
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-text-primary);
}

.feynman-panel__start,
.feynman-panel__teach-submit {
  background: var(--pyt-color-primary);
  color: var(--pyt-color-text-on-dark);
}

.feynman-panel__ghost:hover:not(:disabled),
.feynman-panel__start:hover:not(:disabled),
.feynman-panel__teach-submit:hover:not(:disabled) {
  transform: translateY(-1px);
}

.feynman-panel__ghost:disabled,
.feynman-panel__start:disabled,
.feynman-panel__teach-submit:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.feynman-panel__error {
  flex-shrink: 0;
  margin: 0;
  padding: var(--pyt-space-2) var(--pyt-space-3);
  border: 2px solid var(--pyt-color-danger);
  border-radius: var(--pyt-radius-md);
  background: var(--pyt-color-danger-bg);
  color: var(--pyt-color-danger);
  font-size: var(--pyt-font-size-sm);
}

.feynman-panel__intro-hint {
  flex-shrink: 0;
  margin: 0;
  padding: var(--pyt-space-4);
  border: 2px dashed var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-text-hint);
  line-height: 1.7;
}

/* ── 主舞台：左「错误猎人」｜右「讲明白 → 真判题」，各自内部滚动，整页不上下滑 ── */
.feynman-panel__stage {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr);
  gap: var(--pyt-space-3);
}

.feynman-panel__game,
.feynman-panel__flow {
  min-height: 0;
  overflow-y: auto;
}

.feynman-panel__flow {
  display: grid;
  grid-template-rows: auto auto auto;
  gap: var(--pyt-space-3);
  align-content: start;
  padding-right: 2px;
}

.feynman-panel__card {
  display: grid;
  gap: var(--pyt-space-2);
  padding: var(--pyt-space-3);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  background: var(--pyt-color-bg-soft);
  box-shadow: var(--pyt-shadow-card);
  font-size: var(--pyt-font-size-sm);
}

.feynman-panel__card h3 {
  margin: 0;
  font-family: var(--pyt-font-pixel);
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-hint);
}

.feynman-panel__muted {
  margin: 0;
  color: var(--pyt-color-text-muted);
}

/* 对话可能多轮：卡片内部滚动，避免把右栏撑高破坏视口适配。 */
.feynman-panel__dialog {
  max-height: 38vh;
  overflow-y: auto;
}

.feynman-panel__turn {
  display: grid;
  gap: var(--pyt-space-1);
  padding: var(--pyt-space-2);
  background: var(--pyt-color-bg-primary);
  border-left: 4px solid var(--pyt-color-info);
  border-radius: var(--pyt-radius-sm);
}

.feynman-panel__turn p {
  margin: 0;
  line-height: 1.6;
}

.feynman-panel__turn strong {
  font-family: var(--pyt-font-pixel);
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-hint);
}

.feynman-panel__fix {
  margin: 0;
  padding: var(--pyt-space-2);
  border: 1px solid var(--pyt-color-success);
  border-radius: var(--pyt-radius-sm);
  background: var(--pyt-color-success-bg);
  line-height: 1.6;
}

.feynman-panel__fix-label {
  display: block;
  font-family: var(--pyt-font-pixel);
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-success);
}

.feynman-panel__teach-hint {
  margin: 0;
  padding: var(--pyt-space-2);
  border: 1px solid var(--pyt-color-warning);
  border-radius: var(--pyt-radius-sm);
  background: var(--pyt-color-warning-bg);
  color: var(--pyt-color-text-hint);
  line-height: 1.6;
}

.feynman-panel__teach-input {
  width: 100%;
  resize: vertical;
  min-height: 64px;
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
  background: var(--pyt-color-bg-primary);
  color: var(--pyt-color-text-primary);
  padding: var(--pyt-space-2);
  font: inherit;
  line-height: 1.6;
}

.feynman-panel__teach-submit {
  justify-self: start;
}

.feynman-panel__code {
  margin: 0;
  max-height: 22vh;
  padding: var(--pyt-space-2);
  overflow: auto;
  border: 2px solid var(--pyt-color-text-hint);
  border-radius: var(--pyt-radius-sm);
  background: var(--pyt-color-terminal-bg);
  color: var(--pyt-color-terminal-text);
  font-family: var(--pyt-font-mono);
  font-size: var(--pyt-font-size-sm);
}

.feynman-panel__judge-msg {
  margin: 0;
  line-height: 1.6;
}

.feynman-panel__verdict {
  border-color: var(--pyt-color-info);
}

.feynman-panel__judge--passed {
  border-color: var(--pyt-color-success);
  background: var(--pyt-color-success-bg);
}

.feynman-panel__judge--failed,
.feynman-panel__judge--error {
  border-color: var(--pyt-color-danger);
  background: var(--pyt-color-danger-bg);
}

.feynman-panel__judge--pending {
  border-color: var(--pyt-color-warning);
  background: var(--pyt-color-warning-bg);
}

/* 窄屏：单列堆叠 + 允许整页自然滚动（不强行锁视口高度）。 */
@media (max-width: 900px) {
  .feynman-panel {
    height: auto;
  }

  .feynman-panel__stage {
    grid-template-columns: 1fr;
    overflow-y: visible;
  }

  .feynman-panel__game,
  .feynman-panel__flow {
    overflow-y: visible;
  }

  .feynman-panel__dialog,
  .feynman-panel__code {
    max-height: none;
  }

  .feynman-panel__bar-actions {
    margin-left: 0;
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .feynman-panel__ghost,
  .feynman-panel__start,
  .feynman-panel__teach-submit {
    transition: none;
  }
}

.feynman-panel--reduced-motion .feynman-panel__ghost,
.feynman-panel--reduced-motion .feynman-panel__start,
.feynman-panel--reduced-motion .feynman-panel__teach-submit {
  transition: none;
}
</style>
