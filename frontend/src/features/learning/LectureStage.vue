<template>
  <div v-if="visible" class="lecture-stage">
    <p class="lecture-subtitle" :style="{ fontSize: `${preferences.subtitleSize}rem` }" :class="{ 'is-muted': !currentLine }" aria-live="polite">
      {{ audioError || currentLine || emptyHint }}
    </p>

    <div class="lecture-bar">
      <button
        type="button"
        class="lecture-play"
        :disabled="!canPlay || !clip || pageReady === false"
        :aria-label="playing ? '暂停讲解' : '播放讲解'"
        @click="toggle"
      >
        <GameIcon :name="playing ? 'pause' : 'play'" />
      </button>
      <input
        class="lecture-track"
        type="range"
        min="0"
        :max="totalDuration"
        step="0.2"
        :value="globalPosition"
        :style="{ '--progress': `${percent}%` }"
        :disabled="!timeline.length"
        aria-label="整篇讲解进度"
        @input="seek"
      />
      <span class="lecture-time">{{ clock }}</span>
      <select class="lecture-speed" :value="speed" aria-label="讲解语速" @change="setSpeed(($event.target as HTMLSelectElement).value)">
        <option v-for="value in SPEEDS" :key="value" :value="value">{{ value }}×</option>
      </select>
    </div>

    <audio
      v-if="clip && pageReady !== false"
      ref="audio"
      :src="clip"
      preload="auto"
      @canplay="resume"
      @play="playing = true"
      @pause="playing = false"
      @timeupdate="tick"
      @loadedmetadata="readDuration"
      @ended="advance"
      @error="audioError = '音频无法播放，可继续阅读或向助教提问。'"
    ></audio>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import GameIcon from "../../shared/GameIcon.vue";
import { api } from "../../shared/api";
import { useSession } from "../../shared/session";
import { useUiPreferences } from "../../shared/uiPreferences";
import { lectureTimeline, lectureTarget, lectureStamp, type LectureSegment } from "../../shared/lectureTimeline";

const SPEEDS = ["0.7", "0.8", "0.9", "1", "1.1", "1.2", "1.4", "1.6", "1.8", "2"];
const LINE_CHARS = 44;

const props = withDefaults(defineProps<{ materialId: number; pageNo: number; totalPages: number; pageReady?: boolean }>(), { pageReady: true });
const emit = defineEmits<{ page: [value: number]; timeline: [value: LectureSegment[]]; availability: [value: boolean] }>();
const session = useSession();
const preferences = useUiPreferences();

const loading = ref(true);
const playable = ref<number[]>([]);
const texts = ref<Record<number, string>>({});
const clip = ref("");
const playing = ref(false);
const wantsPlay = ref(false);
const audio = ref<HTMLAudioElement>();
const position = ref(0);
const duration = ref(0);
const durations = ref<Record<number, number>>({});
let pendingSeek: { pageNo: number; offset: number } | undefined;
const lineIndex = ref(0);
const speed = ref(localStorage.getItem("narration-speed") ?? "1");
let generation = 0;
let audioGeneration = 0;
let audioController: AbortController | undefined;
const audioError = ref("");

const visible = computed(() => playable.value.length > 0 || !!audioError.value);
watch(visible, value => emit("availability", value), { immediate: true });

const canPlay = computed(() => playable.value.includes(props.pageNo));
const emptyHint = computed(() => (loading.value ? "正在加载讲解…" : "这一页还没有可播放的讲解。"));

/** 讲义按句切行，播放时一行一行换，不再整段堆在画面下。 */
const lines = computed(() => splitLines(texts.value[props.pageNo] ?? ""));
const currentLine = computed(() => lines.value[lineIndex.value] ?? "");
const timeline = computed(() => lectureTimeline(playable.value, durations.value));
watch(timeline, value => emit('timeline', value), { immediate: true });
const totalDuration = computed(() => { const last = timeline.value.at(-1); return last ? last.start + last.seconds : 0; });
const globalPosition = computed(() => {
  const current = timeline.value.find(item => item.pageNo === props.pageNo);
  return current ? current.start + Math.min(position.value, current.seconds) : timeline.value.find(item => item.pageNo > props.pageNo)?.start ?? totalDuration.value;
});
const percent = computed(() => totalDuration.value ? Math.min(100, globalPosition.value / totalDuration.value * 100) : 0);
const clock = computed(() => timeline.value.length ? `${lectureStamp(globalPosition.value)} / ${lectureStamp(totalDuration.value)}` : '时长待加载');

/** 先按整句（。！？；）切行；单句过长再按逗号切，避免把半句话单独留在屏上。 */
function splitLines(text: string): string[] {
  const source = text.trim();
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

function clearAudio() {
  audioController?.abort();
  ++audioGeneration;
  audio.value?.pause();
  playing.value = false;
  if (clip.value) URL.revokeObjectURL(clip.value);
  clip.value = "";
  audioError.value = "";
  position.value = duration.value = lineIndex.value = 0;
}

async function load() {
  const ticket = ++generation;
  clearAudio();
  wantsPlay.value = false;
  pendingSeek = undefined;
  durations.value = {};
  texts.value = {};
  playable.value = [];
  loading.value = true;
  const id = props.materialId;
  try {
    const data = await api.get<{ pages: { page_no: number; text: string; audio: boolean; audio_seconds?: number }[] }>(
      `/materials/${id}/narrations?${query()}`,
    );
    if (ticket !== generation) return;
    texts.value = Object.fromEntries(data.pages.map((page) => [page.page_no, page.text]));
    durations.value = Object.fromEntries(data.pages.map(page => [page.page_no, page.audio_seconds ?? 0]));
    playable.value = data.pages.filter((page) => page.audio).map((page) => page.page_no).sort((a, b) => a - b);
  } catch {
    if (ticket === generation) audioError.value = "讲解暂时无法加载，可以继续阅读。";
  } finally {
    if (ticket === generation) loading.value = false;
  }
}

async function loadAudio() {
  clearAudio();
  if (pendingSeek && pendingSeek.pageNo !== props.pageNo) pendingSeek = undefined;
  if (!canPlay.value) return;
  const ticket = audioGeneration;
  const controller = new AbortController();
  audioController = controller;
  try {
    const blob = await api.blob(`/materials/${props.materialId}/narrations/${props.pageNo}/audio?${query()}`, controller.signal);
    if (ticket !== audioGeneration || controller.signal.aborted) return;
    clip.value = URL.createObjectURL(blob);
  } catch {
    if (ticket === audioGeneration && !controller.signal.aborted) audioError.value = "音频暂时无法播放，可以继续阅读讲解文字。";
  }
}

/** 用户点过播放后，翻页加载完成就接着播；没点过就停在待播状态。 */
function resume() {
  if (!audio.value) return;
  audio.value.playbackRate = Number(speed.value);
  if (!wantsPlay.value) return;
  void audio.value.play().catch(() => (playing.value = false));
}

function readDuration() {
  duration.value = audio.value?.duration ?? 0;
  if (duration.value > 0 && Number.isFinite(duration.value)) durations.value[props.pageNo] = duration.value;
  if (audio.value && pendingSeek?.pageNo === props.pageNo) {
    audio.value.currentTime = Math.min(pendingSeek.offset, duration.value);
    pendingSeek = undefined;
    tick();
  }
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
  const target = lectureTarget(timeline.value, Number((event.target as HTMLInputElement).value));
  if (!target) return;
  pendingSeek = target;
  if (target.pageNo !== props.pageNo) emit('page', target.pageNo);
  else if (audio.value) { audio.value.currentTime = target.offset; pendingSeek = undefined; tick(); }
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
    audio.value.playbackRate = Number(speed.value);
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

watch(() => [props.materialId, session.classroomId], () => void load(), { immediate: true });
watch(() => [props.pageNo, playable.value], () => void loadAudio());
onBeforeUnmount(() => {
  ++generation;
  clearAudio();
});
</script>

<style scoped>
.lecture-stage {
  display: grid;
  grid-template-rows: auto auto;
  gap: 10px;
  width: 100%;
  padding: 0 12px 10px;
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
  white-space: pre-wrap;
  overflow: hidden;
  overflow-wrap: anywhere;
}
.lecture-subtitle.is-muted { color: var(--game-muted, #7c6b55); font-size: var(--game-body-size, .9375rem); }
.lecture-bar { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; padding: 0 2px 2px; }
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
.game-workspace .lecture-track {
  flex: 1;
  min-width: 0;
  height: 6px;
  min-height: 6px;
  border: 0;
  margin: 0;
  padding: 0;
  appearance: none;
  border-radius: 999px;
  background: linear-gradient(to right, var(--game-forest, #284b43) 0 var(--progress, 0%), #e0d3ba var(--progress, 0%) 100%);
  cursor: pointer;
}
.lecture-track:disabled { opacity: .45; cursor: default; }
.game-workspace .lecture-track::-webkit-slider-runnable-track {
  height: 6px;
  background: linear-gradient(to right, var(--game-forest) 0 var(--progress, 0%), #e0d3ba var(--progress, 0%) 100%);
}
.game-workspace .lecture-track::-moz-range-track {
  height: 6px;
  background: linear-gradient(to right, var(--game-forest) 0 var(--progress, 0%), #e0d3ba var(--progress, 0%) 100%);
}
.game-workspace .lecture-track::-webkit-slider-thumb {
  appearance: none;
  width: 14px;
  height: 14px;
  margin-top: -4px;
  border: 2px solid var(--game-forest, #284b43);
  border-radius: 50%;
  background: var(--game-paper, #fbf5e9);
}
.game-workspace .lecture-track::-moz-range-thumb {
  width: 12px;
  height: 12px;
  border: 2px solid var(--game-forest, #284b43);
  border-radius: 50%;
  background: var(--game-paper, #fbf5e9);
}
.lecture-time { flex: none; color: var(--game-muted, #7c6b55); font-size: var(--game-caption-size, .8125rem); font-variant-numeric: tabular-nums; }
.game-workspace .lecture-stage .lecture-bar select.lecture-speed {
  flex: none;
  width: 58px;
  min-width: 58px;
  height: 26px;
  min-height: 26px;
  padding: 0 18px 0 5px;
  border: 1px solid var(--game-line, #d7c7a9);
  border-radius: 6px;
  background: var(--game-paper-raised, #fffaf0);
  appearance: auto;
  color: var(--game-ink, #513b28);
  font-size: var(--game-caption-size, .8125rem);
}
</style>
