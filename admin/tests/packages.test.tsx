import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Packages } from "../src/pages/packages";
import { setToken } from "../src/lib/api";

class PackageRequest {
  static HEADERS_RECEIVED = 2;
  upload: {
    onprogress?: (event: ProgressEvent) => void;
    onload?: () => void;
  } = {};
  onprogress?: (event: ProgressEvent) => void;
  onreadystatechange?: () => void;
  onload?: () => void;
  onerror?: () => void;
  onabort?: () => void;
  onloadend?: () => void;
  response: unknown;
  status = 0;
  readyState = 0;
  open = vi.fn();
  setRequestHeader = vi.fn();
  send = vi.fn();
  getResponseHeader = vi.fn<() => string | null>(() => null);
  abort = vi.fn(() => {
    this.onabort?.();
    this.onloadend?.();
  });
}

function stubTransfer() {
  const xhr = new PackageRequest();
  vi.stubGlobal(
    "XMLHttpRequest",
    Object.assign(
      function () { return xhr; },
      { HEADERS_RECEIVED: PackageRequest.HEADERS_RECEIVED },
    ),
  );
  return xhr;
}

beforeEach(() => {
  vi.stubGlobal("fetch", vi.fn(() => Promise.resolve(new Response(JSON.stringify({ items: [], unavailable: 0 })))));
});
afterEach(() => vi.unstubAllGlobals());

it("separates upload bytes, server validation and applying while preserving apply retries", async () => {
  const xhr = stubTransfer();
  const apply: Record<string, unknown>[] = [];
  let finishApply!: (response: Response) => void;
  vi.stubGlobal("fetch", vi.fn((url: string, init: RequestInit) => {
    if (url.endsWith("/packages")) return Promise.resolve(new Response(JSON.stringify({ items: [], unavailable: 0 })));
    apply.push(JSON.parse(init.body as string));
    return new Promise<Response>((resolve) => { finishApply = resolve; });
  }));
  const user = userEvent.setup();
  setToken("teacher-session");
  render(<Packages classroomId={31} />);
  await user.upload(
    screen.getByLabelText("课程包 ZIP 文件"),
    new File(["fixture"], "course.zip", { type: "application/zip" }),
  );
  await user.click(screen.getByRole("button", { name: "校验并预览" }));
  expect(xhr.open).toHaveBeenCalledWith(
    "POST", "/api/teacher/classrooms/31/course/imports/preview",
  );
  expect(xhr.setRequestHeader).toHaveBeenCalledWith("Authorization", "Bearer teacher-session");
  act(() => xhr.upload.onprogress?.(new ProgressEvent("progress", {
    lengthComputable: true, loaded: 256, total: 1024,
  })));
  expect(screen.getByRole("progressbar", { name: "正在上传课程包" })).toHaveAttribute("value", "256");
  expect(screen.getByText("256 B / 1.0 KiB · 25%")).toBeVisible();
  act(() => xhr.upload.onload?.());
  expect(screen.getByRole("progressbar", { name: "正在校验课程包并生成差异" })).not.toHaveAttribute("value");
  expect(screen.getByRole("button", { name: "导出课程包" })).toBeDisabled();
  expect(screen.queryByText("导入差异")).not.toBeInTheDocument();
  act(() => {
    xhr.status = 200;
    xhr.response = {
      id: "preview-1",
      expected_revision: 4,
      package_id: "test",
      package_version: "1",
      name: "测试课程",
      can_apply: true,
      expires_at: "2030-01-01T00:00:00Z",
      blockers: [],
      diff: { questions: { added: ["q1"], updated: [], removed: [] } },
      warnings: [],
    };
    xhr.onload?.();
    xhr.onloadend?.();
  });
  await screen.findByText("导入差异");
  expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
  expect(apply).toHaveLength(0);

  await user.click(screen.getByRole("checkbox", { name: /我已核对差异/ }));
  const applyButton = screen.getByRole("button", { name: "确认导入" });
  await user.click(applyButton);
  expect(screen.getByRole("progressbar", { name: "正在导入课程和资源" })).not.toHaveAttribute("value");
  expect(applyButton).toBeDisabled();
  await act(() => finishApply(new Response(JSON.stringify({ detail: "连接暂时失败" }), { status: 503 })));
  expect(screen.getByRole("alert")).toHaveTextContent("连接暂时失败");
  await user.click(applyButton);
  await act(() => finishApply(new Response(JSON.stringify({ instance_id: 1, revision: 5 }))));
  expect(screen.getByRole("status")).toHaveTextContent("课程包已导入");
  expect(screen.getByLabelText("课程包 ZIP 文件")).toHaveValue("");
  expect(apply).toHaveLength(2);
  expect(apply[0]).toEqual(apply[1]);
  expect(apply[0].expected_revision).toBe(4);
});

it("shows packaging, download bytes and errors, including responses without a total", async () => {
  const xhr = stubTransfer();
  const user = userEvent.setup();
  render(<Packages classroomId={31} />);
  await user.click(screen.getByRole("button", { name: "导出课程包" }));
  expect(screen.getByRole("progressbar", { name: "正在打包课程和资源" })).not.toHaveAttribute("value");

  act(() => {
    xhr.status = 200;
    xhr.readyState = PackageRequest.HEADERS_RECEIVED;
    xhr.onreadystatechange?.();
    xhr.onprogress?.(new ProgressEvent("progress", { loaded: 512 }));
  });
  const progress = screen.getByRole("progressbar", { name: "正在下载课程包" });
  expect(progress).not.toHaveAttribute("value");
  expect(screen.getByText("已传输 512 B")).toBeVisible();
  act(() => xhr.onprogress?.(new ProgressEvent("progress", {
    lengthComputable: true, loaded: 1024, total: 2048,
  })));
  expect(progress).toHaveAttribute("value", "1024");
  expect(screen.getByText("1.0 KiB / 2.0 KiB · 50%")).toBeVisible();
  await act(() => {
    xhr.onerror?.();
    xhr.onloadend?.();
  });
  expect(screen.getByRole("alert")).toHaveTextContent("传输中断");
  expect(screen.getByRole("button", { name: "导出课程包" })).toBeEnabled();
});

it("aborts the transfer when the package page is left", async () => {
  const xhr = stubTransfer();
  const user = userEvent.setup();
  const { unmount } = render(<Packages classroomId={31} />);
  await user.click(screen.getByRole("button", { name: "导出课程包" }));
  unmount();
  expect(xhr.abort).toHaveBeenCalledOnce();
});


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
  expect(screen.getByLabelText("课程包 ZIP 文件")).toBeEnabled();
});

it("keeps uploads available if the existing package catalog fails", async () => {
  vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ detail: "课程列表暂不可用" }), { status: 503 })));
  const user = userEvent.setup();
  render(<Packages classroomId={31} />);
  expect(await screen.findByRole("alert")).toHaveTextContent("课程列表暂不可用");
  expect(screen.getByRole("button", { name: "预览所选课程包" })).toBeDisabled();
  await user.upload(screen.getByLabelText("课程包 ZIP 文件"), new File(["fixture"], "course.zip", { type: "application/zip" }));
  expect(screen.getByRole("button", { name: "校验并预览" })).toBeEnabled();
});
