import { http } from './http';
import type {
  BugMineCardDto,
  BugMineErrorBookItemDto,
  BugMineOpenRequest,
  BugMineRetryRequest,
  BugMineSessionDto,
  BugMineSessionStatus,
} from './dto/BugMine';
import type { SubmissionDto } from './dto/Submission';

export async function openBugMineSession(
  request: BugMineOpenRequest,
): Promise<BugMineSessionDto> {
  const response = await http.post<BugMineSessionDto>('/api/bug-mine/sessions', request);
  return response.data;
}

export async function getBugMineSession(id: number): Promise<BugMineSessionDto> {
  const response = await http.get<BugMineSessionDto>(`/api/bug-mine/sessions/${id}`);
  return response.data;
}

export async function getBugMineErrorBook(): Promise<BugMineErrorBookItemDto[]> {
  const response = await http.get<BugMineErrorBookItemDto[]>('/api/bug-mine/errorbook');
  return response.data;
}

export async function revealBugMineCard(
  sessionId: number,
  cardId: number,
): Promise<BugMineCardDto> {
  const response = await http.post<BugMineCardDto>(
    `/api/bug-mine/sessions/${sessionId}/cards/${cardId}/reveal`,
    {},
  );
  return response.data;
}

export async function retryBugMineSubmit(
  sessionId: number,
  request: BugMineRetryRequest,
): Promise<SubmissionDto> {
  const response = await http.post<SubmissionDto>(
    `/api/bug-mine/sessions/${sessionId}/retry-submit`,
    request,
  );
  return response.data;
}

export async function variantBugMineSubmit(
  sessionId: number,
  request: BugMineRetryRequest,
): Promise<SubmissionDto> {
  const response = await http.post<SubmissionDto>(
    `/api/bug-mine/sessions/${sessionId}/variant-submit`,
    request,
  );
  return response.data;
}

export async function closeBugMineSession(
  sessionId: number,
  status: BugMineSessionStatus,
): Promise<BugMineSessionDto> {
  const response = await http.post<BugMineSessionDto>(
    `/api/bug-mine/sessions/${sessionId}/close`,
    null,
    { params: { status } },
  );
  return response.data;
}
