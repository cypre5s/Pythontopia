import { expect, test, type APIRequestContext, type BrowserContext, type Page } from '@playwright/test';
import {
  acquireStudentSession,
  acquireTeacherSession,
  ensureDemoClassroom,
  injectSession,
  resolveBaseUrl,
  suppressFirstVisitOverlays,
  type DemoSession,
} from './support/demoSession';

const BASE_URL = resolveBaseUrl();
const KC_CODE = 'PY43-KC-016';
const TRAINING_PROBLEM_ID = 476;

const TEACHER_SESSION_AVAILABLE =
  Boolean(process.env.PYT_E2E_TEACHER_USERNAME && process.env.PYT_E2E_TEACHER_PASSWORD)
  || Boolean(process.env.PYT_E2E_REGISTER === '1' && process.env.PYT_E2E_TEACHER_INVITE_CODE);

const IGNORABLE_CONSOLE = [
  /favicon/i,
  /AudioContext|autoplay|user gesture|play\(\) request was interrupted/i,
  /ResizeObserver loop/i,
  /source map/i,
  /WebGL|GL_INVALID|GPU stall|powerPreference/i,
  /\[vite\]|hot-update|sockjs|ws:\/\//i,
  /Download the Vue Devtools/i,
  /Permissions-Policy|third-party cookie/i,
];

const FATAL_CONSOLE = [
  /\b(TypeError|ReferenceError|SyntaxError|RangeError)\b/,
  /Cannot read propert/i,
  /is not a function/i,
  /Failed to fetch dynamically imported module/i,
  /Loading chunk .* failed/i,
  /Uncaught/i,
];

interface ConsoleReport {
  fatal: string[];
}

interface ResourceCreateBody {
  status: 'SUCCESS' | 'PARTIAL' | 'FAILED';
  resourceId: number;
  runId: number;
}

function collectConsole(page: Page): ConsoleReport {
  const report: ConsoleReport = { fatal: [] };
  page.on('console', (msg) => {
    if (msg.type() !== 'error') return;
    const text = msg.text();
    if (IGNORABLE_CONSOLE.some((re) => re.test(text))) return;
    if (FATAL_CONSOLE.some((re) => re.test(text))) report.fatal.push(text);
  });
  page.on('pageerror', (err) => {
    const text = `[pageerror] ${err.message}`;
    if (IGNORABLE_CONSOLE.some((re) => re.test(text))) return;
    report.fatal.push(text);
  });
  return report;
}

function assertNoFatal(report: ConsoleReport, scene: string): void {
  expect(report.fatal, `${scene} 出现致命 console/pageerror：\n${report.fatal.join('\n')}`).toEqual([]);
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

async function prepareTeacher(
  context: BrowserContext,
  request: APIRequestContext,
): Promise<DemoSession> {
  const session = await acquireTeacherSession(request);
  await ensureDemoClassroom(request, session);
  await injectSession(context, session);
  await suppressFirstVisitOverlays(context);
  return session;
}

function authHeaders(session: DemoSession): Record<string, string> {
  return { Authorization: `Bearer ${session.token}` };
}

async function createResource(
  request: APIRequestContext,
  session: DemoSession,
): Promise<number> {
  for (let attempt = 1; attempt <= 2; attempt += 1) {
    const response = await request.post(`${BASE_URL}/api/workshop/resources`, {
      headers: authHeaders(session),
      data: {
        kcCodes: [KC_CODE],
        type: 'HANDOUT',
        focusHint: '上线深链点击鲁棒性验收：用于反复打开书房资源详情。',
      },
      timeout: 150_000,
    });
    if (response.ok()) {
      const body = (await response.json()) as ResourceCreateBody;
      expect(body.status, `资源生成失败 run=${body.runId}`).not.toBe('FAILED');
      expect(body.resourceId).toBeGreaterThan(0);
      return body.resourceId;
    }
    const text = await response.text();
    if (attempt < 2 && response.status() === 502 && text.includes('WORKSHOP_GRAPH_UNAVAILABLE')) {
      await pageSleep(5_000);
      continue;
    }
    throw new Error(`POST /api/workshop/resources failed: HTTP ${response.status()} ${text}`);
  }
  throw new Error('POST /api/workshop/resources failed after retries');
}

function pageSleep(ms: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

async function expectRouteReady(page: Page, route: string, testId: string): Promise<void> {
  const response = await page.goto(route, { waitUntil: 'domcontentloaded' });
  expect(response?.status(), `${route} HTTP 状态异常`).toBe(200);
  await expect(page, `${route} 不应被守卫送回登录页`).not.toHaveURL(/\/login/, {
    timeout: 15_000,
  });
  await expect(page.getByTestId('not-found-page'), `${route} 不应落到 404`).toHaveCount(0);
  await expect(page.getByTestId(testId), `${route} 主体未渲染`).toBeVisible({
    timeout: 90_000,
  });
}

async function dismissBlockingProbes(page: Page): Promise<void> {
  const helpClose = page.getByRole('button', { name: '我知道了，开始探索' });
  const probeSkip = page.getByTestId('probe-skip');
  for (let i = 0; i < 8; i += 1) {
    if (await helpClose.isVisible().catch(() => false)) {
      await helpClose.click({ timeout: 5_000 }).catch(() => undefined);
      await page.waitForTimeout(250);
      continue;
    }
    if (await probeSkip.isVisible().catch(() => false)) {
      await probeSkip.click({ timeout: 5_000 }).catch(() => undefined);
      await page.waitForTimeout(250);
      continue;
    }
    await page.waitForTimeout(300);
    if (
      !(await helpClose.isVisible().catch(() => false))
      && !(await probeSkip.isVisible().catch(() => false))
    ) {
      return;
    }
  }
  await expect(page.getByTestId('probe-card'), '阻塞点击的画像探针未能清场').toHaveCount(0, {
    timeout: 5_000,
  });
}

test.describe('上线交付深链点击鲁棒性 @delivery-clicks', () => {
  test('小镇 HUD 与暂停菜单可反复开关，并能从菜单进入工坊', async ({
    page,
    context,
    request,
  }) => {
    test.setTimeout(180_000);
    await prepareStudent(context, request);
    const report = collectConsole(page);

    await page.goto('/world', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('.world-root canvas')).toBeVisible({ timeout: 90_000 });
    await expect(page.getByTestId('hud-shell')).toBeVisible({ timeout: 60_000 });
    await dismissBlockingProbes(page);

    await page.getByTestId('hud-hide-btn').click();
    await expect(page.getByTestId('hud-reveal-strip')).toBeVisible({ timeout: 10_000 });
    await page.getByTestId('hud-reveal-strip').click();
    await expect(page.getByTestId('hud-top')).toBeVisible({ timeout: 10_000 });

    await page.getByTestId('hud-tool-menu').click();
    await expect(page.getByTestId('pause-resume')).toBeVisible({ timeout: 10_000 });
    await page.keyboard.press('Escape');
    await expect(page.getByTestId('pause-resume')).toHaveCount(0, { timeout: 10_000 });

    await page.getByTestId('hud-tool-menu').click();
    await page.getByTestId('pause-workshop-nav').click();
    await expect(page).toHaveURL(/\/workshop/, { timeout: 30_000 });
    await expect(page.getByTestId('workshop-page')).toBeVisible({ timeout: 60_000 });
    assertNoFatal(report, '小镇 HUD/暂停菜单点击链');
  });

  test('主线地图章节抽屉可反复打开关闭，进入学习后返回仍稳定', async ({
    page,
    context,
    request,
  }) => {
    test.setTimeout(180_000);
    await prepareStudent(context, request);
    const report = collectConsole(page);

    await page.goto('/mainline-map', { waitUntil: 'domcontentloaded' });
    await expect(page.getByTestId('mainline-map-page')).toBeVisible({ timeout: 60_000 });
    await expect(page.getByTestId('mainline-error')).toHaveCount(0);

    await page.getByTestId('mainline-node-btn-1').dispatchEvent('click');
    await expect(page.getByTestId('mainline-stage-panel')).toBeVisible({ timeout: 30_000 });
    await expect(page.getByTestId('mainline-chapter-title')).toContainText('第 1 章');

    await page.getByTestId('mainline-drawer-close').click();
    await expect(page.getByTestId('mainline-stage-panel')).toHaveCount(0, { timeout: 10_000 });
    await expect(page.getByTestId('mainline-empty')).toBeVisible({ timeout: 10_000 });

    await page.getByTestId('mainline-node-btn-1').dispatchEvent('click');
    await expect(page.getByTestId('mainline-stage-panel')).toBeVisible({ timeout: 30_000 });
    const enter = page.locator('[data-testid^="mainline-enter-stage-"]:not([disabled])').first();
    await expect(enter).toBeVisible({ timeout: 30_000 });
    const testId = await enter.getAttribute('data-testid');
    const stageId = testId?.match(/mainline-enter-stage-(\d+)/)?.[1];
    expect(stageId, '无法读取进入学习按钮的 stageId').toBeTruthy();
    await enter.click();

    await expect(page).toHaveURL(new RegExp(`/mainline/stage/${stageId}(?:[?#]|$)`), {
      timeout: 30_000,
    });
    await expect(page.getByTestId('stage-learn-page')).toBeVisible({ timeout: 90_000 });
    await expect(page.getByTestId('stage-learn-error')).toHaveCount(0);

    const coursewareTab = page.getByTestId('stage-toc-courseware');
    if (await coursewareTab.isVisible().catch(() => false)) {
      await coursewareTab.click();
      await expect(page.getByTestId('stage-learn-page')).toBeVisible();
    }
    const practiceTab = page.getByTestId('stage-toc-practice');
    if (await practiceTab.isVisible().catch(() => false)) {
      await practiceTab.click();
      await expect(page.getByTestId('stage-learn-page')).toBeVisible();
    }

    await page.goBack({ waitUntil: 'domcontentloaded' });
    await expect(page.getByTestId('mainline-map-page')).toBeVisible({ timeout: 60_000 });
    assertNoFatal(report, '主线地图/阶段学习点击链');
  });

  test('指南页桌面翻页与移动目录反复点击不白屏不溢出', async ({
    page,
    context,
    request,
  }) => {
    test.setTimeout(150_000);
    await prepareStudent(context, request);
    const report = collectConsole(page);

    await page.setViewportSize({ width: 1366, height: 800 });
    await page.goto('/guide', { waitUntil: 'domcontentloaded' });
    await expect(page.getByTestId('guide-content')).toBeVisible({ timeout: 30_000 });
    await expect(page.getByTestId('guide-pager')).toBeVisible();

    const next = page.getByTestId('guide-pager-next');
    if (await next.isVisible().catch(() => false)) {
      await next.click();
      await expect(page.getByTestId('guide-content')).toBeVisible({ timeout: 30_000 });
    }
    const prev = page.getByTestId('guide-pager-prev');
    if (await prev.isVisible().catch(() => false)) {
      await prev.click();
      await expect(page.getByTestId('guide-content')).toBeVisible({ timeout: 30_000 });
    }

    await page.setViewportSize({ width: 390, height: 844 });
    await page.reload({ waitUntil: 'domcontentloaded' });
    await expect(page.getByTestId('guide-content')).toBeVisible({ timeout: 30_000 });
    const toggle = page.getByTestId('guide-nav-toggle');
    await toggle.click();
    await expect(toggle).toHaveAttribute('aria-expanded', 'true');
    await toggle.click();
    await expect(toggle).toHaveAttribute('aria-expanded', 'false');

    const overflowPx = await page.evaluate(
      () => document.documentElement.scrollWidth - window.innerWidth,
    );
    expect(overflowPx, `移动端指南横向溢出 ${overflowPx}px`).toBeLessThanOrEqual(2);
    assertNoFatal(report, '指南桌面/移动点击链');
  });

  test('工坊历史/书房/进度抽屉与资源弹窗可反复开关', async ({
    page,
    context,
    request,
  }) => {
    test.setTimeout(240_000);
    const session = await prepareStudent(context, request);
    const resourceId = await createResource(request, session);
    const report = collectConsole(page);

    await page.goto('/workshop', { waitUntil: 'domcontentloaded' });
    await expect(page.getByTestId('workshop-page')).toBeVisible({ timeout: 60_000 });

    const history = page.getByTestId('workshop-drawer-history');
    if (!(await history.isVisible().catch(() => false))) {
      await page.getByTestId('workshop-open-history').click();
    }
    await expect(history).toBeVisible({ timeout: 30_000 });
    await page.getByTestId('workshop-history-close').click();
    await expect(history).toHaveCount(0, { timeout: 10_000 });

    await page.getByTestId('workshop-open-study').click();
    await expect(page.getByTestId('workshop-drawer-study')).toBeVisible({ timeout: 30_000 });
    await expect(page.getByTestId(`resource-list-item-${resourceId}`)).toBeVisible({
      timeout: 60_000,
    });

    await page.getByTestId(`resource-list-item-${resourceId}`).click();
    await expect(page.getByTestId('resource-modal')).toBeVisible({ timeout: 30_000 });
    await page.getByTestId('resource-modal-close').click();
    await expect(page.getByTestId('resource-modal')).toHaveCount(0, { timeout: 10_000 });

    await page.getByTestId(`resource-list-item-${resourceId}`).click();
    await expect(page.getByTestId('resource-modal')).toBeVisible({ timeout: 30_000 });
    await page.keyboard.press('Escape');
    await expect(page.getByTestId('resource-modal')).toHaveCount(0, { timeout: 10_000 });
    await page.keyboard.press('Escape');
    await expect(page.getByTestId('workshop-drawer-study')).toHaveCount(0, { timeout: 10_000 });

    await page.getByTestId('workshop-open-calls').click();
    await expect(page.getByTestId('workshop-drawer-calls')).toBeVisible({ timeout: 30_000 });
    await expect(page.getByTestId('workshop-process')).toBeVisible({ timeout: 30_000 });
    await page.getByTestId('workshop-drawer-close').click();
    await expect(page.getByTestId('workshop-drawer-calls')).toHaveCount(0, { timeout: 10_000 });

    await page.getByTestId('workshop-open-library').click();
    await expect(page).toHaveURL(/\/library/, { timeout: 30_000 });
    await expect(page.getByTestId('library-split-body')).toBeVisible({ timeout: 60_000 });
    assertNoFatal(report, '工坊抽屉/资源弹窗点击链');
  });

  test('训练、公告板、复盘、画像、题库和闯关深链刷新后仍可用', async ({
    page,
    context,
    request,
  }) => {
    test.setTimeout(300_000);
    await prepareStudent(context, request);
    const report = collectConsole(page);

    const routes = [
      { route: `/training?problemId=${TRAINING_PROBLEM_ID}`, testId: 'training-panel' },
      { route: '/board?tab=classroom', testId: 'board-panel' },
      { route: '/review-hall', testId: 'review-hall-panel' },
      { route: '/profile', testId: 'profile-page' },
      { route: '/bug-mine', testId: 'bug-mine-panel' },
      { route: '/problems', testId: 'problem-table' },
      { route: '/endless', testId: 'endless-page' },
      { route: '/expedition', testId: 'expedition-page' },
    ] as const;

    for (const { route, testId } of routes) {
      await expectRouteReady(page, route, testId);
      const reload = await page.reload({ waitUntil: 'domcontentloaded' });
      expect(reload?.status(), `${route} 刷新后 HTTP 状态异常`).toBe(200);
      await expect(page.getByTestId('not-found-page'), `${route} 刷新后不应 404`).toHaveCount(0);
      await expect(page.getByTestId(testId), `${route} 刷新后主体未渲染`).toBeVisible({
        timeout: 90_000,
      });
    }
    assertNoFatal(report, '核心深链刷新点击链');
  });

  test('教师首页和班级看板多标签点击稳定', async ({ page, context, request }) => {
    test.skip(!TEACHER_SESSION_AVAILABLE, '未提供教师账号或教师邀请码，跳过教师点击鲁棒性验收');
    test.setTimeout(180_000);
    await prepareTeacher(context, request);
    const report = collectConsole(page);

    await page.goto('/teacher', { waitUntil: 'domcontentloaded' });
    await expect(page.getByTestId('teacher-home')).toBeVisible({ timeout: 60_000 });
    await expect(page.getByTestId('classroom-list')).toBeVisible({ timeout: 60_000 });
    const classroomCard = page.locator('.classroom-card').first();
    await expect(classroomCard).toBeVisible({ timeout: 60_000 });
    await classroomCard.getByRole('button', { name: '进入看板' }).click();

    await expect(page.getByTestId('teacher-detail')).toBeVisible({ timeout: 60_000 });
    await expect(page.getByTestId('teacher-detail')).not.toContainText('看板加载失败');

    for (const tabName of ['成员', '任务', 'KC 热力图', '高频误区', '资源使用', '直播'] as const) {
      await page.getByRole('button', { name: new RegExp(`^${tabName}$`) }).click();
      await expect(page.getByTestId('teacher-detail')).toBeVisible();
      await expect(page.getByTestId('teacher-detail')).not.toContainText('看板加载失败');
    }

    await page.reload({ waitUntil: 'domcontentloaded' });
    await expect(page.getByTestId('teacher-detail')).toBeVisible({ timeout: 60_000 });
    await expect(page.getByTestId('teacher-detail')).not.toContainText('看板加载失败');
    assertNoFatal(report, '教师首页/看板点击链');
  });
});
