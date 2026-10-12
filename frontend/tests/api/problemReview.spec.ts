import { beforeEach, describe, expect, it, vi } from "vitest";
import { http } from "@/api/http";
import {
  createProblemReview,
  decideProblemReview,
  listProblemReviews,
  searchProblemReviews,
  type BossProblemReviewDto,
} from "@/api/problemReview";

vi.mock("@/api/http", () => ({
  http: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
  },
}));

const review: BossProblemReviewDto = {
  id: 41,
  packId: 43,
  sourceProblemId: "source-1",
  kind: "debug",
  problem: {
    problemId: "source-1",
    chapter: 1,
    stage: 1,
    difficulty: 2,
    kcCode: "PY43-KC-001",
    statement: "题面",
    hints: [],
    sampleInput: "2 3",
    sampleOutput: "5",
    totalTestCases: 2,
    reviewed: true,
    aiGenerated: true,
  },
  decision: "PENDING",
  requesterId: 10,
  requesterUsername: "teacher",
  requestedAt: "2026-08-02T08:00:00Z",
  deciderId: null,
  deciderUsername: null,
  decidedAt: null,
  decisionReason: null,
  version: 1,
};

describe("problemReview API", () => {
  beforeEach(() => vi.clearAllMocks());

  it("lists the server-authoritative review queue", async () => {
    vi.mocked(http.get).mockResolvedValue({ data: [review] });

    await expect(listProblemReviews(80)).resolves.toEqual([review]);

    expect(http.get).toHaveBeenCalledWith("/api/teacher/problem-reviews", {
      params: { limit: 80 },
    });
  });

  it("searches the persisted review queue with server pagination", async () => {
    const page = {
      content: [review],
      page: 1,
      size: 20,
      totalElements: 25,
      totalPages: 2,
    };
    vi.mocked(http.get).mockResolvedValue({ data: page });

    await expect(
      searchProblemReviews({
        page: 1,
        size: 20,
        sortBy: "REQUESTED_AT",
        sortDirection: "DESC",
        decision: "PENDING",
        q: " loop ",
      }),
    ).resolves.toEqual(page);

    expect(http.get).toHaveBeenCalledWith("/api/teacher/problem-reviews/page", {
      params: {
        page: 1,
        size: 20,
        sortBy: "REQUESTED_AT",
        sortDirection: "DESC",
        decision: "PENDING",
        q: "loop",
      },
    });
  });

  it("creates a backend review candidate instead of requesting a public Boss DTO", async () => {
    vi.mocked(http.post).mockResolvedValue({ data: review });

    await expect(
      createProblemReview({
        chapter: 1,
        stage: 1,
        kcCode: "PY43-KC-001",
        kind: "debug",
      }),
    ).resolves.toEqual(review);

    expect(http.post).toHaveBeenCalledWith(
      "/api/teacher/problem-reviews",
      { chapter: 1, stage: 1, kcCode: "PY43-KC-001", kind: "debug" },
      { timeout: 120_000 },
    );
  });

  it("patches a terminal decision with reason and optimistic version", async () => {
    const approved = { ...review, decision: "APPROVED" as const, version: 2 };
    vi.mocked(http.patch).mockResolvedValue({ data: approved });

    await expect(
      decideProblemReview(41, {
        decision: "APPROVED",
        reason: "题面与隐藏用例均已复核",
        version: 1,
      }),
    ).resolves.toEqual(approved);

    expect(http.patch).toHaveBeenCalledWith("/api/teacher/problem-reviews/41", {
      decision: "APPROVED",
      reason: "题面与隐藏用例均已复核",
      version: 1,
    });
  });
});
