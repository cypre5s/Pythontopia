<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import gsap from 'gsap';
import { useSessionStore } from '@/stores/session';
import type { StoryboardContent, StoryboardScene } from '@/api/dto/Workshop';

const props = defineProps<{
  content: StoryboardContent;
}>();

const currentScene = ref(0);
const isPlaying = ref(false);
const showSubtitle = ref(true);
const playbackSpeed = ref(1);
const currentAudio = ref<HTMLAudioElement | null>(null);
const sceneImageUrl = ref('');
const sceneImageError = ref(false);
const stageRef = ref<HTMLElement | null>(null);
const sceneRefs = ref<HTMLLIElement[]>([]);

const scenes = computed(() => props.content.scenes);
const activeScene = computed<StoryboardScene | undefined>(
  () => scenes.value[currentScene.value],
);
const progressPercent = computed(() =>
  scenes.value.length > 0
    ? ((currentScene.value + 1) / scenes.value.length) * 100
    : 0,
);
const speedOptions: readonly number[] = [0.5, 1, 1.5, 2];

let currentBlobUrl: string | null = null;

function setSceneRef(el: unknown, i: number): void {
  if (el instanceof HTMLElement) sceneRefs.value[i] = el as HTMLLIElement;
}

function loadSceneImage(scene: StoryboardScene): void {
  sceneImageError.value = false;
  if (scene.evidence_page_id) {
    sceneImageUrl.value = `/api/coursepack/pages/${scene.evidence_page_id}/image`;
  } else {
    sceneImageUrl.value = '';
    sceneImageError.value = true;
  }
}

function onImageError(): void {
  sceneImageError.value = true;
}

function animateScene(index: number): void {
  const el = sceneRefs.value[index];
  if (!el) return;
  gsap.fromTo(
    el,
    { opacity: 0, y: 30, scale: 0.95 },
    { opacity: 1, y: 0, scale: 1, duration: 0.5, ease: 'power2.out' },
  );
  const badge = el.querySelector('.badge');
  if (badge) {
    gsap.fromTo(badge, { scale: 0 }, { scale: 1, duration: 0.3, delay: 0.2, ease: 'back.out(2)' });
  }
  const narration = el.querySelector('.narration');
  if (narration) {
    gsap.fromTo(narration, { opacity: 0, x: -20 }, { opacity: 1, x: 0, duration: 0.4, delay: 0.25 });
  }
  const visual = el.querySelector('.visual');
  if (visual) {
    gsap.fromTo(visual, { opacity: 0, x: 20 }, { opacity: 1, x: 0, duration: 0.4, delay: 0.35 });
  }
}

function animateStage(): void {
  if (!stageRef.value) return;
  const content = stageRef.value.querySelector('.stage-content');
  if (content) {
    gsap.fromTo(content, { opacity: 0 }, { opacity: 1, duration: 0.4, ease: 'power2.out' });
  }
}

function stopCurrentAudio(): void {
  if (currentAudio.value) {
    currentAudio.value.pause();
    currentAudio.value = null;
  }
  if (currentBlobUrl) {
    URL.revokeObjectURL(currentBlobUrl);
    currentBlobUrl = null;
  }
  if ('speechSynthesis' in window) {
    speechSynthesis.cancel();
  }
}

async function fetchTts(text: string, voice: string): Promise<Blob> {
  const session = useSessionStore();
  const response = await fetch('/api/media/tts', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${session.token}`,
    },
    body: JSON.stringify({ text: text.slice(0, 200), voice }),
  });
  if (!response.ok) throw new Error('TTS failed');
  return response.blob();
}

async function playScene(index: number): Promise<void> {
  currentScene.value = index;
  const scene = scenes.value[index];
  if (!scene) return;

  loadSceneImage(scene);
  animateScene(index);
  animateStage();

  try {
    const voice = scene.narration_tts_voice ?? 'x4_yezi';
    const audioBlob = await fetchTts(scene.narration, voice);
    if (!isPlaying.value) return;

    stopCurrentAudio();
    currentBlobUrl = URL.createObjectURL(audioBlob);
    const audio = new Audio(currentBlobUrl);
    audio.playbackRate = playbackSpeed.value;
    currentAudio.value = audio;

    await new Promise<void>((resolve) => {
      audio.onended = () => resolve();
      audio.onerror = () => resolve();
      audio.onpause = () => resolve();
      audio.play().catch(() => resolve());
    });
  } catch {
    if (!isPlaying.value) return;

    if ('speechSynthesis' in window) {
      const utterance = new SpeechSynthesisUtterance(scene.narration);
      utterance.lang = 'zh-CN';
      utterance.rate = playbackSpeed.value;
      speechSynthesis.speak(utterance);
      await new Promise<void>((resolve) => {
        utterance.onend = () => resolve();
        utterance.onerror = () => resolve();
        setTimeout(resolve, (scene.duration_seconds * 1000) / playbackSpeed.value);
      });
    } else {
      await new Promise<void>((r) =>
        setTimeout(r, (scene.duration_seconds * 1000) / playbackSpeed.value),
      );
    }
  }
}

async function playAll(): Promise<void> {
  if (isPlaying.value) return;
  isPlaying.value = true;
  for (let i = currentScene.value; i < scenes.value.length; i++) {
    if (!isPlaying.value) break;
    await playScene(i);
  }
  isPlaying.value = false;
}

function pause(): void {
  isPlaying.value = false;
  stopCurrentAudio();
}

function togglePlay(): void {
  if (isPlaying.value) pause();
  else playAll();
}

function goToScene(i: number): void {
  pause();
  currentScene.value = i;
  const scene = scenes.value[i];
  if (scene) {
    loadSceneImage(scene);
    animateScene(i);
    animateStage();
  }
}

function prevScene(): void {
  if (currentScene.value > 0) goToScene(currentScene.value - 1);
}

function nextScene(): void {
  if (currentScene.value < scenes.value.length - 1) goToScene(currentScene.value + 1);
}

watch(playbackSpeed, (speed) => {
  if (currentAudio.value) currentAudio.value.playbackRate = speed;
});

onMounted(async () => {
  await nextTick();
  const first = scenes.value[0];
  if (first) {
    loadSceneImage(first);
    animateScene(0);
  }
});

onBeforeUnmount(() => {
  stopCurrentAudio();
});

watch(
  () => props.content.scenes,
  async () => {
    pause();
    currentScene.value = 0;
    await nextTick();
    const first = scenes.value[0];
    if (first) {
      loadSceneImage(first);
      animateScene(0);
    }
  },
);
</script>

<template>
  <div class="storyboard-wrapper">
    <div
      v-if="scenes.length === 0"
      class="empty-state"
    >
      暂无故事板场景数据。
    </div>
    <template v-else>
      <div
        ref="stageRef"
        class="theater-stage"
      >
        <div class="stage-content">
          <img
            v-if="sceneImageUrl && !sceneImageError"
            :src="sceneImageUrl"
            :alt="activeScene?.visual_hint ?? ''"
            class="stage-image"
            @error="onImageError"
          >
          <div
            v-else
            class="stage-placeholder"
          >
            <svg
              class="placeholder-icon"
              viewBox="0 0 80 60"
              width="80"
              height="60"
            >
              <rect
                x="4"
                y="4"
                width="72"
                height="52"
                rx="4"
                fill="none"
                stroke="currentColor"
                stroke-width="3"
              />
              <polygon
                points="32,18 32,42 54,30"
                fill="currentColor"
                opacity="0.6"
              />
            </svg>
            <p
              v-if="activeScene"
              class="visual-hint"
            >
              {{ activeScene.visual_hint }}
            </p>
          </div>
        </div>
        <Transition name="subtitle-fade">
          <div
            v-if="showSubtitle && activeScene"
            :key="currentScene"
            class="subtitle-overlay"
          >
            <p>{{ activeScene.narration }}</p>
          </div>
        </Transition>
      </div>

      <div class="control-bar">
        <div class="playback-controls">
          <button
            type="button"
            class="ctrl-btn"
            :disabled="currentScene <= 0"
            title="上一幕"
            @click="prevScene"
          >
            &#x23EE;
          </button>
          <button
            type="button"
            class="ctrl-btn play-btn"
            :title="isPlaying ? '暂停' : '播放'"
            @click="togglePlay"
          >
            {{ isPlaying ? '\u23F8' : '\u25B6' }}
          </button>
          <button
            type="button"
            class="ctrl-btn"
            :disabled="currentScene >= scenes.length - 1"
            title="下一幕"
            @click="nextScene"
          >
            &#x23ED;
          </button>
        </div>
        <span class="scene-counter">{{ currentScene + 1 }} / {{ scenes.length }}</span>
        <div class="progress-track">
          <div
            class="progress-fill"
            :style="{ width: progressPercent + '%' }"
          />
        </div>
        <button
          type="button"
          :class="['ctrl-btn', 'subtitle-toggle', { active: showSubtitle }]"
          title="字幕开关"
          @click="showSubtitle = !showSubtitle"
        >
          字幕
        </button>
        <select
          v-model="playbackSpeed"
          class="speed-select"
          title="播放速度"
        >
          <option
            v-for="s in speedOptions"
            :key="s"
            :value="s"
          >
            {{ s }}x
          </option>
        </select>
      </div>

      <div class="scene-nav">
        <button
          v-for="(scene, i) in scenes"
          :key="i"
          type="button"
          :class="['nav-dot', { active: i === currentScene }]"
          :title="scene.title"
          @click="goToScene(i)"
        >
          {{ i + 1 }}
        </button>
      </div>

      <ol class="storyboard">
        <li
          v-for="(scene, index) in scenes"
          :key="index"
          :ref="(el) => setSceneRef(el, index)"
          :class="['scene-card', { current: index === currentScene, dimmed: index !== currentScene }]"
        >
          <header>
            <span class="badge">{{ scene.duration_seconds }}s</span>
            <h3>{{ scene.title }}</h3>
          </header>
          <p class="narration">
            旁白：{{ scene.narration }}
          </p>
          <p class="visual">
            画面：{{ scene.visual_hint }}
          </p>
          <small>关联 KC: {{ scene.kc_codes.join(', ') || '未标注' }}</small>
        </li>
      </ol>
    </template>
  </div>
</template>

<style scoped>
.storyboard-wrapper {
  display: grid;
  gap: 12px;
}

.empty-state {
  padding: 24px;
  text-align: center;
  color: #6e4f1d;
  font-size: 14px;
}

/* ── Theater Stage ── */
.theater-stage {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  background: #2e2417;
  border-radius: 8px;
  border: 3px solid #6e4f1d;
  overflow: hidden;
}
.stage-content {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.stage-image {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.stage-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #c9a86b;
  padding: 20px;
  text-align: center;
}
.placeholder-icon { opacity: 0.5; }
.visual-hint {
  font-size: 14px;
  line-height: 1.5;
  max-width: 80%;
  margin: 0;
}

/* ── Subtitle Overlay ── */
.subtitle-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 12px 24px;
  background: rgba(46, 36, 23, 0.85);
}
.subtitle-overlay p {
  margin: 0;
  color: #f9e7bc;
  font-size: 15px;
  line-height: 1.55;
  text-align: center;
  font-family: var(--pyt-font-pixel, monospace);
}
.subtitle-fade-enter-active,
.subtitle-fade-leave-active {
  transition: opacity 0.3s, transform 0.3s;
}
.subtitle-fade-enter-from,
.subtitle-fade-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

/* ── Control Bar ── */
.control-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: #fffbe9;
  border: 2px solid #c9a86b;
  border-radius: 8px;
}
.playback-controls {
  display: flex;
  gap: 4px;
}
.ctrl-btn {
  padding: 6px 12px;
  border-radius: 6px;
  border: 2px solid #6e4f1d;
  background: #f9e7bc;
  color: #3a2a14;
  cursor: pointer;
  font-size: 14px;
  font-family: var(--pyt-font-pixel, monospace);
  transition: background 0.15s;
  line-height: 1;
}
.ctrl-btn:hover:not(:disabled) { background: #f1d896; }
.ctrl-btn:disabled { opacity: 0.4; cursor: default; }
.play-btn {
  background: #4f8a3e;
  color: #fff7e6;
  border-color: #2b6021;
  min-width: 40px;
  text-align: center;
}
.play-btn:hover { background: #3d7230; }
.scene-counter {
  font-size: 13px;
  color: #6e4f1d;
  white-space: nowrap;
  font-family: var(--pyt-font-pixel, monospace);
}
.progress-track {
  flex: 1;
  height: 8px;
  background: #e6d4a5;
  border-radius: 4px;
  overflow: hidden;
  border: 1px solid #c9a86b;
  min-width: 60px;
}
.progress-fill {
  height: 100%;
  background: #4f8a3e;
  border-radius: 4px;
  transition: width 0.3s ease;
}
.subtitle-toggle {
  font-size: 12px;
  padding: 4px 10px;
}
.subtitle-toggle.active {
  background: #4f8a3e;
  color: #fff7e6;
  border-color: #2b6021;
}
.speed-select {
  padding: 4px 6px;
  border-radius: 4px;
  border: 2px solid #6e4f1d;
  background: #f9e7bc;
  color: #3a2a14;
  font-size: 12px;
  font-family: var(--pyt-font-pixel, monospace);
  cursor: pointer;
}

/* ── Scene Navigation ── */
.scene-nav {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.nav-dot {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 2px solid #c9a86b;
  background: #fffbe9;
  font-size: 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  font-family: var(--pyt-font-pixel, monospace);
}
.nav-dot.active {
  background: #4f8a3e;
  color: #fff7e6;
  border-color: #2b6021;
  transform: scale(1.15);
}

/* ── Scene Cards ── */
.storyboard {
  list-style: none;
  padding: 0;
  display: grid;
  gap: 12px;
}
.scene-card {
  background: #fffbe9;
  border: 2px solid #c9a86b;
  border-radius: 8px;
  padding: 12px;
  transition: opacity 0.3s, border-color 0.3s;
}
.scene-card.current {
  border-color: #4f8a3e;
  box-shadow: 0 0 12px rgba(79, 138, 62, 0.25);
}
.scene-card.dimmed { opacity: 0.5; }
.storyboard header {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.storyboard h3 {
  margin: 0;
  font-size: 16px;
}
.badge {
  background: #4f8a3e;
  color: #fff7e6;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
}
.narration {
  margin: 6px 0;
  line-height: 1.55;
}
.visual {
  margin: 0 0 6px;
  color: #5e4923;
}
small {
  color: #6e4f1d;
  font-size: 12px;
}
</style>
