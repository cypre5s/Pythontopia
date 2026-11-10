import { describe, expect, it } from "vitest";
import tiledTown from "../public/assets/maps/town_hub.json";
import { buildingMessage, displayQuestTitle, getWorldMap, mapTargets, nearbyWorldTarget, portalRoute, reachableApproach, reachableTargetApproach, resolveWorldTarget, resolveWorldTargets, worldConfig, worldTargetHint, type WorldDefinition, type WorldTarget } from "../src/game/config/worldConfig";
const courseAccess = (can_enter: boolean) => ({ building_access: { library: { enabled: true, unlocked: can_enter, can_enter, blocked_message: "课程尚未开放" } } });

describe("configured world places and portals", () => {
  it("names and reaches every real house, including prop-based cottages", () => {
    const map = getWorldMap("town_hub");
    const art = tiledTown.layers.flatMap(layer => "objects" in layer ? layer.objects : []);
    const collision = tiledTown.layers.find(layer => layer.name === "collision")!;
    const blocked = (x: number, y: number) => "data" in collision && Boolean(collision.data[y * map.width + x]);
    expect(map.buildings).toHaveLength(11);
    for (const building of map.buildings) {
      expect(building.label).toMatch(/[\u4e00-\u9fff]/);
      expect(art.some(object => object.name === building.object_name)).toBe(true);
      const target = resolveWorldTarget(building.id, []);
      expect(target).toBeDefined();
      expect(reachableApproach({ x: worldConfig.default_spawn.tile_x, y: worldConfig.default_spawn.tile_y }, target!, 2, map.width, map.height, blocked)).not.toBeNull();
    }
    const cottage = map.buildings.find(building => building.object_name.startsWith("cottage_"))!;
    expect(resolveWorldTarget(cottage.object_name, [])?.id).toBe(cottage.id);
    expect(buildingMessage(cottage)).not.toContain("暂未开放");
  });
  it("uses the configured library portal once and exposes honest nearby hints", () => {
    const targets = mapTargets("town_hub");
    expect(targets.filter(item => item.x === 8 && item.y === 22)).toHaveLength(1);
    const entrance = resolveWorldTarget("library", [])!;
    expect(entrance.portal?.id).toBe("library_door");
    expect(worldTargetHint(entrance, [], courseAccess(false))).toBe("");
    expect(worldTargetHint(entrance, [], courseAccess(true))).toBe("进入");
    expect(resolveWorldTarget("practice_plaza", [])).toEqual(expect.objectContaining({ x: 38, y: 24, kind: "object" }));
    expect(worldTargetHint(resolveWorldTarget("mayor_hall", [])!, [])).toBe("");
  });
  it("plans both directions through configured portals and honors entry requirements", () => {
    expect(portalRoute("town_hub", "library", [], worldConfig, courseAccess(false))).toBeNull();
    expect(portalRoute("town_hub", "library", [], worldConfig, courseAccess(true))?.map(item => item.id)).toEqual(["library_door"]);
    expect(portalRoute("library", "town_hub", [])?.map(item => item.id)).toEqual(["library_exit"]);
    expect(portalRoute("library", "library", [])).toEqual([]);
  });
  it("routes through several arbitrary maps without special map-name branches", () => {
    const base = worldConfig.portals[0]!;
    const config: WorldDefinition = { ...worldConfig, portals: [
      { ...base, id: "a-b", from: { ...base.from, map_key: "a" }, to: { ...base.to, map_key: "b" }, required_flags: [] },
      { ...base, id: "b-c", from: { ...base.from, map_key: "b" }, to: { ...base.to, map_key: "c" }, required_flags: ["c_open"] },
      { ...base, id: "b-a", from: { ...base.from, map_key: "b" }, to: { ...base.to, map_key: "a" }, required_flags: [] },
    ] };
    expect(portalRoute("a", "c", [], config)).toBeNull();
    expect(portalRoute("a", "c", ["c_open"], config)?.map(item => item.id)).toEqual(["a-b", "b-c"]);
  });
  it("chooses a reachable approach rather than a nearer isolated tile", () => {
    const walls = new Set(["3,1", "3,3", "2,2", "4,2"]);
    const route = reachableApproach({ x: 0, y: 2 }, { x: 4, y: 2 }, 1, 6, 5, (x, y) => walls.has(`${x},${y}`));
    expect(route).not.toBeNull();
    expect(route!.point).not.toEqual({ x: 3, y: 2 });
    expect(route!.path.length).toBeGreaterThan(0);
    expect(reachableApproach({ x: 0, y: 0 }, { x: 3, y: 3 }, 1, 4, 4, () => true)).toBeNull();
  });
  it("keeps one semantic shelf entry while every physical shelf has a usable anchor", () => {
    const entries = mapTargets("library").filter(target => target.id === "library_shelf");
    expect(entries).toHaveLength(1);
    expect(entries[0]?.label).toBe("图书馆书架");
    expect(entries[0]?.points).toHaveLength(10);
    const shelves = resolveWorldTargets("library_shelf", []);
    expect(shelves).toHaveLength(10);
    expect(shelves).toContainEqual(expect.objectContaining({ x: 4, y: 4 }));
    expect(shelves).toContainEqual(expect.objectContaining({ x: 15, y: 8 }));
    const guide = reachableTargetApproach({ x: 4, y: 4 }, shelves, 18, 14, () => false);
    expect(guide?.path).toHaveLength(0);
    const clicked = reachableTargetApproach({ x: 4, y: 4 }, shelves, 18, 14, () => false, { x: 15, y: 8 });
    expect(clicked?.target).toEqual(expect.objectContaining({ x: 15, y: 8 }));
    expect(clicked?.path.length).toBeGreaterThan(0);
  });
  it("updates compact nearby actions at fractional moving positions and excludes closed buildings", () => {
    const shelves = resolveWorldTargets("library_shelf", []);
    const within = nearbyWorldTarget({ map_key: "library", x: 15, y: 10 }, shelves, []);
    expect(worldTargetHint(within!, [])).toBe("阅读");
    expect(nearbyWorldTarget({ map_key: "library", x: 15, y: 10.01 }, shelves, [])).toBeUndefined();
    const closed = resolveWorldTarget("training", [])!;
    expect(nearbyWorldTarget({ map_key: closed.map_key, x: closed.x, y: closed.y }, [closed], [])).toBeUndefined();
    expect(nearbyWorldTarget({ map_key: closed.map_key, x: closed.x, y: closed.y }, [closed], [], closed.id)).toBeUndefined();
    const npc: WorldTarget = { id: "person", map_key: "library", x: 9, y: 5, label: "Very long name", kind: "npc", radius: 2 };
    expect(worldTargetHint(npc, [])).toBe("交谈");
    expect(worldTargetHint(resolveWorldTarget("library_exit", [])!, [])).toBe("返回");
  });
  it("renames only the displayed legacy shelf title without mutating published content", () => {
    const node = { target: "library_shelf", title: "调查西侧书架" };
    expect(displayQuestTitle(node)).toBe("调查图书馆书架");
    expect(node.title).toBe("调查西侧书架");
    expect(displayQuestTitle({ title: "西侧书架的故事" })).toBe("西侧书架的故事");
  });
});
