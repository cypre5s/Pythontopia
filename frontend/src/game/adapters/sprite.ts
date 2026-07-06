// 隔离原因：保持业务层无 Phaser 依赖，禁止业务代码直接 import phaser。
// 主角用官方 ULPC「Expanded」整表 player_sara2（13×42）：真实 run / jump / idle / sit
//   + 6 基础动作；帧数学集中在 lpcAnimations.ts。待机=idle 动画（呼吸），奔跑=run 动画，
//   跳跃=jump 动画 + 位移弧线，工具动作=thrust + 手持工具叠加层（浇水壶/锄头）。
import Phaser from 'phaser';
import type { Direction } from '@api/dto/Direction';
import type { PlayerSpriteHandle, RectangleLike, SceneLike } from './sceneTypes';
import {
  animKey,
  cardinalize,
  idleFrame as lpcIdleFrame,
  registerCharacterAnimations,
  registerWeaponSlashAnims,
  resolveAnimKey,
  toolOverlayForAction,
  TOOL_TEXTURE_KEYS,
  weaponOverlayForAction,
  WEAPON_TEXTURE_KEYS,
  type CharacterAction,
  type LpcLayout,
} from './lpcAnimations';

const LPC_SHEET_KEY = 'player_sara2';
const PLAYER_ANIM_PREFIX = 'sara2';
const PLAYER_LAYOUT: LpcLayout = 'ulpc';
const SPRITE_FIXED_SCALE = 0.7;
const ACTION_FALLBACK_MS = 1400;

function idleFrame(dir: Direction): number {
  return lpcIdleFrame(PLAYER_LAYOUT, dir);
}
function walkAnimKey(dir: Direction): string {
  return animKey(PLAYER_ANIM_PREFIX, 'walk', dir);
}
function runAnimKey(dir: Direction): string {
  return animKey(PLAYER_ANIM_PREFIX, 'run', dir);
}

export interface CreatePlayerOptions {
  tileX: number;
  tileY: number;
  direction: Direction;
  tileSize: number;
}

const TWEEN_DURATION_MS = 140;
const JUMP_DURATION_MS = 260;
const JUMP_ARC_PX = 18;

function ensureAnims(phaserScene: Phaser.Scene): void {
  registerCharacterAnimations(phaserScene.anims, LPC_SHEET_KEY, PLAYER_ANIM_PREFIX, PLAYER_LAYOUT);
  const textures = (phaserScene as unknown as { textures?: { exists?: (k: string) => boolean } }).textures;
  for (const texKey of TOOL_TEXTURE_KEYS) {
    if (textures?.exists?.(texKey)) {
      registerCharacterAnimations(phaserScene.anims, texKey, texKey, 'universal');
    }
  }
  // 超尺寸武器（斧头）192px slash 叠加动画。
  for (const texKey of WEAPON_TEXTURE_KEYS) {
    if (textures?.exists?.(texKey)) {
      registerWeaponSlashAnims(phaserScene.anims, texKey, texKey);
    }
  }
}

// 192px 超尺寸武器叠加层的对齐：身体 (0.5,0.75) 锚点在 64px 帧的 (32,48)，
// 在 192px 帧里对应 (96,112)，故 origin = (0.5, 112/192)。
const WEAPON_ORIGIN_Y = 112 / 192;

interface SpriteWithEvents {
  once?(event: string, cb: () => void): void;
}
interface TimeWithDelay {
  delayedCall?(ms: number, cb: () => void): { remove?: () => void };
}

export function createPlayerSprite(scene: SceneLike, opts: CreatePlayerOptions): PlayerSpriteHandle {
  const phaserScene = scene as unknown as Phaser.Scene;
  ensureAnims(phaserScene);

  const startX = opts.tileX * opts.tileSize + opts.tileSize / 2;
  const startY = opts.tileY * opts.tileSize + opts.tileSize / 2;

  const shadow = phaserScene.add
    .ellipse(startX, startY + opts.tileSize / 2 - 4, opts.tileSize * 0.7, opts.tileSize * 0.3, 0x000000, 0.35)
    .setDepth(14);
  const sprite = phaserScene.add
    .sprite(startX, startY, LPC_SHEET_KEY, idleFrame(opts.direction))
    .setOrigin(0.5, 0.75)
    .setDepth(15);
  sprite.setScale(SPRITE_FIXED_SCALE);

  let jumping = false;
  let busy = false;
  let actionFallback: { remove?: () => void } | null = null;

  function clearActionFallback(): void {
    if (actionFallback?.remove) actionFallback.remove();
    actionFallback = null;
  }

  // 手持工具叠加层（浇水壶/锄头），与主角同帧布局，叠在身体前后两层。
  let toolFg: Phaser.GameObjects.Sprite | null = null;
  let toolBg: Phaser.GameObjects.Sprite | null = null;

  function ensureToolSprites(sampleTex: string): boolean {
    if (toolFg && toolBg) return true;
    const textures = (phaserScene as unknown as { textures?: { exists?: (k: string) => boolean } }).textures;
    if (!textures?.exists?.(sampleTex)) return false;
    toolBg = phaserScene.add
      .sprite(sprite.x, sprite.y, sampleTex)
      .setOrigin(0.5, 0.75)
      .setDepth(14.6)
      .setScale(SPRITE_FIXED_SCALE)
      .setVisible(false);
    toolFg = phaserScene.add
      .sprite(sprite.x, sprite.y, sampleTex)
      .setOrigin(0.5, 0.75)
      .setDepth(15.4)
      .setScale(SPRITE_FIXED_SCALE)
      .setVisible(false);
    return true;
  }
  function hideTool(): void {
    toolBg?.setVisible(false);
    toolFg?.setVisible(false);
    toolBg?.stop();
    toolFg?.stop();
  }
  function showTool(action: CharacterAction, dir: Direction): void {
    const overlay = toolOverlayForAction(action);
    if (!overlay) {
      hideTool();
      return;
    }
    if (!ensureToolSprites(overlay.fg) || !toolBg || !toolFg) return;
    toolBg.setTexture(overlay.bg);
    toolFg.setTexture(overlay.fg);
    toolBg.x = sprite.x;
    toolBg.y = sprite.y;
    toolFg.x = sprite.x;
    toolFg.y = sprite.y;
    toolBg.setVisible(true);
    toolFg.setVisible(true);
    toolBg.play(animKey(overlay.bg, action, dir), true);
    toolFg.play(animKey(overlay.fg, action, dir), true);
  }

  // 超尺寸武器叠加层（斧头，192px），砍树/挖矿时随 slash 同步挥砍。
  let weaponFg: Phaser.GameObjects.Sprite | null = null;
  let weaponBg: Phaser.GameObjects.Sprite | null = null;
  function ensureWeaponSprites(sampleTex: string): boolean {
    if (weaponFg && weaponBg) return true;
    const textures = (phaserScene as unknown as { textures?: { exists?: (k: string) => boolean } }).textures;
    if (!textures?.exists?.(sampleTex)) return false;
    weaponBg = phaserScene.add
      .sprite(sprite.x, sprite.y, sampleTex)
      .setOrigin(0.5, WEAPON_ORIGIN_Y)
      .setDepth(14.5)
      .setScale(SPRITE_FIXED_SCALE)
      .setVisible(false);
    weaponFg = phaserScene.add
      .sprite(sprite.x, sprite.y, sampleTex)
      .setOrigin(0.5, WEAPON_ORIGIN_Y)
      .setDepth(15.6)
      .setScale(SPRITE_FIXED_SCALE)
      .setVisible(false);
    return true;
  }
  function hideWeapon(): void {
    weaponBg?.setVisible(false);
    weaponFg?.setVisible(false);
    weaponBg?.stop();
    weaponFg?.stop();
  }
  function showWeapon(action: CharacterAction, dir: Direction): void {
    const overlay = weaponOverlayForAction(action);
    if (!overlay) {
      hideWeapon();
      return;
    }
    if (!ensureWeaponSprites(overlay.fg) || !weaponBg || !weaponFg) return;
    weaponBg.setTexture(overlay.bg);
    weaponFg.setTexture(overlay.fg);
    weaponBg.x = sprite.x;
    weaponBg.y = sprite.y;
    weaponFg.x = sprite.x;
    weaponFg.y = sprite.y;
    weaponBg.setVisible(true);
    weaponFg.setVisible(true);
    const cdir = cardinalize(dir).toLowerCase();
    weaponBg.play(`${overlay.bg}_slash_${cdir}`, true);
    weaponFg.play(`${overlay.fg}_slash_${cdir}`, true);
  }

  function snapToTile(handle: PlayerSpriteHandle): void {
    const cx = handle.tileX * opts.tileSize + opts.tileSize / 2;
    const cy = handle.tileY * opts.tileSize + opts.tileSize / 2;
    sprite.x = cx;
    sprite.y = cy;
    shadow.x = cx;
    shadow.y = cy + opts.tileSize / 2 - 4;
    handle.x = cx;
    handle.y = cy;
  }

  // 待机=idle 动画（呼吸）；无 idle 帧时退化为静态走路第 0 帧。
  function playIdle(direction: Direction): void {
    hideTool();
    hideWeapon();
    sprite.setScale(SPRITE_FIXED_SCALE);
    const key = resolveAnimKey(PLAYER_ANIM_PREFIX, PLAYER_LAYOUT, 'idle', direction);
    if (key) {
      sprite.play(key, true);
    } else {
      sprite.stop();
      sprite.setFrame(idleFrame(direction));
    }
  }

  const handle: PlayerSpriteHandle = {
    x: startX,
    y: startY,
    tileX: opts.tileX,
    tileY: opts.tileY,
    direction: opts.direction,
    isJumping(): boolean {
      return jumping;
    },
    isBusy(): boolean {
      return busy;
    },
    faceDirection(direction: Direction): void {
      this.direction = direction;
      phaserScene.tweens.killTweensOf(sprite);
      phaserScene.tweens.killTweensOf(shadow);
      snapToTile(this);
      playIdle(direction);
    },
    moveToTile(tileX: number, tileY: number, direction: Direction, moveOpts?: { running?: boolean }): void {
      jumping = false;
      busy = false;
      clearActionFallback();
      hideTool();
      hideWeapon();
      this.tileX = tileX;
      this.tileY = tileY;
      this.direction = direction;
      const targetX = tileX * opts.tileSize + opts.tileSize / 2;
      const targetY = tileY * opts.tileSize + opts.tileSize / 2;
      sprite.setScale(SPRITE_FIXED_SCALE);
      sprite.play(moveOpts?.running ? runAnimKey(direction) : walkAnimKey(direction), true);
      phaserScene.tweens.killTweensOf(sprite);
      phaserScene.tweens.killTweensOf(shadow);
      phaserScene.tweens.add({
        targets: sprite,
        x: targetX,
        y: targetY,
        duration: TWEEN_DURATION_MS,
        ease: 'Linear',
        onUpdate: () => {
          handle.x = sprite.x;
          handle.y = sprite.y;
        },
        onComplete: () => {
          playIdle(direction);
          handle.x = sprite.x;
          handle.y = sprite.y;
        },
      });
      phaserScene.tweens.add({
        targets: shadow,
        x: targetX,
        y: targetY + opts.tileSize / 2 - 4,
        duration: TWEEN_DURATION_MS,
        ease: 'Linear',
      });
    },
    jumpToTile(tileX: number, tileY: number, direction: Direction): void {
      const sourceX = sprite.x;
      const sourceY = sprite.y;
      const sourceShadowX = shadow.x;
      const sourceShadowY = shadow.y;
      const targetX = tileX * opts.tileSize + opts.tileSize / 2;
      const targetY = tileY * opts.tileSize + opts.tileSize / 2;
      const targetShadowY = targetY + opts.tileSize / 2 - 4;
      const progress = { t: 0 };

      jumping = true;
      busy = false;
      clearActionFallback();
      hideTool();
      hideWeapon();
      this.tileX = tileX;
      this.tileY = tileY;
      this.direction = direction;
      sprite.setScale(SPRITE_FIXED_SCALE);
      sprite.play(resolveAnimKey(PLAYER_ANIM_PREFIX, PLAYER_LAYOUT, 'jump', direction) ?? walkAnimKey(direction), true);
      phaserScene.tweens.killTweensOf(sprite);
      phaserScene.tweens.killTweensOf(shadow);

      phaserScene.tweens.add({
        targets: progress,
        t: 1,
        duration: JUMP_DURATION_MS,
        ease: 'Sine.easeInOut',
        onUpdate: () => {
          const t = progress.t;
          const arc = Math.sin(Math.PI * t) * JUMP_ARC_PX;
          sprite.x = sourceX + (targetX - sourceX) * t;
          sprite.y = sourceY + (targetY - sourceY) * t - arc;
          shadow.x = sourceShadowX + (targetX - sourceShadowX) * t;
          shadow.y = sourceShadowY + (targetShadowY - sourceShadowY) * t;
          handle.x = sprite.x;
          handle.y = sprite.y;
        },
        onComplete: () => {
          jumping = false;
          sprite.x = targetX;
          sprite.y = targetY;
          shadow.x = targetX;
          shadow.y = targetShadowY;
          playIdle(direction);
          handle.x = sprite.x;
          handle.y = sprite.y;
        },
      });
    },
    playAction(action: CharacterAction, actionOpts?: { direction?: Direction; onComplete?: () => void }): void {
      if (jumping) {
        actionOpts?.onComplete?.();
        return;
      }
      const direction = actionOpts?.direction ?? this.direction;
      this.direction = direction;
      const key = resolveAnimKey(PLAYER_ANIM_PREFIX, PLAYER_LAYOUT, action, direction);
      phaserScene.tweens.killTweensOf(sprite);
      phaserScene.tweens.killTweensOf(shadow);
      snapToTile(this);
      sprite.setScale(SPRITE_FIXED_SCALE);

      if (!key) {
        playIdle(direction);
        actionOpts?.onComplete?.();
        return;
      }

      busy = true;
      clearActionFallback();
      sprite.play(key, true);
      showTool(action, direction);
      showWeapon(action, direction);

      const finish = (): void => {
        if (!busy) return;
        busy = false;
        clearActionFallback();
        playIdle(direction);
        actionOpts?.onComplete?.();
      };

      const spriteEvents = sprite as unknown as SpriteWithEvents;
      if (typeof spriteEvents.once === 'function') {
        spriteEvents.once(Phaser.Animations.Events.ANIMATION_COMPLETE, finish);
      }
      const time = phaserScene.time as unknown as TimeWithDelay;
      if (typeof time.delayedCall === 'function') {
        actionFallback = time.delayedCall(ACTION_FALLBACK_MS, finish);
      }
    },
  };

  playIdle(opts.direction);
  return handle;
}

export function setupClickable(target: RectangleLike, callback: () => void): void {
  target.setInteractive().on('pointerdown', callback);
}
