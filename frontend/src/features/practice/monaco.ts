import * as monaco from "monaco-editor/editor/editor.api.js";
import "monaco-editor/languages/definitions/python/register.js";
import EditorWorker from "monaco-editor/editor/editor.worker.js?worker";
import "@fontsource/jetbrains-mono/latin-400.css";
(globalThis as typeof globalThis & { MonacoEnvironment: { getWorker: () => Worker } }).MonacoEnvironment = { getWorker: () => new EditorWorker() };
monaco.editor.defineTheme("town-python", {
  base: "vs", inherit: true, rules: [],
  colors: {
    "editor.background": "#FFFAF0", "editor.foreground": "#513B28",
    "editorLineNumber.foreground": "#9B8B70", "editorLineNumber.activeForeground": "#284B43",
    "editor.lineHighlightBackground": "#F3EDDF", "editor.selectionBackground": "#D7E3DA",
    "editorCursor.foreground": "#284B43", "editorGutter.background": "#FFFAF0",
  },
});
export { monaco };
