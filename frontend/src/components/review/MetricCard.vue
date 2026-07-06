<script setup lang="ts">
import { computed } from 'vue';
import type { EvaluationMetricDto } from '@/api/dto/Evaluation';

const props = defineProps<{ metric: EvaluationMetricDto }>();

const labels: Record<string, string> = {
  kc_mastery_total: 'KC 总掌握',
  kc_mastery_delta_total: 'KC 增量',
  practice_pass_rate: '练习通过率',
  bug_mine_resolve_rate: 'Bug 修复率',
  profile_confidence_delta: '画像置信度',
  resource_consumption_count: '资源消化数',
};

const trend = computed<'up' | 'down' | 'neutral'>(() => {
  if (props.metric.metricKey === 'profile_confidence_delta' || props.metric.metricKey === 'kc_mastery_delta_total') {
    if (props.metric.value > 0) return 'up';
    if (props.metric.value < 0) return 'down';
    return 'neutral';
  }
  if (props.metric.metricKey === 'practice_pass_rate' || props.metric.metricKey === 'bug_mine_resolve_rate') {
    if (props.metric.value >= 0.7) return 'up';
    if (props.metric.value <= 0.3) return 'down';
    return 'neutral';
  }
  return 'neutral';
});

const display = computed(() => {
  const value = props.metric.value;
  if (props.metric.displayUnit === 'ratio') {
    return `${(value * 100).toFixed(1)}%`;
  }
  if (props.metric.displayUnit === 'count') {
    return Number.isInteger(value) ? String(value) : value.toFixed(0);
  }
  return value.toFixed(2);
});

const subline = computed(() => {
  const { numerator, denominator, displayUnit } = props.metric;
  if (displayUnit === 'ratio') {
    return `${numerator.toFixed(0)} / ${denominator.toFixed(0) || '0'}`;
  }
  if (displayUnit === 'score' && props.metric.metricKey === 'kc_mastery_delta_total') {
    return props.metric.value >= 0 ? `较上次 +${props.metric.value.toFixed(2)}` : `较上次 ${props.metric.value.toFixed(2)}`;
  }
  return props.metric.notes ?? '';
});
</script>

<template>
  <article
    :class="['metric-card', `trend-${trend}`]"
    :data-metric-key="metric.metricKey"
  >
    <header class="header">
      <span class="label">{{ labels[metric.metricKey] ?? metric.metricKey }}</span>
      <span
        class="trend"
        aria-hidden="true"
      >
        {{ trend === 'up' ? '↑' : trend === 'down' ? '↓' : '·' }}
      </span>
    </header>
    <div class="value">
      {{ display }}
    </div>
    <div
      v-if="subline"
      class="subline"
    >
      {{ subline }}
    </div>
  </article>
</template>

<style scoped>
.metric-card {
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  padding: 10px 12px;
  min-width: 140px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.metric-card.trend-up {
  border-color: var(--pyt-color-success);
}
.metric-card.trend-down {
  border-color: var(--pyt-color-danger);
}
.header {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  font-weight: 600;
  color: var(--pyt-color-text-hint);
}
.value {
  font-size: 22px;
  font-weight: 700;
  color: var(--pyt-color-text-primary);
}
.subline {
  font-size: 11px;
  color: var(--pyt-color-text-muted);
}
.trend {
  font-size: 16px;
}
.trend-up .trend {
  color: var(--pyt-color-success);
}
.trend-down .trend {
  color: var(--pyt-color-danger);
}
</style>
