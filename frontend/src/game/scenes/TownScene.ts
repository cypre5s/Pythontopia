import { router } from '@/app/router';
import { playBgm } from '@/composables/useAudio';
import {
  createDirectionController,
  toServerDirection,
  type DirectionController,
  type ExtendedDirection,
} from '@game/adapters/input';
import { openNpcDialog } from '@game/adapters/dialog';
import { createPlayerSprite } from '@game/adapters/sprite';
import { drawNpcSprite, loadTownMap, type TownMapData } from '@game/adapters/tilemap';
import type { PlayerSpriteHandle, RectangleLike, SceneLike } from '@game/adapters/sceneTypes';
import { useWorldStore } from '@stores/world';

const MOVE_COOLDOWN_MS = 130;
const CAMERA_ZOOM = 2;
const CAMERA_LERP = 0.15;

let map: TownMapData | null = null;
let player: PlayerSpriteHandle | null = null;
let controller: DirectionController | null = null;
let lastMoveAt = 0;
let actionWasDown = false;

export interface BuildingInteraction {
  kind: 'building';
  route: string;
  label: string;
}

export interface NpcInteraction {
  kind: 'npc';
  npcKey: string;
  label: string;
}

export type Interaction = BuildingInteraction | NpcInteraction;

export const TownScene = {
  key: 'TownScene',
  create(this: SceneLike) {
    const world = useWorldStore();
    const townMap = loadTownMap(this, 'town_main');
    map = townMap;
    const startTileX = world.player && world.player.tileX > 0 ? world.player.tileX : townMap.spawn.tileX;
    const startTileY = world.player && world.player.tileY > 0 ? world.player.tileY : townMap.spawn.tileY;
    const playerHandle = createPlayerSprite(this, {
      tileX: startTileX,
      tileY: startTileY,
      direction: world.player?.direction ?? 'DOWN',
      tileSize: townMap.tileSize,
    });
    player = playerHandle;
    const cameraMain = this.cameras.main;
    cameraMain.startFollow(playerHandle, true, CAMERA_LERP, CAMERA_LERP);
    cameraMain.setZoom?.(CAMERA_ZOOM);
    controller = createDirectionController(this);
    townMap.buildings.forEach((building) => {
      const marker = this.add
        .rectangle(
          (building.tileX + building.width / 2) * townMap.tileSize,
          (building.tileY + building.height + 0.25) * townMap.tileSize,
          building.width * townMap.tileSize,
          18,
          0x2f5a36,
          0.7,
        )
        .setInteractive()
        .setDepth(7);
      bindClick(marker, () => {
        void router.push(building.route);
      });
    });
    townMap.npcs.forEach((npc) => {
      drawNpcSprite(this, npc, townMap.tileSize);
      const marker = this.add
        .rectangle(
          npc.tileX * townMap.tileSize + townMap.tileSize / 2,
          npc.tileY * townMap.tileSize + townMap.tileSize / 2,
          30,
          34,
          0xffffff,
          0,
        )
        .setInteractive()
        .setDepth(12);
      bindClick(marker, () => openNpcDialog(npc.npcKey));
    });
    const onBeforeUnload = (): void => {
      void world.flushPlayerUpdate();
    };
    window.addEventListener('beforeunload', onBeforeUnload);
    playBgm('bgm_town_day');

    this.events?.on('shutdown', () => {
      window.removeEventListener('beforeunload', onBeforeUnload);
      map = null;
      player = null;
      controller = null;
      lastMoveAt = 0;
      actionWasDown = false;
    });
  },
  update(this: SceneLike) {
    if (!map || !player || !controller) {
      return;
    }
    const direction = controller.getDirection();
    if (direction && this.time.now - lastMoveAt > MOVE_COOLDOWN_MS) {
      lastMoveAt = this.time.now;
      const serverDir = toServerDirection(direction);
      const next = nextTile(player.tileX, player.tileY, direction, map.width, map.height);
      if (!isBlocked(map, next.tileX, next.tileY)) {
        player.moveToTile(next.tileX, next.tileY, serverDir);
        void useWorldStore().updatePlayerTile({
          tileX: next.tileX,
          tileY: next.tileY,
          direction: serverDir,
        });
      } else {
        // 对角线撞墙时降级为主轴方向重试一次
        const fallback = fallbackAxis(map, player.tileX, player.tileY, direction);
        if (fallback) {
          player.moveToTile(fallback.tileX, fallback.tileY, toServerDirection(fallback.direction));
          void useWorldStore().updatePlayerTile({
            tileX: fallback.tileX,
            tileY: fallback.tileY,
            direction: toServerDirection(fallback.direction),
          });
        } else {
          player.moveToTile(player.tileX, player.tileY, serverDir);
        }
      }
    }
    const actionIsDown = controller.isActionPressed();
    if (actionIsDown && !actionWasDown) {
      const currentPlayer = player;
      const nearby = map.npcs.find(
        (npc) => Math.abs(npc.tileX - currentPlayer.tileX) + Math.abs(npc.tileY - currentPlayer.tileY) <= 1,
      );
      if (nearby) {
        openNpcDialog(nearby.npcKey);
      }
    }
    actionWasDown = actionIsDown;
  },
};

export function nextTile(
  tileX: number,
  tileY: number,
  direction: ExtendedDirection,
  width: number,
  height: number,
): { tileX: number; tileY: number } {
  let dx = 0;
  let dy = 0;
  switch (direction) {
    case 'LEFT':
      dx = -1;
      break;
    case 'RIGHT':
      dx = 1;
      break;
    case 'UP':
      dy = -1;
      break;
    case 'DOWN':
      dy = 1;
      break;
    case 'UP_LEFT':
      dx = -1;
      dy = -1;
      break;
    case 'UP_RIGHT':
      dx = 1;
      dy = -1;
      break;
    case 'DOWN_LEFT':
      dx = -1;
      dy = 1;
      break;
    case 'DOWN_RIGHT':
      dx = 1;
      dy = 1;
      break;
  }
  return {
    tileX: Math.max(0, Math.min(width - 1, tileX + dx)),
    tileY: Math.max(0, Math.min(height - 1, tileY + dy)),
  };
}

function fallbackAxis(
  townMap: TownMapData,
  tileX: number,
  tileY: number,
  direction: ExtendedDirection,
): { tileX: number; tileY: number; direction: ExtendedDirection } | null {
  // 仅对角线方向需要 fallback，主轴撞墙直接停留即可
  const axisPairs: Record<string, ReadonlyArray<ExtendedDirection>> = {
    UP_LEFT: ['UP', 'LEFT'],
    UP_RIGHT: ['UP', 'RIGHT'],
    DOWN_LEFT: ['DOWN', 'LEFT'],
    DOWN_RIGHT: ['DOWN', 'RIGHT'],
  };
  const candidates = axisPairs[direction];
  if (!candidates) {
    return null;
  }
  for (const axis of candidates) {
    const candidate = nextTile(tileX, tileY, axis, townMap.width, townMap.height);
    if (!isBlocked(townMap, candidate.tileX, candidate.tileY)) {
      return { ...candidate, direction: axis };
    }
  }
  return null;
}

export function findNearestInteraction(
  townMap: TownMapData,
  tileX: number,
  tileY: number,
): Interaction | null {
  const npc = townMap.npcs.find(
    (item) => Math.abs(item.tileX - tileX) + Math.abs(item.tileY - tileY) <= 1,
  );
  if (npc) {
    return { kind: 'npc', npcKey: npc.npcKey, label: npc.label };
  }
  const building = townMap.buildings.find((zone) => {
    const withinX = tileX >= zone.tileX && tileX < zone.tileX + zone.width;
    const adjacentY = tileY === zone.tileY + zone.height || tileY === zone.tileY - 1;
    return withinX && adjacentY;
  });
  if (building) {
    return { kind: 'building', route: building.route, label: building.label };
  }
  return null;
}

function isBlocked(townMap: TownMapData, tileX: number, tileY: number): boolean {
  if (tileX < 0 || tileY < 0 || tileX >= townMap.width || tileY >= townMap.height) {
    return true;
  }
  const row = townMap.collisions[tileY];
  if (!row) {
    return true;
  }
  return Boolean(row[tileX]);
}

function bindClick(target: RectangleLike, callback: () => void): void {
  target.on('pointerdown', callback);
}
