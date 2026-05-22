type PhaserLoader = {
  on(event: string, callback: (value: number) => void): void;
  image(key: string, url: string): void;
  spritesheet(key: string, url: string, config: { frameWidth: number; frameHeight: number }): void;
  audio(key: string, url: string | string[]): void;
};

type BootSceneContext = { load?: PhaserLoader };

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
  ['kenney_tiny_town', '/assets/pixel/tilesets/kenney-tiny-town/Tilemap/tilemap_packed.png'],
  ['kenney_tiny_dungeon', '/assets/pixel/tilesets/kenney-tiny-dungeon/Tilemap/tilemap_packed.png'],
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
  // [key, url, frameWidth, frameHeight] — LPC 标准 64x64
  ['student_default', '/assets/pixel/characters/student_default.svg', 32, 32],
];

export const BootScene = {
  key: 'BootScene',
  preload(this: BootSceneContext) {
    const loader = this.load;
    if (!loader) {
      return;
    }
    loader.on('progress', (value: number) => {
      window.dispatchEvent(new CustomEvent('pythontopia:load-progress', { detail: { progress: value } }));
    });

    for (const [key, url] of TILESET_LIST) loader.image(key, url);
    for (const [key, url] of KC_ICONS) loader.image(key, url);
    for (const [key, url, w, h] of CHARACTER_SHEETS) loader.spritesheet(key, url, { frameWidth: w, frameHeight: h });
    for (const [key, url] of SFX_LIST) loader.audio(key, url);
    for (const [key, url] of BGM_LIST) loader.audio(key, url);
  },
  create(this: { scene: { start(key: string): void } }) {
    window.dispatchEvent(new CustomEvent('pythontopia:load-progress', { detail: { progress: 1 } }));
    this.scene.start('TownScene');
  },
};
