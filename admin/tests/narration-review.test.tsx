import { it, expect, vi, afterEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NarrationReview } from "../src/components/narration-review";

afterEach(() => vi.unstubAllGlobals());

function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

const list = {
  material_id: 7,
  title: "第 1 讲",
  coverage: { pages: 2, covered: 1, missing: 1, draft: 1, accepted: 0, rejected: 0, unavailable: 0, stale: 0 },
  pages: [
    {
      page_no: 1,
      title: "划重点",
      has_text: true,
      source_chars: 12,
      source_text: "监督学习的定义",
      narration: {
        text: "这一页先看监督学习的定义。",
        bridge_in: "",
        target_seconds: 90,
        status: "draft",
        source: "ai_generated",
        model: "deepseek-flash",
        image_used: true,
        anchors: ["监督学习"],
        edited_by_teacher: false,
        reviewed_at: null,
        stale_reason: null,
      },
    },
    {
      page_no: 2,
      title: "空页",
      has_text: false,
      source_chars: 0,
      source_text: "",
      narration: null,
    },
  ],
};

function stub(calls: { url: string; method: string; body?: unknown }[], overrides: Record<string, unknown> = {}) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, init: RequestInit = {}) => {
      const method = init.method ?? "GET";
      if (method !== "GET") calls.push({ url, method, body: init.body ? JSON.parse(init.body as string) : undefined });
      if (url.endsWith("/quota")) return json({ used_today: 4, daily_limit: 300, available: true });
      if (url.endsWith("/audio"))
        return json({
          available: true,
          voice: "Meilin",
          model: "zonos3-mini",
          pages: [{ page_no: 1, status: "draft", ready: false, seconds: null }],
        });
      if (url.endsWith("/narrations")) return json(overrides.list ?? list);
      if (url.endsWith("/calibrate"))
        return json({ id: 1, windows: 1, pages: 2, summary: "整体连贯", terms: ["监督学习"], issues: [] });
      return json({ page_no: 1, status: "draft", reused: false, model: "deepseek-flash" });
    }),
  );
}

it("shows coverage and the generated draft next to the page source", async () => {
  stub([]);
  render(<NarrationReview classroomId={3} materialId={7} title="第 1 讲" onClose={() => {}} />);
  await waitFor(() => expect(screen.getByText("共 2 页")).toBeTruthy());
  expect(screen.getByText("已生成 1")).toBeTruthy();
  expect(screen.getByText("待审 1")).toBeTruthy();
  expect(screen.getByText(/今日已生成 4 \/ 300/)).toBeTruthy();
  expect(screen.getByText("监督学习的定义")).toBeTruthy();
  await waitFor(() => expect(screen.getByLabelText("讲解稿")).toHaveValue("这一页先看监督学习的定义。"));
});

it("accepting is blocked until an edited draft is saved", async () => {
  const calls: { url: string; method: string; body?: unknown }[] = [];
  stub(calls);
  const user = userEvent.setup();
  render(<NarrationReview classroomId={3} materialId={7} title="第 1 讲" onClose={() => {}} />);
  await waitFor(() => expect(screen.getByLabelText("讲解稿")).toBeTruthy());

  const accept = screen.getByRole("button", { name: /接受/ });
  await waitFor(() => expect(accept).not.toBeDisabled());
  await user.type(screen.getByLabelText("讲解稿"), "再看标签。");
  expect(accept).toBeDisabled();

  await user.click(screen.getByRole("button", { name: /保存修改/ }));
  await waitFor(() => expect(calls.some((call) => call.method === "PUT")).toBe(true));
  const saved = calls.find((call) => call.method === "PUT")!;
  expect(saved.url).toContain("/teacher/classrooms/3/materials/7/narrations/1");
  expect(saved.body).toEqual({ text: "这一页先看监督学习的定义。再看标签。" });
});

it("keeps audio generation disabled until the page is accepted", async () => {
  stub([]);
  render(<NarrationReview classroomId={3} materialId={7} title="第 1 讲" onClose={() => {}} />);
  await waitFor(() => expect(screen.getByText("音色 Meilin · zonos3-mini")).toBeTruthy());
  const audio = screen.getByRole("button", { name: /生成音频/ });
  expect(audio).toBeDisabled();
  expect(screen.getByRole("button", { name: /试听/ })).toBeDisabled();
});

it("generate and calibrate hit the scoped endpoints", async () => {
  const calls: { url: string; method: string; body?: unknown }[] = [];
  stub(calls);
  const user = userEvent.setup();
  render(<NarrationReview classroomId={3} materialId={7} title="第 1 讲" onClose={() => {}} />);
  await waitFor(() => expect(screen.getByRole("button", { name: /生成本页/ })).toBeTruthy());
  await user.click(screen.getByRole("button", { name: /生成本页/ }));
  await waitFor(() => expect(calls.some((call) => call.url.endsWith("/1/generate"))).toBe(true));

  await user.click(screen.getByRole("button", { name: /全篇校准/ }));
  await waitFor(() => expect(calls.some((call) => call.url.endsWith("/calibrate"))).toBe(true));
});
