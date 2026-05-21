<script setup lang="ts">
import { reactive, ref } from 'vue';
import type {
  ClassroomQuestCreateRequest,
  ClassroomQuestDto,
} from '@/api/dto/ClassroomQuest';
import type { QuestCompletionDto } from '@/api/dto/TeacherDashboard';

const props = defineProps<{
  quests: ClassroomQuestDto[];
  questSummary: QuestCompletionDto[];
}>();
const emit = defineEmits<{
  (event: 'publish', payload: ClassroomQuestCreateRequest): void;
  (event: 'archive', questId: number): void;
}>();

interface QuestFormState {
  title: string;
  description: string;
  kcCodes: string[];
  targetProblemId: number | null;
  targetResourceType: string | null;
  dueDate: string;
}

const form = reactive<QuestFormState>({
  title: '',
  description: '',
  kcCodes: [],
  targetProblemId: null,
  targetResourceType: null,
  dueDate: '',
});

const kcInput = ref<string>('');

const resourceTypes = ['HANDOUT', 'MIND_MAP', 'PRACTICE', 'CODE_LAB', 'READING', 'STORYBOARD'];

function onProblemInput(raw: string) {
  form.targetProblemId = raw === '' ? null : Number(raw);
}

function syncKcCodes() {
  form.kcCodes = kcInput.value
    .split(/[,，\s]+/)
    .map((code) => code.trim())
    .filter((code) => code.length > 0);
}

function submit() {
  syncKcCodes();
  const payload: ClassroomQuestCreateRequest = {
    title: form.title,
    kcCodes: form.kcCodes,
    dueDate: form.dueDate,
  };
  if (form.description.trim().length > 0) {
    payload.description = form.description;
  }
  if (form.targetProblemId != null) {
    payload.targetProblemId = form.targetProblemId;
  }
  if (form.targetResourceType) {
    payload.targetResourceType = form.targetResourceType;
  }
  emit('publish', payload);
}

function findCompletion(id: number): QuestCompletionDto | null {
  return props.questSummary.find((entry) => entry.classroomQuestId === id) ?? null;
}

function fmtRate(value: number): string {
  return (value * 100).toFixed(0) + '%';
}
</script>

<template>
  <section
    class="quest-tab"
    data-testid="quest-tab"
  >
    <form
      class="publish-form"
      data-testid="publish-form"
      @submit.prevent="submit"
    >
      <h3>发布班级任务</h3>
      <label>
        标题
        <input
          v-model="form.title"
          maxlength="64"
          required
        >
      </label>
      <label>
        描述
        <textarea
          v-model="form.description"
          maxlength="256"
        />
      </label>
      <label>
        KC 代码（用空格或逗号分隔）
        <input
          v-model="kcInput"
          required
          placeholder="例如 PY43-KC-001 PY43-KC-002"
        >
      </label>
      <label>
        题目 ID（与资源类型二选一）
        <input
          :value="form.targetProblemId ?? ''"
          type="number"
          min="1"
          @input="onProblemInput(($event.target as HTMLInputElement).value)"
        >
      </label>
      <label>
        资源类型
        <select v-model="form.targetResourceType">
          <option :value="null">不绑定资源</option>
          <option
            v-for="type in resourceTypes"
            :key="type"
            :value="type"
          >
            {{ type }}
          </option>
        </select>
      </label>
      <label>
        截止日期
        <input
          v-model="form.dueDate"
          type="date"
          required
        >
      </label>
      <button
        type="submit"
        class="primary"
      >
        发布
      </button>
    </form>

    <h3>班级任务列表</h3>
    <p
      v-if="quests.length === 0"
      class="empty"
    >
      还没有发布任务。
    </p>
    <table
      v-else
      class="quest-table"
    >
      <thead>
        <tr>
          <th>标题</th>
          <th>KC</th>
          <th>截止</th>
          <th>状态</th>
          <th>完成率</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="quest in quests"
          :key="quest.id"
        >
          <td>{{ quest.title }}</td>
          <td>{{ quest.kcCodes.join(', ') }}</td>
          <td>{{ quest.dueDate }}</td>
          <td>{{ quest.status }}</td>
          <td>
            <template v-if="findCompletion(quest.id)">
              {{ findCompletion(quest.id)!.completedCount }}/{{ findCompletion(quest.id)!.totalStudents }}
              ({{ fmtRate(findCompletion(quest.id)!.completionRate) }})
            </template>
            <template v-else>
              ——
            </template>
          </td>
          <td>
            <button
              v-if="quest.status === 'ACTIVE'"
              class="secondary"
              @click="emit('archive', quest.id)"
            >
              归档
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </section>
</template>

<style scoped>
.quest-tab {
  display: grid;
  gap: 16px;
}
.publish-form {
  display: grid;
  gap: 8px;
  background: #fffbe9;
  border: 2px solid #c9a86b;
  border-radius: 10px;
  padding: 14px;
}
.publish-form input,
.publish-form textarea,
.publish-form select {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid #c9a86b;
  border-radius: 6px;
  background: #fff;
}
.primary {
  background: #2f5b8a;
  color: #fff7e6;
  border: 2px solid #1b3a5a;
  border-radius: 8px;
  padding: 8px 18px;
  cursor: pointer;
}
.secondary {
  background: transparent;
  color: #2f5b8a;
  border: 2px solid #2f5b8a;
  border-radius: 6px;
  padding: 4px 10px;
  cursor: pointer;
}
.empty {
  padding: 16px;
  background: #fdf3d0;
  border-radius: 8px;
  text-align: center;
}
.quest-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.quest-table th,
.quest-table td {
  border: 1px solid #c9a86b;
  padding: 6px 8px;
  text-align: center;
}
.quest-table th {
  background: #fdf3d0;
}
</style>
