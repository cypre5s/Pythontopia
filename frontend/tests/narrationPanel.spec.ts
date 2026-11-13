import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import NarrationPanel from "../src/features/NarrationPanel.vue";
import { api } from "../src/shared/api";

vi.mock("../src/shared/api", () => ({ api: { get: vi.fn(), blob: vi.fn() } }));
vi.mock("../src/shared/session", () => ({
  useSession: () => ({ classroomId: 7, user: { id: 1 } }),
}));

const pages = [
  { page_no: 1, text: "第一页讲监督学习。", bridge_in: "", target_seconds: 90, audio: true, audio_seconds: 80 },
  { page_no: 2, text: "第二页讲无监督学习。", bridge_in: "接着上一页看没有标签的情况。", target_seconds: 60, audio: false },
];

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(api.get).mockResolvedValue({ material_id: 3, pages });
  vi.mocked(api.blob).mockResolvedValue(new Blob(["audio"], { type: "audio/mpeg" }));
});

afterEach(() => vi.clearAllMocks());

describe("NarrationPanel", () => {
  it("stays collapsed until the student asks for the narration", async () => {
    const wrapper = mount(NarrationPanel, { props: { materialId: 3, pageNo: 1 } });
    await flushPromises();
    expect(api.get).toHaveBeenCalledWith("/materials/3/narrations?classroom_id=7");
    const toggle = wrapper.get("button");
    expect(toggle.attributes("aria-expanded")).toBe("false");
    expect(wrapper.text()).toContain("本页讲解");
    expect(wrapper.find("#narration-body").isVisible()).toBe(false);

    await toggle.trigger("click");
    expect(toggle.attributes("aria-expanded")).toBe("true");
    expect(wrapper.get(".narration-text").text()).toBe("第一页讲监督学习。");
    expect(wrapper.text()).toContain("AI 生成 · 教师已审校");
    expect(wrapper.text()).toContain("不计入阅读完成");
  });

  it("follows the page the reader is on, with its own duration and bridge", async () => {
    const wrapper = mount(NarrationPanel, { props: { materialId: 3, pageNo: 1 } });
    await flushPromises();
    await wrapper.setProps({ pageNo: 2 });
    expect(wrapper.get(".narration-text").text()).toBe("第二页讲无监督学习。");
    expect(wrapper.get(".narration-bridge").text()).toContain("接着上一页");
    expect(wrapper.text()).toContain("约 1 分钟");
  });

  it("plays the approved recording when the page has one", async () => {
    vi.stubGlobal("URL", { ...URL, createObjectURL: vi.fn(() => "blob:audio"), revokeObjectURL: vi.fn() });
    const wrapper = mount(NarrationPanel, { props: { materialId: 3, pageNo: 1 } });
    await flushPromises();
    await wrapper.get("button").trigger("click");
    const audioButton = wrapper.get(".narration-audio button");
    expect(audioButton.text()).toContain("听本页讲解");
    await audioButton.trigger("click");
    await flushPromises();
    expect(api.blob).toHaveBeenCalledWith("/materials/3/narrations/1/audio?classroom_id=7");
    expect(wrapper.get("audio").attributes("src")).toBe("blob:audio");

    // Moving to a page without audio drops the previous clip.
    await wrapper.setProps({ pageNo: 2 });
    expect(wrapper.find(".narration-audio").exists()).toBe(false);
  });

  it("renders nothing when the page has no approved narration, and survives fetch errors", async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ material_id: 3, pages: [] });
    const empty = mount(NarrationPanel, { props: { materialId: 3, pageNo: 4 } });
    await flushPromises();
    expect(empty.find(".narration-panel").exists()).toBe(false);

    vi.mocked(api.get).mockRejectedValueOnce(new Error("网络错误"));
    const broken = mount(NarrationPanel, { props: { materialId: 9, pageNo: 1 } });
    await flushPromises();
    expect(broken.find(".narration-panel").exists()).toBe(false);
  });
});
