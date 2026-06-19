<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import PyButton from '@/components/ui/PyButton.vue';
import MetricCard from './MetricCard.vue';
import KcGrowthList from './KcGrowthList.vue';
import PathDiffView from './PathDiffView.vue';
import { useLearningStore } from '@stores/learning';
import { useToastStore } from '@stores/toast';
import type { EvaluationActedStatus, EvaluationDto } from '@/api/dto/Evaluation';

const learning = useLearningStore();
const toast = useToastStore();
const runPhase = ref(false);
// 详情弹窗的开合与「当前评估」解耦：列表点击/立即评估完成才打开，页面加载不自动弹。
const detailOpen = ref(false);

const list = computed<EvaluationDto[]>(() => learning.evaluation.list);
const current = computed<EvaluationDto | null>(() => learning.evaluation.current);
const loading = computed(() => learning.evaluation.loading);
const lastError = computed(() => learning.evaluation.lastError);
const isPending = computed(() => current.value?.actedStatus === 'PENDING');

// ── 周分组 ────────────────────────────────────────────────
interface WeekGroup {
  key: string;
  title: string;
  rangeLabel: string;
  entries: EvaluationDto[];
}

/** 取某时间点所在周的周一 00:00（本地时区，周一为一周起点）。 */
function startOfWeek(iso: string | Date): Date {
  const d = new Date(iso);
  d.setHours(0, 0, 0, 0);
  const mondayOffset = (d.getDay() + 6) % 7;
  d.setDate(d.getDate() - mondayOffset);
  return d;
}

function md(date: Date): string {
  return `${date.getMonth() + 1}月${date.getDate()}日`;
}

const weekGroups = computed<WeekGroup[]>(() => {
  const thisMonday = startOfWeek(new Date()).getTime();
  const buckets = new Map<string, EvaluationDto[]>();
  for (const entry of list.value) {
    const key = startOfWeek(entry.createdAt).toISOString().slice(0, 10);
    const bucket = buckets.get(key);
    if (bucket) bucket.push(entry);
    else buckets.set(key, [entry]);
  }

  const groups: WeekGroup[] = [];
  for (const [key, entries] of buckets) {
    const monday = new Date(`${key}T00:00:00`);
    const sunday = new Date(monday);
    sunday.setDate(monday.getDate() + 6);
    const weeksAgo = Math.round((thisMonday - monday.getTime()) / (7 * 86_400_000));
    const rangeLabel = `${md(monday)} – ${md(sunday)}`;
    let title = rangeLabel;
    if (weeksAgo === 0) title = '本周';
    else if (weeksAgo === 1) title = '上周';
    groups.push({
      key,
      title,
      rangeLabel,
      entries: [...entries].sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
    });
  }
  return groups.sort((a, b) => b.key.localeCompare(a.key));
});

// ── 展示工具 ──────────────────────────────────────────────
const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];

function rowDate(iso: string): string {
  const d = new Date(iso);
  return `${d.getMonth() + 1}月${d.getDate()}日 ${WEEKDAYS[d.getDay()]}`;
}
function rowTime(iso: string): string {
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}
function periodLabel(entry: EvaluationDto): string {
  return `评估周期 ${md(new Date(entry.periodStart))} – ${md(new Date(entry.periodEnd))}`;
}

const STATUS_LABEL: Record<EvaluationActedStatus, string> = {
  PENDING: '待处理',
  ACCEPTED: '已采纳新路径',
  KEPT_OLD: '保留原路径',
  PARTIAL: '数据不足',
};
function statusLabel(status: EvaluationActedStatus): string {
  return STATUS_LABEL[status] ?? status;
}

// ── 交互 ──────────────────────────────────────────────────
async function openRow(id: number): Promise<void> {
  detailOpen.value = true;
  await learning.openEvaluation(id);
}

function closeDetail(): void {
  detailOpen.value = false;
}

async function runEvaluation(): Promise<void> {
  runPhase.value = true;
  try {
    const created = await learning.runEvaluationNow({ periodDays: 7 });
    if (created) {
      detailOpen.value = true;
      toast.show('评估完成，已生成新路径建议。', 'success');
    } else if (learning.evaluation.lastError) {
      toast.show(learning.evaluation.lastError, 'error');
    }
  } finally {
    runPhase.value = false;
  }
}

async function accept(): Promise<void> {
  if (!current.value) return;
  const updated = await learning.acceptEvaluationPath(current.value.id);
  if (updated) {
    toast.show('已接受新路径，旧路径已归档。', 'success');
  } else if (learning.evaluation.lastError) {
    toast.show(learning.evaluation.lastError, 'error');
  }
}

async function keepOld(): Promise<void> {
  if (!current.value) return;
  const updated = await learning.keepCurrentEvaluationPath(current.value.id);
  if (updated) {
    toast.show('已保留当前路径，DRAFT 已归档。', 'info');
  } else if (learning.evaluation.lastError) {
    toast.show(learning.evaluation.lastError, 'error');
  }
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && detailOpen.value) {
    event.preventDefault();
    closeDetail();
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown));
onUnmounted(() => window.removeEventListener('keydown', onKeydown));
</script>

<template>
  <section
    class="review"
    data-testid="review-hall-panel"
  >
    <header class="review__head">
      <div class="review__intro">
        <h2 class="review__title">
          复盘大厅
        </h2>
      </div>
      <PyButton
        variant="primary"
        :disabled="runPhase"
        test-id="run-evaluation"
        @click="runEvaluation"
      >
        {{ runPhase ? '评估中…' : '＋ 立即评估' }}
      </PyButton>
    </header>

    <p
      v-if="lastError"
      class="review__error"
      role="alert"
    >
      {{ lastError }}
    </p>

    <section class="history">
      <h3 class="history__title">
        历史周报
      </h3>

      <template v-if="weekGroups.length">
        <div
          v-for="group in weekGroups"
          :key="group.key"
          class="week"
        >
          <div class="week__header">
            <span class="week__name">{{ group.title }}</span>
            <span class="week__range">{{ group.rangeLabel }}</span>
            <span class="week__count">{{ group.entries.length }} 份</span>
          </div>
          <ul class="week__list">
            <li
              v-for="entry in group.entries"
              :key="entry.id"
            >
              <button
                type="button"
                class="eval-row"
                data-testid="eval-row"
                :class="{ 'eval-row--active': detailOpen && current && current.id === entry.id }"
                @click="openRow(entry.id)"
              >
                <span class="eval-row__main">
                  <span class="eval-row__day">{{ rowDate(entry.createdAt) }}</span>
                  <span class="eval-row__sub">{{ rowTime(entry.createdAt) }} · {{ periodLabel(entry) }}</span>
                </span>
                <span class="eval-row__aside">
                  <span
                    class="status-pill"
                    :class="`status-pill--${entry.actedStatus.toLowerCase()}`"
                  >
                    {{ statusLabel(entry.actedStatus) }}
                  </span>
                  <span
                    class="eval-row__chevron"
                    aria-hidden="true"
                  >›</span>
                </span>
              </button>
            </li>
          </ul>
        </div>
      </template>

      <p
        v-else
        class="history__empty"
      >
        还没有评估。建议先在小镇里完成画像、解锁 ≥ 1 个 KC、提交 1 道练习，再点击右上角“立即评估”。
      </p>
    </section>

    <!-- 详情弹窗：点击周报某一天后，以模态展示该次评估摘要 -->
    <transition name="pyt-fade">
      <div
        v-if="detailOpen"
        class="detail-mask"
        data-testid="evaluation-modal"
        role="dialog"
        aria-modal="true"
        aria-label="评估详情"
        @click.self="closeDetail"
      >
        <transition name="pyt-scale">
          <div
            v-if="detailOpen"
            class="detail-card"
          >
            <header class="detail-card__head">
              <div class="detail-card__heading">
                <h3 class="detail-card__title">
                  {{ current ? `评估 #${current.id}` : '加载中…' }}
                </h3>
                <span
                  v-if="current"
                  class="status-pill"
                  :class="`status-pill--${current.actedStatus.toLowerCase()}`"
                >
                  {{ statusLabel(current.actedStatus) }}
                </span>
              </div>
              <button
                type="button"
                class="detail-card__close"
                aria-label="关闭"
                data-testid="evaluation-modal-close"
                @click="closeDetail"
              >
                ✕
              </button>
            </header>

            <div class="detail-card__body pyt-scroll">
              <div
                v-if="loading || !current"
                class="detail-card__loading"
              >
                加载中…
              </div>
              <template v-else>
                <p class="detail-card__meta">
                  生成于 {{ rowDate(current.createdAt) }} {{ rowTime(current.createdAt) }} · {{ periodLabel(current) }}
                  <span class="ai-tag">AI 生成解释 · 仅作建议</span>
                </p>
                <div class="metric-grid">
                  <MetricCard
                    v-for="metric in current.metrics"
                    :key="metric.metricKey"
                    :metric="metric"
                  />
                </div>
                <KcGrowthList :evaluation="current" />
                <PathDiffView
                  :evaluation="current"
                  :active-path="learning.pathState.active"
                />
              </template>
            </div>

            <footer
              v-if="current && isPending"
              class="detail-card__foot"
            >
              <PyButton
                variant="primary"
                test-id="accept-new-path"
                :disabled="loading || !current.draftPathId"
                @click="accept"
              >
                接受新路径
              </PyButton>
              <PyButton
                variant="secondary"
                test-id="keep-old"
                :disabled="loading"
                @click="keepOld"
              >
                保留当前
              </PyButton>
            </footer>
            <footer
              v-else-if="current"
              class="detail-card__foot detail-card__foot--note"
            >
              <span>当前状态：{{ statusLabel(current.actedStatus) }} · 操作已完成，可再次触发评估。</span>
            </footer>
          </div>
        </transition>
      </div>
    </transition>
  </section>
</template>

<style scoped>
.review {
  background: linear-gradient(180deg, var(--pyt-color-bg-elevated) 0%, var(--pyt-color-bg-soft) 100%);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  padding: 18px;
  display: grid;
  gap: 16px;
}

/* ── 顶部 ─────────────────────────────────────────────── */
.review__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}
.review__title {
  margin: 0;
  font-size: 20px;
  color: var(--pyt-color-text-primary);
}
.review__error {
  background: var(--pyt-color-danger-bg);
  border: 1px solid var(--pyt-color-danger);
  color: var(--pyt-color-danger);
  border-radius: 6px;
  padding: 8px 10px;
  margin: 0;
  font-size: 12px;
}

/* ── 周报列表 ─────────────────────────────────────────── */
.history__title {
  margin: 0 0 10px;
  color: var(--pyt-color-text-primary);
  font-size: 15px;
}

.week {
  margin-bottom: 14px;
}
.week:last-child {
  margin-bottom: 0;
}
.week__header {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 4px 2px 8px;
  border-bottom: 2px solid var(--pyt-color-border);
}
.week__name {
  font-weight: 700;
  font-size: 14px;
  color: var(--pyt-color-text-primary);
}
.week__range {
  font-size: 11px;
  color: var(--pyt-color-text-muted);
  font-family: 'JetBrains Mono', monospace;
}
.week__count {
  margin-left: auto;
  font-size: 11px;
  color: var(--pyt-color-text-hint);
}

.week__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.eval-row {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  background: transparent;
  border: none;
  border-bottom: 1px solid var(--pyt-color-border);
  cursor: pointer;
  text-align: left;
  color: var(--pyt-color-text-primary);
  transition: background 120ms ease, padding-left 120ms ease;
}
.eval-row:hover {
  background: var(--pyt-color-bg-elevated);
  padding-left: 18px;
}
.eval-row:focus-visible {
  outline: 2px solid var(--pyt-color-primary);
  outline-offset: -2px;
}
.eval-row--active {
  background: var(--pyt-color-bg-elevated);
  box-shadow: inset 3px 0 0 var(--pyt-color-primary);
}
.eval-row__main {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}
.eval-row__day {
  font-size: 14px;
  font-weight: 600;
}
.eval-row__sub {
  font-size: 11px;
  color: var(--pyt-color-text-muted);
}
.eval-row__aside {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.eval-row__chevron {
  font-size: 18px;
  color: var(--pyt-color-text-muted);
}

/* ── 状态徽标 ─────────────────────────────────────────── */
.status-pill {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid var(--pyt-color-border);
  background: var(--pyt-color-bg-sunken);
  color: var(--pyt-color-text-hint);
  white-space: nowrap;
}
.status-pill--pending {
  background: var(--pyt-color-warning-bg);
  color: var(--pyt-color-text-hint);
  border-color: var(--pyt-color-warning);
}
.status-pill--accepted {
  background: var(--pyt-color-success-bg);
  color: var(--pyt-color-success);
  border-color: var(--pyt-color-success);
}
.status-pill--kept_old,
.status-pill--partial {
  background: var(--pyt-color-bg-sunken);
  color: var(--pyt-color-text-muted);
}

.history__empty {
  margin: 0;
  font-size: 12px;
  color: var(--pyt-color-text-muted);
}

/* ── 详情弹窗 ─────────────────────────────────────────── */
.detail-mask {
  position: fixed;
  inset: 0;
  z-index: var(--pyt-z-modal, 1000);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgba(8, 8, 12, 0.62);
  backdrop-filter: blur(3px);
}
.detail-card {
  width: min(720px, 100%);
  max-height: min(86vh, 86dvh);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--pyt-color-bg-soft);
  border: 2px solid var(--pyt-color-border-strong, var(--pyt-color-border));
  border-radius: var(--pyt-radius-lg);
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.45);
}
.detail-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 2px solid var(--pyt-color-border);
  background: var(--pyt-color-bg-elevated);
}
.detail-card__heading {
  display: flex;
  align-items: center;
  gap: 10px;
}
.detail-card__title {
  margin: 0;
  font-size: 16px;
  color: var(--pyt-color-text-primary);
}
.detail-card__close {
  width: 30px;
  height: 30px;
  border: 2px solid var(--pyt-color-border);
  border-radius: 6px;
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-text-primary);
  cursor: pointer;
  font-size: 13px;
  line-height: 1;
}
.detail-card__close:hover {
  background: var(--pyt-color-danger-bg);
  border-color: var(--pyt-color-danger);
  color: var(--pyt-color-danger);
}
.detail-card__body {
  flex: 1 1 auto;
  overflow-y: auto;
  min-height: 0;
  padding: 16px;
  display: grid;
  gap: 12px;
}
.detail-card__meta {
  margin: 0;
  font-size: 12px;
  color: var(--pyt-color-text-hint);
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.ai-tag {
  background: var(--pyt-color-bg-sunken);
  border: 1px dashed var(--pyt-color-border);
  color: var(--pyt-color-text-hint);
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
}
.detail-card__loading {
  font-size: 13px;
  color: var(--pyt-color-text-hint);
  padding: 24px 0;
  text-align: center;
}
.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 10px;
}
.detail-card__foot {
  display: flex;
  gap: 10px;
  padding: 12px 16px;
  border-top: 2px solid var(--pyt-color-border);
  background: var(--pyt-color-bg-elevated);
}
.detail-card__foot--note {
  font-size: 12px;
  color: var(--pyt-color-text-hint);
}

@media (max-width: 640px) {
  .review__head {
    flex-direction: column;
  }
  .detail-card__body {
    padding: 12px;
  }
}
</style>
