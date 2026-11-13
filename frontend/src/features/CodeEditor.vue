<template>
  <div class="code-editor">
    <div ref="root" class="monaco-host" aria-label="Python代码编辑器"></div>
    <footer v-if="!loadError" class="code-position" aria-label="光标位置">行 {{ position.lineNumber }}，列 {{ position.column }}</footer>
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
const position = ref({ lineNumber: 1, column: 1 });
let view: editor.IStandaloneCodeEditor | undefined;
let model: editor.ITextModel | undefined;
let subscription: IDisposable | undefined;
let cursorSubscription: IDisposable | undefined;
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
    cursorSubscription = view.onDidChangeCursorPosition(event => { position.value = event.position; });
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
  subscription?.dispose(); cursorSubscription?.dispose(); view?.dispose(); model?.dispose();
});
</script>
<style scoped>
.code-editor { display: flex; flex-direction: column; }
.monaco-host { flex: 1; min-height: 0; }
.code-position { flex: 0 0 auto; padding: 5px 12px; text-align: right; color: var(--game-muted); font-size: .75rem; line-height: 1.5; }
</style>
