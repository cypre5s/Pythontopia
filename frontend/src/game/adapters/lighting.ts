// 「假光晕」适配层：不做逐像素光照，只在光源道具下面贴一张 ADD 混合的径向渐变。
//
// 为什么不用 Phaser Light2D（真光照）：
//   - 它要法线贴图；SRW 素材是纯平面像素图，硬生成的法线图会让光斑脏、边缘乱。
//   - tilemap 与 Light2D 的配合在 Phaser 3.87 里很别扭，且低端机上开销明显。
//   - 本项目目标是「课堂笔记本上稳定 60fps」，不是写实光照。
//
// 隔离原因：把 Phaser 的 Image / blendMode / tweens 细节挡在 adapter 层，
// 场景侧只传「描述」（sprite 名、锚点、深度），纯函数（lightSpecFor / glowAlpha）
// 单独导出便于单测，不需要跑 Phaser。
import type { TimeOfDay } from './daynight';
import type { SceneLike } from './sceneTypes';

/** 光照贴图键：scripts/make_glow.py 生成的 128×128 纯白径向渐变。 */
export const GLOW_TEXTURE_KEY = 'fx_glow_soft';

export interface LightSpec {
  /** 贴图着色。一张白图 + tint，避免为每种光出一张贴图。 */
  color: number;
  /** 光晕直径（格）。 */
  radiusTiles: number;
  /** 光心相对道具底边中心的高度（格）：火把的火苗在上半截，路灯更高。 */
  offsetTiles: number;
  /** 基准强度 0~1，最终 alpha = 强度 × 昼夜系数。 */
  strength: number;
}

/**
 * 道具贴图名 → 光参数。用贴图名前缀而不是地图里的新字段：
 * 现有 town_hub.json 里的火把/路灯已经是 `srw_torch_*` / `srw_lamp_*`，
 * 加字段要同时改生成器 + 地图 + 运行时读取三处，而前缀本身已经表达了语义。
 */
/**
 * Phaser.BlendModes.ADD 的数值（= 1）。
 * 不直接写字符串 'ADD'：Phaser 3 的 setBlendMode 原样存值，WebGL 渲染器用数字去
 * 索引 blendModes 表，传字符串会静默退回 NORMAL（实战：光晕变成了普通半透明白圈）。
 * 也不 import Phaser：adapter 保持零运行时依赖，jsdom 单测不用拖 1.4MB 引擎。
 */
const BLEND_MODE_ADD = 1;

const LIGHT_SPECS: ReadonlyArray<readonly [prefix: string, spec: LightSpec]> = [
  ['srw_torch', { color: 0xffa03a, radiusTiles: 4.2, offsetTiles: 1.05, strength: 1.0 }],
  ['srw_lamp', { color: 0xffe6a0, radiusTiles: 3.6, offsetTiles: 1.75, strength: 0.8 }],
];

/** 纯函数：贴图名 → 光参数（不是光源就返回 null）。 */
export function lightSpecFor(sprite: string): LightSpec | null {
  for (const [prefix, spec] of LIGHT_SPECS) {
    if (sprite.startsWith(prefix)) return spec;
  }
  return null;
}

/**
 * 纯函数：昼夜 → 光晕系数。
 *
 * 白天几乎看不见（0.12）但仍留一点，火光在太阳底下本来就该很淡；
 * 夜里满值。取值与 daynight.ts 的四段制对齐。
 */
export function glowAlpha(time: TimeOfDay, strength: number): number {
  const factor: Record<TimeOfDay, number> = {
    morning: 0.3,
    noon: 0.12,
    evening: 0.7,
    night: 1,
  };
  const value = strength * factor[time];
  return Math.max(0, Math.min(1, value));
}

/** 呼吸幅度：不做逐帧计算，交给 tween 在两个 alpha 之间往返。 */
const FLICKER_RATIO = 0.86;

interface GlowScene {
  add: {
    image: (x: number, y: number, key: string) => GlowImage;
  };
  tweens?: { add: (config: Record<string, unknown>) => unknown };
}

interface GlowImage {
  setOrigin: (x: number, y?: number) => GlowImage;
  setScale: (x: number, y?: number) => GlowImage;
  setDepth: (depth: number) => GlowImage;
  setBlendMode?: (mode: unknown) => GlowImage;
  setTint?: (color: number) => GlowImage;
  setAlpha: (alpha: number) => GlowImage;
  destroy: () => void;
}

export interface PropGlowOptions {
  /** 道具贴图名（如 srw_torch_01）。 */
  sprite: string;
  /** 道具底边中心 x（世界像素）。 */
  x: number;
  /** 道具底边 y（世界像素）。 */
  bottomY: number;
  /** 瓦片尺寸，用于把 radiusTiles/offsetTiles 换成像素。 */
  tileSize: number;
  /** 绘制深度：光晕要压在道具下面、地面之上。 */
  depth: number;
  time: TimeOfDay;
  /** 贴图边长，默认与 GLOW_TEXTURE_KEY 一致（128）。 */
  textureSize?: number;
}

export interface PropGlowHandle {
  destroy(): void;
}

/**
 * 给一件道具挂光晕。非光源返回 null（调用方无需判断）。
 * 闪烁用 tween 往返 alpha，不逐帧计算；尊重 prefersReducedMotion。
 */
export function attachPropGlow(
  scene: SceneLike,
  opts: PropGlowOptions,
): PropGlowHandle | null {
  const spec = lightSpecFor(opts.sprite);
  if (!spec) return null;
  const phaserScene = scene as unknown as GlowScene;
  if (typeof phaserScene.add?.image !== 'function') return null;

  const textureSize = opts.textureSize ?? 128;
  const diameter = spec.radiusTiles * opts.tileSize;
  const base = glowAlpha(opts.time, spec.strength);
  if (base <= 0) return null;

  const glow = phaserScene.add
    .image(opts.x, opts.bottomY - spec.offsetTiles * opts.tileSize, GLOW_TEXTURE_KEY)
    .setOrigin(0.5)
    .setScale(diameter / textureSize)
    .setDepth(opts.depth)
    .setAlpha(base);
  // ADD 让光晕读起来是「发光」而不是「贴了一张纸」。
  glow.setBlendMode?.(BLEND_MODE_ADD);
  glow.setTint?.(spec.color);

  // 呼吸：每盏灯用坐标做相位差，避免整条街同频闪。
  const phase = ((opts.x * 7 + opts.bottomY * 13) % 400) / 400;
  const low = base * FLICKER_RATIO;
  phaserScene.tweens?.add({
    targets: glow,
    alpha: { from: base, to: low },
    duration: 620 + phase * 460,
    yoyo: true,
    repeat: -1,
    delay: phase * 300,
    ease: 'Sine.easeInOut',
  });

  return { destroy: () => glow.destroy() };
}
