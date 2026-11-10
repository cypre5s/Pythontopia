import { it, expect, vi, afterEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { Authoring } from "../src/pages/authoring";
import type { Authoring as Document } from "../src/lib/authoring";
const base: Document & { students: { id: number; name: string }[] } = {
  revision: 7,
  catalog: {
    maps: [
      {
        key: "town",
        label: "小镇",
        width: 30,
        height: 20,
        walk_bounds: {},
        objects: [
          {
            id: "shelf",
            label: "图书架",
            points: [{ map_key: "town", tile_x: 4, tile_y: 5 }],
          },
        ],
      },
    ],
    buildings: [
      { id: "library", label: "图书馆", map_key: "town", has_interior: true },
    ],
    chapters: [{ id: "ch1", title: "第一章" }],
    activities: [{ id: "a1", title: "阅读资料", kind: "lesson" }],
    npcs: [{ id: "guide", name: "向导" }],
    quests: [],
  },
  flows: [
    {
      id: "public",
      title: "公共欢迎",
      chapter_id: null,
      quest_id: null,
      content: {
        schema_version: 1,
        mode: "public",
        bindings: {},
        entries: { guide: [{ when: { always: true }, node: "end" }] },
        nodes: [{ id: "end", type: "end" }],
      },
    },
  ],
  quests: [],
  configuration: { world: {}, buildings: {}, editor: {}, assistant: {} },
  students: [{ id: 3, name: "学生甲" }],
};
function json(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
afterEach(() => vi.unstubAllGlobals());
// Browser tests cover measured nodes and drag handles. These tests exercise editor forms.
vi.mock("../src/components/flow-canvas", () => ({
  FlowCanvas: ({ renderWorkspace, paletteItems, onAddNode }: {
    renderWorkspace: (slots: { palette: ReactNode; canvas: ReactNode }) => ReactNode;
    paletteItems?: { kind: string; label: string }[];
    onAddNode: (kind: string, point: { x: number; y: number }) => void;
  }) => renderWorkspace({
    palette: paletteItems?.map(({ kind, label }) => (
      <button key={kind} onClick={() => onAddNode(kind, { x: 10, y: 20 })}>{label}</button>
    )),
    canvas: null,
  }),
}));
async function openSettings(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole("button", { name: "流程设置与入口" }));
  return screen.findByLabelText("流程名称");
}
async function selectOption(user: ReturnType<typeof userEvent.setup>, name: string, option: string) {
  await user.click(screen.getByRole("combobox", { name }));
  await user.click(await screen.findByRole("option", { name: option }));
}
it("locks authored fields while validation is pending and restores the same draft after rejection", async () => {
  let finish!: (response: Response) => void;
  vi.stubGlobal("fetch", vi.fn(async (_url: string, init: RequestInit) => {
    if (init.method === "POST") return new Promise<Response>((resolve) => { finish = resolve; });
    return json(base);
  }));
  const user = userEvent.setup();
  render(<Authoring classroomId={12} />);
  await user.clear(await openSettings(user));
  await user.type(screen.getByLabelText("流程名称"), "等待保存的草稿");
  await user.click(screen.getByRole("button", { name: "关闭" }));
  await user.click(screen.getByRole("button", { name: "保存课程" }));
  expect(screen.getByTestId("authoring-fields")).toHaveAttribute("inert");
  finish(json({ valid: false, diagnostics: [{ path: "flows", message: "请补全分支" }] }));
  await screen.findByText("请补全分支");
  expect(screen.getByTestId("authoring-fields")).not.toHaveAttribute("inert");
  expect(await openSettings(user)).toHaveValue("等待保存的草稿");
});
it("preserves editable drafts when server graph validation rejects a save", async () => {
  const saves = vi.fn();
  vi.stubGlobal(
    "fetch",
    vi.fn(async (_url: string, init: RequestInit) => {
      if (init.method === "POST")
        return json({
          valid: false,
          diagnostics: [{ path: "flows", message: "演出包含不可达节点" }],
        });
      if (init.method === "PUT") {
        saves();
        return json({ revision: 8 });
      }
      return json(base);
    }),
  );
  const user = userEvent.setup();
  render(<Authoring classroomId={12} />);
  await user.clear(await openSettings(user));
  await user.type(screen.getByLabelText("流程名称"), "修改后的流程");
  await user.click(screen.getByRole("button", { name: "关闭" }));
  await user.click(screen.getByRole("button", { name: "保存课程" }));
  await screen.findByText("演出包含不可达节点");
  expect(saves).not.toHaveBeenCalled();
  expect(await openSettings(user)).toHaveValue("修改后的流程");
});
it("confirms discard and restores the latest saved revision without another write", async () => {
  const writes = vi.fn();
  vi.stubGlobal("fetch", vi.fn(async (_url: string, init: RequestInit) => {
    if (init.method === "POST") return json({ valid: true, diagnostics: [] });
    if (init.method === "PUT") {
      writes(JSON.parse(init.body as string));
      return json({ revision: 8 });
    }
    return json(base);
  }));
  const user = userEvent.setup();
  render(<Authoring classroomId={12} />);
  await user.clear(await openSettings(user));
  await user.type(screen.getByLabelText("流程名称"), "最新保存");
  await user.click(screen.getByRole("button", { name: "关闭" }));
  await user.click(screen.getByRole("button", { name: "保存课程" }));
  await screen.findByText("课程已保存。");

  await user.clear(await openSettings(user));
  await user.type(screen.getByLabelText("流程名称"), "临时草稿");
  await user.click(screen.getByRole("button", { name: "关闭" }));
  await user.click(screen.getByRole("button", { name: "取消编辑" }));
  await user.click(screen.getByRole("button", { name: "继续编辑" }));
  expect(await openSettings(user)).toHaveValue("临时草稿");
  await user.click(screen.getByRole("button", { name: "关闭" }));
  await user.click(screen.getByRole("button", { name: "取消编辑" }));
  await user.click(screen.getByRole("button", { name: "放弃修改" }));
  expect(screen.getByRole("button", { name: "保存课程" })).toBeDisabled();
  expect(await openSettings(user)).toHaveValue("最新保存");
  expect(writes).toHaveBeenCalledTimes(1);
});
it("creates map interactions using object references and binds them to existing flow entries", async () => {
  let saved: Document | undefined;
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, init: RequestInit) => {
      if (url.endsWith("/validate"))
        return json({ valid: true, diagnostics: [] });
      if (init.method === "PUT") {
        saved = JSON.parse(init.body as string);
        return json({ revision: 8 });
      }
      return json(base);
    }),
  );
  const user = userEvent.setup();
  render(<Authoring classroomId={12} />);
  await user.click(await screen.findByRole("tab", { name: "地图交互与开放规则" }));
  await user.click(screen.getByRole("button", { name: "选择地图对象 图书架" }));
  await selectOption(user, "触发流程", "公共欢迎");
  await user.click(screen.getByRole("button", { name: "保存课程" }));
  await waitFor(() => expect(saved).toBeDefined());
  const item = saved!.configuration.world.interactions![0];
  expect(item.target).toEqual({ object_id: "shelf" });
  expect(saved!.flows[0].content.entries[`interaction:${item.id}`]).toEqual([
    { when: { always: true }, node: "end" },
  ]);
  expect(
    (saved as unknown as { expected_revision: number }).expected_revision,
  ).toBe(7);
});
it("edits quest dependencies as typed ALL and ANY data", async () => {
  let saved: Document | undefined;
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, init: RequestInit) => {
      if (url.endsWith("/validate"))
        return json({ valid: true, diagnostics: [] });
      if (init.method === "PUT") {
        saved = JSON.parse(init.body as string);
        return json({ revision: 8 });
      }
      return json(base);
    }),
  );
  const user = userEvent.setup();
  render(<Authoring classroomId={12} />);
  await user.click(await screen.findByRole("tab", { name: "任务目标与依赖" }));
  await user.click(screen.getByRole("button", { name: "新建任务" }));
  await user.click(await screen.findByRole("button", { name: "关闭属性" }));
  await user.click(screen.getByRole("button", { name: "与角色交谈" }));
  await user.click(screen.getByRole("button", { name: "完成阅读活动" }));
  await user.click(screen.getByRole("button", { name: "编辑目标属性" }));
  const dependency = await screen.findByRole("checkbox", { name: "与角色交谈" });
  expect(dependency).not.toBeChecked();
  await user.click(dependency);
  await selectOption(user, "前置依赖关系", "任一前置完成（ANY）");
  await user.click(screen.getByRole("button", { name: "关闭属性" }));
  await user.click(screen.getByRole("button", { name: "保存课程" }));
  await waitFor(() => expect(saved).toBeDefined());
  const q = saved!.quests[0];
  expect(q.definition.nodes[1].dependencies).toEqual([
    q.definition.nodes[0].id,
  ]);
  expect(q.definition.nodes[1].aggregation).toBe("ANY");
  expect(q.definition.nodes[1].activity_id).toBe("a1");
  expect(q.definition.chapter_id).toBe(q.chapter_id);
});
it("previews using an authorized real student selection without saving", async () => {
  const requests: { url: string; body: unknown }[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, init: RequestInit) => {
      if (init.method === "POST") {
        requests.push({ url, body: JSON.parse(init.body as string) });
        return json({
          selected_flow_id: "public",
          node_id: "end",
          trace: [{ node_id: "end", type: "end" }],
          stopped_at: "end",
        });
      }
      return json(base);
    }),
  );
  const user = userEvent.setup();
  render(<Authoring classroomId={12} />);
  await user.click(await screen.findByRole("button", { name: "预览" }));
  await user.click(screen.getByRole("button", { name: "执行只读预览" }));
  await screen.findByText("预览停止：end");
  expect(requests).toHaveLength(1);
  expect(requests[0].url).toBe("/api/teacher/classrooms/12/authoring/preview");
  expect(requests[0].body).toMatchObject({
    student_id: 3,
    entry: "guide",
    expected_revision: 7,
  });
});
