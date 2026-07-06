// 资源加载：Tiled JSON 走文件系统读取，tileset PNG 走 wx.createImage。
import { createImage, readTextFileSync, type GameImage } from '../runtime/wx';
import type { TiledMap } from '../types/tiled';

export const MAP_PATH = 'assets/maps/town_hub.json';
export const TILESET_PATH = 'assets/tilesets/tilemap_packed.png';

export function loadTownMapJson(path: string): TiledMap {
  const text = readTextFileSync(path);
  const parsed: unknown = JSON.parse(text);
  return parsed as TiledMap;
}

export function loadImage(path: string): Promise<GameImage> {
  return new Promise<GameImage>((resolve, reject) => {
    const image = createImage();
    image.onload = (): void => resolve(image);
    image.onerror = (err: unknown): void =>
      reject(err instanceof Error ? err : new Error(`failed to load image: ${path}`));
    image.src = path;
  });
}
