import type { User } from "./types";

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
