<script setup lang="ts">
import type { CodeLabContent } from '@/api/dto/Workshop';

const props = defineProps<{
  content: CodeLabContent;
}>();
</script>

<template>
  <div class="code-lab">
    <section
      v-for="(walkthrough, index) in props.content.walkthroughs"
      :key="index"
      class="walkthrough"
    >
      <header>
        <h3>{{ walkthrough.title }}</h3>
      </header>
      <p class="narrative">
        {{ walkthrough.narrative }}
      </p>
      <pre><code>{{ walkthrough.code }}</code></pre>
      <div class="expected">
        <span>预期输出</span>
        <pre><code>{{ walkthrough.expected_output }}</code></pre>
      </div>
      <div
        v-if="walkthrough.common_mistakes.length > 0"
        class="mistakes"
      >
        <h4>常见错误</h4>
        <ul>
          <li
            v-for="(mistake, mIndex) in walkthrough.common_mistakes"
            :key="mIndex"
          >
            {{ mistake }}
          </li>
        </ul>
      </div>
      <button
        type="button"
        class="primary"
        disabled
      >
        在 Pyodide 中运行（阶段 09 上线）
      </button>
    </section>
  </div>
</template>

<style scoped>
.code-lab {
  display: grid;
  gap: 16px;
}
.walkthrough {
  background: #fffbe9;
  border: 2px solid #c9a86b;
  border-radius: 8px;
  padding: 14px;
}
.narrative {
  line-height: 1.55;
}
.walkthrough pre {
  background: #2e2417;
  color: #f1e5c8;
  padding: 10px;
  border-radius: 6px;
  overflow-x: auto;
  font-size: 13px;
}
.expected {
  display: grid;
  grid-template-columns: max-content 1fr;
  align-items: start;
  gap: 8px;
  margin-top: 8px;
}
.mistakes {
  margin-top: 10px;
  padding: 10px;
  background: #fff2c9;
  border-radius: 6px;
}
.primary {
  margin-top: 12px;
  padding: 6px 10px;
  border: 2px solid #6e4f1d;
  background: #6e4f1d;
  color: #fff7e6;
  border-radius: 4px;
  cursor: not-allowed;
  opacity: 0.7;
}
</style>
