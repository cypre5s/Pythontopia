<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { fetchAiRuntimeOperations, type AiRuntimeOperations, type RuntimeOperationStatus } from '@/api/aiRuntime';
import AdminPanel from './AdminPanel.vue';

const operations = ref<AiRuntimeOperations | null>(null);
const loading = ref(false);
const error = ref<string | null>(null);

const evidenceCoverage = computed(() => {
  const subject = operations.value?.subject;
  if (!subject || subject.learnerCount === 0) return '暂无学习者数据';
  return `${Math.round((subject.evidenceLearnerCount / subject.learnerCount) * 100)}% 已有学习证据`;
});

function statusLabel(status: RuntimeOperationStatus): string {
  if (status === 'PASS') return '通过';
  if (status === 'WARN') return '需关注';
  return '证据不足';
}

function statusClass(status: RuntimeOperationStatus): string {
  if (status === 'PASS') return 'status-pill--pass';
  if (status === 'WARN') return 'status-pill--warn';
  return 'status-pill--insufficient';
}

async function loadOperations(): Promise<void> {
  if (loading.value) return;
  loading.value = true;
  error.value = null;
  try {
    operations.value = await fetchAiRuntimeOperations();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '运维数据暂不可用';
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void loadOperations();
});
</script>

<template>
  <section
    class="admin-operations"
    data-testid="admin-operations"
    :aria-busy="loading"
  >
    <p class="admin-operations__intro">
      平台统计来自后端 AI Runtime 快照，按当前服务端返回展示。
    </p>

    <p
      v-if="loading"
      class="admin-operations__state"
      role="status"
      aria-live="polite"
    >
      正在读取平台运维数据…
    </p>

    <div
      v-if="error"
      class="admin-operations__error"
      role="alert"
    >
      <p>读取失败：{{ error }}</p>
      <button
        type="button"
        class="admin-button"
        :disabled="loading"
        @click="loadOperations"
      >
        {{ loading ? '正在重试…' : '重试' }}
      </button>
    </div>

    <template v-if="operations">
      <div class="admin-operations__stats">
        <article class="admin-stat-card">
          <span>活跃学习者</span>
          <strong>{{ operations.subject.learnerCount }}</strong>
          <small>{{ operations.subject.label }}</small>
        </article>
        <article class="admin-stat-card">
          <span>已有学习证据</span>
          <strong>{{ operations.subject.evidenceLearnerCount }}</strong>
          <small>{{ evidenceCoverage }}</small>
        </article>
      </div>

      <div class="admin-operations__panels">
        <AdminPanel
          title="学习闭环"
          description="当前平台学习流程的运行状态与下一步动作"
        >
          <p
            v-if="operations.loops.length === 0"
            class="admin-operations__empty"
          >
            后端当前没有返回学习闭环记录。
          </p>
          <ul
            v-else
            class="operation-list"
          >
            <li
              v-for="loop in operations.loops"
              :key="loop.loopType"
              class="operation-row"
            >
              <span
                class="status-pill"
                :class="statusClass(loop.status)"
              >{{ statusLabel(loop.status) }}</span>
              <div class="operation-row__body">
                <strong>{{ loop.label }}</strong>
                <span>{{ loop.nextAction }}</span>
                <small v-if="loop.latestTraceId">Trace {{ loop.latestTraceId }}</small>
              </div>
              <span
                v-if="loop.evidenceRefs.length"
                class="operation-row__evidence"
                :title="loop.evidenceRefs.join(' · ')"
              >
                {{ loop.evidenceRefs.length }} 项证据
              </span>
            </li>
          </ul>
        </AdminPanel>

        <AdminPanel
          title="治理检查"
          description="后端返回的治理状态和建议动作"
        >
          <p
            v-if="operations.governance.length === 0"
            class="admin-operations__empty"
          >
            后端当前没有返回治理检查项。
          </p>
          <ul
            v-else
            class="operation-list"
          >
            <li
              v-for="item in operations.governance"
              :key="item.key"
              class="operation-row"
            >
              <span
                class="status-pill"
                :class="statusClass(item.status)"
              >{{ statusLabel(item.status) }}</span>
              <div class="operation-row__body">
                <strong>{{ item.label }}</strong>
                <span>{{ item.action }}</span>
                <small v-if="item.reviewRoute">关联页面：{{ item.reviewRoute }}</small>
              </div>
              <span
                v-if="item.evidenceRefs.length"
                class="operation-row__evidence"
                :title="item.evidenceRefs.join(' · ')"
              >
                {{ item.evidenceRefs.length }} 项证据
              </span>
            </li>
          </ul>
        </AdminPanel>
      </div>
    </template>
  </section>
</template>

<style scoped>
.admin-operations {
  display: grid;
  gap: 20px;
  min-width: 0;
}

.admin-operations__intro {
  margin: 0;
  color: var(--admin-muted);
  font-size: 13px;
}

.admin-operations__state,
.admin-operations__empty {
  margin: 0;
  color: var(--admin-muted);
  font-size: 13px;
}

.admin-operations__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid #fecaca;
  border-radius: 10px;
  background: #fff7f7;
  color: #991b1b;
  font-size: 13px;
}

.admin-operations__error p {
  margin: 0;
  overflow-wrap: anywhere;
}

.admin-button {
  min-height: 36px;
  padding: 0 13px;
  border: 1px solid #cbd5e1;
  border-radius: 7px;
  background: #fff;
  color: #334155;
  font: inherit;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.admin-button:hover:not(:disabled) {
  border-color: #93c5fd;
  color: var(--admin-primary-hover);
}

.admin-button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.admin-operations__stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.admin-stat-card {
  display: grid;
  min-height: 116px;
  align-content: center;
  gap: 5px;
  padding: 20px 22px;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-panel-radius);
  background: var(--admin-surface);
  box-shadow: var(--admin-panel-shadow);
}

.admin-stat-card span,
.admin-stat-card small {
  color: var(--admin-muted);
  font-size: 12px;
}

.admin-stat-card strong {
  color: var(--admin-text);
  font-size: 27px;
  font-variant-numeric: tabular-nums;
  line-height: 1.15;
}

.admin-operations__panels {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: start;
  gap: 16px;
}

.operation-list {
  display: grid;
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.operation-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: start;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #edf1f6;
}

.operation-row:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.operation-row__body {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.operation-row__body strong {
  color: var(--admin-text);
  font-size: 13px;
}

.operation-row__body span,
.operation-row__body small,
.operation-row__evidence {
  overflow: hidden;
  color: var(--admin-muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-pill {
  display: inline-flex;
  min-height: 23px;
  align-items: center;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 700;
  white-space: nowrap;
}

.status-pill--pass {
  background: #dcfce7;
  color: #166534;
}

.status-pill--warn {
  background: #fef3c7;
  color: #92400e;
}

.status-pill--insufficient-evidence {
  background: #e2e8f0;
  color: #475569;
}

@media (max-width: 900px) {
  .admin-operations__panels {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 540px) {
  .admin-operations__stats {
    grid-template-columns: 1fr;
  }

  .operation-row {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .operation-row__evidence {
    grid-column: 2;
  }
}
</style>
