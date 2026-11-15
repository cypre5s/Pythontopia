import { existsSync, readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { boundaryErrors, importsOf } from './module-boundaries.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const sourceRoots = ['backend/app', 'frontend/src', 'admin/src'];
const errors = [];
const large = [];
let checked = 0;
function visit(directory) {
  for (const entry of readdirSync(path.join(root, directory), { withFileTypes: true })) {
    const file = `${directory}/${entry.name}`;
    if (entry.isDirectory()) {
      if (!['__pycache__', 'node_modules'].includes(entry.name)) visit(file);
      continue;
    }
    if (!/\.(py|ts|tsx|vue|css)$/.test(file) || file.endsWith('.d.ts')) continue;
    const source = readFileSync(path.join(root, file), 'utf8');
    checked++;
    for (const dependency of importsOf(file, source))
      for (const error of boundaryErrors(file, dependency)) errors.push(`${file}: ${error} (${dependency})`);
    const lines = source.trimEnd().split(/\r?\n/);
    const dense = lines.filter(line => line.length > 200).length;
    if (lines.length > 500 || dense > 10) large.push({ file, lines: lines.length, dense });
  }
}
sourceRoots.forEach(visit);
// The retired entries must not return as forwarding shims.
for (const file of [
  'backend/app/course_exchange.py', 'backend/app/course_package.py',
  'backend/app/course_catalog.py', 'backend/app/course_cleanup.py',
  'backend/app/classrooms.py', 'backend/app/storage.py',
  'backend/app/experience_tools.py', 'backend/app/world_authoring.py',
  'backend/app/story.py', 'backend/app/story_conditions.py', 'backend/app/quests.py',
  'frontend/src/shared/session.ts', 'frontend/src/features/DevToolsPanel.vue',
  'frontend/src/features/ExperienceTools.vue', 'frontend/src/app/style.css',
  'frontend/src/app/gameUi.css', 'admin/src/pages/authoring.tsx',
  'admin/src/components/node-editor.tsx', 'admin/src/components/quest-editor.tsx',
]) if (existsSync(path.join(root, file))) errors.push(`${file}: 已退场入口不能恢复为兼容转发层`);

for (const error of errors) console.error(error);
console.log(`模块边界：检查 ${checked} 个生产源文件，${errors.length} 个错误。`);
if (large.length) {
  console.log('复杂度提醒（不按行数阻断；复核职责、压缩代码与可读性）：');
  for (const item of large.sort((a, b) => b.lines - a.lines))
    console.log(`  ${item.file}: ${item.lines} 行，${item.dense} 行超过 200 字符`);
}
process.exitCode = errors.length ? 1 : 0;
