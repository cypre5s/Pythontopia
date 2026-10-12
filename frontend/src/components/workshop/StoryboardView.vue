<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import gsap from 'gsap';
import { kcDisplayName } from '@/data/kcNames';
import BilibiliVideoCards from '@/components/video/BilibiliVideoCards.vue';
import type { StoryboardContent, StoryboardScene } from '@/api/dto/Workshop';

const props = defineProps<{ content: StoryboardContent }>();
const currentScene = ref(0);
const isPlaying = ref(false);
const showSubtitle = ref(true);
const playbackSpeed = ref(1);
const sceneImageUrl = ref('');
const sceneImageError = ref(false);
const stageRef = ref<HTMLElement | null>(null);
const sceneRefs = ref<HTMLLIElement[]>([]);
const scenes = computed(() => props.content.scenes);
const biliKeyword = computed<string>(() => {
  const codes = Array.from(new Set(scenes.value.flatMap((scene) => scene.kc_codes)));
  return `Python ${codes.slice(0, 2).map(kcDisplayName).join(' ')}`.trim();
});
const activeScene = computed<StoryboardScene | undefined>(
  () => scenes.value[currentScene.value],
);
const progressPercent = computed(() =>
  scenes.value.length > 0 ? ((currentScene.value + 1) / scenes.value.length) * 100 : 0,
);
const speedOptions: readonly number[] = [0.5, 1, 1.5, 2];

function setSceneRef(element: unknown, index: number): void {
  if (element instanceof HTMLElement) sceneRefs.value[index] = element as HTMLLIElement;
}

function loadSceneImage(scene: StoryboardScene): void {
  sceneImageError.value = false;
  sceneImageUrl.value = scene.evidence_page_id
    ? `/api/course/pages/${scene.evidence_page_id}/image`
    : '';
}

function onImageError(): void {
  sceneImageError.value = true;
}

function animateScene(index: number): void {
  const element = sceneRefs.value[index];
  if (!element) return;
  gsap.fromTo(
    element,
    { opacity: 0, y: 30, scale: 0.95 },
    { opacity: 1, y: 0, scale: 1, duration: 0.5, ease: 'power2.out' },
  );
  const badge = element.querySelector('.badge');
  if (badge) gsap.fromTo(badge, { scale: 0 }, { scale: 1, duration: 0.3, delay: 0.2 });
  const narration = element.querySelector('.narration');
  if (narration) gsap.fromTo(narration, { opacity: 0, x: -20 }, { opacity: 1, x: 0, duration: 0.4 });
  const visual = element.querySelector('.visual');
  if (visual) gsap.fromTo(visual, { opacity: 0, x: 20 }, { opacity: 1, x: 0, duration: 0.4 });
}

function animateStage(): void {
  const content = stageRef.value?.querySelector('.stage-content');
  if (content) gsap.fromTo(content, { opacity: 0 }, { opacity: 1, duration: 0.4, ease: 'power2.out' });
}

let playbackTimer: number | null = null;
let resolvePlaybackWait: (() => void) | null = null;

function waitForScene(durationSeconds: number): Promise<void> {
  return new Promise((resolve) => {
    resolvePlaybackWait = resolve;
    playbackTimer = window.setTimeout(() => {
      playbackTimer = null;
      resolvePlaybackWait = null;
      resolve();
    }, (durationSeconds * 1000) / playbackSpeed.value);
  });
}

async function playScene(index: number): Promise<void> {
  currentScene.value = index;
  const scene = scenes.value[index];
  if (!scene) return;
  loadSceneImage(scene);
  animateScene(index);
  animateStage();
  if (isPlaying.value) await waitForScene(scene.duration_seconds);
}

async function playAll(): Promise<void> {
  if (isPlaying.value) return;
  isPlaying.value = true;
  for (let index = currentScene.value; index < scenes.value.length; index += 1) {
    if (!isPlaying.value) break;
    await playScene(index);
  }
  isPlaying.value = false;
}

function pause(): void {
  isPlaying.value = false;
  if (playbackTimer !== null) {
    window.clearTimeout(playbackTimer);
    playbackTimer = null;
    resolvePlaybackWait?.();
    resolvePlaybackWait = null;
  }
}

function togglePlay(): void {
  if (isPlaying.value) pause();
  else void playAll();
}

function goToScene(index: number): void {
  pause();
  currentScene.value = index;
  const scene = scenes.value[index];
  if (scene) {
    loadSceneImage(scene);
    animateScene(index);
    animateStage();
  }
}

function prevScene(): void {
  if (currentScene.value > 0) goToScene(currentScene.value - 1);
}

function nextScene(): void {
  if (currentScene.value < scenes.value.length - 1) goToScene(currentScene.value + 1);
}

onMounted(async () => {
  await nextTick();
  const first = scenes.value[0];
  if (first) {
    loadSceneImage(first);
    animateScene(0);
  }
});

onBeforeUnmount(pause);
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
      <BilibiliVideoCards
        class="storyboard-bili"
        :keyword="biliKeyword"
        :limit="6"
        heading="B站精选讲解"
        data-testid="storyboard-bili"
      />
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
            :title="isPlaying ? '暂停静音播放' : '静音播放场景'"
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
          <small>关联知识点: {{ scene.kc_codes.map(kcDisplayName).join('、') || '未标注' }}</small>
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
  color: var(--pyt-color-text-hint);
  font-size: 14px;
}

/* ── 剧场舞台 ── */
.theater-stage {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  background: var(--pyt-color-terminal-bg);
  border-radius: var(--pyt-radius-lg);
  border: 3px solid var(--pyt-color-text-hint);
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
.stage-video {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #1a1208;
}
.stage-loading {
  position: absolute;
  top: 10px;
  left: 50%;
  transform: translateX(-50%);
  padding: 4px 12px;
  background: rgba(46, 36, 23, 0.85);
  color: #f9e7bc;
  border-radius: 999px;
  font-size: 12px;
  font-family: var(--pyt-font-pixel);
}
.degraded-badge {
  position: absolute;
  top: 10px;
  right: 10px;
  padding: 3px 10px;
  background: #b39554;
  color: var(--pyt-color-bg-elevated);
  border-radius: 999px;
  font-size: 11px;
  font-family: var(--pyt-font-pixel);
}

/* ── 多模态工具条 ── */
.multimodal-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.mm-btn {
  padding: 6px 14px;
  border-radius: 6px;
  border: 2px solid var(--pyt-color-text-hint);
  background: #f9e7bc;
  color: #3a2a14;
  cursor: pointer;
  font-size: 13px;
  font-family: var(--pyt-font-pixel);
  transition: background 0.15s;
}
.mm-btn:hover:not(:disabled) { background: #f1d896; }
.mm-btn:disabled { opacity: 0.5; cursor: default; }
.mm-btn.active {
  background: #4f8a3e;
  color: var(--pyt-color-bg-elevated);
  border-color: #2b6021;
}
.compose-btn {
  background: #d4af37;
  border-color: #8a6a12;
  color: var(--pyt-color-shadow);
}
.compose-btn:hover:not(:disabled) { background: #e6c14a; }

/* ── 合成进度 / 降级提示 ── */
.compose-studio {
  margin-top: 2px;
}
.compose-error {
  margin: 0;
  padding: 8px 12px;
  background: #fbe2dc;
  border: 2px solid #d98c76;
  border-radius: var(--pyt-radius-md);
  color: #8a3a26;
  font-size: 13px;
  line-height: 1.5;
  font-family: var(--pyt-font-pixel);
}
.stage-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--pyt-color-border);
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

/* ── 字幕浮层 ── */
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
  font-family: var(--pyt-font-pixel);
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

/* ── 播放控制条 ── */
.control-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: #fffbe9;
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
}
.playback-controls {
  display: flex;
  gap: 4px;
}
.ctrl-btn {
  padding: 6px 12px;
  border-radius: 6px;
  border: 2px solid var(--pyt-color-text-hint);
  background: #f9e7bc;
  color: #3a2a14;
  cursor: pointer;
  font-size: 14px;
  font-family: var(--pyt-font-pixel);
  transition: background 0.15s;
  line-height: 1;
}
.ctrl-btn:hover:not(:disabled) { background: #f1d896; }
.ctrl-btn:disabled { opacity: 0.4; cursor: default; }
.play-btn {
  background: #4f8a3e;
  color: var(--pyt-color-bg-elevated);
  border-color: #2b6021;
  min-width: 40px;
  text-align: center;
}
.play-btn:hover { background: #3d7230; }
.scene-counter {
  font-size: 13px;
  color: var(--pyt-color-text-hint);
  white-space: nowrap;
  font-family: var(--pyt-font-pixel);
}
.progress-track {
  flex: 1;
  height: 8px;
  background: #e6d4a5;
  border-radius: 4px;
  overflow: hidden;
  border: 1px solid var(--pyt-color-border);
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
  color: var(--pyt-color-bg-elevated);
  border-color: #2b6021;
}
.speed-select {
  padding: 4px 6px;
  border-radius: 4px;
  border: 2px solid var(--pyt-color-text-hint);
  background: #f9e7bc;
  color: #3a2a14;
  font-size: 12px;
  font-family: var(--pyt-font-pixel);
  cursor: pointer;
}

/* ── 分镜导航 ── */
.scene-nav {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.nav-dot {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 2px solid var(--pyt-color-border);
  background: #fffbe9;
  font-size: 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  font-family: var(--pyt-font-pixel);
}
.nav-dot.active {
  background: #4f8a3e;
  color: var(--pyt-color-bg-elevated);
  border-color: #2b6021;
  transform: scale(1.15);
}

/* ── 分镜卡片 ── */
.storyboard {
  list-style: none;
  padding: 0;
  display: grid;
  gap: 12px;
}
.scene-card {
  background: #fffbe9;
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
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
  color: var(--pyt-color-bg-elevated);
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
  color: var(--pyt-color-text-hint);
  font-size: 12px;
}
</style>
