<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { BossProblemDto } from '@/api/dto/Boss';

const props = defineProps<{ problem: BossProblemDto }>();
const code = defineModel<string>('code', { required: true });

const BLANK = '___';

const segments = computed(() => {
  const stmt = props.problem.statement ?? '';
  const parts = stmt.split(BLANK);
  return parts;
});

const blankCount = computed(() => Math.max(segments.value.length - 1, 0));
const blanks = ref<string[]>([]);

watch(
  blankCount,
  (n) => {
    blanks.value = Array.from({ length: n }, () => '');
  },
  { immediate: true },
);

watch(
  blanks,
  (vals) => {
    const parts = segments.value;
    let result = '';
    for (let i = 0; i < parts.length; i++) {
      result += parts[i];
      if (i < vals.length) result += vals[i] || BLANK;
    }
    code.value = result;
  },
  { deep: true },
);
</script>

<template>
  <div
    class="fill-blank-card"
    data-testid="fill-blank-card"
  >
    <header class="card-head">
      <span class="card-icon">▭</span>
      <h3>代码填空</h3>
      <span class="blank-count">{{ blankCount }} 处待填</span>
    </header>
    <div class="code-body">
      <template
        v-for="(seg, i) in segments"
        :key="i"
      >
        <span class="code-seg">{{ seg }}</span>
        <input
          v-if="i < blankCount"
          v-model="blanks[i]"
          class="blank-input"
          :placeholder="`填空 ${i + 1}`"
          :data-testid="`blank-${i}`"
          spellcheck="false"
          autocomplete="off"
        >
      </template>
    </div>
    <p class="hint">
      在高亮空位填入正确代码，使程序按预期输出。
    </p>
  </div>
</template>

<style scoped>
.fill-blank-card { display: grid; gap: 12px; }
.card-head { display: flex; align-items: center; gap: 8px; }
.card-icon { font-size: 20px; }
.card-head h3 { margin: 0; font-size: 16px; color: var(--pyt-color-gold-bright); }
.blank-count { margin-left: auto; font-size: 11px; color: var(--pyt-color-info); padding: 2px 8px; background: var(--pyt-color-info-bg); border: 1px solid var(--pyt-color-info); border-radius: 3px; }
.code-body { background: var(--pyt-color-terminal-bg); border: 2px solid var(--pyt-color-border-strong); padding: 12px; font-family: 'JetBrains Mono', monospace; font-size: 13px; line-height: 2; white-space: pre-wrap; color: var(--pyt-color-terminal-text); }
.code-seg { white-space: pre-wrap; }
.blank-input { display: inline-block; width: 120px; margin: 0 4px; padding: 2px 6px; background: var(--pyt-color-shadow); border: 2px dashed var(--pyt-color-gold); border-radius: 3px; color: var(--pyt-color-gold-bright); font-family: inherit; font-size: 13px; text-align: center; }
.blank-input:focus { border-color: var(--pyt-color-success); background: var(--pyt-color-terminal-bg); outline: none; }
.hint { font-size: 12px; color: var(--pyt-color-text-muted); margin: 0; }
</style>
