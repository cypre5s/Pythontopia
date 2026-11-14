import { afterEach, expect, it, vi } from "vitest";
import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Judge } from "../src/pages/judge";

afterEach(() => vi.unstubAllGlobals());
const row = { id: "submission-1", student_name: "小林", student_id: "123456", question_title: "打招呼", activity_title: "练习",
  status: "wrong_answer", created_at: "2026-11-14T00:00:00Z", passed_count: 1, total_count: 2 };
const service = { paused: false, health: { status: "healthy", detail: "连接与隔离检查通过", latency_ms: 2 }, checked_at: "2026-11-14T00:00:00Z",
  worker_seen_at: "2026-11-14T00:00:00Z", worker_alive: true, counts: { prepared: 2 }, global_counts: { prepared: 4 }, events: [] };
const detail = { ...row, code: "print(input())", feedback: {}, diagnostic: { started_at: "2026-11-14T00:00:00Z", finished_at: "2026-11-14T00:00:01Z", detail: "" },
  cases: [{ index: 0, public: false, input: "小林", expected_output: "你好，小林！", result: { status: "wrong_answer", stdout: "你好，小林", stderr: "", time_ns: 12000000, memory_bytes: 1048576, output_truncated: false } }] };
const response = (data: unknown) => new Response(JSON.stringify(data));

it("applies the same inclusive dates to statistics and submissions and allows all history", async () => {
  const urls: string[] = [];
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    urls.push(url);
    return response(url.includes("/service?") ? service : { items: [], total: 0 });
  }));
  const user = userEvent.setup();
  render(<Judge classroomId={1} canManage />);
  await screen.findByText("没有匹配的提交");
  expect(screen.getByRole("button", { name: "近七天" })).toHaveAttribute("aria-pressed", "true");
  expect(urls.every(url => url.includes("since=") && url.includes("until="))).toBe(true);
  fireEvent.change(screen.getByLabelText("开始日期"), { target: { value: "2026-11-10" } });
  fireEvent.change(screen.getByLabelText("结束日期"), { target: { value: "2026-11-12" } });
  await user.click(screen.getByRole("button", { name: "应用日期" }));
  await waitFor(() => expect(urls.length).toBe(4));
  for (const url of urls.slice(-2)) {
    const params = new URL(url, "http://test").searchParams;
    expect(params.get("since")).toBe("2026-11-09T16:00:00.000Z");
    expect(params.get("until")).toBe("2026-11-12T16:00:00.000Z");
  }
  fireEvent.change(screen.getByLabelText("开始日期"), { target: { value: "2026-11-15" } });
  await user.click(screen.getByRole("button", { name: "应用日期" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("结束日期不能早于开始日期");
  expect(urls.length).toBe(4);
  await user.click(screen.getByRole("button", { name: "全部时间" }));
  await waitFor(() => expect(urls.length).toBe(6));
  expect(urls.slice(-2).every(url => !url.includes("since=") && !url.includes("until="))).toBe(true);
});

it("shows private submitted evidence and handles missing historical diagnostics", async () => {
  let historical = false;
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (url.includes("/service?")) return response(service);
    if (url.includes("/submissions/")) return response(historical ? { ...detail, diagnostic: null, cases: [{ ...detail.cases[0], result: null }] } : detail);
    return response({ items: [row], total: 1 });
  }));
  const user = userEvent.setup();
  render(<Judge classroomId={1} canManage={false} />);
  await user.click(await screen.findByRole("button", { name: /查看 小林 的提交/ }));
  const dialog = await screen.findByRole("dialog");
  expect(await within(dialog).findByText("print(input())")).toBeVisible();
  expect(within(dialog).getByText("你好，小林！")).toBeVisible();
  expect(within(dialog).getByText("你好，小林")).toBeVisible();
  expect(within(dialog).getByText(/CPU 耗时：12.00 ms/)).toBeVisible();
  expect(screen.queryByRole("button", { name: "暂停派发" })).not.toBeInTheDocument();
  await user.keyboard("{Escape}");
  historical = true;
  await user.click(screen.getByRole("button", { name: /查看 小林 的提交/ }));
  expect(await screen.findByText("当时未记录执行诊断；历史提交不会自动重跑。")).toBeVisible();
  expect(screen.getByText(/没有执行记录/)).toBeVisible();
});

it("requires a reason and shows global scope before administrator pauses dispatch", async () => {
  const mutations: unknown[] = [];
  vi.stubGlobal("fetch", vi.fn(async (url: string, init: RequestInit) => {
    if (init.method === "PUT") { mutations.push(JSON.parse(init.body as string)); return response({ paused: true }); }
    return response(url.includes("/service") ? service : { items: [], total: 0 });
  }));
  const user = userEvent.setup();
  render(<Judge classroomId={1} canManage />);
  await screen.findByText(/连接正常/);
  await user.click(screen.getByRole("button", { name: "暂停派发" }));
  expect(screen.getByText(/此操作影响所有班级/)).toBeVisible();
  expect(screen.getByRole("button", { name: "确认" })).toBeDisabled();
  await user.type(screen.getByLabelText("操作原因"), "计划维护");
  await user.click(screen.getByRole("button", { name: "确认" }));
  expect(await screen.findByText(/派发设置已保存/)).toBeVisible();
  expect(mutations).toEqual([{ paused: true, reason: "计划维护" }]);
});

it("discards pending private detail and cancels its request when switching classes", async () => {
  let finish!: (r: Response) => void;
  let detailSignal: AbortSignal | undefined;
  vi.stubGlobal("fetch", vi.fn(async (url: string, init: RequestInit) => {
    if (url.includes("/service")) return response(service);
    if (url.includes("/submissions/")) { detailSignal = init.signal as AbortSignal; return new Promise<Response>(resolve => { finish = resolve; }); }
    return response({ items: url.includes("classroom_id=1&") ? [row] : [], total: 1 });
  }));
  const user = userEvent.setup();
  const { rerender } = render(<Judge classroomId={1} canManage={false} />);
  await user.click(await screen.findByRole("button", { name: /查看 小林 的提交/ }));
  rerender(<Judge classroomId={2} canManage={false} />);
  expect(detailSignal?.aborted).toBe(true);
  await act(() => finish(response(detail)));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(screen.queryByText("print(input())")).not.toBeInTheDocument();
  expect(await screen.findByText("没有匹配的提交")).toBeVisible();
});

it("shows request errors and supports a refresh", async () => {
  let fail = true;
  vi.stubGlobal("fetch", vi.fn(async (url: string) => fail ? new Response(JSON.stringify({ detail: "数据库暂不可用" }), { status: 503 }) : response(url.includes("/service") ? service : { items: [], total: 0 })));
  const user = userEvent.setup();
  render(<Judge classroomId={1} canManage={false} />);
  expect(await screen.findByRole("alert")).toHaveTextContent("数据库暂不可用");
  fail = false;
  await user.click(screen.getByRole("button", { name: "刷新" }));
  expect(await screen.findByText("没有匹配的提交")).toBeVisible();
});
