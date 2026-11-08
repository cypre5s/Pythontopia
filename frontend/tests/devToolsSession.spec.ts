import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { useSession } from "../src/shared/session";
import { api } from "../src/shared/api";
import type { World } from "../src/shared/types";

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(done => { resolve = done; });
  return { promise, resolve };
}
const world = (): World => ({ player: { map_key: "town_hub", tile_x: 24, tile_y: 28, direction: "left" }, flags: ["old"], chapters: [], quests: [], npcs: [] });
function context() {
  const session = useSession();
  session.user = { id: 7, name: "演示学生", email: "dev@example.test", role: "student", dev_tools_enabled: true };
  session.classes = [{ id: 4, name: "当前班级", teacher_id: 2 }, { id: 5, name: "其他班级", teacher_id: 2 }];
  session.classroomId = 4;
  session.world = world();
  session.open({ kind: "developer" });
  api.setToken("development-test-session");
  localStorage.setItem("draft:7:4:q1", "old draft");
  localStorage.setItem("draft:7:5:q1", "other class");
  sessionStorage.setItem("read:7:4:material-186:pdf", "42");
  return session;
}
beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); sessionStorage.clear(); });
afterEach(() => { vi.restoreAllMocks(); localStorage.clear(); sessionStorage.clear(); });

describe("development replay orchestration", () => {
  it("waits for both world and video preparations without replacing either owner", async () => {
    const session = context(), worldSaved = deferred<void>(), videoSaved = deferred<void>();
    const worldPrepare = vi.fn(() => worldSaved.promise), videoPrepare = vi.fn(() => videoSaved.promise), removed = vi.fn(async () => undefined);
    session.registerRestartPreparation(worldPrepare);
    session.registerRestartPreparation(videoPrepare);
    session.registerRestartPreparation(removed)();
    const post = vi.spyOn(api, "post").mockResolvedValue({ ok: true, user_id: 7, classroom_id: 4 });
    const restarting = session.restartCurrentClassroom();
    expect(worldPrepare).toHaveBeenCalledOnce();
    expect(videoPrepare).toHaveBeenCalledOnce();
    expect(removed).not.toHaveBeenCalled();
    worldSaved.resolve();
    await Promise.resolve();
    expect(post).not.toHaveBeenCalled();
    videoSaved.resolve();
    expect(await restarting).toBe(true);
    expect(post).toHaveBeenCalledOnce();
  });
  it("drains world writes and reading before resetting, clears only verified target caches and preserves login", async () => {
    const session = context(), saved = deferred<void>(), reading = deferred<never>();
    const prepare = vi.fn(() => saved.promise);
    session.registerRestartPreparation(prepare);
    const post = vi.spyOn(api, "post").mockImplementation(path => path.includes("/progress") && path !== "/dev/progress/reset"
      ? reading.promise : Promise.resolve({ ok: true, user_id: 7, classroom_id: 4 }) as Promise<never>);
    const save = session.saveActivityProgress("ch1-lesson", 42);
    const restart = session.restartCurrentClassroom();
    expect(session.resetBusy).toBe(true);
    expect(await session.restartCurrentClassroom()).toBe(false);
    await vi.waitFor(() => expect(post).toHaveBeenCalledWith("/activities/ch1-lesson/progress", expect.anything()));
    expect(post.mock.calls.some(([path]) => path === "/dev/progress/reset")).toBe(false);
    saved.resolve();
    reading.resolve({ completed: false, page_no: 42, visited_pages: [42] } as never);
    await save;
    expect(await restart).toBe(true);
    expect(prepare).toHaveBeenCalledOnce();
    expect(post).toHaveBeenLastCalledWith("/dev/progress/reset", { classroom_id: 4, confirmed: true });
    expect(session.world).toBeNull();
    expect(localStorage.getItem("draft:7:4:q1")).toBeNull();
    expect(localStorage.getItem("draft:7:5:q1")).toBe("other class");
    expect(sessionStorage.getItem("read:7:4:material-186:pdf")).toBeNull();
    expect(api.token()).toBe("development-test-session");
    expect(session.user?.id).toBe(7);
    expect(session.classes).toHaveLength(2);
    expect(session.resetBusy).toBe(false);
    expect(session.restartCompleted).toBe(1);
  });
  it("keeps progress and browser caches when the server rejects reset", async () => {
    const session = context(), previous = session.world;
    vi.spyOn(api, "post").mockRejectedValue(new Error("仍有未完成提交"));
    await expect(session.restartCurrentClassroom()).rejects.toThrow("未完成提交");
    expect(session.world).toEqual(previous);
    expect(session.workspace).toEqual({ kind: "developer" });
    expect(localStorage.getItem("draft:7:4:q1")).toBe("old draft");
    expect(sessionStorage.getItem("read:7:4:material-186:pdf")).toBe("42");
    expect(session.resetBusy).toBe(false);
    expect(session.restartCompleted).toBe(0);
  });
  it("ignores a late successful response after account switching", async () => {
    const session = context(), response = deferred<never>();
    const post = vi.spyOn(api, "post").mockReturnValue(response.promise);
    const restarting = session.restartCurrentClassroom();
    await vi.waitFor(() => expect(post).toHaveBeenCalled());
    session.reset();
    session.user = { id: 8, name: "其他学生", email: "other@example.test", role: "student" };
    session.classroomId = 5;
    session.world = world();
    response.resolve({ ok: true, user_id: 7, classroom_id: 4 } as never);
    expect(await restarting).toBe(false);
    expect(session.user.id).toBe(8);
    expect(session.world?.flags).toEqual(["old"]);
    expect(localStorage.getItem("draft:7:4:q1")).toBe("old draft");
    expect(session.resetBusy).toBe(false);
    expect(session.restartCompleted).toBe(0);
  });
  it("rejects disabled tools and class changes while resetting", async () => {
    const session = context(), prepared = deferred<void>();
    session.user!.dev_tools_enabled = false;
    const post = vi.spyOn(api, "post");
    await expect(session.restartCurrentClassroom()).rejects.toThrow("开发工具不可用");
    expect(post).not.toHaveBeenCalled();
    session.user!.dev_tools_enabled = true;
    session.registerRestartPreparation(() => prepared.promise);
    post.mockResolvedValue({ ok: true, user_id: 7, classroom_id: 4 });
    const restarting = session.restartCurrentClassroom();
    await expect(session.switchClassroom(5)).rejects.toThrow("正在重新开始");
    prepared.resolve();
    expect(await restarting).toBe(true);
  });
});
