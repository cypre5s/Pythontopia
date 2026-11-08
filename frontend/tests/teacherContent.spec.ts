import { afterEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import type { VueWrapper } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import TeacherContent from "../src/features/TeacherContent.vue";
import { api } from "../src/shared/api";
import * as apiModule from "../src/shared/api";
import { useSession } from "../src/shared/session";
import type {
  TeacherMaterial,
  TeacherQuestion,
} from "../src/shared/teacherContent";

const courses = {
  chapters: [
    { id: 179, title: "Python 入门", order_index: 1, available: true },
    { id: 180, title: "循环", order_index: 2, available: true },
  ],
  knowledge_points: [{ id: 400, chapter_id: 179, title: "变量", owner_id: 9 }],
  npcs: [
    {
      key: "professor",
      name: "林教授",
      map_key: "town_hub",
      tile_x: 22,
      tile_y: 33,
      portrait: "",
      dialogue: [],
    },
  ],
  activities: [
    {
      id: "my-practice",
      chapter_id: 179,
      title: "变量练习",
      kind: "practice",
      npc_key: "professor",
      assistant_allowed: false,
      material_id: 186,
      knowledge_point_ids: [400],
      owner_id: 9,
      question_ids: ["variable-1"],
    },
  ],
};
const material: TeacherMaterial = {
  owner_id: 9,
  id: 186,
  title: "入门讲义",
  kind: "text",
  status: "ready",
  chapter_ids: [179],
  pages: [{ id: 1, page_no: 1, title: "变量", text: "Python 变量" }],
};
const question: TeacherQuestion = {
  owner_id: 9,
  id: "variable-1",
  title: "变量题目",
  kind: "single_choice",
  prompt: "选择正确的赋值表达式。",
  options: ["x = 1", "x == 1"],
  answer: "x = 1",
  samples: [],
  hidden_cases: [],
  chapter_ids: [179],
  knowledge_point_ids: [400],
  source_type: "imported",
  status: "ready",
  source: {},
  activities: [{ id: "my-practice", title: "变量练习" }],
};
const quest = {
  id: "variables",
  title: "初识变量",
  definition: {
    id: "variables",
    title: "初识变量",
    chapter_id: 179,
    nodes: [],
  },
};
const wrappers: VueWrapper[] = [];
async function setup(
  section = "questions",
  options: {
    materials?: TeacherMaterial[];
    questions?: TeacherQuestion[];
    activities?: typeof courses.activities;
    openEditor?: boolean;
  } = {},
) {
  const pinia = createPinia();
  setActivePinia(pinia);
  useSession().user = {
    id: 9,
    name: "Teacher",
    student_id: "1748881331821",
    role: "teacher",
  };
  const records = {
    materials: options.materials ?? [structuredClone(material)],
    questions: options.questions ?? ([] as TeacherQuestion[]),
  };
  vi.spyOn(api, "get").mockImplementation(async (path: string) => {
    if (path === "/teacher/courses")
      return structuredClone({
        ...courses,
        activities: options.activities ?? courses.activities,
      });
    if (path === "/teacher/materials")
      return structuredClone(records.materials);
    if (path === "/teacher/quests") return [structuredClone(quest)];
    if (path === "/teacher/questions")
      return structuredClone(records.questions);
    if (path === "/teacher/materials/186") return structuredClone(material);
    throw new Error(`Unexpected request: ${path}`);
  });
  const notification = vi
    .spyOn(apiModule, "notify")
    .mockImplementation(() => {});
  const wrapper = mount(TeacherContent, {
    props: { section, classroomId: null },
    global: {
      plugins: [pinia],
      stubs: {
        AdminDialog: {
          props: ["open", "title", "description"],
          template:
            '<section v-if="open" role="dialog" :aria-label="title"><h2>{{ title }}</h2><slot /></section>',
        },
      },
    },
  });
  wrappers.push(wrapper);
  await flushPromises();
  if (options.openEditor) {
    await buttonNamed(
      wrapper,
      section === "materials" ? "上传课程材料" : "新增题目",
    ).trigger("click");
    await flushPromises();
  }
  return { wrapper, notification, records };
}
function buttonNamed(wrapper: VueWrapper, name: string) {
  const button = wrapper.findAll("button").find((item) => item.text() === name);
  if (!button) throw new Error(`Missing button: ${name}`);
  return button;
}
function field(wrapper: VueWrapper, name: string) {
  const label = wrapper
    .findAll("label")
    .filter((item) => item.text().startsWith(name))
    .at(-1);
  if (!label) throw new Error(`Missing field: ${name}`);
  return label.get("input, select, textarea");
}
async function fillQuestion(wrapper: VueWrapper) {
  await field(wrapper, "题目 ID").setValue("variable-check");
  await field(wrapper, "题目标题").setValue("判断变量赋值");
  await field(wrapper, "题干").setValue("变量赋值后不可改变。");
  await field(wrapper, "题型").setValue("boolean");
  await field(wrapper, "正确答案").setValue("false");
}
afterEach(() => {
  for (const wrapper of wrappers.splice(0)) wrapper.unmount();
  vi.restoreAllMocks();
  sessionStorage.clear();
});

describe("independent teacher question bank", () => {
  it("shows shared questions as read-only", async () => {
    const { wrapper } = await setup("questions", {
      questions: [{ ...structuredClone(question), owner_id: null }],
    });
    expect(wrapper.get("tbody").text()).toContain("共享只读");
    expect(wrapper.get("tbody").findAll("button")).toHaveLength(0);
  });
  it("limits assessment selection to 100 questions", async () => {
    const questions = Array.from({ length: 101 }, (_, index) => ({
      ...structuredClone(question),
      id: `q-${index}`,
    }));
    const { wrapper } = await setup("questions", {
      questions,
      activities: [
        {
          ...structuredClone(courses.activities[0]!),
          kind: "assessment",
          question_ids: questions.slice(0, 100).map((item) => item.id),
        },
      ],
    });
    await buttonNamed(wrapper, "练习 / 测验选题").trigger("click");
    await flushPromises();
    await wrapper
      .get(".content-assignment-options")
      .findAll("input")[100]!
      .setValue(true);
    expect(wrapper.get("[role='dialog'] [role='alert']").text()).toContain(
      "最多选择 100 道题目",
    );
    expect(wrapper.findAll(".content-ordered-questions li")).toHaveLength(100);
  });
  it("creates an unclassified question without an activity and keeps boolean answers typed", async () => {
    const { wrapper, notification } = await setup("questions", {
      openEditor: true,
    });
    const post = vi
      .spyOn(api, "post")
      .mockResolvedValue({ id: "variable-check" });
    await fillQuestion(wrapper);
    expect(wrapper.find("textarea.json-editor").exists()).toBe(false);
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(post).toHaveBeenCalledExactlyOnceWith(
      "/teacher/questions",
      expect.objectContaining({
        id: "variable-check",
        chapter_ids: [],
        knowledge_point_ids: [],
        source_type: "imported",
        status: "draft",
        answer: false,
        source: {},
      }),
    );
    expect(post.mock.calls[0]?.[1]).not.toHaveProperty("activity_id");
    expect(notification).toHaveBeenCalledWith("题目已保存到题库。");
    expect(wrapper.find("[role='dialog']").exists()).toBe(false);
  });
  it("edits multiple chapters, optional knowledge points and source references through structured fields", async () => {
    const { wrapper } = await setup("questions", {
      questions: [structuredClone(question)],
    });
    const put = vi.spyOn(api, "put").mockResolvedValue({});
    await buttonNamed(wrapper, "编辑").trigger("click");
    await flushPromises();
    expect(field(wrapper, "题目 ID").attributes("disabled")).toBeDefined();
    const chapterOptions = wrapper
      .get("fieldset.content-tag-select")
      .findAll("input[type='checkbox']");
    await chapterOptions[1]!.setValue(true);
    await field(wrapper, "来源类型").setValue("ai_generated");
    await field(wrapper, "来源资料").setValue("186");
    await field(wrapper, "来源页码").setValue("1");
    await field(wrapper, "来源依据").setValue("依据变量示例");
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(put).toHaveBeenCalledExactlyOnceWith(
      "/teacher/questions/variable-1",
      expect.objectContaining({
        chapter_ids: [179, 180],
        knowledge_point_ids: [400],
        source_type: "ai_generated",
        source: { material_id: 186, page_no: 1, evidence: "依据变量示例" },
      }),
    );
    expect(put.mock.calls[0]?.[1]).not.toHaveProperty("id");
  });
  it.each([
    "抽象与自动化",
    ["抽象与自动化", "抽象和自动化"],
    { value: ["抽象与自动化", "抽象和自动化"], note: "原有答案格式" },
  ])(
    "preserves existing fill answer shapes when editing other fields: %j",
    async (answer) => {
      const { wrapper } = await setup("questions", {
        questions: [
          { ...structuredClone(question), kind: "fill", options: [], answer },
        ],
      });
      const put = vi.spyOn(api, "put").mockResolvedValue({});
      await buttonNamed(wrapper, "编辑").trigger("click");
      await flushPromises();
      expect(
        (field(wrapper, "可接受答案").element as HTMLTextAreaElement).value,
      ).toContain("抽象与自动化");
      await field(wrapper, "题目标题").setValue("仅修改标题");
      await wrapper.get("form").trigger("submit");
      await flushPromises();
      expect(put.mock.calls[0]?.[1]).toMatchObject({
        title: "仅修改标题",
        answer,
      });
      expect(put.mock.calls[0]?.[1]).not.toHaveProperty("id");
    },
  );
  it("edits alternative fill answers while retaining the legacy value wrapper", async () => {
    const { wrapper } = await setup("questions", {
      questions: [
        {
          ...structuredClone(question),
          kind: "fill",
          options: [],
          answer: { value: ["甲"], note: "评分依据" },
        },
      ],
    });
    const put = vi.spyOn(api, "put").mockResolvedValue({});
    await buttonNamed(wrapper, "编辑").trigger("click");
    await flushPromises();
    await field(wrapper, "可接受答案").setValue("乙\n丙");
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(put.mock.calls[0]?.[1]).toMatchObject({
      answer: { value: ["乙", "丙"], note: "评分依据" },
    });
  });
  it("clears knowledge points when their chapter is removed and explains ready validation", async () => {
    const { wrapper } = await setup("questions", {
      questions: [structuredClone(question)],
    });
    const put = vi.spyOn(api, "put").mockResolvedValue({});
    await buttonNamed(wrapper, "编辑").trigger("click");
    await flushPromises();
    await wrapper
      .get("fieldset.content-tag-select")
      .findAll("input")[0]!
      .setValue(false);
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(put).not.toHaveBeenCalled();
    expect(wrapper.get("[role='dialog'] [role='alert']").text()).toContain(
      "至少需要关联一个章节",
    );
    await field(wrapper, "状态").setValue("draft");
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(put.mock.calls[0]?.[1]).toMatchObject({
      chapter_ids: [],
      knowledge_point_ids: [],
    });
  });
  it("keeps failed drafts across closing and navigation, and blocks duplicate saves", async () => {
    const { wrapper } = await setup("questions", { openEditor: true });
    const post = vi
      .spyOn(api, "post")
      .mockRejectedValueOnce(new Error("题目 ID 已存在"));
    await fillQuestion(wrapper);
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(wrapper.get("[role='dialog'] [role='alert']").text()).toBe(
      "题目 ID 已存在",
    );
    await buttonNamed(wrapper, "取消").trigger("click");
    await wrapper.setProps({ section: "materials" });
    await wrapper.setProps({ section: "questions" });
    await buttonNamed(wrapper, "新增题目").trigger("click");
    expect((field(wrapper, "题目标题").element as HTMLInputElement).value).toBe(
      "判断变量赋值",
    );
    let finish!: (value: object) => void;
    post.mockReturnValue(
      new Promise((resolve) => {
        finish = resolve;
      }),
    );
    await wrapper.get("form").trigger("submit");
    await wrapper.get("form").trigger("submit");
    expect(post).toHaveBeenCalledTimes(2);
    expect(
      wrapper.get("button[type='submit']").attributes("disabled"),
    ).toBeDefined();
    finish({});
    await flushPromises();
  });
  it("loads and saves programming samples and hidden cases without JSON editing", async () => {
    const programming = {
      ...structuredClone(question),
      kind: "python",
      answer: null,
      options: [],
      samples: [{ input: "1", output: "1" }],
      hidden_cases: [{ input: "42", expected_output: "42" }],
    };
    const { wrapper } = await setup("questions", { questions: [programming] });
    const put = vi.spyOn(api, "put").mockResolvedValue({});
    await buttonNamed(wrapper, "编辑").trigger("click");
    await flushPromises();
    expect(wrapper.text()).toContain("隐藏用例");
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(put).toHaveBeenCalledWith(
      "/teacher/questions/variable-1",
      expect.objectContaining({
        samples: programming.samples,
        hidden_cases: programming.hidden_cases,
        answer: null,
      }),
    );
  });
  it("selects only ready questions, adjusts their order and saves the ordered association", async () => {
    const { wrapper } = await setup("questions", {
      questions: [
        structuredClone(question),
        {
          ...structuredClone(question),
          id: "second",
          title: "第二题",
          activities: [],
        },
        {
          ...structuredClone(question),
          id: "pending",
          title: "未审核题",
          status: "draft",
        },
        {
          ...structuredClone(question),
          id: "other-chapter",
          title: "其他章节题目",
          chapter_ids: [180],
        },
      ],
    });
    const put = vi.spyOn(api, "put").mockResolvedValue({});
    await buttonNamed(wrapper, "练习 / 测验选题").trigger("click");
    await flushPromises();
    const choices = wrapper.get(".content-assignment-options");
    expect(choices.text()).not.toContain("未审核题");
    expect(choices.text()).not.toContain("其他章节题目");
    await choices.findAll("input")[1]!.setValue(true);
    await wrapper.get("button[aria-label='上移题目 second']").trigger("click");
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(put).toHaveBeenCalledExactlyOnceWith(
      "/teacher/activities/my-practice/questions",
      { question_ids: ["second", "variable-1"] },
    );
  });
  it("keeps reference errors in the delete dialog and preserves the question", async () => {
    const { wrapper } = await setup("questions", {
      questions: [structuredClone(question)],
    });
    vi.spyOn(api, "request").mockRejectedValue(new Error("题目仍被练习引用"));
    await buttonNamed(wrapper, "删除").trigger("click");
    await buttonNamed(wrapper, "确认删除").trigger("click");
    await flushPromises();
    expect(wrapper.get("[role='dialog'] [role='alert']").text()).toBe(
      "题目仍被练习引用",
    );
    expect(wrapper.findAll("tbody tr")).toHaveLength(1);
  });
});

describe("independent teacher materials", () => {
  it("does not reopen an asynchronous preview after navigating away", async () => {
    const { wrapper } = await setup("materials");
    let finish!: (value: TeacherMaterial) => void;
    vi.mocked(api.get).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve;
        }) as never,
    );
    await buttonNamed(wrapper, "预览").trigger("click");
    await wrapper.setProps({ section: "questions" });
    await flushPromises();
    finish(structuredClone(material));
    await flushPromises();
    expect(wrapper.find("[role='dialog']").exists()).toBe(false);
  });
  it("shows shared materials as read-only while allowing previews", async () => {
    const { wrapper } = await setup("materials", {
      materials: [{ ...structuredClone(material), owner_id: null }],
    });
    expect(wrapper.get("tbody").text()).toContain("共享只读");
    expect(
      wrapper
        .get("tbody")
        .findAll("button")
        .map((button) => button.text()),
    ).toEqual(["预览"]);
  });
  it("retains file drafts and uploads unclassified materials with errors in the dialog", async () => {
    const { wrapper } = await setup("materials", { openEditor: true });
    const request = vi
      .spyOn(api, "request")
      .mockRejectedValueOnce(new Error("文件上传失败"))
      .mockResolvedValueOnce({ id: 187 });
    await field(wrapper, "资料名称").setValue("待上传讲义");
    const fileInput = field(wrapper, "文件"),
      file = new File(["讲义正文"], "lesson.txt", { type: "text/plain" });
    Object.defineProperty(fileInput.element, "files", {
      value: [file],
      configurable: true,
    });
    await fileInput.trigger("change");
    await buttonNamed(wrapper, "取消").trigger("click");
    await buttonNamed(wrapper, "上传课程材料").trigger("click");
    expect(wrapper.get("[role='dialog']").text()).toContain(
      "已选择：lesson.txt",
    );
    expect(field(wrapper, "文件").attributes("required")).toBeUndefined();
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(wrapper.get("[role='dialog'] [role='alert']").text()).toBe(
      "文件上传失败",
    );
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(wrapper.find("[role='dialog']").exists()).toBe(false);
    const form = request.mock.calls[1]![1]?.body as FormData;
    expect(form.get("title")).toBe("待上传讲义");
    expect(form.get("chapter_ids")).toBe("[]");
    expect((form.get("file") as File).name).toBe("lesson.txt");
  });
  it("edits chapter tags, filters unclassified content, and previews without a classroom", async () => {
    const { wrapper } = await setup("materials", {
      materials: [
        structuredClone(material),
        {
          ...structuredClone(material),
          id: 187,
          title: "未分类资料",
          chapter_ids: [],
        },
      ],
    });
    const put = vi.spyOn(api, "put").mockResolvedValue({});
    await buttonNamed(wrapper, "编辑").trigger("click");
    await wrapper.get("fieldset").findAll("input")[1]!.setValue(true);
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(put).toHaveBeenCalledWith("/teacher/materials/186", {
      title: "入门讲义",
      chapter_ids: [179, 180],
    });
    await buttonNamed(wrapper, "预览").trigger("click");
    await flushPromises();
    expect(api.get).toHaveBeenCalledWith("/teacher/materials/186");
    expect(wrapper.get(".content-preview-text").text()).toContain(
      "Python 变量",
    );
    await field(wrapper, "章节").setValue("unassigned");
    expect(wrapper.findAll("tbody tr")).toHaveLength(1);
    expect(wrapper.get("tbody").text()).toContain("未分类资料");
  });
  it("replaces a file using the selected material ID and existing chapter tags", async () => {
    const { wrapper } = await setup("materials");
    const request = vi.spyOn(api, "request").mockResolvedValue({});
    await buttonNamed(wrapper, "替换文件").trigger("click");
    const fileInput = field(wrapper, "文件"),
      file = new File(["新版"], "replacement.txt", { type: "text/plain" });
    Object.defineProperty(fileInput.element, "files", {
      value: [file],
      configurable: true,
    });
    await fileInput.trigger("change");
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    const form = request.mock.calls[0]![1]?.body as FormData;
    expect(form.get("material_id")).toBe("186");
    expect(form.get("chapter_ids")).toBe("[179]");
  });
});

describe("teacher materials and question pagination", () => {
  it.each(["materials", "questions"])(
    "paginates %s and resets after filtering",
    async (section) => {
      const materials = Array.from({ length: 26 }, (_, i) => ({
        ...structuredClone(material),
        id: 200 + i,
        title: `材料 ${i + 1}`,
      }));
      const questions = Array.from({ length: 26 }, (_, i) => ({
        ...structuredClone(question),
        id: `question-${i + 1}`,
        title: `题目 ${i + 1}`,
      }));
      const { wrapper } = await setup(section, { materials, questions });
      expect(wrapper.findAll("tbody tr")).toHaveLength(10);
      await wrapper.get("button[aria-label='下一页']").trigger("click");
      expect(wrapper.get(".table-pagination").text()).toContain("11–20");
      await wrapper.get("select[aria-label='每页条数']").setValue("20");
      expect(wrapper.findAll("tbody tr")).toHaveLength(20);
      await wrapper
        .get("input[type='search']")
        .setValue(section === "materials" ? "材料 26" : "题目 26");
      expect(wrapper.findAll("tbody tr")).toHaveLength(1);
      expect(wrapper.get(".table-pagination").text()).toContain("第 1 / 1 页");
      expect(
        wrapper.get("button[aria-label='下一页']").attributes("disabled"),
      ).toBeDefined();
    },
  );
  it("clamps the material page when a refresh removes the current last page", async () => {
    const { wrapper, records } = await setup("materials", {
      materials: Array.from({ length: 26 }, (_, i) => ({
        ...structuredClone(material),
        id: 200 + i,
      })),
    });
    await wrapper.get("button[aria-label='下一页']").trigger("click");
    await wrapper.get("button[aria-label='下一页']").trigger("click");
    records.materials = records.materials.slice(0, 11);
    await buttonNamed(wrapper, "刷新状态").trigger("click");
    await flushPromises();
    expect(wrapper.get(".table-pagination").text()).toContain("第 2 / 2 页");
    expect(wrapper.findAll("tbody tr")).toHaveLength(1);
  });
});

describe("teacher task validation", () => {
  it("clears previous validation after edits and ignores validation returned for an older draft", async () => {
    const { wrapper } = await setup("quests");
    const post = vi
      .spyOn(api, "post")
      .mockResolvedValue({ valid: true, nodes: 2 });
    const task = field(wrapper, "任务 JSON");
    await task.setValue(JSON.stringify(quest.definition));
    const validate = wrapper
      .findAll("button")
      .find((button) => button.text() === "校验任务")!;
    await validate.trigger("click");
    await flushPromises();
    expect(wrapper.get("pre[role='status']").text()).toContain('"valid": true');
    await task.setValue(
      JSON.stringify({ ...quest.definition, title: "新任务标题" }),
    );
    expect(wrapper.find("pre[role='status']").exists()).toBe(false);
    let resolveValidation!: (value: { valid: boolean }) => void;
    post.mockReturnValue(
      new Promise((resolve) => {
        resolveValidation = resolve;
      }),
    );
    await validate.trigger("click");
    await task.setValue(
      JSON.stringify({ ...quest.definition, title: "再次修改" }),
    );
    resolveValidation({ valid: true });
    await flushPromises();
    expect(wrapper.find("pre[role='status']").exists()).toBe(false);
  });
});
