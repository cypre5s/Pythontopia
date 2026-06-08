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
  listSubmissions,
} from '@/api/submissions';
import { getPublicProblem } from '@/api/problems';
import { getPyodideRunner, type PyodideRunResult } from '@/game/adapters/pyodide';
import {
  openBugMineSession,
  getBugMineErrorBook,
  getBugMineEchoes,
  getBugMineSession,
  openBugMineEcho,
  revealBugMineCard,
  retryBugMineSubmit,
  variantBugMineSubmit,
} from '@/api/bugMine';
import {
  acceptEvaluationPath,
  getEvaluation,
  keepOldEvaluationPath,
  listEvaluations,
  runEvaluation,
} from '@/api/evaluation';
import type { CurriculumBootstrapResult } from '@/api/dto/CurriculumBootstrapResult';
import type { LearnerProfileDto } from '@/api/dto/LearnerProfileDto';
import type { MasterySnapshotDto } from '@/api/dto/MasterySnapshotDto';
import type {
  PublicProblemDto,
  SubmissionCreateRequest,
  SubmissionDetailDto,
  SubmissionDto,
} from '@/api/dto/Submission';
import type {
  BugMineEchoCandidateDto,
  BugMineErrorBookItemDto,
  BugMineSessionDto,
} from '@/api/dto/BugMine';
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
  masterySnapshot: MasterySnapshotDto[];
  activeKcCode: string | null;
  loading: boolean;
  bootstrapped: boolean;
  hydrated: boolean;
  lastSignature: string | null;
}

// 训练真题判定通过后，推进主线 submission 子任务。
// 用动态 import 避免 learning ↔ mainQuest 循环依赖（mainQuest 已静态 import learning）。
function notifyMainQuestSubmission(payload: {
  problemId?: number | string;
  kcCode?: string;
}): void {
  if (typeof window === 'undefined') return;
  void import('@stores/mainQuest').then((mod) => {
    try {
      mod.useMainQuestStore().onSubmissionPassed(payload);
    } catch {
      /* mainQuest store 不可用时静默 */
    }
  });
}

// 一次服务端判题（无论 AC/WA）后刷新世界情绪氛围——连挫触发细雨+助教台阶、逆袭回暖到晴。
// 用动态 import 避免 learning ↔ mood 潜在耦合；mood store refresh 内部 try/finally 不抛错、
// 失败降级 sunny，绝不阻断提交结果展示。
function notifyMoodRefresh(): void {
  if (typeof window === 'undefined') return;
  void import('@stores/mood').then((mod) => {
    try {
      void mod.useMoodStore().refresh();
    } catch {
      /* mood store 不可用时静默 */
    }
  });
}

// 练习失败后请求 fail_react 探针（emotion_persistence 行为信号）。
// 动态 import 解耦，避免 learning ↔ probe 静态互依；probe store 仅置 requested 标记，
// 由世界内 ProbeCard 监听弹出（失败发生在训练页、探针卡在世界页，借 store 跨页传信号）。
// fire-and-forget、失败静默，绝不阻塞提交结果展示。
function notifyProbeRequest(probeKey: string): void {
  if (typeof window === 'undefined') return;
  void import('@stores/probe').then((mod) => {
    try {
      mod.useProbeStore().request(probeKey);
    } catch {
      /* probe store 不可用时静默 */
    }
  });
}

// 掌握度快照刷新后，联动主线 kc_mastery「测」子任务。
// 后端 masteryScore 为 0..1；onMasteryReached 阈值（mainQuests thresholdScore）为 0..100，
// 故乘 100 归一到同一标度后再比较。
function notifyMainQuestMastery(
  snapshots: readonly MasterySnapshotDto[],
): void {
  if (typeof window === 'undefined') return;
  void import('@stores/mainQuest').then((mod) => {
    try {
      const mq = mod.useMainQuestStore();
      for (const snap of snapshots) {
        mq.onMasteryReached(snap.kcCode, Math.round((snap.masteryScore ?? 0) * 100));
      }
    } catch {
      /* mainQuest store 不可用时静默 */
    }
  });
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
  /** 当前会话对应的完整题干（按 session.problemId 独立拉取，与训练页 training.problem 解耦）。 */
  problem: PublicProblemDto | null;
  problemLoading: boolean;
  problemError: string | null;
  errorBook: BugMineErrorBookItemDto[];
  errorBookLoading: boolean;
  errorBookError: string | null;
  echoes: BugMineEchoCandidateDto[];
  echoesLoading: boolean;
  echoesError: string | null;
  pendingVariantSessionId: number | null;
  loading: boolean;
  lastError: string | null;
  lastRetry: SubmissionDto | null;
  lastVariant: SubmissionDto | null;
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
      problem: null as PublicProblemDto | null,
      problemLoading: false,
      problemError: null as string | null,
      errorBook: [] as BugMineErrorBookItemDto[],
      errorBookLoading: false,
      errorBookError: null as string | null,
      echoes: [] as BugMineEchoCandidateDto[],
      echoesLoading: false,
      echoesError: null as string | null,
      pendingVariantSessionId: null as number | null,
      loading: false,
      lastError: null as string | null,
      lastRetry: null as SubmissionDto | null,
      lastVariant: null as SubmissionDto | null,
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
        // 先快照本轮之前的多轮历史（用于给 ProfileAgent 多轮记忆），再把当前消息加入本地展示。
        const priorHistory = [...this.profileInterview.history];
        this.profileInterview.history.push({ role: 'user', content: userMessage });
        const response = await sendProfileTurn(sessionId, { userMessage, history: priorHistory });
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
      await this.loadProfile();
      this.profileInterview.done = true;
    },
    async loadField(): Promise<void> {
      this.field.loading = true;
      try {
        const mastery = await fetchMastery();
        this.field.masterySnapshot = mastery;
        this.field.bootstrapped = mastery.length > 0;
        this.field.hydrated = true;
        notifyMainQuestMastery(mastery);
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
    /** `selectKc` 的兼容别名：部分老组件按 `setFieldActiveKc` 命名调用。 */
    setFieldActiveKc(kcCode: string | null): void {
      this.field.activeKcCode = kcCode;
    },
    async refreshAfterEvent(kcCode: string): Promise<void> {
      const mastery = await fetchMastery();
      this.field.masterySnapshot = mastery;
      this.field.hydrated = true;
      if (kcCode) {
        this.field.activeKcCode = kcCode;
      }
      notifyMainQuestMastery(mastery);
    },
    /**
     * 由 world store 的 `bootstrap()` 一次性下发后调用，避免页面挂载时 N 次串行 GET。
     * 各字段都是可选：后端在某些场景下（例如老用户没有 path）会留空，
     * 此时保持现有 state 不变，等业务侧再触发对应 lazy load。
     */
    setBootstrapData(payload: {
      quests?: QuestDto[];
      mastery?: MasterySnapshotDto[];
      profile?: LearnerProfileDto | null;
      path?: LearningPathDto | null;
    }): void {
      if (payload.mastery) {
        this.field.masterySnapshot = payload.mastery;
        notifyMainQuestMastery(payload.mastery);
      }
      if (payload.profile !== undefined) {
        this.profile = payload.profile;
      }
      if (payload.path !== undefined) {
        this.pathState.active = payload.path;
        this.pathState.phase = payload.path ? 'ready' : 'idle';
      }
      if (payload.quests) {
        this.quests.today = payload.quests;
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
        // 触发主线 evidence_type='resource_consumed' 检测。
        if (typeof window !== 'undefined') {
          void import('@stores/mainQuest').then((mod) => {
            try {
              mod.useMainQuestStore().onResourceCreated(request.type, request.kcCodes);
            } catch {
              // mainQuest store 不可用时静默
            }
          });
        }
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
    async listRecentPassedSubmissions(
      problemId: number,
      limit = 10,
    ): Promise<SubmissionDto[]> {
      const submissions = await listSubmissions({
        problemId,
        status: 'PASSED',
        limit,
      });
      return submissions;
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
        // 判题结果回来即刷新情绪氛围（streak 同时计入 AC/WA，故无论通过与否都刷）。
        notifyMoodRefresh();
        await this.loadTodayQuests();
        // 真题判定通过 → 推进主线「练」子任务，并刷新掌握度联动「测」子任务。
        const passed = detail.judgeResult?.verdict === 'AC';
        if (passed) {
          const kcCode = this.training.problem?.kcCode;
          notifyMainQuestSubmission(
            kcCode ? { problemId: payload.problemId, kcCode } : { problemId: payload.problemId },
          );
          try {
            // 后端在 AC 时 +0.20 掌握度；刷新快照后 refreshAfterEvent 会触发 kc_mastery 联动。
            await this.refreshAfterEvent(kcCode ?? '');
          } catch {
            /* 掌握度刷新失败不阻断提交结果展示 */
          }
        } else if (
          // 服务端判题为「失败」（FAILED/ERROR 或有非 AC 判定）→ 请求 fail_react
          // 探针。仅置标记、不阻塞；排除 PENDING/RUNNING（尚未判定）误触。
          detail.submission.status === 'FAILED' ||
          detail.submission.status === 'ERROR' ||
          (detail.judgeResult !== null && detail.judgeResult.verdict !== 'AC')
        ) {
          notifyProbeRequest('fail_react');
        }
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
        await this.loadBugMineErrorBook();
        return session;
      } catch (error) {
        this.bugMine.lastError = error instanceof Error ? error.message : String(error);
        return null;
      } finally {
        this.bugMine.loading = false;
      }
    },
    async loadBugMineProblem(problemId: number): Promise<void> {
      // 矿洞会话的「题干上下文中心」：拉取完整 PublicProblemDto（含样例），
      // 失败不阻断会话调试，仅记录 problemError 由面板降级展示。
      this.bugMine.problemLoading = true;
      this.bugMine.problemError = null;
      try {
        this.bugMine.problem = await getPublicProblem(problemId);
      } catch (error) {
        this.bugMine.problemError = error instanceof Error ? error.message : String(error);
        this.bugMine.problem = null;
      } finally {
        this.bugMine.problemLoading = false;
      }
    },
    async loadBugMineErrorBook(): Promise<void> {
      this.bugMine.errorBookLoading = true;
      this.bugMine.errorBookError = null;
      try {
        this.bugMine.errorBook = await getBugMineErrorBook();
      } catch (error) {
        this.bugMine.errorBookError = error instanceof Error ? error.message : String(error);
      } finally {
        this.bugMine.errorBookLoading = false;
      }
    },
    async loadBugMineEchoes(): Promise<void> {
      this.bugMine.echoesLoading = true;
      this.bugMine.echoesError = null;
      try {
        this.bugMine.echoes = await getBugMineEchoes();
      } catch (error) {
        this.bugMine.echoesError = error instanceof Error ? error.message : String(error);
      } finally {
        this.bugMine.echoesLoading = false;
      }
    },
    async openBugMineEchoByKc(kcCode: string): Promise<BugMineSessionDto | null> {
      this.bugMine.loading = true;
      this.bugMine.lastError = null;
      try {
        const session = await openBugMineEcho({ kcCode });
        this.bugMine.session = session;
        await this.loadBugMineErrorBook();
        await this.loadBugMineEchoes();
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
      await revealBugMineCard(sessionId, cardId);
      this.bugMine.session = await getBugMineSession(sessionId);
      await this.loadBugMineErrorBook();
    },
    setPendingVariantSession(sessionId: number | null): void {
      this.bugMine.pendingVariantSessionId = sessionId;
    },
    async submitBugMineVariant(code: string, sessionId?: number): Promise<SubmissionDto | null> {
      const targetSessionId = sessionId ?? this.bugMine.pendingVariantSessionId ?? this.bugMine.session?.id;
      if (targetSessionId == null) return null;
      this.bugMine.loading = true;
      this.bugMine.lastError = null;
      try {
        const result = await variantBugMineSubmit(targetSessionId, { code });
        this.bugMine.lastVariant = result;
        this.bugMine.session = await getBugMineSession(targetSessionId);
        await this.loadBugMineErrorBook();
        if (result.status === 'PASSED') {
          this.bugMine.pendingVariantSessionId = null;
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
        await this.loadBugMineErrorBook();
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

    streamCreateResource(_request: WorkshopCreateRequest): void {
      this.resources.phase = 'creating';
      this.resources.lastError = null;
    },

    /**
     * KC mastery 自然衰减 — 模拟学习遗忘曲线。
     * sleep 推进 1 天时调用，每个已掌握的 KC mastery 减少 decayAmount。
     * 下限 clamp 到 0，不会变成负数。
     */
    applyMasteryDecay(decayAmount: number): void {
      if (decayAmount <= 0) return;
      this.field.masterySnapshot = this.field.masterySnapshot.map((entry) => ({
        ...entry,
        masteryScore: Math.max(0, entry.masteryScore - decayAmount),
      }));
    },
  },
});
