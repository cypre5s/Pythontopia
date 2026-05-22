// M2 重构原因：将 TownScene + FieldScene + BugMineScene 等多个独立 Phaser 实例
// 合并为单一 WorldScene，通过 sceneKey 切换 5 张大地图（town_hub / farm_north /
// mine_south / campus_east / port_west），消除多实例资源浪费与状态错乱。
//
// 隔离边界：WorldScene 属于 game adapter 层，允许直接 import phaser；业务层仍
// 只通过 SceneLike / PlayerSpriteHandle 等结构化接口与本文件交互。
import Phaser from 'phaser';
import { router } from '@/app/router';
import { playBgm, type BgmKey } from '@/composables/useAudio';
import type { Direction } from '@api/dto/Direction';
import { createDirectionController, type DirectionController } from '@game/adapters/input';
import { openNpcDialog } from '@game/adapters/dialog';
import { createPlayerSprite } from '@game/adapters/sprite';
import { drawNpcSprite, type NpcSpawn } from '@game/adapters/tilemap';
import type { PlayerSpriteHandle, SceneLike } from '@game/adapters/sceneTypes';
import { applyWeather, normalizeWeatherKind, type WeatherHandle } from '@game/adapters/weather';
import { applyTimeOfDay, deriveTimeOfDay } from '@game/adapters/daynight';
import { useWorldStore } from '@stores/world';

const TILE_SIZE = 16;
const RENDER_SCALE = 2;
const RENDER_TILE_SIZE = TILE_SIZE * RENDER_SCALE;
const CAMERA_ZOOM = 2;
const CAMERA_LERP = 0.15;
const MOVE_COOLDOWN_MS = 130;
const FADE_DURATION_MS = 280;

const SUPPORTED_MAP_KEYS: ReadonlySet<string> = new Set([
  'town_hub',
  'farm_north',
  'mine_south',
  'campus_east',
  'port_west',
]);
const DEFAULT_MAP_KEY = 'town_hub';

const VALID_BGM_KEYS: ReadonlySet<string> = new Set<string>([
  'bgm_town_day',
  'bgm_town_eve',
  'bgm_bug_mine',
  'bgm_quest',
]);
const DEFAULT_BGM: BgmKey = 'bgm_town_day';

export interface MapIndexEntry {
  key: string;
  file?: string;
  label?: string;
  width?: number;
  height?: number;
  bgm?: string;
  tileset?: string;
  spawn?: { tileX: number; tileY: number };
  exits?: string[];
}

export interface MapIndex {
  maps: MapIndexEntry[];
}

interface ExitTile {
  toMap: string;
  toTileX: number;
  toTileY: number;
  tileX: number;
  tileY: number;
}

interface BuildingZone {
  route: string;
  label: string;
  tileX: number;
  tileY: number;
  width: number;
  height: number;
}

interface WorldSceneState {
  mapKey: string;
  index: MapIndex | null;
  entry: MapIndexEntry | null;
  player: PlayerSpriteHandle | null;
  controller: DirectionController | null;
  lastMoveAt: number;
  actionWasDown: boolean;
  exits: ExitTile[];
  npcs: NpcSpawn[];
  width: number;
  height: number;
  renderTileSize: number;
  collisions: boolean[][];
  switching: boolean;
  weather: WeatherHandle | null;
}

const state: WorldSceneState = {
  mapKey: DEFAULT_MAP_KEY,
  index: null,
  entry: null,
  player: null,
  controller: null,
  lastMoveAt: 0,
  actionWasDown: false,
  exits: [],
  npcs: [],
  width: 0,
  height: 0,
  renderTileSize: RENDER_TILE_SIZE,
  collisions: [],
  switching: false,
  weather: null,
};

export const WorldScene = {
  key: 'WorldScene',
  init(this: Phaser.Scene, data?: { mapKey?: string }): void {
    const candidate = typeof data?.mapKey === 'string' ? data.mapKey : state.mapKey;
    state.mapKey = SUPPORTED_MAP_KEYS.has(candidate) ? candidate : DEFAULT_MAP_KEY;
    state.switching = false;
    state.lastMoveAt = 0;
    state.actionWasDown = false;
    // 上一次 restart 残留的 weather 句柄要在 init 阶段一次性销毁，避免重复 setTint
    // 在 jsdom 单测下出现 cameras.main null 报错。
    if (state.weather) {
      state.weather.destroy();
      state.weather = null;
    }
  },
  preload(this: Phaser.Scene): void {
    if (!this.cache.json.exists('map_index')) {
      this.load.json('map_index', '/assets/maps/index.json');
    }
    const mapAssetKey = `map_${state.mapKey}`;
    if (!this.cache.tilemap.exists(mapAssetKey)) {
      this.load.tilemapTiledJSON(mapAssetKey, `/assets/maps/${state.mapKey}.json`);
    }
  },
  create(this: SceneLike): void {
    const phaserScene = this as unknown as Phaser.Scene;
    state.exits = [];
    state.npcs = [];
    state.collisions = [];

    const index = phaserScene.cache.json.exists('map_index')
      ? (phaserScene.cache.json.get('map_index') as MapIndex)
      : null;
    state.index = index;
    state.entry = index?.maps?.find((m) => m.key === state.mapKey) ?? null;

    const mapAssetKey = `map_${state.mapKey}`;
    if (!phaserScene.cache.tilemap.exists(mapAssetKey)) {
      drawFallbackMessage(phaserScene, `地图加载失败：${state.mapKey}`);
      return;
    }

    const map = phaserScene.make.tilemap({ key: mapAssetKey });
    const tilesetKey = state.entry?.tileset ?? 'kenney_tiny_town';
    const tileset = map.addTilesetImage(tilesetKey, tilesetKey, TILE_SIZE, TILE_SIZE, 0, 0);
    if (!tileset) {
      drawFallbackMessage(phaserScene, `Tileset 缺失：${tilesetKey}`);
      return;
    }

    state.width = map.width;
    state.height = map.height;

    const groundLayer = map.createLayer('ground', tileset, 0, 0);
    groundLayer?.setScale(RENDER_SCALE).setDepth(0);
    const buildingsLayer = map.createLayer('buildings', tileset, 0, 0);
    buildingsLayer?.setScale(RENDER_SCALE).setDepth(2);
    const decorationLayer = map.createLayer('decoration', tileset, 0, 0);
    decorationLayer?.setScale(RENDER_SCALE).setDepth(4);

    state.collisions = buildCollisions(map);

    const { spawnTile, exits, buildings, npcs } = parseObjects(map, state.entry?.spawn);
    state.exits = exits;
    state.npcs = npcs;

    const worldStore = useWorldStore();
    const persisted =
      worldStore.player && worldStore.player.mapKey === state.mapKey
        ? { tileX: worldStore.player.tileX, tileY: worldStore.player.tileY }
        : spawnTile;

    const player = createPlayerSprite(this, {
      tileX: persisted.tileX,
      tileY: persisted.tileY,
      direction: worldStore.player?.direction ?? 'DOWN',
      tileSize: RENDER_TILE_SIZE,
    });
    state.player = player;

    if (worldStore.player) {
      worldStore.player = {
        ...worldStore.player,
        mapKey: state.mapKey,
        tileX: player.tileX,
        tileY: player.tileY,
      };
    }

    state.controller = createDirectionController(this);

    buildings.forEach((building) => {
      paintBuildingZone(phaserScene, building);
    });
    npcs.forEach((npc) => {
      drawNpcSprite(this, npc, RENDER_TILE_SIZE);
      paintNpcHotspot(phaserScene, npc);
    });

    const worldWidth = state.width * RENDER_TILE_SIZE;
    const worldHeight = state.height * RENDER_TILE_SIZE;
    this.cameras.main.setBounds(0, 0, worldWidth, worldHeight);
    if (this.physics?.world?.setBounds) {
      this.physics.world.setBounds(0, 0, worldWidth, worldHeight);
    }
    this.cameras.main.startFollow(player, true, CAMERA_LERP, CAMERA_LERP);
    this.cameras.main.setZoom?.(CAMERA_ZOOM);
    phaserScene.cameras.main.fadeIn(FADE_DURATION_MS, 0, 0, 0);

    const bgm = pickBgm(state.entry?.bgm);
    playBgm(bgm);

    // W3 Polish：先应用 weather 粒子，再用 timeOfDay tint 覆盖颜色。两者顺序固定，
    // 不要互换，否则 setTint 会被 weather 反覆盖回去。
    const weatherKind = normalizeWeatherKind(worldStore.session?.weather);
    state.weather = applyWeather(this, weatherKind, worldWidth, worldHeight);
    const sessionForTime = worldStore.session;
    const timeOfDay = deriveTimeOfDay({
      inGameMinutes: sessionForTime ? sessionForTime.inGameMinutes : null,
    });
    applyTimeOfDay(this, timeOfDay);

    const onBeforeUnload = (): void => {
      void worldStore.flushPlayerUpdate();
    };
    window.addEventListener('beforeunload', onBeforeUnload);

    this.events?.on('shutdown', () => {
      window.removeEventListener('beforeunload', onBeforeUnload);
      if (state.weather) {
        state.weather.destroy();
        state.weather = null;
      }
      state.player = null;
      state.controller = null;
      state.collisions = [];
      state.exits = [];
      state.npcs = [];
      state.lastMoveAt = 0;
      state.actionWasDown = false;
    });
  },
  update(this: SceneLike): void {
    if (state.switching) {
      return;
    }
    const player = state.player;
    const controller = state.controller;
    if (!player || !controller) {
      return;
    }

    const rawDirection = controller.getDirection();
    if (rawDirection && this.time.now - state.lastMoveAt > MOVE_COOLDOWN_MS) {
      state.lastMoveAt = this.time.now;
      const delta = directionDelta(rawDirection);
      const cardinal = toCardinal(rawDirection, player.direction);
      if (delta.dx === 0 && delta.dy === 0) {
        return;
      }
      const nextX = clamp(player.tileX + delta.dx, 0, state.width - 1);
      const nextY = clamp(player.tileY + delta.dy, 0, state.height - 1);
      if (!isBlocked(nextX, nextY)) {
        player.moveToTile(nextX, nextY, cardinal);
        void useWorldStore().updatePlayerTile({
          tileX: nextX,
          tileY: nextY,
          direction: cardinal,
        });
        const exit = findExit(nextX, nextY);
        if (exit) {
          triggerSceneTransition(this as unknown as Phaser.Scene, exit);
        }
      } else {
        player.moveToTile(player.tileX, player.tileY, cardinal);
      }
    }

    const actionIsDown = controller.isActionPressed();
    if (actionIsDown && !state.actionWasDown) {
      const nearby = state.npcs.find(
        (npc) => Math.abs(npc.tileX - player.tileX) + Math.abs(npc.tileY - player.tileY) <= 1,
      );
      if (nearby) {
        openNpcDialog(nearby.npcKey);
      }
    }
    state.actionWasDown = actionIsDown;
  },
};

function triggerSceneTransition(phaserScene: Phaser.Scene, exit: ExitTile): void {
  if (!SUPPORTED_MAP_KEYS.has(exit.toMap)) {
    return;
  }
  state.switching = true;
  phaserScene.cameras.main.fadeOut(FADE_DURATION_MS, 0, 0, 0);
  phaserScene.cameras.main.once('camerafadeoutcomplete', () => {
    const worldStore = useWorldStore();
    if (worldStore.player) {
      worldStore.player = {
        ...worldStore.player,
        mapKey: exit.toMap,
        tileX: exit.toTileX,
        tileY: exit.toTileY,
      };
    }
    phaserScene.scene.restart({ mapKey: exit.toMap });
  });
}

function findExit(tileX: number, tileY: number): ExitTile | null {
  return state.exits.find((exit) => exit.tileX === tileX && exit.tileY === tileY) ?? null;
}

function buildCollisions(map: Phaser.Tilemaps.Tilemap): boolean[][] {
  const grid = Array.from({ length: map.height }, () =>
    Array.from({ length: map.width }, () => false),
  );
  const layerNames = ['collision', 'buildings'];
  layerNames.forEach((name) => {
    const layer = map.getLayer(name);
    const tilemapLayer = layer?.tilemapLayer;
    if (!tilemapLayer) {
      return;
    }
    for (let y = 0; y < map.height; y += 1) {
      for (let x = 0; x < map.width; x += 1) {
        const tile = tilemapLayer.getTileAt(x, y, false);
        if (tile && tile.index >= 0) {
          const row = grid[y];
          if (row) {
            row[x] = true;
          }
        }
      }
    }
  });
  return grid;
}

function parseObjects(
  map: Phaser.Tilemaps.Tilemap,
  fallbackSpawn: { tileX: number; tileY: number } | undefined,
): {
  spawnTile: { tileX: number; tileY: number };
  exits: ExitTile[];
  buildings: BuildingZone[];
  npcs: NpcSpawn[];
} {
  let spawnTile: { tileX: number; tileY: number } = fallbackSpawn ?? { tileX: 5, tileY: 5 };
  const exits: ExitTile[] = [];
  const buildings: BuildingZone[] = [];
  const npcs: NpcSpawn[] = [];

  map.objects.forEach((group) => {
    group.objects.forEach((obj) => {
      const type = String(obj.type ?? '');
      const tileX = Math.floor((obj.x ?? 0) / TILE_SIZE);
      const tileY = Math.floor((obj.y ?? 0) / TILE_SIZE);
      const props = collectProperties(obj.properties);
      if (type === 'spawn') {
        spawnTile = { tileX, tileY };
        return;
      }
      if (type === 'exit') {
        exits.push({
          tileX,
          tileY,
          toMap: String(props['toMap'] ?? ''),
          toTileX: Number(props['toTileX'] ?? 0),
          toTileY: Number(props['toTileY'] ?? 0),
        });
        return;
      }
      if (type === 'building') {
        buildings.push({
          route: String(props['route'] ?? '/'),
          label: String(props['label'] ?? obj.name ?? ''),
          tileX,
          tileY,
          width: Math.max(1, Math.floor((obj.width ?? TILE_SIZE) / TILE_SIZE)),
          height: Math.max(1, Math.floor((obj.height ?? TILE_SIZE) / TILE_SIZE)),
        });
        return;
      }
      if (type === 'npc') {
        const spriteValue = props['sprite'];
        npcs.push({
          npcKey: String(props['npcKey'] ?? obj.name ?? 'npc'),
          label: String(props['label'] ?? obj.name ?? ''),
          tileX,
          tileY,
          sprite: typeof spriteValue === 'string' ? spriteValue : 'player_sara',
        });
      }
    });
  });

  return { spawnTile, exits, buildings, npcs };
}

function collectProperties(props: unknown): Record<string, unknown> {
  if (!Array.isArray(props)) {
    return {};
  }
  const entries = (props as Array<{ name?: unknown; value?: unknown }>).filter(
    (p): p is { name: string; value: unknown } => typeof p.name === 'string',
  );
  return Object.fromEntries(entries.map((p) => [p.name, p.value]));
}

function paintBuildingZone(phaserScene: Phaser.Scene, building: BuildingZone): void {
  const px = (building.tileX + building.width / 2) * RENDER_TILE_SIZE;
  const py = (building.tileY + building.height / 2) * RENDER_TILE_SIZE;
  const width = building.width * RENDER_TILE_SIZE;
  const height = building.height * RENDER_TILE_SIZE;
  const rect = phaserScene.add
    .rectangle(px, py, width, height, 0x000000, 0)
    .setStrokeStyle(2, 0x2f5a36, 0.4)
    .setInteractive({ useHandCursor: true })
    .setDepth(7);
  rect.on('pointerdown', () => {
    void router.push(building.route);
  });
  phaserScene.add
    .text(px, building.tileY * RENDER_TILE_SIZE - 4, building.label, {
      fontFamily: 'monospace',
      fontSize: '12px',
      color: '#fff8e1',
      backgroundColor: '#00000088',
      padding: { x: 4, y: 2 },
    })
    .setOrigin(0.5, 1)
    .setDepth(8);
}

function paintNpcHotspot(phaserScene: Phaser.Scene, npc: NpcSpawn): void {
  const cx = npc.tileX * RENDER_TILE_SIZE + RENDER_TILE_SIZE / 2;
  const cy = npc.tileY * RENDER_TILE_SIZE + RENDER_TILE_SIZE / 2;
  const marker = phaserScene.add
    .rectangle(cx, cy, RENDER_TILE_SIZE, RENDER_TILE_SIZE, 0xffffff, 0)
    .setInteractive({ useHandCursor: true })
    .setDepth(12);
  marker.on('pointerdown', () => openNpcDialog(npc.npcKey));
}

function isBlocked(tileX: number, tileY: number): boolean {
  if (tileX < 0 || tileY < 0 || tileX >= state.width || tileY >= state.height) {
    return true;
  }
  const row = state.collisions[tileY];
  return Boolean(row?.[tileX]);
}

export function directionDelta(dir: string): { dx: number; dy: number } {
  if (!dir) {
    return { dx: 0, dy: 0 };
  }
  const tokens = dir.split('_');
  let dx = 0;
  let dy = 0;
  tokens.forEach((token) => {
    if (token === 'LEFT') dx -= 1;
    else if (token === 'RIGHT') dx += 1;
    else if (token === 'UP') dy -= 1;
    else if (token === 'DOWN') dy += 1;
  });
  return { dx, dy };
}

export function toCardinal(dir: string, fallback: Direction): Direction {
  if (dir === 'UP' || dir === 'DOWN' || dir === 'LEFT' || dir === 'RIGHT') {
    return dir;
  }
  const tokens = dir.split('_');
  if (tokens.includes('LEFT')) return 'LEFT';
  if (tokens.includes('RIGHT')) return 'RIGHT';
  if (tokens.includes('UP')) return 'UP';
  if (tokens.includes('DOWN')) return 'DOWN';
  return fallback;
}

function clamp(value: number, lower: number, upper: number): number {
  return Math.max(lower, Math.min(upper, value));
}

function pickBgm(candidate: string | undefined): BgmKey {
  if (candidate && VALID_BGM_KEYS.has(candidate)) {
    return candidate as BgmKey;
  }
  return DEFAULT_BGM;
}

function drawFallbackMessage(phaserScene: Phaser.Scene, text: string): void {
  phaserScene.add
    .text(phaserScene.scale.width / 2, phaserScene.scale.height / 2, text, {
      color: '#fff8e1',
      fontFamily: 'monospace',
      fontSize: '18px',
      backgroundColor: '#3b2411',
      padding: { x: 12, y: 8 },
    })
    .setOrigin(0.5)
    .setDepth(100);
}
