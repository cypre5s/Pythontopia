"""把 effects/weather/cloud.png 的实心青色背景抠成透明，输出 cloud_fx.png。

原图是 120x120 的「4 朵云 + 纯青底」贴图，直接当云层用会在游戏里铺出
一整块青色矩形（线上实测）。这里用从边缘 flood fill 的方式只抠掉外部背景，
云朵内部的青色描边/阴影保持不动。

用法：python scripts/make_cloud_fx.py
"""
import struct
import zlib
from collections import deque
from pathlib import Path

ASSET_DIR = Path(__file__).resolve().parents[1] / 'frontend/public/assets/pixel/effects/weather'
SRC = ASSET_DIR / 'cloud.png'
DST = ASSET_DIR / 'cloud_fx.png'
BG = (0, 177, 184)  # 原图背景调色板 idx1
TOLERANCE = 16


def read_png(path):
    raw = path.read_bytes()
    pos, idat, pal, trns = 8, b'', None, None
    while pos < len(raw):
        ln = struct.unpack('>I', raw[pos:pos + 4])[0]
        typ, data = raw[pos + 4:pos + 8], raw[pos + 8:pos + 8 + ln]
        if typ == b'IHDR':
            w, h, depth, ctype, _, _, interlace = struct.unpack('>IIBBBBB', data)
        elif typ == b'PLTE':
            pal = data
        elif typ == b'tRNS':
            trns = data
        elif typ == b'IDAT':
            idat += data
        elif typ == b'IEND':
            break
        pos += 12 + ln
    assert depth == 8 and interlace == 0, (depth, interlace)
    bpp = {2: 3, 3: 1, 6: 4}[ctype]
    buf = zlib.decompress(idat)
    stride = w * bpp
    px = bytearray(w * h * 4)
    prev = bytearray(stride)
    p = 0
    for y in range(h):
        f = buf[p]
        p += 1
        line = bytearray(buf[p:p + stride])
        p += stride
        for i in range(stride):
            if f == 1:
                line[i] = (line[i] + (line[i - bpp] if i >= bpp else 0)) & 0xFF
            elif f == 2:
                line[i] = (line[i] + prev[i]) & 0xFF
        prev = line
        for x in range(w):
            o = (y * w + x) * 4
            if ctype == 3:
                idx = line[x]
                px[o:o + 3] = pal[idx * 3:idx * 3 + 3]
                px[o + 3] = trns[idx] if trns and idx < len(trns) else 255
            elif ctype == 6:
                px[o:o + 4] = line[x * 4:x * 4 + 4]
            else:
                px[o:o + 3] = line[x * 3:x * 3 + 3]
                px[o + 3] = 255
    return w, h, px


def write_png(path, w, h, px):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        raw += px[y * w * 4:(y + 1) * w * 4]

    def chunk(t, d):
        return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xFFFFFFFF)

    out = b'\x89PNG\r\n\x1a\n'
    out += chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
    out += chunk(b'IDAT', zlib.compress(bytes(raw), 9))
    out += chunk(b'IEND', b'')
    path.write_bytes(out)


def is_bg(px, offset):
    r, g, b = px[offset], px[offset + 1], px[offset + 2]
    return (abs(r - BG[0]) <= TOLERANCE
            and abs(g - BG[1]) <= TOLERANCE
            and abs(b - BG[2]) <= TOLERANCE)


def largest_component_bbox(px, w, h):
    """在抠完背景的图里找最大连通块（即最大的那朵云）的包围盒。"""
    seen = bytearray(w * h)
    best = (0, 0, 0, 0, 0)  # area, minx, miny, maxx, maxy
    for start in range(w * h):
        if seen[start] or px[start * 4 + 3] == 0:
            continue
        queue = deque([start])
        seen[start] = 1
        minx = maxx = start % w
        miny = maxy = start // w
        area = 0
        while queue:
            i = queue.popleft()
            area += 1
            x, y = i % w, i // w
            minx, maxx = min(minx, x), max(maxx, x)
            miny, maxy = min(miny, y), max(maxy, y)
            for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if 0 <= nx < w and 0 <= ny < h:
                    j = ny * w + nx
                    if not seen[j] and px[j * 4 + 3] != 0:
                        seen[j] = 1
                        queue.append(j)
        if area > best[0]:
            best = (area, minx, miny, maxx, maxy)
    return best


def crop(px, w, h, minx, miny, maxx, maxy):
    cw, ch = maxx - minx + 1, maxy - miny + 1
    out = bytearray(cw * ch * 4)
    for y in range(ch):
        src = ((miny + y) * w + minx) * 4
        out[y * cw * 4:(y + 1) * cw * 4] = px[src:src + cw * 4]
    return cw, ch, out


def main():
    w, h, px = read_png(SRC)
    seen = bytearray(w * h)
    queue = deque()
    for x in range(w):
        queue.append((x, 0))
        queue.append((x, h - 1))
    for y in range(h):
        queue.append((0, y))
        queue.append((w - 1, y))
    cleared = 0
    while queue:
        x, y = queue.popleft()
        if x < 0 or y < 0 or x >= w or y >= h:
            continue
        i = y * w + x
        if seen[i]:
            continue
        seen[i] = 1
        if not is_bg(px, i * 4):
            continue
        px[i * 4 + 3] = 0
        cleared += 1
        queue.append((x + 1, y))
        queue.append((x - 1, y))
        queue.append((x, y + 1))
        queue.append((x, y - 1))
    write_png(DST, w, h, px)
    total = w * h
    print(f'{SRC.name} -> {DST.name}(全图)  {w}x{h}  抠掉背景 {cleared}/{total} 像素 '
          f'({cleared * 100 / total:.1f}%)')

    area, minx, miny, maxx, maxy = largest_component_bbox(px, w, h)
    cw, ch, cpx = crop(px, w, h, minx, miny, maxx, maxy)
    write_png(DST, cw, ch, cpx)
    print(f'{DST.name} 改为单朵云：bbox ({minx},{miny})-({maxx},{maxy})  面积 {area}px '
          f'-> 输出 {cw}x{ch}')


if __name__ == '__main__':
    main()
