"""生成「假光晕」贴图（程序绘制，无第三方素材，授权干净）。

为什么是假光：SRW 素材没有法线贴图，Phaser Light2D 那种逐像素光照做不出正确效果，
成本也高。这里只出一张白到透明的径向渐变，运行时用 ADD 混合 + setTint 着色，
贴在火把/路灯脚下，昼夜联动调 alpha。够用、便宜、不糊。

用法：
    python scripts/make_glow.py
输出：
    frontend/public/assets/pixel/effects/light/glow_soft.png  (128×128 RGBA)
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from png_io import write_png  # noqa: E402

OUT = ROOT / 'frontend/public/assets/pixel/effects/light/glow_soft.png'
SIZE = 128


def build() -> tuple[int, int, bytearray]:
    """径向衰减：中心不透明，边缘 0；指数 2.2 让光晕边界更柔和。"""
    px = bytearray(SIZE * SIZE * 4)
    half = (SIZE - 1) / 2
    for y in range(SIZE):
        for x in range(SIZE):
            dx = (x - half) / half
            dy = (y - half) / half
            r = min(1.0, (dx * dx + dy * dy) ** 0.5)
            a = (1.0 - r) ** 2.2
            i = (y * SIZE + x) * 4
            # 纯白 + alpha 衰减：运行时靠 setTint 上色，一张贴图通吃暖/冷。
            px[i] = 255
            px[i + 1] = 255
            px[i + 2] = 255
            px[i + 3] = int(a * 255)
    return SIZE, SIZE, px


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    w, h, px = build()
    write_png(str(OUT), w, h, px)
    print(f'写入 {OUT.relative_to(ROOT)}  {w}×{h}')


if __name__ == '__main__':
    main()
