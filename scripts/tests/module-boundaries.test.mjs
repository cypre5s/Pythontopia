import { test } from 'node:test';
import assert from 'node:assert/strict';
import { importsOf, importTarget, boundaryErrors } from '../module-boundaries.mjs';

test('recognizes lazy, type-only, side-effect and CSS imports without commented examples', () => {
  assert.deepEqual(importsOf('frontend/src/example.ts', `
    // import Hidden from '../features/Hidden.vue';
    import type {
      World,
    } from './types';
    import './style.css';
    const component = () => import('../features/WorldPage.vue');
    /* import Old from './old'; */
    @import './base.css';
  `).sort(), ['../features/WorldPage.vue', './base.css', './style.css', './types'].sort());
});
test('resolves aliases and Python package-relative paths', () => {
  assert.equal(importTarget('admin/src/features/example/page.tsx', '@/components/ui/button'), 'admin/src/components/ui/button');
  assert.equal(importTarget('backend/app/teaching/story/runtime.py', '..quests.graph'), 'backend/app/teaching/quests/graph');
  assert.deepEqual(importsOf('backend/app/main.py', 'from app.teaching.story.router import router\nimport json'), ['app.teaching.story.router', 'app.teaching.story.router.router', 'json']);
});

test('reads consecutive imports and exports independently of line breaks', () => {
  const source = `import type { X } from './types'; import Y from '../features/Y.vue'; export { value } from './values'; export * as graph from './graph';`;
  assert.deepEqual(importsOf('frontend/src/shared/example.ts', source), ['./types', '../features/Y.vue', './values', './graph']);
  assert.equal(boundaryErrors('frontend/src/shared/example.ts', importsOf('frontend/src/shared/example.ts', source)[1]).length, 1);
});

test('does not treat quoted code, template prose, regexes or member calls as imports', () => {
  const source = [
    `const quoted = "import('../features/quoted.vue')";`,
    'const prose = `\nimport X from "../features/prose.vue";\n`;',
    `const pattern = /import('..\\/features\\/regex.vue')/;`,
    `loader.import('../features/member.vue');`,
    `const component = import('./real.vue', { with: { type: 'component' } });`,
  ].join('\n');
  assert.deepEqual(importsOf('frontend/src/shared/example.ts', source), ['./real.vue']);
});

test('checks template expressions while ignoring raw text and computed module paths', () => {
  const source = 'const message = `literal import("./fake") ${await import("./real")} ${`nested ${import("./nested")}`}`; import("./prefix" + name);';
  assert.deepEqual(importsOf('frontend/src/shared/example.ts', source), ['./real', './nested']);
});

test('Vue imports belong to script and style blocks, not displayed template examples', () => {
  const source = `<template><pre>import X from '../features/fake.vue';</pre></template><script setup lang="ts">import X from './real';</script><style>@import url('./theme.css'); .sample::after { content: "import('../features/fake.vue')"; }</style>`;
  assert.deepEqual(importsOf('frontend/src/shared/example.vue', source), ['./real', './theme.css']);
});

test('Python child-module imports, aliases and lists cannot bypass routing boundaries', () => {
  const file = 'backend/app/course_packages/apply.py';
  const imports = importsOf(file, 'from . import (\n router as endpoints,\n service,\n)\nimport json, app.teaching.story.router as story\n');
  assert.deepEqual(imports, ['.router', '.service', 'json', 'app.teaching.story.router']);
  assert.equal(boundaryErrors(file, imports[0]).length, 1);
  assert.equal(boundaryErrors('backend/app/course_packages/validation.py', imports[1]).length, 1);
  assert.deepEqual(importsOf(file, '# from . import router\n"""\nfrom . import router\n"""\nfrom . import paths'), ['.paths']);
});
test('rejects cross-application themes and a shared module depending on a feature', () => {
  assert.equal(boundaryErrors('frontend/src/app/main.ts', '../../../admin/src/styles.css').length, 1);
  assert.equal(boundaryErrors('frontend/src/shared/example.ts', '../features/practice/PracticeWorkspace.vue').length, 1);
  assert.equal(boundaryErrors('frontend/src/features/practice/PracticeWorkspace.vue', '../../shared/api').length, 0);
});
test('keeps Phaser independent and common UI free of business imports', () => {
  assert.equal(boundaryErrors('frontend/src/game/world/scene.ts', '../../shared/session').length, 1);
  assert.equal(boundaryErrors('frontend/src/game/world/scene.ts', '../../shared/types').length, 0);
  assert.equal(boundaryErrors('admin/src/components/ui/button.tsx', '@/features/authoring/page').length, 1);
});
test('keeps definition validation and teaching runtime below application routing', () => {
  assert.equal(boundaryErrors('backend/app/course_packages/validation.py', 'fastapi').length, 1);
  assert.equal(boundaryErrors('backend/app/teaching/story/runtime.py', 'app.teaching.authoring.service').length, 1);
  assert.equal(boundaryErrors('backend/app/course_packages/apply.py', 'app.course_packages.router').length, 1);
  assert.equal(boundaryErrors('backend/app/main.py', 'app.course_packages.router').length, 0);
});
