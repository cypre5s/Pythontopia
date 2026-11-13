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

it("switches Case tabs and moves actions into the workspace header", async () => {
  const target = document.createElement("div");
  target.id = "test-coding-toolbar";
  document.body.append(target);
  const wrapper = mount(PracticeQuestionCard, {
    props: { question: { id:"cases", title:"Cases", kind:"python", prompt:"Compare", options:[],
      samples:[{input:"first",output:"one"},{input:"second",output:"two"}] },
      index:0, answer:"", code:"print(input())", toolbarTarget:"#test-coding-toolbar" },
    global: { stubs: { CodeEditor:true } },
  });
  expect(target.textContent).toContain("提交代码");
  await wrapper.findAll("button").find(b=>b.text()==="Case 2")!.trigger("click");
  expect(wrapper.find(".coding-tests").text()).toContain("second");
  expect(wrapper.find(".coding-tests").text()).not.toContain("first");
  const width = wrapper.find('[aria-label="调整题目与代码宽度"]');
  await width.trigger("keydown", {key:"End"});
  expect(width.attributes("aria-valuenow")).toBe("70");
  await width.trigger("keydown", {key:"ArrowLeft"});
  expect(width.attributes("aria-valuenow")).toBe("68");
  const height = wrapper.find('[aria-label="调整代码与用例高度"]');
  await height.trigger("keydown", {key:"Home"});
  expect(height.attributes("aria-valuenow")).toBe("25");
  expect(JSON.parse(localStorage.getItem("pythontopia-coding-layout")!)).toEqual({width:68,height:25});
  await wrapper.setProps({readOnly:true});
  expect(target.querySelectorAll("button")).toHaveLength(0);
  wrapper.unmount();
  expect(target.textContent).toBe("");
  target.remove();
  localStorage.removeItem("pythontopia-coding-layout");
});
