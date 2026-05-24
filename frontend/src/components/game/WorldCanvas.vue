<script setup lang="ts">
// M2：WorldCanvas 替代 GameCanvas / FieldCanvas，提供单一 Phaser 实例 + WorldScene 入口。
// 多挂载点引发的双实例 Bug 由 phaserGame.ts 的单例兜底，但 WorldPage 是 M2 之后唯一会
// 创建 Phaser.Game 的组件。
//
// M4：挂载前先调用 world.bootstrap() 一次性拉 world + player + crops + quests + mastery +
// profile + path + inventory + skills + currency + npc_states + calendar，避免 WorldScene
// 启动后再 N 次串行 GET。bootstrap 失败时仍会创建 Phaser 实例（WorldScene 会用 fallback 数据），
// 由 toast 提示原因。
//
// W4-FINAL-POLISH：把 BootScene 广播的 pythontopia:load-progress 渲染为像素风加载条，
// progress=1 时延迟 600ms 淡出，避免玩家看着黑屏怀疑卡死。
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { createPhaserGame, getPhaserGame, type PhaserGameHandle, type PhaserSceneConfig } from '@game/adapters/phaserGame';
import { BootScene } from '@game/scenes/BootScene';
import { WorldScene } from '@game/scenes/WorldScene';
import { useWorldStore } from '@stores/world';

const root = ref<HTMLElement | null>(null);
const progress = ref(0);
const overlayVisible = ref(true);
const overlayFading = ref(false);
let game: PhaserGameHandle | null = null;
let fadeTimer: number | null = null;
let resizeRaf: number | null = null;
let resizeObserver: ResizeObserver | null = null;

const progressPercent = computed(() => Math.min(100, Math.max(0, Math.round(progress.value * 100))));

function onProgress(event: Event): void {
  const custom = event as CustomEvent<{ progress: number }>;
  progress.value = custom.detail.progress;
  if (progress.value >= 1 && !overlayFading.value) {
    overlayFading.value = true;
    if (fadeTimer !== null) {
      window.clearTimeout(fadeTimer);
    }
    fadeTimer = window.setTimeout(() => {
      overlayVisible.value = false;
      fadeTimer = null;
    }, 600);
  }
}

/**
 * R-13：响应式触发 Phaser game.scale.resize，避免硬编码 960×640 在窄屏被截断。
 * 用 ResizeObserver 监听容器盒子尺寸，rAF 合并多次 resize 抖动。
 * 不重建 Phaser.Game，符合 AGENTS.md「单 Phaser.Game 实例」硬约束。
 */
function applyCanvasSize(width: number, height: number): void {
  const phaser = getPhaserGame();
  if (!phaser) return;
  // 进入 destroy 流程后 scale 可能已为 null；安全防护
  const scale = phaser.scale as unknown as {
    resize?: (w: number, h: number) => void;
  };
  if (typeof scale.resize === 'function') {
    scale.resize(width, height);
  }
}

function scheduleResize(): void {
  if (resizeRaf !== null) return;
  resizeRaf = window.requestAnimationFrame(() => {
    resizeRaf = null;
    const el = root.value;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    const w = Math.max(320, Math.floor(rect.width));
    const h = Math.max(240, Math.floor(rect.height));
    applyCanvasSize(w, h);
  });
}

onMounted(async () => {
  window.addEventListener('pythontopia:load-progress', onProgress);
  const world = useWorldStore();
  if (!world.bootstrapped) {
    await world.bootstrap();
  }
  if (root.value) {
    const scenes: PhaserSceneConfig[] = [BootScene, WorldScene];
    game = createPhaserGame(root.value, scenes);
    if (typeof ResizeObserver === 'function') {
      resizeObserver = new ResizeObserver(() => scheduleResize());
      resizeObserver.observe(root.value);
    } else {
      window.addEventListener('resize', scheduleResize);
    }
  }
});

onBeforeUnmount(() => {
  window.removeEventListener('pythontopia:load-progress', onProgress);
  window.removeEventListener('resize', scheduleResize);
  if (resizeRaf !== null) {
    window.cancelAnimationFrame(resizeRaf);
    resizeRaf = null;
  }
  if (resizeObserver) {
    resizeObserver.disconnect();
    resizeObserver = null;
  }
  if (fadeTimer !== null) {
    window.clearTimeout(fadeTimer);
    fadeTimer = null;
  }
  game?.destroy(true);
  game = null;
});
</script>

<template>
  <div class="world-frame">
    <div
      ref="root"
      class="world-root"
    />
    <div
      v-if="overlayVisible"
      class="world-load-overlay"
      :class="{ 'world-load-overlay--fading': overlayFading }"
      role="status"
      aria-live="polite"
    >
      <div class="world-load-card">
        <h2 class="world-load-title">
          Pythontopia 加载中…
        </h2>
        <div class="world-load-bar">
          <div
            class="world-load-bar__fill"
            :style="{ width: `${progressPercent}%` }"
          />
        </div>
        <p class="world-load-percent">
          {{ progressPercent }}%
        </p>
        <p class="world-load-hint">
          首次进入会预加载像素素材，请稍候
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.world-frame {
  position: relative;
  width: min(1100px, calc(100vw - 24px));
  /* 3:2 比例锁定，画布缩放时不会被截断 */
  aspect-ratio: 3 / 2;
  max-height: calc(100dvh - 96px);
  margin: 0 auto;
  border: 2px solid #6e4f1d;
  border-radius: 8px;
  overflow: hidden;
  background: #1c2b18;
}
.world-root {
  width: 100%;
  height: 100%;
}

@media (max-width: 1024px) {
  .world-frame {
    width: min(900px, calc(100vw - 16px));
    max-height: calc(100dvh - 72px);
  }
}

@media (max-width: 640px) {
  .world-frame {
    width: calc(100vw - 8px);
    border-radius: 4px;
    border-width: 1px;
    max-height: 60dvh;
  }
}
.world-load-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(28, 43, 24, 0.88);
  color: #fff7e6;
  font-family: var(--pyt-font-pixel, 'Ark Pixel 12', monospace);
  opacity: 1;
  transition: opacity 600ms ease;
}
.world-load-overlay--fading {
  opacity: 0;
  pointer-events: none;
}
.world-load-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 24px 32px;
  background: #4d3613;
  border: 3px solid #ffd166;
  box-shadow: 4px 4px 0 #2a1a05;
  image-rendering: pixelated;
}
.world-load-title {
  margin: 0;
  font-size: 22px;
  letter-spacing: 1px;
  color: #ffd166;
  text-shadow: 2px 2px 0 #2a1a05;
}
.world-load-bar {
  position: relative;
  width: 280px;
  height: 18px;
  border: 3px solid #6e4f1d;
  background: #2a1a05;
  image-rendering: pixelated;
}
.world-load-bar__fill {
  height: 100%;
  background: linear-gradient(90deg, #f4b400 0%, #ffd166 100%);
  transition: width 180ms linear;
}
.world-load-percent {
  margin: 0;
  font-size: 14px;
  color: #fff7e6;
}
.world-load-hint {
  margin: 0;
  font-size: 12px;
  color: #f5e2c1;
  opacity: 0.8;
}
</style>
