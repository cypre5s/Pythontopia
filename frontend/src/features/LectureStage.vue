<template>
  <div class="lecture-stage" :aria-busy="preparing">
    <div class="lecture-frame">
      <img
        v-if="currentImage"
        :src="currentImage"
        :alt="`第 ${pageNo} 页`"
        class="lecture-page"
        @error="markFailed(pageNo)"
      />
      <p v-else-if="failed.has(pageNo)" class="lecture-state">
        {{ imageError || "这一页没有可预览的页面图像" }}
      </p>
      <p v-else class="lecture-state" role="status">正在准备第 {{ pageNo }} 页画面…</p>
    </div>

    <p v-if="subtitle" class="lecture-subtitle">{{ subtitle }}</p>
    <p v-else-if="!loading" class="lecture-subtitle is-muted">这一页还没有已接受的讲解。</p>

    <div class="lecture-controls">
      <button type="button" class="town-secondary" :disabled="!canPlay" @click="toggle">
        <GameIcon :name="playing ? 'pause' : 'play'" />{{ playing ? "暂停" : "播放" }}
      </button>
      <label class="lecture-speed">
        语速
        <select :value="speed" @change="setSpeed(($event.target as HTMLSelectElement).value)">
          <option v-for="value in SPEEDS" :key="value" :value="value">{{ value }}×</option>
        </select>
      </label>
      <span class="lecture-progress">{{ position }}</span>
    </div>

    <audio
      v-if="clip && (currentImage || failed.has(pageNo))"
      ref="audio"
      :src="clip"
      autoplay
      @play="playing = true"
      @pause="playing = false"
      @ended="advance"
    ></audio>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import GameIcon from "../shared/GameIcon.vue";
import { api } from "../shared/api";
import { useSession } from "../shared/session";

const SPEEDS = ["0.7", "0.8", "0.9", "1", "1.1", "1.2", "1.4", "1.6", "1.8", "2"];
const PREFETCH = 5;

const props = defineProps<{ materialId: number; pageNo: number; totalPages: number }>();
const emit = defineEmits<{ page: [value: number] }>();
const session = useSession();

const loading = ref(true);
const preparing = ref(false);
const imageError = ref("");
const failed = ref(new Set<number>());
const images = ref<Record<number, string>>({});
const playable = ref<number[]>([]);
const texts = ref<Record<number, string>>({});
const clip = ref("");
const playing = ref(false);
const audio = ref<HTMLAudioElement>();
const speed = ref(localStorage.getItem("narration-speed") ?? "1");
const inFlight = new Set<number>();

const currentImage = computed(() => images.value[props.pageNo] ?? "");
const subtitle = computed(() => texts.value[props.pageNo] ?? "");
const canPlay = computed(() => playable.value.includes(props.pageNo));
const position = computed(() => {
  const index = playable.value.indexOf(props.pageNo);
  return index === -1 ? "本页无音频" : `${index + 1}/${playable.value.length} 页可听`;
});

function query() {
  return `classroom_id=${session.classroomId}`;
}

async function load() {
  loading.value = true;
  try {
    const data = await api.get<{ pages: { page_no: number; text: string; audio: boolean }[] }>(
      `/materials/${props.materialId}/narrations?${query()}`,
    );
    texts.value = Object.fromEntries(data.pages.map((page) => [page.page_no, page.text]));
    playable.value = data.pages.filter((page) => page.audio).map((page) => page.page_no);
  } catch {
    texts.value = {};
    playable.value = [];
  } finally {
    loading.value = false;
    // 列表决定“哪页有音频”，拿到后立刻补一次音频加载；否则首屏 pageNo 没变就不会再触发。
    void loadAudio();
  }
}

function markFailed(page: number) {
  failed.value = new Set(failed.value).add(page);
  imageError.value = "页面图像无法显示";
}

/** 滑动窗口预取：当前页 + 后 4 页，翻页时画面已在本地。 */
async function prefetch() {
  const ordered: number[] = playable.value.length ? [...playable.value] : [props.pageNo];
  const index = Math.max(0, ordered.indexOf(props.pageNo));
  const window: number[] = ordered.slice(index, index + PREFETCH);

  const keep: Record<number, string> = {};
  for (const [page, url] of Object.entries(images.value)) {
    if (window.includes(Number(page))) keep[Number(page)] = url;
    else URL.revokeObjectURL(url);
  }
  images.value = keep;

  for (const page of window) {
    if (images.value[page] || inFlight.has(page) || failed.value.has(page)) continue;
    inFlight.add(page);
    preparing.value = page === props.pageNo;
    try {
      const blob = await api.blob(`/materials/${props.materialId}/narrations/${page}/page-image?${query()}`);
      images.value = { ...images.value, [page]: URL.createObjectURL(blob) };
    } catch {
      if (page === props.pageNo) markFailed(page);
    } finally {
      inFlight.delete(page);
      if (page === props.pageNo) preparing.value = false;
    }
  }
}

async function loadAudio() {
  if (!canPlay.value) {
    clip.value = "";
    return;
  }
  try {
    const blob = await api.blob(`/materials/${props.materialId}/narrations/${props.pageNo}/audio?${query()}`);
    clip.value = URL.createObjectURL(blob);
  } catch {
    clip.value = "";
  }
}

function advance() {
  playing.value = false;
  const index = playable.value.indexOf(props.pageNo);
  const next = index >= 0 ? playable.value[index + 1] : undefined;
  if (next !== undefined) emit("page", next);
}

function toggle() {
  if (!audio.value) return;
  if (audio.value.paused) void audio.value.play().catch(() => (playing.value = false));
  else audio.value.pause();
}

function setSpeed(value: string) {
  speed.value = value;
  localStorage.setItem("narration-speed", value);
  if (audio.value) audio.value.playbackRate = Number(value);
}

watch(
  () => props.pageNo,
  () => {
    void prefetch();
    void loadAudio();
  },
  { immediate: true },
);

watch(
  () => clip.value,
  () => {
    if (audio.value) audio.value.playbackRate = Number(speed.value);
  },
);

void load();

onBeforeUnmount(() => {
  Object.values(images.value).forEach((url) => URL.revokeObjectURL(url));
  images.value = {};
  if (clip.value) URL.revokeObjectURL(clip.value);
});
</script>

<style scoped>
.lecture-stage { display: grid; gap: 10px; align-content: start; width: 100%; }
.lecture-frame { display: flex; align-items: center; justify-content: center; aspect-ratio: 16 / 9; background: #12100d; border-radius: 8px; overflow: hidden; }
.lecture-page { width: 100%; height: 100%; object-fit: contain; }
.lecture-state { color: #d8ccb4; font-size: .9375rem; padding: 24px; text-align: center; }
.lecture-subtitle { margin: 0; padding: 12px 14px; border-radius: 8px; background: #f6f1e3; color: #2f2b24; line-height: 1.85; font-size: .9375rem; max-height: 22vh; overflow-y: auto; }
.lecture-subtitle.is-muted { color: #8a775c; }
.lecture-controls { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
.lecture-speed { display: inline-flex; align-items: center; gap: 6px; color: #354d42; font-size: .9375rem; }
.lecture-speed select { min-height: 36px; padding: 0 8px; border: 1px solid #d9c9ad; border-radius: 6px; background: #fff; font: inherit; }
.lecture-progress { color: #8a775c; font-size: .875rem; }
</style>
