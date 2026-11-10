import { afterEach, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Activities } from "../src/pages/activities";

const json = (value: unknown) => new Response(JSON.stringify(value), { headers: { "Content-Type": "application/json" } });
afterEach(() => vi.unstubAllGlobals());

it("selects knowledge points by chapter and preserves the other chapter's selections on save", async () => {
  const update = vi.fn();
  const course = {
    chapters: [
      { id: 1, title: "第一章", order_index: 1, owner_id: null },
      { id: 2, title: "第二章", order_index: 2, owner_id: null },
      { id: 3, title: "第三章", order_index: 3, owner_id: null },
    ],
    knowledge_points: [
      { id: 11, chapter_id: 1, title: "变量", content: {}, owner_id: null },
      { id: 22, chapter_id: 2, title: "循环", content: {}, owner_id: null },
      { id: 22, chapter_id: 2, title: "循环", content: {}, owner_id: null },
    ],
    npcs: [{ key: "teacher", name: "老师" }],
    activities: [{ id: "review", chapter_id: 1, title: "综合复习", kind: "practice", material_id: null, npc_key: "teacher", assistant_allowed: true, learning: {}, knowledge_point_ids: [11], question_ids: [], owner_id: null }],
  };
  vi.stubGlobal("fetch", vi.fn(async (url: string, init: RequestInit) => {
    if (init.method === "PUT") { update(url, JSON.parse(init.body as string)); return json({}); }
    return json(url.includes("/teacher/courses") ? course : []);
  }));
  const user = userEvent.setup();
  render(<Activities classroomId={7} />);
  await user.click(await screen.findByRole("button", { name: "编辑综合复习" }));
  expect(screen.getByRole("checkbox", { name: "变量" })).toBeChecked();
  expect(screen.queryByRole("checkbox", { name: "循环" })).not.toBeInTheDocument();
  await user.click(screen.getByRole("combobox", { name: "知识点所属章节" }));
  await user.click(await screen.findByRole("option", { name: "第二章" }));
  expect(screen.getAllByRole("checkbox", { name: "循环" })).toHaveLength(1);
  await user.click(screen.getByRole("checkbox", { name: "循环" }));
  expect(within(screen.getByRole("group", { name: "已选知识点" })).getByText("变量")).toBeInTheDocument();
  expect(screen.getByText("已选 2 / 65")).toBeInTheDocument();
  await user.click(screen.getByRole("combobox", { name: "知识点所属章节" }));
  await user.click(await screen.findByRole("option", { name: "第三章" }));
  expect(screen.getByText("本章暂无知识点，可选择其他章节。")).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "保存" }));
  await waitFor(() => expect(update).toHaveBeenCalledWith("/api/teacher/activities/review?classroom_id=7", expect.objectContaining({ knowledge_point_ids: [11, 22] })));
});
