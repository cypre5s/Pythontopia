import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { createPinia, setActivePinia, type Pinia } from "pinia";
import { nextTick } from "vue";
import { api, ApiError } from "../src/shared/api";
import { useSession } from "../src/shared/session";
import { useUiPreferences } from "../src/shared/uiPreferences";
import WorldCanvas from "../src/features/WorldCanvas.vue";
import DialogueLayer from "../src/features/DialogueLayer.vue";
import {
  createWorld,
  type WorldCallbacks,
  type WorldHandle,
} from "../src/game/adapters/world";
import type { Classroom, Npc, User, World } from "../src/shared/types";

vi.mock("../src/game/adapters/world", () => ({ createWorld: vi.fn() }));

let pinia: Pinia;
const mounted: VueWrapper[] = [];

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

const person = (id: number): User => ({
  id,
  name: `Student ${id}`,
  student_id: `student-${id}@example.test`,
  role: "student",
});
const classroom = (id: number): Classroom => ({
  id,
  name: `Class ${id}`,
  teacher_id: 9,
});
const world = (flag = "current"): World => ({
  player: { map_key: "town_hub", tile_x: 22, tile_y: 36, direction: "down" },
  npcs: [],
  quests: [],
  chapters: [],
  flags: [flag],
});
const resident = (key = "mayor_py"): Npc => ({
  key,
  name: key,
  map_key: "town_hub",
  tile_x: 22,
  tile_y: 34,
  portrait: "",
  dialogue: [{ id: "intro", text: "This is a scripted conversation." }],
});

beforeEach(() => {
  vi.mocked(createWorld).mockClear();
  pinia = createPinia();
  setActivePinia(pinia);
  const session = useSession();
  session.user = person(1);
  session.classroomId = 1;
  session.world = world();
  useUiPreferences().reduceMotion = true;
  vi.stubGlobal(
    "fetch",
    vi.fn(() =>
      Promise.reject(new Error("Unexpected backend call in unit test")),
    ),
  );
  vi.stubGlobal(
    "matchMedia",
    vi.fn(() => ({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
});

afterEach(async () => {
  for (const wrapper of mounted.splice(0)) wrapper.unmount();
  await nextTick();
  document.body.replaceChildren();
  sessionStorage.clear();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

async function canvas() {
  let callbacks!: WorldCallbacks;
  const getMapPosition = vi.fn(() => world().player);
  const handle: WorldHandle = {
    destroy: vi.fn(),
    setPaused: vi.fn(),
    setNpcs: vi.fn(),
    setFlags: vi.fn(),
    setRuntime: vi.fn(),
    setPlayer: vi.fn(),
    direction: vi.fn(),
    interact: vi.fn(),
    guide: vi.fn(),
    mute: vi.fn(),
    setVolume: vi.fn(),
    getMapPosition,
  };
  vi.mocked(createWorld).mockImplementation(
    (_root, _player, _npcs, _flags, received) => {
      callbacks = received;
      return handle;
    },
  );
  const wrapper = mount(WorldCanvas, {
    props: { paused: false },
    global: { plugins: [pinia] },
  });
  mounted.push(wrapper);
  await flushPromises();
  return { callbacks, wrapper, getMapPosition, handle };
}

describe("pending world interactions", () => {
  it("persists the exact region entry tile before requesting its authorized flow", async () => {
    const saved = deferred<void>();
    const request = vi.spyOn(api, "request").mockReturnValue(saved.promise);
    const session = useSession();
    const interact = vi.spyOn(session, "interactStory").mockResolvedValue(true);
    const { callbacks } = await canvas();
    const position = { ...world().player, tile_x: 23 };
    const pending = callbacks.enterRegion!("entry-strip", position);
    await flushPromises();
    expect(request).toHaveBeenCalledWith("/world/position", expect.objectContaining({ body: JSON.stringify({ classroom_id: 1, ...position }) }));
    expect(interact).not.toHaveBeenCalled();
    saved.resolve();
    await pending;
    expect(interact).toHaveBeenCalledWith("entry-strip", "object");
  });
  it("does not trigger a region when its position save failed or its account changed", async () => {
    const session = useSession();
    const interact = vi.spyOn(session, "interactStory").mockResolvedValue(true);
    const request = vi.spyOn(api, "request").mockRejectedValueOnce(new Error("offline"));
    const { callbacks } = await canvas();
    await callbacks.enterRegion!("entry-strip", { ...world().player, tile_x: 23 });
    expect(interact).not.toHaveBeenCalled();
    const saved = deferred<void>();
    request.mockReturnValueOnce(saved.promise);
    const pending = callbacks.enterRegion!("entry-strip", { ...world().player, tile_x: 24 });
    await flushPromises();
    session.user = person(2);
    saved.resolve();
    await pending;
    expect(interact).not.toHaveBeenCalled();
  });
  it("drains pending position saves before the world is restarted", async () => {
    const saved = deferred<void>();
    const request = vi.spyOn(api, "request").mockReturnValue(saved.promise);
    const { callbacks, wrapper, handle } = await canvas();
    callbacks.position({ ...world().player, tile_x: 25 });
    await vi.waitFor(() => expect(request).toHaveBeenCalledOnce());
    let complete = false;
    const pending = (wrapper.vm as unknown as { flushSaves(): Promise<void> }).flushSaves().then(() => { complete = true; });
    await flushPromises();
    expect(handle.setPaused).toHaveBeenLastCalledWith(true);
    expect(complete).toBe(false);
    saved.resolve();
    await pending;
    expect(complete).toBe(true);
  });
  it("applies the actual environment music volume without recreating the world", async () => {
    const { handle } = await canvas();
    const preferences = useUiPreferences();
    expect(handle.setVolume).toHaveBeenCalledWith(0.15);
    preferences.musicVolume = 0.42;
    useSession().audioEnabled = true;
    await flushPromises();
    expect(handle.setVolume).toHaveBeenLastCalledWith(0.42);
    expect(handle.mute).toHaveBeenLastCalledWith(false);
    expect(createWorld).toHaveBeenCalledTimes(1);
  });
  it("snapshots the current sprite for the map synchronously without saving or changing server coordinates", async () => {
    const request = vi.spyOn(api, "request").mockResolvedValue(undefined);
    const { callbacks, wrapper, getMapPosition } = await canvas();
    const session = useSession();
    callbacks.mapPosition?.({ ...world().player, tile_x: 23, direction: "right" });
    expect(session.mapPosition?.tile_x).toBe(23);
    expect(session.world?.player.tile_x).toBe(22);
    getMapPosition.mockReturnValue({ ...world().player, tile_x: 24.375, direction: "right" });
    session.open({ kind: "map" });
    expect(session.mapPosition?.tile_x).toBe(24.375);
    expect(session.world?.player.tile_x).toBe(22);
    await wrapper.setProps({ paused: true });
    expect(session.mapPosition?.tile_x).toBe(24.375);
    expect(request).not.toHaveBeenCalled();
    expect(fetch).not.toHaveBeenCalled();
  });
  it("keeps the latest live position when an older integer save finishes", async () => {
    const saved = deferred<void>();
    const request = vi.spyOn(api, "request").mockReturnValue(saved.promise);
    const { callbacks } = await canvas();
    callbacks.position({ ...world().player, tile_x: 23 });
    callbacks.mapPosition?.({ ...world().player, tile_x: 24.5, direction: "right" });
    await flushPromises();
    expect(useSession().mapPosition?.tile_x).toBe(24.5);
    saved.resolve();
    await flushPromises();
    useSession().world = { ...world(), player: { ...world().player, tile_x: 23 } };
    expect(useSession().mapPosition?.tile_x).toBe(24.5);
    expect(useSession().world?.player.tile_x).toBe(23);
    expect(request).toHaveBeenCalledTimes(1);
    expect(JSON.parse(request.mock.calls[0]![1]!.body as string).tile_x).toBe(23);
  });
  it("clears live map positions on account or classroom changes and ignores the old world's callbacks", async () => {
    const { callbacks } = await canvas();
    const session = useSession();
    callbacks.mapPosition?.({ ...world().player, tile_x: 24.5 });
    session.classroomId = 2;
    expect(session.mapPosition).toBeNull();
    session.world = { ...world(), player: { ...world().player, tile_x: 10 } };
    expect(session.mapPosition?.tile_x).toBe(10);
    callbacks.mapPosition?.({ ...world().player, tile_x: 45.5 });
    expect(session.mapPosition?.tile_x).toBe(10);
    session.classroomId = 1;
    session.user = person(2);
    expect(session.mapPosition).toBeNull();
    session.world = { ...world(), player: { ...world().player, tile_x: 12 } };
    callbacks.mapPosition?.({ ...world().player, tile_x: 45.5 });
    expect(session.mapPosition?.tile_x).toBe(12);
  });
  it("saves the source before a transition and applies only the server's destination", async () => {
    const saved = deferred<void>();
    vi.spyOn(api, "request").mockReturnValue(saved.promise);
    const authoritative = { ...world(), player: { map_key: "library", tile_x: 9, tile_y: 11, direction: "up" } };
    const post = vi.spyOn(api, "post").mockResolvedValue({ player: authoritative.player, world: authoritative });
    const { callbacks } = await canvas();
    const transition = callbacks.transition("library_door", { ...world().player, tile_x: 8, tile_y: 22 });
    await flushPromises();
    expect(post).not.toHaveBeenCalled();
    saved.resolve();
    expect(await transition).toEqual(authoritative.player);
    expect(post).toHaveBeenCalledWith("/world/transition", { classroom_id: 1, portal_id: "library_door" });
    expect(useSession().world?.player).toEqual(authoritative.player);
    expect(useSession().mapPosition).toEqual(authoritative.player);
    callbacks.mapPosition?.({ ...world().player, tile_x: 40.5 });
    expect(useSession().mapPosition).toEqual(authoritative.player);
  });
  it("reconciles a committed transition after a lost response without posting it again", async () => {
    vi.spyOn(api, "request").mockResolvedValue(undefined);
    const authoritative = { ...world(), player: { map_key: "library", tile_x: 9, tile_y: 11, direction: "up" } };
    const post = vi.spyOn(api, "post").mockRejectedValue(new Error("connection lost"));
    const get = vi.spyOn(api, "get").mockResolvedValue(authoritative);
    const { callbacks } = await canvas();
    expect(await callbacks.transition("library_door", { ...world().player, tile_x: 8, tile_y: 22 })).toEqual(authoritative.player);
    expect(post).toHaveBeenCalledTimes(1);
    expect(get).toHaveBeenCalledWith("/world?classroom_id=1");
    expect(useSession().world?.player.map_key).toBe("library");
  });
  it("leaves the source unchanged after denied entry and drops another account's delayed transition", async () => {
    vi.spyOn(api, "request").mockResolvedValue(undefined);
    const post = vi.spyOn(api, "post").mockRejectedValueOnce(new ApiError(403, "入口尚未开放"));
    const { callbacks } = await canvas();
    expect(await callbacks.transition("library_door", world().player)).toBeNull();
    expect(useSession().world?.player.map_key).toBe("town_hub");
    const pending = deferred<{ player: World["player"]; world: World }>();
    post.mockReturnValueOnce(pending.promise);
    const transition = callbacks.transition("library_door", world().player);
    await flushPromises();
    useSession().user = person(2);
    useSession().user = person(1);
    const authoritative = { ...world(), player: { ...world().player, map_key: "library" } };
    pending.resolve({ player: authoritative.player, world: authoritative });
    expect(await transition).toBeNull();
    expect(useSession().world?.player.map_key).toBe("town_hub");
  });
  it("keeps a single world surface without the removed location directory", async () => {
    const { wrapper } = await canvas();
    expect(wrapper.find(".location-toggle").exists()).toBe(false);
    expect(wrapper.find(".world-location-directory").exists()).toBe(false);
    expect(wrapper.get(".world-canvas").attributes("aria-label")).toBe("Pythontopia 像素小镇");
    await wrapper.setProps({ paused: true });
    expect(wrapper.findAll(".world-canvas")).toHaveLength(1);
  });
  it.each([
    "workspace round trip",
    "pause round trip",
    "classroom round trip",
    "account round trip",
    "unmount",
  ])("drops the delayed interaction after %s", async (interrupt) => {
    const saved = deferred<void>();
    vi.spyOn(api, "request").mockReturnValue(saved.promise);
    const { callbacks, wrapper } = await canvas();
    callbacks.position({ ...world().player, tile_x: 23 });
    await flushPromises();
    callbacks.interact("mayor_py", "npc");
    const session = useSession();
    if (interrupt === "workspace round trip") {
      session.open({ kind: "journal" });
      session.open(null);
    } else if (interrupt === "pause round trip") {
      await wrapper.setProps({ paused: true });
      await wrapper.setProps({ paused: false });
    } else if (interrupt === "classroom round trip") {
      session.classroomId = 2;
      session.classroomId = 1;
    } else if (interrupt === "account round trip") {
      session.user = person(2);
      session.user = person(1);
    } else wrapper.unmount();
    saved.resolve();
    await flushPromises();
    expect(wrapper.emitted("interact")).toBeUndefined();
  });

  it("accepts one current interaction after the position save, without replaying repeats", async () => {
    const saved = deferred<void>();
    vi.spyOn(api, "request").mockReturnValue(saved.promise);
    const { callbacks, wrapper } = await canvas();
    callbacks.position({ ...world().player, tile_x: 23 });
    await flushPromises();
    callbacks.interact("mayor_py", "npc");
    callbacks.interact("mayor_py", "npc");
    expect(wrapper.emitted("interact")).toBeUndefined();
    saved.resolve();
    await flushPromises();
    expect(wrapper.emitted("interact")).toEqual([["mayor_py", "npc"]]);
  });
});

function dialogue() {
  const wrapper = mount(DialogueLayer, {
    props: { npc: resident(), actions: [] },
    attachTo: document.body,
    global: { plugins: [pinia] },
  });
  mounted.push(wrapper);
  return wrapper;
}

const stepResult = () => ({
  node: resident().dialogue[0],
  next_id: null,
  completed_root: "intro",
  effects: [
    {
      type: "open_activity",
      kind: "lesson",
      activity_id: "old-conversation-lesson",
    },
  ],
});

describe("late scripted dialogue responses", () => {
  it("reveals text on the first click and only advances on the next click", async () => {
    useUiPreferences().reduceMotion = false;
    const post = vi.spyOn(api, "post").mockReturnValue(new Promise(() => {}));
    const wrapper = dialogue();
    expect(wrapper.find(".primary").exists()).toBe(false);
    await wrapper.get(".dialogue-text").trigger("click");
    expect(wrapper.get(".dialogue-text").text()).toBe(resident().dialogue[0]!.text);
    expect(post).not.toHaveBeenCalled();
    await wrapper.get(".dialogue-text").trigger("click");
    expect(post).toHaveBeenCalledTimes(1);
  });

  it("reveals choices after the text, without choosing on a background click", async () => {
    useUiPreferences().reduceMotion = false;
    const post = vi.spyOn(api, "post").mockReturnValue(new Promise(() => {}));
    const wrapper = dialogue();
    const npc = resident();
    npc.dialogue[0]!.choices = [{ id: "yes", label: "开始", next: "end" }];
    await wrapper.setProps({ npc });
    expect(wrapper.find(".dialogue-response").exists()).toBe(false);
    await wrapper.get(".dialogue-text").trigger("click");
    expect(wrapper.get(".dialogue-response").text()).toContain("开始");
    await wrapper.get(".dialogue-box").trigger("click");
    expect(post).not.toHaveBeenCalled();
    await wrapper.get(".dialogue-response").trigger("click");
    expect(post).toHaveBeenCalledTimes(1);
  });

  it("advances narration by clicking but stops before choices and completion", async () => {
    const npc = resident();
    npc.dialogue = [
      { id: "intro", text: "Welcome", next: "decision" },
      { id: "decision", text: "Your response?", choices: [{ id: "yes", label: "Let's begin", next: "end" }] },
      { id: "end", text: "Ready" },
    ];
    const post = vi.spyOn(api, "post").mockResolvedValue({
      node: npc.dialogue[0], next_id: "decision", effects: [],
    });
    const wrapper = dialogue();
    await wrapper.setProps({ npc });
    await wrapper.get(".dialogue-box").trigger("click");
    await flushPromises();
    expect(post).toHaveBeenCalledTimes(1);
    expect(wrapper.get(".dialogue-response").text()).toContain("Let's begin");
    await wrapper.get(".dialogue-box").trigger("click");
    expect(post).toHaveBeenCalledTimes(1);
    expect(wrapper.emitted("complete")).toBeUndefined();
    await wrapper.trigger("keydown", { key: "ArrowDown" });
    expect(document.activeElement).toBe(wrapper.get(".dialogue-response").element);
  });

  it.each([
    "Escape",
    "unmount",
    "account round trip",
    "classroom round trip",
    "different NPC",
    "same NPC reopened",
  ])("cannot complete or open an activity after %s", async (interrupt) => {
    const pending = deferred<ReturnType<typeof stepResult>>();
    vi.spyOn(api, "post").mockReturnValue(pending.promise);
    const wrapper = dialogue();
    await wrapper.get(".dialogue-box").trigger("click");
    const session = useSession();
    if (interrupt === "Escape") {
      window.dispatchEvent(
        new KeyboardEvent("keydown", { key: "Escape", cancelable: true }),
      );
    } else if (interrupt === "unmount") wrapper.unmount();
    else if (interrupt === "account round trip") {
      session.user = person(2);
      session.user = person(1);
    } else if (interrupt === "classroom round trip") {
      session.classroomId = 2;
      session.classroomId = 1;
    } else
      await wrapper.setProps({
        npc: resident(interrupt === "different NPC" ? "professor" : "mayor_py"),
      });
    session.open({ kind: "settings" });
    pending.resolve(stepResult());
    await flushPromises();
    expect(wrapper.emitted("complete")).toBeUndefined();
    expect(session.workspace).toEqual({ kind: "settings" });
  });

  it("still completes the current transaction and applies its activity effect once", async () => {
    const pending = deferred<ReturnType<typeof stepResult>>();
    const post = vi.spyOn(api, "post").mockReturnValue(pending.promise);
    const wrapper = dialogue();
    await wrapper.get(".dialogue-box").trigger("click");
    await wrapper.get(".dialogue-box").trigger("click");
    expect(post).toHaveBeenCalledTimes(1);
    pending.resolve(stepResult());
    await flushPromises();
    expect(wrapper.emitted("complete")).toHaveLength(1);
    expect(useSession().workspace).toEqual({
      kind: "lesson",
      activity_id: "old-conversation-lesson",
    });
  });
});

describe("session load generation", () => {
  it.each(["profile", "classrooms", "world"])(
    "does not overwrite a newer load with a stale %s response",
    async (phase) => {
      const pending = deferred<unknown>();
      let newer = false;
      vi.spyOn(api, "get").mockImplementation(async (path) => {
        const owner = newer ? 2 : 1;
        if (path === "/auth/me")
          return (
            !newer && phase === "profile"
              ? await pending.promise
              : person(owner)
          ) as never;
        if (path === "/classrooms")
          return (
            !newer && phase === "classrooms"
              ? await pending.promise
              : [classroom(owner)]
          ) as never;
        if (path.startsWith("/world?"))
          return (
            !newer && phase === "world"
              ? await pending.promise
              : world(`owner-${owner}`)
          ) as never;
        throw new Error(`Unexpected fixture path ${path}`);
      });
      const session = useSession();
      const obsolete = session.load();
      await flushPromises();
      newer = true;
      await session.load();
      pending.resolve(
        phase === "profile"
          ? person(1)
          : phase === "classrooms"
            ? [classroom(1)]
            : world("owner-1"),
      );
      await obsolete;
      expect(session.user).toEqual(person(2));
      expect(session.classes).toEqual([classroom(2)]);
      expect(session.classroomId).toBe(2);
      expect(session.world?.flags).toEqual(["owner-2"]);
    },
  );

  it("cannot repopulate the account after logout even without another load", async () => {
    const pending = deferred<User>();
    const get = vi.spyOn(api, "get").mockReturnValue(pending.promise);
    const session = useSession();
    const obsolete = session.load();
    session.reset();
    pending.resolve(person(1));
    await obsolete;
    expect(get).toHaveBeenCalledTimes(1);
    expect(session.user).toBeNull();
    expect(session.classes).toEqual([]);
    expect(session.world).toBeNull();
  });

  it("does not partially replace the user when the classroom request fails", async () => {
    vi.spyOn(api, "get")
      .mockResolvedValueOnce(person(2))
      .mockRejectedValueOnce(new Error("classroom load failed"));
    const session = useSession();
    session.classes = [classroom(1)];
    await expect(session.load()).rejects.toThrow("classroom load failed");
    expect(session.user).toEqual(person(1));
    expect(session.classes).toEqual([classroom(1)]);
  });
});
