import {
  test,
  expect,
  type APIRequestContext,
  type Locator,
  type Page,
} from "@playwright/test";
import { randomUUID } from "node:crypto";

interface UiQuest {
  id: string;
  title: string;
  nodes: { id: string; status: string; optional?: boolean }[];
}

interface UiWorld {
  player: { map_key: string; tile_x: number; tile_y: number };
  quests: UiQuest[];
}

async function readWorld(
  request: APIRequestContext,
  classroomId: number,
  headers: Record<string, string>,
): Promise<UiWorld> {
  const response = await request.get(`/api/world?classroom_id=${classroomId}`, {
    headers,
  });
  expect(response.ok()).toBeTruthy();
  return (await response.json()) as UiWorld;
}

function locationKey(world: UiWorld): string {
  return `${world.player.map_key}:${world.player.tile_x}:${world.player.tile_y}`;
}

async function expectFocusInside(layer: Locator) {
  await expect
    .poll(() => layer.evaluate((root) => root.contains(document.activeElement)))
    .toBe(true);
}

async function expectSingleWorkspace(page: Page) {
  await expect(
    page.getByRole("dialog", { name: "游戏工作区", exact: true }),
  ).toHaveCount(1);
  await expect(
    page.getByRole("dialog", { name: "剧情对白", exact: true }),
  ).toHaveCount(0);
}

/** Exercise native Tab order rather than reproducing the app's focusable-list code. */
async function expectTabRoundTrip(page: Page, layer: Locator) {
  const first = layer.getByRole("button").first();
  await first.focus();
  const anchor = await first.elementHandle();
  expect(anchor).not.toBeNull();
  for (const key of ["Tab", "Shift+Tab"]) {
    await first.focus();
    let wrapped = false;
    for (let step = 0; step < 80; step++) {
      await page.keyboard.press(key);
      await expectFocusInside(layer);
      const state = await layer.evaluate((root) => {
        const active = document.activeElement;
        const assistant = root.querySelector<HTMLElement>(".assistant-panel");
        const assistantHidden =
          assistant !== null &&
          (getComputedStyle(assistant).display === "none" ||
            getComputedStyle(assistant).visibility === "hidden" ||
            assistant.getClientRects().length === 0);
        return {
          activeVisible:
            active instanceof HTMLElement &&
            active.getClientRects().length > 0 &&
            getComputedStyle(active).visibility !== "hidden",
          hiddenAssistantFocused:
            assistantHidden && assistant!.contains(active),
        };
      });
      expect(state.activeVisible).toBe(true);
      expect(state.hiddenAssistantFocused).toBe(false);
      if (
        await anchor!.evaluate((element) => element === document.activeElement)
      ) {
        wrapped = true;
        break;
      }
    }
    expect(wrapped, `${key} must return to its starting control`).toBe(true);
  }
  await anchor!.dispose();
}

async function expectControlInViewport(
  page: Page,
  control: Locator,
  minimumSize: number,
) {
  await expect(control).toBeVisible();
  const box = await control.boundingBox();
  const viewport = page.viewportSize();
  expect(box).not.toBeNull();
  expect(viewport).not.toBeNull();
  if (!box || !viewport) throw new Error("Visible control has no viewport box");
  expect(box.width).toBeGreaterThanOrEqual(minimumSize);
  expect(box.height).toBeGreaterThanOrEqual(minimumSize);
  expect(box.x).toBeGreaterThanOrEqual(0);
  expect(box.y).toBeGreaterThanOrEqual(0);
  expect(box.x + box.width).toBeLessThanOrEqual(viewport.width + 1);
  expect(box.y + box.height).toBeLessThanOrEqual(viewport.height + 1);
}

async function finishIntroDialogue(page: Page) {
  const dialogue = page.getByRole("dialog", { name: "剧情对白", exact: true });
  await expect(dialogue).toBeVisible();
  for (let step = 0; step < 10; step++) {
    const responsePromise = page.waitForResponse(
      (response) =>
        response.url().includes("/api/dialogues/") &&
        response.url().endsWith("/steps") &&
        response.request().method() === "POST",
    );
    const text = dialogue.locator(".dialogue-text");
    await expect(text).toHaveText((await text.getAttribute("aria-label")) ?? "");
    await dialogue.locator(".dialogue-box").press("Enter");
    const response = await responsePromise;
    expect(response.ok(), await response.text()).toBeTruthy();
    const result = (await response.json()) as { next_id: string | null };
    if (!result.next_id) {
      await expect(dialogue).toBeHidden({ timeout: 15_000 });
      return;
    }
  }
  throw new Error("Intro dialogue did not end within ten script nodes");
}

test("usable world HUD, modal focus, history, text scaling and touch controls", async ({
  page,
  request,
}, testInfo) => {
  test.setTimeout(180_000);
  const email = process.env.E2E_TEACHER_EMAIL;
  const password = process.env.E2E_TEACHER_PASSWORD;
  test.skip(
    !email || !password,
    "Requires explicit E2E teacher credentials; no built-in account or mocked business data.",
  );
  const pageErrors: string[] = [];
  page.on("pageerror", (error) => pageErrors.push(error.message));

  const login = await request.post("/api/auth/login", {
    data: { email, password },
  });
  expect(login.ok()).toBeTruthy();
  const teacherToken = (await login.json()) as { access_token: string };
  const teacherHeaders = {
    Authorization: `Bearer ${teacherToken.access_token}`,
  };
  const created = await request.post("/api/classrooms", {
    headers: teacherHeaders,
    data: { name: `UI ${testInfo.project.name} ${randomUUID().slice(0, 8)}` },
  });
  expect(created.status()).toBe(201);
  const classroom = (await created.json()) as {
    id: number;
    join_code: string;
  };
  const released = await request.put(
    `/api/teacher/classrooms/${classroom.id}/releases/179`,
    { headers: teacherHeaders, data: { status: "published", version: 1 } },
  );
  expect(released.ok()).toBeTruthy();

  await page.goto("/");
  await page.getByRole("button", { name: "初次到访？注册居民证" }).click();
  await page.getByLabel("名字", { exact: true }).fill("界面验收居民");
  await page
    .getByLabel("邮箱", { exact: true })
    .fill(`ui-${randomUUID()}@example.test`);
  await page.getByLabel("密码", { exact: true }).fill(`UI-${randomUUID()}`);
  await page.getByRole("button", { name: "注册并进入", exact: true }).click();
  await page
    .getByLabel("班级加入码", { exact: true })
    .fill(classroom.join_code);
  await page.getByRole("button", { name: "加入班级", exact: true }).click();
  const welcome = page.getByRole("dialog", { name: "欢迎回到小镇", exact: true });
  await welcome.getByRole("button", { name: "按任意键继续", exact: true }).click();
  await expect(welcome).toBeHidden();
  await expect(page.locator(".hud-dock")).toBeVisible();
  const worldCanvas = page.locator(".world-canvas canvas");
  await expect(worldCanvas).toHaveCount(1);
  await expect(worldCanvas).toBeVisible();
  await expect(page.locator(".world-hint")).toContainText("交谈", {
    timeout: 15_000,
  });
  const canvasNode = await worldCanvas.elementHandle();
  expect(canvasNode).not.toBeNull();
  const studentToken = await page.evaluate(() =>
    sessionStorage.getItem("pythontopia-reborn-session"),
  );
  expect(studentToken).toBeTruthy();
  const studentHeaders = { Authorization: `Bearer ${studentToken}` };
  const state = () => readWorld(request, classroom.id, studentHeaders);

  // Select the real introductory quest through its journal control, even if this
  // teacher has authored other main quests in the same published chapter.
  const initialWorld = await state();
  const introQuest = initialWorld.quests.find(
    (quest) => quest.id === "arrival",
  );
  expect(introQuest).toBeDefined();
  await page
    .locator(".hud-dock")
    .getByRole("button", { name: /任务日志/ })
    .click();
  const trackingWorkspace = page.getByRole("dialog", {
    name: "游戏工作区",
    exact: true,
  });
  await trackingWorkspace.locator(".quest-card").filter({ hasText: introQuest!.title }).click();
  const introEntry = trackingWorkspace.locator(".journal-entry").filter({
    has: page.getByRole("heading", {
      name: introQuest!.title,
      exact: true,
    }),
  });
  await introEntry.getByRole("button", { name: /^(追踪任务|正在追踪)$/ }).click();
  await trackingWorkspace
    .getByRole("button", { name: "关闭当前工作区", exact: true })
    .click();
  await expect(trackingWorkspace).toBeHidden();

  const tracker = page.locator(".quest-tracker");
  // The current HUD keeps its tracked objective visible without a collapse control.
  await expect(tracker.locator("#tracked-quest-details")).toBeVisible();
  const trackedTitle = (
    await tracker.getByRole("heading").textContent()
  )?.trim();
  const tracked = initialWorld.quests.find(
    (quest) => quest.title === trackedTitle,
  );
  expect(tracked).toBeDefined();
  const required = tracked!.nodes.filter(
    (node) => !node.optional && node.status !== "skipped",
  );
  const initialCompleted = required.filter(
    (node) => node.status === "complete",
  ).length;
  // The current HUD hides its numerical progress bar but retains computed progress data.
  const progress = tracker.locator(".quest-progress");
  await expect(progress).toBeHidden();
  await expect(progress).toHaveAttribute(
    "aria-valuenow",
    String(initialCompleted),
  );
  await expect(progress).toHaveAttribute(
    "aria-valuemax",
    String(required.length),
  );
  expect(required.length).toBeGreaterThan(0);

  const dockButtons = page.locator(".hud-dock button");
  expect(await dockButtons.count()).toBeGreaterThanOrEqual(4);
  for (let index = 0; index < (await dockButtons.count()); index++) {
    await expectControlInViewport(page, dockButtons.nth(index), 44);
  }
  const touch = testInfo.project.name === "phone-landscape";
  if (touch) {
    expect(
      await page.evaluate(() => matchMedia("(pointer: coarse)").matches),
    ).toBe(true);
    const directions = page.locator(".touch-controls button");
    await expect(directions).toHaveCount(5);
    for (let index = 0; index < (await directions.count()); index++) {
      await expectControlInViewport(page, directions.nth(index), 48);
    }
  }
  await page.screenshot({ path: testInfo.outputPath("before-world.png") });

  const workspace = page.getByRole("dialog", {
    name: "游戏工作区",
    exact: true,
  });
  const settingsButton = page.getByRole("button", {
    name: "打开设置",
    exact: true,
  });
  await settingsButton.click();
  await expectSingleWorkspace(page);
  await expectFocusInside(workspace);
  await expectTabRoundTrip(page, workspace);

  const textSize = workspace.getByLabel(/^文字大小/);
  await expect(textSize).toHaveValue("1");
  const settingsProse = workspace.locator(".interface-settings .muted").first();
  const normalFont = await settingsProse.evaluate((element) =>
    Number.parseFloat(getComputedStyle(element).fontSize),
  );
  await textSize.selectOption("2");
  await expect(textSize).toHaveValue("2");
  await expect
    .poll(() =>
      settingsProse.evaluate((element) =>
        Number.parseFloat(getComputedStyle(element).fontSize),
      ),
    )
    .toBeGreaterThanOrEqual(normalFont * 1.9);
  const close = workspace.getByRole("button", {
    name: "关闭当前工作区",
    exact: true,
  });
  await close.scrollIntoViewIfNeeded();
  await expectControlInViewport(page, close, 44);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth + 1,
    ),
  ).toBe(true);
  const reducedMotion = workspace.getByRole("checkbox", {
    name: "减少界面动效",
    exact: true,
  });
  await reducedMotion.check();
  await expect(reducedMotion).toBeChecked();
  await expect(page.locator("html")).toHaveAttribute(
    "data-reduced-motion",
    "true",
  );
  await page.screenshot({
    path: testInfo.outputPath("settings-200-percent.png"),
  });
  await textSize.selectOption("1");
  await expect(textSize).toHaveValue("1");

  await workspace.getByRole("button", { name: /已开放材料/ }).click();
  await expect(workspace.locator(".library-workspace")).toBeVisible();
  await expectSingleWorkspace(page);
  await expectFocusInside(workspace);
  await expectTabRoundTrip(page, workspace);
  await workspace.getByRole("button", { name: /返回设置/ }).click();
  await expect(workspace.locator(".settings-workspace")).toBeVisible();
  await expectSingleWorkspace(page);
  await workspace.getByRole("button", { name: /已开放材料/ }).click();
  await expect(workspace.locator(".library-workspace")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(workspace.locator(".settings-workspace")).toBeVisible();
  await expectSingleWorkspace(page);
  await page.keyboard.press("Escape");
  await expect(workspace).toBeHidden();
  await expect
    .poll(() =>
      page
        .locator(".hud-dock")
        .evaluate((root) => root.contains(document.activeElement)),
    )
    .toBe(true);

  const openDialogue = async () => {
    if (touch) {
      const interact = page.getByRole("button", { name: "互动", exact: true });
      const box = await interact.boundingBox();
      expect(box).not.toBeNull();
      if (!box) throw new Error("Touch interaction control is not visible");
      await page.touchscreen.tap(box.x + box.width / 2, box.y + box.height / 2);
    } else {
      await page.keyboard.press("e");
    }
    await expect(
      page.getByRole("dialog", { name: "剧情对白", exact: true }),
    ).toBeVisible();
  };
  const beforeDialogue = locationKey(await state());
  await openDialogue();
  const dialogue = page.getByRole("dialog", {
    name: "剧情对白",
    exact: true,
  });
  await expectFocusInside(dialogue);
  await expectTabRoundTrip(page, dialogue);
  await page.keyboard.press("j");
  await expect(dialogue).toBeVisible();
  await expect(workspace).toHaveCount(0);
  await expectFocusInside(dialogue);
  const dialogueText = dialogue.locator(".dialogue-text");
  const completeText = await dialogueText.getAttribute("aria-label");
  expect(completeText).toBeTruthy();
  expect((await dialogueText.textContent())?.trim()).toBe(completeText?.trim());
  await page.screenshot({ path: testInfo.outputPath("dialogue.png") });
  await page.keyboard.press("Escape");
  await expect(dialogue).toBeHidden();
  await expect(workspace).toHaveCount(0);
  expect(locationKey(await state())).toBe(beforeDialogue);

  // Reach a real lesson through the deterministic NPC script, never POST fake task evidence.
  for (let round = 0; round < 3; round++) {
    const world = await state();
    if (
      world.quests
        .find((quest) => quest.id === "arrival")
        ?.nodes.find((node) => node.id === "study")?.status === "ready"
    )
      break;
    await openDialogue();
    await finishIntroDialogue(page);
  }
  const learnedWorld = await state();
  const arrival = learnedWorld.quests.find((quest) => quest.id === "arrival");
  expect(arrival?.nodes.find((node) => node.id === "study")?.status).toBe(
    "ready",
  );
  const completedSteps = arrival!.nodes.filter(
    (node) => !node.optional && node.status === "complete",
  ).length;
  expect(completedSteps).toBeGreaterThan(initialCompleted);
  await expect(progress).toHaveAttribute(
    "aria-valuenow",
    String(completedSteps),
  );
  await page.keyboard.press("j");
  await expectSingleWorkspace(page);
  await workspace.locator(".quest-card").filter({ hasText: arrival!.title }).click();
  const arrivalEntry = workspace.locator(".journal-entry").filter({
    has: page.getByRole("heading", { name: arrival!.title, exact: true }),
  });
  await arrivalEntry
    .getByRole("button", { name: "打开课程", exact: true })
    .click();
  await expect(workspace.locator(".lesson-workspace")).toBeVisible();
  await expect(workspace.locator(".lesson-text")).toBeVisible();
  await expectSingleWorkspace(page);
  await expectFocusInside(workspace);
  await expectTabRoundTrip(page, workspace);
  if (touch) {
    await expect(workspace.locator(".assistant-panel")).toBeHidden();
  }
  await workspace
    .getByRole("button", { name: "关闭当前工作区", exact: true })
    .click();
  await expect(workspace).toBeHidden();
  await expect(worldCanvas).toHaveCount(1);
  expect(
    await canvasNode!.evaluate(
      (element) =>
        element.isConnected &&
        element === document.querySelector(".world-canvas canvas"),
    ),
  ).toBe(true);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth + 1,
    ),
  ).toBe(true);
  const assistantStatus = await request.get("/api/assistant/status", {
    headers: studentHeaders,
  });
  expect(assistantStatus.ok()).toBeTruthy();
  expect(
    ((await assistantStatus.json()) as { model_calls: number }).model_calls,
  ).toBe(0);
  expect(pageErrors).toEqual([]);
  await canvasNode!.dispose();
});
