<script setup lang="ts">
/**
 * 无尽远征「班级排行榜」（最高单局得分）。
 *
 * 数据后端权威（GET /api/world/expedition/leaderboard，按班级 MAX(score) 排名），
 * 未入班 → 提示加入班级；本班无人完成 → 抢首杀提示。自身行高亮，未上榜时底部补「我」行。
 * best-effort：board 为 null（拉取失败/未拉）显示降级占位，不阻塞玩家。
 */
import { computed } from 'vue';
import type { ExpeditionLeaderboard } from '@/api/expedition';

const props = withDefaults(
  defineProps<{
    board: ExpeditionLeaderboard | null;
    loading?: boolean;
  }>(),
  { loading: false },
);

const entries = computed(() => props.board?.entries ?? []);
const inClass = computed(() => props.board?.inClass ?? false);
const selfRank = computed(() => props.board?.selfRank ?? null);
const selfBest = computed(() => props.board?.selfBest ?? 0);

// 自身已在展示榜里则不再底部补行，避免重复。
const selfInList = computed(() => entries.value.some((e) => e.self));
const showSelfFooter = computed(
  () => inClass.value && selfRank.value != null && !selfInList.value,
);

function medal(rank: number): string {
  if (rank === 1) return '🥇';
  if (rank === 2) return '🥈';
  if (rank === 3) return '🥉';
  return `${rank}`;
}
</script>

<template>
  <section
    class="lb"
    data-testid="expedition-leaderboard"
  >
    <header class="lb__head">
      <h3 class="lb__title">
        🏆 班级排行榜
      </h3>
      <span class="lb__sub">最高单局得分</span>
    </header>

    <p
      v-if="!board && loading"
      class="lb__note"
      role="status"
    >
      榜单加载中…
    </p>
    <p
      v-else-if="!board"
      class="lb__note"
    >
      排行榜暂不可用，稍后再看。
    </p>
    <div
      v-else-if="!inClass"
      class="lb__empty"
      data-testid="expedition-leaderboard-noclass"
    >
      <p>加入班级后即可和同学比拼远征得分。</p>
      <p
        v-if="selfBest > 0"
        class="lb__selfbest"
      >
        你的最高单局得分：<strong>{{ selfBest }}</strong>
      </p>
    </div>
    <div
      v-else-if="entries.length === 0"
      class="lb__empty"
    >
      <p>本班还没有远征核验成绩。</p>
    </div>
    <ol
      v-else
      class="lb__list"
    >
      <li
        v-for="e in entries"
        :key="e.userId"
        class="lb__row"
        :class="{ 'is-self': e.self, 'is-top': e.rank <= 3 }"
        :data-testid="`expedition-leaderboard-row-${e.rank}`"
      >
        <span class="lb__rank">{{ medal(e.rank) }}</span>
        <span class="lb__name">
          {{ e.displayName }}
          <span
            v-if="e.self"
            class="lb__me"
          >我</span>
        </span>
        <span class="lb__depth">通过题数 {{ e.bestDepth }}</span>
        <span class="lb__score">{{ e.bestScore }}</span>
      </li>
    </ol>

    <p
      v-if="showSelfFooter"
      class="lb__selfrow"
      data-testid="expedition-leaderboard-self"
    >
      我 · 第 {{ selfRank }} 名 · 最高 {{ selfBest }} 分
    </p>
  </section>
</template>

<style scoped>
.lb {
  width: min(520px, 100%);
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px;
  text-align: left;
  background: rgba(255, 249, 234, 0.96);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  box-shadow: 3px 3px 0 rgba(74, 54, 29, 0.25);
  font-family: var(--pyt-font-pixel);
  color: var(--pyt-color-text-primary);
}
.lb__head {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.lb__title {
  margin: 0;
  font-size: 15px;
}
.lb__sub {
  font-size: 11px;
  color: var(--pyt-color-text-muted);
}
.lb__note,
.lb__empty {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--pyt-color-text-hint);
}
.lb__empty p {
  margin: 0;
}
.lb__selfbest strong {
  color: var(--pyt-color-warning);
}
.lb__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.lb__row {
  display: grid;
  grid-template-columns: 32px 1fr auto auto;
  align-items: center;
  gap: 10px;
  padding: 5px 10px;
  font-size: 12px;
  background: var(--pyt-color-bg-soft);
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
}
.lb__row.is-top {
  background: var(--pyt-color-warning-bg);
  border-color: var(--pyt-color-warning);
}
.lb__row.is-self {
  border-color: var(--pyt-color-info);
  box-shadow: 0 0 0 1px var(--pyt-color-info) inset;
}
.lb__rank {
  font-size: 13px;
  font-weight: 700;
  text-align: center;
  color: var(--pyt-color-text-primary);
}
.lb__name {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lb__me {
  flex: 0 0 auto;
  padding: 0 6px;
  font-size: 10px;
  color: var(--pyt-color-info);
  background: var(--pyt-color-info-bg);
  border: 1px solid var(--pyt-color-info);
  border-radius: 999px;
}
.lb__depth {
  font-size: 11px;
  color: var(--pyt-color-text-muted);
  white-space: nowrap;
}
.lb__score {
  font-weight: 700;
  color: var(--pyt-color-warning);
  white-space: nowrap;
}
.lb__selfrow {
  margin: 2px 0 0;
  padding: 5px 10px;
  font-size: 12px;
  color: var(--pyt-color-info);
  background: var(--pyt-color-info-bg);
  border: 1px dashed var(--pyt-color-info);
  border-radius: var(--pyt-radius-sm);
}
</style>
