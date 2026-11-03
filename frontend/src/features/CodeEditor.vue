<template>
  <div ref="root" class="code-editor" aria-label="Python代码编辑器"></div>
</template>
<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from "vue";
import type { EditorView } from "@codemirror/view";
const props = defineProps<{ modelValue: string }>(),
  emit = defineEmits<{ "update:modelValue": [value: string] }>(),
  root = ref<HTMLElement>();
let view: EditorView | undefined;
onMounted(async () => {
  const [
    { EditorState },
    { EditorView, lineNumbers, highlightActiveLine, keymap },
    { python },
    { defaultKeymap, history, historyKeymap },
  ] = await Promise.all([
    import("@codemirror/state"),
    import("@codemirror/view"),
    import("@codemirror/lang-python"),
    import("@codemirror/commands"),
  ]);
  if (!root.value) return;
  view = new EditorView({
    state: EditorState.create({
      doc: props.modelValue,
      extensions: [
        lineNumbers(),
        highlightActiveLine(),
        history(),
        keymap.of([...defaultKeymap, ...historyKeymap]),
        python(),
        EditorView.lineWrapping,
        EditorView.updateListener.of((update) => {
          if (update.docChanged)
            emit("update:modelValue", update.state.doc.toString());
        }),
        EditorView.theme({
          "&": { height: "100%", fontSize: "14px" },
          ".cm-scroller": {
            overflow: "auto",
            fontFamily: "Consolas,monospace",
          },
          ".cm-content": { minHeight: "220px" },
        }),
      ],
    }),
    parent: root.value,
  });
});
watch(
  () => props.modelValue,
  (value) => {
    if (view && value !== view.state.doc.toString())
      view.dispatch({
        changes: { from: 0, to: view.state.doc.length, insert: value },
      });
  },
);
onBeforeUnmount(() => view?.destroy());
</script>
