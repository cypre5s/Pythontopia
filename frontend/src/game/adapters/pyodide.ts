// 隔离原因：业务层只依赖该 adapter。学生代码在可终止的 module Worker 内运行，永不阻塞 Vue/Phaser UI；
// 即时预览不写后端，正式成绩始终以服务端隔离判题为准。
import {
  DEFAULT_PYODIDE_BUDGET,
  PyodideBudgetGuard,
  type PyodideExecutionStatus,
} from '@/services/pyodideBudget';
import type { PyodideWorkerRequest, PyodideWorkerResponse } from './pyodideProtocol';

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

export type PyodideStatus = 'idle' | 'loading' | 'ready' | 'error';
let pyodideStatus: PyodideStatus = 'idle';
const statusListeners = new Set<(status: PyodideStatus) => void>();

function setPyodideStatus(next: PyodideStatus): void {
  pyodideStatus = next;
  for (const listener of [...statusListeners]) {
    try {
      listener(next);
    } catch {
      // 单个 UI 监听器异常不能影响 Worker 生命周期。
    }
  }
}

export function getPyodideStatus(): PyodideStatus {
  return pyodideStatus;
}

export function onPyodideStatus(listener: (status: PyodideStatus) => void): () => void {
  statusListeners.add(listener);
  return () => statusListeners.delete(listener);
}

interface PendingRun {
  resolve: (result: PyodideRunResult) => void;
  timer: ReturnType<typeof setTimeout>;
  started: number;
}

interface CdnPyodideRunnerOptions {
  workerFactory?: () => Worker;
  timeoutMs?: number;
  budget?: PyodideBudgetGuard;
}

const LOCAL_BASE = '/pyodide/';

/**
 * 历史类名保留给现有调用方；实现已改为官方推荐的 module Web Worker 架构。
 * 超时直接 terminate 整个 Worker/解释器，下一次运行自动创建全新实例，不存在主线程死循环。
 */
export class CdnPyodideRunner implements PyodideRunner {
  private readonly workerFactory: () => Worker;
  private readonly timeoutMs: number;
  private readonly budget: PyodideBudgetGuard;
  private worker: Worker | null = null;
  private initPromise: Promise<void> | null = null;
  private resolveInit: (() => void) | null = null;
  private rejectInit: ((reason: Error) => void) | null = null;
  private readonly pending = new Map<number, PendingRun>();
  private nextId = 1;
  private queue: Promise<void> = Promise.resolve();

  constructor(options: CdnPyodideRunnerOptions = {}) {
    this.workerFactory = options.workerFactory ?? defaultWorkerFactory;
    this.timeoutMs = options.timeoutMs ?? DEFAULT_PYODIDE_BUDGET.cpuTimeoutMs;
    this.budget = options.budget ?? new PyodideBudgetGuard();
  }

  async init(): Promise<void> {
    if (this.worker && !this.initPromise) return;
    if (this.initPromise) return this.initPromise;

    setPyodideStatus('loading');
    const worker = this.workerFactory();
    this.worker = worker;
    worker.addEventListener('message', this.handleMessage);
    worker.addEventListener('error', this.handleWorkerError);
    this.initPromise = new Promise<void>((resolve, reject) => {
      this.resolveInit = resolve;
      this.rejectInit = reject;
    });
    worker.postMessage({ type: 'init', indexUrl: LOCAL_BASE } satisfies PyodideWorkerRequest);
    try {
      await this.initPromise;
      setPyodideStatus('ready');
    } catch (error) {
      setPyodideStatus('error');
      this.terminateWorker();
      throw error;
    } finally {
      this.initPromise = null;
      this.resolveInit = null;
      this.rejectInit = null;
    }
  }

  run(code: string, stdin: string = ''): Promise<PyodideRunResult> {
    const task = this.queue.then(() => this.runOne(code, stdin));
    this.queue = task.then(
      () => undefined,
      () => undefined,
    );
    return task;
  }

  dispose(): void {
    this.finishAll(errorResult('Pyodide Worker 已关闭'));
    this.terminateWorker();
    setPyodideStatus('idle');
  }

  private async runOne(code: string, stdin: string): Promise<PyodideRunResult> {
    const scan = this.budget.staticScanCode(code);
    if (scan.blocked.length > 0) {
      return errorResult(scan.blocked.join('\n'));
    }
    const allowance = this.budget.canExecute();
    if (!allowance.allowed) {
      const message =
        allowance.reason === 'minute_limit_exceeded'
          ? '本地预览过于频繁，请稍后再试'
          : '今日本地预览次数已达上限';
      return errorResult(message);
    }

    await this.init();
    const worker = this.worker;
    if (!worker) return errorResult('Pyodide Worker 初始化失败');
    const id = this.nextId++;
    return new Promise<PyodideRunResult>((resolve) => {
      const started = performance.now();
      const timer = setTimeout(() => {
        this.pending.delete(id);
        const result = errorResult(`执行超时（${this.timeoutMs}ms）`, this.timeoutMs);
        this.budget.recordExecution(this.timeoutMs, 'timeout');
        resolve(result);
        setPyodideStatus('error');
        this.terminateWorker();
      }, this.timeoutMs);
      this.pending.set(id, { resolve, timer, started });
      worker.postMessage({ type: 'run', id, code, stdin } satisfies PyodideWorkerRequest);
    });
  }

  private readonly handleMessage = (event: MessageEvent<PyodideWorkerResponse>): void => {
    const message = event.data;
    if (message.type === 'ready') {
      this.resolveInit?.();
      return;
    }
    if (message.type === 'error') {
      if (message.phase === 'init') {
        this.rejectInit?.(new Error(message.error));
        return;
      }
      if (message.id !== undefined) {
        this.finish(message.id, errorResult(message.error));
      }
      return;
    }
    this.finish(message.id, message.result);
  };

  private readonly handleWorkerError = (event: ErrorEvent): void => {
    const message = event.message || 'Pyodide Worker 异常';
    this.rejectInit?.(new Error(message));
    this.finishAll(errorResult(message));
    setPyodideStatus('error');
    this.terminateWorker();
  };

  private finish(id: number, result: PyodideRunResult): void {
    const pending = this.pending.get(id);
    if (!pending) return;
    clearTimeout(pending.timer);
    this.pending.delete(id);
    const durationMs = result.durationMs || Math.round(performance.now() - pending.started);
    const normalized = { ...result, durationMs };
    const status: PyodideExecutionStatus = normalized.isError ? 'error' : 'ok';
    this.budget.recordExecution(durationMs, status);
    pending.resolve(normalized);
  }

  private finishAll(result: PyodideRunResult): void {
    for (const [id, pending] of this.pending) {
      clearTimeout(pending.timer);
      this.pending.delete(id);
      pending.resolve(result);
    }
  }

  private terminateWorker(): void {
    if (this.worker) {
      this.worker.removeEventListener('message', this.handleMessage);
      this.worker.removeEventListener('error', this.handleWorkerError);
      this.worker.terminate();
      this.worker = null;
    }
    this.initPromise = null;
    this.resolveInit = null;
    this.rejectInit = null;
  }
}

function defaultWorkerFactory(): Worker {
  return new Worker(new URL('./pyodide.worker.ts', import.meta.url), {
    type: 'module',
    name: 'pythontopia-pyodide',
  });
}

function errorResult(stderr: string, durationMs = 0): PyodideRunResult {
  return { stdout: '', stderr, durationMs, isError: true };
}

let runnerSingleton: PyodideRunner | null = null;

export function getPyodideRunner(): PyodideRunner {
  if (!runnerSingleton) runnerSingleton = new CdnPyodideRunner();
  return runnerSingleton;
}

export function setPyodideRunner(runner: PyodideRunner | null): void {
  runnerSingleton = runner;
}

export function warmPyodide(): void {
  if (typeof window === 'undefined') return;
  if (pyodideStatus === 'loading' || pyodideStatus === 'ready') return;
  const start = (): void => {
    void getPyodideRunner()
      .init()
      .catch(() => undefined);
  };
  const ric = (window as Window & {
    requestIdleCallback?: (cb: () => void, opts?: { timeout?: number }) => number;
  }).requestIdleCallback;
  if (typeof ric === 'function') ric(start, { timeout: 2_500 });
  else window.setTimeout(start, 400);
}
