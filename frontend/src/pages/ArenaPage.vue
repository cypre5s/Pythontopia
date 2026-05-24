<script setup lang="ts">
/**
 * R-13 §-1 续：/arena/:chapter 章节闯关塔页面（用户 Q2=A 决策实现）。
 *
 * 布局（星露谷牛皮纸风，与 WorkshopPanel / DialogModal 同设计语言）：
 *   ┌──────────────────────────────────────────────┐
 *   │ ← 返回小镇  第 N 章 [章节切换 7 chips]        │
 *   │ 进度 X / 10  连胜 K  累计积分 S               │
 *   ├──────────────────────────────────────────────┤
 *   │ 左：关卡阶梯（10 个卡片纵列，1-9 + boss）     │
 *   │   每卡：关号 / kind 图标 / 标题 / 难度 / 状态  │
 *   │ 右：当前选中关卡详情（hint + KC + 进入按钮）  │
 *   └──────────────────────────────────────────────┘
 *
 * 关卡入口：
 *   - 1-9 关：跳转 /endless?chapter=N&kc=K（EndlessPage 锁定章+KC 出题）
 *   - 10 关：跳转 /boss/N（4 阶段副本）
 *
 * 解锁规则：第 1 关常解锁；后续关需要上一关通过（store.isUnlocked）。
 * 进度持久化：useArenaStore 写 localStorage `pyt_arena_v1`。
 */
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import { useArenaStore } from '@stores/arena';
import { arenaLevelsByChapter } from '@/data/arenaLevels';
import { findBossByChapter } from '@/data/mainQuests';
import { useProfileSignals } from '@/composables/useProfileSignals';
import type { ArenaLevel, ArenaLevelKind } from '@/types/arena';

const route = useRoute();
const router = useRouter();
const arena = useArenaStore();
// R-13 §-1 续：深度接入学生画像 — 在 UI 上标识哪些关卡是「画像推荐」的弱项。
const profileSignals = useProfileSignals();

function isRecommendedByProfile(level: ArenaLevel): boolean {
  if (!profileSignals.hasAnySignal.value) return false;
  const weak = profileSignals.weakKcs.value;
  return level.kcCodes.some((kc) => weak.includes(kc));
}

const chapter = computed<number>(() => {
  const raw = Number(route.params.chapter);
  return Number.isInteger(raw) && raw >= 1 && raw <= 7 ? raw : 1;
});

const chapterLevels = computed<readonly ArenaLevel[]>(() =>
  arenaLevelsByChapter(chapter.value),
);

const boss = computed(() => findBossByChapter(chapter.value));

const selectedLevelId = ref<string>(`${chapter.value}-1`);

watch(chapter, (next) => {
  selectedLevelId.value = `${next}-1`;
});

const selectedLevel = computed<ArenaLevel | null>(
  () => chapterLevels.value.find((l) => l.id === selectedLevelId.value) ?? null,
);

const KIND_ICON: Record<ArenaLevelKind, string> = {
  pdf_question: '📖',
  fill_blank:   '▭',
  debug:        '⚠',
  short_answer: '✎',
  closed_loop:  '⟲',
  dual_track:   '⇆',
  multi_round:  '◉◉◉',
  teach_back:   '🎙',
  bug_mine:     '⛏',
  boss:         '★',
};

const KIND_LABEL: Record<ArenaLevelKind, string> = {
  pdf_question: 'PDF 问答',
  fill_blank:   '填空',
  debug:        '改错',
  short_answer: '简答',
  closed_loop:  '调试闭环',
  dual_track:   '双轨判题',
  multi_round:  '3 题组合',
  teach_back:   '教学反转',
  bug_mine:     'Bug 矿洞',
  boss:         '章末 BOSS',
};

function selectLevel(level: ArenaLevel): void {
  if (!arena.isUnlocked(level.id)) return;
  selectedLevelId.value = level.id;
}

function enterLevel(level: ArenaLevel): void {
  if (!arena.isUnlocked(level.id)) return;
  arena.markAttempt(level.id);
  if (level.kind === 'boss') {
    void router.push(`/boss/${level.chapter}`);
    return;
  }
  // 1-9 关跳 endless，传 chapter + kc + kind query 让 EndlessPage 锁定章 + KC + 题型
  // R-13 §-1 续：kind 让 PracticeAgent 走对应题型模板（fill_blank/debug 等），
  // 真正实现"闯关塔每关都有自己的玩法"而非随机抽题套壳。
  const kc = level.kcCodes[0] ?? '';
  void router.push({
    path: '/endless',
    query: {
      chapter: String(level.chapter),
      kc,
      kind: level.kind,
      arenaLevel: level.id,
    },
  });
}

function switchChapter(next: number): void {
  void router.push(`/arena/${next}`);
}

function back(): void {
  void router.push('/world');
}

function levelClass(level: ArenaLevel): Record<string, boolean> {
  const p = arena.getProgress(level.id);
  return {
    'arena-step': true,
    'arena-step--cleared': p.cleared,
    'arena-step--locked': !arena.isUnlocked(level.id),
    'arena-step--current': selectedLevelId.value === level.id,
    'arena-step--boss': level.kind === 'boss',
    'arena-step--recommended': isRecommendedByProfile(level),
  };
}

function star(level: ArenaLevel): string {
  return '★'.repeat(level.difficulty) + '☆'.repeat(5 - level.difficulty);
}
</script>

<template>
  <AppLayout>
    <section
      class="arena-page"
      data-testid="arena-page"
    >
      <header class="arena-head">
        <button
          type="button"
          class="arena-btn arena-btn--exit"
          data-testid="arena-exit"
          @click="back"
        >
          ← 返回小镇
        </button>
        <div class="arena-head__center">
          <h1 class="arena-title">
            第 {{ chapter }} 章 · 闯关塔
          </h1>
          <p
            v-if="boss"
            class="arena-subtitle"
          >
            守关 boss：{{ boss.name }}
          </p>
        </div>
        <div class="arena-stats">
          <span
            class="arena-stat"
            data-testid="arena-progress"
          >
            ✓ {{ arena.chapterCleared(chapter) }} / {{ arena.chapterTotal(chapter) }}
          </span>
          <span class="arena-stat arena-stat--total">
            总进度 {{ arena.totalCleared }} / {{ arena.totalLevels }}
          </span>
        </div>
      </header>

      <nav class="arena-chapter-tabs">
        <button
          v-for="c in 7"
          :key="c"
          type="button"
          class="arena-tab"
          :class="{ 'arena-tab--active': c === chapter }"
          :data-testid="`arena-chapter-${c}`"
          @click="switchChapter(c)"
        >
          第 {{ c }} 章
          <span class="arena-tab__count">{{ arena.chapterCleared(c) }}/10</span>
        </button>
      </nav>

      <div class="arena-body">
        <aside class="arena-steps" data-testid="arena-steps">
          <ol class="arena-step-list">
            <li
              v-for="level in chapterLevels"
              :key="level.id"
              :class="levelClass(level)"
              :data-testid="`arena-step-${level.id}`"
              @click="selectLevel(level)"
            >
              <span class="arena-step__num">{{ level.index }}</span>
              <span class="arena-step__icon">{{ KIND_ICON[level.kind] }}</span>
              <span class="arena-step__title">{{ level.title }}</span>
              <span class="arena-step__state">
                <template v-if="!arena.isUnlocked(level.id)">🔒</template>
                <template v-else-if="arena.getProgress(level.id).cleared">★</template>
                <template v-else>·</template>
              </span>
            </li>
          </ol>
        </aside>

        <article
          v-if="selectedLevel"
          class="arena-detail"
          data-testid="arena-detail"
        >
          <header class="arena-detail__head">
            <span class="badge badge--kind">{{ KIND_LABEL[selectedLevel.kind] }}</span>
            <span class="badge badge--diff">{{ star(selectedLevel) }}</span>
            <span
              v-if="isRecommendedByProfile(selectedLevel)"
              class="badge badge--reco"
              data-testid="arena-reco-badge"
              title="基于你的学生画像 errorPatterns.frequentKcCodes 推荐"
            >★ 画像推荐</span>
          </header>
          <h2 class="arena-detail__title">
            {{ selectedLevel.title }}
          </h2>
          <p class="arena-detail__hint">
            {{ selectedLevel.hint }}
          </p>
          <p class="arena-detail__kc">
            锚定 KC：{{ selectedLevel.kcCodes.join(' / ') }}
          </p>
          <div
            v-if="profileSignals.hasAnySignal.value"
            class="arena-profile-hint"
            data-testid="arena-profile-hint"
          >
            <span class="profile-icon">◑</span>
            <span>已接入学生画像 · 出题会偏好你的弱项 KC、按画像调节难度</span>
          </div>
          <ul class="arena-detail__rewards">
            <li
              v-for="r in selectedLevel.rewards"
              :key="`${r.kind}-${r.ref ?? r.amount}`"
            >
              奖励：{{ r.kind === 'coin' ? '🪙' : '📈' }} {{ r.amount }}{{ r.ref ? ` (${r.ref})` : '' }}
            </li>
          </ul>

          <div
            v-if="arena.getProgress(selectedLevel.id).cleared"
            class="arena-detail__cleared"
          >
            ✓ 已通关 · 最近用时 {{ arena.getProgress(selectedLevel.id).lastClearedSec ?? '?' }}s
          </div>

          <button
            type="button"
            class="arena-btn arena-btn--primary"
            data-testid="arena-enter"
            :disabled="!arena.isUnlocked(selectedLevel.id)"
            @click="enterLevel(selectedLevel)"
          >
            <template v-if="!arena.isUnlocked(selectedLevel.id)">
              🔒 解锁上一关再来
            </template>
            <template v-else-if="selectedLevel.kind === 'boss'">
              ★ 挑战 BOSS（4 阶段）
            </template>
            <template v-else>
              ▶ 进入第 {{ selectedLevel.index }} 关
            </template>
          </button>
        </article>
      </div>
    </section>
  </AppLayout>
</template>

<style scoped>
.arena-page {
  min-height: 100%;
  padding: 24px;
  background:
    radial-gradient(ellipse at top, rgba(255, 247, 230, 0.85) 0%, rgba(241, 216, 150, 0.55) 60%, rgba(212, 175, 55, 0.18) 100%);
  font-family: var(--pyt-font-pixel, 'Ark Pixel 12', monospace);
  color: #3a2a14;
}

.arena-head {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 16px;
  padding: 14px 22px;
  background: linear-gradient(180deg, #fff7e6 0%, #f1d896 100%);
  border: 3px solid #6e4f1d;
  border-radius: 12px;
  box-shadow: 4px 4px 0 #2a1a05;
  margin-bottom: 16px;
}
.arena-head__center {
  text-align: center;
}
.arena-title {
  margin: 0;
  font-size: 22px;
  color: #4d3613;
  text-shadow: 2px 2px 0 rgba(212, 175, 55, 0.55);
  letter-spacing: 1px;
}
.arena-subtitle {
  margin: 2px 0 0;
  font-size: 12px;
  color: #5d4524;
}
.arena-stats {
  display: flex;
  gap: 10px;
  flex-direction: column;
  align-items: flex-end;
}
.arena-stat {
  font-size: 13px;
  padding: 4px 12px;
  background: #ffd166;
  border: 2px solid #4d3613;
  border-radius: 999px;
}
.arena-stat--total { background: #c5e6f8; }

.arena-btn {
  padding: 10px 18px;
  font-family: inherit;
  font-size: 13px;
  letter-spacing: 1px;
  border: 3px solid #4d3613;
  background: #fff7e6;
  color: #4d3613;
  cursor: pointer;
  box-shadow: 3px 3px 0 #2a1a05;
  transition: transform 0.1s, box-shadow 0.1s;
}
.arena-btn:hover:not(:disabled) {
  transform: translate(1px, 1px);
  box-shadow: 2px 2px 0 #2a1a05;
}
.arena-btn:active:not(:disabled) {
  transform: translate(3px, 3px);
  box-shadow: 0 0 0 #2a1a05;
}
.arena-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.arena-btn--exit { background: #e2c391; }
.arena-btn--primary {
  background: linear-gradient(180deg, #ffd166 0%, #f4b400 100%);
  width: 100%;
  margin-top: 16px;
  padding: 14px 20px;
  font-size: 15px;
}

.arena-chapter-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}
.arena-tab {
  padding: 8px 14px;
  background: rgba(255, 247, 230, 0.85);
  border: 2px solid #6e4f1d;
  border-radius: 6px;
  font-family: inherit;
  font-size: 12px;
  color: #5d4524;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  min-width: 70px;
}
.arena-tab--active {
  background: linear-gradient(180deg, #ffd166 0%, #f4b400 100%);
  color: #4d3613;
  box-shadow: 3px 3px 0 #2a1a05;
  border-color: #4d3613;
}
.arena-tab__count {
  font-size: 10px;
  opacity: 0.8;
}

.arena-body {
  display: grid;
  grid-template-columns: minmax(280px, 380px) 1fr;
  gap: 16px;
}

.arena-steps {
  background:
    repeating-linear-gradient(135deg, rgba(212, 175, 55, 0.08) 0 8px, transparent 8px 18px),
    linear-gradient(180deg, #fff7e6 0%, #f1d896 100%);
  border: 3px solid #6e4f1d;
  border-radius: 10px;
  padding: 12px;
  box-shadow: 4px 4px 0 #2a1a05;
}
.arena-step-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.arena-step {
  display: grid;
  grid-template-columns: 28px 28px 1fr 22px;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: rgba(255, 247, 230, 0.85);
  border: 2px solid #8a6c2c;
  border-radius: 6px;
  cursor: pointer;
  transition: transform 0.1s, background 0.15s;
}
.arena-step:hover:not(.arena-step--locked) {
  background: #fff7e6;
  transform: translateX(2px);
}
.arena-step--cleared {
  border-color: #16a34a;
  background: rgba(187, 247, 208, 0.55);
}
.arena-step--locked {
  opacity: 0.45;
  cursor: not-allowed;
}
.arena-step--current {
  border-color: #b8442d;
  box-shadow: 2px 2px 0 #4d3613;
  background: #ffe7b3;
}
.arena-step--boss {
  background: linear-gradient(135deg, #fff7e6 0%, #facc15 50%, #ffd166 100%);
  border-color: #b8442d;
  border-width: 3px;
}
.arena-step--recommended {
  outline: 2px dashed #7b2cbf;
  outline-offset: 2px;
  position: relative;
}
.arena-step--recommended::after {
  content: '弱项';
  position: absolute;
  top: -8px;
  right: -2px;
  font-size: 9px;
  background: #7b2cbf;
  color: #fff7e6;
  padding: 1px 4px;
  border-radius: 4px;
  letter-spacing: 0.5px;
}
.arena-step__num {
  font-weight: bold;
  text-align: center;
  color: #4d3613;
}
.arena-step__icon {
  font-size: 16px;
  text-align: center;
}
.arena-step__title {
  font-size: 12px;
  color: #3a2a14;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.arena-step__state {
  text-align: center;
}

.arena-detail {
  background: linear-gradient(180deg, #fff7e6 0%, #f1d896 100%);
  border: 3px solid #6e4f1d;
  border-radius: 10px;
  padding: 22px 24px;
  box-shadow: 4px 4px 0 #2a1a05;
}
.arena-detail__head {
  display: flex;
  gap: 8px;
  margin-bottom: 6px;
}
.badge {
  padding: 3px 10px;
  font-size: 11px;
  border-radius: 999px;
  border: 2px solid #4d3613;
  background: #ffd166;
  color: #4d3613;
}
.badge--diff {
  background: #fff7e6;
  letter-spacing: 1px;
  color: #b8442d;
}
.badge--reco {
  background: #ede9fe;
  border-color: #7b2cbf;
  color: #5b21b6;
}
.arena-profile-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  margin: 10px 0;
  background: rgba(237, 233, 254, 0.7);
  border: 2px dashed #7b2cbf;
  border-radius: 6px;
  font-size: 12px;
  color: #4c1d95;
}
.profile-icon {
  font-size: 16px;
  color: #7b2cbf;
}
.arena-detail__title {
  margin: 8px 0 12px;
  font-size: 22px;
  color: #4d3613;
}
.arena-detail__hint {
  margin: 0 0 12px;
  font-size: 14px;
  line-height: 1.6;
  color: #5d4524;
}
.arena-detail__kc {
  margin: 0 0 12px;
  font-size: 12px;
  color: #8a6c2c;
}
.arena-detail__rewards {
  list-style: none;
  padding: 0;
  margin: 0 0 12px;
  font-size: 13px;
  color: #5d4524;
}
.arena-detail__rewards li {
  padding: 3px 0;
}
.arena-detail__cleared {
  padding: 8px 12px;
  background: rgba(187, 247, 208, 0.6);
  border: 2px solid #16a34a;
  border-radius: 6px;
  color: #14532d;
  font-size: 13px;
  margin-bottom: 6px;
}

@media (max-width: 720px) {
  .arena-body {
    grid-template-columns: 1fr;
  }
  .arena-head {
    grid-template-columns: 1fr;
    gap: 8px;
    text-align: center;
  }
  .arena-stats {
    flex-direction: row;
    justify-content: center;
    align-items: center;
  }
}
</style>
