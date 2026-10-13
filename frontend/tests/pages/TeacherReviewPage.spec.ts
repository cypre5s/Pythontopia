import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { useSessionStore } from "@stores/session";
import TeacherReviewPage from "@/pages/teacher/TeacherReviewPage.vue";
import type { BossProblemReviewDto } from "@/api/problemReview";

const runtimeMocks = vi.hoisted(() => ({
  fetchAiRuntimeOperations: vi.fn(),
}));

const reviewMocks = vi.hoisted(() => ({
  listProblemReviews: vi.fn(),
  createProblemReview: vi.fn(),
  decideProblemReview: vi.fn(),
}));

const toastShow = vi.hoisted(() => vi.fn());

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("@/app/AppLayout.vue", () => ({
  default: { name: "AppLayout", template: "<div><slot /></div>" },
}));

vi.mock("@/api/problemReview", () => reviewMocks);

vi.mock("@/api/aiRuntime", () => ({
  fetchAiRuntimeOperations: runtimeMocks.fetchAiRuntimeOperations,
}));

vi.mock("@stores/toast", () => ({
  useToastStore: () => ({ show: toastShow, push: vi.fn() }),
}));

const review = (
  overrides: Partial<BossProblemReviewDto> = {},
): BossProblemReviewDto => ({
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
    statement: "读入两个整数 a b，输出 a+b。" + "内容".repeat(20),
    hints: ["提示一", "提示二"],
    sampleInput: "2 3",
    sampleOutput: "5",
    totalTestCases: 3,
    reviewed: true,
    aiGenerated: true,
  },
  decision: "PENDING",
  requesterId: 2,
  requesterUsername: "teach",
  requestedAt: "2026-08-02T08:00:00Z",
  deciderId: null,
  deciderUsername: null,
  decidedAt: null,
  decisionReason: null,
  version: 1,
  ...overrides,
});

describe("TeacherReviewPage", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    window.localStorage.clear();
    vi.clearAllMocks();
    reviewMocks.listProblemReviews.mockResolvedValue([]);
    runtimeMocks.fetchAiRuntimeOperations.mockResolvedValue({
      subject: {
        scope: "PLATFORM",
        label: "全平台学习证据",
        learnerCount: 6,
        evidenceLearnerCount: 4,
      },
      loops: [
        {
          loopType: "IMPROVE",
          label: "系统演化闭环",
          status: "PASS",
          latestTraceId: "trace-improve",
          nextAction: "complete:system_evolution_loop",
          evidenceRefs: ["agentic-m3-system-evolution-v1"],
        },
      ],
      governance: [],
    });
  });

  afterEach(() => window.localStorage.clear());

  it("loads the server queue for a teacher and renders persisted decisions", async () => {
    reviewMocks.listProblemReviews.mockResolvedValue([
      review({
        decision: "APPROVED",
        deciderId: 3,
        deciderUsername: "admin",
        decidedAt: "2026-08-02T08:01:00Z",
        decisionReason: "题面与隐藏用例均已复核",
        version: 2,
      }),
    ]);
    useSessionStore().user = {
      id: 2,
      username: "teach",
      displayName: "老师",
      role: "TEACHER",
    };

    const wrapper = mount(TeacherReviewPage);
    await flushPromises();

    expect(reviewMocks.listProblemReviews).toHaveBeenCalledWith(100);
    expect(wrapper.findAll(".list-item")).toHaveLength(1);
    expect(
      wrapper.find('[data-testid="teacher-review-counts"]').text(),
    ).toContain("通过 1");
    expect(wrapper.text()).toContain("题面与隐藏用例均已复核");
  });

  it("never requests the reviewer queue for a student and disables generation", async () => {
    useSessionStore().user = {
      id: 1,
      username: "student",
      displayName: "学生",
      role: "STUDENT",
    };
    const wrapper = mount(TeacherReviewPage);
    await flushPromises();

    expect(reviewMocks.listProblemReviews).not.toHaveBeenCalled();
    expect(
      wrapper.find('[data-testid="teacher-review-role-warning"]').exists(),
    ).toBe(true);
    expect(
      (
        wrapper.find('[data-testid="teacher-review-fetch"]')
          .element as HTMLButtonElement
      ).disabled,
    ).toBe(true);
  });

  it("creates a backend review candidate using chapter, stage, KC and kind", async () => {
    reviewMocks.createProblemReview.mockResolvedValue(review());
    useSessionStore().user = {
      id: 2,
      username: "teach",
      displayName: "老师",
      role: "TEACHER",
    };
    const wrapper = mount(TeacherReviewPage);
    await flushPromises();
    await wrapper
      .find('[data-testid="teacher-review-kc"]')
      .setValue("PY43-KC-001");
    await wrapper.find('[data-testid="teacher-review-kind"]').setValue("debug");

    await wrapper.find('[data-testid="teacher-review-fetch"]').trigger("click");
    await flushPromises();

    expect(reviewMocks.createProblemReview).toHaveBeenCalledWith({
      chapter: 1,
      stage: 1,
      kcCode: "PY43-KC-001",
      kind: "debug",
    });
    expect(wrapper.findAll(".list-item")).toHaveLength(1);
    expect(
      wrapper.find('[data-testid="teacher-review-counts"]').text(),
    ).toContain("待审 1");
  });

  it("disables generation while the review queue is loading", async () => {
    let resolveQueue!: (entries: BossProblemReviewDto[]) => void;
    reviewMocks.listProblemReviews.mockReturnValue(
      new Promise((resolve) => {
        resolveQueue = resolve;
      }),
    );
    useSessionStore().user = {
      id: 2,
      username: "teach",
      displayName: "老师",
      role: "TEACHER",
    };

    const wrapper = mount(TeacherReviewPage);
    await flushPromises();
    const fetchButton = wrapper.find(
      '[data-testid="teacher-review-fetch"]',
    );

    expect((fetchButton.element as HTMLButtonElement).disabled).toBe(true);
    await fetchButton.trigger("click");
    await flushPromises();
    expect(reviewMocks.createProblemReview).not.toHaveBeenCalled();

    resolveQueue([]);
    await flushPromises();
  });

  it("keeps a newly generated candidate when an older queue response finishes later", async () => {
    let rejectDecision!: (reason: Error) => void;
    let resolveReload!: (entries: BossProblemReviewDto[]) => void;
    let resolveGenerated!: (entry: BossProblemReviewDto) => void;
    const generated = review({
      id: 42,
      sourceProblemId: "source-2",
      problem: {
        ...review().problem,
        problemId: "fresh-question",
        statement: "freshly generated question",
      },
    });
    reviewMocks.listProblemReviews
      .mockResolvedValueOnce([review()])
      .mockReturnValueOnce(
        new Promise((resolve) => {
          resolveReload = resolve;
        }),
      );
    reviewMocks.decideProblemReview.mockReturnValue(
      new Promise((_resolve, reject) => {
        rejectDecision = reject;
      }),
    );
    reviewMocks.createProblemReview.mockReturnValue(
      new Promise((resolve) => {
        resolveGenerated = resolve;
      }),
    );
    useSessionStore().user = {
      id: 2,
      username: "teach",
      displayName: "老师",
      role: "TEACHER",
    };

    const wrapper = mount(TeacherReviewPage);
    await flushPromises();
    await wrapper
      .find('[data-testid="teacher-review-decision-reason"]')
      .setValue("题面已复核");
    await wrapper
      .find('[data-testid="teacher-review-approve"]')
      .trigger("click");
    await wrapper.find('[data-testid="teacher-review-fetch"]').trigger("click");
    await flushPromises();
    expect(reviewMocks.createProblemReview).toHaveBeenCalledTimes(1);

    rejectDecision(new Error("decision conflict"));
    await flushPromises();
    expect(reviewMocks.listProblemReviews).toHaveBeenCalledTimes(2);

    resolveGenerated(generated);
    await flushPromises();
    expect(wrapper.text()).toContain("freshly generated question");

    resolveReload([review()]);
    await flushPromises();

    expect(wrapper.text()).toContain("freshly generated question");
  });

  it("approves through the API with a reason and optimistic version", async () => {
    reviewMocks.listProblemReviews.mockResolvedValue([review()]);
    reviewMocks.decideProblemReview.mockResolvedValue(
      review({
        decision: "APPROVED",
        deciderId: 2,
        deciderUsername: "teach",
        decidedAt: "2026-08-02T08:02:00Z",
        decisionReason: "题面、样例与测试范围已复核",
        version: 2,
      }),
    );
    useSessionStore().user = {
      id: 2,
      username: "teach",
      displayName: "老师",
      role: "TEACHER",
    };
    const storageSpy = vi.spyOn(Storage.prototype, "setItem");
    const wrapper = mount(TeacherReviewPage);
    await flushPromises();
    await wrapper
      .find('[data-testid="teacher-review-decision-reason"]')
      .setValue("题面、样例与测试范围已复核");

    await wrapper
      .find('[data-testid="teacher-review-approve"]')
      .trigger("click");
    await flushPromises();

    expect(reviewMocks.decideProblemReview).toHaveBeenCalledWith(41, {
      decision: "APPROVED",
      reason: "题面、样例与测试范围已复核",
      version: 1,
    });
    expect(
      wrapper.find('[data-testid="teacher-review-counts"]').text(),
    ).toContain("通过 1");
    expect(storageSpy).not.toHaveBeenCalled();
    storageSpy.mockRestore();
  });

  it("rejects through the API and does not forge a LearningEvent", async () => {
    reviewMocks.listProblemReviews.mockResolvedValue([review()]);
    reviewMocks.decideProblemReview.mockResolvedValue(
      review({
        decision: "REJECTED",
        deciderId: 2,
        deciderUsername: "teach",
        decidedAt: "2026-08-02T08:02:00Z",
        decisionReason: "题目超纲",
        version: 2,
      }),
    );
    useSessionStore().user = {
      id: 2,
      username: "teach",
      displayName: "老师",
      role: "TEACHER",
    };
    const wrapper = mount(TeacherReviewPage);
    await flushPromises();
    await wrapper
      .find('[data-testid="teacher-review-decision-reason"]')
      .setValue("题目超纲");

    await wrapper
      .find('[data-testid="teacher-review-reject"]')
      .trigger("click");
    await flushPromises();

    expect(reviewMocks.decideProblemReview).toHaveBeenCalledWith(41, {
      decision: "REJECTED",
      reason: "题目超纲",
      version: 1,
    });
    expect(
      wrapper.find('[data-testid="teacher-review-counts"]').text(),
    ).toContain("驳回 1");
  });

  it("renders the API-backed runtime operations panel", async () => {
    useSessionStore().user = {
      id: 2,
      username: "teach",
      displayName: "老师",
      role: "TEACHER",
    };
    const wrapper = mount(TeacherReviewPage);
    await flushPromises();

    expect(runtimeMocks.fetchAiRuntimeOperations).toHaveBeenCalledTimes(1);
    expect(wrapper.find('[data-testid="runtime-operations"]').exists()).toBe(
      true,
    );
    expect(wrapper.text()).toContain("全平台学习证据");
  });
});
