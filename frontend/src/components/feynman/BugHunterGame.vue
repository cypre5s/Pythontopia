<script setup lang="ts">
/**
 * 错误猎人：学弟写出一段含 bug 的代码，玩家限时点出错误行；命中/误报/漏判即时判分。
 * bug 行号由后端（buggy/fixed diff）给出，前端只负责交互与计分，不持有"正确答案代码"。
 */
import { computed, onUnmounted, ref } from 'vue';
import { useFeynmanStore } from '@/stores/feynman';
import { useToastStore } from '@stores/toast';

const ROUND_SECONDS = 30;
const HIT_SCORE = 10;
const WRONG_PENALTY = 4;
const COMBO_BONUS = 5;
const BEST_KEY = 'pyt:bug-hunter-best';

const store = useFeynmanStore();
const toast = useToastStore();

type Phase = 'idle' | 'playing' | 'done';
const phase = ref<Phase>('idle');
const score = ref(0);
const streak = ref(0);
const bestScore = ref(readBest());
const selected = ref<Set<number>>(new Set());
const result = ref<{
  hits: number;
  wrong: number;
  missed: number;
  perfect: boolean;
  comboBonus: number;
} | null>(null);
// 计时用「开始时间戳 + 单次 setTimeout」而非按秒 setInterval：避免每秒 reactive 重渲染
// 把可点代码行的 DOM 抖掉（点击发涩）；倒计时改 CSS 进度条动画呈现，剩余秒数在结算时回算。
let timerId: number | undefined;
let roundStartedAt = 0;

function readBest(): number {
  try {
    const raw = window.localStorage.getItem(BEST_KEY);
    const value = raw ? Number(raw) : 0;
    return Number.isFinite(value) ? value : 0;
  } catch {
    return 0;
  }
}

function persistBest(value: number): void {
  try {
    window.localStorage.setItem(BEST_KEY, String(value));
  } catch {
    /* 隐私模式禁用 localStorage 时忽略 */
  }
}

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
    window.clearTimeout(timerId);
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
  phase.value = 'playing';
  clearTimer();
  roundStartedAt = Date.now();
  timerId = window.setTimeout(finish, ROUND_SECONDS * 1000);
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
  // 连胜：连续完美命中累计、给逐级加成；非完美清零。
  streak.value = perfect ? streak.value + 1 : 0;
  const comboBonus = perfect ? (streak.value - 1) * COMBO_BONUS : 0;
  // 命中得分、误报扣分；全对则按剩余秒数给时间奖励 + 连胜加成（剩余秒数由开始时间戳回算）。
  const remainingSeconds = Math.max(
    0,
    Math.round(ROUND_SECONDS - (Date.now() - roundStartedAt) / 1000),
  );
  const timeBonus = perfect ? remainingSeconds : 0;
  score.value = Math.max(0, hits * HIT_SCORE - wrong * WRONG_PENALTY + timeBonus + comboBonus);
  result.value = { hits, wrong, missed, perfect, comboBonus };
  if (score.value > bestScore.value) {
    bestScore.value = score.value;
    persistBest(bestScore.value);
  }
  if (perfect) {
    toast.show(`完美命中！${hunt.value?.hintMd ?? ''}`.trim(), 'success');
  } else {
    toast.show('看看高亮的正确 bug 行，再讲一遍就真懂了', 'info');
  }
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
          v-if="streak > 1"
          class="bug-hunter__streak"
          data-testid="bug-hunter-streak"
        >
          连胜 ×{{ streak }}
        </span>
        <span
          v-if="bestScore > 0"
          class="bug-hunter__best"
          data-testid="bug-hunter-best"
        >
          最高 {{ bestScore }}
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

    <div
      v-if="phase === 'playing'"
      class="bug-hunter__timebar"
      data-testid="bug-hunter-timer"
      aria-hidden="true"
    >
      <span
        class="bug-hunter__timebar-fill"
        :style="{ animationDuration: `${ROUND_SECONDS}s` }"
      />
    </div>

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
          得分 {{ score }}<span v-if="result.comboBonus > 0">（含连胜 +{{ result.comboBonus }}）</span>。
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

    <div
      v-if="phase === 'idle'"
      class="bug-hunter__idle"
      data-testid="bug-hunter-idle"
    >
      <p>
        点上方「开始错误猎人」，学弟会写出一段含 bug 的代码；<br>
        限时揪出错误行——命中得分、误报扣分、漏判提醒。
      </p>
    </div>
  </section>
</template>

<style scoped>
.bug-hunter {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-3);
  height: 100%;
  min-height: 0;
  padding: var(--pyt-space-4);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg, 8px);
  background: var(--pyt-color-bg-elevated);
  box-shadow: var(--pyt-shadow-card);
}

/* 空闲态占位：把「按开始」提示居中铺满整块，避免大片空白。 */
.bug-hunter__idle {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0;
  padding: var(--pyt-space-4);
  text-align: center;
  color: var(--pyt-color-text-muted);
  line-height: 1.8;
}

.bug-hunter__idle p {
  margin: 0;
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
  margin: 2px 0 0;
  font-size: 11px;
  line-height: 1.4;
  color: var(--pyt-color-text-muted);
}

.bug-hunter__meta {
  display: flex;
  align-items: center;
  gap: var(--pyt-space-2);
}

/* 倒计时进度条：纯 CSS 动画，不触发每秒 reactive 重渲染。 */
.bug-hunter__timebar {
  height: 6px;
  border-radius: 999px;
  background: var(--pyt-color-bg-sunken);
  overflow: hidden;
}

.bug-hunter__timebar-fill {
  display: block;
  height: 100%;
  width: 100%;
  background: var(--pyt-color-primary);
  animation-name: bug-hunter-countdown;
  animation-timing-function: linear;
  animation-fill-mode: forwards;
}

@keyframes bug-hunter-countdown {
  0% {
    width: 100%;
    background: var(--pyt-color-primary);
  }
  80% {
    background: var(--pyt-color-primary);
  }
  100% {
    width: 0%;
    background: var(--pyt-color-danger);
  }
}

@media (prefers-reduced-motion: reduce) {
  .bug-hunter__timebar-fill {
    animation: none;
    width: 100%;
  }
}

.bug-hunter__score {
  font-family: var(--pyt-font-mono);
  font-weight: 700;
  color: var(--pyt-color-text-accent, var(--pyt-color-primary));
}

.bug-hunter__streak {
  font-family: var(--pyt-font-pixel);
  font-size: var(--pyt-font-size-sm);
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--pyt-color-warning-bg);
  color: var(--pyt-color-text-hint);
  border: 1px solid var(--pyt-color-warning);
}

.bug-hunter__best {
  font-family: var(--pyt-font-mono);
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-muted);
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
  flex: 1;
  min-height: 96px;
  margin: 0;
  padding: 0;
  list-style: none;
  border: 2px solid var(--pyt-color-text-hint);
  border-radius: var(--pyt-radius-sm, 4px);
  background: var(--pyt-color-terminal-bg);
  overflow-y: auto;
  font-size: var(--pyt-font-size-sm);
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
