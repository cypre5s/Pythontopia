#!/usr/bin/env python3
"""立绘（galgame 对话大图）管线。

两件事：
  1. `placeholders`：用 SRW 角色表 16×20 的站立帧放大，产出 **占位立绘**，
     保证「换皮之前」对话界面就有画，且画风与地图上的人物同源。
  2. `import`：把美术/生图模型产出的**成品立绘**规格化成统一尺寸，覆盖同名文件。

⚠️ 立绘路径是唯一契约：`frontend/public/assets/pixel/portraits/galgame/<npcKey>.png`
   代码只认这个路径（见 `DialogTree.vue`），换图不需要改任何代码。

规格（三条都别破，破了画面会糊）：
  - 画布 **768×1024**（3:4），**任意纯色底**（推荐 #00FF00，脚本会自动抠掉），
    人物脚底贴下沿、水平居中；
  - 运行时按 384×512 或 512 高显示，768/512 = 2 整数倍 → 最近邻缩放不失真；
  - 占位图是 96×120（= 16×20 角色表 ×6），显示时 ×4，同样是整数倍。

底色为什么要纯色：生图模型做不出透明通道（alpha），约定统一纯色底，
`import` 时用 `_remove_solid_background` 自动抠掉（四角取色 + 紧色键 + 边缘泛洪），
不需要人工抠图。

用法：
    python scripts/portraits.py placeholders            # 只补缺失的占位立绘
    python scripts/portraits.py placeholders --force    # 全部重画（会覆盖成品图！）
    python scripts/portraits.py import <图片> <npcKey>   # 导入一张成品立绘
    python scripts/portraits.py import --dir <目录>      # 批量：文件名即 npcKey
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from png_io import read_png, write_png  # noqa: E402

REPO = Path(__file__).resolve().parent.parent
CHARA_DIR = REPO / 'frontend/public/assets/pixel/sprites/super-retro-world/characters'
OUT_DIR = REPO / 'frontend/public/assets/pixel/portraits/galgame'

PORTRAIT_W, PORTRAIT_H = 768, 1024
# 占位图尺寸：16×20 的角色帧 ×6，正好 96×120，放进 96×128 画布
PLACEHOLDER_SCALE = 6
PLACEHOLDER_W = 48 // 3 * PLACEHOLDER_SCALE   # 角色表单帧宽 16 → 96
PLACEHOLDER_H = 80 // 4 * PLACEHOLDER_SCALE   # 角色表单帧高 20 → 120

# npcKey → SRW 角色表编号。
# town_hub 已落地的 7 个 NPC 由 `scripts/generate-maps.mjs` 决定（改那边要同步这里）；
# 其余 4 个 NPC 目前只存在于 `data/npcs/` 与后端对话数据里、没有世界精灵，
# 这里给固定编号纯粹是为了占位图有区别，等真立绘覆盖后此表即失效。
NPC_CHARA: dict[str, int] = {
    # —— 与 town_hub 地图对象上的 sprite 属性一致 ——
    'mayor_py': 2,
    'librarian_doc': 6,
    'workshop_master': 12,
    'coach_q': 16,
    'foreman_bug': 22,
    'sage_review': 26,
    'class_president': 30,
    # —— 未落地的 NPC（占位用）——
    'arena_guard': 4,
    'student_a': 8,
    'mine_keeper': 18,
    'professor': 24,
}


def _fit_size(w: int, h: int, box_w: int, box_h: int) -> tuple[int, int]:
    """把 (w,h) 等比缩进 box_w × box_h 的框，返回缩放后的尺寸。

    为什么不强制裁成 3:4：生图模型默认出 9:16 竖图（如 768×1360），全身人物已经
    占满整幅。再居中裁成 3:4 会砍掉 25% 高度——头或脚直接被切掉。
    立绘的显示高度由 CSS 固定（`height: min(56vh, 512px); width: auto`），
    所以宽高比本来就自由，等比缩放即可。
    """
    scale = min(box_w / w, box_h / h)
    nw = max(1, int(round(w * scale)))
    nh = max(1, int(round(h * scale)))
    return nw, nh


def _nearest_resize(src_w: int, src_h: int, px: bytes, dst_w: int, dst_h: int) -> bytearray:
    out = bytearray(dst_w * dst_h * 4)
    for y in range(dst_h):
        sy = min(src_h - 1, y * src_h // dst_h)
        row = sy * src_w
        for x in range(dst_w):
            sx = min(src_w - 1, x * src_w // dst_w)
            si = (row + sx) * 4
            di = (y * dst_w + x) * 4
            out[di:di + 4] = px[si:si + 4]
    return out


def build_placeholder(npc_key: str) -> None:
    idx = NPC_CHARA.get(npc_key)
    if idx is None:
        raise SystemExit(f'{npc_key} 没有角色表编号，请先在 NPC_CHARA 里补')
    sheet = CHARA_DIR / f'chara_{idx}.png'
    w, h, px = read_png(str(sheet))
    if (w, h) != (48, 80):
        raise SystemExit(f'{sheet.name} 尺寸 {w}x{h}，期望 48x80（3 帧 × 4 向，单帧 16x20）')
    # 站立帧 = DOWN 行（第 0 行）第 0 帧
    fw, fh = 16, 20
    frame = bytearray(fw * fh * 4)
    for y in range(fh):
        src = (y * w) * 4
        frame[y * fw * 4:(y + 1) * fw * 4] = px[src:src + fw * 4]
    big = _nearest_resize(fw, fh, bytes(frame), PLACEHOLDER_W, PLACEHOLDER_H)

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    dst = OUT_DIR / f'{npc_key}.png'
    write_png(str(dst), PLACEHOLDER_W, PLACEHOLDER_H, big)
    print(f'  {dst.name}  {PLACEHOLDER_W}x{PLACEHOLDER_H}  (chara_{idx})')


def _remove_solid_background(w: int, h: int, px: bytes) -> tuple[bytearray, tuple[int, int, int] | None]:
    """把生成图的纯色底抠成透明。

    生图模型做不出透明底，所以约定出图时用**任意纯色底**（推荐 #00FF00），
    这里自动识别并抠掉，省掉人工抠图。两步：
      1. 全局紧色键：与底色距离 ≤ TIGHT 的像素直接置透明（处理闭合区域，
         比如手臂和身体之间的缝隙）；
      2. 从四条边泛洪：距离 ≤ LOOSE 的连通像素也置透明（吃掉底色渐变和
         描边处的过渡带）。
    识别不到纯色底（四角颜色不一致）时原样返回，不乱抠——宁可让用户看到底色。
    """
    out = bytearray(px)
    TIGHT, LOOSE = 18.0, 70.0

    # 底色识别：取边框一圈像素的**逐通道中位数**。
    # 不用「四角一致」也不用众数分桶：
    #   - 角色靠边（抱脚/箱子）或底色有轻微渐变时四角会不一致；
    #   - 5bit 分桶会把 ±4 的噪声拆到相邻桶里，没一个桶能过阈值。
    # 中位数对小数量的非底色像素（角色探进边框）天然免轰，且不受噪声离散度影响。
    border: list[int] = []
    for x in range(w):
        border.append(x)
        border.append((h - 1) * w + x)
    for y in range(1, h - 1):
        border.append(y * w)
        border.append(y * w + w - 1)
    chan = [sorted(out[p * 4 + c] for p in border) for c in range(3)]
    mid = len(border) // 2
    key = (chan[0][mid], chan[1][mid], chan[2][mid])

    def dist(i: int) -> float:
        dr = out[i] - key[0]
        dg = out[i + 1] - key[1]
        db = out[i + 2] - key[2]
        return (dr * dr + dg * dg + db * db) ** 0.5

    # 要求底色至少占边框的 40%，否则当成「不是纯色底」不动手
    if sum(1 for p in border if dist(p * 4) <= LOOSE) < len(border) * 0.4:
        return out, None
    # 1. 全局紧色键
    for p in range(0, w * h):
        i = p * 4
        if out[i + 3] > 0 and dist(i) <= TIGHT:
            out[i + 3] = 0
    # 2. 从边缘泛洪（吃渐变与描边过渡带）
    seen = bytearray(w * h)
    stack: list[int] = []
    for x in range(w):
        stack.append(x)
        stack.append((h - 1) * w + x)
    for y in range(h):
        stack.append(y * w)
        stack.append(y * w + w - 1)
    while stack:
        p = stack.pop()
        if p < 0 or p >= w * h or seen[p]:
            continue
        seen[p] = 1
        i = p * 4
        if out[i + 3] == 0 or dist(i) > LOOSE:
            continue
        out[i + 3] = 0
        x, y = p % w, p // w
        if x > 0:
            stack.append(p - 1)
        if x < w - 1:
            stack.append(p + 1)
        if y > 0:
            stack.append(p - w)
        if y < h - 1:
            stack.append(p + w)

    # 3. 从透明区向内再泛洪一次，容差放宽。
    # 生图模型会在纯色底上画一块「落地投影」（比底色暗一截），这类区域往往被腿部/道具
    # 围住，第一道泛洪进不去，紧色键（距 18）也擒不到。但它们一定和透明区相接，
    # 所以从已有透明像素出发、只向「接近底色的像素」扩散，就能整块吃掉。
    # 容差 100 是安全的：角色本身的颜色（棕靴、绿帽子、绿工具包）离纯绿/品红都在 140 以上，
    # 且它们与透明区之间总隔着画风里的描边/暗部，不会一条路吃穿。
    EDGE_TOL = 100.0
    seen2 = bytearray(w * h)
    stack2 = [p for p in range(w * h) if out[p * 4 + 3] == 0]
    while stack2:
        p = stack2.pop()
        if seen2[p]:
            continue
        seen2[p] = 1
        x, y = p % w, p // w
        for q in ((p - 1) if x > 0 else -1, (p + 1) if x < w - 1 else -1,
                  (p - w) if y > 0 else -1, (p + w) if y < h - 1 else -1):
            if q < 0 or out[q * 4 + 3] == 0 or seen2[q]:
                continue
            if dist(q * 4) <= EDGE_TOL:
                out[q * 4 + 3] = 0
                stack2.append(q)
            else:
                seen2[q] = 1
    return out, key


def import_portrait(src: Path, npc_key: str) -> None:
    w, h, px = read_png(str(src))
    px, key = _remove_solid_background(w, h, bytes(px))
    key_note = f'抠底色 #{key[0]:02X}{key[1]:02X}{key[2]:02X}' if key else '底色不纯，未抠'
    nw, nh = _fit_size(w, h, PORTRAIT_W, PORTRAIT_H)
    out = _nearest_resize(w, h, bytes(px), nw, nh)
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    dst = OUT_DIR / f'{npc_key}.png'
    write_png(str(dst), nw, nh, out)
    print(f'  {src.name}  {w}x{h} → {dst.name}  {nw}x{nh}  ({key_note})')


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print(__doc__)
        return 2
    cmd = argv[1]
    if cmd == 'placeholders':
        force = '--force' in argv
        print(f'占位立绘 → {OUT_DIR.relative_to(REPO)}')
        for key in NPC_CHARA:
            dst = OUT_DIR / f'{key}.png'
            if dst.exists() and not force:
                print(f'  {key}.png  已存在，跳过（--force 覆盖）')
                continue
            build_placeholder(key)
        return 0
    if cmd == 'import':
        args = [a for a in argv[2:] if not a.startswith('--')]
        # --as key1,key2,...：生图工具习惯输出 1.png..11.png 这类无意义名字，
        # 按文件名排序后依次重命名。数量对不上直接报错，不猜。
        as_keys: list[str] = []
        if '--as' in argv:
            as_keys = argv[argv.index('--as') + 1].split(',')
            args = [a for a in args if a != argv[argv.index('--as') + 1]]
            as_keys = [k.strip() for k in as_keys if k.strip()]
            unknown = [k for k in as_keys if k not in NPC_CHARA]
            if unknown:
                raise SystemExit(f'--as 里有未登记 npcKey: {unknown}')
        if '--dir' in argv:
            src_dir = Path(args[0])
            files = sorted(src_dir.glob('*.png'))
            if as_keys and len(as_keys) != len(files):
                raise SystemExit(
                    f'--as 给了 {len(as_keys)} 个 key，但目录里有 {len(files)} 张图')
            for i, f in enumerate(files):
                import_portrait(f, as_keys[i] if as_keys else f.stem)
            return 0
        if len(args) != 2:
            print('用法: portraits.py import <图片> <npcKey>')
            return 2
        import_portrait(Path(args[0]), args[1])
        return 0
    print(__doc__)
    return 2


if __name__ == '__main__':
    raise SystemExit(main(sys.argv))
