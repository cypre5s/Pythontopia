<script setup lang="ts">
/**
 * R-13 D1/D2：原 PDF 页渲染器（pdfjs-dist 4.7）。
 *
 * 直接 fetch 后端 `/api/course/documents/{docId}/pdf`，按 pageNo 渲染到 canvas。
 *
 * 设计取舍：
 *   - 父组件用 `:doc-id` `:page-no` 控制；切页 watch 重新调 loadPdfPage（pdfjs 内部
 *     会 destroy 旧 task，避免重复下载）；
 *   - 失败时显示带 i18n hint 的 fallback，让 LibraryPage 可以保留 Markdown 视图作为兜底；
 *   - resize 监听仅观察 wrapper 容器宽度变化，重新按 targetWidthPx 渲染，
 *     与 D3 拖拽 resize 兼容；
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
}

const props = withDefaults(defineProps<Props>(), { pdfBase: '/api/course/documents' });

const canvasEl = ref<HTMLCanvasElement | null>(null);
const wrapperEl = ref<HTMLDivElement | null>(null);
const loading = ref<boolean>(false);
const error = ref<string | null>(null);
const totalPages = ref<number | null>(null);

let currentLoaded: LoadedPdfPage | null = null;
let resizeObserver: ResizeObserver | null = null;
let renderToken = 0;

function buildPdfUrl(docId: number): string {
  return `${props.pdfBase}/${docId}/pdf`;
}

async function renderNow(): Promise<void> {
  const canvas = canvasEl.value;
  const wrapper = wrapperEl.value;
  if (!canvas || !wrapper) return;
  if (!currentLoaded) return;
  const targetWidthPx = Math.max(320, wrapper.clientWidth);
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
    await renderNow();
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
    >
      正在加载课件 PDF 第 {{ pageNo }} 页…
    </p>
    <p
      v-else-if="error"
      class="state state--error"
      data-testid="library-pdf-error"
    >
      PDF 加载失败：{{ error }}
    </p>
    <canvas
      v-show="!loading && !error"
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
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: center;
  width: 100%;
}

.pdf-canvas {
  display: block;
  max-width: 100%;
  border: 2px solid #c9a86b;
  background: #fff;
  box-shadow: 4px 4px 0 #4d3613;
}

.state {
  margin: 0;
  padding: 8px 12px;
  font-size: 13px;
  color: #6e4f1d;
  font-family: var(--pyt-font-sans, sans-serif);
}

.state--loading {
  color: #6e4f1d;
}

.state--error {
  color: #b3402a;
  font-weight: 600;
}

.state--meta {
  font-size: 11px;
  color: #8a6c2c;
}
</style>
