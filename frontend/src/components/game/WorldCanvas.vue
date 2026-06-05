<script setup lang="ts">
// 全屏背景式画布，承载唯一的 Phaser.Game 实例。关键契约：
//   - 单 Phaser.Game 实例（AGENTS.md 硬约束），统一经 phaserGame.ts 单例创建；
//   - ResizeObserver 把容器尺寸推给 game.scale.resize，配合 RESIZE 模式保持「画布 = 视口」；
//   - canvas 以最近邻像素缩放（image-rendering: pixelated）保持像素风清晰边缘。
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref } from 'vue';
import {
  createPhaserGame,
  getPhaserGame,
  pausePhaserGame,
  resumePhaserGame,
  type PhaserGameHandle,
  type PhaserSceneConfig,
} from '@game/adapters/phaserGame';
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
let listenersBound = false;

const progressPercent = computed(() => Math.min(100, Math.max(0, Math.round(progress.value * 100))));

function bindListeners(): void {
  if (listenersBound) return;
  listenersBound = true;
  window.addEventListener('pythontopia:load-progress', onProgress);
  window.addEventListener('pythontopia:world-travel', onWorldTravel);
}

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

function hideLoadOverlay(): void {
  overlayVisible.value = false;
  overlayFading.value = true;
}

async function ensureGameMounted(): Promise<void> {
  bindListeners();
  const world = useWorldStore();
  if (!world.bootstrapped) {
    await world.bootstrap();
  }
  const pendingMapKey = applyPendingTravel();
  if (!root.value) return;

  const existingGame = getPhaserGame();
  if (existingGame) {
    if (pendingMapKey) {
      try {
        existingGame.scene.start('WorldScene', { mapKey: pendingMapKey });
      } catch {
        // scene manager 尚未就绪
      }
    }
    hideLoadOverlay();
    scheduleResize();
    resumePhaserGame();
    return;
  }

  const scenes: PhaserSceneConfig[] = [BootScene, WorldScene];
  if (pendingMapKey) {
    try {
      getPhaserGame()?.scene.start('WorldScene', { mapKey: pendingMapKey });
    } catch {
      // createPhaserGame 会兜底
    }
  }
  game = createPhaserGame(root.value, scenes);
  if (typeof ResizeObserver === 'function') {
    resizeObserver = new ResizeObserver(() => scheduleResize());
    resizeObserver.observe(root.value);
  }
  window.addEventListener('resize', scheduleResize);
  window.addEventListener('orientationchange', scheduleResize);
  document.addEventListener('fullscreenchange', scheduleResize);
  scheduleResize();
  window.setTimeout(scheduleResize, 150);
  window.setTimeout(scheduleResize, 600);
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

onMounted(() => {
  void ensureGameMounted();
});

onActivated(() => {
  onWorldTravel();
  if (getPhaserGame()) {
    hideLoadOverlay();
    scheduleResize();
    resumePhaserGame();
  } else {
    void ensureGameMounted();
  }
});

onDeactivated(() => {
  pausePhaserGame();
});

onBeforeUnmount(() => {
  if (listenersBound) {
    window.removeEventListener('pythontopia:load-progress', onProgress);
    window.removeEventListener('pythontopia:world-travel', onWorldTravel);
    listenersBound = false;
  }
  window.removeEventListener('resize', scheduleResize);
  window.removeEventListener('orientationchange', scheduleResize);
  document.removeEventListener('fullscreenchange', scheduleResize);
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
  color: var(--pyt-color-bg-elevated);
  font-family: var(--pyt-font-pixel);
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
  border-radius: var(--pyt-radius-lg);
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.55), inset 0 0 0 1px rgba(255, 247, 230, 0.06);
}
.world-load-title {
  margin: 0;
  font-size: 22px;
  letter-spacing: 1px;
  color: var(--pyt-color-gold-bright);
  text-shadow: 2px 2px 0 var(--pyt-color-shadow);
  font-family: var(--pyt-font-pixel);
}
.world-load-bar {
  position: relative;
  width: 280px;
  height: 14px;
  border: 2px solid var(--pyt-color-text-hint);
  background: var(--pyt-color-shadow);
  border-radius: 4px;
  overflow: hidden;
}
.world-load-bar__fill {
  height: 100%;
  background: linear-gradient(90deg, #f4b400 0%, var(--pyt-color-gold-bright) 100%);
  transition: width 180ms linear;
}
.world-load-percent {
  margin: 0;
  font-size: 14px;
  color: var(--pyt-color-bg-elevated);
  font-weight: 700;
}
.world-load-hint {
  margin: 0;
  font-size: 12px;
  color: var(--pyt-color-warning-bg);
  opacity: 0.82;
}
</style>
