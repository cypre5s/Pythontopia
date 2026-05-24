<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue';
import gsap from 'gsap';
import type { StoryboardContent } from '@/api/dto/Workshop';

const props = defineProps<{
  content: StoryboardContent;
}>();

const activeIndex = ref(0);
const playing = ref(false);
const sceneRefs = ref<HTMLLIElement[]>([]);

function setSceneRef(el: unknown, i: number): void {
  if (el instanceof HTMLElement) sceneRefs.value[i] = el as HTMLLIElement;
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

async function playAll(): Promise<void> {
  if (playing.value) return;
  playing.value = true;
  for (let i = 0; i < props.content.scenes.length; i++) {
    activeIndex.value = i;
    animateScene(i);
    const dur = props.content.scenes[i]?.duration_seconds ?? 3;
    await new Promise<void>((r) => setTimeout(r, Math.min(dur, 5) * 1000));
    if (!playing.value) break;
  }
  playing.value = false;
}

function stopPlay(): void {
  playing.value = false;
}

function goToScene(i: number): void {
  stopPlay();
  activeIndex.value = i;
  animateScene(i);
}

onMounted(async () => {
  await nextTick();
  animateScene(0);
});

watch(() => props.content.scenes, async () => {
  activeIndex.value = 0;
  await nextTick();
  animateScene(0);
});
</script>

<template>
  <div class="storyboard-wrapper">
    <div class="storyboard-controls">
      <button
        type="button"
        class="play-btn"
        :disabled="playing"
        @click="playAll"
      >
        {{ playing ? '播放中…' : '▶ 自动播放' }}
      </button>
      <button
        v-if="playing"
        type="button"
        class="stop-btn"
        @click="stopPlay"
      >
        ■ 停止
      </button>
      <span class="scene-counter">
        {{ activeIndex + 1 }} / {{ props.content.scenes.length }} 幕
      </span>
    </div>
    <div class="scene-nav">
      <button
        v-for="(scene, i) in props.content.scenes"
        :key="i"
        type="button"
        :class="['nav-dot', { active: i === activeIndex }]"
        :title="scene.title"
        @click="goToScene(i)"
      >
        {{ i + 1 }}
      </button>
    </div>
    <ol class="storyboard">
      <li
        v-for="(scene, index) in props.content.scenes"
        :key="index"
        :ref="(el) => setSceneRef(el, index)"
        :class="['scene-card', { current: index === activeIndex, dimmed: index !== activeIndex }]"
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
  </div>
</template>

<style scoped>
.storyboard-wrapper {
  display: grid;
  gap: 12px;
}
.storyboard-controls {
  display: flex;
  align-items: center;
  gap: 10px;
}
.play-btn,
.stop-btn {
  padding: 6px 14px;
  border-radius: 6px;
  border: 2px solid #6e4f1d;
  cursor: pointer;
  font-size: 13px;
}
.play-btn {
  background: #4f8a3e;
  color: #fff7e6;
  border-color: #2b6021;
}
.play-btn:disabled {
  opacity: 0.6;
  cursor: default;
}
.stop-btn {
  background: #c14a2f;
  color: #fff7e6;
  border-color: #8b2e1a;
}
.scene-counter {
  font-size: 13px;
  color: #6e4f1d;
  margin-left: auto;
}
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
}
.nav-dot.active {
  background: #4f8a3e;
  color: #fff7e6;
  border-color: #2b6021;
  transform: scale(1.15);
}
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
.scene-card.dimmed {
  opacity: 0.5;
}
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
