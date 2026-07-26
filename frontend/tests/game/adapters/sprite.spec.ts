// 重构原因：sprite.ts 现在用 Phaser.Animations + Tweens，jsdom 下无法真正渲染。
//          本 spec 改为接口级断言：调用 createPlayerSprite 后，返回 handle 暴露的
//          tileX/tileY/direction 与初始入参一致，moveToTile 后会更新这些字段。
//          不再断言具体 Phaser API 调用细节。
import { describe, expect, it, vi } from 'vitest';
import { createPlayerSprite, resolveMoveDuration } from '@/game/adapters/sprite';
import type { SceneLike } from '@/game/adapters/sceneTypes';

function chainSprite() {
  const sprite = {
    x: 0,
    y: 0,
    setOrigin: vi.fn(() => sprite),
    setDepth: vi.fn(() => sprite),
    setScale: vi.fn(() => sprite),
    setFrame: vi.fn(() => sprite),
    play: vi.fn(() => sprite),
    stop: vi.fn(() => sprite),
    once: vi.fn(() => sprite),
    off: vi.fn(() => sprite),
    setVisible: vi.fn(() => sprite),
    setTexture: vi.fn(() => sprite),
  };
  return sprite;
}

function chainEllipse() {
  const ellipse = {
    x: 0,
    y: 0,
    setDepth: vi.fn(() => ellipse),
  };
  return ellipse;
}

function createSceneMock(): SceneLike {
  return {
    add: {
      sprite: vi.fn(() => chainSprite()),
      ellipse: vi.fn(() => chainEllipse()),
      image: vi.fn(() => chainSprite()),
      text: vi.fn(() => chainSprite()),
      rectangle: vi.fn(() => chainSprite()),
      tileSprite: vi.fn(() => chainSprite()),
      grid: vi.fn(() => chainSprite()),
    },
    anims: { exists: vi.fn(() => true), create: vi.fn() },
    tweens: { add: vi.fn(), killTweensOf: vi.fn() },
    input: { keyboard: null },
    time: { now: 0 },
    cameras: { main: { startFollow: vi.fn(), setBounds: vi.fn() } },
  } as unknown as SceneLike;
}

describe('createPlayerSprite', () => {
  it('returns a handle reflecting initial spawn tile and facing direction', () => {
    const scene = createSceneMock();

    const handle = createPlayerSprite(scene, { tileX: 2, tileY: 3, direction: 'DOWN', tileSize: 32 });

    expect(handle.tileX).toBe(2);
    expect(handle.tileY).toBe(3);
    expect(handle.direction).toBe('DOWN');
    // tile (2,3) 中心像素 (2*32 + 16, 3*32 + 16) = (80, 112)
    expect(handle.x).toBe(80);
    expect(handle.y).toBe(112);
  });

  it('moveToTile updates tile coords and facing direction on the handle', () => {
    const scene = createSceneMock();

    const handle = createPlayerSprite(scene, { tileX: 2, tileY: 3, direction: 'DOWN', tileSize: 32 });
    handle.moveToTile(4, 5, 'RIGHT');

    expect(handle.tileX).toBe(4);
    expect(handle.tileY).toBe(5);
    expect(handle.direction).toBe('RIGHT');
  });

  it('连续行走：单格 tween 完成后延迟落 idle，下一步在宽限内到达则取消 idle', () => {
    const scene = createSceneMock();
    const idleTimer = { remove: vi.fn() };
    const delayedCall = vi.fn(() => idleTimer);
    (scene as unknown as { time: { now: number; delayedCall: typeof delayedCall } }).time = {
      now: 0,
      delayedCall,
    };

    const handle = createPlayerSprite(scene, { tileX: 2, tileY: 3, direction: 'DOWN', tileSize: 32 });
    handle.moveToTile(3, 3, 'RIGHT');

    const moveTween = vi
      .mocked(scene.tweens?.add)
      .mock.calls.map((call) => call?.[0] as { onComplete?: () => void } | undefined)
      .find((cfg) => typeof cfg?.onComplete === 'function');
    moveTween?.onComplete?.();

    // tween 完成不立即 idle，而是排一个宽限定时器。
    expect(delayedCall).toHaveBeenCalledTimes(1);
    // 宽限期内的下一步移动取消待落的 idle，走路动画跨格连贯。
    handle.moveToTile(4, 3, 'RIGHT');
    expect(idleTimer.remove).toHaveBeenCalled();
  });

  it('jumpToTile updates coords, reports jumping during the arc, and lands at idle', () => {
    const scene = createSceneMock();

    const handle = createPlayerSprite(scene, { tileX: 2, tileY: 3, direction: 'DOWN', tileSize: 32 });
    handle.jumpToTile(2, 1, 'UP');

    expect(handle.tileX).toBe(2);
    expect(handle.tileY).toBe(1);
    expect(handle.direction).toBe('UP');
    expect(handle.isJumping()).toBe(true);

    // 待机呼吸 tween（yoyo/repeat，无 onComplete）会与跳跃 tween 一起被记录，
    // 跳跃落地 tween 是唯一带 onComplete 的，按此定位更稳健，不依赖调用顺序。
    const tweenConfig = vi
      .mocked(scene.tweens?.add)
      .mock.calls.map((call) => call?.[0] as { onComplete?: () => void } | undefined)
      .find((cfg) => typeof cfg?.onComplete === 'function');
    tweenConfig?.onComplete?.();

    expect(handle.isJumping()).toBe(false);
  });
});

describe('resolveMoveDuration (#8 位移 tween 跟随逐格冷却)', () => {
  it('uses the caller-provided cooldown so the sprite lands when the next step unlocks', () => {
    expect(resolveMoveDuration(130)).toBe(130);
    // 奔跑：130 × RUN_COOLDOWN_FACTOR(0.62) ≈ 80.6ms。写死 140ms 时每 80.6ms 就掐掉
    // 一个刚跑 58% 的 140ms tween，稳态下贴图比逻辑格多欠约 0.74 tile。
    expect(resolveMoveDuration(130 * 0.62)).toBeCloseTo(80.6, 1);
    // 雨天：130 × RAIN_SLOWDOWN(1.25) = 162.5ms。
    expect(resolveMoveDuration(162.5)).toBe(162.5);
  });

  it('falls back to the default for missing / non-finite / non-positive input', () => {
    expect(resolveMoveDuration(undefined)).toBe(140);
    expect(resolveMoveDuration(Number.NaN)).toBe(140);
    expect(resolveMoveDuration(Number.POSITIVE_INFINITY)).toBe(140);
    expect(resolveMoveDuration(0)).toBe(140);
    expect(resolveMoveDuration(-5)).toBe(140);
  });
});

describe('playAction ANIMATION_COMPLETE 解绑 (#9)', () => {
  function playerSpriteOf(scene: SceneLike): ReturnType<typeof chainSprite> {
    const addSprite = vi.mocked((scene.add as unknown as { sprite: () => unknown }).sprite);
    return addSprite.mock.results[0]?.value as ReturnType<typeof chainSprite>;
  }

  it('unbinds the previous finish listener when a new action starts', () => {
    const scene = createSceneMock();
    const handle = createPlayerSprite(scene, { tileX: 0, tileY: 0, direction: 'DOWN', tileSize: 32 });
    const sprite = playerSpriteOf(scene);

    handle.playAction('celebrate');
    const firstFinish = sprite.once.mock.calls.at(-1)?.[1];
    expect(typeof firstFinish).toBe('function');

    sprite.off.mockClear();
    handle.playAction('celebrate');

    // 回归：旧实现只清 delayedCall 兜底、从不 off once，陈旧 finish 会在下一个
    // 动画播完时先跑，把当前动作的收尾抢走（busy 已被新动作置回 true，`!busy` 守卫拦不住）。
    expect(sprite.off).toHaveBeenCalledWith(expect.any(String), firstFinish);
  });

  it('unbinds the pending finish listener when the action is interrupted by walking', () => {
    const scene = createSceneMock();
    const handle = createPlayerSprite(scene, { tileX: 0, tileY: 0, direction: 'DOWN', tileSize: 32 });
    const sprite = playerSpriteOf(scene);

    handle.playAction('celebrate');
    const finish = sprite.once.mock.calls.at(-1)?.[1];

    sprite.off.mockClear();
    handle.moveToTile(1, 0, 'RIGHT');
    expect(sprite.off).toHaveBeenCalledWith(expect.any(String), finish);

    handle.playAction('celebrate');
    const finish2 = sprite.once.mock.calls.at(-1)?.[1];
    sprite.off.mockClear();
    handle.jumpToTile(1, 1, 'DOWN');
    expect(sprite.off).toHaveBeenCalledWith(expect.any(String), finish2);
  });
});
