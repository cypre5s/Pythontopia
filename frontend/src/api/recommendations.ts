import { http } from './http';

export interface RecommendationDto {
  resourceId: number;
  title: string;
  type: string;
  kcCode: string;
  kcName: string;
  reason: string;
}

export async function getRecommendations(): Promise<RecommendationDto[]> {
  const response = await http.get<RecommendationDto[]>('/api/learner/recommendations');
  return response.data;
}
