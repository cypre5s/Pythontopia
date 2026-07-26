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

// 单格位移 tween 的默认时长（未显式传 durationMs 时用）。
//
// 调用方（WorldScene）应把当前的 effectiveCooldown 传进来：逐格移动的节奏由
// MOVE_COOLDOWN_MS × (雨天 RAIN_SLOWDOWN / 奔跑 RUN_COOLDOWN_FACTOR) 决定，
// tween 却写死 140ms 的话，奔跑（冷却 ≈81ms）时每 81ms 就发一次新的 140ms tween，
// 上一格还没走完就被 killTweensOf 掐掉并从半路重新起算。稳态下「发令时刻贴图到目标
// 格的距离」收敛到 duration/cooldown = 140/81 ≈ 1.74 tile，即比理想值(1 tile)多欠
// 约 0.74 tile —— 贴图肉眼可见地拖在角色逻辑位置后面。让 tween 时长跟随同一个冷却
// 值后 duration/cooldown ≡ 1：贴图恰好在下一步可发出的时刻抵达目标格，walk/run 两侧
// 被同一系数缩放，滑步比例保持不变。
const DEFAULT_TWEEN_DURATION_MS = 140;
const JUMP_DURATION_MS = 260;
const JUMP_ARC_PX = 18;
// 连续行走宽限：单格 tween 完成后等一小段再切 idle。逐格冷却（雨天 162ms、键盘
// repeat 间隙）大于 tween 时长时，两格之间不再闪一帧站立，走路循环保持连贯。
const IDLE_GRACE_MS = 90;

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

/**
 * 单格 tween 时长：优先用调用方给的逐格冷却，无值 / 非法值退回默认。
 * 下限 1ms（duration 0 在 Phaser 里等于「立即完成」，会吞掉 onUpdate）。
 */
export function resolveMoveDuration(durationMs?: number): number {
  if (typeof durationMs !== 'number' || !Number.isFinite(durationMs) || durationMs <= 0) {
    return DEFAULT_TWEEN_DURATION_MS;
  }
  return Math.max(1, durationMs);
}

interface SpriteWithEvents {
  once?(event: string, cb: () => void): void;
  off?(event: string, cb: () => void): void;
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
  let pendingIdle: { remove?: () => void } | null = null;
  // 上一次 playAction 挂在 ANIMATION_COMPLETE 上的 finish 回调。
  // once() 只在「动画自然播完」时自动解绑；被 moveToTile / jumpToTile / 下一次
  // playAction 打断时它仍挂在 sprite 上，等到下一个动画播完先跑掉，把 B 动作的
  // 收尾抢走（陈旧 finish 能穿过 `if (!busy) return` 守卫 —— 那时 busy 已被
  // 新动作置回 true）。所以每条打断路径都要显式 off 掉它。
  let actionFinish: (() => void) | null = null;

  function clearActionFallback(): void {
    if (actionFallback?.remove) actionFallback.remove();
    actionFallback = null;
    if (actionFinish) {
      const spriteEvents = sprite as unknown as SpriteWithEvents;
      spriteEvents.off?.(Phaser.Animations.Events.ANIMATION_COMPLETE, actionFinish);
      actionFinish = null;
    }
  }

  function cancelPendingIdle(): void {
    if (pendingIdle?.remove) pendingIdle.remove();
    pendingIdle = null;
  }

  // 行走单格结束后延迟 IDLE_GRACE_MS 再落 idle；期间来了下一步移动/跳跃/动作
  // 就取消，走路动画跨格连贯。无 delayedCall 的环境（测试 mock）立即落 idle。
  function scheduleIdle(direction: Direction): void {
    cancelPendingIdle();
    const time = phaserScene.time as unknown as TimeWithDelay;
    if (typeof time.delayedCall === 'function') {
      pendingIdle = time.delayedCall(IDLE_GRACE_MS, () => {
        pendingIdle = null;
        playIdle(direction);
      });
    } else {
      playIdle(direction);
    }
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
      cancelPendingIdle();
      phaserScene.tweens.killTweensOf(sprite);
      phaserScene.tweens.killTweensOf(shadow);
      snapToTile(this);
      playIdle(direction);
    },
    moveToTile(
      tileX: number,
      tileY: number,
      direction: Direction,
      moveOpts?: { running?: boolean; durationMs?: number },
    ): void {
      jumping = false;
      busy = false;
      clearActionFallback();
      cancelPendingIdle();
      hideTool();
      hideWeapon();
      this.tileX = tileX;
      this.tileY = tileY;
      this.direction = direction;
      const targetX = tileX * opts.tileSize + opts.tileSize / 2;
      const targetY = tileY * opts.tileSize + opts.tileSize / 2;
      const duration = resolveMoveDuration(moveOpts?.durationMs);
      sprite.setScale(SPRITE_FIXED_SCALE);
      sprite.play(moveOpts?.running ? runAnimKey(direction) : walkAnimKey(direction), true);
      phaserScene.tweens.killTweensOf(sprite);
      phaserScene.tweens.killTweensOf(shadow);
      phaserScene.tweens.add({
        targets: sprite,
        x: targetX,
        y: targetY,
        duration,
        ease: 'Linear',
        onUpdate: () => {
          handle.x = sprite.x;
          handle.y = sprite.y;
        },
        onComplete: () => {
          scheduleIdle(direction);
          handle.x = sprite.x;
          handle.y = sprite.y;
        },
      });
      phaserScene.tweens.add({
        targets: shadow,
        x: targetX,
        y: targetY + opts.tileSize / 2 - 4,
        duration,
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
      cancelPendingIdle();
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
      cancelPendingIdle();
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
        // 保存引用：打断路径（moveToTile / jumpToTile / 下一次 playAction）经
        // clearActionFallback 把它从 ANIMATION_COMPLETE 上摘掉，避免陈旧 finish
        // 在下一个动画播完时先跑，提前掐断当前动作。
        actionFinish = finish;
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
