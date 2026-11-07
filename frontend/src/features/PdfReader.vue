<template>
  <div class="pdf-reader" :aria-busy="loading">
    <nav class="reader-toolbar" aria-label="PDF 分页">
      <button
        :disabled="loading || page <= 1"
        aria-label="上一页 PDF"
        @click="turn(-1)"
      >
        <GameIcon name="arrowLeft"/> 上一页</button
      ><span
        id="pdf-reading-position"
        class="reader-position"
        aria-live="polite"
        >第 <strong>{{ page }}</strong> / {{ loading ? "…" : count }} 页<span
          >PDF 课件</span
        ></span
      ><button
        :disabled="loading || page >= count"
        aria-label="下一页 PDF"
        @click="turn(1)"
      >
        下一页 <GameIcon name="arrowRight"/>
      </button>
    </nav>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="loading" class="learning-empty" role="status">
      正在加载 PDF 课件…
    </p>
    <div class="pdf-page-surface">
      <canvas
        ref="canvas"
        aria-label="PDF课件当前页"
        aria-describedby="pdf-reading-position"
        role="img"
      ></canvas>
    </div>
    <p class="pdf-page-help">需要复制内容时，可展开下方当前页的提取文本。</p>
  </div>
</template>
<script setup lang="ts">
import GameIcon from "../shared/GameIcon.vue";
import { onMounted, onBeforeUnmount, ref, watch } from "vue";
import { api } from "../shared/api";
import type { PDFDocumentProxy, RenderTask } from "pdfjs-dist";
import "./learningUi.css";
const props = defineProps<{ url: string; page: number }>(),
  emit = defineEmits<{ page: [value: number] }>(),
  canvas = ref<HTMLCanvasElement>(),
  count = ref(1),
  error = ref(""),
  loading = ref(true);
let doc: PDFDocumentProxy | undefined,
  task: RenderTask | undefined,
  resizeObserver: ResizeObserver | undefined,
  resizeFrame = 0,
  lastWidth = 0,
  renderGeneration = 0;
const controller = new AbortController();
async function load() {
  loading.value = true;
  error.value = "";
  try {
    task?.cancel();
    await doc?.destroy();
    const [{ getDocument, GlobalWorkerOptions }, worker] = await Promise.all([
      import("pdfjs-dist"),
      import("pdfjs-dist/build/pdf.worker.min.mjs?url"),
    ]);
    GlobalWorkerOptions.workerSrc = worker.default;
    const blob = await api.blob(props.url, controller.signal);
    doc = await getDocument({
      data: await blob.arrayBuffer(),
      isEvalSupported: false,
    }).promise;
    count.value = doc.numPages;
    const valid = Math.min(count.value, Math.max(1, props.page));
    if (valid !== props.page) emit("page", valid);
    await render();
  } catch (e) {
    if (!(e instanceof Error && e.name === "AbortError"))
      error.value = e instanceof Error ? e.message : "PDF加载失败";
  } finally {
    loading.value = false;
  }
}
async function render() {
  if (!doc || !canvas.value) return;
  const generation = ++renderGeneration;
  task?.cancel();
  const document = doc;
  const page = await document.getPage(
    Math.min(Math.max(1, props.page), document.numPages),
  );
  if (generation !== renderGeneration || document !== doc || !canvas.value)
    return;
  const unscaled = page.getViewport({ scale: 1 }),
    width = Math.min(canvas.value.parentElement?.clientWidth ?? 800, 1200);
  if (width <= 0) return;
  const view = page.getViewport({
      scale: Math.max(0.3, (width - 16) / unscaled.width) * devicePixelRatio,
    }),
    ctx = canvas.value.getContext("2d");
  if (!ctx) return;
  canvas.value.width = view.width;
  canvas.value.height = view.height;
  canvas.value.style.width = `${view.width / devicePixelRatio}px`;
  canvas.value.style.height = `${view.height / devicePixelRatio}px`;
  task = page.render({ canvasContext: ctx, viewport: view });
  try {
    await task.promise;
  } catch (e) {
    if (e instanceof Error && e.name !== "RenderingCancelledException")
      error.value = e.message;
  }
}
function turn(delta: number) {
  emit("page", Math.min(count.value, Math.max(1, props.page + delta)));
}
watch(() => props.page, render);
watch(() => props.url, load);
onMounted(() => {
  if (typeof ResizeObserver !== "undefined" && canvas.value?.parentElement) {
    resizeObserver = new ResizeObserver((entries) => {
      const width = Math.floor(entries[0]?.contentRect.width ?? 0);
      if (width > 0 && width !== lastWidth) {
        lastWidth = width;
        cancelAnimationFrame(resizeFrame);
        resizeFrame = requestAnimationFrame(() => void render());
      }
    });
    resizeObserver.observe(canvas.value.parentElement);
  }
  void load();
});
onBeforeUnmount(() => {
  controller.abort();
  task?.cancel();
  resizeObserver?.disconnect();
  cancelAnimationFrame(resizeFrame);
  void doc?.destroy();
});
</script>
