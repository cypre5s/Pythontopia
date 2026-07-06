<script setup lang="ts">
/**
 * 代码剧场舞台画布：把「傀儡走代码」的 Phaser 演出挂到剧场页本身。
 *
 * 招牌动画此前只在已有 Phaser 单例（从 /world 进入）时才出现，独立访问 /code-theater 看不到。
 * 本组件在剧场页内独占一个 Phaser 单例并把画布挂进本页容器：进页前若已有单例（如 /world 残留）
 * 先销毁，离页时再销毁——/world 重新进入会经自身 onActivated 兜底重建，互不打架（AGENTS 单 Game 约束）。
 * 真实执行与判分仍走 pipeline / 服务端判题，这里只负责把执行过程演出来。
 */
import { onBeforeUnmount, onMounted, ref } from 'vue';
import {
  createPhaserGame,
  destroyPhaserGame,
  getPhaserGame,
  type PhaserGameHandle,
} from '@game/adapters/phaserGame';
import { ensureCodeTheaterSceneStarted } from '@game/scenes/CodeTheaterScene';

const root = ref<HTMLElement | null>(null);
let handle: PhaserGameHandle | null = null;
let resizeObserver: ResizeObserver | null = null;
let resizeRaf: number | null = null;

function applyCanvasSize(): void {
  const game = getPhaserGame();
  const el = root.value;
  if (!game || !el) return;
  const rect = el.getBoundingClientRect();
  const width = Math.max(320, Math.floor(rect.width));
  const height = Math.max(240, Math.floor(rect.height));
  const scale = game.scale as unknown as { resize?: (w: number, h: number) => void };
  scale.resize?.(width, height);
}

function scheduleResize(): void {
  if (resizeRaf !== null) return;
  resizeRaf = window.requestAnimationFrame(() => {
    resizeRaf = null;
    applyCanvasSize();
  });
}

onMounted(() => {
  if (!root.value) return;
  // 画布父节点必须是本页容器：复用残留单例会把画布渲染到别处（即旧 bug），故先销毁再重建本页专属实例。
  if (getPhaserGame()) destroyPhaserGame(true);
  handle = createPhaserGame(root.value, []);
  ensureCodeTheaterSceneStarted(
    getPhaserGame() as unknown as Parameters<typeof ensureCodeTheaterSceneStarted>[0],
  );
  if (typeof ResizeObserver === 'function') {
    resizeObserver = new ResizeObserver(() => scheduleResize());
    resizeObserver.observe(root.value);
  }
  window.addEventListener('resize', scheduleResize);
  scheduleResize();
  window.setTimeout(scheduleResize, 150);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', scheduleResize);
  if (resizeRaf !== null) {
    window.cancelAnimationFrame(resizeRaf);
    resizeRaf = null;
  }
  resizeObserver?.disconnect();
  resizeObserver = null;
  handle?.destroy(true);
  handle = null;
});
</script>

<template>
  <div
    ref="root"
    class="theater-canvas"
    data-testid="theater-stage-canvas"
  />
</template>

<style scoped>
.theater-canvas {
  width: 100%;
  height: 100%;
}

.theater-canvas :deep(canvas) {
  display: block;
  width: 100% !important;
  height: 100% !important;
  image-rendering: pixelated;
  image-rendering: crisp-edges;
}
</style>
