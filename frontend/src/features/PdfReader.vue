<template>
  <div class="pdf-reader" :aria-busy="loading || rendering">
    <div class="pdf-page-surface" :class="{ 'with-narration': materialId }">
      <h2 class="game-card-title reader-card-title"><GameIcon name="description"/>{{ kind === "presentation" ? "PPT 浏览" : "PDF 浏览" }}</h2>
      <div ref="stage" class="pdf-page-stage">
        <canvas
          v-show="rendered && !error"
          ref="canvas"
          aria-label="PDF课件当前页"
          aria-describedby="pdf-reading-position"
          role="img"
        ></canvas>
      </div>
      <LectureStage v-if="materialId" :material-id="materialId" :page-no="page" :total-pages="count" :page-ready="rendered && !rendering && !loading && !error" @page="emit('page', $event)" @ask="emit('ask')" />
      <div v-if="error" class="pdf-reader-state" role="alert">
        <p>{{ error }}</p>
        <button @click="load">重新加载课件</button>
      </div>
      <p v-else-if="loading" class="pdf-reader-state" role="status">
        正在加载 PDF 课件…
      </p>
    </div>
    <nav class="reader-toolbar pdf-pagination" aria-label="PDF 分页">
      <button
        :disabled="loading || page <= 1"
        aria-label="上一页 PDF"
        @click="turn(-1)"
      >
        <GameIcon name="arrowLeft" /> 上一页
      </button>
      <div id="pdf-reading-position" class="reader-position" aria-live="polite">
        <span class="pdf-document-title" :title="title">{{ title || "PDF 课件" }}</span>
        <span>第 <strong>{{ page }}</strong> / {{ loading ? "…" : count }} 页</span>
        <progress class="reader-position-progress" :value="loading ? 0 : page" :max="count" aria-label="阅读位置" :aria-valuetext="`第 ${page} / ${count} 页`" />
      </div>
      <button
        :disabled="loading || page >= count"
        aria-label="下一页 PDF"
        @click="turn(1)"
      >
        下一页 <GameIcon name="arrowRight" />
      </button>
    </nav>
  </div>
</template>
<script setup lang="ts">
import LectureStage from "./LectureStage.vue";
import GameIcon from "../shared/GameIcon.vue";
import { onMounted, onBeforeUnmount, ref, watch } from "vue";
import { api } from "../shared/api";
import { fitPdfPage } from "../shared/pdfPageLayout";
import type { PDFDocumentLoadingTask, PDFDocumentProxy, RenderTask } from "pdfjs-dist";
import "./pdfReader.css";

const props = defineProps<{ url: string; page: number; title?: string; kind?: string; materialId?: number }>(),
  emit = defineEmits<{ page: [value: number]; ask: [] }>(),
  canvas = ref<HTMLCanvasElement>(),
  stage = ref<HTMLElement>(),
  count = ref(1),
  error = ref(""),
  loading = ref(true),
  rendering = ref(false),
  rendered = ref(false);
let doc: PDFDocumentProxy | undefined,
  documentTask: PDFDocumentLoadingTask | undefined,
  task: RenderTask | undefined,
  controller: AbortController | undefined,
  resizeObserver: ResizeObserver | undefined,
  resizeFrame = 0,
  lastSize = "",
  loadGeneration = 0,
  renderGeneration = 0,
  disposed = false;

function ignoreCancellation(error: unknown) {
  return error instanceof Error && ["AbortError", "RenderingCancelledException"].includes(error.name);
}
async function load() {
  const generation = ++loadGeneration;
  ++renderGeneration;
  controller?.abort();
  controller = new AbortController();
  const signal = controller.signal;
  task?.cancel();
  task = undefined;
  const previous = doc;
  doc = undefined;
  void previous?.destroy().catch(() => undefined);
  void documentTask?.destroy().catch(() => undefined);
  documentTask = undefined;
  loading.value = true;
  rendering.value = false;
  rendered.value = false;
  error.value = "";
  const current = () => !disposed && generation === loadGeneration;
  try {
    const [{ getDocument, GlobalWorkerOptions }, worker, blob] = await Promise.all([
      import("pdfjs-dist"),
      import("pdfjs-dist/build/pdf.worker.min.mjs?url"),
      api.blob(props.url, signal),
    ]);
    if (!current()) return;
    GlobalWorkerOptions.workerSrc = worker.default;
    const data = await blob.arrayBuffer();
    if (!current()) return;
    const pending = getDocument({ data, isEvalSupported: false });
    documentTask = pending;
    const loaded = await pending.promise;
    if (!current()) {
      void loaded.destroy().catch(() => undefined);
      return;
    }
    documentTask = undefined;
    doc = loaded;
    count.value = loaded.numPages;
    const valid = Math.min(count.value, Math.max(1, props.page));
    if (valid !== props.page) emit("page", valid);
    await render();
  } catch (cause) {
    if (current() && !ignoreCancellation(cause))
      error.value = cause instanceof Error ? cause.message : "PDF加载失败";
  } finally {
    if (current()) loading.value = false;
  }
}
async function render() {
  if (!doc || !canvas.value || !stage.value) return;
  const generation = ++renderGeneration,
    document = doc,
    previous = task;
  task = undefined;
  previous?.cancel();
  rendering.value = true;
  rendered.value = false;
  const current = () => !disposed && generation === renderGeneration && document === doc;
  try {
    // PDF.js must finish cancelling before reusing the same canvas.
    await previous?.promise.catch(() => undefined);
    if (!current()) return;
    const page = await document.getPage(Math.min(Math.max(1, props.page), document.numPages));
    if (!current() || !canvas.value || !stage.value) return;
    const natural = page.getViewport({ scale: 1 }),
      fitted = fitPdfPage(natural.width, natural.height, stage.value.clientWidth, stage.value.clientHeight, window.devicePixelRatio);
    // Hidden tabs have no dimensions; their ResizeObserver renders on reveal.
    if (!fitted) return;
    const view = page.getViewport({ scale: fitted.renderScale }),
      context = canvas.value.getContext("2d");
    if (!context) throw new Error("无法显示课件，请重新加载。");
    canvas.value.width = Math.ceil(view.width);
    canvas.value.height = Math.ceil(view.height);
    canvas.value.style.width = `${fitted.width}px`;
    canvas.value.style.height = `${fitted.height}px`;
    error.value = "";
    const pending = page.render({ canvasContext: context, viewport: view });
    task = pending;
    await pending.promise;
    if (current()) rendered.value = true;
  } catch (cause) {
    if (current() && !ignoreCancellation(cause))
      error.value = cause instanceof Error ? cause.message : "课件显示失败";
  } finally {
    if (current()) rendering.value = false;
  }
}
function turn(delta: number) {
  emit("page", Math.min(count.value, Math.max(1, props.page + delta)));
}
watch(() => props.page, render);
watch(() => props.url, load);
onMounted(() => {
  if (typeof ResizeObserver !== "undefined" && stage.value) {
    resizeObserver = new ResizeObserver((entries) => {
      const size = entries[0]?.contentRect,
        key = `${Math.floor(size?.width ?? 0)}:${Math.floor(size?.height ?? 0)}`;
      if (key === lastSize) return;
      lastSize = key;
      cancelAnimationFrame(resizeFrame);
      if (size && size.width > 0 && size.height > 0)
        resizeFrame = requestAnimationFrame(() => void render());
    });
    resizeObserver.observe(stage.value);
  }
  void load();
});
onBeforeUnmount(() => {
  disposed = true;
  ++loadGeneration;
  ++renderGeneration;
  controller?.abort();
  task?.cancel();
  resizeObserver?.disconnect();
  cancelAnimationFrame(resizeFrame);
  void documentTask?.destroy().catch(() => undefined);
  void doc?.destroy().catch(() => undefined);
});
</script>
