<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, nextTick } from 'vue';
import Reveal from 'reveal.js';
import 'reveal.js/dist/reveal.css';
import type { HandoutContent } from '@/api/dto/Workshop';

const props = defineProps<{
  title: string;
  kcCodes: string[];
  content: HandoutContent;
  groundingStatus: string;
  evidencePageIds: number[];
}>();

const emit = defineEmits<{
  close: [];
}>();

const deckRef = ref<HTMLElement | null>(null);
let deck: Reveal | null = null;
let resizeObserver: ResizeObserver | null = null;

// reveal.js 的 TS 类型未暴露 layout()（实例运行时有该方法）；窄类型转换调用，避免 any。
function relayout(): void {
  (deck as unknown as { layout?: () => void } | null)?.layout?.();
}

onMounted(async () => {
  await nextTick();
  if (!deckRef.value) return;
  deck = new Reveal(deckRef.value, {
    embedded: true,
    hash: false,
    controls: true,
    progress: true,
    keyboard: true,
    slideNumber: 'c/t',
    overview: true,
    width: 960,
    height: 700,
    transition: 'slide',
    center: true,
  });
  await deck.initialize();
  // 弹层/flex 容器在初始化瞬间尺寸可能为 0 → reveal 量错尺寸导致空白或排版错位。
  // 下一帧容器拿到真实尺寸后再 layout 一次，并用 ResizeObserver 跟随后续尺寸变化（PPT 正确显示）。
  requestAnimationFrame(() => relayout());
  if (typeof ResizeObserver === 'function') {
    resizeObserver = new ResizeObserver(() => relayout());
    resizeObserver.observe(deckRef.value);
  }
  deckRef.value.focus();
});

onBeforeUnmount(() => {
  if (resizeObserver) {
    resizeObserver.disconnect();
    resizeObserver = null;
  }
  deck?.destroy();
  deck = null;
});

const groundingLabel = computed(() => {
  switch (props.groundingStatus) {
    case 'PASSED': return '✓ 课件证据充分';
    case 'NO_EVIDENCE': return '基于通识知识';
    default: return '⚠ 证据不足';
  }
});

const groundingClass = computed(() => {
  switch (props.groundingStatus) {
    case 'PASSED': return 'grounded';
    case 'NO_EVIDENCE': return 'no-evidence';
    default: return 'failed';
  }
});

function exportMarpMd(): void {
  const lines = ['---', 'marp: true', 'theme: default', '---', ''];
  lines.push(`# ${props.title}`, '', `KC: ${props.kcCodes.join(', ')}`, '', '---', '');
  for (const section of props.content.sections) {
    lines.push(`## ${section.heading}`, '');
    for (const p of section.paragraphs) lines.push(p, '');
    for (const code of section.code_snippets) {
      lines.push('```python', code, '```', '');
    }
    lines.push('---', '');
  }
  const blob = new Blob([lines.join('\n')], { type: 'text/markdown' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `${props.title}.md`;
  a.click();
  URL.revokeObjectURL(url);
}
</script>

<template>
  <div class="ppt-container">
    <div class="ppt-toolbar">
      <button
        type="button"
        class="toolbar-btn"
        @click="exportMarpMd"
      >
        ⬇ 下载 Marp MD
      </button>
      <span class="toolbar-hint">← → 翻页 · F 全屏 · ESC 退出全屏</span>
      <button
        type="button"
        class="toolbar-btn close-btn"
        @click="emit('close')"
      >
        ✕ 关闭
      </button>
    </div>
    <div
      ref="deckRef"
      class="reveal ppt-reveal"
      tabindex="0"
    >
      <div class="slides">
        <section class="cover-slide">
          <h1>{{ title }}</h1>
          <div class="kc-chips">
            <span
              v-for="kc in kcCodes"
              :key="kc"
              class="kc-chip"
            >{{ kc }}</span>
          </div>
          <div
            v-if="evidencePageIds.length > 0"
            class="evidence-row"
          >
            <span class="evidence-label">引用页码：</span>
            <span
              v-for="pid in evidencePageIds"
              :key="pid"
              class="evidence-chip"
            >p.{{ pid }}</span>
          </div>
        </section>

        <section
          v-for="(section, i) in content.sections"
          :key="i"
        >
          <h2>{{ section.heading }}</h2>
          <p
            v-for="(p, j) in section.paragraphs"
            :key="j"
            class="slide-paragraph"
          >
            {{ p }}
          </p>
          <pre
            v-for="(code, k) in section.code_snippets"
            :key="`c-${k}`"
            class="slide-code"
          ><code>{{ code }}</code></pre>
        </section>

        <section class="closing-slide">
          <h2>课程总结</h2>
          <span :class="['grounding-badge', groundingClass]">{{ groundingLabel }}</span>
          <div class="kc-chips closing-kc">
            <span
              v-for="kc in kcCodes"
              :key="kc"
              class="kc-chip"
            >{{ kc }}</span>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ppt-container {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  background: var(--pyt-color-terminal-bg);
}

.ppt-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: #3a2a14;
  border-bottom: 2px solid var(--pyt-color-text-hint);
  flex-shrink: 0;
  z-index: 10;
}

.toolbar-btn {
  padding: 6px 14px;
  border-radius: 4px;
  border: 2px solid var(--pyt-color-text-hint);
  background: #f9e7bc;
  color: #3a2a14;
  font-family: var(--pyt-font-pixel);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s;
}
.toolbar-btn:hover {
  background: #f1d896;
}
.close-btn {
  background: #c14a2f;
  color: var(--pyt-color-bg-elevated);
  border-color: #8b2e1a;
}
.close-btn:hover {
  background: #a33d26;
}

.toolbar-hint {
  font-size: 12px;
  color: var(--pyt-color-border);
  font-family: var(--pyt-font-pixel);
}

.ppt-reveal {
  flex: 1;
  min-height: 0;
}
</style>

<style>
/* Reveal.js 羊皮纸主题覆盖（不加 scoped 以便穿透 :deep） */
.ppt-reveal {
  --r-background-color: #f9e7bc;
  --r-main-font: 'PythontopiaPixel', 'PingFang SC', sans-serif;
  --r-main-font-size: 28px;
  --r-main-color: #3a2a14;
  --r-heading-font: 'PythontopiaPixel', 'PingFang SC', sans-serif;
  --r-heading-color: var(--pyt-color-text-primary);
  --r-heading-text-transform: none;
  --r-link-color: #4f8a3e;
  --r-link-color-hover: #2b6021;
  --r-selection-background-color: var(--pyt-color-border);
  --r-selection-color: var(--pyt-color-bg-elevated);
}

.ppt-reveal .slides {
  text-align: left;
}

.ppt-reveal .slides section {
  padding: 40px 60px;
}

.ppt-reveal .slides h1 {
  font-size: 2em;
  color: var(--pyt-color-text-primary);
  border-bottom: 3px solid var(--pyt-color-text-hint);
  padding-bottom: 12px;
  margin-bottom: 24px;
  text-align: center;
}

.ppt-reveal .slides h2 {
  font-size: 1.5em;
  color: var(--pyt-color-text-primary);
  border-bottom: 2px dashed var(--pyt-color-border);
  padding-bottom: 8px;
  margin-bottom: 20px;
}

.ppt-reveal .slides .slide-paragraph {
  font-size: 0.85em;
  line-height: 1.6;
  color: #3a2a14;
  margin: 8px 0;
}

.ppt-reveal .slides .slide-code {
  background: var(--pyt-color-terminal-bg);
  color: var(--pyt-color-terminal-text);
  padding: 14px 18px;
  border-radius: 6px;
  border: 2px solid var(--pyt-color-text-hint);
  font-size: 0.7em;
  overflow-x: auto;
  text-align: left;
  margin: 12px 0;
}

.ppt-reveal .slides .slide-code code {
  font-family: 'Fira Code', 'Source Code Pro', monospace;
  white-space: pre;
}

.ppt-reveal .kc-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  margin: 16px 0;
}

.ppt-reveal .kc-chip {
  display: inline-block;
  padding: 4px 12px;
  background: #4f8a3e;
  color: var(--pyt-color-bg-elevated);
  border-radius: 4px;
  font-size: 0.65em;
  border: 2px solid #2b6021;
}

.ppt-reveal .evidence-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  align-items: center;
  margin-top: 20px;
}

.ppt-reveal .evidence-label {
  font-size: 0.6em;
  color: var(--pyt-color-text-hint);
}

.ppt-reveal .evidence-chip {
  display: inline-block;
  padding: 3px 10px;
  background: var(--pyt-color-text-hint);
  color: var(--pyt-color-bg-elevated);
  border-radius: 4px;
  font-size: 0.55em;
}

.ppt-reveal .cover-slide {
  text-align: center;
}

.ppt-reveal .closing-slide {
  text-align: center;
}

.ppt-reveal .closing-kc {
  margin-top: 24px;
}

.ppt-reveal .grounding-badge {
  display: inline-block;
  padding: 6px 18px;
  border-radius: 999px;
  font-size: 0.7em;
  margin-top: 16px;
}
.ppt-reveal .grounding-badge.grounded {
  background: #4f8a3e;
  color: var(--pyt-color-bg-elevated);
}
.ppt-reveal .grounding-badge.no-evidence {
  background: #b39554;
  color: var(--pyt-color-bg-elevated);
}
.ppt-reveal .grounding-badge.failed {
  background: #c14a2f;
  color: var(--pyt-color-bg-elevated);
}

/* 进度条主题 */
.ppt-reveal .progress {
  background: rgba(110, 79, 29, 0.3);
  height: 4px;
}
.ppt-reveal .progress span {
  background: #4f8a3e;
}

/* 控制按钮主题 */
.ppt-reveal .controls button {
  color: var(--pyt-color-text-hint);
}

/* 页码主题 */
.ppt-reveal .slide-number {
  color: var(--pyt-color-text-hint);
  font-family: var(--pyt-font-pixel);
  font-size: 14px;
  background: rgba(249, 231, 188, 0.8);
  padding: 4px 10px;
  border-radius: 4px;
  right: 12px;
  bottom: 12px;
}
</style>
