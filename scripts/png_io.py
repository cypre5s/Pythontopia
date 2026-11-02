"""最小 PNG 读写（调色板 1/2/4/8 bit、灰度、GA、RGB、RGBA），无第三方依赖。

为什么不用 PIL：仓库里既有的同类作者工具（scripts/make_cloud_fx.py）就走标准库
struct + zlib，CI 不需要装图像库。本实现补齐了 make_cloud_fx 缺的 Paeth / Average
滤波与子字节调色板分支——Super Retro World 的 Godot 导出图正是这两种情况。
"""
import struct
import zlib


def read_png(path):
    """返回 (w, h, rgba_bytes)，rgba_bytes 长度 = w*h*4。"""
    raw = open(path, 'rb').read()
    pos, idat, pal, trns = 8, b'', None, None
    w = h = depth = ctype = interlace = 0
    while pos < len(raw):
        ln = struct.unpack('>I', raw[pos:pos + 4])[0]
        typ, data = raw[pos + 4:pos + 8], raw[pos + 8:pos + 8 + ln]
        if typ == b'IHDR':
            w, h, depth, ctype, _comp, _filt, interlace = struct.unpack('>IIBBBBB', data[:13])
        elif typ == b'PLTE':
            pal = data
        elif typ == b'tRNS':
            trns = data
        elif typ == b'IDAT':
            idat += data
        elif typ == b'IEND':
            break
        pos += 12 + ln
    if interlace:
        raise ValueError(f'不支持隔行 PNG: {path}')

    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    bits_per_pixel = channels * depth
    bpp = max(1, bits_per_pixel // 8)
    row_bytes = (w * bits_per_pixel + 7) // 8

    buf = zlib.decompress(idat)
    out = bytearray(w * h * 4)
    prev = bytearray(row_bytes)
    pos = 0
    for y in range(h):
        filt = buf[pos]
        line = bytearray(buf[pos + 1:pos + 1 + row_bytes])
        pos += 1 + row_bytes
        if filt == 1:                       # Sub
            for i in range(bpp, row_bytes):
                line[i] = (line[i] + line[i - bpp]) & 0xFF
        elif filt == 2:                     # Up
            for i in range(row_bytes):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif filt == 3:                     # Average
            for i in range(row_bytes):
                left = line[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + ((left + prev[i]) >> 1)) & 0xFF
        elif filt == 4:                     # Paeth
            for i in range(row_bytes):
                a = line[i - bpp] if i >= bpp else 0
                b = prev[i]
                c = prev[i - bpp] if i >= bpp else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        prev = line

        for x in range(w):
            o = (y * w + x) * 4
            if ctype == 3:                  # 调色板（可能是 1/2/4 bit）
                bit_off = x * depth
                byte = line[bit_off >> 3]
                shift = 8 - depth - (bit_off & 7)
                idx = (byte >> shift) & ((1 << depth) - 1)
                out[o:o + 3] = pal[idx * 3:idx * 3 + 3]
                out[o + 3] = trns[idx] if trns and idx < len(trns) else 255
            elif ctype == 6:                # RGBA
                out[o:o + 4] = line[x * 4:x * 4 + 4]
            elif ctype == 2:                # RGB
                out[o:o + 3] = line[x * 3:x * 3 + 3]
                out[o + 3] = 255
            elif ctype == 0:                # 灰度
                bit_off = x * depth
                byte = line[bit_off >> 3]
                shift = 8 - depth - (bit_off & 7)
                v = (byte >> shift) & ((1 << depth) - 1)
                g = v * (255 // ((1 << depth) - 1)) if depth < 8 else v
                out[o:o + 3] = bytes((g, g, g))
                out[o + 3] = 255
            else:                           # GA
                g, a = line[x * 2], line[x * 2 + 1]
                out[o:o + 3] = bytes((g, g, g))
                out[o + 3] = a
    return w, h, out


def write_png(path, w, h, px):
    """写出 8bit RGBA PNG。"""
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        raw += px[y * w * 4:(y + 1) * w * 4]

    def chunk(t, d):
        return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xFFFFFFFF)

    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n')
        f.write(chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)))
        f.write(chunk(b'IDAT', zlib.compress(bytes(raw), 6)))
        f.write(chunk(b'IEND', b''))
