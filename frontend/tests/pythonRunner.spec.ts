import { describe, it, expect } from "vitest";
import { runPython, sameOutput } from "../src/shared/pythonRunner";

describe("public sample execution", () => {
  it("normalizes terminal newline without hiding incorrect internal whitespace", () => {
    expect(sameOutput("hello\r\n", "hello")).toBe(true);
    expect(sameOutput(" hello", "hello")).toBe(false);
    expect(sameOutput("a  b", "a b")).toBe(false);
  });
  it("cancels before creating an execution context", async () => {
    const controller = new AbortController();
    controller.abort();
    await expect(runPython("while True: pass", "", controller.signal)).rejects.toThrow("已停止");
    expect(document.querySelectorAll("iframe")).toHaveLength(0);
  });
});
