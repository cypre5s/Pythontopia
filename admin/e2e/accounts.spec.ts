import { test, expect } from "@playwright/test";
import { setup, json, go } from "./helpers";
test("学生批量选择、筛选隔离、密码重置、移出与账号键盘焦点", async ({
  page,
  request,
}) => {
  const { headers, cid, tag } = await setup(page, request, "账号回归"),
    students = Array.from({ length: 60 }, (_, i) => ({
      student_id: `8${tag.slice(-9)}${String(i).padStart(3, "0")}`,
      name: `回归学生${String(i).padStart(3, "0")}`,
    }));
  await json(
    await request.post(`/api/teacher/classrooms/${cid}/students`, {
      headers,
      data: { students },
    }),
  );
  const target = students[59],
    old = await json<{ access_token: string }>(
      await request.post("/api/auth/login", {
        data: { student_id: target.student_id, password: target.student_id },
      }),
    );
  await go(page, "学生账号");
  await expect(page.locator("tbody tr")).toHaveCount(25);
  await page.getByRole("checkbox", { name: "选择本页学生" }).click();
  await expect(page.getByText("已选择 25 名学生")).toBeVisible();
  await page.getByRole("button", { name: "下一页", exact: true }).click();
  await page.getByRole("checkbox", { name: "选择本页学生" }).click();
  await expect(page.getByText("已选择 50 名学生")).toBeVisible();
  await page.getByLabel("搜索学生").fill(target.student_id);
  await expect(page.locator("tbody tr")).toHaveCount(1);
  await expect(page.getByText(/已选择 \d+ 名学生/)).toHaveCount(0);
  await page.getByRole("checkbox", { name: `选择${target.student_id}`, exact: true }).click();
  await page.getByRole("button", { name: `${target.name}（${target.student_id}）的操作`, exact: true }).click();
  await page.getByRole("menuitem", { name: "重置密码", exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText(target.student_id);
  await page.getByRole("button", { name: "取消", exact: true }).click();
  expect(
    (
      await request.get("/api/auth/me", {
        headers: { Authorization: `Bearer ${old.access_token}` },
      })
    ).status(),
  ).toBe(200);
  await page.getByRole("button", { name: "重置密码", exact: true }).click();
  await page.getByRole("button", { name: "确认操作", exact: true }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  expect(
    (
      await request.get("/api/auth/me", {
        headers: { Authorization: `Bearer ${old.access_token}` },
      })
    ).status(),
  ).toBe(401);
  await page.getByRole("checkbox", { name: `选择${target.student_id}`, exact: true }).click();
  await page.getByRole("button", { name: "移出班级", exact: true }).click();
  await page.getByRole("button", { name: "确认操作", exact: true }).click();
  await expect(page.getByText("没有匹配的学生", { exact: true })).toBeVisible();
  expect(
    (
      await request.post("/api/auth/login", {
        data: { student_id: target.student_id, password: target.student_id },
      })
    ).ok(),
  ).toBeTruthy();
  await page.locator("button.account").click();
  await page.getByRole("menuitem", { name: "修改密码" }).click();
  await expect(page.getByLabel("当前密码", { exact: true })).toBeFocused();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.locator("button.account")).toBeFocused();
  await page.locator("button.account").click();
  await page.getByRole("menuitem", { name: "退出账号" }).click();
  await expect(page.getByRole("button", { name: "进入工作台" })).toBeVisible();
  expect(
    await page.evaluate(() =>
      sessionStorage.getItem("pythontopia-reborn-session"),
    ),
  ).toBeNull();
});
