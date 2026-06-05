<script setup lang="ts">
/**
 * 个人书房：生成资源按 KC 沉淀 + 检索 + 间隔复习。
 *
 * - 复用 listWorkshopResources（learning.resources.list）+ 掌握度快照（learning.field.masterySnapshot）。
 * - 间隔复习：把对应 KC 掌握度偏低的资源置顶为「待复习」，提示「重温资源 + 再练一题」。
 * - 检索：按 KC code / 标题过滤。
 */
import { computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useLearningStore } from '@stores/learning';
import { playUiClick } from '@/composables/useStardewFx';
import { kcDisplayName } from '@/data/kcNames';
import type { WorkshopResourceListItemDto } from '@/api/dto/Workshop';

const learning = useLearningStore();
const router = useRouter();

// 掌握度低于此值（0..100）的资源进入「待复习」。
const REVIEW_THRESHOLD = 60;

const masteryByKc = computed<Map<string, number>>(() => {
  const map = new Map<string, number>();
  for (const snap of learning.field.masterySnapshot) {
    map.set(snap.kcCode, Math.round((snap.masteryScore ?? 0) * 100));
  }
  return map;
});

/** 资源对应 KC 的最低掌握度（0..100）；无快照记 0（生成过但还没练）。 */
function resourceMastery(item: WorkshopResourceListItemDto): number {
  if (item.kcCodes.length === 0) return 100;
  let min = 100;
  for (const code of item.kcCodes) {
    const score = masteryByKc.value.get(code) ?? 0;
    if (score < min) min = score;
  }
  return min;
}

const allItems = computed<WorkshopResourceListItemDto[]>(() => learning.resources.list);

interface ReviewItem {
  resource: WorkshopResourceListItemDto;
  mastery: number;
  weakestKc: string;
}

const reviewItems = computed<ReviewItem[]>(() => {
  const list: ReviewItem[] = [];
  for (const item of allItems.value) {
    const mastery = resourceMastery(item);
    if (mastery >= REVIEW_THRESHOLD) continue;
    // 最弱 KC（用于「再练」直达）。
    let weakestKc = item.kcCodes[0] ?? '';
    let weakest = 100;
    for (const code of item.kcCodes) {
      const score = masteryByKc.value.get(code) ?? 0;
      if (score < weakest) {
        weakest = score;
        weakestKc = code;
      }
    }
    list.push({ resource: item, mastery, weakestKc });
  }
  return list.sort((a, b) => a.mastery - b.mastery).slice(0, 6);
});

// 去冗余：「待复习」已置顶的资源不再在下方「全部资源」里重复出现。
const reviewIds = computed<Set<number>>(
  () => new Set(reviewItems.value.map((r) => r.resource.id)),
);
const otherItems = computed<WorkshopResourceListItemDto[]>(
  () => allItems.value.filter((item) => !reviewIds.value.has(item.id)),
);

onMounted(async () => {
  await learning.loadResources();
  if (!learning.field.hydrated) {
    try {
      await learning.loadField();
    } catch {
      /* 掌握度拉取失败不阻断书房列表 */
    }
  }
});

function openDetail(id: number): void {
  playUiClick();
  void router.push({ path: '/workshop', query: { id: String(id) } });
}

function practiceKc(kcCode: string): void {
  playUiClick();
  void router.push({ path: '/problems', query: kcCode ? { kcCode } : {} });
}
</script>

<template>
  <aside
    class="list"
    data-testid="resource-library"
  >
    <section
      v-if="reviewItems.length > 0"
      class="review"
      data-testid="resource-review"
    >
      <p class="review__banner">
        🔁 {{ reviewItems.length }} 份待复习（掌握 &lt; {{ REVIEW_THRESHOLD }}）
      </p>
      <ul class="review__list">
        <li
          v-for="r in reviewItems"
          :key="r.resource.id"
          class="review__item"
        >
          <div class="review__meta">
            <span class="review__title">{{ r.resource.title }}</span>
            <span class="review__kc">{{ kcDisplayName(r.weakestKc) }} · 掌握度 {{ r.mastery }}/100</span>
          </div>
          <div class="review__actions">
            <button
              type="button"
              class="review__btn"
              @click="openDetail(r.resource.id)"
            >
              重温
            </button>
            <button
              type="button"
              class="review__btn review__btn--practice"
              @click="practiceKc(r.weakestKc)"
            >
              再练
            </button>
          </div>
        </li>
      </ul>
    </section>

    <p
      v-if="learning.resources.loading"
      class="hint"
    >
      载入资源列表中…
    </p>
    <template v-else-if="allItems.length > 0">
      <div class="toolbar">
        <span class="toolbar__label">资源 · {{ allItems.length }}</span>
        <button
          type="button"
          class="link"
          title="刷新列表"
          @click="learning.loadResources()"
        >
          ↻
        </button>
      </div>
      <ul
        v-if="otherItems.length > 0"
        data-testid="resource-list"
      >
        <li
          v-for="item in otherItems"
          :key="item.id"
        >
          <button
            type="button"
            class="card"
            @click="openDetail(item.id)"
          >
            <span class="title">{{ item.title }}</span>
            <span class="meta">
              {{ item.type }} · 掌握 {{ resourceMastery(item) }}
              <template v-if="item.feedbackStatus !== 'NONE'"> · {{ item.feedbackStatus }}</template>
            </span>
          </button>
        </li>
      </ul>
      <p
        v-else
        class="hint"
      >
        其余资源都掌握得不错 🎉
      </p>
    </template>
    <p
      v-else
      class="hint"
    >
      还没有生成任何资源，去聊天里说说你想学什么吧。
    </p>
  </aside>
</template>

<style scoped>
.list {
  background: #fff8df;
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  padding: 10px 12px;
  display: grid;
  gap: 8px;
  height: 100%;
  min-height: 0;
  /* 窄栏：只允许纵向滚动，禁止横向（卡片内部用 wrap/ellipsis 自适应宽度）。 */
  overflow-x: hidden;
  overflow-y: auto;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.toolbar__label {
  font-size: 11px;
  font-weight: 700;
  color: var(--pyt-color-text-muted);
  letter-spacing: 0.5px;
}
.link {
  background: none;
  border: none;
  color: var(--pyt-color-text-hint);
  cursor: pointer;
  text-decoration: underline;
}
/* 复习区 */
.review {
  display: grid;
  gap: 6px;
  padding: 8px;
  border: 2px dashed #d4af37;
  border-radius: var(--pyt-radius-lg);
  background: #fff3d6;
}
.review__banner {
  margin: 0;
  font-size: 12px;
  color: var(--pyt-color-text-hint);
  line-height: 1.5;
}
.review__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 6px;
}
.review__item {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 6px 8px;
  padding: 6px 8px;
  background: #fffbe9;
  border: 1px solid var(--pyt-color-border);
  border-radius: 6px;
}
.review__meta {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
  flex: 1 1 auto;
}
.review__title {
  font-size: 12px;
  font-weight: 600;
  color: #3a2a14;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.review__kc {
  font-size: 10px;
  color: var(--pyt-color-text-muted);
}
.review__actions {
  display: flex;
  gap: 4px;
  flex: 0 0 auto;
  margin-left: auto;
}
.review__btn {
  font-family: inherit;
  font-size: 11px;
  padding: 3px 8px;
  border: 1px solid #8c6a23;
  border-radius: 4px;
  background: var(--pyt-color-bg-elevated);
  color: var(--pyt-color-text-primary);
  cursor: pointer;
}
.review__btn--practice {
  background: #f7d774;
  font-weight: 700;
}
ul {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 8px;
}
.card {
  display: grid;
  gap: 4px;
  text-align: left;
  padding: 10px;
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  background: #fffbe9;
  cursor: pointer;
  width: 100%;
}
.card:hover {
  background: #fff2c9;
}
.title {
  font-weight: 600;
}
.meta {
  font-size: 12px;
  color: var(--pyt-color-text-hint);
}
.hint {
  color: var(--pyt-color-text-hint);
  font-size: 12px;
}
</style>
