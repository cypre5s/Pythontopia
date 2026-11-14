import { afterEach, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Activities } from "../src/pages/activities";

const json = (value: unknown) => new Response(JSON.stringify(value), { headers: { "Content-Type": "application/json" } });
afterEach(() => vi.unstubAllGlobals());

it("previews cascading impact, requires two confirmations, and refreshes after deletion", async () => {
  let removed = false;
  const calls: unknown[] = [];
  const activity = { id: "review", chapter_id: 1, title: "综合复习", kind: "practice", question_ids: [] };
  vi.stubGlobal("fetch", vi.fn(async (url: string, init: RequestInit) => {
    if (url.endsWith("/content-deletions/preview")) return json({ id: "p1", confirmation: "综合复习", remove: [{kind: "activity", id: "review", title: "综合复习"}], detach: [], preserve: [], blockers: [], affected_students: 0, ending_sessions: 0, history_policy: "学生历史记录保留" });
    if (url.endsWith("/p1/confirm")) { calls.push(JSON.parse(init.body as string)); removed = true; return json({ok: true}); }
    return json(url.includes("/teacher/courses") ? { chapters: [{id: 1, title: "第一章"}], npcs: [], knowledge_points: [], activities: removed ? [] : [activity] } : []);
  }));
  const user = userEvent.setup();
  render(<Activities classroomId={7} />);
  await user.click(await screen.findByRole("button", { name: "删除综合复习" }));
  expect(await within(screen.getByRole("dialog")).findByText("一起删除（1）")).toBeVisible();
  await user.click(screen.getByRole("button", {name: "取消"}));
  expect(calls).toHaveLength(0);
  await user.click(screen.getByRole("button", {name: "删除综合复习"}));
  await waitFor(() => expect(screen.getByRole("button", {name: "继续确认"})).toBeEnabled());
  await user.click(screen.getByRole("button", {name: "继续确认"}));
  expect(screen.getByRole("button", {name: "确认级联删除"})).toBeDisabled();
  await user.type(screen.getByRole("textbox"), "综合复习");
  expect(screen.getByRole("button", {name: "确认级联删除"})).toBeDisabled();
  await user.click(screen.getByRole("checkbox", {name: "我已核对删除范围，确认保留学生历史记录"}));
  await user.click(screen.getByRole("button", {name: "确认级联删除"}));
  expect(await screen.findByText("尚无学习活动")).toBeVisible();
  expect(calls).toEqual([{classroom_id: 7, confirmation: "综合复习", confirmed: true}]);
});

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
