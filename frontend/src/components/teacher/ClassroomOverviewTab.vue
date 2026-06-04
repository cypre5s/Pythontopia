<script setup lang="ts">
import { computed } from 'vue';
import type { ClassroomDto } from '@/api/dto/ClassroomDto';
import type {
  KcHeatmapEntryDto,
  QuestCompletionDto,
  RecentActivityDto,
} from '@/api/dto/TeacherDashboard';

const props = defineProps<{
  classroom: ClassroomDto | null;
  membersCount: number;
  questSummary: QuestCompletionDto[];
  kcHeatmap: KcHeatmapEntryDto[];
  recentActivity: RecentActivityDto[];
}>();

const weeklySubmissionGuess = computed<number>(() => {
  return props.recentActivity.filter((entry) => entry.eventKind === 'SUBMISSION').length;
});

const completionRate = computed<number>(() => {
  if (props.questSummary.length === 0) return 0;
  const sum = props.questSummary.reduce((acc, cur) => acc + cur.completionRate, 0);
  return sum / props.questSummary.length;
});

const masteryAvg = computed<number>(() => {
  if (props.kcHeatmap.length === 0) return 0;
  const sum = props.kcHeatmap.reduce((acc, cur) => acc + cur.avgMastery, 0);
  return sum / props.kcHeatmap.length;
});

function fmtRate(value: number): string {
  return (value * 100).toFixed(1) + '%';
}
</script>

<template>
  <section
    class="overview"
    data-testid="overview-tab"
  >
    <div class="cards">
      <article class="card">
        <h4>成员人数</h4>
        <p class="value">
          {{ membersCount }}
        </p>
        <small>仅统计 ACTIVE 状态。</small>
      </article>
      <article class="card">
        <h4>近期事件数</h4>
        <p class="value">
          {{ recentActivity.length }}
        </p>
        <small>含 KC 掌握、提交、资源 3 类事件。</small>
      </article>
      <article class="card">
        <h4>本周提交</h4>
        <p class="value">
          {{ weeklySubmissionGuess }}
        </p>
        <small>来自最近活跃事件流（粗估）。</small>
      </article>
      <article class="card">
        <h4>班级任务平均完成率</h4>
        <p class="value">
          {{ fmtRate(completionRate) }}
        </p>
        <small>{{ questSummary.length }} 个任务的算术平均。</small>
      </article>
      <article class="card">
        <h4>KC 平均掌握度</h4>
        <p class="value">
          {{ masteryAvg.toFixed(2) }}
        </p>
        <small>覆盖全部 {{ kcHeatmap.length }} 个 KC。</small>
      </article>
      <article class="card">
        <h4>加入码</h4>
        <p class="value mono">
          {{ classroom?.joinCode ?? '——' }}
        </p>
        <small>分享给学生加入；可在右上角重置。</small>
      </article>
    </div>

    <h3>最近活跃事件</h3>
    <ul class="activity">
      <li
        v-for="(event, idx) in recentActivity.slice(0, 10)"
        :key="event.studentId + '_' + event.occurredAt + '_' + idx"
      >
        <span class="time">{{ event.occurredAt.slice(0, 19).replace('T', ' ') }}</span>
        <span class="student">{{ event.displayName }}</span>
        <span class="kind">{{ event.eventKind }}</span>
        <span
          v-if="event.kcCode"
          class="kc"
        >{{ event.kcCode }}</span>
        <span
          v-if="event.detail"
          class="detail"
        >{{ event.detail }}</span>
      </li>
    </ul>
    <p
      v-if="recentActivity.length === 0"
      class="empty"
    >
      暂无活跃事件。学生在小镇里执行动作后这里会出现记录。
    </p>
  </section>
</template>

<style scoped>
.overview {
  display: grid;
  gap: 16px;
}
.cards {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
}
.card {
  background: #fffbe9;
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  padding: 12px;
}
.card h4 {
  margin: 0 0 6px;
  font-size: 13px;
  color: var(--pyt-color-text-primary);
}
.card .value {
  margin: 0 0 4px;
  font-size: 22px;
  color: #2f5b8a;
  font-weight: 700;
}
.card .value.mono {
  font-family: 'JetBrains Mono', 'Menlo', monospace;
  font-size: 18px;
}
.card small {
  color: var(--pyt-color-text-hint);
  font-size: 11px;
}
.activity {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 6px;
}
.activity li {
  display: grid;
  grid-template-columns: 150px 100px 100px 110px 1fr;
  gap: 8px;
  padding: 6px 10px;
  border-bottom: 1px dashed var(--pyt-color-border);
  font-size: 12px;
}
.activity .time {
  color: var(--pyt-color-text-hint);
}
.activity .student {
  font-weight: 600;
}
.activity .kind {
  color: #2f5b8a;
}
.activity .kc {
  font-family: 'JetBrains Mono', monospace;
}
.activity .detail {
  color: var(--pyt-color-text-primary);
}
.empty {
  padding: 16px;
  background: var(--pyt-color-bg-soft);
  border-radius: var(--pyt-radius-lg);
  text-align: center;
}
</style>
