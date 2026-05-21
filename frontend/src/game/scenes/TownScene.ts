import { router } from '@/app/router';
import type { Direction } from '@api/dto/Direction';
import { createDirectionController, type DirectionController } from '@game/adapters/input';
import { openNpcDialog } from '@game/adapters/dialog';
import { createPlayerSprite } from '@game/adapters/sprite';
import { loadTownMap, type TownMapData } from '@game/adapters/tilemap';
import type { PlayerSpriteHandle, RectangleLike, SceneLike } from '@game/adapters/sceneTypes';
import { useWorldStore } from '@stores/world';

let map: TownMapData | null = null;
let player: PlayerSpriteHandle | null = null;
let controller: DirectionController | null = null;
let lastMoveAt = 0;
let actionWasDown = false;

export const TownScene = {
  key: 'TownScene',
  create(this: SceneLike) {
    const world = useWorldStore();
    map = loadTownMap(this, 'town_main');
    const townMap = map;
    const startTileX = world.player && world.player.tileX > 0 ? world.player.tileX : townMap.spawn.tileX;
    const startTileY = world.player && world.player.tileY > 0 ? world.player.tileY : townMap.spawn.tileY;
    player = createPlayerSprite(this, {
      tileX: startTileX,
      tileY: startTileY,
      direction: world.player?.direction ?? 'DOWN',
      tileSize: townMap.tileSize,
    });
    this.cameras.main.startFollow(player, true);
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
      const marker = this.add
        .rectangle(npc.tileX * townMap.tileSize + 16, npc.tileY * townMap.tileSize + 16, 30, 34, 0xffffff, 0)
        .setInteractive()
        .setDepth(12);
      bindClick(marker, () => openNpcDialog(npc.npcKey));
    });
    window.addEventListener('beforeunload', () => {
      void world.flushPlayerUpdate();
    });
  },
  update(this: SceneLike) {
    if (!map || !player || !controller) {
      return;
    }
    const direction = controller.getDirection();
    if (direction && this.time.now - lastMoveAt > 130) {
      lastMoveAt = this.time.now;
      const next = nextTile(player.tileX, player.tileY, direction, map.width, map.height);
      player.moveToTile(next.tileX, next.tileY, direction);
      void useWorldStore().updatePlayerTile({
        tileX: next.tileX,
        tileY: next.tileY,
        direction,
      });
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

function nextTile(tileX: number, tileY: number, direction: Direction, width: number, height: number): { tileX: number; tileY: number } {
  if (direction === 'LEFT') {
    return { tileX: Math.max(0, tileX - 1), tileY };
  }
  if (direction === 'RIGHT') {
    return { tileX: Math.min(width - 1, tileX + 1), tileY };
  }
  if (direction === 'UP') {
    return { tileX, tileY: Math.max(0, tileY - 1) };
  }
  return { tileX, tileY: Math.min(height - 1, tileY + 1) };
}

function bindClick(target: RectangleLike, callback: () => void): void {
  target.on('pointerdown', callback);
}
