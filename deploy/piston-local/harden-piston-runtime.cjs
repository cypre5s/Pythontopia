const fs = require('fs');

const sourcePath = '/piston_api/src/nosocket/nosocket.c';
let source = fs.readFileSync(sourcePath, 'utf8');
const loadAnchor = '    seccomp_load(ctx);';
const failClosedMarker = '    if (seccomp_load(ctx) < 0)';
const tempMarker = '    char job_tmp_dir[4096];';

if (!source.includes(tempMarker)) {
  if (!source.includes('#include <stdlib.h>')) {
    const includeAnchor = '#include <unistd.h>\n';
    if (!source.includes(includeAnchor)) {
      throw new Error('Pinned Piston nosocket source changed: missing unistd include');
    }
    source = source.replace(includeAnchor, `${includeAnchor}#include <stdlib.h>\n`);
  }
  const tempAnchor = source.includes(failClosedMarker) ? failClosedMarker : loadAnchor;
  if (source.split(tempAnchor).length !== 2) {
    throw new Error('Pinned Piston nosocket source changed: missing unique job setup anchor');
  }
  source = source.replace(
    tempAnchor,
    `    char job_tmp_dir[4096];
    if (getcwd(job_tmp_dir, sizeof(job_tmp_dir)) == NULL ||
        setenv("TMPDIR", job_tmp_dir, 1) != 0 ||
        setenv("TMP", job_tmp_dir, 1) != 0 ||
        setenv("TEMP", job_tmp_dir, 1) != 0)
    {
        fprintf(stderr, "Unable to set private job temporary directory\\n");
        return 1;
    }

${tempAnchor}`,
  );
}

if (!source.includes(failClosedMarker)) {
  if (source.split(loadAnchor).length !== 2) {
    throw new Error('Pinned Piston nosocket source changed: expected one seccomp load call');
  }
  source = source.replace(
    loadAnchor,
    `    if (seccomp_load(ctx) < 0)
    {
        fprintf(stderr, "Unable to load seccomp filter\\n");
        seccomp_release(ctx);
        return 1;
    }`,
  );
}

fs.writeFileSync(sourcePath, source);

// Piston 3.1 enforces rlimits but does not include runtime measurements in its API response.
const jobPath = '/piston_api/src/job.js';
let jobSource = fs.readFileSync(jobPath, 'utf8');
if (!jobSource.includes('/usr/local/bin/piston-resource-meter')) {
  const commandAnchor = "const proc_call = [\n                'nice',";
  if (jobSource.split(commandAnchor).length !== 2) {
    throw new Error('Pinned Piston job source changed: missing unique command anchor');
  }
  jobSource = jobSource.replace(
    commandAnchor,
    "const proc_call = [\n                '/usr/local/bin/piston-resource-meter',\n                'nice',",
  );

  const outputAnchor = "            var output = '';\n";
  if (jobSource.split(outputAnchor).length !== 2) {
    throw new Error('Pinned Piston job source changed: missing unique output anchor');
  }
  jobSource = jobSource.replace(
    outputAnchor,
    outputAnchor + "            var resource_metrics = '';\n            var started_at = process.hrtime.bigint();\n",
  );

  const stdioAnchor = "                stdio: 'pipe',";
  if (jobSource.split(stdioAnchor).length !== 2) {
    throw new Error('Pinned Piston job source changed: missing unique stdio anchor');
  }
  jobSource = jobSource.replace(stdioAnchor, "                stdio: ['pipe', 'pipe', 'pipe', 'pipe'],");

  const stdinAnchor = '            if (eventBus === null) {';
  if (jobSource.split(stdinAnchor).length !== 2) {
    throw new Error('Pinned Piston job source changed: missing unique stdin anchor');
  }
  jobSource = jobSource.replace(
    stdinAnchor,
    "            proc.stdio[3].on('data', data => {\n                resource_metrics += data.toString();\n            });\n\n" + stdinAnchor,
  );

  const exitAnchor = "            proc.on('exit', (code, signal) => {\n                exit_cleanup();\n\n                resolve({ stdout, stderr, code, signal, output });";
  if (jobSource.split(exitAnchor).length !== 2) {
    throw new Error('Pinned Piston job source changed: missing unique exit anchor');
  }
  jobSource = jobSource.replace(
    exitAnchor,
    "            proc.on('exit', () => {\n"
      + "                exit_cleanup();\n"
      + "            });\n\n"
      + "            // close waits for the resource pipe to drain before metrics are read.\n"
      + "            proc.on('close', (code, signal) => {\n"
      + "                const wall_time = Number(process.hrtime.bigint() - started_at) / 1e6;\n"
      + "                const memory_kb = Number.parseInt(resource_metrics.trim(), 10);\n"
      + "                const memory = Number.isSafeInteger(memory_kb) && memory_kb > 0 ? memory_kb * 1024 : null;\n"
      + "                resolve({ stdout, stderr, code, signal, output, wall_time, memory });",
  );

  fs.writeFileSync(jobPath, jobSource);
}
