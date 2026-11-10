import { describe, expect, it } from "vitest";
import { enteredInteractions } from "../src/game/interactionRegions";
import { mapTargets, portalAvailable, resolveWorldTargets, worldConfig } from "../src/game/config/worldConfig";
import type { CourseInteraction } from "../src/shared/types";

const region: CourseInteraction = {
  id: "welcome-garden", label: "花园", prompt: "进入花园", trigger: "enter", map_key: "town_hub",
  target: { region: { x: 20, y: 30, width: 3, height: 2 } },
  points: [{ map_key: "town_hub", tile_x: 20, tile_y: 30 }], radius: 2,
};
describe("classroom-authored world interactions", () => {
  it("uses only the selected classroom interactions without mutating fixed geometry", () => {
    const first = { interactions: [region], building_access: {} };
    expect(mapTargets("town_hub", first).some(item => item.id === region.id)).toBe(true);
    expect(mapTargets("town_hub", { interactions: [] }).some(item => item.id === region.id)).toBe(false);
    expect(mapTargets("library", { interactions: [] }).some(item => item.id === "library_shelf")).toBe(false);
    expect(worldConfig.interactions[region.id]).toBeUndefined();
  });
  it("resolves an authored object binding by its fixed object ID", () => {
    const binding: CourseInteraction = { ...region, id: "shelf-flow", trigger: "click",
      map_key: "library", target: { object_id: "library_shelf" },
      points: [{ map_key: "library", tile_x: 15, tile_y: 8 }] };
    expect(resolveWorldTargets("library_shelf", [], { interactions: [binding] })[0]?.id).toBe("shelf-flow");
    const entry: CourseInteraction = { ...binding, id: "entrance-flow", map_key: "town_hub", target: { object_id: "library" },
      points: [{ map_key: "town_hub", tile_x: 8, tile_y: 22 }] };
    expect(resolveWorldTargets("library_door", [], { interactions: [entry] })[0]?.id).toBe("entrance-flow");
  });
  it("uses server building permissions and always leaves the exit available", () => {
    const door = worldConfig.portals.find(item => item.building_id === "library")!;
    const exit = worldConfig.portals.find(item => item.id === "library_exit")!;
    const access = { enabled: true, unlocked: false, can_enter: false, blocked_message: "先完成活动" };
    expect(portalAvailable(door, ["library_unlocked"], { building_access: { library: access } })).toBe(false);
    expect(portalAvailable(door, [], { building_access: { library: { ...access, can_enter: true } } })).toBe(true);
    expect(portalAvailable(exit, [], { building_access: {} })).toBe(true);
  });
  it("fires region entry once, resets on leaving and respects the exclusive outer edge", () => {
    const position = { map_key: "town_hub", tile_x: 20, tile_y: 30, direction: "down" };
    const first = enteredInteractions(position, [region], new Set());
    expect(first.entered).toEqual([region.id]);
    expect(enteredInteractions(position, [region], first.inside).entered).toEqual([]);
    const outside = enteredInteractions({ ...position, tile_x: 23 }, [region], first.inside);
    expect(outside.inside.size).toBe(0);
    expect(enteredInteractions(position, [region], outside.inside).entered).toEqual([region.id]);
    expect(enteredInteractions({ ...position, map_key: "library" }, [region], new Set()).entered).toEqual([]);
  });
});
