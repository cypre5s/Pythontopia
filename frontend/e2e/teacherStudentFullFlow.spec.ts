/**
 * 教师 / 学生班级闭环浏览器验收。
 *
 * 覆盖真实链路：
 *   教师创建班级 → 学生加入 → 教师发布带题目证据要求的任务 →
 *   学生公告板领取 → 训练场提交 AC → 公告板提交完成证据 →
 *   教师看板刷新后看到任务 1/1 完成。
 */

import fs from 'node:fs';
import path from 'node:path';
import { expect, test, type Browser, type Page } from '@playwright/test';
import {
  acquireStudentSession,
  acquireTeacherSession,
  injectSession,
  resolveBaseUrl,
  suppressFirstVisitOverlays,
  type DemoSession,
} from './support/demoSession';

const BASE_URL = resolveBaseUrl();
const TRAINING_PROBLEM_ID = 476;
const CODE_AC = 'radius = float(input())\nprint(f"{3.1415 * radius * radius:.4f}")\n';

const RUN_STAMP = new Date()
  .toISOString()
  .replace(/[:.]/g, '-')
  .replace('T', '_')
  .slice(0, 19);

function qaDir(): string {
  if (process.env.PYT_QA_DIR) return process.env.PYT_QA_DIR;
  const cwd = process.cwd();
  const root = path.basename(cwd) === 'frontend' ? path.dirname(cwd) : cwd;
  return path.join(root, 'qa-screens', `teacher-student-full-flow-${RUN_STAMP}`);
}

async function evidence(page: Page, name: string): Promise<void> {
  fs.mkdirSync(qaDir(), { recursive: true });
  await page.screenshot({ path: path.join(qaDir(), `${name}.png`), fullPage: false });
}

async function setEditorCode(page: Page, code: string): Promise<void> {
  const editor = page.locator('.cm-content').first();
  await expect(editor, '未找到训练场代码编辑器').toBeVisible({ timeout: 30_000 });
  await editor.click();
  await page.keyboard.press(process.platform === 'darwin' ? 'Meta+a' : 'Control+a');
  await page.keyboard.press('Delete');
  await page.keyboard.insertText(code);
}

async function submitTrainingAc(page: Page): Promise<void> {
  await setEditorCode(page, CODE_AC);
  const submitted = page.waitForResponse(
    (r) => r.url().includes('/api/submissions') && r.request().method() === 'POST',
    { timeout: 90_000 },
  );
  await page.getByTestId('submit-button').click();
  const resp = await submitted;
  expect(resp.ok(), `训练场提交接口异常：HTTP ${resp.status()}`).toBe(true);
  const result = page.getByTestId('submission-result');
  await expect(result, '判题结果面板未出现').toBeVisible({ timeout: 90_000 });
  await expect(result, '班级任务证据提交前，训练题应先 AC').toContainText('AC');
  await expect(result).toContainText(/PASSED/);
}

async function newAuthedPage(browser: Browser, session: DemoSession) {
  const context = await browser.newContext();
  await injectSession(context, session);
  await suppressFirstVisitOverlays(context);
  const page = await context.newPage();
  return { context, page };
}

function futureDate(days: number): string {
  return new Date(Date.now() + days * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
}

test('教师创建班级、发布任务，学生完成证据后教师看板同步', async ({
  browser,
  request,
}) => {
  test.setTimeout(420_000);

  const teacherSession = await acquireTeacherSession(request);
  const studentSession = await acquireStudentSession(request);
  const teacher = await newAuthedPage(browser, teacherSession);
  const student = await newAuthedPage(browser, studentSession);

  try {
    const classSuffix = Date.now().toString(36);
    const className = `E2E 深度验收班 ${classSuffix}`;
    const questTitle = `E2E 圆面积证据任务 ${classSuffix}`;

    await teacher.page.goto(`${BASE_URL}/teacher`, { waitUntil: 'domcontentloaded' });
    await expect(teacher.page.getByTestId('teacher-home'), '教师首页未渲染').toBeVisible({
      timeout: 60_000,
    });
    await teacher.page.getByTestId('teacher-new-classroom').click();
    const createForm = teacher.page.getByTestId('teacher-create-form');
    await expect(createForm, '新建班级表单未展开').toBeVisible();
    await createForm.locator('input[placeholder="例如 2026 春 Python 一班"]').fill(className);
    await createForm
      .locator('textarea[placeholder="简要说明用途、面向学员、上课频率"]')
      .fill('浏览器全流程验收自动创建，可删除。');

    const createdResponse = teacher.page.waitForResponse(
      (r) => r.url().includes('/api/classrooms') && r.request().method() === 'POST',
      { timeout: 60_000 },
    );
    await createForm.getByRole('button', { name: '创建班级' }).click();
    const created = await createdResponse;
    expect(created.ok(), `创建班级接口异常：HTTP ${created.status()}`).toBe(true);
    const classroom = (await created.json()) as { classroomId: number; joinCode: string };
    expect(classroom.classroomId, '创建班级响应缺少 classroomId').toBeGreaterThan(0);
    expect(classroom.joinCode, '创建班级响应缺少 joinCode').toBeTruthy();

    const card = teacher.page.locator('.classroom-card', { hasText: className }).first();
    await expect(card, '新建班级未出现在教师班级列表').toBeVisible({ timeout: 30_000 });
    await expect(card).toContainText(classroom.joinCode);
    await evidence(teacher.page, '01-teacher-created-classroom');

    const join = await request.post(`${BASE_URL}/api/classrooms/join`, {
      headers: { Authorization: `Bearer ${studentSession.token}` },
      data: { joinCode: classroom.joinCode },
    });
    expect(join.ok(), `学生加入班级失败：HTTP ${join.status()} ${await join.text()}`).toBe(true);

    await card.getByRole('button', { name: '进入看板' }).click();
    await expect(teacher.page, '进入看板后 URL 不正确').toHaveURL(
      new RegExp(`/teacher/classroom/${classroom.classroomId}(?:[?#]|$)`),
      { timeout: 30_000 },
    );
    await expect(teacher.page.getByTestId('teacher-detail'), '教师班级详情页未渲染').toBeVisible({
      timeout: 60_000,
    });
    await expect(teacher.page.getByTestId('overview-tab'), '概览 tab 未渲染').toBeVisible();
    await expect(teacher.page.getByTestId('teacher-detail')).not.toContainText('看板加载失败');

    await teacher.page.reload({ waitUntil: 'domcontentloaded' });
    await expect(teacher.page.getByTestId('teacher-detail')).toBeVisible({ timeout: 60_000 });
    await teacher.page.getByRole('button', { name: /^成员$/ }).click();
    await expect(teacher.page.getByTestId('members-tab'), '成员 tab 未渲染').toBeVisible();
    await expect(
      teacher.page.getByTestId('members-tab'),
      '学生加入后教师成员表未显示学生',
    ).toContainText(studentSession.user.displayName, { timeout: 30_000 });
    await evidence(teacher.page, '02-teacher-member-visible');

    await teacher.page.getByRole('button', { name: /^任务$/ }).click();
    const questTab = teacher.page.getByTestId('quest-tab');
    await expect(questTab, '任务 tab 未渲染').toBeVisible();
    await questTab.locator('input[placeholder="例如 第 2 章随堂练习"]').fill(questTitle);
    await questTab
      .locator('textarea[placeholder="补充任务说明、提交要求"]')
      .fill('请完成圆面积题，并用一次 AC 提交作为证据。');
    await questTab.locator('input[placeholder="例如 PY43-KC-001 PY43-KC-002"]').fill('PY43-KC-005');
    await questTab.locator('input[type="number"]').fill(String(TRAINING_PROBLEM_ID));
    await questTab.locator('input[type="date"]').fill(futureDate(7));

    const publishResponse = teacher.page.waitForResponse(
      (r) =>
        r.url().includes(`/api/teacher/classrooms/${classroom.classroomId}/quests`)
        && r.request().method() === 'POST',
      { timeout: 60_000 },
    );
    await questTab.getByRole('button', { name: '发布任务' }).click();
    const published = await publishResponse;
    expect(published.ok(), `发布班级任务失败：HTTP ${published.status()}`).toBe(true);
    await expect(questTab, '发布后的任务未进入教师任务列表').toContainText(questTitle, {
      timeout: 30_000,
    });
    await evidence(teacher.page, '03-teacher-published-quest');

    for (const tabName of ['KC 热力图', '高频误区', '资源使用', '直播'] as const) {
      await teacher.page.getByRole('button', { name: tabName }).click();
      await expect(teacher.page.getByTestId('teacher-detail')).not.toContainText('看板加载失败');
    }
    await expect(teacher.page.getByTestId('classroom-live-tab'), '直播 tab 未渲染').toBeVisible();

    await student.page.goto(`${BASE_URL}/board?tab=classroom`, { waitUntil: 'domcontentloaded' });
    await expect(student.page.getByTestId('board-panel'), '学生公告板未渲染').toBeVisible({
      timeout: 60_000,
    });
    const studentQuest = student.page.locator('[data-testid^="classroom-quest-"]', {
      hasText: questTitle,
    }).first();
    await expect(studentQuest, '学生公告板未出现教师发布的班级任务').toBeVisible({
      timeout: 60_000,
    });
    await evidence(student.page, '04-student-board-quest');

    await studentQuest.getByRole('button', { name: /去做题|去练习/ }).click();
    await expect(student.page, '班级任务深链未跳到训练场').toHaveURL(/\/training/, {
      timeout: 30_000,
    });
    await expect(student.page.getByTestId('training-panel'), '训练场未渲染').toBeVisible({
      timeout: 90_000,
    });
    await submitTrainingAc(student.page);
    await evidence(student.page, '05-student-training-ac');

    await student.page.goto(`${BASE_URL}/board?tab=classroom`, { waitUntil: 'domcontentloaded' });
    const questAfterAc = student.page.locator('[data-testid^="classroom-quest-"]', {
      hasText: questTitle,
    }).first();
    await expect(questAfterAc, 'AC 后返回公告板未找到班级任务').toBeVisible({
      timeout: 60_000,
    });
    await questAfterAc.getByRole('button', { name: '选择提交' }).click();
    const picker = questAfterAc.locator('[data-testid^="classroom-quest-picker-"]');
    await expect(picker, '证据选择器未展开').toBeVisible({ timeout: 30_000 });
    await picker.locator('input[type="radio"]').first().check();

    const completeResponse = student.page.waitForResponse(
      (r) => /\/api\/quests\/\d+\/complete/.test(r.url()) && r.request().method() === 'POST',
      { timeout: 60_000 },
    );
    await questAfterAc.getByRole('button', { name: '提交完成' }).click();
    const completed = await completeResponse;
    expect(completed.ok(), `学生提交班级任务完成失败：HTTP ${completed.status()}`).toBe(true);
    await expect(questAfterAc, '学生公告板未更新为已完成').toContainText('已完成', {
      timeout: 30_000,
    });
    await evidence(student.page, '06-student-quest-completed');

    await teacher.page.goto(`${BASE_URL}/teacher/classroom/${classroom.classroomId}`, {
      waitUntil: 'domcontentloaded',
    });
    await expect(teacher.page.getByTestId('teacher-detail')).toBeVisible({ timeout: 60_000 });
    await teacher.page.getByRole('button', { name: /^任务$/ }).click();
    const finalQuestTab = teacher.page.getByTestId('quest-tab');
    await expect(finalQuestTab, '教师任务 tab 未渲染').toBeVisible();
    await expect(finalQuestTab, '教师任务列表未同步学生完成率 1/1').toContainText(/1\/1|100%/, {
      timeout: 60_000,
    });
    await evidence(teacher.page, '07-teacher-completion-synced');
  } finally {
    await student.context.close();
    await teacher.context.close();
  }
});
