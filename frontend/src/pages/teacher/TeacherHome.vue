<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
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
        <button
          class="primary"
          @click="formOpen = !formOpen"
        >
          {{ formOpen ? '收起表单' : '+ 新建班级' }}
        </button>
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
        <button
          type="submit"
          class="primary"
          :disabled="creating"
        >
          {{ creating ? '创建中...' : '创建班级' }}
        </button>
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
            <button
              class="secondary"
              @click="open(classroom.classroomId)"
            >
              进入看板
            </button>
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
  color: #3b2a13;
}
header h1 {
  margin: 0 0 6px;
  font-size: 22px;
  font-family: 'Noto Sans SC', 'Source Han Sans SC', system-ui, sans-serif;
}
.actions {
  display: flex;
  justify-content: flex-end;
}
.primary {
  background: #2f5b8a;
  color: var(--pyt-color-bg-elevated);
  border: 2px solid #1b3a5a;
  border-radius: var(--pyt-radius-lg);
  padding: 8px 18px;
  cursor: pointer;
}
.primary:hover {
  background: #3a6da4;
}
.secondary {
  background: transparent;
  color: #2f5b8a;
  border: 2px solid #2f5b8a;
  border-radius: 6px;
  padding: 6px 14px;
  cursor: pointer;
}
.create-form {
  display: grid;
  gap: 12px;
  background: #fffbe9;
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  padding: 16px;
}
.create-form input,
.create-form textarea {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--pyt-color-border);
  border-radius: 6px;
  background: #fff;
  font-family: inherit;
}
.create-form textarea {
  min-height: 60px;
}
.empty {
  padding: 16px;
  background: var(--pyt-color-bg-soft);
  border-radius: var(--pyt-radius-lg);
  text-align: center;
  color: var(--pyt-color-text-hint);
}
.classroom-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
}
.classroom-card {
  background: #fffbe9;
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  padding: 14px;
  display: grid;
  gap: 8px;
}
.classroom-card header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.classroom-card h3 {
  margin: 0;
  font-size: 16px;
}
.badge {
  background: #2f5b8a;
  color: var(--pyt-color-bg-elevated);
  border-radius: 999px;
  padding: 2px 10px;
  font-size: 12px;
}
.classroom-card .desc {
  margin: 0;
  color: var(--pyt-color-text-primary);
  font-size: 13px;
  line-height: 1.55;
}
.classroom-card footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 6px;
}
.code {
  font-size: 13px;
  color: var(--pyt-color-text-hint);
}
</style>
