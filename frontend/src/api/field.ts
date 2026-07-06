import { http } from './http';
import type { CurriculumBootstrapResult } from './dto/CurriculumBootstrapResult';
import type { MasterySnapshotDto } from './dto/MasterySnapshotDto';

export async function fetchMastery(): Promise<MasterySnapshotDto[]> {
  const response = await http.get<MasterySnapshotDto[]>('/api/learner/mastery');
  return response.data;
}

export async function bootstrapCurriculum(): Promise<CurriculumBootstrapResult> {
  const response = await http.post<CurriculumBootstrapResult>(
    '/api/learner/curriculum/bootstrap',
  );
  return response.data;
}
