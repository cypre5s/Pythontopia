import { http } from './http';
import type { EvaluationDto, EvaluationRequest } from './dto/Evaluation';

export async function listEvaluations(limit = 4): Promise<EvaluationDto[]> {
  const response = await http.get<EvaluationDto[]>(`/api/learner/evaluations?limit=${limit}`);
  return response.data;
}

export async function getEvaluation(id: number): Promise<EvaluationDto> {
  const response = await http.get<EvaluationDto>(`/api/learner/evaluations/${id}`);
  return response.data;
}

export async function runEvaluation(request: EvaluationRequest = {}): Promise<EvaluationDto> {
  const response = await http.post<EvaluationDto>('/api/learner/evaluations', request);
  return response.data;
}

export async function acceptEvaluationPath(id: number): Promise<EvaluationDto> {
  const response = await http.post<EvaluationDto>(
    `/api/learner/evaluations/${id}/accept-new-path`,
    {},
  );
  return response.data;
}

export async function keepOldEvaluationPath(id: number): Promise<EvaluationDto> {
  const response = await http.post<EvaluationDto>(
    `/api/learner/evaluations/${id}/keep-old`,
    {},
  );
  return response.data;
}
