import { test, expect } from "@playwright/test";

test("isolated Python samples, errors, timeout and teardown", async ({ page }) => {
  test.skip(!process.env.PYTHON_RUNNER_DEV_URL, "Requires the Vite test server and prepared local runtime");
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

test('prewarmed samples are fast, fresh, network-isolated and released with the workspace', async ({ page }) => {
  test.skip(!process.env.PYTHON_RUNNER_DEV_URL, 'Requires Vite and prepared local runtime');
  test.setTimeout(90000);
  await page.goto(process.env.PYTHON_RUNNER_DEV_URL!);
  let leakedRequests = 0;
  page.on('request', request => {
    if (request.url().includes('/api/runner-security-probe') || request.url().includes('example.com/runner-probe')) leakedRequests++;
  });
  const result = await page.evaluate(async () => {
    const path = '/src/shared/pythonRunner.ts';
    const { createPythonRunner } = await import(/* @vite-ignore */ path);
    const coldStart = performance.now();
    const runner = createPythonRunner(2);
    const anotherQuestion = createPythonRunner(2);
    if (document.querySelectorAll('iframe').length > 2) throw Error('Too many prewarmed contexts');
    anotherQuestion.dispose();
    await runner.ready();
    const preparationMs = performance.now() - coldStart;
    const warmStart = performance.now();
    const first = await runner.run("open('/tmp/probe','w').write('secret')\nx=42\nprint(input(), end='')", '你好，小林！', new AbortController().signal);
    const second = await runner.run("import os\nprint('x' in globals(), os.path.exists('/tmp/probe'))", '', new AbortController().signal);
    const warmMs = performance.now() - warmStart;
    await runner.ready();
    const security = await runner.run("import js\nfor url in ['" + location.origin + "/api/runner-security-probe','https://example.com/runner-probe']:\n try: js.fetch(url)\n except Exception: pass\nprint('done')", '', new AbortController().signal);
    runner.dispose();
    return {first, second, security, preparationMs, warmMs, frames:document.querySelectorAll('iframe').length};
  });
  console.log('Python runner timings', JSON.stringify({preparationMs:result.preparationMs,warmTwoSamplesMs:result.warmMs}));
  expect(result.first).toEqual({stdout:'你好，小林！',stderr:''});
  expect(result.second).toEqual({stdout:'False False\n',stderr:''});
  expect(result.security.stdout).toBe('done\n');
  expect(result.warmMs).toBeLessThan(1000);
  expect(result.frames).toBe(0);
  expect(leakedRequests).toBe(0);
});
