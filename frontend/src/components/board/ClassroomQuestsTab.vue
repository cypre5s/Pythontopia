<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useLearningStore } from '@stores/learning';
import { useToastStore } from '@stores/toast';
import type { ClassroomQuestDto } from '@/api/dto/ClassroomQuest';
import type { QuestCompleteRequest } from '@/api/dto/Quest';
import type { SubmissionDto } from '@/api/dto/Submission';
import {
  evidenceErrorMessage,
  resolveClassroomQuestDeepLink,
} from '@/utils/questDeepLink';

const learning = useLearningStore();
const toast = useToastStore();
const router = useRouter();

const quests = computed(() => learning.quests.classroom);

const submissionsByQuest = reactive<Record<number, SubmissionDto[]>>({});
const selectedSubmissionByQuest = reactive<Record<number, number | null>>({});
const evidenceLoading = ref<number | null>(null);
const submitLoading = ref<number | null>(null);

onMounted(async () => {
  if (learning.quests.classroom.length === 0) {
    await learning.loadClassroomQuests();
  }
});

function deepLinkTitle(quest: ClassroomQuestDto): string {
  switch (quest.nodeType) {
    case 'SOLVE_PROBLEM':
      return quest.targetProblemId ? '去做题' : '去练习';
    case 'READ_RESOURCE':
      return '去工坊读资源';
    case 'FIX_BUG':
      return '去矿洞修 Bug';
    case 'PLANT_KC':
      return '去田里种知识';
    case 'PROFILE_UPDATE':
      return '去画像屋';
    default:
      return '去做';
  }
}

async function goWork(quest: ClassroomQuestDto): Promise<void> {
  const target = resolveClassroomQuestDeepLink(quest);
  await router.push(target);
}

function targetProblemIdFor(quest: ClassroomQuestDto): number | null {
  if (quest.targetProblemId) return quest.targetProblemId;
  if (quest.targetProblemIds && quest.targetProblemIds.length > 0) {
    return quest.targetProblemIds[0] ?? null;
  }
  return null;
}

async function loadSubmissionsFor(quest: ClassroomQuestDto): Promise<void> {
  const problemId = targetProblemIdFor(quest);
  if (!problemId) {
    submissionsByQuest[quest.id] = [];
    return;
  }
  evidenceLoading.value = quest.id;
  try {
    const list = await learning.listRecentPassedSubmissions(problemId, 10);
    submissionsByQuest[quest.id] = list;
    if (list.length > 0 && selectedSubmissionByQuest[quest.id] == null) {
      selectedSubmissionByQuest[quest.id] = list[0]?.id ?? null;
    }
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '加载提交失败', 'error');
  } finally {
    evidenceLoading.value = null;
  }
}

function pickSubmission(quest: ClassroomQuestDto, submissionId: number): void {
  selectedSubmissionByQuest[quest.id] = submissionId;
}

async function submitComplete(quest: ClassroomQuestDto): Promise<void> {
  if (!quest.studentQuestId) {
    toast.show('当前学生尚未生成班级任务副本，请刷新公告板。', 'error');
    return;
  }
  const request: QuestCompleteRequest = { note: '在公告板提交完成' };
  if (quest.requiredEvidenceType === 'submission') {
    const submissionId = selectedSubmissionByQuest[quest.id];
    if (!submissionId) {
      toast.show('请先在下方选择一次已通过的提交作为证据。', 'error');
      return;
    }
    request.submissionId = submissionId;
  }
  submitLoading.value = quest.id;
  try {
    await learning.completeQuestById(quest.studentQuestId, request);
    toast.show('班级任务已提交完成。', 'success');
    await learning.loadClassroomQuests();
  } catch (error) {
    const code = extractCode(error);
    const friendly = evidenceErrorMessage(code);
    const fallback = error instanceof Error ? error.message : '提交失败';
    toast.show(friendly ?? fallback, 'error');
  } finally {
    submitLoading.value = null;
  }
}

function extractCode(error: unknown): string | null {
  if (typeof error === 'object' && error !== null && 'code' in error) {
    const code = (error as { code?: unknown }).code;
    return typeof code === 'string' ? code : null;
  }
  if (error instanceof Error && 'cause' in error) {
    const cause = (error as Error & { cause?: { code?: unknown } }).cause;
    if (cause && typeof cause === 'object' && 'code' in cause) {
      const code = (cause as { code?: unknown }).code;
      return typeof code === 'string' ? code : null;
    }
  }
  return null;
}

function statusLabel(quest: ClassroomQuestDto): string {
  switch (quest.studentQuestStatus) {
    case 'OPEN':
      return '未开始';
    case 'IN_PROGRESS':
      return '进行中';
    case 'COMPLETED':
      return '已完成';
    case 'SKIPPED':
      return '已跳过';
    case 'EXPIRED':
      return '已过期';
    default:
      return '未分发';
  }
}

function isCompletable(quest: ClassroomQuestDto): boolean {
  return (
    quest.studentQuestId != null &&
    (quest.studentQuestStatus === 'OPEN' || quest.studentQuestStatus === 'IN_PROGRESS')
  );
}
</script>

<template>
  <section
    class="classroom-tab"
    data-testid="classroom-tab"
  >
    <p
      v-if="quests.length === 0"
      class="empty"
    >
      暂时没有班级任务。教师在阶段 12 上线后会在这里发布。
    </p>
    <ul
      v-else
      class="quest-list"
    >
      <li
        v-for="quest in quests"
        :key="quest.id"
        class="quest-card"
        :data-testid="`classroom-quest-${quest.id}`"
      >
        <header>
          <h3>{{ quest.title }}</h3>
          <span class="badge">截止 {{ quest.dueDate }}</span>
        </header>
        <p
          v-if="quest.description"
          class="description"
        >
          {{ quest.description }}
        </p>
        <p class="meta">
          KC：{{ quest.kcCodes.join(', ') || '未指定' }} · 类型：{{ quest.nodeType }} ·
          状态：{{ statusLabel(quest) }}
        </p>
        <div class="actions">
          <button
            type="button"
            class="primary"
            :data-testid="`classroom-quest-go-${quest.id}`"
            @click="goWork(quest)"
          >
            {{ deepLinkTitle(quest) }} →
          </button>
          <button
            v-if="quest.requiredEvidenceType === 'submission' && targetProblemIdFor(quest)"
            type="button"
            class="ghost"
            :data-testid="`classroom-quest-load-${quest.id}`"
            :disabled="evidenceLoading === quest.id"
            @click="loadSubmissionsFor(quest)"
          >
            {{ evidenceLoading === quest.id ? '加载中…' : '选择提交' }}
          </button>
          <button
            v-if="isCompletable(quest)"
            type="button"
            class="primary"
            :data-testid="`classroom-quest-complete-${quest.id}`"
            :disabled="submitLoading === quest.id"
            @click="submitComplete(quest)"
          >
            {{ submitLoading === quest.id ? '提交中…' : '提交完成' }}
          </button>
        </div>
        <div
          v-if="quest.requiredEvidenceType === 'submission' && submissionsByQuest[quest.id]"
          class="evidence-picker"
          :data-testid="`classroom-quest-picker-${quest.id}`"
        >
          <p
            v-if="(submissionsByQuest[quest.id] ?? []).length === 0"
            class="hint"
          >
            还没找到 PASSED 提交，请先去训练馆通过这道题。
          </p>
          <label
            v-for="submission in submissionsByQuest[quest.id] ?? []"
            v-else
            :key="submission.id"
            class="evidence-option"
          >
            <input
              type="radio"
              :name="`evidence-${quest.id}`"
              :value="submission.id"
              :checked="selectedSubmissionByQuest[quest.id] === submission.id"
              @change="pickSubmission(quest, submission.id)"
            >
            <span>
              #{{ submission.id }} · 题目 {{ submission.problemId }} ·
              {{ submission.submittedAt.slice(0, 16).replace('T', ' ') }}
            </span>
          </label>
        </div>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.empty {
  padding: 14px;
  background: #fdf3d0;
  border-radius: 8px;
  text-align: center;
  color: #6e4f1d;
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
  border: 2px solid #c9a86b;
  border-radius: 8px;
  padding: 12px;
  display: grid;
  gap: 8px;
}
header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
header h3 {
  margin: 0;
  font-size: 16px;
}
.badge {
  padding: 2px 8px;
  background: #2f5b8a;
  color: #fff7e6;
  border-radius: 999px;
  font-size: 12px;
}
.description {
  margin: 0;
  color: #4d3613;
  line-height: 1.55;
}
.meta {
  margin: 0;
  color: #6e4f1d;
  font-size: 12px;
}
.actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.actions button {
  padding: 6px 12px;
  border-radius: 6px;
  border: 2px solid #6e4f1d;
  cursor: pointer;
}
.actions .primary {
  background: #6e4f1d;
  color: #fff7e6;
}
.actions .ghost {
  background: #fff7e6;
  color: #6e4f1d;
}
.actions button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.evidence-picker {
  background: #fff2c9;
  border-radius: 6px;
  padding: 8px;
  display: grid;
  gap: 4px;
}
.evidence-option {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 12px;
  color: #4d3613;
}
.hint {
  margin: 0;
  font-size: 12px;
  color: #6e4f1d;
}
</style>
