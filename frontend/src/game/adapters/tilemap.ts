// 隔离原因：保持业务层无 Phaser 依赖，禁止业务代码直接 import phaser。
// R-14 起 town_hub 由 WorldScene + Tiled JSON 渲染；本模块仅保留 NPC sprite 绘制能力。
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


export interface NpcSpawn {
  npcKey: string;
  label: string;
  tileX: number;
  tileY: number;
  sprite?: string;
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
