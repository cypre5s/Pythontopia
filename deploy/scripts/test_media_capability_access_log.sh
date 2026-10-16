#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FORMAT_FILE="${ROOT_DIR}/deploy/nginx/pythontopia-log-format.conf"
SITE_FILE="${ROOT_DIR}/deploy/nginx/pythontopia.conf"
FRONTEND_NGINX_FILE="${ROOT_DIR}/frontend/nginx.conf"
INSTALL_FILE="${ROOT_DIR}/deploy/scripts/install_release.sh"
ROLLBACK_FILE="${ROOT_DIR}/deploy/scripts/rollback_release.sh"
GRAPH_COMPOSE_FILE="${ROOT_DIR}/deploy/docker-compose.yml"
GRAPH_DOCKERFILE="${ROOT_DIR}/services/pythontopia-graph/Dockerfile"
RAG_DOCKERFILE="${ROOT_DIR}/services/pythontopia-rag/Dockerfile"

fail() {
  echo "[FAIL] $1" >&2
  exit 1
}

[ -f "${FORMAT_FILE}" ] || fail "缺少主机 Nginx 的 capability-safe log_format"
grep -Eq '^[[:space:]]*log_format[[:space:]]+pythontopia_safe[[:space:]]+' "${FORMAT_FILE}" \
  || fail "未定义 pythontopia_safe 格式"
LOG_FORMAT_LINE="$(grep -E '^[[:space:]]*log_format[[:space:]]+pythontopia_safe[[:space:]]+' "${FORMAT_FILE}")"
printf '%s\n' "${LOG_FORMAT_LINE}" | grep -Eq '\$uri' || fail "安全日志格式未包含不带 query 的 URI"
if printf '%s\n' "${LOG_FORMAT_LINE}" | grep -Eq '\$(request|request_uri|args|http_referer)([^[:alnum:]_]|$)'; then
  fail "安全日志格式仍可能记录签名 URL 的 query capability"
fi
grep -Eq 'access_log[[:space:]]+/var/log/nginx/pythontopia\.access\.log[[:space:]]+pythontopia_safe;' "${SITE_FILE}" \
  || fail "站点 access_log 未启用 pythontopia_safe"
grep -Fq 'NGINX_LOG_FORMAT_SRC_REL="deploy/nginx/pythontopia-log-format.conf"' "${INSTALL_FILE}" \
  || fail "release installer 未声明自定义 log_format 源文件"
grep -Fq 'NGINX_LOG_FORMAT_AVAIL="/etc/nginx/conf.d/pythontopia-log-format.conf"' "${INSTALL_FILE}" \
  || fail "release installer 未将 log_format 安装到 Nginx http 配置目录"
grep -Fq 'install -m 644 "${RELEASE_DIR}/${NGINX_LOG_FORMAT_SRC_REL}" "${NGINX_LOG_FORMAT_AVAIL}"' "${INSTALL_FILE}" \
  || fail "release installer 未安装 log_format 文件"
grep -Fq 'TARGET}/deploy/nginx/pythontopia-log-format.conf' "${ROLLBACK_FILE}" \
  || fail "release rollback 未恢复目标版本所需的 log_format"
grep -Fq -- '--no-access-log' "${GRAPH_COMPOSE_FILE}" \
  || fail "Graph 开发服务仍会记录包含 query 的 Uvicorn 请求行"
grep -Fq -- '--no-access-log' "${GRAPH_DOCKERFILE}" \
  || fail "Graph 镜像启动仍会记录包含 query 的 Uvicorn 请求行"
sed '/^[[:space:]]*location[[:space:]]/,$d' "${FRONTEND_NGINX_FILE}" \
  | grep -Eq '^[[:space:]]+access_log[[:space:]]+off;' \
  || fail "前端容器 Nginx 仍可能记录签名媒体 URL 的 query capability"
grep -Fq -- '--no-access-log' "${RAG_DOCKERFILE}" \
  || fail "RAG 镜像启动仍会记录包含 query 的 Uvicorn 请求行"
grep -A3 -F 'location = /api/media/asr {' "${SITE_FILE}" \
  | grep -Fq 'client_max_body_size 1k;' \
  || fail "主机 Nginx 未限制已关闭 ASR 入口的 multipart 请求体"
grep -A3 -F 'location = /api/media/asr {' "${SITE_FILE}" \
  | grep -Fq 'limit_req zone=pythontopia_upload burst=5 nodelay;' \
  || fail "主机 Nginx 未限制已关闭 ASR 入口的来源 IP 请求速率"
grep -A3 -F 'location = /api/media/asr {' "${FRONTEND_NGINX_FILE}" \
  | grep -Fq 'client_max_body_size 1k;' \
  || fail "前端 Nginx 未限制已关闭 ASR 入口的 multipart 请求体"

echo "[PASS] 主机/前端 Nginx 与 Graph/RAG HTTP access log 均不记录 query string；已关闭 ASR 上传受限"
