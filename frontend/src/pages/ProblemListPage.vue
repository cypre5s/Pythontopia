<script setup lang="ts">
// ProblemListPage —— 39 题题库浏览页。支持 KC / 难度 / 玩家状态多重过滤，
// 排序按章节-KC 或者按通过率，点击行展开题目预览，点击"去做"跳到 /training。
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
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

type SortField = 'chapter' | 'difficulty' | 'attempts';

const router = useRouter();
const toast = useToastStore();

const problems = ref<CourseProblemSummary[]>([]);
const kcMap = ref<Map<string, CourseKcSummary>>(new Map());
const progressMap = ref<Map<number, PlayerProblemProgress>>(new Map());

const filterKcCodes = ref<string[]>([]);
const filterDifficulty = ref<string[]>([]);
const filterStatus = ref<PlayerProblemStatus[]>([]);
const sortField = ref<SortField>('chapter');
const expanded = ref<Set<number>>(new Set());

const loading = ref<boolean>(true);

const allKcs = computed<CourseKcSummary[]>(() =>
  Array.from(kcMap.value.values()).sort((a, b) => {
    const co = (a.chapterOrder ?? 0) - (b.chapterOrder ?? 0);
    if (co !== 0) return co;
    return a.kcCode.localeCompare(b.kcCode);
  }),
);

const difficulties = ['Low', 'Medium', 'High'] as const;
const statusOptions: PlayerProblemStatus[] = ['NOT_TRIED', 'PASSED', 'FAILED'];

const filtered = computed<CourseProblemSummary[]>(() => {
  const kcFilter = new Set(filterKcCodes.value);
  const diffFilter = new Set(filterDifficulty.value);
  const statFilter = new Set(filterStatus.value);
  return problems.value
    .filter((p) => kcFilter.size === 0 || kcFilter.has(p.kcCode))
    .filter((p) => diffFilter.size === 0 || diffFilter.has(p.difficulty))
    .filter((p) => statFilter.size === 0 || statFilter.has(statusOf(p.problemId)));
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
  return kcMap.value.get(kcCode)?.kcName ?? kcCode;
}

function toggleExpand(problemId: number): void {
  if (expanded.value.has(problemId)) {
    expanded.value.delete(problemId);
  } else {
    expanded.value.add(problemId);
  }
  expanded.value = new Set(expanded.value);
}

function goTraining(problemId: number): void {
  void router.push({ path: '/training', query: { problemId: String(problemId) } });
}

function backToWorld(): void {
  void router.push({ path: '/world' });
}

function toggleKcFilter(code: string): void {
  const next = new Set(filterKcCodes.value);
  if (next.has(code)) {
    next.delete(code);
  } else {
    next.add(code);
  }
  filterKcCodes.value = Array.from(next);
}

function toggleDifficultyFilter(d: string): void {
  const next = new Set(filterDifficulty.value);
  if (next.has(d)) {
    next.delete(d);
  } else {
    next.add(d);
  }
  filterDifficulty.value = Array.from(next);
}

function toggleStatusFilter(s: PlayerProblemStatus): void {
  const next = new Set(filterStatus.value);
  if (next.has(s)) {
    next.delete(s);
  } else {
    next.add(s);
  }
  filterStatus.value = Array.from(next);
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
  filterKcCodes.value = [];
  filterDifficulty.value = [];
  filterStatus.value = [];
}

onMounted(async () => {
  loading.value = true;
  try {
    const [problemList, kcDtos] = await Promise.all([
      listAllProblems(),
      listKnowledgeComponents(43).catch(() => [] as KcDto[]),
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
        <button
          class="back-button"
          type="button"
          @click="backToWorld"
        >
          ← 返回小镇
        </button>
      </header>

      <section class="filter-bar">
        <div class="filter-group">
          <h3>难度</h3>
          <div class="chip-row">
            <button
              v-for="d in difficulties"
              :key="d"
              type="button"
              :class="['chip', { active: filterDifficulty.includes(d) }]"
              @click="toggleDifficultyFilter(d)"
            >
              {{ difficultyLabel(d) }}
            </button>
          </div>
        </div>
        <div class="filter-group">
          <h3>玩家状态</h3>
          <div class="chip-row">
            <button
              v-for="s in statusOptions"
              :key="s"
              type="button"
              :class="['chip', statusClass(s), { active: filterStatus.includes(s) }]"
              @click="toggleStatusFilter(s)"
            >
              {{ statusLabel(s) }}
            </button>
          </div>
        </div>
        <div class="filter-group">
          <h3>排序</h3>
          <select
            v-model="sortField"
            class="sort-select"
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
        </div>
        <div class="filter-group filter-group--wide">
          <h3>KC 筛选（{{ filterKcCodes.length }}/{{ allKcs.length }}）</h3>
          <div class="chip-row chip-row--scroll">
            <button
              v-for="kc in allKcs"
              :key="kc.kcCode"
              type="button"
              :class="['chip', { active: filterKcCodes.includes(kc.kcCode) }]"
              :title="kc.summary"
              @click="toggleKcFilter(kc.kcCode)"
            >
              {{ kc.kcCode }}
            </button>
          </div>
        </div>
        <button
          class="reset-button"
          type="button"
          @click="clearFilters"
        >
          清空筛选
        </button>
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
          :class="['problem-row', { expanded: expanded.has(p.problemId) }]"
          role="row"
        >
          <button
            type="button"
            class="row-toggle"
            @click="toggleExpand(p.problemId)"
          >
            {{ p.problemId }}
          </button>
          <button
            type="button"
            class="row-title"
            @click="toggleExpand(p.problemId)"
          >
            {{ p.title }}
          </button>
          <span>
            <span class="badge">第 {{ chapterOf(p.kcCode) ?? '?' }} 章</span>
            <span
              class="badge kc"
              :title="kcNameOf(p.kcCode)"
            >{{ p.kcCode }}</span>
          </span>
          <span :class="['badge difficulty', `diff-${p.difficulty.toLowerCase()}`]">
            {{ difficultyLabel(p.difficulty) }}
          </span>
          <span :class="['badge', statusClass(statusOf(p.problemId))]">
            {{ statusLabel(statusOf(p.problemId)) }}
          </span>
          <span class="attempts">{{ attemptsOf(p.problemId) }}</span>
          <button
            type="button"
            class="go-button"
            data-testid="go-training"
            @click="goTraining(p.problemId)"
          >
            去做
          </button>
          <article
            v-if="expanded.has(p.problemId)"
            class="row-preview"
          >
            <header>
              <strong>{{ p.title }}</strong>
              <span class="badge kc">{{ p.kcCode }} · {{ kcNameOf(p.kcCode) }}</span>
            </header>
            <pre
              v-text="
                p.statementMarkdown.slice(0, 240) +
                  (p.statementMarkdown.length > 240 ? '…' : '')
              "
            />
            <p
              v-if="p.commonMistakes.length"
              class="mistakes"
            >
              <strong>常见失误：</strong>{{ p.commonMistakes[0] }}
            </p>
          </article>
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
    linear-gradient(180deg, #fef7e3 0%, #f3e4be 100%),
    repeating-linear-gradient(0deg, rgba(110, 79, 29, 0.04) 0 2px, transparent 2px 6px);
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 12px;
  padding: 12px 16px;
  background: var(--pyt-color-bg-elevated, #fff7e6);
  border: 3px solid var(--pyt-color-border-strong, #4d3613);
  box-shadow: 4px 4px 0 var(--pyt-color-border-strong, #4d3613);
}
.page-header h1 {
  margin: 0;
  font-family: var(--pyt-font-pixel), sans-serif;
  font-size: 22px;
}
.muted {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--pyt-color-text-muted, #8a6c2c);
}
.back-button {
  font-family: var(--pyt-font-pixel), monospace;
  min-height: 36px;
  padding: 0 16px;
  background: var(--pyt-color-warning-bg, #f5e2c1);
  border: 2px solid var(--pyt-color-border-strong, #4d3613);
  cursor: pointer;
  box-shadow: 3px 3px 0 var(--pyt-color-border-strong, #4d3613);
}

.filter-bar {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  padding: 12px;
  background: var(--pyt-color-bg-elevated, #fff7e6);
  border: 3px solid var(--pyt-color-border-strong, #4d3613);
  box-shadow: 4px 4px 0 var(--pyt-color-border-strong, #4d3613);
}
.filter-group h3 {
  margin: 0 0 6px;
  font-family: var(--pyt-font-pixel), sans-serif;
  font-size: 13px;
  letter-spacing: 0.5px;
}
.filter-group--wide {
  grid-column: span 2;
}
.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.chip-row--scroll {
  max-height: 84px;
  overflow-y: auto;
  padding-right: 4px;
}
.chip {
  font-family: var(--pyt-font-pixel), monospace;
  font-size: 11px;
  padding: 4px 8px;
  border: 2px solid var(--pyt-color-border, #c9a86b);
  background: var(--pyt-color-bg-soft, #fdf3d0);
  cursor: pointer;
  min-height: auto;
  color: var(--pyt-color-text-primary, #4d3613);
}
.chip.active {
  background: var(--pyt-color-warning-bg, #f5e2c1);
  border-color: var(--pyt-color-border-strong, #4d3613);
  font-weight: 700;
}
.sort-select {
  padding: 4px 8px;
  border: 2px solid var(--pyt-color-border-strong, #4d3613);
  background: var(--pyt-color-bg-primary, #fef7e3);
  font: inherit;
}
.reset-button {
  min-height: 36px;
  padding: 0 12px;
  font-family: var(--pyt-font-pixel), monospace;
  background: var(--pyt-color-danger-bg, #fde2dc);
  border: 2px solid var(--pyt-color-border-strong, #4d3613);
  cursor: pointer;
  box-shadow: 2px 2px 0 var(--pyt-color-border-strong, #4d3613);
}

.problem-table {
  background: var(--pyt-color-bg-elevated, #fff7e6);
  border: 3px solid var(--pyt-color-border-strong, #4d3613);
  box-shadow: 4px 4px 0 var(--pyt-color-border-strong, #4d3613);
}
.problem-row {
  display: grid;
  grid-template-columns: 50px minmax(160px, 2fr) minmax(120px, 1.4fr) 70px 70px 60px 70px;
  gap: 8px;
  padding: 8px 12px;
  align-items: center;
  border-bottom: 2px dashed var(--pyt-color-border, #c9a86b);
  font-size: 13px;
}
.problem-row--head {
  background: var(--pyt-color-bg-soft, #fdf3d0);
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
  text-align: left;
  min-height: auto;
  box-shadow: none;
}
.row-title {
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
  font: inherit;
  text-align: left;
  text-decoration: underline;
  min-height: auto;
  box-shadow: none;
}
.badge {
  display: inline-block;
  background: var(--pyt-color-bg-soft, #fdf3d0);
  border: 1px solid var(--pyt-color-border, #c9a86b);
  padding: 1px 6px;
  font-family: var(--pyt-font-pixel), monospace;
  font-size: 11px;
  margin-right: 4px;
}
.badge.kc {
  background: var(--pyt-color-info-bg, #d0e3f1);
}
.diff-low { background: var(--pyt-color-success-bg, #d1eccf); }
.diff-medium { background: var(--pyt-color-warning-bg, #f5e2c1); }
.diff-high { background: var(--pyt-color-danger-bg, #fde2dc); }
.status-passed { background: var(--pyt-color-success-bg, #d1eccf); color: var(--pyt-color-success, #2f8f3c); }
.status-failed { background: var(--pyt-color-danger-bg, #fde2dc); color: var(--pyt-color-danger, #b8442d); }
.status-idle { background: var(--pyt-color-bg-soft, #fdf3d0); color: var(--pyt-color-text-muted, #8a6c2c); }

.attempts {
  font-family: var(--pyt-font-pixel), monospace;
  text-align: center;
}
.go-button {
  font-family: var(--pyt-font-pixel), monospace;
  min-height: 30px;
  padding: 0 10px;
  background: var(--pyt-color-primary, #6e4f1d);
  color: var(--pyt-color-text-on-dark, #fff7e6);
  border: 2px solid var(--pyt-color-border-strong, #4d3613);
  cursor: pointer;
  box-shadow: 2px 2px 0 var(--pyt-color-border-strong, #4d3613);
}
.row-preview {
  grid-column: 1 / -1;
  background: var(--pyt-color-bg-soft, #fdf3d0);
  border: 2px solid var(--pyt-color-border, #c9a86b);
  margin-top: 6px;
  padding: 8px 10px;
}
.row-preview pre {
  margin: 6px 0;
  white-space: pre-wrap;
  font-family: var(--pyt-font-sans, sans-serif);
  font-size: 13px;
  line-height: 1.6;
}
.row-preview .mistakes {
  margin: 0;
  font-size: 12px;
  color: var(--pyt-color-warning, #c2871f);
}
.empty {
  text-align: center;
  padding: 24px;
  margin: 0;
  color: var(--pyt-color-text-muted, #8a6c2c);
}
</style>
