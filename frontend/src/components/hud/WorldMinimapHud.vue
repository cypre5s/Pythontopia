<script setup lang="ts">
// 把 MiniMap 与 useWorldMinimap 接到 WorldPage 右上角，仅负责定位 + 接线（纯 DOM）。
// 关键约束：不创建第二个 Phaser.Game 实例（minimap 走 fetch JSON，不复用 Phaser scene），
//   符合 AGENTS.md「Phaser 单 Game 实例」硬约束；不订阅 Phaser tween，数据驱动 + reduce-motion 友好。
// fixed 右上角避开顶部 HUD；移动端缩小 + 半透明避免遮挡画布；支持折叠节省小屏空间。
// Shift+M 折叠/展开（与 HudShell 的 M 地图 quickslot 互补）。

import { computed, onMounted, onUnmounted, ref } from 'vue';
import MiniMap from '@/components/hud/MiniMap.vue';
import { useWorldMinimap } from '@/composables/useWorldMinimap';
import { useDraggable } from '@/composables/useDraggable';
import { useHudStore } from '@stores/hud';
import { playUiClick } from '@/composables/useStardewFx';

const { data, playerX, playerY, playerFacing, loading } = useWorldMinimap();
const hud = useHudStore();

const rootRef = ref<HTMLElement | null>(null);
const { style: dragStyle, onPointerDown: onDragStart } = useDraggable(
  rootRef,
  'pyt_minimap_pos',
  { top: 96, right: 24 },
);

const collapsed = ref(false);

const visible = computed(() => hud.activeSlot !== 'skill');

const sizePx = computed(() => {
  if (typeof window === 'undefined') return 200;
  if (window.innerWidth < 640) return 120;
  if (window.innerWidth < 1280) return 160;
  return 200;
});

function toggleCollapsed(): void {
  playUiClick();
  collapsed.value = !collapsed.value;
}

function onKey(event: KeyboardEvent): void {
  // 与 HudShell 'M' 快捷键并存：HudShell 控制 activeSlot===map，本组件控制折叠。
  // 使用 Shift+M 折叠/展开 minimap，避免与 HudShell 的 M 冲突。
  if (event.key === 'M' && event.shiftKey) {
    toggleCollapsed();
    event.preventDefault();
  }
}

onMounted(() => {
  if (typeof window !== 'undefined') {
    window.addEventListener('keydown', onKey);
  }
});

onUnmounted(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('keydown', onKey);
  }
});
</script>

<template>
  <aside
    v-if="visible"
    ref="rootRef"
    class="world-minimap-hud"
    :class="{ 'world-minimap-hud--collapsed': collapsed }"
    :style="dragStyle"
    data-testid="world-minimap-hud"
    :aria-label="data ? `小地图：${data.mapName}` : '小地图'"
    @pointerdown="onDragStart"
  >
    <button
      type="button"
      class="world-minimap-hud__handle"
      data-testid="world-minimap-handle"
      :aria-expanded="!collapsed"
      :aria-label="collapsed ? '展开小地图（Shift+M）' : '折叠小地图（Shift+M）'"
      @click="toggleCollapsed"
    >
      <span
        class="world-minimap-hud__handle-icon"
        aria-hidden="true"
      >
        {{ collapsed ? '▾' : '▴' }}
      </span>
      <span class="world-minimap-hud__handle-label">
        {{ data ? data.mapName : (loading ? '加载小地图…' : '小地图') }}
      </span>
    </button>
    <div
      v-show="!collapsed"
      class="world-minimap-hud__body"
    >
      <MiniMap
        :width="sizePx"
        :height="sizePx"
        :tilemap="data?.tilemap ?? null"
        :player-x="playerX"
        :player-y="playerY"
        :npcs="data?.npcs ?? []"
        :map-name="''"
        :exits="data?.exits ?? []"
        :player-facing="playerFacing"
      />
      <p class="world-minimap-hud__hint">
        当前位置 · 按 M 查看大地图
      </p>
    </div>
  </aside>
</template>

<style scoped>
.world-minimap-hud {
  position: fixed;
  top: 36px;
  right: 12px;
  width: max-content;
  height: fit-content;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 6px;
  padding: 10px 10px 8px;
  background: var(--pyt-panel-bg);
  border: 3px solid var(--pyt-panel-border);
  border-radius: 2px;
  box-shadow: 4px 4px 0 var(--pyt-panel-border-dark), 0 8px 24px var(--pyt-panel-shadow);
  z-index: 90;
  pointer-events: auto;
  color: var(--pyt-text-primary);
  font-family: var(--pyt-font-pixel);
  image-rendering: pixelated;
  overflow: hidden;
}

.world-minimap-hud--collapsed {
  padding: 6px 10px;
  gap: 0;
}
.world-minimap-hud--collapsed .world-minimap-hud__body {
  display: none;
}

.world-minimap-hud__handle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 6px;
  background: none;
  border: none;
  color: var(--pyt-text-accent);
  font-family: inherit;
  font-size: 12px;
  cursor: pointer;
  border-radius: 2px;
  transition: background 140ms, color 140ms;
}
.world-minimap-hud__handle:hover {
  background: var(--pyt-btn-hover-bg);
  color: var(--pyt-text-primary);
}
.world-minimap-hud__handle:focus-visible {
  outline: 2px solid var(--pyt-text-gold);
  outline-offset: 1px;
}
.world-minimap-hud__handle-icon {
  font-size: 12px;
  color: var(--pyt-text-accent);
}
.world-minimap-hud__handle-label {
  white-space: nowrap;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-weight: 700;
  letter-spacing: 0.3px;
}

.world-minimap-hud__body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  overflow: hidden;
  max-height: 300px;
}

.world-minimap-hud__hint {
  margin: 0;
  font-size: 11px;
  color: var(--pyt-text-secondary);
  letter-spacing: 0.3px;
}

@media (max-width: 1280px) {
  .world-minimap-hud {
    top: 88px;
    right: 16px;
  }
}

@media (max-width: 1024px) {
  .world-minimap-hud {
    top: auto;
    bottom: 100px;
    right: 12px;
    opacity: 0.92;
  }
}

@media (max-width: 640px) {
  .world-minimap-hud {
    bottom: 96px;
    right: 8px;
    padding: 6px;
  }
  .world-minimap-hud__handle-label {
    max-width: 120px;
    font-size: 11px;
  }
  .world-minimap-hud__hint {
    display: none;
  }
}
</style>
