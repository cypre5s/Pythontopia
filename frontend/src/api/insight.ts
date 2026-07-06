import { http } from './http';
import type { LearnerInsightDto } from './dto/Insight';

/** 拉取最新学情洞察（按 created_at 倒序）；limit 控制条数，省略则由后端默认。 */
export async function getInsights(limit?: number): Promise<LearnerInsightDto[]> {
  const params: Record<string, number> = {};
  if (limit !== undefined) {
    params.limit = limit;
  }
  const response = await http.get<LearnerInsightDto[]>('/api/learner/insights', { params });
  return response.data;
}
