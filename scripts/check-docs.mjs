import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const errors = [];
const warnings = [];
const fetchMissingHistory = process.argv.includes('--fetch');
const normalize = text => text.replace(/\r\n/g, '\n');
const read = file => normalize(readFileSync(file, 'utf8'));
const relative = file => path.relative(root, file).split(path.sep).join('/');
function walk(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const file = path.join(directory, entry.name);
    return entry.isDirectory() ? walk(file) : [file];
  });
}
function withoutCode(source) {
  let fence = null;
  return source.split('\n').map(line => {
    const marker = line.match(/^\s{0,3}(`{3,}|~{3,})/);
    if (marker) {
      if (!fence) fence = marker[1];
      else if (marker[1][0] === fence[0] && marker[1].length >= fence.length) fence = null;
      return '';
    }
    return fence ? '' : line;
  }).join('\n');
}
function headings(source) {
  const counts = new Map();
  const ids = new Set();
  for (const line of withoutCode(source).split('\n')) {
    const match = line.match(/^\s{0,3}#{1,6}\s+(.+?)(?:\s+#+)?\s*$/);
    if (!match) continue;
    const slug = match[1].replace(/\[([^\]]+)\]\([^)]*\)/g, '$1').replace(/<[^>]+>/g, '')
      .toLowerCase().replace(/[^\p{L}\p{N}\p{M}_\-\s]/gu, '').replace(/\s/g, '-');
    const occurrence = counts.get(slug) ?? 0;
    counts.set(slug, occurrence + 1);
    ids.add(occurrence ? `${slug}-${occurrence}` : slug);
  }
  return ids;
}
function archiveBody(source, originalFile, archiveFile) {
  return source.replace(/(\[[^\]\n]*\]\()([^\s)]+)(\))/g, (match, start, target, end) => {
    if (target.startsWith('#') || /^[a-z][a-z\d+.-]*:/i.test(target) || target.startsWith('/')) return match;
    const hash = target.indexOf('#');
    const filePart = hash < 0 ? target : target.slice(0, hash);
    const fragment = hash < 0 ? '' : target.slice(hash);
    const destination = path.resolve(path.dirname(originalFile), decodeURIComponent(filePart));
    return start + path.relative(path.dirname(archiveFile), destination).split(path.sep).join('/') + fragment + end;
  });
}

function commitAvailable(commit) {
  try {
    execFileSync('git', ['cat-file', '-e', `${commit}^{commit}`], { cwd: root, stdio: 'ignore' });
    return true;
  } catch {
    return false;
  }
}
function fetchCommit(commit) {
  try {
    // GitHub serves an arbitrary reachable commit by SHA; one commit keeps CI cheap.
    execFileSync('git', ['fetch', '--depth=1', 'origin', commit], { cwd: root, stdio: 'ignore' });
    return commitAvailable(commit);
  } catch {
    return false;
  }
}

const docs = walk(path.join(root, 'docs'));
const markdown = [path.join(root, 'README.md'), path.join(root, 'AGENTS.md'), ...docs.filter(file => file.endsWith('.md'))];
let links = 0, anchors = 0, archives = 0;
const headingCache = new Map();
for (const file of markdown) {
  const source = withoutCode(read(file)).replace(/(`+)[^\n]*?\1/g, '');
  const targets = [...source.matchAll(/!?\[[^\]\n]*\]\(\s*(<[^>]+>|[^\s)]+)(?:\s+"[^"]*")?\s*\)/g)].map(match => match[1]);
  targets.push(...[...source.matchAll(/^\s{0,3}\[[^\]]+\]:\s*(<[^>]+>|\S+)/gm)].map(match => match[1]));
  for (let target of targets) {
    target = target.replace(/^<|>$/g, '');
    if (/^[a-z][a-z\d+.-]*:/i.test(target) || target.startsWith('//')) continue;
    const hash = target.indexOf('#');
    let filePart, fragment;
    try {
      filePart = decodeURIComponent(hash < 0 ? target : target.slice(0, hash));
      fragment = hash < 0 ? '' : decodeURIComponent(target.slice(hash + 1));
    } catch {
      errors.push(`${relative(file)}: malformed target ${target}`);
      continue;
    }
    const destination = filePart ? path.resolve(filePart.startsWith('/') ? root : path.dirname(file), filePart.replace(/^\//, '')) : file;
    links++;
    if (!existsSync(destination)) {
      errors.push(`${relative(file)}: missing ${target}`);
      continue;
    }
    if (fragment && destination.endsWith('.md')) {
      anchors++;
      if (!headingCache.has(destination)) headingCache.set(destination, headings(read(destination)));
      if (!headingCache.get(destination).has(fragment)) errors.push(`${relative(file)}: missing anchor ${target}`);
    }
  }
}
for (const file of docs.filter(file => file.endsWith('/manifest.json') || file.endsWith('\\manifest.json'))) {
  try {
    const manifest = JSON.parse(read(file));
    if (!/^[a-f\d]{40,64}$/.test(manifest.source_commit) || !Array.isArray(manifest.files)) throw new Error('invalid manifest');
    if (!commitAvailable(manifest.source_commit)) {
      if (fetchMissingHistory && fetchCommit(manifest.source_commit)) {
        // Fetched the single archived commit; continue with the full check.
      } else {
        warnings.push(`${relative(file)}: 缺少提交 ${manifest.source_commit.slice(0, 8)}，跳过归档溯源校验（浅克隆；用 --fetch 或 git fetch --unshallow 后可完整校验）`);
        continue;
      }
    }
    for (const item of manifest.files) {
      if (!item.source.startsWith('docs/') || !item.archive.startsWith('docs/archive/') || [item.source, item.archive].some(name => name.split('/').includes('..'))) throw new Error('invalid source path');
      const bytes = execFileSync('git', ['show', `${manifest.source_commit}:${item.source}`], { cwd: root, maxBuffer: 2 * 1024 * 1024 });
      if (createHash('sha256').update(bytes).digest('hex') !== item.sha256 || bytes.length !== item.bytes) throw new Error(`source digest differs: ${item.source}`);
      const archivedFile = path.join(root, item.archive);
      const source = read(archivedFile), marker = '<!-- archived-source-begin -->\n';
      if (!source.includes(marker)) throw new Error(`missing archive marker: ${item.archive}`);
      const body = source.slice(source.indexOf(marker) + marker.length);
      if (body !== archiveBody(normalize(bytes.toString('utf8')), path.join(root, item.source), archivedFile)) throw new Error(`archive body differs: ${item.archive}`);
      archives++;
    }
  } catch (error) {
    errors.push(`${relative(file)}: ${error.message}`);
  }
}
if (warnings.length) for (const warning of warnings) console.warn(`warning: ${warning}`);
if (errors.length) {
  for (const error of errors) console.error(error);
  process.exitCode = 1;
} else {
  const skipped = warnings.length ? ` (${warnings.length} archive manifest(s) skipped: history unavailable)` : '';
  console.log(`Docs OK: ${markdown.length} Markdown files, ${links} local links, ${anchors} anchors; ${archives} archived source documents intact${skipped}.`);
}
