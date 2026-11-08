import { test, expect, type APIResponse, type Page } from "@playwright/test";
import { randomUUID } from "node:crypto";

async function json<T>(response: APIResponse): Promise<T> {
  expect(response.ok(), await response.text()).toBeTruthy();
  return response.json() as Promise<T>;
}
type Identity = { access_token: string; user: { id: number } };
type Progress = { completed: boolean; read_count: number; read_total: number; watched_seconds: number; required_seconds: number };
const panel = (page: Page) => page.locator(".overlay-host.game-workspace");

// New teacher-owned side definitions and a new classroom/student only. The
// imported real course media are read, never replaced or published as new mains.
test("configured page ranges and real video playback drive authoritative goals", async ({ page, request }, info) => {
  test.setTimeout(150_000);
  const email = process.env.E2E_TEACHER_EMAIL, password = process.env.E2E_TEACHER_PASSWORD;
  test.skip(!email || !password || process.env.E2E_COURSE_MEDIA_IMPORTED !== "1", "Requires isolated QA with imported real media and explicit teacher credentials.");
  const tag = randomUUID().slice(0, 8), questId = `learning-${tag}`;
  const teacher = await json<Identity>(await request.post("/api/auth/login", { data: { email, password } }));
  const headers = { Authorization: `Bearer ${teacher.access_token}` };
  const classroom = await json<{ id: number; join_code: string }>(await request.post("/api/classrooms", { headers, data: { name: `学习目标验收 ${tag}` } }));
  await json(await request.put(`/api/teacher/classrooms/${classroom.id}/releases/179`, { headers, data: { status: "published" } }));
  const identity = await json<Identity>(await request.post("/api/auth/register", { data: { name: "目标验收居民", email: `learning-${randomUUID()}@example.test`, password: `Resident-${randomUUID()}` } }));
  const student = { Authorization: `Bearer ${identity.access_token}` };
  await json(await request.post("/api/classrooms/join", { headers: student, data: { join_code: classroom.join_code } }));
  const materials = await json<{ id: number; kind: string; chapter_id: number; duration_seconds: number | null }[]>(await request.get(`/api/library?classroom_id=${classroom.id}`, { headers: student }));
  const pdf = materials.find(material => material.id === 186)!;
  const video = materials.find(material => material.chapter_id === 179 && material.kind === "video" && (material.duration_seconds ?? 0) > 30)!;
  expect(pdf).toBeTruthy();
  expect(video).toBeTruthy();
  const activities = [
    { id: `read-${tag}`, title: `TEST 指定页阅读 ${tag}`, material_id: pdf.id, learning: { reading: { scope: "range", start_page: 34, end_page: 35 }, highlights: { pages: [{ start_page: 34, end_page: 34, label: "验收重点页" }] } } },
    { id: `watch-${tag}`, title: `TEST 有效视频 ${tag}`, material_id: video.id, learning: { video: { minimum_seconds: 3 }, highlights: { video: [{ start_seconds: 1, end_seconds: 4, label: "验收重点片段" }] } } },
  ];
  for (const activity of activities) {
    await json(await request.post("/api/teacher/activities", { headers, data: { ...activity, chapter_id: 179, kind: "lesson", npc_key: "librarian_doc", assistant_allowed: false } }));
  }
  const questTitle = `TEST 学习目标 ${tag}`;
  await json(await request.post("/api/teacher/quests", { headers, data: { definition: { id: questId, title: questTitle, chapter_id: 179, kind: "side", nodes: activities.map((activity, index) => ({ id: `step-${index}`, type: "STUDY_LESSON", title: activity.title, activity_id: activity.id, dependencies: [] })), effects: [] } } }));
  const progress = (activityId: string) => request.get(`/api/activities/${activityId}/progress?classroom_id=${classroom.id}`, { headers: student }).then(json<Progress>);
  const nodeStatus = async (id: string) => {
    const world = await json<{ quests: { id: string; nodes: { id: string; status: string }[] }[] }>(await request.get(`/api/world?classroom_id=${classroom.id}`, { headers: student }));
    return world.quests.find(quest => quest.id === questId)!.nodes.find(node => node.id === id)!.status;
  };
  const errors: string[] = [];
  page.on("pageerror", error => errors.push(error.message));
  await page.addInitScript(({ token, userId, classroomId }) => {
    sessionStorage.setItem("pythontopia-reborn-session", token);
    sessionStorage.setItem(`classroom:${userId}`, String(classroomId));
  }, { token: identity.access_token, userId: identity.user.id, classroomId: classroom.id });
  await page.goto("/world");
  await page.getByRole("button", { name: "按任意键继续", exact: true }).click();
  async function openActivity(title: string) {
    await page.locator(".hud-dock").getByRole("button", { name: "任务日志", exact: true }).click();
    await panel(page).getByRole("combobox", { name: "任务类型", exact: true }).selectOption("side");
    await panel(page).locator(".quest-card").filter({ hasText: questTitle }).click();
    await panel(page).locator(".journal-entry li").filter({ hasText: title }).getByRole("button", { name: "打开课程", exact: true }).click();
  }
  await openActivity(activities[0]!.title);
  await expect(panel(page).locator(".pdf-reader")).toHaveAttribute("aria-busy", "false", { timeout: 60_000 });
  await expect(panel(page).locator(".support-goal-progress")).toContainText("0 / 2");
  await panel(page).getByRole("button", { name: /阅读第34页/ }).click();
  await expect.poll(async () => (await progress(activities[0]!.id)).read_count).toBe(1);
  await panel(page).getByRole("tab", { name: "文档目录", exact: true }).click();
  await expect(panel(page).getByRole("navigation", { name: "文档页面目录", exact: true })).toContainText("验收重点页");
  await panel(page).getByRole("tab", { name: "学习目标", exact: true }).click();
  await panel(page).getByRole("button", { name: /阅读第35页/ }).click();
  await expect.poll(async () => (await progress(activities[0]!.id)).completed).toBe(true);
  await page.screenshot({ path: info.outputPath("range-reading-and-highlight.png"), animations: "disabled" });
  await panel(page).getByRole("button", { name: "完成阅读", exact: true }).click();
  await expect.poll(() => nodeStatus("step-0")).toBe("complete");
  await panel(page).getByRole("button", { name: "关闭当前工作区", exact: true }).click();
  await openActivity(activities[1]!.title);
  const media = panel(page).locator("video");
  await expect(media).toBeVisible();
  await expect.poll(() => media.evaluate(element => (element as HTMLVideoElement).readyState), { timeout: 30_000 }).toBeGreaterThanOrEqual(2);
  await expect(panel(page)).toContainText("验收重点片段");
  const started = page.waitForResponse(response => response.url().includes(`/activities/${activities[1]!.id}/video-progress`) && response.request().method() === "POST");
  await media.evaluate(async element => { const video = element as HTMLVideoElement; video.muted = true; await video.play(); });
  expect((await started).ok()).toBe(true);
  await media.evaluate(element => { const video = element as HTMLVideoElement; video.currentTime = Math.min(100, video.duration - 5); video.pause(); });
  await expect.poll(() => media.evaluate(element => (element as HTMLVideoElement).paused)).toBe(true);
  expect((await progress(activities[1]!.id)).completed).toBe(false);
  const watchStarted = Date.now();
  await media.evaluate(async element => { const video = element as HTMLVideoElement; video.currentTime = 0; video.playbackRate = 2; await video.play(); });
  await expect.poll(async () => (await progress(activities[1]!.id)).completed, { timeout: 30_000 }).toBe(true);
  await media.evaluate(element => (element as HTMLVideoElement).pause());
  const saved = await progress(activities[1]!.id);
  expect(saved.watched_seconds).toBeGreaterThanOrEqual(3);
  expect(saved.watched_seconds).toBeLessThan(25);
  expect(saved.watched_seconds).toBeLessThanOrEqual((Date.now() - watchStarted) / 1000 + 1.5);
  await page.screenshot({ path: info.outputPath("effective-video-and-highlight.png"), animations: "disabled" });
  await panel(page).getByRole("button", { name: "完成观看", exact: true }).click();
  await expect.poll(() => nodeStatus("step-1")).toBe("complete");
  expect(errors).toEqual([]);
});
