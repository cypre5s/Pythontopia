<script setup lang="ts">
import type { ErrorHotspotDto } from '@/api/dto/TeacherDashboard';

defineProps<{ hotspots: ErrorHotspotDto[] }>();
</script>

<template>
  <section
    class="hotspots-tab"
    data-testid="hotspots-tab"
  >
    <p
      v-if="hotspots.length === 0"
      class="empty"
    >
      过去 7 天班级没有失败提交，恭喜！
    </p>
    <table
      v-else
      class="hotspot-table"
    >
      <thead>
        <tr>
          <th>KC</th>
          <th>错误次数</th>
          <th>影响学生</th>
          <th>错误类别分布</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="entry in hotspots"
          :key="entry.kcCode"
        >
          <td>{{ entry.kcCode }} <small>{{ entry.kcName ?? '' }}</small></td>
          <td>{{ entry.failedSubmissionCount }}</td>
          <td>{{ entry.affectedStudentCount }}</td>
          <td>
            <span
              v-for="category in entry.categories"
              :key="category.category"
              class="chip"
            >
              {{ category.category }} × {{ category.count }}
            </span>
          </td>
        </tr>
      </tbody>
    </table>
  </section>
</template>

<style scoped>
.hotspots-tab {
  display: grid;
  gap: 12px;
}
.empty {
  padding: 16px;
  background: #fdf3d0;
  border-radius: 8px;
  text-align: center;
}
.hotspot-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.hotspot-table th,
.hotspot-table td {
  border: 1px solid #c9a86b;
  padding: 6px 10px;
  text-align: center;
}
.chip {
  display: inline-block;
  background: #e9c46a;
  color: #3b2a13;
  border-radius: 999px;
  padding: 1px 8px;
  margin: 2px;
  font-size: 11px;
}
</style>
