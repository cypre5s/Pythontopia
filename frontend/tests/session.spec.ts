import { afterEach, beforeEach, describe, it, expect, vi } from "vitest";
import { setActivePinia, createPinia } from "pinia";
import { useSession } from "../src/shared/session";
import { api } from "../src/shared/api";
describe("one workspace and account isolation", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    sessionStorage.clear();
  });
  it("replaces the principal workspace instead of stacking it", () => {
    const store = useSession();
    store.open({ kind: "lesson", activity_id: "ch1-lesson" });
    store.open({ kind: "practice", activity_id: "ch1-practice" });
    expect(store.workspace).toEqual({
      kind: "practice",
      activity_id: "ch1-practice",
    });
    store.open(null);
    expect(store.workspace).toBeNull();
  });
  it("clears memory and credentials on logout", () => {
    const store = useSession();
    store.user = {
      id: 1,
      name: "student",
      student_id: "72953879102235",
      role: "student",
    };
    store.drafts = { q1: "print(1)" };
    api.setToken("a-session");
    store.open({ kind: "settings" });
    store.reset();
    expect(store.user).toBeNull();
    expect(store.drafts).toEqual({});
    expect(api.token()).toBeNull();
    expect(store.workspace).toBeNull();
  });
  it("does not make assistant requests for ordinary workspace navigation", () => {
    const fetch = vi.spyOn(globalThis, "fetch");
    const store = useSession();
    store.open({ kind: "journal" });
    store.open({ kind: "settings" });
    store.open(null);
    expect(fetch).not.toHaveBeenCalled();
    fetch.mockRestore();
  });
  it("opens and restores an independent daily story without creating a task workspace", async () => {
    const store = useSession();
    store.user = { id: 1, name: "student", student_id: "72953879102235", role: "student" };
    store.classroomId = 2;
    store.world = { story_available: true, player: { map_key: "town_hub", tile_x: 22, tile_y: 34, direction: "down" }, npcs: [], quests: [], chapters: [], flags: [] };
    const daily = { id: "daily", revision: 0, status: "active", mode: "ambient", npc_key: "mayor_py", node: { id: "hello", text: "今天想聊些什么？" }, activity: null };
    const get = vi.spyOn(api, "get").mockResolvedValue(daily);
    const post = vi.spyOn(api, "post").mockResolvedValue(daily);
    try {
      await store.restoreStory();
      expect(get).toHaveBeenCalledWith("/story/current?classroom_id=2");
      expect(await store.interactStory("mayor_py")).toBe(true);
      expect(post).toHaveBeenCalledWith("/story/interactions", { classroom_id: 2, npc_key: "mayor_py" });
      expect(store.story?.id).toBe("daily");
      expect(store.workspace).toBeNull();
      expect(store.world.quests).toEqual([]);
    } finally {
      get.mockRestore();
      post.mockRestore();
    }
  });
});

it("ignores a late world response from a previously selected classroom", async () => {
  setActivePinia(createPinia());
  const store = useSession();
  store.user = {
    id: 1,
    name: "one",
    student_id: "26485103988533",
    role: "student",
  };
  store.classroomId = 1;
  let resolve!: (value: unknown) => void;
  const response = new Promise((r) => {
    resolve = r;
  });
  const get = vi.spyOn(api, "get").mockReturnValue(response as Promise<never>);
  const refresh = store.refresh();
  store.classroomId = 2;
  resolve({
    player: { tile_x: 1, tile_y: 1, map_key: "town_hub", direction: "down" },
    npcs: [],
    quests: [],
    chapters: [],
    flags: ["old-class"],
  });
  await refresh;
  expect(store.world).toBeNull();
  get.mockRestore();
});

describe("classroom switching", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    sessionStorage.clear();
  });
  afterEach(() => vi.restoreAllMocks());
  function context() {
    const store = useSession();
    store.user = { id: 1, name: "同学", student_id: "72953879102235", role: "student" };
    store.classes = [1, 2, 3].map(id => ({ id, name: `班级${id}`, teacher_id: 4 }));
    store.classroomId = 1;
    store.world = { player: { tile_x: 1, tile_y: 1, map_key: "town_hub", direction: "down" }, npcs: [], quests: [], chapters: [], flags: ["first"] };
    store.open({ kind: "classroom" });
    return store;
  }
  it("keeps the current class and page if the new class cannot load", async () => {
    const store = context();
    vi.spyOn(api, "get").mockRejectedValueOnce(new Error("网络中断"));
    await expect(store.switchClassroom(2)).rejects.toThrow("网络中断");
    expect(store.classroomId).toBe(1);
    expect(store.world?.flags).toEqual(["first"]);
    expect(store.workspace).toEqual({ kind: "classroom" });
  });
  it("atomically changes worlds and ignores the previous request", async () => {
    const store = context();
    let first!: (value: unknown) => void;
    const get = vi.spyOn(api, "get").mockImplementation(path => path.endsWith("=2")
      ? new Promise(resolve => { first = resolve; }) as Promise<never>
      : Promise.resolve({ ...store.world, flags: ["third"] }) as Promise<never>);
    const pending = store.switchClassroom(2);
    await vi.waitFor(() => expect(get).toHaveBeenCalledWith("/world?classroom_id=2"));
    await store.switchClassroom(3);
    first({ ...store.world, flags: ["second"] });
    expect(await pending).toBe(false);
    expect(store.classroomId).toBe(3);
    expect(store.world?.flags).toEqual(["third"]);
    expect(store.workspace).toEqual({ kind: "classroom" });
  });
  it("does not switch after logout or accept a class outside membership", async () => {
    const store = context();
    await expect(store.switchClassroom(7)).rejects.toThrow("请先加入");
    let resolve!: (value: unknown) => void;
    const get = vi.spyOn(api, "get").mockReturnValue(new Promise(r => { resolve = r; }) as Promise<never>);
    const pending = store.switchClassroom(2);
    await vi.waitFor(() => expect(get).toHaveBeenCalledWith("/world?classroom_id=2"));
    store.reset();
    resolve({ flags: ["stale"] });
    expect(await pending).toBe(false);
    expect(store.world).toBeNull();
    expect(store.classroomId).toBeNull();
  });
  it("ignores a switch superseded by a full session reload", async () => {
    const store = context();
    let resolve!: (value: unknown) => void;
    const get = vi.spyOn(api, "get").mockImplementation(path => {
      if (path === "/auth/me") return Promise.resolve(store.user) as Promise<never>;
      if (path === "/classrooms") return Promise.resolve(store.classes) as Promise<never>;
      if (path.endsWith("=2")) return new Promise(done => { resolve = done; }) as Promise<never>;
      return Promise.resolve({ ...store.world, flags: ["reloaded"] }) as Promise<never>;
    });
    const pending = store.switchClassroom(2);
    await vi.waitFor(() => expect(get).toHaveBeenCalledWith("/world?classroom_id=2"));
    await store.load();
    resolve({ ...store.world, flags: ["stale-switch"] });
    expect(await pending).toBe(false);
    expect(store.classroomId).toBe(1);
    expect(store.world?.flags).toEqual(["reloaded"]);
  });
});
