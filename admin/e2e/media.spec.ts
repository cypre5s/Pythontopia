import { test, expect, type Page } from "@playwright/test";
import { setup, go } from "./helpers";
async function video(page: Page) {
  const encoded = await page.evaluate(async () => {
    const canvas = document.createElement("canvas");
    canvas.width = 320;
    canvas.height = 180;
    const ctx = canvas.getContext("2d")!;
    const stream = canvas.captureStream(12),
      recorder = new MediaRecorder(stream, { mimeType: "video/webm" }),
      chunks: Blob[] = [];
    recorder.ondataavailable = (e) => chunks.push(e.data);
    const stopped = new Promise<void>(
      (resolve) => (recorder.onstop = () => resolve()),
    );
    let frame = 0;
    const draw = () => {
      ctx.fillStyle = frame % 2 ? "#386149" : "#32526b";
      ctx.fillRect(0, 0, 320, 180);
      ctx.fillStyle = "white";
      ctx.font = "16px sans-serif";
      ctx.fillText("Self-authored TEST MEDIA", 15, 70);
      ctx.fillText(`Frame ${frame++}`, 15, 105);
    };
    draw();
    recorder.start();
    const timer = setInterval(draw, 80);
    await new Promise((resolve) => setTimeout(resolve, 1300));
    recorder.stop();
    await stopped;
    clearInterval(timer);
    stream.getTracks().forEach((t) => t.stop());
    const bytes = new Uint8Array(await new Blob(chunks).arrayBuffer());
    return btoa(Array.from(bytes, (b) => String.fromCharCode(b)).join(""));
  });
  return Buffer.from(encoded, "base64");
}
test("教师真实视频私有预览与播放", async ({ page, request }, info) => {
  await setup(page, request, "视频回归");
  const bytes = await video(page);
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
