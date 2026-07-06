import { http } from './http';
import type { ClassroomQuestDto } from './dto/ClassroomQuest';

export async function getMyClassroomQuests(): Promise<ClassroomQuestDto[]> {
  const response = await http.get<ClassroomQuestDto[]>('/api/classroom/quests/mine');
  return response.data;
}
