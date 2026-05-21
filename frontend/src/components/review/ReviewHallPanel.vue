<script setup lang="ts">
import { computed, ref } from 'vue';
import MetricCard from './MetricCard.vue';
import KcGrowthList from './KcGrowthList.vue';
import PathDiffView from './PathDiffView.vue';
import { useLearningStore } from '@stores/learning';
import { useToastStore } from '@stores/toast';
import type { EvaluationDto } from '@/api/dto/Evaluation';

const learning = useLearningStore();
const toast = useToastStore();
const runPhase = ref(false);

const list = computed<EvaluationDto[]>(() => learning.evaluation.list);
const current = computed<EvaluationDto | null>(() => learning.evaluation.current);
const loading = computed(() => learning.evaluation.loading);
const lastError = computed(() => learning.evaluation.lastError);
const isPending = computed(() => current.value?.actedStatus === 'PENDING');

async function runEvaluation(): Promise<void> {
  runPhase.value = true;
  try {
    const created = await learning.runEvaluationNow({ periodDays: 7 });
    if (created) {
      toast.show('评估完成，已生成新路径建议。', 'success');
    } else if (learning.evaluation.lastError) {
      toast.show(learning.evaluation.lastError, 'error');
    }
  } finally {
    runPhase.value = false;
  }
}

async function openEvaluation(id: number): Promise<void> {
  await learning.openEvaluation(id);
}

async function accept(): Promise<void> {
  if (!current.value) return;
  const id = current.value.id;
  const updated = await learning.acceptEvaluationPath(id);
  if (updated) {
    toast.show('已接受新路径，旧路径已归档。', 'success');
  } else if (learning.evaluation.lastError) {
    toast.show(learning.evaluation.lastError, 'error');
  }
}

async function keepOld(): Promise<void> {
  if (!current.value) return;
  const id = current.value.id;
  const updated = await learning.keepCurrentEvaluationPath(id);
  if (updated) {
    toast.show('已保留当前路径，DRAFT 已归档。', 'info');
  } else if (learning.evaluation.lastError) {
    toast.show(learning.evaluation.lastError, 'error');
  }
}
</script>

<template>
  <section
    class="review"
    data-testid="review-hall-panel"
  >
    <header class="header">
      <div>
        <h2>复盘大厅</h2>
        <p class="hint">
          复盘大厅是教育闭环的"再播种"环节：基于近 7 天的学习事件生成多维评估，narrative 仅引用真实
          mastery / KC / 提交数据，不会捏造结论。先在小镇里完成几次画像 / 资源 / 提交，再来评估效果更佳。
        </p>
      </div>
      <button
        type="button"
        class="primary"
        :disabled="runPhase"
        data-testid="run-evaluation"
        @click="runEvaluation"
      >
        {{ runPhase ? '评估中…' : '立即评估' }}
      </button>
    </header>

    <p
      v-if="lastError"
      class="error"
    >
      {{ lastError }}
    </p>

    <section class="history">
      <h3>历史评估</h3>
      <ul
        v-if="list.length"
        class="entries"
      >
        <li
          v-for="entry in list"
          :key="entry.id"
          :class="['entry', { active: current && current.id === entry.id }]"
        >
          <button
            type="button"
            class="entry-button"
            @click="openEvaluation(entry.id)"
          >
            <span class="entry-time">{{ entry.createdAt }}</span>
            <span :class="['entry-status', `status-${entry.actedStatus.toLowerCase()}`]">
              {{ entry.actedStatus }}
            </span>
          </button>
        </li>
      </ul>
      <p
        v-else
        class="empty"
      >
        还没有评估。建议先在小镇里完成画像、解锁 ≥ 1 个 KC、提交 1 道练习，再回到复盘大厅点击"立即评估"。
      </p>
    </section>

    <section
      v-if="current"
      class="detail"
    >
      <header class="detail-header">
        <h3>评估 #{{ current.id }}</h3>
        <span class="ai-tag">AI 生成解释 · 仅作建议</span>
      </header>
      <div
        v-if="loading"
        class="loading"
      >
        加载中…
      </div>
      <div
        v-else
        class="metric-grid"
      >
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

      <footer
        v-if="isPending"
        class="actions"
      >
        <button
          type="button"
          class="accept"
          data-testid="accept-new-path"
          :disabled="loading || !current.draftPathId"
          @click="accept"
        >
          接受新路径
        </button>
        <button
          type="button"
          class="keep"
          data-testid="keep-old"
          :disabled="loading"
          @click="keepOld"
        >
          保留当前
        </button>
      </footer>
      <p
        v-else
        class="acted-note"
      >
        当前状态：{{ current.actedStatus }} · 操作已完成，可再次触发评估。
      </p>
    </section>
  </section>
</template>

<style scoped>
.review {
  background: linear-gradient(180deg, #fff7e6 0%, #fdf3d0 100%);
  border: 2px solid #c9a86b;
  border-radius: 10px;
  padding: 16px;
  display: grid;
  gap: 14px;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.header h2 {
  margin: 0;
}
.hint {
  margin: 4px 0 0;
  color: #6e4f1d;
  font-size: 12px;
}
.primary {
  background: #6e4f1d;
  color: #fff7e6;
  border: 2px solid #4d3613;
  border-radius: 4px;
  padding: 6px 16px;
  cursor: pointer;
  font-weight: 600;
}
.primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.error {
  background: #fde2dc;
  border: 1px solid #b8442d;
  color: #5b2017;
  border-radius: 6px;
  padding: 6px 10px;
  margin: 0;
  font-size: 12px;
}
.history h3 {
  margin: 0 0 6px;
  color: #4d3613;
}
.entries {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.entry-button {
  background: #fff7e6;
  border: 1px solid #c9a86b;
  border-radius: 6px;
  padding: 6px 10px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  color: #4d3613;
}
.entry.active .entry-button {
  background: #6e4f1d;
  color: #fff7e6;
  border-color: #4d3613;
}
.entry-time {
  font-size: 11px;
  font-family: 'JetBrains Mono', monospace;
}
.entry-status {
  font-size: 11px;
  padding: 1px 4px;
  border-radius: 4px;
  background: #f3e4be;
  color: #4d3613;
}
.entry-status.status-accepted {
  background: #d1eccf;
  color: #2f8f3c;
}
.entry-status.status-kept_old {
  background: #f5e2c1;
  color: #6e4f1d;
}
.entry-status.status-pending {
  background: #f3e4be;
  color: #4d3613;
}
.empty {
  margin: 0;
  font-size: 12px;
  color: #8a6c2c;
}
.detail {
  display: grid;
  gap: 12px;
}
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.detail-header h3 {
  margin: 0;
  color: #4d3613;
}
.ai-tag {
  background: #f3e4be;
  border: 1px dashed #c9a86b;
  color: #6e4f1d;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
}
.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 10px;
}
.loading {
  font-size: 12px;
  color: #6e4f1d;
}
.actions {
  display: flex;
  gap: 10px;
}
.actions .accept {
  background: #2f8f3c;
  color: #fff7e6;
  border: 2px solid #1f6a28;
  border-radius: 4px;
  padding: 6px 16px;
  font-weight: 600;
  cursor: pointer;
}
.actions .accept:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.actions .keep {
  background: #fff7e6;
  color: #4d3613;
  border: 2px solid #c9a86b;
  border-radius: 4px;
  padding: 6px 16px;
  font-weight: 600;
  cursor: pointer;
}
.acted-note {
  font-size: 12px;
  color: #6e4f1d;
}
</style>
