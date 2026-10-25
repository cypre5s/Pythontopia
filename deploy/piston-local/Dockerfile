FROM ghcr.io/engineer-man/piston@sha256:2f66b7456189c4d713aa986d98eccd0b6ee16d26c7ec5f21b30e942756fd127a

# ⚠️ 底座 digest 必须是**自带 /usr/local/bin/isolate** 的官方镜像。
# 2026-10-24 事故：旧底座（sha256:6111...）里根本没有 isolate，`find / -name '*isolate*'`
# 全盘为空，导致每次提交都卡在 "Executing job"、执行槽被占死，而 /readyz 依旧绿灯（它
# 只校验运行时列表，不校验能不能执行）。换底座前务必先验证：
#   docker run --rm --entrypoint ls <镜像> /usr/local/bin   # 必须能看到 isolate
# 国内拉取可用南京大学 ghcr 镜像：ghcr.nju.edu.cn/engineer-man/piston

# （已移险）原先在这里对 nosocket.c 打 TMPDIR/失败即拒补丁并重新编译。
# 该补丁强依赖上游源码锚点，底座一升级就 build 失败；且它只能防「作业写共享 /tmp」与
# 「seccomp 加载失败降级」，属额外加固，不是判题必需。真实断网由
# PISTON_DISABLE_NETWORKING=true + 独立 judge-execution 网络保证。
# 源文件 harden-piston-runtime.cjs / test_nosocket_fail_closed.c 保留在仓库里供参考。

# 说明：这里不再钉 node-fetch@2，也不再 patch nosocket。
# - 底座自带与自身代码匹配的 node-fetch，强行降级可能破坏 ESM 引用；
# - nosocket 的 TMPDIR/fail-closed 补丁强依赖上游源码锚点，底座升级即失效；
# - 断网由 PISTON_DISABLE_NETWORKING=true 与独立 judge-execution 网络保证。
# ⚠️ 因此 compose 里的 piston healthcheck 不能用 require('node-fetch')，改用 node 内置 http。

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
# 关键细节一：piston 的 src/index.js 用 `.ppman-installed` 标记文件判定“该版本已安装”，
# 缺这个文件时 /api/v2/runtimes 返回 []、backend /readyz 恒为 not_ready（判题不可用）。
#
# 关键细节二（2026-10-25 实测）：老格式 pkg 只有 `environment` 文件，内容是 shell 片段
# `export PATH=$PWD/bin:$PATH`；而新版 Piston 读的是 dotenv 格式的 `.env`：
#   - 缺 `.env`         → `ENOENT ... /.env`，作业直接失败；
#   - `.env` 写成 shell → PATH 解析不出来 → `run: line 3: python3.11: command not found`。
# 同时 `.env` 在 piston 启动时的 `Loading packages` 阶段加载，运行期改动需重启才生效。
# 因此烘焙时直接写规范的 `.env`，并把 `run` 里的解释器改成绝对路径（双保险，不依赖 PATH）。
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
    printf 'PATH=/piston/packages/python/3.11.0/bin:/usr/local/bin:/usr/bin:/bin\nLD_LIBRARY_PATH=/piston/packages/python/3.11.0/lib\n' \
      > /piston/packages/python/3.11.0/.env; \
    sed -i 's|^python3.11|/piston/packages/python/3.11.0/bin/python3.11|' \
      /piston/packages/python/3.11.0/run; \
    test -f /piston/packages/python/3.11.0/metadata.json; \
    /piston/packages/python/3.11.0/bin/python3.11 -V
