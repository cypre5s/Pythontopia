import type { PyodideRunResult } from './pyodide';

export type PyodideWorkerRequest =
  | { type: 'init'; indexUrl: string }
  | { type: 'run'; id: number; code: string; stdin: string };

export type PyodideWorkerResponse =
  | { type: 'ready' }
  | { type: 'result'; id: number; result: PyodideRunResult }
  | { type: 'error'; id?: number; phase: 'init' | 'run'; error: string };
