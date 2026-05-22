import { defineStore } from 'pinia';
import {
  finishProfileInterview,
  getLearnerProfile,
  openProfileInterview,
  sendProfileTurn,
  type CheckResultDto,
  type ProfileMessageDto,
} from '@/api/learner';
import {
  bootstrapCurriculum,
  fetchCrops,
  fetchMastery,
} from '@/api/field';
import {
  createWorkshopResource,
  getWorkshopResource,
  listWorkshopResources,
  submitWorkshopFeedback,
} from '@/api/workshop';
import {
  getActiveLearningPath,
  getArchivedLearningPaths,
  planLearningPath,
} from '@/api/path';
import {
  completeQuest,
  getTodayQuests,
  skipQuest,
  startQuest,
} from '@/api/quests';
import { getMyClassroomQuests } from '@/api/classroomQuests';
import {
  createSubmission,
  getSubmission,
} from '@/api/submissions';
import { getPublicProblem } from '@/api/problems';
import { getPyodideRunner, type PyodideRunResult } from '@/game/adapters/pyodide';
import { celebrateMature } from '@/utils/celebrate';
import {
  openBugMineSession,
  getBugMineSession,
  revealBugMineCard,
  retryBugMineSubmit,
} from '@/api/bugMine';
import {
  acceptEvaluationPath,
  getEvaluation,
  keepOldEvaluationPath,
  listEvaluations,
  runEvaluation,
} from '@/api/evaluation';
import type { CropTileDto } from '@/api/dto/CropTileDto';
import type { CurriculumBootstrapResult } from '@/api/dto/CurriculumBootstrapResult';
import type { LearnerProfileDto } from '@/api/dto/LearnerProfileDto';
import type { MasterySnapshotDto } from '@/api/dto/MasterySnapshotDto';
import type {
  PublicProblemDto,
  SubmissionCreateRequest,
  SubmissionDetailDto,
  SubmissionDto,
} from '@/api/dto/Submission';
import type { BugMineSessionDto } from '@/api/dto/BugMine';
import type {
  EvaluationDto,
  EvaluationRequest,
} from '@/api/dto/Evaluation';
import type {
  WorkshopCreateRequest,
  WorkshopCreateResponse,
  WorkshopFeedbackRequest,
  WorkshopResourceDto,
  WorkshopResourceListItemDto,
  WorkshopResourceType,
} from '@/api/dto/Workshop';
import type { LearningPathDto } from '@/api/dto/Path';
import type { ClassroomQuestDto } from '@/api/dto/ClassroomQuest';
import type {
  QuestCompleteRequest,
  QuestDto,
  QuestSkipRequest,
} from '@/api/dto/Quest';

export interface FieldState {
  crops: CropTileDto[];
  masterySnapshot: MasterySnapshotDto[];
  activeKcCode: string | null;
  loading: boolean;
  bootstrapped: boolean;
  hydrated: boolean;
  lastSignature: string | null;
}

function detectNewMature(
  prev: readonly CropTileDto[],
  next: readonly CropTileDto[],
): boolean {
  if (next.length === 0) {
    return false;
  }
  const prevByKc = new Map(prev.map((crop) => [crop.kcCode, crop.status]));
  return next.some(
    (crop) => crop.status === 'MATURE' && prevByKc.get(crop.kcCode) !== 'MATURE',
  );
}

export type WorkshopRunPhase = 'idle' | 'creating' | 'success' | 'error';

export interface WorkshopFilters {
  type: WorkshopResourceType | null;
  kcCode: string | null;
}

export interface ResourcesState {
  list: WorkshopResourceListItemDto[];
  current: WorkshopResourceDto | null;
  filters: WorkshopFilters;
  phase: WorkshopRunPhase;
  lastError: string | null;
  loading: boolean;
}

export type PathPhase = 'idle' | 'planning' | 'ready' | 'error';

export interface PathState {
  active: LearningPathDto | null;
  archived: LearningPathDto[];
  phase: PathPhase;
  loading: boolean;
  lastError: string | null;
}

export interface QuestsState {
  today: QuestDto[];
  classroom: ClassroomQuestDto[];
  loading: boolean;
  lastError: string | null;
}

export type TrainingPhase = 'idle' | 'previewing' | 'submitting' | 'done' | 'error';

export interface TrainingState {
  problem: PublicProblemDto | null;
  code: string;
  preview: PyodideRunResult | null;
  submission: SubmissionDetailDto | null;
  phase: TrainingPhase;
  lastError: string | null;
}

export interface BugMineState {
  session: BugMineSessionDto | null;
  loading: boolean;
  lastError: string | null;
  lastRetry: SubmissionDto | null;
}

export type EvaluationPhase = 'idle' | 'running' | 'ready' | 'error';

export interface EvaluationState {
  list: EvaluationDto[];
  current: EvaluationDto | null;
  phase: EvaluationPhase;
  loading: boolean;
  lastError: string | null;
}

export const useLearningStore = defineStore('learning', {
  state: () => ({
    activePackId: 43,
    kcs: [] as string[],
    profile: null as LearnerProfileDto | null,
    path: [] as string[],
    profileInterview: {
      sessionId: null as string | null,
      history: [] as ProfileMessageDto[],
      currentQuestion: '请介绍你的专业背景、编程基础和学习目标。',
      dimensionsFilled: [] as string[],
      done: false,
      safety: null as CheckResultDto | null,
      grounding: null as CheckResultDto | null,
      loading: false,
    },
    field: {
      crops: [] as CropTileDto[],
      masterySnapshot: [] as MasterySnapshotDto[],
      activeKcCode: null as string | null,
      loading: false,
      bootstrapped: false,
      lastSignature: null as string | null,
    } as FieldState,
    resources: {
      list: [] as WorkshopResourceListItemDto[],
      current: null as WorkshopResourceDto | null,
      filters: { type: null, kcCode: null } as WorkshopFilters,
      phase: 'idle' as WorkshopRunPhase,
      lastError: null as string | null,
      loading: false,
    } as ResourcesState,
    pathState: {
      active: null as LearningPathDto | null,
      archived: [] as LearningPathDto[],
      phase: 'idle' as PathPhase,
      loading: false,
      lastError: null as string | null,
    } as PathState,
    quests: {
      today: [] as QuestDto[],
      classroom: [] as ClassroomQuestDto[],
      loading: false,
      lastError: null as string | null,
    } as QuestsState,
    training: {
      problem: null as PublicProblemDto | null,
      code: '',
      preview: null as PyodideRunResult | null,
      submission: null as SubmissionDetailDto | null,
      phase: 'idle' as TrainingPhase,
      lastError: null as string | null,
    } as TrainingState,
    bugMine: {
      session: null as BugMineSessionDto | null,
      loading: false,
      lastError: null as string | null,
      lastRetry: null as SubmissionDto | null,
    } as BugMineState,
    evaluation: {
      list: [] as EvaluationDto[],
      current: null as EvaluationDto | null,
      phase: 'idle' as EvaluationPhase,
      loading: false,
      lastError: null as string | null,
    } as EvaluationState,
  }),
  getters: {
    unlockedCrops(): CropTileDto[] {
      return this.field.crops.filter((crop) => crop.unlocked);
    },
    activeKcMastery(): MasterySnapshotDto | null {
      if (!this.field.activeKcCode) {
        return null;
      }
      const code = this.field.activeKcCode;
      return this.field.masterySnapshot.find((entry) => entry.kcCode === code) ?? null;
    },
  },
  actions: {
    async loadProfile(): Promise<void> {
      this.profile = await getLearnerProfile();
    },
    async openInterview(): Promise<void> {
      const response = await openProfileInterview();
      this.profileInterview.sessionId = response.interviewSessionId;
      this.profileInterview.history = response.history;
      this.profile = response.draft;
    },
    async sendTurn(userMessage: string): Promise<void> {
      if (!this.profileInterview.sessionId) {
        await this.openInterview();
      }
      const sessionId = this.profileInterview.sessionId;
      if (!sessionId) {
        throw new Error('Profile interview session was not opened');
      }
      this.profileInterview.loading = true;
      try {
        this.profileInterview.history.push({ role: 'user', content: userMessage });
        const response = await sendProfileTurn(sessionId, { userMessage });
        this.profileInterview.dimensionsFilled = response.dimensionsFilled;
        this.profileInterview.done = response.done;
        this.profileInterview.safety = response.safetyResult;
        this.profileInterview.grounding = response.groundingResult;
        if (response.nextQuestion) {
          this.profileInterview.currentQuestion = response.nextQuestion;
          this.profileInterview.history.push({ role: 'assistant', content: response.nextQuestion });
        }
        if (response.profileDraft) {
          this.profile = response.profileDraft;
        }
      } finally {
        this.profileInterview.loading = false;
      }
    },
    async finishInterview(): Promise<void> {
      if (this.profileInterview.sessionId) {
        await finishProfileInterview(this.profileInterview.sessionId);
      }
    },
    async loadField(): Promise<void> {
      this.field.loading = true;
      try {
        const prevCrops = this.field.crops;
        const wasHydrated = this.field.hydrated;
        const [crops, mastery] = await Promise.all([fetchCrops(), fetchMastery()]);
        this.field.crops = crops;
        this.field.masterySnapshot = mastery;
        this.field.bootstrapped = crops.length > 0;
        this.field.hydrated = true;
        if (wasHydrated && detectNewMature(prevCrops, crops)) {
          celebrateMature(null);
        }
      } finally {
        this.field.loading = false;
      }
    },
    async bootstrapField(): Promise<CurriculumBootstrapResult> {
      const result = await bootstrapCurriculum();
      this.field.lastSignature = result.signature;
      this.field.bootstrapped = true;
      await this.loadField();
      return result;
    },
    selectKc(kcCode: string | null): void {
      this.field.activeKcCode = kcCode;
    },
    async refreshAfterEvent(kcCode: string): Promise<void> {
      const prevCrops = this.field.crops;
      const wasHydrated = this.field.hydrated;
      const [crops, mastery] = await Promise.all([fetchCrops(), fetchMastery()]);
      this.field.crops = crops;
      this.field.masterySnapshot = mastery;
      this.field.hydrated = true;
      if (kcCode) {
        this.field.activeKcCode = kcCode;
      }
      if (wasHydrated && detectNewMature(prevCrops, crops)) {
        celebrateMature(null);
      }
    },
    async loadResources(): Promise<void> {
      this.resources.loading = true;
      try {
        const filters = this.resources.filters;
        const query: {
          type?: WorkshopResourceType;
          kcCode?: string;
          limit: number;
        } = { limit: 30 };
        if (filters.type) {
          query.type = filters.type;
        }
        if (filters.kcCode) {
          query.kcCode = filters.kcCode;
        }
        this.resources.list = await listWorkshopResources(query);
      } finally {
        this.resources.loading = false;
      }
    },
    async createResource(request: WorkshopCreateRequest): Promise<WorkshopCreateResponse> {
      this.resources.phase = 'creating';
      this.resources.lastError = null;
      try {
        const response = await createWorkshopResource(request);
        this.resources.phase = 'success';
        await this.loadResources();
        return response;
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        this.resources.lastError = message;
        this.resources.phase = 'error';
        throw error;
      }
    },
    async openResource(id: number): Promise<WorkshopResourceDto> {
      this.resources.loading = true;
      try {
        const detail = await getWorkshopResource(id);
        this.resources.current = detail;
        return detail;
      } finally {
        this.resources.loading = false;
      }
    },
    async giveResourceFeedback(id: number, request: WorkshopFeedbackRequest): Promise<void> {
      await submitWorkshopFeedback(id, request);
      if (this.resources.current && this.resources.current.id === id) {
        this.resources.current = {
          ...this.resources.current,
          feedbackStatus: request.status,
          feedbackNote: request.note ?? null,
        };
      }
      await this.loadResources();
    },
    setResourceFilter(filters: WorkshopFilters): void {
      this.resources.filters = filters;
    },
    resetResourcePhase(): void {
      this.resources.phase = 'idle';
      this.resources.lastError = null;
    },
    async loadActivePath(): Promise<void> {
      this.pathState.loading = true;
      this.pathState.lastError = null;
      try {
        this.pathState.active = await getActiveLearningPath();
        this.pathState.phase = this.pathState.active ? 'ready' : 'idle';
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        this.pathState.lastError = message;
        this.pathState.phase = 'error';
      } finally {
        this.pathState.loading = false;
      }
    },
    async loadArchivedPaths(): Promise<void> {
      this.pathState.archived = await getArchivedLearningPaths();
    },
    async planPath(): Promise<void> {
      this.pathState.phase = 'planning';
      this.pathState.lastError = null;
      try {
        const response = await planLearningPath();
        this.pathState.active = response.path;
        this.pathState.phase = 'ready';
        await this.loadTodayQuests();
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        this.pathState.lastError = message;
        this.pathState.phase = 'error';
        throw error;
      }
    },
    async loadTodayQuests(): Promise<void> {
      this.quests.loading = true;
      this.quests.lastError = null;
      try {
        this.quests.today = await getTodayQuests();
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        this.quests.lastError = message;
      } finally {
        this.quests.loading = false;
      }
    },
    async loadClassroomQuests(): Promise<void> {
      try {
        this.quests.classroom = await getMyClassroomQuests();
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        this.quests.lastError = message;
      }
    },
    async startQuestById(id: number): Promise<QuestDto> {
      const updated = await startQuest(id);
      this.replaceQuest(updated);
      return updated;
    },
    async completeQuestById(
      id: number,
      request: QuestCompleteRequest = {},
    ): Promise<QuestDto> {
      const updated = await completeQuest(id, request);
      this.replaceQuest(updated);
      return updated;
    },
    async skipQuestById(id: number, request: QuestSkipRequest = {}): Promise<QuestDto> {
      const updated = await skipQuest(id, request);
      this.removeQuest(updated.id);
      return updated;
    },
    replaceQuest(quest: QuestDto): void {
      const index = this.quests.today.findIndex((entry) => entry.id === quest.id);
      if (index === -1) {
        if (quest.status === 'OPEN' || quest.status === 'IN_PROGRESS') {
          this.quests.today = [...this.quests.today, quest];
        }
        return;
      }
      if (quest.status === 'COMPLETED' || quest.status === 'EXPIRED') {
        this.quests.today = this.quests.today.filter((entry) => entry.id !== quest.id);
        return;
      }
      const next = [...this.quests.today];
      next[index] = quest;
      this.quests.today = next;
    },
    removeQuest(id: number): void {
      this.quests.today = this.quests.today.filter((entry) => entry.id !== id);
    },
    async loadTrainingProblem(problemId: number): Promise<void> {
      this.training.lastError = null;
      try {
        const problem = await getPublicProblem(problemId);
        this.training.problem = problem;
        this.training.preview = null;
        this.training.submission = null;
        this.training.phase = 'idle';
        if (!this.training.code) {
          this.training.code = '# 在这里编写 Python 代码\n';
        }
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        this.training.lastError = message;
        this.training.phase = 'error';
      }
    },
    setTrainingCode(code: string): void {
      this.training.code = code;
    },
    async runPyodidePreview(stdin: string = ''): Promise<void> {
      this.training.phase = 'previewing';
      this.training.lastError = null;
      try {
        const runner = getPyodideRunner();
        await runner.init();
        const result = await runner.run(this.training.code, stdin);
        this.training.preview = result;
        this.training.phase = 'done';
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        this.training.lastError = message;
        this.training.phase = 'error';
      }
    },
    async submitToServer(payload: SubmissionCreateRequest): Promise<SubmissionDetailDto | null> {
      this.training.phase = 'submitting';
      this.training.lastError = null;
      try {
        const created = await createSubmission(payload);
        const detail = await getSubmission(created.id);
        this.training.submission = detail;
        this.training.phase = 'done';
        await this.loadTodayQuests();
        return detail;
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        this.training.lastError = message;
        this.training.phase = 'error';
        return null;
      }
    },
    async openBugMine(submissionId: number): Promise<BugMineSessionDto | null> {
      this.bugMine.loading = true;
      this.bugMine.lastError = null;
      try {
        const session = await openBugMineSession({ submissionId });
        this.bugMine.session = session;
        return session;
      } catch (error) {
        this.bugMine.lastError = error instanceof Error ? error.message : String(error);
        return null;
      } finally {
        this.bugMine.loading = false;
      }
    },
    async loadBugMine(sessionId: number): Promise<void> {
      this.bugMine.loading = true;
      this.bugMine.lastError = null;
      try {
        this.bugMine.session = await getBugMineSession(sessionId);
      } catch (error) {
        this.bugMine.lastError = error instanceof Error ? error.message : String(error);
      } finally {
        this.bugMine.loading = false;
      }
    },
    async revealBugMineCardById(cardId: number): Promise<void> {
      if (!this.bugMine.session) return;
      const sessionId = this.bugMine.session.id;
      const updated = await revealBugMineCard(sessionId, cardId);
      this.bugMine.session = {
        ...this.bugMine.session,
        cards: this.bugMine.session.cards.map((card) =>
          card.id === updated.id ? { ...card, revealed: true } : card,
        ),
      };
    },
    async loadEvaluations(limit = 4): Promise<void> {
      this.evaluation.loading = true;
      this.evaluation.lastError = null;
      try {
        this.evaluation.list = await listEvaluations(limit);
      } catch (error) {
        this.evaluation.lastError = error instanceof Error ? error.message : String(error);
      } finally {
        this.evaluation.loading = false;
      }
    },
    async openEvaluation(id: number): Promise<EvaluationDto | null> {
      this.evaluation.loading = true;
      this.evaluation.lastError = null;
      try {
        const detail = await getEvaluation(id);
        this.evaluation.current = detail;
        return detail;
      } catch (error) {
        this.evaluation.lastError = error instanceof Error ? error.message : String(error);
        return null;
      } finally {
        this.evaluation.loading = false;
      }
    },
    async runEvaluationNow(request: EvaluationRequest = {}): Promise<EvaluationDto | null> {
      this.evaluation.phase = 'running';
      this.evaluation.lastError = null;
      try {
        const result = await runEvaluation(request);
        this.evaluation.current = result;
        this.evaluation.list = [result, ...this.evaluation.list.filter((entry) => entry.id !== result.id)].slice(0, 8);
        this.evaluation.phase = 'ready';
        return result;
      } catch (error) {
        this.evaluation.lastError = error instanceof Error ? error.message : String(error);
        this.evaluation.phase = 'error';
        return null;
      }
    },
    async acceptEvaluationPath(id: number): Promise<EvaluationDto | null> {
      this.evaluation.loading = true;
      try {
        const updated = await acceptEvaluationPath(id);
        this.evaluation.current = updated;
        this.evaluation.list = this.evaluation.list.map((entry) =>
          entry.id === updated.id ? updated : entry,
        );
        await this.loadActivePath();
        await this.loadTodayQuests();
        return updated;
      } catch (error) {
        this.evaluation.lastError = error instanceof Error ? error.message : String(error);
        return null;
      } finally {
        this.evaluation.loading = false;
      }
    },
    async keepCurrentEvaluationPath(id: number): Promise<EvaluationDto | null> {
      this.evaluation.loading = true;
      try {
        const updated = await keepOldEvaluationPath(id);
        this.evaluation.current = updated;
        this.evaluation.list = this.evaluation.list.map((entry) =>
          entry.id === updated.id ? updated : entry,
        );
        return updated;
      } catch (error) {
        this.evaluation.lastError = error instanceof Error ? error.message : String(error);
        return null;
      } finally {
        this.evaluation.loading = false;
      }
    },
    async retryBugMine(code: string): Promise<SubmissionDto | null> {
      if (!this.bugMine.session) return null;
      this.bugMine.loading = true;
      this.bugMine.lastError = null;
      try {
        const result = await retryBugMineSubmit(this.bugMine.session.id, { code });
        this.bugMine.lastRetry = result;
        const session = await getBugMineSession(this.bugMine.session.id);
        this.bugMine.session = session;
        if (result.status === 'PASSED') {
          await this.loadTodayQuests();
        }
        return result;
      } catch (error) {
        this.bugMine.lastError = error instanceof Error ? error.message : String(error);
        return null;
      } finally {
        this.bugMine.loading = false;
      }
    },
  },
});
