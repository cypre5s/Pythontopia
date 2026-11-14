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
      <span id="narration-heading">{{ compact ? "向助教提问" : "本页讲解" }}</span>
      <small v-if="!compact">{{ durationLabel }}</small>
      <span class="narration-chevron" aria-hidden="true">{{ open ? "收起" : "展开" }}</span>
    </button>
    <div v-show="open" id="narration-body" class="narration-body">
      <template v-if="!compact">
      <p v-if="current.bridge_in" class="narration-bridge">{{ current.bridge_in }}</p>
      <p class="narration-text">{{ current.text }}</p>
      <div v-if="current.audio" class="narration-audio">
        <button type="button" class="town-secondary" :disabled="loadingAudio" @click="loadAudio">
          <GameIcon name="video" />{{ loadingAudio ? "正在加载音频…" : audioUrl ? "重新载入音频" : "听本页讲解" }}
        </button>
        <small v-if="current.audio_seconds">约 {{ Math.max(1, Math.round(current.audio_seconds / 60)) }} 分钟</small>
        <audio v-if="audioUrl" controls autoplay :src="audioUrl" aria-label="本页讲解音频"></audio>
        <small v-if="audioError" class="narration-audio-error">{{ audioError }}</small>
      </div>
      </template>
      <form class="narration-ask" @submit.prevent="submitQuestion">
        <label for="narration-question">就这页提问</label>
        <div class="narration-ask-row">
          <input
            id="narration-question"
            v-model="question"
            type="text"
            maxlength="500"
            placeholder="例如：这页的 fit 和 transform 有什么区别？"
            :disabled="sending"
            autocomplete="off"
          />
          <button type="submit" class="town-secondary" :disabled="sending || !question.trim()">
            {{ sending ? "思考中…" : "提问" }}
          </button>
        </div>
        <p v-if="askError" class="narration-ask-error" role="alert">{{ askError }}</p>
        <div v-if="answer" class="narration-answer" role="status">
          <p>{{ answer.answer }}</p>
          <small>
            依据第 {{ answer.cites.join("、") || "—" }} 页
            <template v-if="typeof answer.remaining === 'number'">· 今日还可提问 {{ answer.remaining }} 次</template>
          </small>
        </div>
      </form>
      <p class="narration-source">AI 生成 · 教师已审校。讲解是辅助材料，不替代课件原文，也不计入阅读完成。</p>
    </div>
  </section>
</template>
<script setup lang="ts">
import GameIcon from "../shared/GameIcon.vue";
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { api } from "../shared/api";
import { useSession } from "../shared/session";

interface NarrationPage {
  page_no: number;
  text: string;
  bridge_in: string;
  target_seconds: number;
  audio?: boolean;
  audio_seconds?: number | null;
}

const props = defineProps<{ materialId: number; pageNo: number; compact?: boolean }>();
const session = useSession();
const pages = ref<NarrationPage[]>([]);
const open = ref(false);
const audioUrl = ref(""), audioError = ref(""), loadingAudio = ref(false);
const question = ref(""), sending = ref(false), askError = ref("");
const answer = ref<{ answer: string; cites: number[]; remaining?: number } | null>(null);
let generation = 0;

const current = computed(() => pages.value.find((item) => item.page_no === props.pageNo) ?? null);
const durationLabel = computed(() => {
  const seconds = current.value?.target_seconds ?? 0;
  return seconds > 0 ? `约 ${Math.round(seconds / 60) || 1} 分钟` : "";
});

function releaseAudio() {
  if (audioUrl.value) URL.revokeObjectURL(audioUrl.value);
  audioUrl.value = "";
  audioError.value = "";
}

async function submitQuestion() {
  const classroomId = session.classroomId;
  const page = current.value;
  const text = question.value.trim();
  if (classroomId === null || !page || !text || sending.value) return;
  sending.value = true;
  askError.value = "";
  answer.value = null;
  try {
    answer.value = await api.post<{ answer: string; cites: number[]; remaining?: number }>(
      `/materials/${props.materialId}/narrations/${page.page_no}/ask?classroom_id=${classroomId}`,
      { question: text },
    );
    question.value = "";
  } catch (reason) {
    askError.value = reason instanceof Error ? reason.message : "提问失败，请稍后重试";
  } finally {
    sending.value = false;
  }
}

async function loadAudio() {
  const classroomId = session.classroomId;
  const page = current.value;
  if (classroomId === null || !page) return;
  loadingAudio.value = true;
  releaseAudio();
  try {
    const blob = await api.blob(`/materials/${props.materialId}/narrations/${page.page_no}/audio?classroom_id=${classroomId}`);
    audioUrl.value = URL.createObjectURL(blob);
  } catch {
    // Audio is optional; failing to fetch it must not break reading.
    audioError.value = "音频暂时无法播放，可以继续看讲解文字。";
  } finally {
    loadingAudio.value = false;
  }
}

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
watch(() => props.pageNo, () => {
  releaseAudio();
  // An answer belongs to the page it was asked on.
  answer.value = null;
  askError.value = "";
});
onBeforeUnmount(releaseAudio);
</script>
<style scoped>
.narration-panel { margin-top: 18px; border: 1px solid #d9c9ad; border-radius: 10px; background: #fffdf5; }
.narration-toggle { display: flex; align-items: center; gap: 10px; width: 100%; padding: 12px 16px; min-height: 44px; color: #354d42; font: inherit; text-align: left; background: none; border: 0; cursor: pointer; }
.narration-toggle small { color: #8a775c; }
.narration-chevron { margin-left: auto; color: #6c7d57; font-size: .875rem; }
.narration-body { padding: 0 16px 16px; }
.narration-bridge { margin: 0 0 8px; color: #8a775c; font-size: .875rem; }
.narration-text { margin: 0; line-height: 1.9; white-space: pre-wrap; }
.narration-audio { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin-top: 14px; }
.narration-audio audio { flex: 1 1 min(320px, 100%); min-width: 0; }
.narration-audio small { color: #8a775c; }
.narration-audio-error { color: #8a4b32; }
.narration-ask { margin-top: 16px; padding-top: 14px; border-top: 1px dashed #d9c9ad; display: grid; gap: 8px; }
.narration-ask > label { color: #354d42; font-size: .9375rem; }
.narration-ask-row { display: flex; flex-wrap: wrap; gap: 8px; }
.narration-ask-row input { flex: 1 1 min(260px, 100%); min-width: 0; min-height: 44px; padding: 0 12px; border: 1px solid #d9c9ad; border-radius: 8px; background: #fff; font: inherit; }
.narration-answer { padding: 12px; border-radius: 8px; background: #f5f0e2; }
.narration-answer p { margin: 0 0 8px; white-space: pre-wrap; line-height: 1.8; }
.narration-answer small, .narration-ask-error { color: #8a775c; }
.narration-ask-error { color: #8a4b32; }
.narration-source { margin: 12px 0 0; color: #8a775c; font-size: .8125rem; }
@media (max-width: 700px) { .narration-toggle { padding: 12px; } .narration-body { padding: 0 12px 14px; } }
</style>
