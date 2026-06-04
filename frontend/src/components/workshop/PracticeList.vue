<script setup lang="ts">
import { ref } from 'vue';
import { kcDisplayName } from '@/data/kcNames';
import type { PracticeContent } from '@/api/dto/Workshop';

const props = defineProps<{
  content: PracticeContent;
}>();

const expandedHints = ref<Record<number, boolean>>({});

function toggleHints(index: number): void {
  expandedHints.value[index] = !expandedHints.value[index];
}
</script>

<template>
  <ol
    class="practice"
    data-testid="practice-list"
  >
    <li
      v-for="(problem, index) in props.content.problems"
      :key="index"
      class="problem"
    >
      <header>
        <span class="difficulty">{{ problem.difficulty }}</span>
        <h3>{{ problem.title }}</h3>
      </header>
      <p class="statement">
        {{ problem.statement_md }}
      </p>
      <details
        class="hints"
        :open="expandedHints[index] ?? false"
        @toggle="toggleHints(index)"
      >
        <summary>提示（点击展开）</summary>
        <ul>
          <li
            v-for="(hint, hIndex) in problem.hints"
            :key="hIndex"
          >
            {{ hint }}
          </li>
        </ul>
      </details>
      <section
        class="samples"
        data-testid="sample-io"
      >
        <h4>样例输入 / 输出</h4>
        <div
          v-for="(io, ioIndex) in problem.sample_io"
          :key="ioIndex"
          class="io"
        >
          <pre><code>{{ io.input }}</code></pre>
          <pre><code>{{ io.output }}</code></pre>
        </div>
      </section>
      <p class="kc">
        关联知识点: {{ problem.kc_codes.map(kcDisplayName).join('、') || '未标注' }}
      </p>
    </li>
  </ol>
</template>

<style scoped>
.practice {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 16px;
}
.problem {
  background: #fffbe9;
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  padding: 14px;
}
.problem header {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.problem h3 {
  margin: 0;
  font-size: 16px;
}
.difficulty {
  background: var(--pyt-color-text-hint);
  color: var(--pyt-color-bg-elevated);
  border-radius: 999px;
  padding: 1px 8px;
  font-size: 12px;
}
.statement {
  margin: 8px 0;
  line-height: 1.55;
}
.hints summary {
  cursor: pointer;
  color: var(--pyt-color-text-hint);
  font-weight: 600;
}
.hints ul {
  margin-top: 6px;
  padding-left: 18px;
}
.samples h4 {
  margin: 12px 0 6px;
}
.io {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-bottom: 8px;
}
.io pre {
  background: var(--pyt-color-terminal-bg);
  color: var(--pyt-color-terminal-text);
  margin: 0;
  padding: 8px;
  border-radius: 6px;
  font-size: 13px;
  overflow-x: auto;
}
.kc {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--pyt-color-text-hint);
}
</style>
