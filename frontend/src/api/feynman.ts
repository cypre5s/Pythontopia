import { http } from './http';

export type FeynmanSessionStatus = 'OPEN' | 'TAUGHT' | 'JUDGED_PASS' | 'JUDGED_FAIL';
export type FeynmanTurnRole = 'TUTEE' | 'STUDENT';
/**
 * 判题结论状态。`ERROR` = 判题系统故障（不是学生失败）；`NA` = 本模式不做代表题真判题
 * （概念模式的中性说明）。与后端 FeynmanDtos.JudgeStatus 一一对应。
 */
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
  producedCode?: string | null;
  judgeResult?: FeynmanJudgeResultDto | null;
  /** 系统侧诚实旁白（非 LLM 生成），例如「该知识点还没有真实错误记录，先去做题」。 */
  noticeMd?: string | null;
}

export async function startFeynmanSession(kcCode: string): Promise<FeynmanSessionDto> {
  const response = await http.post<FeynmanSessionDto>('/api/feynman/sessions', { kcCode });
  return response.data;
}

// 费曼「教学/出题」要等 graph LLM（学弟评估 / 现生 bug）。后端 graphRestTemplate 是
// connect 3s + read 30s，浏览器侧必须留在它之后收网，否则前端先超时、后端仍在写库，
// 学生看到「超时」却已经产生了一轮 turn。取 35s（> 3s + 30s）。
const FEYNMAN_LLM_TIMEOUT_MS = 35_000;

// 真判题最多跑 10 个用例 × 5000ms = 50s，全局默认 10s 必然先超时，
// 而后端事务已提交并计分，学生自然重试即造成重复计分。留足 60s。
const FEYNMAN_JUDGE_TIMEOUT_MS = 60_000;

export async function teachFeynmanSession(
  sessionId: number,
  explanation: string,
): Promise<FeynmanTurnDto> {
  const response = await http.post<FeynmanTurnDto>(
    `/api/feynman/sessions/${encodeURIComponent(String(sessionId))}/teach`,
    { explanation },
    { timeout: FEYNMAN_LLM_TIMEOUT_MS },
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
    { timeout: FEYNMAN_LLM_TIMEOUT_MS },
  );
  return response.data;
}

export interface FeynmanVerdictDto {
  sessionId: number;
  passed: boolean;
  message: string;
  /** 后端权威状态；`passed` 只是 `PASSED` 的布尔投影，无法区分 ERROR / NA。 */
  status?: FeynmanJudgeStatus;
}

export async function judgeFeynmanSession(sessionId: number): Promise<FeynmanVerdictDto> {
  const response = await http.post<FeynmanVerdictDto>(
    `/api/feynman/sessions/${encodeURIComponent(String(sessionId))}/judge`,
    {},
    { timeout: FEYNMAN_JUDGE_TIMEOUT_MS },
  );
  return response.data;
}
