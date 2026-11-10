import {
  expect,
  type Page,
  type Locator,
  type APIRequestContext,
  type APIResponse,
} from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

export async function chooseOption(page: Page, trigger: Locator, label: string) {
  await trigger.click();
  await page.getByRole("option", { name: label, exact: true }).click();
}
export const credentials: { student_id: string; password: string } =
  process.env.E2E_TEACHER_STUDENT_ID && process.env.E2E_TEACHER_PASSWORD
    ? { student_id: process.env.E2E_TEACHER_STUDENT_ID, password: process.env.E2E_TEACHER_PASSWORD }
    : JSON.parse(fs.readFileSync(process.env.ADMIN_E2E_CREDENTIALS ?? path.resolve("../.tmp/course-browser-private.json"), "utf8"));
export async function json<T = Record<string, unknown>>(
  response: APIResponse,
): Promise<T> {
  expect(
    response.ok(),
    `HTTP ${response.status()}: ${await response.text()}`,
  ).toBeTruthy();
  return response.json() as Promise<T>;
}
export async function setup(
  page: Page,
  request: APIRequestContext,
  name: string,
) {
  const identity = await json<{ access_token: string; user: { id: number } }>(
    await request.post("/api/auth/login", {
      data: {
        student_id: credentials.student_id,
        password: credentials.password,
      },
    }),
  );
  const headers = { Authorization: `Bearer ${identity.access_token}` },
    tag = Date.now().toString(),
    classroom = await json<{ id: number }>(
      await request.post("/api/classrooms", {
        headers,
        data: { name: `${name} ${tag}` },
      }),
    );
  await json(
    await request.post(`/api/teacher/classrooms/${classroom.id}/course/empty`, {
      headers,
      data: { confirmed: true, name: `${name}课程` },
    }),
  );
  const scoped = (p: string) => `/api${p}?classroom_id=${classroom.id}`;
  const chapter = await json<{ id: number }>(
    await request.post(scoped("/teacher/chapters"), {
      headers,
      data: { title: "回归章节", order_index: 1 },
    }),
  );
  const doc = await json<{
    catalog: {
      maps: {
        key: string;
        objects: { points: { tile_x: number; tile_y: number }[] }[];
      }[];
    };
  }>(
    await request.get(`/api/teacher/classrooms/${classroom.id}/authoring`, {
      headers,
    }),
  );
  const map = doc.catalog.maps[0],
    point = map.objects[0].points[0];
  const npc = await json<{ key: string }>(
    await request.post(scoped("/teacher/npcs"), {
      headers,
      data: {
        key: `npc-${tag}`,
        name: "回归向导",
        map_key: map.key,
        tile_x: point.tile_x,
        tile_y: point.tile_y,
        portrait: "",
        dialogue: [{ id: "hello", text: "欢迎" }],
      },
    }),
  );
  await page.addInitScript(
    ({ token, userId, cid }) => {
      sessionStorage.setItem("pythontopia-reborn-session", token);
      sessionStorage.setItem(`classroom:${userId}`, String(cid));
    },
    {
      token: identity.access_token,
      userId: identity.user.id,
      cid: classroom.id,
    },
  );
  await page.goto("/teacher/");
  await expect(page.getByLabel("当前班级")).toContainText(`${name} ${tag}`);
  return {
    headers,
    cid: classroom.id,
    chapterId: chapter.id,
    npcKey: npc.key,
    tag,
    scoped,
  };
}
export async function go(page: Page, name: string) {
  await page.getByRole("link", { name, exact: true }).click();
}
export function pdf() {
  const items = [
    "<< /Type /Catalog /Pages 2 0 R >>",
    "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
  ];
  const content =
    "BT /F1 18 Tf 36 740 Td (Self-authored TEST MEDIA, not course content.) Tj ET";
  items.push(
    `<< /Length ${Buffer.byteLength(content)} >>\nstream\n${content}\nendstream`,
  );
  let result = "%PDF-1.4\n";
  const offsets = [0];
  for (const [i, item] of items.entries()) {
    offsets.push(Buffer.byteLength(result));
    result += `${i + 1} 0 obj\n${item}\nendobj\n`;
  }
  const xref = Buffer.byteLength(result);
  result +=
    `xref\n0 ${items.length + 1}\n0000000000 65535 f \n` +
    offsets
      .slice(1)
      .map((x) => `${String(x).padStart(10, "0")} 00000 n \n`)
      .join("") +
    `trailer\n<< /Size ${items.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(result);
}
