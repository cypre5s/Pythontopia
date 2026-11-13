import { expect, type APIRequestContext } from "@playwright/test";
import { randomUUID } from "node:crypto";

export interface CourseIds {
  chapter: Record<string, number>;
  material: Record<string, number>;
  knowledge_point: Record<string, number>;
  example: Record<string, number>;
  activity: Record<string, string>;
  question: Record<string, string>;
  quest: Record<string, string>;
  story: Record<string, string>;
  npc: Record<string, string>;
}

/** Explicit fixture import; no global content fallback or learner evidence injection. */
export async function importCourse(request: APIRequestContext, headers: Record<string, string>, classroomId: number, base = ""): Promise<CourseIds> {
  const configured = process.env.REBORN_E2E_COURSE_PACKAGE;
  if (!configured) throw new Error("REBORN_E2E_COURSE_PACKAGE must identify a mounted test course by package_id.");
  const root = `${base}/api/teacher/classrooms/${classroomId}/course`;
  const catalog = await request.get(`${root}/packages`, { headers });
  expect(catalog.ok(), await catalog.text()).toBeTruthy();
  const { items } = await catalog.json() as { items: Array<{ key: string; package_id: string }> };
  const packages = items.filter((item) => item.package_id === configured);
  expect(packages, `Expected one mounted test course with package_id=${configured}`).toHaveLength(1);
  const preview = await request.post(`${root}/imports/available/preview`, {
    headers,
    data: { package_key: packages[0].key },
  });
  expect(preview.ok(), await preview.text()).toBeTruthy();
  const item = await preview.json();
  expect(item.can_apply, JSON.stringify(item.blockers)).toBe(true);
  const applied = await request.post(`${root}/imports/${item.id}/apply`, { headers,
    data: { request_id: randomUUID(), expected_revision: item.expected_revision } });
  expect(applied.ok(), await applied.text()).toBeTruthy();
  return courseIds(request, headers, classroomId, base);
}

export async function courseIds(request: APIRequestContext, headers: Record<string, string>, classroomId: number, base = ""): Promise<CourseIds> {
  const response = await request.get(`${base}/api/teacher/classrooms/${classroomId}/course/ids`, { headers });
  expect(response.ok(), await response.text()).toBeTruthy();
  return response.json() as Promise<CourseIds>;
}

export const scopedTeacher = (path: string, classroomId: number) => `${path}${path.includes("?") ? "&" : "?"}classroom_id=${classroomId}`;
