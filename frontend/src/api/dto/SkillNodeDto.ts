export interface SkillNodeDto {
  skillKey: string;
  displayName: string;
  description?: string;
  kcCodes: string[];
  tier: number;
  iconUrl?: string;
}
