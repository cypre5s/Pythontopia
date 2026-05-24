<script setup lang="ts">
// R-13：把 MiniMap.vue 与 useWorldMinimap composable 接到生产 WorldPage 右上角。
//
// 设计要点：
//   1. 自身只负责定位 + 接线，纯 DOM；不创建第二个 Phaser.Game 实例（minimap 走
//      fetch JSON，不复用 Phaser scene），符合 AGENTS.md「Phaser 单 Game 实例」硬约束。
//   2. fixed 右上角，安全离开顶部 HUD（top:16px 给 HudShell 顶部状态条预留 ≥60px）；
//      移动端缩到 120×120 + 半透明，避免遮挡画布关键区。
//   3. 支持折叠：点击标题切换 collapsed 状态，节省小屏空间；折叠状态下只显示 64×24
//      指示条 + 当前地图名，再点击展开。
//   4. 不订阅任何 Phaser tween：完全数据驱动 + reduce-motion 友好。
//   5. 接管「M」快捷键打开/关闭 minimap（与 HudShell 的 map quickslot 互补）。

import { computed, onMounted, onUnmounted, ref } from 'vue';
import MiniMap from '@/components/hud/MiniMap.vue';
import { useWorldMinimap } from '@/composables/useWorldMinimap';
import { useHudStore } from '@stores/hud';
import { playUiClick } from '@/composables/useStardewFx';

const { data, playerX, playerY, loading } = useWorldMinimap();
const hud = useHudStore();

const collapsed = ref(false);

const visible = computed(() => hud.activeSlot !== 'inventory' && hud.activeSlot !== 'skill');

const sizePx = computed(() => {
  if (typeof window === 'undefined') return 180;
  return window.innerWidth < 640 ? 120 : 180;
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
    class="world-minimap-hud"
    :class="{ 'world-minimap-hud--collapsed': collapsed }"
    data-testid="world-minimap-hud"
    :aria-label="data ? `小地图：${data.mapName}` : '小地图'"
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
      />
    </div>
  </aside>
</template>

<style scoped>
.world-minimap-hud {
  position: fixed;
  top: 72px;
  right: 16px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  z-index: 90;
  pointer-events: auto;
  filter: drop-shadow(0 4px 8px rgba(0, 0, 0, 0.35));
}

.world-minimap-hud--collapsed .world-minimap-hud__body {
  display: none;
}

.world-minimap-hud__handle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  background: rgba(28, 24, 16, 0.86);
  border: 2px solid #d4af37;
  border-radius: 4px;
  color: #fff7e6;
  font-family: 'PythontopiaPixel', 'PingFang SC', monospace;
  font-size: 11px;
  cursor: pointer;
  transition: background 120ms, border-color 120ms;
}

.world-minimap-hud__handle:hover {
  background: rgba(60, 40, 12, 0.92);
  border-color: #f1c453;
}

.world-minimap-hud__handle:focus-visible {
  outline: 2px solid #f1c453;
  outline-offset: 1px;
}

.world-minimap-hud__handle-icon {
  font-size: 12px;
  color: #ffd166;
}

.world-minimap-hud__handle-label {
  white-space: nowrap;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.world-minimap-hud__body {
  position: relative;
  padding: 22px 4px 4px;
}

@media (max-width: 640px) {
  .world-minimap-hud {
    top: 60px;
    right: 8px;
    opacity: 0.88;
  }
  .world-minimap-hud__handle-label {
    max-width: 96px;
    font-size: 10px;
  }
}
</style>
