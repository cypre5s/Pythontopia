<template>
  <div class="code-editor">
    <div ref="root" class="monaco-host" aria-label="Python代码编辑器"></div>
    <p v-if="loadError" role="alert">{{ loadError }}</p>
  </div>
</template>
<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from "vue";
import type { editor, IDisposable } from "monaco-editor";
import { useUiPreferences } from "../shared/uiPreferences";
const preferences = useUiPreferences();
const props = defineProps<{ modelValue: string }>();
const emit = defineEmits<{ "update:modelValue": [value: string] }>();
const root = ref<HTMLElement>(), loadError = ref("");
let view: editor.IStandaloneCodeEditor | undefined;
let model: editor.ITextModel | undefined;
let subscription: IDisposable | undefined;
let disposed = false;
onMounted(async () => {
  try {
    const { monaco } = await import("../shared/monaco");
    if (disposed || !root.value) return;
    model = monaco.editor.createModel(props.modelValue, "python");
    view = monaco.editor.create(root.value, {
      model, theme: "town-python", automaticLayout: true,
      ariaLabel: "Python代码编辑器", fontSize: 14 * preferences.textScale, lineHeight: 22 * preferences.textScale,
      fontFamily: '"JetBrains Mono", Consolas, -apple-system, BlinkMacSystemFont, "Segoe UI", "Noto Sans SC", monospace',
      minimap: { enabled: false }, glyphMargin: false, folding: false,
      wordWrap: "on", lineNumbersMinChars: 3, scrollBeyondLastLine: false,
      padding: { top: 12, bottom: 12 }, tabSize: 4, overviewRulerLanes: 0,
      scrollbar: { verticalScrollbarSize: 8, horizontalScrollbarSize: 8 },
      unicodeHighlight: { ambiguousCharacters: false, nonBasicASCII: false },
    });
    subscription = view.onDidChangeModelContent(() => emit("update:modelValue", model!.getValue()));
    void document.fonts?.ready.then(() => { if (!disposed) monaco.editor.remeasureFonts(); });
  } catch {
    if (!disposed) loadError.value = "代码编辑器加载失败，请刷新页面后重试。";
  }
});
watch(() => props.modelValue, (value) => {
  if (view && model && value !== model.getValue())
    view.executeEdits("draft", [{ range: model.getFullModelRange(), text: value }]);
});
watch(() => preferences.textScale, (scale) => {
  view?.updateOptions({ fontSize: 14 * scale, lineHeight: 22 * scale });
});
onBeforeUnmount(() => {
  disposed = true;
  subscription?.dispose(); view?.dispose(); model?.dispose();
});
</script>
<style scoped>
.monaco-host { height: 100%; min-height: 0; }
</style>
