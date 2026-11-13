import { afterEach, expect, it, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Packages } from "../src/pages/packages";
afterEach(() => vi.unstubAllGlobals());
it("previews an existing package without uploading and still requires explicit confirmation", async () => {
  const calls: { url: string; body: unknown }[] = [];
  const key = "mounted-" + "a".repeat(64);
  vi.stubGlobal("fetch", vi.fn(async (url: string, init: RequestInit) => {
    if (url.endsWith("/packages")) return new Response(JSON.stringify({ items: [
      { key, name: "共享课程", package_id: "shared", package_version: "2", source: "mounted" },
    ], unavailable: 0 }));
    calls.push({ url, body: JSON.parse(init.body as string) });
    if (url.endsWith("/imports/available/preview")) return new Response(JSON.stringify({
      id: "mounted-preview", expected_revision: 3, package_id: "shared", package_version: "2", name: "共享课程",
      can_apply: true, expires_at: "2030-01-01T00:00:00Z", blockers: [], warnings: [],
      diff: { activities: { added: ["lesson"], updated: [], removed: [] } },
    }));
    return new Response(JSON.stringify({ instance_id: 1, revision: 4 }));
  }));
  const user = userEvent.setup();
  render(<Packages classroomId={31} />);
  await user.click(await screen.findByRole("combobox", { name: "选择已有课程包" }));
  await user.click(await screen.findByRole("option", { name: "共享课程 · 2 · 可用课程包" }));
  await user.click(screen.getByRole("button", { name: "预览所选课程包" }));
  await screen.findByText("导入差异");
  expect(calls).toEqual([{ url: "/api/teacher/classrooms/31/course/imports/available/preview", body: { package_key: key } }]);
  expect(screen.getByRole("button", { name: "确认导入" })).toBeDisabled();
  await user.click(screen.getByRole("checkbox", { name: /我已核对差异/ }));
  await user.click(screen.getByRole("button", { name: "确认导入" }));
  await screen.findByText("课程包已导入，班级课程已更新。");
  expect(calls[1].url).toBe("/api/teacher/classrooms/31/course/imports/mounted-preview/apply");
  expect(calls[1].body).toMatchObject({ expected_revision: 3 });
  expect(screen.queryByLabelText("课程包 ZIP 文件")).not.toBeInTheDocument();
});


it("exports to a server directory and cancels requests when leaving", async () => {
  let finish!: (response: Response) => void;
  let signal: AbortSignal | undefined;
  vi.stubGlobal("fetch", vi.fn((url: string, init: RequestInit) => {
    if (url.endsWith("/packages")) return Promise.resolve(new Response(JSON.stringify({ items: [], unavailable: 0 })));
    signal = init.signal as AbortSignal;
    return new Promise<Response>((resolve) => { finish = resolve; });
  }));
  const user = userEvent.setup();
  const { unmount } = render(<Packages classroomId={31} />);
  await user.click(screen.getByRole("button", { name: "导出课程包" }));
  expect(screen.getByText("正在导出课程目录和资源")).toBeVisible();
  expect(screen.getByText(/已等待/)).toBeVisible();
  await act(() => finish(new Response(JSON.stringify({ path: "data/course/export-31-test" }))));
  expect(screen.getByText("课程已导出到 data/course/export-31-test")).toBeVisible();
  await user.click(screen.getByRole("button", { name: "导出课程包" }));
  unmount();
  expect(signal?.aborted).toBe(true);
});

it("shows a catalog error with a retry and no ZIP upload", async () => {
  vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ detail: "课程列表暂不可用" }), { status: 503 })));
  render(<Packages classroomId={31} />);
  expect(await screen.findByRole("alert")).toHaveTextContent("课程列表暂不可用");
  expect(screen.getByRole("button", { name: "刷新列表" })).toBeEnabled();
  expect(screen.queryByLabelText("课程包 ZIP 文件")).not.toBeInTheDocument();
});
