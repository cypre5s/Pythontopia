<script setup lang="ts">
/**
 * 无尽远征「像素远征塔」画布：把 rogueRun 的分支地图演成一座可见的像素秘境塔挂到远征页。
 *
 * 渐进增强 + 单 Game 约束（AGENTS.md）：进页前若已有 Phaser 单例（如 /world 残留）先销毁，
 * 在本页容器内独占一个 Phaser 单例并挂 ExpeditionScene；离页时销毁——/world 重新进入会经自身
 * onActivated 兜底重建，互不打架（同 TheaterStageCanvas）。绝不 new 第二个 Phaser.Game。
 *
 * 交互真源仍是 store：像素节点点击经 ExpeditionScene 桥回流 emit('select', id) → run.enterNode
 * （与 DOM 地图同一动作）。本组件只负责把 run 状态喂给场景并把点击抛回父级。
 */
import { onBeforeUnmount, onMounted, watch } from 'vue';
import { ref } from 'vue';
import {
  createPhaserGame,
  destroyPhaserGame,
  getPhaserGame,
  type PhaserGameHandle,
} from '@game/adapters/phaserGame';
import {
  ensureExpeditionSceneStarted,
  loadExpedition,
  stopExpeditionScene,
} from '@game/scenes/ExpeditionScene';
import type { RogueNode, RogueRunPhase } from '@/types/rogue';

const props = defineProps<{
  nodes: RogueNode[];
  layers: number;
  currentNodeId: string | null;
  phase: RogueRunPhase;
  hp: number;
  maxHp: number;
  score: number;
}>();

const emit = defineEmits<{ (e: 'select', id: string): void }>();

const root = ref<HTMLElement | null>(null);
let handle: PhaserGameHandle | null = null;
let resizeObserver: ResizeObserver | null = null;
let resizeRaf: number | null = null;
let mounted = false;

function syncScene(): void {
  if (!mounted) return;
  loadExpedition(
    { nodes: props.nodes, layers: props.layers },
    {
      currentNodeId: props.currentNodeId,
      phase: props.phase,
      hp: props.hp,
      maxHp: props.maxHp,
      score: props.score,
    },
    { onNodeClick: (id) => emit('select', id) },
  );
}

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
  // 画布父节点必须是本页容器：复用残留单例会把画布渲染到别处，故先销毁再重建本页专属实例。
  if (getPhaserGame()) destroyPhaserGame(true);
  handle = createPhaserGame(root.value, []);
  ensureExpeditionSceneStarted(
    getPhaserGame() as unknown as Parameters<typeof ensureExpeditionSceneStarted>[0],
  );
  mounted = true;
  syncScene();
  if (typeof ResizeObserver === 'function') {
    resizeObserver = new ResizeObserver(() => scheduleResize());
    resizeObserver.observe(root.value);
  }
  window.addEventListener('resize', scheduleResize);
  scheduleResize();
  window.setTimeout(scheduleResize, 150);
});

watch(
  () => [
    props.nodes.map((n) => `${n.id}:${n.state}`).join(','),
    props.currentNodeId,
    props.phase,
    props.hp,
    props.maxHp,
    props.score,
  ],
  () => syncScene(),
);

onBeforeUnmount(() => {
  mounted = false;
  window.removeEventListener('resize', scheduleResize);
  if (resizeRaf !== null) {
    window.cancelAnimationFrame(resizeRaf);
    resizeRaf = null;
  }
  resizeObserver?.disconnect();
  resizeObserver = null;
  stopExpeditionScene(getPhaserGame() as unknown as Parameters<typeof stopExpeditionScene>[0]);
  handle?.destroy(true);
  handle = null;
});
</script>

<template>
  <div
    ref="root"
    class="expedition-canvas"
    data-testid="expedition-canvas"
  />
</template>

<style scoped>
.expedition-canvas {
  width: 100%;
  height: 100%;
}

.expedition-canvas :deep(canvas) {
  display: block;
  width: 100% !important;
  height: 100% !important;
  image-rendering: pixelated;
  image-rendering: crisp-edges;
}
</style>
