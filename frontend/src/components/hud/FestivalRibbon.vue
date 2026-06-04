<script setup lang="ts">
// R-13 迭代 13：节日 ribbon 横幅，挂在顶部 HUD 下方。
//
// 设计要点：
//   1. 只在「今日节日」当天显示（重要事件 ribbon 闪 + chime SFX 一次）；
//   2. R-14 降负荷：不再常驻显示「距下个节日还有 N 天」的倒数，避免与中央任务列抢注意力；
//   3. 数据来自 hud.meta.dayIndex + 前端静态 FESTIVALS（后端 V118 seed 对齐）；
//   4. 一旦后端 WorldFestivalService 暴露 Controller，可以平滑切换为 fetch；
//   5. 不挤占小地图（minimap 在 top:72px，ribbon 在 top:50px）。

import { computed, ref, watch } from 'vue';
import { findCurrentFestival } from '@/data/festivals';
import { useHudStore } from '@stores/hud';
import { playChime } from '@/composables/useStardewFx';

const hud = useHudStore();
const today = computed(() => hud.meta.dayIndex);

const current = computed(() => findCurrentFestival(today.value));

const lastChimedDay = ref<number>(-1);
watch(current, (festival) => {
  if (festival && lastChimedDay.value !== festival.day) {
    lastChimedDay.value = festival.day;
    playChime('season');
  }
});

const ribbonClass = computed(() => `festival-ribbon--${current.value?.season ?? 'spring'}`);
</script>

<template>
  <transition name="festival-fade">
    <div
      v-if="current"
      class="festival-ribbon"
      :class="ribbonClass"
      data-testid="festival-ribbon"
      role="status"
      aria-live="polite"
    >
      <span
        class="festival-ribbon__icon"
        aria-hidden="true"
      >{{ current.icon }}</span>
      <span class="festival-ribbon__label">{{ current.label }}</span>
      <span class="festival-ribbon__desc">{{ current.description }}</span>
    </div>
  </transition>
</template>

<style scoped>
.festival-ribbon {
  position: fixed;
  top: 50px;
  left: 50%;
  transform: translateX(-50%);
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 6px 14px;
  background: rgba(28, 24, 16, 0.86);
  border: 2px solid #d4af37;
  border-radius: 999px;
  color: var(--pyt-color-bg-elevated);
  font-family: var(--pyt-font-pixel), 'PingFang SC', monospace;
  font-size: 12px;
  letter-spacing: 0.5px;
  pointer-events: none;
  z-index: 95;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.35);
  max-width: calc(100vw - 32px);
}

.festival-ribbon__icon {
  font-size: 16px;
  color: var(--pyt-color-gold-bright);
  text-shadow: 1px 1px 0 var(--pyt-color-shadow);
}

.festival-ribbon__label {
  color: var(--pyt-color-gold-bright);
  font-weight: 700;
}

.festival-ribbon__desc {
  color: rgba(255, 247, 230, 0.85);
}

.festival-ribbon--spring {
  border-color: #6bbf59;
}
.festival-ribbon--summer {
  border-color: var(--pyt-color-success);
}
.festival-ribbon--autumn {
  border-color: var(--pyt-color-warning);
}
.festival-ribbon--winter {
  border-color: #aac6d9;
}

.festival-fade-enter-active,
.festival-fade-leave-active {
  transition: opacity 320ms ease, transform 320ms ease;
}
.festival-fade-enter-from {
  opacity: 0;
  transform: translate(-50%, -8px);
}
.festival-fade-leave-to {
  opacity: 0;
  transform: translate(-50%, 8px);
}

@media (max-width: 640px) {
  .festival-ribbon {
    top: 44px;
    padding: 4px 10px;
    font-size: 10px;
    gap: 6px;
  }
  .festival-ribbon__desc {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .festival-fade-enter-active,
  .festival-fade-leave-active {
    transition: opacity 150ms linear;
  }
  .festival-fade-enter-from,
  .festival-fade-leave-to {
    transform: translateX(-50%);
  }
}
</style>
