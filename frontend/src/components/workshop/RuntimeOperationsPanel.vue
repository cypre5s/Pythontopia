<script setup lang="ts">
import { onMounted, ref } from 'vue';
import {
  fetchAiRuntimeOperations,
  type AiRuntimeOperations,
  type RuntimeOperationStatus,
} from '@/api/aiRuntime';

const operations = ref<AiRuntimeOperations | null>(null);
const loading = ref(true);
const error = ref<string | null>(null);

onMounted(async () => {
  try {
    operations.value = await fetchAiRuntimeOperations();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Runtime 运维状态暂不可用';
  } finally {
    loading.value = false;
  }
});

function statusLabel(status: RuntimeOperationStatus): string {
  if (status === 'PASS') return '通过';
  if (status === 'WARN') return '需关注';
  return '证据不足';
}
</script>

<template>
  <section
    class="ops"
    data-testid="runtime-operations"
  >
    <header class="ops__head">
      <span class="ops__eyebrow">Loop Ops</span>
      <strong>闭环运维状态</strong>
    </header>

    <p
      v-if="loading"
      class="ops__muted"
    >
      正在读取闭环状态…
    </p>
    <p
      v-else-if="error"
      class="ops__error"
    >
      {{ error }}
    </p>

    <template v-else-if="operations">
      <p
        class="ops__subject"
        data-testid="runtime-operations-subject"
      >
        {{ operations.subject.label }} · {{ operations.subject.evidenceLearnerCount }} /
        {{ operations.subject.learnerCount }} 名学习者已有证据
      </p>

      <ul class="ops__loops">
        <li
          v-for="loop in operations.loops"
          :key="loop.loopType"
          class="ops-row"
          :data-status="loop.status"
        >
          <span class="ops-row__status">{{ statusLabel(loop.status) }}</span>
          <span class="ops-row__body">
            <b>{{ loop.label }}</b>
            <small>{{ loop.nextAction }}</small>
            <em v-if="loop.evidenceRefs.length > 0">{{ loop.evidenceRefs.slice(0, 2).join(' · ') }}</em>
          </span>
        </li>
      </ul>

      <ul class="ops__governance">
        <li
          v-for="item in operations.governance"
          :key="item.key"
          class="gov-row"
          :data-status="item.status"
        >
          <span class="gov-row__status">{{ statusLabel(item.status) }}</span>
          <span class="gov-row__body">
            <b>{{ item.label }}</b>
            <small>{{ item.action }}</small>
            <em v-if="item.evidenceRefs.length > 0">{{ item.evidenceRefs.slice(0, 2).join(' · ') }}</em>
          </span>
        </li>
      </ul>
    </template>
  </section>
</template>

<style scoped>
.ops {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid #dfe5dd;
  border-radius: 8px;
  background: #ffffff;
  color: #252a2e;
}
.ops__head {
  display: grid;
  gap: 2px;
}
.ops__eyebrow {
  font-size: 10px;
  font-weight: 700;
  color: #64706a;
  text-transform: uppercase;
  letter-spacing: 0;
}
.ops__head strong {
  font-size: 13px;
}
.ops__muted,
.ops__error {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
}
.ops__muted {
  color: #6a736e;
}
.ops__error {
  color: #9e3528;
}
.ops__subject {
  margin: 0;
  padding: 6px 8px;
  border-left: 3px solid #6f8d78;
  background: #f4f7f3;
  color: #4a5b51;
  font-size: 11px;
  line-height: 1.5;
}
.ops__loops,
.ops__governance {
  list-style: none;
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
}
.ops__governance {
  padding-top: 8px;
  border-top: 1px solid #e6ebe5;
}
.ops-row,
.gov-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 8px;
  align-items: start;
}
.ops-row__status,
.gov-row__status {
  min-width: 56px;
  padding: 3px 6px;
  border-radius: 8px;
  border: 1px solid #c7d6c8;
  background: #edf5ef;
  color: #36513f;
  font-size: 11px;
  font-weight: 700;
  text-align: center;
}
.ops-row[data-status='WARN'] .ops-row__status,
.gov-row[data-status='WARN'] .gov-row__status {
  border-color: #e0c48f;
  background: #fff7ea;
  color: #6a4b22;
}
.ops-row[data-status='INSUFFICIENT_EVIDENCE'] .ops-row__status,
.gov-row[data-status='INSUFFICIENT_EVIDENCE'] .gov-row__status {
  border-color: #ccd3cb;
  background: #f4f5f2;
  color: #69736e;
}
.ops-row__body,
.gov-row__body {
  display: grid;
  gap: 2px;
  min-width: 0;
}
.ops-row__body b,
.gov-row__body b {
  font-size: 12px;
  color: #26312b;
}
.ops-row__body small,
.ops-row__body em,
.gov-row__body small,
.gov-row__body em {
  overflow: hidden;
  color: #6a736e;
  font-size: 11px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ops-row__body em,
.gov-row__body em {
  font-style: normal;
}
</style>
