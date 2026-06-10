<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { BossProblemDto } from '@/api/dto/Boss';

const props = defineProps<{ problem: BossProblemDto }>();
const code = defineModel<string>('code', { required: true });

const rounds = computed(() => {
  const stmt = props.problem.statement ?? '';
  const parts = stmt.split(/---+/).map((s) => s.trim()).filter(Boolean);
  return parts.length >= 2 ? parts : [stmt];
});

const answers = ref<string[]>([]);
const currentRound = ref(0);

watch(
  rounds,
  (r) => {
    answers.value = Array.from({ length: r.length }, () => '');
    currentRound.value = 0;
  },
  { immediate: true },
);

watch(
  answers,
  (vals) => {
    code.value = vals.map((a, i) => `# --- Round ${i + 1} ---\n${a}`).join('\n\n');
  },
  { deep: true },
);

function nextRound(): void {
  if (currentRound.value < rounds.value.length - 1) {
    currentRound.value += 1;
  }
}

function prevRound(): void {
  if (currentRound.value > 0) {
    currentRound.value -= 1;
  }
}
</script>

<template>
  <div
    class="multi-round-card"
    data-testid="multi-round-card"
  >
    <header class="card-head">
      <span class="card-icon">◉◉◉</span>
      <h3>{{ rounds.length }} 题组合</h3>
      <div class="round-dots">
        <button
          v-for="(_, i) in rounds"
          :key="i"
          type="button"
          :class="['round-dot', { active: i === currentRound, done: (answers[i] ?? '').trim().length > 0 }]"
          @click="currentRound = i"
        >
          {{ i + 1 }}
        </button>
      </div>
    </header>

    <div class="round-nav">
      <button
        type="button"
        :disabled="currentRound === 0"
        @click="prevRound"
      >
        ← 上一题
      </button>
      <span>第 {{ currentRound + 1 }} / {{ rounds.length }} 题</span>
      <button
        type="button"
        :disabled="currentRound === rounds.length - 1"
        @click="nextRound"
      >
        下一题 →
      </button>
    </div>

    <pre class="question">{{ rounds[currentRound] }}</pre>

    <textarea
      :value="answers[currentRound] ?? ''"
      :data-testid="`multi-round-answer-${currentRound}`"
      rows="8"
      placeholder="# 本轮答案"
      spellcheck="false"
      class="round-editor"
      @input="answers[currentRound] = ($event.target as HTMLTextAreaElement).value"
    />
  </div>
</template>

<style scoped>
.multi-round-card { display: grid; gap: 12px; }
.card-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.card-icon { font-size: 18px; color: var(--pyt-color-gold); }
.card-head h3 { margin: 0; font-size: 16px; color: var(--pyt-color-gold-bright); }
.round-dots { display: flex; gap: 4px; margin-left: auto; }
.round-dot { width: 26px; height: 26px; border-radius: 50%; border: 2px solid #4a90e2; background: rgba(74,144,226,0.1); color: #93c5fd; font-size: 12px; cursor: pointer; display: flex; align-items: center; justify-content: center; }
.round-dot.active { background: var(--pyt-color-gold); color: #2a1a08; border-color: var(--pyt-color-text-hint); }
.round-dot.done { background: #6bbf59; color: #fff; border-color: #2d5b1b; }
.round-nav { display: flex; align-items: center; justify-content: space-between; font-size: 12px; color: #93c5fd; }
.round-nav button { font-family: inherit; font-size: 12px; padding: 4px 10px; background: #1e3a8a; color: var(--pyt-color-bg-elevated); border: 1px solid #4a90e2; border-radius: 3px; cursor: pointer; }
.round-nav button:disabled { opacity: 0.4; cursor: not-allowed; }
.question { margin: 0; padding: 10px; background: #0a1428; border: 2px solid #4a90e2; white-space: pre-wrap; font-size: 13px; line-height: 1.7; color: #e5e7eb; }
.round-editor { font-family: 'JetBrains Mono', monospace; font-size: 13px; background: #0a1428; color: #f0f9ff; border: 2px solid #4a90e2; padding: 8px; resize: vertical; min-height: 120px; }
</style>
