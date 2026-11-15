import path from 'node:path';

const normalize = value => value.split(path.sep).join('/');

// Read literal module references only: no evaluation, computed paths or resolution
// of exported symbols. Compilers remain responsible for syntax/type resolution.
function tokensOf(source, python = false) {
  const tokens = [];
  let cursor = 0;
  const add = (kind, value) => tokens.push({ kind, value });
  const previous = () => tokens.findLast(token => token.kind !== 'newline');
  function quoted(quote) {
    const triple = python && source.startsWith(quote.repeat(3), cursor);
    const end = triple ? quote.repeat(3) : quote;
    cursor += end.length;
    let value = '';
    while (cursor < source.length && !source.startsWith(end, cursor)) {
      const character = source[cursor++];
      if (character !== '\\') { value += character; continue; }
      const escaped = source[cursor++];
      if (escaped === '\n') continue;
      if (escaped === '\r') { if (source[cursor] === '\n') cursor++; continue; }
      const code = source.slice(cursor).match(escaped === 'u' ? /^(?:\{([a-f\d]+)\}|([a-f\d]{4}))/i : escaped === 'x' ? /^([a-f\d]{2})/i : /$^/);
      if (code) { value += String.fromCodePoint(parseInt(code[1] ?? code[2], 16)); cursor += code[0].length; }
      else value += ({ n: '\n', r: '\r', t: '\t' })[escaped] ?? escaped ?? '';
    }
    cursor += end.length;
    add('string', value);
  }
  function template() {
    cursor++;
    while (cursor < source.length) {
      if (source[cursor] === '\\') { cursor += 2; continue; }
      if (source[cursor] === '`') { cursor++; break; }
      if (source.startsWith('${', cursor)) { cursor += 2; code(true); }
      else cursor++;
    }
    add('literal', 'template');
  }
  function regexLiteral() {
    cursor++;
    let characterClass = false;
    while (cursor < source.length) {
      const character = source[cursor++];
      if (character === '\\') { cursor++; continue; }
      if (character === '[') characterClass = true;
      if (character === ']') characterClass = false;
      if (character === '/' && !characterClass) break;
      if (character === '\n') break;
    }
    while (/[a-z]/i.test(source[cursor] ?? '') && cursor < source.length) cursor++;
    add('literal', 'regexp');
  }
  function code(interpolation = false) {
    let depth = 0;
    while (cursor < source.length) {
      const character = source[cursor];
      if (interpolation && character === '}' && depth === 0) { cursor++; return; }
      if (character === '\n') { add('newline', character); cursor++; continue; }
      if (/\s/.test(character)) { cursor++; continue; }
      if ((!python && source.startsWith('//', cursor)) || (python && character === '#')) {
        cursor = source.indexOf('\n', cursor);
        if (cursor < 0) cursor = source.length;
        continue;
      }
      if (source.startsWith('/*', cursor)) {
        const end = source.indexOf('*/', cursor + 2);
        cursor = end < 0 ? source.length : end + 2;
        continue;
      }
      if (character === '"' || character === "'") { quoted(character); continue; }
      if (!python && character === '`') { template(); continue; }
      if (!python && character === '/') {
        const before = previous();
        if (!before || /^(?:[=(:,;!&|?{}\[]|return|throw|case|yield|await)$/.test(before.value)) {
          regexLiteral(); continue;
        }
      }
      const identifier = source.slice(cursor).match(/^[\w$]+/);
      if (identifier) { add('word', identifier[0]); cursor += identifier[0].length; continue; }
      if (character === '{') depth++;
      if (character === '}') depth--;
      add('punctuation', character);
      cursor++;
    }
  }
  code();
  return tokens;
}

function pythonImports(source) {
  const tokens = tokensOf(source, true), result = [];
  for (let index = 0; index < tokens.length; index++) {
    const token = tokens[index];
    if (token.kind !== 'word' || !['from', 'import'].includes(token.value)) continue;
    const from = token.value === 'from';
    let cursor = index + 1, module = '';
    if (from) {
      while (tokens[cursor] && tokens[cursor].kind !== 'newline' && tokens[cursor].value !== 'import') module += tokens[cursor++].value;
      if (tokens[cursor]?.value !== 'import') continue;
      cursor++;
      // Keep the source for ordinary imports (e.g. fastapi), and inspect child
      // modules too so `from . import router` cannot bypass dependency rules.
      if (module.replaceAll('.', '')) result.push(module);
    }
    let name = '', alias = false, parentheses = 0;
    const finish = () => {
      if (name && name !== '*') result.push(from ? `${module}${module.endsWith('.') ? '' : '.'}${name}` : name);
      name = ''; alias = false;
    };
    for (; cursor < tokens.length; cursor++) {
      const item = tokens[cursor];
      if (item.value === ';' || (item.kind === 'newline' && !parentheses)) break;
      if (item.value === '(') { parentheses++; continue; }
      if (item.value === ')') { parentheses--; continue; }
      if (item.value === ',') { finish(); continue; }
      if (item.value === 'as') { alias = true; continue; }
      if (item.kind !== 'newline' && !alias) name += item.value;
    }
    finish();
    index = cursor;
  }
  return [...new Set(result)];
}

export function importsOf(file, source) {
  if (file.endsWith('.py')) return pythonImports(source);
  // Vue template prose is not JavaScript. Script and style blocks own imports.
  if (file.endsWith('.vue')) source = [...source.matchAll(/<(script|style)\b[^>]*>([\s\S]*?)<\/\1\s*>/gi)].map(match => match[2]).join('\n');
  const tokens = tokensOf(source).filter(token => token.kind !== 'newline'), result = [];
  const add = token => { if (token?.kind === 'string') result.push(token.value); };
  for (let index = 0; index < tokens.length; index++) {
    const token = tokens[index], next = tokens[index + 1];
    if (token.kind !== 'word' || !['import', 'export'].includes(token.value)) continue;
    if (tokens[index - 1]?.value === '.') continue;
    if (token.value === 'import' && tokens[index - 1]?.value === '@') {
      add(next?.value === 'url' ? tokens[index + 3] : next);
    } else if (token.value === 'import' && next?.value === '(') {
      if ([',', ')'].includes(tokens[index + 3]?.value)) add(tokens[index + 2]);
    } else if (token.value === 'import' && next?.kind === 'string') add(next);
    else if (next?.value !== '.' && (token.value === 'import' || ['type', '{', '*'].includes(next?.value))) {
      for (let cursor = index + 1; cursor < tokens.length; cursor++) {
        const item = tokens[cursor];
        if (item.value === ';' || ['import', 'export'].includes(item.value)) break;
        if (item.value === 'from') { add(tokens[cursor + 1]); break; }
      }
    }
  }
  return [...new Set(result)];
}

export function importTarget(file, specifier) {
  if (file.endsWith('.py')) {
    if (specifier.startsWith('app.')) return `backend/${specifier.replaceAll('.', '/')}`;
    if (!specifier.startsWith('.')) return null;
    const dots = specifier.match(/^\.+/)[0].length;
    return normalize(path.posix.join(path.posix.dirname(file), '../'.repeat(dots - 1), specifier.slice(dots).replaceAll('.', '/')));
  }
  const clean = specifier.split('?')[0];
  if (clean.startsWith('@/')) return `${file.split('/')[0]}/src/${clean.slice(2)}`;
  return clean.startsWith('.') ? normalize(path.posix.join(path.posix.dirname(file), clean)) : null;
}

export function boundaryErrors(file, specifier) {
  const errors = [];
  const target = importTarget(file, specifier);
  const frontend = file.startsWith('frontend/src/');
  const admin = file.startsWith('admin/src/');
  if ((frontend && target?.startsWith('admin/')) || (admin && target?.startsWith('frontend/')))
    errors.push('学生端和教师后台不得互相导入代码或主题');
  if (frontend && /^(tailwindcss|@tailwindcss)(\/|$)/.test(specifier))
    errors.push('学生端不得引入后台 Tailwind/Preflight');
  if (file.startsWith('frontend/src/shared/') && target?.startsWith('frontend/src/features/'))
    errors.push('共享基础模块不得反向依赖业务页面');
  if (file.startsWith('frontend/src/game/') && (
    target?.startsWith('frontend/src/features/') || target?.startsWith('frontend/src/shared/session') ||
    /^(vue|pinia|vue-router)$/.test(specifier)))
    errors.push('Phaser 层通过适配器回调交互，不得依赖 Vue 页面或会话 store');
  if (file.startsWith('admin/src/components/ui/') && /^(admin\/src\/(features|pages)\/)/.test(target ?? ''))
    errors.push('后台通用 UI 不得依赖业务页面');
  if (file.startsWith('backend/app/') && file !== 'backend/app/main.py' && !file.endsWith('/__init__.py') && target?.endsWith('/router'))
    errors.push('业务模块不得依赖 HTTP 路由装配');
  if (/^backend\/app\/(course_packages|teaching)\/.*validation\.py$/.test(file) && (
    specifier.startsWith('fastapi') || specifier.startsWith('starlette') || target?.endsWith('/router') || target?.endsWith('/service')))
    errors.push('定义校验不得依赖 HTTP 或应用编排');
  if (/^backend\/app\/teaching\/(story|quests|conditions|world)\//.test(file) &&
      !file.endsWith('/authoring.py') && target?.startsWith('backend/app/teaching/authoring/'))
    errors.push('教学运行模块不得反向依赖教师编辑编排');
  if (/(?:frontend|admin)\/src\//.test(file) && /\/(tests|e2e)\//.test(target ?? ''))
    errors.push('产品代码不得导入测试实现');
  return errors;
}
