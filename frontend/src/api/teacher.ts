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
    undefined,
    { timeout: 180_000 },
  );
  return response.data;
}

// ───────────────────────────────────────────────────────────────────────────
// R-15 主线教师看板（§9 的 4 个新端点）。DTO 与后端
// com.pythontopia.classroom.dashboard.dto.* 严格对齐；列/表口径见各端点后端注释。
// ───────────────────────────────────────────────────────────────────────────

/** 单章星级人数分布（0/1/2/3★ 各档人数）+ 平均星级。 */
export interface MainlineChapterStarRow {
  chapter: number;
  zeroStar: number;
  oneStar: number;
  twoStar: number;
  threeStar: number;
  avgStars: number;
}

/** 总星排行项：跨 7 章累计星数 + 已得星章节数。 */
export interface StudentStarTotalDto {
  studentId: number;
  displayName: string;
  totalStars: number;
  starredChapters: number;
}

/** GET /dashboard/mainline-stars：每章 0/1/2/3★ 人数矩阵 + 总星 TopN。 */
export interface MainlineStarMatrixDto {
  totalStudents: number;
  chapters: MainlineChapterStarRow[];
  topStudents: StudentStarTotalDto[];
}

/** 箱线五数概括（基于每生最高分）；无数据时数值字段为 null、count=0。 */
export interface ChapterTestScoreDistribution {
  count: number;
  min: number | null;
  q1: number | null;
  median: number | null;
  q3: number | null;
  max: number | null;
}

/** 单章章测统计：参试人数 / 提交次数 / 通过人数 / 通过率 / 分数箱线分布。 */
export interface ChapterTestRowDto {
  chapter: number;
  participantCount: number;
  attemptCount: number;
  passedCount: number;
  passRate: number;
  scoreDistribution: ChapterTestScoreDistribution;
}

/** GET /dashboard/chapter-tests：每章参试数/通过率/分数分布(箱线)。 */
export interface ChapterTestStatsDto {
  totalStudents: number;
  chapters: ChapterTestRowDto[];
}

/** 单日无尽补星活跃：达标补星轮次 + 发放补星数（口径相同）。 */
export interface EndlessDailyDto {
  day: string;
  recoveryRounds: number;
  recoveryStars: number;
}

/** 单章无尽补星活跃：达标补星轮次 + 发放补星数 + 参与人数。 */
export interface EndlessChapterDto {
  chapter: number;
  recoveryRounds: number;
  recoveryStars: number;
  studentCount: number;
}

/**
 * GET /dashboard/endless-activity：近 7 日无尽补星轮次/补星数/活跃人数。
 * 口径见后端 EndlessActivityDto：源自 star_award_event(ENDLESS_RECOVERY)，每事件 = 一次达标补星轮次；
 * accuracyPct 在「无尽轮次明细表」落地前固定为 null（§3.2 暂未定义该表）。
 */
export interface EndlessActivityDto {
  periodStart: string;
  periodEnd: string;
  totalStudents: number;
  activeStudentCount: number;
  recoveryRoundTotal: number;
  recoveryStarTotal: number;
  accuracyPct: number | null;
  daily: EndlessDailyDto[];
  chapters: EndlessChapterDto[];
}

/** 单章 stage 总数（来自 mainline_stage 静态定义）。 */
export interface MainlineChapterStageCount {
  chapter: number;
  stageCount: number;
}

/** 成员在某章的完成度单元格：已完成 stage / 该章 stage 总数。 */
export interface MainlineChapterCell {
  chapter: number;
  completedStages: number;
  totalStages: number;
}

/** 单个成员的主线进度行：跨章完成 stage 合计 + 累计星数 + 逐章完成度单元格。 */
export interface MainlineMemberProgressRow {
  studentId: number;
  displayName: string;
  completedStages: number;
  totalStars: number;
  chapters: MainlineChapterCell[];
}

/** GET /dashboard/mainline-progress：成员×章节 stage 完成度矩阵。 */
export interface MainlineProgressMatrixDto {
  totalStudents: number;
  totalStages: number;
  chapterStageCounts: MainlineChapterStageCount[];
  members: MainlineMemberProgressRow[];
}

/** 每章 0/1/2/3★ 人数矩阵 + 总星 TopN。 */
export async function fetchMainlineStars(classroomId: number): Promise<MainlineStarMatrixDto> {
  const response = await http.get<MainlineStarMatrixDto>(
    `/api/teacher/classrooms/${classroomId}/dashboard/mainline-stars`,
  );
  return response.data;
}

/** 每章参试数/通过率/分数分布(箱线)。 */
export async function fetchChapterTests(classroomId: number): Promise<ChapterTestStatsDto> {
  const response = await http.get<ChapterTestStatsDto>(
    `/api/teacher/classrooms/${classroomId}/dashboard/chapter-tests`,
  );
  return response.data;
}

/** 近 7 日无尽轮次/正确率/补星数。 */
export async function fetchEndlessActivity(classroomId: number): Promise<EndlessActivityDto> {
  const response = await http.get<EndlessActivityDto>(
    `/api/teacher/classrooms/${classroomId}/dashboard/endless-activity`,
  );
  return response.data;
}

/** 成员×章节 stage 完成度矩阵。 */
export async function fetchMainlineProgress(
  classroomId: number,
): Promise<MainlineProgressMatrixDto> {
  const response = await http.get<MainlineProgressMatrixDto>(
    `/api/teacher/classrooms/${classroomId}/dashboard/mainline-progress`,
  );
  return response.data;
}
