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
  /** 本轮之前的多轮对话，传给 ProfileAgent 形成多轮记忆。 */
  history?: ProfileMessageDto[];
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

export interface ProfileDimensionDto {
  key: string;
  label: string;
  /** 已做读时 decay 的 effective 得分。 */
  score: number;
  /** 置信度 = min(1, evidenceCount/8)；<0.3 视为信号不足。 */
  confidence: number;
  evidenceCount: number;
  selfEditable: boolean;
  tags: string[];
}

/** 8 维画像得分快照（雷达图数据源）。 */
export async function getProfileDimensions(): Promise<ProfileDimensionDto[]> {
  const response = await http.get<ProfileDimensionDto[]>('/api/learner/profile/dimensions');
  return response.data;
}

export interface DimensionUpdateResult {
  userId: number;
  dimensionKey: string;
  finalDimension: Record<string, unknown>;
}

/** 学生自评：对单个可自评维度提交得分增量（scoreDelta ∈ [-1,1]）。 */
export async function selfUpdateDimension(
  dimensionKey: string,
  scoreDelta: number,
): Promise<DimensionUpdateResult> {
  const response = await http.put<DimensionUpdateResult>(
    `/api/learner/profile/dimensions/${dimensionKey}`,
    { scoreDelta },
  );
  return response.data;
}

/**
 * ③玩中建画像：提交一次世界内认知探针选择（中性、可跳过）。后端按 probeKey→维度
 * 累积小增量 + evidence_count（见 PassiveProfileService），成功返回 204 No Content，
 * 故不读取 body；未知 probeKey/choiceId 配对后端返回 400 PROBE_INVALID。
 */
export async function submitProbe(probeKey: string, choiceId: string): Promise<void> {
  await http.post('/api/learner/probe', { probeKey, choiceId });
}

/** ②情绪化小镇：由 emotion_persistence 派生的世界情绪状态。 */
export interface WorldStateDto {
  /** 直接驱动小镇氛围叠加层（背景级）。 */
  mood: 'sunny' | 'cloudy' | 'rainy';
  /** 带符号的近期提交连击：正=连续 AC、负=连续失败。 */
  streak: number;
  /** [0,1] 的有效情绪坚持度得分（已做读时 decay）。 */
  emotionScore: number;
}

/**
 * ②情绪化小镇：拉取学习者当前世界情绪状态。需 STUDENT 鉴权（共享 axios 自动注入 token）。
 * 后端可恒返回 200——无情绪信号/冷启动时降级为 sunny，前端据此渲染氛围，绝不阻塞玩家。
 */
export async function fetchWorldState(): Promise<WorldStateDto> {
  const response = await http.get<WorldStateDto>('/api/learner/world-state');
  return response.data;
}
