import { http } from './http';

export type FeynmanSessionStatus = 'OPEN' | 'TAUGHT' | 'JUDGED_PASS' | 'JUDGED_FAIL';
export type FeynmanTurnRole = 'TUTEE' | 'STUDENT';
export type FeynmanJudgeStatus = 'PENDING' | 'PASSED' | 'FAILED' | 'ERROR' | 'NA';

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
  noticeMd?: string | null;
  producedCode?: string | null;
  judgeResult?: FeynmanJudgeResultDto | null;
}

export async function startFeynmanSession(kcCode: string): Promise<FeynmanSessionDto> {
  const response = await http.post<FeynmanSessionDto>('/api/feynman/sessions', {
    kcCode,
  });
  return response.data;
}

// 覆盖后端费曼教学专用 90s read 上限，给公网代理与 JSON 映射留出余量。
const FEYNMAN_TUTEE_TIMEOUT_MS = 95_000;
// 代码找茬仍走普通 Graph 30s fail-fast 客户端。
const FEYNMAN_GRAPH_TIMEOUT_MS = 35_000;

export async function teachFeynmanSession(
  sessionId: number,
  explanation: string,
): Promise<FeynmanTurnDto> {
  const response = await http.post<FeynmanTurnDto>(
    `/api/feynman/sessions/${encodeURIComponent(String(sessionId))}/teach`,
    { explanation },
    { timeout: FEYNMAN_TUTEE_TIMEOUT_MS },
  );
  return response.data;
}

export type FeynmanBugHuntSource = 'real_misconception' | 'generic_beginner_bug' | 'none';

export interface FeynmanBugHuntDto {
  source: FeynmanBugHuntSource;
  misconceptionTag: string | null;
  buggyCode: string | null;
  /** buggyCode 中确属 bug 的 0-based 行号（后端由 buggy/fixed diff 得出，仅用于判分，不下发答案）。 */
  bugLineIndices: number[];
  hintMd: string;
}

export async function huntFeynmanBug(sessionId: number): Promise<FeynmanBugHuntDto> {
  const response = await http.post<FeynmanBugHuntDto>(
    `/api/feynman/sessions/${encodeURIComponent(String(sessionId))}/bug-hunt`,
    {},
    { timeout: FEYNMAN_GRAPH_TIMEOUT_MS },
  );
  return response.data;
}

export interface FeynmanVerdictDto {
  sessionId: number;
  passed: boolean;
  message: string;
  status: Exclude<FeynmanJudgeStatus, 'PENDING'>;
}

export async function judgeFeynmanSession(sessionId: number): Promise<FeynmanVerdictDto> {
  const response = await http.post<FeynmanVerdictDto>(
    `/api/feynman/sessions/${encodeURIComponent(String(sessionId))}/judge`,
    {},
    { timeout: 60_000 },
  );
  return response.data;
}
