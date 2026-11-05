import { describe, it, expect, vi, afterEach } from "vitest";
import { defineComponent } from "vue";
import {
  mount,
  flushPromises,
  type VueWrapper,
  type DOMWrapper,
} from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import TeacherCurriculum from "../src/features/TeacherCurriculum.vue";
import KnowledgePointSelect from "../src/features/KnowledgePointSelect.vue";
import { api } from "../src/shared/api";
import { useSession } from "../src/shared/session";
import type { TeacherChapter, KnowledgePoint } from "../src/shared/curriculum";
const chapters: TeacherChapter[] = [
  { id: 179, title: "共享第一章", order_index: 1, owner_id: null },
  { id: 300, title: "我的扩展章节", order_index: 8, owner_id: 9 },
];
const points: KnowledgePoint[] = [
  { id: 400, chapter_id: 179, title: "共享计算思维", owner_id: null },
  { id: 401, chapter_id: 300, title: "自建知识点", owner_id: 9 },
];
// Unit tests exercise the authoring state and payloads; actual Reka portal/focus behavior is covered in adminManagement E2E.
const AdminDialogStub = defineComponent({
  props: ["open", "title", "description"],
  emits: ["update:open"],
  template:
    '<section v-if="open" role="dialog" :aria-label="title"><h2>{{ title }}</h2><slot /><button aria-label="关闭弹窗" @click="$emit(\'update:open\', false)">关闭</button></section>',
});
const mounted: VueWrapper[] = [];
function setup(
  data: { chapters: TeacherChapter[]; knowledge_points: KnowledgePoint[] } = {
    chapters,
    knowledge_points: points,
  },
) {
  const pinia = createPinia();
  setActivePinia(pinia);
  useSession().user = {
    id: 9,
    name: "Teacher",
    email: "teacher@example.test",
    role: "teacher",
  };
  vi.spyOn(api, "get").mockResolvedValue(data);
  const wrapper = mount(TeacherCurriculum, {
    attachTo: document.body,
    global: { plugins: [pinia], stubs: { AdminDialog: AdminDialogStub } },
  });
  mounted.push(wrapper);
  return wrapper;
}
function button(root: VueWrapper | DOMWrapper<Element>, text: string) {
  const result = root.findAll("button").find((b) => b.text() === text);
  if (!result) throw new Error(`Missing button: ${text}`);
  return result;
}
afterEach(() => {
  for (const wrapper of mounted.splice(0)) wrapper.unmount();
  vi.restoreAllMocks();
});

describe("teacher curriculum tabs, ownership and authoring", () => {
  it("exposes owned actions in each tab while shared curriculum remains read only", async () => {
    const wrapper = setup();
    await flushPromises();
    const chapterPanel = wrapper.find("#curriculum-chapters-panel");
    const knowledgePanel = wrapper.find("#curriculum-knowledge-panel");
    expect(chapterPanel.isVisible()).toBe(true);
    expect(knowledgePanel.isVisible()).toBe(false);
    expect(
      chapterPanel.findAll("button").filter((b) => b.text() === "编辑章节"),
    ).toHaveLength(1);
    const shared = chapterPanel
      .findAll("tbody tr")
      .find((row) => row.text().includes("共享第一章"))!;
    expect(shared.text()).toContain("只读");
    expect(shared.findAll("button")).toHaveLength(0);
    await wrapper.find("#curriculum-knowledge-tab").trigger("click");
    expect(knowledgePanel.isVisible()).toBe(true);
    expect(chapterPanel.isVisible()).toBe(false);
    expect(
      knowledgePanel.findAll("button").filter((b) => b.text() === "编辑知识点"),
    ).toHaveLength(1);
    expect(
      knowledgePanel.findAll("tbody tr")[0]!.findAll("button"),
    ).toHaveLength(0);
  });
  it("supports roving tab focus with arrows, Home and End", async () => {
    const wrapper = setup();
    await flushPromises();
    const chapterTab = wrapper.find("#curriculum-chapters-tab");
    const knowledgeTab = wrapper.find("#curriculum-knowledge-tab");
    await chapterTab.trigger("keydown", { key: "ArrowRight" });
    expect(knowledgeTab.attributes("aria-selected")).toBe("true");
    expect(knowledgeTab.attributes("tabindex")).toBe("0");
    expect(chapterTab.attributes("tabindex")).toBe("-1");
    expect(document.activeElement).toBe(knowledgeTab.element);
    await knowledgeTab.trigger("keydown", { key: "Home" });
    expect(chapterTab.attributes("aria-selected")).toBe("true");
    await chapterTab.trigger("keydown", { key: "End" });
    expect(knowledgeTab.attributes("aria-selected")).toBe("true");
    await knowledgeTab.trigger("keydown", { key: "ArrowLeft" });
    expect(chapterTab.attributes("aria-selected")).toBe("true");
  });
  it("creates a chapter through a dialog and omits an unspecified order from the payload", async () => {
    const wrapper = setup();
    const post = vi.spyOn(api, "post").mockResolvedValue({ id: 301 });
    await flushPromises();
    expect(wrapper.find("form").exists()).toBe(false);
    await button(
      wrapper.find("#curriculum-chapters-panel"),
      "新增章节",
    ).trigger("click");
    const dialog = wrapper.find('[role="dialog"][aria-label="新增章节"]');
    await dialog.find("input").setValue("新的教学章节");
    await dialog.find("form").trigger("submit");
    await flushPromises();
    expect(post).toHaveBeenCalledWith("/teacher/chapters", {
      title: "新的教学章节",
    });
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
  });
  it("retains canceled drafts and shows a rejected save inside the dialog", async () => {
    const wrapper = setup();
    vi.spyOn(api, "post").mockRejectedValue(new Error("章节标题已存在"));
    await flushPromises();
    await button(
      wrapper.find("#curriculum-chapters-panel"),
      "新增章节",
    ).trigger("click");
    let dialog = wrapper.find('[role="dialog"]');
    await dialog.find("input").setValue("保留的章节草稿");
    await dialog.find('input[type="number"]').setValue("12");
    await dialog.find('[aria-label="关闭弹窗"]').trigger("click");
    await button(
      wrapper.find("#curriculum-chapters-panel"),
      "新增章节",
    ).trigger("click");
    dialog = wrapper.find('[role="dialog"]');
    expect((dialog.find("input").element as HTMLInputElement).value).toBe(
      "保留的章节草稿",
    );
    expect(
      (dialog.find('input[type="number"]').element as HTMLInputElement).value,
    ).toBe("12");
    await dialog.find("form").trigger("submit");
    await flushPromises();
    expect(dialog.find('[role="alert"]').text()).toBe("章节标题已存在");
    expect((dialog.find("input").element as HTMLInputElement).value).toBe(
      "保留的章节草稿",
    );
  });
  it("never moves an existing knowledge point to another chapter through its update payload", async () => {
    const wrapper = setup();
    const put = vi.spyOn(api, "put").mockResolvedValue({ id: 401 });
    await flushPromises();
    await wrapper.find("#curriculum-knowledge-tab").trigger("click");
    await button(
      wrapper.find("#curriculum-knowledge-panel"),
      "编辑知识点",
    ).trigger("click");
    const form = wrapper.find('[role="dialog"] form');
    expect(form.find("select").attributes("disabled")).toBeDefined();
    await form.find("input").setValue("修订知识点名称");
    await form.trigger("submit");
    await flushPromises();
    expect(put).toHaveBeenCalledWith("/teacher/knowledge-points/401", {
      title: "修订知识点名称",
    });
  });
  it("keeps deletion confirmation and server reference errors within the deletion dialog", async () => {
    const wrapper = setup();
    const remove = vi
      .spyOn(api, "request")
      .mockRejectedValue(new Error("章节已被学习活动引用"));
    await flushPromises();
    await button(
      wrapper.find("#curriculum-chapters-panel"),
      "删除章节",
    ).trigger("click");
    const dialog = wrapper.find('[role="dialog"][aria-label="删除课程内容"]');
    expect(remove).not.toHaveBeenCalled();
    await button(dialog, "确认删除").trigger("click");
    await flushPromises();
    expect(remove).toHaveBeenCalledWith("/teacher/chapters/300", {
      method: "DELETE",
    });
    expect(dialog.find('[role="alert"]').text()).toBe("章节已被学习活动引用");
  });
});

describe("curriculum list pagination", () => {
  it("limits chapter rows, resets page on search/page-size changes, and exposes empty states", async () => {
    const manyChapters = Array.from({ length: 25 }, (_, i) => ({
      id: i + 1,
      title: `Chapter ${String(i + 1).padStart(2, "0")}`,
      order_index: i + 1,
      owner_id: 9,
    }));
    const wrapper = setup({ chapters: manyChapters, knowledge_points: [] });
    await flushPromises();
    const panel = wrapper.find("#curriculum-chapters-panel");
    expect(panel.findAll("tbody tr")).toHaveLength(10);
    await panel.find('[aria-label="下一页"]').trigger("click");
    expect(panel.findAll("tbody tr")[0]!.text()).toContain("Chapter 11");
    await panel.find('input[type="search"]').setValue("Chapter 25");
    expect(panel.findAll("tbody tr")).toHaveLength(1);
    expect(panel.text()).toContain("第 1 / 1 页");
    await panel.find('input[type="search"]').setValue("");
    await panel.find('[aria-label="下一页"]').trigger("click");
    await panel.find('[aria-label="每页条数"]').setValue("20");
    expect(panel.findAll("tbody tr")).toHaveLength(20);
    expect(panel.findAll("tbody tr")[0]!.text()).toContain("Chapter 01");
    await panel.find('input[type="search"]').setValue("missing");
    expect(panel.findAll("tbody tr")).toHaveLength(0);
    expect(panel.find(".admin-empty").text()).toContain("没有匹配的章节");
    expect(
      panel.find('[aria-label="下一页"]').attributes("disabled"),
    ).toBeDefined();
  });
  it("paginates knowledge points within the chapter filter and resets search/filter pages independently", async () => {
    const manyPoints = Array.from({ length: 25 }, (_, i) => ({
      id: i + 1,
      chapter_id: i < 13 ? 179 : 300,
      title: `KC ${String(i + 1).padStart(2, "0")}`,
      owner_id: 9,
    }));
    const wrapper = setup({ chapters, knowledge_points: manyPoints });
    await flushPromises();
    await wrapper.find("#curriculum-knowledge-tab").trigger("click");
    const panel = wrapper.find("#curriculum-knowledge-panel");
    expect(panel.findAll("tbody tr")).toHaveLength(10);
    await panel.find('[aria-label="下一页"]').trigger("click");
    await panel.find('[aria-label="下一页"]').trigger("click");
    expect(panel.findAll("tbody tr")).toHaveLength(5);
    await panel.find(".table-toolbar select").setValue("179");
    expect(panel.text()).toContain("第 1 / 2 页");
    expect(panel.findAll("tbody tr")).toHaveLength(10);
    await panel.find('[aria-label="下一页"]').trigger("click");
    expect(panel.findAll("tbody tr")).toHaveLength(3);
    await panel.find('input[type="search"]').setValue("KC 13");
    expect(panel.findAll("tbody tr")).toHaveLength(1);
    expect(panel.text()).toContain("第 1 / 1 页");
    await button(panel, "新增知识点").trigger("click");
    expect(
      (wrapper.find('[role="dialog"] select').element as HTMLSelectElement)
        .value,
    ).toBe("179");
  });
});

describe("activity knowledge point binding", () => {
  it("shows only the selected chapter and clears bindings from a previous chapter", async () => {
    const wrapper = mount(KnowledgePointSelect, {
      props: { chapterId: 179, points, modelValue: [400] },
    });
    mounted.push(wrapper);
    expect(wrapper.text()).toContain("共享计算思维");
    expect(wrapper.text()).not.toContain("自建知识点");
    await wrapper.setProps({ chapterId: 300 });
    expect(wrapper.emitted("update:modelValue")?.[0]).toEqual([[]]);
  });
});
