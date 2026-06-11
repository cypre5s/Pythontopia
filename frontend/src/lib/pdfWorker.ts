// pdfjs Web Worker 自定义入口。
//
// Web Worker 是独立线程，有自己的全局作用域，**不共享**主线程 main.ts 注入的
// Promise.withResolvers polyfill。pdfjs-dist 4.x 的 worker 在 getPdfManager/setupDoc
// 运行时会调 Promise.withResolvers，部分环境（旧 Chromium / Electron 内置浏览器）worker
// 内无此方法 → 「Promise.withResolvers is not a function」导致 PDF 解析失败。
//
// 这里先 import polyfill（在 worker 全局装好 withResolvers），再加载 pdfjs 官方 worker。
import '../polyfills';
import 'pdfjs-dist/build/pdf.worker.mjs';
