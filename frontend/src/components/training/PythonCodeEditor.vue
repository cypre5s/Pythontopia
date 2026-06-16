<script setup lang="ts">
import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue';
import { autocompletion, closeBrackets, closeBracketsKeymap, completionKeymap } from '@codemirror/autocomplete';
import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands';
import { bracketMatching, defaultHighlightStyle, foldGutter, syntaxHighlighting } from '@codemirror/language';
import { python } from '@codemirror/lang-python';
import { highlightSelectionMatches, searchKeymap } from '@codemirror/search';
import { Compartment, EditorState, type Extension } from '@codemirror/state';
import {
  EditorView,
  highlightActiveLine,
  keymap,
  lineNumbers,
} from '@codemirror/view';

const props = withDefaults(
  defineProps<{
    modelValue: string;
    readonly?: boolean;
  }>(),
  {
    readonly: false,
  },
);

const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void;
}>();

const host = ref<HTMLDivElement | null>(null);
let view: EditorView | null = null;
let applyingExternalValue = false;

// 字体统一用 Consolas（按用户要求），缺失时回退其它等宽字体。
const MONO_FONT = '"Consolas", "Fira Code", "JetBrains Mono", "Source Code Pro", monospace';

const darkTheme = EditorView.theme(
  {
    '&': { height: '100%', minHeight: '0', color: '#e8f4ff', backgroundColor: '#111827', fontSize: '13px' },
    '.cm-scroller': { fontFamily: MONO_FONT, lineHeight: '1.65' },
    '.cm-content': { minHeight: '100%', padding: '12px 0' },
    '.cm-gutters': {
      backgroundColor: '#0f172a',
      color: '#8fa3b8',
      borderRight: '1px solid rgba(148, 163, 184, 0.25)',
    },
    '.cm-activeLine': { backgroundColor: 'rgba(245, 158, 11, 0.12)' },
    '.cm-activeLineGutter': { backgroundColor: 'rgba(245, 158, 11, 0.16)', color: '#fbbf24' },
    '.cm-selectionBackground, &.cm-focused .cm-selectionBackground': {
      backgroundColor: 'rgba(96, 165, 250, 0.35)',
    },
    '&.cm-focused': { outline: 'none' },
  },
  { dark: true },
);

const lightTheme = EditorView.theme(
  {
    '&': { height: '100%', minHeight: '0', color: '#26303a', backgroundColor: '#fbf7ec', fontSize: '13px' },
    '.cm-scroller': { fontFamily: MONO_FONT, lineHeight: '1.65' },
    '.cm-content': { minHeight: '100%', padding: '12px 0' },
    '.cm-gutters': {
      backgroundColor: '#f1e8d2',
      color: '#8a7a55',
      borderRight: '1px solid rgba(110, 79, 29, 0.25)',
    },
    '.cm-activeLine': { backgroundColor: 'rgba(212, 175, 55, 0.14)' },
    '.cm-activeLineGutter': { backgroundColor: 'rgba(212, 175, 55, 0.22)', color: '#7a5210' },
    '.cm-selectionBackground, &.cm-focused .cm-selectionBackground': {
      backgroundColor: 'rgba(42, 111, 151, 0.22)',
    },
    '&.cm-focused': { outline: 'none' },
  },
  { dark: false },
);

const themeCompartment = new Compartment();
const theme = ref<'light' | 'dark'>('dark');
// 用户手动切过主题后，不再跟随站点主题自动变化（尊重显式选择）。
let userOverrode = false;

function detectSiteTheme(): 'light' | 'dark' {
  if (typeof document === 'undefined') return 'dark';
  const attr = document.documentElement.dataset.theme;
  if (attr === 'light') return 'light';
  if (attr === 'dark') return 'dark';
  if (typeof window !== 'undefined' && window.matchMedia?.('(prefers-color-scheme: light)').matches) {
    return 'light';
  }
  return 'dark';
}

function themeExtension(): Extension {
  return theme.value === 'light' ? lightTheme : darkTheme;
}

function reconfigureTheme(): void {
  view?.dispatch({ effects: themeCompartment.reconfigure(themeExtension()) });
}

function toggleTheme(): void {
  userOverrode = true;
  theme.value = theme.value === 'light' ? 'dark' : 'light';
  reconfigureTheme();
}

const isLight = computed(() => theme.value === 'light');

function editorExtensions(): Extension[] {
  return [
    lineNumbers(),
    foldGutter(),
    history(),
    highlightActiveLine(),
    bracketMatching(),
    closeBrackets(),
    autocompletion(),
    highlightSelectionMatches(),
    python(),
    syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
    themeCompartment.of(themeExtension()),
    EditorView.lineWrapping,
    EditorView.editable.of(!props.readonly),
    keymap.of([
      indentWithTab,
      ...closeBracketsKeymap,
      ...completionKeymap,
      ...searchKeymap,
      ...historyKeymap,
      ...defaultKeymap,
    ]),
    EditorView.updateListener.of((update) => {
      if (!update.docChanged || applyingExternalValue) return;
      emit('update:modelValue', update.state.doc.toString());
    }),
  ];
}

function replaceDocument(nextValue: string): void {
  if (!view) return;
  const current = view.state.doc.toString();
  if (current === nextValue) return;
  applyingExternalValue = true;
  view.dispatch({
    changes: { from: 0, to: current.length, insert: nextValue },
  });
  applyingExternalValue = false;
}

// 跟随站点主题（用户未手动覆盖时）。
let themeObserver: MutationObserver | null = null;
let mediaQuery: MediaQueryList | null = null;
function followSiteTheme(): void {
  if (userOverrode) return;
  const next = detectSiteTheme();
  if (next !== theme.value) {
    theme.value = next;
    reconfigureTheme();
  }
}

onMounted(() => {
  theme.value = detectSiteTheme();
  if (!host.value) return;
  view = new EditorView({
    parent: host.value,
    state: EditorState.create({
      doc: props.modelValue,
      extensions: editorExtensions(),
    }),
  });
  if (typeof MutationObserver === 'function') {
    themeObserver = new MutationObserver(followSiteTheme);
    themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
  }
  if (typeof window !== 'undefined' && window.matchMedia) {
    mediaQuery = window.matchMedia('(prefers-color-scheme: light)');
    mediaQuery.addEventListener?.('change', followSiteTheme);
  }
});

watch(
  () => props.modelValue,
  (nextValue) => replaceDocument(nextValue),
);

onBeforeUnmount(() => {
  themeObserver?.disconnect();
  themeObserver = null;
  mediaQuery?.removeEventListener?.('change', followSiteTheme);
  mediaQuery = null;
  view?.destroy();
  view = null;
});
</script>

<template>
  <div
    class="python-code-editor"
    :class="isLight ? 'python-code-editor--light' : 'python-code-editor--dark'"
    data-testid="code-editor-wrap"
  >
    <button
      type="button"
      class="editor-theme-toggle"
      :title="isLight ? '切换到暗色主题' : '切换到亮色主题'"
      data-testid="editor-theme-toggle"
      @click="toggleTheme"
    >
      {{ isLight ? '🌙 暗色' : '☀ 亮色' }}
    </button>
    <div
      ref="host"
      class="python-code-editor__host"
      data-testid="code-editor"
    />
  </div>
</template>

<style scoped>
.python-code-editor {
  position: relative;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  border: 1px solid rgba(74, 54, 29, 0.36);
  border-radius: 6px;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.04);
}
.python-code-editor--dark {
  background: #111827;
}
.python-code-editor--light {
  background: #fbf7ec;
}
.python-code-editor__host {
  height: 100%;
  min-height: 0;
}

.editor-theme-toggle {
  position: absolute;
  top: 6px;
  right: 10px;
  z-index: 3;
  padding: 2px 8px;
  font-family: var(--pyt-font-pixel), monospace;
  font-size: 11px;
  cursor: pointer;
  border-radius: 4px;
  border: 1px solid rgba(140, 106, 35, 0.6);
  background: rgba(255, 247, 230, 0.9);
  color: #3a2a14;
  opacity: 0.55;
  transition: opacity 120ms ease;
}
.editor-theme-toggle:hover {
  opacity: 1;
}

.python-code-editor :deep(.cm-editor) {
  height: 100%;
}

.python-code-editor :deep(.cm-tooltip) {
  font-family: var(--pyt-font-read);
  border: 1px solid var(--pyt-color-border);
}
</style>
