import { expect, request as playwrightRequest, test, type APIRequestContext } from '@playwright/test';
import {
  acquireStudentSession,
  injectSession,
  resolveBaseUrl,
  suppressFirstVisitOverlays,
  type DemoSession,
} from './support/demoSession';

const BASE_URL = resolveBaseUrl();
const KC_CODE = 'PY43-KC-016';
const ALT_KC_CODE = 'PY43-KC-005';
const PROBLEM_ID = 476;
const CODE_AC = 'radius = float(input())\nprint(f"{3.1415 * radius * radius:.4f}")\n';
const CODE_WA = 'print("0.0000")\n';

type JsonMap = Record<string, unknown>;

interface BootstrapBody {
  world: JsonMap;
  player: { mapKey: string; tileX: number; tileY: number };
  profile: JsonMap;
  quests: JsonMap[];
  mastery: JsonMap[];
  path: JsonMap[];
  skills: JsonMap[];
  npcStates: JsonMap[];
  activeSlot: JsonMap | null;
  pendingEvents: JsonMap[];
}

interface ResourceCreateBody {
  status: 'SUCCESS' | 'PARTIAL' | 'FAILED';
  resourceId: number;
  runId: number;
}

interface ResourceBody {
  id: number;
  kcCodes: string[];
  type: string;
  title: string;
  summary: string;
  evidencePageIds: number[];
  groundingStatus: string;
  safetyStatus: string;
}

interface ConsumptionBody {
  resourceId: number;
  kcCodes: string[];
  nextAction: string;
  readRecorded: boolean;
  loopState: string;
  evidenceRequired: string;
}

interface CheckBody {
  checkId: number;
  status: 'PASSED' | 'FAILED';
  masteryDelta: number;
  nextAction: 'pass' | 'remediate' | 'swap_resource' | 'replan';
  remediationType: string | null;
  loopState: string;
  practiceSubmissionId: number | null;
  decisionReason: string;
}

interface ResourceEffectivenessBody {
  resourceId: number;
  feedbackStatus: 'NONE' | 'LIKED' | 'DISLIKED';
  adoptedCount: number;
  rejectedCount: number;
  checkCount: number;
  passedCheckCount: number;
  failedCheckCount: number;
  passRate: number;
  masteryDeltaSum: number;
  evidencePageIds: number[];
  signal: 'INSUFFICIENT_EVIDENCE' | 'WATCH' | 'EFFECTIVE' | 'NEEDS_REVISION';
  evaluationHook: 'resource_feedback_or_check';
}

interface SubmissionBody {
  id: number;
  status: 'PASSED' | 'FAILED' | 'ERROR' | 'PENDING' | 'RUNNING';
  problemId: number;
}

interface LoopRunBody {
  id: number;
  userId: number;
  sessionId: string;
  maxSteps: number;
  stepsUsed: number;
  toolsUsed: string[];
  groundingPass: boolean;
  safetyPass: boolean;
  traceId: string;
}

interface RuntimeSnapshotBody {
  loopRun: LoopRunBody;
  checkpoints: Array<{ id: number; resumeCursor: string; stepIndex: number }>;
  toolCalls: Array<{ id: number; toolName: string; status: string }>;
  agentRuns: Array<{ id: number; agentName: string; status: string }>;
  evalResults: Array<{ id: number; datasetId: string; loopType: string; passed: boolean }>;
}

interface ReplayPlanBody {
  loopRunId: number;
  loopType: string;
  resumeCursor: string;
  nextAction: string;
  evidenceRefs: string[];
  replayable: boolean;
}

interface BugMineSessionBody {
  id: number;
  submissionId: number;
  status: string;
  cards: Array<{ id: number; revealed: boolean; cardType: string }>;
}

interface RuntimeSummaryBody {
  protocol: { aguiEventCount: number; toolsWithEvidenceFieldCount: number };
  evidence: { nodeCount: number; graphCompletenessScore: number };
  learnerTwin: { memoryEventCount: number; failedPracticeCount: number; succeededPracticeCount: number };
  gates: Array<{ key: string; status: string; value: number }>;
}

interface RuntimeOperationsBody {
  loops: Array<{
    loopType: string;
    label: string;
    status: 'PASS' | 'WARN' | 'INSUFFICIENT_EVIDENCE';
    latestTraceId: string;
    nextAction: string;
    evidenceRefs: string[];
  }>;
  governance: Array<{
    key: string;
    label: string;
    status: 'PASS' | 'WARN' | 'INSUFFICIENT_EVIDENCE';
    action: string;
    reviewRoute: string;
    evidenceRefs: string[];
  }>;
}

interface MemoryBriefBody {
  facts: unknown[];
  weak_kcs: unknown[];
  style_notes: unknown[];
  as_of: string;
  stable_preferences: unknown[];
  struggle_patterns: unknown[];
  recent_wins: unknown[];
  intervention_hints: unknown[];
}

interface EvidenceGraphBody {
  nodes: unknown[];
  edges: unknown[];
  metrics: { nodeCount: number; edgeCount: number; touchedKcCount: number };
}

function u(path: string): string {
  return `${BASE_URL}${path}`;
}

function authHeaders(session: DemoSession): Record<string, string> {
  return { Authorization: `Bearer ${session.token}` };
}

async function postJson<T>(
  api: APIRequestContext,
  session: DemoSession,
  path: string,
  data: JsonMap = {},
): Promise<T> {
  const response = await api.post(u(path), {
    headers: authHeaders(session),
    data,
    timeout: 120_000,
  });
  if (!response.ok()) {
    throw new Error(`POST ${path} failed: HTTP ${response.status()} ${await response.text()}`);
  }
  return (await response.json()) as T;
}

async function getJson<T>(api: APIRequestContext, session: DemoSession, path: string): Promise<T> {
  const response = await api.get(u(path), {
    headers: authHeaders(session),
    timeout: 120_000,
  });
  if (!response.ok()) {
    throw new Error(`GET ${path} failed: HTTP ${response.status()} ${await response.text()}`);
  }
  return (await response.json()) as T;
}

async function createResource(
  api: APIRequestContext,
  session: DemoSession,
  kcCode: string,
  focusHint: string,
): Promise<number> {
  for (let attempt = 1; attempt <= 2; attempt += 1) {
    const response = await api.post(u('/api/workshop/resources'), {
      headers: authHeaders(session),
      data: {
        kcCodes: [kcCode],
        type: 'HANDOUT',
        focusHint,
      },
      timeout: 150_000,
    });
    if (response.ok()) {
      const body = (await response.json()) as ResourceCreateBody;
      expect(body.status, `resource run ${body.runId}`).not.toBe('FAILED');
      expect(body.resourceId).toBeGreaterThan(0);
      return body.resourceId;
    }
    const text = await response.text();
    if (response.status() === 502 && text.includes('WORKSHOP_GRAPH_UNAVAILABLE') && attempt < 2) {
      await sleep(5_000);
      continue;
    }
    throw new Error(`POST /api/workshop/resources failed: HTTP ${response.status()} ${text}`);
  }
  throw new Error('POST /api/workshop/resources failed after retries');
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

async function submitCode(
  api: APIRequestContext,
  session: DemoSession,
  code: string,
): Promise<SubmissionBody> {
  for (let attempt = 1; attempt <= 3; attempt += 1) {
    const response = await api.post(u('/api/submissions'), {
      headers: authHeaders(session),
      data: {
        problemId: PROBLEM_ID,
        source: 'TRAINING',
        code,
      },
      timeout: 120_000,
    });
    if (response.ok()) {
      return (await response.json()) as SubmissionBody;
    }
    const body = await response.text();
    if (response.status() === 429 && body.includes('SUBMISSION_THROTTLED') && attempt < 3) {
      await sleep(11_000);
      continue;
    }
    throw new Error(`POST /api/submissions failed: HTTP ${response.status()} ${body}`);
  }
  throw new Error('POST /api/submissions failed after retries');
}

async function retryBugMine(
  api: APIRequestContext,
  session: DemoSession,
  bugMineSessionId: number,
  code: string,
): Promise<SubmissionBody> {
  const path = `/api/bug-mine/sessions/${bugMineSessionId}/retry-submit`;
  for (let attempt = 1; attempt <= 3; attempt += 1) {
    const response = await api.post(u(path), {
      headers: authHeaders(session),
      data: { code },
      timeout: 120_000,
    });
    if (response.ok()) {
      return (await response.json()) as SubmissionBody;
    }
    const body = await response.text();
    if (response.status() === 429 && body.includes('SUBMISSION_THROTTLED') && attempt < 3) {
      await sleep(11_000);
      continue;
    }
    throw new Error(`POST ${path} failed: HTTP ${response.status()} ${body}`);
  }
  throw new Error(`POST ${path} failed after retries`);
}

async function submitCheck(
  api: APIRequestContext,
  session: DemoSession,
  resourceId: number,
  data: JsonMap,
): Promise<CheckBody> {
  return postJson<CheckBody>(api, session, `/api/workshop/resources/${resourceId}/checks`, data);
}

test.describe.configure({ mode: 'serial' });

test.describe('下一代 Agentic 深度闭环 E2E @agentic-deep', () => {
  let api: APIRequestContext;
  let session: DemoSession;
  let bootstrap: BootstrapBody;
  let mainResourceId = 0;
  let failResourceId = 0;
  let passedSubmission: SubmissionBody;
  let failedSubmission: SubmissionBody;
  let loopRun: LoopRunBody;
  let bugMineSession: BugMineSessionBody;

  test.beforeAll(async () => {
    test.setTimeout(360_000);
    api = await playwrightRequest.newContext({ baseURL: BASE_URL });
    session = await acquireStudentSession(api);
    bootstrap = await postJson<BootstrapBody>(api, session, '/api/game/bootstrap');
    mainResourceId = await createResource(api, session, KC_CODE, 'E2E 主资源：验证读、微测和证据闭环。');
    failResourceId = await createResource(api, session, KC_CODE, 'E2E 失败升级资源：连续失败应换资源。');
    failedSubmission = await submitCode(api, session, CODE_WA);
    passedSubmission = await submitCode(api, session, CODE_AC);
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('01 bootstrap 使用聚合启动接口返回世界、画像和学习集合', async () => {
    expect(bootstrap.world).toBeTruthy();
    expect(bootstrap.player.mapKey).toBe('town_hub');
    expect(bootstrap.profile).toBeTruthy();
    expect(Array.isArray(bootstrap.quests)).toBe(true);
    expect(Array.isArray(bootstrap.mastery)).toBe(true);
    expect(Array.isArray(bootstrap.path)).toBe(true);
  });

  test('02 legacy world session 仍可兼容但 bootstrap 更完整', async () => {
    const legacy = await getJson<JsonMap>(api, session, '/api/world/session');

    expect(legacy).toHaveProperty('worldSessionId');
    expect(bootstrap).toHaveProperty('activeSlot');
    expect(bootstrap).toHaveProperty('pendingEvents');
    expect(bootstrap.npcStates.length).toBeGreaterThan(0);
  });

  test('03 loop run 审计接受有界 run 并保留 trace', async () => {
    loopRun = await postJson<LoopRunBody>(api, session, '/api/loop/runs', {
      userId: session.user.id,
      sessionId: `agentic-e2e-${Date.now()}`,
      turn: 1,
      maxSteps: 4,
      stepsUsed: 3,
      replanCount: 1,
      toolsUsed: ['rag.query', 'judge.submit', 'workshop.check'],
      groundingPass: true,
      safetyPass: true,
      inputTokens: 120,
      outputTokens: 80,
      traceId: 'trace-agentic-e2e',
    });

    expect(loopRun.id).toBeGreaterThan(0);
    expect(loopRun.maxSteps).toBe(4);
    expect(loopRun.stepsUsed).toBe(3);
    expect(loopRun.traceId).toBe('trace-agentic-e2e');
  });

  test('04 loop run 可按本人读取且工具审计不丢失', async () => {
    const found = await getJson<LoopRunBody>(api, session, `/api/loop/runs/${loopRun.id}`);

    expect(found.userId).toBe(session.user.id);
    expect(found.toolsUsed).toEqual(['rag.query', 'judge.submit', 'workshop.check']);
    expect(found.groundingPass).toBe(true);
    expect(found.safetyPass).toBe(true);
  });

  test('05b loop runtime artifacts 可写入并恢复快照', async () => {
    const checkpoint = await postJson<{ id: number }>(api, session, `/api/loop/runs/${loopRun.id}/checkpoints`, {
      stepIndex: 2,
      status: 'READY',
      state: {
        node: 'dispatch',
        nextAction: 'resume micro-check after resource reading',
        evidenceRefs: [`resource:${mainResourceId}`, 'micro-check:pending'],
        replayReason: 'resource consumed -> micro-check pending',
      },
      resumeCursor: 'agentic-e2e-cursor',
    });
    await postJson(api, session, `/api/loop/runs/${loopRun.id}/tool-calls`, {
      checkpointId: checkpoint.id,
      toolName: 'resource.generate',
      requestPayload: { kcCode: KC_CODE },
      responsePayload: { status: 'SUCCESS' },
      status: 'SUCCESS',
      latencyMs: 120,
    });
    await postJson(api, session, `/api/loop/runs/${loopRun.id}/agent-runs`, {
      agentName: 'ResourceDesignerAgent',
      status: 'SUCCESS',
      inputSummary: '生成资源',
      outputArtifact: { resourceId: mainResourceId },
    });
    await postJson(api, session, `/api/loop/runs/${loopRun.id}/eval-results`, {
      datasetId: 'agentic-e2e-runtime',
      itemId: 'learner-case',
      loopType: 'LEARNER',
      policyVersion: 'agentic_loop_v1',
      metrics: { mean_uplift: 0.2 },
      passed: true,
    });

    const snapshot = await getJson<RuntimeSnapshotBody>(api, session, `/api/loop/runs/${loopRun.id}/runtime`);

    expect(snapshot.loopRun.id).toBe(loopRun.id);
    expect(snapshot.checkpoints[0].resumeCursor).toBe('agentic-e2e-cursor');
    expect(snapshot.toolCalls[0].toolName).toBe('resource.generate');
    expect(snapshot.agentRuns[0].agentName).toBe('ResourceDesignerAgent');
    expect(snapshot.evalResults[0].loopType).toBe('LEARNER');

    const replay = await getJson<ReplayPlanBody>(api, session, `/api/loop/runs/${loopRun.id}/replay`);

    expect(replay.loopRunId).toBe(loopRun.id);
    expect(replay.loopType).toBe('LEARNER');
    expect(replay.resumeCursor).toBe('agentic-e2e-cursor');
    expect(replay.nextAction).toContain('micro-check');
    expect(replay.evidenceRefs).toContain(`resource:${mainResourceId}`);
    expect(replay.replayable).toBe(true);
  });

  test('05 loop run 拒绝超过上限的自由循环', async () => {
    const response = await api.post(u('/api/loop/runs'), {
      headers: authHeaders(session),
      data: {
        userId: session.user.id,
        sessionId: 'agentic-e2e-unbounded',
        turn: 1,
        maxSteps: 99,
        stepsUsed: 1,
      },
    });

    expect(response.status()).toBe(400);
  });

  test('06 loop run 拒绝伪造他人 userId', async () => {
    const response = await api.post(u('/api/loop/runs'), {
      headers: authHeaders(session),
      data: {
        userId: session.user.id + 999_999,
        sessionId: 'agentic-e2e-cross-user',
        turn: 1,
        maxSteps: 4,
        stepsUsed: 1,
      },
    });

    expect(response.status()).toBe(403);
  });

  test('07 runtime summary 暴露 AG-UI 和证据 gate', async () => {
    const summary = await getJson<RuntimeSummaryBody>(api, session, '/api/ai/runtime/summary');

    expect(summary.protocol.aguiEventCount).toBeGreaterThan(0);
    expect(summary.protocol.toolsWithEvidenceFieldCount).toBeGreaterThanOrEqual(2);
    expect(summary.gates.map((gate) => gate.key)).toContain('protocol.evidence_fields');
  });

  test('07b runtime operations 暴露五类 loop 与治理项', async () => {
    const operations = await getJson<RuntimeOperationsBody>(api, session, '/api/ai/runtime/operations');

    expect(operations.loops.map((loop) => loop.loopType)).toEqual([
      'LEARNER',
      'DEBUG',
      'RESOURCE',
      'WORLD',
      'IMPROVE',
    ]);
    expect(operations.loops.every((loop) => loop.label && loop.nextAction)).toBe(true);
    expect(operations.governance.map((item) => item.key)).toEqual([
      'resource_review',
      'world_patch_review',
      'policy_canary_review',
    ]);
    expect(operations.governance.every((item) => item.action && item.reviewRoute)).toBe(true);
  });

  test('08 memory brief 返回深层个性化字段', async () => {
    const brief = await getJson<MemoryBriefBody>(
      api,
      session,
      '/api/learner/memory/brief?query=%E5%BE%AA%E7%8E%AF',
    );

    expect(Array.isArray(brief.facts)).toBe(true);
    expect(Array.isArray(brief.weak_kcs)).toBe(true);
    expect(Array.isArray(brief.style_notes)).toBe(true);
    expect(brief.as_of).toMatch(/T/);
    expect(Array.isArray(brief.stable_preferences)).toBe(true);
    expect(Array.isArray(brief.struggle_patterns)).toBe(true);
    expect(Array.isArray(brief.recent_wins)).toBe(true);
    expect(Array.isArray(brief.intervention_hints)).toBe(true);
  });

  test('09 evidence graph 可聚合真实学习事件', async () => {
    const graph = await getJson<EvidenceGraphBody>(
      api,
      session,
      `/api/learner/evidence-graph?focusKc=${KC_CODE}&limit=80`,
    );

    expect(Array.isArray(graph.nodes)).toBe(true);
    expect(Array.isArray(graph.edges)).toBe(true);
    expect(graph.metrics.nodeCount).toBeGreaterThanOrEqual(0);
    expect(graph.metrics.touchedKcCount).toBeGreaterThanOrEqual(0);
  });

  test('10 resource list 包含本轮真实生成资源', async () => {
    const list = await getJson<Array<{ id: number; kcCodes: string[] }>>(
      api,
      session,
      `/api/workshop/resources?kcCode=${KC_CODE}&limit=30`,
    );

    expect(list.map((item) => item.id)).toContain(mainResourceId);
    expect(list.some((item) => item.kcCodes.includes(KC_CODE))).toBe(true);
  });

  test('11 resource detail 保留 grounding/safety 与证据页', async () => {
    const resource = await getJson<ResourceBody>(api, session, `/api/workshop/resources/${mainResourceId}`);

    expect(resource.id).toBe(mainResourceId);
    expect(resource.kcCodes).toContain(KC_CODE);
    expect(['PASSED', 'FAILED', 'NO_EVIDENCE']).toContain(resource.groundingStatus);
    expect(['PASSED', 'FAILED', 'NO_EVIDENCE']).toContain(resource.safetyStatus);
    expect(Array.isArray(resource.evidencePageIds)).toBe(true);
  });

  test('12 resource consumption 记录阅读并要求下一步证据', async () => {
    const consumption = await postJson<ConsumptionBody>(
      api,
      session,
      `/api/workshop/resources/${mainResourceId}/consumptions`,
    );

    expect(consumption.resourceId).toBe(mainResourceId);
    expect(consumption.readRecorded).toBe(true);
    expect(consumption.nextAction).toBe('micro_check');
    expect(consumption.loopState).toBe('MICRO_CHECK_REQUIRED');
    expect(consumption.evidenceRequired).toContain('micro_check');
  });

  test('13 micro quiz 命中资源内容后通过闭环', async () => {
    const resource = await getJson<ResourceBody>(api, session, `/api/workshop/resources/${mainResourceId}`);
    const summary = resource.summary || 'input 函数用于读取用户输入';
    const result = await submitCheck(api, session, mainResourceId, {
      kcCode: KC_CODE,
      evidenceType: 'MICRO_QUIZ',
      answerText: `我读懂的要点是：${summary}。我会再用正式练习证据证明。`,
    });

    expect(result.status).toBe('PASSED');
    expect(result.nextAction).toBe('pass');
    expect(result.loopState).toBe('PASSED');
    expect(result.masteryDelta).toBeGreaterThan(0);
  });

  test('13b resource effectiveness 暴露发布后反馈与效果信号', async () => {
    const effectiveness = await getJson<ResourceEffectivenessBody>(
      api,
      session,
      `/api/workshop/resources/${mainResourceId}/effectiveness`,
    );

    expect(effectiveness.resourceId).toBe(mainResourceId);
    expect(effectiveness.evaluationHook).toBe('resource_feedback_or_check');
    expect(effectiveness.checkCount).toBeGreaterThanOrEqual(1);
    expect(effectiveness.passedCheckCount).toBeGreaterThanOrEqual(1);
    expect(effectiveness.passRate).toBeGreaterThan(0);
    expect(effectiveness.masteryDeltaSum).toBeGreaterThan(0);
    expect(Array.isArray(effectiveness.evidencePageIds)).toBe(true);
    expect(effectiveness.signal).toBe('EFFECTIVE');
  });

  test('14 micro quiz 首次失败进入补救而不是假装通过', async () => {
    const result = await submitCheck(api, session, failResourceId, {
      kcCode: KC_CODE,
      evidenceType: 'MICRO_QUIZ',
      answerText: '完全没懂',
    });

    expect(result.status).toBe('FAILED');
    expect(result.nextAction).toBe('remediate');
    expect(result.loopState).toBe('NEED_REMEDIATION');
    expect(result.masteryDelta).toBeLessThan(0);
  });

  test('15 micro quiz 连续失败升级到换资源', async () => {
    const result = await submitCheck(api, session, failResourceId, {
      kcCode: KC_CODE,
      evidenceType: 'MICRO_QUIZ',
      answerText: '还是不明白',
    });

    expect(result.status).toBe('FAILED');
    expect(result.nextAction).toBe('swap_resource');
    expect(result.loopState).toBe('NEED_RESOURCE_SWAP');
    expect(result.remediationType).toBe('CODE_LAB');
  });

  test('16 operation evidence 无正式提交时不写入掌握度', async () => {
    const result = await submitCheck(api, session, mainResourceId, {
      kcCode: KC_CODE,
      evidenceType: 'OPERATION',
      answerText: '我用 input 和 print 完成了一个可运行练习。',
      operationEvidence: {
        code: 'radius = float(input())\nprint(radius)',
        output: '2.0',
      },
    });

    expect(result.status).toBe('FAILED');
    expect(result.nextAction).toBe('remediate');
    expect(result.masteryDelta).toBeLessThan(0);
    expect(result.decisionReason).toContain('先补一轮练习');
  });

  test('17 failed formal submission drives replan', async () => {
    expect(failedSubmission.status).not.toBe('PASSED');
    const result = await submitCheck(api, session, mainResourceId, {
      kcCode: KC_CODE,
      evidenceType: 'OPERATION',
      answerText: `训练场提交 #${failedSubmission.id}`,
      operationEvidence: {
        submissionId: failedSubmission.id,
        status: failedSubmission.status,
      },
    });

    expect(result.status).toBe('FAILED');
    expect(result.nextAction).toBe('replan');
    expect(result.loopState).toBe('NEED_REPLAN');
    expect(result.practiceSubmissionId).toBe(failedSubmission.id);
  });

  test('18 passed formal submission closes the loop', async () => {
    expect(passedSubmission.status).toBe('PASSED');
    const result = await submitCheck(api, session, mainResourceId, {
      kcCode: KC_CODE,
      evidenceType: 'OPERATION',
      answerText: `训练场提交 #${passedSubmission.id}`,
      operationEvidence: {
        submissionId: passedSubmission.id,
        status: passedSubmission.status,
      },
    });

    expect(result.status).toBe('PASSED');
    expect(result.nextAction).toBe('pass');
    expect(result.practiceSubmissionId).toBe(passedSubmission.id);
  });

  test('19 submission evidence 不能串到不匹配 KC 的资源', async () => {
    const response = await api.post(u(`/api/workshop/resources/${mainResourceId}/checks`), {
      headers: authHeaders(session),
      data: {
        kcCode: ALT_KC_CODE,
        evidenceType: 'OPERATION',
        answerText: `训练场提交 #${passedSubmission.id}`,
        operationEvidence: { submissionId: passedSubmission.id },
      },
    });

    expect(response.status()).toBe(400);
    expect(await response.text()).toContain('RESOURCE_CHECK_KC_MISMATCH');
  });

  test('20 submission list 可按 PASSED 过滤到正式通过提交', async () => {
    const list = await getJson<SubmissionBody[]>(
      api,
      session,
      `/api/submissions?problemId=${PROBLEM_ID}&status=PASSED&limit=20`,
    );

    expect(list.map((item) => item.id)).toContain(passedSubmission.id);
    expect(list.every((item) => item.status === 'PASSED')).toBe(true);
  });

  test('21 submission detail 返回正式判题结果', async () => {
    const detail = await getJson<{ submission: SubmissionBody; judgeResult: JsonMap }>(
      api,
      session,
      `/api/submissions/${passedSubmission.id}`,
    );

    expect(detail.submission.id).toBe(passedSubmission.id);
    expect(detail.submission.status).toBe('PASSED');
    expect(detail.judgeResult).toBeTruthy();
  });

  test('22 failed submission can open Bug Mine debug loop', async () => {
    bugMineSession = await postJson<BugMineSessionBody>(api, session, '/api/bug-mine/sessions', {
      submissionId: failedSubmission.id,
    });

    expect(bugMineSession.id).toBeGreaterThan(0);
    expect(bugMineSession.submissionId).toBe(failedSubmission.id);
    expect(bugMineSession.cards.length).toBeGreaterThan(0);
  });

  test('23 Bug Mine card reveal is layered rather than pre-revealed', async () => {
    const hidden = bugMineSession.cards.find((card) => !card.revealed) ?? bugMineSession.cards[0];
    const response = await api.post(
      u(`/api/bug-mine/sessions/${bugMineSession.id}/cards/${hidden.id}/reveal`),
      { headers: authHeaders(session) },
    );
    const body = (await response.json()) as { code: string };

    expect(bugMineSession.cards.every((card) => !card.revealed)).toBe(true);
    expect(response.status()).toBe(409);
    expect(body.code).toBe('BUG_MINE_DEPTH_LOCKED');
  });

  test('24 Bug Mine retry with AC code records recovery attempt', async () => {
    const retry = await retryBugMine(api, session, bugMineSession.id, CODE_AC);

    expect(retry.id).toBeGreaterThan(0);
    expect(['PASSED', 'FAILED', 'ERROR']).toContain(retry.status);
  });

  test('25 quests today endpoint stays evidence-shaped', async () => {
    const quests = await getJson<Array<{ id: number; kcCode: string | null; requiredEvidenceType: string | null }>>(
      api,
      session,
      '/api/quests/today',
    );

    expect(Array.isArray(quests)).toBe(true);
    for (const quest of quests) {
      expect(quest).toHaveProperty('id');
      expect(quest).toHaveProperty('requiredEvidenceType');
    }
  });

  test('26 recommendations endpoint remains readable after evidence events', async () => {
    const recommendations = await getJson<JsonMap[]>(api, session, '/api/learner/recommendations');

    expect(Array.isArray(recommendations)).toBe(true);
  });

  test('27 workshop browser rail is compact and action-first', async ({ browser }) => {
    const context = await browser.newContext();
    await injectSession(context, session);
    await suppressFirstVisitOverlays(context);
    const page = await context.newPage();
    try {
      await page.goto('/workshop', { waitUntil: 'domcontentloaded' });
      await expect(page.getByTestId('workshop-page')).toBeVisible({ timeout: 60_000 });
      await page.getByTestId('workshop-open-study').click();
      await expect(page.getByTestId('workshop-drawer-study')).toBeVisible({ timeout: 30_000 });
      await page.getByTestId(`resource-list-item-${mainResourceId}`).click();
      await expect(page.getByTestId('resource-modal')).toBeVisible({ timeout: 30_000 });
      await expect(page.getByTestId('resource-loop-rail')).toContainText('读');
      await expect(page.getByTestId('resource-loop-rail')).toContainText('测');
      await expect(page.getByTestId('resource-loop-rail')).toContainText('判');
      const modalCards = await page.locator('[data-testid="resource-modal"] .card').count();
      expect(modalCards).toBeLessThanOrEqual(2);
    } finally {
      await context.close();
    }
  });

  test('28 workshop progress drawer stays student-facing', async ({ browser }) => {
    const context = await browser.newContext();
    await injectSession(context, session);
    await suppressFirstVisitOverlays(context);
    const page = await context.newPage();
    try {
      await page.goto('/workshop', { waitUntil: 'domcontentloaded' });
      await expect(page.getByTestId('workshop-page')).toBeVisible({ timeout: 60_000 });
      await page.getByTestId('workshop-open-calls').click();
      await expect(page.getByTestId('workshop-drawer-calls')).toBeVisible({ timeout: 30_000 });
      await expect(page.getByTestId('workshop-process')).toContainText('还没有处理记录', { timeout: 30_000 });
      await expect(page.getByTestId('workshop-drawer-calls')).not.toContainText(/MCP 工具|策略灰度|闭环运维/);
    } finally {
      await context.close();
    }
  });

  test('29 workshop browser can submit micro-check from compact rail', async ({ browser }) => {
    const context = await browser.newContext();
    await injectSession(context, session);
    await suppressFirstVisitOverlays(context);
    const page = await context.newPage();
    try {
      await page.goto('/workshop', { waitUntil: 'domcontentloaded' });
      await page.getByTestId('workshop-open-study').click();
      await page.getByTestId(`resource-list-item-${mainResourceId}`).click();
      await expect(page.getByTestId('resource-modal')).toBeVisible({ timeout: 30_000 });
      await page.getByTestId('resource-check-answer').fill(`${KC_CODE} 我能说明资源关键点`);
      await page.getByTestId('resource-check-submit').click();
      await expect(page.getByTestId('resource-loop-state')).toContainText(/通过|补救|换资源|重规划/, {
        timeout: 30_000,
      });
      await expect(page.getByTestId('resource-check-result')).toBeVisible();
    } finally {
      await context.close();
    }
  });
});
