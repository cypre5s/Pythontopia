import { describe, it, expect, vi } from "vitest";
import { mount, flushPromises } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import AssistantPanel from "../src/features/AssistantPanel.vue";
import { api } from "../src/shared/api";
import { useSession } from "../src/shared/session";
describe("explicit isolated assistant stream fixture", () => {
  it("keeps overlong questions intact, reports the limit only when exceeded and never sends them", async () => {
    sessionStorage.clear();
    const pinia = createPinia(); setActivePinia(pinia);
    const session = useSession();
    session.user = { id: 9, email: "fixture@example.test", name: "Fixture", role: "student" }; session.classroomId = 4;
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
    session.user = { id: 9, email: "fixture@example.test", name: "Fixture", role: "student" };
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
    session.user = { id: 9, email: "fixture@example.test", name: "Fixture", role: "student" };
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
  it("only posts when asked and renders split SSE tokens and material references", async () => {
    sessionStorage.clear();
    const pinia = createPinia();
    setActivePinia(pinia);
    const session = useSession();
    session.user = {
      id: 9,
      email: "fixture@example.test",
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
        activityId: "ch1-lesson",
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
      activity_id: "ch1-lesson" });
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
