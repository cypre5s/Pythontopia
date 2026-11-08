import { afterEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import LessonWorkspace from "../src/features/LessonWorkspace.vue";
import { api } from "../src/shared/api";
import { useSession } from "../src/shared/session";
import type { ActivityProgress } from "../src/shared/types";

const initial: ActivityProgress = { page_no: null, visited_pages: [], completed: false, watched_seconds: 0, required_seconds: 5 };
function setup() {
  const pinia = createPinia(); setActivePinia(pinia);
  const session = useSession();
  session.user = { id: 1, email: "fixture@example.test", name: "Fixture", role: "student" }; session.classroomId = 2;
  session.world = { player: { map_key: "library", tile_x: 5, tile_y: 5, direction: "down" }, npcs: [], quests: [], chapters: [], flags: [] };
  vi.stubGlobal("matchMedia", () => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() }));
  vi.stubGlobal("URL", { createObjectURL: vi.fn(() => "blob:video"), revokeObjectURL: vi.fn() });
  vi.spyOn(document, "hidden", "get").mockReturnValue(false);
  vi.spyOn(session, "activity").mockResolvedValue({ id: "video", chapter_id: 179, title: "观看", kind: "lesson", npc_key: "librarian_doc", assistant_allowed: false, questions: [], progress: initial,
    learning: { video: { minimum_seconds: 5 }, highlights: { video: [{ start_seconds: 30, end_seconds: 40, label: "重点演示" }] } },
    material: { id: 193, title: "示范视频", kind: "video", status: "ready", pages: [], duration_seconds: 460 } });
  vi.spyOn(api, "get").mockResolvedValue(initial);
  vi.spyOn(api, "blob").mockResolvedValue(new Blob(["video"]));
  return { session, pinia };
}
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); localStorage.clear(); sessionStorage.clear(); });

describe("configured video learning", () => {
  it("sends material-bound playback evidence and keeps seeking separate from completion", async () => {
    const { pinia } = setup(), post = vi.spyOn(api, "post").mockResolvedValue(initial);
    const wrapper = mount(LessonWorkspace, { props: { activityId: "video" }, global: { plugins: [pinia], stubs: { AssistantPanel: true } } });
    await flushPromises();
    const video = wrapper.get("video").element as HTMLVideoElement;
    Object.defineProperty(video, "paused", { configurable: true, value: false });
    video.currentTime = 0;
    await wrapper.get("video").trigger("playing");
    await flushPromises();
    expect(post).toHaveBeenCalledWith("/activities/video/video-progress", expect.objectContaining({ classroom_id: 2, material_id: 193, sequence: 1, position_seconds: 0, playing: true }));
    video.currentTime = 5;
    video.playbackRate = 2;
    await wrapper.get("video").trigger("ratechange");
    await flushPromises();
    const rateSamples = post.mock.calls.slice(-2).map(call => call[1]);
    expect(rateSamples[0]).toMatchObject({ playback_rate: 1, playing: false });
    expect(rateSamples[1]).toMatchObject({ playback_rate: 2, playing: true, sequence: 1 });
    await wrapper.get(".support-goal-page").trigger("click");
    await flushPromises();
    expect(video.currentTime).toBe(30);
    expect(wrapper.get(".support-goal-progress progress").attributes("value")).toBe("0");
    expect(wrapper.findAll("button").find(button => button.text() === "完成观看")!.attributes("disabled")).toBeDefined();
    await wrapper.get("video").trigger("seeked");
    await flushPromises();
    const restarted = post.mock.calls.at(-1)![1] as { sequence: number; position_seconds: number };
    expect(restarted).toMatchObject({ sequence: 1, position_seconds: 30 });
    video.currentTime = 460.2;
    await wrapper.get("video").trigger("pause");
    await flushPromises();
    expect(post).toHaveBeenLastCalledWith("/activities/video/video-progress", expect.objectContaining({ position_seconds: 460, playing: false }));
    wrapper.unmount();
  });
  it("retains reset preparation until an unmounted reader's in-flight heartbeat settles", async () => {
    const { pinia, session } = setup();
    let prepare!: () => Promise<void>, finish!: (progress: ActivityProgress) => void;
    const unregister = vi.fn();
    vi.spyOn(session, "registerRestartPreparation").mockImplementation(callback => { prepare = callback; return unregister; });
    vi.spyOn(api, "post").mockReturnValue(new Promise(resolve => { finish = resolve as (progress: ActivityProgress) => void; }));
    const wrapper = mount(LessonWorkspace, { props: { activityId: "video" }, global: { plugins: [pinia], stubs: { AssistantPanel: true } } });
    await flushPromises();
    const video = wrapper.get("video").element as HTMLVideoElement;
    Object.defineProperty(video, "paused", { configurable: true, value: false });
    await wrapper.get("video").trigger("playing");
    wrapper.unmount();
    expect(unregister).not.toHaveBeenCalled();
    let ready = false;
    const prepared = prepare().then(() => { ready = true; });
    await Promise.resolve();
    expect(ready).toBe(false);
    finish(initial);
    await prepared;
    await flushPromises();
    expect(unregister).toHaveBeenCalledOnce();
  });
});
