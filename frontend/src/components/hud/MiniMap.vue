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

interface Props {
  width?: number;
  height?: number;
  tilemap?: MiniMapTilemap | null;
  playerX: number;
  playerY: number;
  npcs?: NpcMarker[];
}

const props = withDefaults(defineProps<Props>(), {
  width: 200,
  height: 200,
  tilemap: null,
  npcs: () => [],
});

const canvasWidth = computed<number>(() => props.width);
const canvasHeight = computed<number>(() => props.height);

const GROUND_COLOR = '#b8d99a';
const COLLISION_COLOR = '#3a352a';
const BG_COLOR = '#2a2118';
const PLAYER_COLOR = '#e63946';
const NPC_COLOR = '#ffd166';
const POINT_SIZE = 3;

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
  color: #ffd166;
  font-family: 'PythontopiaPixel', 'PingFang SC', 'Noto Sans CJK SC', monospace;
  font-size: 13px;
  letter-spacing: 0.5px;
}
</style>
