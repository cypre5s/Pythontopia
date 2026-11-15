import type { User } from "./types";
import type { Workspace } from "./types";

export type ExperienceClientState = { session?: Record<string, string>; local?: Record<string, string>; workspace?: Workspace };
function experienceKey(key: string, owner: string) { return key.startsWith(`read:${owner}:`) || key.startsWith(`draft:${owner}:`); }
export function captureExperience(userId: number, classroomId: number, workspace?: Workspace): ExperienceClientState {
  const owner = `${userId}:${classroomId}`, result: ExperienceClientState = { workspace };
  for (const [name, storage] of [['session', sessionStorage], ['local', localStorage]] as const) {
    const values: Record<string, string> = {};
    for (let index = 0; index < storage.length; index++) { const key = storage.key(index); if (key && experienceKey(key, owner)) values[key] = storage.getItem(key)!; }
    result[name] = values;
  }
  return result;
}
export function restoreExperienceCaches(userId: number, classroomId: number, state: ExperienceClientState) {
  const owner = `${userId}:${classroomId}`;
  for (const [name, storage] of [['session', sessionStorage], ['local', localStorage]] as const) {
    const keys = Array.from({ length: storage.length }, (_, index) => storage.key(index)).filter((key): key is string => !!key && experienceKey(key, owner));
    for (const key of keys) storage.removeItem(key);
    for (const [key, value] of Object.entries(state[name] ?? {})) if (experienceKey(key, owner) && typeof value === 'string') storage.setItem(key, value);
  }
}

export function canUseDevTools(user: Pick<User, "role" | "dev_tools_enabled"> | null | undefined): boolean {
  return user?.role === "student" && user.dev_tools_enabled === true;
}

/** Remove only this learner's current classroom caches after a verified reset. */
export function clearClassroomCaches(userId: number, classroomId: number, stores?: { session: Storage; local: Storage }): { failedStorage: boolean } {
  let failedStorage = false;
  const owner = `${userId}:${classroomId}`;
  function clear(resolve: () => Storage, prefixes: string[], exact: string[] = []) {
    try {
      const target = resolve(), exactKeys = new Set(exact), keys: string[] = [];
      for (let index = 0; index < target.length; index++) {
        const key = target.key(index);
        if (key && (exactKeys.has(key) || prefixes.some(prefix => key.startsWith(prefix)))) keys.push(key);
      }
      for (const key of keys) {
        try { target.removeItem(key); }
        catch { failedStorage = true; }
      }
    } catch { failedStorage = true; }
  }
  clear(() => stores?.session ?? sessionStorage,
    [`read:${owner}:`, `draft:${owner}:`, `assistant:${owner}:`], [`library-view:${owner}`, `library-scroll:${owner}`]);
  clear(() => stores?.local ?? localStorage, [`draft:${owner}:`]);
  return { failedStorage };
}
