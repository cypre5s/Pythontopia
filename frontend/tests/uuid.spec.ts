import { afterEach, expect, test, vi } from "vitest";
import { randomUUID } from "../src/shared/uuid";

afterEach(() => vi.unstubAllGlobals());

test("uses the native method with its Crypto receiver", () => {
  const native = { randomUUID() { expect(this).toBe(native); return "native-id"; } };
  vi.stubGlobal("crypto", native);
  expect(randomUUID()).toBe("native-id");
});

test("HTTP fallback preserves random bytes and sets UUID v4 version and variant", () => {
  vi.stubGlobal("crypto", { getRandomValues(bytes: Uint8Array) { bytes.fill(255); return bytes; } });
  expect(randomUUID()).toBe("ffffffff-ffff-4fff-bfff-ffffffffffff");
});

test("does not replace a missing secure random source with weak randomness", () => {
  vi.stubGlobal("crypto", undefined);
  expect(() => randomUUID()).toThrow("不支持安全随机数");
});
