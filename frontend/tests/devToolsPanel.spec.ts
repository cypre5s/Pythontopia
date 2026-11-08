import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import DevToolsPanel from "../src/features/DevToolsPanel.vue";
import GameHUD from "../src/features/GameHUD.vue";
import OverlayHost from "../src/features/OverlayHost.vue";
import { useSession } from "../src/shared/session";
import { api } from "../src/shared/api";

const wrappers: VueWrapper[] = [];
beforeEach(() => {
  setActivePinia(createPinia());
  const session = useSession();
  session.user = { id: 7, name: "小林", student_id: "97972435969354", role: "student", dev_tools_enabled: true };
  session.classes = [{ id: 2, name: "第一班", teacher_id: 10 }, { id: 3, name: "第二班", teacher_id: 10 }];
  session.classroomId = 2;
  api.setToken("fixture-session");
});
afterEach(() => {
  for (const wrapper of wrappers.splice(0)) wrapper.unmount();
  document.body.replaceChildren();
  vi.restoreAllMocks(); sessionStorage.clear(); localStorage.clear();
});
function panel() {
  const wrapper = mount(DevToolsPanel, { attachTo: document.body });
  wrappers.push(wrapper);
  return wrapper;
}
async function confirm(wrapper: VueWrapper) {
  await wrapper.get(".dev-tools-reset > button").trigger("click");
  await wrapper.get(".dev-tools-confirm .town-primary").trigger("click");
}

describe("developer workspace", () => {
  it("adds a native menu entry after settings only for the server-enabled student", async () => {
    const session = useSession(), open = vi.spyOn(session, "open");
    const wrapper = mount(GameHUD); wrappers.push(wrapper);
    expect(wrapper.get('nav[aria-label="小镇菜单"]').findAll("button").map(button => button.text())).toEqual(["任务日志J", "学习资料", "地图", "我的班级", "设置Esc", "开发工具"]);
    const entry = wrapper.get(".hud-dock button:last-child");
    expect(entry.find("svg").exists()).toBe(true);
    await entry.trigger("click");
    expect(open).toHaveBeenCalledWith({ kind: "developer" });
    session.user!.dev_tools_enabled = false;
    await flushPromises();
    expect(wrapper.text()).not.toContain("开发工具");
    session.user = { ...session.user!, role: "teacher", dev_tools_enabled: true };
    await flushPromises();
    expect(wrapper.text()).not.toContain("开发工具");
  });

  it("shows the shared header and never falls through to settings for unavailable developer tools", async () => {
    const session = useSession();
    session.open({ kind: "library" });
    session.navigate({ kind: "developer" });
    const back = vi.spyOn(session, "back");
    const wrapper = mount(OverlayHost, { attachTo: document.body, global: { stubs: { SettingsPanel: true } } }); wrappers.push(wrapper);
    await flushPromises();
    expect(wrapper.findAll("h2").map(heading => heading.text())).toEqual(["开发工具"]);
    expect(wrapper.findComponent(DevToolsPanel).exists()).toBe(true);
    session.resetBusy = true;
    await flushPromises();
    expect(wrapper.get(".return-to-town").attributes("disabled")).toBeDefined();
    expect(wrapper.get(".workspace-back").attributes("disabled")).toBeDefined();
    await wrapper.get(".return-to-town").trigger("click");
    await wrapper.get(".workspace-back").trigger("click");
    window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    expect(wrapper.emitted("close")).toBeUndefined();
    expect(back).not.toHaveBeenCalled();
    session.resetBusy = false;
    session.user!.dev_tools_enabled = false;
    await flushPromises();
    expect(wrapper.findComponent(DevToolsPanel).exists()).toBe(false);
    expect(wrapper.find("settings-panel-stub").exists()).toBe(false);
    expect(wrapper.get('[role="alert"]').text()).toBe("当前功能不可用。");
  });

  it("shows the account, classroom and consequences before one cancellable confirmation", async () => {
    const restart = vi.spyOn(useSession(), "restartCurrentClassroom").mockResolvedValue(true), wrapper = panel();
    expect(wrapper.text()).toContain("97972435969354");
    expect(wrapper.text()).toContain("第一班");
    expect(wrapper.text()).toContain("练习和测试成绩");
    expect(wrapper.text()).toContain("课程材料和界面偏好会保留");
    expect(wrapper.find("h2").exists()).toBe(false);
    await wrapper.get(".dev-tools-reset > button").trigger("click");
    expect(restart).not.toHaveBeenCalled();
    expect(wrapper.get(".dev-tools-confirm").text()).toContain("确认重新开始“第一班”？");
    await wrapper.get(".dev-tools-confirm .town-secondary").trigger("click");
    expect(wrapper.find(".dev-tools-confirm").exists()).toBe(false);
    expect(restart).not.toHaveBeenCalled();
  });

  it("waits for the reset and prevents duplicate confirmation requests", async () => {
    let finish!: (value: boolean) => void;
    const restart = vi.spyOn(useSession(), "restartCurrentClassroom").mockImplementation(() => new Promise(resolve => { finish = resolve; }));
    const wrapper = panel();
    await confirm(wrapper);
    expect(wrapper.get(".dev-tools-confirm .town-primary").text()).toBe("正在重新开始…");
    expect(wrapper.get(".dev-tools-confirm .town-primary").attributes("disabled")).toBeDefined();
    await wrapper.get(".dev-tools-confirm .town-primary").trigger("click");
    expect(restart).toHaveBeenCalledTimes(1);
    finish(true); await flushPromises();
    expect(wrapper.get('[role="status"]').text()).toBe("已重新开始当前班级。");
    expect(wrapper.find(".dev-tools-confirm").exists()).toBe(false);
  });

  it.each([false, new Error("请求失败，请重试。")])("keeps caches and shows a retryable failure for %s", async result => {
    const restart = vi.spyOn(useSession(), "restartCurrentClassroom");
    if (result instanceof Error) restart.mockRejectedValue(result); else restart.mockResolvedValue(result);
    sessionStorage.setItem("read:7:2:material-186", "42"); localStorage.setItem("draft:7:2:q1", "answer");
    const wrapper = panel(); await confirm(wrapper); await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain(result instanceof Error ? result.message : "重新开始未完成");
    expect(wrapper.get(".dev-tools-confirm .town-primary").attributes("disabled")).toBeUndefined();
    expect(sessionStorage.getItem("read:7:2:material-186")).toBe("42");
    expect(localStorage.getItem("draft:7:2:q1")).toBe("answer");
  });

  it.each(["account", "classroom", "capability"])("cancels an old confirmation after changing %s", async change => {
    const session = useSession(), restart = vi.spyOn(session, "restartCurrentClassroom"), wrapper = panel();
    await wrapper.get(".dev-tools-reset > button").trigger("click");
    if (change === "account") session.user = { ...session.user!, id: 8 };
    else if (change === "classroom") session.classroomId = 3;
    else session.user!.dev_tools_enabled = false;
    await flushPromises();
    expect(wrapper.find(".dev-tools-confirm").exists()).toBe(false);
    expect(restart).not.toHaveBeenCalled();
  });

  it("ignores a late failure after changing classroom and does not clear either classroom cache", async () => {
    let fail!: (reason: Error) => void;
    const session = useSession();
    vi.spyOn(session, "restartCurrentClassroom").mockImplementation(() => new Promise((_, reject) => { fail = reject; }));
    sessionStorage.setItem("read:7:2:material-186", "42"); sessionStorage.setItem("read:7:3:material-186", "1");
    const wrapper = panel(); await confirm(wrapper);
    session.classroomId = 3; await flushPromises();
    fail(new Error("旧班级请求失败")); await flushPromises();
    expect(wrapper.text()).toContain("第二班");
    expect(wrapper.text()).not.toContain("旧班级请求失败");
    expect(wrapper.find(".dev-tools-confirm").exists()).toBe(false);
    expect(sessionStorage.getItem("read:7:2:material-186")).toBe("42"); expect(sessionStorage.getItem("read:7:3:material-186")).toBe("1");
  });

  it("disables restarting without a current classroom", async () => {
    useSession().classroomId = null;
    const restart = vi.spyOn(useSession(), "restartCurrentClassroom"), wrapper = panel();
    expect(wrapper.get(".dev-tools-reset > button").attributes("disabled")).toBeDefined();
    await wrapper.get(".dev-tools-reset > button").trigger("click");
    expect(restart).not.toHaveBeenCalled();
  });
});
