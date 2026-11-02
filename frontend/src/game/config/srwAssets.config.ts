/* eslint-disable */
/**
 * Super Retro World 运行时资产清单。
 *
 * 由 scripts/build_srw_assets.py 生成，勿手改：重新切片后请重跑该脚本。
 * BootScene 按这份表注册贴图，因此切片与运行时不可能走偏。
 *
 * 贴图键统一为 `srw_<文件名>`，与 generate-maps.mjs 地图对象里的 sprite 字段同名。
 */
export const SRW_TILESET_KEY = 'srw_town' as const;
export const SRW_TILESET_URL = '/assets/pixel/tilesets/super-retro-world/town_tiles.png' as const;
export const SRW_TILE_SIZE = 16;
export const SRW_TILE_COLUMNS = 32;
export const SRW_TILE_COUNT = 220;

/** 角色表：48x80 = 3 帧 x 4 朝向，单帧 16x20。 */
export const SRW_CHARA_KEYS = ['chara_0', 'chara_1', 'chara_10', 'chara_11', 'chara_12', 'chara_13', 'chara_14', 'chara_15', 'chara_16', 'chara_17', 'chara_18', 'chara_19', 'chara_2', 'chara_20', 'chara_21', 'chara_22', 'chara_23', 'chara_24', 'chara_25', 'chara_26', 'chara_27', 'chara_28', 'chara_29', 'chara_3', 'chara_30', 'chara_31', 'chara_4', 'chara_5', 'chara_6', 'chara_7', 'chara_8', 'chara_9'];
export const SRW_CHARA_FRAME = { frameWidth: 16, frameHeight: 20 } as const;

/** 整栋房屋精灵（一文件一张，尺寸各异）。 */
export const SRW_HOUSE_KEYS = ['house_01', 'house_02', 'house_03', 'house_04', 'house_05', 'house_06', 'house_07', 'house_08', 'house_09', 'house_10', 'house_11', 'house_12'];

/** 道具精灵：按目录分组（目录名参与 URL 拼装）。 */
export const SRW_PROP_GROUPS: ReadonlyArray<{ dir: string; stems: string[] }> = [
  { dir: 'barrels', stems: ['barrel_01', 'barrel_02', 'barrel_03', 'barrel_04', 'barrel_05'] },
  { dir: 'crates', stems: ['crate_01', 'crate_02', 'crate_03', 'crate_04', 'crate_05', 'crate_06', 'crate_07', 'crate_08'] },
  { dir: 'lamps', stems: ['lamp_01', 'lamp_02', 'lamp_03', 'lamp_04', 'lamp_05'] },
  { dir: 'pots', stems: ['pot_01', 'pot_02', 'pot_03', 'pot_04', 'pot_05', 'pot_06', 'pot_07', 'pot_08', 'pot_09', 'pot_10', 'pot_11', 'pot_12', 'pot_13', 'pot_14', 'pot_15', 'pot_16', 'pot_17', 'pot_18', 'pot_19', 'pot_20', 'pot_21', 'pot_22', 'pot_23', 'pot_24', 'pot_25', 'pot_26', 'pot_27'] },
  { dir: 'potted_plants', stems: ['potted_plant_01', 'potted_plant_02', 'potted_plant_03', 'potted_plant_04', 'potted_plant_05', 'potted_plant_06', 'potted_plant_07', 'potted_plant_08', 'potted_plant_09', 'potted_plant_10', 'potted_plant_11', 'potted_plant_12', 'potted_plant_13', 'potted_plant_14', 'potted_plant_15'] },
  { dir: 'rocks', stems: ['rock_01', 'rock_02', 'rock_03', 'rock_04', 'rock_05', 'rock_06', 'rock_07', 'rock_08', 'rock_09', 'rock_10', 'rock_11', 'rock_12', 'rock_13', 'rock_14', 'rock_15', 'rock_16', 'rock_17', 'rock_18', 'rock_19', 'rock_20', 'rock_21', 'rock_22', 'rock_23', 'rock_24', 'rock_25', 'rock_26', 'rock_27', 'rock_28', 'rock_29', 'rock_30', 'rock_31', 'rock_32', 'rock_33', 'rock_34', 'rock_35', 'rock_36', 'rock_37', 'rock_38', 'rock_39', 'rock_40', 'rock_41', 'rock_42', 'rock_43', 'rock_44', 'rock_45', 'rock_46'] },
  { dir: 'torches', stems: ['torch_01', 'torch_02', 'torch_03', 'torch_04', 'torch_05', 'torch_06', 'torch_07', 'torch_08', 'torch_09', 'torch_10', 'torch_11', 'torch_12', 'torch_13', 'torch_14', 'torch_15', 'torch_16', 'torch_17', 'torch_18'] },
  { dir: 'trees', stems: ['tree_01', 'tree_02', 'tree_03', 'tree_04', 'tree_05', 'tree_06', 'tree_07', 'tree_08', 'tree_09', 'tree_10', 'tree_11', 'tree_12', 'tree_13', 'tree_14', 'tree_15'] },
];
