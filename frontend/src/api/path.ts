import { http } from './http';
import type {
  LearningPathDto,
  PlanPathResponse,
} from './dto/Path';

export async function planLearningPath(): Promise<PlanPathResponse> {
  const response = await http.post<PlanPathResponse>('/api/learner/path/plan', {});
  return response.data;
}

export async function getActiveLearningPath(): Promise<LearningPathDto | null> {
  try {
    const response = await http.get<LearningPathDto>('/api/learner/path/active');
    return response.data;
  } catch (error) {
    if (isNotFound(error)) {
      return null;
    }
    throw error;
  }
}

export async function getArchivedLearningPaths(): Promise<LearningPathDto[]> {
  const response = await http.get<LearningPathDto[]>('/api/learner/path/archived');
  return response.data;
}

export async function getLearningPathById(id: number): Promise<LearningPathDto> {
  const response = await http.get<LearningPathDto>(`/api/learner/path/${id}`);
  return response.data;
}

function isNotFound(error: unknown): boolean {
  if (typeof error === 'object' && error !== null && 'response' in error) {
    const { response } = error as { response?: { status?: number } };
    return response?.status === 404;
  }
  return false;
}
