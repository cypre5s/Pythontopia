import { ref } from 'vue';
import {
  completeQuest,
  getTodayQuests,
  skipQuest,
  startQuest,
} from '@/api/quests';
import { getMyClassroomQuests } from '@/api/classroomQuests';
import { listSubmissions } from '@/api/submissions';
import type { ClassroomQuestDto } from '@/api/dto/ClassroomQuest';
import type {
  QuestCompleteRequest,
  QuestDto,
  QuestSkipRequest,
} from '@/api/dto/Quest';
import type { SubmissionDto } from '@/api/dto/Submission';

export interface QuestsState {
  today: QuestDto[];
  classroom: ClassroomQuestDto[];
  loading: boolean;
  lastError: string | null;
}

/** 今日任务 / 班级任务切片（任务完成需 evidence，由后端 QuestService 强校验）。 */
export function createQuestsSlice() {
  const quests = ref<QuestsState>({
    today: [],
    classroom: [],
    loading: false,
    lastError: null,
  });

  async function loadTodayQuests(): Promise<void> {
    quests.value.loading = true;
    quests.value.lastError = null;
    try {
      quests.value.today = await getTodayQuests();
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      quests.value.lastError = message;
    } finally {
      quests.value.loading = false;
    }
  }

  async function loadClassroomQuests(): Promise<void> {
    try {
      quests.value.classroom = await getMyClassroomQuests();
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      quests.value.lastError = message;
    }
  }

  async function listRecentPassedSubmissions(
    problemId: number,
    limit = 10,
  ): Promise<SubmissionDto[]> {
    const submissions = await listSubmissions({
      problemId,
      status: 'PASSED',
      limit,
    });
    return submissions;
  }

  function replaceQuest(quest: QuestDto): void {
    const index = quests.value.today.findIndex((entry) => entry.id === quest.id);
    if (index === -1) {
      if (quest.status === 'OPEN' || quest.status === 'IN_PROGRESS') {
        quests.value.today = [...quests.value.today, quest];
      }
      return;
    }
    if (quest.status === 'COMPLETED' || quest.status === 'EXPIRED') {
      quests.value.today = quests.value.today.filter((entry) => entry.id !== quest.id);
      return;
    }
    const next = [...quests.value.today];
    next[index] = quest;
    quests.value.today = next;
  }

  function removeQuest(id: number): void {
    quests.value.today = quests.value.today.filter((entry) => entry.id !== id);
  }

  async function startQuestById(id: number): Promise<QuestDto> {
    const updated = await startQuest(id);
    replaceQuest(updated);
    return updated;
  }

  async function completeQuestById(
    id: number,
    request: QuestCompleteRequest = {},
  ): Promise<QuestDto> {
    const updated = await completeQuest(id, request);
    replaceQuest(updated);
    return updated;
  }

  async function skipQuestById(id: number, request: QuestSkipRequest = {}): Promise<QuestDto> {
    const updated = await skipQuest(id, request);
    removeQuest(updated.id);
    return updated;
  }

  return {
    quests,
    loadTodayQuests,
    loadClassroomQuests,
    listRecentPassedSubmissions,
    startQuestById,
    completeQuestById,
    skipQuestById,
    replaceQuest,
    removeQuest,
  };
}
