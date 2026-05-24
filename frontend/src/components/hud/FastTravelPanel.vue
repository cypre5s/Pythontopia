<script setup lang="ts">
// R-13 迭代 18：快速旅行面板（前端静态版）。
//
// 触发：玩家在 minimap 上点击「快速旅行」icon 或按快捷键 T。
// 行为：罗列 5 个核心地图，点击后通过 worldStore.player 直接更新坐标 + scene.restart。
// 注意：后端 FastTravelService 还没 Controller，本组件先在前端直接改 worldStore，
//       后续接入后改为调 `/api/world/fast-travel` 与服务端原子结算。
// 视觉：像素风浮窗，与 SettingsModal 共用模板，但用一组按钮替代滑块。

import { computed, onMounted, onUnmounted, ref } from 'vue';
import { FAST_TRAVEL_ROUTES, type FastTravelRoute } from '@/data/fastTravel';
import { useWorldStore } from '@stores/world';
import { useToastStore } from '@stores/toast';
import { playUiClick, playChime } from '@/composables/useStardewFx';

const world = useWorldStore();
const toast = useToastStore();

const visible = ref(false);

function open(): void {
  visible.value = true;
  playUiClick();
}
function close(): void {
  visible.value = false;
  playUiClick();
}

function onKey(event: KeyboardEvent): void {
  if (event.target instanceof HTMLElement) {
    const tag = event.target.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || event.target.isContentEditable) {
      return;
    }
  }
  if (event.key === 't' || event.key === 'T') {
    visible.value = !visible.value;
    event.preventDefault();
    if (visible.value) playUiClick();
  } else if (event.key === 'Escape' && visible.value) {
    visible.value = false;
    event.preventDefault();
  }
}

function travelTo(route: FastTravelRoute): void {
  playChime('dawn');
  if (world.player) {
    world.player = {
      ...world.player,
      mapKey: route.toMap,
      tileX: route.tileX,
      tileY: route.tileY,
    };
  }
  toast.show(`已传送到 ${route.label}`, 'info');
  visible.value = false;
}

function onExternalOpen(): void {
  visible.value = true;
}

onMounted(() => {
  if (typeof window !== 'undefined') {
    window.addEventListener('keydown', onKey);
    // R-13 迭代 19：HudShell 通过 CustomEvent 触发打开（避免组件互相 import）
    window.addEventListener('pythontopia:fast-travel-open', onExternalOpen);
  }
});

onUnmounted(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('keydown', onKey);
    window.removeEventListener('pythontopia:fast-travel-open', onExternalOpen);
  }
});

const currentMap = computed(() => world.player?.mapKey ?? null);

defineExpose({ open, close });
</script>

<template>
  <div
    v-if="visible"
    class="ft-mask"
    data-testid="fast-travel-panel"
    role="dialog"
    aria-modal="true"
    aria-label="快速旅行"
    @click.self="close"
  >
    <section class="ft-card">
      <header class="ft-card__head">
        <h2 class="ft-card__title">
          快速旅行
        </h2>
        <button
          type="button"
          class="ft-card__close"
          aria-label="关闭"
          @click="close"
        >
          ✕
        </button>
      </header>
      <p class="ft-card__hint">
        按 T 也可以打开此面板。已位于的地图会标记为当前。
      </p>
      <ul class="ft-card__list">
        <li
          v-for="route in FAST_TRAVEL_ROUTES"
          :key="route.key"
        >
          <button
            type="button"
            class="ft-card__item"
            :class="{ 'ft-card__item--current': currentMap === route.toMap }"
            :data-testid="`fast-travel-${route.key}`"
            :disabled="currentMap === route.toMap"
            @click="travelTo(route)"
          >
            <span class="ft-card__item-label">{{ route.label }}</span>
            <span class="ft-card__item-desc">{{ route.description }}</span>
            <span class="ft-card__item-cost">
              {{ route.costCoins > 0 ? `${route.costCoins} 金币` : '免费' }}
            </span>
          </button>
        </li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.ft-mask {
  position: fixed;
  inset: 0;
  background: rgba(8, 8, 12, 0.62);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 210;
}

.ft-card {
  width: min(440px, calc(100vw - 32px));
  background: rgba(28, 24, 16, 0.96);
  border: 3px solid #d4af37;
  border-radius: 10px;
  color: #fff7e6;
  font-family: 'PythontopiaPixel', 'PingFang SC', monospace;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5);
  padding: 16px 20px;
}

.ft-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 8px;
  border-bottom: 2px solid rgba(212, 175, 55, 0.4);
  margin-bottom: 10px;
}

.ft-card__title {
  margin: 0;
  font-size: 18px;
  color: #ffd166;
  letter-spacing: 1px;
  text-shadow: 1px 1px 0 #2a1a05;
}

.ft-card__close {
  background: transparent;
  border: 2px solid rgba(255, 247, 230, 0.4);
  color: #fff7e6;
  font-size: 14px;
  width: 30px;
  height: 30px;
  border-radius: 4px;
  cursor: pointer;
}

.ft-card__hint {
  margin: 0 0 10px;
  font-size: 12px;
  color: rgba(255, 247, 230, 0.7);
}

.ft-card__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 6px;
}

.ft-card__item {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 4px 12px;
  width: 100%;
  padding: 10px 12px;
  background: rgba(77, 54, 19, 0.45);
  border: 2px solid #d4af37;
  border-radius: 6px;
  color: #fff7e6;
  cursor: pointer;
  text-align: left;
  font-family: inherit;
  font-size: 13px;
  transition: background 120ms, transform 80ms;
}

.ft-card__item:hover:not([disabled]) {
  background: rgba(212, 175, 55, 0.28);
}

.ft-card__item[disabled] {
  opacity: 0.55;
  cursor: not-allowed;
}

.ft-card__item--current {
  border-color: #b8d99a;
}

.ft-card__item-label {
  color: #ffd166;
  font-weight: 700;
}

.ft-card__item-cost {
  align-self: center;
  color: #f1c453;
  font-size: 12px;
}

.ft-card__item-desc {
  grid-column: 1 / -1;
  color: rgba(255, 247, 230, 0.75);
  font-size: 11px;
}
</style>
