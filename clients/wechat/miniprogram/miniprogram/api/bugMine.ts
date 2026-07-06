import { request } from '../utils/request';
import {
  BugMineCard,
  BugMineCodeRequest,
  BugMineEchoCandidate,
  BugMineEchoOpenRequest,
  BugMineErrorBookItem,
  BugMineOpenRequest,
  BugMineSession,
  BugMineSessionStatus,
} from './dto/bugMine';
import { Submission } from './dto/submissions';

/** 当前未关闭的错题矿洞 session 列表（含 minePriority / decayRisk / recommendationReason）。 */
export function getErrorBook(): Promise<BugMineErrorBookItem[]> {
  return request<BugMineErrorBookItem[]>('/bug-mine/errorbook');
}

/** 遗忘回声候选 KC（仅 FSRS 到期项，按入矿优先级降序）。 */
export function getEchoes(): Promise<BugMineEchoCandidate[]> {
  return request<BugMineEchoCandidate[]>('/bug-mine/echoes');
}

/** 由遗忘回声 KC 打开矿洞 session。 */
export function openEcho(kcCode: string): Promise<BugMineSession> {
  return request<BugMineSession, BugMineEchoOpenRequest>('/bug-mine/echoes', {
    method: 'POST',
    data: { kcCode },
  });
}

/** 由失败提交打开矿洞 session。 */
export function openSession(submissionId: number): Promise<BugMineSession> {
  return request<BugMineSession, BugMineOpenRequest>('/bug-mine/sessions', {
    method: 'POST',
    data: { submissionId },
  });
}

export function getSession(id: number): Promise<BugMineSession> {
  return request<BugMineSession>(`/bug-mine/sessions/${id}`);
}

/** 解锁当前深度允许的卡片。 */
export function revealCard(sessionId: number, cardId: number): Promise<BugMineCard> {
  return request<BugMineCard>(`/bug-mine/sessions/${sessionId}/cards/${cardId}/reveal`, {
    method: 'POST',
  });
}

/** 原题重试，服务端判题，返回提交（随后轮询 GET /submissions/{id} 取判定）。 */
export function retrySubmit(sessionId: number, code: string): Promise<Submission> {
  return request<Submission, BugMineCodeRequest>(`/bug-mine/sessions/${sessionId}/retry-submit`, {
    method: 'POST',
    data: { code },
  });
}

/** L5 同 KC 变式提交（掌握确认）。 */
export function variantSubmit(sessionId: number, code: string): Promise<Submission> {
  return request<Submission, BugMineCodeRequest>(`/bug-mine/sessions/${sessionId}/variant-submit`, {
    method: 'POST',
    data: { code },
  });
}

/** 关闭 session（RESOLVED / ABANDONED）。 */
export function closeSession(
  sessionId: number,
  status: BugMineSessionStatus,
): Promise<BugMineSession> {
  return request<BugMineSession>(`/bug-mine/sessions/${sessionId}/close`, {
    method: 'POST',
    query: { status },
  });
}
