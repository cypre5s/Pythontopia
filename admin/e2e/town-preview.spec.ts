import { expect, test } from "@playwright/test";
import { setup } from "./helpers";

test("教师从独立标签页进入并继续本班体验存档", async ({ page, request, context }) => {
  const { cid } = await setup(page, request, "教师体验");
  const teacherTab = page.url();
  const open = async () => {
    const [town] = await Promise.all([
      context.waitForEvent("page"),
      page.getByRole("button", { name: "进入小镇", exact: true }).click(),
    ]);
    await expect(town).toHaveURL(/\/world$/);
    await expect(town.getByRole("heading", { name: "欢迎回到小镇，教师体验学生。" })).toBeVisible();
    await town.getByRole("button", { name: "按任意键继续" }).click();
    await town.getByRole("button", { name: "开发工具", exact: true }).click();
    await expect(town.getByRole("heading", { name: "重新开始当前班级" })).toBeVisible();
    expect(await town.evaluate(() => window.opener === null)).toBe(true);
    return town;
  };

  const town = await open();
  const readIdentity = (game: typeof town) => game.evaluate(async () => {
    const response = await fetch("/api/auth/me", {
      headers: { Authorization: `Bearer ${sessionStorage.getItem("pythontopia-reborn-session")}` },
    });
    const user = await response.json() as { id: number; role: string; town_preview?: boolean };
    return { id: user.id, role: user.role, townPreview: user.town_preview === true };
  });
  const first = await readIdentity(town);
  expect(first).toEqual({ id: expect.any(Number), role: "student", townPreview: true });
  await town.close();
  await expect(page).toHaveURL(teacherTab);
  const teacher = await readIdentity(page);
  expect(["teacher", "admin"]).toContain(teacher.role);
  expect(teacher.id).not.toBe(first.id);

  const reopened = await open();
  expect(await readIdentity(reopened)).toEqual(first);
  const room = await reopened.evaluate(async () => {
    const response = await fetch("/api/classrooms", {
      headers: { Authorization: `Bearer ${sessionStorage.getItem("pythontopia-reborn-session")}` },
    });
    return (await response.json() as { id: number }[]).map((item) => item.id);
  });
  expect(room).toEqual([cid]);
  await reopened.close();
});
