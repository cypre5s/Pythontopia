<template>
  <section v-if="current" class="narration-panel" aria-labelledby="narration-heading">
    <button
      class="narration-toggle"
      type="button"
      :aria-expanded="open"
      aria-controls="narration-body"
      @click="open = !open"
    >
      <GameIcon name="book" />
      <span id="narration-heading">本页讲解</span>
      <small>{{ durationLabel }}</small>
      <span class="narration-chevron" aria-hidden="true">{{ open ? "收起" : "展开" }}</span>
    </button>
    <div v-show="open" id="narration-body" class="narration-body">
      <p v-if="current.bridge_in" class="narration-bridge">{{ current.bridge_in }}</p>
      <p class="narration-text">{{ current.text }}</p>
      <p class="narration-source">AI 生成 · 教师已审校。讲解是辅助材料，不替代课件原文，也不计入阅读完成。</p>
    </div>
  </section>
</template>
<script setup lang="ts">
import GameIcon from "../shared/GameIcon.vue";
import { computed, ref, watch } from "vue";
import { api } from "../shared/api";
import { useSession } from "../shared/session";

interface NarrationPage {
  page_no: number;
  text: string;
  bridge_in: string;
  target_seconds: number;
}

const props = defineProps<{ materialId: number; pageNo: number }>();
const session = useSession();
const pages = ref<NarrationPage[]>([]);
const open = ref(false);
let generation = 0;

const current = computed(() => pages.value.find((item) => item.page_no === props.pageNo) ?? null);
const durationLabel = computed(() => {
  const seconds = current.value?.target_seconds ?? 0;
  return seconds > 0 ? `约 ${Math.round(seconds / 60) || 1} 分钟` : "";
});

async function load() {
  const classroomId = session.classroomId;
  const wanted = props.materialId;
  const ticket = ++generation;
  pages.value = [];
  if (classroomId === null || !wanted) return;
  try {
    const result = await api.get<{ pages: NarrationPage[] }>(`/materials/${wanted}/narrations?classroom_id=${classroomId}`);
    // A slower response for a previous material must never replace the current one.
    if (ticket === generation) pages.value = result.pages ?? [];
  } catch {
    // Narration is optional: a failure here must not interrupt reading.
    if (ticket === generation) pages.value = [];
  }
}

watch(() => props.materialId, () => void load(), { immediate: true });
</script>
<style scoped>
.narration-panel { margin-top: 18px; border: 1px solid #d9c9ad; border-radius: 10px; background: #fffdf5; }
.narration-toggle { display: flex; align-items: center; gap: 10px; width: 100%; padding: 12px 16px; min-height: 44px; color: #354d42; font: inherit; text-align: left; background: none; border: 0; cursor: pointer; }
.narration-toggle small { color: #8a775c; }
.narration-chevron { margin-left: auto; color: #6c7d57; font-size: .875rem; }
.narration-body { padding: 0 16px 16px; }
.narration-bridge { margin: 0 0 8px; color: #8a775c; font-size: .875rem; }
.narration-text { margin: 0; line-height: 1.9; white-space: pre-wrap; }
.narration-source { margin: 12px 0 0; color: #8a775c; font-size: .8125rem; }
@media (max-width: 700px) { .narration-toggle { padding: 12px; } .narration-body { padding: 0 12px 14px; } }
</style>
