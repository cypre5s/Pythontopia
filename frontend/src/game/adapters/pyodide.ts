// 隔离原因：保持业务层无 Pyodide 直接依赖，Pyodide 即时预览只能通过 adapter 调用。
// 即时预览结果不会写入后端数据库，仅作学生本地预览使用。
export interface PyodideRunResult {
  stdout: string;
  stderr: string;
  durationMs: number;
  isError: boolean;
}

export interface PyodideRunner {
  init(): Promise<void>;
  run(code: string, stdin?: string): Promise<PyodideRunResult>;
  dispose(): void;
}

interface LoadedPyodide {
  runPython(code: string): unknown;
  globals: { set(key: string, value: unknown): void; get(key: string): unknown };
  setStdin(handler: { stdin: () => string | null }): void;
  setStdout(handler: { batched: (output: string) => void }): void;
  setStderr(handler: { batched: (output: string) => void }): void;
}

type LoadPyodideFn = (options?: { indexURL?: string }) => Promise<LoadedPyodide>;

declare global {
  interface Window {
    loadPyodide?: LoadPyodideFn;
  }
}

const PYODIDE_VERSION = '0.25.0';
// 第三方 CDN（仅作回退）：浏览器「跟踪保护 / Tracking Prevention」会拦截其 storage 访问，
// 触发控制台告警甚至加载失败，所以优先走下面的同源自托管路径。
const CDN_BASE = `https://cdn.jsdelivr.net/pyodide/v${PYODIDE_VERSION}/full/`;
// 同源自托管（scripts/sync-pyodide.mjs 把运行时复制到 public/pyodide/）：避免跨站存储被拦截。
// 应用部署在站点根（nginx `/`、vite dev `/`），故用绝对同源路径。
const LOCAL_BASE = '/pyodide/';

export class CdnPyodideRunner implements PyodideRunner {
  private instance: LoadedPyodide | null = null;
  private initPromise: Promise<void> | null = null;
  // ensureLoader 选定的实际加载基址（同源或 CDN），init 用它作 indexURL 以便找到 asm/stdlib。
  private resolvedBase = LOCAL_BASE;

  async init(): Promise<void> {
    if (this.instance) return;
    if (this.initPromise) {
      await this.initPromise;
      return;
    }
    this.initPromise = (async () => {
      const loader = await this.ensureLoader();
      try {
        this.instance = await loader({ indexURL: this.resolvedBase });
      } catch (error) {
        // 自托管资源缺失/损坏（如未跑 sync-pyodide）→ 回退 CDN 再试一次。
        if (this.resolvedBase !== CDN_BASE) {
          this.resolvedBase = CDN_BASE;
          this.instance = await loader({ indexURL: CDN_BASE });
        } else {
          throw error;
        }
      }
    })();
    try {
      await this.initPromise;
    } finally {
      this.initPromise = null;
    }
  }

  async run(code: string, stdin: string = ''): Promise<PyodideRunResult> {
    await this.init();
    if (!this.instance) {
      throw new Error('Pyodide failed to initialise');
    }
    const stdoutChunks: string[] = [];
    const stderrChunks: string[] = [];
    const stdinSource = (stdin ?? '').split(/\r?\n/);
    let stdinIndex = 0;
    this.instance.setStdin({
      stdin: () => {
        if (stdinIndex >= stdinSource.length) {
          return null;
        }
        const line = stdinSource[stdinIndex];
        stdinIndex += 1;
        return line ?? null;
      },
    });
    this.instance.setStdout({ batched: (chunk: string) => stdoutChunks.push(chunk) });
    this.instance.setStderr({ batched: (chunk: string) => stderrChunks.push(chunk) });
    const started = performance.now();
    try {
      const guarded = `try:\n${indent(code)}\nexcept Exception as exc:\n    import traceback\n    traceback.print_exc()\n`;
      this.instance.runPython(guarded);
      return {
        stdout: stdoutChunks.join(''),
        stderr: stderrChunks.join(''),
        durationMs: Math.round(performance.now() - started),
        isError: stderrChunks.length > 0,
      };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      stderrChunks.push(message);
      return {
        stdout: stdoutChunks.join(''),
        stderr: stderrChunks.join(''),
        durationMs: Math.round(performance.now() - started),
        isError: true,
      };
    }
  }

  dispose(): void {
    this.instance = null;
  }

  private async ensureLoader(): Promise<LoadPyodideFn> {
    if (typeof window === 'undefined') {
      throw new Error('Pyodide can only run in a browser');
    }
    if (window.loadPyodide) {
      return window.loadPyodide;
    }
    // 同源优先，失败回退 CDN；记录最终生效的基址供 init 设置 indexURL。
    try {
      await loadScript(`${LOCAL_BASE}pyodide.js`);
      this.resolvedBase = LOCAL_BASE;
    } catch {
      await loadScript(`${CDN_BASE}pyodide.js`);
      this.resolvedBase = CDN_BASE;
    }
    if (!window.loadPyodide) {
      throw new Error('Pyodide script loaded but loadPyodide is undefined');
    }
    return window.loadPyodide;
  }
}

/** 注入一个 <script> 并等待加载完成；失败 reject。 */
function loadScript(src: string): Promise<void> {
  return new Promise<void>((resolve, reject) => {
    const script = document.createElement('script');
    script.src = src;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error(`Failed to load script: ${src}`));
    document.head.appendChild(script);
  });
}

function indent(text: string): string {
  return text
    .split(/\r?\n/)
    .map((line) => '    ' + line)
    .join('\n');
}

let runnerSingleton: PyodideRunner | null = null;

export function getPyodideRunner(): PyodideRunner {
  if (!runnerSingleton) {
    runnerSingleton = new CdnPyodideRunner();
  }
  return runnerSingleton;
}

export function setPyodideRunner(runner: PyodideRunner | null): void {
  runnerSingleton = runner;
}
