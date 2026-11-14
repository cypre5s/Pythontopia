import { copyFile, mkdir, readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const source = new URL('../node_modules/pyodide/', import.meta.url);
const { version } = JSON.parse(await readFile(new URL('package.json', source), 'utf8'));
if (version !== '0.27.7') throw new Error('Update the sandbox runtime path before changing Pyodide');
const target = new URL(`../public/python-runtime/${version}/`, import.meta.url);
await mkdir(target, { recursive:true });
for (const name of ['pyodide.js', 'pyodide.asm.js', 'pyodide.asm.wasm', 'python_stdlib.zip', 'pyodide-lock.json']) {
  await copyFile(new URL(name, source), new URL(name, target));
}
await copyFile(new URL('PYODIDE-LICENSE.txt', import.meta.url), new URL('LICENSE.txt', target));
console.log(`Prepared local Python runtime: ${fileURLToPath(target)}`);
