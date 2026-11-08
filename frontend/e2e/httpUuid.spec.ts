import { expect, test } from "@playwright/test";
import { readFileSync } from "node:fs";
import { ModuleKind, ScriptTarget, transpileModule } from "typescript";

test("request UUIDs work on a non-localhost HTTP origin", async ({ page }) => {
  await page.route("http://uuid-http.test/", route => route.fulfill({
    contentType: "text/html", body: "<!doctype html><title>HTTP UUID regression</title>",
  }));
  await page.goto("http://uuid-http.test/");
  const source = readFileSync(new URL("../src/shared/uuid.ts", import.meta.url), "utf8");
  const script = transpileModule(source, { compilerOptions: { module: ModuleKind.ESNext, target: ScriptTarget.ES2022 } }).outputText;
  const result = await page.evaluate(async code => {
    const { randomUUID } = await import(`data:text/javascript,${encodeURIComponent(code)}`);
    return {
      secure: window.isSecureContext,
      native: typeof crypto.randomUUID,
      ids: Array.from({ length: 100 }, () => randomUUID() as string),
    };
  }, script);
  expect(result.secure).toBe(false);
  expect(result.native).toBe("undefined");
  expect(new Set(result.ids).size).toBe(100);
  for (const id of result.ids) expect(id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
});
