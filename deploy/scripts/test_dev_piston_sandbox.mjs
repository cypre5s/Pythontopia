#!/usr/bin/env node

import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
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
  const runtimePatch = await readFile(
    new URL('../piston-local/harden-piston-runtime.cjs', import.meta.url),
    'utf8',
  );
  assert.match(
    runtimePatch,
    /proc\.on\('close'/,
    'resource metrics must wait for every child pipe to close',
  );

  const markerDirectory = await mkdtemp(join(tmpdir(), 'pythontopia-piston-'));
  const markerPath = join(markerDirectory, 'host-only-marker.txt');
  try {
    await writeFile(markerPath, 'host-only', { mode: 0o600 });

    const basic = await execute('print("piston-smoke-ok")');
    assert.equal(basic.stdout.trim(), 'piston-smoke-ok', 'Python runtime must execute code');
    assert.ok(Number.isFinite(basic.wall_time) && basic.wall_time > 0, 'Piston must report measured wall time');
    assert.ok(Number.isSafeInteger(basic.memory) && basic.memory > 0, 'Piston must report measured peak RSS in bytes');

    const larger = await execute('buffer = bytearray(16 * 1024 * 1024)\nprint(len(buffer))');
    assert.ok(larger.memory > basic.memory, 'peak RSS must reflect additional process memory');

    const identityProbe = await execute('import os\nprint(os.geteuid())');
    assert.match(identityProbe.stdout.trim(), /^\d+$/, 'sandbox must report a numeric effective UID');
    assert.notEqual(identityProbe.stdout.trim(), '0', 'sandboxed Python must not run as root');

    const fileProbe = await execute(
      `import os\nprint(os.path.exists(${JSON.stringify(markerPath)}))`,
    );
    assert.equal(fileProbe.stdout.trim(), 'False', 'sandbox must not see a host-only file');

    const tempProbe = await execute(
      'import os, tempfile\n'
        + 'with tempfile.NamedTemporaryFile() as f:\n'
        + ' print(os.path.realpath(os.path.dirname(f.name)) == os.path.realpath(os.getcwd()))',
    );
    assert.equal(tempProbe.stdout.trim(), 'True', 'Python temporary files must stay inside this job directory');

    const sharedTempPath = `/tmp/pythontopia-piston-${process.pid}-${Date.now()}`;
    const sharedTempProbe = await execute(
      `import os\ntry:\n open(${JSON.stringify(sharedTempPath)}, "w").close()\n os.remove(${JSON.stringify(sharedTempPath)})\n print("writable")\nexcept PermissionError:\n print("blocked")`,
    );
    assert.equal(sharedTempProbe.stdout.trim(), 'blocked', 'sandbox jobs must not access shared /tmp');

    const networkProbe = await execute(
      'import socket\nsock = socket.socket()\nsock.settimeout(1)\nprint(sock.connect_ex(("127.0.0.1", 2000)))',
    );
    assert.notEqual(
      networkProbe.stdout.trim(),
      '0',
      'sandbox must not reach the host Piston API through loopback',
    );

    const compatSocketProbe = await execute(
      'import ctypes, mmap, os, platform, struct\n'
        + 'if platform.machine() != "x86_64":\n print("unsupported-architecture")\n'
        + 'else:\n'
        + ' libc = ctypes.CDLL(None, use_errno=True)\n'
        + ' libc.mmap.argtypes = [ctypes.c_void_p, ctypes.c_size_t, ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_long]\n'
        + ' libc.mmap.restype = ctypes.c_void_p\n'
        + ' flags = 0x02 | 0x20 | 0x40\n'
        + ' args = libc.mmap(None, 4096, 3, flags, -1, 0)\n'
        + ' code = libc.mmap(None, 4096, 7, flags, -1, 0)\n'
        + ' if args in (None, ctypes.c_void_p(-1).value) or code in (None, ctypes.c_void_p(-1).value): raise OSError(ctypes.get_errno(), "mmap")\n'
        + ' ctypes.memmove(args, struct.pack("<III", 2, 1, 0), 12)\n'
        + ' payload = b"\\xb8\\x66\\x00\\x00\\x00\\xbb\\x01\\x00\\x00\\x00\\xb9" + struct.pack("<I", args) + b"\\xcd\\x80\\xc3"\n'
        + ' ctypes.memmove(code, payload, len(payload))\n'
        + ' create_socket = ctypes.CFUNCTYPE(ctypes.c_int)(code)\n'
        + ' descriptor = create_socket()\n'
        + ' print("blocked" if descriptor < 0 else "allowed")\n'
        + ' if descriptor >= 0: os.close(descriptor)',
    );
    assert.ok(
      ['blocked', 'unsupported-architecture'].includes(compatSocketProbe.stdout.trim()),
      'compat socketcall must be blocked on x86_64 (or explicitly skipped elsewhere)',
    );

    const timeout = await execute('while True:\n    pass', 1_000);
    assert.equal(timeout.signal, 'SIGKILL', 'Piston must kill work that exceeds its time budget');

    console.log(
      `Piston sandbox passed: Python ${pythonVersion}, non-root identity, isolated host file and loopback, timeout enforced.`,
    );
  } finally {
    await rm(markerDirectory, { recursive: true, force: true });
  }
}

main().catch((error) => {
  console.error(`[piston-sandbox-smoke] ${error.message}`);
  process.exitCode = 1;
});
