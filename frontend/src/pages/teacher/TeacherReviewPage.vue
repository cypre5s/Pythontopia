<script setup lang="ts">
/** 教师审核 AI 出题页。完整题与审核决定均以后端 pack-43 review bank 为唯一权威。 */
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import AppLayout from "@/app/AppLayout.vue";
import RuntimeOperationsPanel from "@/components/workshop/RuntimeOperationsPanel.vue";
import { useSessionStore } from "@stores/session";
import { useToastStore } from "@stores/toast";
import {
  createProblemReview,
  decideProblemReview,
  listProblemReviews,
  type BossProblemReviewDecision,
  type BossProblemReviewDto,
} from "@/api/problemReview";

const session = useSessionStore();
const toast = useToastStore();
const router = useRouter();

const chapter = ref<number>(1);
const stage = ref<number>(1);
const kcCode = ref<string>("");
const kind = ref<string>("");
const loading = ref<boolean>(false);
const loadingQueue = ref<boolean>(false);
const deciding = ref<boolean>(false);
const entries = ref<BossProblemReviewDto[]>([]);
const decisionReason = ref<string>("");
const selectedId = ref<number | null>(null);
let queueRevision = 0;

const isTeacherOrAdmin = computed<boolean>(() => {
  const role = session.role;
  return role === "TEACHER" || role === "ADMIN";
});

const counts = computed(() => ({
  total: entries.value.length,
  approved: entries.value.filter((entry) => entry.decision === "APPROVED")
    .length,
  rejected: entries.value.filter((entry) => entry.decision === "REJECTED")
    .length,
  pending: entries.value.filter((entry) => entry.decision === "PENDING").length,
}));

async function loadQueue(): Promise<void> {
  if (!isTeacherOrAdmin.value || loadingQueue.value) return;
  loadingQueue.value = true;
  const revisionAtRequest = queueRevision;
  try {
    const loadedEntries = await listProblemReviews(100);
    entries.value =
      revisionAtRequest === queueRevision
        ? loadedEntries
        : [
            ...entries.value,
            ...loadedEntries.filter(
              (loaded) => !entries.value.some((current) => current.id === loaded.id),
            ),
          ];
    if (selectedId.value === null && entries.value.length > 0) {
      selectedId.value = entries.value[0]?.id ?? null;
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    toast.show(`审核队列加载失败：${message}`, "error");
  } finally {
    loadingQueue.value = false;
  }
}

async function fetchProblem(): Promise<void> {
  if (loading.value || loadingQueue.value || !isTeacherOrAdmin.value) return;
  loading.value = true;
  try {
    const requestedKc = kcCode.value.trim();
    const requestedKind = kind.value.trim();
    const candidate = await createProblemReview({
      chapter: chapter.value,
      stage: stage.value,
      ...(requestedKc ? { kcCode: requestedKc } : {}),
      ...(requestedKind ? { kind: requestedKind } : {}),
    });
    queueRevision += 1;
    entries.value = [
      candidate,
      ...entries.value.filter((entry) => entry.id !== candidate.id),
    ];
    selectedId.value = candidate.id;
    toast.show("AI 题已进入后端审核队列", "info");
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    toast.show(`拉题失败：${message}`, "error");
  } finally {
    loading.value = false;
  }
}

async function decideCurrent(
  decision: Exclude<BossProblemReviewDecision, "PENDING">,
): Promise<void> {
  const entry = currentEntry.value;
  const reason = decisionReason.value.trim();
  if (!entry || entry.decision !== "PENDING" || deciding.value) return;
  if (!reason) {
    toast.show("请填写审核意见", "error");
    return;
  }
  deciding.value = true;
  try {
    const updated = await decideProblemReview(entry.id, {
      decision,
      reason,
      version: entry.version,
    });
    queueRevision += 1;
    entries.value = entries.value.map((item) =>
      item.id === updated.id ? updated : item,
    );
    decisionReason.value = "";
    toast.show(
      decision === "APPROVED"
        ? "题目已发布到全局 approved bank"
        : "题目已驳回并保留审计记录",
      decision === "APPROVED" ? "success" : "error",
    );
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    toast.show(`审核失败：${message}`, "error");
    await loadQueue();
  } finally {
    deciding.value = false;
  }
}

function approveCurrent(): void {
  void decideCurrent("APPROVED");
}

function rejectCurrent(): void {
  void decideCurrent("REJECTED");
}

function exitToHome(): void {
  void router.push({ name: "teacher" });
}

onMounted(() => {
  void loadQueue();
});

const currentEntry = computed<BossProblemReviewDto | null>(
  () => entries.value.find((entry) => entry.id === selectedId.value) ?? null,
);

const canDecide = computed<boolean>(
  () =>
    isTeacherOrAdmin.value &&
    currentEntry.value?.decision === "PENDING" &&
    decisionReason.value.trim().length > 0 &&
    !deciding.value,
);
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
            AI
            题经自动审查后进入后端持久化队列；教师决定、审核意见与版本均保留审计记录。
            只有通过的题目会进入学生 Boss 题库，隐藏用例始终只保存在后端。
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
        当前账号角色
        <strong>{{ session.role ?? "未登录" }}</strong> 没有审核权限； 请用
        TEACHER 或 ADMIN 账号登录后访问。
      </div>

      <RuntimeOperationsPanel class="review-runtime" />

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
            >第 {{ c }} 章</option>
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
            >Stage {{ s }}</option>
          </select>
        </label>
        <label class="toolbar-field">
          <span>KC（可选）</span>
          <input
            v-model="kcCode"
            data-testid="teacher-review-kc"
            maxlength="64"
            placeholder="留空则取本章首个 KC"
          >
        </label>
        <label class="toolbar-field">
          <span>题型（可选）</span>
          <input
            v-model="kind"
            data-testid="teacher-review-kind"
            maxlength="32"
            placeholder="如 debug"
          >
        </label>
        <button
          type="button"
          class="btn btn-primary"
          data-testid="teacher-review-fetch"
          :disabled="loading || loadingQueue || !isTeacherOrAdmin"
          @click="fetchProblem"
        >
          {{ loading ? "拉题中…" : "调 graph 出 1 道题" }}
        </button>
        <span
          class="counts"
          data-testid="teacher-review-counts"
        >
          总 {{ counts.total }} · 待审 {{ counts.pending }} ·
          <span class="ok">通过 {{ counts.approved }}</span> ·
          <span class="bad">驳回 {{ counts.rejected }}</span>
        </span>
        <span
          v-if="loadingQueue"
          class="muted"
        >正在同步后端队列…</span>
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
              v-for="entry in entries"
              :key="entry.id"
            >
              <button
                type="button"
                :data-testid="`teacher-review-select-${entry.id}`"
                :class="{
                  'list-item': true,
                  'list-item--current': entry.id === selectedId,
                  'list-item--approved': entry.decision === 'APPROVED',
                  'list-item--rejected': entry.decision === 'REJECTED',
                }"
                :aria-pressed="entry.id === selectedId"
                @click="selectedId = entry.id"
              >
                <header>
                  <strong>第 {{ entry.problem.chapter }} 章 S{{
                    entry.problem.stage
                  }}</strong>
                  <span class="badge">{{ entry.problem.kcCode }}</span>
                  <span
                    class="badge"
                    :class="{
                      'badge--ai': entry.problem.aiGenerated,
                      'badge--golden': !entry.problem.aiGenerated,
                    }"
                  >
                    {{ entry.problem.aiGenerated ? "AI" : "Golden" }}
                  </span>
                </header>
                <p class="list-statement">
                  {{ entry.problem.statement.slice(0, 60) }}…
                </p>
                <small class="list-meta">
                  <span
                    class="decision-dot"
                    :class="`decision-dot--${entry.decision.toLowerCase()}`"
                    aria-hidden="true"
                  />
                  {{
                    entry.decision === "PENDING"
                      ? "待审"
                      : entry.decision === "APPROVED"
                        ? "已通过"
                        : "已驳回"
                  }}
                  · {{ new Date(entry.requestedAt).toLocaleString() }}
                </small>
              </button>
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
                  {{
                    currentEntry.problem.reviewed
                      ? "5 重审查通过"
                      : "未通过审查"
                  }}
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
                <pre>{{ currentEntry.problem.sampleInput || "(无输入)" }}</pre>
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
              v-if="currentEntry.decision !== 'PENDING'"
              class="decision-recap"
              :class="{
                'decision-recap--approved':
                  currentEntry.decision === 'APPROVED',
                'decision-recap--rejected':
                  currentEntry.decision === 'REJECTED',
              }"
            >
              <strong>{{
                currentEntry.decision === "APPROVED" ? "已通过" : "已驳回"
              }}</strong>
              <span v-if="currentEntry.decisionReason">
                · 审核意见：{{ currentEntry.decisionReason }}
              </span>
              <span v-if="currentEntry.deciderUsername">
                · 审核人：{{ currentEntry.deciderUsername }}
              </span>
              <small>{{
                currentEntry.decidedAt
                  ? new Date(currentEntry.decidedAt).toLocaleString()
                  : ""
              }}</small>
            </section>
            <footer class="actions">
              <button
                type="button"
                class="btn btn-ok"
                data-testid="teacher-review-approve"
                :disabled="!canDecide"
                @click="approveCurrent"
              >
                通过 · 上线该题
              </button>
              <textarea
                v-model="decisionReason"
                class="reject-input"
                rows="2"
                maxlength="1000"
                :disabled="currentEntry.decision !== 'PENDING' || deciding"
                placeholder="审核意见（通过或驳回均必填，写入后端审计记录）"
                data-testid="teacher-review-decision-reason"
              />
              <button
                type="button"
                class="btn btn-bad"
                data-testid="teacher-review-reject"
                :disabled="!canDecide"
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
  background: linear-gradient(
    180deg,
    var(--pyt-color-bg-elevated) 0%,
    #f1d896 100%
  );
  font-family: var(--pyt-font-pixel), "PingFang SC", monospace;
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
  color: var(--pyt-color-text-hint);
  max-width: 720px;
}
.btn {
  font-family: inherit;
  cursor: pointer;
  font-size: 13px;
  padding: 6px 12px;
  border: 2px solid var(--pyt-color-text-hint);
  background: var(--pyt-color-bg-soft);
  color: #3a2a14;
  border-radius: 4px;
  box-shadow: 2px 2px 0 var(--pyt-color-text-hint);
}
.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  box-shadow: none;
}
.btn:active:not(:disabled) {
  transform: translate(2px, 2px);
  box-shadow: 0 0 0 var(--pyt-color-text-hint);
}
.btn-back {
  background: var(--pyt-color-warning-bg);
}
.btn-primary {
  background: var(--pyt-color-gold);
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
  color: var(--pyt-color-text-hint);
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
.review-runtime {
  box-shadow: 2px 2px 0 var(--pyt-color-text-primary);
}
.review-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-text-primary);
  box-shadow: 3px 3px 0 var(--pyt-color-text-primary);
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
  border: 2px solid var(--pyt-color-text-hint);
}
.counts {
  font-size: 12px;
  color: var(--pyt-color-text-hint);
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
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-text-primary);
  box-shadow: 3px 3px 0 var(--pyt-color-text-primary);
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
  width: 100%;
  padding: 8px;
  border: 2px solid var(--pyt-color-border);
  background: var(--pyt-color-bg-soft);
  cursor: pointer;
  border-radius: 3px;
  color: inherit;
  font: inherit;
  text-align: left;
}
.list-item:hover {
  background: var(--pyt-color-warning-bg);
}
.list-item:focus-visible {
  outline: 3px solid var(--pyt-color-text-hint);
  outline-offset: 2px;
}
.list-item--current {
  outline: 3px solid var(--pyt-color-text-hint);
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
  color: var(--pyt-color-text-primary);
  line-height: 1.5;
  word-break: break-word;
}
.list-meta {
  font-size: 10px;
  color: var(--pyt-color-text-muted);
  display: flex;
  align-items: center;
  gap: 4px;
}
.decision-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border: 1px solid var(--pyt-color-text-primary);
  background: var(--pyt-color-bg-soft);
}
.decision-dot--approved {
  background: #4d7a2e;
}
.decision-dot--rejected {
  background: #8c2a08;
}
.review-detail {
  overflow: auto;
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-text-primary);
  box-shadow: 3px 3px 0 var(--pyt-color-text-primary);
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
  color: var(--pyt-color-text-primary);
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.statement {
  margin: 0;
  padding: 10px;
  background: var(--pyt-color-bg-soft);
  border: 2px solid var(--pyt-color-border);
  font-family: "Consolas", "JetBrains Mono", "Cascadia Code", Menlo,
    "Courier New", monospace;
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
  font-family: "Consolas", "JetBrains Mono", "Cascadia Code", Menlo,
    "Courier New", monospace;
  font-size: 12px;
  border: 2px solid var(--pyt-color-text-primary);
}
.decision-recap {
  padding: 8px 12px;
  border: 2px solid var(--pyt-color-text-primary);
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
  color: var(--pyt-color-text-hint);
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
  border: 2px solid var(--pyt-color-text-hint);
  background: var(--pyt-color-bg-soft);
  resize: vertical;
}
.badge {
  font-size: 10px;
  padding: 1px 6px;
  background: var(--pyt-color-warning-bg);
  border: 1px solid var(--pyt-color-border);
  border-radius: 3px;
}
.badge--ai {
  background: #d6ead1;
  border-color: #4d7a2e;
}
.badge--golden {
  background: var(--pyt-color-warning-bg);
  border-color: var(--pyt-color-border);
}
.badge--reviewed {
  background: var(--pyt-color-info-bg);
  border-color: var(--pyt-color-info);
}
.muted {
  font-size: 13px;
  color: var(--pyt-color-text-hint);
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
