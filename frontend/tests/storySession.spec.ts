import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { flushPromises, mount } from "@vue/test-utils";
import { api, ApiError } from "../src/shared/api";
import { useSession } from "../src/shared/session";
import { useUiPreferences } from "../src/shared/uiPreferences";
import DialogueLayer from "../src/features/DialogueLayer.vue";
import GameHUD from "../src/features/GameHUD.vue";
import type { StoryView, World } from "../src/shared/types";

const world = (): World => ({
  player: { map_key: "town_hub", tile_x: 22, tile_y: 36, direction: "down" },
  npcs: [{ key: "librarian_doc", name: "图书管理员", map_key: "library", tile_x: 7, tile_y: 5, portrait: "", dialogue: [{ id: "greeting", text: "你好，欢迎阅读" }] }],
  quests: [{ id: "arrival", chapter_id: 179, kind: "main", title: "第一章", story_id: "first-steps", status: "active", nodes: [
    { id: "study", type: "STUDY_LESSON", title: "阅读第一章", target: "librarian_doc", navigation_target: "librarian_doc", activity_id: "ch1-lesson", dependencies: [], aggregation: "ALL", optional: false, status: "ready" },
  ] }], chapters: [], flags: [],
});
const view = (status: StoryView["status"] = "active", revision = 0): StoryView => ({
  id: "story-session", revision, status, npc_key: "librarian_doc",
  node: status === "active" ? { id: "welcome", text: "欢迎，第一次来图书馆吗？", choices: [{ id: "read", label: "开始阅读" }] } : null,
  activity: status === "waiting" ? { id: "ch1-lesson", kind: "lesson", page_no: 42 } : null,
});
function deferred<T>() {
  let resolve!: (result: T) => void;
  const promise = new Promise<T>(done => { resolve = done; });
  return { resolve, promise };
}
beforeEach(() => {
  setActivePinia(createPinia());
  const session = useSession();
  session.user = { id: 1, name: "学生", student_id: "72953879102235", role: "student" };
  session.classroomId = 2;
  session.world = world();
  useUiPreferences().reduceMotion = true;
});
afterEach(() => { vi.restoreAllMocks(); sessionStorage.clear(); document.body.replaceChildren(); });

describe("server-owned chapter performance", () => {
  it("routes a map interaction without inventing an NPC and opens a workspace only after acknowledgment", async () => {
    const session = useSession();
    const action: StoryView = { ...view(), npc_key: null, interaction_id: "shelf", node: {
      id: "open", type: "action", action: "open_workspace", workspace: "library", text: "查看开放资料", speaker: "书架",
    } };
    const post = vi.spyOn(api, "post").mockResolvedValueOnce(action)
      .mockRejectedValueOnce(new ApiError(409, "课程已更新"))
      .mockResolvedValueOnce({ ...action, status: "ended", node: null, revision: 1 });
    await session.interactStory("shelf", "object");
    expect(post).toHaveBeenCalledWith("/story/interactions", { classroom_id: 2, interaction_id: "shelf" });
    const wrapper = mount(DialogueLayer, { props: { actions: [], story: action }, attachTo: document.body });
    expect(wrapper.text()).toContain("查看开放资料");
    await expect(session.advanceStory()).rejects.toThrow("课程已更新");
    expect(session.workspace).toBeNull();
    await session.advanceStory();
    expect(session.workspace).toEqual({ kind: "library" });
    expect(post.mock.calls[1]?.[1]).toEqual(post.mock.calls[2]?.[1]);
    wrapper.unmount();
  });
  it("does not open an old account's action and explicitly ends only the current flow", async () => {
    const session = useSession();
    session.story = { ...view(), npc_key: null, interaction_id: "sign", node: {
      id: "open", type: "action", action: "open_workspace", workspace: "journal", text: "任务日志",
    } };
    const pending = deferred<StoryView>();
    const post = vi.spyOn(api, "post").mockReturnValueOnce(pending.promise).mockResolvedValueOnce(view("ended", 2));
    const changing = session.advanceStory();
    session.classroomId = 3;
    pending.resolve(view("ended", 1));
    expect(await changing).toBe(false);
    expect(session.workspace).toBeNull();
    expect(session.story).toBeNull();
    session.story = { ...view(), id: "current-class-story" };
    await session.endStory();
    expect(post).toHaveBeenLastCalledWith("/story/sessions/current-class-story/end", expect.objectContaining({ classroom_id: 3 }));
  });
  it("keeps the authorized world usable when the previous chapter cannot be restored", async () => {
    const session = useSession();
    session.story = view("waiting", 4);
    session.open({ kind: "lesson", activity_id: "ch1-lesson", story_session_id: "story-session" });
    vi.spyOn(api, "get").mockRejectedValue(new ApiError(403, "该章节已暂停开放"));
    const post = vi.spyOn(api, "post");
    await expect(session.restoreStory()).resolves.toBeUndefined();
    expect(session.world).toEqual(world());
    expect(session.story).toBeNull();
    expect(session.workspace).toBeNull();
    expect(post).not.toHaveBeenCalled();
  });
  it.each([403, 404])("lets students leave a waiting workspace when access is removed (%s)", async status => {
    const session = useSession();
    session.story = view("waiting", 4);
    session.open({ kind: "lesson", activity_id: "ch1-lesson", story_session_id: "story-session" });
    const post = vi.spyOn(api, "post").mockRejectedValue(new ApiError(status, "该活动暂不可用"));
    vi.spyOn(api, "get").mockResolvedValue(world());
    await expect(session.returnStoryActivity()).resolves.toBe(false);
    expect(session.workspace).toBeNull();
    expect(session.story).toBeNull();
    expect(session.storyDismissed).toBe(true);
    expect(post).toHaveBeenCalledTimes(1);
    expect(post.mock.calls[0]?.[0]).toBe("/story/sessions/story-session/return");
  });
  it.each(["advance", "end"] as const)("releases a reset dialogue and starts the updated flow (%s)", async action => {
    const session = useSession();
    session.story = view("active", 4);
    const refreshed = { ...world(), flags: ["another-achievement"] };
    const get = vi.spyOn(api, "get").mockResolvedValue(refreshed);
    const restarted = { ...view(), id: "updated-session" };
    vi.spyOn(api, "post").mockRejectedValueOnce(new ApiError(404, "演出会话不存在")).mockResolvedValueOnce(restarted);
    expect(await (action === "advance" ? session.advanceStory("read") : session.endStory())).toBe(false);
    expect(session.story).toBeNull();
    expect(session.storyDismissed).toBe(true);
    expect(get).toHaveBeenCalledWith("/world?classroom_id=2");
    expect(session.world).toEqual(refreshed);
    expect(await session.interactStory("librarian_doc")).toBe(true);
    expect(session.story?.id).toBe("updated-session");
  });
  it("keeps the waiting workspace and retry ID after a network failure", async () => {
    const session = useSession();
    session.story = view("waiting", 4);
    const workspace = { kind: "lesson" as const, activity_id: "ch1-lesson", story_session_id: "story-session" };
    session.open(workspace);
    const post = vi.spyOn(api, "post").mockRejectedValueOnce(new Error("网络连接中断")).mockResolvedValueOnce(view("active", 5));
    await expect(session.returnStoryActivity()).rejects.toThrow("网络连接中断");
    expect(session.workspace).toEqual(workspace);
    expect(session.story?.status).toBe("waiting");
    await session.returnStoryActivity();
    expect(post.mock.calls[0]?.[1]).toEqual(post.mock.calls[1]?.[1]);
    expect(session.workspace).toBeNull();
  });
  it("uses the server's first-visit and repeat-visit entries instead of the NPC's first line", async () => {
    const session = useSession();
    const post = vi.spyOn(api, "post").mockResolvedValueOnce(view()).mockResolvedValueOnce({ ...view("active", 3), node: { id: "return", text: "你回来啦，要继续看吗？" } });
    await session.interactStory("librarian_doc");
    expect(session.story?.node?.id).toBe("welcome");
    session.dismissStory();
    await session.interactStory("librarian_doc");
    expect(session.story?.node?.text).toContain("你回来啦");
    expect(session.storyDismissed).toBe(false);
    expect(post).toHaveBeenCalledTimes(2);
  });
  it("restores a waiting activity and server page without issuing an exit or completing it", async () => {
    const session = useSession();
    const get = vi.spyOn(api, "get").mockResolvedValue(view("waiting", 4));
    const post = vi.spyOn(api, "post");
    await session.restoreStory();
    expect(get).toHaveBeenCalledWith("/story/current?classroom_id=2");
    expect(session.workspace).toEqual({ kind: "lesson", activity_id: "ch1-lesson", page_no: 42, story_session_id: "story-session" });
    expect(post).not.toHaveBeenCalled();
  });
  it("shows the pause response after explicitly leaving, and resumes through a server choice", async () => {
    const session = useSession();
    session.story = view("waiting", 4);
    session.open({ kind: "lesson", activity_id: "ch1-lesson", story_session_id: "story-session" });
    session.navigate({ kind: "material", material_id: 186, page_no: 43 });
    const paused = { ...view("active", 5), node: { id: "pause", text: "随时可以回来再看看。" } };
    const post = vi.spyOn(api, "post").mockResolvedValueOnce(paused).mockResolvedValueOnce(view("waiting", 6));
    await session.returnStoryActivity();
    expect(session.workspace).toBeNull();
    expect(session.story?.node?.id).toBe("pause");
    await session.advanceStory("continue");
    expect(post).toHaveBeenLastCalledWith("/story/sessions/story-session/advance", expect.objectContaining({ choice: "continue", revision: 5, classroom_id: 2 }));
    expect(session.workspace?.kind).toBe("lesson");
  });
  it("reuses the mutation ID after a lost response so retry cannot apply the choice twice", async () => {
    const session = useSession();
    session.story = view();
    const post = vi.spyOn(api, "post").mockRejectedValueOnce(new Error("connection lost")).mockResolvedValueOnce(view("waiting", 1));
    await expect(session.advanceStory("read")).rejects.toThrow("connection lost");
    await session.advanceStory("read");
    expect(post.mock.calls[0]?.[1]).toEqual(post.mock.calls[1]?.[1]);
    expect(session.workspace?.kind).toBe("lesson");
  });
  it("does not reopen another account's activity when an old interaction arrives", async () => {
    const pending = deferred<StoryView>();
    vi.spyOn(api, "post").mockReturnValue(pending.promise);
    const session = useSession();
    const interaction = session.interactStory("librarian_doc");
    session.user = { id: 2, name: "另一位", student_id: "193802344597316", role: "student" };
    session.open({ kind: "settings" });
    pending.resolve(view("waiting", 1));
    expect(await interaction).toBe(true);
    expect(session.story).toBeNull();
    expect(session.workspace).toEqual({ kind: "settings" });
  });
  it("finishes queued reading saves before asking the server to evaluate completion", async () => {
    const pending = deferred<{ page_no: number; visited_pages: number[]; completed: boolean }>();
    const session = useSession();
    session.story = view("waiting", 2);
    const post = vi.spyOn(api, "post").mockReturnValueOnce(pending.promise).mockResolvedValueOnce(view("active", 3));
    const saved = session.saveActivityProgress("ch1-lesson", 43, 2);
    const returned = session.returnStoryActivity();
    await flushPromises();
    expect(post).toHaveBeenCalledTimes(1);
    pending.resolve({ page_no: 43, visited_pages: [10715, 10723, 10724], completed: true });
    await saved; await returned;
    expect(post.mock.calls[1]?.[0]).toBe("/story/sessions/story-session/return");
  });
  it("falls back only for an NPC without a story, and preserves real errors", async () => {
    const post = vi.spyOn(api, "post").mockRejectedValueOnce(new ApiError(404, "no entry")).mockRejectedValueOnce(new ApiError(409, "revision conflict"));
    const session = useSession();
    expect(await session.interactStory("visitor")).toBe(false);
    await expect(session.interactStory("librarian_doc")).rejects.toThrow("revision conflict");
    session.world!.quests[0]!.story_id = undefined;
    expect(await session.interactStory("librarian_doc")).toBe(false);
    expect(post).toHaveBeenCalledTimes(2);
  });
  it("advances displayed story choices through the story endpoint and navigates before opening lessons", async () => {
    const session = useSession();
    session.story = view();
    const post = vi.spyOn(api, "post").mockResolvedValue(view("waiting", 1));
    const wrapper = mount(DialogueLayer, { props: { npc: world().npcs[0]!, actions: [], story: session.story }, attachTo: document.body });
    expect(wrapper.get(".dialogue-text").text()).toContain("第一次来图书馆");
    await wrapper.get(".dialogue-response").trigger("click");
    await flushPromises();
    expect(post).toHaveBeenCalledWith("/story/sessions/story-session/advance", expect.objectContaining({ choice: "read" }));
    wrapper.unmount();
    session.open(null);
    const hud = mount(GameHUD);
    expect(hud.get(".quest-go").text()).toBe("立即前往");
    await hud.get(".quest-go").trigger("click");
    expect(hud.emitted("guide")).toEqual([["librarian_doc"]]);
    expect(session.workspace).toBeNull();
    hud.unmount();
  });
});
