// 引导手册 21 张截图采集套件。
//
// 运行前置：本地栈已启动（vite :5173 + backend :18080 + Postgres/微服务，推荐根目录 ./dev.sh），
// 并按 e2e/support/demoSession.ts 顶部说明设置 PYT_E2E_* 环境变量。
// 采集命令：npm run capture:guide
// 输出：frontend/public/guide/screenshots/NN-name.webp（契约文件名，1440x900）
// 文档：docs/guide/screenshot-manifest.md（页面路径 / 前置状态 / 遮蔽规则 / 重拍方法）
//
// 纪律：
//   - 只等「元素可见 / 状态元素消失 / rAF 帧稳定」这类确定性条件，不用长 sleep；
//     不用 networkidle（应用挂有 SSE 长连接，networkidle 永远不会触发）；
//   - token / 邮箱 / 真实时间戳 / 个人信息用 Playwright mask 遮蔽（见 globalMasks 与各测试 extraMasks）；
//   - 任一目标页面不可达（登录跳转 / 加载失败 / 错误态）或输出文件为空 → 测试必须失败。

import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  expect,
  test,
  type APIRequestContext,
  type BrowserContext,
  type Locator,
  type Page,
  type TestInfo,
} from '@playwright/test';
import sharp from 'sharp';
import {
  acquireStudentSession,
  acquireTeacherSession,
  ensureDemoClassroom,
  injectSession,
  suppressFirstVisitOverlays,
  type DemoSession,
} from './support/demoSession';

const SCREENSHOT_DIR = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '../public/guide/screenshots',
);
const VIEWPORT = { width: 1440, height: 900 } as const;
/** 空白/坏图兜底：正常 1440x900 界面截图的 WebP 远大于该下限。 */
const MIN_WEBP_BYTES = 4096;
const MASK_COLOR = '#10131a';

// 单 worker 串行（见 playwright.config.ts），同一学生/教师会话在全部用例间复用：
// 注册/登录只发生一次，避免每张图都新建演示账号。
let studentSessionPromise: Promise<DemoSession> | null = null;
let teacherSessionPromise: Promise<DemoSession> | null = null;

function studentSession(request: APIRequestContext): Promise<DemoSession> {
  studentSessionPromise ??= acquireStudentSession(request);
  return studentSessionPromise;
}

function teacherSession(request: APIRequestContext): Promise<DemoSession> {
  teacherSessionPromise ??= acquireTeacherSession(request);
  return teacherSessionPromise;
}

/** 注入学生会话 + 抑制首次进入引导弹层（所有学生视角用例的统一前置）。 */
async function prepareStudentContext(
  context: BrowserContext,
  request: APIRequestContext,
): Promise<void> {
  const session = await studentSession(request);
  await suppressFirstVisitOverlays(context);
  await injectSession(context, session);
}

/** 等待 n 个 rAF 帧：给 Phaser/CodeMirror/PDF 渲染留出确定性的「绘制完成」窗口，替代 sleep。 */
async function settleFrames(page: Page, frames = 4): Promise<void> {
  await page.evaluate(async (count) => {
    for (let i = 0; i < count; i += 1) {
      await new Promise<void>((resolve) => {
        window.requestAnimationFrame(() => resolve());
      });
    }
  }, frames);
}

/** 会话注入失败时路由守卫会踢回 /login——直接判失败，给出明确原因。 */
function assertAuthedUrl(page: Page): void {
  const { pathname } = new URL(page.url());
  expect(pathname.startsWith('/login'), '被重定向到登录页：会话注入失败或 token 已失效').toBe(false);
}

/** 全局遮蔽：登录昵称（AppHeader）与任何 <time> 真实时间戳。未命中的选择器自动跳过。 */
function globalMasks(page: Page): Locator[] {
  return [page.locator('.header-status__name'), page.locator('time')];
}

/**
 * 截 PNG（带遮蔽）→ sharp 转 WebP（quality 92，保证面板小字可读，不过度压缩）→
 * 校验输出尺寸 1440x900 且非空，任何一步不满足都让测试失败。
 */
async function captureShot(
  page: Page,
  testInfo: TestInfo,
  name: string,
  extraMasks: Locator[] = [],
): Promise<void> {
  assertAuthedUrl(page);
  const pngPath = testInfo.outputPath(`${name}.png`);
  const screenshotOptions = {
    path: pngPath,
    fullPage: false,
    animations: 'disabled' as const,
    caret: 'hide' as const,
    mask: [...globalMasks(page), ...extraMasks],
    maskColor: MASK_COLOR,
  };
  for (let attempt = 0; attempt < 3; attempt += 1) {
    try {
      await page.screenshot(screenshotOptions);
      break;
    } catch (error) {
      if (
        attempt === 2
        || !(error instanceof Error)
        || !/Execution context was destroyed|navigation/i.test(error.message)
      ) {
        throw error;
      }
      await page.waitForLoadState('domcontentloaded').catch(() => undefined);
      await settleFrames(page, 4);
      assertAuthedUrl(page);
    }
  }
  await fs.mkdir(SCREENSHOT_DIR, { recursive: true });
  const webpPath = path.join(SCREENSHOT_DIR, `${name}.webp`);
  await sharp(pngPath).webp({ quality: 92, effort: 5 }).toFile(webpPath);

  const meta = await sharp(webpPath).metadata();
  expect(meta.width, `${name}.webp 宽度必须为 ${VIEWPORT.width}`).toBe(VIEWPORT.width);
  expect(meta.height, `${name}.webp 高度必须为 ${VIEWPORT.height}`).toBe(VIEWPORT.height);
  const { size } = await fs.stat(webpPath);
  expect(size, `${name}.webp 输出为空或异常小（${size} 字节）`).toBeGreaterThan(MIN_WEBP_BYTES);
}

/** 进入 /world 并等小镇真正可看：HUD 出现、加载遮罩从 DOM 移除、Phaser 画布出帧。 */
async function openWorld(page: Page): Promise<void> {
  await page.goto('/world', { waitUntil: 'domcontentloaded' });
  await expect(page.locator('[data-testid="hud-shell"]')).toBeVisible();
  // 加载遮罩 v-if 移除即资产预加载完成；首次进入要下像素素材，单独放宽到 90s。
  await expect(page.locator('.world-load-overlay')).toHaveCount(0, { timeout: 90_000 });
  await expect(page.locator('.world-root canvas')).toBeVisible();
  await settleFrames(page, 8);
  await dismissProbes(page);
}

/** 新账号进镇会弹画像探针卡（认知风格等），逐个点「跳过」清场，保证截图主体不被遮挡。 */
async function dismissProbes(page: Page): Promise<void> {
  for (let i = 0; i < 4; i += 1) {
    const skip = page.locator('[data-testid="probe-skip"]');
    try {
      await skip.waitFor({ state: 'visible', timeout: 2_000 });
    } catch {
      return; // 没有（更多）探针弹窗
    }
    await skip.click();
    await expect(page.locator('[data-testid="probe-card"]')).toHaveCount(0, { timeout: 5_000 });
    await settleFrames(page, 2);
  }
}

/** 进入冒险地图并展开第 1 章抽屉（小节三件套面板），两张主线截图共用。 */
async function openMainlineChapterOne(page: Page): Promise<void> {
  await page.goto('/mainline-map', { waitUntil: 'domcontentloaded' });
  await expect(page.locator('[data-testid="mainline-map-page"]')).toBeVisible();
  await expect(page.locator('[data-testid="mainline-loading"]')).toHaveCount(0, {
    timeout: 30_000,
  });
  await expect(
    page.locator('[data-testid="mainline-error"]'),
    '冒险地图加载失败（mainline overview 接口异常）',
  ).toHaveCount(0);
  await expect(page.locator('.map-canvas canvas')).toBeVisible();
  await settleFrames(page, 8);
  // 章节选择的可达镜像列表是视觉隐藏（clip 1px）的 a11y 结构，常规 click 会因不可见而卡住，
  // 改为派发真实 click 事件走同一条 Vue 处理链路。
  await page.locator('[data-testid="mainline-node-btn-1"]').dispatchEvent('click');
  await expect(page.locator('[data-testid="mainline-stage-panel"]')).toBeVisible();
}

test.describe('引导手册 23 张截图（输出 frontend/public/guide/screenshots）', () => {
  test('01-town-hub 小镇主城', async ({ page, context, request }, testInfo) => {
    await prepareStudentContext(context, request);
    await openWorld(page);
    await captureShot(page, testInfo, '01-town-hub');
  });

  test('02-pause-menu 暂停菜单', async ({ page, context, request }, testInfo) => {
    await prepareStudentContext(context, request);
    await openWorld(page);
    await page.keyboard.press('Escape');
    await expect(page.locator('.pause-panel')).toBeVisible();
    await expect(page.locator('[data-testid="pause-resume"]')).toBeVisible();
    // 「操作帮助」是 /guide 指南页入口，滚动到可见再截，保证示意图能看到该入口。
    await page.locator('[data-testid="pause-help"]').scrollIntoViewIfNeeded();
    await settleFrames(page, 2);
    // .pause-player 含登录昵称（“某某 · 第N天”），属个人信息，遮蔽。
    await captureShot(page, testInfo, '02-pause-menu', [page.locator('.pause-player')]);
  });

  test('03-ai-guide AI 向导悬浮窗', async ({ page, context, request }, testInfo) => {
    await prepareStudentContext(context, request);
    await openWorld(page);
    await page.locator('[data-testid="hud-guide"]').click();
    await expect(page.locator('[data-testid="guide-overlay"]')).toBeVisible();
    // 欢迎语是前端内置文案，出现即说明浮窗渲染完成，不依赖 LLM 后端。
    await expect(page.locator('[data-testid="guide-thread"]')).toContainText('AI 助教');
    await captureShot(page, testInfo, '03-ai-guide');
  });

  test('04-mainline-map 主线冒险地图', async ({ page, context, request }, testInfo) => {
    await prepareStudentContext(context, request);
    await openMainlineChapterOne(page);
    await settleFrames(page, 4);
    await captureShot(page, testInfo, '04-mainline-map');
  });

  test('05-stage-learning 阶段学习页', async ({ page, context, request }, testInfo) => {
    await prepareStudentContext(context, request);
    await openMainlineChapterOne(page);
    // 进入第 1 章第一个可进入的小节（新账号下即 1-1，由后端解锁状态决定）。
    const enterButton = page
      .locator('[data-testid^="mainline-enter-stage-"]:enabled')
      .first();
    await expect(enterButton, '第 1 章没有可进入的小节（解锁状态异常）').toBeVisible();
    await enterButton.click();
    await expect(page).toHaveURL(/\/mainline\/stage\/\d+/);
    await expect(page.locator('[data-testid="stage-learn-page"]')).toBeVisible();
    await expect(page.locator('[data-testid="stage-learn-loading"]')).toHaveCount(0, {
      timeout: 30_000,
    });
    await expect(
      page.locator('[data-testid="stage-learn-missing"]'),
      '阶段学习页提示小节不存在',
    ).toHaveCount(0);
    await expect(
      page.locator('[data-testid="stage-learn-error"]'),
      '阶段学习页加载失败',
    ).toHaveCount(0);
    await expect(page.locator('[data-testid="stage-learn-toc"]')).toBeVisible();
    await settleFrames(page, 6);
    await captureShot(page, testInfo, '05-stage-learning');
  });

  test('06-library 图书馆（课件阅读器）', async ({ page, context, request }, testInfo) => {
    await prepareStudentContext(context, request);
    await page.goto('/library', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('[data-testid="library-split-body"]')).toBeVisible();
    await expect(page.locator('[data-testid^="chapter-doc-"]').first()).toBeVisible({
      timeout: 30_000,
    });
    await expect(page.locator('[data-testid="library-page-content"]')).toBeVisible();
    // PDF.js 渲染第一页需要几帧，等画布稳定再拍。
    await settleFrames(page, 10);
    await captureShot(page, testInfo, '06-library');
  });

  test('07-workshop 百晓工坊', async ({ page, context, request }, testInfo) => {
    await prepareStudentContext(context, request);
    await page.goto('/workshop', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('[data-testid="workshop-page"]')).toBeVisible();
    await expect(page.locator('[data-testid="workshop-body"]')).toBeVisible();
    await settleFrames(page, 4);
    await captureShot(page, testInfo, '07-workshop');
  });

  test('08-training 训练场', async ({ page, context, request }, testInfo) => {
    await prepareStudentContext(context, request);
    await page.goto('/training', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('[data-testid="training-panel"]')).toBeVisible();
    // 默认题目（共享课程题）从后端加载完成后题面才出现。
    await expect(page.locator('[data-testid="problem-statement"]')).toBeVisible({
      timeout: 30_000,
    });
    await expect(page.locator('.cm-editor').first()).toBeVisible();
    await settleFrames(page, 4);
    await captureShot(page, testInfo, '08-training');
  });

  test('09-bug-mine Bug 矿洞', async ({ page, context, request }, testInfo) => {
    await prepareStudentContext(context, request);
    await page.goto('/bug-mine', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('[data-testid="bug-mine-panel"]')).toBeVisible();
    await expect(page.locator('[data-testid="error-book"]')).toBeVisible({ timeout: 30_000 });
    await settleFrames(page, 4);
    await captureShot(page, testInfo, '09-bug-mine');
  });

  test('10-review-hall 复盘大厅', async ({ page, context, request }, testInfo) => {
    await prepareStudentContext(context, request);
    await page.goto('/review-hall', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('[data-testid="review-hall-panel"]')).toBeVisible({
      timeout: 30_000,
    });
    await settleFrames(page, 4);
    await captureShot(page, testInfo, '10-review-hall');
  });

  test('11-profile 个人画像', async ({ page, context, request }, testInfo) => {
    await prepareStudentContext(context, request);
    await page.goto('/profile', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('[data-testid="profile-page"]')).toBeVisible();
    await settleFrames(page, 6);
    // .profile-name 是登录昵称，遮蔽。
    await captureShot(page, testInfo, '11-profile', [page.locator('.profile-name')]);
  });

  test('12-teacher-classroom 教师班级', async ({ page, context, request }, testInfo) => {
    const session = await teacherSession(request);
    // 教师号没有班级时列表是空态，先通过真实 API 建一个演示班级。
    await ensureDemoClassroom(request, session);
    await injectSession(context, session);
    await page.goto('/teacher', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('[data-testid="teacher-home"]')).toBeVisible();
    await expect(page.locator('[data-testid="classroom-list"]')).toBeVisible({
      timeout: 30_000,
    });
    await settleFrames(page, 4);
    // 遮蔽：标题下的「这里是 XX 的班级总览」（昵称）与每张班级卡的加入码。
    await captureShot(page, testInfo, '12-teacher-classroom', [
      page.locator('.teacher-home > header p'),
      page.locator('.classroom-card .code'),
    ]);
  });

  test('13-npc-dialog 与镇长对话', async ({ page, context, request }, testInfo) => {
    await prepareStudentContext(context, request);
    await openWorld(page);
    // 经由真实 dialog store 的 openWithTree 打开镇长对话（与走到 NPC 面前按 F 走同一条
    // store→组件链路，仅触发方式编程化——出生点与 NPC 距离在 E2E 中不可控）。
    await page.evaluate(() => {
      const app = (document.querySelector('#app') as HTMLElement & {
        __vue_app__?: { config: { globalProperties: { $pinia?: { _s: Map<string, unknown> } } } };
      }).__vue_app__;
      const pinia = app?.config.globalProperties.$pinia;
      const dialog = pinia?._s.get('dialog') as
        | { openWithTree: (npcKey: string) => Promise<void> }
        | undefined;
      if (!dialog) throw new Error('dialog store 不可用');
      void dialog.openWithTree('mayor_py');
    });
    const dialogText = page.locator('[data-testid="dialog-text"]');
    await expect(dialogText, '镇长对话框未出现').toBeVisible({ timeout: 20_000 });
    // 等打字机至少打出一句话再拍。
    await expect
      .poll(async () => ((await dialogText.innerText().catch(() => '')) ?? '').length, {
        timeout: 20_000,
      })
      .toBeGreaterThan(8);
    await settleFrames(page, 4);
    await captureShot(page, testInfo, '13-npc-dialog');
  });

  test('14-ai-guide-plan AI 助教学习计划', async ({ page, context, request }, testInfo) => {
    test.setTimeout(300_000);
    await prepareStudentContext(context, request);
    await openWorld(page);
    await page.locator('[data-testid="hud-guide"]').click();
    await expect(page.locator('[data-testid="guide-overlay"]')).toBeVisible();
    const input = page.locator('[data-testid="guide-input"]');
    await input.fill('我是零基础，想从第 1 章开始学 Python，帮我安排看学练测的计划');
    await page.locator('[data-testid="guide-send"]').click();
    // 真实编排：等「看/学/练/测」计划渲染（含 LLM 时延）。
    await expect(
      page.locator('[data-testid="guide-plan"]').last(),
      'AI 助教未给出学习计划',
    ).toBeVisible({ timeout: 240_000 });
    await expect(page.locator('[data-testid="guide-input"]')).toBeEnabled({ timeout: 120_000 });
    await settleFrames(page, 4);
    await captureShot(page, testInfo, '14-ai-guide-plan');
  });

  test('15/16 阶段学习页：课件阅读与随堂练习', async ({ page, context, request }, testInfo) => {
    await prepareStudentContext(context, request);
    await openMainlineChapterOne(page);
    const enterButton = page.locator('[data-testid^="mainline-enter-stage-"]:enabled').first();
    await expect(enterButton, '第 1 章没有可进入的小节').toBeVisible();
    await enterButton.click();
    await expect(page.locator('[data-testid="stage-learn-page"]')).toBeVisible();
    await expect(page.locator('[data-testid="stage-learn-loading"]')).toHaveCount(0, {
      timeout: 30_000,
    });

    // 学 · 课件阅读
    await page.locator('[data-testid="stage-toc-courseware"]').click();
    await expect(
      page.locator('[data-testid="stage-learn-page"]'),
      '课件阅读步骤未渲染',
    ).toBeVisible();
    // 课件页内容（图片/文本）渲染需要几帧。
    await page.waitForTimeout(2_500);
    await settleFrames(page, 6);
    await captureShot(page, testInfo, '15-stage-reading');

    // 练 · 随堂练习
    await page.locator('[data-testid="stage-toc-practice"]').click();
    await page.waitForTimeout(1_500);
    await settleFrames(page, 6);
    await captureShot(page, testInfo, '16-stage-practice');
  });

  test('17-library-rag 课件检索证据', async ({ page, context, request }, testInfo) => {
    await prepareStudentContext(context, request);
    await page.goto('/library', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('[data-testid="library-split-body"]')).toBeVisible();
    const ragInput = page.locator('[data-testid="library-rag-input"]');
    await expect(ragInput).toBeVisible({ timeout: 30_000 });
    await ragInput.fill('input函数');
    const submit = page.locator('[data-testid="library-rag-submit"]');
    await submit.click();
    await expect(submit, '课件检索未完成').toBeEnabled({ timeout: 120_000 });
    const answer = page.locator('[data-testid="library-rag-answer"]');
    const results = page.locator('[data-testid="library-rag-results"]');
    await expect
      .poll(
        async () => (await answer.isVisible().catch(() => false))
          || (await results.isVisible().catch(() => false)),
        { message: '检索结果未渲染', timeout: 60_000 },
      )
      .toBe(true);
    const evidenceArea = await answer.isVisible().catch(() => false) ? answer : results;
    await expect(evidenceArea, '检索结果缺少页码证据').toContainText(/第\s*\d+\s*页/, {
      timeout: 60_000,
    });
    await settleFrames(page, 4);
    await captureShot(page, testInfo, '17-library-rag');
  });

  test('18-workshop-agents 处理进度', async ({ page, context, request }, testInfo) => {
    test.setTimeout(300_000);
    await prepareStudentContext(context, request);
    await page.goto('/workshop', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('[data-testid="workshop-page"]')).toBeVisible();
    const composer = page.locator('[data-testid="composer-input"]');
    await expect(composer).toBeVisible({ timeout: 30_000 });
    await composer.fill('帮我规划第 1 章怎么学，安排看视频、读讲义、做练习的完整计划');
    await composer.press('Enter');
    await page.locator('[data-testid="workshop-open-calls"]').click();
    await expect(page.locator('[data-testid="workshop-drawer-calls"]')).toBeVisible({
      timeout: 30_000,
    });
    const process = page.locator('[data-testid="workshop-process"]');
    await expect(process, '处理进度未显示').toBeVisible({ timeout: 120_000 });
    await expect(process.locator('[data-testid="tool-call-step"]').first(), '没有可读处理步骤')
      .toBeVisible({ timeout: 120_000 });
    await page.waitForTimeout(4_000);
    await settleFrames(page, 4);
    await captureShot(page, testInfo, '18-workshop-agents');
  });

  test('19/20 判题结果与 Bug 矿洞调试', async ({ page, context, request }, testInfo) => {
    test.setTimeout(300_000);
    await prepareStudentContext(context, request);
    await page.goto('/training?problemId=476', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('[data-testid="training-panel"]')).toBeVisible();
    await expect(page.locator('[data-testid="problem-statement"]')).toBeVisible({
      timeout: 30_000,
    });

    const submitCode = async (code: string): Promise<void> => {
      const editor = page.locator('.cm-content').first();
      await editor.click();
      await page.keyboard.press('Control+a');
      await page.keyboard.press('Delete');
      await page.keyboard.insertText(code);
      // 同账号 10 秒限流：撞 429 时等窗口过去重试（最多 3 次）。
      for (let attempt = 1; attempt <= 3; attempt += 1) {
        const submitted = page.waitForResponse(
          (r) => r.url().includes('/api/submissions') && r.request().method() === 'POST',
          { timeout: 60_000 },
        );
        await page.locator('[data-testid="submit-button"]').click();
        const resp = await submitted;
        if (resp.ok()) return;
        if (resp.status() === 429 && attempt < 3) {
          await page.waitForTimeout(11_000);
          continue;
        }
        throw new Error(`提交接口异常：HTTP ${resp.status()}`);
      }
    };

    // 19：AC 判题结果
    await submitCode('radius = float(input())\nprint(f"{3.1415 * radius * radius:.4f}")');
    const result = page.locator('[data-testid="submission-result"]');
    await expect(result, '判题结果未出现').toBeVisible({ timeout: 90_000 });
    await expect(result, '期望 AC 通过').toContainText('AC', { timeout: 30_000 });
    await settleFrames(page, 4);
    await captureShot(page, testInfo, '19-training-verdict');

    // 20：先 WA 一发让错题入册，再进矿洞拍调试现场
    await page.waitForTimeout(11_000);
    await submitCode('radius = float(input())\nprint("0.0000")');
    await expect(result).toContainText(/WA|FAILED/, { timeout: 90_000 });
    await page.locator('text=进入 Bug 矿洞').first().click();
    await expect(page).toHaveURL(/\/bug-mine/, { timeout: 20_000 });
    await expect(page.locator('[data-testid="bug-mine-panel"]')).toBeVisible({ timeout: 30_000 });
    // 错题本里出现刚才的错题，且右侧已选中进入逐层调试。
    await expect(
      page.locator('[data-testid="bug-mine-panel"]'),
      '矿洞没有收到刚才的错题',
    ).toContainText('圆面积', { timeout: 30_000 });
    await page.waitForTimeout(2_000);
    await settleFrames(page, 4);
    await captureShot(page, testInfo, '20-bugmine-debug');
  });

  test('21-teacher-board 教师班级看板', async ({ page, context, request }, testInfo) => {
    const session = await teacherSession(request);
    await ensureDemoClassroom(request, session);
    await injectSession(context, session);
    await page.goto('/teacher', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('[data-testid="teacher-home"]')).toBeVisible();
    await expect(page.locator('[data-testid="classroom-list"]')).toBeVisible({ timeout: 30_000 });
    await page.locator('text=进入看板').first().click();
    await expect(page.locator('[data-testid="teacher-detail"]'), '班级看板未渲染').toBeVisible({
      timeout: 30_000,
    });
    await settleFrames(page, 6);
    // 遮蔽看板上的加入码与教师昵称。
    await captureShot(page, testInfo, '21-teacher-board', [
      page.locator('.code'),
      page.locator('.teacher-detail header p').first(),
    ]);
  });

  test('23-clients 多端下载与同步说明', async ({ page, context, request }, testInfo) => {
    await prepareStudentContext(context, request);
    await page.goto('/guide?topic=clients', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('[data-testid="guide-content"]')).toContainText('多端下载');
    const downloads = page.locator('[data-testid="guide-downloads"]');
    await downloads.scrollIntoViewIfNeeded();
    await expect(downloads).toBeVisible();
    await expect(downloads).toContainText('Android');
    await expect(downloads).toContainText('Windows');
    await settleFrames(page, 6);
    await captureShot(page, testInfo, '23-clients');
  });

  // ── R-16 统一玩法新增：无尽闯关闭环（25–29） ─────────────────────────
  // 25/26/27/29 走后端 golden 兜底出题。

  test('26-expedition-tower 无尽远征浅色分支地图', async ({ page, context, request }, testInfo) => {
    await prepareStudentContext(context, request);
    await page.goto('/expedition', { waitUntil: 'domcontentloaded' });
    await expect(page).toHaveURL(/\/expedition$/);
    await expect(page.locator('[data-testid="expedition-page"]')).toBeVisible();
    await page.locator('[data-testid="expedition-begin"]').click();
    await expect(page.locator('[data-testid="expedition-map"]')).toBeVisible({ timeout: 60_000 });
    await expect(page.locator('[data-testid="expedition-tower"]')).toBeVisible();
    await expect(page.locator('[data-testid="expedition-canvas"] canvas')).toBeVisible({ timeout: 30_000 });
    await settleFrames(page, 4);
    await captureShot(page, testInfo, '26-arena-tower');
  });

  test('27-expedition-node 无尽远征节点挑战详情', async ({ page, context, request }, testInfo) => {
    await prepareStudentContext(context, request);
    await page.goto('/expedition', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('[data-testid="expedition-page"]')).toBeVisible();
    await page.locator('[data-testid="expedition-begin"]').click();
    await expect(page.locator('[data-testid="expedition-map"]')).toBeVisible({ timeout: 60_000 });
    await page.locator('[data-testid="expedition-view-list"]').click();
    await page.locator('button[data-testid^="expedition-node-"]:not([disabled])').first().click();
    await expect(page.locator('[data-testid="expedition-challenge"]')).toBeVisible({ timeout: 90_000 });
    await expect(page.locator('[data-testid="challenge-workspace"]')).toBeVisible({ timeout: 90_000 });
    await settleFrames(page, 4);
    await captureShot(page, testInfo, '27-loop-mastery');
  });

  test('29-endless-weak-kc 无尽闯关·锁定章节出题', async ({ page, context, request }, testInfo) => {
    test.setTimeout(120_000);
    await prepareStudentContext(context, request);
    await page.goto('/endless?chapter=1', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('[data-testid="endless-page"]')).toBeVisible();
    await page.locator('[data-testid="endless-start"]').click();
    // golden 兜底出题即可（不依赖 graph）；题面 + 统一编辑器 + 连胜/难度状态条出现即可拍。
    await expect(page.locator('[data-testid="endless-body"]')).toBeVisible({ timeout: 60_000 });
    await expect(page.locator('[data-testid="endless-focus-chapter"]')).toBeVisible();
    await expect(page.locator('.cm-content').first()).toBeVisible();
    await settleFrames(page, 4);
    await captureShot(page, testInfo, '29-endless-weak-kc');
  });

  test('25-endless-result 无尽闯关·统一判题结果', async ({ page, context, request }, testInfo) => {
    test.setTimeout(150_000);
    await prepareStudentContext(context, request);
    await page.goto('/endless?chapter=1', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('[data-testid="endless-page"]')).toBeVisible();
    await page.locator('[data-testid="endless-start"]').click();
    await expect(page.locator('[data-testid="endless-body"]')).toBeVisible({ timeout: 60_000 });
    const editor = page.locator('.cm-content').first();
    await expect(editor).toBeVisible();
    await editor.click();
    await page.keyboard.press('Control+a');
    await page.keyboard.press('Delete');
    await page.keyboard.insertText('print(1)\n');
    await page.locator('[data-testid="endless-submit"]').click();
    // 通过/未通过都展示统一 JudgeResultPanel（这正是要展示的统一结果面板）。
    await expect(
      page.locator('[data-testid="endless-result"]'),
      '无尽闯关判题结果面板未出现（后端判题可能不可用）',
    ).toBeVisible({ timeout: 90_000 });
    await settleFrames(page, 4);
    await captureShot(page, testInfo, '25-endless-result');
  });
});
