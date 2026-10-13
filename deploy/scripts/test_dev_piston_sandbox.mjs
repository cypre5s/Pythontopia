#!/usr/bin/env node

import assert from 'node:assert/strict';
import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const baseUrl = (process.env.PYT_PISTON_URL || 'http://127.0.0.1:2000').replace(/\/$/, '');
const pythonVersion = process.env.PYT_PISTON_PYTHON_VERSION || '3.12.0';

async function execute(code, runTimeoutMs = 3_000) {
  const response = await fetch(`${baseUrl}/api/v2/execute`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      language: 'python',
      version: pythonVersion,
      files: [{ name: 'main.py', content: code }],
      run_timeout: runTimeoutMs,
      run_cpu_time: runTimeoutMs,
      run_memory_limit: 134_217_728,
    }),
    signal: AbortSignal.timeout(runTimeoutMs + 10_000),
  });
  assert.equal(response.status, 200, 'Piston execution request must succeed');
  const body = await response.json();
  assert.ok(body.run && typeof body.run === 'object', 'Piston must return a run result');
  return body.run;
}

async function main() {
  const markerDirectory = await mkdtemp(join(tmpdir(), 'pythontopia-piston-'));
  const markerPath = join(markerDirectory, 'host-only-marker.txt');
  try {
    await writeFile(markerPath, 'host-only', { mode: 0o600 });

    const basic = await execute('print("piston-smoke-ok")');
    assert.equal(basic.stdout.trim(), 'piston-smoke-ok', 'Python runtime must execute code');

    const fileProbe = await execute(
      `import os\nprint(os.path.exists(${JSON.stringify(markerPath)}))`,
    );
    assert.equal(fileProbe.stdout.trim(), 'False', 'sandbox must not see a host-only file');

    const networkProbe = await execute(
      'import socket\nsock = socket.socket()\nsock.settimeout(1)\nprint(sock.connect_ex(("127.0.0.1", 2000)))',
    );
    assert.notEqual(
      networkProbe.stdout.trim(),
      '0',
      'sandbox must not reach the host Piston API through loopback',
    );

    const timeout = await execute('while True:\n    pass', 1_000);
    assert.equal(timeout.signal, 'SIGKILL', 'Piston must kill work that exceeds its time budget');

    console.log(
      `Piston sandbox passed: Python ${pythonVersion}, isolated host file and loopback, timeout enforced.`,
    );
  } finally {
    await rm(markerDirectory, { recursive: true, force: true });
  }
}

main().catch((error) => {
  console.error(`[piston-sandbox-smoke] ${error.message}`);
  process.exitCode = 1;
});
