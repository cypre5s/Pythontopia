<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { fetchEndlessActivity, type EndlessActivityDto } from '@/api/teacher';

const props = defineProps<{ classroomId: number }>();

const data = ref<EndlessActivityDto | null>(null);
const loading = ref(false);
const error = ref<string | null>(null);

const dailyMax = computed<number>(() => {
  if (!data.value) return 1;
  return Math.max(1, ...data.value.daily.map((d) => d.recoveryRounds));
});

function barHeightPct(rounds: number): number {
  return (rounds / dailyMax.value) * 100;
}

// accuracyPct 为 null 表示 §3.2 暂无无尽轮次明细表（逐轮正确率未采集）。
const accuracyLabel = computed<string>(() => {
  const pct = data.value?.accuracyPct;
  return pct == null ? '暂未采集' : (pct * 100).toFixed(1) + '%';
});

const chaptersWithActivity = computed(() =>
  (data.value?.chapters ?? []).filter((c) => c.recoveryRounds > 0),
);

async function load(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    data.value = await fetchEndlessActivity(props.classroomId);
  } catch (e) {
    error.value = e instanceof Error ? e.message : '无尽活跃度加载失败';
  } finally {
    loading.value = false;
  }
}

onMounted(load);
watch(() => props.classroomId, load);
</script>

<template>
  <section
    class="endless-activity"
    data-testid="endless-activity-tab"
  >
    <p
      v-if="loading"
      class="banner"
      data-testid="endless-loading"
    >
      无尽活跃度加载中…
    </p>
    <p
      v-else-if="error"
      class="banner banner--error"
      data-testid="endless-error"
    >
      {{ error }}
    </p>
    <template v-else-if="data">
      <div class="cards">
        <article class="card">
          <h4>近 7 日补星数</h4>
          <p class="value">
            {{ data.recoveryStarTotal }}
          </p>
          <small>无尽挑战达标补星（ENDLESS_RECOVERY）。</small>
        </article>
        <article class="card">
          <h4>达标补星轮次</h4>
          <p class="value">
            {{ data.recoveryRoundTotal }}
          </p>
          <small>每条事件 = 一次达标补星轮次（§7）。</small>
        </article>
        <article class="card">
          <h4>活跃人数</h4>
          <p class="value">
            {{ data.activeStudentCount }} / {{ data.totalStudents }}
          </p>
          <small>近 7 日有无尽补星的去重学生数。</small>
        </article>
        <article class="card">
          <h4>平均正确率</h4>
          <p
            class="value"
            data-testid="endless-accuracy"
          >
            {{ accuracyLabel }}
          </p>
          <small>逐轮正确率需无尽轮次明细表（§3.2 待补）。</small>
        </article>
      </div>

      <h3 class="subhead">
        逐日补星轮次（{{ data.periodStart.slice(5) }} ~ {{ data.periodEnd.slice(5) }}）
      </h3>
      <div
        class="daybars"
        data-testid="endless-daybars"
      >
        <div
          v-for="d in data.daily"
          :key="d.day"
          class="daybar"
          :title="`${d.day}：${d.recoveryRounds} 轮 / ${d.recoveryStars} 星`"
        >
          <span class="daybar__track">
            <span
              class="daybar__fill"
              :style="{ height: barHeightPct(d.recoveryRounds) + '%' }"
            />
          </span>
          <span class="daybar__count">{{ d.recoveryRounds }}</span>
          <span class="daybar__label">{{ d.day.slice(5) }}</span>
        </div>
      </div>

      <h3 class="subhead">
        分章补星
      </h3>
      <table
        v-if="chaptersWithActivity.length > 0"
        class="chapters"
        data-testid="endless-chapters"
      >
        <thead>
          <tr>
            <th>章节</th>
            <th>补星轮次</th>
            <th>补星数</th>
            <th>参与人数</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="c in chaptersWithActivity"
            :key="c.chapter"
          >
            <td>第 {{ c.chapter }} 章</td>
            <td>{{ c.recoveryRounds }}</td>
            <td>{{ c.recoveryStars }}</td>
            <td>{{ c.studentCount }}</td>
          </tr>
        </tbody>
      </table>
      <p
        v-else
        class="empty"
        data-testid="endless-empty"
      >
        近 7 日暂无无尽补星活动。学生在章节聚焦远征中，本章正式判题答对 ≥ 8 题且正确率 ≥ 80% 可申请补星。
      </p>
    </template>
  </section>
</template>

<style scoped>
.endless-activity { display: grid; gap: 14px; }
.banner {
  padding: 10px 14px;
  background: var(--pyt-color-bg-soft);
  border-radius: var(--pyt-radius-md);
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-muted);
  margin: 0;
}
.banner--error { background: var(--pyt-color-danger-bg); color: var(--pyt-color-danger); }
.empty {
  padding: 16px;
  background: var(--pyt-color-bg-soft);
  border-radius: var(--pyt-radius-lg);
  text-align: center;
  color: var(--pyt-color-text-hint);
}
.cards {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
}
.card {
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  padding: 12px;
  box-shadow: var(--pyt-shadow-card);
}
.card h4 {
  margin: 0 0 6px;
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-muted);
  font-weight: 600;
}
.card .value {
  margin: 0 0 4px;
  font-size: 22px;
  color: var(--pyt-color-primary-dark);
  font-weight: 700;
  font-family: var(--pyt-font-pixel);
}
.card small { color: var(--pyt-color-text-hint); font-size: var(--pyt-font-size-xs); }
.subhead {
  margin: 4px 0 0;
  font-size: var(--pyt-font-size-sm);
  font-family: var(--pyt-font-pixel);
  color: var(--pyt-color-text-primary);
}
.daybars {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 8px;
  align-items: end;
  padding: 10px;
  background: var(--pyt-color-bg-soft);
  border: 1px solid var(--pyt-color-border-soft);
  border-radius: var(--pyt-radius-md);
}
.daybar { display: grid; gap: 4px; justify-items: center; }
.daybar__track {
  width: 100%;
  height: 80px;
  display: flex;
  align-items: flex-end;
  background: var(--pyt-color-bg-sunken);
  border-radius: var(--pyt-radius-sm);
  overflow: hidden;
}
.daybar__fill {
  width: 100%;
  background: #2a9d8f;
  border-radius: var(--pyt-radius-sm) var(--pyt-radius-sm) 0 0;
  transition: height var(--pyt-motion-slow);
  min-height: 2px;
}
.daybar__count { font-size: var(--pyt-font-size-xs); font-weight: 700; color: var(--pyt-color-text-primary); }
.daybar__label { font-size: 10px; color: var(--pyt-color-text-hint); }
.chapters { width: 100%; border-collapse: collapse; font-size: var(--pyt-font-size-sm); }
.chapters th,
.chapters td {
  border: 1px solid var(--pyt-color-border);
  padding: 5px 8px;
  text-align: center;
}
.chapters th {
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-text-muted);
  font-weight: 600;
}
</style>
