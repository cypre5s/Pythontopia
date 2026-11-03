import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

interface NpcMetaFile {
  key?: string;
  portrait?: string;
}

const frontendRoot = process.cwd();
const repoRoot = join(frontendRoot, '..');
const npcRoot = join(repoRoot, 'data', 'npcs');
const portraitsRoot = join(frontendRoot, 'public', 'assets', 'pixel', 'portraits');

/** 成品立绘：等比缩进 768×1024 的框（生图多是 9:16，不强制裁切，否则会切掉头/脚） */
const PORTRAIT_BOX_W = 768;
const PORTRAIT_BOX_H = 1024;
/** 占位立绘规格：SRW 角色表 16×20 站立帧 ×6（见 scripts/portraits.py） */
const PLACEHOLDER_W = 96;
const PLACEHOLDER_H = 120;

function pngSize(path: string): { width: number; height: number } {
  const buf = readFileSync(path);
  // PNG 头固定 8 字节签名 + 4 字节长度 + 4 字节 'IHDR'，宽高紧接其后（大端）
  return { width: buf.readUInt32BE(16), height: buf.readUInt32BE(20) };
}

describe('NPC portrait assets', () => {
  // R-14 换源：立绘从「LPC 切头头像 96×96」改为「galgame 大立绘」，
  // 路径约定 `data/npcs/<key>/meta.json → galgame/<key>.png`。
  // 本用例守两件事：
  //   1. meta.portrait 指向的文件真实存在（防止改路径时忘记放图，运行时静默无立绘）；
  //   2. 尺寸只允许两档规格（768×1024 成品 / 96×120 占位），防止随手塞一张
  //      任意尺寸的图进来——尺寸错了运行时会被浏览器拉伸，就不锐了。
  it('每个 NPC 的 meta.portrait 都指向真实存在且尺寸合规的立绘', () => {
    const problems = readdirSync(npcRoot, { withFileTypes: true })
      .filter((entry) => entry.isDirectory())
      .flatMap((entry) => {
        const key = entry.name;
        const metaPath = join(npcRoot, key, 'meta.json');
        const meta = JSON.parse(readFileSync(metaPath, 'utf8')) as NpcMetaFile;
        const relPath = (meta.portrait ?? `galgame/${meta.key ?? key}.png`).replace(/^\/+/, '');
        if (!relPath) return [`${key}: meta.portrait 为空`];

        const fullPath = join(portraitsRoot, relPath);
        if (!existsSync(fullPath)) return [`${key}: 缺文件 portraits/${relPath}`];

        const { width, height } = pngSize(fullPath);
        // 成品：高度必须正好 1024（脚本按框等比缩放，宽高比自由），宽度只能是 1..768
        const isProduct = height === PORTRAIT_BOX_H && width > 0 && width <= PORTRAIT_BOX_W;
        const isPlaceholder = width === PLACEHOLDER_W && height === PLACEHOLDER_H;
        return isProduct || isPlaceholder
          ? []
          : [`${key}: portraits/${relPath} 尺寸 ${width}×${height}，只允许高 ${PORTRAIT_BOX_H}（宽 ≤ ${PORTRAIT_BOX_W}）或占位图 ${PLACEHOLDER_W}×${PLACEHOLDER_H}`];
      });

    expect(problems).toEqual([]);
  });
});
