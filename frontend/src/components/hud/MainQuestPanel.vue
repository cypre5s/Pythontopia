<script setup lang="ts">
/**
 * R-13 §5 Phase 3：主线全屏面板（按 Q 切换显示）。
 *
 * 视觉：星露谷牛皮纸 9-slice 大窗 + 半透明遮罩。
 * 内容：7 章树（每章 5 子任务 + boss）+ 进度条 + 「去做」「打 Boss」按钮。
 * 行为：
 *   - 「去做」按钮跳到对应地图（复用 fastTravel 路由），无对应路径时回 town_hub；
 *   - 「打 Boss」按钮在该章 5 任务完成后亮起，跳 /boss/{chapter}（Phase 4 实现）；
 *   - ESC / 重按 Q 关闭。
 */
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { useMainQuestStore } from '@stores/mainQuest';
import { useWorldStore } from '@stores/world';
import { FAST_TRAVEL_ROUTES, type FastTravelRoute } from '@/data/fastTravel';
import type { MainSubQuestViewItem } from '@/types/mainQuest';

const mainQuest = useMainQuestStore();
const world = useWorldStore();
const router = useRouter();

const visible = computed(() => mainQuest.panelOpen);

function close(): void {
  mainQuest.closePanel();
}

function findRoute(mapKey: string): FastTravelRoute | null {
  return FAST_TRAVEL_ROUTES.find((r) => r.toMap === mapKey) ?? null;
}

function teleportTo(sub: MainSubQuestViewItem): void {
  const route = findRoute(sub.mapKey);
  if (route && world.player) {
    world.player = {
      ...world.player,
      mapKey: route.toMap,
      tileX: route.tileX,
      tileY: route.tileY,
    };
  }
  mainQuest.closePanel();
  void router.push({ path: '/world' });
}

function fightBoss(chapter: number): void {
  mainQuest.queueBoss(chapter);
  mainQuest.closePanel();
  void router.push({ path: `/boss/${chapter}` });
}

function goEndless(): void {
  mainQuest.closePanel();
  void router.push({ path: '/endless' });
}

function chapterReady(chapter: number, complete: number, total: number, defeated: boolean): boolean {
  return !defeated && complete >= total && chapter === mainQuest.progress.currentChapter;
}
</script>

<template>
  <section
    v-if="visible"
    class="mq-modal"
    data-testid="main-quest-panel"
    role="dialog"
    aria-modal="true"
    aria-labelledby="mq-modal-title"
    @click.self="close"
  >
    <div class="mq-card">
      <header class="mq-card__head">
        <h2
          id="mq-modal-title"
          class="mq-card__title"
        >
          主线任务 · 镇长 Py 实习计划
        </h2>
        <button
          type="button"
          class="mq-card__close"
          aria-label="关闭主线面板（ESC / Q）"
          @click="close"
        >
          ✕
        </button>
      </header>
      <div class="mq-card__progress">
        <span>整体进度</span>
        <div class="mq-progress-bar">
          <span
            class="mq-progress-bar__fill"
            :style="{ width: `${Math.round(mainQuest.overallProgress * 100)}%` }"
          />
        </div>
        <span class="mq-progress-pct">
          {{ Math.round(mainQuest.overallProgress * 100) }}%
        </span>
        <button
          type="button"
          class="mq-endless-btn"
          data-testid="main-quest-endless"
          title="无尽模式 · 连续 AI 出题 + 自适应难度"
          @click="goEndless"
        >
          无尽闯关
        </button>
      </div>
      <div
        class="mq-chapters"
        data-testid="main-quest-chapters"
      >
        <article
          v-for="ch in mainQuest.chapterViews"
          :key="ch.chapter"
          class="mq-chapter"
          :class="{
            'mq-chapter--active': ch.chapter === mainQuest.progress.currentChapter,
            'mq-chapter--done': ch.bossDefeated,
          }"
        >
          <header class="mq-chapter__head">
            <span class="mq-chapter__no">第 {{ ch.chapter }} 章</span>
            <span class="mq-chapter__name">{{ ch.title }}</span>
            <span class="mq-chapter__progress">
              <span class="mq-progress-bar mq-progress-bar--mini">
                <span
                  class="mq-progress-bar__fill"
                  :style="{ width: `${Math.round(ch.progress * 100)}%` }"
                />
              </span>
              <span>{{ ch.subQuests.filter((s) => s.status === 'completed').length }}/5</span>
            </span>
          </header>
          <ul class="mq-quest-list">
            <li
              v-for="sub in ch.subQuests"
              :key="sub.id"
              class="mq-quest-item"
              :class="`mq-quest-item--${sub.status}`"
              :data-testid="`main-quest-sub-${sub.id}`"
            >
              <span class="mq-quest-status">
                <template v-if="sub.status === 'completed'">✔</template>
                <template v-else-if="sub.status === 'in_progress'">▶</template>
                <template v-else>•</template>
              </span>
              <div class="mq-quest-meta">
                <span class="mq-quest-title">{{ sub.title }}</span>
                <small class="mq-quest-hint">{{ sub.hint }}</small>
              </div>
              <button
                type="button"
                class="mq-quest-go"
                :data-testid="`main-quest-go-${sub.id}`"
                :disabled="sub.status === 'completed'"
                @click="teleportTo(sub)"
              >
                {{ sub.status === 'completed' ? '已完成' : '去做' }}
              </button>
            </li>
          </ul>
          <footer class="mq-chapter__foot">
            <span class="mq-boss-label">章末 BOSS · {{ ch.boss.name }}</span>
            <button
              v-if="!ch.bossDefeated"
              type="button"
              class="mq-boss-btn"
              :data-testid="`main-quest-boss-${ch.chapter}`"
              :disabled="!chapterReady(ch.chapter, ch.subQuests.filter((s) => s.status === 'completed').length, ch.subQuests.length, ch.bossDefeated)"
              @click="fightBoss(ch.chapter)"
            >
              {{
                chapterReady(ch.chapter, ch.subQuests.filter((s) => s.status === 'completed').length, ch.subQuests.length, ch.bossDefeated)
                  ? '挑战 BOSS'
                  : '完成全部子任务后解锁'
              }}
            </button>
            <span
              v-else
              class="mq-boss-cleared"
            >★ 已击败</span>
          </footer>
        </article>
      </div>
    </div>
  </section>
</template>

<style scoped>
.mq-modal {
  position: fixed;
  inset: 0;
  background: rgba(8, 8, 12, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 220;
  pointer-events: auto;
}
.mq-card {
  width: min(960px, calc(100vw - 32px));
  max-height: calc(100vh - 64px);
  background: linear-gradient(180deg, #fff7e6 0%, #f5e2c1 100%);
  border: 4px solid #4d3613;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.5), inset 0 0 0 2px rgba(255, 247, 230, 0.6);
  color: #3a2a14;
  font-family: 'PythontopiaPixel', 'PingFang SC', monospace;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.mq-card__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 18px;
  border-bottom: 3px solid #c9a86b;
  background: rgba(212, 175, 55, 0.18);
}
.mq-card__title {
  margin: 0;
  font-size: 16px;
  color: #4d3613;
  letter-spacing: 1px;
}
.mq-card__close {
  background: transparent;
  border: 2px solid #6e4f1d;
  color: #4d3613;
  width: 28px;
  height: 28px;
  border-radius: 4px;
  cursor: pointer;
}
.mq-card__progress {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 18px;
  border-bottom: 2px dashed #c9a86b;
  font-size: 12px;
}
.mq-progress-bar {
  position: relative;
  display: inline-block;
  flex: 1 1 auto;
  height: 8px;
  background: rgba(110, 79, 29, 0.2);
  border: 1px solid #4d3613;
  border-radius: 2px;
  overflow: hidden;
}
.mq-progress-bar--mini {
  width: 60px;
  flex: 0 0 60px;
  height: 6px;
}
.mq-progress-bar__fill {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #6bbf59, #f1c453);
}
.mq-progress-pct {
  font-size: 12px;
  font-weight: 700;
  color: #4d3613;
  min-width: 36px;
  text-align: right;
}
.mq-endless-btn {
  margin-left: 12px;
  font-family: 'PythontopiaPixel', monospace;
  font-size: 12px;
  padding: 4px 12px;
  background: #1e3a8a;
  color: #fff7e6;
  border: 2px solid #4a90e2;
  border-radius: 4px;
  box-shadow: 0 2px 0 #1a2a6e;
  cursor: pointer;
}
.mq-endless-btn:hover {
  background: #2a4fb0;
}
.mq-endless-btn:active {
  transform: translateY(1px);
  box-shadow: none;
}
.mq-chapters {
  flex: 1 1 auto;
  overflow: auto;
  padding: 12px 18px 18px;
  display: grid;
  gap: 12px;
}
.mq-chapter {
  background: #fff7e6;
  border: 2px solid #c9a86b;
  border-radius: 6px;
  padding: 10px 14px;
}
.mq-chapter--active {
  border-color: #4d3613;
  box-shadow: inset 0 0 0 1px #f1c453;
}
.mq-chapter--done {
  opacity: 0.78;
}
.mq-chapter__head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.mq-chapter__no {
  font-weight: 700;
  font-size: 13px;
  color: #6e4f1d;
}
.mq-chapter__name {
  font-size: 13px;
  flex: 1 1 auto;
}
.mq-chapter__progress {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #6e4f1d;
}
.mq-quest-list {
  list-style: none;
  margin: 4px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.mq-quest-item {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 8px;
  align-items: center;
  padding: 4px 8px;
  border: 1px solid #c9a86b;
  border-radius: 4px;
  background: #fdf3d0;
}
.mq-quest-item--completed {
  background: rgba(180, 220, 160, 0.4);
  border-color: #5a8a3a;
  color: #1f4720;
}
.mq-quest-item--in_progress {
  background: #ffe098;
  border-color: #6e4f1d;
}
.mq-quest-status {
  font-size: 14px;
  width: 18px;
  text-align: center;
  color: #6e4f1d;
}
.mq-quest-meta {
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.mq-quest-title {
  font-size: 12px;
  font-weight: 700;
}
.mq-quest-hint {
  font-size: 10px;
  color: #6e4f1d;
}
.mq-quest-go {
  font-family: inherit;
  font-size: 11px;
  padding: 4px 10px;
  background: #f7d774;
  color: #2a1a08;
  border: 2px solid #8c6a23;
  border-radius: 4px;
  cursor: pointer;
  box-shadow: 0 2px 0 #8c6a23;
}
.mq-quest-go:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.mq-quest-go:active:not(:disabled) {
  transform: translateY(1px);
  box-shadow: 0 1px 0 #8c6a23;
}
.mq-chapter__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px dashed #c9a86b;
}
.mq-boss-label {
  font-size: 11px;
  color: #6e4f1d;
  font-weight: 700;
}
.mq-boss-btn {
  font-family: inherit;
  font-size: 12px;
  padding: 4px 12px;
  background: #c95e3a;
  color: #fff7e6;
  border: 2px solid #5a1a08;
  border-radius: 4px;
  cursor: pointer;
  box-shadow: 0 2px 0 #5a1a08;
}
.mq-boss-btn:disabled {
  background: #d6c6a1;
  color: #6e4f1d;
  border-color: #6e4f1d;
  box-shadow: none;
  cursor: not-allowed;
}
.mq-boss-cleared {
  font-size: 12px;
  color: #5a8a3a;
  font-weight: 700;
}
@media (max-width: 760px) {
  .mq-card {
    width: calc(100vw - 16px);
    max-height: calc(100vh - 24px);
  }
  .mq-quest-item {
    grid-template-columns: auto 1fr;
  }
  .mq-quest-go {
    grid-column: 1 / -1;
    justify-self: stretch;
  }
}
</style>
