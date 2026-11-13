import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { it, expect, vi } from "vitest";
import { MapEditor } from "../src/components/map-editor";
import type { Authoring } from "../src/lib/authoring";

vi.mock("../../shared/world/mapRender", () => ({
  paintMapPreview: vi.fn().mockRejectedValue(new Error("素材加载失败")),
}));

const data: Authoring = {
  npcs: [],
  revision: 1,
  catalog: {
    maps: [
      { key: "town_hub", label: "小镇", width: 50, height: 40, walk_bounds: {}, objects: [] },
      { key: "library", label: "图书馆", width: 18, height: 14, walk_bounds: {}, objects: [] },
    ],
    buildings: [], chapters: [], activities: [], npcs: [], quests: [],
  },
  flows: [],
  quests: [],
  configuration: {
    world: {
      interactions: [
        { id: "east-training", label: "东侧练习场", prompt: "前往练习", trigger: "click", map_key: "town_hub", target: { object_id: "east-training" } },
        { id: "bookshelf", label: "阅览区", prompt: "查看", trigger: "click", map_key: "library", target: { object_id: "bookshelf" } },
      ],
    },
    buildings: {}, editor: {}, assistant: {},
  },
};

it("opens an existing interaction without a condition even if the map art fails to load", async () => {
  const onChange = vi.fn();
  render(<MapEditor data={data} onChange={onChange} />);
  expect(await screen.findByRole("status")).toHaveTextContent("底图加载失败");
  await userEvent.setup().click(screen.getByRole("button", { name: "东侧练习场" }));
  const inspector = await screen.findByRole("dialog", { name: "交互入口" });
  expect(within(inspector).getByLabelText("交互名称")).toHaveValue("东侧练习场");
  expect(within(inspector).getByLabelText("条件类型")).toHaveTextContent("总是");
  expect(onChange).not.toHaveBeenCalled();
});

it("locates a reported interaction on another map without editing it", async () => {
  const onChange = vi.fn();
  render(<MapEditor data={data} onChange={onChange} focusTarget={{ kind: "interaction", id: "bookshelf" }} />);
  const inspector = await screen.findByRole("dialog", { name: "交互入口" });
  expect(within(inspector).getByLabelText("交互名称")).toHaveValue("阅览区");
  expect(screen.getByLabelText("选择地图", { exact: true })).toHaveTextContent("图书馆");
  expect(onChange).not.toHaveBeenCalled();
});
