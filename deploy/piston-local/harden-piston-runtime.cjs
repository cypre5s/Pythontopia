const fs = require('fs');

const sourcePath = '/piston_api/src/nosocket/nosocket.c';
let source = fs.readFileSync(sourcePath, 'utf8');
const anchor = '    seccomp_load(ctx);';
const marker = '    char job_tmp_dir[4096];';

if (source.includes(marker)) {
  process.exit(0);
}

if (!source.includes('#include <stdlib.h>')) {
  const includeAnchor = '#include <unistd.h>\n';
  if (!source.includes(includeAnchor)) {
    throw new Error('Pinned Piston nosocket source changed: missing unistd include');
  }
  source = source.replace(includeAnchor, `${includeAnchor}#include <stdlib.h>\n`);
}

if (source.split(anchor).length !== 2) {
  throw new Error('Pinned Piston nosocket source changed: expected one seccomp load call');
}

const hardened = `    char job_tmp_dir[4096];
    if (getcwd(job_tmp_dir, sizeof(job_tmp_dir)) == NULL ||
        setenv("TMPDIR", job_tmp_dir, 1) != 0 ||
        setenv("TMP", job_tmp_dir, 1) != 0 ||
        setenv("TEMP", job_tmp_dir, 1) != 0)
    {
        fprintf(stderr, "Unable to set private job temporary directory\\n");
        return 1;
    }

${anchor}`;

fs.writeFileSync(sourcePath, source.replace(anchor, hardened));
