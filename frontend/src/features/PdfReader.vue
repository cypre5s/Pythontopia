<template>
  <div class="pdf-reader">
    <div class="reader-toolbar">
      <button :disabled="page <= 1" @click="turn(-1)">←</button
      ><span>第 {{ page }} / {{ count }} 页</span
      ><button :disabled="page >= count" @click="turn(1)">→</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <canvas ref="canvas" aria-label="PDF课件当前页"></canvas>
  </div>
</template>
<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from "vue";
import { api } from "../shared/api";
import type { PDFDocumentProxy, RenderTask } from "pdfjs-dist";
const props = defineProps<{ url: string; page: number }>(),
  emit = defineEmits<{ page: [value: number] }>(),
  canvas = ref<HTMLCanvasElement>(),
  count = ref(1),
  error = ref("");
let doc: PDFDocumentProxy | undefined, task: RenderTask | undefined;
const controller = new AbortController();
async function load() {
  error.value = "";
  try {
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
    await render();
  } catch (e) {
    if (!(e instanceof Error && e.name === "AbortError"))
      error.value = e instanceof Error ? e.message : "PDF加载失败";
  }
}
async function render() {
  if (!doc || !canvas.value) return;
  task?.cancel();
  const page = await doc.getPage(
    Math.min(Math.max(1, props.page), doc.numPages),
  );
  const unscaled = page.getViewport({ scale: 1 });
  const width = Math.min(canvas.value.parentElement?.clientWidth ?? 800, 1200);
  const view = page.getViewport({
    scale: Math.max(0.3, width / unscaled.width) * devicePixelRatio,
  });
  const ctx = canvas.value.getContext("2d");
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
onMounted(load);
onBeforeUnmount(() => {
  controller.abort();
  task?.cancel();
  void doc?.destroy();
});
</script>
