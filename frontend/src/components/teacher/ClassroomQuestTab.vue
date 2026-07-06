<script setup lang="ts">
import { reactive, ref } from 'vue';
import PyButton from '@/components/ui/PyButton.vue';
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

function rateColor(value: number): string {
  if (value >= 0.8) return 'var(--pyt-color-success)';
  if (value >= 0.4) return 'var(--pyt-color-warning)';
  return 'var(--pyt-color-danger)';
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

      <!-- 基础信息 -->
      <fieldset class="form-section">
        <legend>基础信息</legend>
        <label>
          标题
          <input
            v-model="form.title"
            maxlength="64"
            placeholder="例如 第 2 章随堂练习"
            required
          >
        </label>
        <label>
          描述（选填）
          <textarea
            v-model="form.description"
            maxlength="256"
            placeholder="补充任务说明、提交要求"
          />
        </label>
        <label>
          KC 代码（空格或逗号分隔）
          <input
            v-model="kcInput"
            required
            placeholder="例如 PY43-KC-001 PY43-KC-002"
          >
        </label>
      </fieldset>

      <!-- 绑定目标 -->
      <fieldset class="form-section">
        <legend>绑定目标（题目 / 资源二选一，可留空）</legend>
        <div class="form-row">
          <label>
            题目 ID
            <input
              :value="form.targetProblemId ?? ''"
              type="number"
              min="1"
              placeholder="如 1024"
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
        </div>
      </fieldset>

      <!-- 截止 + 提交 -->
      <div class="form-foot">
        <label class="form-foot__due">
          截止日期
          <input
            v-model="form.dueDate"
            type="date"
            required
          >
        </label>
        <PyButton
          type="submit"
          variant="primary"
        >
          发布任务
        </PyButton>
      </div>
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
            <div
              v-if="findCompletion(quest.id)"
              class="rate-cell"
            >
              <div class="rate-bar">
                <span
                  class="rate-bar__fill"
                  :style="{ width: fmtRate(findCompletion(quest.id)!.completionRate), background: rateColor(findCompletion(quest.id)!.completionRate) }"
                />
              </div>
              <span class="rate-text">
                {{ findCompletion(quest.id)!.completedCount }}/{{ findCompletion(quest.id)!.totalStudents }}
                · {{ fmtRate(findCompletion(quest.id)!.completionRate) }}
              </span>
            </div>
            <span
              v-else
              class="rate-na"
            >——</span>
          </td>
          <td>
            <PyButton
              v-if="quest.status === 'ACTIVE'"
              variant="secondary"
              size="sm"
              @click="emit('archive', quest.id)"
            >
              归档
            </PyButton>
          </td>
        </tr>
      </tbody>
    </table>
  </section>
</template>

<style scoped>
.quest-tab { display: grid; gap: 16px; }
.publish-form {
  display: grid;
  gap: 10px;
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  padding: 16px;
}
.publish-form h3 { margin: 0; font-size: var(--pyt-font-size-base); font-family: var(--pyt-font-pixel); }
/* ─── 表单分区 ─── */
.form-section {
  border: 1px solid var(--pyt-color-border-soft);
  border-radius: var(--pyt-radius-md);
  padding: 12px 14px;
  margin: 0;
  display: grid;
  gap: 10px;
}
.form-section legend {
  padding: 0 6px;
  font-size: var(--pyt-font-size-xs);
  font-weight: 700;
  color: var(--pyt-color-text-muted);
}
.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.form-foot {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.form-foot__due { flex: 0 0 auto; }
.publish-form label {
  display: grid;
  gap: 4px;
  font-size: var(--pyt-font-size-sm);
  font-weight: 600;
  color: var(--pyt-color-text-primary);
}
.publish-form input,
.publish-form textarea,
.publish-form select {
  width: 100%;
  padding: 6px 10px;
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  background: var(--pyt-color-bg-primary);
  color: var(--pyt-color-text-primary);
  font-family: inherit;
  font-size: var(--pyt-font-size-base);
}
.publish-form input:focus,
.publish-form textarea:focus,
.publish-form select:focus {
  outline: none;
  border-color: var(--pyt-color-gold);
}
.empty {
  padding: 16px;
  background: var(--pyt-color-bg-soft);
  border-radius: var(--pyt-radius-lg);
  text-align: center;
  color: var(--pyt-color-text-hint);
}
.quest-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--pyt-font-size-sm);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  overflow: hidden;
}
.quest-table th,
.quest-table td {
  border-bottom: 1px solid var(--pyt-color-border-soft);
  padding: 8px 10px;
  text-align: left;
}
.quest-table th {
  background: var(--pyt-color-bg-sunken);
  color: var(--pyt-color-text-hint);
  font-weight: 700;
  font-size: var(--pyt-font-size-xs);
  border-bottom: 2px solid var(--pyt-color-border);
}
.quest-table tbody tr:hover { background: var(--pyt-color-warning-bg); }
.quest-table tbody tr:last-child td { border-bottom: none; }

/* ─── 完成率进度条 ─── */
.rate-cell { display: grid; gap: 3px; min-width: 120px; }
.rate-bar {
  height: 6px;
  background: var(--pyt-color-bg-sunken);
  border-radius: var(--pyt-radius-full);
  overflow: hidden;
}
.rate-bar__fill {
  display: block;
  height: 100%;
  border-radius: var(--pyt-radius-full);
  transition: width var(--pyt-motion-slow);
}
.rate-text { font-size: var(--pyt-font-size-xs); color: var(--pyt-color-text-muted); }
.rate-na { color: var(--pyt-color-text-muted); }
</style>
