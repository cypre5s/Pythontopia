import { expect, test } from '@playwright/test';

interface MockApiRequest {
  method: string;
  path: string;
  body: Record<string, unknown> | null;
}

const runId = 'e2e-expedition-responsive';
const problemId = 'e2e-expedition-boss-problem';

function mapSnapshot(bossEntered = false, hp = 8) {
  return {
    runId,
    phase: 'map',
    focusChapter: 2,
    nodes: [
      {
        id: 'node-entry',
        layer: 0,
        col: 0,
        kind: 'battle',
        kcCode: 'PY43-KC-010',
        challengeKind: 'dual_track',
        difficulty: 2,
        next: ['node-boss'],
        state: bossEntered ? 'cleared' : 'current',
      },
      {
        id: 'node-boss',
        layer: 1,
        col: 0,
        kind: 'boss',
        kcCode: 'PY43-KC-010',
        challengeKind: 'dual_track',
        difficulty: 3,
        next: [],
        state: bossEntered ? 'current' : 'available',
        bossPhase: 1,
      },
    ],
    layers: 2,
    currentNodeId: bossEntered ? 'node-boss' : 'node-entry',
    hp,
    maxHp: 8,
    momentum: 0,
    bestMomentum: 0,
    score: 0,
    verifiedDepth: 0,
    relics: [],
    isDaily: false,
    history: [],
  };
}

test('keeps the active expedition map and Boss challenge usable at 320px', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 780 });
  await page.addInitScript(() => {
    const user = {
      id: 990016,
      username: 'e2e-expedition-responsive',
      displayName: 'E2E 学生',
      role: 'STUDENT',
    };
    localStorage.setItem('pythontopia.token', 'route-mocked-e2e-token');
    localStorage.setItem('pythontopia.expiresAt', '2035-01-01T00:00:00.000Z');
    localStorage.setItem('pythontopia.user', JSON.stringify(user));
  });

  const requests: MockApiRequest[] = [];
  const unexpectedApiPaths = new Set<string>();
  const initialSnapshot = mapSnapshot();
  const enteredBossSnapshot = mapSnapshot(true);
  const settledBossSnapshot = mapSnapshot(true, 7);

  // Fail closed for every API request: the browser test cannot reach Spring, Graph, a model, or the DB.
  await page.route((url) => new URL(url).pathname.startsWith('/api/'), async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const method = request.method();
    const body = method === 'POST' || method === 'PUT'
      ? request.postDataJSON() as Record<string, unknown> | null
      : null;
    requests.push({ method, path, body });

    const respond = (data: unknown, status = 200) => route.fulfill({
      status,
      contentType: 'application/json',
      body: JSON.stringify(data),
    });

    if (path === '/api/auth/refresh' && method === 'POST') {
      return respond({
        token: 'route-mocked-e2e-token',
        expiresAt: '2035-01-01T00:00:00.000Z',
        user: {
          id: 990016,
          username: 'e2e-expedition-responsive',
          displayName: 'E2E 学生',
          role: 'STUDENT',
        },
      });
    }
    if (path === '/api/world/expedition/run' && method === 'GET') {
      return route.fulfill({ status: 204, body: '' });
    }
    if (path === '/api/world/expedition/run' && method === 'POST') {
      return respond(initialSnapshot);
    }
    if (path === '/api/world/expedition/leaderboard' && method === 'GET') {
      return respond({ entries: [], selfRank: null, selfBest: 0, inClass: false });
    }
    if (path === '/api/world/boss/select-frontier' && method === 'POST') {
      return respond({ picks: [{ kcCode: 'PY43-KC-010', chapter: 2, difficulty: 3, reason: 'mock' }] });
    }
    if (path === '/api/world/boss/2/prefetch' && (method === 'GET' || method === 'POST')) {
      return respond({
        jobId: 1,
        status: 'READY',
        attempts: 1,
        errorMessage: null,
        retryable: false,
        updatedAt: '2035-01-01T00:00:00.000Z',
      });
    }
    if (path === '/api/world/expedition/run/actions' && method === 'POST') {
      if (body?.action === 'ENTER_NODE' && body.nodeId === 'node-boss') {
        return respond(enteredBossSnapshot);
      }
      if (body?.action === 'SETTLE_CHALLENGE' && body.nodeId === 'node-boss') {
        return respond(settledBossSnapshot);
      }
    }
    if (path === '/api/world/boss/2/problem' && method === 'POST') {
      return respond({
        problemId,
        chapter: 2,
        stage: 3,
        difficulty: 3,
        kcCode: 'PY43-KC-010',
        statement: '读取两个整数并输出它们的和。',
        starterCode: 'a, b = map(int, input().split())\nprint(a + b)\n',
        hints: [],
        sampleInput: '1 2',
        sampleOutput: '3',
        totalTestCases: 2,
        reviewed: true,
        aiGenerated: false,
      });
    }
    if (path === '/api/world/boss/2/attempt' && method === 'POST') {
      return respond({
        problemId,
        passed: false,
        passedTestCases: 0,
        totalTestCases: 2,
        failureFirstHint: '公开样例未通过',
        score: 0,
        feedback: '检查输入拆分和求和逻辑。',
      });
    }

    unexpectedApiPaths.add(`${method} ${path}`);
    return respond({ code: 'E2E_UNEXPECTED_API', message: '未配置的 E2E API mock', details: [] }, 501);
  });

  const pageErrors: string[] = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));
  await page.goto('/expedition', { waitUntil: 'domcontentloaded' });

  await expect(page.getByTestId('expedition-begin')).toBeVisible();
  await page.getByTestId('expedition-begin').click();
  await expect(page.getByTestId('expedition-map')).toBeVisible();
  await expect(page.getByTestId('expedition-view-list')).toBeVisible();
  await expect(page.getByTestId('expedition-view-pixel')).toHaveCount(0);
  await expect(page.getByTestId('expedition-node-node-boss')).toBeVisible();

  const expectNoHorizontalOverflow = async () => {
    await expect.poll(() => page.evaluate(
      () => document.documentElement.scrollWidth - window.innerWidth,
    )).toBe(0);
  };
  await expectNoHorizontalOverflow();

  await page.getByTestId('expedition-node-node-boss').click();
  await expect(page.getByTestId('expedition-challenge')).toBeVisible();
  await expect(page.getByTestId('expedition-canvas')).toBeVisible();
  await expect(page.getByTestId('expedition-view-map')).toBeVisible();
  await expect(page.getByTestId('expedition-submit')).toBeVisible();
  await expectNoHorizontalOverflow();

  await page.getByTestId('expedition-submit').click();
  await expect(page.getByText('答错 · 体力 7/8')).toBeVisible();
  await expect(page.getByTestId('expedition-retry')).toBeVisible();
  await expectNoHorizontalOverflow();

  expect(requests.some(({ path, body }) =>
    path === '/api/world/expedition/run/actions' && body?.action === 'ENTER_NODE',
  )).toBe(true);
  expect(requests.some(({ path }) => path === '/api/world/boss/2/attempt')).toBe(true);
  expect(requests.some(({ path, body }) =>
    path === '/api/world/expedition/run/actions' && body?.action === 'SETTLE_CHALLENGE',
  )).toBe(true);
  expect(unexpectedApiPaths).toEqual(new Set());
  expect(pageErrors).toEqual([]);
});
