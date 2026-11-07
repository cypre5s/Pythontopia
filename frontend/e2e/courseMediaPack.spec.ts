import { test, expect, type APIRequestContext, type Page } from "@playwright/test";
import { randomUUID } from "node:crypto";

interface LibraryMaterial { id: number; title: string; kind: string; supplementary?: boolean; }

async function prepare(request: APIRequestContext) {
  const email = process.env.E2E_TEACHER_EMAIL, password = process.env.E2E_TEACHER_PASSWORD;
  test.skip(process.env.E2E_COURSE_MEDIA_IMPORTED !== "1" || !email || !password,
    "Requires the real course media pack imported into an isolated deployment and explicit teacher credentials.");
  const login = await request.post("/api/auth/login", { data: { email, password } });
  expect(login.ok()).toBeTruthy();
  const teacher = { Authorization: `Bearer ${(await login.json()).access_token}` };
  const created = await request.post("/api/classrooms", { headers: teacher, data: { name: `课程媒体验收 ${randomUUID().slice(0, 8)}` } });
  expect(created.status()).toBe(201);
  const classroom = await created.json();
  for (const chapter of [179, 180, 181, 182, 183, 184, 185]) {
    expect((await request.put(`/api/teacher/classrooms/${classroom.id}/releases/${chapter}`, {
      headers: teacher, data: { status: "published" },
    })).ok()).toBeTruthy();
  }
  const registered = await request.post("/api/auth/register", { data: {
    email: `course-media-${randomUUID()}@example.test`, password: `Media-${randomUUID()}`, name: "课程媒体验收居民",
  } });
  expect(registered.status()).toBe(201);
  const identity = await registered.json();
  const student = { Authorization: `Bearer ${identity.access_token}` };
  expect((await request.post("/api/classrooms/join", { headers: student, data: { join_code: classroom.join_code } })).ok()).toBeTruthy();
  const read = async (path: string) => {
    const response = await request.get(path, { headers: student });
    expect(response.ok(), await response.text()).toBeTruthy();
    return response.json();
  };
  const library = await read(`/api/library?classroom_id=${classroom.id}`) as LibraryMaterial[];
  expect(library.filter(item => item.kind === "pdf")).toHaveLength(7);
  expect(library.filter(item => item.kind === "video" && item.supplementary)).toHaveLength(34);
  return { classroom, identity, student, teacher, library, read };
}

async function enter(page: Page, identity: { access_token: string; user: { id: number } }, classroomId: number, oldTextIndex?: number) {
  await page.addInitScript(({ identity, classroomId, oldTextIndex }) => {
    sessionStorage.setItem("pythontopia-reborn-session", identity.access_token);
    sessionStorage.setItem(`classroom:${identity.user.id}`, String(classroomId));
    if (oldTextIndex !== undefined) sessionStorage.setItem(`read:${identity.user.id}:${classroomId}:material-186`, String(oldTextIndex));
  }, { identity, classroomId, oldTextIndex });
  await page.goto("/world");
  const welcome = page.getByRole("dialog", { name: "欢迎回到小镇", exact: true });
  await welcome.getByRole("button", { name: "按任意键继续", exact: true }).click();
  await expect(welcome).toBeHidden();
}

async function openLibrary(page: Page) {
  await page.getByRole("button", { name: /学习资料/ }).click();
  await expect(page.locator(".library-workspace")).toBeVisible();
}

test("imported PDF and supplementary video preserve existing lesson progress and the only main quest", async ({ page, request }, testInfo) => {
  test.setTimeout(180_000);
  const { classroom, identity, student, teacher, library, read } = await prepare(request);
  const pdf = await read(`/api/materials/186?classroom_id=${classroom.id}`);
  expect(pdf.kind).toBe("pdf");
  expect(pdf.pages).toHaveLength(45);
  expect(pdf.pages.filter((p: { id: number }) => [10715, 10723, 10724].includes(p.id)).map((p: { page_no: number }) => p.page_no)).toEqual([34, 42, 43]);
  const saved = await request.post("/api/activities/ch1-lesson/progress", { headers: student, data: { classroom_id: classroom.id, page_no: 42 } });
  expect(saved.ok()).toBeTruthy();
  const originalProgress = await saved.json();
  const worldBefore = await read(`/api/world?classroom_id=${classroom.id}`);
  expect(worldBefore.quests.map((quest: { id: string }) => quest.id)).toEqual(["arrival"]);
  const recordPath = `/api/teacher/classrooms/${classroom.id}/students/${identity.user.id}`;
  const recordsBefore = await (await request.get(recordPath, { headers: teacher })).json();
  const aiBefore = await read("/api/assistant/status");
  const mutations: string[] = [];
  page.on("request", outgoing => {
    if (outgoing.method() === "POST" && /\/api\/(?:events|submissions|activities\/[^/]+\/(?:progress|participation)|assistant\/sessions)/.test(outgoing.url())) mutations.push(outgoing.url());
  });
  await enter(page, identity, classroom.id, 33);
  await openLibrary(page);
  await page.locator(".library-list button").filter({ hasText: pdf.title }).click();
  await expect(page.locator(".pdf-reader .reader-position strong")).toHaveText("34", { timeout: 60_000 });
  await expect(page.locator(".pdf-reader")).toHaveAttribute("aria-busy", "false", { timeout: 60_000 });
  expect(await page.locator(".pdf-reader canvas").evaluate(canvas => (canvas as HTMLCanvasElement).width)).toBeGreaterThan(100);
  await expect(page.locator(".lesson-footer")).toHaveCount(0);
  await page.screenshot({ path: testInfo.outputPath("original-course-pdf-page34.png") });
  await page.getByRole("button", { name: "关闭当前工作区", exact: true }).click();
  await openLibrary(page);
  const videoMaterial = library.find(item => item.kind === "video" && item.title === "计算思维小游戏")!;
  expect(videoMaterial).toBeTruthy();
  await expect(page.locator(".library-list button").filter({ hasText: videoMaterial.title })).toContainText("补充视频");
  const range = await request.get(`/api/materials/${videoMaterial.id}/file?classroom_id=${classroom.id}`, { headers: { ...student, Range: "bytes=0-1023" } });
  expect(range.status()).toBe(206);
  expect((await range.body()).length).toBe(1024);
  expect(range.headers()["content-range"]).toMatch(/^bytes 0-1023\//);
  expect((await request.get(`/api/materials/${videoMaterial.id}/file?classroom_id=${classroom.id}`)).status()).toBe(401);
  await page.locator(".library-list button").filter({ hasText: videoMaterial.title }).click();
  const video = page.locator(".course-video");
  await expect(video).toBeVisible({ timeout: 45_000 });
  await expect.poll(() => video.evaluate(element => (element as HTMLVideoElement).readyState), { timeout: 30_000 }).toBeGreaterThanOrEqual(2);
  await video.evaluate(async element => { const v = element as HTMLVideoElement; v.muted = true; v.currentTime = 60; await v.play(); });
  await expect.poll(() => video.evaluate(element => (element as HTMLVideoElement).currentTime)).toBeGreaterThan(60.2);
  await video.evaluate(element => (element as HTMLVideoElement).pause());
  await page.screenshot({ path: testInfo.outputPath("supplementary-course-video.png") });
  const storedTime = await page.evaluate(key => Number(sessionStorage.getItem(key)), `read:${identity.user.id}:${classroom.id}:material-${videoMaterial.id}:video`);
  expect(storedTime).toBeGreaterThan(60);
  await page.getByRole("button", { name: "关闭当前工作区", exact: true }).click();
  await openLibrary(page);
  await page.locator(".library-list button").filter({ hasText: videoMaterial.title }).click();
  await expect(video).toBeVisible({ timeout: 45_000 });
  await expect.poll(() => video.evaluate(element => (element as HTMLVideoElement).currentTime), { timeout: 30_000 }).toBeGreaterThanOrEqual(60);
  expect(await read(`/api/activities/ch1-lesson/progress?classroom_id=${classroom.id}`)).toEqual(originalProgress);
  expect(await (await request.get(recordPath, { headers: teacher })).json()).toEqual(recordsBefore);
  expect((await read("/api/assistant/status")).model_calls).toBe(aiBefore.model_calls);
  expect((await read(`/api/world?classroom_id=${classroom.id}`)).quests).toEqual(worldBefore.quests);
  expect(mutations).toEqual([]);
});

test("all 34 imported videos have decodable first frames in the real browser", async ({ page, request }, testInfo) => {
  test.setTimeout(360_000);
  test.skip(process.env.E2E_ALL_COURSE_VIDEOS !== "1", "Opt-in full original-video decode check.");
  const { classroom, identity, library } = await prepare(request);
  await page.goto("/");
  const videos = library.filter(item => item.kind === "video");
  const results = [];
  for (const material of videos) {
    const result = await page.evaluate(async ({ id, classroomId, token }) => {
      const response = await fetch(`/api/materials/${id}/file?classroom_id=${classroomId}`, { headers: { Authorization: `Bearer ${token}` } });
      if (!response.ok) throw new Error(`Video ${id}: HTTP ${response.status}`);
      const url = URL.createObjectURL(await response.blob()), video = document.createElement("video");
      video.muted = true;
      video.preload = "auto";
      video.src = url;
      document.body.append(video);
      try {
        await new Promise<void>((resolve, reject) => {
          const timer = setTimeout(() => reject(new Error(`Video ${id}: decode timed out`)), 20_000);
          video.onloadeddata = () => { clearTimeout(timer); resolve(); };
          video.onerror = () => { clearTimeout(timer); reject(new Error(`Video ${id}: decoder ${video.error?.code}`)); };
          video.load();
        });
        await video.play();
        await new Promise(resolve => setTimeout(resolve, 400));
        return { width: video.videoWidth, height: video.videoHeight, duration: video.duration, played: video.currentTime };
      } finally {
        video.pause(); video.removeAttribute("src"); video.load(); video.remove(); URL.revokeObjectURL(url);
      }
    }, { id: material.id, classroomId: classroom.id, token: identity.access_token });
    expect(result.width, material.title).toBeGreaterThan(0);
    expect(result.height, material.title).toBeGreaterThan(0);
    expect(result.duration, material.title).toBeGreaterThan(1);
    expect(result.played, material.title).toBeGreaterThan(0);
    results.push({ title: material.title, ...result });
  }
  await testInfo.attach("original-video-decode-results", { body: JSON.stringify(results, null, 2), contentType: "application/json" });
});
