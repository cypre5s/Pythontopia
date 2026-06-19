<script setup lang="ts">
/**
 * 错误猎人：学弟写出一段含 bug 的代码，玩家限时点出错误行；命中/误报/漏判即时判分。
 * bug 行号由后端（buggy/fixed diff）给出，前端只负责交互与计分，不持有"正确答案代码"。
 */
import { computed, onUnmounted, ref } from 'vue';
import { useFeynmanStore } from '@/stores/feynman';

const ROUND_SECONDS = 30;
const HIT_SCORE = 10;
const WRONG_PENALTY = 4;

const store = useFeynmanStore();

type Phase = 'idle' | 'playing' | 'done';
const phase = ref<Phase>('idle');
const remaining = ref(ROUND_SECONDS);
const score = ref(0);
const selected = ref<Set<number>>(new Set());
const result = ref<{ hits: number; wrong: number; missed: number; perfect: boolean } | null>(null);
let timerId: number | undefined;

const hunt = computed(() => store.bugHunt);
const lines = computed<string[]>(() =>
  hunt.value?.buggyCode ? hunt.value.buggyCode.replace(/\n$/, '').split('\n') : [],
);
const bugSet = computed(() => new Set(hunt.value?.bugLineIndices ?? []));
const playable = computed(() => (hunt.value?.buggyCode?.trim().length ?? 0) > 0);

const sourceLabel = computed(() => {
  switch (hunt.value?.source) {
    case 'real_misconception':
      return '学弟复现了你的同款错误，找出它写错的那一行';
    case 'generic_beginner_bug':
      return '学弟随手写了个常见初学者陷阱，找出错误行';
    default:
      return '';
  }
});

function clearTimer(): void {
  if (timerId !== undefined) {
    window.clearInterval(timerId);
    timerId = undefined;
  }
}

async function start(): Promise<void> {
  const data = await store.startBugHunt();
  if (!data || !data.buggyCode) {
    phase.value = 'idle';
    return;
  }
  selected.value = new Set();
  result.value = null;
  score.value = 0;
  remaining.value = ROUND_SECONDS;
  phase.value = 'playing';
  clearTimer();
  timerId = window.setInterval(() => {
    remaining.value -= 1;
    if (remaining.value <= 0) {
      finish();
    }
  }, 1000);
}

function toggleLine(index: number): void {
  if (phase.value !== 'playing') {
    return;
  }
  const next = new Set(selected.value);
  if (next.has(index)) {
    next.delete(index);
  } else {
    next.add(index);
  }
  selected.value = next;
}

function finish(): void {
  clearTimer();
  phase.value = 'done';
  let hits = 0;
  let wrong = 0;
  for (const index of selected.value) {
    if (bugSet.value.has(index)) {
      hits += 1;
    } else {
      wrong += 1;
    }
  }
  const missed = [...bugSet.value].filter((index) => !selected.value.has(index)).length;
  const perfect = hits > 0 && missed === 0 && wrong === 0;
  // 命中得分、误报扣分；全对则按剩余秒数给时间奖励。
  const timeBonus = perfect ? Math.max(0, remaining.value) : 0;
  score.value = Math.max(0, hits * HIT_SCORE - wrong * WRONG_PENALTY + timeBonus);
  result.value = { hits, wrong, missed, perfect };
}

function lineClass(index: number): string {
  if (phase.value === 'done') {
    if (bugSet.value.has(index)) {
      return selected.value.has(index) ? 'code-line--hit' : 'code-line--missed';
    }
    return selected.value.has(index) ? 'code-line--wrong' : '';
  }
  return selected.value.has(index) ? 'code-line--selected' : '';
}

onUnmounted(clearTimer);
</script>

<template>
  <section
    class="bug-hunter"
    data-testid="bug-hunter"
  >
    <header class="bug-hunter__head">
      <div>
        <h3 class="bug-hunter__title">
          错误猎人
        </h3>
        <p class="bug-hunter__sub">
          {{ phase === 'idle' ? '限时找出学弟代码里的 bug 行——命中得分、误报扣分。' : sourceLabel }}
        </p>
      </div>
      <div class="bug-hunter__meta">
        <span
          v-if="phase === 'playing'"
          class="bug-hunter__timer"
          :class="{ 'bug-hunter__timer--urgent': remaining <= 5 }"
          data-testid="bug-hunter-timer"
        >
          ⏱ {{ remaining }}s
        </span>
        <span
          v-if="phase !== 'idle'"
          class="bug-hunter__score"
          data-testid="bug-hunter-score"
        >
          {{ score }} 分
        </span>
        <button
          type="button"
          class="bug-hunter__action"
          data-testid="bug-hunter-start"
          :disabled="store.hunting"
          @click="start"
        >
          {{ store.hunting ? '生成中…' : phase === 'idle' ? '开始错误猎人' : '换一局' }}
        </button>
      </div>
    </header>

    <p
      v-if="phase !== 'idle' && !playable"
      class="bug-hunter__empty"
      data-testid="bug-hunter-empty"
    >
      {{ hunt?.hintMd || '暂时没有可玩的 bug 样本，先去做几道题积累错误再来。' }}
    </p>

    <template v-else-if="phase !== 'idle'">
      <ol
        class="bug-hunter__code"
        data-testid="bug-hunter-code"
      >
        <li
          v-for="(line, index) in lines"
          :key="index"
        >
          <button
            type="button"
            class="code-line"
            :class="lineClass(index)"
            :data-line="index"
            :disabled="phase === 'done'"
            :aria-pressed="selected.has(index)"
            @click="toggleLine(index)"
          >
            <span class="code-line__no">{{ index + 1 }}</span>
            <code class="code-line__text">{{ line || ' ' }}</code>
          </button>
        </li>
      </ol>

      <div class="bug-hunter__foot">
        <button
          v-if="phase === 'playing'"
          type="button"
          class="bug-hunter__action bug-hunter__action--primary"
          data-testid="bug-hunter-submit"
          :disabled="selected.size === 0"
          @click="finish"
        >
          提交（已选 {{ selected.size }} 行）
        </button>

        <p
          v-else-if="result"
          class="bug-hunter__result"
          :class="{ 'bug-hunter__result--perfect': result.perfect }"
          data-testid="bug-hunter-result"
        >
          <strong>{{ result.perfect ? '完美命中！' : '本局结束' }}</strong>
          命中 {{ result.hits }} · 误报 {{ result.wrong }} · 漏判 {{ result.missed }} ·
          得分 {{ score }}。
          <span class="bug-hunter__teach-tip">
            想真正"出师"？在下方用一句话讲清这行为什么错，再让学弟改对跑真判题。
          </span>
        </p>
      </div>

      <p
        v-if="hunt?.hintMd && phase === 'playing'"
        class="bug-hunter__hint"
      >
        提示：{{ hunt.hintMd }}
      </p>
    </template>
  </section>
</template>

<style scoped>
.bug-hunter {
  display: grid;
  gap: var(--pyt-space-3);
  padding: var(--pyt-space-4);
  border: 2px solid var(--pyt-color-border);
  background: var(--pyt-color-bg-elevated);
  box-shadow: var(--pyt-shadow-card);
}

.bug-hunter__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--pyt-space-3);
  flex-wrap: wrap;
}

.bug-hunter__title {
  margin: 0;
  font-family: var(--pyt-font-pixel);
  color: var(--pyt-color-text-primary);
}

.bug-hunter__sub {
  margin: var(--pyt-space-1) 0 0;
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-muted);
}

.bug-hunter__meta {
  display: flex;
  align-items: center;
  gap: var(--pyt-space-2);
}

.bug-hunter__timer {
  font-family: var(--pyt-font-mono);
  font-weight: 700;
  color: var(--pyt-color-text-hint);
}

.bug-hunter__timer--urgent {
  color: var(--pyt-color-danger);
}

.bug-hunter__score {
  font-family: var(--pyt-font-mono);
  font-weight: 700;
  color: var(--pyt-color-text-accent, var(--pyt-color-primary));
}

.bug-hunter__action {
  border: 2px solid var(--pyt-color-border-strong);
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-text-primary);
  padding: var(--pyt-space-2) var(--pyt-space-3);
  font-family: var(--pyt-font-pixel);
  cursor: pointer;
}

.bug-hunter__action--primary {
  background: var(--pyt-color-primary);
  color: var(--pyt-color-text-on-dark);
}

.bug-hunter__action:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.bug-hunter__empty {
  margin: 0;
  color: var(--pyt-color-text-muted);
}

/* ── 代码行（可点找茬） ─────────────────────────────────── */
.bug-hunter__code {
  margin: 0;
  padding: 0;
  list-style: none;
  border: 2px solid var(--pyt-color-text-hint);
  background: var(--pyt-color-terminal-bg);
  overflow: hidden;
}

.code-line {
  display: flex;
  align-items: stretch;
  gap: var(--pyt-space-2);
  width: 100%;
  border: none;
  border-left: 4px solid transparent;
  background: transparent;
  color: var(--pyt-color-terminal-text);
  font-family: var(--pyt-font-mono);
  text-align: left;
  cursor: pointer;
  padding: 0;
}

.code-line:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.06);
}

.code-line__no {
  flex: 0 0 auto;
  width: 2.4em;
  padding: 4px 8px;
  text-align: right;
  color: var(--pyt-color-text-hint);
  background: rgba(0, 0, 0, 0.25);
  user-select: none;
}

.code-line__text {
  flex: 1 1 auto;
  padding: 4px 8px;
  white-space: pre;
  overflow-x: auto;
}

.code-line--selected {
  border-left-color: var(--pyt-color-warning);
  background: rgba(255, 209, 102, 0.16);
}

.code-line--hit {
  border-left-color: var(--pyt-color-success);
  background: var(--pyt-color-success-bg);
  color: var(--pyt-color-success);
}

.code-line--missed {
  border-left-color: var(--pyt-color-danger);
  background: rgba(230, 57, 70, 0.18);
}

.code-line--wrong {
  border-left-color: var(--pyt-color-danger);
  background: rgba(230, 57, 70, 0.1);
  text-decoration: line-through;
  opacity: 0.8;
}

.bug-hunter__foot {
  display: flex;
  align-items: center;
}

.bug-hunter__result {
  margin: 0;
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-primary);
  line-height: 1.7;
}

.bug-hunter__result--perfect strong {
  color: var(--pyt-color-success);
}

.bug-hunter__teach-tip {
  display: block;
  color: var(--pyt-color-text-muted);
}

.bug-hunter__hint {
  margin: 0;
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-hint);
}
</style>
