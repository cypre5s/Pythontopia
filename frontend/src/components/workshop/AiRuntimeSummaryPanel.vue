<script setup lang="ts">
import { onMounted, ref } from 'vue';
import {
  fetchAiRuntimeSummary,
  type AiRuntimeGate,
  type AiRuntimeSummary,
} from '@/api/aiRuntime';

const summary = ref<AiRuntimeSummary | null>(null);
const loading = ref(true);
const error = ref<string | null>(null);

onMounted(async () => {
  try {
    summary.value = await fetchAiRuntimeSummary();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'AI Runtime 摘要暂不可用';
  } finally {
    loading.value = false;
  }
});

function statusLabel(status: AiRuntimeGate['status']): string {
  if (status === 'PASS') return '通过';
  if (status === 'WARN') return '需关注';
  return '证据不足';
}

function formatScore(value: number): string {
  return Number.isInteger(value) ? String(value) : value.toFixed(2);
}
</script>

<template>
  <section
    class="runtime"
    data-testid="runtime-summary"
  >
    <header class="runtime__head">
      <span class="runtime__eyebrow">Runtime / Eval</span>
      <strong>AI 证据驾驶舱</strong>
    </header>

    <p
      v-if="loading"
      class="runtime__muted"
    >
      正在读取协议、证据与记忆状态…
    </p>
    <p
      v-else-if="error"
      class="runtime__error"
    >
      {{ error }}
    </p>

    <template v-else-if="summary">
      <div class="runtime__metrics">
        <span class="metric">
          <b>MCP 工具 {{ summary.protocol.mcpToolCount }}</b>
          <small>{{ summary.protocol.toolsWithEvidenceFieldCount }} 个声明证据字段</small>
        </span>
        <span class="metric">
          <b>能力包 {{ summary.protocol.skillPackageCount }}</b>
          <small>{{ summary.protocol.evidenceSkillPackageCount }} 个绑定证据合同</small>
        </span>
        <span class="metric">
          <b>证据图 {{ formatScore(summary.evidence.graphCompletenessScore) }}</b>
          <small>{{ summary.evidence.nodeCount }} 节点 / {{ summary.evidence.groundedPageCount }} 页</small>
        </span>
        <span class="metric">
          <b>记忆事件 {{ summary.learnerTwin.memoryEventCount }}</b>
          <small>{{ summary.learnerTwin.resourceRunCount }} 次资源运行</small>
        </span>
        <span class="metric">
          <b>外部查证 {{ summary.externalResearch.allowedDomains.length }}</b>
          <small>{{ summary.externalResearch.citationPolicy }}</small>
        </span>
      </div>

      <ul class="runtime__gates">
        <li
          v-for="gate in summary.gates"
          :key="gate.key"
          class="gate"
          :data-status="gate.status"
        >
          <span class="gate__status">{{ statusLabel(gate.status) }}</span>
          <span class="gate__body">
            <b>{{ gate.label }}</b>
            <small>当前 {{ formatScore(gate.value) }} / 阈值 {{ formatScore(gate.threshold) }}</small>
            <em v-if="gate.evidenceRefs.length > 0">{{ gate.evidenceRefs.slice(0, 3).join(' · ') }}</em>
          </span>
        </li>
      </ul>
    </template>
  </section>
</template>

<style scoped>
.runtime {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid #dfe5dd;
  border-radius: 8px;
  background: #ffffff;
  color: #252a2e;
}
.runtime__head {
  display: grid;
  gap: 2px;
}
.runtime__eyebrow {
  font-size: 10px;
  font-weight: 700;
  color: #64706a;
  text-transform: uppercase;
  letter-spacing: 0;
}
.runtime__head strong {
  font-size: 13px;
}
.runtime__muted,
.runtime__error {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
}
.runtime__muted {
  color: #6a736e;
}
.runtime__error {
  color: #9e3528;
}
.runtime__metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}
.metric {
  display: grid;
  gap: 3px;
  min-width: 0;
  padding: 8px;
  border: 1px solid #e2e7e1;
  border-radius: 8px;
  background: #fbfbf8;
}
.metric b {
  font-size: 12px;
  color: #26312b;
}
.metric small {
  font-size: 11px;
  line-height: 1.35;
  color: #69736e;
}
.runtime__gates {
  list-style: none;
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
}
.gate {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 8px;
  align-items: start;
}
.gate__status {
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
.gate[data-status='WARN'] .gate__status {
  border-color: #e0c48f;
  background: #fff7ea;
  color: #6a4b22;
}
.gate[data-status='INSUFFICIENT_EVIDENCE'] .gate__status {
  border-color: #ccd3cb;
  background: #f4f5f2;
  color: #69736e;
}
.gate__body {
  display: grid;
  gap: 2px;
  min-width: 0;
}
.gate__body b {
  font-size: 12px;
  color: #26312b;
}
.gate__body small,
.gate__body em {
  font-size: 11px;
  line-height: 1.35;
  color: #6a736e;
}
.gate__body em {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-style: normal;
}
</style>
