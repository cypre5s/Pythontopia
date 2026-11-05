import { describe, expect, it } from "vitest";
import town from "../public/assets/maps/town_hub.json";
import { mapCameraBounds, mapRenderBounds, withinWalkBounds, worldConfig, type WorldMapConfig } from "../src/game/config/worldConfig";
import { mapPreviewLayout, mapTextureUrls, parseRenderMap, resolveMapAsset, tileSource } from "../src/game/config/worldRender";

describe("shared authored map rendering and camera geometry", () => {
  it("supports a logical-map crop while keeping full exterior bounds as the backdrop default", () => {
    const full = mapRenderBounds(worldConfig.maps.town_hub!);
    const backdrop = mapPreviewLayout("town_hub", full.width * 16, full.height * 16);
    expect(backdrop.bounds).toEqual(full);
    expect(backdrop.scale).toBe(1);
    expect(backdrop.offsetX).toBe(-full.x * 16);
    expect(backdrop.offsetY).toBe(-full.y * 16);
    const logical = { x: 0, y: 0, width: 50, height: 40 };
    expect(mapPreviewLayout("town_hub", 800, 640, logical)).toEqual({ bounds: logical, scale: 1, offsetX: 0, offsetY: 0 });
  });
  it("normalizes existing prop sprites and tileset frames without procedural room artwork", () => {
    const map = parseRenderMap(town, "/assets/maps/town_hub.json");
    expect(map.collision).toHaveLength(50 * 40);
    expect(map.props.some(prop => prop.name === "building_library")).toBe(true);
    const fence = map.props.find(prop => prop.name.startsWith("low_path_fence"));
    expect(fence?.textureUrl).toBe("/assets/pixel/tilesets/super-retro-world/town_tiles.png");
    expect(fence?.frame).toEqual(expect.objectContaining({ width: 16, height: 16 }));
    expect(mapTextureUrls(map)).toContain("/assets/pixel/tilesets/super-retro-world/town_tiles.png");
  });
  it("supports multiple source sheets, negative scenery layers and cropped furniture objects", () => {
    const raw = { width: 2, height: 2, tilewidth: 16, tileheight: 16,
      tilesets: [
        { firstgid: 1, name: "first", image: "../pixel/first.png", tilewidth: 16, tileheight: 16, columns: 2, tilecount: 4 },
        { firstgid: 5, name: "second", image: "../pixel/second.png", tilewidth: 16, tileheight: 16, columns: 2, tilecount: 4 },
      ], layers: [
        { name: "scenery", type: "tilelayer", x: -1, y: -1, width: 4, height: 4, data: Array(16).fill(1) },
        { name: "ground", type: "tilelayer", width: 2, height: 2, data: [1, 5, 0, 2] },
        { name: "collision", type: "tilelayer", width: 2, height: 2, visible: false, data: [1, 0, 0, 1] },
        { name: "furniture", type: "objectgroup", objects: [{ name: "shelf", x: 16, y: 16, width: 16, height: 16,
          properties: [{ name: "texture", value: "/assets/pixel/second.png" }, { name: "frame_x", value: 32 }, { name: "frame_y", value: 48 },
            { name: "frame_width", value: 32 }, { name: "frame_height", value: 48 }] }] },
      ] };
    const map = parseRenderMap(raw, "/assets/maps/room.json");
    expect(map.tileLayers[0]).toEqual(expect.objectContaining({ x: -16, y: -16, width: 4, height: 4 }));
    expect(tileSource(map, 5)).toEqual(expect.objectContaining({ imageUrl: "/assets/pixel/second.png", frame: { x: 0, y: 0, width: 16, height: 16 } }));
    expect(map.props[0]).toEqual(expect.objectContaining({ x: 24, y: 32, frame: { x: 32, y: 48, width: 32, height: 48 } }));
    expect(map.collision).toEqual([1, 0, 0, 1]);
  });
  it("keeps room cameras unbounded while scenery never enlarges the walkable map", () => {
    const room: WorldMapConfig = { label: "Room", width: 18, height: 14, renderer: "tiled", buildings: [], camera_mode: "centered",
      walk_bounds: { x: 0, y: 0, width: 18, height: 14 }, render_bounds: { x: 0, y: 0, width: 18, height: 14 } };
    expect(mapCameraBounds(room)).toBeNull();
    const outdoor: WorldMapConfig = { ...room, width: 50, height: 40, camera_mode: "bounded",
      walk_bounds: { x: 0, y: 0, width: 50, height: 40 }, render_bounds: { x: -18, y: -14, width: 86, height: 68 } };
    expect(mapCameraBounds(outdoor)).toEqual(mapRenderBounds(outdoor));
    expect(withinWalkBounds(outdoor, -1, 20)).toBe(false);
    expect(withinWalkBounds(outdoor, 50, 20)).toBe(false);
    expect(withinWalkBounds(outdoor, 49, 39)).toBe(true);
  });
  it("rejects external artwork URLs and incomplete collision data", () => {
    expect(() => resolveMapAsset("/assets/maps/map.json", "https://example.invalid/image.png")).toThrow();
    expect(() => parseRenderMap({ ...town, layers: town.layers.filter(layer => layer.name !== "collision") }, "/assets/maps/town_hub.json")).toThrow(/碰撞/);
  });
});
