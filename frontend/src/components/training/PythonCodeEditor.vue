<script setup lang="ts">
import {
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
import { EditorState, type Extension } from '@codemirror/state';
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

const trainingTheme = EditorView.theme({
  '&': {
    height: '100%',
    minHeight: '0',
    color: '#e8f4ff',
    backgroundColor: '#111827',
    fontSize: '13px',
  },
  '.cm-scroller': {
    fontFamily: '"Fira Code", "JetBrains Mono", "Source Code Pro", monospace',
    lineHeight: '1.65',
  },
  '.cm-content': {
    minHeight: '100%',
    padding: '12px 0',
  },
  '.cm-gutters': {
    backgroundColor: '#0f172a',
    color: '#8fa3b8',
    borderRight: '1px solid rgba(148, 163, 184, 0.25)',
  },
  '.cm-activeLine': {
    backgroundColor: 'rgba(245, 158, 11, 0.12)',
  },
  '.cm-activeLineGutter': {
    backgroundColor: 'rgba(245, 158, 11, 0.16)',
    color: '#fbbf24',
  },
  '.cm-selectionBackground, &.cm-focused .cm-selectionBackground': {
    backgroundColor: 'rgba(96, 165, 250, 0.35)',
  },
  '&.cm-focused': {
    outline: 'none',
  },
});

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
    trainingTheme,
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

onMounted(() => {
  if (!host.value) return;
  view = new EditorView({
    parent: host.value,
    state: EditorState.create({
      doc: props.modelValue,
      extensions: editorExtensions(),
    }),
  });
});

watch(
  () => props.modelValue,
  (nextValue) => replaceDocument(nextValue),
);

onBeforeUnmount(() => {
  view?.destroy();
  view = null;
});
</script>

<template>
  <div
    ref="host"
    class="python-code-editor"
    data-testid="code-editor"
  />
</template>

<style scoped>
.python-code-editor {
  height: 100%;
  min-height: 0;
  overflow: hidden;
  border: 1px solid rgba(74, 54, 29, 0.36);
  border-radius: 6px;
  background: #111827;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.04);
}

.python-code-editor :deep(.cm-editor) {
  height: 100%;
}

.python-code-editor :deep(.cm-tooltip) {
  font-family: var(--pyt-font-read);
  border: 1px solid var(--pyt-color-border);
}
</style>
