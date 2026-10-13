#!/usr/bin/env node
/**
 * sync-pyodide.mjs — 把 Pyodide 运行时自托管到前端 public/ 下。
 *
 * @file 从 `node_modules/pyodide` 复制运行时数据文件到
 *        `frontend/public/pyodide/`，让前端以同源路径 `/pyodide/` 加载，
 *        避免第三方 CDN（jsdelivr）被浏览器「跟踪保护 / Tracking Prevention」
 *        拦截 storage，从而导致 Pyodide 加载告警 / 失败。
 *
 * @summary
 *   - 源：`<frontend>/node_modules/pyodide`
 *   - 目标：`<frontend>/public/pyodide`
 *   - 只复制 WASM、标准库和锁文件（不含按需下载的科学计算 wheel），约 12MB
 *   - 幂等：目标已是最新（大小一致）则跳过；--force 强制覆盖
 *   - Pyodide JS 模块由前端 Worker bundle 提供；这里只发布运行所需的数据文件
 *
 * @example
 *   node scripts/sync-pyodide.mjs            # 同步
 *   node scripts/sync-pyodide.mjs --quiet    # 安静模式（predev/prebuild 钩子用）
 *
 * @runtime Node 20+，零 npm 依赖
 * @license MIT
 */

import { promises as fs } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const FRONTEND_ROOT = path.resolve(__dirname, '..');
const SRC_ROOT = path.join(FRONTEND_ROOT, 'node_modules', 'pyodide');
const DEST_ROOT = path.join(FRONTEND_ROOT, 'public', 'pyodide');

// Worker 将 Pyodide JS 模块打包；运行时只需从同源目录读取这些数据文件。
const RUNTIME_FILES = [
  'pyodide.asm.wasm',
  'python_stdlib.zip',
  'pyodide-lock.json',
];

// 清掉上一套运行时按 URL 加载的 JS 文件，避免保留重复 bundle。
const OBSOLETE_RUNTIME_FILES = [
  'pyodide.asm.js',
  'pyodide.asm.mjs',
  'pyodide.js',
  'pyodide.js.map',
  'pyodide.mjs',
  'pyodide.mjs.map',
  'package.json',
];

const quiet = process.argv.includes('--quiet');
const force = process.argv.includes('--force');

function log(...args) {
  if (!quiet) console.log('[sync-pyodide]', ...args);
}

async function statSafe(p) {
  try {
    return await fs.stat(p);
  } catch (err) {
    if (err && err.code === 'ENOENT') return null;
    throw err;
  }
}

async function main() {
  const srcStat = await statSafe(SRC_ROOT);
  if (!srcStat) {
    // Pyodide 是前端依赖；未安装依赖的场景不阻断同步脚本。
    log('未找到 node_modules/pyodide，跳过。先 `npm install` 安装前端依赖。');
    return;
  }

  await fs.mkdir(DEST_ROOT, { recursive: true });
  for (const name of OBSOLETE_RUNTIME_FILES) {
    await fs.rm(path.join(DEST_ROOT, name), { force: true });
  }
  let copied = 0;
  let skipped = 0;
  for (const name of RUNTIME_FILES) {
    const src = path.join(SRC_ROOT, name);
    const dest = path.join(DEST_ROOT, name);
    const srcFile = await statSafe(src);
    if (!srcFile) {
      log(`缺少源文件 ${name}，跳过。`);
      continue;
    }
    const destFile = await statSafe(dest);
    if (!force && destFile && destFile.size === srcFile.size) {
      skipped += 1;
      continue;
    }
    await fs.copyFile(src, dest);
    copied += 1;
  }
  log(`完成：复制 ${copied}，跳过 ${skipped}（已最新）→ public/pyodide/`);
}

main().catch((err) => {
  // 同步失败不阻断 dev/build，仅告警。
  console.warn('[sync-pyodide] 同步失败：', err?.message ?? err);
});
