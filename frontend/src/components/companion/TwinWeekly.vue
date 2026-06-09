<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { fetchWeeklyReport, type WeeklyReport } from '@/api/twin';

const report = ref<WeeklyReport | null>(null);
const busy = ref(true);
const error = ref('');

onMounted(async () => {
  try {
    report.value = await fetchWeeklyReport();
  } catch {
    error.value = '暂时无法加载周报';
  } finally {
    busy.value = false;
  }
});

const dimLabel: Record<string, string> = {
  error_pattern: '易错', cognitive_style: '风格',
  emotion_persistence: '坚持', practical_ability: '实践', knowledge_base: '基础',
};
const agentIcons: Record<string, string> = {
  handout: '\u{1F4D6}', practice: '\u{270F}\u{FE0F}', reading: '\u{1F4D5}',
  tutor: '\u{1F9D1}\u{200D}\u{1F3EB}', mindmap: '\u{1F9E0}',
  codelab: '\u{2328}\u{FE0F}', storyboard: '\u{1F3AC}', reflection: '\u{1F4AD}',
};
function agentIcon(n: string): string {
  const lo = n.toLowerCase();
  for (const [k, v] of Object.entries(agentIcons)) { if (lo.includes(k)) return v; }
  return '\u{1F916}';
}
</script>

<template>
  <div class="tw" :class="{ empty: !report && !busy }">
    <div v-if="busy" class="tw__load">...</div>
    <div v-else-if="error" class="tw__err">{{ error }}</div>
    <div v-else-if="!report" class="tw__nil">本周暂无数据</div>
    <template v-else>
      <div class="tw__hd">
        <span class="tw__ttl">本周回顾</span>
        <span class="tw__rng">{{ report.periodStart }} → {{ report.periodEnd }}</span>
      </div>
      <div class="tw__card">
        <span class="tw__ic">🧠</span>
        <span class="tw__tx">
          {{ report.memoryBrief.totalEvents }} 事件 ·
          <span class="ok">{{ report.memoryBrief.succeededPractices }}</span> 通过 ·
          <span class="ng">{{ report.memoryBrief.failedPractices }}</span> 未过
        </span>
      </div>
      <div v-for="i in report.insights" :key="i.id" class="tw__card">
        <span class="tw__ic">💡</span>
        <span class="tw__tx">
          <span class="tw__badge">{{ dimLabel[i.dimensionKey] ?? i.dimensionKey }}</span>
          {{ i.text }}
        </span>
      </div>
      <div v-for="d in report.masteryDeltas" :key="'m-' + d.kcCode" class="tw__card">
        <span class="tw__ic">📈</span>
        <span class="tw__tx">
          <span class="tw__badge">{{ d.kcCode }}</span>
          掌握 {{ (d.currentScore * 100).toFixed(0) }}% · 置信 {{ (d.confidence * 100).toFixed(0) }}%
        </span>
      </div>
      <div v-for="r in report.resourceRuns" :key="r.createdAt + r.agentName" class="tw__card">
        <span class="tw__ic">{{ agentIcon(r.agentName) }}</span>
        <span class="tw__tx">
          {{ r.scenario }}
          <span class="tw__badge" :class="{ ok: r.status === 'SUCCESS' }">
            {{ r.status === 'SUCCESS' ? '✓' : r.status }}
          </span>
        </span>
      </div>
      <div v-if="report.resourceRuns.length === 0" class="tw__card">
        <span class="tw__ic">🤖</span>
        <span class="tw__tx">本周 AI 团队待命中</span>
      </div>
    </template>
  </div>
</template>

<style scoped>
.tw {
  font-family: 'Zpix', monospace; font-size: 12px;
  color: var(--pyt-color-text-primary); background: var(--pyt-color-bg-primary);
  border: 2px solid var(--pyt-color-border); border-radius: 4px;
  padding: 12px; max-width: 360px; image-rendering: pixelated;
}
.tw__hd {
  display: flex; justify-content: space-between; align-items: baseline;
  margin-bottom: 10px; padding-bottom: 6px;
  border-bottom: 2px solid var(--pyt-color-border-soft);
}
.tw__ttl { font-weight: 700; }
.tw__rng { font-size: 10px; color: var(--pyt-color-text-muted); }
.tw__card {
  display: flex; align-items: flex-start; gap: 8px; padding: 6px 0;
  border-bottom: 1px solid var(--pyt-color-border-soft);
}
.tw__card:last-child { border-bottom: none; }
.tw__ic { flex-shrink: 0; font-size: 16px; line-height: 1; }
.tw__tx { flex: 1; line-height: 1.6; }
.tw__badge {
  display: inline-block; padding: 0 4px; font-size: 10px;
  background: var(--pyt-color-info-bg); color: var(--pyt-color-info); margin-right: 4px;
}
.tw__badge.ok { background: var(--pyt-color-success-bg); color: var(--pyt-color-success); }
.ok { color: var(--pyt-color-success); } .ng { color: var(--pyt-color-danger); }
.tw__load,.tw__err,.tw__nil { text-align: center; padding: 16px; color: var(--pyt-color-text-muted); }
.tw__err { color: var(--pyt-color-danger); }
</style>
