#!/usr/bin/env python3
"""build_ulpc_characters.py — Pythontopia R-12 动作模组（方案A）。

用**官方 Universal-LPC-Spritesheet-Character-Generator** 的整套素材库 + sheet_definitions
（z 序/路径/动画清单）+ 官方 expanded 表布局（sources/state/constants.ts 的
ANIMATION_OFFSETS），合成「全动作」角色精灵表（含 run/jump/idle/sit/emote + 基础 6 动作）。

非手搓美术：所有像素来自官方 ULPC 素材库（CC-BY-SA / CC-BY / OGA-BY / GPL，见各
sheet_definition 的 credits）。本脚本只做忠实合成（按 zPos 叠层、按官方 y 偏移摆放）。

依赖：Pillow。源仓库默认 /home/cypress/ulpc-gen（git clone 官方仓库）。
输出：frontend/public/assets/pixel/sprites/ulpc/<name>.png（832×2688，13列×42行，64px）。
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

try:
    from PIL import Image, ImageChops
except ImportError:  # pragma: no cover
    sys.exit("需要 Pillow：pip install Pillow")

REPO = Path(__file__).resolve().parent.parent
ULPC = Path("/home/cypress/ulpc-gen")
SPRITES = ULPC / "spritesheets"
SHEETDEFS = ULPC / "sheet_definitions"
OUT = REPO / "frontend/public/assets/pixel/sprites/ulpc"

CELL = 64
WIDTH = 13 * CELL  # 832

# 取官方 constants.ts 的 ANIMATION_OFFSETS（像素 y）。仅用到这批（不含 combat/1h）。
ANIM_ROWS = {
    "spellcast": 0,
    "thrust": 4,
    "walk": 8,
    "slash": 12,
    "shoot": 16,
    "hurt": 20,
    "climb": 21,
    "idle": 22,
    "jump": 26,
    "sit": 30,
    "emote": 34,
    "run": 38,
}
# 每个动画占多少行（多向=4，单向=1）。决定画布总高与是否越界。
ANIM_DIRROWS = {
    "hurt": 1,
    "climb": 1,
}
SHEET_ROWS = 42  # run(38)+4
HEIGHT = SHEET_ROWS * CELL  # 2688


def find_def(name: str) -> Path:
    matches = list(SHEETDEFS.rglob(f"{name}.json"))
    if not matches:
        raise FileNotFoundError(f"找不到 sheet_definition: {name}")
    return matches[0]


def category_of(def_name: str) -> str:
    if def_name == "body":
        return "body"
    for cat in ("hair", "torso", "legs", "feet", "head", "headwear", "arms"):
        if def_name.startswith(cat):
            return cat
    return "other"


def collect_layers(def_names: list[str], body_type: str) -> list[tuple[int, str, str]]:
    """返回 [(zPos, spritesheet_relative_dir, category)]，按 zPos 升序。"""
    layers: list[tuple[int, str, str]] = []
    for name in def_names:
        d = json.load(open(find_def(name), encoding="utf-8"))
        cat = category_of(name)
        for key in d:
            if not key.startswith("layer_"):
                continue
            layer = d[key]
            z = int(layer.get("zPos", 10))
            path = layer.get(body_type) or layer.get("male") or layer.get("female")
            if not path:
                continue
            layers.append((z, path.rstrip("/"), cat))
    layers.sort(key=lambda t: t[0])
    return layers


def tint_multiply(img: Image.Image, color: tuple[int, int, int]) -> Image.Image:
    """正片叠底着色：白/浅底布料 → 目标色（保留明暗与 alpha）。"""
    rgb = img.convert("RGB")
    solid = Image.new("RGB", img.size, color)
    out = ImageChops.multiply(rgb, solid)
    out.putalpha(img.split()[-1])
    return out


def build(def_names: list[str], body_type: str, tints: dict[str, tuple[int, int, int]] | None) -> Image.Image:
    canvas = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    layers = collect_layers(def_names, body_type)
    tints = tints or {}
    for anim, row in ANIM_ROWS.items():
        y = row * CELL
        for _z, rel, cat in layers:
            png = SPRITES / rel / f"{anim}.png"
            if not png.is_file():
                continue
            img = Image.open(png).convert("RGBA")
            if cat in tints:
                img = tint_multiply(img, tints[cat])
            # 防越界：贴图不能超出画布。
            if y + img.height > HEIGHT or img.width > WIDTH:
                img = img.crop((0, 0, min(img.width, WIDTH), min(img.height, HEIGHT - y)))
            canvas.alpha_composite(img, (0, y))
    return canvas


# 角色配方：def 名按「从底到顶」无所谓（脚本按 zPos 排序），列出即可。
# 主角 Sara 风：女性体 + 长发 + 长袖 + 裤 + 鞋（均支持 expanded 动画）。
RECIPES: dict[str, dict] = {
    # 主角 Sara 风：女性体 + 长发 + 长袖 + 裤 + 鞋（全 expanded 动画）。蓝裙金棕发。
    "player_sara2": {
        "body": "female",
        "defs": ["body", "heads_human_female", "hair_long", "torso_clothes_longsleeve", "legs_leggings", "feet_shoes_sara"],
        "tints": {"torso": (95, 140, 210), "legs": (70, 85, 120), "hair": (190, 140, 70)},
    },
    # 8 个变体 NPC：不同体型/发型/头型/衣裤 + 逐图层正片叠底着色 → 外观各不相同。
    "ulpc_villager_f": {"body": "female", "defs": ["body", "heads_human_female", "hair_bob", "torso_clothes_tshirt", "legs_pants", "feet_slippers"],
        "tints": {"torso": (215, 120, 150), "legs": (105, 95, 140), "hair": (150, 95, 55)}},
    "ulpc_villager_m": {"body": "male", "defs": ["body", "heads_human_male", "hair_relm_short", "torso_clothes_shortsleeve", "legs_pants2", "feet_boots_basic"],
        "tints": {"torso": (185, 85, 70), "legs": (95, 80, 70), "hair": (75, 50, 40)}},
    "ulpc_scholar": {"body": "male", "defs": ["body", "heads_human_male_gaunt", "hair_natural", "torso_clothes_longsleeve2_buttoned", "legs_formal", "feet_boots_basic"],
        "tints": {"torso": (120, 90, 165), "legs": (80, 70, 95), "hair": (120, 110, 95)}},
    "ulpc_worker": {"body": "male", "defs": ["body", "heads_human_male", "hair_afro", "torso_clothes_shortsleeve_polo", "legs_shorts", "feet_sandals"],
        "tints": {"torso": (215, 175, 80), "legs": (95, 120, 85), "hair": (60, 45, 38)}},
    "ulpc_lady": {"body": "female", "defs": ["body", "heads_human_female", "hair_long", "torso_clothes_shortsleeve_cardigan", "legs_leggings", "feet_slippers"],
        "tints": {"torso": (120, 185, 175), "legs": (150, 130, 90), "hair": (165, 110, 65)}},
    "ulpc_youth": {"body": "female", "defs": ["body", "heads_human_female_small", "hair_lob", "torso_clothes_tshirt_scoop", "legs_shorts", "feet_sandals"],
        "tints": {"torso": (110, 170, 215), "legs": (170, 150, 95), "hair": (115, 78, 55)}},
    "ulpc_elder": {"body": "male", "defs": ["body", "heads_human_male_elderly", "hair_relm_short", "torso_clothes_longsleeve", "legs_hose", "feet_boots_basic"],
        "tints": {"torso": (135, 135, 145), "legs": (90, 90, 100), "hair": (205, 205, 205)}},
    "ulpc_guard": {"body": "male", "defs": ["body", "heads_human_male", "hair_bob", "torso_clothes_longsleeve2_buttoned", "legs_pants", "feet_boots_basic"],
        "tints": {"torso": (120, 130, 155), "legs": (78, 82, 98), "hair": (70, 55, 45)}},
}


def main() -> None:
    if not SPRITES.is_dir():
        sys.exit(f"找不到 ULPC 素材库：{SPRITES}（先 git clone 官方仓库到 {ULPC}）")
    OUT.mkdir(parents=True, exist_ok=True)
    only = sys.argv[1] if len(sys.argv) > 1 else None
    for name, rec in RECIPES.items():
        if only and name != only:
            continue
        sheet = build(rec["defs"], rec["body"], rec.get("tints"))
        dest = OUT / f"{name}.png"
        sheet.save(dest)
        print(f"[ulpc] {dest.relative_to(REPO)}  {sheet.size[0]}x{sheet.size[1]}  defs={rec['defs']}")


if __name__ == "__main__":
    main()
