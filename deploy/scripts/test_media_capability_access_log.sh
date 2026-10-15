#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FORMAT_FILE="${ROOT_DIR}/deploy/nginx/pythontopia-log-format.conf"
SITE_FILE="${ROOT_DIR}/deploy/nginx/pythontopia.conf"
INSTALL_FILE="${ROOT_DIR}/deploy/scripts/install_release.sh"
ROLLBACK_FILE="${ROOT_DIR}/deploy/scripts/rollback_release.sh"
GRAPH_COMPOSE_FILE="${ROOT_DIR}/deploy/docker-compose.yml"
GRAPH_DOCKERFILE="${ROOT_DIR}/services/pythontopia-graph/Dockerfile"

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

echo "[PASS] Nginx 与 Graph HTTP access log 均不记录 query string"
