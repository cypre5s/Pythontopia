import { test, expect } from "@playwright/test";
import { randomUUID } from "node:crypto";

test("teacher curriculum ownership, knowledge binding and isolated code drafts", async ({
  page,
  request,
}, testInfo) => {
  test.setTimeout(240_000);
  const email = process.env.E2E_TEACHER_EMAIL,
    password = process.env.E2E_TEACHER_PASSWORD,
    otherEmail = process.env.E2E_OTHER_TEACHER_EMAIL,
    otherPassword = process.env.E2E_OTHER_TEACHER_PASSWORD;
  test.skip(
    !email || !password || !otherEmail || !otherPassword,
    "Two explicit isolated teacher accounts are required; this test creates no default teacher credentials.",
  );
  const suffix = randomUUID().slice(0, 8),
    chapterTitle = `自建课程 ${suffix}`,
    editedTitle = `自建课程修订 ${suffix}`,
    pointTitle = `知识点 ${suffix}`,
    pointEdited = `知识点修订 ${suffix}`,
    activityId = `authoring-${suffix}`,
    questId = `authoring-quest-${suffix}`,
    questionId = `authoring-python-${suffix}`;
  await page.goto("/");
  await page.getByLabel("邮箱", { exact: true }).fill(email!);
  await page.getByLabel("密码", { exact: true }).fill(password!);
  await page.getByRole("button", { name: "进入小镇", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "课程与知识点", exact: true }),
  ).toBeVisible();
  const teacherToken = await page.evaluate(() =>
      sessionStorage.getItem("pythontopia-reborn-session"),
    ),
    teacher = { Authorization: `Bearer ${teacherToken}` };
  const getCourses = async () => {
    const r = await request.get("/api/teacher/courses", { headers: teacher });
    expect(r.ok()).toBeTruthy();
    return r.json();
  };
  await page.getByRole("button", { name: "课程与知识点", exact: true }).click();
  await page.getByRole("button", { name: "新增章节", exact: true }).click();
  const chapterForm = page.getByRole("dialog", { name: /^(新增|编辑)章节$/ }),
    pointForm = page.getByRole("dialog", { name: /^(新增|编辑)知识点$/ });
  await chapterForm.getByLabel("章节标题").fill(chapterTitle);
  await chapterForm
    .getByRole("button", { name: "保存章节", exact: true })
    .click();
  await expect(chapterForm).toBeHidden();
  await page.getByRole("searchbox", { name: "搜索章节", exact: true }).fill(chapterTitle);
  await expect(
    page.getByRole("row").filter({ hasText: chapterTitle }),
  ).toBeVisible();
  let courses = await getCourses();
  const chapter = courses.chapters.find(
    (c: { title: string }) => c.title === chapterTitle,
  );
  expect(chapter).toBeTruthy();
  await page
    .getByRole("row")
    .filter({ hasText: chapterTitle })
    .getByRole("button", { name: "编辑章节", exact: true })
    .click();
  await chapterForm.getByLabel("章节标题").fill(editedTitle);
  await chapterForm
    .getByRole("button", { name: "保存章节", exact: true })
    .click();
  await expect(chapterForm).toBeHidden();
  await page.getByRole("searchbox", { name: "搜索章节", exact: true }).fill(editedTitle);
  await expect(
    page.getByRole("row").filter({ hasText: editedTitle }),
  ).toBeVisible();
  await page.getByRole("tab", { name: /^知识点/ }).click();
  await page.getByRole("button", { name: "新增知识点", exact: true }).click();
  await pointForm
    .getByRole("combobox", { name: "所属章节", exact: true })
    .selectOption(String(chapter.id));
  await pointForm.getByLabel("知识点名称").fill(pointTitle);
  await pointForm
    .getByRole("button", { name: "保存知识点", exact: true })
    .click();
  await expect(pointForm).toBeHidden();
  await page.getByRole("searchbox", { name: "搜索知识点", exact: true }).fill(pointTitle);
  await expect(
    page.getByRole("row").filter({ hasText: pointTitle }),
  ).toBeVisible();
  courses = await getCourses();
  const point = courses.knowledge_points.find(
    (k: { title: string }) => k.title === pointTitle,
  );
  expect(point).toBeTruthy();
  await page
    .getByRole("row")
    .filter({ hasText: pointTitle })
    .getByRole("button", { name: "编辑知识点", exact: true })
    .click();
  await expect(
    pointForm.getByRole("combobox", { name: "所属章节", exact: true }),
  ).toBeDisabled();
  await pointForm.getByLabel("知识点名称").fill(pointEdited);
  await pointForm
    .getByRole("button", { name: "保存知识点", exact: true })
    .click();
  await expect(pointForm).toBeHidden();
  await page.getByRole("searchbox", { name: "搜索知识点", exact: true }).fill(pointEdited);
  await expect(
    page.getByRole("row").filter({ hasText: pointEdited }),
  ).toBeVisible();
  await page.screenshot({
    path: testInfo.outputPath("curriculum-authoring.png"),
  });
  // A second real teacher cannot see, update or delete another teacher's curriculum.
  const otherLogin = await request.post("/api/auth/login", {
    data: { email: otherEmail, password: otherPassword },
  });
  expect(otherLogin.ok()).toBeTruthy();
  const other = {
    Authorization: `Bearer ${(await otherLogin.json()).access_token}`,
  };
  const otherCourses = await (
    await request.get("/api/teacher/courses", { headers: other })
  ).json();
  expect(
    otherCourses.chapters.some((c: { id: number }) => c.id === chapter.id),
  ).toBe(false);
  expect(
    otherCourses.knowledge_points.some(
      (k: { id: number }) => k.id === point.id,
    ),
  ).toBe(false);
  expect(
    (
      await request.put(`/api/teacher/chapters/${chapter.id}`, {
        headers: other,
        data: { title: "Unauthorized change" },
      })
    ).status(),
  ).toBe(403);
  expect(
    (
      await request.put(`/api/teacher/knowledge-points/${point.id}`, {
        headers: other,
        data: { title: "Unauthorized change" },
      })
    ).status(),
  ).toBe(403);
  await page.getByRole("button", { name: "班级", exact: true }).click();
  const className = `课程配置验收 ${suffix}`;
  await page.locator(".teacher-heading").getByRole("button", { name: "创建班级", exact: true }).click();
  const classDialog = page.getByRole("dialog", { name: "创建班级", exact: true });
  await classDialog.getByLabel("班级名称", { exact: true }).fill(className);
  await classDialog.getByRole("button", { name: "创建班级", exact: true }).click();
  await expect(
    page.locator(".class-card").filter({ hasText: className }),
  ).toBeVisible();
  const classroom = (
    await (await request.get("/api/classrooms", { headers: teacher })).json()
  ).find((c: { name: string }) => c.name === className);
  expect(classroom).toBeTruthy();
  await page.getByRole("button", { name: "活动与 NPC", exact: true }).click();
  await page.getByRole("button", { name: "新建活动", exact: true }).click();
  const activityForm = page.locator(".admin-editor form");
  await activityForm.getByLabel("活动 ID", { exact: true }).fill(activityId);
  await activityForm
    .getByLabel("标题", { exact: true })
    .fill(`独立编程练习 ${suffix}`);
  await activityForm
    .getByRole("combobox", { name: "章节", exact: true })
    .selectOption(String(chapter.id));
  await activityForm
    .getByRole("combobox", { name: "类型", exact: true })
    .selectOption("practice");
  await activityForm.getByLabel(pointEdited, { exact: true }).check();
  await activityForm.getByLabel("允许学生在活动内主动使用助教", { exact: true }).uncheck();
  await activityForm
    .getByRole("button", { name: "创建活动", exact: true })
    .click();
  await expect(
    page.getByRole("row").filter({ hasText: activityId }),
  ).toBeVisible();
  courses = await getCourses();
  expect(
    courses.activities.find((a: { id: string }) => a.id === activityId)
      .knowledge_point_ids,
  ).toEqual([point.id]);
  // A public Python exercise fixture enables editor validation without executing code.
  const q = await request.post("/api/teacher/questions", {
    headers: teacher,
    data: {
      id: questionId,
      activity_id: activityId,
      title: "自建编辑器验证题",
      kind: "python",
      prompt: "这是一道教师自建编辑器验证题，本次仅保存草稿，不运行或提交。",
      options: [],
      samples: [{ input: "", output: "hello" }],
      hidden_cases: [{ input: "", expected_output: "hello" }],
      source: { kind: "explicit_test_fixture" },
    },
  });
  expect(q.status()).toBe(201);
  const questionVersion = await q.json();
  expect(questionVersion.activity_version).toBeGreaterThan(1);
  await page.getByRole("button", { name: "任务配置", exact: true }).click();
  const definition = {
    id: questId,
    version: 1,
    title: `课程配置任务 ${suffix}`,
    chapter_id: chapter.id,
    kind: "main",
    nodes: [
      {
        id: "practice",
        type: "COMPLETE_PRACTICE",
        title: "打开自建编程练习",
        activity_id: activityId,
        activity_version: questionVersion.activity_version,
        dependencies: [],
      },
    ],
    effects: [],
  };
  await page
    .getByLabel("任务 JSON", { exact: true })
    .fill(JSON.stringify(definition));
  await page.getByRole("button", { name: "校验任务", exact: true }).click();
  await expect(page.locator("pre.notice")).toBeVisible();
  await page.getByRole("button", { name: "创建任务版本", exact: true }).click();
  await expect(
    page.getByText("新任务版本已创建，请更新班级发布规则。", { exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "章节管理", exact: true }).click();
  await page.getByRole("combobox", { name: "当前班级", exact: true }).selectOption(String(classroom.id));
  const chapterRow = page.locator(".chapter-management tbody tr").filter({ hasText: editedTitle });
  await chapterRow.getByRole("button", { name: "立即解锁", exact: true }).click();
  await expect(chapterRow.locator(".status-badge")).toHaveText("已解锁");
  const registration = await request.post("/api/auth/register", {
    data: {
      email: `${randomUUID()}@example.test`,
      password: `Reborn-${randomUUID()}`,
      name: "草稿验收学生",
    },
  });
  expect(registration.ok()).toBeTruthy();
  const studentToken = (await registration.json()).access_token,
    student = { Authorization: `Bearer ${studentToken}` };
  expect(
    (
      await request.post("/api/classrooms/join", {
        headers: student,
        data: { join_code: classroom.join_code },
      })
    ).ok(),
  ).toBeTruthy();
  await page.evaluate(
    (token) => sessionStorage.setItem("pythontopia-reborn-session", token),
    studentToken,
  );
  await page.goto("/world");
  await expect(page.locator("canvas")).toBeVisible();
  await page.getByRole("button", { name: "按任意键继续", exact: true }).click();
  await expect(page.getByRole("button", { name: /任务日志/ })).toBeVisible();
  const position = async () => {
    const r = await request.get(`/api/world?classroom_id=${classroom.id}`, {
      headers: student,
    });
    expect(r.ok()).toBeTruthy();
    return (await r.json()).player;
  };
  const before = await position();
  const submissionsBefore = (
    await (
      await request.get(`/api/teacher/classrooms/${classroom.id}/progress`, {
        headers: teacher,
      })
    ).json()
  ).submissions.length;
  await page.getByRole("button", { name: /任务日志/ }).click();
  await page.getByRole("button", { name: "打开练习", exact: true }).click();
  await page.locator(".cm-content").click();
  await page.keyboard.type("wasd");
  await expect(page.locator(".cm-content")).toHaveText("wasd");
  await page.waitForTimeout(1800);
  expect(await position()).toEqual(before);
  await page.screenshot({
    path: testInfo.outputPath("code-draft.png"),
  });
  await page.getByRole("button", { name: "关闭当前工作区" }).click();
  await page.getByRole("button", { name: /任务日志/ }).click();
  await page.getByRole("button", { name: "打开练习", exact: true }).click();
  await expect(page.locator(".cm-content")).toHaveText("wasd");
  const after = (
    await (
      await request.get(`/api/teacher/classrooms/${classroom.id}/progress`, {
        headers: teacher,
      })
    ).json()
  ).submissions.length;
  expect(after).toBe(submissionsBefore);
});
