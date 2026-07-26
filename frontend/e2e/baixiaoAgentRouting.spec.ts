import {
  expect,
  test,
  type APIRequestContext,
  type BrowserContext,
} from "@playwright/test";
import {
  acquireStudentSession,
  injectSession,
  resolveBaseUrl,
  suppressFirstVisitOverlays,
  type DemoSession,
} from "./support/demoSession";

const PROBLEM_ID = 476;
const FAILED_CODE = 'print("0.0000")\n';

interface SubmissionBody {
  id: number;
  status: "PASSED" | "FAILED" | "ERROR" | "PENDING" | "RUNNING";
}

interface BugMineSessionBody {
  id: number;
  submissionId: number;
  cards: unknown[];
}

interface RouteRequestBody {
  requestedIntent?: string;
  interaction?: { latestSubmissionId?: number };
  source?: string;
}

interface RouteSearchCandidateBody {
  candidateId: string;
  label: string;
  agentPath: string[];
  toolPath: string[];
  depth: number;
  gCost: number;
  hCost: number;
  penalty: number;
  totalCost: number;
}

interface RouteResponseBody {
  intent: string;
  secondaryIntent?: string | null;
  needsClarification: boolean;
  routeExecutionToken?: string;
  routeDecision?: {
    routeId: string;
    task: {
      needsClarification: boolean;
      subIntents?: string[];
      complexity?: string;
    };
    orchestration: {
      topology: string;
      primaryAgent: string | null;
      supportingAgents?: string[];
    };
    tools: { required: string[] };
    policies?: { maxParallelAgents: number; maxReplans?: number };
    output: { schemaName: string };
    personalization?: {
      strategy: string;
      signalsUsed: string[];
      explanations: string[];
      writebackMode: string;
    };
    search?: {
      strategy: string;
      candidates: RouteSearchCandidateBody[];
      selectedCandidateId: string;
      maxDepth: number;
      scoreFormula: string;
      replanPolicy: { maxCandidateSwitches: number };
    } | null;
  };
}

interface RuntimeSnapshotBody {
  loopRun: { id: number; traceId: string };
  checkpoints: Array<{ resumeCursor: string; status: string }>;
  toolCalls: Array<{ toolName: string; status: string }>;
  agentRuns: Array<{ agentName: string; status: string }>;
}

interface ReplayPlanBody {
  loopRunId: number;
  loopType: string;
  resumeCursor: string;
  nextAction: string;
  evidenceRefs: string[];
  replayable: boolean;
}

function u(path: string): string {
  return `${resolveBaseUrl()}${path}`;
}

function authHeaders(session: DemoSession): Record<string, string> {
  return { Authorization: `Bearer ${session.token}` };
}

async function prepareStudent(
  context: BrowserContext,
  request: APIRequestContext,
): Promise<DemoSession> {
  const session = await acquireStudentSession(request);
  await injectSession(context, session);
  await suppressFirstVisitOverlays(context);
  return session;
}

async function postJson<T>(
  request: APIRequestContext,
  session: DemoSession,
  path: string,
  data: Record<string, unknown>,
): Promise<T> {
  const response = await request.post(u(path), {
    headers: authHeaders(session),
    data,
    timeout: 120_000,
  });
  if (!response.ok()) {
    throw new Error(
      `POST ${path} failed: HTTP ${response.status()} ${await response.text()}`,
    );
  }
  return (await response.json()) as T;
}

async function postCreated(
  request: APIRequestContext,
  session: DemoSession,
  path: string,
  data: unknown,
): Promise<void> {
  const response = await request.post(u(path), {
    headers: authHeaders(session),
    data,
    timeout: 120_000,
  });
  if (response.status() !== 201) {
    throw new Error(
      `POST ${path} failed: HTTP ${response.status()} ${await response.text()}`,
    );
  }
}

async function getJson<T>(
  request: APIRequestContext,
  session: DemoSession,
  path: string,
): Promise<T> {
  const response = await request.get(u(path), {
    headers: authHeaders(session),
    timeout: 120_000,
  });
  if (!response.ok()) {
    throw new Error(
      `GET ${path} failed: HTTP ${response.status()} ${await response.text()}`,
    );
  }
  return (await response.json()) as T;
}

test.describe("百晓工坊 Agent 路由真实联测 @baixiao-routing", () => {
  test("修报错经策略门进入 BugMine/DebugAgent，且不会误入 Tutor SSE", async ({
    page,
    context,
    request,
  }) => {
    test.setTimeout(180_000);
    const session = await prepareStudent(context, request);
    const submission = await postJson<SubmissionBody>(
      request,
      session,
      "/api/submissions",
      {
        problemId: PROBLEM_ID,
        source: "TRAINING",
        code: FAILED_CODE,
      },
    );
    expect(submission.status).not.toBe("PASSED");

    const seededSession = await postJson<BugMineSessionBody>(
      request,
      session,
      "/api/bug-mine/sessions",
      { submissionId: submission.id },
    );
    expect(seededSession.submissionId).toBe(submission.id);
    expect(seededSession.cards.length).toBeGreaterThan(0);

    let tutorAskCalls = 0;
    page.on("request", (candidate) => {
      if (
        candidate.method() === "POST" &&
        candidate.url().includes("/api/tutor/ask")
      ) {
        tutorAskCalls += 1;
      }
    });

    await page.goto("/workshop", { waitUntil: "domcontentloaded" });
    await expect(page.getByTestId("workshop-page")).toBeVisible({
      timeout: 30_000,
    });

    const routeRequestPromise = page.waitForRequest(
      (candidate) =>
        candidate.method() === "POST" &&
        candidate.url().includes("/api/intent/route"),
    );
    const routeResponsePromise = page.waitForResponse(
      (candidate) =>
        candidate.request().method() === "POST" &&
        candidate.url().includes("/api/intent/route"),
    );
    const bugMineRequestPromise = page.waitForRequest(
      (candidate) =>
        candidate.method() === "POST" &&
        candidate.url().includes("/api/bug-mine/sessions"),
    );

    await page
      .getByTestId("composer-input")
      .fill("/修报错 请定位我最近一次失败提交");
    await page.getByTestId("composer-submit").click();

    const [routeRequest, routeResponse, bugMineRequest] = await Promise.all([
      routeRequestPromise,
      routeResponsePromise,
      bugMineRequestPromise,
    ]);
    const routeBody = routeRequest.postDataJSON() as RouteRequestBody;
    expect(routeBody).toMatchObject({
      requestedIntent: "FIX_BUG",
      interaction: { latestSubmissionId: submission.id },
      source: "slash_command",
    });

    expect(routeResponse.ok()).toBe(true);
    const routeResult = (await routeResponse.json()) as RouteResponseBody;
    expect(routeResult).toMatchObject({
      intent: "FIX_BUG",
      needsClarification: false,
      routeDecision: {
        task: { needsClarification: false },
        orchestration: {
          topology: "SINGLE_AGENT_TOOL",
          primaryAgent: "DebugAgent",
        },
        tools: { required: ["FIX_BUG"] },
        output: { schemaName: "DebugHintCard" },
      },
    });

    expect(bugMineRequest.postDataJSON()).toEqual({
      submissionId: submission.id,
    });
    await expect(page).toHaveURL(/\/bug-mine$/, { timeout: 30_000 });
    await expect(page.getByTestId("bug-mine-panel")).toBeVisible({
      timeout: 30_000,
    });
    await expect(page.getByTestId("session-statusbar")).toBeVisible({
      timeout: 30_000,
    });
    await expect(page.getByTestId("layer-content")).toContainText(
      "独立改一版",
      {
        timeout: 30_000,
      },
    );
    await expect(page.getByTestId("retry-area")).toBeVisible({
      timeout: 30_000,
    });
    expect(tutorAskCalls).toBe(0);
  });

  test("总结后出题经签名 RouteDecision 进入单次顺序 TaskPlan", async ({
    page,
    context,
    request,
  }) => {
    test.setTimeout(300_000);
    const session = await prepareStudent(context, request);

    let tutorAskCalls = 0;
    let directWorkshopCalls = 0;
    let orchestratorCalls = 0;
    page.on("request", (candidate) => {
      if (candidate.method() !== "POST") return;
      if (candidate.url().includes("/api/tutor/ask")) tutorAskCalls += 1;
      if (candidate.url().includes("/api/workshop/resources"))
        directWorkshopCalls += 1;
      if (candidate.url().includes("/api/orchestrator/turn/stream"))
        orchestratorCalls += 1;
    });

    await page.goto("/workshop", { waitUntil: "domcontentloaded" });
    await expect(page.getByTestId("workshop-page")).toBeVisible({
      timeout: 30_000,
    });

    const routeResponsePromise = page.waitForResponse(
      (candidate) =>
        candidate.request().method() === "POST" &&
        candidate.url().includes("/api/intent/route"),
    );
    const orchestratorRequestPromise = page.waitForRequest(
      (candidate) =>
        candidate.method() === "POST" &&
        candidate.url().includes("/api/orchestrator/turn/stream"),
      { timeout: 60_000 },
    );
    const orchestratorResponsePromise = page.waitForResponse(
      (candidate) =>
        candidate.request().method() === "POST" &&
        candidate.url().includes("/api/orchestrator/turn/stream"),
      { timeout: 60_000 },
    );

    await page
      .getByTestId("composer-input")
      .fill("总结 for 循环课件，然后根据总结出三道练习");
    await page.getByTestId("composer-submit").click();

    const [routeResponse, orchestratorRequest, orchestratorResponse] =
      await Promise.all([
        routeResponsePromise,
        orchestratorRequestPromise,
        orchestratorResponsePromise,
      ]);
    expect(routeResponse.ok()).toBe(true);
    const route = (await routeResponse.json()) as RouteResponseBody;
    expect(route).toMatchObject({
      intent: "HANDOUT",
      secondaryIntent: "PRACTICE",
      needsClarification: false,
      routeDecision: {
        task: {
          needsClarification: false,
          subIntents: ["HANDOUT", "PRACTICE"],
        },
        orchestration: {
          topology: "SEQUENTIAL",
          primaryAgent: "HandoutAgent",
          supportingAgents: ["PracticeAgent"],
        },
        tools: { required: ["HANDOUT", "PRACTICE"] },
        output: { schemaName: "CompoundLearningSupportCard" },
      },
    });
    expect(route.routeExecutionToken).toBeTruthy();

    const orchestratorBody = orchestratorRequest.postDataJSON() as {
      routeDecision?: RouteResponseBody["routeDecision"];
      routeExecutionToken?: string;
      recentKcCodes?: string[];
    };
    expect(orchestratorBody.routeDecision?.orchestration.topology).toBe(
      "SEQUENTIAL",
    );
    expect(orchestratorBody.routeExecutionToken).toBe(
      route.routeExecutionToken,
    );
    expect(orchestratorResponse.ok()).toBe(true);

    const planMessage = page.getByTestId("plan-card-msg").last();
    await expect(planMessage).toBeVisible({ timeout: 240_000 });
    await expect(planMessage.getByTestId("plan-step")).toHaveCount(2, {
      timeout: 240_000,
    });
    await expect(planMessage).toContainText("生成章节总结");
    await expect(planMessage).toContainText("生成针对性练习");
    await expect(planMessage.getByTestId("plan-step-status")).toHaveCount(2, {
      timeout: 240_000,
    });
    await expect(planMessage.getByTestId("plan-step-status")).toHaveText(
      ["✓", "✓"],
      { timeout: 240_000 },
    );
    await expect(planMessage).toHaveAttribute("data-streaming", "false", {
      timeout: 240_000,
    });
    await expect(page.getByTestId("secondary-chip")).toHaveCount(0);

    const loopRunId = Number(
      await planMessage.getAttribute("data-loop-run-id"),
    );
    expect(loopRunId).toBeGreaterThan(0);

    const snapshot = await getJson<RuntimeSnapshotBody>(
      request,
      session,
      `/api/loop/runs/${loopRunId}/runtime`,
    );
    const replay = await getJson<ReplayPlanBody>(
      request,
      session,
      `/api/loop/runs/${loopRunId}/replay`,
    );
    expect(snapshot.loopRun.id).toBe(loopRunId);
    expect(snapshot.loopRun.traceId).toBe(route.routeDecision?.routeId);
    expect(snapshot.checkpoints.length).toBeGreaterThanOrEqual(2);
    expect(snapshot.agentRuns).toHaveLength(2);
    expect(snapshot.toolCalls).toHaveLength(2);
    expect(snapshot.agentRuns.every((run) => run.status === "COMPLETED")).toBe(
      true,
    );
    expect(
      snapshot.toolCalls.every((call) => call.status === "COMPLETED"),
    ).toBe(true);
    expect(replay).toMatchObject({
      loopRunId,
      loopType: "RESOURCE",
      nextAction: "review_result",
      replayable: true,
    });
    expect(replay.resumeCursor).toContain("practice");
    expect(replay.evidenceRefs).toContain(
      `route:${route.routeDecision?.routeId}`,
    );
    expect(orchestratorCalls).toBe(1);
    expect(tutorAskCalls).toBe(0);
    expect(directWorkshopCalls).toBe(0);
  });

  test("典型 C3 复合请求编译三条受限候选并按 A* 选中路径真实执行", async ({
    page,
    context,
    request,
  }) => {
    test.setTimeout(480_000);
    const session = await prepareStudent(context, request);

    let orchestratorCalls = 0;
    page.on("request", (candidate) => {
      if (
        candidate.method() === "POST" &&
        candidate.url().includes("/api/orchestrator/turn/stream")
      ) {
        orchestratorCalls += 1;
      }
    });

    await page.goto("/workshop", { waitUntil: "domcontentloaded" });
    await expect(page.getByTestId("workshop-page")).toBeVisible({
      timeout: 30_000,
    });

    const routeResponsePromise = page.waitForResponse(
      (candidate) =>
        candidate.request().method() === "POST" &&
        candidate.url().includes("/api/intent/route"),
    );
    const orchestratorRequestPromise = page.waitForRequest(
      (candidate) =>
        candidate.method() === "POST" &&
        candidate.url().includes("/api/orchestrator/turn/stream"),
      { timeout: 60_000 },
    );

    await page
      .getByTestId("composer-input")
      .fill(
        "我循环这一章学得很乱，先给我总结一下，再根据我以前犯的错出几道题，并告诉我先做哪一道。",
      );
    await page.getByTestId("composer-submit").click();

    const [routeResponse, orchestratorRequest] = await Promise.all([
      routeResponsePromise,
      orchestratorRequestPromise,
    ]);
    expect(routeResponse.ok()).toBe(true);
    const route = (await routeResponse.json()) as RouteResponseBody;
    expect(route.needsClarification).toBe(false);
    expect(route.routeDecision?.task.complexity).toBe("C3");
    expect(route.routeDecision?.task.subIntents).toEqual([
      "HANDOUT",
      "PRACTICE",
      "PLAN",
    ]);
    expect(route.routeDecision?.orchestration.topology).toBe(
      "PLANNER_EXECUTOR",
    );
    expect(route.routeDecision?.tools.required).toEqual([
      "HANDOUT",
      "PRACTICE",
      "PLAN",
    ]);
    expect(route.routeDecision?.policies?.maxReplans).toBe(1);
    expect(route.routeExecutionToken).toBeTruthy();

    const search = route.routeDecision?.search;
    expect(search).toBeTruthy();
    if (!search) throw new Error("C3 route must carry search contract");
    expect(search.strategy).toBe("BOUNDED_TOT_ASTAR");
    expect(search.replanPolicy.maxCandidateSwitches).toBe(1);
    expect(search.candidates).toHaveLength(3);
    expect(search.candidates.map((candidate) => candidate.label)).toEqual([
      "规划诊断优先",
      "知识重构优先",
      "练习探测优先",
    ]);
    for (const candidate of search.candidates) {
      expect(candidate.depth).toBeLessThanOrEqual(search.maxDepth);
      expect([...candidate.toolPath].sort()).toEqual([
        "HANDOUT",
        "PLAN",
        "PRACTICE",
      ]);
      expect(candidate.totalCost).toBeCloseTo(
        candidate.gCost + candidate.hCost + candidate.penalty,
        4,
      );
    }
    const selected = search.candidates.find(
      (candidate) => candidate.candidateId === search.selectedCandidateId,
    );
    if (!selected) throw new Error("selected candidate missing from search");
    const minimumCost = Math.min(
      ...search.candidates.map((candidate) => candidate.totalCost),
    );
    expect(selected.totalCost).toBe(minimumCost);
    // 无失败历史的新学生：练习探测优先剩余估计最低，应被 A* 选中。
    // 依赖 PYT_E2E_REGISTER=1 每次注册全新学生；若复用固定账号，历史失败/资源
    // 事实会合法地翻转 A* 选择，此断言将失败——那是画像生效而非回归。
    expect(search.selectedCandidateId).toBe("candidate_practice_first");

    const orchestratorBody = orchestratorRequest.postDataJSON() as {
      routeDecision?: RouteResponseBody["routeDecision"];
      routeExecutionToken?: string;
    };
    expect(orchestratorBody.routeDecision?.search?.selectedCandidateId).toBe(
      "candidate_practice_first",
    );
    expect(orchestratorBody.routeExecutionToken).toBe(
      route.routeExecutionToken,
    );

    const planMessage = page.getByTestId("plan-card-msg").last();
    await expect(planMessage).toBeVisible({ timeout: 240_000 });
    const searchNote = planMessage.getByTestId("plan-search");
    await expect(searchNote).toBeVisible({ timeout: 60_000 });
    await expect(searchNote).toContainText("已比较 3 条受限学习路径");
    await expect(searchNote).toContainText("练习探测优先");
    const searchNoteText = (await searchNote.textContent()) ?? "";
    expect(searchNoteText).not.toContain("candidate_practice_first");
    expect(searchNoteText).not.toContain("g_cost");

    await expect(planMessage.getByTestId("plan-step")).toHaveCount(3, {
      timeout: 300_000,
    });
    await expect(planMessage.getByTestId("plan-step").nth(0)).toContainText(
      "生成针对性练习",
    );
    await expect(planMessage.getByTestId("plan-step").nth(1)).toContainText(
      "生成章节总结",
    );
    await expect(planMessage.getByTestId("plan-step").nth(2)).toContainText(
      "安排学习路径",
    );
    await expect(planMessage.getByTestId("plan-step-status")).toHaveText(
      ["✓", "✓", "✓"],
      { timeout: 300_000 },
    );
    await expect(planMessage).toHaveAttribute("data-streaming", "false", {
      timeout: 300_000,
    });

    const loopRunId = Number(
      await planMessage.getAttribute("data-loop-run-id"),
    );
    expect(loopRunId).toBeGreaterThan(0);
    const snapshot = await getJson<RuntimeSnapshotBody>(
      request,
      session,
      `/api/loop/runs/${loopRunId}/runtime`,
    );
    expect(snapshot.loopRun.traceId).toBe(route.routeDecision?.routeId);
    // 真实栈允许最多一次有界恢复（例如 grounding 拒绝后切换候选重派同一工具），
    // 因此运行数为 3（无恢复）或 4（一次恢复）；超过 4 说明越过了硬预算。
    expect(snapshot.agentRuns.length).toBeGreaterThanOrEqual(3);
    expect(snapshot.agentRuns.length).toBeLessThanOrEqual(4);
    expect(snapshot.toolCalls.length).toBe(snapshot.agentRuns.length);
    const failedRuns = snapshot.agentRuns.filter(
      (run) => run.status !== "COMPLETED",
    );
    expect(failedRuns.length).toBeLessThanOrEqual(1);
    const lastStatusByAgent = new Map<string, string>();
    for (const run of snapshot.agentRuns) {
      lastStatusByAgent.set(run.agentName, run.status);
    }
    expect([...lastStatusByAgent.keys()].sort()).toEqual([
      "HandoutAgent",
      "PathPlannerAgent",
      "PracticeAgent",
    ]);
    expect(
      [...lastStatusByAgent.values()].every((status) => status === "COMPLETED"),
    ).toBe(true);
    expect(orchestratorCalls).toBe(1);
  });

  test("同一复合请求会按真实失败历史从并行改为脚手架式顺序执行", async ({
    page,
    context,
    request,
  }) => {
    test.setTimeout(180_000);
    const session = await prepareStudent(context, request);
    const message = "生成一份循环讲义和思维导图";

    const before = await postJson<RouteResponseBody>(
      request,
      session,
      "/api/intent/route",
      { message, source: "composer_text" },
    );
    expect(before.routeDecision).toMatchObject({
      orchestration: { topology: "PARALLEL" },
      tools: { required: ["HANDOUT", "MINDMAP"] },
      personalization: { strategy: "STANDARD" },
    });

    await postCreated(request, session, "/api/learning/events/batch", [
      {
        eventType: "practice_failed",
        payload: { kcCode: "PY43-KC-07", submissionId: -1 },
      },
      {
        eventType: "practice_failed",
        payload: { kcCode: "PY43-KC-07", submissionId: -2 },
      },
      {
        eventType: "resource_consumed",
        payload: { resourceId: "frontend-forged-resource" },
      },
    ]);
    const afterUntrustedEvents = await postJson<RouteResponseBody>(
      request,
      session,
      "/api/intent/route",
      { message, source: "composer_text" },
    );
    expect(afterUntrustedEvents.routeDecision).toMatchObject({
      orchestration: { topology: "PARALLEL" },
      personalization: { strategy: "STANDARD" },
    });
    expect(afterUntrustedEvents.routeDecision?.routeId).toBe(
      before.routeDecision?.routeId,
    );

    const firstFailure = await postJson<SubmissionBody>(
      request,
      session,
      "/api/submissions",
      { problemId: PROBLEM_ID, source: "TRAINING", code: FAILED_CODE },
    );
    // 正式提交按产品契约每个账号 10 秒一次；跨过窗口后再制造第二条真实失败历史。
    await page.waitForTimeout(11_000);
    const secondFailure = await postJson<SubmissionBody>(
      request,
      session,
      "/api/submissions",
      { problemId: PROBLEM_ID, source: "TRAINING", code: FAILED_CODE },
    );
    expect(firstFailure.status).not.toBe("PASSED");
    expect(secondFailure.status).not.toBe("PASSED");

    const after = await postJson<RouteResponseBody>(
      request,
      session,
      "/api/intent/route",
      { message, source: "composer_text" },
    );
    expect(after.routeDecision).toMatchObject({
      orchestration: { topology: "SEQUENTIAL" },
      tools: { required: ["HANDOUT", "MINDMAP"] },
      policies: { maxParallelAgents: 1 },
      personalization: {
        strategy: "SCAFFOLD_FIRST",
        writebackMode: "DOMAIN_EVENTS_ONLY",
      },
    });
    expect(after.routeDecision?.personalization?.signalsUsed).toContain(
      "cognitive_load:HIGH",
    );
    expect(after.routeDecision?.routeId).not.toBe(
      before.routeDecision?.routeId,
    );

    await page.goto("/workshop", { waitUntil: "domcontentloaded" });
    await expect(page.getByTestId("workshop-page")).toBeVisible({
      timeout: 30_000,
    });
    const routeResponsePromise = page.waitForResponse(
      (candidate) =>
        candidate.request().method() === "POST" &&
        candidate.url().includes("/api/intent/route"),
    );
    const orchestratorRequestPromise = page.waitForRequest(
      (candidate) =>
        candidate.method() === "POST" &&
        candidate.url().includes("/api/orchestrator/turn/stream"),
      { timeout: 60_000 },
    );

    await page.getByTestId("composer-input").fill(message);
    await page.getByTestId("composer-submit").click();

    const [uiRouteResponse, orchestratorRequest] = await Promise.all([
      routeResponsePromise,
      orchestratorRequestPromise,
    ]);
    expect(uiRouteResponse.ok()).toBe(true);
    const uiRoute = (await uiRouteResponse.json()) as RouteResponseBody;
    expect(uiRoute.routeDecision?.personalization?.strategy).toBe(
      "SCAFFOLD_FIRST",
    );
    expect(
      (
        orchestratorRequest.postDataJSON() as {
          routeDecision?: RouteResponseBody["routeDecision"];
        }
      ).routeDecision?.orchestration.topology,
    ).toBe("SEQUENTIAL");
    await expect(page.getByTestId("plan-personalization")).toContainText(
      "短步骤串行执行并关闭并发",
      { timeout: 30_000 },
    );
    await expect(page.getByTestId("plan-personalization")).not.toContainText(
      "cognitive_load",
    );
  });

  test("自由文本诊断后补救不因服务端隐式补全 interaction 而验签失败", async ({
    page,
    context,
    request,
  }) => {
    test.setTimeout(300_000);
    const session = await prepareStudent(context, request);
    const submission = await postJson<SubmissionBody>(
      request,
      session,
      "/api/submissions",
      {
        problemId: PROBLEM_ID,
        source: "TRAINING",
        code: FAILED_CODE,
      },
    );
    expect(submission.status).not.toBe("PASSED");

    await page.goto("/workshop", { waitUntil: "domcontentloaded" });
    await expect(page.getByTestId("workshop-page")).toBeVisible({
      timeout: 30_000,
    });

    const routeRequestPromise = page.waitForRequest(
      (candidate) =>
        candidate.method() === "POST" &&
        candidate.url().includes("/api/intent/route"),
    );
    const routeResponsePromise = page.waitForResponse(
      (candidate) =>
        candidate.request().method() === "POST" &&
        candidate.url().includes("/api/intent/route"),
    );
    const orchestratorRequestPromise = page.waitForRequest(
      (candidate) =>
        candidate.method() === "POST" &&
        candidate.url().includes("/api/orchestrator/turn/stream"),
      { timeout: 60_000 },
    );
    const orchestratorResponsePromise = page.waitForResponse(
      (candidate) =>
        candidate.request().method() === "POST" &&
        candidate.url().includes("/api/orchestrator/turn/stream"),
      { timeout: 60_000 },
    );

    await page
      .getByTestId("composer-input")
      .fill("诊断这次 WA，然后根据原因出三道补救练习");
    await page.getByTestId("composer-submit").click();

    const [
      routeRequest,
      routeResponse,
      orchestratorRequest,
      orchestratorResponse,
    ] = await Promise.all([
      routeRequestPromise,
      routeResponsePromise,
      orchestratorRequestPromise,
      orchestratorResponsePromise,
    ]);
    const routeRequestBody = routeRequest.postDataJSON() as RouteRequestBody;
    expect(routeRequestBody.requestedIntent).toBeUndefined();
    expect(routeRequestBody.interaction).toBeUndefined();
    expect(routeResponse.ok()).toBe(true);
    const route = (await routeResponse.json()) as RouteResponseBody;
    expect(route).toMatchObject({
      intent: "FIX_BUG",
      secondaryIntent: "PRACTICE",
      needsClarification: false,
      routeDecision: {
        task: { subIntents: ["FIX_BUG", "PRACTICE"] },
        orchestration: {
          topology: "SEQUENTIAL",
          primaryAgent: "DebugAgent",
          supportingAgents: ["PracticeAgent"],
        },
        tools: { required: ["FIX_BUG", "PRACTICE"] },
      },
    });
    expect(route.routeExecutionToken).toBeTruthy();

    const orchestratorBody = orchestratorRequest.postDataJSON() as {
      interaction?: unknown;
      routeExecutionToken?: string;
    };
    expect(orchestratorBody.interaction).toBeUndefined();
    expect(orchestratorBody.routeExecutionToken).toBe(
      route.routeExecutionToken,
    );
    expect(orchestratorResponse.ok()).toBe(true);

    const planMessage = page.getByTestId("plan-card-msg").last();
    await expect(planMessage.getByTestId("plan-step")).toHaveCount(2, {
      timeout: 240_000,
    });
    await expect(planMessage.getByTestId("plan-step-status")).toHaveText(
      ["✓", "✓"],
      { timeout: 240_000 },
    );
    await expect(planMessage).toHaveAttribute("data-streaming", "false", {
      timeout: 240_000,
    });
    expect(
      Number(await planMessage.getAttribute("data-loop-run-id")),
    ).toBeGreaterThan(0);
  });
});
