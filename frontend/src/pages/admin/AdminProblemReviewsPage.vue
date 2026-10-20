<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue';
import { useToastStore } from '@stores/toast';
import {
  decideProblemReview,
  searchProblemReviews,
  type BossProblemReviewDecision,
  type BossProblemReviewDto,
  type BossProblemReviewSortBy,
  type BossProblemReviewSortDirection,
} from '@/api/problemReview';
import AdminPanel from './AdminPanel.vue';
import AdminTable from './AdminTable.vue';
import AdminPagination from './AdminPagination.vue';
import { kcDisplayName } from '@/data/kcNames';

type DecisionFilter = 'ALL' | BossProblemReviewDecision;
type FinalDecision = Exclude<BossProblemReviewDecision, 'PENDING'>;

const toast = useToastStore();
const entries = ref<BossProblemReviewDto[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);
const selectedId = ref<number | null>(null);
const filter = ref<DecisionFilter>('ALL');
const sortBy = ref<BossProblemReviewSortBy>('REQUESTED_AT');
const sortDirection = ref<BossProblemReviewSortDirection>('DESC');
const searchInput = ref('');
const searchTerm = ref('');
const page = ref(0);
const totalPages = ref(0);
const totalElements = ref(0);
const pageSize = ref(20);
const pendingDecision = ref<FinalDecision | null>(null);
const decisionReason = ref('');
const decisionError = ref<string | null>(null);
const deciding = ref(false);
const reviewPage = ref<HTMLElement | null>(null);
const detailDialog = ref<HTMLDialogElement | null>(null);
const decisionDialog = ref<HTMLDialogElement | null>(null);
const decisionTrigger = ref<HTMLButtonElement | null>(null);
const detailTrigger = ref<HTMLButtonElement | null>(null);

const selectedEntry = computed(
  () => entries.value.find((entry) => entry.id === selectedId.value) ?? null,
);

const counts = computed(() => ({
  pending: entries.value.filter((entry) => entry.decision === 'PENDING').length,
  approved: entries.value.filter((entry) => entry.decision === 'APPROVED').length,
  rejected: entries.value.filter((entry) => entry.decision === 'REJECTED').length,
}));

function decisionLabel(decision: BossProblemReviewDecision): string {
  if (decision === 'APPROVED') return '已通过';
  if (decision === 'REJECTED') return '已驳回';
  return '待审核';
}

function decisionClass(decision: BossProblemReviewDecision): string {
  return `review-status--${decision.toLowerCase()}`;
}

function reviewSourceLabel(source: BossProblemReviewDto['reviewSource']): string {
  return source === 'AUTOMATED' ? 'AI 自动审核' : '教师审核';
}

function canDecide(entry: BossProblemReviewDto | null): boolean {
  return entry !== null
    && (entry.decision === 'PENDING'
      || (entry.decision === 'APPROVED' && entry.reviewSource === 'AUTOMATED'));
}

function decisionTone(decision: BossProblemReviewDecision): string {
  if (decision === 'APPROVED') return 'admin-status--positive';
  if (decision === 'REJECTED') return 'admin-status--danger';
  return 'admin-status--warning';
}

function shortStatement(statement: string): string {
  const singleLine = statement.replace(/\s+/g, ' ').trim();
  return singleLine.length > 110 ? `${singleLine.slice(0, 110)}…` : singleLine;
}

function formatDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(date);
}

async function loadQueue(): Promise<void> {
  if (loading.value) return;
  loading.value = true;
  error.value = null;
  try {
    const readPage = () => searchProblemReviews({
      page: page.value,
      size: pageSize.value,
      sortBy: sortBy.value,
      sortDirection: sortDirection.value,
      ...(filter.value === 'ALL' ? {} : { decision: filter.value }),
      ...(searchTerm.value ? { q: searchTerm.value } : {}),
    });
    let result = await readPage();
    const lastPage = Math.max(0, result.totalPages - 1);
    if (page.value > lastPage) {
      page.value = lastPage;
      result = await readPage();
    }
    const selectionStillPresent = result.content.some((entry) => entry.id === selectedId.value);
    if (!selectionStillPresent && detailDialog.value?.open) closeDetail(false);
    entries.value = result.content;
    totalElements.value = result.totalElements;
    totalPages.value = result.totalPages;
    if (!result.content.some((entry) => entry.id === selectedId.value)) {
      selectedId.value = result.content[0]?.id ?? null;
    }
  } catch (cause) {
    entries.value = [];
    selectedId.value = null;
    totalElements.value = 0;
    totalPages.value = 0;
    error.value = cause instanceof Error ? cause.message : '审核队列暂不可用';
  } finally {
    loading.value = false;
  }
}

function applyQueueFilters(): void {
  page.value = 0;
  void loadQueue();
}

function searchQueue(): void {
  searchTerm.value = searchInput.value.trim();
  applyQueueFilters();
}

function goToPage(nextPage: number): void {
  if (nextPage < 0 || nextPage >= totalPages.value || loading.value) return;
  page.value = nextPage;
  void loadQueue();
}

function changePageSize(nextSize: number): void {
  if (loading.value || nextSize === pageSize.value) return;
  pageSize.value = nextSize;
  page.value = 0;
  void loadQueue();
}

function openDetail(entry: BossProblemReviewDto, event: MouseEvent): void {
  selectedId.value = entry.id;
  detailTrigger.value = event.currentTarget as HTMLButtonElement;
  void nextTick(() => detailDialog.value?.showModal());
}

function closeDetail(restoreTrigger = true): void {
  detailDialog.value?.close();
  void nextTick(() => {
    if (restoreTrigger && detailTrigger.value?.isConnected) detailTrigger.value.focus();
    else reviewPage.value?.focus();
  });
}

function openDecision(
  decision: FinalDecision,
  event: MouseEvent,
  entry: BossProblemReviewDto | null = selectedEntry.value,
): void {
  if (!entry || !canDecide(entry) || loading.value || deciding.value) return;
  selectedId.value = entry.id;
  decisionTrigger.value = event.currentTarget as HTMLButtonElement;
  pendingDecision.value = decision;
  decisionReason.value = '';
  decisionError.value = null;
  void nextTick(() => {
    decisionDialog.value?.showModal();
    decisionDialog.value?.querySelector('textarea')?.focus();
  });
}

function closeDecision(): void {
  if (deciding.value) return;
  dismissDecision();
}

function dismissDecision(restoreTrigger = true): void {
  decisionDialog.value?.close();
  pendingDecision.value = null;
  decisionReason.value = '';
  decisionError.value = null;
  void nextTick(() => {
    if (restoreTrigger && decisionTrigger.value?.isConnected) decisionTrigger.value.focus();
    else reviewPage.value?.focus();
  });
}

async function submitDecision(): Promise<void> {
  const entry = selectedEntry.value;
  const decision = pendingDecision.value;
  const reason = decisionReason.value.trim();
  if (!entry || !canDecide(entry) || !decision || deciding.value) return;
  if (!reason) {
    decisionError.value = '请填写审核意见。';
    return;
  }

  deciding.value = true;
  decisionError.value = null;
  const isRemovingAutoApproved = decision === 'REJECTED'
    && entry.decision === 'APPROVED'
    && entry.reviewSource === 'AUTOMATED';
  try {
    const updated = await decideProblemReview(entry.id, {
      decision,
      reason,
      version: entry.version,
    });
    entries.value = entries.value.map((item) => item.id === updated.id ? updated : item);
    dismissDecision(false);
    closeDetail(false);
    toast.show(
      decision === 'APPROVED'
        ? '题目审核已通过。'
        : isRemovingAutoApproved ? '题目已撤下并留档。' : '题目已驳回，审核记录已保存。',
      'success',
    );
    await loadQueue();
  } catch (cause) {
    decisionError.value = cause instanceof Error ? cause.message : '审核提交失败，请刷新队列后重试。';
    toast.show(`审核失败：${decisionError.value}`, 'error');
  } finally {
    deciding.value = false;
  }
}

onMounted(() => {
  void loadQueue();
});
</script>

<template>
  <section
    ref="reviewPage"
    class="admin-reviews"
    tabindex="-1"
    data-testid="admin-problem-reviews"
  >
    <AdminPanel
      title="AI 出题审核"
      description="AI 通过审查的题目可自动进入题库；教师可复核并撤下，人工决定均保留审计记录。"
    >
      <template #actions>
        <button
          class="admin-button"
          type="button"
          :disabled="loading"
          @click="loadQueue"
        >
          {{ loading ? '正在刷新…' : '刷新队列' }}
        </button>
      </template>

      <template #toolbar>
        <div class="review-toolbar">
          <form
            class="review-search"
            data-testid="admin-review-search-form"
            @submit.prevent="searchQueue"
          >
            <label for="admin-review-search">搜索题目、KC、申请人或编号</label>
            <input
              id="admin-review-search"
              v-model="searchInput"
              class="admin-control"
              type="search"
              maxlength="100"
              :disabled="loading"
              data-testid="admin-review-search"
            >
            <button
              class="admin-button admin-button--primary"
              type="submit"
              :disabled="loading"
            >
              搜索
            </button>
          </form>
          <label class="review-filter">
            <span>状态</span>
            <select
              v-model="filter"
              class="admin-control"
              aria-label="按审核状态筛选"
              :disabled="loading"
              @change="applyQueueFilters"
            >
              <option value="ALL">全部状态</option>
              <option value="PENDING">待审核</option>
              <option value="APPROVED">已通过</option>
              <option value="REJECTED">已驳回</option>
            </select>
          </label>
          <label class="review-filter">
            <span>排序字段</span>
            <select
              v-model="sortBy"
              class="admin-control"
              aria-label="审核队列排序字段"
              :disabled="loading"
              @change="applyQueueFilters"
            >
              <option value="REQUESTED_AT">申请时间</option>
              <option value="STATEMENT">题面</option>
              <option value="REQUESTER_USERNAME">申请人</option>
            </select>
          </label>
          <label class="review-filter">
            <span>顺序</span>
            <select
              v-model="sortDirection"
              class="admin-control"
              aria-label="审核队列排序方向"
              :disabled="loading"
              @change="applyQueueFilters"
            >
              <option value="DESC">降序</option>
              <option value="ASC">升序</option>
            </select>
          </label>
        </div>
      </template>

      <div
        class="review-counts"
        aria-label="审核队列数量"
      >
        <span>本页待审核 <strong>{{ counts.pending }}</strong></span>
        <span>本页已通过 <strong>{{ counts.approved }}</strong></span>
        <span>本页已驳回 <strong>{{ counts.rejected }}</strong></span>
      </div>

      <p
        v-if="loading && entries.length === 0"
        class="review-state"
        role="status"
        aria-live="polite"
      >
        正在读取审核队列…
      </p>
      <div
        v-if="error"
        class="review-error"
        role="alert"
      >
        <span>审核队列加载失败：{{ error }}</span>
        <button
          class="admin-button"
          type="button"
          :disabled="loading"
          @click="loadQueue"
        >
          重试
        </button>
      </div>
      <p
        v-else-if="!loading && entries.length === 0 && totalElements === 0"
        class="review-state"
      >
        {{ searchTerm || filter !== 'ALL' ? '没有匹配的审核记录。' : '后端审核队列为空。' }}
      </p>
      <p
        v-else-if="entries.length === 0"
        class="review-state"
      >
        当前页没有记录。
      </p>
      <AdminTable
        v-else
        label="审核队列，可横向滚动"
        :min-width="1152"
      >
        <table class="review-table">
          <thead>
            <tr>
              <th
                scope="col"
                class="review-table__id"
              >
                编号
              </th>
              <th
                scope="col"
                class="review-table__problem"
              >
                题目
              </th>
              <th
                scope="col"
                class="review-table__chapter"
              >
                章节 / 阶段
              </th>
              <th
                scope="col"
                class="review-table__kc"
              >
                知识点
              </th>
              <th
                scope="col"
                class="review-table__difficulty"
              >
                难度
              </th>
              <th
                scope="col"
                class="review-table__requester"
              >
                提交者 / 时间
              </th>
              <th
                scope="col"
                class="review-table__status"
              >
                状态
              </th>
              <th
                scope="col"
                class="review-table__action admin-table__sticky"
              >
                操作
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="entry in entries"
              :key="entry.id"
              :class="{ 'is-selected': entry.id === selectedId }"
            >
              <td>
                <button
                  class="review-id"
                  type="button"
                  :aria-pressed="entry.id === selectedId"
                  @click="openDetail(entry, $event)"
                >
                  #{{ entry.id }}
                </button>
              </td>
              <td
                class="review-table__problem-cell"
                :title="entry.problem.statement"
              >
                {{ shortStatement(entry.problem.statement) }}
              </td>
              <td>第 {{ entry.problem.chapter }} 章 · S{{ entry.problem.stage }}</td>
              <td
                class="review-table__kc-cell"
                :title="kcDisplayName(entry.problem.kcCode)"
              >
                {{ kcDisplayName(entry.problem.kcCode) }}
              </td>
              <td>{{ entry.problem.difficulty }}</td>
              <td>
                <span
                  class="review-requester"
                  :title="entry.requesterUsername"
                >{{ entry.requesterUsername }}</span>
                <small>{{ formatDate(entry.requestedAt) }}</small>
              </td>
              <td>
                <span
                  class="admin-status review-status"
                  :class="[decisionClass(entry.decision), decisionTone(entry.decision)]"
                >{{ decisionLabel(entry.decision) }}</span>
              </td>
              <td class="admin-table__sticky">
                <div class="review-row-actions">
                  <button
                    class="admin-button review-open"
                    type="button"
                    @click="openDetail(entry, $event)"
                  >
                    查看
                  </button>
                  <button
                    v-if="canDecide(entry)"
                    class="admin-button admin-button--danger"
                    type="button"
                    :aria-label="`${entry.decision === 'APPROVED' ? '撤下' : '驳回'}题目 ${entry.id}`"
                    :data-testid="`admin-review-reject-${entry.id}`"
                    :disabled="loading || deciding"
                    @click="openDecision('REJECTED', $event, entry)"
                  >
                    {{ entry.decision === 'APPROVED' ? '撤下' : '驳回' }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </AdminTable>
      <template #footer>
        <AdminPagination
          :page="page"
          :total-pages="totalPages"
          :page-size="pageSize"
          :total-elements="totalElements"
          item-label="条审核记录"
          :disabled="loading"
          @page-change="goToPage"
          @page-size-change="changePageSize"
        />
      </template>
    </AdminPanel>

    <dialog
      v-if="selectedEntry"
      ref="detailDialog"
      class="admin-dialog review-detail-dialog"
      aria-modal="true"
      aria-labelledby="review-detail-title"
      @cancel.prevent="closeDetail()"
      @click.self="closeDetail()"
    >
      <header class="review-detail-dialog__header">
        <div>
          <h2 id="review-detail-title">
            题目预览
          </h2>
          <p>#{{ selectedEntry.id }} · 第 {{ selectedEntry.problem.chapter }} 章 · S{{ selectedEntry.problem.stage }} · {{ kcDisplayName(selectedEntry.problem.kcCode) }}</p>
        </div>
        <button
          class="admin-button"
          type="button"
          aria-label="关闭题目预览"
          @click="closeDetail()"
        >
          关闭
        </button>
      </header>
      <div class="review-detail">
        <div class="review-detail__body">
          <h3>题面</h3>
          <p class="review-detail__statement">
            {{ selectedEntry.problem.statement }}
          </p>
          <ul
            v-if="selectedEntry.problem.hints.length"
            class="review-detail__hints"
          >
            <li
              v-for="(hint, index) in selectedEntry.problem.hints"
              :key="`${selectedEntry.id}-hint-${index}`"
            >
              {{ hint }}
            </li>
          </ul>
          <div class="review-samples">
            <section>
              <h4>公开输入样例</h4>
              <pre>{{ selectedEntry.problem.sampleInput || '无' }}</pre>
            </section>
            <section>
              <h4>公开输出样例</h4>
              <pre>{{ selectedEntry.problem.sampleOutput || '无' }}</pre>
            </section>
          </div>
          <p class="review-detail__meta">
            难度 {{ selectedEntry.problem.difficulty }} · {{ selectedEntry.problem.totalTestCases }} 个测试用例 ·
            {{ selectedEntry.problem.aiGenerated ? 'AI 生成' : '课程题目' }}
          </p>
        </div>

        <aside
          class="review-detail__audit"
          aria-label="审核记录"
        >
          <h3>审核记录</h3>
          <dl>
            <div><dt>申请人</dt><dd>{{ selectedEntry.requesterUsername }}</dd></div>
            <div><dt>申请时间</dt><dd>{{ formatDate(selectedEntry.requestedAt) }}</dd></div>
            <div><dt>当前状态</dt><dd>{{ decisionLabel(selectedEntry.decision) }}</dd></div>
            <div><dt>审核来源</dt><dd>{{ reviewSourceLabel(selectedEntry.reviewSource) }}</dd></div>
            <template v-if="selectedEntry.decidedAt">
              <div v-if="selectedEntry.deciderUsername">
                <dt>审核人</dt><dd>{{ selectedEntry.deciderUsername }}</dd>
              </div>
              <div>
                <dt>审核时间</dt><dd>{{ formatDate(selectedEntry.decidedAt) }}</dd>
              </div>
              <div v-if="selectedEntry.decisionReason">
                <dt>审核意见</dt><dd>{{ selectedEntry.decisionReason }}</dd>
              </div>
            </template>
          </dl>
          <div
            v-if="canDecide(selectedEntry)"
            class="review-detail__actions"
          >
            <button
              class="admin-button admin-button--positive review-decision review-decision--approve"
              type="button"
              :disabled="loading"
              @click="openDecision('APPROVED', $event)"
            >
              {{ selectedEntry.reviewSource === 'AUTOMATED' ? '教师确认' : '通过并发布' }}
            </button>
            <button
              class="admin-button admin-button--danger review-decision review-decision--reject"
              type="button"
              :disabled="loading"
              @click="openDecision('REJECTED', $event)"
            >
              {{ selectedEntry.reviewSource === 'AUTOMATED' ? '撤下题库并留档' : '驳回并留档' }}
            </button>
          </div>
        </aside>
      </div>
    </dialog>

    <dialog
      v-if="pendingDecision"
      ref="decisionDialog"
      class="admin-dialog review-dialog"
      aria-labelledby="review-dialog-title"
      @cancel.prevent="closeDecision"
      @click.self="closeDecision"
    >
      <h2 id="review-dialog-title">
        {{ pendingDecision === 'APPROVED'
          ? '确认通过题目'
          : selectedEntry?.decision === 'APPROVED' ? '确认撤下题目' : '确认驳回题目' }}
      </h2>
      <p>审核决定会写入后端题库和审计记录，请填写本次审核意见。</p>
      <label for="review-decision-reason">审核意见</label>
      <textarea
        id="review-decision-reason"
        v-model="decisionReason"
        class="admin-control"
        rows="4"
        maxlength="1000"
        :disabled="deciding"
        data-testid="admin-review-reason"
      />
      <p
        v-if="decisionError"
        class="review-dialog__error"
        role="alert"
      >
        {{ decisionError }}
      </p>
      <div class="review-dialog__actions">
        <button
          class="admin-button"
          type="button"
          :disabled="deciding"
          @click="closeDecision"
        >
          取消
        </button>
        <button
          class="admin-button review-decision"
          :class="pendingDecision === 'APPROVED'
            ? ['review-decision--approve', 'admin-button--positive']
            : ['review-decision--reject', 'admin-button--danger']"
          type="button"
          :disabled="deciding"
          data-testid="admin-review-confirm"
          @click="submitDecision"
        >
          {{ deciding ? '正在提交…' : '确认提交' }}
        </button>
      </div>
    </dialog>
  </section>
</template>

<style scoped>
.admin-reviews {
  display: grid;
  gap: 20px;
  min-width: 0;
}

.review-filter {
  display: grid;
  min-width: 112px;
  gap: 4px;
  color: var(--admin-text-muted);
  font-size: 11px;
}

.review-filter select {
  min-width: 112px;
}

.review-toolbar {
  display: flex;
  width: 100%;
  align-items: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

.review-search {
  display: flex;
  min-width: min(360px, 100%);
  align-items: flex-end;
  gap: 8px;
  flex: 1 1 360px;
  flex-wrap: wrap;
  margin: 0;
}

.review-search label {
  display: grid;
  min-width: 0;
  gap: 4px;
  color: var(--admin-text-muted);
  font-size: 11px;
}

.review-search input {
  width: min(320px, 100%);
  flex: 1 1 180px;
}

.review-counts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.review-counts span {
  padding: 6px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: var(--admin-panel-muted-bg);
  color: #64748b;
  font-size: 11px;
}

.review-counts strong {
  margin-left: 4px;
  color: #1e293b;
  font-variant-numeric: tabular-nums;
}

.review-state {
  margin: 0;
  padding: 24px 12px;
  color: var(--admin-text-muted);
  font-size: 13px;
  text-align: center;
}

.review-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  padding: 12px;
  border: 1px solid #fecaca;
  border-radius: 8px;
  background: #fff7f7;
  color: #991b1b;
  font-size: 12px;
}

.review-table td:nth-child(1),
.review-table td:nth-child(3),
.review-table td:nth-child(5),
.review-table td:nth-child(7),
.review-table td:nth-child(8) {
  white-space: nowrap;
}

.review-table td:nth-child(3),
.review-table td:nth-child(5),
.review-table td:nth-child(7) {
  text-align: center;
}

.review-table tbody tr:hover,
.review-table tbody tr.is-selected {
  background: var(--admin-table-hover-bg);
}

.review-table__id { width: 72px; }
.review-table__problem { width: 290px; }
.review-table__chapter { width: 130px; }
.review-table__kc { width: 180px; }
.review-table__difficulty { width: 70px; }
.review-table__requester { width: 170px; }
.review-table__status { width: 100px; }
.review-table__action { width: 140px; text-align: center !important; }

.review-row-actions {
  display: flex;
  justify-content: center;
  gap: 6px;
}

.review-row-actions .admin-button {
  padding: 0 10px;
}

.review-table__problem-cell,
.review-table__kc-cell,
.review-table td small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.review-table td small,
.review-requester {
  display: block;
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.review-table td small {
  margin-top: 3px;
  color: #94a3b8;
  font-size: 10px;
}

.review-id {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--primary-color);
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}

.review-id:focus-visible {
  outline: 3px solid var(--admin-focus);
  outline-offset: 2px;
}

.review-detail {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(240px, 0.8fr);
  gap: 24px;
}

dialog.admin-dialog.review-detail-dialog {
  width: min(760px, calc(100vw - 32px));
}

.review-detail-dialog__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.review-detail-dialog__header p {
  margin: 6px 0 0;
  color: var(--admin-text-muted);
  font-size: 12px;
}

.review-detail__body,
.review-detail__audit {
  min-width: 0;
}

.review-detail h3 {
  margin: 0 0 10px;
  color: var(--admin-text-strong);
  font-size: 14px;
}

.review-detail__statement {
  margin: 0;
  color: #334155;
  font-size: 13px;
  line-height: 1.7;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.review-detail__hints {
  display: grid;
  gap: 5px;
  margin: 12px 0 0;
  padding-left: 20px;
  color: #64748b;
  font-size: 12px;
}

.review-samples {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 16px;
}

.review-samples section {
  min-width: 0;
}

.review-samples h4 {
  margin: 0 0 5px;
  color: #64748b;
  font-size: 11px;
}

.review-samples pre {
  min-height: 48px;
  overflow: auto;
  margin: 0;
  padding: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 7px;
  background: #f8fafc;
  color: #334155;
  font: 12px/1.5 var(--pyt-font-mono, monospace);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.review-detail__meta {
  margin: 12px 0 0;
  color: #64748b;
  font-size: 11px;
}

.review-detail__audit {
  padding-left: 20px;
  border-left: 1px solid #e2e8f0;
}

.review-detail__audit dl {
  display: grid;
  gap: 9px;
  margin: 0;
}

.review-detail__audit dl div {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  gap: 8px;
}

.review-detail__audit dt {
  color: #94a3b8;
  font-size: 11px;
}

.review-detail__audit dd {
  min-width: 0;
  margin: 0;
  color: #334155;
  font-size: 11px;
  overflow-wrap: anywhere;
}

.review-detail__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 18px;
}

.review-dialog {
  position: fixed;
  z-index: 1000;
  margin: auto;
}

.review-dialog > p {
  margin: 8px 0 16px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.review-dialog label {
  display: block;
  margin-bottom: 6px;
  color: #334155;
  font-size: 12px;
  font-weight: 600;
}

.review-dialog textarea {
  display: block;
  width: 100%;
}

.review-dialog__error {
  margin: 7px 0 0 !important;
  color: #b91c1c !important;
}

.review-dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}

@media (max-width: 760px) {
  .review-detail {
    grid-template-columns: 1fr;
  }

  .review-detail__audit {
    padding: 16px 0 0;
    border-top: 1px solid #e2e8f0;
    border-left: 0;
  }
}

@media (max-width: 540px) {
  .review-search {
    align-items: stretch;
  }

  .review-search input {
    flex: 1 1 100%;
  }

  .review-counts {
    gap: 5px;
  }

  .review-counts span {
    padding: 5px 7px;
  }

  .review-samples {
    grid-template-columns: 1fr;
  }

  .review-error {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
