import type Phaser from 'phaser';
import { ensurePixelFontLoaded } from '@game/adapters/pixelFont';
import { attachLifecycle } from '@game/core/SceneLifecycle';

// W1：资源加载超时 fallback 默认 15s，超出后向外广播 pythontopia:load-timeout 让
// WorldCanvas 给玩家提示，不强制中断 Phaser loader（loader 继续重试），避免在弱网/
// 大资源场景下"黑屏死等"。
const LOAD_TIMEOUT_MS = 15000;

type LoaderProgressCallback = (value: number) => void;
type LoaderGenericCallback = (...args: unknown[]) => void;

interface PhaserLoader {
  on(event: string, callback: LoaderProgressCallback): void;
  once?(event: string, callback: LoaderGenericCallback): void;
  image(key: string, url: string): void;
  spritesheet(key: string, url: string, config: { frameWidth: number; frameHeight: number }): void;
  audio(key: string, url: string | string[]): void;
  json?(key: string, url: string): void;
  tilemapTiledJSON?(key: string, url: string): void;
}

interface GraphicsLike {
  fillStyle(color: number, alpha?: number): GraphicsLike;
  fillRect(x: number, y: number, w: number, h: number): GraphicsLike;
  clear(): GraphicsLike;
  setDepth?(depth: number): GraphicsLike;
}

interface BootTextLike {
  setOrigin(x: number, y?: number): BootTextLike;
  setDepth?(depth: number): BootTextLike;
  setText?(text: string): BootTextLike;
  destroy?(): void;
}

interface BootSceneAddLike {
  graphics?(): GraphicsLike;
  text?(x: number, y: number, text: string, style?: Record<string, unknown>): BootTextLike;
}

interface BootSceneScaleLike {
  width: number;
  height: number;
}

interface BootScenePreloadContext {
  load?: PhaserLoader;
  add?: BootSceneAddLike;
  scale?: BootSceneScaleLike;
}

interface BootSceneCreateContext {
  scene: { start(key: string, data?: unknown): void };
  events?: {
    on(event: string, fn: () => void, context?: unknown): unknown;
    off?(event: string, fn: () => void, context?: unknown, once?: boolean): unknown;
  };
  tweens?: { killAll(): unknown };
  time?: { removeAllEvents(): unknown };
  input?: { removeAllListeners(): unknown };
}

// M2 WorldScene 需要的 5 张大地图：BootScene 一次性预加载，切场景时直接命中缓存。
const WORLD_MAP_KEYS: ReadonlyArray<string> = [
  'town_hub',
  'farm_north',
  'mine_south',
  'campus_east',
  'port_west',
];

const SFX_LIST: ReadonlyArray<[string, string]> = [
  ['sfx_click', '/assets/pixel/sfx/click/click.ogg'],
  ['sfx_hover', '/assets/pixel/sfx/hover/hover.ogg'],
  ['sfx_coin', '/assets/pixel/sfx/coin/coin.ogg'],
  ['sfx_success', '/assets/pixel/sfx/success.ogg'],
  ['sfx_error', '/assets/pixel/sfx/error.ogg'],
  ['sfx_jingle_complete', '/assets/pixel/sfx/jingle-complete.ogg'],
  ['sfx_jingle_levelup', '/assets/pixel/sfx/jingle-level-up.ogg'],
];

const BGM_LIST: ReadonlyArray<[string, string]> = [
  ['bgm_town_day', '/assets/pixel/bgm/track_01.mp3'],
  ['bgm_town_eve', '/assets/pixel/bgm/track_02.ogg'],
  ['bgm_bug_mine', '/assets/pixel/bgm/track_03.ogg'],
  ['bgm_quest', '/assets/pixel/bgm/track_04.mp3'],
];

const TILESET_LIST: ReadonlyArray<[string, string]> = [
  ['tileset_farm', '/assets/pixel/vendor/opengameart/tileset_farm.png'],
  ['vegetables', '/assets/pixel/vendor/opengameart/vegetables.png'],
  ['cereals', '/assets/pixel/vendor/opengameart/cereals.png'],
  ['crates_and_sacks', '/assets/pixel/vendor/opengameart/crates_and_sacks.png'],
  ['kenney_tiny_dungeon', '/assets/pixel/tilesets/kenney-tiny-dungeon/Tilemap/tilemap_packed.png'],
];

// Kenney tiny-town tilemap_packed.png: 192x176, 16x16 tiles, 12 cols × 11 rows = 132 frames.
// 以 spritesheet 形式加载，便于按 frameIndex 取出单个 tile（frame 0 = 草地）。
const TILESET_SPRITESHEETS: ReadonlyArray<[string, string, number, number]> = [
  ['kenney_tiny_town', '/assets/pixel/tilesets/kenney-tiny-town/Tilemap/tilemap_packed.png', 16, 16],
];

const KC_ICONS: ReadonlyArray<[string, string]> = [
  ['icon_chapter_basics', '/assets/pixel/icons_kc/chapter_basics/lorc__brain.png'],
  ['icon_variable', '/assets/pixel/icons_kc/variable_assignment/lorc__padlock.png'],
  ['icon_data_types', '/assets/pixel/icons_kc/data_types_number_string_bool/lorc__light-bulb.png'],
  ['icon_collection', '/assets/pixel/icons_kc/list_tuple_set_dict/delapouite__stack.png'],
  ['icon_control_flow', '/assets/pixel/icons_kc/control_flow_if_else/delapouite__choice.png'],
  ['icon_loop', '/assets/pixel/icons_kc/loop_for_while/lorc__cycle.png'],
  ['icon_function', '/assets/pixel/icons_kc/function_def_args_return/lorc__gears.png'],
  ['icon_class', '/assets/pixel/icons_kc/class_oop/lorc__crown.png'],
  ['icon_module', '/assets/pixel/icons_kc/module_import_pip/delapouite__plug.png'],
  ['icon_io_file', '/assets/pixel/icons_kc/io_file/delapouite__save.png'],
  ['icon_exception', '/assets/pixel/icons_kc/exception_try_except/lorc__broken-shield.png'],
  ['icon_decorator', '/assets/pixel/icons_kc/decorator_generator/lorc__magic-swirl.png'],
  ['icon_bug_mine', '/assets/pixel/icons_kc/bug_mine/delapouite__mining-helmet.png'],
  ['icon_quest_main', '/assets/pixel/icons_kc/reward_xp_coin/lorc__laurels.png'],
  ['icon_quest_daily', '/assets/pixel/icons_kc/quest_daily/delapouite__calendar.png'],
];

const CHARACTER_SHEETS: ReadonlyArray<[string, string, number, number]> = [
  ['student_default', '/assets/pixel/characters/student_default.svg', 32, 32],
  ['player_sara', '/assets/pixel/sprites/lpc-sara/SaraFullSheet.png', 64, 64],
];

// W3 视觉 polish：天气粒子 + KC 收获 confetti 共用一套小型贴图。
const PARTICLE_LIST: ReadonlyArray<[string, string]> = [
  ['fx_rain_drop', '/assets/pixel/effects/weather/rain_drop.png'],
  ['fx_snowflake', '/assets/pixel/effects/weather/snowflake.png'],
  ['fx_cloud_drift', '/assets/pixel/effects/weather/cloud.png'],
  ['fx_confetti', '/assets/pixel/effects/weather/confetti.png'],
  ['fx_confetti_burst', '/assets/pixel/effects/weather/confetti_burst.png'],
];

interface ProgressUi {
  update(progress: number): void;
  setMessage(message: string): void;
  destroy(): void;
}

export const BootScene = {
  key: 'BootScene',
  preload(this: BootScenePreloadContext) {
    void ensurePixelFontLoaded();
    const loader = this.load;
    if (!loader) {
      return;
    }

    const progressUi = setupProgressUi(this);

    loader.on('progress', (value: number) => {
      window.dispatchEvent(
        new CustomEvent('pythontopia:load-progress', { detail: { progress: value } }),
      );
      progressUi?.update(value);
    });

    setupTimeoutFallback(loader, progressUi);

    for (const [key, url] of TILESET_LIST) loader.image(key, url);
    for (const [key, url, w, h] of TILESET_SPRITESHEETS) {
      loader.spritesheet(key, url, { frameWidth: w, frameHeight: h });
    }
    for (const [key, url] of KC_ICONS) loader.image(key, url);
    for (const [key, url, w, h] of CHARACTER_SHEETS) {
      loader.spritesheet(key, url, { frameWidth: w, frameHeight: h });
    }
    for (const [key, url] of PARTICLE_LIST) loader.image(key, url);
    for (const [key, url] of SFX_LIST) loader.audio(key, url);
    for (const [key, url] of BGM_LIST) loader.audio(key, url);

    // M2 地图清单 + 5 张 Tiled JSON：loader 桩可能未实现这两个方法，因此使用 ?.()。
    loader.json?.('map_index', '/assets/maps/index.json');
    for (const mapKey of WORLD_MAP_KEYS) {
      loader.tilemapTiledJSON?.(`map_${mapKey}`, `/assets/maps/${mapKey}.json`);
    }
  },
  create(this: BootSceneCreateContext) {
    window.dispatchEvent(
      new CustomEvent('pythontopia:load-progress', { detail: { progress: 1 } }),
    );
    // W1：BootScene 也接入统一生命周期清理，hot-reload 时 progress timer / loader
    // listener 不会再泄漏到下一次 Game 实例。
    attachLifecycle(this as unknown as Phaser.Scene);
    this.scene.start('WorldScene', { mapKey: 'town_hub' });
  },
};

function setupProgressUi(ctx: BootScenePreloadContext): ProgressUi | null {
  const add = ctx.add;
  if (!add || typeof add.graphics !== 'function') {
    return null;
  }
  const width = ctx.scale?.width ?? 960;
  const height = ctx.scale?.height ?? 640;
  const barWidth = Math.max(160, Math.min(360, Math.floor(width * 0.45)));
  const barHeight = 16;
  const barX = Math.floor((width - barWidth) / 2);
  const barY = Math.floor(height / 2 - barHeight / 2);

  const bg = add.graphics();
  bg.fillStyle(0x111418, 0.85);
  bg.fillRect(barX - 4, barY - 4, barWidth + 8, barHeight + 8);
  bg.setDepth?.(1000);

  const fill = add.graphics();
  fill.setDepth?.(1001);

  let messageText: BootTextLike | null = null;
  if (typeof add.text === 'function') {
    const text = add.text(width / 2, barY - 22, '资源加载中...', {
      fontSize: '12px',
      color: '#ffd166',
    });
    text.setOrigin(0.5);
    text.setDepth?.(1002);
    messageText = text;
  }

  return {
    update(progress: number): void {
      const clamped = Math.max(0, Math.min(1, progress));
      fill.clear();
      fill.fillStyle(0xffd166, 1);
      fill.fillRect(barX, barY, Math.floor(barWidth * clamped), barHeight);
    },
    setMessage(message: string): void {
      messageText?.setText?.(message);
    },
    destroy(): void {
      messageText?.destroy?.();
    },
  };
}

function setupTimeoutFallback(loader: PhaserLoader, progressUi: ProgressUi | null): void {
  if (typeof loader.once !== 'function') {
    return;
  }
  let completed = false;
  const timerId = setTimeout(() => {
    if (completed) {
      return;
    }
    window.dispatchEvent(
      new CustomEvent('pythontopia:load-timeout', { detail: { elapsedMs: LOAD_TIMEOUT_MS } }),
    );
    progressUi?.setMessage('资源加载较慢，正在重试，请稍候...');
  }, LOAD_TIMEOUT_MS);

  loader.once('complete', () => {
    completed = true;
    clearTimeout(timerId);
  });

  loader.once('loaderror', (file: unknown) => {
    window.dispatchEvent(
      new CustomEvent('pythontopia:load-error', {
        detail: { src: extractSrc(file) },
      }),
    );
  });
}

function extractSrc(file: unknown): string | undefined {
  if (file && typeof file === 'object' && 'src' in file) {
    const src = (file as { src: unknown }).src;
    if (typeof src === 'string') {
      return src;
    }
  }
  return undefined;
}
