<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { fetchWeeklyReport, type WeeklyReport } from '@/api/twin';

const emit = defineEmits<{
  (event: 'prompt', text: string): void;
}>();

const report = ref<WeeklyReport | null>(null);
const busy = ref(true);
const error = ref('');

async function loadReport(): Promise<void> {
  busy.value = true;
  error.value = '';
  try {
    report.value = await fetchWeeklyReport();
  } catch {
    error.value = '暂时无法加载周报';
  } finally {
    busy.value = false;
  }
}

onMounted(() => void loadReport());

const dimLabel: Record<string, string> = {
  error_pattern: '错题复盘建议',
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

function percent(value: number | null): string {
  return value == null ? '暂无可比数据' : `${(value * 100).toFixed(0)}%`;
}

function deltaPercent(value: number | null): string {
  if (value == null) return '暂无可比数据';
  const points = (value * 100).toFixed(0);
  return `${value > 0 ? '+' : ''}${points} 个百分点`;
}

const masterySourceLabels: Record<string, string> = {
  RESOURCE_READ: '阅读',
  MICRO_CHECK_PASS: '小测通过',
  MICRO_CHECK_FAIL: '小测未过',
  PRACTICE_PASS: '练习通过',
  PRACTICE_FAIL: '练习未过',
  DEBUG_FIX: '调试修复',
  MANUAL_OVERRIDE: '教师调整',
  DECAY: '时间衰减',
  BOOTSTRAP: '初始记录',
};

function evidenceSourceLabel(sources: string[]): string {
  return sources.map((source) => masterySourceLabels[source] ?? source).join('、');
}

function promptWeeklyPlan(): void {
  const r = report.value;
  if (!r) return;
  const focus = r.memoryBrief.topKcCodes.length
    ? r.memoryBrief.topKcCodes.slice(0, 3).join('、')
    : '本周薄弱知识点';
  emit(
    'prompt',
    `基于我的 Learner Twin 本周证据，围绕 ${focus} 按“看、学、练、测”安排下一步。先说明依据，再给 3 个可执行任务。`,
  );
}

function promptInsight(insight: { text: string; dimensionKey: string }): void {
  const label = dimLabel[insight.dimensionKey] ?? insight.dimensionKey;
  emit(
    'prompt',
    `结合 Learner Twin 的学习建议「${insight.text}」（${label}），请在课件证据范围内设计一个针对性学习任务，并说明如何验证效果。`,
  );
}

function promptMastery(delta: {
  kcCode: string;
  currentScore: number | null;
  confidence: number | null;
  weeklyDelta: number | null;
  evidenceCount: number;
  evidenceSources: string[];
}): void {
  emit(
    'prompt',
    `针对 ${delta.kcCode}，我当前掌握约 ${percent(delta.currentScore)}，当前置信指标 ${percent(delta.confidence)}（可能受练习失败和遗忘衰减影响，不是答题概率）；本周证据贡献 ${deltaPercent(delta.weeklyDelta)}，有 ${delta.evidenceCount} 条可信学习证据（${evidenceSourceLabel(delta.evidenceSources)}）。请给我一组由浅入深的练习和课件复习建议。`,
  );
}
</script>

<template>
  <div
    class="tw"
    :class="{ empty: !report && !busy }"
  >
    <div
      v-if="busy"
      class="tw__load"
    >
      ...
    </div>
    <div
      v-else-if="error"
      class="tw__err"
    >
      {{ error }}
      <button
        type="button"
        class="tw__action"
        data-testid="twin-weekly-retry"
        @click="loadReport"
      >
        重试
      </button>
    </div>
    <div
      v-else-if="!report"
      class="tw__nil"
    >
      本周暂无数据
    </div>
    <template v-else>
      <div class="tw__hd">
        <div>
          <span class="tw__ttl">Learner Twin</span>
          <span
            class="tw__rng"
            data-testid="twin-weekly-period"
          >北京时间统计日期：{{ report.periodStart }} 至 {{ report.periodEnd }}</span>
        </div>
        <button
          type="button"
          class="tw__action"
          data-testid="twin-weekly-plan"
          @click="promptWeeklyPlan"
        >
          生成下一步
        </button>
      </div>
      <div class="tw__card">
        <span class="tw__ic">◇</span>
        <span class="tw__tx">
          {{ report.memoryBrief.totalEvents }} 事件 ·
          <span class="ok">{{ report.memoryBrief.succeededPractices }}</span> 通过 ·
          <span class="ng">{{ report.memoryBrief.failedPractices }}</span> 未过
        </span>
      </div>
      <ul
        v-if="report.memoryBrief.highlights.length > 0"
        class="tw__highlights"
        data-testid="twin-weekly-highlights"
        aria-label="近期未通过练习摘要"
      >
        <li
          v-for="(highlight, index) in report.memoryBrief.highlights"
          :key="`${index}-${highlight}`"
        >
          {{ highlight }}
        </li>
      </ul>
      <div
        v-for="i in report.insights"
        :key="i.id"
        class="tw__card"
      >
        <span class="tw__ic">!</span>
        <span class="tw__tx">
          <span class="tw__badge">建议 · {{ dimLabel[i.dimensionKey] ?? i.dimensionKey }}</span>
          {{ i.text }}
        </span>
        <button
          type="button"
          class="tw__mini"
          :data-testid="`twin-insight-prompt-${i.id}`"
          @click="promptInsight(i)"
        >
          带入
        </button>
      </div>
      <div
        v-for="d in report.masteryDeltas"
        :key="'m-' + d.kcCode"
        class="tw__card"
      >
        <span class="tw__ic">↑</span>
        <span class="tw__tx">
          <span class="tw__badge">{{ d.kcCode }}</span>
          掌握 {{ percent(d.currentScore) }} ·
          <span
            data-testid="twin-confidence-metric"
            title="学习状态启发式指标，会随练习失败和遗忘衰减变化，不代表答题正确概率"
          >置信指标 {{ percent(d.confidence) }}</span>
          · 本周证据贡献 {{ deltaPercent(d.weeklyDelta) }} · 本周 {{ d.evidenceCount }} 条可信证据（{{ evidenceSourceLabel(d.evidenceSources) }}）
        </span>
        <button
          type="button"
          class="tw__mini"
          :data-testid="`twin-mastery-prompt-${d.kcCode}`"
          @click="promptMastery(d)"
        >
          补强
        </button>
      </div>
      <div
        v-for="r in report.resourceRuns"
        :key="r.createdAt + r.agentName"
        class="tw__card"
      >
        <span class="tw__ic">{{ agentIcon(r.agentName) }}</span>
        <span class="tw__tx">
          {{ r.scenario }}
          <span
            class="tw__badge"
            :class="{ ok: r.status === 'SUCCESS' }"
          >
            {{ r.status === 'SUCCESS' ? '✓' : r.status }}
          </span>
        </span>
      </div>
      <div
        v-if="report.resourceRuns.length === 0"
        class="tw__card"
      >
        <span class="tw__ic">□</span>
        <span class="tw__tx">本周 AI 团队待命中</span>
      </div>
    </template>
  </div>
</template>

<style scoped>
.tw {
  font-family:
    ui-sans-serif,
    system-ui,
    -apple-system,
    BlinkMacSystemFont,
    'PingFang SC',
    'Microsoft YaHei',
    sans-serif;
  font-size: 12px;
  color: #252a2e;
  background: #ffffff;
  border: 1px solid #dde3dc;
  border-radius: 8px;
  padding: 12px;
  max-width: 420px;
}
.tw__hd {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e4e9e3;
}
.tw__highlights {
  margin: -4px 0 8px 22px;
  padding-left: 16px;
  color: #7a3f35;
  line-height: 1.5;
}
.tw__ttl {
  display: block;
  font-weight: 700;
  color: #1f2d25;
}
.tw__rng {
  display: block;
  margin-top: 2px;
  font-size: 10px;
  color: #77827b;
}
.tw__card {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid #edf0ec;
}
.tw__card:last-child { border-bottom: none; }
.tw__ic {
  flex: 0 0 18px;
  display: inline-flex;
  justify-content: center;
  font-size: 13px;
  line-height: 1.4;
  color: #496758;
}
.tw__tx {
  flex: 1 1 auto;
  min-width: 0;
  line-height: 1.6;
}
.tw__badge {
  display: inline-block;
  padding: 0 5px;
  font-size: 10px;
  border-radius: 999px;
  background: #eef4fb;
  color: #31536b;
  margin-right: 4px;
}
.tw__badge.ok { background: #e8f4e8; color: #2d6a42; }
.tw__action,
.tw__mini {
  flex: 0 0 auto;
  font: inherit;
  border: 1px solid #cdd8d0;
  background: #f7faf7;
  color: #2c4737;
  border-radius: 8px;
  cursor: pointer;
}
.tw__action {
  padding: 6px 9px;
  font-weight: 650;
}
.tw__mini {
  padding: 4px 7px;
  font-size: 11px;
}
.tw__action:hover,
.tw__mini:hover {
  background: #edf5ef;
  border-color: #9fbaa6;
}
.ok { color: #2d6a42; } .ng { color: #9b2d20; }
.tw__load,.tw__err,.tw__nil { text-align: center; padding: 16px; color: #77827b; }
.tw__err { color: #9b2d20; }
</style>
