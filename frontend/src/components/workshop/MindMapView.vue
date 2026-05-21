<script setup lang="ts">
import { computed } from 'vue';
import type { MindMapContent } from '@/api/dto/Workshop';

const props = defineProps<{
  content: MindMapContent;
}>();

const valid = computed(() => {
  const header = props.content.mermaid.trim().split(/\r?\n/)[0] ?? '';
  return /^(graph|flowchart)\s+(TD|LR|RL|BT)/.test(header);
});

const relatedEntries = computed(() => Object.entries(props.content.kc_map ?? {}));
</script>

<template>
  <div
    v-if="!valid"
    class="error"
    data-testid="mermaid-error"
  >
    思维导图源文本不符合 Mermaid 语法，已折叠以避免错误渲染：
    <pre>{{ props.content.mermaid }}</pre>
  </div>
  <div
    v-else
    class="mindmap"
  >
    <pre
      class="mermaid-source"
      data-testid="mermaid-source"
    >{{ props.content.mermaid }}</pre>
    <div
      v-if="relatedEntries.length > 0"
      class="kc-map"
    >
      <h4>相关知识点</h4>
      <ul>
        <li
          v-for="[code, related] in relatedEntries"
          :key="code"
        >
          {{ code }} → {{ related.join(', ') || '无' }}
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.error {
  background: #ffe2d4;
  border: 2px dashed #c14a2f;
  border-radius: 6px;
  padding: 12px;
  color: #6e2c1a;
}
.mindmap {
  display: grid;
  gap: 12px;
}
.mermaid-source {
  background: #fffbe9;
  border: 2px solid #c9a86b;
  border-radius: 6px;
  padding: 10px;
  font-family: 'Fira Code', monospace;
  font-size: 13px;
  white-space: pre-wrap;
}
.kc-map {
  background: #fdf3d0;
  border-radius: 6px;
  padding: 10px;
}
.kc-map h4 {
  margin: 0 0 6px;
}
.kc-map ul {
  margin: 0;
  padding-left: 20px;
}
</style>
