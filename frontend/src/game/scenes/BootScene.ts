import { ensurePixelFontLoaded } from '@game/adapters/pixelFont';

type PhaserLoader = {
  on(event: string, callback: (value: number) => void): void;
  image(key: string, url: string): void;
  spritesheet(key: string, url: string, config: { frameWidth: number; frameHeight: number }): void;
  audio(key: string, url: string | string[]): void;
  // M2 WorldScene 需要的额外 loader 方法；声明为可选，让现有单测的 loader 桩
  // 不必同步实现 json / tilemapTiledJSON 即可继续通过 BootScene.preload.call。
  json?(key: string, url: string): void;
  tilemapTiledJSON?(key: string, url: string): void;
};

type BootSceneContext = { load?: PhaserLoader };

// M2：5 张大地图在 BootScene 一次性预加载，WorldScene 切场景时直接命中缓存。
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
  ['icon_collection', '/assets/pixel/icons_kc/list_tuple_set_dict/delapouite__chemical-tank.png'],
  ['icon_control_flow', '/assets/pixel/icons_kc/control_flow_if_else/delapouite__choice.png'],
  ['icon_loop', '/assets/pixel/icons_kc/loop_for_while/lorc__cycle.png'],
  ['icon_function', '/assets/pixel/icons_kc/function_def_args_return/lorc__gears.png'],
  ['icon_class', '/assets/pixel/icons_kc/class_oop/lorc__crown.png'],
  ['icon_module', '/assets/pixel/icons_kc/module_import_pip/lorc__abstract-052.png'],
  ['icon_io_file', '/assets/pixel/icons_kc/io_file/delapouite__save.png'],
  ['icon_exception', '/assets/pixel/icons_kc/exception_try_except/delapouite__bandage-roll.png'],
  ['icon_decorator', '/assets/pixel/icons_kc/decorator_generator/lorc__magic-swirl.png'],
  ['icon_bug_mine', '/assets/pixel/icons_kc/bug_mine/lorc__ladybug.png'],
  ['icon_quest_main', '/assets/pixel/icons_kc/quest_main/sbed__trophy.png'],
  ['icon_quest_daily', '/assets/pixel/icons_kc/quest_daily/sbed__stopwatch.png'],
];

const CHARACTER_SHEETS: ReadonlyArray<[string, string, number, number]> = [
  // [key, url, frameWidth, frameHeight] — LPC 标准 64x64 / 12 列
  ['student_default', '/assets/pixel/characters/student_default.svg', 32, 32],
  ['player_sara', '/assets/pixel/sprites/lpc-sara/SaraFullSheet.png', 64, 64],
];

// W3 视觉 polish：天气粒子 + KC 收获 confetti 共用一套小型贴图。
// 故意保持 5 张以内，避免 BootScene 启动开销。fx_confetti 与 fx_confetti_burst
// 分别给「持续洒落」与「中心爆发」两种粒子节奏复用。
const PARTICLE_LIST: ReadonlyArray<[string, string]> = [
  ['fx_rain_drop', '/assets/pixel/effects/weather/rain_drop.png'],
  ['fx_snowflake', '/assets/pixel/effects/weather/snowflake.png'],
  ['fx_cloud_drift', '/assets/pixel/effects/weather/cloud.png'],
  ['fx_confetti', '/assets/pixel/effects/weather/confetti.png'],
  ['fx_confetti_burst', '/assets/pixel/effects/weather/confetti_burst.png'],
];

export const BootScene = {
  key: 'BootScene',
  preload(this: BootSceneContext) {
    // 像素字体走 web font 注入，fire-and-forget，不阻塞 Phaser loader。
    void ensurePixelFontLoaded();
    const loader = this.load;
    if (!loader) {
      return;
    }
    loader.on('progress', (value: number) => {
      window.dispatchEvent(new CustomEvent('pythontopia:load-progress', { detail: { progress: value } }));
    });

    for (const [key, url] of TILESET_LIST) loader.image(key, url);
    for (const [key, url, w, h] of TILESET_SPRITESHEETS) loader.spritesheet(key, url, { frameWidth: w, frameHeight: h });
    for (const [key, url] of KC_ICONS) loader.image(key, url);
    for (const [key, url, w, h] of CHARACTER_SHEETS) loader.spritesheet(key, url, { frameWidth: w, frameHeight: h });
    for (const [key, url] of PARTICLE_LIST) loader.image(key, url);
    for (const [key, url] of SFX_LIST) loader.audio(key, url);
    for (const [key, url] of BGM_LIST) loader.audio(key, url);

    // M2 地图清单 + 5 张 Tiled JSON：loader 桩可能未实现这两个方法，因此使用 ?.()。
    loader.json?.('map_index', '/assets/maps/index.json');
    for (const mapKey of WORLD_MAP_KEYS) {
      loader.tilemapTiledJSON?.(`map_${mapKey}`, `/assets/maps/${mapKey}.json`);
    }
  },
  create(this: { scene: { start(key: string, data?: unknown): void } }) {
    window.dispatchEvent(new CustomEvent('pythontopia:load-progress', { detail: { progress: 1 } }));
    this.scene.start('WorldScene', { mapKey: 'town_hub' });
  },
};
