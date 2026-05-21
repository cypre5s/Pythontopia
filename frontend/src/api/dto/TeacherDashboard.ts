export interface KcHeatmapEntryDto {
  kcCode: string;
  kcName: string | null;
  chapterId: number | null;
  chapterOrder: number | null;
  avgMastery: number;
  studentCount: number;
  masteredCount: number;
  learningCount: number;
  strugglingCount: number;
}

export interface QuestCompletionDto {
  classroomQuestId: number;
  title: string;
  dueDate: string;
  status: string;
  totalStudents: number;
  completedCount: number;
  inProgressCount: number;
  skippedCount: number;
  completionRate: number;
}

export interface ErrorCategoryDto {
  category: string;
  count: number;
}

export interface ErrorHotspotDto {
  kcCode: string;
  kcName: string | null;
  failedSubmissionCount: number;
  affectedStudentCount: number;
  categories: ErrorCategoryDto[];
}

export interface ResourceTypeBucket {
  resourceType: string;
  count: number;
}

export interface ResourceDailyBucket {
  day: string;
  resourceType: string;
  count: number;
}

export interface ResourceUsageDto {
  periodStart: string;
  periodEnd: string;
  totals: ResourceTypeBucket[];
  daily: ResourceDailyBucket[];
}

export interface RecentActivityDto {
  studentId: number;
  displayName: string;
  eventKind: string;
  kcCode: string | null;
  detail: string | null;
  occurredAt: string;
}
