import { describe, it, expect, vi } from "vitest";
import { mount, flushPromises } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import AssistantPanel from "../src/features/AssistantPanel.vue";
import { api } from "../src/shared/api";
import { useSession } from "../src/shared/session";
describe("explicit isolated assistant stream fixture", () => {
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
