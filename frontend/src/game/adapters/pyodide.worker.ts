/// <reference lib="webworker" />

import type { PyodideWorkerRequest, PyodideWorkerResponse } from './pyodideProtocol';
import { loadPyodide, type PyodideInterface } from 'pyodide';
import createPyodideModule from 'pyodide/pyodide.asm.mjs';

const workerScope = self as DedicatedWorkerGlobalScope;
let pyodidePromise: Promise<PyodideInterface> | null = null;

workerScope.addEventListener('message', (event: MessageEvent<PyodideWorkerRequest>) => {
  void handleMessage(event.data);
});

async function handleMessage(message: PyodideWorkerRequest): Promise<void> {
  if (message.type === 'init') {
    try {
      await ensurePyodide(message.indexUrl);
      post({ type: 'ready' });
    } catch (error) {
      post({ type: 'error', phase: 'init', error: errorMessage(error) });
    }
    return;
  }

  try {
    const pyodide = await ensurePyodide('/pyodide/');
    const stdoutChunks: string[] = [];
    const stderrChunks: string[] = [];
    const inputLines = message.stdin.split(/\r?\n/);
    let inputIndex = 0;
    pyodide.setStdin({
      stdin: () => inputLines[inputIndex++] ?? null,
    });
    pyodide.setStdout({ batched: (chunk) => stdoutChunks.push(chunk) });
    pyodide.setStderr({ batched: (chunk) => stderrChunks.push(chunk) });
    const started = performance.now();
    try {
      pyodide.runPython(guard(message.code));
    } catch (error) {
      stderrChunks.push(errorMessage(error));
    }
    post({
      type: 'result',
      id: message.id,
      result: {
        stdout: stdoutChunks.join(''),
        stderr: stderrChunks.join(''),
        durationMs: Math.round(performance.now() - started),
        isError: stderrChunks.length > 0,
      },
    });
  } catch (error) {
    post({ type: 'error', id: message.id, phase: 'run', error: errorMessage(error) });
  }
}

function ensurePyodide(indexUrl: string): Promise<PyodideInterface> {
  if (!pyodidePromise) {
    pyodidePromise = loadPyodide({ indexURL: indexUrl, createPyodideModule });
  }
  return pyodidePromise;
}

function guard(code: string): string {
  const indented = code
    .split(/\r?\n/)
    .map((line) => `    ${line}`)
    .join('\n');
  return `try:\n${indented}\nexcept Exception:\n    import traceback\n    traceback.print_exc()\n`;
}

function post(message: PyodideWorkerResponse): void {
  workerScope.postMessage(message);
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

export {};
