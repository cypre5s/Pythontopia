import { test, expect, type APIResponse } from "@playwright/test";
import { randomUUID } from "node:crypto";

async function json<T>(response: APIResponse): Promise<T> {
  expect(response.ok(), await response.text()).toBeTruthy();
  return response.json() as Promise<T>;
}
type Identity = { access_token: string; user: { id: number } };
type Paper = { id: string | null; revision: number; status: string; locked: boolean; completed: boolean; correct_count: number; total: number; score: number | null; max_score: number; answers: { question_id: string; answer: unknown; code: string | null }[]; results: { status: string }[] };

test("practice corrections and a locked whole assessment share the question directory", async ({ page, request }, info) => {
  test.setTimeout(150_000);
  const student_id = process.env.E2E_TEACHER_STUDENT_ID, password = process.env.E2E_TEACHER_PASSWORD;
  test.skip(!student_id || !password, "Requires explicit isolated QA teacher credentials; never uses a demonstration account.");
  const tag = randomUUID().slice(0, 8), practiceId = `practice-paper-${tag}`, examId = `exam-paper-${tag}`, questId = `paper-${tag}`;
  const teacher = await json<Identity>(await request.post("/api/auth/login", { data: { student_id, password } }));
  const headers = { Authorization: `Bearer ${teacher.access_token}` };
  const classroom = await json<{ id: number; join_code: string }>(await request.post("/api/classrooms", { headers, data: { name: `试卷验收 ${tag}` } }));
  await json(await request.put(`/api/teacher/classrooms/${classroom.id}/releases/179`, { headers, data: { status: "published" } }));
  for (const [id, kind] of [[practiceId, "practice"], [examId, "assessment"]]) {
    await json(await request.post("/api/teacher/activities", { headers, data: { id, chapter_id: 179, title: `TEST ${kind} ${tag}`, kind, npc_key: "arena_guard", assistant_allowed: false } }));
    for (const [index, question] of [
      { title: "判断", kind: "boolean", answer: true },
      { title: "填空", kind: "fill", answer: "done" },
      ...(kind === "assessment" ? [{ title: "选择", kind: "single_choice", answer: "乙", options: ["甲", "乙"] }] : []),
    ].entries()) {
      await json(await request.post("/api/teacher/questions", { headers, data: { ...question, id: `${id}-q${index}`, activity_id: id, prompt: "Self-authored acceptance question; not course content." } }));
    }
  }
  const questTitle = `TEST 试卷流程 ${tag}`;
  await json(await request.post("/api/teacher/quests", { headers, data: { definition: { id: questId, title: questTitle, chapter_id: 179, kind: "side", nodes: [
    { id: "practice", type: "COMPLETE_PRACTICE", title: "TEST 逐题练习", activity_id: practiceId, dependencies: [] },
    { id: "assessment", type: "PASS_ASSESSMENT", title: "TEST 整卷测验", activity_id: examId, dependencies: [] },
  ], effects: [] } } }));
  const identity = await json<Identity>(await request.post("/api/auth/register", { data: { name: "试卷验收居民", student_id: BigInt('0x' + randomUUID().replaceAll('-', '').slice(0, 24)).toString(), password: `Resident-${randomUUID()}` } }));
  const student = { Authorization: `Bearer ${identity.access_token}` };
  await json(await request.post("/api/classrooms/join", { headers: student, data: { join_code: classroom.join_code } }));
  const readPaper = () => request.get(`/api/activities/${examId}/assessment?classroom_id=${classroom.id}`, { headers: student }).then(json<Paper>);
  const nodeState = async (nodeId: string) => {
    const world = await json<{ quests: { id: string; nodes: { id: string; status: string }[] }[] }>(await request.get(`/api/world?classroom_id=${classroom.id}`, { headers: student }));
    return world.quests.find(quest => quest.id === questId)!.nodes.find(node => node.id === nodeId)!.status;
  };
  const errors: string[] = [];
  page.on("pageerror", error => errors.push(error.message));
  await page.addInitScript(({ token, userId, classroomId }) => {
    sessionStorage.setItem("pythontopia-reborn-session", token);
    sessionStorage.setItem(`classroom:${userId}`, String(classroomId));
  }, { token: identity.access_token, userId: identity.user.id, classroomId: classroom.id });
  await page.goto("/world");
  await page.getByRole("button", { name: "按任意键继续", exact: true }).click();
  const panel = page.locator(".game-workspace"), directory = panel.getByRole("navigation", { name: "题目目录", exact: true });
  async function open(title: string) {
    await page.locator(".hud-dock").getByRole("button", { name: "任务日志", exact: true }).click();
    await panel.locator(".quest-card").filter({ hasText: questTitle }).click();
    await panel.locator(".journal-entry li").filter({ hasText: title }).getByRole("button", { name: "打开练习", exact: true }).click();
    await panel.getByRole("tab", { name: "题目目录", exact: true }).click();
  }
  await open("TEST 逐题练习");
  await expect(panel.locator(".support-question-actions")).toHaveCount(0);
  const directorySpacing = await panel.locator(".support-questions").evaluate(element => {
    const firstQuestion = element.querySelector(".support-question-list button")!;
    return firstQuestion.getBoundingClientRect().top - element.getBoundingClientRect().top;
  });
  expect(directorySpacing).toBeLessThanOrEqual(24);
  await expect(panel.locator(".question-list,.question-position,.learning-meta,.answer-hint,.draft-note")).toHaveCount(0);
  await expect(panel.getByRole("button", { name: "试卷模式", exact: true })).toHaveCount(0);
  await panel.getByLabel("错误", { exact: true }).check();
  await panel.getByRole("button", { name: "提交答案", exact: true }).click();
  await expect(panel.locator(".submission-result strong")).toHaveText("回答错误");
  await panel.getByLabel("正确", { exact: true }).check();
  await panel.getByRole("button", { name: "提交答案", exact: true }).click();
  await expect(panel.locator(".submission-result strong")).toHaveText("回答正确");
  expect(await panel.locator(".submission-result").evaluate(element => {
    const box = element.getBoundingClientRect(), title = element.querySelector("strong")!.getBoundingClientRect();
    return Math.abs(box.top + box.height / 2 - title.top - title.height / 2);
  })).toBeLessThanOrEqual(2);
  await expect(panel.locator(".submission-result small")).toHaveCount(0);
  for (const state of await panel.locator(".support-question-state").all()) {
    expect(await state.evaluate(element => element.getBoundingClientRect().height <= parseFloat(getComputedStyle(element).fontSize) * 2)).toBe(true);
  }
  await panel.getByRole("button", { name: "下一题", exact: true }).click();
  await panel.getByLabel("填写答案", { exact: true }).fill("done");
  await panel.getByRole("button", { name: "提交答案", exact: true }).click();
  await expect.poll(() => nodeState("practice")).toBe("complete");
  await page.screenshot({ path: info.outputPath("practice-corrected.png"), animations: "disabled" });
  await panel.getByRole("button", { name: "关闭当前工作区", exact: true }).click();
  await open("TEST 整卷测验");
  for (const actions of await panel.locator(".support-question-actions,.assessment-actions").all()) {
    expect(await actions.evaluate(element => parseFloat(getComputedStyle(element).borderBottomWidth))).toBe(0);
  }
  await expect(panel.locator(".game-workspace-title h2")).toHaveText("单元测验");
  await expect(panel.getByRole("button", { name: "提交试卷", exact: true })).toBeDisabled();
  await panel.getByLabel("正确", { exact: true }).check();
  await panel.getByRole("button", { name: "试卷模式", exact: true }).click();
  await expect(panel.locator(".question-paper .practice-question-card")).toHaveCount(3);
  await panel.getByLabel("填写答案", { exact: true }).fill("done");
  await panel.getByLabel("甲", { exact: true }).check();
  await expect.poll(async () => (await readPaper()).answers.length).toBe(3);
  const content = panel.locator(".practice-content"), oldWidth = (await content.boundingBox())!.width;
  await panel.getByRole("button", { name: "折叠侧边栏", exact: true }).click();
  await expect(panel.locator(".learning-support")).toBeHidden();
  expect((await content.boundingBox())!.width).toBeGreaterThan(oldWidth + 100);
  await panel.getByRole("button", { name: "展开侧边栏", exact: true }).click();
  await expect(panel.getByLabel("填写答案", { exact: true })).toHaveValue("done");
  await directory.getByRole("button").nth(2).click();
  await expect(panel.locator(`[data-question-id="${examId}-q2"]`)).toBeInViewport({ ratio: .8 });
  await page.screenshot({ path: info.outputPath("assessment-paper-draft.png"), animations: "disabled" });
  await panel.getByRole("button", { name: "关闭当前工作区", exact: true }).click();
  await open("TEST 整卷测验");
  await panel.getByRole("button", { name: "试卷模式", exact: true }).click();
  await expect(panel.getByLabel("填写答案", { exact: true })).toHaveValue("done");
  const submitted = page.waitForResponse(response => response.url().endsWith(`/activities/${examId}/assessment/submit`) && response.request().method() === "POST");
  await panel.getByRole("button", { name: "提交试卷", exact: true }).click();
  const receipt = await submitted;
  const locked = await receipt.json() as Paper;
  expect(receipt.ok()).toBe(true);
  expect(locked).toMatchObject({ locked: true, completed: true, correct_count: 2, total: 3 });
  await expect(panel.locator(".assessment-score")).toHaveText("66 / 100 分");
  expect(locked.score).toBe(66);
  expect(locked.max_score).toBe(100);
  for (const state of await panel.locator(".support-question-state").all()) {
    expect(await state.evaluate(element => element.getBoundingClientRect().height <= parseFloat(getComputedStyle(element).fontSize) * 2)).toBe(true);
  }
  for (const input of await panel.locator(".practice-question-card input").all()) await expect(input).toBeDisabled();
  await expect(panel.getByRole("button", { name: "提交试卷", exact: true })).toHaveCount(0);
  await expect.poll(() => nodeState("assessment")).toBe("complete");
  expect((await request.post(`/api/activities/${examId}/assessment/submit`, { headers: student, data: { classroom_id: classroom.id, idempotency_key: randomUUID(), answers: locked.answers } })).status()).toBe(409);
  expect((await request.put(`/api/activities/${examId}/assessment`, { headers: student, data: { classroom_id: classroom.id, revision: locked.revision, answers: [] } })).status()).toBe(409);
  expect((await request.post("/api/submissions", { headers: student, data: { classroom_id: classroom.id, activity_id: examId, question_id: `${examId}-q2`, idempotency_key: randomUUID(), mode: "submit", answer: "乙" } })).status()).toBeGreaterThanOrEqual(400);
  expect((await readPaper()).correct_count).toBe(2);
  await page.screenshot({ path: info.outputPath("assessment-locked-result.png"), animations: "disabled" });
  expect(errors).toEqual([]);
});
