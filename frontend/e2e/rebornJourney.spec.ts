import { test, expect, type Page } from "@playwright/test";
import { randomUUID } from "node:crypto";

test("real teacher/class/student two-chapter journey and isolated workspaces", async ({
  page,
  request,
}, testInfo) => {
  test.setTimeout(240_000);
  const browserErrors: string[] = [];
  page.on("pageerror", (error) => browserErrors.push(error.message));
  const email = process.env.E2E_TEACHER_EMAIL,
    password = process.env.E2E_TEACHER_PASSWORD;
  test.skip(
    !email || !password,
    "Explicit isolated teacher credentials required; no default account is created.",
  );
  const teacherLogin = await request.post("/api/auth/login", {
    data: { email, password },
  });
  expect(teacherLogin.ok()).toBeTruthy();
  const teacher = {
    Authorization: `Bearer ${(await teacherLogin.json()).access_token}`,
  };
  const classRes = await request.post("/api/classrooms", {
    headers: teacher,
    data: { name: `验收 ${testInfo.project.name} ${randomUUID().slice(0, 6)}` },
  });
  expect(classRes.status()).toBe(201);
  const classroom = await classRes.json();
  expect(
    (
      await request.put(
        `/api/teacher/classrooms/${classroom.id}/releases/179`,
        { headers: teacher, data: { status: "published", version: 1 } },
      )
    ).ok(),
  ).toBeTruthy();
  await page.goto("/");
  await page.getByRole("button", { name: "初次到访？注册居民证" }).click();
  await page.getByLabel("名字", { exact: true }).fill("新居民");
  await page
    .getByLabel("邮箱", { exact: true })
    .fill(`${randomUUID()}@example.test`);
  await page.getByLabel("密码", { exact: true }).fill(`Reborn-${randomUUID()}`);
  await page.getByRole("button", { name: "注册并进入", exact: true }).click();
  await page.getByLabel("班级加入码").fill(classroom.join_code);
  await page.getByRole("button", { name: "加入班级", exact: true }).click();
  await expect(page.locator("canvas")).toBeVisible();
  await expect(page.locator(".world-hint")).toContainText("镇长 Py", {
    timeout: 15000,
  });
  const token = await page.evaluate(() =>
    sessionStorage.getItem("pythontopia-reborn-session"),
  );
  const student = { Authorization: `Bearer ${token}` };
  const world = async () => {
    const r = await request.get(`/api/world?classroom_id=${classroom.id}`, {
      headers: student,
    });
    expect(r.ok()).toBeTruthy();
    return r.json();
  };
  const status = async (q: string, n: string) =>
    (await world()).quests
      .find((v: { id: string }) => v.id === q)
      ?.nodes.find((v: { id: string }) => v.id === n)?.status;
  expect(
    (
      await request.get(
        `/api/activities/ch2-lesson?classroom_id=${classroom.id}`,
        { headers: student },
      )
    ).status(),
  ).toBe(403);
  await page.screenshot({
    path: `test-results/${testInfo.project.name}-world.png`,
  });
  await talk(page);
  await expect.poll(() => status("arrival", "greet")).toBe("complete");
  await talk(page);
  await expect.poll(() => status("arrival", "opening")).toBe("complete");
  await study(page);
  await expect.poll(() => status("arrival", "study")).toBe("complete");
  await page.getByRole("button", { name: "寻找目标" }).click();
  await expect
    .poll(() => status("arrival", "reach-practice"), { timeout: 30_000 })
    .toBe("complete");
  await practice(page, ["抽象与自动化", "正确", "建模"]);
  await expect.poll(() => status("arrival", "practice")).toBe("complete");
  await page.getByRole("button", { name: "寻找目标" }).click();
  await expect(page.getByRole("dialog", { name: "剧情对白" })).toBeVisible({
    timeout: 30_000,
  });
  await finishTalk(page);
  await expect
    .poll(async () => (await world()).flags)
    .toContain("library_unlocked");
  await page.screenshot({
    path: `test-results/${testInfo.project.name}-chapter-one-complete.png`,
  });
  // Teacher publishes the second chapter without rebuilding the client.
  expect(
    (
      await request.put(
        `/api/teacher/classrooms/${classroom.id}/releases/180`,
        {
          headers: teacher,
          data: {
            status: "published",
            version: 1,
            prerequisite_chapter_id: 179,
          },
        },
      )
    ).ok(),
  ).toBeTruthy();
  await page.reload({ waitUntil: "domcontentloaded" });
  await talk(page);
  await expect.poll(() => status("lost-language", "mission")).toBe("complete");
  await page.getByRole("button", { name: "寻找目标" }).click();
  await expect(page.getByRole("dialog", { name: "剧情对白" })).toBeVisible({
    timeout: 30_000,
  });
  await finishTalk(page);
  await study(page);
  await page.getByRole("button", { name: /任务日志/ }).click();
  await page
    .getByRole("button", { name: "辨认程序设计语言", exact: true })
    .click();
  await page.getByRole("button", { name: "关闭当前工作区" }).click();
  await practice(page, ["机器语言、汇编语言、高级语言", "正确", "助记符"]);
  await expect
    .poll(() => status("lost-language", "concept-practice"))
    .toBe("complete");
  expect(await status("lost-language", "code-practice")).toBe("skipped");
  // Enter the library via the real doorway; no business-page navigation.
  await page.getByRole("button", { name: "寻找目标" }).click();
  await expect
    .poll(async () => (await world()).player.map_key, { timeout: 30_000 })
    .toBe("library");
  await page.screenshot({
    path: `test-results/${testInfo.project.name}-library.png`,
  });
  // Remaining interior interactions are reached through the world controls.
  await page.getByRole("button", { name: "寻找目标" }).click();
  await expect(page.getByRole("dialog", { name: "剧情对白" })).toBeVisible({
    timeout: 20_000,
  });
  await finishTalk(page);
  await expect
    .poll(() => status("lost-language", "meet-librarian"))
    .toBe("complete");
  await page.getByRole("button", { name: "寻找目标" }).click();
  await expect
    .poll(() => status("lost-language", "read-shelf"), { timeout: 20_000 })
    .toBe("complete");
  if (await page.getByRole("button", { name: "关闭当前工作区" }).isVisible())
    await page.getByRole("button", { name: "关闭当前工作区" }).click();
  await page.getByRole("button", { name: "寻找目标" }).click();
  await expect(page.getByRole("dialog", { name: "剧情对白" })).toBeVisible({
    timeout: 40_000,
  });
  await finishTalk(page);
  await expect
    .poll(async () => (await world()).flags)
    .toContain("language_archive_restored");
  await page.getByRole("button", { name: /任务日志/ }).click();
  expect(await page.getByRole("dialog", { name: "游戏工作区" }).count()).toBe(
    1,
  );
  await page.getByRole("button", { name: "已完成", exact: true }).click();
  await expect(page.locator(".journal-entry")).toHaveCount(2);
  await page.screenshot({
    path: `test-results/${testInfo.project.name}-completed.png`,
  });
  const progress = await (
    await request.get(`/api/teacher/classrooms/${classroom.id}/progress`, {
      headers: teacher,
    })
  ).json();
  expect(
    progress.events.filter(
      (event: { node_id: string }) => event.node_id === "report",
    ),
  ).toHaveLength(1);
  expect(
    progress.events.filter(
      (event: { node_id: string }) => event.node_id === "report-language",
    ),
  ).toHaveLength(1);
  const ai = await (
    await request.get("/api/assistant/status", { headers: student })
  ).json();
  expect(ai.model_calls).toBe(0);
  expect(JSON.stringify(ai)).not.toContain("api_key");
  await page.reload({ waitUntil: "domcontentloaded" });
  await expect(page.locator(".world-hint")).not.toHaveText("", {
    timeout: 15000,
  });
  expect((await world()).flags).toContain("language_archive_restored");
  await page.getByRole("button", { name: "打开设置" }).click();
  await page.getByRole("button", { name: "退出账号", exact: true }).click();
  await page.getByRole("button", { name: "初次到访？注册居民证" }).click();
  await page.getByLabel("名字", { exact: true }).fill("另一位新居民");
  await page
    .getByLabel("邮箱", { exact: true })
    .fill(`${randomUUID()}@example.test`);
  await page.getByLabel("密码", { exact: true }).fill(`Reborn-${randomUUID()}`);
  await page.getByRole("button", { name: "注册并进入", exact: true }).click();
  await page.getByLabel("班级加入码").fill(classroom.join_code);
  await page.getByRole("button", { name: "加入班级", exact: true }).click();
  await expect(
    page.getByText("在南侧广场找到镇长 Py", { exact: true }),
  ).toBeVisible();
  const nextToken = await page.evaluate(() =>
    sessionStorage.getItem("pythontopia-reborn-session"),
  );
  const nextWorld = await (
    await request.get(`/api/world?classroom_id=${classroom.id}`, {
      headers: { Authorization: `Bearer ${nextToken}` },
    })
  ).json();
  expect(nextWorld.flags).not.toContain("library_unlocked");
  expect(
    nextWorld.quests.find((q: { id: string }) => q.id === "arrival").status,
  ).toBe("active");
  expect(browserErrors).toEqual([]);
});

async function talk(page: Page) {
  await expect(page.locator(".world-hint")).toContainText("镇长 Py", {
    timeout: 15000,
  });
  await page.keyboard.press("e");
  await expect(page.getByRole("dialog", { name: "剧情对白" })).toBeVisible();
  await finishTalk(page);
}
async function finishTalk(page: Page) {
  const dialogue = page.getByRole("dialog", { name: "剧情对白" });
  for (let i = 0; i < 10; i++) {
    if (!(await dialogue.isVisible())) return;
    await dialogue.locator(".dialogue-text").click();
    const choice = dialogue.getByRole("button", {
      name: "辨认程序设计语言",
      exact: true,
    });
    const button = (await choice.count())
      ? choice
      : dialogue.locator(".dialogue-actions .primary");
    const responsePromise = page.waitForResponse(
      (r) =>
        r.url().includes("/dialogues/") &&
        r.url().endsWith("/steps") &&
        r.request().method() === "POST",
    );
    await button.click();
    const response = await responsePromise;
    expect(response.ok(), await response.text()).toBeTruthy();
    const result = await response.json();
    if (!result.next_id) {
      // Closing waits for the real event transaction and world refresh, not only the script response.
      await expect(dialogue).toBeHidden({ timeout: 15_000 });
      return;
    }
    await page.waitForTimeout(100);
  }
  throw new Error("Dialogue did not terminate within ten bounded nodes");
}
async function study(page: Page) {
  await page.getByRole("button", { name: /任务日志/ }).click();
  await page
    .getByRole("button", { name: "打开课程", exact: true })
    .first()
    .click();
  await expect(
    page.getByRole("button", { name: "记录本次学习" }),
  ).toBeVisible();
  await page.screenshot({
    path: `test-results/lesson-${randomUUID().slice(0, 6)}.png`,
  });
  await page.getByRole("button", { name: "记录本次学习" }).click();
  await expect(
    page.getByText("本次学习已记录，任务目标已更新。", { exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "关闭当前工作区" }).click();
}
async function practice(page: Page, answers: string[]) {
  await page.getByRole("button", { name: /任务日志/ }).click();
  await page
    .getByRole("button", { name: "打开练习", exact: true })
    .first()
    .click();
  await expect(page.locator(".question-body")).toBeVisible();
  for (let i = 0; i < answers.length; i++) {
    if (i) await page.locator(".question-list button").nth(i).click();
    const answer = answers[i]!;
    await expect(page.locator(".question-body")).toBeVisible();
    const option = page.getByLabel(answer, { exact: true });
    if (await option.count()) await option.check();
    else await page.getByRole("textbox", { name: "填写答案" }).fill(answer);
    await page.getByRole("button", { name: "正式提交", exact: true }).click();
    await expect(page.locator(".submission-result strong")).toHaveText("通过");
  }
  await page.getByRole("button", { name: "关闭当前工作区" }).click();
}
