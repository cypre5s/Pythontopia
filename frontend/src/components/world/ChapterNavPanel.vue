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
  position: fixed;
  top: 80px;
  left: 16px;
  width: 132px;
  background: rgba(34, 24, 14, 0.85);
  border: 3px solid #d4af37;
  border-radius: 8px;
  padding: 12px 8px;
  z-index: 50;
  color: #fff;
  font-family: 'PythontopiaPixel', 'PingFang SC', 'Noto Sans CJK SC', monospace;
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
