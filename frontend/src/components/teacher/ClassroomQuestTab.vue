<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import PyButton from '@/components/ui/PyButton.vue';
import type {
  ClassroomQuestCreateRequest,
  ClassroomQuestDto,
} from '@/api/dto/ClassroomQuest';
import type { QuestCompletionDto } from '@/api/dto/TeacherDashboard';
import { kcDisplayName } from '@/data/kcNames';

const props = withDefaults(
  defineProps<{
    quests: ClassroomQuestDto[];
    questSummary: QuestCompletionDto[];
    /** 发布请求是否在飞行中；由父组件在 await publish 期间置 true，防重复提交。 */
    publishing?: boolean;
  }>(),
  { publishing: false },
);
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
const submitError = ref<string | null>(null);

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

const anchorMissing = computed(
  () => form.targetProblemId == null && !form.targetResourceType,
);
/** 表单可提交性：请求飞行中禁止再次提交（否则回车/双击会产出两条重复的 classroom_quest）。 */
const canSubmit = computed(() => !props.publishing && !anchorMissing.value);

function submit() {
  // `<form @submit.prevent>` 里任意 input 按回车都会触发提交，所以校验必须写在这里，
  // 不能只靠按钮的 disabled 状态。
  if (props.publishing) return;
  syncKcCodes();
  if (anchorMissing.value) {
    // 后端 validateRequest 与 DB CHECK(chk_classroom_quest_anchor) 都要求二者必居其一，
    // 前置拦截省掉一次必然 400 的往返。
    submitError.value = '请至少绑定一个目标：题目 ID 或资源类型。';
    return;
  }
  submitError.value = null;
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
          知识点（空格或逗号分隔）
          <input
            v-model="kcInput"
            required
            placeholder="输入关联知识点"
          >
        </label>
      </fieldset>

      <!-- 绑定目标 -->
      <fieldset class="form-section">
        <legend>绑定目标（题目 / 资源至少选其一）</legend>
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
          test-id="publish-submit"
          :disabled="!canSubmit"
        >
          {{ publishing ? '发布中...' : '发布任务' }}
        </PyButton>
      </div>
      <p
        v-if="submitError"
        class="form-error"
        data-testid="publish-error"
      >
        {{ submitError }}
      </p>
    </form>

    <h3>班级任务列表</h3>
    <p
      v-if="quests.length === 0"
      class="empty"
    >
      还没有发布任务。
    </p>
    <div
      v-else
      class="quest-table-scroll"
      role="region"
      aria-label="班级任务列表，可横向滚动"
      tabindex="0"
    >
      <table>
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
          <td>{{ quest.kcCodes.map(kcDisplayName).join('、') }}</td>
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
    </div>
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
.form-error {
  margin: 0;
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-danger);
}
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
.quest-table-scroll {
  min-width: 0;
  max-width: 100%;
  overflow-x: auto;
  border-radius: var(--pyt-radius-md);
}
.quest-table-scroll:focus-visible {
  outline: 2px solid var(--pyt-color-gold);
  outline-offset: 2px;
}
.quest-table {
  width: 100%;
  min-width: 760px;
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
