import { test, expect, type APIResponse } from "@playwright/test";
import { randomUUID } from "node:crypto";
async function json<T>(response: APIResponse): Promise<T> {
  expect(response.ok(), await response.text()).toBeTruthy();
  return response.json() as Promise<T>;
}
test("independent question bank preserves answers and reuses ordered questions in two activities", async ({
  page,
  request,
}, info) => {
  test.setTimeout(120_000);
  const student_id = process.env.E2E_TEACHER_STUDENT_ID,
    password = process.env.E2E_TEACHER_PASSWORD;
  test.skip(
    !student_id || !password,
    "Requires explicit isolated QA teacher credentials.",
  );
  const tag = randomUUID().slice(0, 8),
    questionId = `library-fill-${tag}`,
    secondId = `library-boolean-${tag}`;
  const identity = await json<{ access_token: string }>(
    await request.post("/api/auth/login", { data: { student_id, password } }),
  );
  const headers = { Authorization: `Bearer ${identity.access_token}` };
  const first = await json<{ id: number; title: string }>(
    await request.post("/api/teacher/chapters", {
      headers,
      data: { title: `题库验收 A ${tag}` },
    }),
  );
  const second = await json<{ id: number; title: string }>(
    await request.post("/api/teacher/chapters", {
      headers,
      data: { title: `题库验收 B ${tag}` },
    }),
  );
  const point = await json<{ id: number }>(
    await request.post("/api/teacher/knowledge-points", {
      headers,
      data: { chapter_id: first.id, title: `变量知识点 ${tag}` },
    }),
  );
  const activities = [`library-practice-${tag}`, `library-assessment-${tag}`];
  for (const [index, id] of activities.entries())
    await json(
      await request.post("/api/teacher/activities", {
        headers,
        data: {
          id,
          title: `选题活动 ${index} ${tag}`,
          chapter_id: first.id,
          kind: index ? "assessment" : "practice",
          npc_key: "mayor_py",
          assistant_allowed: false,
        },
      }),
    );
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.addInitScript(
    (token) => sessionStorage.setItem("pythontopia-reborn-session", token),
    identity.access_token,
  );
  await page.goto("/teacher");
  await page
    .locator("#teacher-navigation")
    .getByRole("button", { name: "题库", exact: true })
    .click();
  await page.getByRole("button", { name: "新增题目", exact: true }).click();
  const editor = page.getByRole("dialog", { name: "新增题目", exact: true });
  await editor.getByLabel(/^题目 ID/).fill(questionId);
  await editor.getByLabel("题目标题", { exact: true }).fill(`同义答案 ${tag}`);
  await editor
    .getByRole("combobox", { name: "题型", exact: true })
    .selectOption("fill");
  await editor.getByLabel("题干", { exact: true }).fill("填写示例名称。");
  await editor.getByLabel(/^可接受答案/).fill("变量\n可变名称");
  await editor.getByRole("button", { name: "保存题目", exact: true }).click();
  await expect(editor).toBeHidden();
  type BankQuestion = {
    id: string;
    status: string;
    chapter_ids: number[];
    answer: unknown;
  };
  const bank = () =>
    request
      .get("/api/teacher/questions", { headers })
      .then(json<BankQuestion[]>);
  expect(
    (await bank()).find((question) => question.id === questionId),
  ).toMatchObject({
    status: "draft",
    chapter_ids: [],
    answer: ["变量", "可变名称"],
  });
  await page
    .getByRole("searchbox", { name: "搜索题目标题或 ID", exact: true })
    .fill(questionId);
  const questionRow = page.getByRole("row").filter({ hasText: questionId });
  await questionRow.getByRole("button", { name: "编辑", exact: true }).click();
  const edit = page.getByRole("dialog", { name: "编辑题目", exact: true });
  await expect(edit.getByLabel(/^可接受答案/)).toHaveValue("变量\n可变名称");
  const chapterTags = edit.getByRole("group", {
    name: "关联章节",
    exact: true,
  });
  await chapterTags.getByLabel(first.title, { exact: true }).check();
  await chapterTags.getByLabel(second.title, { exact: true }).check();
  await edit
    .getByRole("group", { name: "关联知识点", exact: true })
    .getByRole("checkbox", { name: new RegExp(`变量知识点 ${tag}`) })
    .check();
  await edit.getByRole("combobox", { name: /^状态/ }).selectOption("ready");
  await edit
    .getByRole("combobox", { name: "来源类型", exact: true })
    .selectOption("ai_generated");
  await edit
    .getByLabel("来源依据（可选）", { exact: true })
    .fill("验收来源标记，不调用 AI。");
  const updated = page.waitForResponse(
    (response) =>
      response.url().endsWith(`/api/teacher/questions/${questionId}`) &&
      response.request().method() === "PUT",
  );
  await edit.getByRole("button", { name: "保存题目", exact: true }).click();
  expect((await updated).status()).toBe(200);
  await expect(edit).toBeHidden();
  expect(
    (await bank()).find((question) => question.id === questionId),
  ).toMatchObject({
    status: "ready",
    chapter_ids: [first.id, second.id],
    knowledge_point_ids: [point.id],
    answer: ["变量", "可变名称"],
    source_type: "ai_generated",
  });
  await json(
    await request.post("/api/teacher/questions", {
      headers,
      data: {
        id: secondId,
        title: `独立判断 ${tag}`,
        kind: "boolean",
        prompt: "这是独立验收题目。",
        answer: true,
        chapter_ids: [first.id],
        status: "ready",
        source_type: "imported",
      },
    }),
  );
  const refreshed = page.waitForResponse(
    (response) =>
      response.url().endsWith("/api/teacher/questions") &&
      response.request().method() === "GET",
  );
  await page.getByRole("button", { name: "刷新题库", exact: true }).click();
  await refreshed;
  for (const id of activities) {
    await page
      .getByRole("button", { name: "练习 / 测验选题", exact: true })
      .click();
    const assignment = page.getByRole("dialog", {
      name: "练习 / 测验选题",
      exact: true,
    });
    await assignment
      .getByRole("combobox", { name: "练习或测验", exact: true })
      .selectOption(id);
    await assignment.getByLabel("搜索可用题目", { exact: true }).fill(tag);
    await assignment
      .locator(".content-assignment-options label")
      .filter({ hasText: questionId })
      .getByRole("checkbox")
      .check();
    await assignment
      .locator(".content-assignment-options label")
      .filter({ hasText: secondId })
      .getByRole("checkbox")
      .check();
    await assignment
      .getByRole("button", { name: `上移题目 ${secondId}`, exact: true })
      .click();
    await assignment
      .getByRole("button", { name: "保存选题与顺序", exact: true })
      .click();
    await expect(assignment).toBeHidden();
  }
  const current = await json<{
    activities: { id: string; question_ids: string[] }[];
  }>(await request.get("/api/teacher/courses", { headers }));
  for (const id of activities)
    expect(
      current.activities.find((activity) => activity.id === id)?.question_ids,
    ).toEqual([secondId, questionId]);
  await expect(questionRow).toContainText(`选题活动 0 ${tag}`);
  await expect(questionRow).toContainText(`选题活动 1 ${tag}`);
  await questionRow.getByRole("button", { name: "删除", exact: true }).click();
  const removal = page.getByRole("dialog", { name: "删除题目", exact: true });
  await removal.getByRole("button", { name: "确认删除", exact: true }).click();
  await expect(removal.getByRole("alert")).toBeVisible();
  await removal.getByRole("button", { name: "取消", exact: true }).click();
  await page.screenshot({
    path: info.outputPath("teacher-independent-question-bank.png"),
    fullPage: true,
  });
  for (const id of activities)
    await json(
      await request.put(`/api/teacher/activities/${id}/questions`, {
        headers,
        data: { question_ids: [] },
      }),
    );
  await questionRow.getByRole("button", { name: "删除", exact: true }).click();
  await removal.getByRole("button", { name: "确认删除", exact: true }).click();
  await expect(removal).toBeHidden();
  await expect(questionRow).toHaveCount(0);
  expect(errors).toEqual([]);
});
