// 隔离原因：保持业务层无 Phaser 依赖，禁止业务代码直接 import phaser。
import type { SceneLike } from './sceneTypes';

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
}

export interface TownMapData {
  tileSize: number;
  width: number;
  height: number;
  spawn: { tileX: number; tileY: number };
  buildings: MapZone[];
  npcs: NpcSpawn[];
}

export function loadTownMap(scene: SceneLike, mapKey: string): TownMapData {
  const tileSize = 32;
  const width = 30;
  const height = 20;
  scene.add.tileSprite(480, 320, width * tileSize, height * tileSize, 'tileset_farm').setAlpha(0.18).setDepth(0);
  scene.add.grid(480, 320, width * tileSize, height * tileSize, tileSize, tileSize, 0x87b779, 1, 0x5e8755, 0.35);
  const buildings: MapZone[] = [
    { key: 'profile-room', label: '画像屋', route: '/profile-room', tileX: 3, tileY: 3, width: 4, height: 3 },
    { key: 'workshop', label: '资源工坊', route: '/workshop', tileX: 12, tileY: 2, width: 5, height: 3 },
    { key: 'knowledge-field', label: '知识田', route: '/field', tileX: 22, tileY: 3, width: 5, height: 4 },
    { key: 'board', label: '公告板', route: '/board', tileX: 13, tileY: 13, width: 4, height: 3 },
    { key: 'training', label: '训练场', route: '/training', tileX: 5, tileY: 13, width: 5, height: 3 },
    { key: 'teacher', label: '教师屋', route: '/teacher', tileX: 22, tileY: 13, width: 4, height: 3 },
  ];
  const npcs: NpcSpawn[] = [
    { npcKey: 'mayor_py', label: '镇长 Py', tileX: 14, tileY: 9 },
    { npcKey: 'librarian_loop', label: 'Loop', tileX: 13, tileY: 6 },
    { npcKey: 'coach_debug', label: 'Debug 教练', tileX: 8, tileY: 15 },
  ];
  buildings.forEach((building) => drawBuilding(scene, building, tileSize));
  scene.add.image(760, 164, 'vegetables').setOrigin(0.5).setScale(0.42).setAlpha(0.94).setDepth(8);
  scene.add.image(764, 260, 'cereals').setOrigin(0.5).setScale(0.48).setAlpha(0.9).setDepth(8);
  scene.add.image(462, 198, 'crates_and_sacks').setOrigin(0.5).setScale(0.8).setAlpha(0.9).setDepth(8);
  npcs.forEach((npc) => drawNpc(scene, npc, tileSize));
  scene.cameras.main.setBounds(0, 0, width * tileSize, height * tileSize);
  scene.physics?.world.setBounds(0, 0, width * tileSize, height * tileSize);
  void mapKey;
  return { tileSize, width, height, spawn: { tileX: 15, tileY: 11 }, buildings, npcs };
}

function drawBuilding(scene: SceneLike, zone: MapZone, tileSize: number): void {
  const x = (zone.tileX + zone.width / 2) * tileSize;
  const y = (zone.tileY + zone.height / 2) * tileSize;
  scene.add
    .rectangle(x, y, zone.width * tileSize - 4, zone.height * tileSize - 4, 0xc8663f)
    .setStrokeStyle(3, 0x57352c)
    .setInteractive()
    .setDepth(5);
  scene.add
    .text(x, y - 8, zone.label, { color: '#fff8e1', fontFamily: 'monospace', fontSize: '14px' })
    .setOrigin(0.5)
    .setDepth(6);
}

function drawNpc(scene: SceneLike, npc: NpcSpawn, tileSize: number): void {
  const x = npc.tileX * tileSize + tileSize / 2;
  const y = npc.tileY * tileSize + tileSize / 2;
  scene.add.rectangle(x, y, 22, 28, 0xf1c453).setStrokeStyle(3, 0x4d3b1f).setInteractive().setDepth(10);
  scene.add
    .text(x, y - 28, npc.label, { color: '#263426', fontFamily: 'monospace', fontSize: '12px' })
    .setOrigin(0.5)
    .setDepth(11);
}
