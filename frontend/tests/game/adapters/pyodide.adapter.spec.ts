import { afterEach, describe, expect, it, vi } from 'vitest';
import { CdnPyodideRunner } from '@/game/adapters/pyodide';

type Listener = (event: MessageEvent) => void;

class FakeWorker {
  readonly posted: unknown[] = [];
  terminated = false;
  autoRespondToRun = true;
  private readonly messageListeners = new Set<Listener>();
  private readonly errorListeners = new Set<(event: ErrorEvent) => void>();

  postMessage(message: unknown): void {
    this.posted.push(message);
    const payload = message as { type?: string; id?: number };
    if (payload.type === 'init') {
      queueMicrotask(() => this.emit({ type: 'ready' }));
    } else if (payload.type === 'run' && this.autoRespondToRun) {
      queueMicrotask(() =>
        this.emit({
          type: 'result',
          id: payload.id,
          result: { stdout: 'hi\n', stderr: '', durationMs: 3, isError: false },
        }),
      );
    }
  }

  addEventListener(type: string, listener: EventListener): void {
    if (type === 'message') this.messageListeners.add(listener as unknown as Listener);
    if (type === 'error') {
      this.errorListeners.add(listener as unknown as (event: ErrorEvent) => void);
    }
  }

  removeEventListener(type: string, listener: EventListener): void {
    if (type === 'message') this.messageListeners.delete(listener as unknown as Listener);
    if (type === 'error') {
      this.errorListeners.delete(listener as unknown as (event: ErrorEvent) => void);
    }
  }

  terminate(): void {
    this.terminated = true;
  }

  private emit(data: unknown): void {
    const event = { data } as MessageEvent;
    for (const listener of this.messageListeners) listener(event);
  }
}

afterEach(() => {
  vi.useRealTimers();
});

describe('CdnPyodideRunner worker isolation', () => {
  it('initialises one module worker and runs Python off the UI thread', async () => {
    const workers: FakeWorker[] = [];
    const runner = new CdnPyodideRunner({
      workerFactory: () => {
        const worker = new FakeWorker();
        workers.push(worker);
        return worker as unknown as Worker;
      },
      timeoutMs: 5_000,
    });

    const result = await runner.run("print('hi')", 'input');
    const second = await runner.run("print('hi')");

    expect(result).toEqual({ stdout: 'hi\n', stderr: '', durationMs: 3, isError: false });
    expect(second.isError).toBe(false);
    expect(workers).toHaveLength(1);
    expect(workers[0]?.posted).toEqual([
      expect.objectContaining({ type: 'init' }),
      expect.objectContaining({ type: 'run', code: "print('hi')", stdin: 'input' }),
      expect.objectContaining({ type: 'run', code: "print('hi')", stdin: '' }),
    ]);

    runner.dispose();
    expect(workers[0]?.terminated).toBe(true);
  });

  it('terminates and recreates the worker when synchronous Python exceeds the deadline', async () => {
    vi.useFakeTimers();
    const workers: FakeWorker[] = [];
    const runner = new CdnPyodideRunner({
      workerFactory: () => {
        const worker = new FakeWorker();
        worker.autoRespondToRun = workers.length > 0;
        workers.push(worker);
        return worker as unknown as Worker;
      },
      timeoutMs: 50,
    });

    const timedOut = runner.run('while True: pass');
    await vi.advanceTimersByTimeAsync(60);

    await expect(timedOut).resolves.toMatchObject({ isError: true, stderr: '执行超时（50ms）' });
    expect(workers[0]?.terminated).toBe(true);

    const recovered = runner.run("print('hi')");
    await vi.runAllTimersAsync();
    await expect(recovered).resolves.toMatchObject({ isError: false, stdout: 'hi\n' });
    expect(workers).toHaveLength(2);
  });

  it('wires the existing budget guard and refuses blocked imports before posting code', async () => {
    const worker = new FakeWorker();
    const runner = new CdnPyodideRunner({
      workerFactory: () => worker as unknown as Worker,
      timeoutMs: 5_000,
    });

    const result = await runner.run('import subprocess\nsubprocess.run(["x"])');

    expect(result.isError).toBe(true);
    expect(result.stderr).toContain('PB-001');
    expect(worker.posted.some((item) => (item as { type?: string }).type === 'run')).toBe(false);
  });
});
