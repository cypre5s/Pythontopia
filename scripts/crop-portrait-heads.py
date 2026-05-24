#!/usr/bin/env python3
# coding: utf-8
"""
R-13 §-1 B1 续修：批量把 LPC sprite sheet 的 portrait 切出左上第一帧（idle 朝下），
统一输出 32x48 单帧头像 PNG 到 `portraits/heads/portrait_<key>_head.png`，
然后 DialogModal.vue 切到 `_head.png` 路径，CSS 简化到不再处理 sprite sheet。

策略：
  1. 所有 48 宽 portrait（48x64/72/76/80/96）按 LPC 3 列 spritesheet 处理：取左上 16x24
     （idle 朝下第一帧），输出 32x48（2x scale 保留像素感）；
  2. 异类大尺寸 portrait（153~187 x 128）取左上 32x32（这些不是 LPC sheet，多为
     拼接装饰物或 portrait + body 混排），输出 32x48 时按比例适配；
  3. 直接覆盖原文件不安全（CDN 缓存），所以输出到 heads/ 子目录，DialogModal
     用 `${portrait}` 路径 → `${portrait_head}` 路径。

约束：
  - 仅依赖 PIL（已被 Pythontopia services 用于截图 / 头像，无新依赖）；
  - 切完打印每张图的 source vs head 尺寸，方便人眼校验；
  - 输出文件 LICENSE 继承原文件（CC-BY-SA 3.0 / CC-BY 4.0，归类 LPC LiberatedPixelCup）。
"""
from __future__ import annotations

import os
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    print("[crop-portrait-heads] 缺少 Pillow，请 pip install Pillow", file=sys.stderr)
    sys.exit(1)

ROOT = Path(__file__).resolve().parent.parent
PORTRAITS_DIR = ROOT / "frontend" / "public" / "assets" / "pixel" / "portraits"
OUT_DIR = PORTRAITS_DIR / "heads"
OUT_DIR.mkdir(parents=True, exist_ok=True)

FRAME_W = 16
FRAME_H = 24
TARGET_W = 32
TARGET_H = 48


def crop_lpc_first_frame(img: Image.Image) -> Image.Image:
    """LPC 3 列 sprite sheet → 左上 16x24（idle 朝下）→ 放大到 32x48。"""
    w, h = img.size
    fw = min(FRAME_W, w)
    fh = min(FRAME_H, h)
    frame = img.crop((0, 0, fw, fh))
    return frame.resize((TARGET_W, TARGET_H), Image.NEAREST)


def crop_oddball_head(img: Image.Image) -> Image.Image:
    """异类大尺寸 portrait（如 187x128）：取左上 32x32 头像区域，放大到 32x48。"""
    w, h = img.size
    side = min(32, w, h)
    frame = img.crop((0, 0, side, side))
    return frame.resize((TARGET_W, TARGET_H), Image.NEAREST)


def main() -> int:
    files = sorted(p for p in PORTRAITS_DIR.iterdir() if p.suffix == ".png" and p.is_file())
    if not files:
        print(f"[crop-portrait-heads] 目录为空: {PORTRAITS_DIR}", file=sys.stderr)
        return 1

    print(f"[crop-portrait-heads] 处理 {len(files)} 张 portrait")
    for src in files:
        img = Image.open(src).convert("RGBA")
        w, h = img.size
        if w == 48:
            head = crop_lpc_first_frame(img)
            kind = "lpc-3col"
        else:
            head = crop_oddball_head(img)
            kind = "oddball-32x32"
        out = OUT_DIR / src.name.replace(".png", "_head.png")
        head.save(out)
        print(f"  {src.name:48s} {w}x{h:<4d} → {kind:14s} → {out.name} ({TARGET_W}x{TARGET_H})")

    print(f"[crop-portrait-heads] 完成，输出目录: {OUT_DIR}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
