import { http } from './http';

export type FeynmanSessionStatus = 'OPEN' | 'TAUGHT' | 'JUDGED_PASS' | 'JUDGED_FAIL';
export type FeynmanTurnRole = 'TUTEE' | 'STUDENT';
export type FeynmanJudgeStatus = 'PENDING' | 'PASSED' | 'FAILED' | 'ERROR';

export interface FeynmanMisconceptionDto {
  tag: string;
  evidenceEventIds: number[];
  resolved: boolean;
}

export interface FeynmanJudgeResultDto {
  status: FeynmanJudgeStatus;
  messageZh: string;
  evidenceEventIds: number[];
}

export interface FeynmanTuteeStateDto {
  misconceptions: FeynmanMisconceptionDto[];
  producedCode?: string | null;
  judgeResult?: FeynmanJudgeResultDto | null;
}

export interface FeynmanSessionDto {
  id: number;
  userId: number;
  kcCode: string;
  status: FeynmanSessionStatus;
  tuteeState: FeynmanTuteeStateDto;
}

export interface StartFeynmanSessionRequest {
  kcCode: string;
}

export interface TeachFeynmanSessionRequest {
  explanation: string;
}

export interface FeynmanTurnDto {
  id: number;
  role: FeynmanTurnRole;
  content: string;
  taughtFix: string | null;
  producedCode?: string | null;
  judgeResult?: FeynmanJudgeResultDto | null;
}

export async function startFeynmanSession(kcCode: string): Promise<FeynmanSessionDto> {
  const response = await http.post<FeynmanSessionDto>('/api/feynman/sessions', { kcCode });
  return response.data;
}

export async function teachFeynmanSession(
  sessionId: number,
  explanation: string,
): Promise<FeynmanTurnDto> {
  const response = await http.post<FeynmanTurnDto>(
    `/api/feynman/sessions/${encodeURIComponent(String(sessionId))}/teach`,
    { explanation },
  );
  return response.data;
}

export interface FeynmanVerdictDto {
  sessionId: number;
  passed: boolean;
  message: string;
}

export async function judgeFeynmanSession(sessionId: number): Promise<FeynmanVerdictDto> {
  const response = await http.post<FeynmanVerdictDto>(
    `/api/feynman/sessions/${encodeURIComponent(String(sessionId))}/judge`,
    {},
  );
  return response.data;
}
