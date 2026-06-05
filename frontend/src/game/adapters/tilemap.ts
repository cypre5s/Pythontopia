// 隔离原因：保持业务层无 Phaser 依赖，禁止业务代码直接 import phaser。
// 铺地用 Phaser.Tilemaps API：make.tilemap + addTilesetImage + createBlankLayer + putTileAt。
// loadTownMap 进入时盖一层全屏黑色 loading mask，最少显示 200ms 后再 300ms 渐隐，
// 避免地图构图过程中露出底色 / 上一场景残影导致黑屏闪烁。
import Phaser from 'phaser';
import type { Direction } from '@api/dto/Direction';
import type { SceneLike } from './sceneTypes';
import { PIXEL_FONT_FAMILY } from './pixelFont';
import type { Direction as CompassDir } from '@game/core/PlayerStateMachine';
import {
  animKey,
  idleFrame as lpcIdleFrame,
  registerCharacterAnimations,
  resolveAnimKey,
  spriteLayout,
  toolOverlayForAction,
  TOOL_TEXTURE_KEYS,
  type CharacterAction,
  type LpcLayout,
} from './lpcAnimations';

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
  lowObstacles: boolean[][];
}

// Kenney tiny-town tilemap_packed.png：16×16 源 tile，渲染按 1:1 保持 16px 网格，
// 镜头放大由 cameraTuning zoom 承担。
const TOWN_TILESET_KEY = 'kenney_tiny_town';
const SOURCE_TILE_SIZE = 16;
const RENDER_TILE_SIZE = 16;
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
  { key: 'workshop', label: '百晓工坊', route: '/workshop', tileX: 12, tileY: 2, width: 5, height: 3 },
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

export interface LoadingMaskOptions {
  minDisplayMs?: number;
  fadeDurationMs?: number;
  color?: number;
  alpha?: number;
  depth?: number;
}

export interface LoadingMaskHandle {
  dismiss(): void;
  destroy(): void;
}

const DEFAULT_MIN_DISPLAY_MS = 200;
const DEFAULT_FADE_DURATION_MS = 300;
const DEFAULT_MASK_DEPTH = 9999;

interface MaskTargetLike {
  setDepth(depth: number): unknown;
  setScrollFactor?(x: number, y?: number): unknown;
  setAlpha?(alpha: number): unknown;
  alpha?: number;
  destroy?(): void;
}

interface TweenManagerLike {
  add(config: Record<string, unknown>): unknown;
}

export function showLoadingMask(
  scene: SceneLike,
  opts?: LoadingMaskOptions,
): LoadingMaskHandle {
  const minDisplayMs = opts?.minDisplayMs ?? DEFAULT_MIN_DISPLAY_MS;
  const fadeDurationMs = opts?.fadeDurationMs ?? DEFAULT_FADE_DURATION_MS;
  const color = opts?.color ?? 0x000000;
  const alpha = opts?.alpha ?? 1;
  const depth = opts?.depth ?? DEFAULT_MASK_DEPTH;

  const sceneExtras = scene as unknown as {
    cameras?: { main?: { width?: number; height?: number } };
    tweens?: TweenManagerLike;
  };
  const camera = sceneExtras.cameras?.main;
  const width = camera?.width ?? 960;
  const height = camera?.height ?? 640;

  const rect = scene.add.rectangle(width / 2, height / 2, width, height, color, alpha);
  const mask = rect as unknown as MaskTargetLike;
  mask.setDepth(depth);
  mask.setScrollFactor?.(0, 0);

  let dismissed = false;
  let destroyed = false;
  let autoDismissTimer: ReturnType<typeof setTimeout> | null = null;
  const startedAt = Date.now();

  const performFade = (): void => {
    if (destroyed) {
      return;
    }
    const tweens = sceneExtras.tweens;
    if (tweens && typeof tweens.add === 'function') {
      tweens.add({
        targets: rect,
        alpha: 0,
        duration: fadeDurationMs,
        onComplete: () => {
          if (destroyed) {
            return;
          }
          destroyed = true;
          mask.destroy?.();
        },
      });
    } else {
      destroyed = true;
      mask.destroy?.();
    }
  };

  const triggerDismissNow = (): void => {
    if (dismissed || destroyed) {
      return;
    }
    dismissed = true;
    performFade();
  };

  autoDismissTimer = setTimeout(() => {
    autoDismissTimer = null;
    triggerDismissNow();
  }, minDisplayMs);

  return {
    dismiss(): void {
      if (dismissed || destroyed) {
        return;
      }
      const elapsed = Date.now() - startedAt;
      const wait = Math.max(0, minDisplayMs - elapsed);
      if (autoDismissTimer !== null) {
        clearTimeout(autoDismissTimer);
        autoDismissTimer = null;
      }
      if (wait > 0) {
        setTimeout(triggerDismissNow, wait);
      } else {
        triggerDismissNow();
      }
    },
    destroy(): void {
      if (destroyed) {
        return;
      }
      if (autoDismissTimer !== null) {
        clearTimeout(autoDismissTimer);
        autoDismissTimer = null;
      }
      destroyed = true;
      mask.destroy?.();
    },
  };
}

export function loadTownMap(scene: SceneLike, mapKey: string): TownMapData {
  void mapKey;
  const phaserScene = scene as unknown as Phaser.Scene;
  // 先盖一层 loading mask，构图过程中不会露出底色或上一场景残影。
  showLoadingMask(scene);

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
  const lowObstacles: boolean[][] = Array.from({ length: MAP_HEIGHT }, () =>
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

  const tutorialLogs = [
    { tileX: 16, tileY: 11 },
    { tileX: 17, tileY: 11 },
  ];
  tutorialLogs.forEach(({ tileX, tileY }) => {
    buildingLayer.putTileAt(84, tileX, tileY);
    const collisionRow = collisions[tileY];
    const obstacleRow = lowObstacles[tileY];
    if (collisionRow && obstacleRow) {
      collisionRow[tileX] = true;
      obstacleRow[tileX] = true;
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
    lowObstacles,
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

// 罗盘 8 向 → Direction（动画用）。NpcFramework / PlayerStateMachine 用罗盘记号，
// 动画层用 UP/DOWN/LEFT/RIGHT(+对角)。
const COMPASS_TO_DIRECTION: Readonly<Record<CompassDir, Direction>> = {
  N: 'UP',
  S: 'DOWN',
  E: 'RIGHT',
  W: 'LEFT',
  NE: 'UP_RIGHT',
  NW: 'UP_LEFT',
  SE: 'DOWN_RIGHT',
  SW: 'DOWN_LEFT',
};

export interface NpcInstanceLike {
  x: number;
  y: number;
  direction: CompassDir;
}

/**
 * NPC 动画句柄：同步巡逻位置、按朝向播放行走/待机、并做对话/工作手势的控制器。
 */
export interface NpcSpriteController {
  /** 每帧用 NpcFramework 实例的位置/朝向同步贴图与动画。 */
  sync(instance: NpcInstanceLike, deltaMs: number): void;
  /** 对话/工作一次性手势（universal 表播真实动作帧，walk9 表用补间小跳）。 */
  playGesture(kind: 'talk' | 'work'): void;
  /** 收起头顶 "!" 气泡（对话后）。 */
  hideHint(): void;
  destroy(): void;
}

const NPC_SCALE = 0.7;
const NPC_IDLE_BOB_DURATION_MS = 1100;
// 站桩 NPC 每隔一段时间做一次「工作手势」，给小镇生命感；带抖动避免整齐划一。
const NPC_WORK_INTERVAL_MS = 7000;
const NPC_WORK_JITTER_MS = 4000;
const MOVE_EPSILON = 0.05;

export interface NpcSpriteOptions {
  /** 工作手势在 universal 表上播放的动作（按角色定制，如 cast/slash/water）。 */
  workAction?: CharacterAction;
}

// world 渲染入口把 npc_* 映射到官方 ULPC「全动作」整表（ulpc_*，13×42，
// scripts/build_ulpc_characters.py 产出）；AgentOfficeScene 仍用原 npc_* 9×4 贴图，二者互不影响。
const NPC_ACTION_VARIANT: Readonly<Record<string, string>> = {
  npc_mayor: 'ulpc_scholar',
  npc_librarian: 'ulpc_lady',
  npc_professor: 'ulpc_scholar',
  npc_tutor_ai: 'ulpc_scholar',
  npc_sage: 'ulpc_elder',
  npc_workshop: 'ulpc_worker',
  npc_chef: 'ulpc_villager_m',
  npc_student: 'ulpc_youth',
  npc_merchant: 'ulpc_villager_m',
  npc_sailor: 'ulpc_worker',
  npc_farmer: 'ulpc_worker',
  npc_gardener: 'ulpc_villager_f',
  npc_miner: 'ulpc_villager_m',
  npc_coach: 'ulpc_guard',
  npc_trainer: 'ulpc_guard',
  npc_guard: 'ulpc_guard',
};

export function drawNpcSprite(
  scene: SceneLike,
  npc: NpcSpawn,
  tileSize: number,
  opts?: NpcSpriteOptions,
): NpcSpriteController {
  const phaserScene = scene as unknown as Phaser.Scene;
  const requestedKey = npc.sprite ?? 'player_sara';
  const spriteKey = NPC_ACTION_VARIANT[requestedKey] ?? requestedKey;
  const layout: LpcLayout = spriteLayout(spriteKey);
  registerCharacterAnimations(phaserScene.anims, spriteKey, spriteKey, layout);
  // 手持工具叠加层动画（纹理存在才注册，幂等）。
  const npcTextures = (phaserScene as unknown as { textures?: { exists?: (k: string) => boolean } }).textures;
  for (const texKey of TOOL_TEXTURE_KEYS) {
    if (npcTextures?.exists?.(texKey)) {
      registerCharacterAnimations(phaserScene.anims, texKey, texKey, 'universal');
    }
  }

  let x = npc.tileX * tileSize + tileSize / 2;
  let y = npc.tileY * tileSize + tileSize / 2;
  const shadowDy = tileSize / 2 - 4;

  const shadow = phaserScene.add
    .ellipse(x, y + shadowDy, tileSize * 0.6, tileSize * 0.25, 0x000000, 0.3)
    .setDepth(9);
  const sprite = phaserScene.add
    .sprite(x, y, spriteKey, lpcIdleFrame(layout, 'DOWN'))
    .setOrigin(0.5, 0.7)
    .setDepth(10)
    .setScale(NPC_SCALE);
  // 名牌放到头顶上方，避免遮挡身体（ULPC 角色较高）。
  const labelDy = tileSize * 2.6;
  const label = phaserScene.add
    .text(x, y - labelDy, npc.label, {
      fontFamily: PIXEL_FONT_FAMILY,
      fontSize: '11px',
      color: '#fff8e1',
      backgroundColor: '#00000088',
      padding: { x: 4, y: 2 },
    })
    .setOrigin(0.5)
    .setDepth(11);

  const hintBaseY = y - tileSize * 3.4;
  const hint = phaserScene.add
    .text(x, hintBaseY, '!', {
      fontFamily: PIXEL_FONT_FAMILY,
      fontSize: '14px',
      color: '#4d3613',
      backgroundColor: '#ffd166',
      padding: { x: 5, y: 1 },
    })
    .setOrigin(0.5)
    .setDepth(12);
  if (phaserScene.tweens?.add) {
    phaserScene.tweens.add({
      targets: hint,
      y: hintBaseY - 4,
      duration: 800,
      yoyo: true,
      repeat: -1,
      ease: 'Sine.easeInOut',
    });
  }

  let currentDir: Direction = 'DOWN';
  let walking = false;
  let gesturing = false;
  let bobActive = false;
  let currentWalkKey: string | null = null;
  let workTimerMs = NPC_WORK_INTERVAL_MS + Math.random() * NPC_WORK_JITTER_MS;

  function reposition(): void {
    sprite.x = x;
    sprite.y = y;
    shadow.x = x;
    shadow.y = y + shadowDy;
    label.x = x;
    label.y = y - labelDy;
    hint.x = x;
    if (toolBg) {
      toolBg.x = x;
      toolBg.y = y;
    }
    if (toolFg) {
      toolFg.x = x;
      toolFg.y = y;
    }
  }

  function startBob(): void {
    if (bobActive) return;
    bobActive = true;
    sprite.setScale(NPC_SCALE);
    phaserScene.tweens?.add?.({
      targets: sprite,
      scaleY: NPC_SCALE * 1.04,
      duration: NPC_IDLE_BOB_DURATION_MS,
      yoyo: true,
      repeat: -1,
      ease: 'Sine.easeInOut',
    });
  }

  function stopBob(): void {
    if (!bobActive) return;
    bobActive = false;
    phaserScene.tweens?.killTweensOf?.(sprite);
    sprite.setScale(NPC_SCALE);
  }

  function setIdleFrame(): void {
    sprite.stop?.();
    sprite.setFrame(lpcIdleFrame(layout, currentDir));
  }

  // NPC 手持工具叠加层（如农夫浇水）。仅 universal 表 + 有工具映射的动作生效。
  let toolFg: Phaser.GameObjects.Sprite | null = null;
  let toolBg: Phaser.GameObjects.Sprite | null = null;
  function ensureTool(sampleTex: string): boolean {
    if (toolFg && toolBg) return true;
    if (!npcTextures?.exists?.(sampleTex)) return false;
    toolBg = phaserScene.add
      .sprite(sprite.x, sprite.y, sampleTex)
      .setOrigin(0.5, 0.7)
      .setDepth(9.6)
      .setScale(NPC_SCALE)
      .setVisible(false);
    toolFg = phaserScene.add
      .sprite(sprite.x, sprite.y, sampleTex)
      .setOrigin(0.5, 0.7)
      .setDepth(10.4)
      .setScale(NPC_SCALE)
      .setVisible(false);
    return true;
  }
  function hideTool(): void {
    toolBg?.setVisible(false);
    toolFg?.setVisible(false);
    toolBg?.stop?.();
    toolFg?.stop?.();
  }
  function showTool(action: CharacterAction): void {
    const overlay = toolOverlayForAction(action);
    if (!overlay || layout !== 'universal') {
      hideTool();
      return;
    }
    if (!ensureTool(overlay.fg) || !toolBg || !toolFg) return;
    toolBg.setTexture(overlay.bg);
    toolFg.setTexture(overlay.fg);
    toolBg.x = sprite.x;
    toolBg.y = sprite.y;
    toolFg.x = sprite.x;
    toolFg.y = sprite.y;
    toolBg.setVisible(true);
    toolFg.setVisible(true);
    toolBg.play(animKey(overlay.bg, action, currentDir), true);
    toolFg.play(animKey(overlay.fg, action, currentDir), true);
  }

  startBob();
  setIdleFrame();

  const controller: NpcSpriteController = {
    sync(instance: NpcInstanceLike, deltaMs: number): void {
      const dx = instance.x - x;
      const dy = instance.y - y;
      x = instance.x;
      y = instance.y;
      reposition();

      if (gesturing) return;

      const moving = Math.abs(dx) > MOVE_EPSILON || Math.abs(dy) > MOVE_EPSILON;
      currentDir = COMPASS_TO_DIRECTION[instance.direction] ?? currentDir;

      if (moving) {
        walking = true;
        workTimerMs = NPC_WORK_INTERVAL_MS + Math.random() * NPC_WORK_JITTER_MS;
        stopBob();
        hideTool();
        const key = animKey(spriteKey, 'walk', currentDir);
        if (key !== currentWalkKey) {
          currentWalkKey = key;
          sprite.play?.(key, true);
        }
        return;
      }

      if (walking) {
        walking = false;
        currentWalkKey = null;
        setIdleFrame();
        startBob();
      }

      // 站桩 NPC：周期性做一次工作手势。
      workTimerMs -= deltaMs;
      if (workTimerMs <= 0) {
        workTimerMs = NPC_WORK_INTERVAL_MS + Math.random() * NPC_WORK_JITTER_MS;
        this.playGesture('work');
      }
    },
    playGesture(kind: 'talk' | 'work'): void {
      if (gesturing || walking) return;
      const action: CharacterAction = kind === 'talk' ? 'cast' : (opts?.workAction ?? 'cast');
      const key = resolveAnimKey(spriteKey, layout, action, currentDir);
      stopBob();
      if (key) {
        gesturing = true;
        sprite.play?.(key, true);
        showTool(action);
        const spriteEvents = sprite as unknown as { once?: (e: string, cb: () => void) => void };
        const done = (): void => {
          gesturing = false;
          hideTool();
          setIdleFrame();
          startBob();
        };
        if (typeof spriteEvents.once === 'function') {
          spriteEvents.once(Phaser.Animations.Events.ANIMATION_COMPLETE, done);
        } else {
          done();
        }
        return;
      }
      // walk9 表无动作帧：用一次纵向小跳代替「点头/干活」。
      gesturing = true;
      const baseY = sprite.y;
      if (phaserScene.tweens?.add) {
        phaserScene.tweens.add({
          targets: sprite,
          y: baseY - (kind === 'talk' ? 3 : 5),
          duration: kind === 'talk' ? 140 : 200,
          yoyo: true,
          ease: 'Sine.easeOut',
          onComplete: () => {
            sprite.y = baseY;
            gesturing = false;
          },
        });
      } else {
        gesturing = false;
      }
    },
    hideHint(): void {
      phaserScene.tweens?.killTweensOf?.(hint);
      const obj = hint as unknown as { setVisible?: (v: boolean) => void; visible?: boolean };
      if (typeof obj.setVisible === 'function') {
        obj.setVisible(false);
      } else {
        obj.visible = false;
      }
    },
    destroy(): void {
      phaserScene.tweens?.killTweensOf?.(sprite);
      phaserScene.tweens?.killTweensOf?.(hint);
      (sprite as unknown as { destroy?: () => void }).destroy?.();
      (shadow as unknown as { destroy?: () => void }).destroy?.();
      (label as unknown as { destroy?: () => void }).destroy?.();
      (hint as unknown as { destroy?: () => void }).destroy?.();
      (toolFg as unknown as { destroy?: () => void } | null)?.destroy?.();
      (toolBg as unknown as { destroy?: () => void } | null)?.destroy?.();
    },
  };

  return controller;
}
