<script setup lang="ts">
/**
 * R-13 §5 Phase 3：HUD 顶部主线进度 chip。
 *
 * 显示：`第 X 章 · {boss 名} (i/5)` + 整体进度百分比 + 「按 Q」提示。
 * 点击：调 mainQuest.togglePanel()；HudShell 的 Q 键监听做相同事。
 */
import { computed } from 'vue';
import { useMainQuestStore } from '@stores/mainQuest';

const mainQuest = useMainQuestStore();

const currentChapter = computed(() => mainQuest.progress.currentChapter);

const chapterTitle = computed<string>(() => {
  const idx = currentChapter.value - 1;
  const view = mainQuest.chapterViews[idx];
  return view?.title ?? '未知章';
});

const completedInChapter = computed<number>(() => {
  const view = mainQuest.chapterViews[currentChapter.value - 1];
  return view ? view.subQuests.filter((s) => s.status === 'completed').length : 0;
});

const overallPct = computed<number>(() =>
  Math.round(mainQuest.overallProgress * 100),
);

function onChipClick(): void {
  mainQuest.togglePanel();
}
</script>

<template>
  <button
    type="button"
    class="main-quest-chip"
    data-testid="main-quest-chip"
    :aria-label="`主线进度：第 ${currentChapter} 章 ${chapterTitle}（${completedInChapter}/5），整体 ${overallPct}%，按 Q 查看`"
    :aria-pressed="mainQuest.panelOpen"
    @click="onChipClick"
  >
    <span class="mqc-chapter">第 {{ currentChapter }} 章</span>
    <span class="mqc-sep">·</span>
    <span class="mqc-title">{{ chapterTitle }}</span>
    <span class="mqc-progress">
      <span class="mqc-sub">{{ completedInChapter }}/5</span>
      <span class="mqc-bar">
        <span
          class="mqc-bar__fill"
          :style="{ width: `${overallPct}%` }"
        />
      </span>
      <span class="mqc-pct">{{ overallPct }}%</span>
    </span>
    <span class="mqc-hint">按 Q</span>
  </button>
</template>

<style scoped>
.main-quest-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: rgba(241, 196, 83, 0.25);
  border: 2px solid var(--pyt-color-gold);
  border-radius: 4px;
  color: var(--pyt-color-bg-elevated);
  font-family: var(--pyt-font-pixel), 'PingFang SC', monospace;
  font-size: 12px;
  cursor: pointer;
  pointer-events: auto;
  user-select: none;
  transition: background 120ms;
}
.main-quest-chip:hover {
  background: rgba(241, 196, 83, 0.4);
}
.main-quest-chip[aria-pressed="true"] {
  background: rgba(241, 196, 83, 0.55);
  box-shadow: inset 0 0 0 1px var(--pyt-color-bg-elevated);
}
.mqc-chapter {
  color: var(--pyt-color-gold-bright);
  font-weight: 700;
}
.mqc-sep {
  color: rgba(255, 247, 230, 0.5);
}
.mqc-title {
  color: var(--pyt-color-bg-elevated);
}
.mqc-progress {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: 4px;
}
.mqc-sub {
  font-size: 11px;
  color: var(--pyt-color-bg-elevated);
}
.mqc-bar {
  position: relative;
  display: inline-block;
  width: 50px;
  height: 6px;
  background: rgba(0, 0, 0, 0.35);
  border: 1px solid var(--pyt-color-text-hint);
  border-radius: 1px;
  overflow: hidden;
}
.mqc-bar__fill {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #6bbf59, var(--pyt-color-gold));
  transition: width 220ms ease-out;
}
.mqc-pct {
  font-size: 10px;
  color: var(--pyt-color-gold);
  min-width: 28px;
  text-align: right;
}
.mqc-hint {
  font-size: 10px;
  color: rgba(255, 247, 230, 0.55);
  margin-left: 2px;
  border: 1px solid rgba(255, 247, 230, 0.4);
  padding: 0 4px;
  border-radius: 2px;
}
</style>
