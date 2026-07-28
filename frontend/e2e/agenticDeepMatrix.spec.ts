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
type RuntimeOperationStatus = 'PASS' | 'WARN' | 'INSUFFICIENT_EVIDENCE';

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
  checkpoints: Array<{ id: number; resumeCursor: string; stepIndex: number; status: string }>;
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
    status: RuntimeOperationStatus;
    latestTraceId: string;
    nextAction: string;
    evidenceRefs: string[];
  }>;
  governance: Array<{
    key: string;
    label: string;
    status: RuntimeOperationStatus;
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

interface BugMineSessionBody {
  id: number;
  submissionId: number;
  status: string;
  cards: Array<{ id: number; revealed: boolean; cardType: string }>;
}

interface MatrixContext {
  api: APIRequestContext;
  session: DemoSession;
  bootstrap: BootstrapBody;
  legacyWorld: JsonMap;
  loopRun: LoopRunBody;
  snapshot: RuntimeSnapshotBody;
  replay: ReplayPlanBody;
  summary: RuntimeSummaryBody;
  operations: RuntimeOperationsBody;
  memory: MemoryBriefBody;
  evidenceGraph: EvidenceGraphBody;
  mainResourceId: number;
  failResourceId: number;
  resourceList: Array<{ id: number; kcCodes: string[] }>;
  resource: ResourceBody;
  consumption: ConsumptionBody;
  passCheck: CheckBody;
  effectiveness: ResourceEffectivenessBody;
  failCheck: CheckBody;
  swapCheck: CheckBody;
  noSubmissionCheck: CheckBody;
  failedOperationCheck: CheckBody;
  passedOperationCheck: CheckBody;
  failedSubmission: SubmissionBody;
  passedSubmission: SubmissionBody;
  mismatchStatus: number;
  mismatchBody: string;
  passedSubmissionList: SubmissionBody[];
  submissionDetail: { submission: SubmissionBody; judgeResult: JsonMap };
  bugMineSession: BugMineSessionBody;
  bugRevealStatus: number;
  bugRevealBody: { code?: string };
  bugRetry: SubmissionBody;
  quests: Array<{ id: number; kcCode: string | null; requiredEvidenceType: string | null }>;
  recommendations: JsonMap[];
}

interface MatrixCase {
  id: string;
  group: string;
  title: string;
  check: (ctx: MatrixContext) => void | Promise<void>;
}

function u(path: string): string {
  return `${BASE_URL}${path}`;
}

function authHeaders(session: DemoSession): Record<string, string> {
  return { Authorization: `Bearer ${session.token}` };
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
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
      timeout: 210_000,
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

async function submitCode(api: APIRequestContext, session: DemoSession, code: string): Promise<SubmissionBody> {
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

function expectNonEmptyString(value: string): void {
  expect(value.trim().length).toBeGreaterThan(0);
}

function expectValidOperationStatus(value: RuntimeOperationStatus): void {
  expect(['PASS', 'WARN', 'INSUFFICIENT_EVIDENCE']).toContain(value);
}

test.describe.configure({ mode: 'serial' });

test.describe('Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix', () => {
  let ctx: MatrixContext;

  test.beforeAll(async () => {
    test.setTimeout(600_000);
    const api = await playwrightRequest.newContext({ baseURL: BASE_URL });
    const session = await acquireStudentSession(api);
    const bootstrap = await postJson<BootstrapBody>(api, session, '/api/game/bootstrap');
    const legacyWorld = await getJson<JsonMap>(api, session, '/api/world/session');
    const mainResourceId = await createResource(api, session, KC_CODE, 'E2E 100+矩阵：验证完整学习闭环。');
    const failResourceId = await createResource(api, session, KC_CODE, 'E2E 100+矩阵：验证补救和换资源分支。');
    const failedSubmission = await submitCode(api, session, CODE_WA);
    const passedSubmission = await submitCode(api, session, CODE_AC);

    const loopRun = await postJson<LoopRunBody>(api, session, '/api/loop/runs', {
      userId: session.user.id,
      sessionId: `agentic-matrix-${Date.now()}`,
      turn: 1,
      maxSteps: 4,
      stepsUsed: 3,
      replanCount: 1,
      toolsUsed: ['rag.query', 'judge.submit', 'workshop.check'],
      groundingPass: true,
      safetyPass: true,
      inputTokens: 160,
      outputTokens: 96,
      traceId: 'trace-agentic-matrix',
    });
    const checkpoint = await postJson<{ id: number }>(api, session, `/api/loop/runs/${loopRun.id}/checkpoints`, {
      stepIndex: 2,
      status: 'READY',
      state: {
        node: 'dispatch',
        nextAction: 'resume micro-check after resource reading',
        evidenceRefs: [`resource:${mainResourceId}`, 'micro-check:pending'],
      },
      resumeCursor: 'agentic-matrix-cursor',
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
      datasetId: 'agentic-e2e-runtime-matrix',
      itemId: 'learner-case',
      loopType: 'LEARNER',
      policyVersion: 'agentic_loop_v1',
      metrics: { mean_uplift: 0.2 },
      passed: true,
    });

    const snapshot = await getJson<RuntimeSnapshotBody>(api, session, `/api/loop/runs/${loopRun.id}/runtime`);
    const replay = await getJson<ReplayPlanBody>(api, session, `/api/loop/runs/${loopRun.id}/replay`);
    const summary = await getJson<RuntimeSummaryBody>(api, session, '/api/ai/runtime/summary');
    const operations = await getJson<RuntimeOperationsBody>(api, session, '/api/ai/runtime/operations');
    const memory = await getJson<MemoryBriefBody>(api, session, '/api/learner/memory/brief?query=%E5%BE%AA%E7%8E%AF');
    const evidenceGraph = await getJson<EvidenceGraphBody>(
      api,
      session,
      `/api/learner/evidence-graph?focusKc=${KC_CODE}&limit=80`,
    );
    const resourceList = await getJson<Array<{ id: number; kcCodes: string[] }>>(
      api,
      session,
      `/api/workshop/resources?kcCode=${KC_CODE}&limit=30`,
    );
    const resource = await getJson<ResourceBody>(api, session, `/api/workshop/resources/${mainResourceId}`);
    const consumption = await postJson<ConsumptionBody>(
      api,
      session,
      `/api/workshop/resources/${mainResourceId}/consumptions`,
    );
    const summaryText = resource.summary || 'input 函数用于读取用户输入';
    const passCheck = await submitCheck(api, session, mainResourceId, {
      kcCode: KC_CODE,
      evidenceType: 'MICRO_QUIZ',
      answerText: `我读懂的要点是：${summaryText}。我会再用正式练习证据证明。`,
    });
    const effectiveness = await getJson<ResourceEffectivenessBody>(
      api,
      session,
      `/api/workshop/resources/${mainResourceId}/effectiveness`,
    );
    const failCheck = await submitCheck(api, session, failResourceId, {
      kcCode: KC_CODE,
      evidenceType: 'MICRO_QUIZ',
      answerText: '完全没懂',
    });
    const swapCheck = await submitCheck(api, session, failResourceId, {
      kcCode: KC_CODE,
      evidenceType: 'MICRO_QUIZ',
      answerText: '还是不明白',
    });
    const noSubmissionCheck = await submitCheck(api, session, mainResourceId, {
      kcCode: KC_CODE,
      evidenceType: 'OPERATION',
      answerText: '我用 input 和 print 完成了一个可运行练习。',
      operationEvidence: {
        code: 'radius = float(input())\nprint(radius)',
        output: '2.0',
      },
    });
    const failedOperationCheck = await submitCheck(api, session, mainResourceId, {
      kcCode: KC_CODE,
      evidenceType: 'OPERATION',
      answerText: `训练场提交 #${failedSubmission.id}`,
      operationEvidence: {
        submissionId: failedSubmission.id,
        status: failedSubmission.status,
      },
    });
    const passedOperationCheck = await submitCheck(api, session, mainResourceId, {
      kcCode: KC_CODE,
      evidenceType: 'OPERATION',
      answerText: `训练场提交 #${passedSubmission.id}`,
      operationEvidence: {
        submissionId: passedSubmission.id,
        status: passedSubmission.status,
      },
    });
    const mismatch = await api.post(u(`/api/workshop/resources/${mainResourceId}/checks`), {
      headers: authHeaders(session),
      data: {
        kcCode: ALT_KC_CODE,
        evidenceType: 'OPERATION',
        answerText: `训练场提交 #${passedSubmission.id}`,
        operationEvidence: { submissionId: passedSubmission.id },
      },
    });
    const mismatchBody = await mismatch.text();
    const passedSubmissionList = await getJson<SubmissionBody[]>(
      api,
      session,
      `/api/submissions?problemId=${PROBLEM_ID}&status=PASSED&limit=20`,
    );
    const submissionDetail = await getJson<{ submission: SubmissionBody; judgeResult: JsonMap }>(
      api,
      session,
      `/api/submissions/${passedSubmission.id}`,
    );
    const bugMineSession = await postJson<BugMineSessionBody>(api, session, '/api/bug-mine/sessions', {
      submissionId: failedSubmission.id,
    });
    const hidden = bugMineSession.cards.find((card) => !card.revealed) ?? bugMineSession.cards[0];
    const reveal = await api.post(u(`/api/bug-mine/sessions/${bugMineSession.id}/cards/${hidden.id}/reveal`), {
      headers: authHeaders(session),
    });
    const bugRevealBody = (await reveal.json()) as { code?: string };
    const bugRetry = await retryBugMine(api, session, bugMineSession.id, CODE_AC);
    const quests = await getJson<Array<{ id: number; kcCode: string | null; requiredEvidenceType: string | null }>>(
      api,
      session,
      '/api/quests/today',
    );
    const recommendations = await getJson<JsonMap[]>(api, session, '/api/learner/recommendations');

    ctx = {
      api,
      session,
      bootstrap,
      legacyWorld,
      loopRun,
      snapshot,
      replay,
      summary,
      operations,
      memory,
      evidenceGraph,
      mainResourceId,
      failResourceId,
      resourceList,
      resource,
      consumption,
      passCheck,
      effectiveness,
      failCheck,
      swapCheck,
      noSubmissionCheck,
      failedOperationCheck,
      passedOperationCheck,
      failedSubmission,
      passedSubmission,
      mismatchStatus: mismatch.status(),
      mismatchBody,
      passedSubmissionList,
      submissionDetail,
      bugMineSession,
      bugRevealStatus: reveal.status(),
      bugRevealBody,
      bugRetry,
      quests,
      recommendations,
    };
  });

  test.afterAll(async () => {
    await ctx?.api.dispose();
  });

  const matrixCases: MatrixCase[] = [
    { id: 'M001', group: 'Session', title: '真实学生 token 存在', check: (c) => expectNonEmptyString(c.session.token) },
    { id: 'M002', group: 'Session', title: '学生用户 id 有效', check: (c) => expect(c.session.user.id).toBeGreaterThan(0) },
    { id: 'M003', group: 'Session', title: '学生角色正确', check: (c) => expect(c.session.user.role).toBe('STUDENT') },
    { id: 'M004', group: 'Bootstrap', title: '聚合启动返回 world', check: (c) => expect(c.bootstrap.world).toBeTruthy() },
    { id: 'M005', group: 'Bootstrap', title: '玩家主地图为 town_hub', check: (c) => expect(c.bootstrap.player.mapKey).toBe('town_hub') },
    { id: 'M006', group: 'Bootstrap', title: '玩家 tileX 为数字', check: (c) => expect(typeof c.bootstrap.player.tileX).toBe('number') },
    { id: 'M007', group: 'Bootstrap', title: '玩家 tileY 为数字', check: (c) => expect(typeof c.bootstrap.player.tileY).toBe('number') },
    { id: 'M008', group: 'Bootstrap', title: '画像载入成功', check: (c) => expect(c.bootstrap.profile).toBeTruthy() },
    { id: 'M009', group: 'Bootstrap', title: '任务集合为数组', check: (c) => expect(Array.isArray(c.bootstrap.quests)).toBe(true) },
    { id: 'M010', group: 'Bootstrap', title: '掌握度集合为数组', check: (c) => expect(Array.isArray(c.bootstrap.mastery)).toBe(true) },
    { id: 'M011', group: 'Bootstrap', title: '路径集合为数组', check: (c) => expect(Array.isArray(c.bootstrap.path)).toBe(true) },
    { id: 'M012', group: 'Bootstrap', title: '技能集合为数组', check: (c) => expect(Array.isArray(c.bootstrap.skills)).toBe(true) },
    { id: 'M013', group: 'Bootstrap', title: 'NPC 状态已载入', check: (c) => expect(c.bootstrap.npcStates.length).toBeGreaterThan(0) },
    { id: 'M014', group: 'Bootstrap', title: 'activeSlot 字段存在', check: (c) => expect(c.bootstrap).toHaveProperty('activeSlot') },
    { id: 'M015', group: 'Bootstrap', title: 'pendingEvents 字段为数组', check: (c) => expect(Array.isArray(c.bootstrap.pendingEvents)).toBe(true) },
    { id: 'M016', group: 'World', title: 'legacy world session 兼容', check: (c) => expect(c.legacyWorld).toHaveProperty('worldSessionId') },
    { id: 'M017', group: 'Runtime', title: 'loop run id 有效', check: (c) => expect(c.loopRun.id).toBeGreaterThan(0) },
    { id: 'M018', group: 'Runtime', title: 'loop run 归属当前学生', check: (c) => expect(c.loopRun.userId).toBe(c.session.user.id) },
    { id: 'M019', group: 'Runtime', title: 'loop run maxSteps 有界', check: (c) => expect(c.loopRun.maxSteps).toBe(4) },
    { id: 'M020', group: 'Runtime', title: 'loop run stepsUsed 记录', check: (c) => expect(c.loopRun.stepsUsed).toBe(3) },
    { id: 'M021', group: 'Runtime', title: 'toolsUsed 包含 RAG', check: (c) => expect(c.loopRun.toolsUsed).toContain('rag.query') },
    { id: 'M022', group: 'Runtime', title: 'toolsUsed 包含 judge', check: (c) => expect(c.loopRun.toolsUsed).toContain('judge.submit') },
    { id: 'M023', group: 'Runtime', title: 'toolsUsed 包含 workshop', check: (c) => expect(c.loopRun.toolsUsed).toContain('workshop.check') },
    { id: 'M024', group: 'Runtime', title: 'grounding gate 通过', check: (c) => expect(c.loopRun.groundingPass).toBe(true) },
    { id: 'M025', group: 'Runtime', title: 'safety gate 通过', check: (c) => expect(c.loopRun.safetyPass).toBe(true) },
    { id: 'M026', group: 'Runtime', title: 'traceId 保留', check: (c) => expect(c.loopRun.traceId).toBe('trace-agentic-matrix') },
    { id: 'M027', group: 'Runtime Snapshot', title: 'snapshot 指向同一 run', check: (c) => expect(c.snapshot.loopRun.id).toBe(c.loopRun.id) },
    { id: 'M028', group: 'Runtime Snapshot', title: 'checkpoint 已持久化', check: (c) => expect(c.snapshot.checkpoints.length).toBeGreaterThan(0) },
    { id: 'M029', group: 'Runtime Snapshot', title: 'checkpoint cursor 可恢复', check: (c) => expect(c.snapshot.checkpoints[0].resumeCursor).toBe('agentic-matrix-cursor') },
    { id: 'M030', group: 'Runtime Snapshot', title: 'checkpoint stepIndex 正确', check: (c) => expect(c.snapshot.checkpoints[0].stepIndex).toBe(2) },
    { id: 'M031', group: 'Runtime Snapshot', title: 'checkpoint 状态记录', check: (c) => expect(c.snapshot.checkpoints[0].status).toBe('READY') },
    { id: 'M032', group: 'Runtime Snapshot', title: 'tool call 已记录', check: (c) => expect(c.snapshot.toolCalls.length).toBeGreaterThan(0) },
    { id: 'M033', group: 'Runtime Snapshot', title: 'tool call 名称正确', check: (c) => expect(c.snapshot.toolCalls[0].toolName).toBe('resource.generate') },
    { id: 'M034', group: 'Runtime Snapshot', title: 'tool call 状态成功', check: (c) => expect(c.snapshot.toolCalls[0].status).toBe('SUCCESS') },
    { id: 'M035', group: 'Runtime Snapshot', title: 'agent run 已记录', check: (c) => expect(c.snapshot.agentRuns.length).toBeGreaterThan(0) },
    { id: 'M036', group: 'Runtime Snapshot', title: 'agent run 名称正确', check: (c) => expect(c.snapshot.agentRuns[0].agentName).toBe('ResourceDesignerAgent') },
    { id: 'M037', group: 'Runtime Snapshot', title: 'agent run 状态成功', check: (c) => expect(c.snapshot.agentRuns[0].status).toBe('SUCCESS') },
    { id: 'M038', group: 'Runtime Snapshot', title: 'eval result 已记录', check: (c) => expect(c.snapshot.evalResults.length).toBeGreaterThan(0) },
    { id: 'M039', group: 'Runtime Snapshot', title: 'eval result loopType 正确', check: (c) => expect(c.snapshot.evalResults[0].loopType).toBe('LEARNER') },
    { id: 'M040', group: 'Runtime Snapshot', title: 'eval result 通过', check: (c) => expect(c.snapshot.evalResults[0].passed).toBe(true) },
    { id: 'M041', group: 'Replay', title: 'replay 指向同一 run', check: (c) => expect(c.replay.loopRunId).toBe(c.loopRun.id) },
    { id: 'M042', group: 'Replay', title: 'replay loopType 正确', check: (c) => expect(c.replay.loopType).toBe('LEARNER') },
    { id: 'M043', group: 'Replay', title: 'replay cursor 正确', check: (c) => expect(c.replay.resumeCursor).toBe('agentic-matrix-cursor') },
    { id: 'M044', group: 'Replay', title: 'replay nextAction 指向微测', check: (c) => expect(c.replay.nextAction).toContain('micro-check') },
    { id: 'M045', group: 'Replay', title: 'replay evidence 包含资源', check: (c) => expect(c.replay.evidenceRefs).toContain(`resource:${c.mainResourceId}`) },
    { id: 'M046', group: 'Replay', title: 'replay 标记可恢复', check: (c) => expect(c.replay.replayable).toBe(true) },
    { id: 'M047', group: 'Summary', title: 'AG-UI 事件数量有效', check: (c) => expect(c.summary.protocol.aguiEventCount).toBeGreaterThan(0) },
    { id: 'M048', group: 'Summary', title: '工具 evidence 字段达标', check: (c) => expect(c.summary.protocol.toolsWithEvidenceFieldCount).toBeGreaterThanOrEqual(2) },
    { id: 'M049', group: 'Summary', title: '协议 evidence gate 存在', check: (c) => expect(c.summary.gates.map((gate) => gate.key)).toContain('protocol.evidence_fields') },
    { id: 'M050', group: 'Summary', title: '证据图节点数有效', check: (c) => expect(c.summary.evidence.nodeCount).toBeGreaterThanOrEqual(0) },
    { id: 'M051', group: 'Summary', title: '证据完整度有效', check: (c) => expect(c.summary.evidence.graphCompletenessScore).toBeGreaterThanOrEqual(0) },
    { id: 'M052', group: 'Summary', title: 'Twin memory 计数有效', check: (c) => expect(c.summary.learnerTwin.memoryEventCount).toBeGreaterThanOrEqual(0) },
    { id: 'M053', group: 'Summary', title: 'Twin failed practice 计数有效', check: (c) => expect(c.summary.learnerTwin.failedPracticeCount).toBeGreaterThanOrEqual(0) },
    { id: 'M054', group: 'Summary', title: 'Twin succeeded practice 计数有效', check: (c) => expect(c.summary.learnerTwin.succeededPracticeCount).toBeGreaterThanOrEqual(0) },
    { id: 'M055', group: 'Operations', title: '五类 loop 数量正确', check: (c) => expect(c.operations.loops.length).toBe(5) },
    { id: 'M056', group: 'Operations', title: '五类 loop 顺序正确', check: (c) => expect(c.operations.loops.map((loop) => loop.loopType)).toEqual(['LEARNER', 'DEBUG', 'RESOURCE', 'WORLD', 'IMPROVE']) },
    { id: 'M057', group: 'Operations', title: 'loop label 非空', check: (c) => expect(c.operations.loops.every((loop) => loop.label.trim().length > 0)).toBe(true) },
    { id: 'M058', group: 'Operations', title: 'loop status 合法', check: (c) => c.operations.loops.forEach((loop) => expectValidOperationStatus(loop.status)) },
    { id: 'M059', group: 'Operations', title: 'loop trace 字段存在', check: (c) => c.operations.loops.forEach((loop) => expect(typeof loop.latestTraceId).toBe('string')) },
    { id: 'M060', group: 'Operations', title: 'loop nextAction 非空', check: (c) => expect(c.operations.loops.every((loop) => loop.nextAction.trim().length > 0)).toBe(true) },
    { id: 'M061', group: 'Operations', title: 'governance 数量正确', check: (c) => expect(c.operations.governance.length).toBe(3) },
    { id: 'M062', group: 'Operations', title: 'governance key 正确', check: (c) => expect(c.operations.governance.map((item) => item.key)).toEqual(['resource_review', 'world_patch_review', 'policy_canary_review']) },
    { id: 'M063', group: 'Operations', title: 'governance status 合法', check: (c) => c.operations.governance.forEach((item) => expectValidOperationStatus(item.status)) },
    { id: 'M064', group: 'Operations', title: 'governance action 非空', check: (c) => expect(c.operations.governance.every((item) => item.action.trim().length > 0)).toBe(true) },
    { id: 'M065', group: 'Operations', title: 'governance reviewRoute 非空', check: (c) => expect(c.operations.governance.every((item) => item.reviewRoute.trim().length > 0)).toBe(true) },
    { id: 'M066', group: 'Memory', title: 'facts 为数组', check: (c) => expect(Array.isArray(c.memory.facts)).toBe(true) },
    { id: 'M067', group: 'Memory', title: 'weak_kcs 为数组', check: (c) => expect(Array.isArray(c.memory.weak_kcs)).toBe(true) },
    { id: 'M068', group: 'Memory', title: 'style_notes 为数组', check: (c) => expect(Array.isArray(c.memory.style_notes)).toBe(true) },
    { id: 'M069', group: 'Memory', title: 'as_of 为 ISO 时间', check: (c) => expect(c.memory.as_of).toMatch(/T/) },
    { id: 'M070', group: 'Memory', title: 'stable_preferences 为数组', check: (c) => expect(Array.isArray(c.memory.stable_preferences)).toBe(true) },
    { id: 'M071', group: 'Memory', title: 'struggle_patterns 为数组', check: (c) => expect(Array.isArray(c.memory.struggle_patterns)).toBe(true) },
    { id: 'M072', group: 'Memory', title: 'recent_wins 为数组', check: (c) => expect(Array.isArray(c.memory.recent_wins)).toBe(true) },
    { id: 'M073', group: 'Memory', title: 'intervention_hints 为数组', check: (c) => expect(Array.isArray(c.memory.intervention_hints)).toBe(true) },
    { id: 'M074', group: 'Evidence', title: 'evidence nodes 为数组', check: (c) => expect(Array.isArray(c.evidenceGraph.nodes)).toBe(true) },
    { id: 'M075', group: 'Evidence', title: 'evidence edges 为数组', check: (c) => expect(Array.isArray(c.evidenceGraph.edges)).toBe(true) },
    { id: 'M076', group: 'Evidence', title: 'evidence nodeCount 有效', check: (c) => expect(c.evidenceGraph.metrics.nodeCount).toBeGreaterThanOrEqual(0) },
    { id: 'M077', group: 'Evidence', title: 'evidence edgeCount 有效', check: (c) => expect(c.evidenceGraph.metrics.edgeCount).toBeGreaterThanOrEqual(0) },
    { id: 'M078', group: 'Evidence', title: 'evidence touchedKcCount 有效', check: (c) => expect(c.evidenceGraph.metrics.touchedKcCount).toBeGreaterThanOrEqual(0) },
    { id: 'M079', group: 'Resource', title: '主资源 id 有效', check: (c) => expect(c.mainResourceId).toBeGreaterThan(0) },
    { id: 'M080', group: 'Resource', title: '失败分支资源 id 有效', check: (c) => expect(c.failResourceId).toBeGreaterThan(0) },
    { id: 'M081', group: 'Resource', title: '资源列表包含主资源', check: (c) => expect(c.resourceList.map((item) => item.id)).toContain(c.mainResourceId) },
    { id: 'M082', group: 'Resource', title: '资源列表包含目标 KC', check: (c) => expect(c.resourceList.some((item) => item.kcCodes.includes(KC_CODE))).toBe(true) },
    { id: 'M083', group: 'Resource', title: '资源详情 id 匹配', check: (c) => expect(c.resource.id).toBe(c.mainResourceId) },
    { id: 'M084', group: 'Resource', title: '资源详情绑定 KC', check: (c) => expect(c.resource.kcCodes).toContain(KC_CODE) },
    { id: 'M085', group: 'Resource', title: '资源 type 非空', check: (c) => expectNonEmptyString(c.resource.type) },
    { id: 'M086', group: 'Resource', title: '资源 title 非空', check: (c) => expectNonEmptyString(c.resource.title) },
    { id: 'M087', group: 'Resource', title: '资源 summary 非空', check: (c) => expectNonEmptyString(c.resource.summary) },
    { id: 'M088', group: 'Resource', title: '资源 evidencePageIds 为数组', check: (c) => expect(Array.isArray(c.resource.evidencePageIds)).toBe(true) },
    { id: 'M089', group: 'Resource', title: 'groundingStatus 合法', check: (c) => expect(['PASSED', 'FAILED', 'NO_EVIDENCE']).toContain(c.resource.groundingStatus) },
    { id: 'M090', group: 'Resource', title: 'safetyStatus 合法', check: (c) => expect(['PASSED', 'FAILED', 'NO_EVIDENCE']).toContain(c.resource.safetyStatus) },
    { id: 'M091', group: 'Consumption', title: '阅读记录指向主资源', check: (c) => expect(c.consumption.resourceId).toBe(c.mainResourceId) },
    { id: 'M092', group: 'Consumption', title: '阅读记录绑定 KC', check: (c) => expect(c.consumption.kcCodes).toContain(KC_CODE) },
    { id: 'M093', group: 'Consumption', title: '阅读已记录', check: (c) => expect(c.consumption.readRecorded).toBe(true) },
    { id: 'M094', group: 'Consumption', title: '阅读后进入 micro_check', check: (c) => expect(c.consumption.nextAction).toBe('micro_check') },
    { id: 'M095', group: 'Consumption', title: '阅读后 loopState 正确', check: (c) => expect(c.consumption.loopState).toBe('MICRO_CHECK_REQUIRED') },
    { id: 'M096', group: 'Consumption', title: '阅读后要求 micro evidence', check: (c) => expect(c.consumption.evidenceRequired).toContain('micro_check') },
    { id: 'M097', group: 'MicroCheck', title: '命中内容微测通过', check: (c) => expect(c.passCheck.status).toBe('PASSED') },
    { id: 'M098', group: 'MicroCheck', title: '通过后 nextAction pass', check: (c) => expect(c.passCheck.nextAction).toBe('pass') },
    { id: 'M099', group: 'MicroCheck', title: '通过后 loopState PASSED', check: (c) => expect(c.passCheck.loopState).toBe('PASSED') },
    { id: 'M100', group: 'MicroCheck', title: '通过后 masteryDelta 正向', check: (c) => expect(c.passCheck.masteryDelta).toBeGreaterThan(0) },
    { id: 'M101', group: 'Effectiveness', title: '效果信号指向资源', check: (c) => expect(c.effectiveness.resourceId).toBe(c.mainResourceId) },
    { id: 'M102', group: 'Effectiveness', title: '效果 hook 正确', check: (c) => expect(c.effectiveness.evaluationHook).toBe('resource_feedback_or_check') },
    { id: 'M103', group: 'Effectiveness', title: 'checkCount 已累计', check: (c) => expect(c.effectiveness.checkCount).toBeGreaterThanOrEqual(1) },
    { id: 'M104', group: 'Effectiveness', title: 'passedCheckCount 已累计', check: (c) => expect(c.effectiveness.passedCheckCount).toBeGreaterThanOrEqual(1) },
    { id: 'M105', group: 'Effectiveness', title: 'passRate 正向', check: (c) => expect(c.effectiveness.passRate).toBeGreaterThan(0) },
    { id: 'M106', group: 'Effectiveness', title: 'masteryDeltaSum 正向', check: (c) => expect(c.effectiveness.masteryDeltaSum).toBeGreaterThan(0) },
    { id: 'M107', group: 'Effectiveness', title: 'signal 为 EFFECTIVE', check: (c) => expect(c.effectiveness.signal).toBe('EFFECTIVE') },
    { id: 'M108', group: 'Remediation', title: '首次失败进入 FAILED', check: (c) => expect(c.failCheck.status).toBe('FAILED') },
    { id: 'M109', group: 'Remediation', title: '首次失败进入 remediate', check: (c) => expect(c.failCheck.nextAction).toBe('remediate') },
    { id: 'M110', group: 'Remediation', title: '首次失败 loopState 补救', check: (c) => expect(c.failCheck.loopState).toBe('NEED_REMEDIATION') },
    { id: 'M111', group: 'Remediation', title: '首次失败 masteryDelta 负向', check: (c) => expect(c.failCheck.masteryDelta).toBeLessThan(0) },
    { id: 'M112', group: 'Remediation', title: '连续失败仍 FAILED', check: (c) => expect(c.swapCheck.status).toBe('FAILED') },
    { id: 'M113', group: 'Remediation', title: '连续失败换资源', check: (c) => expect(c.swapCheck.nextAction).toBe('swap_resource') },
    { id: 'M114', group: 'Remediation', title: '连续失败 loopState 换资源', check: (c) => expect(c.swapCheck.loopState).toBe('NEED_RESOURCE_SWAP') },
    { id: 'M115', group: 'Remediation', title: '连续失败补救类型 CodeLab', check: (c) => expect(c.swapCheck.remediationType).toBe('CODE_LAB') },
    { id: 'M116', group: 'Formal Judge', title: '无正式提交不通过', check: (c) => expect(c.noSubmissionCheck.status).toBe('FAILED') },
    { id: 'M117', group: 'Formal Judge', title: '无正式提交要求补救', check: (c) => expect(c.noSubmissionCheck.nextAction).toBe('remediate') },
    { id: 'M118', group: 'Formal Judge', title: '无正式提交 masteryDelta 负向', check: (c) => expect(c.noSubmissionCheck.masteryDelta).toBeLessThan(0) },
    { id: 'M119', group: 'Formal Judge', title: '失败正式提交触发 replan', check: (c) => expect(c.failedOperationCheck.nextAction).toBe('replan') },
    { id: 'M120', group: 'Formal Judge', title: '失败正式提交绑定 submissionId', check: (c) => expect(c.failedOperationCheck.practiceSubmissionId).toBe(c.failedSubmission.id) },
    { id: 'M121', group: 'Formal Judge', title: '通过正式提交闭环通过', check: (c) => expect(c.passedOperationCheck.status).toBe('PASSED') },
    { id: 'M122', group: 'Formal Judge', title: '通过正式提交 nextAction pass', check: (c) => expect(c.passedOperationCheck.nextAction).toBe('pass') },
    { id: 'M123', group: 'Formal Judge', title: '通过正式提交绑定 submissionId', check: (c) => expect(c.passedOperationCheck.practiceSubmissionId).toBe(c.passedSubmission.id) },
    { id: 'M124', group: 'Formal Judge', title: 'KC mismatch 被拒绝', check: (c) => expect(c.mismatchStatus).toBe(400) },
    { id: 'M125', group: 'Formal Judge', title: 'KC mismatch 错误码明确', check: (c) => expect(c.mismatchBody).toContain('RESOURCE_CHECK_KC_MISMATCH') },
    { id: 'M126', group: 'Submission', title: '失败提交不是 PASSED', check: (c) => expect(c.failedSubmission.status).not.toBe('PASSED') },
    { id: 'M127', group: 'Submission', title: '通过提交 PASSED', check: (c) => expect(c.passedSubmission.status).toBe('PASSED') },
    { id: 'M128', group: 'Submission', title: '通过提交 problemId 正确', check: (c) => expect(c.passedSubmission.problemId).toBe(PROBLEM_ID) },
    { id: 'M129', group: 'Submission', title: 'PASSED 列表包含通过提交', check: (c) => expect(c.passedSubmissionList.map((item) => item.id)).toContain(c.passedSubmission.id) },
    { id: 'M130', group: 'Submission', title: 'PASSED 列表状态全通过', check: (c) => expect(c.passedSubmissionList.every((item) => item.status === 'PASSED')).toBe(true) },
    { id: 'M131', group: 'Submission', title: '提交详情 id 正确', check: (c) => expect(c.submissionDetail.submission.id).toBe(c.passedSubmission.id) },
    { id: 'M132', group: 'Submission', title: '提交详情状态通过', check: (c) => expect(c.submissionDetail.submission.status).toBe('PASSED') },
    { id: 'M133', group: 'Submission', title: '提交详情含 judgeResult', check: (c) => expect(c.submissionDetail.judgeResult).toBeTruthy() },
    { id: 'M134', group: 'BugMine', title: 'Bug Mine session id 有效', check: (c) => expect(c.bugMineSession.id).toBeGreaterThan(0) },
    { id: 'M135', group: 'BugMine', title: 'Bug Mine 绑定失败提交', check: (c) => expect(c.bugMineSession.submissionId).toBe(c.failedSubmission.id) },
    { id: 'M136', group: 'BugMine', title: 'Bug Mine 卡片存在', check: (c) => expect(c.bugMineSession.cards.length).toBeGreaterThan(0) },
    { id: 'M137', group: 'BugMine', title: 'Bug Mine 初始卡片未全揭示', check: (c) => expect(c.bugMineSession.cards.some((card) => !card.revealed)).toBe(true) },
    { id: 'M138', group: 'BugMine', title: '深度锁定返回 409', check: (c) => expect(c.bugRevealStatus).toBe(409) },
    { id: 'M139', group: 'BugMine', title: '深度锁定错误码明确', check: (c) => expect(c.bugRevealBody.code).toBe('BUG_MINE_DEPTH_LOCKED') },
    { id: 'M140', group: 'BugMine', title: 'Bug Mine retry 创建提交', check: (c) => expect(c.bugRetry.id).toBeGreaterThan(0) },
    { id: 'M141', group: 'BugMine', title: 'Bug Mine retry 状态合法', check: (c) => expect(['PASSED', 'FAILED', 'ERROR']).toContain(c.bugRetry.status) },
    { id: 'M142', group: 'Quest', title: '今日任务为数组', check: (c) => expect(Array.isArray(c.quests)).toBe(true) },
    { id: 'M143', group: 'Quest', title: '今日任务 id 字段存在', check: (c) => c.quests.forEach((quest) => expect(quest).toHaveProperty('id')) },
    { id: 'M144', group: 'Quest', title: '今日任务 evidence 字段存在', check: (c) => c.quests.forEach((quest) => expect(quest).toHaveProperty('requiredEvidenceType')) },
    { id: 'M145', group: 'Recommendation', title: '推荐接口为数组', check: (c) => expect(Array.isArray(c.recommendations)).toBe(true) },
  ];

  for (const matrixCase of matrixCases) {
    test(`${matrixCase.id} ${matrixCase.group}: ${matrixCase.title}`, async () => {
      await matrixCase.check(ctx);
    });
  }

  test('M146 Browser: 工坊页面可打开', async ({ browser }) => {
    const context = await browser.newContext();
    await injectSession(context, ctx.session);
    await suppressFirstVisitOverlays(context);
    const page = await context.newPage();
    try {
      await page.goto('/workshop', { waitUntil: 'domcontentloaded' });
      await expect(page.getByTestId('workshop-page')).toBeVisible({ timeout: 60_000 });
    } finally {
      await context.close();
    }
  });

  test('M147 Browser: 进度抽屉展示用户可读状态', async ({ browser }) => {
    const context = await browser.newContext();
    await injectSession(context, ctx.session);
    await suppressFirstVisitOverlays(context);
    const page = await context.newPage();
    try {
      await page.goto('/workshop', { waitUntil: 'domcontentloaded' });
      await page.getByTestId('workshop-open-calls').click();
      await expect(page.getByTestId('workshop-drawer-calls')).toBeVisible({ timeout: 30_000 });
      await expect(page.getByTestId('workshop-process')).toContainText('还没有处理记录', { timeout: 30_000 });
    } finally {
      await context.close();
    }
  });

  test('M148 Browser: 进度抽屉不暴露 runtime summary', async ({ browser }) => {
    const context = await browser.newContext();
    await injectSession(context, ctx.session);
    await suppressFirstVisitOverlays(context);
    const page = await context.newPage();
    try {
      await page.goto('/workshop', { waitUntil: 'domcontentloaded' });
      await page.getByTestId('workshop-open-calls').click();
      await expect(page.getByTestId('workshop-process')).toBeVisible({ timeout: 30_000 });
      await expect(page.getByTestId('workshop-drawer-calls')).not.toContainText(/Runtime|MCP 工具|能力包/);
    } finally {
      await context.close();
    }
  });

  test('M149 Browser: 书房抽屉可打开', async ({ browser }) => {
    const context = await browser.newContext();
    await injectSession(context, ctx.session);
    await suppressFirstVisitOverlays(context);
    const page = await context.newPage();
    try {
      await page.goto('/workshop', { waitUntil: 'domcontentloaded' });
      await page.getByTestId('workshop-open-study').click();
      await expect(page.getByTestId('workshop-drawer-study')).toBeVisible({ timeout: 30_000 });
    } finally {
      await context.close();
    }
  });

  test('M150 Browser: 资源列表包含本轮资源', async ({ browser }) => {
    const context = await browser.newContext();
    await injectSession(context, ctx.session);
    await suppressFirstVisitOverlays(context);
    const page = await context.newPage();
    try {
      await page.goto('/workshop', { waitUntil: 'domcontentloaded' });
      await page.getByTestId('workshop-open-study').click();
      await expect(page.getByTestId(`resource-list-item-${ctx.mainResourceId}`)).toBeVisible({ timeout: 30_000 });
    } finally {
      await context.close();
    }
  });

  test('M151 Browser: 资源详情弹窗可打开', async ({ browser }) => {
    const context = await browser.newContext();
    await injectSession(context, ctx.session);
    await suppressFirstVisitOverlays(context);
    const page = await context.newPage();
    try {
      await page.goto('/workshop', { waitUntil: 'domcontentloaded' });
      await page.getByTestId('workshop-open-study').click();
      await page.getByTestId(`resource-list-item-${ctx.mainResourceId}`).click();
      await expect(page.getByTestId('resource-modal')).toBeVisible({ timeout: 30_000 });
    } finally {
      await context.close();
    }
  });

  test('M152 Browser: 资源闭环 rail 展示读测判', async ({ browser }) => {
    const context = await browser.newContext();
    await injectSession(context, ctx.session);
    await suppressFirstVisitOverlays(context);
    const page = await context.newPage();
    try {
      await page.goto('/workshop', { waitUntil: 'domcontentloaded' });
      await page.getByTestId('workshop-open-study').click();
      await page.getByTestId(`resource-list-item-${ctx.mainResourceId}`).click();
      await expect(page.getByTestId('resource-loop-rail')).toContainText('读', { timeout: 30_000 });
      await expect(page.getByTestId('resource-loop-rail')).toContainText('测');
      await expect(page.getByTestId('resource-loop-rail')).toContainText('判');
    } finally {
      await context.close();
    }
  });

  test('M153 Browser: 资源微测输入框可用', async ({ browser }) => {
    const context = await browser.newContext();
    await injectSession(context, ctx.session);
    await suppressFirstVisitOverlays(context);
    const page = await context.newPage();
    try {
      await page.goto('/workshop', { waitUntil: 'domcontentloaded' });
      await page.getByTestId('workshop-open-study').click();
      await page.getByTestId(`resource-list-item-${ctx.mainResourceId}`).click();
      await page.getByTestId('resource-check-answer').fill(`${KC_CODE} 我能说明资源关键点`);
      await expect(page.getByTestId('resource-check-answer')).toHaveValue(`${KC_CODE} 我能说明资源关键点`);
    } finally {
      await context.close();
    }
  });
});
