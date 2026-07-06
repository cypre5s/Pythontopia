#!/usr/bin/env bash
# 受限网络下一键重建：host 网络 + 127.0.0.1:7892 代理 + 国内镜像。
set -euo pipefail
cd "$(dirname "$0")/.."

PROXY="${PYT_BUILD_PROXY:-http://127.0.0.1:7892}"

if ! curl -sf --connect-timeout 3 -x "$PROXY" https://registry.npmmirror.com/ -o /dev/null; then
  echo "ERROR: 代理 $PROXY 不可用。请先启动 Clash/7892，或设置 PYT_BUILD_PROXY。" >&2
  exit 1
fi

# 勿 export HTTP_PROXY：会让 Docker 客户端拉取基础镜像时也走代理（代理未开时会 connection refused）。
# 代理仅通过 docker-compose.build.yml 的 build.args 注入构建阶段。
unset HTTP_PROXY HTTPS_PROXY http_proxy https_proxy ALL_PROXY all_proxy

echo "==> 构建代理 (build-arg): $PROXY"
echo "==> Maven: 腾讯云镜像 + settings.build.xml 代理"
echo "==> npm: npmmirror | pip: 阿里云"

PYT_BUILD_PROXY="$PROXY" docker compose -f docker-compose.yml -f docker-compose.build.yml up -d --build

echo "==> 等待健康检查..."
sleep 15
docker compose ps
curl -sf http://127.0.0.1:18080/healthz && echo " backend ok"
curl -sf http://127.0.0.1:18091/healthz && echo " graph ok"
curl -sf http://127.0.0.1:18092/healthz && echo " rag ok"
curl -sf http://127.0.0.1:8080/healthz && echo " frontend ok"
echo "==> 完成。演示入口: http://localhost:8080"
