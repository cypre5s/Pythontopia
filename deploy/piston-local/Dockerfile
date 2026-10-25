FROM ghcr.io/engineer-man/piston@sha256:61119510ea98fba5f45c95e2821990b598dba3f5b27cad98f41303b9c71b6016

# Keep Python's temporary files inside the per-job directory and deny runner access to shared /tmp.
COPY harden-piston-runtime.cjs /tmp/harden-piston-runtime.cjs
COPY test_nosocket_fail_closed.c /tmp/test_nosocket_fail_closed.c
COPY piston_resource_meter.c /tmp/piston_resource_meter.c
RUN node /tmp/harden-piston-runtime.cjs \
    && gcc -O2 -Dmain=nosocket_main -c /piston_api/src/nosocket/nosocket.c -o /tmp/nosocket-test.o \
    && gcc -O2 /tmp/nosocket-test.o /tmp/test_nosocket_fail_closed.c -Wl,--wrap=seccomp_load -Wl,--wrap=execvp -lseccomp -o /tmp/nosocket-fail-closed-test \
    && /tmp/nosocket-fail-closed-test \
    && rm /tmp/nosocket-test.o /tmp/nosocket-fail-closed-test /tmp/test_nosocket_fail_closed.c \
    && gcc -O2 /piston_api/src/nosocket/nosocket.c -o /usr/local/bin/nosocket -lseccomp \
    && gcc -O2 -Wall -Wextra -Werror /tmp/piston_resource_meter.c -o /usr/local/bin/piston-resource-meter \
    && chmod 755 /usr/local/bin/piston-resource-meter \
    && rm /tmp/harden-piston-runtime.cjs /tmp/piston_resource_meter.c

# Piston API currently calls require('node-fetch'), while its image resolves v3 (ESM).
# Keep the local dev image runnable by pinning the latest CommonJS-compatible major.
RUN cd /piston_api \
    && npm install --force --no-audit --no-fund --save-exact node-fetch@2.7.0
