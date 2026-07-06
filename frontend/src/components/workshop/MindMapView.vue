<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import type { MindMapContent } from '@/api/dto/Workshop';

const props = defineProps<{
  content: MindMapContent;
}>();

const valid = computed(() => {
  const header = props.content.mermaid.trim().split(/\r?\n/)[0] ?? '';
  return /^(graph|flowchart)\s+(TD|LR|RL|BT)/.test(header);
});

const relatedEntries = computed(() => Object.entries(props.content.kc_map ?? {}));

const renderContainer = ref<HTMLDivElement | null>(null);
const renderError = ref<string | null>(null);
const showSource = ref(false);

async function renderMermaid(): Promise<void> {
  if (!valid.value || !renderContainer.value) return;
  renderError.value = null;
  try {
    const mermaid = (await import('mermaid')).default;
    mermaid.initialize({
      startOnLoad: false,
      theme: 'base',
      themeVariables: {
        primaryColor: '#fff7e6',
        primaryBorderColor: '#c9a86b',
        primaryTextColor: '#2b1c0b',
        lineColor: '#6e4f1d',
        secondaryColor: '#fdf3d0',
        tertiaryColor: '#f4e4c1',
      },
      fontFamily: '"PingFang SC", "Noto Sans CJK SC", monospace',
    });
    const id = `mermaid-${Date.now()}`;
    const { svg } = await mermaid.render(id, props.content.mermaid);
    renderContainer.value.innerHTML = svg;
  } catch (e: unknown) {
    renderError.value = e instanceof Error ? e.message : '渲染失败';
  }
}

onMounted(async () => {
  await nextTick();
  await renderMermaid();
});

watch(() => props.content.mermaid, async () => {
  await nextTick();
  await renderMermaid();
});
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
    <div
      ref="renderContainer"
      class="mermaid-render"
      data-testid="mermaid-render"
    />
    <p
      v-if="renderError"
      class="render-fallback"
    >
      Mermaid 渲染失败：{{ renderError }}
    </p>
    <div class="source-toggle">
      <button
        type="button"
        @click="showSource = !showSource"
      >
        {{ showSource ? '隐藏源码' : '查看 Mermaid 源码' }}
      </button>
    </div>
    <pre
      v-show="showSource || renderError"
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
.mermaid-render {
  background: #fffbe9;
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  padding: 16px;
  overflow-x: auto;
  min-height: 80px;
  display: flex;
  justify-content: center;
}
.mermaid-render :deep(svg) {
  max-width: 100%;
  height: auto;
}
.render-fallback {
  margin: 0;
  padding: 8px 12px;
  background: #ffe8d1;
  border-radius: 6px;
  color: #8b4513;
  font-size: 13px;
}
.source-toggle {
  display: flex;
  justify-content: flex-end;
}
.source-toggle button {
  background: none;
  border: 1px solid var(--pyt-color-border);
  border-radius: 4px;
  padding: 4px 10px;
  font-size: 12px;
  color: var(--pyt-color-text-hint);
  cursor: pointer;
}
.source-toggle button:hover {
  background: var(--pyt-color-bg-soft);
}
.mermaid-source {
  background: #fffbe9;
  border: 2px solid var(--pyt-color-border);
  border-radius: 6px;
  padding: 10px;
  font-family: 'Consolas', 'JetBrains Mono', 'Cascadia Code', Menlo, 'Courier New', monospace;
  font-size: 13px;
  white-space: pre-wrap;
}
.kc-map {
  background: var(--pyt-color-bg-soft);
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
