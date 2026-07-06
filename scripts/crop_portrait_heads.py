#!/usr/bin/env python3
# coding: utf-8
"""批量裁切 NPC portrait 为单帧头像。

输出 32×48 PNG 到 portraits/heads/portrait_<key>_head.png，
供 DialogModal / DialogTree 使用 _head.png 路径。

裁切策略：
  - 48 宽 portrait（LPC 3 列 sheet）：取 idle 朝下首帧 16×24，2x 放大至 32×48；
  - 大尺寸异类 portrait（153~187×128）：取左上 32×32 适配至 32×48；
  - 64×64 composite NPC：从 sprites/npc-composites 取 down/正面 idle 帧，
    刷新 portrait_<key>.png 后再输出 _head.png。

输出至 heads/ 子目录，避免覆盖原文件引发 CDN 缓存问题。依赖 PIL。
"""
from __future__ import annotations

import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    print("[crop_portrait_heads] 缺少 Pillow，请 pip install Pillow", file=sys.stderr)
    sys.exit(1)

ROOT = Path(__file__).resolve().parent.parent
PORTRAITS_DIR = ROOT / "frontend" / "public" / "assets" / "pixel" / "portraits"
COMPOSITE_DIR = ROOT / "frontend" / "public" / "assets" / "pixel" / "sprites" / "npc-composites"
OUT_DIR = PORTRAITS_DIR / "heads"
OUT_DIR.mkdir(parents=True, exist_ok=True)

FRAME_W = 16
FRAME_H = 24
TARGET_W = 32
TARGET_H = 48
COMPOSITE_CELL = 64
COMPOSITE_DOWN_ROW = 2

COMPOSITE_SOURCES = {
    "portrait_arena_guard.png": "npc_guard.png",
    "portrait_farmer.png": "npc_farmer.png",
    "portrait_miner.png": "npc_miner.png",
    "portrait_professor.png": "npc_professor.png",
    "portrait_sailor.png": "npc_sailor.png",
    "portrait_student_a.png": "npc_student.png",
    "portrait_trainer.png": "npc_trainer.png",
    "portrait_tutor_ai.png": "npc_tutor_ai.png",
    "portrait_workshop_master.png": "npc_workshop.png",
}


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


def crop_composite_down_idle(img: Image.Image) -> Image.Image:
    """64x64 LPC composite walk sheet → down/正面 idle → 48x64 portrait。"""
    y = COMPOSITE_DOWN_ROW * COMPOSITE_CELL
    frame = img.crop((0, y, COMPOSITE_CELL, y + COMPOSITE_CELL))
    return frame.crop((8, 0, 56, 64))


def crop_composite_head(img: Image.Image) -> Image.Image:
    """64x64 LPC composite walk sheet → down/正面 idle 的居中 32x48 头像。"""
    y = COMPOSITE_DOWN_ROW * COMPOSITE_CELL
    frame = img.crop((0, y, COMPOSITE_CELL, y + COMPOSITE_CELL))
    return frame.crop((16, 0, 48, TARGET_H))


def main() -> int:
    files = sorted(p for p in PORTRAITS_DIR.iterdir() if p.suffix == ".png" and p.is_file())
    if not files:
        print(f"[crop_portrait_heads] 目录为空: {PORTRAITS_DIR}", file=sys.stderr)
        return 1

    print(f"[crop_portrait_heads] 处理 {len(files)} 张 portrait")
    for src in files:
        composite_name = COMPOSITE_SOURCES.get(src.name)
        composite_path = COMPOSITE_DIR / composite_name if composite_name else None
        if composite_path and composite_path.exists():
            composite = Image.open(composite_path).convert("RGBA")
            portrait = crop_composite_down_idle(composite)
            portrait.save(src)
            head = crop_composite_head(composite)
            kind = f"composite-down:{composite_name}"
            w, h = composite.size
        else:
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

    print(f"[crop_portrait_heads] 完成，输出目录: {OUT_DIR}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
