<script setup lang="ts">
// 题库浏览页：按 KC / 难度 / 玩家状态过滤，按章节 / 难度 / 尝试次数排序；
// 点击题号 / 标题 / 「去做」均直达做题界面 /training（不再行内展开题面预览）。
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import PyButton from '@/components/ui/PyButton.vue';
import {
  listAllProblems,
  listKcFallbacks,
  loadPlayerProblemProgress,
} from '@/api/courseLibrary';
import { listKnowledgeComponents } from '@/api/course';
import { useToastStore } from '@stores/toast';
import type {
  CourseKcSummary,
  CourseProblemSummary,
  PlayerProblemStatus,
  PlayerProblemProgress,
} from '@/api/dto/Library';
import type { KcDto } from '@/api/dto/KcDto';
import { kcDisplayName } from '@/data/kcNames';

type SortField = 'chapter' | 'difficulty' | 'attempts';
type DifficultyFilter = '' | 'Low' | 'Medium' | 'High';
type StatusFilter = '' | PlayerProblemStatus;

const router = useRouter();
const toast = useToastStore();

const problems = ref<CourseProblemSummary[]>([]);
const kcMap = ref<Map<string, CourseKcSummary>>(new Map());
const progressMap = ref<Map<number, PlayerProblemProgress>>(new Map());

const searchQuery = ref<string>('');
const filterKcCode = ref<string>('');
const filterDifficulty = ref<DifficultyFilter>('');
const filterStatus = ref<StatusFilter>('');
const sortField = ref<SortField>('chapter');

const loading = ref<boolean>(true);

/** 题库下拉仅展示「至少关联 1 道题」的 KC（pack 共 65 KC，题库仅 39 题）。 */
const kcsWithProblems = computed<CourseKcSummary[]>(() => {
  const codes = [...new Set(problems.value.map((p) => p.kcCode))];
  return codes
    .map((code) => kcMap.value.get(code) ?? stubKcSummary(code))
    .sort((a, b) => {
      const co = (a.chapterOrder ?? 0) - (b.chapterOrder ?? 0);
      if (co !== 0) return co;
      return a.kcCode.localeCompare(b.kcCode);
    });
});

const difficulties = ['Low', 'Medium', 'High'] as const;
const statusOptions: PlayerProblemStatus[] = ['NOT_TRIED', 'PASSED', 'FAILED'];

const kcChapterGroups = computed(() => {
  const byChapter = new Map<number, CourseKcSummary[]>();
  for (const kc of kcsWithProblems.value) {
    const order = kc.chapterOrder ?? 0;
    const list = byChapter.get(order) ?? [];
    list.push(kc);
    byChapter.set(order, list);
  }
  return Array.from(byChapter.entries())
    .sort(([a], [b]) => a - b)
    .map(([chapterOrder, kcs]) => ({ chapterOrder, kcs }));
});

function normalizedSearchQuery(): string {
  return searchQuery.value.trim().toLowerCase();
}

function problemMatchesSearch(p: CourseProblemSummary): boolean {
  const q = normalizedSearchQuery();
  if (!q) return true;
  const haystack = [
    String(p.problemId),
    p.title,
    p.kcCode,
    kcNameOf(p.kcCode),
    p.statementMarkdown,
  ]
    .join('\n')
    .toLowerCase();
  return haystack.includes(q);
}

const filtered = computed<CourseProblemSummary[]>(() => {
  return problems.value
    .filter((p) => problemMatchesSearch(p))
    .filter((p) => !filterKcCode.value || p.kcCode === filterKcCode.value)
    .filter((p) => !filterDifficulty.value || p.difficulty === filterDifficulty.value)
    .filter((p) => !filterStatus.value || statusOf(p.problemId) === filterStatus.value);
});

const sorted = computed<CourseProblemSummary[]>(() => {
  const list = filtered.value.slice();
  switch (sortField.value) {
    case 'difficulty':
      list.sort((a, b) => difficultyWeight(a.difficulty) - difficultyWeight(b.difficulty));
      break;
    case 'attempts':
      list.sort((a, b) => attemptsOf(b.problemId) - attemptsOf(a.problemId));
      break;
    case 'chapter':
    default:
      list.sort((a, b) => {
        const ca = kcMap.value.get(a.kcCode)?.chapterOrder ?? 99;
        const cb = kcMap.value.get(b.kcCode)?.chapterOrder ?? 99;
        if (ca !== cb) return ca - cb;
        return a.problemId - b.problemId;
      });
      break;
  }
  return list;
});

function difficultyWeight(d: CourseProblemSummary['difficulty']): number {
  switch (d) {
    case 'Low':
      return 1;
    case 'Medium':
      return 2;
    case 'High':
      return 3;
    default:
      return 9;
  }
}

function statusOf(problemId: number): PlayerProblemStatus {
  return progressMap.value.get(problemId)?.status ?? 'NOT_TRIED';
}
function attemptsOf(problemId: number): number {
  return progressMap.value.get(problemId)?.attempts ?? 0;
}

function chapterOf(kcCode: string): number | null {
  return kcMap.value.get(kcCode)?.chapterOrder ?? null;
}
function kcNameOf(kcCode: string): string {
  const fromApi = kcMap.value.get(kcCode)?.kcName;
  if (fromApi) return fromApi;
  return kcDisplayName(kcCode);
}

function stubKcSummary(kcCode: string): CourseKcSummary {
  return {
    kcId: 0,
    packId: 43,
    kcCode,
    kcName: kcDisplayName(kcCode),
    chapterId: 0,
    chapterOrder: 99,
    difficulty: null,
    summary: '',
    pageRefs: [],
  };
}

function kcOptionLabel(kc: CourseKcSummary): string {
  const name = kc.kcName || kcDisplayName(kc.kcCode);
  const count = problems.value.filter((p) => p.kcCode === kc.kcCode).length;
  const ch = kc.chapterOrder;
  const prefix = ch != null && ch < 99 ? `第${ch}章 · ` : '';
  return `${prefix}${name}（${count} 题）`;
}

watch(kcsWithProblems, (kcs) => {
  if (filterKcCode.value && !kcs.some((kc) => kc.kcCode === filterKcCode.value)) {
    filterKcCode.value = '';
  }
});

function goTraining(problemId: number): void {
  void router.push({ path: '/training', query: { problemId: String(problemId) } });
}

function backToWorld(): void {
  void router.push({ path: '/world' });
}

function difficultyLabel(d: string): string {
  switch (d) {
    case 'Low':
      return '简单';
    case 'Medium':
      return '中等';
    case 'High':
      return '困难';
    default:
      return d;
  }
}
function statusLabel(s: PlayerProblemStatus): string {
  switch (s) {
    case 'PASSED':
      return '已通过';
    case 'FAILED':
      return '失败';
    case 'NOT_TRIED':
    default:
      return '未做';
  }
}

function statusClass(s: PlayerProblemStatus): string {
  switch (s) {
    case 'PASSED':
      return 'status-passed';
    case 'FAILED':
      return 'status-failed';
    default:
      return 'status-idle';
  }
}

function clearFilters(): void {
  searchQuery.value = '';
  filterKcCode.value = '';
  filterDifficulty.value = '';
  filterStatus.value = '';
}

const hasActiveFilters = computed(
  () =>
    Boolean(
      normalizedSearchQuery() || filterKcCode.value || filterDifficulty.value || filterStatus.value,
    ),
);

onMounted(async () => {
  loading.value = true;
  try {
    const [problemList, kcDtos] = await Promise.all([
      listAllProblems(),
      listKnowledgeComponents().catch(() => [] as KcDto[]),
    ]);
    problems.value = problemList;

    let kcs: CourseKcSummary[] = [];
    if (kcDtos.length > 0) {
      kcs = kcDtos.map((kc) => ({
        kcId: kc.kcId,
        packId: 43,
        kcCode: kc.kcCode,
        kcName: kc.kcName,
        chapterId: kc.chapterId,
        chapterOrder: kc.chapterOrder,
        difficulty: kc.difficulty ?? null,
        summary: kc.summaryMarkdown ?? '',
        pageRefs: [],
      }));
    } else {
      kcs = await listKcFallbacks();
    }
    kcMap.value = new Map(kcs.map((kc) => [kc.kcCode, kc]));

    const ids = problemList.map((p) => p.problemId);
    progressMap.value = await loadPlayerProblemProgress(ids);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    toast.push({ kind: 'error', text: `题库加载失败：${message}` });
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <AppLayout>
    <section class="problem-list-page">
      <header class="page-header">
        <div>
          <h1>Pythontopia 题库</h1>
          <p class="muted">
            packId=43 · 共 {{ problems.length }} 题 · 已筛 {{ sorted.length }} 题
            <template v-if="loading">
              · 加载中…
            </template>
          </p>
        </div>
        <PyButton
          test-id="problem-back"
          variant="secondary"
          size="sm"
          @click="backToWorld"
        >
          ← 返回小镇
        </PyButton>
      </header>

      <section class="filter-bar">
        <label class="filter-search">
          <span class="filter-label">搜索题目</span>
          <input
            v-model="searchQuery"
            type="search"
            class="filter-search-input"
            placeholder="题号、标题、知识点…"
            autocomplete="off"
            data-testid="filter-search"
          >
        </label>
        <div class="filter-bar__controls">
          <label class="filter-field">
            <span class="filter-label">难度</span>
            <select
              v-model="filterDifficulty"
              class="filter-select"
              data-testid="filter-difficulty"
            >
              <option value="">
                全部难度
              </option>
              <option
                v-for="d in difficulties"
                :key="d"
                :value="d"
              >
                {{ difficultyLabel(d) }}
              </option>
            </select>
          </label>
          <label class="filter-field">
            <span class="filter-label">玩家状态</span>
            <select
              v-model="filterStatus"
              class="filter-select"
              data-testid="filter-status"
            >
              <option value="">
                全部状态
              </option>
              <option
                v-for="s in statusOptions"
                :key="s"
                :value="s"
              >
                {{ statusLabel(s) }}
              </option>
            </select>
          </label>
          <label class="filter-field">
            <span class="filter-label">排序</span>
            <select
              v-model="sortField"
              class="filter-select"
              data-testid="filter-sort"
            >
              <option value="chapter">
                按章节
              </option>
              <option value="difficulty">
                按难度
              </option>
              <option value="attempts">
                按尝试次数
              </option>
            </select>
          </label>
          <label class="filter-field filter-field--kc">
            <span class="filter-label">知识点（{{ kcsWithProblems.length }}）</span>
            <select
              v-model="filterKcCode"
              class="filter-select"
              data-testid="filter-kc"
            >
              <option value="">
                全部知识点
              </option>
              <optgroup
                v-for="group in kcChapterGroups"
                :key="group.chapterOrder"
                :label="`第 ${group.chapterOrder} 章`"
              >
                <option
                  v-for="kc in group.kcs"
                  :key="kc.kcCode"
                  :value="kc.kcCode"
                  :title="kc.summary"
                >
                  {{ kcOptionLabel(kc) }}
                </option>
              </optgroup>
            </select>
          </label>
          <PyButton
            test-id="filter-reset"
            variant="ghost"
            size="sm"
            :disabled="!hasActiveFilters"
            @click="clearFilters"
          >
            清空
          </PyButton>
        </div>
      </section>

      <div
        class="problem-table"
        role="table"
        data-testid="problem-table"
      >
        <div
          class="problem-row problem-row--head"
          role="row"
        >
          <span>#</span>
          <span>标题</span>
          <span>章 / KC</span>
          <span>难度</span>
          <span>状态</span>
          <span>尝试</span>
          <span>操作</span>
        </div>
        <div
          v-for="p in sorted"
          :key="p.problemId"
          class="problem-row"
          role="row"
        >
          <button
            type="button"
            class="row-toggle"
            @click="goTraining(p.problemId)"
          >
            {{ p.problemId }}
          </button>
          <button
            type="button"
            class="row-title"
            @click="goTraining(p.problemId)"
          >
            {{ p.title }}
          </button>
          <span>
            <span class="badge">第 {{ chapterOf(p.kcCode) ?? '?' }} 章</span>
            <span
              class="badge kc"
              :title="p.kcCode"
            >{{ kcNameOf(p.kcCode) }}</span>
          </span>
          <span :class="['badge difficulty', `diff-${p.difficulty.toLowerCase()}`]">
            {{ difficultyLabel(p.difficulty) }}
          </span>
          <span :class="['badge', statusClass(statusOf(p.problemId))]">
            {{ statusLabel(statusOf(p.problemId)) }}
          </span>
          <span class="attempts">{{ attemptsOf(p.problemId) }}</span>
          <PyButton
            test-id="go-training"
            variant="primary"
            size="sm"
            @click="goTraining(p.problemId)"
          >
            去做
          </PyButton>
        </div>
        <p
          v-if="!loading && sorted.length === 0"
          class="empty"
        >
          没有题目命中当前筛选。
        </p>
      </div>
    </section>
  </AppLayout>
</template>

<style scoped>
.problem-list-page {
  display: grid;
  gap: 16px;
  padding: 16px;
  min-height: calc(100vh - 56px);
  background:
    linear-gradient(180deg, var(--pyt-color-bg-primary) 0%, var(--pyt-color-bg-sunken) 100%),
    repeating-linear-gradient(0deg, rgba(110, 79, 29, 0.04) 0 2px, transparent 2px 6px);
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 12px;
  padding: 12px 16px;
  background: var(--pyt-color-bg-elevated, var(--pyt-color-bg-elevated));
  border: 3px solid var(--pyt-color-border-strong, var(--pyt-color-text-primary));
  box-shadow: 4px 4px 0 var(--pyt-color-border-strong, var(--pyt-color-text-primary));
}
.page-header h1 {
  margin: 0;
  font-family: var(--pyt-font-pixel), sans-serif;
  font-size: 22px;
}
.muted {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--pyt-color-text-muted, var(--pyt-color-text-muted));
}
.back-button {
  font-family: var(--pyt-font-pixel), monospace;
  min-height: 36px;
  padding: 0 16px;
  background: var(--pyt-color-warning-bg, var(--pyt-color-warning-bg));
  border: 2px solid var(--pyt-color-border-strong, var(--pyt-color-text-primary));
  cursor: pointer;
  box-shadow: 3px 3px 0 var(--pyt-color-border-strong, var(--pyt-color-text-primary));
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px 16px;
  padding: 10px 12px;
  background: var(--pyt-color-bg-elevated, var(--pyt-color-bg-elevated));
  border: 3px solid var(--pyt-color-border-strong, var(--pyt-color-text-primary));
  box-shadow: 4px 4px 0 var(--pyt-color-border-strong, var(--pyt-color-text-primary));
}
.filter-search {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1 1 240px;
  min-width: 200px;
  max-width: 420px;
}
.filter-search-input {
  width: 100%;
  min-height: 32px;
  padding: 4px 10px;
  border: 2px solid var(--pyt-color-border-strong, var(--pyt-color-text-primary));
  background: var(--pyt-color-bg-primary, var(--pyt-color-bg-primary));
  font: inherit;
  font-size: 13px;
}
.filter-search-input::placeholder {
  color: var(--pyt-color-text-muted, var(--pyt-color-text-muted));
}
.filter-search-input:focus-visible {
  outline: 2px solid var(--pyt-color-primary, var(--pyt-color-text-hint));
  outline-offset: 1px;
}
.filter-bar__controls {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: flex-end;
  gap: 10px 14px;
  flex: 1 1 auto;
  margin-left: auto;
}
.filter-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 120px;
}
.filter-field--kc {
  flex: 0 1 240px;
  min-width: 180px;
  max-width: 280px;
}
.filter-label {
  font-family: var(--pyt-font-pixel), sans-serif;
  font-size: 11px;
  letter-spacing: 0.4px;
  color: var(--pyt-color-text-muted, var(--pyt-color-text-muted));
}
.filter-select {
  width: 100%;
  min-height: 32px;
  padding: 4px 8px;
  border: 2px solid var(--pyt-color-border-strong, var(--pyt-color-text-primary));
  background: var(--pyt-color-bg-primary, var(--pyt-color-bg-primary));
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.filter-select:focus-visible {
  outline: 2px solid var(--pyt-color-primary, var(--pyt-color-text-hint));
  outline-offset: 1px;
}
.reset-button {
  flex-shrink: 0;
  align-self: flex-end;
  min-height: 32px;
  padding: 4px 10px;
  font-family: var(--pyt-font-pixel), monospace;
  font-size: 11px;
  line-height: 1.2;
  background: var(--pyt-color-danger-bg, var(--pyt-color-danger-bg));
  border: 2px solid var(--pyt-color-border-strong, var(--pyt-color-text-primary));
  cursor: pointer;
  box-shadow: 2px 2px 0 var(--pyt-color-border-strong, var(--pyt-color-text-primary));
}
.reset-button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
  box-shadow: none;
}

.problem-table {
  background: var(--pyt-color-bg-elevated, var(--pyt-color-bg-elevated));
  border: 3px solid var(--pyt-color-border-strong, var(--pyt-color-text-primary));
  box-shadow: 4px 4px 0 var(--pyt-color-border-strong, var(--pyt-color-text-primary));
}
.problem-row {
  display: grid;
  grid-template-columns: 50px minmax(160px, 2fr) minmax(120px, 1.4fr) 70px 70px 60px 70px;
  gap: 8px;
  padding: 8px 12px;
  align-items: center;
  justify-items: center;
  text-align: center;
  border-bottom: 2px dashed var(--pyt-color-border, var(--pyt-color-border));
  font-size: 13px;
}
.problem-row--head {
  background: var(--pyt-color-bg-soft, var(--pyt-color-bg-soft));
  font-family: var(--pyt-font-pixel), sans-serif;
  font-weight: 700;
  font-size: 12px;
}
.row-toggle {
  font-family: var(--pyt-font-pixel), monospace;
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
  text-align: center;
  min-height: auto;
  box-shadow: none;
}
.row-title {
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
  font: inherit;
  text-align: center;
  text-decoration: none;
  color: var(--pyt-color-text-primary);
  min-height: auto;
  box-shadow: none;
  transition: color var(--pyt-motion-base);
}
.row-title:hover {
  color: var(--pyt-color-primary);
}
.badge {
  display: inline-block;
  background: var(--pyt-color-bg-soft, var(--pyt-color-bg-soft));
  border: 1px solid var(--pyt-color-border, var(--pyt-color-border));
  padding: 1px 6px;
  font-family: var(--pyt-font-pixel), monospace;
  font-size: 11px;
  margin-right: 4px;
}
.badge.kc {
  background: var(--pyt-color-info-bg, var(--pyt-color-info-bg));
}
.diff-low { background: var(--pyt-color-success-bg, var(--pyt-color-success-bg)); }
.diff-medium { background: var(--pyt-color-warning-bg, var(--pyt-color-warning-bg)); }
.diff-high { background: var(--pyt-color-danger-bg, var(--pyt-color-danger-bg)); }
.status-passed { background: var(--pyt-color-success-bg, var(--pyt-color-success-bg)); color: var(--pyt-color-success, var(--pyt-color-success)); }
.status-failed { background: var(--pyt-color-danger-bg, var(--pyt-color-danger-bg)); color: var(--pyt-color-danger, var(--pyt-color-danger)); }
.status-idle { background: var(--pyt-color-bg-soft, var(--pyt-color-bg-soft)); color: var(--pyt-color-text-muted, var(--pyt-color-text-muted)); }

.attempts {
  font-family: var(--pyt-font-pixel), monospace;
  text-align: center;
}
.go-button {
  font-family: var(--pyt-font-pixel), monospace;
  min-height: 30px;
  padding: 0 10px;
  background: var(--pyt-color-primary, var(--pyt-color-text-hint));
  color: var(--pyt-color-text-on-dark, var(--pyt-color-bg-elevated));
  border: 2px solid var(--pyt-color-border-strong, var(--pyt-color-text-primary));
  cursor: pointer;
  box-shadow: 2px 2px 0 var(--pyt-color-border-strong, var(--pyt-color-text-primary));
}
.empty {
  text-align: center;
  padding: 24px;
  margin: 0;
  color: var(--pyt-color-text-muted, var(--pyt-color-text-muted));
}
</style>
