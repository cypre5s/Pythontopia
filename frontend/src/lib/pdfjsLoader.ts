// pdfjs-dist 4.x 加载适配层。
//
// 业务层不直接 import pdfjs-dist，全部走本文件，方便：
//   - 在 vitest（jsdom）里 stub worker / 不实际渲染；
//   - 集中处理 GlobalWorkerOptions.workerSrc 配置；
//   - 后续切换 pdfjs 版本只改一处。
//
// 动态 import 原因：pdfjs-dist 4.7 顶层 import 时会调 `Promise.withResolvers`
// （Node 22+ 才有），jsdom（Node 20）启动单测时会炸；改成按需 import 让
// LibraryPage.spec 这种不实际渲染 PDF 的场景仍然能加载本模块。

// 仅类型导入，不触发运行时代码。
type PDFDocumentLoadingTask = import('pdfjs-dist').PDFDocumentLoadingTask;
type PDFDocumentProxy = import('pdfjs-dist').PDFDocumentProxy;
type PDFPageProxy = import('pdfjs-dist').PDFPageProxy;

let workerInitialised = false;
let pdfModulePromise: Promise<typeof import('pdfjs-dist')> | null = null;

async function getPdfModule(): Promise<typeof import('pdfjs-dist')> {
  if (!pdfModulePromise) {
    pdfModulePromise = import('pdfjs-dist');
  }
  return pdfModulePromise;
}

async function ensureWorker(): Promise<void> {
  if (workerInitialised) return;
  if (typeof window === 'undefined') return;
  const mod = await getPdfModule();
  // 用自定义 worker 入口（内部先打 Promise.withResolvers polyfill 再加载 pdfjs 官方 worker）：
  // worker 线程全局独立于主线程 polyfill，必须在 worker 内单独注入，否则
  // 「Promise.withResolvers is not a function」在 worker 内（getPdfManager）触发，PDF 解析失败。
  // 标准 Web Worker 写法（new URL + import.meta.url），Vite 会把入口打包成独立 worker chunk。
  const worker = new Worker(new URL('./pdfWorker.ts', import.meta.url), { type: 'module' });
  mod.GlobalWorkerOptions.workerPort = worker;
  workerInitialised = true;
}

export interface LoadedPdfPage {
  pdf: PDFDocumentProxy;
  page: PDFPageProxy;
}

export interface LoadPdfPageOptions {
  url: string;
  pageNo: number;
  /** 透传给 pdfjs 的请求头，用于带 Authorization 等。 */
  httpHeaders?: Record<string, string>;
  withCredentials?: boolean;
}

let activeTask: PDFDocumentLoadingTask | null = null;

/**
 * 加载远端 PDF 文档并返回指定页 proxy。
 *
 * - 同一 url 同一时间只允许一个 loading task：高频翻页时主动 destroy 上一个，
 *   防止 pdfjs 在 jsdom 单测里把同一文件并行下载多次内存爆炸。
 * - 失败时把 task 清掉，避免后续调用永远命中已 destroyed 的 promise。
 */
export async function loadPdfPage(options: LoadPdfPageOptions): Promise<LoadedPdfPage> {
  await ensureWorker();
  const mod = await getPdfModule();
  if (activeTask) {
    try {
      await activeTask.destroy();
    } catch {
      /* 旧 task 被取消属于正常路径，忽略 */
    }
    activeTask = null;
  }
  const task = mod.getDocument({
    url: options.url,
    withCredentials: options.withCredentials ?? true,
    httpHeaders: options.httpHeaders ?? {},
  });
  activeTask = task;
  try {
    const pdf = await task.promise;
    const page = await pdf.getPage(options.pageNo);
    return { pdf, page };
  } catch (error) {
    activeTask = null;
    throw error;
  }
}

export interface RenderPageOptions {
  page: PDFPageProxy;
  canvas: HTMLCanvasElement;
  /** 期望渲染宽度（CSS px），高度自动按宽高比换算。 */
  targetWidthPx: number;
  /** 设备像素比，默认 window.devicePixelRatio，兜底 1。 */
  devicePixelRatio?: number;
}

export interface RenderPageResult {
  renderedWidth: number;
  renderedHeight: number;
}

// 同一 canvas 上同一时刻只允许一个 renderTask；新 render 触发前取消上一个，
// 否则 pdfjs 抛 "Cannot use the same canvas during multiple render() operations"。
const canvasRenderTasks = new WeakMap<HTMLCanvasElement, { cancel(): void }>();

/** 把指定页渲染到 canvas（按 targetWidthPx 自适应 DPR）。 */
export async function renderPdfPageToCanvas(
  options: RenderPageOptions,
): Promise<RenderPageResult> {
  const dpr = options.devicePixelRatio
    ?? (typeof window !== 'undefined' && window.devicePixelRatio ? window.devicePixelRatio : 1);
  const baseViewport = options.page.getViewport({ scale: 1 });
  const cssScale = options.targetWidthPx / baseViewport.width;
  const renderScale = cssScale * dpr;
  const viewport = options.page.getViewport({ scale: renderScale });
  const canvas = options.canvas;

  // 取消同 canvas 上正在跑的旧 render，避免 pdfjs RaceError。
  const previous = canvasRenderTasks.get(canvas);
  if (previous) {
    try {
      previous.cancel();
    } catch {
      /* 旧 task 已结束属于正常路径，忽略 */
    }
    canvasRenderTasks.delete(canvas);
  }

  canvas.width = Math.ceil(viewport.width);
  canvas.height = Math.ceil(viewport.height);
  canvas.style.width = `${Math.ceil(viewport.width / dpr)}px`;
  canvas.style.height = `${Math.ceil(viewport.height / dpr)}px`;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    throw new Error('2D canvas context unavailable');
  }
  const renderTask = options.page.render({ canvasContext: ctx, viewport });
  canvasRenderTasks.set(canvas, renderTask);
  try {
    await renderTask.promise;
    return {
      renderedWidth: viewport.width / dpr,
      renderedHeight: viewport.height / dpr,
    };
  } catch (error) {
    // pdfjs 在 cancel 后会抛 RenderingCancelledException，直接转成空结果让上层不弹 UI 错误。
    const name = error instanceof Error ? error.name : String(error);
    if (name === 'RenderingCancelledException' || /cancelled/i.test(String(error))) {
      return { renderedWidth: 0, renderedHeight: 0 };
    }
    throw error;
  } finally {
    if (canvasRenderTasks.get(canvas) === renderTask) {
      canvasRenderTasks.delete(canvas);
    }
  }
}

/** 仅测试用：判断 worker 是否已注册。 */
export function isPdfWorkerInitialisedForTest(): boolean {
  return workerInitialised;
}

/** 仅测试用：重置 worker 状态，便于多次断言 ensureWorker 调用。 */
export async function resetPdfWorkerForTest(): Promise<void> {
  workerInitialised = false;
  if (pdfModulePromise) {
    const mod = await pdfModulePromise;
    mod.GlobalWorkerOptions.workerSrc = '';
  }
}
