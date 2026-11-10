export const TOKEN_KEY = "pythontopia-reborn-session";
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public detail?: unknown,
  ) {
    super(message);
  }
}
export const token = () => sessionStorage.getItem(TOKEN_KEY);
export function setToken(value: string | null) {
  if (value) sessionStorage.setItem(TOKEN_KEY, value);
  else sessionStorage.removeItem(TOKEN_KEY);
}
export function scoped(path: string, classroomId: number) {
  return `${path}${path.includes("?") ? "&" : "?"}classroom_id=${classroomId}`;
}
export function responseError(
  status: number,
  payload: { detail?: unknown } | null,
  captured: string | null,
  fallback = "请求失败，请重试",
) {
  const detail = payload?.detail;
  const message =
    typeof detail === "string"
      ? detail
      : Array.isArray(detail)
        ? detail.map((e: { msg?: string }) => e?.msg ?? "字段无效").join("；")
        : detail && typeof detail === "object" && "message" in detail && typeof detail.message === "string"
          ? detail.message
          : fallback;
  if (status === 401 && captured && captured === token()) {
    setToken(null);
    window.dispatchEvent(new Event("session-expired"));
  }
  return new ApiError(status, message, detail);
}
export async function request<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const headers = new Headers(init.headers);
  const captured = token();
  if (captured) headers.set("Authorization", `Bearer ${captured}`);
  if (init.body && !(init.body instanceof FormData))
    headers.set("Content-Type", "application/json");
  const response = await fetch(`/api${path}`, { ...init, headers });
  if (!response.ok) {
    const data = await response.json().catch(() => null);
    throw responseError(response.status, data, captured);
  }
  return response.status === 204
    ? (undefined as T)
    : (response.json() as Promise<T>);
}
export const api = {
  get: <T>(p: string, signal?: AbortSignal) => request<T>(p, { signal }),
  post: <T>(p: string, b: unknown) =>
    request<T>(p, { method: "POST", body: JSON.stringify(b) }),
  put: <T>(p: string, b: unknown) =>
    request<T>(p, { method: "PUT", body: JSON.stringify(b) }),
  delete: (p: string) => request<void>(p, { method: "DELETE" }),
  upload: <T>(p: string, b: FormData) =>
    request<T>(p, { method: "POST", body: b }),
};
export const errorMessage = (e: unknown) =>
  e instanceof Error ? e.message : "操作失败，请重试";
export async function fileBlob(path: string) {
  const headers = new Headers();
  const captured = token();
  if (captured) headers.set("Authorization", `Bearer ${captured}`);
  const r = await fetch(`/api${path}`, { headers });
  if (captured !== token())
    throw new Error("账号状态已改变，请重新发起文件请求");
  if (!r.ok) {
    const data = await r.json().catch(() => null);
    throw responseError(
      r.status,
      data,
      captured,
      "文件读取失败或无权访问",
    );
  }
  const blob = await r.blob();
  if (captured !== token())
    throw new Error("账号状态已改变，请重新发起文件请求");
  return blob;
}
export function download(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
