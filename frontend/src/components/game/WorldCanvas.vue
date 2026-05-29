<script setup lang="ts">
// R-12 美化：WorldCanvas 改为全屏背景式画布。
//
// 设计要点：
//   1. 容器铺满整个 .world-page（position:absolute + inset:0），让地图占据
//      全部主视觉区域，避免右侧出现大块深绿色无内容空白；
//   2. 显式声明 image-rendering: pixelated + crisp-edges，让浏览器以最近邻
//      插值缩放像素，避免低分屏下出现模糊毛刺；
//   3. ResizeObserver 把容器盒子尺寸推给 Phaser.game.scale.resize，配合
//      phaserGame.ts 的 Phaser.Scale.RESIZE 模式实现「画布永远 = 视口」；
//   4. 仍保留 BootScene 加载条遮罩，避免黑屏；
//   5. 单 Phaser.Game 实例（AGENTS.md 硬约束）。
//
// 注意：不再使用 aspect-ratio + max-height，因为父容器已是 100vh，子层只需要
// 充满父层即可。
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { createPhaserGame, getPhaserGame, type PhaserGameHandle, type PhaserSceneConfig } from '@game/adapters/phaserGame';
import { BootScene } from '@game/scenes/BootScene';
import { WorldScene } from '@game/scenes/WorldScene';
import { useWorldStore } from '@stores/world';
import { useGameSettingsStore } from '@stores/gameSettings';

const gameSettings = useGameSettingsStore();

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

function applyCanvasSize(width: number, height: number): void {
  const phaser = getPhaserGame();
  if (!phaser) return;
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

function applyPendingTravel(): string | null {
  const world = useWorldStore();
  const pending = world.consumePendingTravel();
  if (!pending) return null;
  if (world.player) {
    world.player = {
      ...world.player,
      mapKey: pending.mapKey,
      tileX: pending.tile?.x ?? world.player.tileX,
      tileY: pending.tile?.y ?? world.player.tileY,
    };
  }
  return pending.mapKey;
}

function onWorldTravel(): void {
  const mapKey = applyPendingTravel();
  if (!mapKey) return;
  const existingGame = getPhaserGame();
  try {
    existingGame?.scene.start('WorldScene', { mapKey });
  } catch {
    // scene manager 尚未就绪时保留当前画面；下次挂载会从 store/player 兜底。
  }
}

onMounted(async () => {
  window.addEventListener('pythontopia:load-progress', onProgress);
  window.addEventListener('pythontopia:world-travel', onWorldTravel);
  const world = useWorldStore();
  if (!world.bootstrapped) {
    await world.bootstrap();
  }
  const pendingMapKey = applyPendingTravel();
  if (root.value) {
    const scenes: PhaserSceneConfig[] = [BootScene, WorldScene];
    const existingGame = getPhaserGame();
    if (existingGame && pendingMapKey) {
      try {
        existingGame.scene.start('WorldScene', { mapKey: pendingMapKey });
      } catch {
        // scene manager 尚未就绪，下面 createPhaserGame 会兜底
      }
    }
    game = createPhaserGame(root.value, scenes);
    if (typeof ResizeObserver === 'function') {
      resizeObserver = new ResizeObserver(() => scheduleResize());
      resizeObserver.observe(root.value);
    } else {
      window.addEventListener('resize', scheduleResize);
    }
    // 触发一次首屏尺寸同步，防止初始 width=960 与全屏父层不一致
    scheduleResize();
    setTimeout(() => {
      const canvas = root.value?.querySelector('canvas');
      if (canvas instanceof HTMLCanvasElement) {
        canvas.tabIndex = 0;
        canvas.style.outline = 'none';
        canvas.focus();
        canvas.addEventListener('click', () => canvas.focus());
      }
    }, 300);
  }
});

onBeforeUnmount(() => {
  window.removeEventListener('pythontopia:load-progress', onProgress);
  window.removeEventListener('pythontopia:world-travel', onWorldTravel);
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
    <div class="world-frame" :class="{ 'world-frame--smooth': !gameSettings.display.pixelPerfect }">
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
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: #1c2b18;
  z-index: 2;
}
.world-root {
  width: 100%;
  height: 100%;
}

/* 让所有 Phaser 渲染的 canvas 以及子图都按最近邻像素缩放，保持像素风清晰边缘 */
.world-frame :deep(canvas) {
  image-rendering: pixelated;
  image-rendering: crisp-edges;
  display: block;
  width: 100% !important;
  height: 100% !important;
}
.world-frame--smooth :deep(canvas) {
  image-rendering: auto;
}

.world-load-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(20, 28, 18, 0.92);
  color: #fff7e6;
  font-family: var(--pyt-font-pixel, 'Ark Pixel 12', monospace);
  opacity: 1;
  transition: opacity 600ms ease;
  z-index: 3;
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
  background: rgba(40, 28, 14, 0.94);
  border: 2px solid #d4af37;
  border-radius: 14px;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.55), inset 0 0 0 1px rgba(255, 247, 230, 0.06);
}
.world-load-title {
  margin: 0;
  font-size: 22px;
  letter-spacing: 1px;
  color: #ffd166;
  text-shadow: 2px 2px 0 #2a1a05;
  font-family: var(--pyt-font-pixel, 'Ark Pixel 12'), 'PingFang SC', monospace;
}
.world-load-bar {
  position: relative;
  width: 280px;
  height: 14px;
  border: 2px solid #6e4f1d;
  background: #2a1a05;
  border-radius: 4px;
  overflow: hidden;
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
  font-weight: 700;
}
.world-load-hint {
  margin: 0;
  font-size: 12px;
  color: #f5e2c1;
  opacity: 0.82;
}
</style>
