// 假光晕适配层的单测：光参数匹配与昼夜系数是纯函数，可直接断言；
// attachPropGlow 用桩场景验证「非光源不创建对象」「光源按格换算位置与半径」。
// jsdom 没有 GPU，混合模式/tint 只断言被调用，不断言像素。
import { describe, expect, it, vi } from 'vitest';
import {
  attachPropGlow,
  glowAlpha,
  GLOW_TEXTURE_KEY,
  lightSpecFor,
} from '@/game/adapters/lighting';
import type { SceneLike } from '@/game/adapters/sceneTypes';

interface StubImage {
  x: number;
  y: number;
  key: string;
  origin: number | null;
  scale: number | null;
  depth: number | null;
  alpha: number | null;
  blend: unknown;
  tint: number | null;
  destroyed: boolean;
}

function createGlowScene(): { scene: SceneLike; images: StubImage[]; tweens: ReturnType<typeof vi.fn> } {
  const images: StubImage[] = [];
  const tweens = vi.fn();
  const scene = {
    add: {
      image: (x: number, y: number, key: string) => {
        const rec: StubImage = {
          x, y, key, origin: null, scale: null, depth: null, alpha: null, blend: null, tint: null, destroyed: false,
        };
        images.push(rec);
        const img = {
          setOrigin: (ox: number) => { rec.origin = ox; return img; },
          setScale: (s: number) => { rec.scale = s; return img; },
          setDepth: (d: number) => { rec.depth = d; return img; },
          setAlpha: (a: number) => { rec.alpha = a; return img; },
          setBlendMode: (m: unknown) => { rec.blend = m; return img; },
          setTint: (c: number) => { rec.tint = c; return img; },
          destroy: () => { rec.destroyed = true; },
        };
        return img;
      },
    },
    tweens: { add: tweens },
  } as unknown as SceneLike;
  return { scene, images, tweens };
}

describe('lightSpecFor', () => {
  it('火把与路灯被识别为光源，半径/色温/强度各不相同', () => {
    const torch = lightSpecFor('srw_torch_01');
    const lamp = lightSpecFor('srw_lamp_05');
    expect(torch).not.toBeNull();
    expect(lamp).not.toBeNull();
    expect(torch!.radiusTiles).toBeGreaterThan(0);
    expect(torch!.color).not.toBe(lamp!.color);
    // 路灯的灯头更高，光心抬高量必须大于火把（否则光晕落在灯柱根部）。
    expect(lamp!.offsetTiles).toBeGreaterThan(torch!.offsetTiles);
  });

  it('普通道具/空串不是光源', () => {
    expect(lightSpecFor('srw_tree_03')).toBeNull();
    expect(lightSpecFor('srw_house_04')).toBeNull();
    expect(lightSpecFor('')).toBeNull();
  });
});

describe('glowAlpha', () => {
  it('夜里最亮、正午最暗，且单调递增 morning < evening < night', () => {
    const s = 0.5;
    const noon = glowAlpha('noon', s);
    const morning = glowAlpha('morning', s);
    const evening = glowAlpha('evening', s);
    const night = glowAlpha('night', s);
    expect(noon).toBeLessThan(morning);
    expect(morning).toBeLessThan(evening);
    expect(evening).toBeLessThan(night);
    expect(night).toBeCloseTo(s, 5);
  });

  it('白天仍留一点余光，不归零', () => {
    expect(glowAlpha('noon', 0.5)).toBeGreaterThan(0);
  });

  it('结果夹在 [0,1]，强度超界也不会出错', () => {
    expect(glowAlpha('night', 4)).toBe(1);
    expect(glowAlpha('noon', -1)).toBe(0);
  });
});

describe('attachPropGlow', () => {
  it('光源：按格换算位置/直径/透明度，走 ADD 混合并开呼吸 tween', () => {
    const { scene, images, tweens } = createGlowScene();
    const handle = attachPropGlow(scene, {
      sprite: 'srw_torch_01',
      x: 200,
      bottomY: 320,
      tileSize: 16,
      depth: 3.5,
      time: 'night',
    });

    expect(handle).not.toBeNull();
    expect(images).toHaveLength(1);
    const glow = images[0]!;
    expect(glow.key).toBe(GLOW_TEXTURE_KEY);
    expect(glow.x).toBe(200);
    // 光心在底边之上 offsetTiles 格（火把 1.05 格 = 16.8px）。
    expect(glow.y).toBeCloseTo(320 - 1.05 * 16, 5);
    // 直径 = radiusTiles × tileSize，再除以贴图边长得到 scale。
    expect(glow.scale).toBeCloseTo((4.2 * 16) / 128, 5);
    expect(glow.depth).toBe(3.5);
    expect(glow.alpha).toBeCloseTo(1, 5);
    // 1 = Phaser.BlendModes.ADD。不能传字符串：setBlendMode 原样存值，
    // WebGL 渲染器用数字索引表，传 'ADD' 会静默退回 NORMAL。
    expect(glow.blend).toBe(1);
    expect(glow.tint).toBe(0xffa03a);
    expect(tweens).toHaveBeenCalledTimes(1);
  });

  it('非光源直接返回 null，不创建任何对象', () => {
    const { scene, images, tweens } = createGlowScene();
    const handle = attachPropGlow(scene, {
      sprite: 'srw_tree_03',
      x: 0,
      bottomY: 0,
      tileSize: 16,
      depth: 3.5,
      time: 'night',
    });
    expect(handle).toBeNull();
    expect(images).toHaveLength(0);
    expect(tweens).not.toHaveBeenCalled();
  });

  it('destroy 会销毁光晕对象', () => {
    const { scene, images } = createGlowScene();
    const handle = attachPropGlow(scene, {
      sprite: 'srw_lamp_02',
      x: 10,
      bottomY: 10,
      tileSize: 16,
      depth: 3.5,
      time: 'evening',
    });
    handle!.destroy();
    expect(images[0]!.destroyed).toBe(true);
  });

  it('场景没有 add.image（测试桩/无渲染环境）时安全返回 null', () => {
    const scene = {} as unknown as SceneLike;
    expect(attachPropGlow(scene, {
      sprite: 'srw_torch_01',
      x: 0,
      bottomY: 0,
      tileSize: 16,
      depth: 3.5,
      time: 'night',
    })).toBeNull();
  });
});
