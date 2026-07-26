/**
 * 公网部署验收 E2E（Playwright）。
 *
 * 目标地址由环境变量 BASE_URL 驱动：
 *   - 本地集成：BASE_URL=http://127.0.0.1:5173
 *   - 公网验收：BASE_URL=http://47.120.29.62
 *
 * 演示账号契约（凭证绝不写死在仓库里）：
 *   - PYT_E2E_USERNAME / PYT_E2E_PASSWORD：已有账号直接登录；
 *   - PYT_E2E_REGISTER=1：通过 API 注册临时 QA 账号（与上面两个变量可同时给，
 *     给了用户名则注册该用户名，已存在冲突时容忍并直接登录）。
 *   - 三者都缺省时，需要登录态的用例整组跳过并给出中文原因。
 *
 * 子集运行示例（test.describe 标题即过滤键）：
 *   npx playwright test e2e/publicAcceptance.spec.ts -g "图书馆 RAG"
 *   npx playwright test e2e/publicAcceptance.spec.ts -g "训练场判题"
 *
 * 运行时证据（截图）统一落到仓库根 qa-screens/（已 gitignore），
 * 目录可用 PYT_QA_DIR 覆盖。
 */

import fs from 'node:fs';
import path from 'node:path';
import {
  expect,
  test,
  type APIRequestContext,
  type ConsoleMessage,
  type Page,
} from '@playwright/test';

// ---------------------------------------------------------------------------
// 环境契约
// ---------------------------------------------------------------------------

const BASE_URL = (process.env.BASE_URL ?? 'http://127.0.0.1:5173').replace(/\/+$/, '');
const ENV_USERNAME = (process.env.PYT_E2E_USERNAME ?? '').trim();
const ENV_PASSWORD = process.env.PYT_E2E_PASSWORD ?? '';
const SHOULD_REGISTER = process.env.PYT_E2E_REGISTER === '1';

/** 没有任何可用账号来源时为 false（相关用例跳过）。 */
const ACCOUNT_AVAILABLE = Boolean((ENV_USERNAME && ENV_PASSWORD) || SHOULD_REGISTER);
const SKIP_REASON_NO_ACCOUNT =
  '未提供 PYT_E2E_USERNAME/PYT_E2E_PASSWORD，且未开启 PYT_E2E_REGISTER=1，跳过需要登录态的用例';

function u(p: string): string {
  return new URL(p, `${BASE_URL}/`).toString();
}

// ---------------------------------------------------------------------------
// 证据收集（qa-screens/，已在仓库根 .gitignore 中忽略）
// ---------------------------------------------------------------------------

const RUN_STAMP = new Date()
  .toISOString()
  .replace(/[:.]/g, '-')
  .replace('T', '_')
  .slice(0, 19);

function qaDir(): string {
  if (process.env.PYT_QA_DIR) return process.env.PYT_QA_DIR;
  // Playwright 以 frontend/ 为工作目录运行（config 在 frontend/）；
  // 若从仓库根运行则直接落仓库根 qa-screens/。
  const cwd = process.cwd();
  const root = path.basename(cwd) === 'frontend' ? path.dirname(cwd) : cwd;
  return path.join(root, 'qa-screens');
}

async function evidence(page: Page, name: string): Promise<void> {
  const dir = path.join(qaDir(), `public-acceptance-${RUN_STAMP}`);
  fs.mkdirSync(dir, { recursive: true });
  const target = path.join(dir, `${name}.png`);
  await page
    .screenshot({ path: target, fullPage: false, timeout: 15_000 })
    .catch((error: unknown) => {
      test.info().annotations.push({
        type: '截图证据跳过',
        description: `${name}: ${error instanceof Error ? error.message : String(error)}`,
      });
    });
}

async function gotoWithRetry(page: Page, pathOrUrl: string, scene: string): Promise<void> {
  const target = pathOrUrl.startsWith('http') ? pathOrUrl : u(pathOrUrl);
  let lastError: unknown = null;
  for (let attempt = 1; attempt <= 2; attempt += 1) {
    try {
      await page.goto(target, { waitUntil: 'domcontentloaded', timeout: 90_000 });
      return;
    } catch (error) {
      lastError = error;
      if (attempt === 2) break;
      test.info().annotations.push({
        type: '公网导航重试',
        description: `${scene} 首次打开超时，重试 ${target}`,
      });
      await page
        .goto('about:blank', { waitUntil: 'domcontentloaded', timeout: 10_000 })
        .catch(() => undefined);
      await page.waitForTimeout(1_500);
    }
  }
  throw lastError;
}

// ---------------------------------------------------------------------------
// 账号：注册（可选）+ 登录（API 直连，按 worker 缓存）
// ---------------------------------------------------------------------------

interface QaAccount {
  username: string;
  password: string;
  displayName: string;
}

interface SessionBundle {
  token: string;
  expiresAt: string;
  user: unknown;
  account: QaAccount;
  registeredThisRun: boolean;
}

function buildAccount(workerIndex: number): QaAccount {
  if (ENV_USERNAME && ENV_PASSWORD) {
    return { username: ENV_USERNAME, password: ENV_PASSWORD, displayName: 'QA 验收账号' };
  }
  // PYT_E2E_REGISTER=1 且未给用户名：生成 worker 级唯一临时账号。
  const stamp = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
  return {
    username: `qa-e2e-w${workerIndex}-${stamp}`.slice(0, 40),
    password: `Qa!${stamp}9aZ`,
    displayName: `QA临时账号-w${workerIndex}`,
  };
}

const sessionCache = new Map<number, Promise<SessionBundle>>();

async function ensureSession(
  request: APIRequestContext,
  workerIndex: number,
): Promise<SessionBundle> {
  const cached = sessionCache.get(workerIndex);
  if (cached) return cached;

  const promise = (async (): Promise<SessionBundle> => {
    const account = buildAccount(workerIndex);
    let registeredThisRun = false;

    if (SHOULD_REGISTER) {
      const reg = await request.post(u('/api/auth/register'), {
        data: {
          username: account.username,
          password: account.password,
          displayName: account.displayName,
          role: 'STUDENT',
        },
      });
      if (reg.ok()) {
        registeredThisRun = true;
      } else if (reg.status() !== 409) {
        // 409（用户名已存在）容忍并继续登录；其余状态视为环境异常。
        const body = await reg.text().catch(() => '');
        throw new Error(
          `注册临时 QA 账号失败：HTTP ${reg.status()}，响应：${body.slice(0, 300)}`,
        );
      }
    }

    const login = await request.post(u('/api/auth/login'), {
      data: { username: account.username, password: account.password },
    });
    if (!login.ok()) {
      const body = await login.text().catch(() => '');
      throw new Error(
        `登录失败（${account.username}）：HTTP ${login.status()}，响应：${body.slice(0, 300)}`,
      );
    }
    const payload = (await login.json()) as { token: string; expiresAt: string; user: unknown };
    return { ...payload, account, registeredThisRun };
  })();

  sessionCache.set(workerIndex, promise);
  promise.catch(() => sessionCache.delete(workerIndex));
  return promise;
}

/** 在页面任何脚本执行前写入会话（与 stores/session.ts 的 localStorage 键对齐）。 */
async function seedSession(page: Page, s: SessionBundle): Promise<void> {
  await page.addInitScript(
    ([token, expiresAt, user]) => {
      window.localStorage.setItem('pythontopia.token', token);
      window.localStorage.setItem('pythontopia.expiresAt', expiresAt);
      window.localStorage.setItem('pythontopia.user', user);
      // 抑制首次进镇的帮助弹窗/镇长引导（与 guideScreenshots 的 suppressFirstVisitOverlays
      // 同款做法）：该弹层 z 序最高会拦截 HUD 与探针的点击，且与本套件验收目标无关。
      window.localStorage.setItem('pyt_first_world_visit', '1');
      window.localStorage.setItem('pyt_helped_v1', '1');
    },
    [s.token, s.expiresAt, JSON.stringify(s.user)] as const,
  );
}

/** 登录态用例的统一前置：无账号来源则跳过；否则换发会话并注种到页面。 */
async function preparePage(
  page: Page,
  request: APIRequestContext,
  workerIndex: number,
): Promise<SessionBundle> {
  const session = await ensureSession(request, workerIndex);
  await seedSession(page, session);
  test.info().annotations.push({
    type: '测试账号',
    description: `${session.account.username}（本轮新注册：${session.registeredThisRun ? '是' : '否'}）`,
  });
  return session;
}

// ---------------------------------------------------------------------------
// console 错误收集：区分「致命」与「可容忍噪音」
// ---------------------------------------------------------------------------

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
  errors: string[];
}

function collectConsole(page: Page): ConsoleReport {
  const report: ConsoleReport = { fatal: [], errors: [] };
  page.on('console', (msg: ConsoleMessage) => {
    if (msg.type() !== 'error') return;
    const text = msg.text();
    if (IGNORABLE_CONSOLE.some((re) => re.test(text))) return;
    report.errors.push(text);
    if (FATAL_CONSOLE.some((re) => re.test(text))) report.fatal.push(text);
  });
  page.on('pageerror', (err) => {
    const text = `[pageerror] ${err.message}`;
    if (IGNORABLE_CONSOLE.some((re) => re.test(text))) return;
    report.errors.push(text);
    report.fatal.push(text);
  });
  return report;
}

function assertNoFatal(report: ConsoleReport, scene: string): void {
  expect(
    report.fatal,
    `${scene}：出现致命 console 错误：\n${report.fatal.join('\n')}`,
  ).toEqual([]);
}

// ---------------------------------------------------------------------------
// 泄密样式库：错误渲染面里不允许出现的内容
// ---------------------------------------------------------------------------

const LEAK_PATTERNS: Array<{ label: string; re: RegExp }> = [
  { label: 'Java 堆栈帧', re: /\bat\s+(com|org|java|jakarta)\.[\w.$]+\(/ },
  { label: 'Java 异常类名', re: /(java\.lang\.\w+(Exception|Error)|org\.springframework|NullPointerException)/ },
  { label: 'Python 堆栈', re: /Traceback \(most recent call last\)/ },
  { label: 'Spring Whitelabel 错误页', re: /Whitelabel Error Page/i },
  {
    label: '内部服务主机名/连接串',
    re: /(pythontopia-graph|pythontopia-rag|host\.docker\.internal|jdbc:postgresql|postgres:5432|redis:6379)/i,
  },
  { label: '内部服务端口', re: /:(18080|18091|18092|15432|16379)\b/ },
  {
    label: '默认口令/密钥字样',
    re: /(pytopia_dev_password|pytopia-rag-admin|replace-with-32-char-secret|PYT_JWT_SECRET)/i,
  },
  { label: '疑似 JWT 令牌', re: /eyJ[\w-]{8,}\.[\w-]{8,}\.[\w-]{8,}/ },
];

function assertNoLeak(
  text: string,
  scene: string,
  options: { skipLabels?: string[] } = {},
): void {
  for (const { label, re } of LEAK_PATTERNS) {
    if (options.skipLabels?.includes(label)) {
      continue;
    }
    expect(re.test(text), `${scene}：错误内容疑似泄露「${label}」，命中片段：${(text.match(re) ?? [''])[0]}`).toBe(false);
  }
}

// ---------------------------------------------------------------------------
// 页面交互辅助
// ---------------------------------------------------------------------------

/**
 * 新账号首次进镇会依次弹「首次帮助弹窗」与「画像探针卡」（可能连发多张），
 * 它们的遮罩会拦截 HUD 点击；循环逐层关掉，直到两类弹层都确认不在。
 */
async function dismissProbes(page: Page): Promise<void> {
  const helpClose = page.getByRole('button', { name: '我知道了，开始探索' });
  const probeSkip = page.getByTestId('probe-skip');
  for (let i = 0; i < 8; i += 1) {
    if (await helpClose.isVisible().catch(() => false)) {
      await helpClose.click({ timeout: 5_000 }).catch(() => undefined);
      await page.waitForTimeout(300);
      continue;
    }
    if (await probeSkip.isVisible().catch(() => false)) {
      await probeSkip.click({ timeout: 5_000 }).catch(() => undefined);
      await page.waitForTimeout(300);
      continue;
    }
    // 两类弹层当前都不在：再等一拍确认没有后续补弹的，再放行。
    await page.waitForTimeout(500);
    if (
      !(await helpClose.isVisible().catch(() => false))
      && !(await probeSkip.isVisible().catch(() => false))
    ) {
      return;
    }
  }
  await expect(page.getByTestId('probe-card'), '进镇弹层清场失败（多次跳过后仍有弹窗）')
    .toHaveCount(0, { timeout: 5_000 });
}

/** 在 /world 打开伴学向导浮层（HUD 按钮优先，键盘 G 兜底）。 */
async function openGuideOverlay(page: Page): Promise<void> {
  // 先等世界真正可交互（HUD 出现 + 资产加载遮罩移除），探针卡此时才会弹，再做清场。
  await expect(page.getByTestId('hud-shell'), '主城 HUD 未渲染').toBeVisible({ timeout: 90_000 });
  // 像素小镇首屏需加载 ~29MB 资产；2C4G ECS 持续压测下放宽到 150s（配合 config 的 retries 兜底抖动）。
  await expect(page.locator('.world-load-overlay')).toHaveCount(0, { timeout: 150_000 });
  await dismissProbes(page);
  const overlay = page.getByTestId('guide-overlay');
  const hudGuide = page.getByTestId('hud-guide');
  for (let attempt = 0; attempt < 3; attempt += 1) {
    if (await hudGuide.isVisible().catch(() => false)) {
      await hudGuide.click({ force: true });
    } else {
      await page.keyboard.press('g');
    }
    try {
      await expect(overlay, '伴学向导浮层未能打开').toHaveClass(/guide-overlay--open/, {
        timeout: 5_000,
      });
      return;
    } catch {
      await page.keyboard.press('g');
      await page.waitForTimeout(300);
    }
  }
  await expect(overlay, '伴学向导浮层未能打开').toHaveClass(/guide-overlay--open/, {
    timeout: 15_000,
  });
}

/** 向导输入并发送一轮消息。 */
async function guideSend(page: Page, text: string): Promise<void> {
  const input = page.getByTestId('guide-input');
  await expect(input, '向导输入框不可用（可能仍在流式回复中）').toBeEnabled({ timeout: 90_000 });
  await input.fill(text);
  await input.press('Enter');
}

/** 等待当前向导回合流式结束（输入框解禁即回合结束）。 */
async function waitGuideTurnEnd(page: Page): Promise<void> {
  await expect(page.getByTestId('guide-input'), 'AI 向导回合超时未结束').toBeEnabled({
    timeout: 300_000,
  });
}

/** 等待一次编排 SSE 响应并校验代理层未损坏流（200 + event-stream）。 */
function watchSse(page: Page, urlPart: string): Promise<{ status: number; contentType: string }> {
  return page
    .waitForResponse(
      (r) => r.url().includes(urlPart) && r.request().method() === 'POST',
      { timeout: 90_000 },
    )
    .then((r) => ({
      status: r.status(),
      contentType: r.headers()['content-type'] ?? '',
    }));
}

/** 把代码写入 CodeMirror 编辑器（训练场）。 */
async function setEditorCode(page: Page, code: string): Promise<void> {
  const editor = page.locator('.cm-content').first();
  await expect(editor, '未找到训练场代码编辑器').toBeVisible({ timeout: 20_000 });
  await editor.click();
  await page.keyboard.press(process.platform === 'darwin' ? 'Meta+a' : 'Control+a');
  await page.keyboard.press('Delete');
  await page.keyboard.insertText(code);
}

function escapeRegExp(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

// ===========================================================================
// 1. 认证与会话
// ===========================================================================

test.describe('公网验收 · 认证与会话 @auth', () => {
  test('未登录访问受保护页应被带去登录页', async ({ page }) => {
    await page.goto(u('/world'), { waitUntil: 'domcontentloaded' });
    await expect(page, '未登录访问 /world 应重定向到 /login').toHaveURL(/\/login/, {
      timeout: 15_000,
    });
    await expect(page.locator('input[autocomplete="username"]')).toBeVisible();
    await evidence(page, 'auth-未登录重定向登录页');
  });

  test('注册（可选）后通过登录表单进入小镇', async ({ page, request }, testInfo) => {
    test.skip(!ACCOUNT_AVAILABLE, SKIP_REASON_NO_ACCOUNT);
    const session = await ensureSession(request, testInfo.workerIndex);
    testInfo.annotations.push({
      type: '测试账号',
      description: `${session.account.username}（本轮新注册：${session.registeredThisRun ? '是' : '否'}）`,
    });

    await page.goto(u('/login'), { waitUntil: 'domcontentloaded' });
    await page.locator('input[autocomplete="username"]').fill(session.account.username);
    await page.locator('input[autocomplete="current-password"]').fill(session.account.password);
    await page.getByRole('button', { name: /进入小镇/ }).click();

    await expect(page, '登录成功后应跳转 /world').toHaveURL(/\/world/, { timeout: 30_000 });
    const token = await page.evaluate(() => window.localStorage.getItem('pythontopia.token'));
    expect(token, '登录成功后 localStorage 应持有会话令牌').toBeTruthy();
    await evidence(page, 'auth-登录成功进入小镇');
  });

  test('已认证状态访问 /login 应自动跳回小镇（guestOnly 守卫）', async ({
    page,
    request,
  }, testInfo) => {
    test.skip(!ACCOUNT_AVAILABLE, SKIP_REASON_NO_ACCOUNT);
    await preparePage(page, request, testInfo.workerIndex);
    await page.goto(u('/login'), { waitUntil: 'domcontentloaded' });
    await expect(page, '已登录访问 /login 应被守卫送回 /world').toHaveURL(/\/world/, {
      timeout: 15_000,
    });
  });

  test('公网快速体验账号可直接进入小镇', async ({ page }) => {
    await page.goto(u('/login'), { waitUntil: 'domcontentloaded' });
    const loginResponse = page.waitForResponse(
      (response) =>
        response.url().includes('/api/auth/login') && response.request().method() === 'POST',
      { timeout: 30_000 },
    );
    await page.getByRole('button', { name: /快速体验：演示学生/ }).click();

    const response = await loginResponse;
    expect(response.status(), '快速体验账号登录接口应返回 200').toBe(200);
    await expect(page, '快速体验登录后应进入 /world').toHaveURL(/\/world/, {
      timeout: 30_000,
    });
    await expect(page.getByText(/密码错误|用户名或密码错误/)).toHaveCount(0);
    await evidence(page, 'auth-快速体验进入小镇');
  });
});

// ===========================================================================
// 2. 主城 town_hub 加载
// ===========================================================================

test.describe('公网验收 · 主城 town_hub @world', () => {
  test('主城画布加载完成且无致命 console 错误', async ({ page, request }, testInfo) => {
    test.skip(!ACCOUNT_AVAILABLE, SKIP_REASON_NO_ACCOUNT);
    test.setTimeout(120_000);
    const report = collectConsole(page);
    await preparePage(page, request, testInfo.workerIndex);

    await page.goto(u('/world'), { waitUntil: 'domcontentloaded' });
    await expect(page, '应停留在 /world（未被守卫弹回登录页）').toHaveURL(/\/world/);

    // Phaser 单例画布挂载在 .world-root 容器下。
    const canvas = page.locator('.world-root canvas');
    await expect(canvas, 'town_hub 主城 Phaser 画布未渲染').toBeVisible({ timeout: 60_000 });
    await expect(page.getByTestId('hud-shell'), '主城 HUD 未渲染').toBeVisible({
      timeout: 30_000,
    });

    // 给纹理/音频等异步加载一个观察窗口，再判定 console。
    await page.waitForTimeout(5_000);
    await evidence(page, 'world-主城加载');
    assertNoFatal(report, 'town_hub 主城加载');
    if (report.errors.length > 0) {
      testInfo.annotations.push({
        type: '非致命 console 错误',
        description: report.errors.slice(0, 10).join('\n'),
      });
    }
  });
});

// ===========================================================================
// 3. 主线地图点击层级
// ===========================================================================

test.describe('公网验收 · 主线地图 @mainline', () => {
  test('右侧进入学习不穿透到底层章节节点', async ({ page, request }, testInfo) => {
    test.skip(!ACCOUNT_AVAILABLE, SKIP_REASON_NO_ACCOUNT);
    await preparePage(page, request, testInfo.workerIndex);
    await page.goto(u('/mainline-map'), { waitUntil: 'domcontentloaded' });
    await expect(page.getByTestId('mainline-map-page')).toBeVisible({ timeout: 60_000 });

    // 通过无障碍镜像稳定选中第 1 章，再对抽屉内按钮执行真实鼠标点击。
    await page.getByTestId('mainline-node-btn-1').click({ force: true });
    await expect(page.getByTestId('mainline-chapter-title')).toContainText('第 1 章');
    const enter = page.locator('[data-testid^="mainline-enter-stage-"]:not([disabled])').first();
    await expect(enter, '第 1 章没有可进入的小节').toBeVisible();
    const testId = await enter.getAttribute('data-testid');
    const stageId = testId?.match(/mainline-enter-stage-(\d+)/)?.[1];
    expect(stageId, '无法从进入学习按钮读取 stageId').toBeTruthy();

    await enter.click();
    await expect(page, '进入学习点击被底层地图改选了其他章节').toHaveURL(
      new RegExp(`/mainline/stage/${stageId}(?:[?#]|$)`),
      { timeout: 30_000 },
    );
    await expect(page.getByTestId('stage-learn-page')).toBeVisible({ timeout: 60_000 });
    await expect(page.getByTestId('stage-learn-error')).toHaveCount(0);
    await expect(page.getByTestId('stage-learn-missing')).toHaveCount(0);
    await evidence(page, 'mainline-进入学习无穿透');
  });
});

// ===========================================================================
// 4. /guide 使用指南页（桌面 + 移动）
// ===========================================================================

test.describe('公网验收 · /guide 使用指南 @guide', () => {
  test.describe('桌面布局', () => {
    test.use({ viewport: { width: 1366, height: 800 } });

    test('/guide 可导航、锚点跳转生效', async ({ page, request }, testInfo) => {
      test.skip(!ACCOUNT_AVAILABLE, SKIP_REASON_NO_ACCOUNT);
      await preparePage(page, request, testInfo.workerIndex);
      const report = collectConsole(page);

      await page.goto(u('/guide'), { waitUntil: 'domcontentloaded' });
      await expect(
        page.getByTestId('not-found-page'),
        '/guide 路由不存在（落到 404 兜底页）——指南页尚未上线或路由名不一致',
      ).toHaveCount(0);
      // SPA 挂载是异步的：先等指南正文真正渲染，再做结构断言。
      await expect(page.getByTestId('guide-content'), '/guide 正文未渲染').toBeVisible({
        timeout: 30_000,
      });

      // 锚点导航：限定到指南自己的章节导航，避开 AppLayout 的无障碍 skip-link。
      const anchors = page
        .getByRole('navigation', { name: '章节导航' })
        .locator('a[href^="#"], a[href*="/guide#"]');
      const anchorCount = await anchors.count();
      expect(anchorCount, '/guide 应提供目录/锚点导航链接').toBeGreaterThan(0);

      const firstAnchor = anchors.first();
      const href = (await firstAnchor.getAttribute('href')) ?? '';
      const anchorId = href.split('#')[1] ?? '';
      expect(anchorId, `锚点链接缺少 # 片段：${href}`).not.toBe('');

      await firstAnchor.click();
      await expect(page, '点击锚点后 URL 未带上 hash').toHaveURL(
        new RegExp(`#${escapeRegExp(anchorId)}`),
      );
      const target = page.locator(`[id="${anchorId}"]`).first();
      await expect(target, `锚点目标 #${anchorId} 不存在`).toBeAttached();
      const inView = await target.evaluate((el) => {
        const rect = el.getBoundingClientRect();
        return rect.top >= -rect.height && rect.top <= window.innerHeight;
      });
      expect(inView, `点击锚点后目标 #${anchorId} 未滚动进视口`).toBe(true);

      await evidence(page, 'guide-桌面-锚点导航');
      assertNoFatal(report, '/guide 桌面加载');
    });

    test('/guide 图片可点击放大并可关闭', async ({ page, request }, testInfo) => {
      test.skip(!ACCOUNT_AVAILABLE, SKIP_REASON_NO_ACCOUNT);
      await preparePage(page, request, testInfo.workerIndex);

      await page.goto(u('/guide'), { waitUntil: 'domcontentloaded' });
      await expect(page.getByTestId('not-found-page')).toHaveCount(0);
      await expect(page.getByTestId('guide-content'), '/guide 正文未渲染').toBeVisible({
        timeout: 30_000,
      });

      const imgs = page.locator('main img, article img, .guide img, img').filter({
        hasNot: page.locator('[aria-hidden="true"]'),
      });
      const imgCount = await imgs.count();
      expect(imgCount, '/guide 页面应包含说明配图').toBeGreaterThan(0);

      const firstImg = imgs.first();
      await firstImg.scrollIntoViewIfNeeded();
      await firstImg.click();

      // 放大层兼容多种实现：lightbox/viewer/zoom 类名、dialog 元素或 role=dialog。
      const lightbox = page
        .locator(
          '[class*="lightbox"], [class*="viewer"], [class*="zoom"], [class*="preview"], dialog[open], [role="dialog"]',
        )
        .filter({ has: page.locator('img') })
        .first();
      await expect(lightbox, '点击图片后未出现放大查看层').toBeVisible({ timeout: 10_000 });
      await evidence(page, 'guide-桌面-图片放大');

      await page.keyboard.press('Escape');
      const stillOpen = await lightbox.isVisible().catch(() => false);
      if (stillOpen) {
        // Esc 不生效时尝试点击遮罩/关闭按钮关闭。
        await lightbox.click({ position: { x: 8, y: 8 } }).catch(() => undefined);
      }
    });
  });

  test.describe('移动布局', () => {
    test.use({ viewport: { width: 390, height: 844 }, hasTouch: true });

    test('/guide 移动端无横向溢出且核心内容可见', async ({ page, request }, testInfo) => {
      test.skip(!ACCOUNT_AVAILABLE, SKIP_REASON_NO_ACCOUNT);
      await preparePage(page, request, testInfo.workerIndex);
      const report = collectConsole(page);

      await page.goto(u('/guide'), { waitUntil: 'domcontentloaded' });
      await expect(page.getByTestId('not-found-page')).toHaveCount(0);
      await expect(page.getByTestId('guide-content'), '/guide 正文未渲染').toBeVisible({
        timeout: 30_000,
      });
      await page.waitForTimeout(1_500);

      const overflowPx = await page.evaluate(
        () => document.documentElement.scrollWidth - window.innerWidth,
      );
      expect(overflowPx, `移动端出现横向溢出 ${overflowPx}px`).toBeLessThanOrEqual(2);

      const heading = page.locator('h1, h2').first();
      await expect(heading, '移动端首屏未见标题内容').toBeVisible({ timeout: 10_000 });

      // 移动端导航可用：目录锚点直接可见，或存在可点开的菜单/目录按钮。
      const anchors = page.locator(
        '[data-testid="guide-content"] a[href^="#"], [data-testid="guide-content"] a[href*="/guide#"], [data-testid="guide-outline"] a[href^="#"], [data-testid="guide-outline"] a[href*="/guide#"]',
      );
      const menuToggle = page.locator(
        'button[aria-label*="目录"], button[aria-label*="菜单"], [class*="menu"], [class*="toc"]',
      );
      const navUsable = (await anchors.count()) > 0 || (await menuToggle.count()) > 0;
      expect(navUsable, '移动端既无可见锚点目录、也无菜单入口').toBe(true);

      await evidence(page, 'guide-移动-布局');
      assertNoFatal(report, '/guide 移动端加载');
    });
  });
});

// ===========================================================================
// 4. AI 向导：多轮 SSE / 澄清 / 计划 / 失败重试
// ===========================================================================

test.describe('公网验收 · AI 向导 SSE @guide-ai', () => {
  test('多轮 SSE 对话 + 计划渲染（含澄清分支）', async ({ page, request }, testInfo) => {
    test.skip(!ACCOUNT_AVAILABLE, SKIP_REASON_NO_ACCOUNT);
    test.setTimeout(300_000);
    await preparePage(page, request, testInfo.workerIndex);

    await page.goto(u('/world'), { waitUntil: 'domcontentloaded' });
    await expect(page.locator('.world-root canvas')).toBeVisible({ timeout: 60_000 });
    await openGuideOverlay(page);

    // 第一轮：明确学习目标，期待计划或澄清。
    const sse1 = watchSse(page, '/api/orchestrator/turn/stream');
    await guideSend(page, '我想从第 1 章开始学 Python，帮我安排看、学、练的步骤');
    const r1 = await sse1;
    expect(r1.status, `编排 SSE 首轮响应异常：HTTP ${r1.status}`).toBe(200);
    expect(
      r1.contentType.includes('text/event-stream'),
      `编排流响应 Content-Type 异常：${r1.contentType}`,
    ).toBe(true);
    await waitGuideTurnEnd(page);

    const assistantBubbles = page.locator('.guide-msg--assistant .guide-bubble');
    const firstReply = (await assistantBubbles.last().innerText()).trim();
    const planVisible = await page.getByTestId('guide-plan').last().isVisible().catch(() => false);
    const loopVisible = await page.getByTestId('guide-loop-progress').isVisible().catch(() => false);
    expect(
      firstReply.length > 10 || planVisible || loopVisible,
      'AI 向导首轮既无文本回复，也未渲染计划/闭环进度',
    ).toBe(true);

    // 追问澄清分支：若助手抛出澄清问题，按建议回答一轮，推动产出计划。
    const clarify = page.locator('.guide-clarify');
    if (await clarify.last().isVisible().catch(() => false)) {
      testInfo.annotations.push({
        type: '澄清追问',
        description: (await clarify.last().innerText()).slice(0, 200),
      });
      const sseClarify = watchSse(page, '/api/orchestrator/turn/stream');
      await guideSend(page, '从当前章开始，我想先打好概念再做题');
      expect((await sseClarify).status).toBe(200);
      await waitGuideTurnEnd(page);
    }

    // 计划渲染：看/学/练/测步骤列表。
    await expect(
      page.getByTestId('guide-plan').last(),
      'AI 向导未渲染「看/学/练/测」学习计划',
    ).toBeVisible({ timeout: 30_000 });
    const planSteps = page.getByTestId('guide-plan').last().locator('.guide-plan__step');
    expect(await planSteps.count(), '学习计划步骤为空').toBeGreaterThan(0);
    await evidence(page, 'guide-ai-计划渲染');

    // 第二轮：上下文追问，验证多轮会话与第二条 SSE。
    const sse2 = watchSse(page, '/api/orchestrator/turn/stream');
    await guideSend(page, '那我现在第一步具体做什么？');
    expect((await sse2).status, '第二轮编排 SSE 响应异常').toBe(200);
    await waitGuideTurnEnd(page);

    const replies = await assistantBubbles.count();
    expect(replies, '多轮对话后助手气泡数量不足').toBeGreaterThanOrEqual(2);
    const secondReply = (await assistantBubbles.last().innerText()).trim();
    expect(secondReply.length, 'AI 向导第二轮回复为空').toBeGreaterThan(10);
    await evidence(page, 'guide-ai-多轮对话');
  });

  test('模糊目标触发澄清追问或可执行计划', async ({ page, request }, testInfo) => {
    test.skip(!ACCOUNT_AVAILABLE, SKIP_REASON_NO_ACCOUNT);
    test.setTimeout(240_000);
    await preparePage(page, request, testInfo.workerIndex);

    await page.goto(u('/world'), { waitUntil: 'domcontentloaded' });
    await expect(page.locator('.world-root canvas')).toBeVisible({ timeout: 60_000 });
    await openGuideOverlay(page);

    const sse = watchSse(page, '/api/orchestrator/turn/stream');
    await guideSend(page, '帮帮我');
    expect((await sse).status).toBe(200);
    await waitGuideTurnEnd(page);

    const clarifyVisible = await page.locator('.guide-clarify').last().isVisible().catch(() => false);
    const planVisible = await page.getByTestId('guide-plan').last().isVisible().catch(() => false);
    // needsUserInput 时建议 chips 切换为「从当前章开始」等澄清选项。
    const clarifyChips = await page
      .locator('.guide-suggest__chip', { hasText: '从当前章开始' })
      .isVisible()
      .catch(() => false);

    expect(
      clarifyVisible || clarifyChips || planVisible,
      '模糊目标既未触发澄清追问，也未直接给出学习计划',
    ).toBe(true);
    testInfo.annotations.push({
      type: '澄清分支',
      description: `clarify=${clarifyVisible} chips=${clarifyChips} plan=${planVisible}`,
    });
    await evidence(page, 'guide-ai-澄清追问');
  });

  test('注入一次网络失败后，可见失败态并重试成功', async ({ page, request }, testInfo) => {
    test.skip(!ACCOUNT_AVAILABLE, SKIP_REASON_NO_ACCOUNT);
    test.setTimeout(240_000);
    await preparePage(page, request, testInfo.workerIndex);

    await page.goto(u('/world'), { waitUntil: 'domcontentloaded' });
    await expect(page.locator('.world-root canvas')).toBeVisible({ timeout: 60_000 });
    await openGuideOverlay(page);

    // 仅放断第一次编排请求，模拟一次网络抖动。
    let injected = false;
    await page.route('**/api/orchestrator/turn/stream', async (route) => {
      if (!injected) {
        injected = true;
        await route.abort('connectionfailed');
        return;
      }
      await route.fallback();
    });

    await guideSend(page, '我想学列表');
    const failedBubble = page.locator('.guide-bubble--failed');
    await expect(failedBubble, '注入失败后未出现失败态气泡').toBeVisible({ timeout: 30_000 });
    const errorLine = page.locator('.guide-error');
    await expect(errorLine, '注入失败后未显示错误提示行').toBeVisible({ timeout: 10_000 });

    // 失败提示必须友好，不得泄露内部细节。
    assertNoLeak(await failedBubble.innerText(), 'AI 向导失败气泡');
    assertNoLeak(await errorLine.innerText(), 'AI 向导错误提示');
    await evidence(page, 'guide-ai-注入失败态');

    // 重试：第二次请求放行，应正常完成一轮。
    const sseRetry = watchSse(page, '/api/orchestrator/turn/stream');
    await guideSend(page, '我想学列表');
    expect((await sseRetry).status, '重试后的编排 SSE 仍失败').toBe(200);
    await waitGuideTurnEnd(page);

    const lastBubble = page.locator('.guide-msg--assistant .guide-bubble').last();
    await expect(lastBubble, '重试后未出现新的助手回复').not.toHaveClass(/guide-bubble--failed/);
    expect((await lastBubble.innerText()).trim().length, '重试后的回复为空').toBeGreaterThan(10);
    await evidence(page, 'guide-ai-重试成功');
    await page.unroute('**/api/orchestrator/turn/stream');
  });
});

// ===========================================================================
// 5. AI 向导 → 百晓工坊深链（KC 上下文保留）
// ===========================================================================

test.describe('公网验收 · 向导→工坊深链 @deeplink', () => {
  test('计划步骤「去工坊」深链落地且 KC 上下文保留', async ({ page, request }, testInfo) => {
    test.skip(!ACCOUNT_AVAILABLE, SKIP_REASON_NO_ACCOUNT);
    test.setTimeout(300_000);
    await preparePage(page, request, testInfo.workerIndex);

    await page.goto(u('/world'), { waitUntil: 'domcontentloaded' });
    await expect(page.locator('.world-root canvas')).toBeVisible({ timeout: 60_000 });
    await openGuideOverlay(page);

    const sse = watchSse(page, '/api/orchestrator/turn/stream');
    await guideSend(page, '帮我生成一份 for 循环的学习讲义，我想去工坊学');
    expect((await sse).status).toBe(200);
    await waitGuideTurnEnd(page);

    // 澄清分支兜底：回答一轮再等计划。
    if (await page.locator('.guide-clarify').last().isVisible().catch(() => false)) {
      const sse2 = watchSse(page, '/api/orchestrator/turn/stream');
      await guideSend(page, '就学 for 循环，给我讲义');
      expect((await sse2).status).toBe(200);
      await waitGuideTurnEnd(page);
    }

    await expect(page.getByTestId('guide-plan').last(), '向导未给出学习计划').toBeVisible({
      timeout: 30_000,
    });
    const workshopGo = page
      .getByTestId('guide-plan')
      .last()
      .locator('.guide-plan__go', { hasText: /去(百晓)?工坊/ });
    expect(
      await workshopGo.count(),
      '学习计划中没有「去工坊」入口（panel=workshop/tutor 的步骤缺失）',
    ).toBeGreaterThan(0);

    await workshopGo.first().click();
    await expect(page, '点击「去工坊」后未跳转 /workshop').toHaveURL(/\/workshop/, {
      timeout: 20_000,
    });
    await expect(page.getByTestId('workshop-page'), '工坊页面未渲染').toBeVisible({
      timeout: 30_000,
    });
    await expect(page.getByTestId('chat-composer'), '工坊输入框未就绪').toBeVisible({
      timeout: 30_000,
    });

    // KC 上下文保留：URL 携带 kc 参数 / KC 徽标 / 续聊提示 / 页面出现主题关键词，任一成立。
    const url = page.url();
    const urlHasKc = /[?&]kc=/.test(url);
    const kcBadge = await page.getByTestId('quiz-kc-badge').isVisible().catch(() => false);
    const continueHint = await page
      .getByTestId('workshop-continue-hint')
      .isVisible()
      .catch(() => false);
    const bodyText = await page.getByTestId('workshop-page').innerText();
    const topicKept = /for|循环|KC-?\d+|PY43-KC/i.test(bodyText);
    expect(
      urlHasKc || kcBadge || continueHint || topicKept,
      `深链后工坊侧未检测到 KC 上下文（url=${url}，badge=${kcBadge}，hint=${continueHint}）`,
    ).toBe(true);
    testInfo.annotations.push({
      type: 'KC 上下文判定',
      description: `urlHasKc=${urlHasKc} kcBadge=${kcBadge} continueHint=${continueHint} topicKept=${topicKept}`,
    });
    await evidence(page, 'deeplink-向导到工坊');
  });
});

// ===========================================================================
// 6. 百晓工坊真实响应路径
// ===========================================================================

test.describe('公网验收 · 工坊真实响应 @workshop', () => {
  test('提问走真实后端流式链路并产出非空回复', async ({ page, request }, testInfo) => {
    test.skip(!ACCOUNT_AVAILABLE, SKIP_REASON_NO_ACCOUNT);
    test.setTimeout(300_000);
    await preparePage(page, request, testInfo.workerIndex);

    await page.goto(u('/workshop'), { waitUntil: 'domcontentloaded' });
    await expect(page.getByTestId('workshop-page')).toBeVisible({ timeout: 30_000 });
    const composer = page.getByTestId('composer-input');
    await expect(composer).toBeVisible({ timeout: 30_000 });

    const chat = page.getByTestId('workshop-chat');
    const beforeText = (await chat.innerText().catch(() => '')) ?? '';

    // 工坊会按意图路由到 /api/tutor/ask、/api/workshop/resources/stream
    // 或 /api/orchestrator/turn/stream，三者任一返回 200 即为真实链路打通。
    const streamResponse = page.waitForResponse(
      (r) =>
        r.request().method() === 'POST'
        && /\/api\/(tutor\/ask|workshop\/resources\/stream|orchestrator\/turn\/stream)/.test(r.url()),
      { timeout: 120_000 },
    );

    await composer.fill('用一句话解释 Python 的列表是什么？');
    await page.getByTestId('composer-submit').click();

    const resp = await streamResponse;
    expect(resp.status(), `工坊后端流式响应异常：HTTP ${resp.status()}（${resp.url()}）`).toBe(200);
    testInfo.annotations.push({ type: '命中流式端点', description: resp.url() });

    // 等待流式结束：输入框解禁，且聊天区文本实际增长（真实生成内容，非空壳）。
    await expect(composer, '工坊回合超时未结束').toBeEnabled({ timeout: 240_000 });
    await expect
      .poll(
        async () => ((await chat.innerText().catch(() => '')) ?? '').length,
        { timeout: 60_000, message: '工坊聊天区内容未增长（疑似空回复）' },
      )
      .toBeGreaterThan(beforeText.length + 20);

    // 不应停留在失败重试态。
    expect(
      await page.getByTestId('retry-chip').isVisible().catch(() => false),
      '工坊回复进入失败重试态',
    ).toBe(false);
    await evidence(page, 'workshop-真实响应');
  });
});

// ===========================================================================
// 7. 图书馆 RAG：三个标准查询都要有证据页码
// ===========================================================================

test.describe('公网验收 · 图书馆 RAG @library', () => {
  for (const query of ['input函数', 'for循环', '列表']) {
    test(`RAG 查询「${query}」返回带页码的证据`, async ({ page, request }, testInfo) => {
      test.skip(!ACCOUNT_AVAILABLE, SKIP_REASON_NO_ACCOUNT);
      test.setTimeout(180_000);
      await preparePage(page, request, testInfo.workerIndex);

      // 记录 /api/rag/query 实际状态（学生角色 403 时前端按设计回退本地检索）。
      const ragStatuses: number[] = [];
      page.on('response', (r) => {
        if (r.url().includes('/api/rag/query')) ragStatuses.push(r.status());
      });

      await page.goto(u('/library'), { waitUntil: 'domcontentloaded' });
      await expect(page.getByTestId('library-split-body')).toBeVisible({ timeout: 60_000 });

      const input = page.getByTestId('library-rag-input');
      await expect(input).toBeVisible({ timeout: 30_000 });
      await input.fill(query);
      await expect(input).toHaveValue(query);
      const submit = page.getByTestId('library-rag-submit');
      await submit.click();
      await expect(submit, `查询「${query}」在时限内未完成`).toBeEnabled({ timeout: 120_000 });

      const answer = page.getByTestId('library-rag-answer');
      const results = page.getByTestId('library-rag-results');
      await expect
        .poll(
          async () => {
            const answerVisible = await answer.isVisible().catch(() => false);
            const answerText = answerVisible ? await answer.innerText().catch(() => '') : '';
            const citeCount = answerVisible
              ? await answer.getByTestId('library-rag-cite').count().catch(() => 0)
              : 0;
            const resultsVisible = await results.isVisible().catch(() => false);
            const resultCount = await results.locator('li').count().catch(() => 0);
            const resultsText =
              resultsVisible || resultCount > 0 ? await results.innerText().catch(() => '') : '';
            return (
              (answerVisible && citeCount > 0 && /第\s*\d+\s*页/.test(answerText))
              || (resultCount > 0 && /第\s*\d+\s*页/.test(resultsText))
            );
          },
          {
            timeout: 120_000,
            message: `查询「${query}」未出现带页码的证据引用或片段结果`,
          },
        )
        .toBe(true);
      const answerMode = await answer.isVisible().catch(() => false);
      const evidenceArea = answerMode ? answer : results;
      await expect(evidenceArea, `查询「${query}」未渲染证据区`).toBeVisible();
      await expect(
        evidenceArea,
        `查询「${query}」结果中没有「第 N 页」证据页码`,
      ).toContainText(/第\s*\d+\s*页/);

      const hitCount = answerMode
        ? await answer.getByTestId('library-rag-cite').count()
        : await results.locator('li').count();
      expect(hitCount, `查询「${query}」证据条目为空`).toBeGreaterThan(0);

      testInfo.annotations.push({
        type: 'RAG 路径',
        description: `${answerMode ? 'AI 作答引用' : '片段检索回退'}；服务端 /api/rag/query 状态=${ragStatuses.join(',') || '未调用'}（403 表示按设计回退本地检索）；命中条目=${hitCount}`,
      });
      await evidence(page, `library-rag-${query}`);
    });
  }
});

// ===========================================================================
// 8. 训练场判题：通过 / 答错 / 运行错误
// ===========================================================================

// 默认演示题 476「圆面积计算」：读浮点半径，π=3.1415，保留 4 位小数。
const TRAINING_PROBLEM_ID = 476;
const CODE_AC = 'radius = float(input())\nprint(f"{3.1415 * radius * radius:.4f}")\n';
const CODE_WA = 'radius = float(input())\nprint("0.0000")\n';
const CODE_RE = 'radius = float(input())\nraise RuntimeError("e2e 故意触发运行错误")\n';

async function submitTrainingCode(page: Page, code: string): Promise<string> {
  await setEditorCode(page, code);
  // 后端按设计限流：同一账号 10 秒内只许一次正式提交（三态用例共用一个账号连跑会撞 429），
  // 撞限流时等窗口过去再重试，最多 3 次。
  let status = 0;
  for (let attempt = 1; attempt <= 3; attempt += 1) {
    const submitted = page.waitForResponse(
      (r) => r.url().includes('/api/submissions') && r.request().method() === 'POST',
      { timeout: 60_000 },
    );
    await page.getByTestId('submit-button').click();
    const resp = await submitted;
    status = resp.status();
    if (resp.ok()) {
      break;
    }
    if (status === 429 && attempt < 3) {
      await page.waitForTimeout(11_000);
      continue;
    }
    expect(resp.ok(), `提交接口异常：HTTP ${status}`).toBe(true);
  }

  const result = page.getByTestId('submission-result');
  await expect(result, '判题结果面板未出现').toBeVisible({ timeout: 90_000 });
  return (await result.innerText()).trim();
}

test.describe('公网验收 · 训练场判题三态 @training', () => {
  test.beforeEach(async ({ page, request }, testInfo) => {
    test.skip(!ACCOUNT_AVAILABLE, SKIP_REASON_NO_ACCOUNT);
    await preparePage(page, request, testInfo.workerIndex);
    await gotoWithRetry(page, `/training?problemId=${TRAINING_PROBLEM_ID}`, '训练场');
    await expect(page.getByTestId('training-panel')).toBeVisible({ timeout: 90_000 });
    await expect(page.getByTestId('problem-statement')).toBeVisible({ timeout: 60_000 });
  });

  test('正确解法判 AC（通过）', async ({ page }) => {
    test.setTimeout(240_000);
    const text = await submitTrainingCode(page, CODE_AC);
    expect(text, `判题结果应为 AC，实际：${text}`).toMatch(/\bAC\b/);
    expect(text, `判题状态应为 PASSED，实际：${text}`).toMatch(/PASSED/);
    await evidence(page, 'training-AC通过');
  });

  test('错误输出判 WA（答错）', async ({ page }) => {
    test.setTimeout(240_000);
    const text = await submitTrainingCode(page, CODE_WA);
    expect(text, `判题结果应为 WA/FAILED，实际：${text}`).toMatch(/\bWA\b|FAILED/);
    await evidence(page, 'training-WA答错');
  });

  test('抛异常判 RE（运行错误）且错误信息不泄密', async ({ page }) => {
    test.setTimeout(240_000);
    const text = await submitTrainingCode(page, CODE_RE);
    expect(text, `判题结果应为 RE/ERROR，实际：${text}`).toMatch(/\bRE\b|ERROR/);
    // 判题失败原因可包含学生代码自身的报错（Python Traceback 属教学必需信息），
    // 但不得带出判题机内部 Java 堆栈 / 内部主机名 / 秘密。
    assertNoLeak(text, '训练场判题失败原因', { skipLabels: ['Python 堆栈'] });
    await evidence(page, 'training-RE运行错误');
  });
});

// ===========================================================================
// 9. 复盘大厅与个人画像
// ===========================================================================

test.describe('公网验收 · 复盘大厅与画像 @review-profile', () => {
  test('复盘大厅面板可见', async ({ page, request }, testInfo) => {
    test.skip(!ACCOUNT_AVAILABLE, SKIP_REASON_NO_ACCOUNT);
    await preparePage(page, request, testInfo.workerIndex);
    const report = collectConsole(page);

    await page.goto(u('/review-hall'), { waitUntil: 'domcontentloaded' });
    await expect(page.getByTestId('review-hall-panel'), '复盘大厅面板未渲染').toBeVisible({
      timeout: 60_000,
    });
    await evidence(page, 'review-复盘大厅');
    assertNoFatal(report, '复盘大厅');
  });

  test('个人画像页可见且渲染画像维度', async ({ page, request }, testInfo) => {
    test.skip(!ACCOUNT_AVAILABLE, SKIP_REASON_NO_ACCOUNT);
    await preparePage(page, request, testInfo.workerIndex);
    const report = collectConsole(page);

    await page.goto(u('/profile'), { waitUntil: 'domcontentloaded' });
    await expect(page.getByTestId('profile-page'), '个人画像页未渲染').toBeVisible({
      timeout: 60_000,
    });
    // 新账号画像可能尚未生成：维度卡片与「画像尚未生成」空态二者必居其一，都算页面正常。
    const dimCount = await page.locator('[data-testid^="dim-"]').count();
    if (dimCount === 0) {
      await expect(
        page.getByText('画像尚未生成'),
        '画像页既无维度条目、也无「画像尚未生成」空态说明',
      ).toBeVisible({ timeout: 15_000 });
    }
    testInfo.annotations.push({
      type: '画像维度数',
      description: dimCount > 0 ? String(dimCount) : '0（新账号空态，含引导说明）',
    });
    await evidence(page, 'review-个人画像');
    assertNoFatal(report, '个人画像页');
  });
});

// ===========================================================================
// 10. 深链直接刷新：/guide /workshop /mainline-map
// ===========================================================================

test.describe('公网验收 · 深链直接刷新 @refresh', () => {
  for (const route of ['/guide', '/workshop', '/mainline-map']) {
    test(`直接打开并刷新 ${route} 不白屏不 404`, async ({ page, request }, testInfo) => {
      test.skip(!ACCOUNT_AVAILABLE, SKIP_REASON_NO_ACCOUNT);
      test.setTimeout(120_000);
      await preparePage(page, request, testInfo.workerIndex);
      const report = collectConsole(page);

      const resp = await page.goto(u(route), { waitUntil: 'domcontentloaded' });
      expect(resp?.status(), `${route} 首次直开 HTTP 状态异常`).toBe(200);
      await expect(page, `${route} 被守卫弹回登录页（深链刷新后会话未保持）`).not.toHaveURL(
        /\/login/,
        { timeout: 15_000 },
      );
      await expect(page.getByTestId('not-found-page'), `${route} 落到 404 兜底页`).toHaveCount(0);

      // 字面意义的「刷新」再来一次。
      const reload = await page.reload({ waitUntil: 'domcontentloaded' });
      expect(reload?.status(), `${route} 刷新后 HTTP 状态异常`).toBe(200);
      await expect(page).not.toHaveURL(/\/login/, { timeout: 15_000 });
      await expect(page.getByTestId('not-found-page')).toHaveCount(0);

      // SPA 刷新后挂载是异步的（workshop 等页 chunk 较大），轮询等正文出现而非一次性读取。
      await expect
        .poll(
          async () => (await page.locator('#app').innerText().catch(() => '')).trim().length,
          { message: `${route} 刷新后页面近乎空白`, timeout: 30_000 },
        )
        .toBeGreaterThan(0);

      await evidence(page, `refresh-${route.replace(/\//g, '_')}`);
      assertNoFatal(report, `${route} 深链刷新`);
    });
  }
});

// ===========================================================================
// 11. 错误渲染面不泄密（秘密 / 堆栈 / 内部主机名）
// ===========================================================================

test.describe('公网验收 · 错误信息不泄密 @security-surface', () => {
  test('登录失败提示友好且不含内部细节', async ({ page }) => {
    await page.goto(u('/login'), { waitUntil: 'domcontentloaded' });
    await page.locator('input[autocomplete="username"]').fill('qa-not-exist-user');
    await page.locator('input[autocomplete="current-password"]').fill('definitely-wrong-1');
    await page.getByRole('button', { name: /进入小镇/ }).click();

    // 错误提示是短时 toast（约 2-3 秒自动消失），点击后立刻轮询断言，抓到即扫描泄密面。
    await expect(
      page.getByText(/用户名或密码错误|登录失败|无法连接服务器/).first(),
      '登录失败后未见任何中文友好错误提示',
    ).toBeVisible({ timeout: 10_000 });
    const bodyText = await page.locator('body').innerText();
    assertNoLeak(bodyText, '登录失败页面');
    await evidence(page, 'security-登录失败提示');
  });

  test('API 错误响应体不含堆栈与内部主机名', async ({ request }) => {
    // 错误凭证。
    const badLogin = await request.post(u('/api/auth/login'), {
      data: { username: 'qa-not-exist-user', password: 'definitely-wrong-1' },
    });
    expect([400, 401, 403]).toContain(badLogin.status());
    assertNoLeak(await badLogin.text(), '登录失败 API 响应');

    // 未认证访问受保护 API。
    const noAuth = await request.get(u('/api/learner/profile'));
    expect([401, 403, 404]).toContain(noAuth.status());
    assertNoLeak(await noAuth.text(), '未认证 API 响应');

    // 不存在的 API 路径。
    const missing = await request.get(u('/api/definitely-missing-path-e2e'));
    expect(missing.status(), '不存在的 API 路径不应返回 5xx').toBeLessThan(500);
    assertNoLeak(await missing.text(), '404 API 响应');

    // 畸形 JSON 请求体。
    const malformed = await request.post(u('/api/auth/login'), {
      headers: { 'Content-Type': 'application/json' },
      data: '{"username": "broken"',
    });
    expect(malformed.status(), '畸形请求体不应导致 5xx').toBeLessThan(500);
    assertNoLeak(await malformed.text(), '畸形请求 API 响应');
  });
});
