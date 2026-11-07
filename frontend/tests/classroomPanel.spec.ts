import { afterEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import ClassroomPanel from "../src/features/ClassroomPanel.vue";
import { api } from "../src/shared/api";
import { useSession } from "../src/shared/session";
import type { QuestNode, World } from "../src/shared/types";

function setup() {
  const pinia = createPinia();
  setActivePinia(pinia);
  const session = useSession();
  session.user = { id: 1, name: "测试居民", email: "resident@example.test", role: "student" };
  session.classes = [
    { id: 2, name: "入门班", teacher_id: 10, teacher_name: "林老师" },
    { id: 3, name: "进阶班", teacher_id: 11, teacher_name: "陈老师" },
  ];
  session.classroomId = 2;
  session.workspace = { kind: "classroom" };
  const node = (id: string, status: QuestNode["status"], optional = false): QuestNode => ({
    id, status, optional, title: id === "study" ? "阅读指定课件" : id, type: "STUDY_LESSON", target: "librarian_doc",
    dependencies: [], aggregation: "ALL",
  });
  session.world = {
    player: { map_key: "town_hub", tile_x: 22, tile_y: 36, direction: "down" }, npcs: [], flags: [],
    chapters: [{ id: 179, title: "第一章", order_index: 1, available: true }, { id: 180, title: "第二章", order_index: 2, available: false }],
    quests: [{ id: "arrival", title: "初来小镇：第一堂 Python 课", chapter_id: 179, kind: "main", status: "active",
      nodes: [node("welcome", "complete"), node("study", "ready"), node("extra", "ready", true), node("other-route", "skipped")] }],
  } satisfies World;
  return { session, pinia };
}
function mountPanel() {
  const { session, pinia } = setup();
  const wrapper = mount(ClassroomPanel, { global: { plugins: [pinia] } });
  return { wrapper, session };
}
async function select(wrapper: VueWrapper, name: string) {
  const button = wrapper.findAll(".town-directory-item").find(item => item.text() === name);
  expect(button).toBeDefined();
  await button!.trigger("click");
}

afterEach(() => { vi.restoreAllMocks(); sessionStorage.clear(); document.body.innerHTML = ""; });

describe("classroom workspace", () => {
  it("shows the actual class and task progress while excluding optional and unselected steps", () => {
    const { wrapper } = mountPanel();
    expect(wrapper.findAll("h2")).toHaveLength(0);
    expect(wrapper.get(".current-classroom h3").text()).toBe("入门班");
    expect(wrapper.text()).toContain("授课教师：林老师");
    expect(wrapper.text()).toContain("1 章已开放 · 0 章已完成");
    expect(wrapper.get(".classroom-progress-caption").text()).toContain("1 / 2 步");
    expect(wrapper.get("progress").attributes("value")).toBe("1");
    expect(wrapper.get("progress").attributes("max")).toBe("2");
    expect(wrapper.get(".classroom-next-goal").text()).toContain("阅读指定课件");
    expect(wrapper.text()).not.toContain("掌握率");
    wrapper.unmount();
  });

  it("switches through the store and keeps the class workspace visible", async () => {
    const { wrapper, session } = mountPanel();
    const switchClassroom = vi.spyOn(session, "switchClassroom").mockImplementation(async id => {
      session.classroomId = id;
      session.world = { ...session.world!, quests: [] };
      return true;
    });
    await select(wrapper, "切换班级");
    await wrapper.get('[aria-label="切换到：进阶班"]').trigger("click");
    await flushPromises();
    expect(switchClassroom).toHaveBeenCalledOnce();
    expect(switchClassroom).toHaveBeenCalledWith(3);
    expect(session.workspace).toEqual({ kind: "classroom" });
    expect(wrapper.get(".current-classroom h3").text()).toBe("进阶班");
    expect(wrapper.get('[role="status"]').text()).toContain("已切换到进阶班");
    wrapper.unmount();
  });

  it("retains the selected class when switching fails and allows retry", async () => {
    const { wrapper, session } = mountPanel();
    vi.spyOn(session, "switchClassroom").mockRejectedValue(new Error("班级暂时无法载入"));
    await select(wrapper, "切换班级");
    await wrapper.get('[aria-label="切换到：进阶班"]').trigger("click");
    await flushPromises();
    expect(session.classroomId).toBe(2);
    expect(wrapper.get('[role="alert"]').text()).toBe("班级暂时无法载入");
    expect(wrapper.get('[aria-label="切换到：进阶班"]').attributes("disabled")).toBeUndefined();
    wrapper.unmount();
  });

  it("does not announce a superseded switch as successful", async () => {
    const { wrapper, session } = mountPanel();
    vi.spyOn(session, "switchClassroom").mockResolvedValue(false);
    await select(wrapper, "切换班级");
    await wrapper.get('[aria-label="切换到：进阶班"]').trigger("click");
    await flushPromises();
    expect(session.classroomId).toBe(2);
    expect(wrapper.find('[role="status"]').exists()).toBe(false);
    expect(wrapper.get('[aria-label="切换到：进阶班"]').attributes("disabled")).toBeUndefined();
    wrapper.unmount();
  });

  it("joins and refreshes membership without forcing a classroom switch", async () => {
    const { wrapper, session } = mountPanel();
    const post = vi.spyOn(api, "post").mockResolvedValue({ ok: true });
    const load = vi.spyOn(session, "load").mockImplementation(async () => {
      session.classes.push({ id: 4, name: "新班级", teacher_id: 12, teacher_name: "新老师" });
    });
    await select(wrapper, "加入班级");
    await wrapper.get("#classroom-join-code").setValue("  code-from-teacher  ");
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(post).toHaveBeenCalledOnce();
    expect(post).toHaveBeenCalledWith("/classrooms/join", { join_code: "code-from-teacher" });
    expect(load).toHaveBeenCalledOnce();
    expect(session.classroomId).toBe(2);
    expect(wrapper.get('[aria-label="切换到：新班级"]').exists()).toBe(true);
    expect(wrapper.get('[role="status"]').text()).toContain("可以在列表中选择切换");
    wrapper.unmount();
  });

  it("does not reload a different account after an old join request completes", async () => {
    const { wrapper, session } = mountPanel();
    let resolve!: (value: unknown) => void;
    vi.spyOn(api, "post").mockReturnValue(new Promise(done => { resolve = done; }));
    const load = vi.spyOn(session, "load");
    await select(wrapper, "加入班级");
    await wrapper.get("#classroom-join-code").setValue("old-account-code");
    await wrapper.get("form").trigger("submit");
    session.user = { id: 9, name: "另一居民", email: "other@example.test", role: "student" };
    resolve({ ok: true });
    await flushPromises();
    expect(load).not.toHaveBeenCalled();
    expect(wrapper.find('[role="status"]').exists()).toBe(false);
    wrapper.unmount();
  });
});
