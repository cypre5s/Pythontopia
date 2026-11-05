import { afterEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import type { VueWrapper } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import TeacherContent from "../src/features/TeacherContent.vue";
import { api } from "../src/shared/api";
import * as apiModule from "../src/shared/api";
import { useSession } from "../src/shared/session";

const courses = {
  chapters: [
    { id: 179, title: "Python 入门", order_index: 1, available: true },
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
      version: 2,
      npc_key: "professor",
      assistant_allowed: false,
      material_id: 186,
      knowledge_point_ids: [400],
      owner_id: 9,
    },
  ],
};
const material = {
  id: 186,
  title: "入门讲义",
  kind: "text",
  version: 1,
  status: "ready",
  pages: [{ id: 1, page_no: 1, title: "变量", text: "Python 变量" }],
};
const question = { id: "variable-1", title: "变量题目", kind: "single_choice", prompt: "选择正确的赋值表达式。", options: ["x = 1", "x == 1"], samples: [], version: 1, activity_id: "my-practice" };
const quest = {
  id: "variables",
  title: "初识变量",
  version: 1,
  definition: {
    id: "variables",
    title: "初识变量",
    chapter_id: 179,
    nodes: [],
  },
};
const wrappers: VueWrapper[] = [];

async function setup(section = "questions", options: { materials?: typeof material[]; questions?: typeof question[]; openEditor?: boolean } = {}) {
  const pinia = createPinia();
  setActivePinia(pinia);
  useSession().user = {
    id: 9,
    name: "Teacher",
    email: "teacher@example.test",
    role: "teacher",
  };
  const records = { materials: options.materials ?? [structuredClone(material)], questions: options.questions ?? [] as typeof question[] };
  vi.spyOn(api, "get").mockImplementation(async (path: string) => {
    if (path === "/teacher/courses") return structuredClone(courses);
    if (path === "/teacher/materials") return structuredClone(records.materials);
    if (path === "/teacher/quests") return [structuredClone(quest)];
    if (path === "/teacher/questions") return structuredClone(records.questions);
    throw new Error(`Unexpected request: ${path}`);
  });
  const notification = vi
    .spyOn(apiModule, "notify")
    .mockImplementation(() => {});
  const wrapper = mount(TeacherContent, {
    props: { section, classroomId: 3 },
    global: { plugins: [pinia], stubs: { AdminDialog: { props: ["open", "title", "description"], template: '<section v-if="open" role="dialog" :aria-label="title"><h2>{{ title }}</h2><slot /></section>' } } },
  });
  wrappers.push(wrapper);
  await flushPromises();
  if (options.openEditor ?? section === "questions") await buttonNamed(wrapper, section === "materials" ? "上传课程材料" : "新增题目").trigger("click");
  return { wrapper, notification, records };
}

function buttonNamed(wrapper: VueWrapper, name: string) {
  const button = wrapper.findAll("button").find(item => item.text() === name);
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
function createButton(wrapper: VueWrapper) {
  return wrapper.get("button[type='submit']");
}
function canonicalQuestion(wrapper: VueWrapper) {
  return JSON.parse(
    (field(wrapper, "题目 JSON").element as HTMLTextAreaElement).value,
  );
}

afterEach(() => {
  for (const wrapper of wrappers.splice(0)) wrapper.unmount();
  vi.restoreAllMocks();
  sessionStorage.clear();
});

describe("teacher question authoring", () => {
  it("synchronizes common fields with the complete JSON and keeps boolean answers typed", async () => {
    const { wrapper } = await setup();
    await field(wrapper, "题目 ID").setValue("variable-check");
    await field(wrapper, "题目标题").setValue("判断变量赋值");
    await field(wrapper, "题干").setValue("变量赋值后不可改变。请选择判断。");
    await field(wrapper, "题型").setValue("boolean");
    await field(wrapper, "正确答案").setValue("false");
    expect(canonicalQuestion(wrapper)).toMatchObject({
      id: "variable-check",
      activity_id: "my-practice",
      title: "判断变量赋值",
      kind: "boolean",
      prompt: "变量赋值后不可改变。请选择判断。",
      answer: false,
    });
    expect(typeof canonicalQuestion(wrapper).answer).toBe("boolean");
    await field(wrapper, "题目 JSON").setValue(
      JSON.stringify({
        ...canonicalQuestion(wrapper),
        title: "从完整 JSON 导入的标题",
      }),
    );
    expect((field(wrapper, "题目标题").element as HTMLInputElement).value).toBe(
      "从完整 JSON 导入的标题",
    );
  });

  it("preserves invalid full JSON visibly and blocks creation until it is fixed", async () => {
    const { wrapper } = await setup();
    const post = vi.spyOn(api, "post").mockResolvedValue({});
    await field(wrapper, "题目 JSON").setValue('{"id": "incomplete"');
    expect(
      (field(wrapper, "题目 JSON").element as HTMLTextAreaElement).value,
    ).toBe('{"id": "incomplete"');
    expect(wrapper.text()).toContain("题目 JSON 格式不正确");
    expect(field(wrapper, "题目标题").attributes("disabled")).toBeDefined();
    expect(createButton(wrapper).attributes("disabled")).toBeDefined();
    await wrapper.get("form").trigger("submit");
    expect(post).not.toHaveBeenCalled();
  });

  it("retains unfinished fragment edits without replacing them from other form fields", async () => {
    const { wrapper } = await setup();
    const post = vi.spyOn(api, "post").mockResolvedValue({});
    await field(wrapper, "选项 JSON").setValue('["unfinished"');
    await field(wrapper, "题目标题").setValue("继续整理中的题目");
    expect(
      (field(wrapper, "选项 JSON").element as HTMLTextAreaElement).value,
    ).toBe('["unfinished"');
    expect(wrapper.text()).toContain("选项 JSON 格式不正确");
    expect(canonicalQuestion(wrapper).options).toEqual(["选项一", "选项二"]);
    expect(createButton(wrapper).attributes("disabled")).toBeDefined();
    await wrapper.get("form").trigger("submit");
    expect(post).not.toHaveBeenCalled();
    await field(wrapper, "选项 JSON").setValue('["新的选项一", "新的选项二"]');
    expect(canonicalQuestion(wrapper).options).toEqual([
      "新的选项一",
      "新的选项二",
    ]);
    expect(createButton(wrapper).attributes("disabled")).toBeUndefined();
  });

  it("submits the full canonical payload including private cases and reports the returned activity version", async () => {
    const { wrapper, notification } = await setup();
    const post = vi
      .spyOn(api, "post")
      .mockResolvedValue({
        id: "python-print",
        version: 1,
        activity_version: 3,
      });
    const payload = {
      id: "python-print",
      activity_id: "my-practice",
      title: "输出数字",
      kind: "python",
      prompt: "读取数字并原样输出。",
      options: [],
      answer: null,
      samples: [{ input: "1", output: "1" }],
      hidden_cases: [{ input: "42", expected_output: "42" }],
      source: { kind: "teacher_authored", material_id: 186, page_no: 1 },
    };
    await field(wrapper, "题目 JSON").setValue(JSON.stringify(payload));
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(post).toHaveBeenCalledExactlyOnceWith("/teacher/questions", payload);
    expect(notification).toHaveBeenCalledWith(
      "题目已创建，关联活动已更新为版本 3。请在新任务定义中引用该版本。",
    );
    expect(wrapper.find("[role='dialog']").exists()).toBe(false);
    await buttonNamed(wrapper, "新增题目").trigger("click");
    expect(canonicalQuestion(wrapper).hidden_cases).toEqual(
      payload.hidden_cases,
    );
  });

  it("prevents duplicate mutations while a create request is pending", async () => {
    const { wrapper } = await setup();
    let resolveCreate!: (value: {
      id: string;
      version: number;
      activity_version: number;
    }) => void;
    const pending = new Promise<{
      id: string;
      version: number;
      activity_version: number;
    }>((resolve) => {
      resolveCreate = resolve;
    });
    const post = vi.spyOn(api, "post").mockReturnValue(pending);
    await wrapper.get("form").trigger("submit");
    expect(createButton(wrapper).attributes("disabled")).toBeDefined();
    await wrapper.get("form").trigger("submit");
    expect(post).toHaveBeenCalledTimes(1);
    resolveCreate({ id: "my-concept-1", version: 1, activity_version: 3 });
    await flushPromises();
    expect(wrapper.find("[role='dialog']").exists()).toBe(false);
    await buttonNamed(wrapper, "新增题目").trigger("click");
    expect(createButton(wrapper).attributes("disabled")).toBeUndefined();
  });

  it("keeps errors inside the dialog and preserves unfinished drafts after cancel and navigation", async () => {
    const { wrapper } = await setup();
    vi.spyOn(api, "post").mockRejectedValue(new Error("题目 ID 已存在"));
    await field(wrapper, "题目标题").setValue("尚未保存的题目");
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(wrapper.get("[role='dialog'] [role='alert']").text()).toBe("题目 ID 已存在");
    await buttonNamed(wrapper, "取消").trigger("click");
    expect(wrapper.find("[role='dialog']").exists()).toBe(false);
    await buttonNamed(wrapper, "新增题目").trigger("click");
    expect((field(wrapper, "题目标题").element as HTMLInputElement).value).toBe("尚未保存的题目");
    await wrapper.setProps({ section: "materials" });
    expect(wrapper.find("[role='dialog']").exists()).toBe(false);
    await wrapper.setProps({ section: "questions" });
    await buttonNamed(wrapper, "新增题目").trigger("click");
    expect((field(wrapper, "题目标题").element as HTMLInputElement).value).toBe("尚未保存的题目");
  });
});

describe("teacher materials modal", () => {
  it("retains file drafts across closing, displays upload errors in the dialog, and closes after success", async () => {
    const { wrapper } = await setup("materials", { openEditor: true });
    const request = vi.spyOn(api, "request").mockRejectedValueOnce(new Error("文件上传失败")).mockResolvedValueOnce({ id: 187 });
    await field(wrapper, "资料名称").setValue("待上传讲义");
    const fileInput = field(wrapper, "文件");
    const file = new File(["讲义正文"], "lesson.txt", { type: "text/plain" });
    Object.defineProperty(fileInput.element, "files", { value: [file], configurable: true });
    await fileInput.trigger("change");
    await buttonNamed(wrapper, "取消").trigger("click");
    await buttonNamed(wrapper, "上传课程材料").trigger("click");
    expect(wrapper.get("[role='dialog']").text()).toContain("已选择：lesson.txt");
    expect(field(wrapper, "文件").attributes("required")).toBeUndefined();
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(wrapper.get("[role='dialog'] [role='alert']").text()).toBe("文件上传失败");
    expect((field(wrapper, "资料名称").element as HTMLInputElement).value).toBe("待上传讲义");
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(wrapper.find("[role='dialog']").exists()).toBe(false);
    expect(request).toHaveBeenCalledTimes(2);
    const form = request.mock.calls[1]![1]?.body as FormData;
    expect(form.get("title")).toBe("待上传讲义");
    expect((form.get("file") as File).name).toBe("lesson.txt");
  });
});

describe("teacher materials and question pagination", () => {
  it.each(["materials", "questions"])("paginates %s with page-size choices and resets after filtering", async section => {
    const materials = Array.from({ length: 26 }, (_, i) => ({ ...material, id: 200 + i, title: `材料 ${i + 1}` }));
    const questions = Array.from({ length: 26 }, (_, i) => ({ ...question, id: `question-${i + 1}`, title: `题目 ${i + 1}` }));
    const { wrapper } = await setup(section, { materials, questions, openEditor: false });
    expect(wrapper.findAll("tbody tr")).toHaveLength(10);
    expect(wrapper.get(".table-pagination").text()).toContain("1–10");
    await wrapper.get("button[aria-label='下一页']").trigger("click");
    expect(wrapper.get("tbody tr").text()).toContain(section === "materials" ? "材料 11" : "题目 11");
    expect(wrapper.get(".table-pagination").text()).toContain("11–20");
    await wrapper.get("select[aria-label='每页条数']").setValue("20");
    expect(wrapper.findAll("tbody tr")).toHaveLength(20);
    expect(wrapper.get(".table-pagination").text()).toContain("第 1 / 2 页");
    await wrapper.get("button[aria-label='下一页']").trigger("click");
    await wrapper.get("input[type='search']").setValue(section === "materials" ? "材料 26" : "题目 26");
    expect(wrapper.findAll("tbody tr")).toHaveLength(1);
    expect(wrapper.get(".table-pagination").text()).toContain("第 1 / 1 页");
    expect(wrapper.get("button[aria-label='下一页']").attributes("disabled")).toBeDefined();
    await wrapper.get("input[type='search']").setValue("");
    await wrapper.get("select[aria-label='每页条数']").setValue("50");
    expect(wrapper.findAll("tbody tr")).toHaveLength(26);
  });

  it("clamps the material page when a refresh removes the current last page", async () => {
    const { wrapper, records } = await setup("materials", { materials: Array.from({ length: 26 }, (_, i) => ({ ...material, id: 200 + i })) });
    await wrapper.get("button[aria-label='下一页']").trigger("click");
    await wrapper.get("button[aria-label='下一页']").trigger("click");
    expect(wrapper.get(".table-pagination").text()).toContain("第 3 / 3 页");
    records.materials = records.materials.slice(0, 11);
    await buttonNamed(wrapper, "刷新状态").trigger("click");
    await flushPromises();
    expect(wrapper.get(".table-pagination").text()).toContain("第 2 / 2 页");
    expect(wrapper.findAll("tbody tr")).toHaveLength(1);
  });

  it("clamps the question page after reloading a smaller result set on create", async () => {
    const { wrapper, records } = await setup("questions", { questions: Array.from({ length: 26 }, (_, i) => ({ ...question, id: `question-${i + 1}` })), openEditor: false });
    vi.spyOn(api, "post").mockResolvedValue({ id: "my-concept-1", version: 1, activity_version: 3 });
    await wrapper.get("button[aria-label='下一页']").trigger("click");
    await wrapper.get("button[aria-label='下一页']").trigger("click");
    records.questions = records.questions.slice(0, 11);
    await buttonNamed(wrapper, "新增题目").trigger("click");
    await wrapper.get("form").trigger("submit");
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
