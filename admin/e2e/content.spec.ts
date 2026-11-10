import { test, expect } from "@playwright/test";
import { setup, json, go, pdf, chooseOption } from "./helpers";
test("知识点、题目草稿与私有答案、多活动有序复用、资料替换和私有预览", async ({
  page,
  request,
}, info) => {
  const { headers, cid, chapterId, npcKey, tag, scoped } = await setup(
    page,
    request,
    "内容回归",
  );
  page.on("dialog", (d) => d.accept());
  await go(page, "课程结构");
  await page.getByRole("tab", { name: "知识点", exact: true }).click();
  await page.getByRole("button", { name: "新增知识点", exact: true }).click();
  await page.getByLabel("名称", { exact: true }).fill("变量概念");
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "保存", exact: true })
    .click();
  await expect(
    page.getByRole("cell", { name: "变量概念", exact: true }),
  ).toBeVisible();
  await page.getByLabel("编辑变量概念").click();
  await page.getByLabel("名称", { exact: true }).fill("变量与名称");
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "保存", exact: true })
    .click();
  await expect(
    page.getByRole("cell", { name: "变量与名称", exact: true }),
  ).toBeVisible();
  await go(page, "题库");
  await page.getByRole("button", { name: "新增题目", exact: true }).click();
  await page.getByLabel("题目名称").fill("同义答案");
  await chooseOption(page, page.getByRole("dialog").getByLabel("题型"), "填空题");
  await page.getByLabel("题干").fill("填写概念名称");
  await page.getByLabel("可接受答案（每行一个）").fill("变量\n可变名称");
  await page.getByRole("button", { name: "保存题目", exact: true }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  let bank = await json<
    { id: string; title: string; answer: unknown; status: string }[]
  >(await request.get(scoped("/teacher/questions"), { headers }));
  const question = bank.find((q) => q.title === "同义答案")!;
  expect(question.answer).toEqual(["变量", "可变名称"]);
  expect(question.status).toBe("draft");
  await page.getByLabel("编辑同义答案").click();
  await expect(page.getByLabel("可接受答案（每行一个）")).toHaveValue(
    "变量\n可变名称",
  );
  await page
    .getByRole("dialog")
    .getByLabel("回归章节", { exact: true })
    .check();
  await page
    .getByRole("dialog")
    .getByLabel("变量与名称", { exact: true })
    .check();
  await chooseOption(page, page.getByRole("dialog").getByLabel("状态", { exact: true }), "可用");
  await page.getByRole("button", { name: "保存题目", exact: true }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  const second = await json<{ id: string }>(
    await request.post(scoped("/teacher/questions"), {
      headers,
      data: {
        id: `second-${tag}`,
        title: "第二道题",
        kind: "boolean",
        prompt: "Python 是编程语言",
        answer: true,
        status: "ready",
        chapter_ids: [chapterId],
        source_type: "imported",
      },
    }),
  );
  const activities = [];
  for (const [i, kind] of ["practice", "assessment"].entries()) {
    const a = await json<{ id: string }>(
      await request.post(scoped("/teacher/activities"), {
        headers,
        data: {
          id: `activity-${tag}-${i}`,
          title: `复用活动${i}`,
          chapter_id: chapterId,
          kind,
          npc_key: npcKey,
          assistant_allowed: false,
        },
      }),
    );
    activities.push(a.id);
  }
  await go(page, "学习活动");
  for (let i = 0; i < 2; i++) {
    await page.getByLabel(`选题复用活动${i}`).click();
    await page
      .getByRole("dialog")
      .getByLabel("同义答案", { exact: true })
      .check();
    await page
      .getByRole("dialog")
      .getByLabel("第二道题", { exact: true })
      .check();
    await page.getByLabel("上移第2题").click();
    await page
      .getByRole("dialog")
      .getByRole("button", { name: "保存", exact: true })
      .click();
    await expect(page.getByRole("dialog")).toHaveCount(0);
  }
  const course = await json<{
    activities: { id: string; question_ids: string[] }[];
  }>(await request.get(scoped("/teacher/courses"), { headers }));
  for (const id of activities)
    expect(course.activities.find((a) => a.id === id)?.question_ids).toEqual([
      second.id,
      question.id,
    ]);
  bank = await json(
    await request.get(scoped("/teacher/questions"), { headers }),
  );
  expect(bank.find((q) => q.id === question.id)?.answer).toEqual([
    "变量",
    "可变名称",
  ]);
  const unused = await json<{ id: string }>(await request.post(scoped("/teacher/questions"), {
    headers,
    data: { id: `unused-${tag}`, title: "待删除的独立题目", kind: "boolean", prompt: "独立题目", answer: true, source_type: "imported", status: "draft" },
  }));
  await go(page, "题库");
  await page.getByRole("checkbox", { name: "选择同义答案", exact: true }).click();
  await page.getByRole("checkbox", { name: "选择待删除的独立题目", exact: true }).click();
  await page.getByRole("button", { name: "批量删除", exact: true }).click();
  await page.getByRole("button", { name: "确认删除", exact: true }).click();
  const deleteDialog = page.getByRole("dialog");
  await expect(deleteDialog.getByRole("alert")).toContainText("同义答案");
  await expect(deleteDialog.getByRole("status")).toContainText("已删除 1 条");
  await deleteDialog.getByRole("button", { name: "关闭", exact: true }).click();
  await expect(page.getByText("同义答案", { exact: true })).toBeVisible();
  expect((await json<{ id: string }[]>(await request.get(scoped("/teacher/questions"), { headers }))).some((item) => item.id === unused.id)).toBe(false);
  await go(page, "资料库");
  async function upload(name: string, file: Buffer, fileName: string) {
    await page.getByRole("button", { name: "上传资料", exact: true }).click();
    await page.getByLabel("资料名称").fill(name);
    await page.getByLabel("文件", { exact: true }).setInputFiles({
      name: fileName,
      mimeType: fileName.endsWith("pdf") ? "application/pdf" : "text/plain",
      buffer: file,
    });
    await page
      .getByRole("dialog")
      .getByLabel("回归章节", { exact: true })
      .check();
    await page.getByRole("button", { name: "保存资料", exact: true }).click();
    await expect(page.getByRole("dialog")).toHaveCount(0);
    await expect(async () => {
      await page.reload();
      await expect(page.getByLabel(`预览${name}`)).toBeEnabled();
    }).toPass({ timeout: 30000 });
  }
  await upload("文本资料", Buffer.from("初始测试文本"), "text.txt");
  let materials = await json<{ id: number; title: string }[]>(
    await request.get(scoped("/teacher/materials"), { headers }),
  );
  const original = materials.find((m) => m.title === "文本资料")!;
  await page.getByLabel("编辑文本资料").click();
  await page.getByLabel("替换文件（可选）").setInputFiles({
    name: "replace.txt",
    mimeType: "text/plain",
    buffer: Buffer.from("替换后的真实资料内容"),
  });
  await page.getByRole("button", { name: "保存资料", exact: true }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(async () => {
    await page.reload();
    await expect(page.getByLabel("预览文本资料")).toBeEnabled();
  }).toPass({ timeout: 30000 });
  await page.getByLabel("预览文本资料").click();
  await expect(
    page.getByText("替换后的真实资料内容", { exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "关闭", exact: true }).click();
  materials = await json(
    await request.get(scoped("/teacher/materials"), { headers }),
  );
  expect(materials.find((m) => m.title === "文本资料")?.id).toBe(original.id);
  expect(
    (
      await request.get(
        `/api/teacher/materials/${original.id}/file?classroom_id=${cid}`,
      )
    ).status(),
  ).toBe(401);
  await upload("私有PDF", pdf(), "authored.pdf");
  await page.getByLabel("预览私有PDF").click();
  await expect(page.getByRole("dialog").locator("iframe")).toHaveAttribute(
    "src",
    /^blob:/,
  );
  await expect.poll(async () => {
    const viewer = page.frames().find((frame) => frame.url().startsWith("chrome-extension://"));
    return viewer ? viewer.locator("embed").count() : 0;
  }).toBe(1);
  const pdfViewer = page.frames().find((frame) => frame.url().startsWith("chrome-extension://"))!;
  await expect(pdfViewer.locator("#pageSelector")).toHaveValue("1");
  await expect(pdfViewer.locator("#pageSelector")).toBeVisible();
  await page.screenshot({ path: info.outputPath("private-pdf-preview.png"), animations: "disabled" });
  await page.getByRole("button", { name: "关闭", exact: true }).click();
  await go(page, "教学管理");
  await page.getByRole("tab", { name: "学习记录", exact: true }).click();
  await expect(page.getByText("没有匹配的学生", { exact: true })).toBeVisible();
});
