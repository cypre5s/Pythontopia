import { computed, ref } from 'vue';
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

  /**
   * 加载失败与「今天真的没有任务」是两回事。
   *
   * 拉取异常时 `today` 保持原样（很可能仍是空数组），只有 `lastError` 能区分两者；
   * 组件的空态判定必须叠加 `!questsLoadFailed`，否则后端抖动会被渲染成「今天没有任务」，
   * 学生既看不到出错也没有重试入口。
   */
  const questsLoadFailed = computed<boolean>(() => quests.value.lastError !== null);

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
      // 成功必须清错：否则一次失败会让错误态永久粘住，重试成功也退不出来。
      quests.value.lastError = null;
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      quests.value.lastError = message;
    }
  }

  /** 供组件的「重试」按钮在发起重试前清掉上一次的错误提示。 */
  function clearQuestsError(): void {
    quests.value.lastError = null;
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
    questsLoadFailed,
    clearQuestsError,
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
