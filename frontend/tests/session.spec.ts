import { beforeEach, describe, it, expect, vi } from "vitest";
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
      email: "student@example.test",
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
});

it("ignores a late world response from a previously selected classroom", async () => {
  setActivePinia(createPinia());
  const store = useSession();
  store.user = {
    id: 1,
    name: "one",
    email: "one@example.test",
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
