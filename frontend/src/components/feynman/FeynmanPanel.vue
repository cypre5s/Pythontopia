<script setup lang="ts">
import { computed, ref } from 'vue';
import { storeToRefs } from 'pinia';
import { useReducedMotion } from '@/composables/useReducedMotion';
import { useFeynmanStore } from '@/stores/feynman';
import type { FeynmanJudgeResultDto } from '@/api/feynman';

const props = defineProps<{
  kcCode: string;
}>();

const store = useFeynmanStore();
const { currentSession, turns, loading, teaching, error, latestProducedCode, latestJudgeResult } =
  storeToRefs(store);
const teachingText = ref('');
const prefersReducedMotion = useReducedMotion();

const canTeach = computed(
  () => currentSession.value !== null && teachingText.value.trim().length > 0 && !teaching.value,
);

const misconceptions = computed(() => currentSession.value?.tuteeState.misconceptions ?? []);

const judgeText = computed(() => {
  const result = latestJudgeResult.value;
  if (!result) {
    return '等待学弟提交可判题代码';
  }
  return result.messageZh;
});

const evidenceEvents = computed(() => {
  const ids = new Set<number>();
  for (const misconception of misconceptions.value) {
    for (const id of misconception.evidenceEventIds) {
      ids.add(id);
    }
  }
  const judge = latestJudgeResult.value;
  if (judge) {
    for (const id of judge.evidenceEventIds) {
      ids.add(id);
    }
  }
  return [...ids].sort((a, b) => a - b);
});

function judgeClass(result: FeynmanJudgeResultDto | null): string {
  if (!result) {
    return 'feynman-panel__judge--pending';
  }
  return `feynman-panel__judge--${result.status.toLowerCase()}`;
}

async function start(): Promise<void> {
  await store.startSession(props.kcCode);
}

async function submitTeaching(): Promise<void> {
  const explanation = teachingText.value.trim();
  if (!canTeach.value || explanation.length === 0) {
    return;
  }
  await store.teach(explanation);
}
</script>

<template>
  <section
    class="feynman-panel"
    :class="{ 'feynman-panel--reduced-motion': prefersReducedMotion }"
    :data-motion="prefersReducedMotion ? 'reduced' : 'default'"
    data-testid="feynman-panel"
  >
    <header class="feynman-panel__header">
      <div>
        <p class="feynman-panel__eyebrow">费曼反演</p>
        <h2>教会 AI 学弟：{{ kcCode }}</h2>
      </div>
      <button
        type="button"
        class="feynman-panel__start"
        data-testid="feynman-start"
        :disabled="loading"
        @click="start"
      >
        {{ currentSession ? '重新开始' : '开始费曼反演' }}
      </button>
    </header>

    <p v-if="error" class="feynman-panel__error" role="alert">{{ error }}</p>

    <div class="feynman-panel__grid">
      <article class="feynman-panel__card" data-testid="feynman-tutee-state">
        <h3>学弟状态</h3>
        <p v-if="!currentSession" class="feynman-panel__muted">
          先开启会话，后端会用你的真实错题和事件生成学弟的误解。
        </p>
        <ul v-else-if="misconceptions.length > 0" class="feynman-panel__misconceptions">
          <li v-for="misconception in misconceptions" :key="misconception.tag">
            <span>{{ misconception.tag }}</span>
            <small>{{ misconception.resolved ? '已讲通' : '待讲清' }}</small>
          </li>
        </ul>
        <p v-else class="feynman-panel__muted">这个 KC 暂无真实错误史，不生成虚构误解。</p>
      </article>

      <article class="feynman-panel__card feynman-panel__dialog" data-testid="feynman-dialog">
        <h3>学弟对话</h3>
        <p v-if="turns.length === 0" class="feynman-panel__muted">
          学弟会在你讲解后复述自己的理解。
        </p>
        <div
          v-for="turn in turns"
          :key="turn.id"
          class="feynman-panel__turn"
        >
          <strong>{{ turn.role === 'TUTEE' ? 'AI 学弟' : '我' }}</strong>
          <p>{{ turn.content }}</p>
        </div>
      </article>

      <article class="feynman-panel__card">
        <h3>你的教学</h3>
        <textarea
          v-model="teachingText"
          class="feynman-panel__teach-input"
          data-testid="feynman-teach-input"
          placeholder="用自己的话解释概念、指出学弟错在哪里，但不要直接贴完整答案。"
          rows="6"
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

      <article class="feynman-panel__card">
        <h3>学弟代码</h3>
        <pre class="feynman-panel__code" data-testid="feynman-code"><code>{{ latestProducedCode || '等待学弟写出可判题代码' }}</code></pre>
      </article>

      <article
        class="feynman-panel__card feynman-panel__judge"
        :class="judgeClass(latestJudgeResult)"
        data-testid="feynman-judge"
      >
        <h3>判题结果</h3>
        <p>{{ judgeText }}</p>
      </article>

      <article class="feynman-panel__card" data-testid="feynman-fix">
        <h3>修正点</h3>
        <p v-if="turns.at(-1)?.taughtFix">{{ turns.at(-1)?.taughtFix }}</p>
        <p v-else class="feynman-panel__muted">提交教学后，这里展示学弟吸收了哪一个修正点。</p>
      </article>
    </div>

    <aside class="feynman-panel__evidence" data-testid="feynman-evidence-chain">
      <h3>玻璃盒证据链</h3>
      <ol>
        <li>
          <strong>误解</strong>
          <span v-if="misconceptions.length > 0">
            {{ misconceptions.map((item) => item.tag).join('、') }}
          </span>
          <span v-else>暂无真实误解来源</span>
        </li>
        <li>
          <strong>你的教学</strong>
          <span>{{ teachingText.trim() || '等待输入' }}</span>
        </li>
        <li>
          <strong>判题</strong>
          <span>{{ judgeText }}</span>
        </li>
        <li>
          <strong>证据</strong>
          <span v-if="evidenceEvents.length > 0">
            {{ evidenceEvents.map((id) => `event #${id}`).join(' · ') }}
          </span>
          <span v-else>等待真实 event_id 回链</span>
        </li>
      </ol>
    </aside>
  </section>
</template>

<style scoped>
.feynman-panel {
  display: grid;
  gap: var(--pyt-space-4);
  color: var(--pyt-color-text-primary);
  font-family: var(--pyt-font-read);
}

.feynman-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pyt-space-4);
  padding: var(--pyt-space-4);
  border: 2px solid var(--pyt-color-border);
  background: var(--pyt-color-bg-elevated);
  box-shadow: var(--pyt-shadow-card);
}

.feynman-panel__eyebrow {
  margin: 0 0 var(--pyt-space-1);
  font-family: var(--pyt-font-pixel);
  color: var(--pyt-color-text-muted);
  font-size: var(--pyt-font-size-sm);
}

.feynman-panel h2,
.feynman-panel h3 {
  margin: 0;
}

.feynman-panel__start,
.feynman-panel__teach-submit {
  border: 2px solid var(--pyt-color-border-strong);
  background: var(--pyt-color-primary);
  color: var(--pyt-color-text-on-dark);
  padding: var(--pyt-space-2) var(--pyt-space-3);
  font-family: var(--pyt-font-pixel);
  cursor: pointer;
  transition: transform var(--pyt-motion-base) ease;
}

.feynman-panel__start:disabled,
.feynman-panel__teach-submit:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.feynman-panel__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--pyt-space-3);
}

.feynman-panel__card,
.feynman-panel__evidence {
  display: grid;
  gap: var(--pyt-space-3);
  padding: var(--pyt-space-4);
  border: 2px solid var(--pyt-color-border);
  background: rgba(255, 247, 230, 0.88);
  box-shadow: var(--pyt-shadow-card);
}

.feynman-panel__muted {
  color: var(--pyt-color-text-muted);
}

.feynman-panel__error {
  margin: 0;
  padding: var(--pyt-space-3);
  border: 1px solid var(--pyt-color-danger);
  background: var(--pyt-color-danger-bg);
  color: var(--pyt-color-danger);
}

.feynman-panel__misconceptions {
  display: grid;
  gap: var(--pyt-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.feynman-panel__misconceptions li {
  display: flex;
  justify-content: space-between;
  gap: var(--pyt-space-2);
  padding: var(--pyt-space-2);
  background: var(--pyt-color-bg-soft);
  border: 1px solid var(--pyt-color-border-soft);
}

.feynman-panel__turn {
  display: grid;
  gap: var(--pyt-space-1);
  padding: var(--pyt-space-2);
  background: var(--pyt-color-bg-soft);
  border-left: 4px solid var(--pyt-color-info);
}

.feynman-panel__turn p {
  margin: 0;
}

.feynman-panel__teach-input {
  width: 100%;
  resize: vertical;
  border: 2px solid var(--pyt-color-border);
  background: var(--pyt-color-bg-primary);
  color: var(--pyt-color-text-primary);
  padding: var(--pyt-space-3);
  font: inherit;
  line-height: 1.7;
}

.feynman-panel__code {
  min-height: 108px;
  margin: 0;
  padding: var(--pyt-space-3);
  overflow-x: auto;
  border: 2px solid var(--pyt-color-text-hint);
  background: var(--pyt-color-terminal-bg);
  color: var(--pyt-color-terminal-text);
  font-family: var(--pyt-font-mono);
}

.feynman-panel__judge {
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

.feynman-panel__evidence ol {
  display: grid;
  gap: var(--pyt-space-2);
  margin: 0;
  padding-left: var(--pyt-space-4);
}

.feynman-panel__evidence li {
  line-height: 1.7;
}

.feynman-panel__evidence strong {
  margin-right: var(--pyt-space-2);
  color: var(--pyt-color-text-hint);
}

@media (max-width: 640px) {
  .feynman-panel__header,
  .feynman-panel__grid {
    grid-template-columns: 1fr;
  }

  .feynman-panel__header {
    display: grid;
  }

  .feynman-panel__grid {
    display: grid;
  }
}

@media (prefers-reduced-motion: reduce) {
  .feynman-panel__start,
  .feynman-panel__teach-submit {
    transition: none;
  }
}

.feynman-panel--reduced-motion .feynman-panel__start,
.feynman-panel--reduced-motion .feynman-panel__teach-submit {
  transition: none;
}
</style>
