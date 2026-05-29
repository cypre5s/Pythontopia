<script setup lang="ts">
// R-13 迭代 13：节日 ribbon 横幅，挂在顶部 HUD 下方。
//
// 设计要点：
//   1. 优先显示「今日节日」（重要事件 ribbon 闪 + chime SFX 一次）；
//   2. 没有今日节日时显示「下一个节日」与倒数 N 天，引导玩家期待；
//   3. 数据来自 calendar.today.dayIndex + 前端静态 FESTIVALS（后端 V118 seed 对齐）；
//   4. 一旦后端 WorldFestivalService 暴露 Controller，可以平滑切换为 fetch；
//   5. 不挤占小地图（minimap 在 top:72px，ribbon 在 top:50px）。

import { computed, ref, watch } from 'vue';
import {
  findCurrentFestival,
  findUpcomingFestival,
  daysUntilFestival,
} from '@/data/festivals';
import { useHudStore } from '@stores/hud';
import { playChime } from '@/composables/useStardewFx';

const hud = useHudStore();
const today = computed(() => hud.meta.dayIndex);

const current = computed(() => findCurrentFestival(today.value));
const upcoming = computed(() => findUpcomingFestival(today.value));

const lastChimedDay = ref<number>(-1);
watch(current, (festival) => {
  if (festival && lastChimedDay.value !== festival.day) {
    lastChimedDay.value = festival.day;
    playChime('season');
  }
});

const ribbonClass = computed(() => {
  const season = current.value?.season ?? upcoming.value?.season ?? 'spring';
  return `festival-ribbon--${season}`;
});
</script>

<template>
  <transition name="festival-fade">
    <div
      v-if="current || upcoming"
      class="festival-ribbon"
      :class="ribbonClass"
      data-testid="festival-ribbon"
      role="status"
      aria-live="polite"
    >
      <template v-if="current">
        <span
          class="festival-ribbon__icon"
          aria-hidden="true"
        >{{ current.icon }}</span>
        <span class="festival-ribbon__label">{{ current.label }}</span>
        <span class="festival-ribbon__desc">{{ current.description }}</span>
      </template>
      <template v-else-if="upcoming">
        <span
          class="festival-ribbon__icon"
          aria-hidden="true"
        >{{ upcoming.icon }}</span>
        <span class="festival-ribbon__label">距 {{ upcoming.label }} 还有 {{ daysUntilFestival(today, upcoming) }} 天</span>
        <span class="festival-ribbon__desc">{{ upcoming.description }}</span>
      </template>
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
  color: #fff7e6;
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
  color: #ffd166;
  text-shadow: 1px 1px 0 #2a1a05;
}

.festival-ribbon__label {
  color: #ffd166;
  font-weight: 700;
}

.festival-ribbon__desc {
  color: rgba(255, 247, 230, 0.85);
}

.festival-ribbon--spring {
  border-color: #6bbf59;
}
.festival-ribbon--summer {
  border-color: #2f8f3c;
}
.festival-ribbon--autumn {
  border-color: #c2871f;
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
