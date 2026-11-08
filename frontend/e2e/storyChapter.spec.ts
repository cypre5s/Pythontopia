import { answerStoryQuestions } from "./answerActivity";
import {
  test,
  expect,
  type APIRequestContext,
  type Page,
  type Response,
} from "@playwright/test";
import { randomUUID } from "node:crypto";

interface StoryWorld {
  player: { map_key: string; tile_x: number; tile_y: number };
  flags: string[];
  quests: {
    id: string;
    status: string;
    story_id?: string;
    nodes: { id: string; status: string }[];
  }[];
}

interface StoryView {
  id: string;
  revision: number;
  status: "active" | "waiting" | "ended";
  node: { id: string; text: string } | null;
  activity: { id: string; kind: string; } | null;
}

interface ActivityView {
  id: string;
  completion?: { required_page_ids: number[] };
  material: {
    pages: { id: number; page_no: number }[];
  } | null;
  progress?: { page_no: number | null; completed: boolean };
}

const dialogue = (page: Page) =>
  page.getByRole("dialog", { name: "剧情对白", exact: true });
const workspace = (page: Page) =>
  page.getByRole("dialog", { name: "游戏工作区", exact: true });

async function successfulJson<T>(response: Response): Promise<T> {
  expect(response.ok(), await response.text()).toBeTruthy();
  return (await response.json()) as T;
}

async function dismissArrival(page: Page) {
  const welcome = page.getByRole("dialog", {
    name: "欢迎回到小镇",
    exact: true,
  });
  await expect(welcome).toBeVisible();
  await welcome.getByRole("button", { name: "按任意键继续" }).click();
  await expect(welcome).toBeHidden();
  await expect(page.locator(".world-canvas canvas")).toHaveCount(1);
}

async function registerStudent(page: Page, joinCode: string, name: string) {
  await page.getByRole("button", { name: "初次到访？注册居民证" }).click();
  await page.getByLabel("名字", { exact: true }).fill(name);
  await page
    .getByLabel("学号", { exact: true })
    .fill(BigInt('0x' + randomUUID().replaceAll('-', '').slice(0, 24)).toString());
  await page.getByLabel("密码", { exact: true }).fill(`Story-${randomUUID()}`);
  await page.getByRole("button", { name: "注册并进入", exact: true }).click();
  await page.getByLabel("班级加入码").fill(joinCode);
  await page.getByRole("button", { name: "加入班级", exact: true }).click();
  await dismissArrival(page);
  const token = await page.evaluate(() =>
    sessionStorage.getItem("pythontopia-reborn-session"),
  );
  expect(token).toBeTruthy();
  return { Authorization: `Bearer ${token}` };
}

async function advanceDialogue(page: Page, choice?: string) {
  const current = dialogue(page);
  await expect(current).toBeVisible();
  const response = page.waitForResponse(
    (candidate) =>
      /\/api\/story\/sessions\/[^/]+\/advance$/.test(candidate.url()) &&
      candidate.request().method() === "POST",
  );
  if (choice) {
    await current
      .locator(".dialogue-response")
      .filter({ hasText: choice })
      .click();
  } else {
    await expect(current.locator(".dialogue-response")).toHaveCount(0);
    await current.locator(".dialogue-text").click();
  }
  return successfulJson<StoryView>(await response);
}

/** Every advance goes through the visible dialogue and its real server request. */
async function finishDialogue(page: Page, choice?: string) {
  const lines: string[] = [];
  for (let step = 0; step < 16; step++) {
    const current = dialogue(page);
    await expect(current).toBeVisible();
    lines.push((await current.locator(".dialogue-text").textContent()) ?? "");
    const hasChoices = (await current.locator(".dialogue-response").count()) > 0;
    if (hasChoices && !choice)
      throw new Error("A narrative choice requires an explicit test selection");
    const view = await advanceDialogue(page, hasChoices ? choice : undefined);
    if (view.status !== "active") {
      await expect(current).toBeHidden();
      return { view, lines };
    }
    if (view.node) {
      await expect(current.locator(".dialogue-text")).toHaveAttribute(
        "aria-label",
        view.node.text,
      );
      await expect(current.locator(".dialogue-text")).toHaveText(view.node.text);
    }
  }
  throw new Error("The bounded scene did not finish within sixteen nodes");
}

async function approachObjective(page: Page) {
  const response = page.waitForResponse(
    (candidate) =>
      candidate.url().endsWith("/api/story/interactions") &&
      candidate.request().method() === "POST",
    { timeout: 60_000 },
  );
  await page.getByRole("button", { name: "立即前往", exact: true }).click();
  const view = await successfulJson<StoryView>(await response);
  await expect(dialogue(page)).toBeVisible();
  return view;
}

async function interactAgain(page: Page, touch: boolean) {
  const response = page.waitForResponse(
    (candidate) =>
      candidate.url().endsWith("/api/story/interactions") &&
      candidate.request().method() === "POST",
  );
  if (touch)
    await page.getByRole("button", { name: "互动", exact: true }).tap();
  else await page.keyboard.press("e");
  const view = await successfulJson<StoryView>(await response);
  await expect(dialogue(page)).toBeVisible();
  return view;
}

async function nextReadingPage(page: Page) {
  const nextPage = Number(
    await page.locator(".reader-position strong").textContent(),
  ) + 1;
  const response = page.waitForResponse(
    (candidate) =>
      /\/api\/activities\/ch1-lesson\/progress$/.test(candidate.url()) &&
      candidate.request().method() === "POST" &&
      candidate.request().postDataJSON().page_no === nextPage,
  );
  await page.getByRole("button", { name: "下一页" }).click();
  const saved = await successfulJson<{
    page_no: number;
    completed: boolean;
  }>(await response);
  await expect(page.locator(".reader-position strong")).toHaveText(
    String(saved.page_no),
  );
  return saved;
}

async function readRequiredPage(page: Page, pageNo: number) {
  const response = page.waitForResponse(
    (candidate) =>
      /\/api\/activities\/ch1-lesson\/progress$/.test(candidate.url()) &&
      candidate.request().method() === "POST" &&
      candidate.request().postDataJSON().page_no === pageNo,
  );
  await page
    .getByRole("navigation", { name: "本章阅读目标", exact: true })
    .getByRole("button", { name: new RegExp(`阅读第${pageNo}页$`) })
    .click();
  const saved = await successfulJson<{ page_no: number; completed: boolean }>(
    await response,
  );
  await expect(page.locator(".reader-position strong")).toHaveText(
    String(pageNo),
  );
  return saved;
}

async function closeActivity(page: Page) {
  const response = page.waitForResponse(
    (candidate) =>
      /\/api\/story\/sessions\/[^/]+\/return$/.test(candidate.url()) &&
      candidate.request().method() === "POST",
  );
  await page.getByRole("button", { name: "关闭当前工作区" }).click();
  const view = await successfulJson<StoryView>(await response);
  await expect(workspace(page)).toBeHidden();
  await expect(dialogue(page)).toBeVisible();
  return view;
}

async function readJson<T>(
  request: APIRequestContext,
  path: string,
  headers: Record<string, string>,
): Promise<T> {
  const response = await request.get(path, { headers });
  expect(response.ok(), await response.text()).toBeTruthy();
  return (await response.json()) as T;
}

for (const viewport of [
  { name: "desktop", width: 1920, height: 1080, touch: false },
]) {
  test.describe(`chapter story ${viewport.name}`, () => {
    test.use({
      viewport: { width: viewport.width, height: viewport.height },
      hasTouch: viewport.touch,
    });

    test("current chapter, contextual NPCs, activity resume and account isolation", async ({
      page,
      request,
    }, testInfo) => {
      test.setTimeout(360_000);
      const student_id = process.env.E2E_TEACHER_STUDENT_ID;
      const password = process.env.E2E_TEACHER_PASSWORD;
      test.skip(
        !student_id || !password,
        "Explicit isolated teacher credentials are required; no default account or business API mock is used.",
      );
      await page.emulateMedia({ reducedMotion: "reduce" });
      const browserErrors: string[] = [];
      const assistantMessages: string[] = [];
      page.on("pageerror", (error) => browserErrors.push(error.message));
      page.on("request", (outgoing) => {
        if (
          outgoing.method() === "POST" &&
          /\/api\/assistant\/sessions\/[^/]+\/messages$/.test(outgoing.url())
        )
          assistantMessages.push(outgoing.url());
      });

      const login = await request.post("/api/auth/login", {
        data: { student_id, password },
      });
      expect(login.ok()).toBeTruthy();
      const teacher = {
        Authorization: `Bearer ${(await login.json()).access_token}`,
      };
      const created = await request.post("/api/classrooms", {
        headers: teacher,
        data: { name: `剧情验收 ${viewport.name} ${randomUUID().slice(0, 8)}` },
      });
      expect(created.status()).toBe(201);
      const classroom = (await created.json()) as {
        id: number;
        join_code: string;
      };
      const release = await request.put(
        `/api/teacher/classrooms/${classroom.id}/releases/179`,
        { headers: teacher, data: { status: "published", } },
      );
      expect(release.ok(), await release.text()).toBeTruthy();

      await page.goto("/");
      const student = await registerStudent(
        page,
        classroom.join_code,
        `剧情新居民-${viewport.name}`,
      );
      const readWorld = () =>
        readJson<StoryWorld>(
          request,
          `/api/world?classroom_id=${classroom.id}`,
          student,
        );
      const quest = async () =>
        (await readWorld()).quests.find((value) => value.id === "arrival")!;
      const nodeStatus = async (nodeId: string) =>
        (await quest()).nodes.find((node) => node.id === nodeId)?.status;
      const readLesson = () =>
        readJson<ActivityView>(
          request,
          `/api/activities/ch1-lesson?classroom_id=${classroom.id}`,
          student,
        );
      const aiBefore = await readJson<{ model_calls: number }>(
        request,
        "/api/assistant/status",
        student,
      );
      expect(await quest()).toMatchObject({

        story_id: "first-steps",
        status: "active",
      });
      expect(await nodeStatus("assessment")).toBe("locked");

      await approachObjective(page);
      await expect(dialogue(page).locator(".dialogue-text")).toContainText(
        "欢迎来到 Pythontopia",
      );
      const introduction = await finishDialogue(page);
      expect(introduction.lines.join("\n")).toContain("图书馆");
      await expect.poll(() => nodeStatus("welcome")).toBe("complete");
      await interactAgain(page, viewport.touch);
      await expect(dialogue(page).locator(".dialogue-text")).toContainText(
        "去找图书管理员吧",
      );
      await expect(dialogue(page).locator(".dialogue-text")).not.toContainText(
        "欢迎来到 Pythontopia",
      );
      await finishDialogue(page);

      await approachObjective(page);
      await expect.poll(async () => (await readWorld()).player.map_key).toBe(
        "library",
      );
      await expect(dialogue(page).locator(".dialogue-text")).toContainText(
        "新来的冒险家",
      );
      const firstReading = await finishDialogue(page, "现在开始阅读");
      expect(firstReading.view).toMatchObject({
        status: "waiting",
        activity: { id: "ch1-lesson", },
      });
      await expect(page.locator(".lesson-workspace")).toBeVisible();
      await expect(dialogue(page)).toHaveCount(0);
      await expect(page.getByRole("button", { name: "完成阅读", exact: true }))
        .toBeDisabled();
      const savedPage = (await nextReadingPage(page)).page_no;
      await closeActivity(page);
      await expect(dialogue(page).locator(".dialogue-text")).toContainText(
        "要去忙别的了",
      );
      await finishDialogue(page);
      expect(await nodeStatus("study")).toBe("ready");
      expect((await readLesson()).progress?.page_no).toBe(savedPage);

      await interactAgain(page, viewport.touch);
      await expect(dialogue(page).locator(".dialogue-text")).toContainText(
        "你回来啦",
      );
      const resumedReading = await finishDialogue(page, "继续阅读");
      await expect(page.locator(".reader-position strong")).toHaveText(
        String(savedPage),
      );
      await page.reload({ waitUntil: "domcontentloaded" });
      await dismissArrival(page);
      await expect(page.locator(".lesson-workspace")).toBeVisible();
      await expect(page.locator(".reader-position strong")).toHaveText(
        String(savedPage),
      );
      const restored = await readJson<StoryView>(
        request,
        `/api/story/current?classroom_id=${classroom.id}`,
        student,
      );
      expect(restored).toMatchObject({
        id: resumedReading.view.id,
        status: "waiting",
      });
      const lesson = await readLesson();
      expect(lesson.material).not.toBeNull();
      expect(lesson.completion?.required_page_ids).toEqual([
        10715, 10723, 10724,
      ]);
      const requiredPages = lesson.material!.pages
        .filter((item) => lesson.completion!.required_page_ids.includes(item.id))
        .map((item) => item.page_no);
      expect(requiredPages).toEqual([34, 42, 43]);
      for (const pageNo of requiredPages)
        await readRequiredPage(page, pageNo);
      await expect.poll(async () => (await readLesson()).progress?.completed)
        .toBe(true);
      expect(await nodeStatus("study")).toBe("ready");
      const pdfReader = page.locator(".pdf-reader");
      if (await pdfReader.count()) {
        await expect(pdfReader).toHaveAttribute("aria-busy", "false", { timeout: 60_000 });
        await expect(pdfReader.locator(".reader-position")).toContainText(`/ ${lesson.material!.pages.length} 页`);
        expect(await pdfReader.locator("canvas").evaluate(canvas => (canvas as HTMLCanvasElement).width)).toBeGreaterThan(100);
      }
      await page.screenshot({ path: testInfo.outputPath("resumed-reading.png") });
      expect(await page.evaluate(() =>
        document.documentElement.scrollWidth <= window.innerWidth,
      )).toBe(true);
      const readingReturn = page.waitForResponse(
        (candidate) =>
          /\/api\/story\/sessions\/[^/]+\/return$/.test(candidate.url()) &&
          candidate.request().method() === "POST",
      );
      await page.getByRole("button", { name: "完成阅读", exact: true }).click();
      await successfulJson<StoryView>(await readingReturn);
      await expect(dialogue(page)).toBeVisible();
      const readingEnd = await finishDialogue(page);
      expect(readingEnd.lines.join("\n")).toContain("教练");
      await expect.poll(() => nodeStatus("study")).toBe("complete");

      await approachObjective(page);
      const practice = await finishDialogue(page, "开始练习");
      expect(practice.view).toMatchObject({
        status: "waiting",
        activity: { id: "ch1-practice", },
      });
      await answerStoryQuestions(page, [
        { title: "计算思维的核心", answer: "抽象与自动化" },
        { title: "计算的规则", answer: "正确" },
        { title: "问题到计算", answer: "建模" },
      ]);
      await finishDialogue(page);
      await expect.poll(() => nodeStatus("practice")).toBe("complete");

      await approachObjective(page);
      const assessment = await finishDialogue(page, "开始章节测试");
      expect(assessment.view).toMatchObject({
        status: "waiting",
        activity: { id: "ch1-assessment", },
      });
      await answerStoryQuestions(page, [
        { title: "计算与规则", answer: "给定输入，按照规则处理并得到输出" },
        { title: "抽象与自动化", answer: "正确" },
        { title: "从问题到方案", answer: "数学模型" },
      ], true);
      const finale = await finishDialogue(page);
      expect(finale.lines.join("\n")).toContain("第一章已经完成");
      expect(finale.lines.join("\n")).toContain("满分");
      await expect.poll(async () => (await quest()).status).toBe("complete");
      expect((await quest()).nodes.every((node) => node.status === "complete"))
        .toBe(true);
      await page.screenshot({ path: testInfo.outputPath("chapter-complete.png") });
      await page.reload({ waitUntil: "domcontentloaded" });
      await dismissArrival(page);
      expect((await quest()).status).toBe("complete");

      const progress = await readJson<{
        events: { node_id: string; quest_id?: string }[];
      }>(
        request,
        `/api/teacher/classrooms/${classroom.id}/progress`,
        teacher,
      );
      for (const goal of (await quest()).nodes)
        expect(progress.events.filter((event) => event.node_id === goal.id))
          .toHaveLength(1);
      expect(assistantMessages).toEqual([]);
      const aiAfter = await readJson<{ model_calls: number }>(
        request,
        "/api/assistant/status",
        student,
      );
      expect(aiAfter.model_calls).toBe(aiBefore.model_calls);

      await page.getByRole("button", { name: "打开设置" }).click();
      await page.getByRole("button", { name: "退出账号", exact: true }).click();
      const other = await registerStudent(
        page,
        classroom.join_code,
        `另一位居民-${viewport.name}`,
      );
      const otherWorld = await readJson<StoryWorld>(
        request,
        `/api/world?classroom_id=${classroom.id}`,
        other,
      );
      const otherQuest = otherWorld.quests.find((value) => value.id === "arrival")!;
      expect(otherQuest.status).toBe("active");
      expect(otherQuest.nodes.find((node) => node.id === "welcome")?.status)
        .toBe("ready");
      expect(otherQuest.nodes.some((node) => node.status === "complete"))
        .toBe(false);
      expect(await readJson<StoryView | null>(
        request,
        `/api/story/current?classroom_id=${classroom.id}`,
        other,
      )).toBeNull();
      const otherLesson = await readJson<ActivityView>(
        request,
        `/api/activities/ch1-lesson?classroom_id=${classroom.id}`,
        other,
      );
      expect(otherLesson.progress?.completed ?? false).toBe(false);
      expect(otherLesson.progress?.page_no ?? null).toBeNull();
      await approachObjective(page);
      await expect(dialogue(page).locator(".dialogue-text")).toContainText(
        "欢迎来到 Pythontopia",
      );
      expect(browserErrors).toEqual([]);
    });
  });
}
