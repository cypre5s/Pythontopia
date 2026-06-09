<script setup lang="ts">
import type { ClassroomMemberDto } from '@/api/dto/ClassroomMemberDto';
import PyButton from '@/components/ui/PyButton.vue';

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
            <PyButton
              variant="ghost"
              size="sm"
              :disabled="member.status !== 'ACTIVE'"
              @click="handleRemove(member.studentId)"
            >
              移除
            </PyButton>
          </td>
        </tr>
      </tbody>
    </table>
  </section>
</template>

<style scoped>
.members-tab { display: grid; gap: 12px; }
.empty {
  padding: 16px;
  background: var(--pyt-color-bg-soft);
  border-radius: var(--pyt-radius-lg);
  text-align: center;
  color: var(--pyt-color-text-hint);
}
.member-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--pyt-font-size-sm);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  overflow: hidden;
}
.member-table th,
.member-table td {
  border-bottom: 1px solid var(--pyt-color-border-soft);
  padding: 8px 12px;
  text-align: left;
}
.member-table th {
  background: var(--pyt-color-bg-sunken);
  color: var(--pyt-color-text-hint);
  font-weight: 700;
  font-size: var(--pyt-font-size-xs);
  border-bottom: 2px solid var(--pyt-color-border);
}
.member-table tbody tr:hover { background: var(--pyt-color-warning-bg); }
.member-table tbody tr:last-child td { border-bottom: none; }
</style>
