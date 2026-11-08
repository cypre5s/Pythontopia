import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { nextTick } from "vue";
import LibraryWorkspace from "../src/features/LibraryWorkspace.vue";
import { useSession } from "../src/shared/session";
import { api } from "../src/shared/api";
import type { Material } from "../src/shared/types";

const material = (id: number, chapter: number, title: string, kind = "pdf"): Material => ({ id, chapter_id: chapter, title, kind, status: "ready", pages: [], supplementary: kind === "video", duration_seconds: kind === "video" ? 274 : null });
const items = [material(188, 181, "第三章信息表示"), material(193, 179, "计算思维小游戏", "video"), material(186, 179, "第一章计算思维"), material(187, 180, "Python 语言基础")];
const mounted: VueWrapper[] = [];
beforeEach(() => {
  setActivePinia(createPinia());
  const session = useSession();
  session.user = { id: 1, name: "居民", student_id: "135214705245359", role: "student" };
  session.classroomId = 2;
  session.world = {
    player: { map_key: "town_hub", tile_x: 22, tile_y: 36, direction: "down" }, npcs: [], quests: [], flags: [],
    chapters: [
      { id: 180, order_index: 2, title: "第二章：Python语言基础", available: true },
      { id: 179, order_index: 1, title: "第一章：计算工具与计算思维", available: true },
      { id: 181, order_index: 3, title: "第三章：计算机信息表示", available: true },
      { id: 182, order_index: 4, title: "第四章：未开放资料", available: false },
    ],
  };
});
afterEach(async () => {
  for (const wrapper of mounted.splice(0)) wrapper.unmount();
  await nextTick();
  vi.restoreAllMocks();
  sessionStorage.clear();
  document.body.replaceChildren();
});
async function library() {
  const wrapper = mount(LibraryWorkspace, { attachTo: document.body });
  mounted.push(wrapper);
  await flushPromises();
  return wrapper;
}
describe("chapter library", () => {
  it("groups only authorized materials by chapter and combines chapter, type and normalized search", async () => {
    const get = vi.spyOn(api, "get").mockResolvedValue(items);
    const post = vi.spyOn(api, "post");
    const wrapper = await library();
    expect(get).toHaveBeenCalledExactlyOnceWith("/library?classroom_id=2");
    expect(wrapper.findAll(".library-chapter-heading h3").map(item => item.text())).toEqual(["第一章：计算工具与计算思维", "第二章：Python语言基础", "第三章：计算机信息表示"]);
    expect(wrapper.get('[aria-label="资料章节"]').text()).not.toContain("未开放资料");
    expect(wrapper.find("h2").exists()).toBe(false);
    expect(wrapper.text()).not.toContain("留一页");
    await wrapper.get('[aria-label="资料类型"]').findAll("button")[2]!.trigger("click");
    expect(wrapper.findAll(".library-card")).toHaveLength(1);
    expect(wrapper.get(".library-card").text()).toContain("补充视频");
    expect(wrapper.get(".library-card").text()).toContain("4:34");
    await wrapper.get('input[type="search"]').setValue(" 计算 思维 ");
    expect(wrapper.findAll(".library-card")).toHaveLength(1);
    await wrapper.get('[aria-label="资料章节"]').findAll("button")[2]!.trigger("click");
    expect(wrapper.text()).toContain("没有找到匹配的资料");
    await wrapper.get(".library-empty button").trigger("click");
    expect(wrapper.findAll(".library-card")).toHaveLength(4);
    await wrapper.get('input[type="search"]').setValue(" PYTHON ");
    expect(wrapper.findAll(".library-card")).toHaveLength(1);
    expect(wrapper.get(".library-card").text()).toContain("Python 语言基础");
    expect(post).not.toHaveBeenCalled();
  });
  it("returns from reading with the same search, type, chapter and scroll position", async () => {
    vi.spyOn(api, "get").mockResolvedValue(items);
    const session = useSession();
    const navigate = vi.spyOn(session, "navigate");
    const wrapper = await library();
    await wrapper.get('input[type="search"]').setValue("计算");
    await wrapper.get('[aria-label="资料类型"]').findAll("button")[2]!.trigger("click");
    await wrapper.get('[aria-label="资料章节"]').findAll("button")[1]!.trigger("click");
    (wrapper.get(".library-content").element as HTMLElement).scrollTop = 231;
    await wrapper.get(".library-card").trigger("click");
    expect(navigate).toHaveBeenCalledWith({ kind: "material", material_id: 193 });
    wrapper.unmount();
    mounted.splice(mounted.indexOf(wrapper), 1);
    const returned = await library();
    expect(returned.get('input[type="search"]').element).toHaveProperty("value", "计算");
    expect(returned.get('[aria-label="资料类型"]').findAll("button")[2]!.attributes("aria-pressed")).toBe("true");
    expect(returned.get('[aria-label="资料章节"]').findAll("button")[1]!.attributes("aria-pressed")).toBe("true");
    expect((returned.get(".library-content").element as HTMLElement).scrollTop).toBe(231);
  });
  it("isolates saved views and ignores old classroom responses", async () => {
    let oldResolve!: (value: Material[]) => void;
    vi.spyOn(api, "get").mockImplementation(path => path.includes("classroom_id=2") ? new Promise<Material[]>(resolve => { oldResolve = resolve; }) as never : Promise.resolve([material(202, 180, "新班级资料")]) as never);
    sessionStorage.setItem("library-view:1:2", JSON.stringify({ query: "旧班级", type: "video", chapter: 179, scroll: 80 }));
    const wrapper = await library(), session = useSession();
    session.classroomId = 3;
    await flushPromises();
    expect(wrapper.get('input[type="search"]').element).toHaveProperty("value", "");
    expect(wrapper.get(".library-list").text()).toContain("新班级资料");
    oldResolve(items);
    await flushPromises();
    expect(wrapper.text()).not.toContain("计算思维小游戏");
    expect(wrapper.get(".library-card").text()).toContain("新班级资料");
    expect(JSON.parse(sessionStorage.getItem("library-view:1:2")!).query).toBe("旧班级");
  });
  it("recovers from malformed optional view storage and keeps unready materials disabled", async () => {
    sessionStorage.setItem("library-view:1:2", "null");
    vi.spyOn(api, "get").mockResolvedValue([{ ...material(199, 179, "等待处理"), status: "processing" }]);
    const wrapper = await library();
    expect(wrapper.get(".library-card").attributes("disabled")).toBeDefined();
    expect(wrapper.get(".library-card").text()).toContain("处理中");
  });
});
