import { useState } from "react";
import { expect, it } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NodeEditor } from "../src/components/node-editor";
import type { Catalog, Flow, StoryNode } from "../src/lib/authoring";

const catalog: Catalog = { maps: [], buildings: [], chapters: [], activities: [], npcs: [], quests: [] };
const flow: Flow = { id: "welcome", title: "欢迎", chapter_id: null, quest_id: null,
  content: { schema_version: 1, mode: "public", bindings: {}, entries: {}, nodes: [] } };
function Editor({ initial }: { initial: string }) {
  const [node, setNode] = useState<StoryNode>({ id: "greeting", type: "dialogue", text: initial, next: "end" });
  return <>
    <NodeEditor node={node} flow={flow} catalog={catalog} onChange={setNode}
      onFlowChange={() => {}} onDelete={() => {}} onConvertActionText={() => {}} />
    <output data-testid="saved-node">{JSON.stringify(node)}</output>
  </>;
}

it("loads existing rounds and preserves their order through edits, additions, removal and reopening", async () => {
  const user = userEvent.setup();
  const editor = render(<Editor initial={"欢迎来到小镇。\n\n图书馆正在整理展览。"} />);
  expect(screen.getByLabelText("对白 1")).toHaveValue("欢迎来到小镇。");
  expect(screen.getByLabelText("对白 2")).toHaveValue("图书馆正在整理展览。");
  await user.clear(screen.getByLabelText("对白 2"));
  await user.type(screen.getByLabelText("对白 2"), "来看看计算工具的故事。");
  await user.click(screen.getByRole("button", { name: "添加对白" }));
  await user.type(screen.getByLabelText("对白 3"), "准备好了再去练习场。");
  await user.click(screen.getByRole("button", { name: "删除对白 1" }));
  const saved = JSON.parse(screen.getByTestId("saved-node").textContent!);
  expect(saved).toEqual({ id: "greeting", type: "dialogue", next: "end",
    text: "来看看计算工具的故事。\n\n准备好了再去练习场。" });
  editor.unmount();
  render(<Editor initial={saved.text} />);
  expect(screen.getByLabelText("对白 1")).toHaveValue("来看看计算工具的故事。");
  expect(screen.getByLabelText("对白 2")).toHaveValue("准备好了再去练习场。");
  await user.click(screen.getByRole("button", { name: "删除对白 2" }));
  expect(screen.getByRole("button", { name: "删除对白 1" })).toBeDisabled();
});

it("keeps pasted paragraphs in one round and applies the total node text limit", () => {
  render(<Editor initial={"甲".repeat(7990) + "\n\n乙"} />);
  expect(screen.getByLabelText("对白 2")).toHaveAttribute("maxlength", "8");
  fireEvent.change(screen.getByLabelText("对白 2"), { target: { value: "乙\n\n丙" } });
  expect(screen.getByLabelText("对白 2")).toHaveValue("乙\n丙");
  expect(screen.queryByLabelText("对白 3")).not.toBeInTheDocument();
});
