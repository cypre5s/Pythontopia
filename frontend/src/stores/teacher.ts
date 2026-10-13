import { defineStore } from 'pinia';
import {
  archiveClassroomQuest,
  fetchErrorHotspots,
  fetchKcHeatmap,
  fetchQuestSummary,
  fetchRecentActivity,
  fetchResourceUsage,
  listClassroomMembersByStatus,
  listClassroomQuestsForTeacher,
  publishClassroomQuest,
  regenerateClassroomJoinCode,
  updateClassroom,
  type UpdateClassroomRequest,
} from '@/api/teacher';
import {
  createClassroom,
  listMyClassrooms,
  removeClassroomMember,
  type CreateClassroomRequest,
} from '@/api/classroom';
import type { ClassroomDto } from '@/api/dto/ClassroomDto';
import type { ClassroomMemberDto } from '@/api/dto/ClassroomMemberDto';
import type {
  ClassroomQuestCreateRequest,
  ClassroomQuestDto,
  ClassroomQuestPublishResponse,
} from '@/api/dto/ClassroomQuest';
import type {
  ErrorHotspotDto,
  KcHeatmapEntryDto,
  QuestCompletionDto,
  RecentActivityDto,
  ResourceUsageDto,
} from '@/api/dto/TeacherDashboard';

export interface TeacherDashboardState {
  kcHeatmap: KcHeatmapEntryDto[];
  questSummary: QuestCompletionDto[];
  errorHotspots: ErrorHotspotDto[];
  resourceUsage: ResourceUsageDto | null;
  recentActivity: RecentActivityDto[];
  loading: boolean;
  lastError: string | null;
}

export interface TeacherClassroomDetailState {
  classroom: ClassroomDto | null;
  members: ClassroomMemberDto[];
  quests: ClassroomQuestDto[];
  dashboard: TeacherDashboardState;
  /** 班级级（成员/任务）加载错误。为 null 表示这一层没有出错。 */
  lastError: string | null;
  /** 班级级数据是否在飞行中。 */
  loading: boolean;
}

export const useTeacherStore = defineStore('teacher', {
  state: () => ({
    classrooms: [] as ClassroomDto[],
    loading: false,
    lastError: null as string | null,
    currentId: null as number | null,
    current: {
      classroom: null,
      members: [],
      quests: [],
      lastError: null,
      loading: false,
      dashboard: {
        kcHeatmap: [],
        questSummary: [],
        errorHotspots: [],
        resourceUsage: null,
        recentActivity: [],
        loading: false,
        lastError: null,
      },
    } as TeacherClassroomDetailState,
    lastPublishResponse: null as ClassroomQuestPublishResponse | null,
  }),
  getters: {
    hasClassrooms(): boolean {
      return this.classrooms.length > 0;
    },
  },
  actions: {
    async loadClassrooms(): Promise<void> {
      this.loading = true;
      this.lastError = null;
      try {
        this.classrooms = await listMyClassrooms();
      } catch (error) {
        this.lastError = errorMessage(error);
      } finally {
        this.loading = false;
      }
    },
    async createClassroomEntry(request: CreateClassroomRequest): Promise<ClassroomDto> {
      const created = await createClassroom(request);
      this.classrooms = [...this.classrooms, created];
      return created;
    },
    async updateClassroomEntry(
      classroomId: number,
      request: UpdateClassroomRequest,
    ): Promise<ClassroomDto> {
      const updated = await updateClassroom(classroomId, request);
      this.replaceClassroom(updated);
      if (this.current.classroom?.classroomId === classroomId) {
        this.current.classroom = updated;
      }
      return updated;
    },
    async regenerateJoinCode(classroomId: number): Promise<ClassroomDto> {
      const updated = await regenerateClassroomJoinCode(classroomId);
      this.replaceClassroom(updated);
      if (this.current.classroom?.classroomId === classroomId) {
        this.current.classroom = updated;
      }
      return updated;
    },
    async openClassroom(classroomId: number): Promise<void> {
      if (this.currentId !== classroomId) {
        // 清除旧班级快照，避免切换期间或新班看板失败后把旧数据显示在新班下。
        this.current.members = [];
        this.current.quests = [];
        this.current.lastError = null;
        this.current.loading = false;
        this.current.dashboard = {
          kcHeatmap: [],
          questSummary: [],
          errorHotspots: [],
          resourceUsage: null,
          recentActivity: [],
          loading: false,
          lastError: null,
        };
      }
      this.currentId = classroomId;
      this.current.classroom =
        this.classrooms.find((c) => c.classroomId === classroomId) ?? null;
      if (!this.current.classroom) {
        await this.loadClassrooms();
        if (this.currentId !== classroomId) return;
        this.current.classroom =
          this.classrooms.find((c) => c.classroomId === classroomId) ?? null;
      }
      if (this.currentId !== classroomId) return;
      await this.refreshDetail();
    },
    /**
     * 刷新班级级数据（成员 + 任务）。
     *
     * 这里必须自己吞掉异常并写 `current.lastError`：调用方 ClassroomDetail 只有一条
     * `await openClassroom() → await loadDashboard()` 的顺序链，一旦这里抛出（403 / 网络），
     * loadDashboard 永远不会执行，`dashboard.loading` 和 `dashboard.lastError` 都保持初值，
     * 页面上两个 banner 一个都不显示，侧栏永久停在「正在加载...」。
     */
    async refreshDetail(): Promise<void> {
      if (!this.currentId) return;
      const id = this.currentId;
      this.current.loading = true;
      this.current.lastError = null;
      try {
        const [members, quests] = await Promise.all([
          listClassroomMembersByStatus(id),
          listClassroomQuestsForTeacher(id),
        ]);
        if (this.currentId !== id) return;
        this.current.members = members;
        this.current.quests = quests;
      } catch (error) {
        if (this.currentId !== id) return;
        this.current.lastError = errorMessage(error);
        this.current.members = [];
        this.current.quests = [];
      } finally {
        if (this.currentId === id) this.current.loading = false;
      }
    },
    /** 班级详情页的统一重试入口：班级级 + 看板并列重跑，互不阻塞。 */
    async reloadCurrent(classroomId: number): Promise<void> {
      await Promise.allSettled([
        this.openClassroom(classroomId),
        this.loadDashboard(classroomId),
      ]);
    },
    async publishQuest(
      classroomId: number,
      request: ClassroomQuestCreateRequest,
    ): Promise<ClassroomQuestPublishResponse> {
      const response = await publishClassroomQuest(classroomId, request);
      this.lastPublishResponse = response;
      if (this.currentId === classroomId) {
        await this.refreshDetail();
      }
      return response;
    },
    async archiveQuest(classroomId: number, questId: number): Promise<ClassroomQuestDto> {
      const archived = await archiveClassroomQuest(classroomId, questId);
      if (this.currentId === classroomId) {
        await this.refreshDetail();
      }
      return archived;
    },
    async removeMember(classroomId: number, studentId: number): Promise<void> {
      await removeClassroomMember(classroomId, studentId);
      if (this.currentId === classroomId) {
        await this.refreshDetail();
      }
    },
    async loadDashboard(classroomId: number): Promise<void> {
      const selectedId = this.currentId;
      const stillSelected = () =>
        this.currentId === selectedId && (selectedId === null || selectedId === classroomId);
      this.current.dashboard.loading = true;
      this.current.dashboard.lastError = null;
      try {
        const [heatmap, summary, hotspots, usage, activity] = await Promise.all([
          fetchKcHeatmap(classroomId),
          fetchQuestSummary(classroomId),
          fetchErrorHotspots(classroomId),
          fetchResourceUsage(classroomId),
          fetchRecentActivity(classroomId, 20),
        ]);
        if (!stillSelected()) return;
        this.current.dashboard.kcHeatmap = heatmap;
        this.current.dashboard.questSummary = summary;
        this.current.dashboard.errorHotspots = hotspots;
        this.current.dashboard.resourceUsage = usage;
        this.current.dashboard.recentActivity = activity;
      } catch (error) {
        if (!stillSelected()) return;
        this.current.dashboard.lastError = errorMessage(error);
      } finally {
        if (stillSelected()) this.current.dashboard.loading = false;
      }
    },
    replaceClassroom(updated: ClassroomDto): void {
      this.classrooms = this.classrooms.map((c) =>
        c.classroomId === updated.classroomId ? updated : c,
      );
    },
  },
});

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
