import { answerStoryQuestions } from "./answerActivity";
import { verifyDailyDialogue, verifyUnlockedDailyChoice } from "./dailyDialogue";
import { test, expect, type Page } from "@playwright/test";
import { randomUUID } from "node:crypto";

test("only configured arrival main quest completes and stays isolated across accounts", async ({
  page,
  request }, testInfo) => {
  test.setTimeout(240_000);
  await page.emulateMedia({ reducedMotion: "reduce" });
  const browserErrors: string[] = [];
  page.on("pageerror", (error) => browserErrors.push(error.message));
  const student_id = process.env.E2E_TEACHER_STUDENT_ID,
    password = process.env.E2E_TEACHER_PASSWORD;
  test.skip(
    !student_id || !password,
    "Explicit isolated teacher credentials required; no default account is created.",
  );
  const teacherLogin = await request.post("/api/auth/login", {
    data: { student_id, password } });
  expect(teacherLogin.ok()).toBeTruthy();
  const teacher = {
    Authorization: `Bearer ${(await teacherLogin.json()).access_token}` };
  const classRes = await request.post("/api/classrooms", {
    headers: teacher,
    data: { name: `验收 ${testInfo.project.name} ${randomUUID().slice(0, 6)}` } });
  expect(classRes.status()).toBe(201);
  const classroom = await classRes.json();
  expect(
    (
      await request.put(
        `/api/teacher/classrooms/${classroom.id}/releases/179`,
        { headers: teacher, data: { status: "published" } },
      )
    ).ok(),
  ).toBeTruthy();
  await page.goto("/");
  await page.getByRole("button", { name: "初次到访？注册居民证" }).click();
  await page.getByLabel("名字", { exact: true }).fill("新居民");
  await page
    .getByLabel("学号", { exact: true })
    .fill(BigInt('0x' + randomUUID().replaceAll('-', '').slice(0, 24)).toString());
  await page.getByLabel("密码", { exact: true }).fill(`Reborn-${randomUUID()}`);
  await page.getByRole("button", { name: "注册并进入", exact: true }).click();
  await page.getByLabel("班级加入码").fill(classroom.join_code);
  await page.getByRole("button", { name: "加入班级", exact: true }).click();
  await dismissArrival(page);
  await expect(page.locator(".world-canvas canvas")).toBeVisible();
  await expect(page.locator(".world-hint")).toContainText("交谈", {
    timeout: 15000 });
  const token = await page.evaluate(() =>
    sessionStorage.getItem("pythontopia-reborn-session"),
  );
  const student = { Authorization: `Bearer ${token}` };
  const world = async () => {
    const r = await request.get(`/api/world?classroom_id=${classroom.id}`, {
      headers: student });
    expect(r.ok()).toBeTruthy();
    return r.json();
  };
  const status = async (q: string, n: string) =>
    (await world()).quests
      .find((v: { id: string }) => v.id === q)
      ?.nodes.find((v: { id: string }) => v.id === n)?.status;
  expect(
    (
      await request.get(
        `/api/materials/187?classroom_id=${classroom.id}`,
        { headers: student },
      )
    ).status(),
  ).toBe(403);
  expect((await world()).quests.filter((quest: { kind: string }) => quest.kind === "main").map((quest: { id: string }) => quest.id)).toEqual(["arrival"]);
  expect((await world()).quests.find((quest: { id: string }) => quest.id === "arrival").title).toBe("初来小镇：第一堂 Python 课");
  await page.screenshot({
    path: testInfo.outputPath("world.png") });
  await page.getByRole("button", { name: "立即前往", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "剧情对白" })).toBeVisible({ timeout: 30_000 });
  await finishTalk(page);
  await expect.poll(() => status("arrival", "welcome")).toBe("complete");
  await page.getByRole("button", { name: "立即前往", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "剧情对白" })).toBeVisible({ timeout: 40_000 });
  await finishTalk(page);
  await expect(page.getByRole("navigation", { name: "本章阅读目标" })).toBeVisible();
  for (const pageNo of [34, 42, 43]) {
    const saved = page.waitForResponse(r => r.url().endsWith("/api/activities/ch1-lesson/progress") && r.request().method() === "POST" && r.request().postDataJSON().page_no === pageNo);
    await page.getByRole("navigation", { name: "本章阅读目标" }).getByRole("button", { name: new RegExp("阅读第" + pageNo + "页$") }).click();
    expect((await saved).ok()).toBeTruthy();
  }
  await page.getByRole("button", { name: "完成阅读", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "剧情对白" })).toBeVisible();
  await finishTalk(page);
  await expect.poll(() => status("arrival", "study")).toBe("complete");
  await page.getByRole("button", { name: "立即前往", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "剧情对白" })).toBeVisible({ timeout: 40_000 });
  await finishTalk(page);
  await answerStoryQuestions(page, [
    { title: "计算思维的核心", answer: "抽象与自动化" },
    { title: "计算的规则", answer: "正确" },
    { title: "问题到计算", answer: "建模" },
  ]);
  await finishTalk(page);
  await expect.poll(() => status("arrival", "practice")).toBe("complete");
  await page.getByRole("button", { name: "立即前往", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "剧情对白" })).toBeVisible({ timeout: 40_000 });
  await finishTalk(page);
  await answerStoryQuestions(page, [
    { title: "计算与规则", answer: "给定输入，按照规则处理并得到输出" },
    { title: "抽象与自动化", answer: "错误" },
    { title: "从问题到方案", answer: "数学模型" },
  ], true);
  const endingLines = await finishTalk(page);
  expect(endingLines.join("\n")).toContain("继续巩固");
  const paper = await (await request.get(`/api/activities/ch1-assessment/assessment?classroom_id=${classroom.id}`, { headers: student })).json();
  expect(paper).toMatchObject({ score: 66, max_score: 100, completed: true });
  await expect.poll(() => status("arrival", "report")).toBe("complete");
  await expect.poll(async () => (await world()).flags).toContain("library_unlocked");
  await page.screenshot({ path: testInfo.outputPath("chapter-one-complete.png") });
  await verifyDailyDialogue(page, request, classroom.id, student, testInfo);
  // Opening another course chapter must not restore a retired built-in quest.
  const releaseOtherChapter = await request.put(
    `/api/teacher/classrooms/${classroom.id}/releases/180`,
    { headers: teacher, data: { status: "published", prerequisite_chapter_id: 179 } },
  );
  expect(releaseOtherChapter.ok()).toBeTruthy();
  await verifyUnlockedDailyChoice(page, testInfo);
  await page.reload({ waitUntil: "domcontentloaded" });
  await dismissArrival(page);
  expect((await world()).quests.filter((quest: { kind: string }) => quest.kind === "main").map((quest: { id: string }) => quest.id)).toEqual(["arrival"]);
  await page.getByRole("button", { name: /任务日志/ }).click();
  expect(await page.getByRole("dialog", { name: "游戏工作区" }).count()).toBe(
    1,
  );
  await page.getByRole("button", { name: "已完成", exact: true }).click();
  await expect(page.locator(".quest-directory .quest-card")).toHaveCount(1);
  await expect(page.locator(".journal-entry")).toHaveCount(1);
  for (const card of await page.locator(".quest-directory .quest-card").all()) {
    const title = await card.locator("strong").innerText();
    await card.click();
    await expect(page.locator(".journal-entry")).toHaveCount(1);
    await expect(page.locator(".journal-entry h3")).toHaveText(title);
    await expect(
      page.locator(".journal-entry .quest-detail-footer"),
    ).toContainText("故事已收录");
    await expect(page.locator(".journal-objectives progress")).toHaveJSProperty("position", 1);
  }
  await page.screenshot({
    path: testInfo.outputPath("completed.png") });
  const progress = await (
    await request.get(`/api/teacher/classrooms/${classroom.id}/progress`, {
      headers: teacher })
  ).json();
  expect(
    progress.events.filter(
      (event: { node_id: string }) => event.node_id === "report",
    ),
  ).toHaveLength(1);
  expect(progress.members).toHaveLength(1);
  expect(progress.members[0].completed_chapters).toBe(1);
  const ai = await (
    await request.get("/api/assistant/status", { headers: student })
  ).json();
  expect(ai.model_calls).toBe(0);
  expect(JSON.stringify(ai)).not.toContain("api_key");
  await page.reload({ waitUntil: "domcontentloaded" });
  await dismissArrival(page);
  await expect(page.locator(".world-hint")).not.toHaveText("", {
    timeout: 15000 });
  expect((await world()).quests.filter((quest: { kind: string }) => quest.kind === "main").map((quest: { id: string }) => quest.id)).toEqual(["arrival"]);
  expect((await world()).flags).toContain("library_unlocked");
  await page.getByRole("button", { name: "打开设置" }).click();
  await page.getByRole("button", { name: "退出账号", exact: true }).click();
  await page.getByRole("button", { name: "初次到访？注册居民证" }).click();
  await page.getByLabel("名字", { exact: true }).fill("另一位新居民");
  await page
    .getByLabel("学号", { exact: true })
    .fill(BigInt('0x' + randomUUID().replaceAll('-', '').slice(0, 24)).toString());
  await page.getByLabel("密码", { exact: true }).fill(`Reborn-${randomUUID()}`);
  await page.getByRole("button", { name: "注册并进入", exact: true }).click();
  await page.getByLabel("班级加入码").fill(classroom.join_code);
  await page.getByRole("button", { name: "加入班级", exact: true }).click();
  await dismissArrival(page);
  await expect(
    page.getByText("前往广场，拜访镇长", { exact: true }),
  ).toBeVisible();
  const nextToken = await page.evaluate(() =>
    sessionStorage.getItem("pythontopia-reborn-session"),
  );
  const nextWorld = await (
    await request.get(`/api/world?classroom_id=${classroom.id}`, {
      headers: { Authorization: `Bearer ${nextToken}` } })
  ).json();
  expect(nextWorld.flags).not.toContain("library_unlocked");
  expect(nextWorld.quests.filter((quest: { kind: string }) => quest.kind === "main").map((quest: { id: string }) => quest.id)).toEqual(["arrival"]);
  expect(
    nextWorld.quests.find((q: { id: string }) => q.id === "arrival").status,
  ).toBe("active");
  expect(browserErrors).toEqual([]);
});

async function dismissArrival(page: Page) {
  const welcome = page.getByRole("dialog", { name: "欢迎回到小镇", exact: true });
  await expect(welcome).toBeVisible();
  await welcome.getByRole("button", { name: "按任意键继续", exact: true }).click();
  await expect(welcome).toBeHidden();
  await expect(page.locator(".world-canvas canvas")).toHaveCount(1);
  await expect(page.locator(".hud-dock")).toBeVisible();
}
async function finishTalk(page: Page) {
  const dialogue = page.getByRole("dialog", { name: "剧情对白" });
  const lines: string[] = [];
  for (let i = 0; i < 10; i++) {
    if (!(await dialogue.isVisible())) return lines;
    const text = dialogue.locator(".dialogue-text");
    const fullText = await text.getAttribute("aria-label");
    if (fullText) await expect(text).toHaveText(fullText);
    lines.push((await text.textContent()) ?? "");
    const responses = dialogue.locator(".dialogue-response");
    const button = (await responses.count()) ? responses.first() : text;
    const responsePromise = page.waitForResponse(
      (r) =>
        /\/story\/sessions\/[^/]+\/advance$/.test(r.url()) &&
        r.request().method() === "POST",
    );
    await button.click();
    const response = await responsePromise;
    expect(response.ok(), await response.text()).toBeTruthy();
    const result = await response.json();
    if (result.status !== "active") {
      // Closing waits for the real event transaction and world refresh, not only the script response.
      await expect(dialogue).toBeHidden({ timeout: 15_000 });
      return lines;
    }
    await page.waitForTimeout(100);
  }
  throw new Error("Dialogue did not terminate within ten bounded nodes");
}
