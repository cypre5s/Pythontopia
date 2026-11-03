import { describe, it, expect, vi, afterEach } from "vitest";
import { mount, flushPromises } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import TeacherCurriculum from "../src/features/TeacherCurriculum.vue";
import KnowledgePointSelect from "../src/features/KnowledgePointSelect.vue";
import { api } from "../src/shared/api";
import { useSession } from "../src/shared/session";
const chapters = [
  { id: 179, title: "共享第一章", order_index: 1, owner_id: null },
  { id: 300, title: "我的扩展章节", order_index: 8, owner_id: 9 },
];
const points = [
  { id: 400, chapter_id: 179, title: "共享计算思维", owner_id: null },
  { id: 401, chapter_id: 300, title: "自建知识点", owner_id: 9 },
];
function setup() {
  const pinia = createPinia();
  setActivePinia(pinia);
  useSession().user = {
    id: 9,
    name: "Teacher",
    email: "teacher@example.test",
    role: "teacher",
  };
  vi.spyOn(api, "get").mockResolvedValue({
    chapters,
    knowledge_points: points,
  });
  return mount(TeacherCurriculum, { global: { plugins: [pinia] } });
}
afterEach(() => vi.restoreAllMocks());
describe("teacher curriculum ownership and authoring", () => {
  it("keeps shared course entries read only and exposes editing only for owned content", async () => {
    const wrapper = setup();
    await flushPromises();
    expect(
      wrapper.findAll("button").filter((b) => b.text() === "编辑章节"),
    ).toHaveLength(1);
    expect(
      wrapper.findAll("button").filter((b) => b.text() === "删除章节"),
    ).toHaveLength(1);
    expect(
      wrapper.findAll("button").filter((b) => b.text() === "编辑知识点"),
    ).toHaveLength(1);
    expect(wrapper.text()).toContain("共享第一章");
    expect(wrapper.text()).toContain("只读");
  });
  it("omits default order and relies on the authoritative server sequence when creating a chapter", async () => {
    const wrapper = setup();
    const post = vi.spyOn(api, "post").mockResolvedValue({ id: 301 });
    await flushPromises();
    const form = wrapper.findAll("form")[0]!;
    await form.find("input").setValue("新的教学章节");
    await form.trigger("submit");
    await flushPromises();
    expect(post).toHaveBeenCalledWith("/teacher/chapters", {
      title: "新的教学章节",
    });
  });
  it("cannot move an existing knowledge point to another chapter through its update payload", async () => {
    const wrapper = setup();
    const put = vi.spyOn(api, "put").mockResolvedValue({ id: 401 });
    await flushPromises();
    await wrapper
      .findAll("button")
      .find((b) => b.text() === "编辑知识点")!
      .trigger("click");
    const form = wrapper.findAll("form")[1]!;
    expect(form.find("select").attributes("disabled")).toBeDefined();
    await form.find("input").setValue("修订知识点名称");
    await form.trigger("submit");
    await flushPromises();
    expect(put).toHaveBeenCalledWith("/teacher/knowledge-points/401", {
      title: "修订知识点名称",
    });
  });
});
describe("activity knowledge point binding", () => {
  it("shows only the selected chapter and clears bindings from a previous chapter", async () => {
    const wrapper = mount(KnowledgePointSelect, {
      props: { chapterId: 179, points, modelValue: [400] },
    });
    expect(wrapper.text()).toContain("共享计算思维");
    expect(wrapper.text()).not.toContain("自建知识点");
    await wrapper.setProps({ chapterId: 300 });
    expect(wrapper.emitted("update:modelValue")?.[0]).toEqual([[]]);
  });
});
