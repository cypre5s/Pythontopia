FROM ghcr.io/engineer-man/piston@sha256:61119510ea98fba5f45c95e2821990b598dba3f5b27cad98f41303b9c71b6016

# Keep Python's temporary files inside the per-job directory and deny runner access to shared /tmp.
COPY harden-piston-runtime.cjs /tmp/harden-piston-runtime.cjs
RUN node /tmp/harden-piston-runtime.cjs \
    && gcc -O2 /piston_api/src/nosocket/nosocket.c -o /usr/local/bin/nosocket -lseccomp \
    && rm /tmp/harden-piston-runtime.cjs

# Piston API currently calls require('node-fetch'), while its image resolves v3 (ESM).
# Keep the local dev image runnable by pinning the latest CommonJS-compatible major.
RUN cd /piston_api \
    && npm install --force --no-audit --no-fund --save-exact node-fetch@2.7.0
