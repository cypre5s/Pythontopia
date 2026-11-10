import { it, expect, vi, afterEach } from "vitest";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import App from "../src/App";
import { setToken } from "../src/lib/api";
vi.mock("../src/pages/materials", async () => {
  const { useDraft } = await import("../src/lib/drafts");
  return { Materials: () => { useDraft(true); return <input aria-label="未保存资料" defaultValue="保留草稿" />; } };
});
const json = (value: unknown) => new Response(JSON.stringify(value), { headers: { "Content-Type": "application/json" } });
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); setToken(null); history.replaceState({}, "", "/"); });
it("cancelling draft discard prevents class creation and keeps the current class and draft", async () => {
  setToken("test-session");
  history.replaceState({}, "", "/teacher/materials");
  const create = vi.fn();
  vi.stubGlobal("fetch", vi.fn(async (url: string, init: RequestInit) => {
    if (init.method === "POST") { create(); return json({ id: 2, name: "新班级" }); }
    return json(url.endsWith("/auth/me") ? { id: 1, name: "教师", role: "teacher" } : [{ id: 1, name: "原班级" }]);
  }));
  vi.spyOn(window, "confirm").mockReturnValue(false);
  const user = userEvent.setup();
  render(<App />);
  await screen.findByLabelText("未保存资料");
  await user.click(screen.getByRole("button", { name: "创建班级" }));
  await user.type(screen.getByLabelText("班级名称"), "新班级");
  await user.click(screen.getAllByRole("button", { name: "创建班级" }).at(-1)!);
  expect(create).not.toHaveBeenCalled();
  expect(screen.getByLabelText("当前班级")).toHaveTextContent("原班级");
  expect(screen.getByLabelText("未保存资料")).toHaveValue("保留草稿");
});
it("ignores an old account's class list even when its fetch resolves after cancellation", async () => {
  setToken("old-session");
  history.replaceState({}, "", "/teacher/materials");
  let finishOld!: (response: Response) => void;
  vi.stubGlobal("fetch", vi.fn(async (url: string, init: RequestInit) => {
    if (url.endsWith("/auth/me")) return json({ id: 1, name: "旧教师", role: "teacher" });
    if (url.endsWith("/auth/login")) return json({ access_token: "new-session", user: { id: 2, name: "新教师", role: "teacher" } });
    if (new Headers(init.headers).get("Authorization") === "Bearer old-session") return new Promise<Response>((resolve) => { finishOld = resolve; });
    return json([{ id: 22, name: "新班级" }]);
  }));
  const user = userEvent.setup();
  render(<App />);
  await waitFor(() => expect(finishOld).toBeDefined());
  act(() => { setToken(null); window.dispatchEvent(new Event("session-expired")); });
  await user.type(screen.getByLabelText("学号", { exact: true }), "9999");
  await user.type(screen.getByLabelText("密码", { exact: true }), "test-password");
  await user.click(screen.getByRole("button", { name: "进入工作台" }));
  await waitFor(() => expect(screen.getByLabelText("当前班级")).toHaveTextContent("新班级"));
  await act(async () => { finishOld(json([{ id: 11, name: "旧班级" }])); });
  expect(screen.getByLabelText("当前班级")).toHaveTextContent("新班级");
  await user.click(screen.getByLabelText("当前班级"));
  expect(await screen.findByRole("option", { name: "新班级" })).toBeVisible();
  expect(screen.queryByRole("option", { name: "旧班级" })).not.toBeInTheDocument();
});
