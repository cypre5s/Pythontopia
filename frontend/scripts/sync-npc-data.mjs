#!/usr/bin/env node
/**
 * sync-npc-data.mjs — Pythontopia 前端 NPC 数据同步脚本
 *
 * @file 把仓库根 `data/npcs/` 下的 meta.json / dialog.json 增量复制到
 *        `frontend/public/data/npcs/`，使 vite dev server 可直接以
 *        `/data/npcs/<key>/dialog.json` 访问到 W2-NPC 子代理产出的对话树。
 *
 * @summary
 *   - 源（src）：`<repo>/data/npcs/`
 *   - 目标（dest）：`<repo>/frontend/public/data/npcs/`
 *   - 复制策略：默认 force 覆盖（NPC 数据更新频率高，覆盖最稳）
 *   - 仅复制 .json，忽略 .DS_Store / 备份文件
 *   - 同步 data/npcs/index.json，供前端做 NPC 列表 / 校验
 *
 * @example
 *   node scripts/sync-npc-data.mjs            # 全量覆盖同步
 *   node scripts/sync-npc-data.mjs --check    # 仅检查 diff，不复制
 *
 * @author Pythontopia 子代理 W2-DIALOG
 * @license MIT
 * @runtime Node 20+，零 npm 依赖
 */

import { promises as fs } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const FRONTEND_ROOT = path.resolve(__dirname, '..');
const REPO_ROOT = path.resolve(FRONTEND_ROOT, '..');
const SRC_ROOT = path.join(REPO_ROOT, 'data', 'npcs');
const DEST_ROOT = path.join(FRONTEND_ROOT, 'public', 'data', 'npcs');

const ALLOWED_EXT = new Set(['.json']);

function parseArgs() {
  const args = process.argv.slice(2);
  return {
    check: args.includes('--check'),
    quiet: args.includes('--quiet'),
  };
}

async function statSafe(p) {
  try {
    return await fs.stat(p);
  } catch (err) {
    if (err && err.code === 'ENOENT') return null;
    throw err;
  }
}

/**
 * 递归收集源目录下所有 .json 文件相对路径。
 * @param {string} dir
 * @returns {Promise<string[]>}
 */
async function walkJson(dir) {
  const out = [];
  async function recurse(current, rel) {
    const entries = await fs.readdir(current, { withFileTypes: true });
    for (const entry of entries) {
      if (entry.name.startsWith('.')) continue;
      const childAbs = path.join(current, entry.name);
      const childRel = rel ? `${rel}/${entry.name}` : entry.name;
      if (entry.isDirectory()) {
        await recurse(childAbs, childRel);
      } else if (entry.isFile() && ALLOWED_EXT.has(path.extname(entry.name).toLowerCase())) {
        out.push(childRel);
      }
    }
  }
  await recurse(dir, '');
  return out.sort();
}

async function copyOne(srcAbs, destAbs) {
  await fs.mkdir(path.dirname(destAbs), { recursive: true });
  await fs.copyFile(srcAbs, destAbs);
}

async function main() {
  const opts = parseArgs();
  const srcStat = await statSafe(SRC_ROOT);
  if (!srcStat || !srcStat.isDirectory()) {
    if (!opts.quiet) {
      console.warn(`[sync-npc-data] 跳过：源目录不存在 ${SRC_ROOT}`);
    }
    return;
  }

  const files = await walkJson(SRC_ROOT);
  let created = 0;
  let updated = 0;
  let unchanged = 0;

  for (const rel of files) {
    const srcAbs = path.join(SRC_ROOT, rel);
    const destAbs = path.join(DEST_ROOT, rel);
    const [srcBuf, destStat] = await Promise.all([fs.readFile(srcAbs), statSafe(destAbs)]);
    if (destStat) {
      const destBuf = await fs.readFile(destAbs);
      if (destBuf.equals(srcBuf)) {
        unchanged += 1;
        continue;
      }
      if (opts.check) {
        if (!opts.quiet) console.log(`[diff] ${rel}`);
        updated += 1;
        continue;
      }
      await copyOne(srcAbs, destAbs);
      updated += 1;
    } else {
      if (opts.check) {
        if (!opts.quiet) console.log(`[new]  ${rel}`);
        created += 1;
        continue;
      }
      await copyOne(srcAbs, destAbs);
      created += 1;
    }
  }

  if (!opts.quiet) {
    console.log(
      `[sync-npc-data] ${opts.check ? 'check ' : ''}done: ` +
        `+${created}  ~${updated}  =${unchanged}  total=${files.length}  ` +
        `src=${path.relative(REPO_ROOT, SRC_ROOT)}  dest=${path.relative(REPO_ROOT, DEST_ROOT)}`,
    );
  }
}

main().catch((err) => {
  console.error('[sync-npc-data] 运行失败：', err);
  process.exitCode = 1;
});
