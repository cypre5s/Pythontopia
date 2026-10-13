<script setup lang="ts">
/**
 * 指南截图：URL 为冻结契约，截图文件由集成阶段统一生成落盘。
 * 开发期文件缺失（onerror）时降级为文字占位说明——绝不使用假截图。
 * 点击截图可放大查看（lightbox），ESC / 点击任意处关闭。
 */
import { nextTick, ref } from 'vue';

defineProps<{
  src: string;
  alt: string;
  caption: string;
}>();

const failed = ref(false);
const zoomed = ref(false);
const lightboxEl = ref<HTMLElement | null>(null);

async function openZoom(): Promise<void> {
  if (failed.value) {
    return;
  }
  zoomed.value = true;
  await nextTick();
  lightboxEl.value?.focus();
}

function closeZoom(): void {
  zoomed.value = false;
}
</script>

<template>
  <figure
    class="guide-screenshot"
    data-testid="guide-screenshot"
  >
    <div class="guide-screenshot__frame">
      <button
        v-if="!failed"
        type="button"
        class="guide-screenshot__zoom-btn"
        :aria-label="`放大查看：${alt}`"
        data-testid="guide-screenshot-zoom"
        @click="openZoom"
      >
        <img
          :src="src"
          :alt="alt"
          loading="lazy"
          class="guide-screenshot__img"
          @error="failed = true"
        >
      </button>
      <div
        v-else
        class="guide-screenshot__fallback"
        data-testid="guide-screenshot-fallback"
        role="img"
        :aria-label="alt"
      >
        <span class="guide-screenshot__badge pyt-pixel-text">截图准备中</span>
        <p class="guide-screenshot__fallback-text">
          {{ alt }}
        </p>
        <p class="guide-screenshot__fallback-hint">
          截图将在集成阶段统一生成；当前以文字说明代替，不影响阅读。
        </p>
      </div>
    </div>
    <figcaption class="guide-screenshot__caption">
      {{ caption }}
    </figcaption>

    <Teleport to="body">
      <div
        v-if="zoomed"
        ref="lightboxEl"
        class="guide-screenshot__lightbox"
        role="dialog"
        aria-modal="true"
        :aria-label="`放大查看：${alt}`"
        data-testid="guide-screenshot-lightbox"
        tabindex="-1"
        @click="closeZoom"
        @keydown.escape.stop="closeZoom"
      >
        <img
          :src="src"
          :alt="alt"
          class="guide-screenshot__lightbox-img"
        >
        <p class="guide-screenshot__lightbox-caption">
          {{ caption }}（ESC 或点击任意处关闭）
        </p>
        <button
          type="button"
          class="guide-screenshot__lightbox-close"
          aria-label="关闭放大图"
          @click.stop="closeZoom"
        >
          ×
        </button>
      </div>
    </Teleport>
  </figure>
</template>

<style scoped>
.guide-screenshot {
  margin: var(--pyt-space-5) 0;
}

/* 固定 3:2 取景框：截图到位前后版面都不跳动 */
.guide-screenshot__frame {
  position: relative;
  aspect-ratio: 3 / 2;
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-sm);
  background: var(--pyt-color-bg-sunken);
  box-shadow: 4px 4px 0 var(--pyt-color-border-soft);
  overflow: hidden;
}

/* 像素装饰角：左上 / 右下各一枚金色小方块 */
.guide-screenshot__frame::before,
.guide-screenshot__frame::after {
  content: '';
  position: absolute;
  width: 8px;
  height: 8px;
  background: var(--pyt-color-gold);
  z-index: 1;
}

.guide-screenshot__frame::before {
  top: 0;
  left: 0;
}

.guide-screenshot__frame::after {
  right: 0;
  bottom: 0;
}

.guide-screenshot__zoom-btn {
  display: block;
  width: 100%;
  height: 100%;
  padding: 0;
  border: 0;
  background: none;
  cursor: zoom-in;
}

.guide-screenshot__zoom-btn:focus-visible {
  outline: 3px solid var(--pyt-color-gold);
  outline-offset: -3px;
}

.guide-screenshot__img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
  image-rendering: pixelated;
}

.guide-screenshot__lightbox {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--pyt-space-3);
  padding: var(--pyt-space-5);
  background: rgba(20, 14, 4, 0.88);
  cursor: zoom-out;
}

.guide-screenshot__lightbox-img {
  max-width: min(96vw, 1600px);
  max-height: 84vh;
  object-fit: contain;
  border: 3px solid var(--pyt-color-gold);
  background: var(--pyt-color-bg-sunken);
}

.guide-screenshot__lightbox-caption {
  margin: 0;
  font-size: 14px;
  color: #f6ecd4;
  text-align: center;
}

.guide-screenshot__lightbox-close {
  position: absolute;
  top: 16px;
  right: 20px;
  width: 40px;
  height: 40px;
  font-size: 22px;
  line-height: 1;
  color: #f6ecd4;
  background: rgba(0, 0, 0, 0.5);
  border: 2px solid var(--pyt-color-gold);
  border-radius: var(--pyt-radius-sm);
  cursor: pointer;
}

.guide-screenshot__fallback {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--pyt-space-2);
  height: 100%;
  padding: var(--pyt-space-5);
  text-align: center;
  /* 细网格纸纹理：暗示"这里将出现一张截图" */
  background-image:
    repeating-linear-gradient(0deg, transparent 0 23px, var(--pyt-color-border-soft) 23px 24px),
    repeating-linear-gradient(90deg, transparent 0 23px, var(--pyt-color-border-soft) 23px 24px);
}

.guide-screenshot__badge {
  font-size: 12px;
  color: var(--pyt-color-primary-dark);
  background: var(--pyt-color-gold);
  border: 2px solid var(--pyt-color-border-strong);
  padding: 2px 10px;
}

.guide-screenshot__fallback-text {
  margin: 0;
  max-width: 36em;
  font-size: 15px;
  line-height: 1.7;
  color: var(--pyt-color-text-primary);
}

.guide-screenshot__fallback-hint {
  margin: 0;
  font-size: 13px;
  color: var(--pyt-color-text-muted);
}

.guide-screenshot__caption {
  margin-top: var(--pyt-space-2);
  font-size: 14px;
  line-height: 1.6;
  color: var(--pyt-color-text-muted);
}
</style>
