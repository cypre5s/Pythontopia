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
    student_id: "124639931179929",
    role: "student" };
  session.classroomId = 2;
  session.world = {
    player: { map_key: "town_hub", tile_x: 22, tile_y: 36, direction: "down" },
    npcs: [],
    quests: [],
    chapters: [],
    flags: [] };
  vi.stubGlobal(
    "matchMedia",
    vi.fn(() => ({
      matches: compact,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn() })),
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
  it("uses configured reading ranges, original outline pages and verified evidence to complete an ordinary quest node", async () => {
    const { pinia, session } = setup();
    const initial = { page_no: 34, visited_pages: [], required_page_ids: [201, 202], read_count: 0, read_total: 2, completed: false };
    session.world!.quests = [{ id: "fixture", kind: "side", chapter_id: 179, title: "测试委托", status: "active", nodes: [{ id: "read", type: "STUDY_LESSON", title: "阅读", target: "", activity_id: "range", dependencies: [], aggregation: "ALL", optional: false, status: "ready" }] }];
    vi.spyOn(session, "activity").mockResolvedValue({ id: "range", chapter_id: 179, title: "范围阅读", kind: "lesson", npc_key: "librarian_doc", assistant_allowed: false, questions: [], progress: initial,
      learning: { reading: { scope: "range", start_page: 34, end_page: 35 }, highlights: { pages: [{ start_page: 35, end_page: 35, label: "课堂重点" }] } },
      material: { id: 186, title: "课件", kind: "text", status: "ready", pages: [{ id: 200, page_no: 33, title: "导读", text: "范围外" }, { id: 201, page_no: 34, title: "计算", text: "计算" }, { id: 202, page_no: 35, title: "求解", text: "求解" }] } });
    vi.spyOn(api, "get").mockResolvedValue(initial);
    const post = vi.spyOn(api, "post").mockResolvedValueOnce({ ...initial, visited_pages: [201], read_count: 1 }).mockResolvedValueOnce({ ...initial, page_no: 35, visited_pages: [201, 202], read_count: 2, completed: true }).mockResolvedValueOnce({ record_id: "record" });
    const event = vi.spyOn(session, "event").mockResolvedValue();
    vi.spyOn(session, "refresh").mockResolvedValue();
    const wrapper = mount(LessonWorkspace, { props: { activityId: "range" }, global: { plugins: [pinia], stubs: { AssistantPanel: true } } });
    await flushPromises();
    expect(wrapper.get(".support-goal-progress progress").attributes("value")).toBe("1");
    expect(wrapper.get('[aria-label="文档页面目录"]').findAll("button")).toHaveLength(3);
    expect(wrapper.get(".support-outline-important").text()).toBe("课堂重点");
    await wrapper.get('[aria-label="本章阅读目标"]').findAll("button")[1]!.trigger("click");
    await flushPromises();
    expect(wrapper.get(".support-goal-progress progress").attributes("value")).toBe("2");
    await wrapper.findAll("button").find(button => button.text() === "完成阅读")!.trigger("click");
    await flushPromises();
    expect(post).toHaveBeenLastCalledWith("/activities/range/participation", { classroom_id: 2, page_no: 35 });
    expect(event).toHaveBeenCalledWith(session.world!.quests[0], session.world!.quests[0]!.nodes[0], { record_id: "record" });
    wrapper.unmount();
  });
  it("returns compact goal-page navigation to the reading region without changing original page IDs", async () => {
    const { pinia, session } = setup(true);
    vi.spyOn(session, "activity").mockResolvedValue({ id: "lesson", chapter_id: 179, title: "阅读", kind: "lesson", npc_key: "librarian_doc", assistant_allowed: false, questions: [],
      material: { id: 186, title: "课件", kind: "text", status: "ready", pages: [{ id: 201, page_no: 42, title: "目标页", text: "原始正文" }] },
      completion: { required_page_ids: [201] } });
    vi.spyOn(api, "get").mockResolvedValue({ page_no: 42, visited_pages: [201], completed: true });
    vi.spyOn(api, "post").mockResolvedValue({ page_no: 42, visited_pages: [201], completed: true });
    const wrapper = mount(LessonWorkspace, { props: { activityId: "lesson" }, global: { plugins: [pinia], stubs: { AssistantPanel: true } } });
    await flushPromises();
    await wrapper.findAll("button").find(button => button.text() === "目标与助教")!.trigger("click");
    expect(wrapper.get(".lesson-content").attributes("hidden")).toBeDefined();
    await wrapper.get('[aria-label="本章阅读目标"] button').trigger("click");
    expect(wrapper.get(".lesson-content").attributes("hidden")).toBeUndefined();
    expect(wrapper.get(".lesson-text").text()).toContain("原始正文");
    wrapper.unmount();
  });
  it("refreshes server-verified learning goals after a normal practice submission", async () => {
    const { pinia, session } = setup();
    vi.spyOn(session, "activity").mockResolvedValue({ id: "practice", chapter_id: 179, title: "练习", kind: "practice", npc_key: "arena_guard", assistant_allowed: false, material: null,
      progress: { page_no: null, visited_pages: [], accepted_question_ids: [], completed: false },
      questions: [{ id: "q1", title: "判断题", kind: "boolean", prompt: "请选择", options: [], samples: [] }] });
    vi.spyOn(api, "post").mockResolvedValue({ id: "s1", question_id: "q1", mode: "submit", status: "accepted", feedback: {} });
    const get = vi.spyOn(api, "get").mockResolvedValue({ page_no: null, visited_pages: [], accepted_question_ids: ["q1"], completed: true });
    vi.spyOn(session, "refresh").mockResolvedValue();
    const wrapper = mount(PracticeWorkspace, { props: { activityId: "practice" }, global: { plugins: [pinia], stubs: { AssistantPanel: true } } });
    await flushPromises();
    expect(wrapper.find(".support-goal-state").exists()).toBe(false);
    await wrapper.get('input[value="true"]').setValue();
    await wrapper.findAll("button").find(button => button.text() === "提交答案")!.trigger("click");
    await flushPromises();
    expect(get).toHaveBeenCalledWith("/activities/practice/progress?classroom_id=2");
    expect(wrapper.get(".support-goal-state").text()).toBe("已完成");
    wrapper.unmount();
  });
  it("retains the server page and original required IDs when current lesson media becomes PDF", async () => {
    const { pinia, session } = setup();
    const pages = [{ id: 10715, page_no: 34, title: "计算", text: "计算" }, { id: 10723, page_no: 42, title: "抽象", text: "抽象" }];
    const progress = { page_no: 42, visited_pages: [10715, 10723], completed: true };
    vi.spyOn(session, "activity").mockResolvedValue({
      id: "ch1-lesson", chapter_id: 179, title: "第一章", kind: "lesson", npc_key: "librarian_doc", assistant_allowed: true,
      questions: [], completion: { required_page_ids: [10715, 10723] }, progress,
      material: { id: 186, title: "课件", kind: "pdf", status: "ready", pages },
    });
    vi.spyOn(api, "get").mockResolvedValue(progress);
    const post = vi.spyOn(api, "post").mockResolvedValue(progress);
    sessionStorage.setItem("read:1:2:material-186:pdf", "34");
    const wrapper = mount(LessonWorkspace, { props: { activityId: "ch1-lesson" }, global: { plugins: [pinia], stubs: {
      AssistantPanel: true, PdfReader: { props: ["page"], template: '<div class="pdf-fixture">{{ page }}</div>' },
    } } });
    await flushPromises();
    expect(wrapper.get(".pdf-fixture").text()).toBe("42");
    expect(wrapper.find(".lesson-extracted-text").exists()).toBe(false);
    expect(wrapper.text()).not.toContain("提取文本");
    expect(wrapper.get('[aria-label="本章阅读目标"]').text()).toContain("阅读第42页");
    expect(post).toHaveBeenCalledWith("/activities/ch1-lesson/progress", { classroom_id: 2, page_no: 42 });
    wrapper.unmount();
  });

  it("preserves a library text position on PDF replacement without writing activity progress", async () => {
    const { pinia } = setup();
    sessionStorage.setItem("read:1:2:material-186", "1");
    vi.spyOn(api, "get").mockResolvedValue({ id: 186, title: "课件", kind: "pdf", status: "ready", pages: [
      { id: 10715, page_no: 34, title: "计算", text: "计算" }, { id: 10723, page_no: 42, title: "抽象", text: "抽象" },
    ] });
    const post = vi.spyOn(api, "post");
    const wrapper = mount(LessonWorkspace, { props: { activityId: "", materialId: 186 }, global: { plugins: [pinia], stubs: {
      AssistantPanel: true, PdfReader: { props: ["page"], template: '<div class="pdf-fixture">{{ page }}</div>' },
    } } });
    await flushPromises();
    expect(wrapper.get(".pdf-fixture").text()).toBe("42");
    expect(wrapper.find(".lesson-footer").exists()).toBe(false);
    expect(wrapper.findComponent({ name: "AssistantPanel" }).exists()).toBe(true);
    expect(wrapper.findComponent({ name: "AssistantPanel" }).props("materialId")).toBe(186);
    expect(post).not.toHaveBeenCalled();
    wrapper.unmount();
  });
  it("restores a library reader's previous local position after moving to the current material key", async () => {
    const { pinia } = setup();
    sessionStorage.setItem("read:1:2:material-186-2", "1");
    vi.spyOn(api, "get").mockResolvedValue({
      id: 186, title: "资料库课件", kind: "text", status: "ready", pages: [
        { id: 1, page_no: 1, title: "第一页", text: "开始阅读" },
        { id: 2, page_no: 2, title: "第二页", text: "上次读到这里" },
      ],
    });
    const wrapper = mount(LessonWorkspace, { props: { activityId: "", materialId: 186 }, global: { plugins: [pinia], stubs: { AssistantPanel: true } } });
    await flushPromises();
    expect(wrapper.get(".lesson-text").text()).toContain("上次读到这里");
    expect(sessionStorage.getItem("read:1:2:material-186")).toBe("1");
    expect(sessionStorage.getItem("read:1:2:material-186-2")).toBeNull();
    wrapper.unmount();
  });

  it("restores the current activity and existing reading progress from a waiting story", async () => {
    const { pinia, session } = setup();
    session.world!.quests = [{ id: "arrival", kind: "main", chapter_id: 179, title: "第一章", story_id: "first-steps", status: "active", nodes: [] }];
    const current = { id: "ch1-lesson", chapter_id: 179, title: "课程", kind: "lesson", npc_key: "librarian_doc", assistant_allowed: true, questions: [], material: { id: 186, kind: "text", status: "ready", title: "课件", pages: [{ id: 201, page_no: 42, title: "当前内容", text: "当前教学配置中的课件" }] }, completion: { required_page_ids: [201] }, progress: { page_no: 42, visited_pages: [201], completed: true } };
    const get = vi.spyOn(api, "get").mockImplementation(async path => {
      if (path.startsWith("/story/current")) return { id: "story-session", revision: 3, status: "waiting", npc_key: "librarian_doc", node: null, activity: { id: "ch1-lesson", kind: "lesson", page_no: 42 } } as never;
      if (path === "/activities/ch1-lesson?classroom_id=2") return current as never;
      if (path.startsWith("/activities/ch1-lesson/progress")) return current.progress as never;
      throw new Error("Unexpected path " + path);
    });
    const post = vi.spyOn(api, "post").mockResolvedValue(current.progress);
    await session.restoreStory();
    expect(session.workspace).toEqual(expect.objectContaining({ activity_id: "ch1-lesson" }));
    const wrapper = mount(OverlayHost, { global: { plugins: [pinia], stubs: { AssistantPanel: true } } });
    await vi.waitFor(async () => { await flushPromises(); expect(wrapper.text()).toContain("当前教学配置中的课件"); });
    expect(get).toHaveBeenCalledWith("/activities/ch1-lesson?classroom_id=2");
    expect(get).toHaveBeenCalledWith("/activities/ch1-lesson/progress?classroom_id=2");
    expect(post).toHaveBeenCalledWith("/activities/ch1-lesson/progress", { classroom_id: 2, page_no: 42 });
    expect(wrapper.get('[aria-label="本章阅读目标"]').text()).toContain("阅读第42页");
    expect(wrapper.text()).not.toContain("版本");
    wrapper.unmount();
  });
  it("returns from a story practice only after the server verifies all formal questions", async () => {
    const { pinia, session } = setup();
    vi.spyOn(session, "activity").mockResolvedValue({
      id: "ch1-practice", chapter_id: 179, title: "练习", kind: "practice", npc_key: "arena_guard", assistant_allowed: false, material: null,
      progress: { page_no: null, visited_pages: [], completed: false, accepted_question_ids: [] },
      questions: ["q1", "q2"].map(id => ({ id, title: id, kind: "boolean" as const, prompt: "请选择", options: [], samples: [] })) });
    const post = vi.spyOn(api, "post").mockResolvedValueOnce({ id: "s1", activity_id: "ch1-practice", question_id: "q1", mode: "submit", status: "accepted", feedback: {}, created_at: "2026-11-05T00:00:00Z" })
      .mockResolvedValueOnce({ id: "s2", activity_id: "ch1-practice", question_id: "q2", mode: "submit", status: "accepted", feedback: {}, created_at: "2026-11-05T00:00:00Z" });
    const get = vi.spyOn(api, "get").mockResolvedValueOnce({ page_no: null, visited_pages: [], completed: false, accepted_question_ids: ["q1"] })
      .mockResolvedValueOnce({ page_no: null, visited_pages: [], completed: true, accepted_question_ids: ["q1", "q2"] });
    const wrapper = mount(PracticeWorkspace, { props: { activityId: "ch1-practice", storySessionId: "story-session" }, global: { plugins: [pinia], stubs: { AssistantPanel: true } } });
    await flushPromises();
    await wrapper.get('input[value="true"]').setValue();
    await wrapper.findAll("button").find(button => button.text() === "提交答案")!.trigger("click");
    await flushPromises();
    expect(wrapper.emitted("complete")).toBeUndefined();
    expect(wrapper.get(".support-goal-progress").text()).toContain("已完成 1 / 2 题");
    await wrapper.findAll("button").find(button => button.text().includes("下一题"))!.trigger("click");
    await wrapper.get('input[value="false"]').setValue();
    await wrapper.findAll("button").find(button => button.text() === "提交答案")!.trigger("click");
    await flushPromises();
    expect(wrapper.emitted("complete")).toBeUndefined();
    await wrapper.findAll("button").find(button => button.text() === "完成练习")!.trigger("click");
    expect(wrapper.emitted("complete")).toEqual([[]]);
    expect(get).toHaveBeenLastCalledWith("/activities/ch1-practice/progress?classroom_id=2");
    expect(post.mock.calls.every(([path]) => path === "/submissions")).toBe(true);
    wrapper.unmount();
  });
  it("restores the server page and requires every configured reading page before continuing the story", async () => {
    const { pinia, session } = setup();
    vi.spyOn(session, "activity").mockResolvedValue({
      id: "ch1-lesson", chapter_id: 179, title: "第一章", kind: "lesson", npc_key: "librarian_doc", assistant_allowed: true, questions: [],
      completion: { required_page_ids: [10715, 10723, 10724] },
      progress: { page_no: 42, visited_pages: [10715], completed: false },
      material: { id: 186, title: "真实课件文本", kind: "text", status: "ready", pages: [
        { id: 10715, page_no: 34, title: "目标一", text: "计算思维" },
        { id: 10723, page_no: 42, title: "目标二", text: "抽象" },
        { id: 10724, page_no: 43, title: "目标三", text: "问题求解" },
      ] } });
    vi.spyOn(api, "get").mockResolvedValue({ page_no: 42, visited_pages: [10715], completed: false });
    const post = vi.spyOn(api, "post").mockResolvedValueOnce({ page_no: 42, visited_pages: [10715, 10723], completed: false })
      .mockResolvedValueOnce({ page_no: 43, visited_pages: [10715, 10723, 10724], completed: true });
    const wrapper = mount(LessonWorkspace, { props: { activityId: "ch1-lesson", storySessionId: "story-session" }, global: { plugins: [pinia], stubs: { AssistantPanel: true } } });
    await flushPromises();
    expect(wrapper.get(".lesson-text").text()).toContain("抽象");
    const complete = wrapper.findAll("button").find(button => button.text() === "完成阅读")!;
    expect(complete.attributes("disabled")).toBeDefined();
    expect(wrapper.get(".support-goal-progress").text()).toContain("已阅读 2 / 3 页");
    await wrapper.get('[aria-label="本章阅读目标"]').findAll("button")[2]!.trigger("click");
    await flushPromises();
    expect(post).toHaveBeenLastCalledWith("/activities/ch1-lesson/progress", { classroom_id: 2, page_no: 43 });
    expect(wrapper.get(".support-goal-progress").text()).toContain("已阅读 3 / 3 页");
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
      npc_key: "mayor_py",
      assistant_allowed: true,
      questions: [],
      material: {
        id: 186,
        title: "课件",
        kind: "text",
        status: "ready",
        pages: [
          { id: 1, page_no: 41, title: "第一页", text: "原课件文本" },
          { id: 2, page_no: 42, title: "第二页", text: "第二页文本" },
        ] } });
    const wrapper = mount(LessonWorkspace, {
      attachTo: document.body,
      props: { activityId: "lesson" },
      global: {
        plugins: [pinia],
        stubs: {
          AssistantPanel: {
            template:
              '<section class="assistant-panel"><textarea aria-label="测试助教问题" /></section>' } } } });
    await flushPromises();
    expect(wrapper.find(".learning-support").attributes("hidden")).toBeDefined();
    expect(wrapper.find(".learning-support").attributes("inert")).toBeDefined();
    await wrapper
      .findAll("button")
      .find((b) => b.text().trim() === "下一页")!
      .trigger("click");
    await wrapper
      .findAll("button")
      .find((b) => b.text() === "目标与助教")!
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
          samples: [] },
      ] });
    const post = vi
      .spyOn(api, "post")
      .mockResolvedValue({
        id: "s1",
        activity_id: "practice",
        question_id: "boolean-q",
        mode: "submit",
        status: "wrong_answer",
        feedback: { detail: "请重新思考" },
        created_at: "2026-11-03T00:00:00Z" });
    const wrapper = mount(PracticeWorkspace, {
      props: { activityId: "practice" },
      global: { plugins: [pinia], stubs: { AssistantPanel: true } } });
    await flushPromises();
    const submit = wrapper
      .findAll("button")
      .find((b) => b.text() === "提交答案")!;
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
