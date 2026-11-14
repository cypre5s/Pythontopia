import { describe, it, expect, vi } from "vitest";
import { mount, flushPromises } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import AssistantPanel from "../src/features/AssistantPanel.vue";
import { api } from "../src/shared/api";
import { useSession } from "../src/shared/session";
describe("explicit isolated assistant stream fixture", () => {
  it('sends a frozen state only with an explicit question and retains failed input', async () => {
    sessionStorage.clear();
    const pinia = createPinia(); setActivePinia(pinia);
    const session = useSession(); session.user = { id: 9, student_id: 'fixture', name: '小林', role: 'student' }; session.classroomId = 4;
    const get = vi.spyOn(api, 'get').mockResolvedValue({ available: true, reason: '' });
    const post = vi.spyOn(api, 'post').mockResolvedValue({ id: 'state-session' });
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ detail: '测试失败' }), { status: 503 }));
    const wrapper = mount(AssistantPanel, { props: { activityId: 'practice', allowed: true, roleName: '教练', state: { question_id: 'one', answer: 'A' } }, global: { plugins: [pinia] } });
    await flushPromises();
    await wrapper.setProps({ state: { question_id: 'two', answer: 'B' } });
    expect(fetch).not.toHaveBeenCalled(); expect(post).not.toHaveBeenCalled();
    await wrapper.get('textarea').setValue('为什么'); await wrapper.get('form').trigger('submit'); await flushPromises();
    const payload = JSON.parse(fetch.mock.calls[0]![1]!.body as string);
    expect(payload.state).toEqual({ question_id: 'two', answer: 'B' });
    expect(payload.expected_revision).toBe(0); expect(payload.client_request_id).toBeTruthy();
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('为什么');
    wrapper.unmount(); get.mockRestore(); post.mockRestore(); fetch.mockRestore(); sessionStorage.clear();
  });
  it("keeps overlong questions intact, reports the limit only when exceeded and never sends them", async () => {
    sessionStorage.clear();
    const pinia = createPinia(); setActivePinia(pinia);
    const session = useSession();
    session.user = { id: 9, student_id: "124639931179929", name: "Fixture", role: "student" }; session.classroomId = 4;
    const get = vi.spyOn(api, "get").mockResolvedValue({ available: true, reason: "" });
    const post = vi.spyOn(api, "post"), fetch = vi.spyOn(globalThis, "fetch");
    const wrapper = mount(AssistantPanel, { props: { activityId: "ch1-lesson", allowed: true, roleName: "图书管理员" }, global: { plugins: [pinia] } });
    await flushPromises();
    const input = wrapper.get("textarea"), send = wrapper.get('button[aria-label="发送"]');
    expect(wrapper.find(".assistant-character-count").exists()).toBe(false);
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(input.attributes("maxlength")).toBeUndefined();
    expect(send.find("svg").exists()).toBe(true);
    expect(send.text()).toBe("");
    const longQuestion = "问".repeat(2001);
    await input.setValue(longQuestion);
    expect((input.element as HTMLTextAreaElement).value).toBe(longQuestion);
    expect(input.attributes("aria-invalid")).toBe("true");
    expect(wrapper.get('[role="alert"]').text()).toContain("请删减 1 字");
    expect(input.attributes("aria-describedby")).toBe(wrapper.get('[role="alert"]').attributes("id"));
    expect(send.attributes("disabled")).toBeDefined();
    await wrapper.get("form").trigger("submit"); await flushPromises();
    expect(post).not.toHaveBeenCalled(); expect(fetch).not.toHaveBeenCalled();
    await input.setValue("问".repeat(2000));
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(send.attributes("disabled")).toBeUndefined();
    await input.setValue("🙂".repeat(2000));
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(send.attributes("disabled")).toBeUndefined();
    wrapper.unmount(); get.mockRestore(); post.mockRestore(); fetch.mockRestore(); sessionStorage.clear();
  });
  it("ignores delayed history from a classroom that has already been left", async () => {
    sessionStorage.clear();
    const pinia = createPinia();
    setActivePinia(pinia);
    const session = useSession();
    session.user = { id: 9, student_id: "124639931179929", name: "Fixture", role: "student" };
    session.classroomId = 4;
    sessionStorage.setItem("assistant:9:4:ch1-lesson", "old-class-history");
    let finishOld!: (value: never) => void;
    const get = vi.spyOn(api, "get").mockImplementation(async path => {
      if (path === "/assistant/status") return { available: true, reason: "" } as never;
      return new Promise(resolve => { finishOld = resolve; });
    });
    const wrapper = mount(AssistantPanel, { props: { activityId: "ch1-lesson", allowed: true, roleName: "图书管理员" }, global: { plugins: [pinia] } });
    await flushPromises();
    session.classroomId = 5;
    await flushPromises();
    finishOld({ messages: [{ role: "assistant", content: "旧班级内容" }] } as never);
    await flushPromises();
    expect(wrapper.text()).not.toContain("旧班级内容");
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe("");
    wrapper.unmount();
    get.mockRestore();
    sessionStorage.clear();
  });
  it("restores history by user, classroom and stable activity ID", async () => {
    sessionStorage.clear();
    const pinia = createPinia();
    setActivePinia(pinia);
    const session = useSession();
    session.user = { id: 9, student_id: "124639931179929", name: "Fixture", role: "student" };
    session.classroomId = 4;
    sessionStorage.setItem("assistant:9:4:ch1-lesson", "lesson-history");
    sessionStorage.setItem("assistant:9:4:ch1-practice", "practice-history");
    const get = vi.spyOn(api, "get").mockImplementation(async path => {
      if (path === "/assistant/status") return { available: true, reason: "" } as never;
      return { messages: [{ role: "assistant", content: path.endsWith("lesson-history") ? "阅读历史" : "练习历史" }] } as never;
    });
    const post = vi.spyOn(api, "post");
    const wrapper = mount(AssistantPanel, { props: { activityId: "ch1-lesson", allowed: true, roleName: "图书管理员" }, global: { plugins: [pinia] } });
    await flushPromises();
    expect(wrapper.text()).toContain("阅读历史");
    expect(wrapper.text()).not.toContain("练习历史");
    expect(get).toHaveBeenCalledWith("/assistant/sessions/lesson-history");
    expect(get).not.toHaveBeenCalledWith("/assistant/sessions/practice-history");
    expect(post).not.toHaveBeenCalled();
    wrapper.unmount();
    get.mockRestore(); post.mockRestore(); sessionStorage.clear();
  });
  it.each(["activity", "material"])("only posts when asked and renders split SSE for %s context", async (target) => {
    sessionStorage.clear();
    const pinia = createPinia();
    setActivePinia(pinia);
    const session = useSession();
    session.user = {
      id: 9,
      student_id: "124639931179929",
      name: "Fixture",
      role: "student" };
    session.classroomId = 4;
    const get = vi
      .spyOn(api, "get")
      .mockResolvedValue({ available: true, reason: "" });
    const post = vi
      .spyOn(api, "post")
      .mockResolvedValue({ id: "fixture-session" });
    const encoder = new TextEncoder();
    const chunks = [
      'event: meta\ndata: {"request_id":"fixture-request"}\n\nevent: to',
      'ken\ndata: {"text":"先观察"}\n\nevent: token\ndata: {"text":"这一页。"}\n\nevent: references\ndata: {"items":[{"material_id":186,"page_no":42,"title":"真实课程引用"}]}\n\nevent: done\ndata: {"status":"completed"}\n\n',
    ];
    const fetch = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        new ReadableStream({
          start(controller) {
            chunks.forEach((c) => controller.enqueue(encoder.encode(c)));
            controller.close();
          } }),
        { status: 200, headers: { "Content-Type": "text/event-stream" } },
      ),
    );
    HTMLElement.prototype.scrollTo = vi.fn();
    const wrapper = mount(AssistantPanel, {
      props: {
        ...(target === "activity" ? { activityId: "ch1-lesson" } : { materialId: 186 }),
        allowed: true,
        pageNo: 42,
        roleName: "镇长 Py" },
      global: { plugins: [pinia] } });
    await flushPromises();
    expect(fetch).not.toHaveBeenCalled();
    await wrapper.find("textarea").setValue("什么是抽象？");
    await wrapper.find("form").trigger("submit");
    await flushPromises();
    expect(post).toHaveBeenCalledWith("/assistant/sessions", {
      classroom_id: 4,
      ...(target === "activity" ? { activity_id: "ch1-lesson" } : { material_id: 186 }) });
    expect(wrapper.text()).toContain("先观察这一页。");
    expect(wrapper.text()).toContain("真实课程引用");
    await wrapper.find(".citation").trigger("click");
    expect(wrapper.emitted("cite")?.[0]).toEqual([186, 42]);
    wrapper.unmount();
    get.mockRestore();
    post.mockRestore();
    fetch.mockRestore();
  });
});

it("starts a new conversation only after success, preserves failed drafts and copies assistant Markdown only", async () => {
  sessionStorage.clear();const pinia=createPinia();setActivePinia(pinia);const session=useSession();
  session.user={id:9,student_id:"fixture",name:"小林",role:"student"};session.classroomId=4;
  sessionStorage.setItem('assistant:9:4:lesson','old');
  const get=vi.spyOn(api,'get').mockImplementation(async path => (path==='/assistant/status'?{available:true,reason:''}:{messages:[{role:'user',content:'你好'},{role:'assistant',content:'**先尝试**\n\n1. 读取输入'}]}) as never);
  const post=vi.spyOn(api,'post').mockRejectedValueOnce(new Error('创建失败')).mockResolvedValue({id:'new'});
  const fetch=vi.spyOn(globalThis,'fetch');const writeText=vi.fn().mockResolvedValue(undefined);
  Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText}});
  const wrapper=mount(AssistantPanel,{props:{activityId:'lesson',allowed:true,roleName:'教练'},global:{plugins:[pinia]}});await flushPromises();
  expect(wrapper.get('article.user .message-author').text()).toBe('小林');expect(wrapper.find('article.user button').exists()).toBe(false);
  expect(wrapper.get('.assistant-markdown strong').text()).toBe('先尝试');
  await wrapper.get('[aria-label="复制回复"]').trigger('click');await flushPromises();expect(writeText).toHaveBeenCalledWith('**先尝试**\n\n1. 读取输入');
  await wrapper.get('textarea').setValue('保留草稿');await wrapper.get('[aria-label="开启新对话"]').trigger('click');await flushPromises();
  expect(wrapper.text()).toContain('先尝试');expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('保留草稿');expect(sessionStorage.getItem('assistant:9:4:lesson')).toBe('old');
  await wrapper.get('[aria-label="开启新对话"]').trigger('click');await flushPromises();expect(wrapper.findAll('article')).toHaveLength(0);expect(sessionStorage.getItem('assistant:9:4:lesson')).toBe('new');expect(fetch).not.toHaveBeenCalled();
  wrapper.unmount();get.mockRestore();post.mockRestore();fetch.mockRestore();sessionStorage.clear();
});
it("ignores IME and Shift+Enter and isolates a late new-session response after switching class", async () => {
  sessionStorage.clear();const pinia=createPinia();setActivePinia(pinia);const session=useSession();session.user={id:9,student_id:'fixture',name:'小林',role:'student'};session.classroomId=4;
  const get=vi.spyOn(api,'get').mockResolvedValue({available:true,reason:''});let finish!: (value:never)=>void;
  const post=vi.spyOn(api,'post').mockImplementation(()=>new Promise(resolve=>{finish=resolve}));
  const wrapper=mount(AssistantPanel,{props:{activityId:'lesson',allowed:true,roleName:'教练'},global:{plugins:[pinia]}});await flushPromises();
  const input=wrapper.get('textarea');await input.setValue('问题');await input.trigger('keydown',{key:'Enter',isComposing:true});await input.trigger('keydown',{key:'Enter',shiftKey:true});expect(post).not.toHaveBeenCalled();
  await wrapper.get('[aria-label="开启新对话"]').trigger('click');session.classroomId=5;await flushPromises();finish({id:'old-class'} as never);await flushPromises();expect(sessionStorage.getItem('assistant:9:5:lesson')).toBeNull();expect(sessionStorage.getItem('assistant:9:4:lesson')).toBeNull();
  wrapper.unmount();get.mockRestore();post.mockRestore();sessionStorage.clear();
});

it("does not restore old history over a newly created conversation", async () => {
  sessionStorage.clear();const pinia=createPinia();setActivePinia(pinia);const session=useSession();session.user={id:9,student_id:'fixture',name:'小林',role:'student'};session.classroomId=4;sessionStorage.setItem('assistant:9:4:lesson','old');
  let finish!: (value:never)=>void;const get=vi.spyOn(api,'get').mockImplementation(async path=>path==='/assistant/status'?{available:true,reason:''} as never:new Promise(resolve=>{finish=resolve}));
  const post=vi.spyOn(api,'post').mockResolvedValue({id:'new'});const wrapper=mount(AssistantPanel,{props:{activityId:'lesson',allowed:true,roleName:'教练'},global:{plugins:[pinia]}});await flushPromises();await wrapper.get('[aria-label="开启新对话"]').trigger('click');await flushPromises();finish({messages:[{role:'assistant',content:'旧会话'}]} as never);await flushPromises();expect(wrapper.text()).not.toContain('旧会话');expect(sessionStorage.getItem('assistant:9:4:lesson')).toBe('new');wrapper.unmount();get.mockRestore();post.mockRestore();sessionStorage.clear();
});
