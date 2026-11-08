import { test, expect, type Locator, type Page } from "@playwright/test";
import { randomUUID } from "node:crypto";

const sections = [
  "班级",
  "学习记录",
  "章节管理",
  "课程与知识点",
  "课程材料",
  "题库",
  "活动与 NPC",
  "任务配置",
];

async function openSection(page: Page, name: string) {
  const navigation = page.locator("#teacher-navigation");
  const trigger = page.getByRole("button", {
    name: "切换管理导航",
    exact: true,
  });
  if (!(await navigation.isVisible())) await trigger.click();
  await navigation.getByRole("button", { name, exact: true }).click();
  await expect(page.locator(".teacher-heading h1")).toHaveText(name);
  await expect(
    page.locator(".teacher-heading .eyebrow, .teacher-heading .page-description"),
  ).toHaveCount(0);
  if (await trigger.isVisible())
    await expect(trigger).toHaveAttribute("aria-expanded", "false");
}

async function entryStyles(page: Page) {
  await expect(page.locator(".arrival-panel .primary")).toBeEnabled();
  await page.mouse.move(0, 0);
  await page.locator(".arrival-brand").click();
  await page.evaluate(() => document.fonts.ready.then(() => undefined));
  return page.evaluate(() => {
    const properties = [
      "background-color",
      "color",
      "font-family",
      "font-size",
      "font-weight",
      "line-height",
      "border-top-width",
      "border-top-color",
      "border-radius",
      "padding-top",
      "padding-left",
    ];
    return Object.fromEntries(
      [".arrival-panel .primary", ".arrival-panel input"].map((selector) => {
        const node = document.querySelector(selector);
        if (!node) throw new Error(`Missing entry control: ${selector}`);
        const style = getComputedStyle(node);
        return [
          selector,
          Object.fromEntries(
            properties.map((property) => [
              property,
              style.getPropertyValue(property),
            ]),
          ),
        ];
      }),
    );
  });
}

async function expectFocusTrapped(page: Page, dialog: Locator) {
  const input = dialog.getByRole("textbox", { name: "班级名称", exact: true });
  await input.focus();
  for (const key of ["Tab", "Shift+Tab"]) {
    for (let step = 0; step < 8; step++) {
      await page.keyboard.press(key);
      await expect
        .poll(() =>
          dialog.evaluate((root) => root.contains(document.activeElement)),
        )
        .toBe(true);
    }
  }
}

test("teacher management layout, forms, real records, responsive navigation and isolated theme", async ({
  page,
  request,
}, testInfo) => {
  test.setTimeout(180_000);
  const student_id = process.env.E2E_TEACHER_STUDENT_ID;
  const password = process.env.E2E_TEACHER_PASSWORD;
  test.skip(
    !student_id || !password,
    "Requires an explicitly provisioned isolated Reborn teacher account.",
  );
  const errors: string[] = [];
  const assistantWrites: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("request", (req) => {
    if (req.url().includes("/api/assistant/") && req.method() !== "GET")
      assistantWrites.push(req.method());
  });
  await page.setViewportSize({ width: 1920, height: 1080 });
  await page.goto("/");
  const originalEntryStyles = await entryStyles(page);
  await page.getByLabel("学号", { exact: true }).fill(student_id!);
  await page.getByLabel("密码", { exact: true }).fill(password!);
  const loggedIn = page.waitForResponse(
    (r) =>
      r.url().endsWith("/api/auth/login") && r.request().method() === "POST",
  );
  await page.getByRole("button", { name: "进入小镇", exact: true }).click();
  const login = await loggedIn;
  expect(login.ok()).toBeTruthy();
  const identity = (await login.json()) as {
    access_token: string;
    user: { role: string };
  };
  const teacher = { Authorization: `Bearer ${identity.access_token}` };
  await expect(page.locator(".teacher-heading h1")).toHaveText("班级");
  const initialAssistant = await request.get("/api/assistant/status", {
    headers: teacher,
  });
  expect(initialAssistant.ok()).toBeTruthy();
  const initialCalls = (
    (await initialAssistant.json()) as { model_calls: number }
  ).model_calls;

  // The create dialog keeps its draft on Escape, traps keyboard focus, and submits once.
  const tag = randomUUID().slice(0, 8);
  const classroomName = `后台界面验收 ${tag}`;
  const createButton = page
    .locator(".teacher-heading")
    .getByRole("button", { name: "创建班级", exact: true });
  await createButton.click();
  const createDialog = page.getByRole("dialog", {
    name: "创建班级",
    exact: true,
  });
  await expect(createDialog).toBeVisible();
  await createDialog
    .getByLabel("班级名称", { exact: true })
    .fill(classroomName);
  await expectFocusTrapped(page, createDialog);
  await page.keyboard.press("Escape");
  await expect(createDialog).toBeHidden();
  await createButton.click();
  await expect(
    createDialog.getByLabel("班级名称", { exact: true }),
  ).toHaveValue(classroomName);
  const created = page.waitForResponse(
    (r) =>
      r.url().endsWith("/api/classrooms") && r.request().method() === "POST",
  );
  await createDialog
    .getByRole("button", { name: "创建班级", exact: true })
    .click();
  const classResponse = await created;
  expect(classResponse.status()).toBe(201);
  const classroom = (await classResponse.json()) as {
    id: number;
    join_code: string;
  };
  await expect(createDialog).toBeHidden();
  const classSearch = page.getByRole("searchbox", {
    name: "搜索班级",
    exact: true,
  });
  await classSearch.fill(classroomName);
  await expect(page.locator(".class-card")).toHaveCount(1);
  await expect(page.locator(".class-card h3")).toHaveText(classroomName);
  await classSearch.fill(`missing-${tag}`);
  await expect(
    page.getByRole("heading", { name: "没有匹配的班级", exact: true }),
  ).toBeVisible();
  await classSearch.fill("");

  await openSection(page, "章节管理");
  await page
    .getByRole("combobox", { name: "当前班级", exact: true })
    .selectOption(String(classroom.id));
  const firstChapter = page.locator(".chapter-schedule-table tbody tr").first();
  await expect(firstChapter).toContainText("第一章");
  await expect(page.getByText("发布任务版本", { exact: true })).toHaveCount(0);
  await firstChapter
    .locator('input[type="datetime-local"]')
    .fill("2099-01-02T09:00");
  const scheduled = page.waitForResponse(
    (r) => r.url().endsWith("/releases/179") && r.request().method() === "PUT",
  );
  await firstChapter.getByRole("button", { name: "保存", exact: true }).click();
  expect((await scheduled).ok()).toBeTruthy();
  await expect(firstChapter).toContainText("待解锁");
  await page.getByRole("button", { name: "刷新", exact: true }).click();
  await expect(firstChapter.locator("input")).toHaveValue("2099-01-02T09:00");
  const released = page.waitForResponse(
    (r) => r.url().endsWith("/releases/179") && r.request().method() === "PUT",
  );
  await firstChapter
    .getByRole("button", { name: "立即解锁", exact: true })
    .click();
  expect((await released).ok()).toBeTruthy();
  await expect(firstChapter).toContainText("已解锁");

  // A genuine registered student supplies the membership record shown by the admin UI.
  const studentName = `后台验收学生 ${tag}`;
  const registered = await request.post("/api/auth/register", {
    data: {
      student_id: BigInt('0x' + randomUUID().replaceAll('-', '').slice(0, 24)).toString(),
      password: `Admin-ui-${randomUUID()}`,
      name: studentName,
    },
  });
  expect(registered.status()).toBe(201);
  const studentIdentity = (await registered.json()) as {
    access_token: string;
    user: { id: number };
  };
  const student = { Authorization: `Bearer ${studentIdentity.access_token}` };
  const joined = await request.post("/api/classrooms/join", {
    headers: student,
    data: { join_code: classroom.join_code },
  });
  expect(joined.ok()).toBeTruthy();
  expect(
    (
      await request.get(`/api/teacher/classrooms/${classroom.id}/progress`, {
        headers: student,
      })
    ).status(),
  ).toBe(403);
  await openSection(page, "学习记录");
  await page.getByRole("button", { name: "刷新记录", exact: true }).click();
  const memberSearch = page.getByRole("searchbox", {
    name: "搜索学生姓名或学号",
    exact: true,
  });
  await memberSearch.fill(studentName);
  const member = page.getByRole("row").filter({ hasText: studentName });
  await expect(member).toBeVisible();
  await expect(member.locator("td").nth(1)).toHaveText("0");
  await expect(member.locator("td").nth(2)).toHaveText("0");
  await expect(
    page.getByRole("columnheader", { name: "已完成章节", exact: true }),
  ).toBeVisible();
  await expect(page.locator(".stats-strip")).toHaveCount(0);
  await expect(
    page.getByRole("heading", { name: "提交状态分布", exact: true }),
  ).toHaveCount(0);
  await expect(page.locator(".member-total")).toHaveText("共 1 人");
  const memberPanel = page.locator(".member-panel");
  await expect(
    memberPanel.locator("h2, .panel-description, .table-toolbar"),
  ).toHaveCount(0);
  await expect(page.locator(".member-toolbar")).toBeVisible();
  expect(
    await memberPanel.evaluate((panel) =>
      panel.previousElementSibling?.classList.contains("member-toolbar"),
    ),
  ).toBe(true);
  await page.setViewportSize({ width: 1920, height: 1080 });
  await expect(page.locator(".toast-layer p")).toHaveCount(0, {
    timeout: 8_000,
  });
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: testInfo.outputPath("teacher-records-flat-1920x1080.png"),
  });
  await openSection(page, "班级");
  await classSearch.fill(classroomName);
  await expect(page.locator(".class-card")).toHaveCount(1);
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: testInfo.outputPath("teacher-classes-flat-1920x1080.png"),
    mask: [page.locator(".join-code code")],
  });
  await classSearch.fill("");
  await openSection(page, "学习记录");
  await page.setViewportSize({ width: 1920, height: 1080 });
  await member.getByRole("button", { name: "查看记录", exact: true }).click();
  const recordsDialog = page.getByRole("dialog", {
    name: `${studentName}的学习记录`,
    exact: true,
  });
  await expect(recordsDialog).toBeVisible();
  await expect(recordsDialog).toContainText("暂无正式提交");
  await page.keyboard.press("Escape");
  await expect(recordsDialog).toBeHidden();
  await memberSearch.fill(`missing-${tag}`);
  await expect(
    page.getByRole("heading", { name: "没有匹配的学生", exact: true }),
  ).toBeVisible();
  await memberSearch.fill(studentName);
  await member.getByRole("button", { name: "移出班级", exact: true }).click();
  const removeDialog = page.getByRole("dialog", {
    name: "移出班级",
    exact: true,
  });
  await expect(removeDialog).toContainText(studentName);
  await removeDialog.getByRole("button", { name: "取消", exact: true }).click();
  await expect(removeDialog).toBeHidden();
  await expect(member).toBeVisible();
  const retained = await request.get(
    `/api/teacher/classrooms/${classroom.id}/progress`,
    { headers: teacher },
  );
  expect(
    ((await retained.json()) as { members: { id: number }[] }).members.some(
      (m) => m.id === studentIdentity.user.id,
    ),
  ).toBe(true);
  await member.getByRole("button", { name: "移出班级", exact: true }).click();
  const removed = page.waitForResponse(
    (r) =>
      r.url().endsWith(`/members/${studentIdentity.user.id}`) &&
      r.request().method() === "DELETE",
  );
  await removeDialog
    .getByRole("button", { name: "确认移出", exact: true })
    .click();
  expect((await removed).ok()).toBeTruthy();
  await expect(removeDialog).toBeHidden();
  await expect(member).toHaveCount(0);
  const absent = await request.get(
    `/api/teacher/classrooms/${classroom.id}/progress`,
    { headers: teacher },
  );
  expect(
    ((await absent.json()) as { members: { id: number }[] }).members.some(
      (m) => m.id === studentIdentity.user.id,
    ),
  ).toBe(false);
  await memberSearch.fill("");

  // Pagination owns its fixtures rather than depending on materials left by earlier test runs.
  for (let index = 0; index < 11; index++) {
    const uploaded = await request.post("/api/teacher/materials/upload", {
      headers: teacher,
      multipart: {
        chapter_id: "179",
        title: `分页验收资料 ${tag}-${index}`,
        file: {
          name: `pagination-${index}.txt`,
          mimeType: "text/plain",
          buffer: Buffer.from("Self-authored pagination test fixture, not course content."),
        },
      },
    });
    expect(uploaded.status()).toBe(201);
  }

  // Search and filter the actual shared curriculum and resource lists.
  await openSection(page, "课程与知识点");
  await expect(
    page.locator(".curriculum-workspace tbody tr").first(),
  ).toBeVisible();
  const sharedRow = page
    .locator(".curriculum-workspace tbody tr")
    .filter({ hasText: "共享课程" })
    .first();
  await expect(sharedRow).toBeVisible();
  if (identity.user.role === "teacher") {
    await expect(sharedRow).toContainText("只读");
    await expect(sharedRow.getByRole("button")).toHaveCount(0);
  }
  const chapterSearch = page.getByRole("searchbox", {
    name: "搜索章节",
    exact: true,
  });
  await chapterSearch.fill(`missing-${tag}`);
  await expect(
    page.getByText("没有匹配的章节，请调整搜索关键词。", { exact: true }),
  ).toBeVisible();
  await chapterSearch.fill("");
  await page.getByRole("tab", { name: /^知识点/ }).click();
  const knowledgePanel = page.locator("#curriculum-knowledge-panel");
  await expect(knowledgePanel).toBeVisible();
  await expect(knowledgePanel.locator("tbody tr")).toHaveCount(10);
  const firstPoint = await knowledgePanel
    .locator("tbody tr")
    .first()
    .textContent();
  await knowledgePanel
    .getByRole("button", { name: "下一页", exact: true })
    .click();
  await expect(knowledgePanel.locator("tbody tr").first()).not.toHaveText(
    firstPoint!,
  );
  await page
    .getByRole("combobox", { name: "按章节查看知识点", exact: true })
    .selectOption("179");
  const pointSearch = page.getByRole("searchbox", {
    name: "搜索知识点",
    exact: true,
  });
  await pointSearch.fill(`missing-${tag}`);
  await expect(
    page.getByText("没有匹配的知识点，请调整搜索关键词。", { exact: true }),
  ).toBeVisible();
  await pointSearch.fill("");
  await page
    .getByRole("combobox", { name: "按章节查看知识点", exact: true })
    .selectOption("0");

  await openSection(page, "课程材料");
  const materialSearch = page.getByRole("searchbox", {
    name: "搜索材料名称或 ID",
    exact: true,
  });
  await materialSearch.fill(`missing-${tag}`);
  await expect(page.locator(".admin-table-scroll .admin-empty")).toBeVisible();
  await materialSearch.fill("");
  await page
    .getByRole("combobox", { name: "处理状态", exact: true })
    .selectOption("ready");
  const materialRows = page
    .locator("tbody tr")
    .filter({ has: page.locator("td.record-title") });
  for (const row of await materialRows.all())
    await expect(row.locator(".status-badge")).toHaveAttribute(
      "data-status",
      "ready",
    );
  await page
    .getByRole("combobox", { name: "处理状态", exact: true })
    .selectOption("");

  const materialPanel = page.locator(".admin-panel").first();
  await expect(materialPanel.locator("tbody tr")).toHaveCount(10);
  await materialPanel
    .getByRole("button", { name: "下一页", exact: true })
    .click();
  await materialSearch.fill("missing-" + tag);
  await expect(materialPanel.locator(".table-pagination")).toContainText(
    "第 1 / 1 页",
  );
  await materialSearch.fill("");
  await page.getByRole("button", { name: "上传课程材料", exact: true }).click();
  const uploadDialog = page.getByRole("dialog", {
    name: "上传课程材料",
    exact: true,
  });
  await expect(uploadDialog).toBeVisible();
  await uploadDialog
    .getByLabel("资料名称", { exact: true })
    .fill("未上传资料 " + tag);
  await page.keyboard.press("Escape");
  await page.getByRole("button", { name: "上传课程材料", exact: true }).click();
  await expect(
    uploadDialog.getByLabel("资料名称", { exact: true }),
  ).toHaveValue("未上传资料 " + tag);
  await uploadDialog.getByRole("button", { name: "取消", exact: true }).click();

  // Content drafts survive switching between the admin sections and the class list.
  await openSection(page, "活动与 NPC");
  await page.getByRole("button", { name: "新建活动", exact: true }).click();
  const activityForm = page.locator(".admin-editor form");
  const activityDraft = `draft-${tag}`;
  await activityForm.getByLabel("活动 ID", { exact: true }).fill(activityDraft);
  await activityForm
    .getByLabel("标题", { exact: true })
    .fill(`未发布活动 ${tag}`);
  await openSection(page, "班级");
  await openSection(page, "活动与 NPC");
  await expect(activityForm.getByLabel("活动 ID", { exact: true })).toHaveValue(
    activityDraft,
  );
  await expect(activityForm.getByLabel("标题", { exact: true })).toHaveValue(
    `未发布活动 ${tag}`,
  );
  await openSection(page, "题库");
  const questionSearch = page.getByRole("searchbox", {
    name: "搜索题目标题、ID 或活动",
    exact: true,
  });
  await questionSearch.fill(`missing-${tag}`);
  await expect(page.locator(".admin-table-scroll .admin-empty")).toBeVisible();
  await questionSearch.fill("");
  await page
    .getByRole("combobox", { name: "题型", exact: true })
    .selectOption("python");
  const questionRows = page
    .locator("tbody tr")
    .filter({ has: page.locator("td.record-title") });
  for (const row of await questionRows.all())
    await expect(row.locator(".status-badge")).toHaveText("编程题");
  await page
    .getByRole("combobox", { name: "题型", exact: true })
    .selectOption("");
  const questionPanel = page.locator(".admin-panel").first();
  await expect(questionPanel.locator("tbody tr")).toHaveCount(10);
  await questionPanel
    .getByRole("button", { name: "下一页", exact: true })
    .click();
  await questionSearch.fill("missing-" + tag);
  await expect(questionPanel.locator(".table-pagination")).toContainText(
    "第 1 / 1 页",
  );
  await questionSearch.fill("");
  await page.getByRole("button", { name: "新增题目", exact: true }).click();
  await page
    .locator("summary")
    .filter({ hasText: "完整 JSON（导入或高级编辑）" })
    .click();
  const questionDraft = JSON.stringify(
    { id: `unpublished-${tag}`, title: "仅保存界面草稿" },
    null,
    2,
  );
  await page.getByLabel("题目 JSON", { exact: true }).fill(questionDraft);
  await page.keyboard.press("Escape");
  await expect(
    page.getByRole("dialog", { name: "新增题目", exact: true }),
  ).toBeHidden();
  await openSection(page, "班级");
  await openSection(page, "题库");
  await page.getByRole("button", { name: "新增题目", exact: true }).click();
  await page
    .locator("summary")
    .filter({ hasText: "完整 JSON（导入或高级编辑）" })
    .click();
  await expect(page.getByLabel("题目 JSON", { exact: true })).toHaveValue(
    questionDraft,
  );
  await page.keyboard.press("Escape");
  await openSection(page, "任务配置");
  await page
    .getByRole("combobox", { name: "已有任务", exact: true })
    .selectOption({ index: 1 });
  await expect(page.getByLabel("任务 JSON", { exact: true })).not.toHaveValue(
    "",
  );
  await expect(page.locator(".toast-layer p")).toHaveCount(0, {
    timeout: 8_000,
  });

  // Every section fits the document width; wide tables scroll inside their panels.
  for (const viewport of [
    { width: 1920, height: 1080 },
  ]) {
    await page.setViewportSize(viewport);
    for (const name of sections) {
      await openSection(page, name);
      await expect
        .poll(
          () =>
            page.evaluate(
              () => document.documentElement.scrollWidth <= innerWidth + 1,
            ),
          { message: `${name} must fit ${viewport.width}×${viewport.height}` },
        )
        .toBe(true);
    }
    await openSection(page, "题库");
    await page.getByRole("button", { name: "新增题目", exact: true }).click();
    const modal = page.getByRole("dialog", { name: "新增题目", exact: true });
    await expect(modal).toBeVisible();
    const rect = await modal.boundingBox();
    expect(rect!.x).toBeGreaterThanOrEqual(0);
    expect(rect!.y).toBeGreaterThanOrEqual(0);
    expect(rect!.x + rect!.width).toBeLessThanOrEqual(viewport.width + 1);
    expect(rect!.y + rect!.height).toBeLessThanOrEqual(viewport.height + 1);
    await modal
      .getByRole("button", { name: "创建题目", exact: true })
      .scrollIntoViewIfNeeded();
    await expect(
      modal.getByRole("button", { name: "创建题目", exact: true }),
    ).toBeVisible();
    await page.keyboard.press("Escape");
    await openSection(page, "课程与知识点");
    await page.screenshot({
      path: testInfo.outputPath(
        `teacher-curriculum-${viewport.width}x${viewport.height}.png`,
      ),
      fullPage: true,
    });
    if (viewport.width === 1920) {
      await page.evaluate(() => window.scrollTo(0, 0));
      await page.screenshot({
        path: testInfo.outputPath("teacher-curriculum-desktop-viewport.png"),
      });
    }
  }

  const finalAssistant = await request.get("/api/assistant/status", {
    headers: teacher,
  });
  expect(finalAssistant.ok()).toBeTruthy();
  expect(
    ((await finalAssistant.json()) as { model_calls: number }).model_calls,
  ).toBe(initialCalls);
  const studentAssistant = await request.get("/api/assistant/status", {
    headers: student,
  });
  expect(studentAssistant.ok()).toBeTruthy();
  expect(
    ((await studentAssistant.json()) as { model_calls: number }).model_calls,
  ).toBe(0);
  expect(assistantWrites).toEqual([]);
  await page.setViewportSize({ width: 1920, height: 1080 });
  if (!(await page.locator("#teacher-navigation").isVisible()))
    await page
      .getByRole("button", { name: "切换管理导航", exact: true })
      .click();
  await page.getByRole("button", { name: "退出账号", exact: true }).click();
  await expect(page.locator(".arrival-panel")).toBeVisible();
  expect(await entryStyles(page)).toEqual(originalEntryStyles);
  expect(errors).toEqual([]);
});
