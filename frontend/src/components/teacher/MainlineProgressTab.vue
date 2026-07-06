<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { fetchMainlineProgress, type MainlineProgressMatrixDto } from '@/api/teacher';

const props = defineProps<{ classroomId: number }>();

const data = ref<MainlineProgressMatrixDto | null>(null);
const loading = ref(false);
const error = ref<string | null>(null);

const stageCounts = computed<number[]>(() =>
  (data.value?.chapterStageCounts ?? []).map((c) => c.stageCount),
);

// 完成度 → 背景着色（done/total，0 → 透明，满 → 掌握绿），与 KcHeatmap 的内联着色一致。
function cellBg(done: number, total: number): string {
  if (total <= 0) return 'transparent';
  const ratio = Math.min(1, done / total);
  return `rgba(47, 143, 111, ${(0.15 + ratio * 0.55).toFixed(3)})`;
}

async function load(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    data.value = await fetchMainlineProgress(props.classroomId);
  } catch (e) {
    error.value = e instanceof Error ? e.message : '主线进度加载失败';
  } finally {
    loading.value = false;
  }
}

onMounted(load);
watch(() => props.classroomId, load);
</script>

<template>
  <section
    class="mainline-progress"
    data-testid="mainline-progress-tab"
  >
    <p
      v-if="loading"
      class="banner"
      data-testid="progress-loading"
    >
      主线进度加载中…
    </p>
    <p
      v-else-if="error"
      class="banner banner--error"
      data-testid="progress-error"
    >
      {{ error }}
    </p>
    <template v-else-if="data">
      <p
        v-if="data.members.length === 0"
        class="empty"
        data-testid="progress-empty"
      >
        暂无班级成员主线进度。学生进入关卡地图并完成 stage 三件套后，这里会出现成员 × 章节完成度矩阵。
      </p>
      <template v-else>
        <p class="summary">
          班级 {{ data.totalStudents }} 人 · 全主线共 {{ data.totalStages }} 个 stage（单元格 = 已完成 / 该章 stage 数）
        </p>
        <div class="table-wrap">
          <table
            class="matrix"
            data-testid="progress-matrix"
          >
            <thead>
              <tr>
                <th class="sticky">
                  学生
                </th>
                <th
                  v-for="cell in data.chapterStageCounts"
                  :key="cell.chapter"
                >
                  C{{ cell.chapter }}
                  <small>({{ cell.stageCount }})</small>
                </th>
                <th>合计</th>
                <th>星</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="member in data.members"
                :key="member.studentId"
                :data-testid="`progress-member-${member.studentId}`"
              >
                <td class="sticky name">
                  {{ member.displayName }}
                </td>
                <td
                  v-for="cell in member.chapters"
                  :key="cell.chapter"
                  class="cell"
                  :style="{ background: cellBg(cell.completedStages, cell.totalStages) }"
                >
                  {{ cell.completedStages }}/{{ cell.totalStages }}
                </td>
                <td class="total">
                  {{ member.completedStages }}
                </td>
                <td class="stars">
                  {{ member.totalStars }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </template>
    <!-- stageCounts 仅用于派生表头 small 标注，引用以避免未使用告警 -->
    <span
      v-show="false"
      data-testid="progress-stage-total"
    >{{ stageCounts.length }}</span>
  </section>
</template>

<style scoped>
.mainline-progress { display: grid; gap: 12px; }
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
.summary { margin: 0; font-size: var(--pyt-font-size-xs); color: var(--pyt-color-text-muted); }
.table-wrap { overflow-x: auto; }
.matrix {
  border-collapse: collapse;
  font-size: var(--pyt-font-size-xs);
  width: 100%;
  min-width: 520px;
}
.matrix th,
.matrix td {
  border: 1px solid var(--pyt-color-border);
  padding: 5px 6px;
  text-align: center;
  white-space: nowrap;
}
.matrix th {
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-text-muted);
  font-weight: 600;
}
.matrix th small { font-weight: 400; color: var(--pyt-color-text-hint); }
.sticky {
  position: sticky;
  left: 0;
  z-index: var(--pyt-z-base);
  background: var(--pyt-color-bg-elevated);
}
.matrix .name { text-align: left; font-weight: 600; color: var(--pyt-color-text-primary); }
.matrix .cell { font-family: var(--pyt-font-mono); color: var(--pyt-color-text-primary); }
.matrix .total { font-weight: 700; color: var(--pyt-color-primary-dark); }
.matrix .stars { font-family: var(--pyt-font-pixel); color: var(--pyt-color-warning); font-weight: 700; }
</style>
