import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { nextTick } from "vue";
import SettingsPanel from "../src/features/SettingsPanel.vue";
import { useSession } from "../src/shared/session";
import { useUiPreferences } from "../src/shared/uiPreferences";
import { api } from "../src/shared/api";

const mounted: VueWrapper[] = [], disconnect: (() => void)[] = [];

beforeEach(() => {
  setActivePinia(createPinia());
  localStorage.clear();
  sessionStorage.clear();
  vi.stubGlobal("matchMedia", vi.fn(() => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() })));
});
afterEach(() => {
  for (const wrapper of mounted.splice(0)) wrapper.unmount();
  for (const stop of disconnect.splice(0)) stop();
  document.body.replaceChildren();
  localStorage.clear();
  sessionStorage.clear();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

async function panel(role = "student") {
  const session = useSession();
  session.user = { id: 7, name: "小林", email: "learner@example.test", role };
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: "/", component: { template: "<div>登录</div>" } },
    { path: "/world", component: { template: "<div>小镇</div>" } },
    { path: "/teacher", component: { template: "<div>工作台</div>" } },
  ] });
  await router.push("/world");
  await router.isReady();
  const wrapper = mount(SettingsPanel, { attachTo: document.body, global: { plugins: [router] } });
  mounted.push(wrapper);
  return { session, wrapper, router, preferences: useUiPreferences() };
}

describe("categorized settings", () => {
  it("opens account first without classroom, materials or external credits navigation", async () => {
    const { wrapper } = await panel();
    const tabs = wrapper.findAll('[role="tab"]');
    expect(tabs.map(tab => tab.text())).toEqual(["账户", "界面", "音乐", "操作", "关于"]);
    expect(tabs[0].attributes("aria-selected")).toBe("true");
    expect(wrapper.find('[role="tabpanel"]').text()).toContain("learner@example.test");
    expect(wrapper.text()).toContain("学生");
    expect(wrapper.findAll("h2")).toHaveLength(0);
    expect(wrapper.text()).not.toContain("欢迎回来");
    expect(wrapper.text()).not.toContain("班级与课程");
    expect(wrapper.text()).not.toContain("已开放材料");
    expect(wrapper.find('a[href="/credits"]').exists()).toBe(false);
    expect(wrapper.find('a[href="/teacher"]').exists()).toBe(false);
    await tabs[4].trigger("click");
    expect(wrapper.find('[role="tabpanel"]').text()).toContain("Super Retro World");
    expect(wrapper.find('[role="tabpanel"]').text()).toContain("Alethicode");
    expect(wrapper.find('a[href*="LICENSE-ART.md"]').attributes("target")).toBe("_blank");
    expect(wrapper.find('a[href="/credits"]').exists()).toBe(false);
  });

  it("keeps music and interface controls live without changing activities or navigating workspaces", async () => {
    const get = vi.spyOn(api, "get"), post = vi.spyOn(api, "post");
    const { wrapper, preferences, session } = await panel();
    const open = vi.spyOn(session, "open"), navigate = vi.spyOn(session, "navigate");
    disconnect.push(preferences.connect());
    await wrapper.find("#settings-tab-music").trigger("click");
    const volume = wrapper.find<HTMLInputElement>("#settings-music-volume");
    expect(volume.element.disabled).toBe(true);
    await wrapper.find("#settings-music-enabled").setValue(true);
    expect(session.audioEnabled).toBe(true);
    expect(volume.element.disabled).toBe(false);
    await volume.setValue("0.62");
    expect(preferences.musicVolume).toBe(0.62);
    expect(wrapper.find("output").text()).toBe("62%");
    await wrapper.find("#settings-tab-interface").trigger("click");
    await wrapper.find("#settings-text-scale").setValue("2");
    await wrapper.find("#settings-reduce-motion").setValue(true);
    await nextTick();
    expect(preferences.textScale).toBe(2);
    expect(preferences.reduceMotion).toBe(true);
    expect(JSON.parse(localStorage.getItem("pythontopia-reborn-interface")!)).toEqual({ textScale: 2, reduceMotion: true, musicVolume: 0.62 });
    expect(get).not.toHaveBeenCalled();
    expect(post).not.toHaveBeenCalled();
    expect(open).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  it("moves keyboard focus between category tabs", async () => {
    const { wrapper } = await panel();
    await wrapper.find("#settings-tab-account").trigger("keydown", { key: "ArrowDown" });
    await nextTick();
    expect(document.activeElement?.id).toBe("settings-tab-interface");
    expect(wrapper.find("#settings-tab-interface").attributes("aria-selected")).toBe("true");
    await wrapper.find("#settings-tab-interface").trigger("keydown", { key: "End" });
    await nextTick();
    expect(document.activeElement?.id).toBe("settings-tab-about");
    await wrapper.find("#settings-tab-about").trigger("keydown", { key: "Home" });
    await nextTick();
    expect(document.activeElement?.id).toBe("settings-tab-account");
  });

  it("exposes the teacher workbench only to teaching accounts", async () => {
    const { wrapper } = await panel("teacher");
    expect(wrapper.text()).toContain("教师");
    expect(wrapper.find('a[href="/teacher"]').exists()).toBe(true);
  });

  it.each([false, true])("clears local account state on sign-out, server failure=%s", async failure => {
    const post = vi.spyOn(api, "post");
    if (failure) post.mockRejectedValue(new Error("offline"));
    else post.mockResolvedValue({});
    const { wrapper, session, router } = await panel();
    api.setToken("test-session");
    await wrapper.find(".settings-logout").trigger("click");
    await flushPromises();
    expect(post).toHaveBeenCalledWith("/auth/logout", {});
    expect(session.user).toBeNull();
    expect(api.token()).toBeNull();
    expect(router.currentRoute.value.path).toBe("/");
  });
});

describe("music device preference", () => {
  it.each([[0.35, 0.35], [2, 1], [-1, 0], ["loud", 0.15], [null, 0.15]])("normalizes saved volume %s", (saved, expected) => {
    localStorage.setItem("pythontopia-reborn-interface", JSON.stringify({ musicVolume: saved }));
    expect(useUiPreferences().musicVolume).toBe(expected);
  });

  it("normalizes invalid live changes before persisting", async () => {
    const preferences = useUiPreferences();
    disconnect.push(preferences.connect());
    preferences.musicVolume = Number.POSITIVE_INFINITY;
    await nextTick();
    expect(preferences.musicVolume).toBe(0.15);
    expect(JSON.parse(localStorage.getItem("pythontopia-reborn-interface")!).musicVolume).toBe(0.15);
  });
});
