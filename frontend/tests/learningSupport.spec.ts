import { afterEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { Comment, Fragment, Text, h, nextTick, ref } from "vue";
import LearningSupportPanel from "../src/features/LearningSupportPanel.vue";
import { api } from "../src/shared/api";
import { useSession } from "../src/shared/session";

function setup() {
  const pinia = createPinia();
  setActivePinia(pinia);
  const session = useSession();
  session.user = { id: 9, student_id: "124639931179929", name: "Fixture", role: "student" };
  session.classroomId = 4;
  return { pinia, session };
}
afterEach(() => {
  vi.restoreAllMocks();
  sessionStorage.clear();
  document.body.innerHTML = "";
});

describe("learning goals and character assistant", () => {
  it("does not reserve paper-action space for an empty conditional practice slot", async () => {
    const { pinia } = setup(), showActions = ref(false);
    const wrapper = mount(LearningSupportPanel, { props: { allowed: false, roleName: "练习场教练", goals: [], questionOutline: [{ id: "q1", title: "第一题" }] },
      slots: { "question-actions": () => [h(Fragment, null, [h(Text, "  "), showActions.value ? h("button", "提交试卷") : h(Comment), null])] }, global: { plugins: [pinia] } });
    await wrapper.findAll('[role="tab"]').find(tab => tab.text() === "快速跳转")!.trigger("click");
    expect(wrapper.find(".support-question-actions").exists()).toBe(false);
    showActions.value = true; await nextTick();
    expect(wrapper.get(".support-question-actions").text()).toBe("提交试卷");
    showActions.value = false; await nextTick();
    expect(wrapper.find(".support-question-actions").exists()).toBe(false);
    wrapper.unmount();
  });
  it("keeps question navigation and paper actions together without revealing unsubmitted correctness", async () => {
    const { pinia } = setup();
    const get = vi.spyOn(api, "get"), post = vi.spyOn(api, "post");
    const wrapper = mount(LearningSupportPanel, { props: { allowed: false, roleName: "练习场教练", goals: [], currentQuestionId: "q2", questionOutline: [
      { id: "q1", title: "第一题", state: "answered" }, { id: "q2", title: "第二题", state: "unanswered" },
    ] }, slots: { "question-actions": '<button>提交试卷</button>' }, global: { plugins: [pinia] } });
    const tabs = wrapper.findAll('[role="tab"]');
    expect(tabs.map(tab => tab.text())).toEqual(["题目列表", "快速跳转", "练习场教练"]);
    await tabs[1]!.trigger("click");
    const directory = wrapper.get('[aria-label="题目快速跳转"]');
    expect(wrapper.get('.support-question-actions').text()).toContain("提交试卷");
    expect(directory.text()).toContain("已作答");
    expect(directory.findAll("button").every(button => button.attributes("data-state") !== "accepted")).toBe(true);
    const questions = directory.findAll("button");
    expect(questions[1]!.attributes("aria-current")).toBe("step");
    await questions[0]!.trigger("click");
    expect(wrapper.emitted("selectQuestion")).toEqual([["q1"]]);
    expect(get).not.toHaveBeenCalled();
    expect(post).not.toHaveBeenCalled();
    wrapper.unmount();
  });
  it("navigates original-page outlines and keeps position distinct from the aggregate learning goal", async () => {
    const { pinia } = setup();
    const get = vi.spyOn(api, "get"), post = vi.spyOn(api, "post");
    const wrapper = mount(LearningSupportPanel, { attachTo: document.body, props: {
      allowed: false, roleName: "图书管理员", pageNo: 34,
      goals: [{ title: "阅读课件", progress: { value: 1, max: 2, label: "已阅读 1 / 2 页" }, items: [{ title: "计算思维", pageNo: 34, completed: true }, { title: "问题求解", pageNo: 42 }] }],
      outline: [{ pageNo: 34, title: "计算思维" }, { pageNo: 42, title: "问题求解", important: true, highlightLabel: "课堂重点" }],
    }, global: { plugins: [pinia] } });
    expect(wrapper.get('progress').attributes("value")).toBe("1");
    expect(wrapper.get('progress').attributes("max")).toBe("2");
    const tabs = wrapper.findAll('[role="tab"]');
    expect(tabs.map(tab => tab.text())).toEqual(["学习目标", "快速跳转", "图书管理员"]);
    await tabs[0]!.trigger("keydown", { key: "ArrowRight" });
    expect(document.activeElement).toBe(tabs[1]!.element);
    expect(wrapper.get(".support-outline").attributes("hidden")).toBeUndefined();
    const pages = wrapper.get('[aria-label="文档页面目录"]').findAll("button");
    expect(pages[0]!.attributes("aria-current")).toBe("page");
    expect(pages[1]!.text()).toContain("课堂重点");
    await pages[1]!.trigger("click");
    expect(wrapper.emitted("selectPage")).toEqual([[42]]);
    await tabs[1]!.trigger("keydown", { key: "End" });
    expect(document.activeElement).toBe(tabs[2]!.element);
    expect(get).not.toHaveBeenCalled();
    expect(post).not.toHaveBeenCalled();
    wrapper.unmount();
  });
  it("keeps the composer mounted across accessible tabs without sending a question", async () => {
    const { pinia } = setup();
    vi.spyOn(api, "get").mockResolvedValue({ available: true, reason: "" });
    const post = vi.spyOn(api, "post");
    const wrapper = mount(LearningSupportPanel, {
      attachTo: document.body,
      props: { activityId: "ch1-lesson", allowed: true, roleName: "图书管理员", portraitUrl: "/librarian.png", goals: [{ title: "理解抽象", pageNo: 42, completed: true }] },
      slots: { "goals-footer": '<button>完成阅读</button>' },
      global: { plugins: [pinia] },
    });
    await flushPromises();
    expect(wrapper.get('.support-chat').attributes("hidden")).toBeDefined();
    expect(wrapper.get('.support-chat').attributes("inert")).toBeDefined();
    expect(wrapper.get('[aria-label="本章阅读目标"]').text()).toContain("阅读第42页");
    await wrapper.get('.support-goal-page').trigger("click");
    expect(wrapper.emitted("selectPage")?.[0]).toEqual([42]);
    expect(wrapper.get('.support-goals').text()).toContain("完成阅读");
    const tabs = wrapper.findAll('[role="tab"]');
    await tabs[1]!.trigger("click");
    const input = wrapper.get('textarea');
    expect(input.attributes("placeholder")).toBe("向助教提问");
    expect(wrapper.get('.assistant-avatar').attributes("alt")).toBe("");
    await input.setValue("这一页有什么含义？");
    await tabs[0]!.trigger("click");
    await tabs[0]!.trigger("keydown", { key: "ArrowRight" });
    expect(document.activeElement).toBe(tabs[1]!.element);
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe("这一页有什么含义？");
    expect(wrapper.get('.support-goals').attributes("hidden")).toBeDefined();
    expect(post).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it("offers the librarian for standalone materials without sending a question", async () => {
    const { pinia } = setup();
    const get = vi.spyOn(api, "get").mockResolvedValue({ available: true, reason: "" }), post = vi.spyOn(api, "post");
    const wrapper = mount(LearningSupportPanel, { props: { materialId: 186, allowed: true, roleName: "图书管理员", goals: [] }, global: { plugins: [pinia] } });
    await flushPromises();
    await wrapper.findAll('[role="tab"]')[1]!.trigger("click");
    expect(wrapper.get('.support-chat').text()).toContain("图书管理员");
    expect(wrapper.find('textarea').exists()).toBe(true);
    expect(get).toHaveBeenCalledWith('/assistant/status');
    expect(post).not.toHaveBeenCalled();
    wrapper.unmount();
  });
});

it("shows question types and statuses in the list and groups numbered jumps without changing IDs", async () => {
  const {pinia} = setup();
  const wrapper = mount(LearningSupportPanel, {props:{allowed:false, roleName:"助教", goals:[], questionOutline:[{id:"a",title:"选择",kind:"single_choice",state:"accepted"},{id:"b",title:"代码",kind:"python",state:"wrong"},{id:"c",title:"判断",kind:"boolean",state:"pending"}]},global:{plugins:[pinia]}});
  expect(wrapper.get('[aria-label="题目列表"]').text()).toContain("编程题");
  expect(wrapper.findAll('.question-status-icon').map(item=>item.attributes('data-state'))).toEqual(['accepted','wrong','pending']);
  const jump = wrapper.get('[aria-label="题目快速跳转"]');
  expect(jump.findAll('.question-jump-group')).toHaveLength(3);
  await jump.findAll('button')[1]!.trigger('click');
  expect(wrapper.emitted('selectQuestion')).toEqual([['b']]);
  expect(jump.findAll('button')[1]!.attributes('aria-label')).toContain('错误');
  wrapper.unmount();
});
it("offers video timestamps in quick jump without marking progress", async () => {
  const {pinia} = setup();
  const post=vi.spyOn(api,'post');
  const wrapper=mount(LearningSupportPanel,{props:{allowed:false,roleName:'助教',goals:[],timeOutline:[{timeSeconds:0,title:'视频开始'},{timeSeconds:85,title:'重点片段'}]},global:{plugins:[pinia]}});
  await wrapper.findAll('[role="tab"]')[1]!.trigger('click');
  const items=wrapper.get('[aria-label="视频片段跳转"]').findAll('button');
  expect(items[1]!.text()).toContain('1:25');await items[1]!.trigger('click');
  expect(wrapper.emitted('selectTime')).toEqual([[85]]);expect(post).not.toHaveBeenCalled();wrapper.unmount();
});
