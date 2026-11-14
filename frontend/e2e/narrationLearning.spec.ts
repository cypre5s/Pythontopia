import { test, expect } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { importCourse } from "./courseFixture";

function testPdf() {
  const objects = ["<< /Type /Catalog /Pages 2 0 R >>", "<< /Type /Pages /Kids [3 0 R 5 0 R 7 0 R] /Count 3 >>"];
  for (let page = 1; page <= 3; page++) {
    const stream = `BT /F1 28 Tf 60 160 Td (PR2 test page ${page}) Tj ET`;
    objects.push(`<< /Type /Page /Parent 2 0 R /MediaBox [0 0 480 270] /Resources << /Font << /F1 9 0 R >> >> /Contents ${page * 2 + 2} 0 R >>`);
    objects.push(`<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`);
  }
  objects.push("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
  let pdf = "%PDF-1.4\n";
  const offsets = [0];
  objects.forEach((object, i) => { offsets.push(Buffer.byteLength(pdf)); pdf += `${i + 1} 0 obj\n${object}\nendobj\n`; });
  const xref = Buffer.byteLength(pdf);
  pdf += `xref\n0 ${offsets.length}\n0000000000 65535 f \n`;
  pdf += offsets.slice(1).map(offset => `${String(offset).padStart(10, "0")} 00000 n \n`).join("");
  return Buffer.from(pdf + `trailer\n<< /Size ${offsets.length} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF`);
}

test("PDF narration keeps all pages readable and opens the existing learning assistant without a model call", async ({ page, request }, info) => {
  test.setTimeout(120000);
  const student_id = process.env.E2E_TEACHER_STUDENT_ID, password = process.env.E2E_TEACHER_PASSWORD;
  test.skip(!student_id || !password, "Requires isolated teacher credentials");
  const login = await request.post("/api/auth/login", { data: { student_id, password } });
  expect(login.ok()).toBe(true);
  const teacher = { Authorization: `Bearer ${(await login.json()).access_token}` };
  const created = await request.post("/api/classrooms", { headers: teacher, data: { name: `讲解集成 ${randomUUID().slice(0, 8)}` } });
  const classroom = await created.json();
  const ids = await importCourse(request, teacher, classroom.id);
  expect((await request.put(`/api/teacher/classrooms/${classroom.id}/releases/${ids.chapter["179"]}`, { headers: teacher, data: { status: "published" } })).ok()).toBe(true);
  const title = `讲解测试课件 ${randomUUID().slice(0, 6)}`;
  const upload = await request.post(`/api/teacher/materials/upload?classroom_id=${classroom.id}`, { headers: teacher, multipart: {
    title, chapter_ids: JSON.stringify([ids.chapter["179"]]), file: { name: "narration-test.pdf", mimeType: "application/pdf", buffer: testPdf() },
  } });
  expect(upload.status(), await upload.text()).toBe(201);
  const material = await upload.json();
  await expect.poll(async () => (await (await request.get(`/api/teacher/materials/${material.id}?classroom_id=${classroom.id}`, { headers: teacher })).json()).status, { timeout: 30000 }).toBe("ready");
  const activity = await request.post(`/api/teacher/activities?classroom_id=${classroom.id}`, { headers: teacher, data: {
    id: `narration-${randomUUID()}`, chapter_id: ids.chapter["179"], title, kind: "reading", material_id: material.id,
  } });
  expect(activity.ok(), await activity.text()).toBe(true);
  const registered = await request.post("/api/auth/register", { data: { student_id: BigInt("0x" + randomUUID().replaceAll("-", "").slice(0, 24)).toString(), password: `Test-${randomUUID()}`, name: "讲解验收居民" } });
  const identity = await registered.json();
  const student = { Authorization: `Bearer ${identity.access_token}` };
  expect((await request.post("/api/classrooms/join", { headers: student, data: { join_code: classroom.join_code } })).ok()).toBe(true);
  await expect.poll(async () => (await (await request.get(`/api/materials/${material.id}?classroom_id=${classroom.id}`, { headers: student })).json()).status).toBe("ready");
  const narration = `/api/teacher/classrooms/${classroom.id}/materials/${material.id}/narrations`;
  for (const p of [1, 3]) {
    expect((await request.put(`${narration}/${p}`, { headers: teacher, data: { text: `第 ${p} 页教师讲解：for i in range(3)` } })).ok()).toBe(true);
    expect((await request.post(`${narration}/${p}/accept`, { headers: teacher })).ok()).toBe(true);
  }
  await page.addInitScript(({ token, userId, cid }) => {
    sessionStorage.setItem("pythontopia-reborn-session", token);
    sessionStorage.setItem(`classroom:${userId}`, String(cid));
  }, { token: identity.access_token, userId: identity.user.id, cid: classroom.id });
  const modelRequests: string[] = [], pageImages: string[] = [];
  page.on("request", req => {
    if (req.method() === "POST" && /\/messages|\/ask/.test(req.url())) modelRequests.push(req.url());
    if (req.url().includes("/page-image")) pageImages.push(req.url());
  });
  await page.goto("/world");
  await page.getByRole("dialog", { name: "欢迎回到小镇", exact: true }).getByRole("button", { name: "按任意键继续", exact: true }).click();
  await page.getByRole("button", { name: /学习资料/ }).click();
  await page.locator(".library-list button").filter({ hasText: title }).click();
  const canvas = page.locator(".pdf-reader canvas");
  await expect(canvas).toBeVisible();
  await expect(page.locator(".lecture-subtitle")).toContainText("for i in range(3)");
  await page.getByRole("button", { name: "下一页 PDF", exact: true }).click();
  await expect(page.locator(".lecture-subtitle")).toContainText("这一页还没有");
  await expect(page.locator(".pdf-reader")).toHaveAttribute("aria-busy", "false");
  for (const width of [1920, 1280]) {
    await page.setViewportSize({ width, height: width === 1920 ? 1080 : 720 });
    await expect(canvas).toBeVisible();
    expect(await canvas.evaluate(el => (el as HTMLCanvasElement).width)).toBeGreaterThan(0);
    await page.screenshot({ path: info.outputPath(`narration-${width}.png`) });
  }
  await page.getByRole("button", { name: "就这页提问", exact: true }).click();
  await expect(page.locator(".support-chat")).toBeVisible();
  const message = page.locator(".support-chat textarea");
  // This real-service QA also runs without model credentials; opening the panel
  // must preserve the shared assistant's unavailable state instead of bypassing it.
  if (await message.isEnabled()) await expect(message).toBeFocused();
  else await expect(message).toBeDisabled();
  expect(modelRequests).toEqual([]);
  expect(pageImages).toEqual([]);
});
