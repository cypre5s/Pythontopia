#!/usr/bin/env python3
"""build-cottages.py — 从 LPC 茅草屋 preview 裁出「作者已正确装配」的整屋 sprite。

来源：assets/inbox/buildings_npc/lpc_thatched-roof-cottage/preview.png（CC-BY-SA 3.0，
作者 bluecarrot16 / daneeklu / Sharm）。preview 里的房屋由原作者按正确 3/4 透视装配，
直接裁切并把草绿底色 key 成透明 + 去边，得到透视一致、观感正确的整屋（不再是
「平面墙 + 俯视屋顶」的拼接错位）。

用法：python3 scripts/build-cottages.py
"""
from __future__ import annotations

import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    sys.exit("需要 Pillow")

REPO = Path(__file__).resolve().parent.parent
SRC = REPO / "assets/inbox/buildings_npc/lpc_thatched-roof-cottage/preview.png"
OUT = REPO / "frontend/public/assets/pixel/sprites/cottages"

# 从 preview 裁出的整屋框（x0,y0,x1,y1），坐标经屋顶投影自动定位后人工微调。
RECIPES: dict[str, tuple[int, int, int, int]] = {
    "cottage_tan": (538, 52, 742, 332),    # 经典正面尖顶茅草屋
    "cottage_cream": (203, 70, 410, 332),  # 宽体平顶茅草屋
    "cottage_stone": (873, 60, 1042, 332), # 棕顶白墙屋
}


def is_grass(r: int, g: int, b: int, a: int) -> bool:
    return a > 0 and g > 64 and g >= r * 1.1 and g >= b * 1.12


def key_grass(im: Image.Image) -> Image.Image:
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if is_grass(r, g, b, a):
                px[x, y] = (r, g, b, 0)
    return im


def trim(im: Image.Image) -> Image.Image:
    bb = im.getbbox()
    return im.crop(bb) if bb else im


def main() -> None:
    if not SRC.is_file():
        sys.exit(f"找不到 preview：{SRC}")
    OUT.mkdir(parents=True, exist_ok=True)
    preview = Image.open(SRC).convert("RGBA")
    for name, box in RECIPES.items():
        img = trim(key_grass(preview.crop(box).copy()))
        img.save(OUT / f"{name}.png")
        print(f"[cottage] {name}.png {img.size[0]}x{img.size[1]}")


if __name__ == "__main__":
    main()
