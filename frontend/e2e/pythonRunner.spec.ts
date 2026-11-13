import { test, expect } from "@playwright/test";

test("isolated Python samples, errors, timeout and teardown", async ({ page }) => {
  test.skip(!process.env.PYTHON_RUNNER_DEV_URL, "Requires the Vite test server and runtime download");
  test.setTimeout(180000);
  await page.goto(process.env.PYTHON_RUNNER_DEV_URL!);
  let applicationRequests = 0;
  page.on("request", request => { if (request.url().endsWith("/api/runner-security-probe")) applicationRequests++; });
  const result = await page.evaluate(async () => {
    // Vite serves the actual implementation, including its iframe CSP.
    const path = "/src/shared/pythonRunner.ts";
    const { runPython } = await import(/* @vite-ignore */ path);
    const run = (code: string, input = "") => runPython(code, input, new AbortController().signal);
    const echo = await run("print(input())", "hello");
    const error = await run("raise ValueError('visible error')");
    const security = await run("import js\njs.fetch('" + location.origin + "/api/runner-security-probe')");
    let timeout = "";
    try { await run("while True: pass"); } catch (e) { timeout = String(e); }
    const controller = new AbortController();
    const stopped = runPython("while True: pass", "", controller.signal).catch(String);
    controller.abort();
    return { echo, error, security, timeout, stopped: await stopped, frames: document.querySelectorAll("iframe").length };
  });
  expect(result.echo).toEqual({ stdout: "hello\n", stderr: "" });
  expect(result.error.stderr).toContain("ValueError");
  expect(result.timeout).toContain("5秒");
  expect(result.stopped).toContain("已停止");
  expect(result.frames).toBe(0);
  expect(applicationRequests).toBe(0);
});
