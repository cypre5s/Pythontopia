#!/usr/bin/env python3
"""把 Super Retro World 源素材切成 Pythontopia 运行时用的最小资产集。

用法：
    python scripts/build_srw_assets.py

产物（全部入库；源素材在 assets/inbox/incoming/ 不入库）：
  frontend/public/assets/pixel/tilesets/super-retro-world/town_tiles.png
      16px 合成图集：单格地面/装饰 + road/dirt/fence/water 四套 47-blob 岸线块
  frontend/public/assets/pixel/tilesets/super-retro-world/town_tiles.json
      同一份数据的可读清单
  frontend/public/assets/pixel/sprites/super-retro-world/characters/chara_N.png
      48x80 = 3 帧 x 4 朝向（行序 DOWN,LEFT,RIGHT,UP），单帧 16x20
  frontend/public/assets/pixel/buildings/super-retro-world/house_NN.png
  frontend/public/assets/pixel/props/super-retro-world/<kind>/<stem>_NN.png
      统一顺序重命名成 01..NN（源包编号不连续，例 trees 没有 06/07）

同时把图集表回写进 scripts/generate-maps.mjs 的 SRW_ATLAS 块（生成器必须单文件
自包含，见 sync_generator_atlas 的说明）。

为什么用 Python 不用 Node：仓库既有同类作者工具 scripts/build_ulpc_characters.py、
scripts/make_cloud_fx.py 走的是同一套约定（一次性切片 + 产物入库）。
"""
import argparse
import json
import re
import shutil
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from png_io import read_png, write_png                  # 标准库实现，不拉 PIL

REPO = Path(__file__).resolve().parent.parent
SRC = REPO / 'assets/inbox/incoming/super-retro-world'
PIXEL = REPO / 'frontend/public/assets/pixel'
OUT_TILESET = PIXEL / 'tilesets/super-retro-world'
OUT_CHARS = PIXEL / 'sprites/super-retro-world/characters'
OUT_HOUSES = PIXEL / 'buildings/super-retro-world'
OUT_PROPS = PIXEL / 'props/super-retro-world'

T = 16
ATLAS_COLS = 32

# ---- 单格地面 / 装饰块 ----
# 装饰块是按「与底草色 (113,170,52) 的调色板距离」筛过的：距离 >40 的浅青柠块
# （例 (4,8) 平均色 159,198,54）铺出去像晒白斑块，已剔除。
SINGLES = [
    ('grass', ('tileset', 55, 2)),            # 纯色草皮，重复不出条纹
    ('grass_tall_a', ('tileset', 52, 2)),
    ('grass_tall_b', ('tileset', 53, 2)),
    ('tuft_a', ('tileset', 12, 9)),           # 距底色 0
    ('tuft_b', ('tileset', 13, 9)),           # 0
    ('tuft_c', ('tileset', 9, 11)),           # 9
    ('tuft_d', ('tileset', 12, 10)),          # 9
    ('tuft_e', ('tileset', 11, 11)),          # 17
    ('tuft_f', ('tileset', 13, 11)),          # 43（最深一档，只在林缘用）
    ('flower_a', ('tileset', 17, 9)),
    ('flower_b', ('tileset', 18, 9)),
    ('flower_c', ('tileset', 19, 9)),
    ('flower_d', ('tileset', 20, 9)),
    ('flower_e', ('tileset', 21, 9)),
    ('bush_a', ('tileset', 17, 2)),
    ('bush_b', ('tileset', 22, 1)),
    ('bush_c', ('tileset', 10, 10)),
    ('bush_d', ('tileset', 10, 12)),
    ('bush_e', ('tileset', 17, 4)),
    ('blade_a', ('tileset', 5, 1)),           # 深草叶：制造阴影
    ('blade_b', ('tileset', 8, 1)),
    ('blade_c', ('tileset', 1, 6)),
    # 矿洞土丘 3×3；洞口块是底边中心那一格（草檐 + 黑腔）
    ('mound_tl', ('tileset', 49, 15)),
    ('mound_tc', ('tileset', 50, 15)),
    ('mound_tr', ('tileset', 51, 15)),
    ('mound_ml', ('tileset', 49, 16)),
    ('mound_mc', ('tileset', 50, 16)),
    ('mound_mr', ('tileset', 51, 16)),
    ('mound_bl', ('tileset', 49, 17)),
    ('mound_bc', ('tileset', 50, 17)),
    ('mound_br', ('tileset', 51, 17)),
    ('cave_mouth', ('tileset', 52, 17)),
]

# ---- 四套 47-blob autotile：source / terrain_set / terrain（取自 .tres）----
BLOBS = [
    ('road', ('autotile', 2, 0, 2)),
    ('dirt', ('autotile', 2, 0, 0)),
    ('fence', ('autotile', 2, 3, 0)),
    ('water', ('anim', 1, 4, 0)),
]
BLOB_ORDER = ('road', 'dirt', 'fence', 'water')

PROPS = [
    # (输出子目录, 源目录, 源文件名前缀, 输出词干)
    ('trees', 'Trees', 'tree', 'tree'),
    ('rocks', 'Rocks', 'rock', 'rock'),
    ('lamps', 'Lamps', 'lamp', 'lamp'),
    ('barrels', 'Barrels', 'barrel', 'barrel'),
    ('crates', 'Crates', 'crate', 'crate'),
    ('potted_plants', 'Potted plants', 'potted_plant', 'potted_plant'),
    ('pots', 'Pots', 'pot', 'pot'),
    ('torches', 'Torches', 'Torch', 'torch'),
]

SIDES = ['top_side', 'top_right_corner', 'right_side', 'bottom_right_corner',
         'bottom_side', 'bottom_left_corner', 'left_side', 'top_left_corner']


def crop_first_frame(src, dst, fw):
    """多帧横排精灵表 -> 只保留第一帧。"""
    w, h, px = read_png(str(src))
    out = bytearray(fw * h * 4)
    for y in range(h):
        for x in range(fw):
            so = (y * w + x) * 4
            do = (y * fw + x) * 4
            out[do:do + 4] = px[so:so + 4]
    write_png(str(dst), fw, h, out)


def load_sheets():
    return {
        'tileset': read_png(str(SRC / 'Tilesets/SuperRetroWorld_Farming_Tileset.png')),
        'autotile': read_png(str(SRC / 'Tilesets/SuperRetroWorld_Farming_Autotile.png')),
        'anim': read_png(str(SRC / 'Tilesets/SuperRetroWorld_Farming_Autotile_Anim.png')),
    }


def tile_px(sheets, ref):
    """从源图集取一格 16x16。ref = (图集名, 列, 行)。"""
    sheet, cx, cy = ref
    w, _h, px = sheets[sheet]
    out = bytearray(T * T * 4)
    for y in range(T):
        for x in range(T):
            so = ((cy * T + y) * w + cx * T + x) * 4
            do = (y * T + x) * 4
            out[do:do + 4] = px[so:so + 4]
    return out


def parse_blob_tables():
    """从 .tres 的 terrains_peering_bits 还原 mask -> (atlas_x, atlas_y)。

    SIDES 顺序与 Godot 一致。角落位只在其两侧侧边位都亮时才有意义 —— 运行时查表
    必须做同样规范化，否则会命中不到块而回退到内部块，在路/水边留下硬切边。
    """
    text = (SRC / 'Tilesets/Super_Retro_Tilesets.tres').read_text(encoding='utf-8', errors='ignore')
    tiles = defaultdict(dict)
    src_index, in_src = -1, False
    for line in text.splitlines():
        if line.startswith('[sub_resource type="TileSetAtlasSource"'):
            src_index += 1
            in_src = True
            continue
        if line.startswith('['):
            in_src = False
            continue
        if not in_src:
            continue
        m = re.match(r'^(\d+):(\d+)/(\d+)(?:/(.+?))? = (-?\d+)$', line)
        if m:
            tiles[(src_index, int(m.group(1)), int(m.group(2)), int(m.group(3)))][
                m.group(4) or '_exists'] = int(m.group(5))

    out = {}
    for name, (_sheet, source, tset, terrain) in BLOBS:
        table = defaultdict(list)
        for (si, x, y, _alt), f in tiles.items():
            if si != source or f.get('terrain_set') != tset or f.get('terrain') != terrain:
                continue
            mask = 0
            for bit, side in enumerate(SIDES):
                if f.get(f'terrains_peering_bit/{side}') == terrain:
                    mask |= 1 << bit
            table[mask].append((x, y))
        out[name] = {int(k): v[0] for k, v in table.items()}
    return out


RUNTIME_TS = REPO / 'shared/world/srwAssets.config.ts'


def emit_runtime_manifest(manifest):
    """生成 shared/world/srwAssets.config.ts。

    游戏和地图预览使用这份清单，避免"手抄文件名"和实际切片走偏：
    上一版就是这么错的（生成器引用 srw_tree_06，而切片目录里根本没有这个文件，
    结果是运行时静默画不出树）。
    """
    def stems(directory):
        return sorted(f.stem for f in (PIXEL / directory).glob('*.png'))

    chars = stems('sprites/super-retro-world/characters')
    houses = stems('buildings/super-retro-world')
    prop_root = PIXEL / 'props/super-retro-world'
    prop_dirs = sorted(d.name for d in prop_root.iterdir() if d.is_dir())

    def ts_list(items):
        return '[' + ', '.join(f"'{n}'" for n in items) + ']'

    body = [
        '/* eslint-disable */',
        '/**',
        ' * Super Retro World 运行时资产清单。',
        ' *',
        ' * 由 scripts/build_srw_assets.py 生成，勿手改：重新切片后请重跑该脚本。',
        ' * 游戏和地图预览按这份表加载贴图，因此切片与运行时不可能走偏。',
        ' *',
        ' * 贴图键统一为 `srw_<文件名>`，与 generate-maps.mjs 地图对象里的 sprite 字段同名。',
        ' */',
        "export const SRW_TILESET_KEY = 'srw_town' as const;",
        "export const SRW_TILESET_URL = '/assets/pixel/tilesets/super-retro-world/town_tiles.png' as const;",
        f"export const SRW_TILE_SIZE = {int(manifest['tileSize'])};",
        f"export const SRW_TILE_COLUMNS = {int(manifest['columns'])};",
        f"export const SRW_TILE_COUNT = {len(manifest['single']) + sum(len(v) for v in manifest['blob'].values())};",
        '',
        '/** 角色表：48x80 = 3 帧 x 4 朝向，单帧 16x20。 */',
        f'export const SRW_CHARA_KEYS = {ts_list(chars)};',
        'export const SRW_CHARA_FRAME = { frameWidth: 16, frameHeight: 20 } as const;',
        '',
        '/** 整栋房屋精灵（一文件一张，尺寸各异）。 */',
        f'export const SRW_HOUSE_KEYS = {ts_list(houses)};',
        '',
        '/** 道具精灵：按目录分组（目录名参与 URL 拼装）。 */',
        'export const SRW_PROP_GROUPS: ReadonlyArray<{ dir: string; stems: string[] }> = [',
    ]
    for d in prop_dirs:
        body.append(f"  {{ dir: '{d}', stems: {ts_list(stems(f'props/super-retro-world/{d}'))} }},")
    body += ['];', '']
    RUNTIME_TS.write_text('\n'.join(body), encoding='utf-8')
    print(f'运行时清单 -> {RUNTIME_TS.relative_to(REPO)} '
          f'(角色 {len(chars)} / 房屋 {len(houses)} / 道具目录 {len(prop_dirs)})')


def sync_generator_atlas(manifest, entry_count):
    """把图集表回写进 generate-maps.mjs 的 SRW_ATLAS 块。

    为什么回写而不是让生成器运行时读 town_tiles.json：
    frontend/tests/assets/generatedMaps.spec.ts 只把 generate-maps.mjs 单文件复制到
    临时目录执行、再与提交产物比哈希，任何"运行时读仓库其他文件"都会在那环境里失败。
    """
    gen = REPO / 'scripts/generate-maps.mjs'
    text = gen.read_text(encoding='utf-8')
    begin = text.find('// BEGIN SRW_ATLAS')
    finish = text.find('// END SRW_ATLAS')
    if begin < 0 or finish < 0:
        raise SystemExit(f'{gen} 缺少 // BEGIN SRW_ATLAS / // END SRW_ATLAS 标记，拒绝猜测插入位置')
    tail_start = text.index('\n', finish) + 1

    def int_map(d):
        body = ', '.join(f'{int(k)}: {int(v)}'
                         for k, v in sorted(d.items(), key=lambda kv: int(kv[0])))
        return '{ ' + body + ' }'

    single = ', '.join(f'{k}: {v}' for k, v in manifest['single'].items())
    blobs = '\n'.join(f'    {name}: {int_map(manifest["blob"][name])},' for name in BLOB_ORDER)
    props = ', '.join(f'{k}: {v}' for k, v in sorted(manifest['props'].items()))
    char_sheet = json.dumps(manifest['charSheet']).replace('"', "'")
    block = '\n'.join([
        '// BEGIN SRW_ATLAS（由 scripts/build_srw_assets.py 从 town_tiles.json 回写，勿手改）',
        'const SRW_ATLAS = {',
        "  image: '../pixel/tilesets/super-retro-world/town_tiles.png',",
        f"  columns: {manifest['columns']},",
        f'  tileCount: {entry_count},',
        f"  tileSize: {manifest['tileSize']},",
        f'  single: {{ {single} }},',
        '  blob: {',
        blobs,
        '  },',
        f'  props: {{ {props} }},',
        f'  charSheet: {char_sheet},',
        '};',
        '// END SRW_ATLAS',
    ])
    # 注意：block 末行是 `// END SRW_ATLAS`（无尾换行），tail_start 已跳过原块尾换行，
    # 这里必须自己补一个，否则会和下一行粘成 `// END SRW_ATLASconst gid = ...`，
    # 把 `const gid` 注释掉 → 生成器运行时报 gid is not defined。
    new_text = text[:begin] + block + '\n' + text[tail_start:]
    if not new_text[begin + len(block):].startswith('\n'):
        raise SystemExit('SRW_ATLAS 回写后换行丢失，已中止')
    # 自保：回写后关键函数必须仍在。上一版按 `\n};\n` 找块尾，命中了后面某个常量的 };
    # ，结果把整个布局引擎吞掉了（854 行），这种事故不能再犯。
    for must in ('function makeGrid', 'function bakeGrid', 'function connectAll',
                 'function buildIndex', 'function generateTownHub',
                 'const gid = (index) => index + 1;'):
        if must not in new_text:
            raise SystemExit(f'回写后丢失 {must}，已中止且不落盘')
    gen.write_text(new_text, encoding='utf-8')


def source_files(directory, prefix):
    """Godot exports can contain mixed-case names on case-sensitive filesystems."""
    return sorted((p for p in directory.glob('*.png') if p.name.casefold().startswith(prefix.casefold() + '_')),
                  key=lambda p: p.name.casefold())


def main():
    global SRC
    parser = argparse.ArgumentParser(description='Prepare SRW slices, or compare a source pack without writing files.')
    parser.add_argument('--source', type=Path, default=SRC, help='Source asset directory (read-only)')
    parser.add_argument('--check', action='store_true', help='Validate and compare the source pack; do not write any outputs')
    args = parser.parse_args()
    SRC = args.source.resolve()
    chars = SRC / 'Characters'
    if not source_files(chars, 'chara'):
        chars = chars / 'Characters'
    characters = source_files(chars, 'chara')
    houses = source_files(SRC / 'Props/Houses/Sprites', 'house')
    props = {sub: source_files(SRC / 'Props' / src_dir / 'Sprites', prefix) for sub, src_dir, prefix, _stem in PROPS}
    if not characters or not houses or any(not files for files in props.values()):
        raise SystemExit('源包缺少角色、房屋或必需道具；未修改任何输出。')
    sheets = load_sheets()
    blobs = parse_blob_tables()

    # ---------- 合成图集 ----------
    entries = [(key, tile_px(sheets, ref)) for key, ref in SINGLES]
    blob_index = {}
    for name, (sheet, _s, _t, _e) in BLOBS:
        maskmap = {}
        for mask, (ax, ay) in sorted(blobs[name].items()):
            maskmap[mask] = len(entries)
            entries.append((f'{name}_{mask}', tile_px(sheets, (sheet, ax, ay))))
        blob_index[name] = maskmap
        print(f'blob {name}: {len(maskmap)} 个合法掩码')

    rows = -(-len(entries) // ATLAS_COLS)
    W, H = ATLAS_COLS * T, rows * T
    atlas = bytearray(W * H * 4)
    for i, (_key, px) in enumerate(entries):
        ox, oy = (i % ATLAS_COLS) * T, (i // ATLAS_COLS) * T
        for y in range(T):
            for x in range(T):
                so = (y * T + x) * 4
                do = ((oy + y) * W + ox + x) * 4
                atlas[do:do + 4] = px[so:so + 4]
    if args.check:
        current = read_png(str(OUT_TILESET / 'town_tiles.png'))
        print(json.dumps({'source': str(SRC), 'characters': len(characters), 'houses': len(houses),
                          'props': {key: len(files) for key, files in props.items()},
                          'atlas': [W, H], 'atlas_matches_current': current == (W, H, atlas), 'writes': False},
                         ensure_ascii=False))
        return
    for d in (OUT_TILESET, OUT_CHARS, OUT_HOUSES, OUT_PROPS):
        d.mkdir(parents=True, exist_ok=True)
    write_png(str(OUT_TILESET / 'town_tiles.png'), W, H, atlas)

    manifest = {
        'comment': 'Super Retro World 切片图集。生成器只读这份清单，不读 assets/inbox。',
        'authoritative': '由 scripts/build_srw_assets.py 生成，勿手改',
        'tileSize': T,
        'columns': ATLAS_COLS,
        'image': 'town_tiles.png',
        'single': {key: i for i, (key, _px) in enumerate(entries) if not key.startswith(
            ('road_', 'dirt_', 'fence_', 'water_'))},
        'blob': {name: {str(k): v for k, v in m.items()} for name, m in blob_index.items()},
        'props': {},
        'charSheet': {
            'size': [48, 80],
            'frame': [16, 20],
            'framesPerDir': 3,
            'dirRowOrder': ['DOWN', 'LEFT', 'RIGHT', 'UP'],
            'idleFrame': 0,
        },
    }

    # ---------- 角色 / 房屋 ----------
    for old in OUT_CHARS.glob('*.png'):
        old.unlink()
    n = 0
    for p in characters:
        shutil.copyfile(p, OUT_CHARS / p.name)
        n += 1
    manifest['props']['chara'] = n
    print(f'角色 {n} 张')
    for old in OUT_HOUSES.glob('*.png'):
        old.unlink()
    n = 0
    for p in houses:
        shutil.copyfile(p, OUT_HOUSES / p.name)
        n += 1
    manifest['props']['house'] = n
    print(f'房屋 {n} 张')

    # ---------- 道具：统一顺序重命名 ----------
    # 源包编号不连续（trees 实际是 01..05,08..13,29..32），沿用原名会让生成器按下标
    # 取模时引用到不存在的文件（实测踩过 srw_tree_06）。
    for sub, src_dir, prefix, stem in PROPS:
        dst = OUT_PROPS / sub
        dst.mkdir(parents=True, exist_ok=True)
        for old in dst.glob('*.png'):
            old.unlink()
        files = props[sub]
        for i, src in enumerate(files, 1):
            if stem == 'torch':
                # 源是 48x32 的三帧横排动画表；小镇阶段只用静态第一帧，
                # 等"视觉强化"那一步再换成动画精灵表（届时 runtime 按 spritesheet 载入）。
                crop_first_frame(src, dst / f'{stem}_{i:02d}.png', 16)
            else:
                shutil.copyfile(src, dst / f'{stem}_{i:02d}.png')
        manifest['props'][stem] = len(files)
        print(f'道具 {sub}: {len(files)} 张 -> {stem}_01..{stem}_{len(files):02d}')

    emit_runtime_manifest(manifest)

    (OUT_TILESET / 'town_tiles.json').write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    sync_generator_atlas(manifest, len(entries))
    print(f'图集 {W}x{H}（{len(entries)} 格 / {rows} 行）；清单已写出；'
          f'已回写 scripts/generate-maps.mjs 的 SRW_ATLAS')


if __name__ == '__main__':
    main()
