#!/usr/bin/env node
/**
 * sync-pyodide.mjs — 把 Pyodide 运行时自托管到前端 public/ 下。
 *
 * @file 从 `node_modules/pyodide` 复制启动 Pyodide 所需的最小运行时文件到
 *        `frontend/public/pyodide/`，让前端以同源路径 `/pyodide/` 加载，
 *        避免第三方 CDN（jsdelivr）被浏览器「跟踪保护 / Tracking Prevention」
 *        拦截 storage，从而导致 Pyodide 加载告警 / 失败。
 *
 * @summary
 *   - 源：`<frontend>/node_modules/pyodide`
 *   - 目标：`<frontend>/public/pyodide`
 *   - 只复制启动核心（不含按需下载的科学计算 wheel），约 13MB
 *   - 幂等：目标已是最新（大小一致）则跳过；--force 强制覆盖
 *   - 缺源（未 npm i pyodide）时不报错退出（保留 CDN 回退），仅告警
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

// 启动 Pyodide 核心运行时所需的最小文件集（不含 numpy/pandas 等按需 wheel）。
const RUNTIME_FILES = [
  'pyodide.js',
  'pyodide.asm.js',
  'pyodide.asm.wasm',
  'python_stdlib.zip',
  'pyodide-lock.json',
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
    // 没装 pyodide：不阻断构建，运行时自动回退 CDN。
    log('未找到 node_modules/pyodide，跳过（运行时回退 CDN）。先 `npm i` 可启用自托管。');
    return;
  }

  await fs.mkdir(DEST_ROOT, { recursive: true });
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
  // 同步失败不阻断 dev/build（运行时回退 CDN），仅告警。
  console.warn('[sync-pyodide] 同步失败（运行时将回退 CDN）：', err?.message ?? err);
});
