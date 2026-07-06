import { http } from './http';
import type { LearnerSkillDto } from './dto/LearnerSkillDto';
import type { SkillNodeDto } from './dto/SkillNodeDto';

export interface SkillTreeResponse {
  catalog: SkillNodeDto[];
  unlocked: LearnerSkillDto[];
}

export async function fetchSkillTree(): Promise<SkillTreeResponse> {
  const response = await http.get<SkillTreeResponse>('/api/world/skills');
  return response.data;
}

export async function unlockSkill(skillKey: string): Promise<LearnerSkillDto> {
  const response = await http.post<LearnerSkillDto>(
    `/api/world/skills/${encodeURIComponent(skillKey)}/unlock`,
  );
  return response.data;
}
