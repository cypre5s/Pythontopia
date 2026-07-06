// 生成 build/icon.ico（多尺寸）。纯 JS（pngjs + png-to-ico），无系统依赖。
// 任何失败都不阻断打包：electron-builder 找不到 icon 时会回退默认图标。
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const OUT = resolve(__dirname, '..', 'build', 'icon.ico');

const PARCHMENT = [254, 247, 227];
const DARK = [47, 36, 24];
const FRAME = [92, 74, 50];
const GOLD = [241, 196, 83];

// 5x7 像素「P」字形（Pythontopia）。
const GLYPH = ['11110', '10001', '10001', '11110', '10000', '10000', '10000'];

function draw(size) {
  const buf = Buffer.alloc(size * size * 4);
  const put = (x, y, [r, g, b]) => {
    if (x < 0 || y < 0 || x >= size || y >= size) return;
    const i = (y * size + x) * 4;
    buf[i] = r; buf[i + 1] = g; buf[i + 2] = b; buf[i + 3] = 255;
  };
  const fill = (x0, y0, x1, y1, c) => {
    for (let y = y0; y < y1; y += 1) for (let x = x0; x < x1; x += 1) put(x, y, c);
  };
  const s = size / 256; // 以 256 为基准等比缩放
  fill(0, 0, size, size, PARCHMENT);
  fill(Math.round(14 * s), Math.round(14 * s), size - Math.round(14 * s), size - Math.round(14 * s), FRAME);
  fill(Math.round(26 * s), Math.round(26 * s), size - Math.round(26 * s), size - Math.round(26 * s), GOLD);
  // 居中绘制 P 字形
  const scale = Math.max(2, Math.round(26 * s));
  const gw = 5 * scale;
  const gh = 7 * scale;
  const sx = Math.round(size / 2 - gw / 2);
  const sy = Math.round(size / 2 - gh / 2);
  for (let row = 0; row < 7; row += 1) {
    for (let col = 0; col < 5; col += 1) {
      if (GLYPH[row][col] === '1') {
        fill(sx + col * scale, sy + row * scale, sx + (col + 1) * scale, sy + (row + 1) * scale, DARK);
      }
    }
  }
  return buf;
}

try {
  const pngjs = await import('pngjs');
  const { PNG } = pngjs.default ?? pngjs;
  const pngToIcoMod = await import('png-to-ico');
  const pngToIco = pngToIcoMod.default ?? pngToIcoMod;

  const sizes = [256, 128, 64, 48, 32, 16];
  const pngBuffers = sizes.map((size) => {
    const png = new PNG({ width: size, height: size });
    draw(size).copy(png.data);
    return PNG.sync.write(png);
  });
  const ico = await pngToIco(pngBuffers);
  mkdirSync(dirname(OUT), { recursive: true });
  writeFileSync(OUT, ico);
  console.log(`[make-icon] wrote ${OUT} (${ico.length} bytes)`);

  // 同时输出 256 PNG 供运行时窗口/任务栏图标使用（signAndEditExecutable=false 时 exe 不内嵌图标）。
  const PNG_OUT = resolve(__dirname, '..', 'icon.png');
  writeFileSync(PNG_OUT, pngBuffers[0]);
  console.log(`[make-icon] wrote ${PNG_OUT}`);
} catch (err) {
  console.warn('[make-icon] 跳过图标生成（将使用默认图标）:', err?.message || err);
  process.exit(0);
}
