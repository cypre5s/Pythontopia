<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { http } from '@/api/http';

interface DecisionItem {
  agent_name: string;
  scenario: string;
  status: string;
  evidence_page_ids: number[];
  trace: string;
  ts: string;
}

interface TraceEntry {
  node?: string;
  status?: string;
}

const items = ref<DecisionItem[]>([]);
const busy = ref(true);

onMounted(async () => {
  try { const r = await http.get<DecisionItem[]>('/api/learner/decisions'); items.value = r.data.slice(0, 10); }
  finally { busy.value = false; }
});
function expanded(t: string): string {
  try {
    const parsed: unknown = JSON.parse(t);
    if (!Array.isArray(parsed)) {
      return '';
    }
    return parsed
      .map((entry: TraceEntry) => `${entry.node ?? 'unknown'}:${entry.status ?? 'unknown'}`)
      .join('→');
  }
  catch { return ''; }
}
</script>
<template>
  <div class="gb">
    <div v-if="busy" class="gb__l">...</div>
    <details v-for="(d,i) in items" :key="i" class="gb__i">
      <summary>
        <b>{{ d.agent_name }}</b> {{ d.scenario }}
        <span class="gb__b" :class="{ok:d.status==='SUCCESS'}">{{ d.status }}</span>
      </summary>
      <div class="gb__d">
        <p v-if="d.evidence_page_ids?.length">📄 证据页: {{ d.evidence_page_ids.join(', ') }}</p>
        <p v-else>通识知识</p>
        <p v-if="d.trace" class="gb__t">{{ expanded(d.trace) }}</p>
      </div>
    </details>
  </div>
</template>
<style scoped>
.gb{font-family:'Zpix',monospace;font-size:12px;color:var(--pyt-color-text-primary);max-width:400px;image-rendering:pixelated}
.gb__i{border-bottom:1px solid var(--pyt-color-border-soft);padding:6px 0}
.gb__b{padding:0 4px;font-size:10px;background:var(--pyt-color-info-bg);color:var(--pyt-color-info)}
.gb__b.ok{background:var(--pyt-color-success-bg);color:var(--pyt-color-success)}
.gb__d{padding:4px 0 4px 12px;color:var(--pyt-color-text-muted)}
.gb__t{font-size:10px;word-break:break-all}.gb__l{text-align:center;padding:12px}
</style>
