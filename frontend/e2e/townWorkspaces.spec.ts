import { importCourse } from "./courseFixture";
import { test, expect, type APIRequestContext, type Page, type APIResponse, type TestInfo } from "@playwright/test";
import { randomUUID } from "node:crypto";

interface ClassInfo { id: number; name: string; join_code: string; }
interface Identity { access_token: string; user: { id: number; name: string; student_id: string }; }
interface WorldInfo {
  npcs: { key: string; map_key: string; tile_x: number; tile_y: number }[];
  quests: { id: string; nodes: { id: string; status: string }[] }[];
}
interface StoryInfo { id: string; revision: number; status: string; }
type Header = { Authorization: string };
declare global {
  interface Window {
    __townWorkspaceAudios: HTMLAudioElement[];
    __townWorkspaceCanvas: HTMLCanvasElement | null;
  }
}

async function json<T>(response: APIResponse): Promise<T> {
  expect(response.ok(), await response.text()).toBeTruthy();
  return response.json() as Promise<T>;
}

async function prepare(request: APIRequestContext, purpose: string) {
  const student_id = process.env.E2E_TEACHER_STUDENT_ID, password = process.env.E2E_TEACHER_PASSWORD;
  test.skip(!student_id || !password || process.env.E2E_COURSE_MEDIA_IMPORTED !== "1",
    "Requires an isolated deployment with the real course media imported and explicit teacher credentials.");
  const login = await json<Identity>(await request.post("/api/auth/login", { data: { student_id, password } }));
  const teacher: Header = { Authorization: `Bearer ${login.access_token}` };
  const createClass = async (label: string, chapters: number[]) => {
    const classroom = await json<ClassInfo>(await request.post("/api/classrooms", {
      headers: teacher, data: { name: `${purpose}${label} ${randomUUID().slice(0, 6)}` },
    }));
    const ids = await importCourse(request, teacher, classroom.id);
    for (const chapter of chapters) {
      await json(await request.put(`/api/teacher/classrooms/${classroom.id}/releases/${ids.chapter[String(chapter)]}`, {
        headers: teacher, data: { status: "published" },
      }));
    }
    return { ...classroom, ids };
  };
  const first = await createClass("主班", [179, 180, 181, 182, 183, 184, 185]);
  const second = await createClass("新班", [179]);
  const identity = await json<Identity>(await request.post("/api/auth/register", { data: {
    name: "小镇界面验收学生", student_id: BigInt('0x' + randomUUID().replaceAll('-', '').slice(0, 24)).toString(), password: `Resident-${randomUUID()}`,
  } }));
  const student: Header = { Authorization: `Bearer ${identity.access_token}` };
  for (const classroom of [first, second]) await json(await request.post("/api/classrooms/join", {
    headers: student, data: { join_code: classroom.join_code },
  }));
  const world = await json<WorldInfo>(await request.get(`/api/world?classroom_id=${first.id}`, { headers: student }));
  const mayor = world.npcs.find(npc => npc.key === first.ids.npc.mayor_py)!;
  await json(await request.put("/api/world/position", { headers: student, data: {
    classroom_id: first.id, map_key: mayor.map_key, tile_x: mayor.tile_x, tile_y: mayor.tile_y, direction: "down",
  } }));
  let story = await json<StoryInfo>(await request.post("/api/story/interactions", {
    headers: student, data: { classroom_id: first.id, npc_key: mayor.key },
  }));
  for (let step = 0; step < 8 && story.status === "active"; step++) {
    story = await json<StoryInfo>(await request.post(`/api/story/sessions/${story.id}/advance`, {
      headers: student, data: { classroom_id: first.id, event_id: randomUUID(), revision: story.revision },
    }));
  }
  expect(story.status).toBe("ended");
  const updated = await json<WorldInfo>(await request.get(`/api/world?classroom_id=${first.id}`, { headers: student }));
  expect(updated.quests.find(quest => quest.id === first.ids.quest.arrival)!.nodes.filter(node => node.status === "complete")).toHaveLength(1);
  return { first, second, identity, student, teacherName: login.user.name, createClass };
}

async function enter(page: Page, identity: Identity, classroom: ClassInfo, audio = false) {
  await page.addInitScript(({ token, userId, classroomId, audio }) => {
    sessionStorage.setItem("pythontopia-reborn-session", token);
    sessionStorage.setItem(`classroom:${userId}`, String(classroomId));
    localStorage.setItem("pythontopia-reborn-interface", JSON.stringify({ textScale: 1, reduceMotion: true, musicVolume: 0.15 }));
    if (audio) {
      window.__townWorkspaceAudios = [];
      window.Audio = new Proxy(window.Audio, {
        construct(target, args, newTarget) {
          const player = Reflect.construct(target, args, newTarget) as HTMLAudioElement;
          window.__townWorkspaceAudios.push(player);
          return player;
        },
      });
    }
  }, { token: identity.access_token, userId: identity.user.id, classroomId: classroom.id, audio });
  await page.goto("/world");
  const welcome = page.getByRole("dialog", { name: "欢迎回到小镇", exact: true });
  await expect(welcome).toBeVisible();
  await welcome.getByRole("button", { name: "按任意键继续", exact: true }).click();
  await expect(welcome).toBeHidden();
  await expect(page.locator(".hud-dock")).toBeVisible();
  await expect(page.locator(".world-canvas canvas")).toHaveCount(1);
  await page.evaluate(() => { window.__townWorkspaceCanvas = document.querySelector<HTMLCanvasElement>(".world-canvas canvas"); });
}

const workspace = (page: Page) => page.getByRole("dialog", { name: "游戏工作区", exact: true });
async function sameCanvas(page: Page) {
  expect(await page.evaluate(() => window.__townWorkspaceCanvas === document.querySelector(".world-canvas canvas"))).toBe(true);
  await expect(page.locator(".world-canvas canvas")).toHaveCount(1);
}
async function nearFullPage(page: Page) {
  const book = await workspace(page).locator(".town-book").boundingBox();
  expect(book).not.toBeNull();
  const viewport = page.viewportSize()!;
  expect(book!.width / viewport.width).toBeGreaterThanOrEqual(0.85);
  expect(book!.height / viewport.height).toBeGreaterThanOrEqual(0.75);
}
async function noHorizontalOverflow(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
  expect(await workspace(page).locator(".town-content").evaluate(element => element.scrollWidth <= element.clientWidth + 1)).toBe(true);
}
async function reducedViewportChecks(page: Page, testInfo: TestInfo, name: string) {
  for (const viewport of [{ width: 1366, height: 768 }, { width: 980, height: 760 }]) {
    await page.setViewportSize(viewport);
    await noHorizontalOverflow(page);
    await page.screenshot({ path: testInfo.outputPath(`${name}-${viewport.width}.png`) });
  }
  await page.setViewportSize({ width: 1920, height: 1080 });
}
async function noModelCalls(request: APIRequestContext, student: Header) {
  expect((await json<{ model_calls: number }>(await request.get("/api/assistant/status", { headers: student }))).model_calls).toBe(0);
}

test("town library fills the workspace and preserves chapter search and filters across reading", async ({ page, request }, testInfo) => {
  test.setTimeout(180_000);
  const setup = await prepare(request, "图书馆");
  await enter(page, setup.identity, setup.first);
  await page.getByRole("button", { name: "学习资料", exact: true }).click();
  const panel = workspace(page);
  await expect(panel.getByRole("heading", { level: 2, name: "小镇图书馆", exact: true })).toBeVisible();
  await expect(panel.locator("h2")).toHaveCount(1);
  await expect(panel).not.toContainText("留一页，慢慢读");
  await expect(panel.locator(".library-chapter")).toHaveCount(7);
  await expect(panel.locator(".library-card")).toHaveCount(41);
  await nearFullPage(page);
  await page.screenshot({ path: testInfo.outputPath("town-library-1920.png") });
  const firstChapter = panel.getByRole("navigation", { name: "资料章节" }).getByRole("button", { name: /第一章/ });
  await firstChapter.click();
  await expect(panel.locator(".library-card")).toHaveCount(4);
  const filters = panel.getByRole("group", { name: "资料类型" });
  await filters.getByRole("button", { name: "视频", exact: true }).click();
  await expect(panel.locator(".library-card")).toHaveCount(3);
  await filters.getByRole("button", { name: "课件", exact: true }).click();
  await expect(panel.locator(".library-card")).toHaveCount(1);
  await panel.getByLabel("搜索资料").fill("计 算");
  await expect(panel.locator(".library-card")).toHaveCount(1);
  await panel.getByLabel("搜索资料").fill("不存在的资料检索");
  await expect(panel.locator(".library-card")).toHaveCount(0);
  await expect(panel.getByText("没有找到匹配的资料", { exact: true })).toBeVisible();
  await panel.getByRole("button", { name: "清除筛选", exact: true }).click();
  await expect(panel.locator(".library-card")).toHaveCount(41);
  await firstChapter.click();
  await filters.getByRole("button", { name: "课件", exact: true }).click();
  await panel.getByLabel("搜索资料").fill("计算");
  await panel.locator(".library-card").click();
  await expect(panel.locator(".pdf-reader")).toHaveAttribute("aria-busy", "false", { timeout: 60_000 });
  await expect(panel.locator("#pdf-reading-position")).toContainText("45");
  await sameCanvas(page);
  await page.keyboard.press("Escape");
  await expect(panel.locator(".library-workspace")).toBeVisible();
  await expect(panel.getByLabel("搜索资料")).toHaveValue("计算");
  await expect(firstChapter).toHaveAttribute("aria-pressed", "true");
  await expect(filters.getByRole("button", { name: "课件", exact: true })).toHaveAttribute("aria-pressed", "true");
  await panel.getByLabel("搜索资料").fill("");
  await panel.getByRole("navigation", { name: "资料章节" }).getByRole("button", { name: /全部章节/ }).click();
  await filters.getByRole("button", { name: "全部资料", exact: true }).click();
  await reducedViewportChecks(page, testInfo, "town-library");
  for (let step = 0; step < 9; step++) {
    await page.keyboard.press("Tab");
    expect(await panel.evaluate(element => element.contains(document.activeElement))).toBe(true);
  }
  await sameCanvas(page);
  await page.keyboard.press("Escape");
  await expect(panel).toBeHidden();
  await page.getByRole("button", { name: "我的班级", exact: true }).click();
  await expect(panel.locator(".classroom-workspace")).toBeVisible();
  await sameCanvas(page);
  await page.keyboard.press("Escape");
  await page.getByRole("button", { name: "打开设置", exact: true }).click();
  await expect(panel.locator(".settings-workspace")).toBeVisible();
  await sameCanvas(page);
  await page.keyboard.press("Escape");
  await expect(panel).toBeHidden();
  await noModelCalls(request, setup.student);
});

test("my classroom shows verified progress and safely switches or joins real classrooms", async ({ page, request }, testInfo) => {
  test.setTimeout(150_000);
  const setup = await prepare(request, "班级");
  await enter(page, setup.identity, setup.first);
  await page.getByRole("button", { name: "我的班级", exact: true }).click();
  const panel = workspace(page), navigation = panel.getByRole("navigation", { name: "班级目录" });
  await expect(panel.getByRole("heading", { level: 2, name: "我的班级", exact: true })).toBeVisible();
  await expect(panel.locator("h2")).toHaveCount(1);
  await expect(panel.locator(".current-classroom h3")).toHaveText(setup.first.name);
  await expect(panel.locator(".current-classroom")).toContainText(setup.teacherName);
  await expect(panel.locator(".classroom-progress-caption strong")).toHaveText("1 / 8 步");
  await nearFullPage(page);
  await page.screenshot({ path: testInfo.outputPath("my-classroom-1920.png") });
  await reducedViewportChecks(page, testInfo, "my-classroom");
  await sameCanvas(page);
  await navigation.getByRole("button", { name: "切换班级", exact: true }).click();
  await panel.getByRole("button", { name: `切换到：${setup.second.name}`, exact: true }).click();
  await expect(panel.locator(".current-classroom h3")).toHaveText(setup.second.name);
  await expect(panel.locator(".classroom-progress-caption strong")).toHaveText("0 / 8 步");
  await expect(page.locator(".world-canvas canvas")).toHaveCount(1);
  await navigation.getByRole("button", { name: "切换班级", exact: true }).click();
  await panel.getByRole("button", { name: `切换到：${setup.first.name}`, exact: true }).click();
  await expect(panel.locator(".current-classroom h3")).toHaveText(setup.first.name);
  await expect(panel.locator(".classroom-progress-caption strong")).toHaveText("1 / 8 步");
  await navigation.getByRole("button", { name: "加入班级", exact: true }).click();
  await panel.getByLabel("班级加入码", { exact: true }).fill("invalid-town-code");
  const invalidJoin = page.waitForResponse(response => response.url().endsWith("/api/classrooms/join") && response.request().method() === "POST");
  await panel.locator(".classroom-join-form").getByRole("button", { name: "加入班级", exact: true }).click();
  expect((await invalidJoin).status()).toBe(404);
  await expect(panel.getByRole("alert")).toBeVisible();
  const third = await setup.createClass("加入班", [179]);
  await panel.getByLabel("班级加入码", { exact: true }).fill(third.join_code);
  await panel.locator(".classroom-join-form").getByRole("button", { name: "加入班级", exact: true }).click();
  await expect(panel.getByRole("status")).toContainText("已加入班级");
  await panel.getByRole("button", { name: `切换到：${third.name}`, exact: true }).click();
  await expect(panel.locator(".current-classroom h3")).toHaveText(third.name);
  await expect(panel.locator(".classroom-progress-caption strong")).toHaveText("0 / 8 步");
  const preserved = await json<WorldInfo>(await request.get(`/api/world?classroom_id=${setup.first.id}`, { headers: setup.student }));
  expect(preserved.quests[0]!.nodes.filter(node => node.status === "complete")).toHaveLength(1);
  await page.screenshot({ path: testInfo.outputPath("joined-classroom-1920.png") });
  await page.keyboard.press("Escape");
  await expect(panel).toBeHidden();
  await noModelCalls(request, setup.student);
});

test("categorized settings update the real music player and keep about information inside the workspace", async ({ page, request }, testInfo) => {
  test.setTimeout(150_000);
  const setup = await prepare(request, "设置");
  await enter(page, setup.identity, setup.first, true);
  await page.getByRole("button", { name: "打开设置", exact: true }).click();
  const panel = workspace(page);
  await expect(panel.getByRole("heading", { level: 2, name: "设置", exact: true })).toBeVisible();
  await expect(panel.locator("h2")).toHaveCount(1);
  await expect(panel.getByRole("tab").first()).toHaveText("账户");
  await expect(panel.getByRole("tab", { name: "账户", exact: true })).toHaveAttribute("aria-selected", "true");
  await expect(panel.getByRole("tabpanel")).toContainText(setup.identity.user.student_id);
  await nearFullPage(page);
  await page.screenshot({ path: testInfo.outputPath("settings-account-1920.png") });
  await panel.getByRole("tab", { name: "账户", exact: true }).focus();
  await page.keyboard.press("ArrowDown");
  await expect(panel.getByRole("tab", { name: "界面", exact: true })).toBeFocused();
  const prose = panel.locator(".setting-description").first();
  const font = await prose.evaluate(element => Number.parseFloat(getComputedStyle(element).fontSize));
  await panel.getByLabel(/^文字大小/).selectOption("2");
  await expect.poll(() => prose.evaluate(element => Number.parseFloat(getComputedStyle(element).fontSize))).toBeGreaterThanOrEqual(font * 1.9);
  await noHorizontalOverflow(page);
  await page.screenshot({ path: testInfo.outputPath("settings-interface-200-percent.png") });
  await panel.getByLabel(/^文字大小/).selectOption("1");
  await panel.getByRole("switch", { name: /^减少界面动效/ }).uncheck();
  await panel.getByRole("switch", { name: /^减少界面动效/ }).check();
  await expect(page.locator("html")).toHaveAttribute("data-reduced-motion", "true");
  await panel.getByRole("tab", { name: "音乐", exact: true }).click();
  const volume = panel.getByRole("slider", { name: /^音乐音量/ });
  await expect(volume).toBeDisabled();
  await panel.getByRole("switch", { name: /^播放小镇环境音乐/ }).check();
  await expect(volume).toBeEnabled();
  await volume.evaluate(element => {
    (element as HTMLInputElement).value = "0.62";
    element.dispatchEvent(new Event("input", { bubbles: true }));
  });
  await expect(panel.locator("output")).toHaveText("62%");
  await expect.poll(() => page.evaluate(() => window.__townWorkspaceAudios.at(-1)?.volume)).toBeCloseTo(0.62, 2);
  await expect.poll(() => page.evaluate(() => window.__townWorkspaceAudios.at(-1)?.paused)).toBe(false);
  await expect.poll(() => page.evaluate(() => window.__townWorkspaceAudios.at(-1)?.currentTime ?? 0)).toBeGreaterThan(0);
  await page.screenshot({ path: testInfo.outputPath("settings-music-1920.png") });
  await panel.getByRole("tab", { name: "关于", exact: true }).click();
  await expect(panel.getByRole("tabpanel")).toContainText("Super Retro World");
  await expect(panel.getByRole("tabpanel")).toContainText("Alethicode");
  await expect(panel.locator('a[href="/credits"]')).toHaveCount(0);
  await expect(panel.getByRole("button", { name: "班级与课程", exact: true })).toHaveCount(0);
  await expect(panel.getByRole("button", { name: "已开放材料", exact: true })).toHaveCount(0);
  await expect(page).toHaveURL(/\/world$/);
  await page.screenshot({ path: testInfo.outputPath("settings-about-1920.png") });
  await panel.getByRole("tab", { name: "界面", exact: true }).click();
  await reducedViewportChecks(page, testInfo, "settings-interface");
  for (let step = 0; step < 8; step++) {
    await page.keyboard.press("Tab");
    expect(await panel.evaluate(element => element.contains(document.activeElement))).toBe(true);
  }
  await sameCanvas(page);
  await page.keyboard.press("Escape");
  await expect(panel).toBeHidden();
  await noModelCalls(request, setup.student);
});
