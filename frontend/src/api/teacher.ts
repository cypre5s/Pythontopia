import { http } from './http';
import type { ClassroomDto } from './dto/ClassroomDto';
import type { ClassroomMemberDto } from './dto/ClassroomMemberDto';
import type {
  ClassroomQuestCreateRequest,
  ClassroomQuestDto,
  ClassroomQuestPublishResponse,
} from './dto/ClassroomQuest';
import type {
  ErrorHotspotDto,
  KcHeatmapEntryDto,
  QuestCompletionDto,
  RecentActivityDto,
  ResourceUsageDto,
} from './dto/TeacherDashboard';

export interface UpdateClassroomRequest {
  name: string;
  description?: string;
}

export async function updateClassroom(
  classroomId: number,
  request: UpdateClassroomRequest,
): Promise<ClassroomDto> {
  const response = await http.put<ClassroomDto>(
    `/api/teacher/classrooms/${classroomId}`,
    request,
  );
  return response.data;
}

export async function regenerateClassroomJoinCode(classroomId: number): Promise<ClassroomDto> {
  const response = await http.post<ClassroomDto>(
    `/api/teacher/classrooms/${classroomId}/regenerate-code`,
  );
  return response.data;
}

export async function listClassroomMembersByStatus(
  classroomId: number,
  status?: 'ACTIVE' | 'REMOVED',
): Promise<ClassroomMemberDto[]> {
  const response = await http.get<ClassroomMemberDto[]>(
    `/api/teacher/classrooms/${classroomId}/members`,
    { params: status ? { status } : {} },
  );
  return response.data;
}

export async function publishClassroomQuest(
  classroomId: number,
  request: ClassroomQuestCreateRequest,
): Promise<ClassroomQuestPublishResponse> {
  const response = await http.post<ClassroomQuestPublishResponse>(
    `/api/teacher/classrooms/${classroomId}/quests`,
    request,
  );
  return response.data;
}

export async function listClassroomQuestsForTeacher(
  classroomId: number,
): Promise<ClassroomQuestDto[]> {
  const response = await http.get<ClassroomQuestDto[]>(
    `/api/teacher/classrooms/${classroomId}/quests`,
  );
  return response.data;
}

export async function archiveClassroomQuest(
  classroomId: number,
  questId: number,
): Promise<ClassroomQuestDto> {
  const response = await http.post<ClassroomQuestDto>(
    `/api/teacher/classrooms/${classroomId}/quests/${questId}/archive`,
  );
  return response.data;
}

export async function fetchKcHeatmap(classroomId: number): Promise<KcHeatmapEntryDto[]> {
  const response = await http.get<KcHeatmapEntryDto[]>(
    `/api/teacher/classrooms/${classroomId}/dashboard/kc-heatmap`,
  );
  return response.data;
}

export async function fetchQuestSummary(classroomId: number): Promise<QuestCompletionDto[]> {
  const response = await http.get<QuestCompletionDto[]>(
    `/api/teacher/classrooms/${classroomId}/dashboard/quest-summary`,
  );
  return response.data;
}

export async function fetchErrorHotspots(classroomId: number): Promise<ErrorHotspotDto[]> {
  const response = await http.get<ErrorHotspotDto[]>(
    `/api/teacher/classrooms/${classroomId}/dashboard/error-hotspots`,
  );
  return response.data;
}

export async function fetchResourceUsage(classroomId: number): Promise<ResourceUsageDto> {
  const response = await http.get<ResourceUsageDto>(
    `/api/teacher/classrooms/${classroomId}/dashboard/resource-usage`,
  );
  return response.data;
}

export async function fetchRecentActivity(
  classroomId: number,
  limit = 20,
): Promise<RecentActivityDto[]> {
  const response = await http.get<RecentActivityDto[]>(
    `/api/teacher/classrooms/${classroomId}/dashboard/recent-activity`,
    { params: { limit } },
  );
  return response.data;
}

/** 单个薄弱 KC 的补救包：针对该 KC 生成的补救资源 + 命中的课件证据页。 */
export interface RemediationPackDto {
  kcCode: string;
  kcName: string;
  resourceIds: number[];
  evidencePageIds: number[];
}

/** 班级「一键补救」结果：全班薄弱 KC 数 + 每个薄弱 KC 的补救包。 */
export interface RemediationResultDto {
  classroomId: number;
  weakKcCount: number;
  packs: RemediationPackDto[];
}

/**
 * 一键生成班级补救包：基于全班薄弱 KC 为每个薄弱点生成针对性补救资源。
 * 契约：POST /api/teacher/classrooms/{classroomId}/remediation（教师 JWT）。
 */
export async function triggerRemediation(
  classroomId: number,
): Promise<RemediationResultDto> {
  const response = await http.post<RemediationResultDto>(
    `/api/teacher/classrooms/${classroomId}/remediation`,
  );
  return response.data;
}
