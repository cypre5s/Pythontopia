// 运行时 polyfill：必须在任何业务模块（尤其 pdfjs-dist）之前执行。
//
// Promise.withResolvers（ES2024）：pdfjs-dist 4.x 顶层依赖它来加载 worker / 文档。
// 部分运行环境（旧 Chromium、Electron 内置浏览器、未更新的浏览器、Node 20 jsdom）无此方法，
// 会报「PDF 加载失败：Promise.withResolvers is not a function」导致课件 PDF 完全无法显示。
// 这里做标准等价实现，已存在则不覆盖（用原生实现）。

interface PromiseWithResolversResult<T> {
  promise: Promise<T>;
  resolve: (value: T | PromiseLike<T>) => void;
  reject: (reason?: unknown) => void;
}

type PromiseCtorMaybeWithResolvers = PromiseConstructor & {
  withResolvers?: <T>() => PromiseWithResolversResult<T>;
};

const PromiseCtor = Promise as PromiseCtorMaybeWithResolvers;

if (typeof PromiseCtor.withResolvers !== 'function') {
  PromiseCtor.withResolvers = function withResolvers<T>(): PromiseWithResolversResult<T> {
    let resolve!: (value: T | PromiseLike<T>) => void;
    let reject!: (reason?: unknown) => void;
    const promise = new Promise<T>((res, rej) => {
      resolve = res;
      reject = rej;
    });
    return { promise, resolve, reject };
  };
}

export {};
