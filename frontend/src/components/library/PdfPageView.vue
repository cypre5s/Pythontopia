<script setup lang="ts">
/**
 * 原 PDF 页渲染器（pdfjs-dist 4.x）。
 *
 * 直接 fetch 后端 `/api/course/documents/{docId}/pdf`，按 pageNo 渲染到 canvas。
 *
 * 设计取舍：
 *   - 父组件用 `:doc-id` `:page-no` 控制；切页 watch 重新调 loadPdfPage（pdfjs 内部
 *     会 destroy 旧 task，避免重复下载）；
 *   - 失败时显示带 i18n hint 的 fallback，让 LibraryPage 可以保留 Markdown 视图作为兜底；
 *   - resize 监听仅观察 wrapper 容器宽度变化，重新按 targetWidthPx 渲染，
 *     与拖拽 resize 兼容；
 *   - 不引入额外 zoom/scroll：让父容器 overflow:auto 控制大页 PDF 滚动；
 *   - 业务层不直接 import pdfjs-dist —— 透过 `@/lib/pdfjsLoader` 适配。
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import {
  loadPdfPage,
  renderPdfPageToCanvas,
  type LoadedPdfPage,
} from '@/lib/pdfjsLoader';

interface Props {
  /** course_document.id，例如 186~192。 */
  docId: number;
  /** 1-based 页号。 */
  pageNo: number;
  /** 自定义 PDF base 路径（默认 `/api/course/documents`，便于测试注入）。 */
  pdfBase?: string;
  /**
   * 适配模式：'width'（默认，按容器宽撑满，高度溢出由父级滚动）；
   * 'contain'（同时按容器宽高自适应，整页恰好放进容器、不产生滚动——
   * 主线 PPT 精读单屏布局用）。'contain' 要求父级给本组件确定高度。
   */
  fit?: 'width' | 'contain';
}

const props = withDefaults(defineProps<Props>(), {
  pdfBase: '/api/course/documents',
  fit: 'width',
});

const canvasEl = ref<HTMLCanvasElement | null>(null);
const wrapperEl = ref<HTMLDivElement | null>(null);
const loading = ref<boolean>(false);
const error = ref<string | null>(null);
const totalPages = ref<number | null>(null);
/**
 * 是否已成功画过至少一页。翻页加载期间 canvas 保留上一页画面（不再 display:none
 * 塌缩 → 触发 ResizeObserver → 与新页渲染竞态清空画布），加载提示浮在画布上方。
 */
const hasRendered = ref<boolean>(false);

let currentLoaded: LoadedPdfPage | null = null;
let resizeObserver: ResizeObserver | null = null;
let renderToken = 0;

function buildPdfUrl(docId: number): string {
  return `${props.pdfBase}/${docId}/pdf`;
}

/** 期望渲染宽度：'width' 撑满容器宽；'contain' 再按容器高反推宽，整页可见。 */
function computeTargetWidthPx(wrapper: HTMLElement): number {
  const widthFit = Math.max(240, wrapper.clientWidth);
  if (props.fit !== 'contain' || !currentLoaded) return widthFit;
  const base = currentLoaded.page.getViewport({ scale: 1 });
  const availHeight = wrapper.clientHeight - 8;
  if (availHeight < 80 || base.height <= 0) return widthFit;
  const heightFit = Math.floor(availHeight * (base.width / base.height));
  return Math.max(240, Math.min(widthFit, heightFit));
}

async function renderNow(): Promise<void> {
  const canvas = canvasEl.value;
  const wrapper = wrapperEl.value;
  if (!canvas || !wrapper) return;
  if (!currentLoaded) return;
  // 翻页加载中由 loadAndRender 收尾统一渲染；此处跳过，避免 ResizeObserver
  // 用上一页（可能已 destroy）的 page 与新页渲染竞态、互相 cancel 清空画布。
  if (loading.value) return;
  const targetWidthPx = computeTargetWidthPx(wrapper);
  try {
    await renderPdfPageToCanvas({
      page: currentLoaded.page,
      canvas,
      targetWidthPx,
    });
  } catch (renderErr) {
    error.value = renderErrToString(renderErr);
  }
}

async function loadAndRender(docId: number, pageNo: number): Promise<void> {
  const token = ++renderToken;
  loading.value = true;
  error.value = null;
  try {
    const loaded = await loadPdfPage({ url: buildPdfUrl(docId), pageNo });
    if (token !== renderToken) {
      // 期间又触发了新 load，丢弃旧结果避免 race。
      return;
    }
    currentLoaded = loaded;
    totalPages.value = loaded.pdf.numPages;
    // 先解除 loading 再渲染：renderNow 在 loading 期间直接 return（防 resize 竞态）。
    loading.value = false;
    await renderNow();
    hasRendered.value = true;
  } catch (err) {
    if (token !== renderToken) return;
    error.value = renderErrToString(err);
    currentLoaded = null;
  } finally {
    if (token === renderToken) {
      loading.value = false;
    }
  }
}

function renderErrToString(raw: unknown): string {
  if (raw instanceof Error) return raw.message;
  return String(raw);
}

onMounted(() => {
  void loadAndRender(props.docId, props.pageNo);
  const wrapper = wrapperEl.value;
  if (wrapper && typeof ResizeObserver === 'function') {
    resizeObserver = new ResizeObserver(() => {
      if (currentLoaded) {
        void renderNow();
      }
    });
    resizeObserver.observe(wrapper);
  }
});

watch(
  () => [props.docId, props.pageNo] as const,
  (next, prev) => {
    const [docId, pageNo] = next;
    const [oldDoc, oldPage] = prev ?? [docId, pageNo];
    if (docId === oldDoc && pageNo === oldPage) return;
    void loadAndRender(docId, pageNo);
  },
);

onBeforeUnmount(() => {
  renderToken += 1;
  if (resizeObserver) {
    resizeObserver.disconnect();
    resizeObserver = null;
  }
  currentLoaded = null;
});
</script>

<template>
  <div
    ref="wrapperEl"
    class="pdf-page-view"
    :data-loading="loading ? 'true' : 'false'"
    data-testid="library-pdf-page"
  >
    <p
      v-if="loading"
      class="state state--loading"
      :class="{ 'state--floating': hasRendered }"
    >
      正在加载课件 PDF 第 {{ pageNo }} 页…
    </p>
    <p
      v-if="!loading && error"
      class="state state--error"
      data-testid="library-pdf-error"
    >
      PDF 加载失败：{{ error }}
    </p>
    <!-- 翻页加载期间保留上一页画面（仅首次加载/出错时隐藏） -->
    <canvas
      v-show="!error && (hasRendered || !loading)"
      ref="canvasEl"
      class="pdf-canvas"
      data-testid="library-pdf-canvas"
    />
    <p
      v-if="totalPages !== null && !error"
      class="state state--meta"
    >
      第 {{ pageNo }} / {{ totalPages }} 页 · 原版 PDF
    </p>
  </div>
</template>

<style scoped>
.pdf-page-view {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: center;
  width: 100%;
}

.pdf-canvas {
  display: block;
  max-width: 100%;
  border: 2px solid var(--pyt-color-border);
  background: #fff;
  box-shadow: 4px 4px 0 var(--pyt-color-text-primary);
}

.state {
  margin: 0;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--pyt-color-text-hint);
  font-family: var(--pyt-font-sans, sans-serif);
}

.state--loading {
  color: var(--pyt-color-text-hint);
}

/* 翻页期间浮在画布上方的小提示条（不参与布局，避免画布尺寸抖动） */
.state--floating {
  position: absolute;
  top: 8px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1;
  padding: 2px 10px;
  font-size: 11px;
  background: rgba(255, 247, 230, 0.92);
  border: 1px solid var(--pyt-color-border);
  border-radius: 999px;
  white-space: nowrap;
}

.state--error {
  color: #b3402a;
  font-weight: 600;
}

.state--meta {
  font-size: 11px;
  color: var(--pyt-color-text-muted);
}
</style>
