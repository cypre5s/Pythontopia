import { http } from './http';
import type { LearnerProfileDto } from './dto/LearnerProfileDto';

/** 画像访谈单次作答由 ProfileAgent 生成，后端 graph 读超时 30s，前端必须覆盖同一窗口。 */
const PROFILE_INTERVIEW_TIMEOUT_MS = 40_000;

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

/**
 * 画像访谈单轮回包。
 *
 * 注意可空性：后端 `ProfileTurnResponse` 是 record，字段全部由外部 graph（ProfileAgent）
 * 的 JSON 反序列化填充——graph 少给哪个键，Jackson 就填 null。因此除 `nextQuestion` 外，
 * `done` / `dimensionsFilled` / `evidenceAnchors` / `safetyResult` / `groundingResult`
 * 都必须按可空消费（消费点补默认值），不能声明成必填字段对前端谎报。
 */
export interface ProfileTurnResponse {
  nextQuestion: string | null;
  profileDraft: LearnerProfileDto | null;
  /** graph 未回该字段时为 null；消费方按「未完成」处理。 */
  done: boolean | null;
  dimensionsFilled: string[] | null;
  evidenceAnchors: Array<{ source: string; ref: string; summary: string }> | null;
  safetyResult: CheckResultDto | null;
  groundingResult: CheckResultDto | null;
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
    // 访谈转次同步调 ProfileAgent（graph 读超时 30s）；全局 10s 会在作答生成前就断开。
    { timeout: PROFILE_INTERVIEW_TIMEOUT_MS },
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

/** 学生自评维度增量：scoreDelta ∈ [-1,1]；tagsAdd 去重追加（INTERVIEW 自评把所选 tag 提为主导）。 */
export interface SelfDimensionDelta {
  scoreDelta?: number;
  tagsAdd?: string[];
}

/**
 * 学生自评：对单个可自评维度提交增量。
 * 兼容旧调用（传 number = 仅 scoreDelta）；认知风格纠偏传 {@link SelfDimensionDelta}，
 * 后端按 source=INTERVIEW 让所选 tag 成为主导，不增加可信证据。
 */
export async function selfUpdateDimension(
  dimensionKey: string,
  delta: number | SelfDimensionDelta,
): Promise<DimensionUpdateResult> {
  const body: SelfDimensionDelta = typeof delta === 'number' ? { scoreDelta: delta } : delta;
  const response = await http.put<DimensionUpdateResult>(
    `/api/learner/profile/dimensions/${dimensionKey}`,
    body,
  );
  return response.data;
}

/**
 * 玩中建画像：提交一次世界内认知探针选择（中性、可跳过）。后端按 probeKey→维度
 * 累积小增量 + evidence_count（见 PassiveProfileService），成功返回 204 No Content，
 * 故不读取 body；未知 probeKey/choiceId 配对后端返回 400 PROBE_INVALID。
 */
export async function submitProbe(probeKey: string, choiceId: string): Promise<void> {
  await http.post('/api/learner/probe', { probeKey, choiceId });
}

/** 兼容世界氛围接口；服务端不再根据学习行为推断情绪。 */
export interface WorldStateDto {
  /** 兼容旧客户端的固定中性氛围值。 */
  mood: 'sunny' | 'cloudy' | 'rainy';
  /** 带符号的近期提交连击：正=连续 AC、负=连续失败。 */
  streak: number;
  /** 固定 0.5 的兼容字段，不代表学生情绪或坚持度。 */
  emotionScore: number;
}

/**
 * 拉取客观学习连击及兼容氛围字段。需 STUDENT 鉴权（共享 axios 自动注入 token）。
 */
export async function fetchWorldState(): Promise<WorldStateDto> {
  const response = await http.get<WorldStateDto>('/api/learner/world-state');
  return response.data;
}
