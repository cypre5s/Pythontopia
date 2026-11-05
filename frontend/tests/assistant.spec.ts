import { describe, it, expect, vi } from "vitest";
import { mount, flushPromises } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import AssistantPanel from "../src/features/AssistantPanel.vue";
import { api } from "../src/shared/api";
import { useSession } from "../src/shared/session";
describe("explicit isolated assistant stream fixture", () => {
  it("restores separate assistant histories for two versions of the same activity", async () => {
    sessionStorage.clear();
    const pinia = createPinia();
    setActivePinia(pinia);
    const session = useSession();
    session.user = { id: 9, email: "fixture@example.test", name: "Fixture", role: "student" };
    session.classroomId = 4;
    sessionStorage.setItem("assistant:9:4:ch1-lesson:v1", "version-one");
    sessionStorage.setItem("assistant:9:4:ch1-lesson:v2", "version-two");
    const get = vi.spyOn(api, "get").mockImplementation(async path => {
      if (path === "/assistant/status") return { available: true, reason: "" } as never;
      return { messages: [{ role: "assistant", content: path.endsWith("version-two") ? "第二版历史" : "第一版历史" }] } as never;
    });
    const post = vi.spyOn(api, "post");
    const second = mount(AssistantPanel, { props: { activityId: "ch1-lesson", activityVersion: 2, allowed: true, roleName: "图书管理员" }, global: { plugins: [pinia] } });
    await flushPromises();
    expect(second.text()).toContain("第二版历史");
    expect(second.text()).not.toContain("第一版历史");
    expect(get).not.toHaveBeenCalledWith("/assistant/sessions/version-one");
    second.unmount();
    const first = mount(AssistantPanel, { props: { activityId: "ch1-lesson", activityVersion: 1, allowed: true, roleName: "图书管理员" }, global: { plugins: [pinia] } });
    await flushPromises();
    expect(first.text()).toContain("第一版历史");
    expect(first.text()).not.toContain("第二版历史");
    expect(post).not.toHaveBeenCalled();
    first.unmount();
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
      role: "student",
    };
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
      'ken\ndata: {"text":"先观察"}\n\nevent: token\ndata: {"text":"这一页。"}\n\nevent: references\ndata: {"items":[{"material_id":186,"version":1,"page_no":42,"title":"真实课程引用"}]}\n\nevent: done\ndata: {"status":"completed"}\n\n',
    ];
    const fetch = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        new ReadableStream({
          start(controller) {
            chunks.forEach((c) => controller.enqueue(encoder.encode(c)));
            controller.close();
          },
        }),
        { status: 200, headers: { "Content-Type": "text/event-stream" } },
      ),
    );
    HTMLElement.prototype.scrollTo = vi.fn();
    const wrapper = mount(AssistantPanel, {
      props: {
        activityId: "ch1-lesson",
        allowed: true,
        pageNo: 42,
        roleName: "镇长 Py",
      },
      global: { plugins: [pinia] },
    });
    await flushPromises();
    expect(fetch).not.toHaveBeenCalled();
    await wrapper.find("textarea").setValue("什么是抽象？");
    await wrapper.find("form").trigger("submit");
    await flushPromises();
    expect(post).toHaveBeenCalledWith("/assistant/sessions", {
      classroom_id: 4,
      activity_id: "ch1-lesson",
    });
    expect(wrapper.text()).toContain("先观察这一页。");
    expect(wrapper.text()).toContain("真实课程引用");
    await wrapper.find(".citation").trigger("click");
    expect(wrapper.emitted("cite")?.[0]).toEqual([186, 1, 42]);
    wrapper.unmount();
    get.mockRestore();
    post.mockRestore();
    fetch.mockRestore();
  });
});
