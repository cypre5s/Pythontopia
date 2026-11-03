import { describe, it, expect, vi, afterEach } from "vitest";
import { mount, flushPromises } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import LessonWorkspace from "../src/features/LessonWorkspace.vue";
import PracticeWorkspace from "../src/features/PracticeWorkspace.vue";
import { api } from "../src/shared/api";
import { useSession } from "../src/shared/session";
function setup(compact = false) {
  const pinia = createPinia();
  setActivePinia(pinia);
  const session = useSession();
  session.user = {
    id: 1,
    name: "fixture",
    email: "fixture@example.test",
    role: "student",
  };
  session.classroomId = 2;
  session.world = {
    player: { map_key: "town_hub", tile_x: 22, tile_y: 36, direction: "down" },
    npcs: [],
    quests: [],
    chapters: [],
    flags: [],
  };
  vi.stubGlobal(
    "matchMedia",
    vi.fn(() => ({
      matches: compact,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
  return { pinia, session };
}
afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  localStorage.clear();
  sessionStorage.clear();
  document.body.innerHTML = "";
});
describe("learning workspace usability", () => {
  it("makes inactive compact regions hidden and inert while preserving reading position across the assistant view", async () => {
    const { pinia, session } = setup(true);
    vi.spyOn(session, "activity").mockResolvedValue({
      id: "lesson",
      chapter_id: 179,
      title: "真实文本夹具",
      kind: "lesson",
      version: 1,
      npc_key: "mayor_py",
      assistant_allowed: true,
      questions: [],
      material: {
        id: 186,
        title: "课件",
        kind: "text",
        version: 1,
        status: "ready",
        pages: [
          { id: 1, page_no: 41, title: "第一页", text: "原课件文本" },
          { id: 2, page_no: 42, title: "第二页", text: "第二页文本" },
        ],
      },
    });
    const wrapper = mount(LessonWorkspace, {
      attachTo: document.body,
      props: { activityId: "lesson" },
      global: {
        plugins: [pinia],
        stubs: {
          AssistantPanel: {
            template:
              '<section class="assistant-panel"><textarea aria-label="测试助教问题" /></section>',
          },
        },
      },
    });
    await flushPromises();
    expect(wrapper.find(".assistant-panel").attributes("hidden")).toBeDefined();
    expect(wrapper.find(".assistant-panel").attributes("inert")).toBeDefined();
    await wrapper
      .findAll("button")
      .find((b) => b.text() === "下一页 →")!
      .trigger("click");
    await wrapper
      .findAll("button")
      .find((b) => b.text() === "向助教提问")!
      .trigger("click");
    expect(wrapper.find(".lesson-content").attributes("hidden")).toBeDefined();
    expect(wrapper.find(".lesson-content").attributes("inert")).toBeDefined();
    expect(document.activeElement?.id).toBe("lesson-assistant-region");
    await wrapper
      .findAll("button")
      .find((b) => b.text() === "课程内容")!
      .trigger("click");
    expect(
      wrapper.find(".lesson-content").attributes("hidden"),
    ).toBeUndefined();
    expect(wrapper.find(".lesson-text").text()).toContain("第二页文本");
    expect(document.activeElement?.id).toBe("lesson-reading-region");
    wrapper.unmount();
  });
  it("requires a deliberate boolean choice and sends false only after the student selects it", async () => {
    const { pinia, session } = setup();
    vi.spyOn(session, "activity").mockResolvedValue({
      id: "practice",
      chapter_id: 179,
      title: "判断题夹具",
      kind: "practice",
      version: 1,
      npc_key: "mayor_py",
      assistant_allowed: false,
      material: null,
      questions: [
        {
          id: "boolean-q",
          title: "判断一个命题",
          kind: "boolean",
          prompt: "请选择你的判断。",
          options: [],
          samples: [],
          version: 1,
        },
      ],
    });
    const post = vi
      .spyOn(api, "post")
      .mockResolvedValue({
        id: "s1",
        activity_id: "practice",
        question_id: "boolean-q",
        version: 1,
        mode: "submit",
        status: "wrong_answer",
        feedback: { detail: "请重新思考" },
        created_at: "2026-11-03T00:00:00Z",
      });
    const wrapper = mount(PracticeWorkspace, {
      props: { activityId: "practice" },
      global: { plugins: [pinia], stubs: { AssistantPanel: true } },
    });
    await flushPromises();
    const submit = wrapper
      .findAll("button")
      .find((b) => b.text() === "正式提交")!;
    expect(submit.attributes("disabled")).toBeDefined();
    await submit.trigger("click");
    expect(post).not.toHaveBeenCalled();
    await wrapper.find('input[value="false"]').setValue();
    expect(submit.attributes("disabled")).toBeUndefined();
    await submit.trigger("click");
    await flushPromises();
    expect(post).toHaveBeenCalledWith(
      "/submissions",
      expect.objectContaining({ answer: false, question_id: "boolean-q" }),
    );
    wrapper.unmount();
  });
});
