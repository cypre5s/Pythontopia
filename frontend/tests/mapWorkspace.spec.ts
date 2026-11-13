import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { defineComponent, h, nextTick } from "vue";
import MapWorkspace from "../src/features/MapWorkspace.vue";
import OverlayHost from "../src/features/OverlayHost.vue";
import GameHUD from "../src/features/GameHUD.vue";
import { useSession } from "../src/shared/session";
import { worldConfig } from "../src/game/config/worldConfig";
import { paintMapPreview } from "../src/game/config/worldRender";
import { api } from "../src/shared/api";

vi.mock("../src/game/config/worldRender", () => ({ paintMapPreview: vi.fn(async () => undefined) }));
const mounted: VueWrapper[] = [];
beforeEach(() => {
  setActivePinia(createPinia());
  const session = useSession();
  session.user = { id: 1, student_id: "97590652190498", name: "地图居民", role: "student" };
  session.classroomId = 2;
  session.world = {
    player: { map_key: "town_hub", tile_x: 22, tile_y: 36, direction: "down" }, flags: [], chapters: [], quests: [],
    npcs: [
      { key: "mayor_py", name: "镇长 Py", map_key: "town_hub", tile_x: 22, tile_y: 34, portrait: "", direction: "down", sprite: { id: "builtin:sprite:chara_2", url: "/chara_2.png", frame_width: 16, frame_height: 20 } },
      { key: "librarian_doc", name: "图书管理员", map_key: "library", tile_x: 9, tile_y: 6, portrait: "", direction: "down", sprite: { id: "builtin:sprite:chara_2", url: "/chara_2.png", frame_width: 16, frame_height: 20 } },
    ],
  };
  session.open({ kind: "map" });
  vi.mocked(paintMapPreview).mockClear();
});
afterEach(async () => {
  for (const wrapper of mounted.splice(0)) wrapper.unmount();
  await nextTick();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  document.body.replaceChildren();
});
async function map() {
  const wrapper = mount(MapWorkspace, { attachTo: document.body });
  mounted.push(wrapper);
  await flushPromises();
  return wrapper;
}
describe("the map previews places and navigates only on request", () => {
  it("fits the full desktop map into both the available width and height", async () => {
    let stageWidth = 900;
    vi.spyOn(HTMLElement.prototype, "clientWidth", "get").mockImplementation(function (this: HTMLElement) { return this.classList.contains("map-stage") ? stageWidth : 0; });
    vi.spyOn(HTMLElement.prototype, "clientHeight", "get").mockImplementation(function (this: HTMLElement) { return this.classList.contains("map-stage") ? 300 : 0; });
    const wrapper = await map();
    const bounds = worldConfig.maps.town_hub!;
    expect(wrapper.get(".map-sheet").attributes("style")).toContain(`width: ${Math.min(900, 300 * bounds.width / bounds.height)}px`);
    const playerStyle = wrapper.get('[aria-label="玩家当前位置"]').attributes("style");
    stageWidth = 340;
    vi.stubGlobal("innerWidth", 390);
    window.dispatchEvent(new Event("resize"));
    await nextTick();
    expect(wrapper.get(".map-sheet").attributes("style")).toContain("width: 560px");
    expect(wrapper.get('[aria-label="玩家当前位置"]').attributes("style")).toBe(playerStyle);
    const stage = wrapper.get(".map-stage").element as HTMLElement;
    expect(stage.scrollLeft).toBeGreaterThan(0);
    expect(stage.scrollTop).toBeGreaterThan(0);
    const playerX = 22.5 / bounds.width * 560, playerY = 36.5 / bounds.height * 560 * bounds.height / bounds.width;
    expect(playerX - stage.scrollLeft).toBeGreaterThan(0);
    expect(playerX - stage.scrollLeft).toBeLessThan(stage.clientWidth);
    expect(playerY - stage.scrollTop).toBeGreaterThan(0);
    expect(playerY - stage.scrollTop).toBeLessThan(stage.clientHeight);
  });
  it("crops the town preview to its logical map and keeps marker placement aligned", async () => {
    const wrapper = await map();
    const bounds = { x: 0, y: 0, width: 50, height: 40 };
    const canvas = wrapper.get('canvas[aria-label="地图预览"]');
    expect(canvas.attributes("width")).toBe(String(bounds.width * 16));
    expect(canvas.attributes("height")).toBe(String(bounds.height * 16));
    expect(paintMapPreview).toHaveBeenCalledWith(canvas.element, "town_hub", expect.any(AbortSignal), bounds);
    expect(wrapper.findAll('.map-marker.building')).toHaveLength(11);
    const marker = wrapper.get('[aria-label="地图标记：镇长 Py"]');
    expect(marker.attributes("style")).toContain(`left: ${(22.5 - bounds.x) / bounds.width * 100}%`);
    await wrapper.get('[aria-label="筛选人物"]').trigger("click");
    expect(wrapper.findAll(".map-marker")).toHaveLength(1);
    expect(wrapper.get('[aria-label="地图标记：镇长 Py"]').attributes("style")).toBe(marker.attributes("style"));
  });
  it("opens only the player's current map without a picker or auxiliary map metadata", async () => {
    const session = useSession();
    session.world!.player = { map_key: "library", tile_x: 9, tile_y: 11, direction: "up" };
    const original = { ...session.world!.player };
    const post = vi.spyOn(api, "post");
    const wrapper = await map();
    expect(paintMapPreview).toHaveBeenLastCalledWith(expect.any(HTMLCanvasElement), "library", expect.any(AbortSignal), { x: 0, y: 0, width: 18, height: 14 });
    expect(wrapper.find("select").exists()).toBe(false);
    expect(wrapper.find(".map-legend").exists()).toBe(false);
    expect(wrapper.find(".map-current").exists()).toBe(false);
    expect(wrapper.find(".map-sidebar > .map-filters").exists()).toBe(true);
    const player = wrapper.get('[aria-label="玩家当前位置"]');
    expect(player.attributes("role")).toBe("img");
    expect(player.text()).toBe("你在这里");
    expect(player.attributes("style")).toContain(`left: ${9.5 / 18 * 100}%`);
    expect(player.attributes("style")).toContain(`top: ${11.5 / 14 * 100}%`);
    expect(wrapper.get('[aria-label="选择图书管理员"]').exists()).toBe(true);
    expect(wrapper.findAll('[aria-label="选择图书馆书架"]')).toHaveLength(1);
    expect(wrapper.text()).not.toContain("西侧");
    await wrapper.get('[aria-label="筛选建筑"]').trigger("click");
    expect(wrapper.get('[aria-label="选择返回小镇"]').exists()).toBe(true);
    expect(session.world!.player).toEqual(original);
    expect(session.workspace).toEqual({ kind: "map" });
    expect(session.navigationTarget).toBeNull();
    expect(post).not.toHaveBeenCalled();
  });
  it("keeps the player visible in every filter and reacts to the latest position without becoming a destination", async () => {
    const wrapper = await map(), session = useSession();
    for (const label of ["筛选人物", "筛选建筑", "筛选全部"]) {
      await wrapper.get(`[aria-label="${label}"]`).trigger("click");
      const marker = wrapper.get('[aria-label="玩家当前位置"]');
      expect(marker.isVisible()).toBe(true);
      expect(marker.classes()).not.toContain("map-marker");
      expect(marker.attributes("style")).toContain("left: 45%");
      expect(marker.attributes("style")).toContain("top: 91.25%");
      expect(wrapper.find('[aria-label="地图目标列表"] [aria-label="玩家当前位置"]').exists()).toBe(false);
    }
    session.setMapPosition({ map_key: "town_hub", tile_x: 39.25, tile_y: 2.25, direction: "right" });
    await nextTick();
    const marker = wrapper.get('[aria-label="玩家当前位置"]');
    expect(marker.attributes("style")).toContain("left: 79.5%");
    expect(Number.parseFloat((marker.element as HTMLElement).style.top)).toBeCloseTo(6.875);
    expect(marker.classes()).toContain("label-left");
    expect(marker.classes()).toContain("label-below");
    expect(session.world!.player.tile_x).toBe(22);
    expect(session.navigationTarget).toBeNull();
  });
  it("links map markers with list selection and reads locked-entry feedback from configuration", async () => {
    const session = useSession();
    session.world!.building_access = { library: { enabled: true, unlocked: false, can_enter: false, blocked_message: "完成当前班级的欢迎流程后开放" } };
    const wrapper = await map();
    await wrapper.get('[aria-label="地图标记：图书馆"]').trigger("click");
    expect(wrapper.get('[aria-label="选择图书馆"]').attributes("aria-pressed")).toBe("true");
    expect(wrapper.get('[aria-label="前往所选目标"]').attributes("disabled")).toBeDefined();
    expect(wrapper.get('[aria-label="所选地图目标"]').text()).toContain("完成当前班级的欢迎流程后开放");
    expect(session.navigationTarget).toBeNull();
    session.world!.building_access.library!.can_enter = true;
    await nextTick();
    expect(wrapper.get('[aria-label="前往所选目标"]').attributes("disabled")).toBeUndefined();
    const original = { ...session.world!.player };
    await wrapper.get('[aria-label="前往所选目标"]').trigger("click");
    expect(session.workspace).toBeNull();
    expect(session.navigationTarget).toBe("library");
    expect(session.world!.player).toEqual(original);
  });
  it("allows walking to a closed building's exterior and does not use movement keys to choose targets", async () => {
    const wrapper = await map(), session = useSession();
    await wrapper.get('[aria-label="选择南街小屋"]').trigger("click");
    expect(wrapper.get('[aria-label="所选地图目标"]').text()).toContain("居民住所");
    await wrapper.trigger("keydown", { key: "ArrowDown" });
    await wrapper.trigger("keydown", { key: "w" });
    expect(wrapper.get('[aria-label="选择南街小屋"]').attributes("aria-pressed")).toBe("true");
    expect(session.navigationTarget).toBeNull();
    await wrapper.get('[aria-label="前往所选目标"]').trigger("click");
    expect(session.navigationTarget).toBe("cottage_d");
  });
  it("adds the map between materials and classroom, and returns focus to the world after closing", async () => {
    const world = document.createElement("div");
    world.className = "world-canvas"; world.tabIndex = -1;
    document.body.append(world);
    const session = useSession(); session.open(null);
    const hud = mount(GameHUD, { attachTo: document.body }); mounted.push(hud);
    const buttons = hud.findAll(".hud-dock button");
    expect(buttons.slice(1, 4).map(button => button.text())).toEqual(["学习资料", "地图", "我的班级"]);
    buttons[2]!.element.focus();
    await buttons[2]!.trigger("click");
    const invoker = document.activeElement;
    const host = mount(defineComponent({ setup: () => () => h(OverlayHost, { onClose: () => session.open(null) }) }), { attachTo: document.body });
    mounted.push(host);
    await vi.waitFor(async () => { await flushPromises(); expect(host.find('[aria-label="地图工作区"]').exists()).toBe(true); });
    expect(host.get(".workspace-topbar").text()).toContain("小镇地图");
    expect(host.get(".game-workspace-title h2").text()).toBe("小镇地图");
    expect(host.get(".game-workspace-title svg").exists()).toBe(true);
    expect(host.get(".workspace-topbar").text()).not.toContain("已暂停");
    expect(host.get(".return-to-town").text()).toContain("回到小镇");
    expect(document.activeElement).toBe(invoker);
    window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true, cancelable: true }));
    await flushPromises();
    expect(session.workspace).toBeNull();
    expect(document.activeElement).toBe(world);
  });
  it("uses the shared back history when Escape leaves a nested map", async () => {
    const session = useSession();
    session.open({ kind: "journal" });
    session.navigate({ kind: "map" });
    const host = mount(OverlayHost, { attachTo: document.body });
    mounted.push(host);
    await flushPromises();
    expect(host.get(".workspace-back").text()).toContain("返回任务日志");
    window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true, cancelable: true }));
    await flushPromises();
    expect(session.workspace).toEqual({ kind: "journal" });
    expect(host.get(".game-workspace-title h2").text()).toBe("任务日志");
  });
});
