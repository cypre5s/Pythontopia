import { test, expect, type APIRequestContext, type APIResponse, type Page, type Response } from "@playwright/test";
import { randomUUID } from "node:crypto";

type Headers = { Authorization: string };
interface Identity { access_token: string; user: { id: number; name: string; dev_tools_enabled: boolean }; }
interface Classroom { id: number; name: string; join_code: string; }
interface World {
  player: { map_key: string; tile_x: number; tile_y: number };
  flags: string[];
  npcs: { key: string; map_key: string; tile_x: number; tile_y: number }[];
  quests: { id: string; kind: "main" | "side"; nodes: { id: string; status: string }[] }[];
}
interface Story { id: string; revision: number; status: string; }
interface Activity {
  id: string;
  material?: { id: number; pages: { id: number; page_no: number }[] };
  completion?: { required_page_ids: number[] };
  questions: { id: string; kind: string; options?: string[] }[];
}
interface Progress {
  members: { id: number; completed_nodes: number; submissions: number; records: number; completed_chapters: number }[];
  events: { id: string; user_id: number }[];
  submissions: { id: string; user_id: number; status: string }[];
}
interface Evidence { submission: string; assistant: string; question: string; material: number; page: number; }
interface Cache { local: Record<string, string>; session: Record<string, string>; }
interface ResetResult { ok: boolean; user_id: number; classroom_id: number; deleted: Record<string, number>; }

const endpoint = (base: string, path: string) => new URL(`/api${path}`, base).href;
const headers = (identity: Identity): Headers => ({ Authorization: `Bearer ${identity.access_token}` });
const panel = (page: Page) => page.locator(".overlay-host.game-workspace");
const welcome = (page: Page) => page.getByRole("dialog", { name: "欢迎回到小镇", exact: true });
const dialogue = (page: Page) => page.getByRole("dialog", { name: "剧情对白", exact: true });

async function json<T>(response: APIResponse | Response): Promise<T> {
  expect(response.ok(), await response.text()).toBeTruthy();
  return response.json() as Promise<T>;
}

function client(request: APIRequestContext, base: string, identity: Identity) {
  const authorization = headers(identity);
  return {
    get: <T>(path: string) => request.get(endpoint(base, path), { headers: authorization }).then(json<T>),
    post: <T>(path: string, data: unknown) => request.post(endpoint(base, path), { headers: authorization, data }).then(json<T>),
    put: <T>(path: string, data: unknown) => request.put(endpoint(base, path), { headers: authorization, data }).then(json<T>),
  };
}

async function register(request: APIRequestContext, base: string, label: string) {
  const credentials = { name: label, student_id: BigInt('0x' + randomUUID().replaceAll('-', '').slice(0, 24)).toString(), password: `Resident-${randomUUID()}` };
  const identity = await json<Identity>(await request.post(endpoint(base, "/auth/register"), { data: credentials }));
  return { identity, credentials };
}

async function createClass(request: APIRequestContext, base: string, teacher: Identity, label: string) {
  const api = client(request, base, teacher);
  const classroom = await api.post<Classroom>("/classrooms", { name: `开发工具验收${label} ${randomUUID().slice(0, 8)}` });
  await api.put(`/teacher/classrooms/${classroom.id}/releases/179`, { status: "published" });
  return classroom;
}

/** Real public requests create participation and a graded submission; no store/DB progress injection. */
async function establishEvidence(request: APIRequestContext, base: string, identity: Identity, classroom: Classroom): Promise<Evidence> {
  const api = client(request, base, identity);
  await api.post("/classrooms/join", { join_code: classroom.join_code });
  const world = await api.get<World>(`/world?classroom_id=${classroom.id}`);
  const mayor = world.npcs.find(npc => npc.key === "mayor_py")!;
  await api.put("/world/position", { classroom_id: classroom.id, map_key: mayor.map_key, tile_x: mayor.tile_x, tile_y: mayor.tile_y, direction: "down" });
  let story = await api.post<Story>("/story/interactions", { classroom_id: classroom.id, npc_key: mayor.key });
  for (let step = 0; step < 8 && story.status === "active"; step++) {
    story = await api.post<Story>(`/story/sessions/${story.id}/advance`, { classroom_id: classroom.id, revision: story.revision, event_id: randomUUID() });
  }
  expect(story.status).toBe("ended");
  const lesson = await api.get<Activity>(`/activities/ch1-lesson?classroom_id=${classroom.id}`);
  const page = lesson.material!.pages.find(value => lesson.completion!.required_page_ids.includes(value.id))!.page_no;
  await api.post("/activities/ch1-lesson/progress", { classroom_id: classroom.id, page_no: page });
  const practice = await api.get<Activity>(`/activities/ch1-practice?classroom_id=${classroom.id}`);
  const question = practice.questions.find(value => value.kind === "single_choice" && value.options?.includes("抽象与自动化"))!;
  expect(question).toBeDefined();
  const submission = await api.post<{ id: string; status: string }>("/submissions", {
    classroom_id: classroom.id, activity_id: practice.id, question_id: question.id, mode: "submit", answer: "抽象与自动化", idempotency_key: randomUUID(),
  });
  expect(submission.status).toBe("accepted");
  // Empty real assistant sessions exercise restore-pointer cleanup and audit preservation without a model call.
  const assistant = await api.post<{ id: string }>("/assistant/sessions", { classroom_id: classroom.id, activity_id: lesson.id });
  return { submission: submission.id, assistant: assistant.id, question: question.id, material: lesson.material!.id, page };
}

function cache(identity: Identity, classroom: Classroom, evidence: Evidence): Cache {
  const scope = `${identity.user.id}:${classroom.id}`;
  return {
    local: { [`draft:${scope}:${evidence.question}`]: JSON.stringify({ code: "", answer: "抽象与自动化", submission_id: evidence.submission }) },
    session: {
      [`read:${scope}:material-${evidence.material}`]: "1",
      [`read:${scope}:material-${evidence.material}:pdf`]: String(evidence.page),
      [`read:${scope}:material-${evidence.material}:video`]: "12",
      [`assistant:${scope}:ch1-lesson`]: evidence.assistant,
      [`library-view:${scope}`]: JSON.stringify({ query: "计算", type: "lesson", chapter: 179, scroll: 85 }),
      [`library-scroll:${scope}`]: "85",
    },
  };
}

async function enter(page: Page, base: string, identity: Identity, classroom: Classroom, caches: Cache[]) {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.addInitScript(({ identity, classroom, caches }) => {
    // Seed only scoped browser restore fixtures once; a genuine reload must not reintroduce cleared caches.
    if (sessionStorage.getItem("e2e-dev-tools-initialized")) return;
    sessionStorage.setItem("e2e-dev-tools-initialized", "1");
    sessionStorage.setItem("pythontopia-reborn-session", identity.access_token);
    sessionStorage.setItem(`classroom:${identity.user.id}`, String(classroom.id));
    localStorage.setItem("pythontopia-reborn-interface", JSON.stringify({ textScale: 1, reduceMotion: true, musicVolume: 0.42 }));
    for (const saved of caches) {
      for (const [key, value] of Object.entries(saved.local)) localStorage.setItem(key, value);
      for (const [key, value] of Object.entries(saved.session)) sessionStorage.setItem(key, value);
    }
  }, { identity, classroom, caches });
  await page.goto(new URL("/world", base).href);
  await expect(welcome(page)).toBeVisible();
  await welcome(page).getByRole("button", { name: "按任意键继续", exact: true }).click();
  await expect(welcome(page)).toBeHidden();
  await expect(page.locator(".world-canvas canvas")).toHaveCount(1);
}

async function inspectCache(page: Page, saved: Cache) {
  return page.evaluate(saved => ({
    local: Object.fromEntries(Object.keys(saved.local).map(key => [key, localStorage.getItem(key)])),
    session: Object.fromEntries(Object.keys(saved.session).map(key => [key, sessionStorage.getItem(key)])),
  }), saved);
}

async function setTextScale(page: Page, value: "1" | "2") {
  await page.locator(".hud-dock").getByRole("button", { name: "打开设置", exact: true }).click();
  await panel(page).getByRole("tab", { name: "界面", exact: true }).click();
  await panel(page).getByLabel(/^文字大小/).selectOption(value);
  await expect.poll(() => page.locator("html").evaluate(element => getComputedStyle(element).getPropertyValue("--text-scale").trim())).toBe(value);
  await page.keyboard.press("Escape");
  await expect(panel(page)).toBeHidden();
}

test("development reset clears only the confirmed student's selected classroom and re-enters arrival", async ({ page, request }, info) => {
  test.setTimeout(180_000);
  const base = process.env.E2E_URL, student_id = process.env.E2E_TEACHER_STUDENT_ID, password = process.env.E2E_TEACHER_PASSWORD;
  test.skip(process.env.E2E_DEV_TOOLS !== "1" || !base || !student_id || !password,
    "Explicit E2E_DEV_TOOLS=1, isolated development E2E_URL and teacher credentials are required; never use a demo account.");
  const target = base!;
  const errors: string[] = [], modelMessages: string[] = [], resetRequests: string[] = [];
  page.on("pageerror", error => errors.push(error.message));
  page.on("request", outgoing => {
    if (outgoing.method() !== "POST") return;
    if (/\/api\/assistant\/sessions\/[^/]+\/messages$/.test(outgoing.url())) modelMessages.push(outgoing.url());
    if (outgoing.url().endsWith("/api/dev/progress/reset")) resetRequests.push(outgoing.url());
  });
  const teacher = await json<Identity>(await request.post(endpoint(target, "/auth/login"), { data: { student_id, password } }));
  expect(teacher.user.dev_tools_enabled).toBe(false);
  const first = await createClass(request, target, teacher, "当前班"), second = await createClass(request, target, teacher, "对照班");
  const { identity } = await register(request, target, "开发工具验收本人");
  const { identity: other } = await register(request, target, "开发工具验收对照学生");
  expect(identity.user.dev_tools_enabled).toBe(true);
  const mine = client(request, target, identity), peer = client(request, target, other), teaching = client(request, target, teacher);
  expect((await mine.get<Identity["user"]>("/auth/me")).dev_tools_enabled).toBe(true);
  const firstEvidence = await establishEvidence(request, target, identity, first);
  const secondEvidence = await establishEvidence(request, target, identity, second);
  const otherEvidence = await establishEvidence(request, target, other, first);
  const secondWorld = await mine.get<World>(`/world?classroom_id=${second.id}`), otherWorld = await peer.get<World>(`/world?classroom_id=${first.id}`);
  const secondProgress = await teaching.get<Progress>(`/teacher/classrooms/${second.id}/progress`);
  const firstProgress = await teaching.get<Progress>(`/teacher/classrooms/${first.id}/progress`);
  const otherMember = firstProgress.members.find(member => member.id === other.user.id)!;
  expect(firstProgress.submissions.filter(submission => submission.status === "accepted")).toHaveLength(2);
  expect(firstProgress.members.find(member => member.id === identity.user.id)).toMatchObject({ completed_nodes: 1, submissions: 1, records: 1 });
  const releases = await teaching.get(`/teacher/classrooms/${first.id}/releases`);
  const assistantAudit = await mine.get(`/assistant/sessions/${firstEvidence.assistant}`);
  const falseConfirmation = await request.post(endpoint(target, "/dev/progress/reset"), { headers: headers(identity), data: { classroom_id: first.id, confirmed: false } });
  expect(falseConfirmation.status()).toBe(422);
  const forbidden = await request.post(endpoint(target, "/dev/progress/reset"), { headers: headers(other), data: { classroom_id: second.id, confirmed: true } });
  expect(forbidden.status()).toBe(403);
  const teacherReset = await request.post(endpoint(target, "/dev/progress/reset"), { headers: headers(teacher), data: { classroom_id: first.id, confirmed: true } });
  expect(teacherReset.status()).toBe(403);

  const currentCache = cache(identity, first, firstEvidence), secondCache = cache(identity, second, secondEvidence), otherCache = cache(other, first, otherEvidence);
  info.annotations.push({ type: "cache-fixture", description: "Browser-only restore caches use real public evidence/session IDs. Learning progress and grades are created through genuine authenticated APIs; no Pinia/DB progress injection or assistant question is used." });
  await enter(page, target, identity, first, [currentCache, secondCache, otherCache]);
  const beforeToken = await page.evaluate(() => sessionStorage.getItem("pythontopia-reborn-session"));
  const preferences = await page.evaluate(() => localStorage.getItem("pythontopia-reborn-interface"));
  const dev = page.locator(".hud-dock").getByRole("button", { name: "开发工具", exact: true });
  await expect(dev).toBeVisible();
  await expect(page.locator(".hud-dock").getByRole("button").last()).toHaveAccessibleName("开发工具");
  await page.screenshot({ path: info.outputPath("dev-tools-menu-1920.png") });
  await setTextScale(page, "2");
  await dev.click();
  await expect(panel(page).locator(".game-workspace-title h2")).toBeInViewport({ ratio: 1 });
  const scaledStart = panel(page).getByRole("button", { name: "重新开始当前班级", exact: true });
  await scaledStart.scrollIntoViewIfNeeded();
  await expect(scaledStart).toBeInViewport({ ratio: 1 });
  await scaledStart.click();
  const scaledConfirm = panel(page).getByRole("button", { name: "确认重新开始", exact: true });
  await scaledConfirm.scrollIntoViewIfNeeded();
  await expect(scaledConfirm).toBeInViewport({ ratio: 0.99 });
  await expect(panel(page).getByRole("button", { name: "关闭当前工作区", exact: true })).toBeInViewport({ ratio: 1 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
  await page.screenshot({ path: info.outputPath("dev-tools-confirmation-200-percent-1920.png") });
  await panel(page).getByRole("button", { name: "取消", exact: true }).click();
  await page.keyboard.press("Escape");
  await setTextScale(page, "1");
  await dev.click();
  await expect(panel(page).locator(".game-workspace-title h2")).toHaveText("开发工具");
  await expect(panel(page)).toContainText(identity.user.name);
  await expect(panel(page)).toContainText(first.name);
  await page.screenshot({ path: info.outputPath("dev-tools-current-classroom-1920.png") });
  await panel(page).getByRole("button", { name: "重新开始当前班级", exact: true }).click();
  const confirm = panel(page).getByRole("button", { name: "确认重新开始", exact: true });
  await expect(confirm).toBeVisible();
  expect(resetRequests).toHaveLength(0);
  await page.screenshot({ path: info.outputPath("dev-tools-confirmation-1920.png") });

  // Revoke this new test membership to exercise a genuine server error, then restore it before retrying.
  await json(await request.delete(endpoint(target, `/teacher/classrooms/${first.id}/members/${identity.user.id}`), { headers: headers(teacher) }));
  const rejected = page.waitForResponse(candidate => candidate.url().endsWith("/api/dev/progress/reset") && candidate.request().method() === "POST");
  await confirm.click();
  expect((await rejected).status()).toBe(403);
  await expect(panel(page).getByRole("alert")).toContainText("只能重新开始已加入班级的学习");
  await expect(confirm).toBeEnabled();
  expect(await inspectCache(page, currentCache)).toEqual(currentCache);
  await page.screenshot({ path: info.outputPath("dev-tools-real-error-preserves-state-1920.png") });
  await mine.post("/classrooms/join", { join_code: first.join_code });
  expect((await mine.get<{ status: string }>(`/submissions/${firstEvidence.submission}?classroom_id=${first.id}`)).status).toBe("accepted");

  // Delay/proxy the real request to observe pending state. Capture the genuine response before success reloads the page.
  let release!: () => void, intercepted!: () => void;
  let result!: ResetResult;
  const gate = new Promise<void>(resolve => { release = resolve; }), reached = new Promise<void>(resolve => { intercepted = resolve; });
  await page.route("**/api/dev/progress/reset", async route => {
    intercepted();
    await gate;
    const actual = await route.fetch();
    result = await actual.json() as ResetResult;
    await route.fulfill({ response: actual });
  });
  const response = page.waitForResponse(candidate => candidate.url().endsWith("/api/dev/progress/reset") && candidate.request().method() === "POST");
  const confirming = (await confirm.elementHandle())!;
  await confirm.click();
  await reached;
  try {
    await expect.poll(() => confirming.evaluate(element => element.isConnected)).toBe(true);
    await expect.poll(() => confirming.isDisabled()).toBe(true);
    await expect(panel(page).locator(".dev-tools-workspace")).toBeVisible();
    await page.screenshot({ path: info.outputPath("dev-tools-pending-1920.png") });
  } finally { release(); }
  expect((await response).ok()).toBe(true);
  expect(result).toMatchObject({ ok: true, user_id: identity.user.id, classroom_id: first.id });
  for (const table of ["player_quests", "activity_progress", "submissions", "world_flags", "player_positions"])
    expect(result.deleted[table], table).toBeGreaterThan(0);
  expect(result.deleted).not.toHaveProperty("assistant_sessions");
  expect(result.deleted).not.toHaveProperty("assistant_calls");
  expect(resetRequests).toHaveLength(2);
  await page.unroute("**/api/dev/progress/reset");
  await expect(welcome(page)).toBeVisible({ timeout: 30_000 });
  await welcome(page).getByRole("button", { name: "按任意键继续", exact: true }).click();
  await expect(welcome(page)).toBeHidden();
  await expect(panel(page)).toBeHidden();
  await expect(page.locator(".world-canvas canvas")).toHaveCount(1);
  expect(await page.evaluate(token => sessionStorage.getItem("pythontopia-reborn-session") === token, beforeToken)).toBe(true);
  expect(await page.evaluate(id => sessionStorage.getItem(`classroom:${id}`), identity.user.id)).toBe(String(first.id));
  expect(await page.evaluate(() => localStorage.getItem("pythontopia-reborn-interface"))).toBe(preferences);
  const cleared = await inspectCache(page, currentCache);
  expect(Object.values(cleared.local).every(value => value === null)).toBe(true);
  expect(Object.values(cleared.session).every(value => value === null)).toBe(true);
  expect(await inspectCache(page, secondCache)).toEqual(secondCache);
  expect(await inspectCache(page, otherCache)).toEqual(otherCache);

  const restarted = await mine.get<World>(`/world?classroom_id=${first.id}`);
  expect(restarted.player).toMatchObject({ map_key: "town_hub", tile_x: 22, tile_y: 36 });
  expect(restarted.flags).toEqual([]);
  expect(restarted.quests.filter(quest => quest.kind === "main").map(quest => quest.id)).toEqual(["arrival"]);
  const arrival = restarted.quests.find(quest => quest.id === "arrival")!;
  expect(arrival.nodes).toHaveLength(8);
  expect(arrival.nodes.filter(node => node.status === "complete")).toHaveLength(0);
  expect(arrival.nodes.find(node => node.id === "welcome")!.status).toBe("ready");
  expect(await mine.get<World>(`/world?classroom_id=${second.id}`)).toEqual(secondWorld);
  expect(await peer.get<World>(`/world?classroom_id=${first.id}`)).toEqual(otherWorld);
  expect(await teaching.get<Progress>(`/teacher/classrooms/${second.id}/progress`)).toEqual(secondProgress);
  expect(await teaching.get(`/teacher/classrooms/${first.id}/releases`)).toEqual(releases);
  const after = await teaching.get<Progress>(`/teacher/classrooms/${first.id}/progress`);
  expect(after.members.find(member => member.id === identity.user.id)).toMatchObject({ completed_nodes: 0, submissions: 0, records: 0, completed_chapters: 0 });
  expect(after.members.find(member => member.id === other.user.id)).toEqual(otherMember);
  expect(after.submissions).toEqual(firstProgress.submissions.filter(submission => submission.user_id === other.user.id));
  expect(after.events).toEqual(firstProgress.events.filter(event => event.user_id === other.user.id));
  const memberships = await mine.get<Classroom[]>("/classrooms");
  expect(memberships.map(classroom => classroom.id).sort((a, b) => a - b)).toEqual([first.id, second.id].sort((a, b) => a - b));
  expect((await request.get(endpoint(target, `/submissions/${firstEvidence.submission}?classroom_id=${first.id}`), { headers: headers(identity) })).status()).toBe(404);
  expect(await mine.get(`/assistant/sessions/${firstEvidence.assistant}`)).toEqual(assistantAudit);
  expect((await mine.get<{ status: string }>(`/submissions/${secondEvidence.submission}?classroom_id=${second.id}`)).status).toBe("accepted");
  expect((await peer.get<{ status: string }>(`/submissions/${otherEvidence.submission}?classroom_id=${first.id}`)).status).toBe("accepted");
  await mine.get(`/assistant/sessions/${secondEvidence.assistant}`);
  await peer.get(`/assistant/sessions/${otherEvidence.assistant}`);
  await page.locator(".hud-dock").getByRole("button", { name: "任务日志", exact: true }).click();
  await panel(page).getByRole("combobox", { name: "任务类型", exact: true }).selectOption("main");
  await expect(panel(page).locator(".journal-objectives span")).toHaveText("0 / 8");
  await page.screenshot({ path: info.outputPath("dev-tools-reset-zero-progress-1920.png") });
  await page.keyboard.press("Escape");
  const interaction = page.waitForResponse(candidate => candidate.url().endsWith("/api/story/interactions") && candidate.request().method() === "POST");
  await page.locator(".quest-tracker").getByRole("button", { name: "立即前往", exact: true }).click();
  await json(await interaction);
  await expect(dialogue(page).locator(".dialogue-text")).toContainText("欢迎来到 Pythontopia");
  await page.screenshot({ path: info.outputPath("dev-tools-restarted-arrival-1920.png") });
  expect((await mine.get<{ model_calls: number }>("/assistant/status")).model_calls).toBe(0);
  expect(modelMessages).toEqual([]);
  expect(errors).toEqual([]);
});

test("production registration and login never expose the reset capability, menu or route", async ({ page, request }, info) => {
  test.setTimeout(90_000);
  const base = process.env.E2E_PRODUCTION_URL;
  const student_id = process.env.E2E_PRODUCTION_TEACHER_STUDENT_ID ?? process.env.E2E_TEACHER_STUDENT_ID;
  const password = process.env.E2E_PRODUCTION_TEACHER_PASSWORD ?? process.env.E2E_TEACHER_PASSWORD;
  test.skip(!base || !student_id || !password, "An explicit independent production QA URL and teacher credentials are required; only new test students/classrooms are used.");
  const target = base!;
  const teacher = await json<Identity>(await request.post(endpoint(target, "/auth/login"), { data: { student_id, password } }));
  expect(teacher.user.dev_tools_enabled).toBe(false);
  const classroom = await createClass(request, target, teacher, "生产关闭");
  const registered = await register(request, target, "生产能力关闭验收学生");
  expect(registered.identity.user.dev_tools_enabled).toBe(false);
  const identity = await json<Identity>(await request.post(endpoint(target, "/auth/login"), { data: { student_id: registered.credentials.student_id, password: registered.credentials.password } }));
  expect(identity.user.dev_tools_enabled).toBe(false);
  const api = client(request, target, identity);
  expect((await api.get<Identity["user"]>("/auth/me")).dev_tools_enabled).toBe(false);
  await api.post("/classrooms/join", { join_code: classroom.join_code });
  const before = await api.get<World>(`/world?classroom_id=${classroom.id}`);
  const denied = await request.post(endpoint(target, "/dev/progress/reset"), { headers: headers(identity), data: { classroom_id: classroom.id, confirmed: true } });
  expect(denied.status()).toBe(404);
  expect(await api.get<World>(`/world?classroom_id=${classroom.id}`)).toEqual(before);
  await enter(page, target, identity, classroom, []);
  await expect(page.locator(".hud-dock").getByRole("button", { name: "开发工具", exact: true })).toHaveCount(0);
  await page.locator(".hud-dock").getByRole("button", { name: "打开设置", exact: true }).click();
  await expect(panel(page)).not.toContainText("重新开始当前班级");
  await page.screenshot({ path: info.outputPath("production-no-dev-tools-1920.png") });
  expect((await api.get<{ model_calls: number }>("/assistant/status")).model_calls).toBe(0);
});
