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
  expect(wrapper.text()).toContain("通过");
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

function codingCard() {
  return mount(PracticeQuestionCard, {
    props: { question: {id:'pending-cases',title:'Two cases',kind:'python',prompt:'Echo',options:[],samples:[{input:'first',output:'one'},{input:'second',output:'two'}]}, index:0, answer:'', code:'print(input())' },
    global: { stubs: { CodeEditor:true } },
  });
}
it('keeps one busy action and skeleton until every public case finishes', async () => {
  let first!: (value: {stdout:string;stderr:string}) => void;
  let second!: (value: {stdout:string;stderr:string}) => void;
  vi.mocked(runPython).mockReset().mockImplementationOnce(() => new Promise(resolve => {first=resolve;})).mockImplementationOnce(() => new Promise(resolve => {second=resolve;}));
  const wrapper=codingCard();
  await wrapper.findAll('button').find(b=>b.text()==='运行公开样例')!.trigger('click');
  expect(wrapper.find('.coding-actions').findAll('button')).toHaveLength(1);
  expect(wrapper.find('.judging-action').attributes('disabled')).toBeDefined();
  expect(wrapper.find('.result-skeleton').exists()).toBe(true);
  first({stdout:'one',stderr:''});await flushPromises();
  expect(wrapper.find('.result-skeleton').exists()).toBe(true);
  expect(wrapper.find('.coding-outcome').exists()).toBe(false);
  second({stdout:'incorrect',stderr:''});await flushPromises();
  expect(wrapper.find('.result-skeleton').exists()).toBe(false);
  expect(wrapper.find('.coding-outcome').text()).toBe('解答错误');
  expect(wrapper.find('.case-tabs button[aria-pressed=true]').text()).toBe('Case 2');
  expect(wrapper.find('.coding-tests').text()).toContain('实际输出incorrect');
  expect(wrapper.find('.coding-tests').text()).toContain('预期输出two');
  expect(wrapper.emitted('submit')).toBeUndefined();
  wrapper.unmount();
});
it('shows execution errors with the failed input and separates formal results', async () => {
  vi.mocked(runPython).mockReset().mockResolvedValue({stdout:'',stderr:'IndentationError: expected an indented block'});
  const wrapper=codingCard();
  await wrapper.findAll('button').find(b=>b.text()==='运行公开样例')!.trigger('click');await flushPromises();
  expect(wrapper.find('.coding-outcome').text()).toBe('执行出错');
  expect(wrapper.find('.coding-error').text()).toContain('IndentationError');
  expect(wrapper.find('.coding-tests').text()).toContain('执行时的输入first');
  await wrapper.setProps({busy:true});
  expect(wrapper.find('.result-skeleton').exists()).toBe(true);
  await wrapper.setProps({busy:false,result:{id:'s',activity_id:'a',question_id:'pending-cases',mode:'submit',status:'wrong_answer',created_at:'',feedback:{detail:'隐藏用例未通过',samples:[{status:'accepted',stdout:'one',stderr:'',compile_output:''}]}}});
  expect(wrapper.find('.result-origin').text()).toBe('正式提交');
  expect(wrapper.find('.coding-outcome').text()).toBe('解答错误');
  expect(wrapper.find('.coding-tests').text()).not.toContain('IndentationError');
  wrapper.unmount();
});
it('aborts a run when the user stops it and on unmount', async () => {
  let signal: AbortSignal | undefined;
  vi.mocked(runPython).mockReset().mockImplementation((_code,_input,value)=>new Promise((_resolve,reject)=>{signal=value;value?.addEventListener('abort',()=>reject(new Error('已停止运行')));}));
  const wrapper=codingCard();
  await wrapper.findAll('button').find(b=>b.text()==='运行公开样例')!.trigger('click');
  await wrapper.find('.stop-run').trigger('click');await flushPromises();
  expect(signal?.aborted).toBe(true);expect(wrapper.text()).toContain('已停止运行');
  await wrapper.findAll('button').find(b=>b.text()==='运行公开样例')!.trigger('click');
  wrapper.unmount();expect(signal?.aborted).toBe(true);await flushPromises();
});

it('keeps the pending state for a queued formal submission, including locked review', async () => {
  const wrapper=codingCard();
  const result={id:'s',activity_id:'a',question_id:'pending-cases',mode:'submit' as const,status:'queued' as const,created_at:'',feedback:{}};
  await wrapper.setProps({result,readOnly:true});
  expect(wrapper.find('.result-skeleton').exists()).toBe(true);
  expect(wrapper.find('.coding-actions').text()).toContain('正在判题');
  await wrapper.setProps({result:{...result,status:'accepted'}});
  expect(wrapper.find('.result-skeleton').exists()).toBe(false);
  expect(wrapper.find('.coding-outcome').text()).toBe('通过');
  expect(wrapper.find('.coding-actions').exists()).toBe(false);
  wrapper.unmount();
});

it("shows only the centered prompt in results before execution and keeps cases available separately", async () => {
  const wrapper = mount(PracticeQuestionCard, { props: { question: { id: "empty", title: "Empty", kind: "python", prompt: "Test", options: [], samples: [{input:"hello",output:"hello"}] }, index:0, answer:"", code:"print(input())" }, global: { stubs: { CodeEditor:true } } });
  await wrapper.findAll("button").find(button => button.text() === "测试结果")!.trigger("click");
  expect(wrapper.get(".coding-empty-result").text()).toBe("请先执行代码");
  expect(wrapper.find(".case-tabs").exists()).toBe(false);
  await wrapper.findAll("button").find(button => button.text() === "测试用例")!.trigger("click");
  expect(wrapper.get(".case-tabs").text()).toContain("Case 1");
  wrapper.unmount();
});
