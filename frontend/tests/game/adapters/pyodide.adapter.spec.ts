import { describe, it, expect, beforeEach, vi } from 'vitest';
import { CdnPyodideRunner } from '@/game/adapters/pyodide';

describe('CdnPyodideRunner', () => {
  beforeEach(() => {
    document.head.innerHTML = '';
    delete (window as unknown as { loadPyodide?: unknown }).loadPyodide;
    Object.defineProperty(performance, 'now', { value: () => 0, configurable: true });
  });

  it('initialises once and captures stdout', async () => {
    const stdoutHandlers: Array<{ batched: (chunk: string) => void }> = [];
    const stderrHandlers: Array<{ batched: (chunk: string) => void }> = [];
    const mockInstance = {
      runPython: vi.fn((code: string) => {
        if (code.includes("print('hi')") || code.includes('print("hi")')) {
          stdoutHandlers[0]?.batched('hi\n');
        } else if (code.includes('boom')) {
          stderrHandlers[0]?.batched('Traceback');
        }
        return undefined;
      }),
      globals: { set: vi.fn(), get: vi.fn() },
      setStdin: vi.fn(),
      setStdout: vi.fn((handler: { batched: (chunk: string) => void }) => {
        stdoutHandlers.length = 0;
        stdoutHandlers.push(handler);
      }),
      setStderr: vi.fn((handler: { batched: (chunk: string) => void }) => {
        stderrHandlers.length = 0;
        stderrHandlers.push(handler);
      }),
    };
    const loadPyodide = vi.fn(async () => mockInstance);
    (window as unknown as { loadPyodide?: typeof loadPyodide }).loadPyodide = loadPyodide;

    const runner = new CdnPyodideRunner();
    const result = await runner.run("print('hi')");
    expect(loadPyodide).toHaveBeenCalledTimes(1);
    expect(result.stdout).toContain('hi');
    expect(result.isError).toBe(false);

    await runner.run("print('hi')");
    expect(loadPyodide).toHaveBeenCalledTimes(1);

    runner.dispose();
  });

  it('captures stderr when python raises', async () => {
    const stdoutHandlers: Array<{ batched: (chunk: string) => void }> = [];
    const stderrHandlers: Array<{ batched: (chunk: string) => void }> = [];
    const mockInstance = {
      runPython: vi.fn(() => {
        stderrHandlers[0]?.batched('Traceback');
      }),
      globals: { set: vi.fn(), get: vi.fn() },
      setStdin: vi.fn(),
      setStdout: vi.fn((handler: { batched: (chunk: string) => void }) => {
        stdoutHandlers.push(handler);
      }),
      setStderr: vi.fn((handler: { batched: (chunk: string) => void }) => {
        stderrHandlers.push(handler);
      }),
    };
    (window as unknown as { loadPyodide?: unknown }).loadPyodide = vi.fn(
      async () => mockInstance,
    );
    const runner = new CdnPyodideRunner();
    const result = await runner.run('boom');
    expect(result.stderr).toContain('Traceback');
    expect(result.isError).toBe(true);
  });
});
