import { test, expect, type APIRequestContext, type APIResponse, type ElementHandle, type Locator, type Page, type Response, type TestInfo } from "@playwright/test";
import { randomUUID } from "node:crypto";

type Headers = { Authorization: string };
interface Identity { access_token: string; user: { id: number; name: string }; }
interface Classroom { id: number; join_code: string; }
interface World {
  npcs: { key: string; map_key: string; tile_x: number; tile_y: number }[];
  quests: { id: string; nodes: { id: string; status: string }[] }[];
}
interface Story {
  id: string; revision: number; status: "active" | "waiting" | "ended";
  node: { text: string } | null; activity: { id: string } | null;
}
interface Activity {
  id: string; material: { id: number; title: string; kind: string; status: string; pages: { id: number; page_no: number }[] };
  completion: { required_page_ids: number[] };
  progress: { page_no: number | null; completed: boolean };
}
interface HeaderStyle { background: string; color: string; border: string; radius: string; padding: string; shadow: string; }
interface Cards { layout: string; surfaces: string[]; }

const panel = (page: Page) => page.locator(".overlay-host.game-workspace");
const dialogue = (page: Page) => page.getByRole("dialog", { name: "剧情对白", exact: true });
const closeButton = (page: Page) => panel(page).getByRole("button", { name: "关闭当前工作区", exact: true });

async function json<T>(response: APIResponse | Response): Promise<T> {
  expect(response.ok(), await response.text()).toBeTruthy();
  return response.json() as Promise<T>;
}

/** Only this newly created classroom/student are mutated; existing course content is read as published. */
async function prepare(request: APIRequestContext) {
  const email = process.env.E2E_TEACHER_EMAIL, password = process.env.E2E_TEACHER_PASSWORD;
  test.skip(!email || !password || process.env.E2E_COURSE_MEDIA_IMPORTED !== "1",
    "Requires an isolated deployment, imported real PDF media and explicit teacher credentials.");
  const login = await json<Identity>(await request.post("/api/auth/login", { data: { email, password } }));
  const teacher: Headers = { Authorization: `Bearer ${login.access_token}` };
  const classroom = await json<Classroom>(await request.post("/api/classrooms", {
    headers: teacher, data: { name: `游戏设计验收 ${randomUUID().slice(0, 8)}` },
  }));
  await json(await request.put(`/api/teacher/classrooms/${classroom.id}/releases/179`, {
    headers: teacher, data: { status: "published" },
  }));
  const identity = await json<Identity>(await request.post("/api/auth/register", {
    data: { name: "游戏界面验收居民", email: `game-design-${randomUUID()}@example.test`, password: `Resident-${randomUUID()}` },
  }));
  const student: Headers = { Authorization: `Bearer ${identity.access_token}` };
  await json(await request.post("/api/classrooms/join", { headers: student, data: { join_code: classroom.join_code } }));
  const readWorld = () => request.get(`/api/world?classroom_id=${classroom.id}`, { headers: student }).then(json<World>);
  const mayor = (await readWorld()).npcs.find(npc => npc.key === "mayor_py")!;
  await json(await request.put("/api/world/position", { headers: student, data: {
    classroom_id: classroom.id, map_key: mayor.map_key, tile_x: mayor.tile_x, tile_y: mayor.tile_y, direction: "down",
  } }));
  let welcome = await json<Story>(await request.post("/api/story/interactions", {
    headers: student, data: { classroom_id: classroom.id, npc_key: mayor.key },
  }));
  for (let step = 0; step < 8 && welcome.status === "active"; step++) {
    welcome = await json<Story>(await request.post(`/api/story/sessions/${welcome.id}/advance`, {
      headers: student, data: { classroom_id: classroom.id, revision: welcome.revision, event_id: randomUUID() },
    }));
  }
  expect(welcome.status).toBe("ended");
  const arrival = (await readWorld()).quests.find(quest => quest.id === "arrival")!;
  expect(arrival.nodes.filter(node => node.status === "complete").map(node => node.id)).toEqual(["welcome"]);
  const readLesson = () => request.get(`/api/activities/ch1-lesson?classroom_id=${classroom.id}`, { headers: student }).then(json<Activity>);
  const lesson = await readLesson();
  expect(lesson.material).toMatchObject({ kind: "pdf", status: "ready" });
  return { classroom, identity, student, lesson, readLesson };
}

async function enter(page: Page, identity: Identity, classroom: Classroom) {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.addInitScript(({ token, userId, classroomId }) => {
    sessionStorage.setItem("pythontopia-reborn-session", token);
    sessionStorage.setItem(`classroom:${userId}`, String(classroomId));
    localStorage.setItem("pythontopia-reborn-interface", JSON.stringify({ textScale: 1, reduceMotion: true, musicVolume: 0.15 }));
  }, { token: identity.access_token, userId: identity.user.id, classroomId: classroom.id });
  await page.goto("/world");
  const welcome = page.getByRole("dialog", { name: "欢迎回到小镇", exact: true });
  await expect(welcome).toBeVisible();
  await welcome.getByRole("button", { name: "按任意键继续", exact: true }).click();
  await expect(welcome).toBeHidden();
  await expect(page.locator(".hud-dock")).toBeVisible();
  await expect(page.locator(".world-canvas canvas")).toHaveCount(1);
  return (await page.locator(".world-canvas canvas").elementHandle())!;
}

async function sameCanvas(page: Page, canvas: ElementHandle) {
  await expect(page.locator(".world-canvas canvas")).toHaveCount(1);
  expect(await canvas.evaluate(element => element === document.querySelector(".world-canvas canvas"))).toBe(true);
}

async function noHorizontalOverflow(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
  const overflow = await panel(page).evaluate(root => [root, ...root.querySelectorAll<HTMLElement>(
    ".workspace-shell,.town-content,.quest-directory,.quest-detail,.lesson-content,.practice-content,.learning-support,.assistant-panel",
  )].filter(element => element.getClientRects().length && !element.closest("[hidden]"))
    .filter(element => element.scrollWidth > element.clientWidth + 1).map(element => element.className));
  expect(overflow).toEqual([]);
}

async function reachable(locator: Locator) {
  await locator.scrollIntoViewIfNeeded();
  await expect(locator).toBeVisible();
  await expect(locator).toBeInViewport({ ratio: 0.95 });
}

async function shell(page: Page, title: string, cards: Cards, baseline?: HeaderStyle) {
  const current = panel(page), heading = current.locator(".game-workspace-title h2");
  await expect(current).toBeVisible();
  await expect(heading).toHaveCount(1);
  await expect(heading).toHaveText(title);
  await expect(heading).toBeInViewport({ ratio: 1 });
  const dimensions = await current.locator(".workspace-shell").boundingBox(), viewport = page.viewportSize()!;
  expect(dimensions!.width / viewport.width).toBeGreaterThanOrEqual(0.9);
  expect(dimensions!.height / viewport.height).toBeGreaterThanOrEqual(0.9);
  expect(await current.locator(".workspace-shell").evaluate(element => {
    const style = getComputedStyle(element);
    return { background: style.backgroundColor, image: style.backgroundImage, border: style.borderWidth, shadow: style.boxShadow };
  })).toEqual({ background: "rgba(0, 0, 0, 0)", image: "none", border: "0px", shadow: "none" });
  expect(await current.locator(".game-workspace-title > .game-icon").evaluate(element => {
    const style = getComputedStyle(element);
    return { width: style.width, height: style.height, color: style.color };
  })).toEqual({ width: "42px", height: "42px", color: "rgb(230, 190, 118)" });
  const sizes = await heading.evaluate(element => ({ title: Number.parseFloat(getComputedStyle(element).fontSize), root: Number.parseFloat(getComputedStyle(document.documentElement).fontSize) }));
  expect(sizes.title).toBeCloseTo(sizes.root * 1.7, 1);
  const close = closeButton(page), closeBox = await close.boundingBox(), titleBox = await heading.boundingBox();
  await expect(close).toBeInViewport({ ratio: 1 });
  expect(closeBox!.height).toBeGreaterThanOrEqual(44);
  expect(closeBox!.x).toBeGreaterThan(titleBox!.x + titleBox!.width);
  expect(closeBox!.x + closeBox!.width).toBeLessThanOrEqual(dimensions!.x + dimensions!.width + 1);
  const style = await close.evaluate(element => {
    const value = getComputedStyle(element);
    return { background: value.backgroundColor, color: value.color, border: value.borderColor, radius: value.borderRadius, padding: value.padding, shadow: value.boxShadow };
  });
  expect(style.radius).toBe("24px");
  if (baseline) expect(style).toEqual(baseline);
  const layout = current.locator(cards.layout);
  expect(await layout.evaluate(element => {
    const value = getComputedStyle(element);
    return { background: value.backgroundColor, image: value.backgroundImage, border: value.borderWidth, shadow: value.boxShadow };
  })).toEqual({ background: "rgba(0, 0, 0, 0)", image: "none", border: "0px", shadow: "none" });
  for (const selector of cards.surfaces) {
    const surface = current.locator(selector);
    await expect(surface).toBeVisible();
    expect(await surface.evaluate(element => Number.parseFloat(getComputedStyle(element).borderRadius))).toBeGreaterThanOrEqual(12);
    if (selector === ".journal-index") {
      await expect(surface).toHaveCSS("background-color", "rgba(0, 0, 0, 0)");
      expect(await surface.locator(".journal-cover").evaluate(element => getComputedStyle(element).backgroundImage)).toContain("linear-gradient");
      await expect(surface.locator(".journal-directory")).toHaveCSS("background-color", "rgb(251, 245, 233)");
      continue;
    }
    expect(await surface.evaluate(element => {
      const value = getComputedStyle(element);
      return value.backgroundColor !== "rgba(0, 0, 0, 0)" || value.backgroundImage !== "none";
    })).toBe(true);
  }
  if (cards.surfaces.length === 2 && cards.layout !== ".learning-layout") {
    expect(await layout.evaluate(element => getComputedStyle(element).columnGap)).toBe("22px");
    const first = await current.locator(cards.surfaces[0]!).boundingBox(), second = await current.locator(cards.surfaces[1]!).boundingBox();
    expect(second!.x - first!.x - first!.width).toBeCloseTo(22, 0);
  }
  await noHorizontalOverflow(page);
  const thickButtons = await current.evaluate(root => Array.from(root.querySelectorAll<HTMLButtonElement>("button"))
    .filter(button => button.getClientRects().length && !button.closest("[hidden],[inert]"))
    .filter(button => {
      const css = getComputedStyle(button);
      return css.boxShadow !== "none" || [css.borderTopWidth, css.borderRightWidth, css.borderBottomWidth, css.borderLeftWidth].some(value => parseFloat(value) > 1.1);
    }).map(button => button.textContent?.trim() || button.getAttribute("aria-label")));
  expect(thickButtons).toEqual([]);
  const sidebar = current.locator(".learning-support,.town-sidebar,.journal-index,.map-sidebar");
  if (await sidebar.count()) {
    await current.getByRole("button", { name: "折叠侧边栏", exact: true }).click();
    await expect(sidebar).toBeHidden();
    const expand = current.getByRole("button", { name: "展开侧边栏", exact: true });
    await expect(expand).toBeInViewport({ ratio: .99 });
    await expand.click();
    await expect(sidebar).toBeVisible();
  }
  return style;
}

async function pdfFitsWithoutLeftScroll(page: Page) {
  const reader = panel(page).locator(".pdf-reader"), left = panel(page).locator(".lesson-content"), surface = reader.locator(".pdf-page-surface");
  await expect(reader).toHaveAttribute("aria-busy", "false", { timeout: 60_000 });
  const canvas = reader.getByRole("img", { name: "PDF课件当前页", exact: true });
  await expect.poll(async () => {
    const image = await canvas.boundingBox(), card = await surface.boundingBox();
    return Boolean(image && card && image.width > 100 && image.height > 100 && image.x >= card.x - 1 && image.y >= card.y - 1
      && image.x + image.width <= card.x + card.width + 1 && image.y + image.height <= card.y + card.height + 1);
  }).toBe(true);
  for (const region of [left, reader, surface]) {
    expect(await region.evaluate(element => ({ vertical: element.scrollHeight <= element.clientHeight + 1, horizontal: element.scrollWidth <= element.clientWidth + 1 })))
      .toEqual({ vertical: true, horizontal: true });
  }
  const controls = reader.getByRole("navigation", { name: "PDF 分页", exact: true });
  await expect(controls).toBeInViewport({ ratio: 1 });
  const card = await surface.boundingBox(), navigation = await controls.boundingBox();
  expect(navigation!.y).toBeGreaterThanOrEqual(card!.y + card!.height - 1);
  await expect(panel(page)).not.toContainText("提取文本");
  await expect(panel(page)).not.toContainText("转换后的课件");
}

async function assistantTab(page: Page, name: string) {
  const support = panel(page).locator(".learning-support");
  const position = panel(page).locator(".pdf-reader .reader-position"), previousPage = await position.count() ? await position.textContent() : null;
  await support.getByRole("tab", { name, exact: true }).click();
  await expect(support.getByRole("tab", { name, exact: true })).toHaveAttribute("aria-selected", "true");
  await expect(support.getByRole("heading", { name, exact: true })).toBeVisible();
  const portrait = support.locator(".assistant-panel .assistant-portrait");
  await expect(portrait).toHaveAttribute("aria-hidden", "true");
  await expect.poll(async () => portrait.evaluate(element => (element as HTMLImageElement).naturalWidth)).toBeGreaterThan(0);
  const imageStyle = await portrait.evaluate(element => ({ opacity: Number(getComputedStyle(element).opacity), pointerEvents: getComputedStyle(element).pointerEvents }));
  expect(imageStyle.opacity).toBeGreaterThan(0);
  expect(imageStyle.opacity).toBeLessThanOrEqual(0.15);
  expect(imageStyle.pointerEvents).toBe("none");
  const composer = support.getByPlaceholder("向助教提问", { exact: true });
  await expect(support.locator(".assistant-form-actions")).not.toContainText("2000");
  await expect(support.getByRole("button", { name: "发送", exact: true }).locator("svg")).toHaveCount(1);
  await expect(composer).not.toHaveAttribute("maxlength");
  await reachable(composer);
  if (previousPage !== null) await expect(position).toHaveText(previousPage);
  return support;
}

async function adjustLearningLayout(page: Page) {
  const separator = panel(page).getByRole("separator", { name: "调整内容与助教宽度", exact: true });
  await expect(separator).toBeVisible();
  const original = Number(await separator.getAttribute("aria-valuenow")), bounds = await separator.boundingBox();
  await page.mouse.move(bounds!.x + bounds!.width / 2, bounds!.y + bounds!.height / 2);
  await page.mouse.down();
  await page.mouse.move(bounds!.x + bounds!.width / 2 - 150, bounds!.y + bounds!.height / 2, { steps: 8 });
  await page.mouse.up();
  expect(Number(await separator.getAttribute("aria-valuenow"))).toBeGreaterThan(original);
  await pdfFitsWithoutLeftScroll(page);
  await separator.focus();
  await page.keyboard.press("Home");
  await expect(separator).toHaveAttribute("aria-valuenow", "28");
  await page.keyboard.press("ArrowLeft");
  await expect(separator).toHaveAttribute("aria-valuenow", "30");
  await expect(panel(page).getByRole("button", { name: "间距", exact: true })).toHaveCount(0);
  await expect(panel(page).locator(".learning-spacing")).toHaveCount(0);
  await expect.poll(async () => panel(page).locator(".learning-divider").evaluate(element => Math.round(element.getBoundingClientRect().width))).toBe(22);
  await pdfFitsWithoutLeftScroll(page);
  await separator.focus();
  await page.keyboard.press("Home");
  await expect(separator).toHaveAttribute("aria-valuenow", "28");
}

async function shot(page: Page, info: TestInfo, name: string) {
  await page.screenshot({ path: info.outputPath(`${name}-1920.png`), animations: "disabled" });
}

async function finishDialogue(page: Page, choice?: string) {
  for (let step = 0; step < 16; step++) {
    const current = dialogue(page);
    await expect(current).toBeVisible();
    const response = page.waitForResponse(candidate => /\/api\/story\/sessions\/[^/]+\/advance$/.test(candidate.url()) && candidate.request().method() === "POST");
    if (await current.locator(".dialogue-response").count()) {
      expect(choice, "An explicit real story choice is required").toBeTruthy();
      await current.locator(".dialogue-response").filter({ hasText: choice! }).click();
    } else await current.locator(".dialogue-text").click();
    const view = await json<Story>(await response);
    if (view.status !== "active") {
      await expect(current).toBeHidden();
      return view;
    }
    if (view.node) await expect(current.locator(".dialogue-text")).toHaveText(view.node.text);
  }
  throw new Error("The bounded story scene did not finish in sixteen nodes");
}

async function approach(page: Page, choice: string, activity: string) {
  const response = page.waitForResponse(candidate => candidate.url().endsWith("/api/story/interactions") && candidate.request().method() === "POST", { timeout: 60_000 });
  await page.locator(".quest-tracker").getByRole("button", { name: "立即前往", exact: true }).click();
  await json<Story>(await response);
  const view = await finishDialogue(page, choice);
  expect(view).toMatchObject({ status: "waiting", activity: { id: activity } });
}

async function suspendActivity(page: Page) {
  const response = page.waitForResponse(candidate => /\/api\/story\/sessions\/[^/]+\/return$/.test(candidate.url()) && candidate.request().method() === "POST");
  await page.keyboard.press("Escape");
  expect((await json<Story>(await response)).status).toBe("active");
  await expect(panel(page)).toBeHidden();
  expect((await finishDialogue(page)).status).toBe("ended");
}

async function setScale(page: Page, value: "1" | "2") {
  await page.locator(".hud-dock").getByRole("button", { name: "打开设置", exact: true }).click();
  await panel(page).getByRole("tab", { name: "界面", exact: true }).click();
  await panel(page).getByLabel(/^文字大小/).selectOption(value);
  await expect.poll(() => page.locator("html").evaluate(element => getComputedStyle(element).getPropertyValue("--text-scale").trim())).toBe(value);
  await expect(closeButton(page)).toBeInViewport({ ratio: 1 });
  await page.keyboard.press("Escape");
  await expect(panel(page)).toBeHidden();
}

test("seven game workspaces share the real shell, controls and readable 100–200% layouts", async ({ page, request }, info) => {
  test.setTimeout(360_000);
  await page.setViewportSize({ width: 1920, height: 1080 });
  const errors: string[] = [], modelRequests: string[] = [];
  page.on("pageerror", error => errors.push(error.message));
  page.on("request", outgoing => {
    if (outgoing.method() === "POST" && /\/api\/assistant\/sessions\/[^/]+\/messages$/.test(outgoing.url())) modelRequests.push(outgoing.url());
  });
  const setup = await prepare(request), canvas = await enter(page, setup.identity, setup.classroom);
  const town = { layout: ".town-book", surfaces: [".town-sidebar", ".town-content"] };
  const lessonCards = { layout: ".learning-layout", surfaces: [".pdf-page-surface", ".learning-support"] };
  const practiceCards = { layout: ".learning-layout", surfaces: [".practice-content", ".learning-support"] };
  const dock = page.locator(".hud-dock");

  await dock.getByRole("button", { name: "任务日志", exact: true }).click();
  const baseline = await shell(page, "任务日志", { layout: ".journal-book", surfaces: [".journal-index", ".quest-detail"] });
  await expect(panel(page).locator(".journal-controls,.journal-count,.journal-cover img")).toHaveCount(0);
  await expect(panel(page).locator(".journal-cover h3")).toHaveText("委托目录");
  expect(await panel(page).locator(".journal-cover h3").evaluate(element => parseFloat(getComputedStyle(element).fontSize))).toBeGreaterThan(22);
  await panel(page).getByRole("group", { name: "任务状态", exact: true }).getByRole("button", { name: "已完成", exact: true }).click();
  await panel(page).getByRole("combobox", { name: "任务类型", exact: true }).selectOption("side");
  await expect(panel(page).locator(".empty-directory")).toHaveText("没有委托");
  await expect(panel(page).locator(".journal-empty")).toHaveText("没有委托");
  await shot(page, info, "01-journal-empty");
  await panel(page).getByRole("combobox", { name: "任务类型", exact: true }).selectOption("all");
  await panel(page).getByRole("group", { name: "任务状态", exact: true }).getByRole("button", { name: "进行中", exact: true }).click();
  await expect(panel(page)).not.toContainText("QUEST JOURNAL");
  const segmented = panel(page).getByRole("group", { name: "任务状态" });
  await expect(segmented).toHaveClass(/game-segmented/);
  await segmented.getByRole("button", { name: "全部", exact: true }).click();
  expect(await segmented.getByRole("button", { name: "全部", exact: true }).evaluate(element => getComputedStyle(element).backgroundImage)).toContain("linear-gradient");
  await shot(page, info, "01-journal");
  await page.keyboard.press("Escape");
  await expect(panel(page)).toBeHidden();
  await sameCanvas(page, canvas);

  await dock.getByRole("button", { name: "学习资料", exact: true }).click();
  await expect(panel(page).locator(".library-card")).not.toHaveCount(0);
  await shell(page, "小镇图书馆", town, baseline);
  await panel(page).getByRole("navigation", { name: "资料章节" }).getByRole("button", { name: /第一章/ }).click();
  await panel(page).getByRole("group", { name: "资料类型" }).getByRole("button", { name: "课件", exact: true }).click();
  await panel(page).getByLabel("搜索资料").fill("计算");
  await expect(panel(page).locator(".library-card")).toHaveCount(1);
  await shot(page, info, "02-library");
  await panel(page).locator(".library-card").click();
  await expect(panel(page).locator(".pdf-reader")).toHaveAttribute("aria-busy", "false", { timeout: 60_000 });
  expect(await panel(page).getByRole("img", { name: "PDF课件当前页" }).evaluate(element => (element as HTMLCanvasElement).width)).toBeGreaterThan(100);
  await shell(page, "资料阅读", { layout: ".learning-layout", surfaces: [".pdf-page-surface"] }, baseline);
  await pdfFitsWithoutLeftScroll(page);
  await panel(page).getByRole("tab", { name: "图书管理员", exact: true }).click();
  await expect(panel(page)).toContainText("当前资料未关联助教活动");
  await expect(panel(page).locator(".assistant-panel")).toHaveCount(0);
  await panel(page).getByRole("tab", { name: "学习目标", exact: true }).click();
  await panel(page).getByRole("tab", { name: "文档目录", exact: true }).click();
  const outline = panel(page).getByRole("navigation", { name: "文档页面目录", exact: true });
  await expect(outline.getByRole("button")).toHaveCount(45);
  await outline.getByRole("button").nth(1).click();
  await expect(panel(page).locator(".pdf-reader .reader-position strong")).toHaveText("2");
  await expect(panel(page).getByRole("progressbar", { name: "阅读位置", exact: true })).toHaveAttribute("value", "2");
  await expect(outline.getByRole("button").nth(1)).toHaveAttribute("aria-current", "page");
  await pdfFitsWithoutLeftScroll(page);
  await shot(page, info, "03-library-outline");
  await page.setViewportSize({ width: 1203, height: 869 });
  await pdfFitsWithoutLeftScroll(page);
  await expect(panel(page).getByRole("tablist", { name: "学习支持", exact: true })).toBeInViewport({ ratio: 1 });
  await noHorizontalOverflow(page);
  await page.screenshot({ path: info.outputPath("03-library-1203x869.png"), animations: "disabled" });
  await page.setViewportSize({ width: 1920, height: 1080 });
  await panel(page).getByRole("tab", { name: "学习目标", exact: true }).click();
  await shot(page, info, "03-library-pdf-reading");
  await page.keyboard.press("Escape");
  await expect(panel(page).locator(".library-workspace")).toBeVisible();
  await expect(panel(page).getByLabel("搜索资料")).toHaveValue("计算");
  await expect(panel(page).getByRole("group", { name: "资料类型" }).getByRole("button", { name: "课件", exact: true })).toHaveAttribute("aria-pressed", "true");
  await panel(page).locator(".library-card").click();
  await panel(page).getByRole("button", { name: /返回小镇图书馆/ }).click();
  await expect(panel(page).locator(".library-workspace")).toBeVisible();
  await panel(page).locator(".library-card").click();
  await closeButton(page).click();
  await expect(panel(page)).toBeHidden();
  await sameCanvas(page, canvas);

  await dock.getByRole("button", { name: "地图", exact: true }).click();
  await expect(panel(page).locator(".map-stage")).toHaveAttribute("aria-busy", "false");
  await shell(page, "小镇地图", { layout: ".map-layout", surfaces: [".map-main", ".map-sidebar"] }, baseline);
  expect(await panel(page).locator(".map-marker:focus,.map-target-list button:focus").count()).toBe(0);
  const selectedMapTargets = () => panel(page).locator('.map-target-list button[aria-pressed="true"]').evaluateAll(elements => elements.map(element => element.getAttribute("aria-label")));
  const beforeMapKeys = await selectedMapTargets();
  await page.keyboard.press("ArrowRight");
  await page.keyboard.press("w");
  expect(await selectedMapTargets()).toEqual(beforeMapKeys);
  await shot(page, info, "04-map");
  await page.keyboard.press("Escape");
  await expect(panel(page)).toBeHidden();
  await sameCanvas(page, canvas);

  await dock.getByRole("button", { name: "我的班级", exact: true }).click();
  await shell(page, "我的班级", town, baseline);
  await shot(page, info, "05-classroom");
  await closeButton(page).click();
  await expect(panel(page)).toBeHidden();

  await dock.getByRole("button", { name: "打开设置", exact: true }).click();
  await shell(page, "设置", town, baseline);
  await shot(page, info, "06-settings-account");
  await panel(page).getByRole("tab", { name: "界面", exact: true }).click();
  const scale = panel(page).getByLabel(/^文字大小/), motion = panel(page).getByRole("switch", { name: /^减少界面动效/ });
  expect(await scale.evaluate(element => ({ appearance: getComputedStyle(element).appearance, radius: getComputedStyle(element).borderRadius, image: getComputedStyle(element).backgroundImage })))
    .toMatchObject({ appearance: "none", radius: "8px" });
  expect(await scale.evaluate(element => getComputedStyle(element).backgroundImage)).toContain("data:image/svg+xml");
  const selectPadding = await scale.evaluate(element => ({ right: Number.parseFloat(getComputedStyle(element).paddingRight), root: Number.parseFloat(getComputedStyle(document.documentElement).fontSize) }));
  expect(selectPadding.right).toBeCloseTo(selectPadding.root * 2.6, 1);
  await motion.focus();
  await page.keyboard.press("Space");
  await expect(motion).not.toBeChecked();
  await page.keyboard.press("Space");
  await expect(motion).toBeChecked();
  expect(await panel(page).locator(".game-switch-track").evaluate(element => getComputedStyle(element).backgroundColor)).toBe("rgb(40, 75, 67)");
  await shot(page, info, "06-settings-interface");
  info.annotations.push({ type: "style-only", description: "The temporary native checkbox checks shared visuals and Space behavior only; the current student product has no multiple-selection business control." });
  await panel(page).locator(".town-content").evaluate(element => {
    const label = document.createElement("label");
    label.dataset.e2eStyleOnly = "checkbox";
    label.style.cssText = "display:flex;align-items:center;gap:12px;min-height:44px;padding:12px";
    const input = document.createElement("input");
    input.type = "checkbox";
    input.setAttribute("aria-label", "复选框样式示例（非业务）");
    label.append(input, document.createTextNode("复选框样式示例（非业务）"));
    element.append(label);
  });
  const checkbox = panel(page).getByRole("checkbox", { name: "复选框样式示例（非业务）", exact: true });
  await checkbox.focus();
  await page.keyboard.press("Space");
  await expect(checkbox).toBeChecked();
  expect(await checkbox.evaluate(element => ({ background: getComputedStyle(element).backgroundColor, radius: getComputedStyle(element).borderRadius, appearance: getComputedStyle(element).appearance })))
    .toEqual({ background: "rgb(40, 75, 67)", radius: "5px", appearance: "none" });
  await shot(page, info, "06-checkbox-style-only");
  await panel(page).locator('[data-e2e-style-only="checkbox"]').evaluate(element => element.remove());
  info.annotations.push({ type: "style-only", description: "Forced colors only emulates browser styling; the real switch is inspected without changing business or learning state." });
  await page.emulateMedia({ forcedColors: "active", reducedMotion: "reduce" });
  expect(await motion.evaluate(element => ({ opacity: getComputedStyle(element).opacity, appearance: getComputedStyle(element).appearance, position: getComputedStyle(element).position })))
    .toEqual({ opacity: "1", appearance: "auto", position: "static" });
  await reachable(motion);
  await expect(panel(page).locator(".game-switch-track")).toBeHidden();
  await shot(page, info, "06-forced-colors-switch-style-only");
  await page.emulateMedia({ forcedColors: "none", reducedMotion: "reduce" });
  await panel(page).getByRole("tab", { name: "音乐", exact: true }).click();
  await panel(page).getByRole("switch", { name: /^播放小镇环境音乐/ }).check();
  const volume = panel(page).getByRole("slider", { name: /^音乐音量/ });
  await expect(volume).toBeEnabled();
  expect(await volume.evaluate(element => ({ appearance: getComputedStyle(element).appearance, minHeight: getComputedStyle(element).minHeight }))).toEqual({ appearance: "none", minHeight: "44px" });
  await volume.focus();
  await page.keyboard.press("ArrowRight");
  await expect(panel(page).locator("output")).toHaveText("16%");
  await shot(page, info, "06-settings-music");
  await panel(page).getByRole("switch", { name: /^播放小镇环境音乐/ }).uncheck();
  await panel(page).getByRole("tab", { name: "界面", exact: true }).click();
  await scale.selectOption("2");
  await shell(page, "设置", town, baseline);
  await reachable(scale);
  await shot(page, info, "06-settings-200-percent");
  await scale.selectOption("1");
  await page.keyboard.press("Escape");
  await expect(panel(page)).toBeHidden();
  await sameCanvas(page, canvas);

  await approach(page, "现在开始阅读", "ch1-lesson");
  await expect(panel(page).locator(".pdf-reader")).toHaveAttribute("aria-busy", "false", { timeout: 60_000 });
  await shell(page, "资料阅读", lessonCards, baseline);
  await pdfFitsWithoutLeftScroll(page);
  await expect(panel(page).getByRole("tab", { name: "学习目标", exact: true })).toHaveAttribute("aria-selected", "true");
  await adjustLearningLayout(page);
  await assistantTab(page, "图书管理员");
  await shot(page, info, "07-story-pdf-reading");
  await suspendActivity(page);
  await setScale(page, "2");
  await approach(page, "继续阅读", "ch1-lesson");
  await expect(panel(page).locator(".pdf-reader")).toHaveAttribute("aria-busy", "false", { timeout: 60_000 });
  await shell(page, "资料阅读", lessonCards, baseline);
  await pdfFitsWithoutLeftScroll(page);
  await reachable(panel(page).getByRole("button", { name: "下一页 PDF", exact: true }));
  await assistantTab(page, "图书管理员");
  await shot(page, info, "07-assistant-200-percent");
  await panel(page).getByRole("tab", { name: "学习目标", exact: true }).click();
  const required = setup.lesson.material.pages.filter(value => setup.lesson.completion.required_page_ids.includes(value.id));
  expect(required.map(value => value.page_no)).toEqual([34, 42, 43]);
  for (const value of required) {
    await panel(page).getByRole("button", { name: new RegExp(`阅读第\\s*${value.page_no}\\s*页`) }).click();
    await expect.poll(async () => (await setup.readLesson()).progress.page_no).toBe(value.page_no);
    await pdfFitsWithoutLeftScroll(page);
  }
  await expect.poll(async () => (await setup.readLesson()).progress.completed).toBe(true);
  const completeReading = panel(page).getByRole("button", { name: "完成阅读", exact: true });
  await expect(completeReading).toBeEnabled();
  await reachable(completeReading);
  await noHorizontalOverflow(page);
  await shot(page, info, "07-reading-200-percent");
  const readingReturn = page.waitForResponse(candidate => /\/api\/story\/sessions\/[^/]+\/return$/.test(candidate.url()) && candidate.request().method() === "POST");
  await completeReading.click();
  await json<Story>(await readingReturn);
  expect((await finishDialogue(page)).status).toBe("ended");
  await setScale(page, "1");

  await approach(page, "开始练习", "ch1-practice");
  await expect(panel(page).locator(".question-body")).toBeVisible();
  await shell(page, "练习", practiceCards, baseline);
  await assistantTab(page, "练习场教练");
  await panel(page).getByRole("tab", { name: "学习目标", exact: true }).click();
  const radios = panel(page).getByRole("radio");
  expect(await radios.count()).toBeGreaterThan(1);
  await radios.first().check();
  expect(await radios.first().evaluate(element => ({ appearance: getComputedStyle(element).appearance, background: getComputedStyle(element).backgroundColor, radius: getComputedStyle(element).borderRadius, width: getComputedStyle(element).width })))
    .toEqual({ appearance: "none", background: "rgb(40, 75, 67)", radius: "50%", width: "20px" });
  await radios.first().focus();
  await page.keyboard.press("ArrowDown");
  await expect(radios.nth(1)).toBeChecked();
  await expect(radios.first()).not.toBeChecked();
  const draftAnswer = await radios.nth(1).inputValue();
  await shot(page, info, "08-practice");
  await suspendActivity(page);
  await setScale(page, "2");
  await approach(page, "继续练习", "ch1-practice");
  await expect(panel(page).locator(".question-body")).toBeVisible();
  await shell(page, "练习", practiceCards, baseline);
  await expect(panel(page).locator('input[type="radio"]:checked')).toHaveValue(draftAnswer);
  await reachable(panel(page).getByRole("radio").nth(1));
  await reachable(panel(page).getByRole("button", { name: "提交答案", exact: true }));
  await assistantTab(page, "练习场教练");
  await noHorizontalOverflow(page);
  await shot(page, info, "08-practice-200-percent");
  await sameCanvas(page, canvas);
  await suspendActivity(page);
  const status = await json<{ model_calls: number }>(await request.get("/api/assistant/status", { headers: setup.student }));
  expect(status.model_calls).toBe(0);
  expect(modelRequests).toEqual([]);
  expect(errors).toEqual([]);
});
