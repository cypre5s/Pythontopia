<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import { useSessionStore } from '@stores/session';
import { useLearningStore } from '@stores/learning';
import { listMyClassrooms } from '@/api/classroom';
import { getRecommendations, type RecommendationDto } from '@/api/recommendations';
import { playSfx } from '@/composables/useAudio';
import type { ClassroomDto } from '@/api/dto/ClassroomDto';

const router = useRouter();
const session = useSessionStore();
const learning = useLearningStore();

const classroom = ref<ClassroomDto | null>(null);
const recommendations = ref<RecommendationDto[]>([]);
const loadingRecs = ref(false);
const loadingQuests = ref(false);

const CIRCUMFERENCE = 2 * Math.PI * 42;

const masteryPct = computed(() => {
  const snap = learning.field.masterySnapshot;
  if (snap.length === 0) return 0;
  const total = snap.reduce((sum, entry) => sum + entry.masteryScore, 0);
  return Math.round(total / snap.length);
});

const dashOffset = computed(() => CIRCUMFERENCE * (1 - masteryPct.value / 100));

const todayQuests = computed(() => learning.quests.today.slice(0, 3));

const RESOURCE_TYPE_ICONS: Record<string, string> = {
  HANDOUT: '✎',
  MINDMAP: '✦',
  PRACTICE: '✪',
  CODE_LAB: '⚒',
  READING: '☼',
  STORYBOARD: '⌘',
};

const QUEST_STATUS_LABELS: Record<string, string> = {
  OPEN: '待接',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  SKIPPED: '已跳过',
  EXPIRED: '已过期',
};

function typeIcon(type: string): string {
  return RESOURCE_TYPE_ICONS[type] ?? '✦';
}

function questStatusLabel(status: string): string {
  return QUEST_STATUS_LABELS[status] ?? status;
}

function onRecommendClick(rec: RecommendationDto): void {
  playSfx('sfx_click');
  void router.push({ path: '/workshop', query: { id: String(rec.resourceId) } });
}

function goWorld(): void {
  playSfx('sfx_click');
  void router.push('/world');
}

function goTutor(): void {
  playSfx('sfx_click');
  void router.push('/tutor');
}

function goTo(path: string): void {
  playSfx('sfx_click');
  void router.push(path);
}

function goLogin(): void {
  void router.push('/login');
}

onMounted(async () => {
  if (!session.isAuthenticated) return;

  const tasks: Promise<void>[] = [];

  if (learning.field.masterySnapshot.length === 0) {
    tasks.push(learning.loadField());
  }

  loadingQuests.value = true;
  tasks.push(
    learning.loadTodayQuests().finally(() => {
      loadingQuests.value = false;
    }),
  );

  loadingRecs.value = true;
  tasks.push(
    getRecommendations()
      .then((recs) => {
        recommendations.value = recs.slice(0, 3);
      })
      .catch(() => {
        /* recommendations are optional */
      })
      .finally(() => {
        loadingRecs.value = false;
      }),
  );

  tasks.push(
    listMyClassrooms()
      .then((list) => {
        classroom.value = list.find((c) => c.status === 'ACTIVE') ?? null;
      })
      .catch(() => {
        /* classroom info is optional */
      }),
  );

  await Promise.allSettled(tasks);
});
</script>

<template>
  <AppLayout>
    <main
      class="home-page"
      data-testid="home-page"
    >
      <!-- ── Authenticated Dashboard ── -->
      <template v-if="session.isAuthenticated">
        <section class="welcome-area">
          <div class="welcome-text">
            <h1 class="welcome-title">
              欢迎回来，{{ session.displayName }}
            </h1>
            <p
              v-if="classroom"
              class="welcome-class"
            >
              班级：{{ classroom.name }}
            </p>
            <p class="welcome-hint">
              今天也来 Pythontopia 种知识吧
            </p>
          </div>
          <div class="mastery-ring-wrap">
            <svg
              viewBox="0 0 100 100"
              class="mastery-ring"
              aria-label="KC 总掌握度"
            >
              <circle
                cx="50"
                cy="50"
                r="42"
                fill="none"
                stroke="#c9a86b"
                stroke-width="6"
              />
              <circle
                cx="50"
                cy="50"
                r="42"
                fill="none"
                stroke="#6bbf59"
                stroke-width="6"
                stroke-linecap="round"
                :stroke-dasharray="CIRCUMFERENCE"
                :stroke-dashoffset="dashOffset"
                transform="rotate(-90 50 50)"
                class="mastery-ring__fill"
              />
              <text
                x="50"
                y="46"
                text-anchor="middle"
                dominant-baseline="central"
                class="ring-pct"
              >{{ masteryPct }}%</text>
              <text
                x="50"
                y="62"
                text-anchor="middle"
                dominant-baseline="central"
                class="ring-label"
              >掌握度</text>
            </svg>
          </div>
        </section>

        <!-- ── 今日推荐 ── -->
        <section class="section recommend-section">
          <h2 class="section__title">
            今日推荐
          </h2>
          <p
            v-if="loadingRecs"
            class="empty-hint"
          >
            加载推荐中…
          </p>
          <p
            v-else-if="recommendations.length === 0"
            class="empty-hint"
          >
            暂无推荐资源，去工坊生成属于你的学习资料吧
          </p>
          <ul
            v-else
            class="rec-cards"
          >
            <li
              v-for="rec in recommendations"
              :key="rec.resourceId"
              class="rec-card"
              @click="onRecommendClick(rec)"
            >
              <span
                class="rec-card__icon"
                aria-hidden="true"
              >{{ typeIcon(rec.type) }}</span>
              <div class="rec-card__body">
                <span class="rec-card__title">{{ rec.title }}</span>
                <span class="rec-card__kc">{{ rec.kcCode }} · {{ rec.kcName }}</span>
                <span class="rec-card__reason">{{ rec.reason }}</span>
              </div>
            </li>
          </ul>
        </section>

        <!-- ── 今日任务 ── -->
        <section class="section quest-section">
          <h2 class="section__title">
            今日任务
          </h2>
          <p
            v-if="loadingQuests"
            class="empty-hint"
          >
            加载任务中…
          </p>
          <p
            v-else-if="todayQuests.length === 0"
            class="empty-hint"
          >
            今日无待办任务
          </p>
          <ul
            v-else
            class="quest-chips"
          >
            <li
              v-for="quest in todayQuests"
              :key="quest.id"
              class="quest-chip"
              :class="`quest-chip--${quest.status.toLowerCase()}`"
            >
              <span class="quest-chip__title">{{ quest.title }}</span>
              <span class="quest-chip__status">{{ questStatusLabel(quest.status) }}</span>
            </li>
          </ul>
        </section>

        <!-- ── CTA 区域 ── -->
        <div class="cta-row">
          <button
            type="button"
            class="cta-main"
            data-testid="cta-world"
            @click="goWorld"
          >
            继续学习
          </button>
          <button
            type="button"
            class="cta-secondary"
            data-testid="cta-tutor"
            @click="goTutor"
          >
            问导师
          </button>
        </div>

        <!-- ── 快速导航 ── -->
        <nav
          class="quick-actions"
          aria-label="快速导航"
        >
          <button
            type="button"
            class="quick-btn"
            @click="goTo('/workshop')"
          >
            <span
              class="quick-btn__icon"
              aria-hidden="true"
            >⚒</span>
            <span class="quick-btn__label">工坊</span>
          </button>
          <button
            type="button"
            class="quick-btn"
            @click="goTo('/library')"
          >
            <span
              class="quick-btn__icon"
              aria-hidden="true"
            >[L]</span>
            <span class="quick-btn__label">图书馆</span>
          </button>
          <button
            type="button"
            class="quick-btn"
            @click="goTo('/problems')"
          >
            <span
              class="quick-btn__icon"
              aria-hidden="true"
            >✪</span>
            <span class="quick-btn__label">题库</span>
          </button>
          <button
            type="button"
            class="quick-btn"
            @click="goTo('/review-hall')"
          >
            <span
              class="quick-btn__icon"
              aria-hidden="true"
            >☆</span>
            <span class="quick-btn__label">复盘</span>
          </button>
        </nav>
      </template>

      <!-- ── Unauthenticated Welcome ── -->
      <template v-else>
        <div class="guest-area">
          <h1 class="guest-title">
            Pythontopia
          </h1>
          <p class="guest-sub">
            面向高校 Python 初学者的像素学习小镇
          </p>
          <button
            type="button"
            class="cta-main"
            @click="goLogin"
          >
            登录 / 注册
          </button>
        </div>
      </template>
    </main>
  </AppLayout>
</template>

<style scoped>
.home-page {
  min-height: calc(100dvh - 56px);
  padding: 24px 20px;
  background:
    linear-gradient(180deg, rgba(255, 247, 230, 0.5), rgba(243, 228, 190, 0.7)),
    repeating-linear-gradient(135deg, rgba(110, 79, 29, 0.05) 0 6px, transparent 6px 14px),
    #fef6dd;
  font-family: var(--pyt-font-pixel, monospace);
  color: #3a2a14;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

/* ── Welcome Area ── */
.welcome-area {
  display: flex;
  align-items: center;
  gap: 24px;
  width: 100%;
  max-width: 720px;
  padding: 16px 20px;
  background: linear-gradient(180deg, #f9e7bc 0%, #f1d896 100%);
  border: 4px solid #6e4f1d;
  border-radius: 10px;
  box-shadow: 0 4px 0 rgba(0, 0, 0, 0.18), inset 0 0 0 2px rgba(255, 247, 230, 0.55);
}
.welcome-text {
  flex: 1 1 auto;
  min-width: 0;
}
.welcome-title {
  margin: 0;
  font-size: 22px;
  color: #4d3613;
  line-height: 1.3;
}
.welcome-class {
  margin: 4px 0 0;
  font-size: 13px;
  color: #6e4f1d;
}
.welcome-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: #8a6c2c;
}

/* ── Mastery Ring ── */
.mastery-ring-wrap {
  flex: 0 0 auto;
  width: 96px;
  height: 96px;
}
.mastery-ring {
  width: 100%;
  height: 100%;
}
.mastery-ring__fill {
  transition: stroke-dashoffset 600ms ease-out;
}
.ring-pct {
  font-family: var(--pyt-font-pixel, monospace);
  font-size: 20px;
  font-weight: 700;
  fill: #4d3613;
}
.ring-label {
  font-family: var(--pyt-font-pixel, monospace);
  font-size: 10px;
  fill: #8a6c2c;
}

/* ── Sections ── */
.section {
  width: 100%;
  max-width: 720px;
}
.section__title {
  margin: 0 0 10px;
  font-size: 16px;
  color: #4d3613;
  border-bottom: 2px dashed rgba(110, 79, 29, 0.35);
  padding-bottom: 4px;
}
.empty-hint {
  margin: 0;
  padding: 14px;
  text-align: center;
  background: #fff7e6;
  border: 2px dashed #c9a86b;
  border-radius: 6px;
  font-size: 13px;
  color: #8a6c2c;
}

/* ── Recommendation Cards ── */
.rec-cards {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.rec-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 14px;
  background: #fff7e6;
  border: 3px solid #c9a86b;
  border-radius: 8px;
  cursor: pointer;
  transition: background 120ms, border-color 120ms, transform 80ms;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.1);
}
.rec-card:hover {
  background: #f5e2c1;
  border-color: #6e4f1d;
  transform: translateY(-1px);
  box-shadow: 0 4px 0 rgba(0, 0, 0, 0.14);
}
.rec-card:active {
  transform: translateY(1px);
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.1);
}
.rec-card__icon {
  font-size: 20px;
  color: #d4af37;
  flex: 0 0 auto;
  margin-top: 2px;
}
.rec-card__body {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.rec-card__title {
  font-size: 14px;
  font-weight: 700;
  color: #4d3613;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rec-card__kc {
  font-size: 11px;
  color: #6e4f1d;
  font-family: var(--pyt-font-pixel-mono, monospace);
}
.rec-card__reason {
  font-size: 12px;
  color: #8a6c2c;
  line-height: 1.4;
}

/* ── Quest Chips ── */
.quest-chips {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.quest-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: #fff7e6;
  border: 2px solid #c9a86b;
  border-radius: 6px;
  font-size: 13px;
}
.quest-chip__title {
  color: #4d3613;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 180px;
}
.quest-chip__status {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 700;
}
.quest-chip--open .quest-chip__status {
  background: rgba(212, 175, 55, 0.25);
  color: #8a6c2c;
}
.quest-chip--in_progress .quest-chip__status {
  background: rgba(107, 191, 89, 0.25);
  color: #2d5b1b;
}
.quest-chip--completed .quest-chip__status {
  background: rgba(120, 180, 80, 0.3);
  color: #1a4d0a;
}

/* ── CTA Row ── */
.cta-row {
  display: flex;
  gap: 12px;
  width: 100%;
  max-width: 720px;
  justify-content: center;
}
.cta-main {
  font-family: var(--pyt-font-pixel, monospace);
  font-size: 16px;
  font-weight: 700;
  padding: 10px 32px;
  background: #f7d774;
  color: #2a1a08;
  border: 4px solid #8c6a23;
  border-radius: 6px;
  box-shadow: 0 4px 0 #8c6a23;
  cursor: pointer;
  transition: transform 80ms, box-shadow 80ms;
}
.cta-main:hover {
  background: #fde48a;
}
.cta-main:active {
  transform: translateY(3px);
  box-shadow: 0 1px 0 #8c6a23;
}
.cta-secondary {
  font-family: var(--pyt-font-pixel, monospace);
  font-size: 14px;
  font-weight: 700;
  padding: 10px 24px;
  background: #fff7e6;
  color: #4d3613;
  border: 3px solid #c9a86b;
  border-radius: 6px;
  box-shadow: 0 3px 0 rgba(0, 0, 0, 0.12);
  cursor: pointer;
  transition: transform 80ms, box-shadow 80ms;
}
.cta-secondary:hover {
  background: #f5e2c1;
  border-color: #6e4f1d;
}
.cta-secondary:active {
  transform: translateY(2px);
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.1);
}

/* ── Quick Actions ── */
.quick-actions {
  display: flex;
  gap: 10px;
  width: 100%;
  max-width: 720px;
  justify-content: center;
}
.quick-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 10px 18px;
  background: linear-gradient(180deg, #f9e7bc 0%, #f1d896 100%);
  border: 3px solid #c9a86b;
  border-radius: 8px;
  cursor: pointer;
  font-family: inherit;
  transition: background 120ms, border-color 120ms, transform 80ms;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.1);
  min-width: 72px;
}
.quick-btn:hover {
  border-color: #6e4f1d;
  background: #f5e2c1;
  transform: translateY(-1px);
  box-shadow: 0 4px 0 rgba(0, 0, 0, 0.14);
}
.quick-btn:active {
  transform: translateY(1px);
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.1);
}
.quick-btn__icon {
  font-size: 22px;
  color: #d4af37;
}
.quick-btn__label {
  font-size: 12px;
  color: #4d3613;
  font-weight: 700;
}

/* ── Guest Area ── */
.guest-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 80px 20px;
}
.guest-title {
  margin: 0;
  font-size: 48px;
  color: #4d3613;
  letter-spacing: 2px;
}
.guest-sub {
  margin: 0;
  font-size: 16px;
  color: #6e4f1d;
}

/* ── Responsive ── */
@media (max-width: 640px) {
  .home-page {
    padding: 16px 12px;
    gap: 16px;
  }
  .welcome-area {
    flex-direction: column;
    align-items: center;
    text-align: center;
    gap: 12px;
  }
  .welcome-title {
    font-size: 18px;
  }
  .mastery-ring-wrap {
    width: 80px;
    height: 80px;
  }
  .rec-cards {
    gap: 6px;
  }
  .quick-actions {
    flex-wrap: wrap;
  }
  .cta-row {
    flex-direction: column;
    align-items: center;
  }
  .guest-title {
    font-size: 32px;
  }
}
</style>
