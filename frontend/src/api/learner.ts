import { http } from './http';
import type { LearnerProfileDto } from './dto/LearnerProfileDto';

export interface ProfileMessageDto {
  role: 'assistant' | 'user';
  content: string;
}

export interface ProfileInterviewStartResponse {
  interviewSessionId: string;
  status: 'OPEN' | 'COMPLETED' | 'ABORTED';
  history: ProfileMessageDto[];
  draft: LearnerProfileDto | null;
}

export interface ProfileTurnRequest {
  userMessage: string;
}

export interface CheckResultDto {
  passed: boolean;
  reasons: string[];
}

export interface ProfileTurnResponse {
  nextQuestion: string | null;
  profileDraft: LearnerProfileDto | null;
  done: boolean;
  dimensionsFilled: string[];
  evidenceAnchors: Array<{ source: string; ref: string; summary: string }>;
  safetyResult: CheckResultDto;
  groundingResult: CheckResultDto;
}

export interface ProfileRevisionDto {
  revisionId: number;
  source: string;
  diff: Record<string, unknown>;
  createdAt: string;
}

export async function getLearnerProfile(): Promise<LearnerProfileDto | null> {
  const response = await http.get<LearnerProfileDto>('/api/learner/profile', {
    validateStatus: (status) => status === 200 || status === 204,
  });
  return response.status === 204 ? null : response.data;
}

export async function openProfileInterview(): Promise<ProfileInterviewStartResponse> {
  const response = await http.post<ProfileInterviewStartResponse>('/api/learner/profile/interview');
  return response.data;
}

export async function sendProfileTurn(
  sessionId: string,
  req: ProfileTurnRequest,
): Promise<ProfileTurnResponse> {
  const response = await http.post<ProfileTurnResponse>(
    `/api/learner/profile/interview/${sessionId}/turn`,
    req,
  );
  return response.data;
}

export async function finishProfileInterview(sessionId: string): Promise<void> {
  await http.post(`/api/learner/profile/interview/${sessionId}/finish`);
}

export async function listProfileRevisions(limit = 10): Promise<ProfileRevisionDto[]> {
  const response = await http.get<ProfileRevisionDto[]>('/api/learner/profile/revisions', {
    params: { limit },
  });
  return response.data;
}
