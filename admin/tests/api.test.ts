import { describe, it, expect, vi, afterEach } from "vitest";
import { api, setToken, scoped, ApiError, fileBlob } from "../src/lib/api";
afterEach(() => vi.unstubAllGlobals());
describe("API authorization boundary", () => {
  it("rejects old-account downloads after account switching", async () => {
    setToken("old");
    let finish!: (r: Response) => void;
    vi.stubGlobal(
      "fetch",
      vi.fn(
        () =>
          new Promise<Response>((resolve) => {
            finish = resolve;
          }),
      ),
    );
    const pending = fileBlob("/teacher/classrooms/1/course/export");
    setToken("new");
    finish(new Response("private zip"));
    await expect(pending).rejects.toThrow("账号状态已改变");
  });
  it("keeps actionable export errors", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          new Response(
            JSON.stringify({ detail: "课程资料文件缺失，请重新上传" }),
            { status: 422 },
          ),
        ),
    );
    await expect(
      fileBlob("/teacher/classrooms/1/course/export"),
    ).rejects.toThrow("课程资料文件缺失");
  });
  it("scopes content and preserves other query fields", () => {
    expect(scoped("/teacher/questions?status=draft", 14)).toBe(
      "/teacher/questions?status=draft&classroom_id=14",
    );
  });
  it("sends the shared token and accepts empty responses", async () => {
    setToken("test-session");
    const fetch = vi
      .fn()
      .mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetch);
    await api.delete(scoped("/teacher/questions/q1", 14));
    const [url, init] = fetch.mock.calls[0];
    expect(url).toBe("/api/teacher/questions/q1?classroom_id=14");
    expect(init.headers.get("Authorization")).toBe("Bearer test-session");
  });
  it("does not clear a newer login when an older request returns unauthorized", async () => {
    setToken("old");
    let resolve!: (r: Response) => void;
    vi.stubGlobal(
      "fetch",
      vi.fn(
        () =>
          new Promise<Response>((r) => {
            resolve = r;
          }),
      ),
    );
    const request = api.get("/auth/me");
    setToken("new");
    resolve(
      new Response(JSON.stringify({ detail: "expired" }), { status: 401 }),
    );
    await expect(request).rejects.toBeInstanceOf(ApiError);
    expect(sessionStorage.getItem("pythontopia-reborn-session")).toBe("new");
  });
  it("clears the expired current session and broadcasts invalidation", async () => {
    setToken("expired");
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ detail: "请重新登录" }), {
          status: 401,
        }),
      ),
    );
    const event = vi.fn();
    window.addEventListener("session-expired", event);
    await expect(api.get("/auth/me")).rejects.toThrow("请重新登录");
    expect(event).toHaveBeenCalledOnce();
    expect(sessionStorage.length).toBe(0);
    window.removeEventListener("session-expired", event);
  });
});
