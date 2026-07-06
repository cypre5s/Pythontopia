<script setup lang="ts">
/**
 * CoursewareEvidence —— 「课件证据」面板（仿 Alethicode 课件问答的引用卡）。
 *
 * 把答疑命中的课件证据直接以**页面图片**摊给学生看（服务端 PDFBox 渲染 + 缓存的
 * `/api/course/pages/{pageId}/image`），让学生一眼看见「系统到底依据了哪页课件」，
 * 点图回链到图书馆原文核对；并在顶部给一句结合提问/上下文/画像的总览说明（evidenceNote）。
 * 默认只内嵌前 3 页图片，其余页折叠为文字链接，避免气泡过重。证据为空且基于通识知识时给
 * 中性说明，绝不伪造引用。图片加载失败自动降级为文字页链接。
 */
import { computed, ref } from 'vue';
import type { EvidenceCitation } from '@/api/tutor';

const props = withDefaults(
  defineProps<{
    citations: EvidenceCitation[];
    /** 接地状态：PASSED / NO_EVIDENCE / FAILED（决定空态文案）。 */
    groundingStatus?: string;
    /** 整组一句话总览说明（结合提问/上下文/画像；可空，仅 live 答疑带）。 */
    evidenceNote?: string;
  }>(),
  { groundingStatus: '', evidenceNote: '' },
);

const emit = defineEmits<{ (e: 'open-page', pageId: number): void }>();

/** 最多内嵌渲染的页面图片数（其余折叠为文字链接）。 */
const MAX_IMAGES = 3;

const imageCitations = computed<EvidenceCitation[]>(() => props.citations.slice(0, MAX_IMAGES));
const extraCitations = computed<EvidenceCitation[]>(() => props.citations.slice(MAX_IMAGES));

// 图片加载失败的 pageId：降级为文字页链接，绝不显示破图。
const failedIds = ref<Set<number>>(new Set());

function imageUrl(pageId: number): string {
  return `/api/course/pages/${pageId}/image`;
}

function onImgError(pageId: number): void {
  const next = new Set(failedIds.value);
  next.add(pageId);
  failedIds.value = next;
}

function onOpen(pageId: number): void {
  emit('open-page', pageId);
}

/** 页码标签：有真实页号显示「第 N 页」，缺省（0，如历史回放只存 pageId）显示「课件页」。 */
function pageLabel(pageNo: number): string {
  return pageNo > 0 ? `第 ${pageNo} 页` : '课件页';
}
</script>

<template>
  <section
    v-if="props.citations.length > 0"
    class="evidence-panel"
    data-testid="courseware-evidence"
  >
    <header class="evidence-panel__head">
      <span class="evidence-panel__title">📖 课件证据</span>
      <span class="evidence-panel__count">系统命中 {{ props.citations.length }} 处</span>
    </header>

    <!-- 整组总览说明：结合提问/上下文/画像的一句话（仅 live 答疑带）。 -->
    <p
      v-if="props.evidenceNote"
      class="evidence-panel__note"
      data-testid="courseware-evidence-note"
    >
      💡 {{ props.evidenceNote }}
    </p>

    <!-- 前 N 页直接以页面图片内嵌；点图回链图书馆原文。加载失败降级为文字链接。 -->
    <ul class="evidence-panel__images">
      <li
        v-for="c in imageCitations"
        :key="c.pageId"
        class="evidence-image"
      >
        <button
          type="button"
          class="evidence-image__btn"
          :title="`查看${pageLabel(c.pageNo)}原文`"
          @click="onOpen(c.pageId)"
        >
          <img
            v-if="!failedIds.has(c.pageId)"
            class="evidence-image__img"
            :src="imageUrl(c.pageId)"
            :alt="pageLabel(c.pageNo)"
            loading="lazy"
            @error="onImgError(c.pageId)"
          >
          <span
            v-else
            class="evidence-image__fallback"
          >{{ pageLabel(c.pageNo) }}（图暂不可用，点此看原文）</span>
          <span class="evidence-image__cap">{{ pageLabel(c.pageNo) }} · 点击看原文</span>
        </button>
        <p
          v-if="c.snippet"
          class="evidence-image__snippet"
        >
          {{ c.snippet }}
        </p>
      </li>
    </ul>

    <!-- 其余命中页：折叠为文字页链接，保持气泡轻量。 -->
    <details
      v-if="extraCitations.length > 0"
      class="evidence-panel__more"
    >
      <summary class="evidence-panel__more-summary">
        其余 {{ extraCitations.length }} 页
      </summary>
      <ul class="evidence-panel__list">
        <li
          v-for="c in extraCitations"
          :key="c.pageId"
          class="evidence-item"
        >
          <button
            type="button"
            class="evidence-item__page"
            :title="`查看${pageLabel(c.pageNo)}原文`"
            @click="onOpen(c.pageId)"
          >
            {{ pageLabel(c.pageNo) }}
          </button>
          <p
            v-if="c.snippet"
            class="evidence-item__snippet"
          >
            {{ c.snippet }}
          </p>
        </li>
      </ul>
    </details>
  </section>
  <!-- 无课件命中时不再渲染「基于通识知识」脚注：诚实标注已由答案开头与接地徽标承担，避免每答必唠。 -->
</template>

<style scoped>
.evidence-panel {
  margin-top: 8px;
  padding: 8px 10px;
  background: #fff2c9;
  border: 1px dashed var(--pyt-color-text-hint);
  border-radius: var(--pyt-radius-md);
  font-family: inherit;
}

.evidence-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}
.evidence-panel__title {
  font-size: 11px;
  font-weight: 700;
  color: var(--pyt-color-text-primary);
  letter-spacing: 0.5px;
}
.evidence-panel__count {
  font-size: 10px;
  color: var(--pyt-color-text-muted);
  white-space: nowrap;
}

.evidence-panel__note {
  margin: 0 0 8px;
  padding: 6px 8px;
  font-size: 11px;
  line-height: 1.5;
  color: #1b4a6b;
  background: var(--pyt-color-info-bg);
  border: 1px dashed var(--pyt-color-info);
  border-radius: var(--pyt-radius-sm);
}

/* 页面图片网格：自适应列宽，每页一张缩略图，点开回链原文。 */
.evidence-panel__images {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 8px;
}
.evidence-image {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.evidence-image__btn {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 0;
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
  background: #fff;
  cursor: pointer;
  overflow: hidden;
  font: inherit;
}
.evidence-image__btn:hover {
  border-color: var(--pyt-color-info);
  box-shadow: 0 0 0 2px rgba(42, 111, 151, 0.18);
}
.evidence-image__img {
  display: block;
  width: 100%;
  height: auto;
  background: #fff;
  image-rendering: auto;
}
.evidence-image__fallback {
  display: block;
  padding: 14px 8px;
  font-size: 11px;
  line-height: 1.5;
  color: #1b4a6b;
  text-align: center;
  background: var(--pyt-color-info-bg);
}
.evidence-image__cap {
  padding: 3px 6px;
  font-size: 10px;
  font-weight: 700;
  color: #1b4a6b;
  background: var(--pyt-color-info-bg);
  border-top: 1px solid var(--pyt-color-border);
  text-align: center;
}
.evidence-image__snippet {
  margin: 0;
  font-size: 10px;
  line-height: 1.45;
  color: var(--pyt-color-text-muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.evidence-panel__more {
  margin-top: 8px;
}
.evidence-panel__more-summary {
  font-size: 11px;
  font-weight: 700;
  color: var(--pyt-color-text-hint);
  cursor: pointer;
}
.evidence-panel__more[open] .evidence-panel__more-summary {
  margin-bottom: 6px;
}

.evidence-panel__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.evidence-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.evidence-item__page {
  flex: none;
  font-family: monospace, sans-serif;
  font-size: 11px;
  font-weight: 700;
  padding: 2px 7px;
  background: var(--pyt-color-info-bg);
  border: 1px solid var(--pyt-color-info);
  color: #1b4a6b;
  border-radius: 4px;
  cursor: pointer;
  white-space: nowrap;
}
.evidence-item__page:hover {
  background: #a8cfe6;
}
.evidence-item__snippet {
  margin: 0;
  flex: 1;
  min-width: 0;
  font-size: 11px;
  line-height: 1.5;
  color: var(--pyt-color-text-primary);
}
</style>
