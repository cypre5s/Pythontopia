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

// 口径：这是「最近活跃事件流里的提交条数」，而事件流本身是近 7 日 + 最多 20 条的截断样本，
// 不是全班本周提交总数。文案与后端窗口保持一致，避免把一个上限 20 的数字标成「本周提交」。
const recentSubmissionCount = computed<number>(() => {
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
        <h4>近 7 日事件数</h4>
        <p class="value">
          {{ recentActivity.length }}
        </p>
        <small>含 KC 掌握、提交、资源 3 类事件（最多 20 条）。</small>
      </article>
      <article class="card">
        <h4>近 7 日提交</h4>
        <p class="value">
          {{ recentSubmissionCount }}
        </p>
        <small>取自近 7 日活跃事件流样本，非全量统计。</small>
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
.overview { display: grid; gap: 20px; }

/* SectionCards-style stat row */
.cards {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
}
.card {
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  padding: 14px 12px;
  box-shadow: var(--pyt-shadow-card);
}
.card h4 {
  margin: 0 0 8px;
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-muted);
  font-weight: 600;
}
.card .value {
  margin: 0 0 4px;
  font-size: 24px;
  color: var(--pyt-color-primary-dark);
  font-weight: 700;
  font-family: var(--pyt-font-pixel);
}
.card .value.mono {
  font-family: var(--pyt-font-mono);
  font-size: 18px;
  letter-spacing: 1px;
}
.card small { color: var(--pyt-color-text-hint); font-size: var(--pyt-font-size-xs); }

/* Activity table */
.activity {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 0;
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  overflow: hidden;
}
.activity li {
  display: grid;
  grid-template-columns: 140px 90px 90px 100px 1fr;
  gap: 8px;
  padding: 7px 12px;
  border-bottom: 1px solid var(--pyt-color-border-soft);
  font-size: var(--pyt-font-size-xs);
  background: var(--pyt-color-bg-elevated);
}
.activity li:last-child { border-bottom: none; }
.activity li:nth-child(even) { background: var(--pyt-color-bg-soft); }
.activity .time { color: var(--pyt-color-text-hint); }
.activity .student { font-weight: 600; color: var(--pyt-color-text-primary); }
.activity .kind { color: var(--pyt-color-info); font-weight: 600; }
.activity .kc { font-family: var(--pyt-font-mono); color: var(--pyt-color-text-hint); }
.activity .detail { color: var(--pyt-color-text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.empty {
  padding: 16px;
  background: var(--pyt-color-bg-soft);
  border-radius: var(--pyt-radius-lg);
  text-align: center;
  color: var(--pyt-color-text-hint);
}
</style>
