import { test, expect } from "@playwright/test";
import { readFile } from "node:fs/promises";
import { setup, go } from "./helpers";
test("教师真实视频私有预览与播放", async ({ page, request }, info) => {
  await setup(page, request, "视频回归");
  // 14 self-authored 320×180 canvas frames with alternating colors and a counter, encoded as VP8.
  const bytes = await readFile(new URL("./fixtures/teacher-preview.webm", import.meta.url));
  await go(page, "资料库");
  await page.getByRole("button", { name: "上传资料", exact: true }).click();
  await page.getByLabel("资料名称").fill("自制验收视频");
  await page
    .getByLabel("文件", { exact: true })
    .setInputFiles({
      name: "test-video.webm",
      mimeType: "video/webm",
      buffer: bytes,
    });
  await page
    .getByRole("dialog")
    .getByLabel("回归章节", { exact: true })
    .check();
  await page.getByRole("button", { name: "保存资料", exact: true }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(async () => {
    await page.reload();
    await expect(page.getByLabel("预览自制验收视频")).toBeEnabled();
  }).toPass({ timeout: 45000 });
  await page.getByLabel("预览自制验收视频").click();
  const player = page.getByRole("dialog").locator("video");
  await expect(player).toHaveAttribute("src", /^blob:/);
  await expect
    .poll(() => player.evaluate((el) => (el as HTMLVideoElement).readyState))
    .toBeGreaterThanOrEqual(2);
  await player.evaluate(async (el) => {
    const media = el as HTMLVideoElement;
    media.muted = true;
    await media.play();
  });
  await expect
    .poll(() => player.evaluate((el) => (el as HTMLVideoElement).currentTime))
    .toBeGreaterThan(0.1);
  expect(
    await player.evaluate((el) => (el as HTMLVideoElement).videoWidth),
  ).toBe(320);
  await page.screenshot({ path: info.outputPath("private-video-preview.png") });
});
test("外部转换器配置后 PPTX 转换为私有 PDF", async ({ page, request }) => {
  test.skip(
    !process.env.ADMIN_E2E_PPTX,
    "需自制 PPTX 文件与实际文稿转换器，不以模拟结果代替",
  );
  test.setTimeout(180000);
  await setup(page, request, "PPT回归");
  await go(page, "资料库");
  await page.getByRole("button", { name: "上传资料", exact: true }).click();
  await page.getByLabel("资料名称").fill("自制演示文稿");
  await page
    .getByLabel("文件", { exact: true })
    .setInputFiles(process.env.ADMIN_E2E_PPTX!);
  await page.getByRole("button", { name: "保存资料", exact: true }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(async () => {
    await page.reload();
    await expect(page.getByLabel("预览自制演示文稿")).toBeEnabled();
  }).toPass({ timeout: 120000 });
  await page.getByLabel("预览自制演示文稿").click();
  await expect(page.getByRole("dialog").locator("iframe")).toHaveAttribute(
    "src",
    /^blob:/,
  );
});
