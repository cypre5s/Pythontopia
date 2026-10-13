#!/usr/bin/env node

const baseUrl = (process.env.PYT_PISTON_URL || 'http://127.0.0.1:2000').replace(/\/$/, '');
const pythonVersion = process.env.PYT_PISTON_PYTHON_VERSION || '3.11.13';
const wait = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));

async function getRuntimes() {
  const response = await fetch(`${baseUrl}/api/v2/runtimes`, {
    signal: AbortSignal.timeout(5_000),
  });
  if (!response.ok) throw new Error(`Piston returned HTTP ${response.status}`);
  const runtimes = await response.json();
  return Array.isArray(runtimes) ? runtimes : [];
}

async function ensurePythonRuntime() {
  let lastError;
  for (let attempt = 0; attempt < 60; attempt += 1) {
    try {
      const runtimes = await getRuntimes();
      if (
        runtimes.some(
          (runtime) =>
            runtime?.language === 'python' && runtime?.version === pythonVersion,
        )
      ) {
        console.log(`Piston Python ${pythonVersion} is ready.`);
        return;
      }
      break;
    } catch (error) {
      lastError = error;
      await wait(2_000);
    }
  }

  if (lastError) {
    try {
      await getRuntimes();
    } catch {
      throw new Error(`Piston did not become ready: ${lastError.message}`);
    }
  }

  const response = await fetch(`${baseUrl}/api/v2/packages`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ language: 'python', version: pythonVersion }),
    signal: AbortSignal.timeout(20 * 60_000),
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(
      `Could not install Piston Python ${pythonVersion}: ${body.message || `HTTP ${response.status}`}`,
    );
  }
  console.log(`Installed Piston Python ${pythonVersion}.`);
}

ensurePythonRuntime().catch((error) => {
  console.error(`[dev-piston] ${error.message}`);
  process.exitCode = 1;
});
