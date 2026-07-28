import { expect, test, type APIRequestContext, type BrowserContext, type Page } from '@playwright/test';
import {
  acquireStudentSession,
  injectSession,
  resolveBaseUrl,
  suppressFirstVisitOverlays,
  type DemoSession,
} from './support/demoSession';

const KC_CODE = 'PY43-KC-016';
const PROBLEM_ID = 476;

interface ResourceCreateBody {
  status: 'SUCCESS' | 'PARTIAL' | 'FAILED';
  resourceId: number;
  runId: number;
}

interface SubmissionBody {
  id: number;
  status: 'PASSED' | 'FAILED' | 'ERROR' | 'PENDING' | 'RUNNING';
}

function u(path: string): string {
  return `${resolveBaseUrl()}${path}`;
}

function bearer(session: DemoSession): Record<string, string> {
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
    headers: bearer(session),
    data,
    timeout: 120_000,
  });
  if (!response.ok()) {
    throw new Error(`POST ${path} failed: HTTP ${response.status()} ${await response.text()}`);
  }
  return (await response.json()) as T;
}

async function createLoopResource(
  request: APIRequestContext,
  session: DemoSession,
): Promise<number> {
  for (let attempt = 1; attempt <= 2; attempt += 1) {
    const response = await request.post(u('/api/workshop/resources'), {
      headers: bearer(session),
      data: {
        kcCodes: [KC_CODE],
        type: 'HANDOUT',
        focusHint: 'E2E 学习闭环：读后必须微测、练习提交、再决策。',
      },
      timeout: 120_000,
    });
    if (response.ok()) {
      const body = (await response.json()) as ResourceCreateBody;
      expect(body.status, `资源生成失败 run=${body.runId}`).not.toBe('FAILED');
      expect(body.resourceId).toBeGreaterThan(0);
      return body.resourceId;
    }
    const body = await response.text();
    if (
      response.status() === 502
      && body.includes('WORKSHOP_GRAPH_UNAVAILABLE')
      && attempt < 2
    ) {
      await new Promise((resolve) => {
        setTimeout(resolve, 5_000);
      });
      continue;
    }
    throw new Error(
      `POST /api/workshop/resources failed: HTTP ${response.status()} ${body}`,
    );
  }
  throw new Error('POST /api/workshop/resources failed after bounded retry');
}

async function createSubmission(
  request: APIRequestContext,
  session: DemoSession,
  code: string,
): Promise<SubmissionBody> {
  const body = await postJson<SubmissionBody>(request, session, '/api/submissions', {
    problemId: PROBLEM_ID,
    source: 'TRAINING',
    code,
  });
  expect(['PASSED', 'FAILED', 'ERROR']).toContain(body.status);
  return body;
}

async function openResourceInStudy(page: Page, resourceId: number): Promise<void> {
  await page.goto('/workshop', { waitUntil: 'domcontentloaded' });
  await expect(page.getByTestId('workshop-page')).toBeVisible({ timeout: 30_000 });
  await page.getByTestId('workshop-open-study').click();
  await expect(page.getByTestId('workshop-drawer-study')).toBeVisible({ timeout: 30_000 });
  await page.getByTestId(`resource-list-item-${resourceId}`).click();
  await expect(page.getByTestId('resource-modal')).toBeVisible({ timeout: 30_000 });
  await expect(page.getByTestId('resource-loop-rail')).toContainText('读');
  await expect(page.getByTestId('resource-loop-state')).toContainText('待测');
}

test.describe('学生学习闭环 E2E @learning-loop', () => {
  test('微测两轮失败后从补救升级到换资源', async ({ page, context, request }) => {
    test.setTimeout(300_000);
    const session = await prepareStudent(context, request);
    const resourceId = await createLoopResource(request, session);

    await openResourceInStudy(page, resourceId);

    await page.getByTestId('resource-check-answer').fill('完全没懂');
    await page.getByTestId('resource-check-submit').click();
    await expect(page.getByTestId('resource-loop-state')).toContainText('补救');
    await expect(page.getByTestId('resource-check-result')).toContainText('-0.02');

    await page.getByTestId('resource-check-answer').fill('还是不明白');
    await page.getByTestId('resource-check-submit').click();
    await expect(page.getByTestId('resource-loop-rail')).toContainText('换资源');
    await expect(page.getByTestId('resource-loop-rail')).toContainText('CODE_LAB');
  });

  test('最近失败训练提交会把闭环推进到重规划', async ({ page, context, request }) => {
    test.setTimeout(300_000);
    const session = await prepareStudent(context, request);
    const resourceId = await createLoopResource(request, session);
    const submission = await createSubmission(request, session, 'print("0.0000")');
    expect(submission.status).not.toBe('PASSED');

    await openResourceInStudy(page, resourceId);
    await page.getByTestId('resource-submission-check').click();

    await expect(page.getByTestId('resource-loop-state')).toContainText('重规划');
    await expect(page.getByTestId('resource-check-result')).toContainText('-0.02');
  });

  test('最近通过训练提交会完成通过闭环', async ({ page, context, request }) => {
    test.setTimeout(300_000);
    const session = await prepareStudent(context, request);
    const resourceId = await createLoopResource(request, session);
    const submission = await createSubmission(
      request,
      session,
      [
        'radius = float(input())',
        'print(f"{3.1415 * radius * radius:.4f}")',
      ].join('\n'),
    );
    expect(submission.status).toBe('PASSED');

    await openResourceInStudy(page, resourceId);
    await page.getByTestId('resource-submission-check').click();

    await expect(page.getByTestId('resource-loop-state')).toContainText('通过');
    await expect(page.getByTestId('resource-check-result')).toContainText('+0.05');
  });
});
