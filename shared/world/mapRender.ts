import { SRW_HOUSE_KEYS, SRW_PROP_GROUPS, SRW_TILE_COLUMNS, SRW_TILESET_URL } from "./srwAssets.config";
import definition from "./world.json";

export interface WorldBounds { x: number; y: number; width: number; height: number }

interface MapPreviewConfig {
  width: number;
  height: number;
  asset?: string;
  render_bounds?: WorldBounds;
}

function getMapPreviewConfig(key: string): MapPreviewConfig {
  if (!Object.hasOwn(definition.maps, key)) throw new Error("地图配置不存在，请重新进入小镇。");
  return (definition.maps as Record<string, MapPreviewConfig>)[key]!;
}

export function mapRenderBounds(map: MapPreviewConfig): WorldBounds {
  return map.render_bounds ?? { x: 0, y: 0, width: map.width, height: map.height };
}

export interface RenderFrame { x: number; y: number; width: number; height: number }
export interface RenderTileset {
  firstgid: number; name: string; imageUrl: string; tileWidth: number; tileHeight: number; columns: number; tileCount: number; margin: number; spacing: number;
}
export interface RenderTileLayer {
  name: string; width: number; height: number; x: number; y: number; opacity: number; data: number[];
}
export interface RenderProp {
  name: string; type: string; x: number; y: number; textureUrl: string; frame?: RenderFrame; depth: number;
  interactionId?: string;
}
export interface RenderMap {
  width: number; height: number; tileWidth: number; tileHeight: number;
  tilesets: RenderTileset[]; tileLayers: RenderTileLayer[]; props: RenderProp[]; collision: number[];
}
interface RawObject {
  name: string; type?: string; x: number; y: number; width: number; height: number; visible?: boolean;
  properties?: { name: string; value: unknown }[];
}
interface RawLayer {
  name: string; type: string; width?: number; height?: number; x?: number; y?: number; offsetx?: number; offsety?: number; opacity?: number; visible?: boolean;
  data?: number[]; objects?: RawObject[];
}
interface RawMap {
  width: number; height: number; tilewidth: number; tileheight: number; layers: RawLayer[];
  tilesets: { firstgid: number; name: string; image: string; tilewidth: number; tileheight: number; columns: number; tilecount: number; margin?: number; spacing?: number }[];
}
const spritePaths = new Map<string, string>([["srw_town", SRW_TILESET_URL]]);
for (const key of SRW_HOUSE_KEYS) spritePaths.set(`srw_${key}`, `/assets/pixel/buildings/super-retro-world/${key}.png`);
for (const group of SRW_PROP_GROUPS) for (const key of group.stems) spritePaths.set(`srw_${key}`, `/assets/pixel/props/super-retro-world/${group.dir}/${key}.png`);

export function resolveMapAsset(mapAsset: string, image: string): string {
  const resolved = new URL(image, new URL(mapAsset, "https://world.local"));
  if (resolved.origin !== "https://world.local" || !resolved.pathname.startsWith("/assets/")) throw new Error("地图资源必须来自本地素材目录。");
  return resolved.pathname;
}

/** Phaser and the readable map preview consume the same normalized art and collision data. */
export function parseRenderMap(input: unknown, mapAsset: string): RenderMap {
  const raw = input as RawMap;
  if (!raw || !Number.isInteger(raw.width) || !Number.isInteger(raw.height) || raw.tilewidth !== 16 || raw.tileheight !== 16
    || !Array.isArray(raw.layers) || !Array.isArray(raw.tilesets)) throw new Error("地图格式无效，需要16像素Tiled地图。");
  const tilesets = raw.tilesets.map(item => ({ firstgid: item.firstgid, name: item.name, imageUrl: resolveMapAsset(mapAsset, item.image),
    tileWidth: item.tilewidth, tileHeight: item.tileheight, columns: item.columns, tileCount: item.tilecount, margin: item.margin ?? 0, spacing: item.spacing ?? 0 }));
  const tileLayers = raw.layers.filter(layer => layer.type === "tilelayer" && layer.name !== "collision" && layer.visible !== false).map(layer => ({
    name: layer.name, width: layer.width ?? raw.width, height: layer.height ?? raw.height,
    x: (layer.x ?? 0) * raw.tilewidth + (layer.offsetx ?? 0), y: (layer.y ?? 0) * raw.tileheight + (layer.offsety ?? 0),
    opacity: layer.opacity ?? 1, data: layer.data ?? [],
  }));
  const props: RenderProp[] = [];
  for (const layer of raw.layers.filter(item => item.type === "objectgroup" && item.visible !== false)) {
    for (const object of layer.objects ?? []) {
      if (object.visible === false) continue;
      const values = Object.fromEntries((object.properties ?? []).map(property => [property.name, property.value]));
      const texture = typeof values.texture === "string" ? resolveMapAsset(mapAsset, values.texture) : spritePaths.get(String(values.sprite ?? ""));
      if (!texture) continue;
      let frame: RenderFrame | undefined;
      if ([values.frame_x, values.frame_y, values.frame_width, values.frame_height].every(value => typeof value === "number" && Number.isInteger(value))) {
        frame = { x: Number(values.frame_x), y: Number(values.frame_y), width: Number(values.frame_width), height: Number(values.frame_height) };
      } else if (values.sprite === "srw_town" && typeof values.frame === "number") {
        frame = { x: values.frame % SRW_TILE_COLUMNS * 16, y: Math.floor(values.frame / SRW_TILE_COLUMNS) * 16, width: 16, height: 16 };
      }
      const x = object.x + object.width / 2 + (layer.offsetx ?? 0), y = object.y + object.height + (layer.offsety ?? 0);
      props.push({ name: object.name, type: object.type ?? "", x, y, textureUrl: texture, frame,
        depth: typeof values.depth === "number" ? values.depth : y / raw.tileheight + 1,
        interactionId: typeof values.interaction_id === "string" ? values.interaction_id : undefined });
    }
  }
  const collision = raw.layers.find(layer => layer.name === "collision")?.data ?? [];
  if (collision.length !== raw.width * raw.height) throw new Error("地图碰撞层尺寸与地图不一致。");
  return { width: raw.width, height: raw.height, tileWidth: raw.tilewidth, tileHeight: raw.tileheight, tilesets, tileLayers, props, collision };
}

export function mapTextureUrls(map: RenderMap): string[] {
  return [...new Set([...map.tilesets.map(tileset => tileset.imageUrl), ...map.props.map(prop => prop.textureUrl)])];
}

export function tileSource(map: RenderMap, rawGid: number): { imageUrl: string; frame: RenderFrame; flipX: boolean; flipY: boolean; diagonal: boolean } | null {
  const gid = rawGid & 0x1fffffff;
  if (!gid) return null;
  const tileset = [...map.tilesets].reverse().find(item => gid >= item.firstgid && gid < item.firstgid + item.tileCount);
  if (!tileset) return null;
  const index = gid - tileset.firstgid;
  return { imageUrl: tileset.imageUrl, frame: { x: tileset.margin + index % tileset.columns * (tileset.tileWidth + tileset.spacing),
    y: tileset.margin + Math.floor(index / tileset.columns) * (tileset.tileHeight + tileset.spacing), width: tileset.tileWidth, height: tileset.tileHeight },
  flipX: Boolean(rawGid & 0x80000000), flipY: Boolean(rawGid & 0x40000000), diagonal: Boolean(rawGid & 0x20000000) };
}

export function drawTileLayers(context: CanvasRenderingContext2D, map: RenderMap, images: ReadonlyMap<string, CanvasImageSource>) {
  context.imageSmoothingEnabled = false;
  for (const layer of map.tileLayers) {
    context.save();
    context.globalAlpha *= layer.opacity;
    layer.data.forEach((gid, index) => {
      const source = tileSource(map, gid), image = source && images.get(source.imageUrl);
      if (!source || !image) return;
      const x = layer.x + index % layer.width * map.tileWidth, y = layer.y + Math.floor(index / layer.width) * map.tileHeight;
      context.save();
      context.translate(x + map.tileWidth / 2, y + map.tileHeight / 2);
      if (source.diagonal) context.transform(0, 1, 1, 0, 0, 0);
      context.scale(source.flipX ? -1 : 1, source.flipY ? -1 : 1);
      const frame = source.frame;
      context.drawImage(image, frame.x, frame.y, frame.width, frame.height, -map.tileWidth / 2, -map.tileHeight / 2, map.tileWidth, map.tileHeight);
      context.restore();
    });
    context.restore();
  }
}

export function drawRenderProp(context: CanvasRenderingContext2D, prop: RenderProp, image: HTMLImageElement) {
  const frame = prop.frame ?? { x: 0, y: 0, width: image.naturalWidth || image.width, height: image.naturalHeight || image.height };
  context.drawImage(image, frame.x, frame.y, frame.width, frame.height, prop.x - frame.width / 2, prop.y - frame.height, frame.width, frame.height);
}

export function mapPreviewLayout(mapKey: string, width: number, height: number, previewBounds?: WorldBounds) {
  const bounds = previewBounds ?? mapRenderBounds(getMapPreviewConfig(mapKey));
  const scale = Math.min(width / (bounds.width * 16), height / (bounds.height * 16));
  return { bounds, scale, offsetX: (width - bounds.width * 16 * scale) / 2 - bounds.x * 16 * scale,
    offsetY: (height - bounds.height * 16 * scale) / 2 - bounds.y * 16 * scale };
}

function loadPreviewImage(url: string, signal?: AbortSignal): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    const cleanup = () => { image.onload = null; image.onerror = null; signal?.removeEventListener("abort", abort); };
    const abort = () => { cleanup(); reject(new DOMException("地图预览已取消", "AbortError")); };
    if (signal?.aborted) { abort(); return; }
    signal?.addEventListener("abort", abort, { once: true });
    image.onload = () => { cleanup(); resolve(image); };
    image.onerror = () => { cleanup(); reject(new Error("地图预览素材加载失败。")); };
    image.src = url;
  });
}

export async function paintMapPreview(
  canvas: HTMLCanvasElement,
  mapKey: string,
  signal?: AbortSignal,
  previewBounds?: WorldBounds,
  assetHref: (path: string) => string = (path) => path,
): Promise<void> {
  const config = getMapPreviewConfig(mapKey);
  if (!config.asset) throw new Error("此地图尚未配置真实素材。");
  const response = await fetch(assetHref(config.asset), { signal });
  if (!response.ok) throw new Error("地图预览加载失败。");
  const map = parseRenderMap(await response.json(), config.asset);
  const images = new Map(await Promise.all(mapTextureUrls(map).map(async url => [url, await loadPreviewImage(assetHref(url), signal)] as const)));
  if (signal?.aborted) return;
  const context = canvas.getContext("2d");
  if (!context) throw new Error("无法显示地图预览。");
  const layout = mapPreviewLayout(mapKey, canvas.width, canvas.height, previewBounds);
  context.clearRect(0, 0, canvas.width, canvas.height);
  context.fillStyle = "#14231f";
  context.fillRect(0, 0, canvas.width, canvas.height);
  context.save();
  context.translate(layout.offsetX, layout.offsetY);
  context.scale(layout.scale, layout.scale);
  context.imageSmoothingEnabled = false;
  drawTileLayers(context, map, images);
  for (const prop of [...map.props].sort((a, b) => a.depth - b.depth)) {
    const image = images.get(prop.textureUrl);
    if (image) drawRenderProp(context, prop, image);
  }
  context.restore();
}
