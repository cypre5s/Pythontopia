<script setup lang="ts">
import { computed } from 'vue';
import type { ClassroomMemberDto } from '@/api/dto/ClassroomMemberDto';
import PyButton from '@/components/ui/PyButton.vue';
import PyTable from '@/components/ui/PyTable.vue';

const props = defineProps<{ members: ClassroomMemberDto[] }>();
const emit = defineEmits<{ (event: 'remove', studentId: number): void }>();

const columns = [
  { key: 'studentId', label: '学号' },
  { key: 'username', label: '用户名' },
  { key: 'displayName', label: '显示名' },
  { key: 'status', label: '状态' },
  { key: 'action', label: '操作', align: 'right' as const },
];

// PyTable rows 接受 Record<string, unknown>[]；props 异步刷新后需要保持响应式。
const rows = computed<Record<string, unknown>[]>(
  () => props.members as unknown as Record<string, unknown>[],
);

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
    <div
      v-else
      class="members-table-scroll"
      role="region"
      aria-label="班级成员列表，可横向滚动"
      tabindex="0"
    >
      <PyTable
        :columns="columns"
        :rows="rows"
        row-key="studentId"
      >
        <template #status="{ value }">
          <span
            class="member-status"
            :class="value === 'ACTIVE' ? 'member-status--active' : 'member-status--removed'"
          >
            {{ value === 'ACTIVE' ? '在班' : '已移除' }}
          </span>
        </template>
        <template #action="{ row }">
          <PyButton
            variant="ghost"
            size="sm"
            :disabled="row.status !== 'ACTIVE'"
            @click="handleRemove(row.studentId as number)"
          >
            移除
          </PyButton>
        </template>
      </PyTable>
    </div>
  </section>
</template>

<style scoped>
.members-tab { display: grid; gap: 12px; }
.members-table-scroll {
  min-width: 0;
  max-width: 100%;
  overflow-x: auto;
  border-radius: var(--pyt-radius-md);
}
.members-table-scroll:focus-visible {
  outline: 2px solid var(--pyt-color-gold);
  outline-offset: 2px;
}
.members-table-scroll :deep(.py-table) { min-width: 720px; }
.empty {
  padding: 16px;
  background: var(--pyt-color-bg-soft);
  border-radius: var(--pyt-radius-lg);
  text-align: center;
  color: var(--pyt-color-text-hint);
}
.member-status {
  display: inline-flex;
  align-items: center;
  padding: 1px 10px;
  border-radius: var(--pyt-radius-full);
  font-size: var(--pyt-font-size-xs);
  font-weight: 600;
  border: 1px solid var(--pyt-color-border);
}
.member-status--active {
  background: var(--pyt-color-success-bg);
  color: var(--pyt-color-success);
  border-color: var(--pyt-color-success);
}
.member-status--removed {
  background: var(--pyt-color-bg-sunken);
  color: var(--pyt-color-text-muted);
}
</style>
