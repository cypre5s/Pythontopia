import { describe, it, expect, vi, afterEach } from "vitest";
import { mount, flushPromises } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import LessonWorkspace from "../src/features/LessonWorkspace.vue";
import PracticeWorkspace from "../src/features/PracticeWorkspace.vue";
import OverlayHost from "../src/features/OverlayHost.vue";
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
  it("keeps the story's activity v2 when another task pins v1, and remounts the same activity across versions", async () => {
    const { pinia, session } = setup();
    session.world!.quests = [1, 2].map(version => ({ id: `task-${version}`, version, kind: "main", chapter_id: 179, title: "固定版本任务", status: "active", ...(version === 2 ? { story_id: "first-steps" } : {}), nodes: [] }));
    const base = { id: "ch1-lesson", chapter_id: 179, title: "课程", kind: "lesson", npc_key: "librarian_doc", assistant_allowed: true, questions: [] };
    const legacy = { ...base, version: 1, material: { id: 186, version: 1, kind: "text", status: "ready", title: "旧课件", pages: [{ id: 10, page_no: 1, title: "旧版", text: "旧版课程内容" }] } };
    const current = { ...base, version: 2, material: { ...legacy.material, pages: [{ id: 201, page_no: 42, title: "新版", text: "故事绑定的第二版课件" }] }, completion: { required_page_ids: [201] }, progress: { page_no: 42, visited_pages: [201], completed: true } };
    const get = vi.spyOn(api, "get").mockImplementation(async path => {
      if (path.startsWith("/story/current")) return { id: "story-session", revision: 3, status: "waiting", npc_key: "librarian_doc", node: null, activity: { id: "ch1-lesson", kind: "lesson", version: 2, page_no: 42 } } as never;
      if (path === "/activities/ch1-lesson?classroom_id=2&version=2") return current as never;
      if (path.startsWith("/activities/ch1-lesson/progress")) return current.progress as never;
      if (path.startsWith("/activities/ch1-lesson?")) return legacy as never;
      throw new Error(`Unexpected path ${path}`);
    });
    const post = vi.spyOn(api, "post").mockResolvedValue(current.progress);
    await session.restoreStory();
    expect(session.workspace).toEqual(expect.objectContaining({ activity_id: "ch1-lesson", activity_version: 2 }));
    const wrapper = mount(OverlayHost, { global: { plugins: [pinia], stubs: { AssistantPanel: true } } });
    await vi.waitFor(async () => { await flushPromises(); expect(wrapper.text()).toContain("故事绑定的第二版课件"); });
    expect(get).toHaveBeenCalledWith("/activities/ch1-lesson?classroom_id=2&version=2");
    expect(get).toHaveBeenCalledWith("/activities/ch1-lesson/progress?classroom_id=2&version=2");
    expect(post).toHaveBeenCalledWith("/activities/ch1-lesson/progress", { classroom_id: 2, page_no: 42, version: 2 });
    expect(wrapper.get('[aria-label="本章阅读目标"]').text()).toContain("阅读第42页");
    const previous = wrapper.get(".lesson-workspace").element;
    session.open({ kind: "lesson", activity_id: "ch1-lesson", activity_version: 1 });
    await vi.waitFor(async () => { await flushPromises(); expect(wrapper.text()).toContain("旧版课程内容"); });
    expect(wrapper.get(".lesson-workspace").element).not.toBe(previous);
    expect(get).toHaveBeenCalledWith("/activities/ch1-lesson?classroom_id=2&version=1");
    expect(wrapper.find('[aria-label="本章阅读目标"]').exists()).toBe(false);
    wrapper.unmount();
  });
  it("returns from a story practice only after the server verifies all formal questions", async () => {
    const { pinia, session } = setup();
    vi.spyOn(session, "activity").mockResolvedValue({
      id: "ch1-practice", chapter_id: 179, title: "练习", kind: "practice", version: 3,
      npc_key: "arena_guard", assistant_allowed: false, material: null,
      progress: { page_no: null, visited_pages: [], completed: false, accepted_question_ids: [] },
      questions: ["q1", "q2"].map(id => ({ id, title: id, kind: "boolean" as const, prompt: "请选择", options: [], samples: [], version: 1 })),
    });
    const post = vi.spyOn(api, "post").mockResolvedValueOnce({ id: "s1", activity_id: "ch1-practice", question_id: "q1", version: 1, mode: "submit", status: "accepted", feedback: {}, created_at: "2026-11-05T00:00:00Z" })
      .mockResolvedValueOnce({ id: "s2", activity_id: "ch1-practice", question_id: "q2", version: 1, mode: "submit", status: "accepted", feedback: {}, created_at: "2026-11-05T00:00:00Z" });
    const get = vi.spyOn(api, "get").mockResolvedValueOnce({ page_no: null, visited_pages: [], completed: false, accepted_question_ids: ["q1"] })
      .mockResolvedValueOnce({ page_no: null, visited_pages: [], completed: true, accepted_question_ids: ["q1", "q2"] });
    const wrapper = mount(PracticeWorkspace, { props: { activityId: "ch1-practice", storySessionId: "story-session" }, global: { plugins: [pinia], stubs: { AssistantPanel: true } } });
    await flushPromises();
    await wrapper.get('input[value="true"]').setValue();
    await wrapper.findAll("button").find(button => button.text() === "正式提交")!.trigger("click");
    await flushPromises();
    expect(wrapper.emitted("complete")).toBeUndefined();
    expect(wrapper.get(".learning-meta").text()).toContain("已完成 1 / 2 题");
    await wrapper.get(".question-list").findAll("button")[1]!.trigger("click");
    await wrapper.get('input[value="false"]').setValue();
    await wrapper.findAll("button").find(button => button.text() === "正式提交")!.trigger("click");
    await flushPromises();
    expect(wrapper.emitted("complete")).toEqual([[]]);
    expect(get).toHaveBeenLastCalledWith("/activities/ch1-practice/progress?classroom_id=2&version=3");
    expect(post.mock.calls.every(([path]) => path === "/submissions")).toBe(true);
    wrapper.unmount();
  });
  it("restores the server page and requires every configured reading page before continuing the story", async () => {
    const { pinia, session } = setup();
    vi.spyOn(session, "activity").mockResolvedValue({
      id: "ch1-lesson", chapter_id: 179, title: "第一章", kind: "lesson", version: 2,
      npc_key: "librarian_doc", assistant_allowed: true, questions: [],
      completion: { required_page_ids: [10715, 10723, 10724] },
      progress: { page_no: 42, visited_pages: [10715], completed: false },
      material: { id: 186, title: "真实课件文本", kind: "text", version: 1, status: "ready", pages: [
        { id: 10715, page_no: 34, title: "目标一", text: "计算思维" },
        { id: 10723, page_no: 42, title: "目标二", text: "抽象" },
        { id: 10724, page_no: 43, title: "目标三", text: "问题求解" },
      ] },
    });
    vi.spyOn(api, "get").mockResolvedValue({ page_no: 42, visited_pages: [10715], completed: false });
    const post = vi.spyOn(api, "post").mockResolvedValueOnce({ page_no: 42, visited_pages: [10715, 10723], completed: false })
      .mockResolvedValueOnce({ page_no: 43, visited_pages: [10715, 10723, 10724], completed: true });
    const wrapper = mount(LessonWorkspace, { props: { activityId: "ch1-lesson", storySessionId: "story-session" }, global: { plugins: [pinia], stubs: { AssistantPanel: true } } });
    await flushPromises();
    expect(wrapper.get(".lesson-text").text()).toContain("抽象");
    const complete = wrapper.findAll("button").find(button => button.text() === "完成阅读")!;
    expect(complete.attributes("disabled")).toBeDefined();
    expect(wrapper.get(".lesson-footer").text()).toContain("已阅读 2 / 3 页");
    await wrapper.get('[aria-label="本章阅读目标"]').findAll("button")[2]!.trigger("click");
    await flushPromises();
    expect(post).toHaveBeenLastCalledWith("/activities/ch1-lesson/progress", { classroom_id: 2, page_no: 43, version: 2 });
    expect(wrapper.get(".lesson-footer").text()).toContain("已阅读 3 / 3 页");
    expect(complete.attributes("disabled")).toBeUndefined();
    await complete.trigger("click");
    expect(wrapper.emitted("complete")).toEqual([[]]);
    expect(post.mock.calls.every(([path]) => path.endsWith("/progress"))).toBe(true);
    wrapper.unmount();
  });
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
      .find((b) => b.text().trim() === "下一页")!
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
