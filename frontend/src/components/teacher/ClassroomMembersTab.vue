<script setup lang="ts">
import type { ClassroomMemberDto } from '@/api/dto/ClassroomMemberDto';

defineProps<{ members: ClassroomMemberDto[] }>();
const emit = defineEmits<{ (event: 'remove', studentId: number): void }>();

function handleRemove(studentId: number) {
  if (typeof window !== 'undefined') {
    const ok = window.confirm('确认从班级移除该学生？学生的提交、画像不会被删除。');
    if (!ok) return;
  }
  emit('remove', studentId);
}
</script>

<template>
  <section
    class="members-tab"
    data-testid="members-tab"
  >
    <p
      v-if="members.length === 0"
      class="empty"
    >
      暂无成员，分享加入码邀请学生。
    </p>
    <table
      v-else
      class="member-table"
    >
      <thead>
        <tr>
          <th>学号</th>
          <th>用户名</th>
          <th>显示名</th>
          <th>状态</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="member in members"
          :key="member.studentId"
        >
          <td>{{ member.studentId }}</td>
          <td>{{ member.username }}</td>
          <td>{{ member.displayName }}</td>
          <td>{{ member.status }}</td>
          <td>
            <button
              class="danger"
              :disabled="member.status !== 'ACTIVE'"
              @click="handleRemove(member.studentId)"
            >
              移除
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </section>
</template>

<style scoped>
.members-tab {
  display: grid;
  gap: 12px;
}
.empty {
  padding: 16px;
  background: #fdf3d0;
  border-radius: 8px;
  text-align: center;
}
.member-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.member-table th,
.member-table td {
  border: 1px solid #c9a86b;
  padding: 6px 10px;
  text-align: center;
}
.member-table th {
  background: #fdf3d0;
}
.danger {
  background: #f4a261;
  color: #fff7e6;
  border: 2px solid #c97f3a;
  border-radius: 6px;
  padding: 4px 12px;
  cursor: pointer;
}
.danger[disabled] {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
