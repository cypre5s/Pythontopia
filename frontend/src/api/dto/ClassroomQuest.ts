import type { QuestNodeType } from './Quest';

export interface ClassroomQuestDto {
  id: number;
  classroomId: number;
  title: string;
  description: string | null;
  kcCodes: string[];
  nodeType: QuestNodeType;
  payload: Record<string, unknown>;
  dueDate: string;
  createdBy: number;
  createdAt: string;
  status: string;
  targetProblemId: number | null;
  targetResourceType: string | null;
}

export interface ClassroomQuestCreateRequest {
  title: string;
  description?: string;
  kcCodes: string[];
  targetProblemId?: number;
  targetResourceType?: string;
  dueDate: string;
}

export interface ClassroomQuestPublishResponse {
  classroomQuest: ClassroomQuestDto;
  totalStudents: number;
  createdQuests: number;
  skippedDuplicates: number;
}
