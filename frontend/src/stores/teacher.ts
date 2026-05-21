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
      this.currentId = classroomId;
      this.current.classroom =
        this.classrooms.find((c) => c.classroomId === classroomId) ?? null;
      if (!this.current.classroom) {
        await this.loadClassrooms();
        this.current.classroom =
          this.classrooms.find((c) => c.classroomId === classroomId) ?? null;
      }
      await this.refreshDetail();
    },
    async refreshDetail(): Promise<void> {
      if (!this.currentId) return;
      const id = this.currentId;
      this.current.members = await listClassroomMembersByStatus(id);
      this.current.quests = await listClassroomQuestsForTeacher(id);
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
        this.current.dashboard.kcHeatmap = heatmap;
        this.current.dashboard.questSummary = summary;
        this.current.dashboard.errorHotspots = hotspots;
        this.current.dashboard.resourceUsage = usage;
        this.current.dashboard.recentActivity = activity;
      } catch (error) {
        this.current.dashboard.lastError = errorMessage(error);
      } finally {
        this.current.dashboard.loading = false;
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
