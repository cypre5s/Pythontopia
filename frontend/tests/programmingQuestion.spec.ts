import { mount, flushPromises } from "@vue/test-utils";
import { it, expect, vi } from "vitest";
import PracticeQuestionCard from "../src/features/PracticeQuestionCard.vue";
import { runPython } from "../src/shared/pythonRunner";

vi.mock("../src/shared/pythonRunner", () => ({
  runPython: vi.fn().mockResolvedValue({ stdout: "hello\n", stderr: "" }),
  sameOutput: (actual: string, expected: string) => actual.trimEnd() === expected,
}));

it("public runs never emit a formal submission, while explicit submit does", async () => {
  const wrapper = mount(PracticeQuestionCard, {
    props: { question: { id: "q", title: "Echo", kind: "python", prompt: "Print input", options: [],
      samples: [{input:"hello",output:"hello"}], hints:["Use input()"] }, index:0, answer:"", code:"print(input())" },
    global: { stubs: { CodeEditor: true } },
  });
  await wrapper.findAll("button").find(b=>b.text()==="运行公开样例")!.trigger("click");
  await flushPromises();
  expect(runPython).toHaveBeenCalledWith("print(input())", "hello", expect.any(AbortSignal));
  expect(wrapper.text()).toContain("样例 1 · 通过");
  expect(wrapper.emitted("submit")).toBeUndefined();
  await wrapper.findAll("button").find(b=>b.text()==="提交代码")!.trigger("click");
  expect(wrapper.emitted("submit")).toEqual([["submit"]]);
  wrapper.unmount();
});
