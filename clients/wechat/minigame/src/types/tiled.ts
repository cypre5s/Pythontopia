// Tiled 地图导出 JSON 的 DTO 类型（只声明本项目读取的字段）。

export type TiledPropertyValue = string | number | boolean;

export interface TiledProperty {
  name: string;
  type: string;
  value: TiledPropertyValue;
}

export interface TiledTileLayer {
  id: number;
  name: string;
  type: 'tilelayer';
  width: number;
  height: number;
  data: number[];
  visible: boolean;
  opacity: number;
}

export interface TiledObject {
  id: number;
  name: string;
  /** Tiled 对象的自定义 class（在 1.10 JSON 里仍序列化为 `type`）。 */
  type: string;
  x: number;
  y: number;
  width: number;
  height: number;
  visible: boolean;
  rotation: number;
  gid?: number;
  properties?: TiledProperty[];
}

export interface TiledObjectLayer {
  id: number;
  name: string;
  type: 'objectgroup';
  objects: TiledObject[];
  visible: boolean;
  opacity: number;
}

export type TiledLayer = TiledTileLayer | TiledObjectLayer;

export interface TiledTileset {
  firstgid: number;
  name: string;
  tilewidth: number;
  tileheight: number;
  tilecount: number;
  columns: number;
  image: string;
  imagewidth: number;
  imageheight: number;
}

export interface TiledMap {
  width: number;
  height: number;
  tilewidth: number;
  tileheight: number;
  layers: TiledLayer[];
  tilesets: TiledTileset[];
  properties?: TiledProperty[];
}

export function isTileLayer(layer: TiledLayer): layer is TiledTileLayer {
  return layer.type === 'tilelayer';
}

export function isObjectLayer(layer: TiledLayer): layer is TiledObjectLayer {
  return layer.type === 'objectgroup';
}

/** 读取对象自定义属性（找不到返回 undefined）。 */
export function readProperty(
  object: TiledObject,
  name: string,
): TiledPropertyValue | undefined {
  const list = object.properties;
  if (!list) {
    return undefined;
  }
  for (const prop of list) {
    if (prop.name === name) {
      return prop.value;
    }
  }
  return undefined;
}

export function readStringProperty(
  object: TiledObject,
  name: string,
): string | undefined {
  const value = readProperty(object, name);
  return typeof value === 'string' ? value : undefined;
}
