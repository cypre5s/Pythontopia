import { http } from "./http";
import type { BossProblemDto } from "./dto/Boss";

export type BossProblemReviewDecision = "PENDING" | "APPROVED" | "REJECTED";
export type BossProblemReviewSortBy = "REQUESTED_AT" | "STATEMENT" | "REQUESTER_USERNAME";
export type BossProblemReviewSortDirection = "ASC" | "DESC";

export interface BossProblemReviewDto {
  id: number;
  packId: 43;
  sourceProblemId: string;
  kind: string | null;
  problem: BossProblemDto;
  decision: BossProblemReviewDecision;
  requesterId: number;
  requesterUsername: string;
  requestedAt: string;
  deciderId: number | null;
  deciderUsername: string | null;
  decidedAt: string | null;
  decisionReason: string | null;
  version: number;
}

export interface BossProblemReviewCreateRequest {
  chapter: number;
  stage: number;
  kcCode?: string;
  difficulty?: number;
  kind?: string;
}

export interface BossProblemReviewDecisionRequest {
  decision: Exclude<BossProblemReviewDecision, "PENDING">;
  reason: string;
  version: number;
}

export interface BossProblemReviewPageDto {
  content: BossProblemReviewDto[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface BossProblemReviewSearch {
  page: number;
  size: number;
  sortBy: BossProblemReviewSortBy;
  sortDirection: BossProblemReviewSortDirection;
  decision?: BossProblemReviewDecision;
  q?: string;
}

export async function listProblemReviews(
  limit = 100,
): Promise<BossProblemReviewDto[]> {
  const response = await http.get<BossProblemReviewDto[]>(
    "/api/teacher/problem-reviews",
    {
      params: { limit },
    },
  );
  return response.data;
}

export async function searchProblemReviews(
  search: BossProblemReviewSearch,
): Promise<BossProblemReviewPageDto> {
  const q = search.q?.trim();
  const params = {
    page: search.page,
    size: search.size,
    sortBy: search.sortBy,
    sortDirection: search.sortDirection,
    ...(search.decision ? { decision: search.decision } : {}),
    ...(q ? { q } : {}),
  };
  const response = await http.get<BossProblemReviewPageDto>(
    "/api/teacher/problem-reviews/page",
    { params },
  );
  return response.data;
}

export async function createProblemReview(
  request: BossProblemReviewCreateRequest,
): Promise<BossProblemReviewDto> {
  const response = await http.post<BossProblemReviewDto>(
    "/api/teacher/problem-reviews",
    request,
    { timeout: 120_000 },
  );
  return response.data;
}

export async function decideProblemReview(
  reviewId: number,
  request: BossProblemReviewDecisionRequest,
): Promise<BossProblemReviewDto> {
  const response = await http.patch<BossProblemReviewDto>(
    `/api/teacher/problem-reviews/${reviewId}`,
    request,
  );
  return response.data;
}
