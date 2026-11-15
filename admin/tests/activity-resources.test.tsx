import { useState } from "react";
import { afterEach, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ResourceEditor } from "../src/features/activities/resource-editor";
import type { ActivityResource, Material } from "../src/lib/types";

afterEach(() => vi.unstubAllGlobals());

it("configures independent required resources, key pages and video requirements in order", async () => {
  const changed = vi.fn();
  const materials = [{ id: 11, title: "课件", kind: "pdf", chapter_ids: [1], status: "ready" },
    { id: 12, title: "演示视频", kind: "video", chapter_ids: [1], status: "ready" }] as Material[];
  vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ pages: [{ id: 101, page_no: 1, title: "重点概念" }] }), { headers: { "Content-Type": "application/json" } })));
  function Form() {
    const [rows, setRows] = useState<ActivityResource[]>([]);
    return <ResourceEditor classroomId={7} chapterId={1} materials={materials} resources={rows} onChange={next => { setRows(next); changed(next); }} />;
  }
  const user = userEvent.setup();
  render(<Form />);
  for (const name of ["课件", "演示视频"]) {
    await user.click(screen.getByRole("combobox", { name: "添加活动资料" }));
    await user.click(await screen.findByRole("option", { name }));
    await user.click(screen.getByRole("button", { name: "添加资料" }));
  }
  await user.click(screen.getByRole("combobox", { name: "资料阅读要求" }));
  await user.click(await screen.findByRole("option", { name: "阅读关键页" }));
  await user.click(await screen.findByRole("checkbox", { name: "1. 重点概念" }));
  await user.clear(screen.getByRole("spinbutton", { name: "最低有效观看秒数" }));
  await user.type(screen.getByRole("spinbutton", { name: "最低有效观看秒数" }), "15");
  await user.click(screen.getAllByRole("checkbox", { name: "必读" })[1]);
  await user.click(screen.getByRole("button", { name: "上移资料2" }));
  await waitFor(() => expect(changed).toHaveBeenLastCalledWith([
    { material_id: 12, required: false, learning: { video: { minimum_seconds: 15 } } },
    { material_id: 11, required: true, learning: { reading: { scope: "pages", page_ids: [101] } } },
  ]));
  expect(screen.getByText("同时设置时长和片段时，两项都要达成。拖动跳过不计入有效观看。")).toBeVisible();
});
