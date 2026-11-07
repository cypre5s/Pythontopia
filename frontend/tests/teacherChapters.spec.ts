import { afterEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import TeacherChapters from "../src/features/TeacherChapters.vue";
import { api } from "../src/shared/api";

const chapters = [
  { id: 179, title: "计算思维", order_index: 1, available: true },
  { id: 180, title: "输入输出", order_index: 2, available: true },
];
const existing = {
  chapter_id: 179,
  status: "published",
  opens_at: "2099-01-02T01:00:00+00:00",
  prerequisite_chapter_id: 178 };
const wrappers: VueWrapper[] = [];
async function setup(rules = [existing]) {
  vi.spyOn(api, "get").mockImplementation(async (path) => {
    if (path.endsWith("/releases")) return rules as never;
    return [
      { chapter_id: 179 },
      { chapter_id: 180 },
    ] as never;
  });
  const put = vi
    .spyOn(api, "put")
    .mockImplementation(
      async (_path, body) =>
        ({ chapter_id: 179, ...(body as object) }) as never,
    );
  const wrapper = mount(TeacherChapters, {
    props: { classroomId: 10, classroomName: "一班", chapters } });
  wrappers.push(wrapper);
  await flushPromises();
  return { wrapper, put };
}
afterEach(() => {
  wrappers.splice(0).forEach((w) => w.unmount());
  vi.restoreAllMocks();
});
describe("teacher chapter schedules", () => {
  it("hydrates actual saved dates in the configured timezone and hides engine controls", async () => {
    const { wrapper } = await setup();
    expect(
      wrapper.find('input[aria-label="计算思维 解锁时间"]').element,
    ).toHaveProperty("value", "2099-01-02T09:00");
    expect(wrapper.text()).toContain("待解锁");
    expect(wrapper.text()).toContain("未安排");
    expect(wrapper.text()).not.toContain("发布任务版本");
    expect(wrapper.findAll("select")).toHaveLength(0);
  });
  it("reschedules while preserving the existing prerequisite", async () => {
    const { wrapper, put } = await setup();
    const row = wrapper.findAll("tbody tr")[0]!;
    await row.find("input").setValue("2099-01-04T10:30");
    await row
      .findAll("button")
      .find((b) => b.text() === "保存")!
      .trigger("click");
    await flushPromises();
    expect(put).toHaveBeenCalledWith("/teacher/classrooms/10/releases/179", {
      status: "published",
      opens_at: "2099-01-04T02:30:00.000Z",
      prerequisite_chapter_id: 178 });
  });
  it("stores immediate unlock without a content selector", async () => {
    const { wrapper, put } = await setup([]);
    const row = wrapper.findAll("tbody tr")[1]!;
    await row
      .findAll("button")
      .find((b) => b.text() === "立即解锁")!
      .trigger("click");
    await flushPromises();
    expect(put).toHaveBeenCalledWith("/teacher/classrooms/10/releases/180", {
      status: "published",
      opens_at: null,
      prerequisite_chapter_id: null });
  });
  it("schedules an unarranged chapter without fetching task definitions", async () => {
    const { wrapper, put } = await setup([]);
    const row = wrapper.findAll("tbody tr")[1]!;
    await row.findAll("button").find(b => b.text() === "立即解锁")!.trigger("click");
    await flushPromises();
    expect(put).toHaveBeenCalledWith("/teacher/classrooms/10/releases/180", {
      status: "published", opens_at: null, prerequisite_chapter_id: null,
    });
    expect(api.get).not.toHaveBeenCalledWith("/teacher/quests");
  });
  it("preserves a saved date when pausing a chapter", async () => {
    const { wrapper, put } = await setup();
    await wrapper
      .findAll("tbody tr")[0]!
      .findAll("button")
      .find((b) => b.text() === "暂停")!
      .trigger("click");
    await flushPromises();
    expect(put).toHaveBeenCalledWith("/teacher/classrooms/10/releases/179", {
      status: "paused",
      opens_at: existing.opens_at,
      prerequisite_chapter_id: 178 });
  });
  it("keeps the edited date on server failure instead of displaying an unsaved success", async () => {
    const { wrapper, put } = await setup();
    put.mockRejectedValueOnce(new Error("保存失败，请重试"));
    const row = wrapper.findAll("tbody tr")[0]!;
    await row.find("input").setValue("2099-01-08T10:00");
    await row
      .findAll("button")
      .find((b) => b.text() === "保存")!
      .trigger("click");
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain("保存失败，请重试");
    expect(row.find("input").element).toHaveProperty(
      "value",
      "2099-01-08T10:00",
    );
    expect(wrapper.text()).toContain("待解锁");
  });
  it("ignores a delayed old-class response after switching classes", async () => {
    const { wrapper, put } = await setup();
    let resolve!: (value: unknown) => void;
    put.mockImplementationOnce(
      () =>
        new Promise((r) => {
          resolve = r;
        }) as never,
    );
    await wrapper
      .findAll("tbody tr")[0]!
      .findAll("button")
      .find((b) => b.text() === "立即解锁")!
      .trigger("click");
    vi.mocked(api.get).mockImplementation(async (path) =>
      path.endsWith("/releases") ? ([] as never) : ([] as never),
    );
    await wrapper.setProps({ classroomId: 11, classroomName: "二班" });
    await flushPromises();
    resolve({ ...existing, status: "published", opens_at: null });
    await flushPromises();
    expect(wrapper.findAll("tbody tr")[0]!.text()).toContain("未安排");
    expect(wrapper.text()).not.toContain("已设为立即解锁");
  });
});
