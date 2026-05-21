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
const PYODIDE_INDEX_URL = `https://cdn.jsdelivr.net/pyodide/v${PYODIDE_VERSION}/full/`;

export class CdnPyodideRunner implements PyodideRunner {
  private instance: LoadedPyodide | null = null;
  private initPromise: Promise<void> | null = null;

  async init(): Promise<void> {
    if (this.instance) return;
    if (this.initPromise) {
      await this.initPromise;
      return;
    }
    this.initPromise = (async () => {
      const loader = await this.ensureLoader();
      this.instance = await loader({ indexURL: PYODIDE_INDEX_URL });
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
    await new Promise<void>((resolve, reject) => {
      const script = document.createElement('script');
      script.src = `${PYODIDE_INDEX_URL}pyodide.js`;
      script.onload = () => resolve();
      script.onerror = () => reject(new Error('Failed to load Pyodide script'));
      document.head.appendChild(script);
    });
    if (!window.loadPyodide) {
      throw new Error('Pyodide script loaded but loadPyodide is undefined');
    }
    return window.loadPyodide;
  }
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
