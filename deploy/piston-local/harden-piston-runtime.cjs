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
