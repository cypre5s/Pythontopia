<script setup lang="ts">
/**
 * R-13 §2.3-6 教师审核兜底页面。
 *
 * 设计取舍（演示节奏）：
 *   - 不引入新的后端表 / API（R-14 才落 `boss_problem_bank.status='reviewable'`）；
 *   - 兜底用「按需触发 graph 出题」+「在内存里维护审核状态」演示界面流程；
 *   - approve / reject 按钮把 review 决定写入 localStorage（教师跨设备同步留 R-14）。
 *
 * 教师价值：
 *   1. 实时召出 1 道 AI 出题，亲眼看 5 重审查结论（reviewed/aiGenerated badge）；
 *   2. 阅读题面 / 提示，决定是否「上线」给学生；
 *   3. 被 reject 的题写 LearningEvent('AI_PROBLEM_REJECTED')（待 R-14 接入后端）。
 *
 * 路由：/teacher/review，需要 TEACHER 或 ADMIN 角色；视图层不做硬鉴权（路由 meta + JWT 角色）。
 */
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import { useSessionStore } from '@stores/session';
import { useToastStore } from '@stores/toast';
import { requestBossProblem } from '@/api/boss';
import type { BossProblemDto } from '@/api/dto/Boss';

const PERSIST_KEY = 'pyt_teacher_review_decisions_v1';

type Decision = 'pending' | 'approved' | 'rejected';

interface ReviewEntry {
  problem: BossProblemDto;
  decision: Decision;
  reason?: string;
  decidedAt?: string;
  fetchedAt: string;
}

const session = useSessionStore();
const toast = useToastStore();
const router = useRouter();

const chapter = ref<number>(1);
const stage = ref<number>(1);
const loading = ref<boolean>(false);
const entries = ref<ReviewEntry[]>([]);
const rejectReason = ref<string>('');
const selectedIdx = ref<number | null>(null);

const isTeacherOrAdmin = computed<boolean>(() => {
  const role = session.role;
  return role === 'TEACHER' || role === 'ADMIN';
});

const counts = computed(() => ({
  total: entries.value.length,
  approved: entries.value.filter((e) => e.decision === 'approved').length,
  rejected: entries.value.filter((e) => e.decision === 'rejected').length,
  pending: entries.value.filter((e) => e.decision === 'pending').length,
}));

function loadPersisted(): void {
  if (typeof window === 'undefined') return;
  try {
    const raw = window.localStorage.getItem(PERSIST_KEY);
    if (!raw) return;
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) {
      entries.value = parsed.filter((e) => e && typeof e === 'object' && 'problem' in e);
    }
  } catch {
    // ignore corrupt storage
  }
}

function persist(): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(PERSIST_KEY, JSON.stringify(entries.value));
  } catch {
    // private mode etc.
  }
}

async function fetchProblem(): Promise<void> {
  if (loading.value) return;
  loading.value = true;
  try {
    const problem = await requestBossProblem(chapter.value, stage.value);
    entries.value = [
      {
        problem,
        decision: 'pending',
        fetchedAt: new Date().toISOString(),
      },
      ...entries.value,
    ];
    persist();
    selectedIdx.value = 0;
    toast.show(
      problem.aiGenerated ? 'AI 出题成功，请审核' : '回落 golden 题（graph 未连通）',
      'info',
    );
  } catch (error) {
    const msg = error instanceof Error ? error.message : String(error);
    toast.show(`拉题失败：${msg}`, 'error');
  } finally {
    loading.value = false;
  }
}

function setDecision(idx: number, decision: Decision, reason?: string): void {
  const entry = entries.value[idx];
  if (!entry) return;
  entry.decision = decision;
  if (reason !== undefined) {
    entry.reason = reason;
  }
  entry.decidedAt = new Date().toISOString();
  persist();
  toast.show(
    `${entry.problem.problemId} → ${decision}${reason ? `（${reason}）` : ''}`,
    decision === 'approved' ? 'success' : 'error',
  );
}

function approveCurrent(): void {
  if (selectedIdx.value !== null) setDecision(selectedIdx.value, 'approved');
}

function rejectCurrent(): void {
  if (selectedIdx.value === null) return;
  const reason = rejectReason.value.trim() || '未提供原因';
  const entry = entries.value[selectedIdx.value];
  setDecision(selectedIdx.value, 'rejected', reason);
  rejectReason.value = '';
  if (entry) {
    void import('@/api/learningEvent').then((mod) => {
      mod.postLearningEvent({
        eventType: 'AI_PROBLEM_REJECTED',
        payload: {
          problemId: entry.problem.problemId,
          chapter: entry.problem.chapter,
          stage: entry.problem.stage,
          kcCode: entry.problem.kcCode,
          reason,
        },
      }).catch(() => {});
    });
  }
}

function clearAll(): void {
  entries.value = [];
  persist();
  toast.show('已清空审核队列', 'info');
}

function exitToHome(): void {
  void router.push({ name: 'teacher' });
}

onMounted(() => {
  loadPersisted();
});

const currentEntry = computed<ReviewEntry | null>(() => {
  if (selectedIdx.value === null) return null;
  return entries.value[selectedIdx.value] ?? null;
});
</script>

<template>
  <AppLayout>
    <section
      class="review-page"
      data-testid="teacher-review-page"
    >
      <header class="review-header">
        <div>
          <h1 class="title">
            教师审核 · AI 出题兜底
          </h1>
          <p class="subtitle">
            R-13 §2.3-6：5 重审查链通过后的题目仍由教师手动「上线」或「驳回」；
            驳回会写 <code>LearningEvent('AI_PROBLEM_REJECTED')</code>（R-14 后端落库）。
          </p>
        </div>
        <button
          type="button"
          class="btn btn-back"
          data-testid="teacher-review-back"
          @click="exitToHome"
        >
          ← 返回教师中心
        </button>
      </header>

      <div
        v-if="!isTeacherOrAdmin"
        class="role-warning"
        data-testid="teacher-review-role-warning"
      >
        当前账号角色 <strong>{{ session.role ?? '未登录' }}</strong> 没有审核权限；
        请用 TEACHER 或 ADMIN 账号登录后访问。R-14 后端将硬鉴权该路由。
      </div>

      <div class="review-toolbar">
        <label class="toolbar-field">
          <span>章节</span>
          <select
            v-model.number="chapter"
            data-testid="teacher-review-chapter"
          >
            <option
              v-for="c in 7"
              :key="c"
              :value="c"
            >
              第 {{ c }} 章
            </option>
          </select>
        </label>
        <label class="toolbar-field">
          <span>Stage</span>
          <select
            v-model.number="stage"
            data-testid="teacher-review-stage"
          >
            <option
              v-for="s in 4"
              :key="s"
              :value="s"
            >
              Stage {{ s }}
            </option>
          </select>
        </label>
        <button
          type="button"
          class="btn btn-primary"
          data-testid="teacher-review-fetch"
          :disabled="loading"
          @click="fetchProblem"
        >
          {{ loading ? '拉题中…' : '调 graph 出 1 道题' }}
        </button>
        <span class="counts" data-testid="teacher-review-counts">
          总 {{ counts.total }} · 待审 {{ counts.pending }} ·
          <span class="ok">通过 {{ counts.approved }}</span> ·
          <span class="bad">驳回 {{ counts.rejected }}</span>
        </span>
        <button
          v-if="entries.length > 0"
          type="button"
          class="btn btn-ghost"
          data-testid="teacher-review-clear"
          @click="clearAll"
        >
          清空队列
        </button>
      </div>

      <div class="review-body">
        <aside
          class="review-list"
          aria-label="审核队列"
          data-testid="teacher-review-list"
        >
          <p
            v-if="entries.length === 0"
            class="muted"
          >
            队列为空：点击上方按钮调 graph 出题，结果会出现在这里。
          </p>
          <ul v-else>
            <li
              v-for="(entry, idx) in entries"
              :key="`${entry.problem.problemId}-${entry.fetchedAt}`"
              :class="{
                'list-item': true,
                'list-item--current': idx === selectedIdx,
                'list-item--approved': entry.decision === 'approved',
                'list-item--rejected': entry.decision === 'rejected',
              }"
              @click="selectedIdx = idx"
            >
              <header>
                <strong>第 {{ entry.problem.chapter }} 章 S{{ entry.problem.stage }}</strong>
                <span class="badge">{{ entry.problem.kcCode }}</span>
                <span
                  class="badge"
                  :class="{
                    'badge--ai': entry.problem.aiGenerated,
                    'badge--golden': !entry.problem.aiGenerated,
                  }"
                >
                  {{ entry.problem.aiGenerated ? 'AI' : 'Golden' }}
                </span>
              </header>
              <p class="list-statement">
                {{ entry.problem.statement.slice(0, 60) }}…
              </p>
              <small class="list-meta">
                <span
                  class="decision-dot"
                  :class="`decision-dot--${entry.decision}`"
                  aria-hidden="true"
                />
                {{ entry.decision === 'pending' ? '待审' : entry.decision === 'approved' ? '已通过' : '已驳回' }}
                · {{ new Date(entry.fetchedAt).toLocaleString() }}
              </small>
            </li>
          </ul>
        </aside>

        <main
          class="review-detail"
          aria-label="审核详情"
          data-testid="teacher-review-detail"
        >
          <article
            v-if="currentEntry"
            class="detail-card"
          >
            <header class="detail-head">
              <h2>
                {{ currentEntry.problem.problemId }}
                <span
                  class="badge"
                  :class="{
                    'badge--reviewed': currentEntry.problem.reviewed,
                  }"
                >
                  {{ currentEntry.problem.reviewed ? '5 重审查通过' : '未通过审查' }}
                </span>
              </h2>
              <p>
                <strong>KC：</strong>{{ currentEntry.problem.kcCode }}
                <strong>难度：</strong>{{ currentEntry.problem.difficulty }} / 5
                <strong>测试用例数：</strong>{{ currentEntry.problem.totalTestCases }}
              </p>
            </header>
            <section>
              <h3>题面</h3>
              <pre class="statement">{{ currentEntry.problem.statement }}</pre>
            </section>
            <section>
              <h3>样例</h3>
              <div class="sample-pair">
                <pre>{{ currentEntry.problem.sampleInput || '(无输入)' }}</pre>
                <pre>{{ currentEntry.problem.sampleOutput }}</pre>
              </div>
            </section>
            <section v-if="currentEntry.problem.hints.length > 0">
              <h3>提示</h3>
              <ul>
                <li
                  v-for="(h, i) in currentEntry.problem.hints"
                  :key="i"
                >
                  {{ h }}
                </li>
              </ul>
            </section>
            <section
              v-if="currentEntry.decision !== 'pending'"
              class="decision-recap"
              :class="{
                'decision-recap--approved': currentEntry.decision === 'approved',
                'decision-recap--rejected': currentEntry.decision === 'rejected',
              }"
            >
              <strong>{{ currentEntry.decision === 'approved' ? '已通过' : '已驳回' }}</strong>
              <span v-if="currentEntry.reason">
                · 原因：{{ currentEntry.reason }}
              </span>
              <small>{{ currentEntry.decidedAt ? new Date(currentEntry.decidedAt).toLocaleString() : '' }}</small>
            </section>
            <footer class="actions">
              <button
                type="button"
                class="btn btn-ok"
                data-testid="teacher-review-approve"
                :disabled="!isTeacherOrAdmin"
                @click="approveCurrent"
              >
                通过 · 上线该题
              </button>
              <textarea
                v-model="rejectReason"
                class="reject-input"
                rows="2"
                placeholder="驳回原因（必填，将记入 LearningEvent）"
                data-testid="teacher-review-reject-reason"
              />
              <button
                type="button"
                class="btn btn-bad"
                data-testid="teacher-review-reject"
                :disabled="!isTeacherOrAdmin"
                @click="rejectCurrent"
              >
                驳回该题
              </button>
            </footer>
          </article>
          <p
            v-else
            class="muted"
          >
            从左侧列表选一条题目查看详情。
          </p>
        </main>
      </div>
    </section>
  </AppLayout>
</template>

<style scoped>
.review-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  min-height: calc(100dvh - 56px);
  background: linear-gradient(180deg, #fff7e6 0%, #f1d896 100%);
  font-family: 'PythontopiaPixel', 'PingFang SC', monospace;
  color: #3a2a14;
}
.review-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}
.title {
  margin: 0 0 4px;
  font-size: 22px;
}
.subtitle {
  margin: 0;
  font-size: 13px;
  color: #6e4f1d;
  max-width: 720px;
}
.btn {
  font-family: inherit;
  cursor: pointer;
  font-size: 13px;
  padding: 6px 12px;
  border: 2px solid #6e4f1d;
  background: #fdf3d0;
  color: #3a2a14;
  border-radius: 4px;
  box-shadow: 2px 2px 0 #6e4f1d;
}
.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  box-shadow: none;
}
.btn:active:not(:disabled) {
  transform: translate(2px, 2px);
  box-shadow: 0 0 0 #6e4f1d;
}
.btn-back {
  background: #f5e2c1;
}
.btn-primary {
  background: #f1c453;
  color: #2a1a08;
}
.btn-ok {
  background: #b8d99a;
  color: #1f3a14;
  border-color: #4d7a2e;
  box-shadow: 2px 2px 0 #4d7a2e;
}
.btn-bad {
  background: #f0bca6;
  color: #4d1a08;
  border-color: #8c2a08;
  box-shadow: 2px 2px 0 #8c2a08;
}
.btn-ghost {
  background: transparent;
  color: #6e4f1d;
  box-shadow: none;
  border-style: dashed;
}
.role-warning {
  padding: 10px 14px;
  background: #ffe2cc;
  border: 2px solid #c95e3a;
  color: #5a1a08;
  font-size: 13px;
}
.review-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: #fff7e6;
  border: 2px solid #4d3613;
  box-shadow: 3px 3px 0 #4d3613;
  flex-wrap: wrap;
}
.toolbar-field {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
}
.toolbar-field select {
  font-family: inherit;
  font-size: 12px;
  padding: 2px 6px;
  border: 2px solid #6e4f1d;
}
.counts {
  font-size: 12px;
  color: #6e4f1d;
}
.counts .ok {
  color: #2d5b1b;
}
.counts .bad {
  color: #8c2a08;
}
.review-body {
  flex: 1 1 auto;
  display: grid;
  grid-template-columns: minmax(280px, 28%) 1fr;
  gap: 12px;
  min-height: 0;
}
.review-list {
  overflow: auto;
  background: #fff7e6;
  border: 2px solid #4d3613;
  box-shadow: 3px 3px 0 #4d3613;
  padding: 8px;
  max-height: calc(100dvh - 220px);
}
.review-list ul {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 6px;
}
.list-item {
  display: grid;
  gap: 3px;
  padding: 8px;
  border: 2px solid #c9a86b;
  background: #fdf3d0;
  cursor: pointer;
  border-radius: 3px;
}
.list-item:hover {
  background: #f5e2c1;
}
.list-item--current {
  outline: 3px solid #6e4f1d;
  outline-offset: -1px;
}
.list-item--approved {
  background: #d6ead1;
  border-color: #4d7a2e;
}
.list-item--rejected {
  background: #f5d4c8;
  border-color: #8c2a08;
}
.list-item header {
  display: flex;
  align-items: center;
  gap: 6px;
}
.list-statement {
  margin: 0;
  font-size: 12px;
  color: #4d3613;
  line-height: 1.5;
  word-break: break-word;
}
.list-meta {
  font-size: 10px;
  color: #8a6c2c;
  display: flex;
  align-items: center;
  gap: 4px;
}
.decision-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border: 1px solid #4d3613;
  background: #fdf3d0;
}
.decision-dot--approved {
  background: #4d7a2e;
}
.decision-dot--rejected {
  background: #8c2a08;
}
.review-detail {
  overflow: auto;
  background: #fff7e6;
  border: 2px solid #4d3613;
  box-shadow: 3px 3px 0 #4d3613;
  padding: 14px;
  max-height: calc(100dvh - 220px);
}
.detail-card {
  display: grid;
  gap: 12px;
}
.detail-head h2 {
  margin: 0 0 4px;
  font-size: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.detail-head p {
  margin: 0;
  font-size: 12px;
  color: #4d3613;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.statement {
  margin: 0;
  padding: 10px;
  background: #fdf3d0;
  border: 2px solid #c9a86b;
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
}
.sample-pair {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.sample-pair pre {
  margin: 0;
  padding: 8px;
  background: #1a0e04;
  color: #f6f1e6;
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  border: 2px solid #4d3613;
}
.decision-recap {
  padding: 8px 12px;
  border: 2px solid #4d3613;
  font-size: 13px;
}
.decision-recap--approved {
  background: #d6ead1;
  border-color: #4d7a2e;
}
.decision-recap--rejected {
  background: #f5d4c8;
  border-color: #8c2a08;
}
.decision-recap small {
  display: block;
  margin-top: 4px;
  color: #6e4f1d;
}
.actions {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 8px;
  align-items: start;
}
.reject-input {
  font-family: inherit;
  font-size: 12px;
  padding: 6px;
  border: 2px solid #6e4f1d;
  background: #fdf3d0;
  resize: vertical;
}
.badge {
  font-size: 10px;
  padding: 1px 6px;
  background: #f5e2c1;
  border: 1px solid #c9a86b;
  border-radius: 3px;
}
.badge--ai {
  background: #d6ead1;
  border-color: #4d7a2e;
}
.badge--golden {
  background: #f5e2c1;
  border-color: #c9a86b;
}
.badge--reviewed {
  background: #d0e3f1;
  border-color: #2a6f97;
}
.muted {
  font-size: 13px;
  color: #6e4f1d;
}
@media (max-width: 900px) {
  .review-body {
    grid-template-columns: 1fr;
  }
  .actions {
    grid-template-columns: 1fr;
  }
}
</style>
