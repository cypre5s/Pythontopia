<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import PyButton from '@/components/ui/PyButton.vue';
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
        /* 推荐为可选数据，加载失败忽略 */
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
        /* 班级信息为可选数据，加载失败忽略 */
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
      <!-- ── 已登录仪表盘 ── -->
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
                stroke-width="6"
                class="mastery-ring__track"
              />
              <circle
                cx="50"
                cy="50"
                r="42"
                fill="none"
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
                <span class="rec-card__kc">{{ rec.kcName }}</span>
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
          <PyButton
            test-id="cta-world"
            variant="primary"
            @click="goWorld"
          >
            继续学习
          </PyButton>
          <PyButton
            test-id="cta-tutor"
            variant="secondary"
            @click="goTutor"
          >
            问导师
          </PyButton>
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

      <!-- ── 未登录欢迎 ── -->
      <template v-else>
        <div class="guest-area">
          <h1 class="guest-title">
            Pythontopia
          </h1>
          <p class="guest-sub">
            面向高校 Python 初学者的像素学习小镇
          </p>
          <PyButton
            variant="primary"
            @click="goLogin"
          >
            登录 / 注册
          </PyButton>
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
    var(--pyt-color-bg-primary);
  font-family: var(--pyt-font-pixel);
  color: var(--pyt-color-text-primary);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

/* ── 欢迎区 ── */
.welcome-area {
  display: flex;
  align-items: center;
  gap: 24px;
  width: 100%;
  max-width: 720px;
  padding: 16px 20px;
  background: linear-gradient(180deg, var(--pyt-color-bg-soft) 0%, var(--pyt-color-bg-sunken) 100%);
  border: 4px solid var(--pyt-color-text-hint);
  border-radius: var(--pyt-radius-lg);
  box-shadow: 0 4px 0 rgba(0, 0, 0, 0.18), inset 0 0 0 2px rgba(255, 247, 230, 0.55);
}
.welcome-text {
  flex: 1 1 auto;
  min-width: 0;
}
.welcome-title {
  margin: 0;
  font-size: 22px;
  color: var(--pyt-color-text-primary);
  line-height: 1.3;
}
.welcome-class {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--pyt-color-text-hint);
}
.welcome-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--pyt-color-text-muted);
}

/* ── 掌握度环 ── */
.mastery-ring-wrap {
  flex: 0 0 auto;
  width: 96px;
  height: 96px;
}
.mastery-ring {
  width: 100%;
  height: 100%;
}
.mastery-ring__track {
  stroke: var(--pyt-color-border);
}
.mastery-ring__fill {
  stroke: var(--pyt-color-success);
  transition: stroke-dashoffset 600ms ease-out;
}
.ring-pct {
  font-family: var(--pyt-font-pixel);
  font-size: 20px;
  font-weight: 700;
  fill: var(--pyt-color-text-primary);
}
.ring-label {
  font-family: var(--pyt-font-pixel);
  font-size: 10px;
  fill: var(--pyt-color-text-muted);
}

/* ── 区块 ── */
.section {
  width: 100%;
  max-width: 720px;
}
.section__title {
  margin: 0 0 10px;
  font-size: 16px;
  color: var(--pyt-color-text-primary);
  border-bottom: 2px dashed rgba(110, 79, 29, 0.35);
  padding-bottom: 4px;
}
.empty-hint {
  margin: 0;
  padding: 14px;
  text-align: center;
  background: var(--pyt-color-bg-elevated);
  border: 2px dashed var(--pyt-color-border);
  border-radius: 6px;
  font-size: 13px;
  color: var(--pyt-color-text-muted);
}

/* ── 推荐卡片 ── */
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
  background: var(--pyt-color-bg-elevated);
  border: 3px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  cursor: pointer;
  transition: background 120ms, border-color 120ms, transform 80ms;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.1);
}
.rec-card:hover {
  background: var(--pyt-color-warning-bg);
  border-color: var(--pyt-color-text-hint);
  transform: translateY(-1px);
  box-shadow: 0 4px 0 rgba(0, 0, 0, 0.14);
}
.rec-card:active {
  transform: translateY(1px);
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.1);
}
.rec-card__icon {
  font-size: 20px;
  color: var(--pyt-color-gold);
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
  color: var(--pyt-color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rec-card__kc {
  font-size: 11px;
  color: var(--pyt-color-text-hint);
  font-family: var(--pyt-font-pixel-mono, monospace);
}
.rec-card__reason {
  font-size: 12px;
  color: var(--pyt-color-text-muted);
  line-height: 1.4;
}

/* ── 任务标签 ── */
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
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border);
  border-radius: 6px;
  font-size: 13px;
}
.quest-chip__title {
  color: var(--pyt-color-text-primary);
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
  color: var(--pyt-color-text-muted);
}
.quest-chip--in_progress .quest-chip__status {
  background: rgba(107, 191, 89, 0.25);
  color: var(--pyt-color-success);
}
.quest-chip--completed .quest-chip__status {
  background: rgba(120, 180, 80, 0.3);
  color: var(--pyt-color-success);
}

/* ── CTA 按钮行 ── */
.cta-row {
  display: flex;
  gap: 12px;
  width: 100%;
  max-width: 720px;
  justify-content: center;
}
/* ── 快速导航 ── */
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
  background: linear-gradient(180deg, var(--pyt-color-bg-soft) 0%, var(--pyt-color-bg-sunken) 100%);
  border: 3px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  cursor: pointer;
  font-family: inherit;
  transition: background 120ms, border-color 120ms, transform 80ms;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.1);
  min-width: 72px;
}
.quick-btn:hover {
  border-color: var(--pyt-color-text-hint);
  background: var(--pyt-color-warning-bg);
  transform: translateY(-1px);
  box-shadow: 0 4px 0 rgba(0, 0, 0, 0.14);
}
.quick-btn:active {
  transform: translateY(1px);
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.1);
}
.quick-btn__icon {
  font-size: 22px;
  color: var(--pyt-color-gold);
}
.quick-btn__label {
  font-size: 12px;
  color: var(--pyt-color-text-primary);
  font-weight: 700;
}

/* ── 访客区 ── */
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
  color: var(--pyt-color-text-primary);
  letter-spacing: 2px;
}
.guest-sub {
  margin: 0;
  font-size: 16px;
  color: var(--pyt-color-text-hint);
}

/* ── 响应式 ── */
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
