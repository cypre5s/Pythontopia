<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useLearningStore } from '@stores/learning';
import { useToastStore } from '@stores/toast';
import { listWorkshopResources } from '@/api/workshop';
import type { QuestCompleteRequest, QuestDto, QuestRequiredEvidenceType } from '@/api/dto/Quest';
import type { SubmissionDto } from '@/api/dto/Submission';
import type { WorkshopResourceListItemDto } from '@/api/dto/Workshop';
import { kcDisplayName } from '@/data/kcNames';
import { useWorkshopUiStore } from '@stores/workshopUi';
import { evidenceErrorMessage, resolveQuestDeepLink } from '@/utils/questDeepLink';

const learning = useLearningStore();
const toast = useToastStore();
const workshopUi = useWorkshopUiStore();
const router = useRouter();

const quests = computed<QuestDto[]>(() => learning.quests.today);
const submissionsByQuest = reactive<Record<number, SubmissionDto[]>>({});
const resourcesByQuest = reactive<Record<number, WorkshopResourceListItemDto[]>>({});
const selectedSubmissionByQuest = reactive<Record<number, number | null>>({});
const selectedResourceByQuest = reactive<Record<number, number | null>>({});
const selectedPageReadIdByQuest = reactive<Record<number, number | null>>({});
const evidenceLoading = ref<number | null>(null);
const completingQuestId = ref<number | null>(null);

onMounted(async () => {
  if (learning.quests.today.length === 0 && !learning.quests.loading) {
    await learning.loadTodayQuests();
  }
});

async function start(id: number): Promise<void> {
  try {
    await learning.startQuestById(id);
    toast.show('已开始任务。', 'success');
  } catch (error) {
    const message = error instanceof Error ? error.message : '开始任务失败';
    toast.show(message, 'error');
  }
}

async function goWork(quest: QuestDto): Promise<void> {
  await router.push(resolveQuestDeepLink(quest));
}

async function openResource(id: number): Promise<void> {
  workshopUi.openResource(id);
  await router.push('/workshop');
}

async function loadEvidence(quest: QuestDto): Promise<void> {
  evidenceLoading.value = quest.id;
  try {
    const type = effectiveEvidenceType(quest);
    if (type === 'submission') {
      const problemId = quest.targetProblemIds[0]
        ?? positiveNumber(quest.payload.suggestedProblemId);
      const submissions = await learning.listRecentPassedSubmissions(problemId ?? undefined, 20);
      submissionsByQuest[quest.id] = submissions;
      selectedSubmissionByQuest[quest.id] = submissions[0]?.id ?? null;
    } else if (type === 'resource') {
      const resources = await listWorkshopResources({
        kcCode: quest.kcCode ?? undefined,
        limit: 20,
      });
      resourcesByQuest[quest.id] = resources;
      selectedResourceByQuest[quest.id] = resources[0]?.id ?? null;
    }
  } catch (error) {
    toast.show(errorMessage(error, '加载任务凭证失败'), 'error');
  } finally {
    evidenceLoading.value = null;
  }
}

async function complete(quest: QuestDto): Promise<void> {
  const request: QuestCompleteRequest = { note: '在公告板完成' };
  switch (effectiveEvidenceType(quest)) {
    case 'submission': {
      const submissionId = selectedSubmissionByQuest[quest.id];
      if (submissionId == null) {
        toast.show('请先选择一次已通过的提交作为证据。', 'error');
        return;
      }
      request.submissionId = submissionId;
      break;
    }
    case 'resource': {
      const resourceId = selectedResourceByQuest[quest.id];
      if (resourceId == null) {
        toast.show('请先在工坊生成并打开该知识点的资源。', 'error');
        return;
      }
      request.resourceId = resourceId;
      break;
    }
    case 'page_read': {
      const pageReadId = selectedPageReadIdByQuest[quest.id];
      if (pageReadId == null) {
        toast.show('请先输入本次已读页面的编号。', 'error');
        return;
      }
      request.pageReadId = pageReadId;
      break;
    }
    case 'kc_mastery':
      request.kcMasteryDelta = quest.requiredMasteryDelta ?? 0;
      break;
  }

  completingQuestId.value = quest.id;
  try {
    await learning.completeQuestById(quest.id, request);
    toast.show('任务已完成。', 'success');
  } catch (error) {
    toast.show(evidenceErrorMessage(errorCode(error)) ?? errorMessage(error, '任务完成失败'), 'error');
  } finally {
    completingQuestId.value = null;
  }
}

function effectiveEvidenceType(quest: QuestDto): QuestRequiredEvidenceType | null {
  if (quest.requiredEvidenceType != null) return quest.requiredEvidenceType;
  if (quest.source !== 'PATH_PLANNER') return null;
  switch (quest.nodeType) {
    case 'READ_RESOURCE': return 'resource';
    case 'SOLVE_PROBLEM':
    case 'FIX_BUG': return 'submission';
    case 'PLANT_KC': return 'kc_mastery';
    case 'PROFILE_UPDATE': return null;
  }
}

function hasUnavailableLegacyEvidence(quest: QuestDto): boolean {
  return quest.source === 'PATH_PLANNER'
    && quest.requiredEvidenceType == null
    && quest.nodeType === 'PROFILE_UPDATE';
}

function positiveNumber(value: unknown): number | null {
  if (typeof value === 'number' && Number.isInteger(value) && value > 0) return value;
  if (typeof value === 'string' && /^\d+$/.test(value) && Number(value) > 0) return Number(value);
  return null;
}

function errorCode(error: unknown): string | null {
  if (typeof error === 'object' && error !== null && 'code' in error) {
    const code = (error as { code?: unknown }).code;
    return typeof code === 'string' ? code : null;
  }
  return null;
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback;
}

async function skip(id: number): Promise<void> {
  try {
    await learning.skipQuestById(id, { reason: '用户跳过' });
    toast.show('已跳过任务。', 'success');
  } catch (error) {
    const message = error instanceof Error ? error.message : '跳过任务失败';
    toast.show(message, 'error');
  }
}
</script>

<template>
  <section
    class="today-tab"
    data-testid="today-tab"
  >
    <p
      v-if="learning.quests.loading"
      class="feedback"
      role="status"
    >
      正在加载今日任务…
    </p>
    <div
      v-else-if="learning.quests.lastError"
      class="feedback error"
      role="alert"
    >
      <p>加载今日任务失败：{{ learning.quests.lastError }}</p>
      <button
        type="button"
        data-testid="today-quests-retry"
        @click="learning.loadTodayQuests()"
      >
        重试
      </button>
    </div>
    <p
      v-else-if="quests.length === 0"
      class="empty"
    >
      今日暂无待办任务。可以去公告板的“规划本周路径”重新生成。
    </p>
    <ul
      v-else-if="quests.length > 0"
      class="quest-list"
      data-testid="quest-list"
    >
      <li
        v-for="quest in quests"
        :key="quest.id"
        class="quest-card"
      >
        <header>
          <span
            class="badge"
            :class="quest.source.toLowerCase()"
          >
            {{ quest.source }}
          </span>
          <h3>{{ quest.title }}</h3>
        </header>
        <p
          v-if="quest.description"
          class="description"
        >
          {{ quest.description }}
        </p>
        <p class="meta">
          知识点: {{ quest.kcCode ? kcDisplayName(quest.kcCode) : '未关联' }} · 类型: {{ quest.nodeType }} · 截止 {{ quest.dueDate }}
        </p>
        <div class="actions">
          <button
            type="button"
            class="ghost"
            :data-testid="`today-quest-go-${quest.id}`"
            @click="goWork(quest)"
          >
            去做 →
          </button>
          <button
            v-if="quest.status === 'OPEN'"
            type="button"
            class="primary"
            @click="start(quest.id)"
          >
            开始
          </button>
          <button
            v-if="(quest.status === 'OPEN' || quest.status === 'IN_PROGRESS') && effectiveEvidenceType(quest) == null && !hasUnavailableLegacyEvidence(quest)"
            type="button"
            class="primary"
            :disabled="completingQuestId === quest.id"
            @click="complete(quest)"
          >
            完成
          </button>
          <button
            v-if="(quest.status === 'OPEN' || quest.status === 'IN_PROGRESS') && ['submission', 'resource'].includes(effectiveEvidenceType(quest) ?? '')"
            type="button"
            class="ghost"
            :data-testid="`today-quest-load-evidence-${quest.id}`"
            :disabled="evidenceLoading === quest.id"
            @click="loadEvidence(quest)"
          >
            {{ evidenceLoading === quest.id ? '加载中…' : '选择凭证' }}
          </button>
          <button
            v-if="(quest.status === 'OPEN' || quest.status === 'IN_PROGRESS') && effectiveEvidenceType(quest)"
            type="button"
            class="primary"
            :data-testid="`today-quest-complete-${quest.id}`"
            :disabled="completingQuestId === quest.id"
            @click="complete(quest)"
          >
            {{ completingQuestId === quest.id ? '提交中…' : '提交完成' }}
          </button>
          <button
            v-if="quest.status === 'OPEN' || quest.status === 'IN_PROGRESS'"
            type="button"
            class="ghost"
            @click="skip(quest.id)"
          >
            跳过
          </button>
          <span
            v-if="quest.status === 'IN_PROGRESS'"
            class="status"
          >进行中</span>
        </div>
        <div
          v-if="submissionsByQuest[quest.id]"
          class="evidence-picker"
          :data-testid="`today-quest-submissions-${quest.id}`"
        >
          <p
            v-if="submissionsByQuest[quest.id]?.length === 0"
            class="hint"
          >
            暂无已通过提交。先完成该知识点练习，再回来选择凭证。
          </p>
          <label
            v-for="submission in submissionsByQuest[quest.id] ?? []"
            :key="submission.id"
            class="evidence-option"
          >
            <input
              v-model="selectedSubmissionByQuest[quest.id]"
              type="radio"
              :name="`today-submission-${quest.id}`"
              :value="submission.id"
            >
            <span>#{{ submission.id }} · 题目 {{ submission.problemId }} · PASSED</span>
          </label>
        </div>
        <div
          v-if="resourcesByQuest[quest.id]"
          class="evidence-picker"
          :data-testid="`today-quest-resources-${quest.id}`"
        >
          <p
            v-if="resourcesByQuest[quest.id]?.length === 0"
            class="hint"
          >
            暂无该知识点资源。先去工坊生成并打开资源，再回来提交。
          </p>
          <div
            v-for="resource in resourcesByQuest[quest.id] ?? []"
            :key="resource.id"
            class="evidence-option"
          >
            <label>
              <input
                v-model="selectedResourceByQuest[quest.id]"
                type="radio"
                :name="`today-resource-${quest.id}`"
                :value="resource.id"
              >
              <span>#{{ resource.id }} · {{ resource.title }}</span>
            </label>
            <button
              type="button"
              class="ghost"
              :data-testid="`today-quest-open-resource-${quest.id}`"
              @click="openResource(resource.id)"
            >
              打开阅读
            </button>
          </div>
        </div>
        <label
          v-if="effectiveEvidenceType(quest) === 'page_read'"
          class="page-read-input"
        >
          已读页面编号
          <input
            v-model.number="selectedPageReadIdByQuest[quest.id]"
            type="number"
            min="1"
            step="1"
          >
        </label>
        <p
          v-if="hasUnavailableLegacyEvidence(quest)"
          class="hint"
        >
          此历史画像任务没有可核验凭证，请跳过并重新规划学习路径。
        </p>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.today-tab {
  display: grid;
  gap: 12px;
}
.quest-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 12px;
}
.quest-card {
  background: #fffbe9;
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  padding: 12px;
  display: grid;
  gap: 6px;
}
header {
  display: flex;
  align-items: center;
  gap: 8px;
}
header h3 {
  margin: 0;
  font-size: 16px;
}
.badge {
  padding: 2px 6px;
  background: var(--pyt-color-text-hint);
  color: var(--pyt-color-bg-elevated);
  border-radius: 999px;
  font-size: 12px;
}
.badge.path_planner { background: #4f8a3e; }
.badge.classroom { background: #2f5b8a; }
.badge.system_default { background: #c47a35; }
.description {
  margin: 0;
  color: var(--pyt-color-text-primary);
  line-height: 1.55;
}
.meta {
  margin: 0;
  font-size: 12px;
  color: var(--pyt-color-text-hint);
}
.actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
.actions button {
  padding: 6px 12px;
  border-radius: 6px;
  border: 2px solid var(--pyt-color-text-hint);
  cursor: pointer;
}
.actions .primary {
  background: var(--pyt-color-text-hint);
  color: var(--pyt-color-bg-elevated);
}
.actions .ghost {
  background: var(--pyt-color-bg-elevated);
  color: var(--pyt-color-text-hint);
}
.status {
  background: #fff2c9;
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 12px;
  color: var(--pyt-color-text-hint);
}
.evidence-picker {
  display: grid;
  gap: 8px;
  padding: 8px;
  border: 1px solid var(--pyt-color-border);
  border-radius: 6px;
}
.evidence-option,
.page-read-input {
  display: flex;
  align-items: center;
  gap: 8px;
}
.evidence-option label {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}
.hint {
  margin: 0;
  color: var(--pyt-color-text-hint);
}
.page-read-input input {
  width: 120px;
}
.empty {
  padding: 14px;
  background: var(--pyt-color-bg-soft);
  border-radius: var(--pyt-radius-lg);
  text-align: center;
  color: var(--pyt-color-text-hint);
}
.feedback {
  margin: 0;
  padding: 14px;
  background: var(--pyt-color-bg-soft);
  border-radius: var(--pyt-radius-lg);
  text-align: center;
}
.error {
  color: var(--pyt-color-text-primary);
}
.error p {
  margin: 0 0 8px;
}
.error button {
  padding: 6px 12px;
  border-radius: 6px;
  border: 2px solid var(--pyt-color-text-hint);
  background: var(--pyt-color-bg-elevated);
  color: var(--pyt-color-text-hint);
  cursor: pointer;
}
</style>
