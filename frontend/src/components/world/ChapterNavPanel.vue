<script setup lang="ts">
// W4-CHAPTER-NAV：恢复 R-11 时 FieldPage 的章节导航。
//
// 设计要点：
//   1. 组件仅负责渲染 + 交互；可见性由父级 WorldPage 通过 `v-if="isOnFarm"` 控制。
//   2. 点击章节时，先在 learning.field.crops 范围内挑出第一个 KC（保留 R-11
//      行为，与 KcDetailPanel/activeKcMastery 兼容）；若 crops 尚未 bootstrap，
//      退化为 range 起点构造的 `PY43-KC-NNN` 占位 code（避免 selectKc(null)）。
//   3. hover 100ms 节流，避免连续触发音效；与任务 4 `playSfx('sfx_hover', {volume:0.15})` 对齐。
//   4. `data-testid` 沿用 `chapter-nav-<slug>` 命名，方便后续 e2e/单测稳定定位。
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useLearningStore } from '@stores/learning';
import {
  CHAPTERS,
  findChapterByKcCode,
  formatKcNumber,
  parseKcNumber,
  type ChapterEntry,
} from '@/data/chapters';
import { playSfx } from '@/composables/useAudio';

const route = useRoute();
const router = useRouter();
const learning = useLearningStore();

const activeKcCode = computed<string | null>(() => learning.field.activeKcCode);
const activeChapterSlug = computed<string | null>(
  () => findChapterByKcCode(activeKcCode.value)?.kcCode ?? null,
);

function pickFirstKcInRange(chapter: ChapterEntry): string {
  const sorted = learning.field.crops
    .map((crop) => ({ crop, kcNumber: parseKcNumber(crop.kcCode) }))
    .filter(
      (
        entry,
      ): entry is { crop: (typeof entry)['crop']; kcNumber: number } =>
        entry.kcNumber !== null &&
        entry.kcNumber >= chapter.startKc &&
        entry.kcNumber <= chapter.endKc,
    )
    .sort((a, b) => a.kcNumber - b.kcNumber);
  if (sorted.length > 0) {
    return sorted[0]!.crop.kcCode;
  }
  return formatKcNumber(chapter.startKc);
}

function selectChapter(chapter: ChapterEntry): void {
  playSfx('sfx_click');
  const targetKc = pickFirstKcInRange(chapter);
  learning.selectKc(targetKc);
  void router.replace({
    path: route.path,
    query: { ...route.query, kc: targetKc },
  });
}

const HOVER_THROTTLE_MS = 100;
let lastHoverAt = 0;
function onHover(): void {
  const now =
    typeof performance !== 'undefined' && typeof performance.now === 'function'
      ? performance.now()
      : Date.now();
  if (now - lastHoverAt < HOVER_THROTTLE_MS) {
    return;
  }
  lastHoverAt = now;
  playSfx('sfx_hover', { volume: 0.15 });
}
</script>

<template>
  <aside
    class="chapter-nav"
    data-testid="chapter-nav-panel"
  >
    <h3 class="chapter-nav__title">
      章节
    </h3>
    <ul class="chapter-nav__list">
      <li
        v-for="chapter in CHAPTERS"
        :key="chapter.kcCode"
      >
        <button
          type="button"
          :data-testid="`chapter-nav-${chapter.kcCode}`"
          :class="[
            'chapter-btn',
            { 'chapter-btn--active': activeChapterSlug === chapter.kcCode },
          ]"
          @click="selectChapter(chapter)"
          @pointerenter="onHover"
        >
          <img
            :src="chapter.iconPath"
            :alt="chapter.name"
            class="chapter-icon"
          >
          <span class="chapter-label">{{ chapter.name }}</span>
        </button>
      </li>
    </ul>
  </aside>
</template>

<style scoped>
.chapter-nav {
  /* R-12 美化：与新版顶部 HUD（top:16px,height~64px）+ 学习目标面板（top:96px）
     共存，下移到 top:96px 起，并向右挪到 left:340px 让出左侧 300px 给
     LearningObjectivePanel。窄屏（≤1280px）回到 left:288px 避免重叠。 */
  position: fixed;
  top: 96px;
  left: 340px;
  width: 132px;
  background: rgba(25, 28, 18, 0.78);
  border: 1px solid rgba(218, 176, 76, 0.65);
  border-radius: 12px;
  padding: 12px 8px;
  z-index: 50;
  color: #fff;
  font-family: var(--pyt-font-pixel, monospace);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.28);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}

@media (max-width: 1280px) {
  .chapter-nav {
    top: 88px;
    left: 288px;
  }
}

@media (max-width: 1024px) {
  .chapter-nav {
    /* 极窄屏让出左侧 HUD：放到画面右下角作为折叠章节按钮 */
    top: auto;
    bottom: 100px;
    left: 12px;
    width: 110px;
    opacity: 0.94;
  }
}
.chapter-nav__title {
  color: #f1d35a;
  font-size: 13px;
  text-align: center;
  margin: 0 0 8px;
}
.chapter-nav__list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.chapter-btn {
  background: none;
  border: 2px solid transparent;
  border-radius: 6px;
  padding: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  color: #fff;
  text-align: left;
}
.chapter-btn:hover {
  background: rgba(212, 175, 55, 0.15);
}
.chapter-btn--active {
  background: rgba(212, 175, 55, 0.22);
  border-color: #d4af37;
}
.chapter-icon {
  width: 28px;
  height: 28px;
  image-rendering: pixelated;
  flex: 0 0 auto;
}
.chapter-label {
  font-size: 11px;
  line-height: 1.2;
  color: #fff;
}
</style>
