<script setup lang="ts">
import { computed } from 'vue';
import { useWorkshopActivityStore } from '@stores/workshopActivity';
import ToolCallTimeline from '@/components/workshop/ToolCallTimeline.vue';

const activity = useWorkshopActivityStore();

const statusLabel = computed<string>(() => {
  if (activity.status === 'streaming') return '正在处理';
  if (activity.status === 'done') return '处理完成';
  if (activity.status === 'error') return '处理未完成';
  return '还没有处理记录';
});

const statusHint = computed<string>(() => {
  if (activity.status === 'streaming') return '可以继续留在对话中，结果会自动出现。';
  if (activity.status === 'error') return '请回到对话重试，或换一种问法。';
  if (activity.status === 'idle') return '提问后会显示处理步骤和课件命中情况。';
  return '';
});
</script>

<template>
  <section
    class="process"
    :data-status="activity.status"
    data-testid="workshop-process"
  >
    <header class="process__head">
      <span
        class="process__dot"
        aria-hidden="true"
      />
      <span class="process__copy">
        <strong>{{ statusLabel }}</strong>
        <span v-if="activity.label">{{ activity.label }}</span>
      </span>
    </header>

    <ToolCallTimeline
      v-if="activity.traces.length > 0"
      :traces="activity.traces"
      :live="activity.status === 'streaming'"
    />

    <p
      v-if="statusHint"
      class="process__hint"
      :class="{ 'process__hint--error': activity.status === 'error' }"
      data-testid="workshop-process-hint"
    >
      {{ statusHint }}
    </p>
  </section>
</template>

<style scoped>
.process {
  display: grid;
  gap: 12px;
  padding: 12px 4px;
  color: var(--pyt-color-text-primary);
}
.process__head {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(159, 116, 43, 0.24);
}
.process__dot {
  flex: 0 0 auto;
  width: 9px;
  height: 9px;
  margin-top: 5px;
  border-radius: 50%;
  background: #9b6b20;
}
.process[data-status='streaming'] .process__dot {
  box-shadow: 0 0 0 4px rgba(155, 107, 32, 0.16);
}
.process[data-status='done'] .process__dot {
  background: #55713b;
}
.process[data-status='error'] .process__dot {
  background: #a13d31;
}
.process__copy {
  display: grid;
  gap: 2px;
  min-width: 0;
}
.process__copy strong {
  font-size: 13px;
  color: #493416;
}
.process__copy span,
.process__hint {
  font-size: 11px;
  line-height: 1.55;
  color: #796748;
}
.process__hint {
  margin: 0;
}
.process__hint--error {
  color: #8d3028;
}
</style>
