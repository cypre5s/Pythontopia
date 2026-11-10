import { test, expect, type APIResponse } from "@playwright/test";
import { randomUUID } from "node:crypto";

async function json<T>(response: APIResponse): Promise<T> {
  expect(response.ok(), await response.text()).toBeTruthy();
  return response.json() as Promise<T>;
}

test("a chapter-free public entry opens one workspace and resumes its following dialogue", async ({ page, request }, info) => {
  test.setTimeout(90_000);
  const student_id = process.env.E2E_TEACHER_STUDENT_ID, password = process.env.E2E_TEACHER_PASSWORD;
  test.skip(!student_id || !password, "Explicit isolated teacher credentials required.");
  const teacher = await json<{ access_token: string }>(await request.post("/api/auth/login", { data: { student_id, password } }));
  const headers = { Authorization: `Bearer ${teacher.access_token}` };
  const classroom = await json<{ id: number; join_code: string }>(await request.post("/api/classrooms", { headers,
    data: { name: `公共流程验收 ${randomUUID().slice(0, 8)}` } }));
  await json(await request.post(`/api/teacher/classrooms/${classroom.id}/course/empty`, { headers,
    data: { confirmed: true, name: "无章节公共流程验收" } }));
  const endpoint = `/api/teacher/classrooms/${classroom.id}/authoring`;
  const authoring = await json<{ revision: number }>(await request.get(endpoint, { headers }));
  await json(await request.put(endpoint, { headers, data: {
    expected_revision: authoring.revision, quests: [],
    configuration: { world: { interactions: [
      { id: "welcome-region", label: "公共阅读入口", prompt: "查看公开资料", trigger: "enter",
        map_key: "town_hub", target: { region: { x: 23, y: 36, width: 2, height: 1 } } },
      { id: "library-arrival", label: "图书馆迎宾区域", prompt: "查看欢迎说明", trigger: "enter",
        map_key: "library", target: { region: { x: 9, y: 11, width: 1, height: 1 } } },
    ], npcs: {} }, buildings: { library: { enabled: true, default_unlocked: true, blocked_message: "图书馆目前关闭" } }, assistant: {}, editor: {} },
    flows: [{ id: "public-welcome", title: "无章节阅读入口", chapter_id: null, quest_id: null, content: {
      schema_version: 1, mode: "public", bindings: {},
      entries: { "interaction:welcome-region": [{ when: { always: true }, node: "greet" }] },
      nodes: [
        { id: "greet", type: "dialogue", text: "欢迎进入公共阅读区域。继续查看课程资料。", next: "open" },
        { id: "open", type: "action", action: "open_workspace", workspace: "library", next: "after" },
        { id: "after", type: "dialogue", speaker: "小镇向导", text: "阅读工作区已关闭，公共流程继续。", next: "done" },
        { id: "done", type: "end" },
      ],
    } }, { id: "library-arrival-flow", title: "图书馆迎宾", chapter_id: null, quest_id: null, content: {
      schema_version: 1, mode: "public", bindings: {},
      entries: { "interaction:library-arrival": [{ when: { always: true }, node: "greeting" }] },
      nodes: [{ id: "greeting", type: "dialogue", speaker: "图书馆", text: "欢迎进入图书馆，跨地图落点也会触发公共流程。", next: "done" }, { id: "done", type: "end" }],
    } }],
  } }));
  const identity = await json<{ access_token: string; user: { id: number } }>(await request.post("/api/auth/register", { data: {
    student_id: BigInt("0x" + randomUUID().replaceAll("-", "").slice(0, 24)).toString(), password: `Public-${randomUUID()}`, name: "公共流程验收居民",
  } }));
  const student = { Authorization: `Bearer ${identity.access_token}` };
  await json(await request.post("/api/classrooms/join", { headers: student, data: { join_code: classroom.join_code } }));
  const before = await json<{ quests: unknown[]; chapters: unknown[] }>(await request.get(`/api/world?classroom_id=${classroom.id}`, { headers: student }));
  expect(before.quests).toEqual([]);
  expect(before.chapters).toEqual([]);
  const errors: string[] = [], modelRequests: string[] = [], interactions: string[] = [];
  page.on("pageerror", error => errors.push(error.message));
  page.on("request", outgoing => {
    if (outgoing.method() !== "POST") return;
    if (/\/api\/assistant\/sessions\/[^/]+\/messages$/.test(outgoing.url())) modelRequests.push(outgoing.url());
    if (outgoing.url().endsWith("/api/story/interactions")) interactions.push(outgoing.url());
  });
  await page.addInitScript(({ token, userId, classroomId }) => {
    sessionStorage.setItem("pythontopia-reborn-session", token);
    sessionStorage.setItem(`classroom:${userId}`, String(classroomId));
  }, { token: identity.access_token, userId: identity.user.id, classroomId: classroom.id });
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/world");
  await page.getByRole("button", { name: "按任意键继续", exact: true }).click();
  const dialogue = page.getByRole("dialog", { name: "剧情对白", exact: true });
  await page.keyboard.down("d");
  try { await expect(dialogue).toBeVisible({ timeout: 15_000 }); }
  finally { await page.keyboard.up("d"); }
  await expect(dialogue).toContainText("欢迎进入公共阅读区域");
  await dialogue.locator(".dialogue-box").click();
  const workspace = page.getByRole("dialog", { name: "游戏工作区", exact: true });
  await expect(page.locator(".library-workspace")).toBeVisible();
  await expect(dialogue).toBeHidden();
  await expect(workspace).toHaveCount(1);
  await workspace.getByRole("button", { name: "关闭当前工作区", exact: true }).click();
  await expect(dialogue).toBeVisible();
  await expect(dialogue).toContainText("公共流程继续");
  await page.screenshot({ path: info.outputPath("public-flow-after-library.png") });
  await dialogue.locator(".dialogue-box").click();
  await expect(dialogue).toBeHidden();
  const current = await json(await request.get(`/api/story/current?classroom_id=${classroom.id}`, { headers: student }));
  expect(current).toBeNull();
  expect(interactions).toHaveLength(1);
  await page.getByRole("button", { name: "地图", exact: true }).click();
  const map = page.getByRole("dialog", { name: "地图工作区", exact: true });
  await map.getByRole("button", { name: "筛选建筑", exact: true }).click();
  await map.getByRole("button", { name: "选择图书馆", exact: true }).click();
  await map.getByRole("button", { name: "前往所选目标", exact: true }).click();
  await expect(dialogue).toBeVisible({ timeout: 30_000 });
  await expect(dialogue).toContainText("跨地图落点也会触发公共流程");
  await page.screenshot({ path: info.outputPath("public-flow-library-arrival.png") });
  await dialogue.locator(".dialogue-box").click();
  await expect(dialogue).toBeHidden();
  expect(interactions).toHaveLength(2);
  await page.reload();
  await page.getByRole("button", { name: "按任意键继续", exact: true }).click();
  await expect(page.locator(".hud-dock")).toBeVisible();
  expect(interactions).toHaveLength(2);
  await expect(dialogue).toBeHidden();
  expect(modelRequests).toEqual([]);
  expect(errors).toEqual([]);
  await expect(page.locator(".world-canvas canvas")).toHaveCount(1);
});
