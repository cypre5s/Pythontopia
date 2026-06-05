import { QuestNodeType } from './quests';

export interface Classroom {
  classroomId: number;
  name: string;
  description: string | null;
  teacherId: number;
  packId: number | null;
  status: string;
  joinCode: string | null;
}

export interface JoinClassroomRequest {
  joinCode: string;
}

export interface ClassroomQuest {
  id: number;
  classroomId: number;
  title: string;
  description: string | null;
  kcCodes: string[];
  nodeType: QuestNodeType;
  payload: unknown;
  dueDate: string | null;
  createdBy: number;
  createdAt: string;
  status: string;
  targetProblemId: number | null;
  targetResourceType: string | null;
  studentQuestId: number | null;
  studentQuestStatus: string | null;
  requiredEvidenceType: string | null;
  targetProblemIds: number[] | null;
  deepLinkRoute: string | null;
  deepLinkParams: unknown;
}
