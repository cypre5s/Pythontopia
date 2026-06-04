<script setup lang="ts">
/**
 * MiniMap.vue —— W3 HUD 小地图组件。
 *
 * 设计要点：
 *   1. 组件为纯展示组件，不直接依赖 store / Phaser，数据由 Page 通过 props 喂入，
 *      保证跨场景（town / farm / mine / campus / port）调出时不与 Phaser 渲染抢资源。
 *   2. 用 <canvas> 一次性绘制整张缩略图：ground 浅绿、collision 深灰、玩家红点、NPC 黄点，
 *      仅在 mount 与相关 props 变化时重画，不参与 requestAnimationFrame 主循环。
 *   3. tilemap 缺失（例如尚未 bootstrap）时降级显示「无地图数据」占位文本，避免空白画布。
 *   4. 像素风：image-rendering: pixelated + 木色边框 + 半透明深色背景，与 OperationHints / ChapterNavPanel 视觉一致。
 *   5. R-13 增强：可选 mapName/exits props 显示当前地图名与出口方向箭头（4 向：north/south/east/west）；
 *      所有新 prop 都是 optional，保持对现有 spec 完全向后兼容。
 */
import { computed, onMounted, ref, watch } from 'vue';

interface MiniMapTilemap {
  width: number;
  height: number;
  ground?: number[][];
  collision?: boolean[][];
}

interface NpcMarker {
  id: string;
  x: number;
  y: number;
}

export type MinimapExitSide = 'north' | 'south' | 'east' | 'west';

export interface MinimapExitMarker {
  side: MinimapExitSide;
  /** 目标地图 key，用于 tooltip 与 a11y 标签 */
  toMap: string;
  /** 中文显示名（可选）。例如「知识农场」 */
  label?: string;
}

interface Props {
  width?: number;
  height?: number;
  tilemap?: MiniMapTilemap | null;
  playerX: number;
  playerY: number;
  npcs?: NpcMarker[];
  /** R-13 增强：当前地图中文显示名，例如「Pythontopia 小镇」 */
  mapName?: string;
  /** R-13 增强：边缘出口方向列表，渲染为 4 边箭头 */
  exits?: MinimapExitMarker[];
  /** 玩家当前朝向（UP/DOWN/LEFT/RIGHT），用于高亮对应方向的 exit arrow */
  playerFacing?: string;
}

const props = withDefaults(defineProps<Props>(), {
  width: 200,
  height: 200,
  tilemap: null,
  npcs: () => [],
  mapName: '',
  exits: () => [],
  playerFacing: '',
});

const canvasWidth = computed<number>(() => props.width);
const canvasHeight = computed<number>(() => props.height);

const GROUND_COLOR = '#b8d99a';
const COLLISION_COLOR = '#3a352a';
const BG_COLOR = '#2a2118';
const PLAYER_COLOR = '#e63946';
const NPC_COLOR = '#ffd166';
const POINT_SIZE = 3;
// R-13 迭代 17：玩家点脉动半径（最大半径，用 sin(time) 调制）
const PLAYER_PULSE_MAX_RADIUS = 6;

const canvasRef = ref<HTMLCanvasElement | null>(null);

const hasTilemap = computed<boolean>(() => props.tilemap != null);

function redraw(): void {
  const canvas = canvasRef.value;
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  if (!ctx) return;
  const tilemap = props.tilemap;
  if (!tilemap) return;

  const { width, height } = props;
  const tilesW = Math.max(tilemap.width, 1);
  const tilesH = Math.max(tilemap.height, 1);
  const scaleX = width / tilesW;
  const scaleY = height / tilesH;

  ctx.fillStyle = BG_COLOR;
  ctx.fillRect(0, 0, width, height);

  const ground = tilemap.ground;
  if (ground) {
    ctx.fillStyle = GROUND_COLOR;
    for (let ty = 0; ty < tilesH; ty += 1) {
      const row = ground[ty];
      if (!row) continue;
      for (let tx = 0; tx < tilesW; tx += 1) {
        const cell = row[tx];
        if (cell !== undefined && cell > 0) {
          ctx.fillRect(
            Math.floor(tx * scaleX),
            Math.floor(ty * scaleY),
            Math.ceil(scaleX),
            Math.ceil(scaleY),
          );
        }
      }
    }
  }

  const collision = tilemap.collision;
  if (collision) {
    ctx.fillStyle = COLLISION_COLOR;
    for (let ty = 0; ty < tilesH; ty += 1) {
      const row = collision[ty];
      if (!row) continue;
      for (let tx = 0; tx < tilesW; tx += 1) {
        if (row[tx]) {
          ctx.fillRect(
            Math.floor(tx * scaleX),
            Math.floor(ty * scaleY),
            Math.ceil(scaleX),
            Math.ceil(scaleY),
          );
        }
      }
    }
  }

  ctx.fillStyle = NPC_COLOR;
  for (const npc of props.npcs) {
    const nx = Math.floor(npc.x * scaleX);
    const ny = Math.floor(npc.y * scaleY);
    ctx.fillRect(nx, ny, POINT_SIZE, POINT_SIZE);
  }

  ctx.fillStyle = PLAYER_COLOR;
  const px = Math.floor(props.playerX * scaleX);
  const py = Math.floor(props.playerY * scaleY);
  ctx.fillRect(px, py, POINT_SIZE, POINT_SIZE);

  // R-13 迭代 17：玩家点外圈脉动光晕。每次 redraw 时按 performance.now() 计算
  // 半径，不接 rAF 主循环（避免空闲时 CPU 占用），仅在 player/tilemap/npcs 变化
  // 触发 watch 时重新计算。
  // jsdom 的 mock canvas context 可能只实现 fillRect/fillStyle，arc/stroke 缺失时
  // 静默跳过，确保 MiniMap.spec 既有用例（只断言 fillRect）不被破坏。
  const ctxWithArc = ctx as unknown as {
    arc?: (x: number, y: number, r: number, a0: number, a1: number) => void;
    beginPath?: () => void;
    stroke?: () => void;
    strokeStyle?: string;
    lineWidth?: number;
  };
  if (
    typeof globalThis !== 'undefined' &&
    'performance' in globalThis &&
    typeof ctxWithArc.arc === 'function' &&
    typeof ctxWithArc.beginPath === 'function' &&
    typeof ctxWithArc.stroke === 'function'
  ) {
    const t = (globalThis.performance.now() % 1600) / 1600;
    const pulse = (Math.sin(t * Math.PI * 2) + 1) / 2;
    const radius = POINT_SIZE + pulse * (PLAYER_PULSE_MAX_RADIUS - POINT_SIZE);
    ctxWithArc.beginPath();
    ctxWithArc.strokeStyle = 'rgba(230, 57, 70, 0.45)';
    ctxWithArc.lineWidth = 1;
    ctxWithArc.arc(px + POINT_SIZE / 2, py + POINT_SIZE / 2, radius, 0, Math.PI * 2);
    ctxWithArc.stroke();
  }
}

function exitArrow(side: MinimapExitSide): string {
  if (side === 'north') return '▲';
  if (side === 'south') return '▼';
  if (side === 'east') return '▶';
  return '◀';
}

const SIDE_TO_DIRECTION: Record<MinimapExitSide, string> = {
  north: 'UP',
  south: 'DOWN',
  east: 'RIGHT',
  west: 'LEFT',
};

function isFacingExit(side: MinimapExitSide): boolean {
  return !!props.playerFacing && SIDE_TO_DIRECTION[side] === props.playerFacing;
}

onMounted(() => {
  redraw();
});

watch(
  () => [
    props.playerX,
    props.playerY,
    props.width,
    props.height,
    props.tilemap,
    props.npcs,
  ],
  () => {
    redraw();
  },
  { deep: true },
);
</script>

<template>
  <div
    class="mini-map"
    :style="{ width: `${canvasWidth}px`, height: `${canvasHeight}px` }"
    data-testid="mini-map"
  >
    <div
      v-if="mapName"
      class="mini-map__title"
      data-testid="mini-map-title"
    >
      {{ mapName }}
    </div>
    <canvas
      v-if="hasTilemap"
      ref="canvasRef"
      :width="canvasWidth"
      :height="canvasHeight"
      class="mini-map__canvas"
      data-testid="mini-map-canvas"
    />
    <div
      v-else
      class="mini-map__empty"
      data-testid="mini-map-empty"
    >
      无地图数据
    </div>
    <template v-if="(exits ?? []).length">
      <span
        v-for="exit in (exits ?? [])"
        :key="`${exit.side}:${exit.toMap}`"
        :class="['mini-map__exit', `mini-map__exit--${exit.side}`, { 'mini-map__exit--facing': isFacingExit(exit.side) }]"
        :data-testid="`mini-map-exit-${exit.side}`"
        :title="exit.label ? `${exit.label}（${exit.toMap}）` : exit.toMap"
        :aria-label="`出口：${exit.side}${exit.label ? `，目标 ${exit.label}` : ''}`"
      >
        {{ exitArrow(exit.side) }}
      </span>
    </template>
  </div>
</template>

<style scoped>
.mini-map {
  position: relative;
  background: rgba(28, 24, 16, 0.78);
  border: 2px solid #d4af37;
  border-radius: 6px;
  padding: 4px;
  box-sizing: content-box;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.35);
  image-rendering: pixelated;
  user-select: none;
}

.mini-map__canvas {
  display: block;
  image-rendering: pixelated;
  background: #2a2118;
}

.mini-map__empty {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--pyt-color-gold-bright);
  font-family: var(--pyt-font-pixel);
  font-size: 13px;
  letter-spacing: 0.5px;
}

.mini-map__title {
  position: absolute;
  top: -22px;
  left: 0;
  right: 0;
  text-align: center;
  font-family: var(--pyt-font-pixel);
  font-size: 12px;
  color: var(--pyt-color-bg-elevated);
  background: rgba(28, 24, 16, 0.78);
  border: 2px solid #d4af37;
  border-bottom: none;
  border-radius: 6px 6px 0 0;
  padding: 2px 6px;
  letter-spacing: 0.5px;
  pointer-events: none;
  text-shadow: 1px 1px 0 var(--pyt-color-shadow);
}

.mini-map__exit {
  position: absolute;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  color: var(--pyt-color-gold-bright);
  font-size: 12px;
  text-shadow: 1px 1px 0 var(--pyt-color-shadow);
  pointer-events: none;
  animation: minimap-exit-pulse 1600ms ease-in-out infinite;
}

.mini-map__exit--north {
  top: -2px;
  left: 50%;
  transform: translateX(-50%);
}

.mini-map__exit--south {
  bottom: -2px;
  left: 50%;
  transform: translateX(-50%);
}

.mini-map__exit--east {
  right: -2px;
  top: 50%;
  transform: translateY(-50%);
}

.mini-map__exit--west {
  left: -2px;
  top: 50%;
  transform: translateY(-50%);
}

.mini-map__exit--facing {
  color: var(--pyt-color-bg-elevated);
  font-size: 15px;
  animation: minimap-exit-pulse-facing 900ms ease-in-out infinite;
  filter: drop-shadow(0 0 4px rgba(255, 247, 230, 0.7));
}

@keyframes minimap-exit-pulse {
  0%, 100% { opacity: 0.6; }
  50% { opacity: 1; }
}

@keyframes minimap-exit-pulse-facing {
  0%, 100% { opacity: 0.7; }
  50% { opacity: 1; }
}

@media (prefers-reduced-motion: reduce) {
  .mini-map__exit {
    animation: none;
    opacity: 0.85;
  }
}
</style>
