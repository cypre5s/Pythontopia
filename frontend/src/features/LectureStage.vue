<template>
  <div class="lecture-stage">
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

    <p class="lecture-subtitle" :class="{ 'is-muted': !currentLine }" aria-live="polite">
      {{ currentLine || emptyHint }}
    </p>

    <div class="lecture-bar">
      <button
        type="button"
        class="lecture-play"
        :disabled="!canPlay"
        :aria-label="playing ? '暂停讲解' : '播放讲解'"
        @click="toggle"
      >
        <GameIcon :name="playing ? 'pause' : 'play'" />
      </button>
      <input
        class="lecture-track"
        type="range"
        min="0"
        :max="duration || 0"
        step="0.2"
        :value="position"
        :style="{ '--progress': `${percent}%` }"
        :disabled="!canPlay"
        aria-label="讲解进度"
        @input="seek"
      />
      <span class="lecture-time">{{ clock }}</span>
      <select class="lecture-speed" :value="speed" aria-label="讲解语速" @change="setSpeed(($event.target as HTMLSelectElement).value)">
        <option v-for="value in SPEEDS" :key="value" :value="value">{{ value }}×</option>
      </select>
    </div>

    <audio
      v-if="clip && (currentImage || failed.has(pageNo))"
      ref="audio"
      :src="clip"
      preload="auto"
      @canplay="resume"
      @play="playing = true"
      @pause="playing = false"
      @timeupdate="tick"
      @loadedmetadata="readDuration"
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
const LINE_CHARS = 44;

const props = defineProps<{ materialId: number; pageNo: number; totalPages: number }>();
const emit = defineEmits<{ page: [value: number] }>();
const session = useSession();

const loading = ref(true);
const imageError = ref("");
const failed = ref(new Set<number>());
const images = ref<Record<number, string>>({});
const playable = ref<number[]>([]);
const texts = ref<Record<number, string>>({});
const clip = ref("");
const playing = ref(false);
const wantsPlay = ref(false);
const audio = ref<HTMLAudioElement>();
const position = ref(0);
const duration = ref(0);
const lineIndex = ref(0);
const speed = ref(localStorage.getItem("narration-speed") ?? "1");
const inFlight = new Set<number>();

const currentImage = computed(() => images.value[props.pageNo] ?? "");
const canPlay = computed(() => playable.value.includes(props.pageNo));
const emptyHint = computed(() => (loading.value ? "正在加载讲解…" : "这一页还没有可播放的讲解。"));

/** 讲义按句切行，播放时一行一行换，不再整段堆在画面下。 */
const lines = computed(() => splitLines(texts.value[props.pageNo] ?? ""));
const currentLine = computed(() => lines.value[lineIndex.value] ?? "");
const percent = computed(() => (duration.value > 0 ? Math.min(100, (position.value / duration.value) * 100) : 0));
const clock = computed(() => (canPlay.value ? `${stamp(position.value)} / ${stamp(duration.value)}` : "--:--"));

function stamp(seconds: number) {
  if (!Number.isFinite(seconds) || seconds <= 0) return "00:00";
  const total = Math.round(seconds);
  return `${String(Math.floor(total / 60)).padStart(2, "0")}:${String(total % 60).padStart(2, "0")}`;
}

/** 先按整句（。！？；）切行；单句过长再按逗号切，避免把半句话单独留在屏上。 */
function splitLines(text: string): string[] {
  const source = text.replace(/\s+/g, "");
  if (!source) return [];
  const rows: string[] = [];
  let buffer = "";
  const flush = () => {
    if (buffer) {
      rows.push(buffer);
      buffer = "";
    }
  };
  for (const sentence of source.split(/(?<=[。！？；!?;])/).filter(Boolean)) {
    if (sentence.length <= LINE_CHARS) {
      if (buffer.length + sentence.length > LINE_CHARS) flush();
      buffer += sentence;
      continue;
    }
    flush();
    let piece = "";
    for (const chunk of sentence.split(/(?<=[，,])/)) {
      if (piece && piece.length + chunk.length > LINE_CHARS) {
        rows.push(piece);
        piece = chunk;
      } else {
        piece += chunk;
      }
    }
    if (piece) rows.push(piece);
  }
  flush();
  return rows.length ? rows : [source];
}

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
    try {
      const blob = await api.blob(`/materials/${props.materialId}/narrations/${page}/page-image?${query()}`);
      images.value = { ...images.value, [page]: URL.createObjectURL(blob) };
    } catch {
      if (page === props.pageNo) markFailed(page);
    } finally {
      inFlight.delete(page);
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

/** 用户点过播放后，翻页加载完成就接着播；没点过就停在待播状态。 */
function resume() {
  if (!wantsPlay.value || !audio.value) return;
  audio.value.playbackRate = Number(speed.value);
  void audio.value.play().catch(() => (playing.value = false));
}

function readDuration() {
  duration.value = audio.value?.duration ?? 0;
}

/** 按播放进度把字幕换到对应那一行（按字数占比估算，无逐字时间轴）。 */
function tick() {
  position.value = audio.value?.currentTime ?? 0;
  duration.value = audio.value?.duration ?? duration.value;
  if (!lines.value.length || duration.value <= 0) return;
  const total = lines.value.reduce((sum, line) => sum + line.length, 0);
  const budget = (position.value / duration.value) * total;
  let passed = 0;
  let next = lines.value.length - 1;
  for (const [index, line] of lines.value.entries()) {
    passed += line.length;
    if (budget < passed) {
      next = index;
      break;
    }
  }
  lineIndex.value = next;
}

function seek(event: Event) {
  const value = Number((event.target as HTMLInputElement).value);
  position.value = value;
  if (!audio.value) return;
  audio.value.currentTime = value;
  tick();
}

function advance() {
  playing.value = false;
  const index = playable.value.indexOf(props.pageNo);
  const next = index >= 0 ? playable.value[index + 1] : undefined;
  if (next !== undefined) emit("page", next);
}

function toggle() {
  if (!audio.value) return;
  if (audio.value.paused) {
    wantsPlay.value = true;
    void audio.value.play().catch(() => (playing.value = false));
  } else {
    wantsPlay.value = false;
    audio.value.pause();
  }
}

function setSpeed(value: string) {
  speed.value = value;
  localStorage.setItem("narration-speed", value);
  if (audio.value) audio.value.playbackRate = Number(value);
}

watch(
  () => props.pageNo,
  () => {
    position.value = 0;
    duration.value = 0;
    lineIndex.value = 0;
    void prefetch();
    void loadAudio();
  },
  { immediate: true },
);

watch(
  () => clip.value,
  () => {
    position.value = 0;
    duration.value = 0;
    lineIndex.value = 0;
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
.lecture-stage {
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto auto;
  gap: 10px;
  width: 100%;
  height: 100%;
  min-height: 0;
}
.lecture-frame {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 0;
  overflow: hidden;
  border-radius: var(--game-radius, 10px);
  background: transparent;
}
.lecture-page { width: 100%; height: 100%; object-fit: contain; border-radius: 3px; box-shadow: 0 3px 18px #382a1814; background: #fff; }
.lecture-state { color: #d8ccb4; font-size: var(--game-body-size, .9375rem); padding: 24px; text-align: center; }
.lecture-subtitle {
  margin: 0;
  min-height: 2.75em;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 12px;
  color: var(--game-ink, #513b28);
  font-size: 1.0625rem;
  line-height: 1.55;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.lecture-subtitle.is-muted { color: var(--game-muted, #7c6b55); font-size: var(--game-body-size, .9375rem); }
.lecture-bar { display: flex; align-items: center; gap: 12px; padding: 0 2px 2px; }
.lecture-play {
  flex: none;
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  padding: 0;
  border: 1px solid #1b312a;
  border-radius: 50%;
  background: var(--game-forest, #284b43);
  color: #f7f1df;
  cursor: pointer;
}
.lecture-play:hover:not(:disabled) { background: var(--game-forest-deep, #203b32); }
.lecture-play:disabled { opacity: .45; cursor: not-allowed; }
.lecture-play :deep(.game-icon) { width: 18px; height: 18px; }
.lecture-track {
  flex: 1;
  min-width: 0;
  height: 6px;
  margin: 0;
  padding: 0;
  appearance: none;
  border-radius: 999px;
  background: linear-gradient(to right, var(--game-forest, #284b43) 0 var(--progress, 0%), #e0d3ba var(--progress, 0%) 100%);
  cursor: pointer;
}
.lecture-track:disabled { opacity: .45; cursor: default; }
.lecture-track::-webkit-slider-thumb {
  appearance: none;
  width: 14px;
  height: 14px;
  border: 2px solid var(--game-forest, #284b43);
  border-radius: 50%;
  background: var(--game-paper, #fbf5e9);
}
.lecture-track::-moz-range-thumb {
  width: 12px;
  height: 12px;
  border: 2px solid var(--game-forest, #284b43);
  border-radius: 50%;
  background: var(--game-paper, #fbf5e9);
}
.lecture-time { flex: none; color: var(--game-muted, #7c6b55); font-size: var(--game-caption-size, .8125rem); font-variant-numeric: tabular-nums; }
.lecture-speed {
  flex: none;
  height: 30px;
  padding: 0 4px;
  border: 1px solid var(--game-line, #d7c7a9);
  border-radius: 6px;
  background: var(--game-paper-raised, #fffaf0);
  color: var(--game-ink, #513b28);
  font-size: var(--game-caption-size, .8125rem);
}
</style>
