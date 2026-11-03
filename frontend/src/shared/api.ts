import { ref } from "vue";
export const notices = ref<{ id: number; text: string }[]>([]);
let nextId = 0;
export function notify(text: string) {
  const id = ++nextId;
  notices.value.push({ id, text });
  window.setTimeout(() => {
    notices.value = notices.value.filter((n) => n.id !== id);
  }, 6500);
}
const TOKEN_KEY = "pythontopia-reborn-session";
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}
export const api = {
  token: () => sessionStorage.getItem(TOKEN_KEY),
  setToken: (token: string | null) => {
    if (token) sessionStorage.setItem(TOKEN_KEY, token);
    else sessionStorage.removeItem(TOKEN_KEY);
  },
  async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const headers = new Headers(init.headers);
    if (!(init.body instanceof FormData))
      headers.set("Content-Type", "application/json");
    const token = this.token();
    if (token) headers.set("Authorization", `Bearer ${token}`);
    const res = await fetch(`/api${path}`, { ...init, headers });
    if (!res.ok) {
      const body = await res.json().catch(() => ({ detail: res.statusText }));
      throw new ApiError(
        res.status,
        typeof body.detail === "string"
          ? body.detail
          : JSON.stringify(body.detail),
      );
    }
    if (res.status === 204) return undefined as T;
    return res.json() as Promise<T>;
  },
  get<T>(path: string) {
    return this.request<T>(path);
  },
  post<T>(path: string, body: unknown) {
    return this.request<T>(path, {
      method: "POST",
      body: JSON.stringify(body),
    });
  },
  put<T>(path: string, body: unknown) {
    return this.request<T>(path, { method: "PUT", body: JSON.stringify(body) });
  },
  async blob(path: string, signal?: AbortSignal) {
    const token = this.token();
    const headers: Record<string, string> = {};
    if (token) headers.Authorization = `Bearer ${token}`;
    const res = await fetch(path.startsWith("/api") ? path : `/api${path}`, {
      headers,
      signal,
    });
    if (!res.ok) throw new ApiError(res.status, "资料加载失败或无权访问");
    return res.blob();
  },
};
export async function reportError(error: unknown) {
  notify(error instanceof Error ? error.message : String(error));
}
