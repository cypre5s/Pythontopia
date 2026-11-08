import { test, expect } from "@playwright/test";
import { randomUUID } from "node:crypto";

test("library reading position survives removal of the former numeric cache suffix", async ({ page, request }, testInfo) => {
  const student_id = process.env.E2E_TEACHER_STUDENT_ID;
  const password = process.env.E2E_TEACHER_PASSWORD;
  test.skip(!student_id || !password, "Requires an explicit isolated teacher account.");
  const login = await request.post("/api/auth/login", { data: { student_id, password } });
  expect(login.ok()).toBeTruthy();
  const teacher = { Authorization: `Bearer ${(await login.json()).access_token}` };
  const created = await request.post("/api/classrooms", { headers: teacher, data: { name: `阅读位置验收 ${randomUUID().slice(0, 8)}` } });
  expect(created.status()).toBe(201);
  const classroom = await created.json();
  const released = await request.put(`/api/teacher/classrooms/${classroom.id}/releases/179`, { headers: teacher, data: { status: "published" } });
  expect(released.ok()).toBeTruthy();
  const registered = await request.post("/api/auth/register", { data: { student_id: BigInt('0x' + randomUUID().replaceAll('-', '').slice(0, 24)).toString(), password: `Reading-${randomUUID()}`, name: "阅读位置验收居民" } });
  expect(registered.status()).toBe(201);
  const identity = await registered.json();
  const student = { Authorization: `Bearer ${identity.access_token}` };
  expect((await request.post("/api/classrooms/join", { headers: student, data: { join_code: classroom.join_code } })).ok()).toBeTruthy();
  const library = await request.get(`/api/library?classroom_id=${classroom.id}`, { headers: student });
  expect(library.ok()).toBeTruthy();
  const listedMaterial = (await library.json())[0];
  const detail = await request.get(`/api/materials/${listedMaterial.id}?classroom_id=${classroom.id}`, { headers: student });
  expect(detail.ok()).toBeTruthy();
  const material = await detail.json();
  expect(material.pages.length).toBeGreaterThan(1);
  const currentKey = `read:${identity.user.id}:${classroom.id}:material-${material.id}`;
  await page.addInitScript(({ token, userId, classroomId, key }) => {
    sessionStorage.setItem("pythontopia-reborn-session", token);
    sessionStorage.setItem(`classroom:${userId}`, String(classroomId));
    sessionStorage.setItem(`${key}-1`, "0");
    sessionStorage.setItem(`${key}-2`, "1");
  }, { token: identity.access_token, userId: identity.user.id, classroomId: classroom.id, key: currentKey });
  await page.goto("/world");
  const welcome = page.getByRole("dialog", { name: "欢迎回到小镇", exact: true });
  await welcome.getByRole("button", { name: "按任意键继续", exact: true }).click();
  await expect(welcome).toBeHidden();
  await page.getByRole("button", { name: /学习资料/ }).click();
  await page.locator(".library-list button").filter({ hasText: material.title }).click();
  await expect(page.locator(".reader-position progress")).toHaveAttribute("value",String(material.pages[1].page_no));
  expect(await page.evaluate(key => ({ current: sessionStorage.getItem(key), oldFirst: sessionStorage.getItem(`${key}-1`), oldSecond: sessionStorage.getItem(`${key}-2`) }), currentKey))
    .toEqual({ current: "1", oldFirst: null, oldSecond: null });
  await expect(page.locator(".lesson-workspace")).not.toContainText("版本");
  await page.screenshot({ path: testInfo.outputPath("restored-library-position.png") });
});
