import { expect, type Page } from "@playwright/test";

/** Uses the student UI and real server receipts; never edits application state. */
export async function answerStoryQuestions(page: Page, answers: { title?: string; answer: string }[], assessment = false) {
  const expand = page.getByRole("button", { name: "展开侧边栏", exact: true });
  if (await expand.isVisible()) await expand.click();
  await page.getByRole("tab", { name: "题目目录", exact: true }).click();
  const directory = page.getByRole("navigation", { name: "题目目录", exact: true });
  await expect(directory.getByRole("button")).toHaveCount(answers.length);
  for (const [index, item] of answers.entries()) {
    await (item.title ? directory.getByRole("button").filter({ hasText: item.title }) : directory.getByRole("button").nth(index)).click();
    if (item.title) await expect(page.locator(".practice-question-card h3")).toContainText(item.title);
    const option = page.getByLabel(item.answer, { exact: true });
    if (await option.count()) await option.check();
    else await page.getByRole("textbox", { name: "填写答案" }).fill(item.answer);
    if (!assessment) {
      const receipt = page.waitForResponse(response => response.url().endsWith("/api/submissions") && response.request().method() === "POST");
      await page.getByRole("button", { name: "提交答案", exact: true }).click();
      const saved = await receipt;
      expect(saved.ok()).toBe(true);
      expect((await saved.json()).status).toBe("accepted");
    }
  }
  if (assessment) {
    const receipt = page.waitForResponse(response => /\/assessment\/submit$/.test(response.url()) && response.request().method() === "POST");
    await page.getByRole("button", { name: "提交试卷", exact: true }).click();
    expect((await receipt).ok()).toBe(true);
  }
  const returned = page.waitForResponse(response => /\/story\/sessions\/[^/]+\/return$/.test(response.url()) && response.request().method() === "POST");
  await page.getByRole("button", { name: assessment ? "完成测验" : "完成练习", exact: true }).click();
  expect((await returned).ok()).toBe(true);
  await expect(page.getByRole("dialog", { name: "剧情对白", exact: true })).toBeVisible();
}
