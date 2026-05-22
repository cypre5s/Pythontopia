// 隔离原因：保持业务层无 Phaser 依赖，禁止业务代码直接 import phaser。
// 重构原因：原实现按 30x20 = 600 个 add.image 手撸铺地，性能差且不利于碰撞。
// 改造为 Phaser.Tilemaps API：make.tilemap + addTilesetImage + createBlankLayer + putTileAt。
import Phaser from 'phaser';
import type { SceneLike } from './sceneTypes';
import { PIXEL_FONT_FAMILY } from './pixelFont';

export interface MapZone {
  key: string;
  label: string;
  route: string;
  tileX: number;
  tileY: number;
  width: number;
  height: number;
}

export interface NpcSpawn {
  npcKey: string;
  label: string;
  tileX: number;
  tileY: number;
  sprite?: string;
}

export interface TownMapData {
  tileSize: number;
  width: number;
  height: number;
  spawn: { tileX: number; tileY: number };
  buildings: MapZone[];
  npcs: NpcSpawn[];
  collisions: boolean[][];
}

// Kenney tiny-town tilemap_packed.png: 16x16 源 tile，渲染时按 2x 放大为 32px 网格。
const TOWN_TILESET_KEY = 'kenney_tiny_town';
const SOURCE_TILE_SIZE = 16;
const RENDER_TILE_SIZE = 32;
const RENDER_SCALE = RENDER_TILE_SIZE / SOURCE_TILE_SIZE;
const MAP_WIDTH = 30;
const MAP_HEIGHT = 20;
const GROUND_FRAME = 0;

// 4x3 楼房瓷砖：[行][列]，row 0=屋顶，row 3=门廊，col 0/2=两端，col 1=中段填充
const BUILDING_FRAMES: ReadonlyArray<ReadonlyArray<number>> = [
  [13, 14, 15],
  [16, 17, 18],
  [19, 20, 21],
  [22, 23, 24],
];

const DEFAULT_BUILDINGS: ReadonlyArray<MapZone> = [
  { key: 'profile-room', label: '画像屋', route: '/profile-room', tileX: 3, tileY: 3, width: 4, height: 3 },
  { key: 'workshop', label: '资源工坊', route: '/workshop', tileX: 12, tileY: 2, width: 5, height: 3 },
  { key: 'knowledge-field', label: '知识田', route: '/field', tileX: 22, tileY: 3, width: 5, height: 4 },
  { key: 'board', label: '公告板', route: '/board', tileX: 13, tileY: 13, width: 4, height: 3 },
  { key: 'training', label: '训练场', route: '/training', tileX: 5, tileY: 13, width: 5, height: 3 },
  { key: 'teacher', label: '教师屋', route: '/teacher', tileX: 22, tileY: 13, width: 4, height: 3 },
];

const DEFAULT_NPCS: ReadonlyArray<NpcSpawn> = [
  { npcKey: 'mayor_py', label: '镇长 Py', tileX: 14, tileY: 9, sprite: 'player_sara' },
  { npcKey: 'librarian_loop', label: 'Loop', tileX: 13, tileY: 6, sprite: 'player_sara' },
  { npcKey: 'coach_debug', label: 'Debug 教练', tileX: 8, tileY: 15, sprite: 'player_sara' },
];

export function loadTownMap(scene: SceneLike, mapKey: string): TownMapData {
  void mapKey;
  const phaserScene = scene as unknown as Phaser.Scene;

  const map = phaserScene.make.tilemap({
    tileWidth: SOURCE_TILE_SIZE,
    tileHeight: SOURCE_TILE_SIZE,
    width: MAP_WIDTH,
    height: MAP_HEIGHT,
  });
  const tileset = map.addTilesetImage(
    TOWN_TILESET_KEY,
    TOWN_TILESET_KEY,
    SOURCE_TILE_SIZE,
    SOURCE_TILE_SIZE,
    0,
    0,
  );
  if (!tileset) {
    throw new Error(`tileset image not loaded: ${TOWN_TILESET_KEY}`);
  }

  const groundLayer = map.createBlankLayer('ground', tileset, 0, 0);
  if (!groundLayer) {
    throw new Error('cannot create ground layer');
  }
  groundLayer.fill(GROUND_FRAME);
  groundLayer.setScale(RENDER_SCALE);
  groundLayer.setDepth(0);

  const buildingLayer = map.createBlankLayer('buildings', tileset, 0, 0);
  if (!buildingLayer) {
    throw new Error('cannot create buildings layer');
  }
  buildingLayer.setScale(RENDER_SCALE);
  buildingLayer.setDepth(5);

  const buildings: MapZone[] = DEFAULT_BUILDINGS.map((b) => ({ ...b }));
  buildings.forEach((building) => {
    paintBuilding(buildingLayer, building);
    drawBuildingLabel(phaserScene, building);
  });

  const collisions: boolean[][] = Array.from({ length: MAP_HEIGHT }, () =>
    Array.from({ length: MAP_WIDTH }, () => false),
  );
  buildings.forEach((building) => {
    for (let dy = 0; dy < building.height; dy += 1) {
      for (let dx = 0; dx < building.width; dx += 1) {
        const ty = building.tileY + dy;
        const tx = building.tileX + dx;
        const row = collisions[ty];
        if (row) {
          row[tx] = true;
        }
      }
    }
  });

  const npcs: NpcSpawn[] = DEFAULT_NPCS.map((n) => ({ ...n }));

  const worldWidth = MAP_WIDTH * RENDER_TILE_SIZE;
  const worldHeight = MAP_HEIGHT * RENDER_TILE_SIZE;
  scene.cameras.main.setBounds(0, 0, worldWidth, worldHeight);
  if (scene.physics && scene.physics.world) {
    scene.physics.world.setBounds(0, 0, worldWidth, worldHeight);
  }

  return {
    tileSize: RENDER_TILE_SIZE,
    width: MAP_WIDTH,
    height: MAP_HEIGHT,
    spawn: { tileX: 15, tileY: 11 },
    buildings,
    npcs,
    collisions,
  };
}

function paintBuilding(layer: Phaser.Tilemaps.TilemapLayer, zone: MapZone): void {
  for (let dy = 0; dy < zone.height; dy += 1) {
    for (let dx = 0; dx < zone.width; dx += 1) {
      const col = dx === 0 ? 0 : dx === zone.width - 1 ? 2 : 1;
      const rowIndex = dy === 0 ? 0 : dy === zone.height - 1 ? 3 : dy === 1 ? 1 : 2;
      const rowFrames = BUILDING_FRAMES[rowIndex];
      if (!rowFrames) {
        continue;
      }
      const frame = rowFrames[col];
      if (typeof frame !== 'number') {
        continue;
      }
      layer.putTileAt(frame, zone.tileX + dx, zone.tileY + dy);
    }
  }
}

function drawBuildingLabel(phaserScene: Phaser.Scene, zone: MapZone): void {
  const labelX = (zone.tileX + zone.width / 2) * RENDER_TILE_SIZE;
  const labelY = zone.tileY * RENDER_TILE_SIZE + RENDER_TILE_SIZE / 4;
  phaserScene.add
    .text(labelX, labelY, zone.label, {
      fontFamily: PIXEL_FONT_FAMILY,
      fontSize: '12px',
      color: '#fff8e1',
      backgroundColor: '#00000088',
      padding: { x: 4, y: 2 },
    })
    .setOrigin(0.5)
    .setDepth(6);
}

export interface NpcHandle {
  hideHint(): void;
}

export function drawNpcSprite(scene: SceneLike, npc: NpcSpawn, tileSize: number): NpcHandle {
  const phaserScene = scene as unknown as Phaser.Scene;
  const x = npc.tileX * tileSize + tileSize / 2;
  const y = npc.tileY * tileSize + tileSize / 2;
  phaserScene.add
    .ellipse(x, y + tileSize / 2 - 4, tileSize * 0.6, tileSize * 0.25, 0x000000, 0.3)
    .setDepth(9);
  const spriteKey = npc.sprite ?? 'player_sara';
  // LPC Sara 13 列 × 21 行 = 273 frame，frame 130 = walk-down 第一帧（行 10 列 0），用作 idle 默认朝向。
  const idleFrame = 130;
  phaserScene.add
    .sprite(x, y, spriteKey, idleFrame)
    .setOrigin(0.5, 0.7)
    .setDepth(10)
    .setScale((tileSize / 64) * 1.4);
  phaserScene.add
    .text(x, y - tileSize * 0.9, npc.label, {
      fontFamily: PIXEL_FONT_FAMILY,
      fontSize: '12px',
      color: '#fff8e1',
      backgroundColor: '#00000088',
      padding: { x: 4, y: 2 },
    })
    .setOrigin(0.5)
    .setDepth(11);
  return drawNpcHint(phaserScene, x, y, tileSize);
}

// W4-GUIDE：NPC 头顶可交互提示气泡，黄底黑字 "!" + 上下浮动 4px，配合屏幕底部
// OperationHints 引导玩家"靠近 NPC 按 F"。hideHint() 留作未来"对话过后收起气泡"
// 的接口，本任务暂时常显，不追踪对话历史。
function drawNpcHint(
  phaserScene: Phaser.Scene,
  x: number,
  y: number,
  tileSize: number,
): NpcHandle {
  const hintY = y - tileSize * 1.55;
  const hint = phaserScene.add
    .text(x, hintY, '!', {
      fontFamily: PIXEL_FONT_FAMILY,
      fontSize: '14px',
      color: '#4d3613',
      backgroundColor: '#ffd166',
      padding: { x: 5, y: 1 },
    })
    .setOrigin(0.5)
    .setDepth(12);
  let tween: Phaser.Tweens.Tween | undefined;
  if (phaserScene.tweens?.add) {
    tween = phaserScene.tweens.add({
      targets: hint,
      y: hintY - 4,
      duration: 800,
      yoyo: true,
      repeat: -1,
      ease: 'Sine.easeInOut',
    }) as Phaser.Tweens.Tween | undefined;
  }
  return {
    hideHint(): void {
      if (tween && phaserScene.tweens?.killTweensOf) {
        phaserScene.tweens.killTweensOf(hint);
      }
      const obj = hint as unknown as { setVisible?: (v: boolean) => void; visible?: boolean };
      if (typeof obj.setVisible === 'function') {
        obj.setVisible(false);
      } else {
        obj.visible = false;
      }
    },
  };
}
