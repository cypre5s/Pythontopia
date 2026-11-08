import { afterEach, describe, expect, it, vi } from "vitest";
import { canUseDevTools, clearClassroomCaches } from "../src/shared/devTools";

afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); sessionStorage.clear(); localStorage.clear(); });

describe("classroom cache reset", () => {
  it("clears current classroom caches from both stores and preserves other classrooms, accounts and preferences", () => {
    const removedSession = ["read:7:2:material-186", "read:7:2:material-186:pdf", "read:7:2:material-186-3:video", "draft:7:2:q1", "assistant:7:2:lesson", "library-view:7:2", "library-scroll:7:2"];
    const retainedSession = ["read:7:20:material-186", "draft:7:20:q1", "assistant:70:2:lesson", "library-view:7:20", "library-scroll:70:2", "library-view:7:2:other", "pythontopia-reborn-session", "classroom:7", "reborn-arrival"];
    const removedLocal = ["draft:7:2:q1", "draft:7:2:q2"];
    const retainedLocal = ["draft:7:20:q1", "draft:70:2:q1", "pythontopia-reborn-interface", "classroom:7"];
    for (const key of [...removedSession, ...retainedSession]) sessionStorage.setItem(key, `value:${key}`);
    for (const key of [...removedLocal, ...retainedLocal]) localStorage.setItem(key, `value:${key}`);
    expect(clearClassroomCaches(7, 2)).toEqual({ failedStorage: false });
    for (const key of removedSession) expect(sessionStorage.getItem(key)).toBeNull();
    for (const key of removedLocal) expect(localStorage.getItem(key)).toBeNull();
    for (const key of retainedSession) expect(sessionStorage.getItem(key)).toBe(`value:${key}`);
    for (const key of retainedLocal) expect(localStorage.getItem(key)).toBe(`value:${key}`);
    expect(clearClassroomCaches(7, 2)).toEqual({ failedStorage: false });
  });

  it("reports inaccessible storage without throwing or skipping the other store", () => {
    localStorage.setItem("draft:7:2:q1", "draft");
    vi.stubGlobal("sessionStorage", { get length() { throw new Error("storage blocked"); } });
    expect(clearClassroomCaches(7, 2)).toEqual({ failedStorage: true });
    expect(localStorage.getItem("draft:7:2:q1")).toBeNull();
  });

  it("continues removing other matching keys after an individual cache removal fails", () => {
    const values = new Map([["read:7:2:a", "one"], ["read:7:2:b", "two"], ["read:7:20:a", "retained"]]);
    const storage = {
      get length() { return values.size; }, key: (index: number) => [...values.keys()][index] ?? null,
      removeItem(key: string) { if (key === "read:7:2:a") throw new Error("blocked"); values.delete(key); },
    } as Storage;
    localStorage.setItem("draft:7:2:q1", "draft");
    expect(clearClassroomCaches(7, 2, { session: storage, local: localStorage })).toEqual({ failedStorage: true });
    expect(values.has("read:7:2:b")).toBe(false);
    expect(values.has("read:7:20:a")).toBe(true);
    expect(localStorage.getItem("draft:7:2:q1")).toBeNull();
  });
});

describe("developer capability", () => {
  it.each([undefined, null, { role: "student" }, { role: "student", dev_tools_enabled: false }, { role: "teacher", dev_tools_enabled: true }, { role: "admin", dev_tools_enabled: true }])("rejects unavailable capabilities %s", user => {
    expect(canUseDevTools(user)).toBe(false);
  });
  it("allows a student with an explicit server capability", () => {
    expect(canUseDevTools({ role: "student", dev_tools_enabled: true })).toBe(true);
  });
});
