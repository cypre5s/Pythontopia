FROM ghcr.io/engineer-man/piston@sha256:61119510ea98fba5f45c95e2821990b598dba3f5b27cad98f41303b9c71b6016

# Keep Python's temporary files inside the per-job directory and deny runner access to shared /tmp.
COPY harden-piston-runtime.cjs /tmp/harden-piston-runtime.cjs
COPY test_nosocket_fail_closed.c /tmp/test_nosocket_fail_closed.c
RUN node /tmp/harden-piston-runtime.cjs \
    && gcc -O2 -Dmain=nosocket_main -c /piston_api/src/nosocket/nosocket.c -o /tmp/nosocket-test.o \
    && gcc -O2 /tmp/nosocket-test.o /tmp/test_nosocket_fail_closed.c -Wl,--wrap=seccomp_load -Wl,--wrap=execvp -lseccomp -o /tmp/nosocket-fail-closed-test \
    && /tmp/nosocket-fail-closed-test \
    && rm /tmp/nosocket-test.o /tmp/nosocket-fail-closed-test /tmp/test_nosocket_fail_closed.c \
    && gcc -O2 /piston_api/src/nosocket/nosocket.c -o /usr/local/bin/nosocket -lseccomp \
    && rm /tmp/harden-piston-runtime.cjs

# Piston API currently calls require('node-fetch'), while its image resolves v3 (ESM).
# Keep the local dev image runnable by pinning the latest CommonJS-compatible major.
RUN cd /piston_api \
    && npm install --force --no-audit --no-fund --save-exact node-fetch@2.7.0

# ── Python 运行时预装（离线优先）──────────────────────────────────────────────
# 背景：piston 默认在首次启动时联网从 GitHub Releases 拉运行时并写入
# piston_packages 卷。生产/内网机器访问 GitHub 会被重置（Recv failure），
# piston-runtime-init 因此永远失败、backend 的 /readyz 恒为 not_ready（判题不可用）。
# 解决办法：构建期就把 python 3.11.0 运行时烘进镜像的 /piston/packages，
# 新机器首次创建 piston_packages 命名卷时 Docker 会把该目录内容复制进卷，
# 从此完全离线可用（piston-runtime-init 检测到运行时已存在会直接成功退出）。
#
# 离线构建：把 python-3.11.0.pkg.tar.gz 放进 piston-pkgs/（该目录内容不入库），
#           脚本会自动优先使用本地包，校验 sha256 后解压。
# 联网构建：目录为空时按 PISTON_PYTHON_PKG_URL 下载。
#
# 关键细节：piston 的 src/index.js 用 `.ppman-installed` 标记文件判定“该版本已安装”，
# 缺这个文件时 /api/v2/runtimes 返回 []、backend /readyz 恒为 not_ready（判题不可用）。
# 手工解压必须同时创建该标记。
#
# ⚠️ 若目标机已有旧的 piston_packages 卷，必须先删除再起容器，否则卷会遮蔽镜像内容。
ARG PISTON_PYTHON_PKG_URL="https://github.com/engineer-man/piston/releases/download/pkgs/python-3.11.0.pkg.tar.gz"
ARG PISTON_PYTHON_PKG_SHA256="ebb8b07d9c227829784d81ab56738574ac8461abf000a13568daffbc0eb89377"
COPY piston-pkgs/ /tmp/piston-pkgs/
RUN set -eux; \
    PKG=/tmp/piston-pkgs/python-3.11.0.pkg.tar.gz; \
    if [ ! -s "$PKG" ]; then \
      (wget -q -O "$PKG" "$PISTON_PYTHON_PKG_URL" \
        || curl -fL --retry 3 -o "$PKG" "$PISTON_PYTHON_PKG_URL"); \
    fi; \
    echo "$PISTON_PYTHON_PKG_SHA256  $PKG" | sha256sum -c -; \
    mkdir -p /piston/packages/python/3.11.0; \
    tar -xzf "$PKG" -C /piston/packages/python/3.11.0; \
    rm -rf /tmp/piston-pkgs; \
    touch /piston/packages/python/3.11.0/.ppman-installed; \
    test -f /piston/packages/python/3.11.0/metadata.json
