<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import PyButton from '@/components/ui/PyButton.vue';
import { useTeacherStore } from '@stores/teacher';
import { useSessionStore } from '@stores/session';
import { useToastStore } from '@stores/toast';

const router = useRouter();
const teacher = useTeacherStore();
const session = useSessionStore();
const toast = useToastStore();

const formOpen = ref(false);
const newName = ref('');
const newDescription = ref('');
const creating = ref(false);

onMounted(async () => {
  await teacher.loadClassrooms();
});

function open(classroomId: number) {
  router.push({ name: 'teacher-classroom', params: { id: classroomId } });
}

async function createClassroom() {
  if (!newName.value.trim()) {
    toast.show('请填写班级名称', 'error');
    return;
  }
  creating.value = true;
  try {
    const created = await teacher.createClassroomEntry({
      name: newName.value.trim(),
      description: newDescription.value.trim(),
    });
    toast.show(`班级 ${created.name} 已创建，加入码 ${created.joinCode}`, 'success');
    newName.value = '';
    newDescription.value = '';
    formOpen.value = false;
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '创建失败', 'error');
  } finally {
    creating.value = false;
  }
}
</script>

<template>
  <AppLayout>
    <section
      class="teacher-home"
      data-testid="teacher-home"
    >
      <header>
        <h1>教师屋</h1>
        <p>这里是 {{ session.displayName }} 的班级总览。</p>
      </header>

      <div class="actions">
        <PyButton
          variant="primary"
          test-id="teacher-new-classroom"
          @click="formOpen = !formOpen"
        >
          {{ formOpen ? '收起表单' : '+ 新建班级' }}
        </PyButton>
      </div>

      <form
        v-if="formOpen"
        class="create-form"
        data-testid="teacher-create-form"
        @submit.prevent="createClassroom"
      >
        <label>
          班级名称
          <input
            v-model="newName"
            maxlength="64"
            placeholder="例如 2026 春 Python 一班"
            required
          >
        </label>
        <label>
          班级说明
          <textarea
            v-model="newDescription"
            maxlength="256"
            placeholder="简要说明用途、面向学员、上课频率"
          />
        </label>
        <PyButton
          type="submit"
          variant="primary"
          :disabled="creating"
        >
          {{ creating ? '创建中...' : '创建班级' }}
        </PyButton>
      </form>

      <p
        v-if="teacher.loading"
        class="empty"
      >
        正在加载班级列表...
      </p>
      <p
        v-else-if="!teacher.hasClassrooms"
        class="empty"
      >
        你还没有创建任何班级，点击"新建班级"开始。
      </p>
      <ul
        v-else
        class="classroom-list"
        data-testid="classroom-list"
      >
        <li
          v-for="classroom in teacher.classrooms"
          :key="classroom.classroomId"
          class="classroom-card"
        >
          <header>
            <h3>{{ classroom.name }}</h3>
            <span class="badge">{{ classroom.status }}</span>
          </header>
          <p class="desc">
            {{ classroom.description || '（暂无说明）' }}
          </p>
          <footer>
            <span class="code">加入码：<code>{{ classroom.joinCode ?? '——' }}</code></span>
            <PyButton
              variant="secondary"
              size="sm"
              @click="open(classroom.classroomId)"
            >
              进入看板
            </PyButton>
          </footer>
        </li>
      </ul>
    </section>
  </AppLayout>
</template>

<style scoped>
.teacher-home {
  padding: 24px;
  display: grid;
  gap: 18px;
  color: var(--pyt-color-text-primary);
  max-width: 1100px;
}
header h1 {
  margin: 0 0 4px;
  font-size: 22px;
  font-family: var(--pyt-font-pixel);
  color: var(--pyt-color-text-primary);
}
header p {
  margin: 0;
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-muted);
}
.actions {
  display: flex;
  justify-content: flex-end;
}
.create-form {
  display: grid;
  gap: 12px;
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  padding: 16px;
}
.create-form label {
  display: grid;
  gap: 4px;
  font-size: var(--pyt-font-size-sm);
  font-weight: 600;
  color: var(--pyt-color-text-primary);
}
.create-form input,
.create-form textarea {
  width: 100%;
  padding: 8px 10px;
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  background: var(--pyt-color-bg-primary);
  color: var(--pyt-color-text-primary);
  font-family: inherit;
  font-size: var(--pyt-font-size-base);
}
.create-form input:focus,
.create-form textarea:focus {
  outline: none;
  border-color: var(--pyt-color-gold);
}
.create-form textarea { min-height: 60px; resize: vertical; }
.empty {
  padding: 16px;
  background: var(--pyt-color-bg-soft);
  border-radius: var(--pyt-radius-lg);
  text-align: center;
  color: var(--pyt-color-text-hint);
}
/* ─── shadcn SectionCards-style classroom card grid ─── */
.classroom-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
}
.classroom-card {
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  padding: 16px;
  display: grid;
  gap: 10px;
  box-shadow: var(--pyt-shadow-card);
  transition: border-color var(--pyt-motion-base), box-shadow var(--pyt-motion-base);
}
.classroom-card:hover {
  border-color: var(--pyt-color-gold);
  box-shadow: 0 4px 0 var(--pyt-color-border);
}
.classroom-card header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}
.classroom-card h3 {
  margin: 0;
  font-size: var(--pyt-font-size-lg);
  font-family: var(--pyt-font-pixel);
  color: var(--pyt-color-text-primary);
  line-height: 1.2;
}
.badge {
  background: var(--pyt-color-info-bg);
  color: var(--pyt-color-info);
  border: 1px solid var(--pyt-color-info);
  border-radius: var(--pyt-radius-full);
  padding: 2px 10px;
  font-size: var(--pyt-font-size-xs);
  font-weight: 600;
  white-space: nowrap;
}
.classroom-card .desc {
  margin: 0;
  color: var(--pyt-color-text-muted);
  font-size: var(--pyt-font-size-sm);
  line-height: 1.55;
}
.classroom-card footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  padding-top: 4px;
  border-top: 1px solid var(--pyt-color-border-soft);
}
.code {
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-hint);
}
.code code {
  font-family: var(--pyt-font-mono);
  color: var(--pyt-color-text-primary);
}
</style>
