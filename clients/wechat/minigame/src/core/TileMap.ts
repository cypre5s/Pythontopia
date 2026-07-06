// 把 Tiled JSON 解析成渲染层、碰撞网格与可交互对象（建筑 / NPC / 地标）。
import {
  isObjectLayer,
  isTileLayer,
  readStringProperty,
  type TiledMap,
  type TiledObject,
} from '../types/tiled';

export interface RenderLayer {
  name: string;
  width: number;
  height: number;
  data: number[];
}

export interface TilesetInfo {
  firstgid: number;
  columns: number;
  tileWidth: number;
  tileHeight: number;
  imageWidth: number;
  imageHeight: number;
}

export interface FootTile {
  x: number;
  y: number;
}

export type InteractKind = 'building' | 'npc' | 'landmark';

export interface Interactable {
  kind: InteractKind;
  /** 主标题：建筑/地标的中文名，或 NPC 名。 */
  title: string;
  /** 副标题：建筑 route、NPC key，地标为空。 */
  subtitle: string;
  /** 建筑/地标的 route（如 /workshop）；NPC 为空串。 */
  route: string;
  /** NPC 的 key（如 mayor_py）；建筑/地标为空串。 */
  npcKey: string;
  centerX: number;
  centerY: number;
  tiles: FootTile[];
}

export interface NpcSpawn {
  key: string;
  label: string;
  sprite: string;
  tileX: number;
  tileY: number;
}

export interface TilePoint {
  tileX: number;
  tileY: number;
}

// Tiled 高位 flip 标记（水平/垂直/对角），取 gid 真实帧号时需要剥离。
const GID_FLAGS_MASK = 0x1fffffff;

export class TileMap {
  readonly width: number;
  readonly height: number;
  readonly tileSize: number;
  readonly tileset: TilesetInfo;
  readonly layers: RenderLayer[];
  readonly npcs: NpcSpawn[];
  readonly interactables: Interactable[];
  readonly spawn: TilePoint;

  private readonly blocked: boolean[];

  constructor(map: TiledMap, fallbackSpawn: TilePoint) {
    this.width = map.width;
    this.height = map.height;
    this.tileSize = map.tilewidth;

    const tileset = map.tilesets[0];
    if (!tileset) {
      throw new Error('town map has no tileset');
    }
    this.tileset = {
      firstgid: tileset.firstgid,
      columns: tileset.columns,
      tileWidth: tileset.tilewidth,
      tileHeight: tileset.tileheight,
      imageWidth: tileset.imagewidth,
      imageHeight: tileset.imageheight,
    };

    this.layers = [];
    this.npcs = [];
    this.interactables = [];
    this.spawn = fallbackSpawn;
    this.blocked = new Array<boolean>(this.width * this.height).fill(false);

    for (const layer of map.layers) {
      if (isTileLayer(layer)) {
        this.layers.push({
          name: layer.name,
          width: layer.width,
          height: layer.height,
          data: layer.data,
        });
      } else if (isObjectLayer(layer)) {
        for (const object of layer.objects) {
          this.ingestObject(object);
        }
      }
    }
  }

  private ingestObject(object: TiledObject): void {
    switch (object.type) {
      case 'building':
        this.addRectInteractable(object, 'building', true);
        break;
      case 'landmark':
        this.addRectInteractable(object, 'landmark', true);
        break;
      case 'low_obstacle':
        this.blockRect(object);
        break;
      case 'npc':
        this.addNpc(object);
        break;
      default:
        break;
    }
  }

  private addRectInteractable(
    object: TiledObject,
    kind: InteractKind,
    block: boolean,
  ): void {
    const tiles = this.rectTiles(object);
    if (tiles.length === 0) {
      return;
    }
    if (block) {
      for (const tile of tiles) {
        this.setBlocked(tile.x, tile.y, true);
      }
    }
    const label = readStringProperty(object, 'label') ?? object.name;
    const route = readStringProperty(object, 'route') ?? '';
    const center = centerOf(tiles);
    this.interactables.push({
      kind,
      title: label,
      subtitle: route,
      route,
      npcKey: '',
      centerX: center.x,
      centerY: center.y,
      tiles,
    });
  }

  private addNpc(object: TiledObject): void {
    const tileX = Math.floor(object.x / this.tileSize);
    const tileY = Math.floor(object.y / this.tileSize);
    const key = readStringProperty(object, 'key') ?? object.name;
    const label = readStringProperty(object, 'label') ?? key;
    const sprite = readStringProperty(object, 'sprite') ?? 'npc_default';
    this.npcs.push({ key, label, sprite, tileX, tileY });
    this.setBlocked(tileX, tileY, true);
    this.interactables.push({
      kind: 'npc',
      title: label,
      subtitle: key,
      route: '',
      npcKey: key,
      centerX: tileX + 0.5,
      centerY: tileY + 0.5,
      tiles: [{ x: tileX, y: tileY }],
    });
  }

  private blockRect(object: TiledObject): void {
    for (const tile of this.rectTiles(object)) {
      this.setBlocked(tile.x, tile.y, true);
    }
  }

  private rectTiles(object: TiledObject): FootTile[] {
    const tileX0 = Math.floor(object.x / this.tileSize);
    const tileY0 = Math.floor(object.y / this.tileSize);
    const tilesW = Math.max(1, Math.round(object.width / this.tileSize));
    const tilesH = Math.max(1, Math.round(object.height / this.tileSize));
    const tiles: FootTile[] = [];
    for (let dy = 0; dy < tilesH; dy += 1) {
      for (let dx = 0; dx < tilesW; dx += 1) {
        const x = tileX0 + dx;
        const y = tileY0 + dy;
        if (this.inBounds(x, y)) {
          tiles.push({ x, y });
        }
      }
    }
    return tiles;
  }

  inBounds(tileX: number, tileY: number): boolean {
    return tileX >= 0 && tileY >= 0 && tileX < this.width && tileY < this.height;
  }

  /** 该格是否不可通行（越界视为阻挡）。 */
  isBlocked(tileX: number, tileY: number): boolean {
    if (!this.inBounds(tileX, tileY)) {
      return true;
    }
    return this.blocked[tileY * this.width + tileX] === true;
  }

  private setBlocked(tileX: number, tileY: number, value: boolean): void {
    if (this.inBounds(tileX, tileY)) {
      this.blocked[tileY * this.width + tileX] = value;
    }
  }

  /** 取 gid 对应的 tileset 帧号（0 表示空、不绘制；剥离 flip 标记）。 */
  frameForGid(gid: number): number {
    const raw = gid & GID_FLAGS_MASK;
    if (raw < this.tileset.firstgid) {
      return -1;
    }
    return raw - this.tileset.firstgid;
  }

  /** 找到与玩家相邻（含对角）且玩家不在其内部的最近可交互对象。 */
  findInteractableNear(playerTileX: number, playerTileY: number): Interactable | null {
    let best: Interactable | null = null;
    let bestDist = Number.POSITIVE_INFINITY;
    for (const item of this.interactables) {
      if (!this.isInteractableAdjacent(item, playerTileX, playerTileY)) {
        continue;
      }
      const dist =
        Math.abs(item.centerX - (playerTileX + 0.5)) +
        Math.abs(item.centerY - (playerTileY + 0.5));
      if (dist < bestDist) {
        bestDist = dist;
        best = item;
      }
    }
    return best;
  }

  /** 玩家是否与该对象八邻接且不站在其 footprint 内（交互/对话保持的判定）。 */
  isInteractableAdjacent(
    item: Interactable,
    playerTileX: number,
    playerTileY: number,
  ): boolean {
    let adjacent = false;
    for (const tile of item.tiles) {
      const dx = Math.abs(tile.x - playerTileX);
      const dy = Math.abs(tile.y - playerTileY);
      if (dx === 0 && dy === 0) {
        return false;
      }
      if (Math.max(dx, dy) <= 1) {
        adjacent = true;
      }
    }
    return adjacent;
  }
}

function centerOf(tiles: FootTile[]): { x: number; y: number } {
  let sumX = 0;
  let sumY = 0;
  for (const tile of tiles) {
    sumX += tile.x + 0.5;
    sumY += tile.y + 0.5;
  }
  return { x: sumX / tiles.length, y: sumY / tiles.length };
}
