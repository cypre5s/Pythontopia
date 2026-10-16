#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
FRONTEND_NGINX="${ROOT_DIR}/frontend/nginx.conf"
BACKEND_CONFIG="${ROOT_DIR}/backend/src/main/resources/application.yml"
INSTALLER="${ROOT_DIR}/deploy/scripts/install_release.sh"
FAILURES=0

require_pattern() {
  local file="$1"
  local description="$2"
  local pattern="$3"
  if grep -Eq "${pattern}" "${file}"; then
    echo "[PASS] ${description}"
  else
    echo "[FAIL] ${description}" >&2
    FAILURES=$((FAILURES + 1))
  fi
}

require_download_hit_limit_block() {
  local edge_file="${ROOT_DIR}/deploy/nginx/pythontopia.conf"
  local hit_block
  hit_block="$(awk '
    /location[[:space:]]+~[[:space:]]+\^\/api\/downloads\// { capture=1 }
    capture { print }
    capture && /^[[:space:]]+}/ { exit }
  ' "${edge_file}")"
  if grep -Eq 'limit_req[[:space:]]+zone=pythontopia_download_hit[[:space:]]+burst=10[[:space:]]+nodelay;' <<<"${hit_block}" \
      && grep -Eq 'limit_req_status[[:space:]]+429;' <<<"${hit_block}"; then
    echo "[PASS] 匿名下载计数入口限速并返回 429"
  else
    echo "[FAIL] 匿名下载计数入口限速并返回 429" >&2
    FAILURES=$((FAILURES + 1))
  fi
}

require_upload_limit_block() {
  local edge_file="${ROOT_DIR}/deploy/nginx/pythontopia.conf"
  local upload_block
  upload_block="$(awk '
    /location[[:space:]]+=[[:space:]]+\/api\/workshop\/uploads/ { capture=1 }
    capture { print }
    capture && /^[[:space:]]*}/ { exit }
  ' "${edge_file}")"
  if grep -Eq 'client_max_body_size[[:space:]]+6m;' <<<"${upload_block}" \
      && grep -Eq 'limit_req[[:space:]]+zone=pythontopia_upload[[:space:]]+burst=5[[:space:]]+nodelay;' <<<"${upload_block}" \
      && grep -Eq 'limit_req_status[[:space:]]+429;' <<<"${upload_block}"; then
    echo "[PASS] 上传入口限制请求体、限速并返回 429"
  else
    echo "[FAIL] 上传入口限制请求体、限速并返回 429" >&2
    FAILURES=$((FAILURES + 1))
  fi
}

require_tutor_workflow_upload_limit_block() {
  local edge_file="${ROOT_DIR}/deploy/nginx/pythontopia.conf"
  local upload_block
  upload_block="$(awk '
    /location[[:space:]]+~[[:space:]]+\^\/api\/tutor\/workflow\/sessions\/\[\^\/\]\+\/attachments\$/ { capture=1 }
    capture { print }
    capture && /^[[:space:]]*}/ { exit }
  ' "${edge_file}")"
  if grep -Eq 'client_max_body_size[[:space:]]+6m;' <<<"${upload_block}" \
      && grep -Eq 'limit_req[[:space:]]+zone=pythontopia_upload[[:space:]]+burst=5[[:space:]]+nodelay;' <<<"${upload_block}" \
      && grep -Eq 'limit_req_status[[:space:]]+429;' <<<"${upload_block}"; then
    echo "[PASS] Tutor 图片上传限制请求体、限速并返回 429"
  else
    echo "[FAIL] Tutor 图片上传限制请求体、限速并返回 429" >&2
    FAILURES=$((FAILURES + 1))
  fi
}

require_save_slot_snapshot_limit_block() {
  local edge_file="${ROOT_DIR}/deploy/nginx/pythontopia.conf"
  local front_file="${ROOT_DIR}/frontend/nginx.conf"
  local snapshot_block

  snapshot_block="$(awk '
    /location[[:space:]]+~[[:space:]]+\^\/api\/world\/saves\/\[0-9\]\+\/snapshot\$/ { capture=1 }
    capture { print }
    capture && /^[[:space:]]*}/ { exit }
  ' "${edge_file}")"
  if grep -Eq 'client_max_body_size[[:space:]]+256k;' <<<"${snapshot_block}" \
      && grep -Eq 'proxy_pass[[:space:]]+http:\/\/pythontopia_frontend;' <<<"${snapshot_block}"; then
    echo "[PASS] 边缘 Nginx 将存档快照请求限制为 256 KiB 并正确代理"
  else
    echo "[FAIL] 边缘 Nginx 将存档快照请求限制为 256 KiB 并正确代理" >&2
    FAILURES=$((FAILURES + 1))
  fi

  snapshot_block="$(awk '
    /location[[:space:]]+~[[:space:]]+\^\/api\/world\/saves\/\[0-9\]\+\/snapshot\$/ { capture=1 }
    capture { print }
    capture && /^[[:space:]]*}/ { exit }
  ' "${front_file}")"
  if grep -Eq 'client_max_body_size[[:space:]]+256k;' <<<"${snapshot_block}" \
      && grep -Eq 'proxy_pass[[:space:]]+http:\/\/backend:18080;' <<<"${snapshot_block}"; then
    echo "[PASS] 前端 Nginx 将直连存档快照请求限制为 256 KiB 并正确代理"
  else
    echo "[FAIL] 前端 Nginx 将直连存档快照请求限制为 256 KiB 并正确代理" >&2
    FAILURES=$((FAILURES + 1))
  fi
}

require_loop_runtime_body_limit_block() {
  local file="$1"
  local description="$2"
  local expected_upstream="$3"
  local body_limit_block
  body_limit_block="$(awk '
    index($0, "location ~ ^/api/(?:agent-runs/") { capture=1 }
    capture { print }
    capture && /^[[:space:]]*}/ { exit }
  ' "${file}")"
  if grep -Eq 'client_max_body_size[[:space:]]+3m;' <<<"${body_limit_block}" \
      && grep -Fq "proxy_pass ${expected_upstream};" <<<"${body_limit_block}"; then
    echo "[PASS] ${description}"
  else
    echo "[FAIL] ${description}" >&2
    FAILURES=$((FAILURES + 1))
  fi
}

require_body_limit_location_block() {
  local file="$1"
  local location="$2"
  local limit="$3"
  local expected_upstream="$4"
  local description="$5"
  local body_limit_block
  body_limit_block="$(awk -v location="${location}" '
    index($0, location) { capture=1 }
    capture { print }
    capture && /^[[:space:]]*}/ { exit }
  ' "${file}")"
  if grep -Eq "client_max_body_size[[:space:]]+${limit};" <<<"${body_limit_block}" \
      && grep -Fq "proxy_pass ${expected_upstream};" <<<"${body_limit_block}"; then
    echo "[PASS] ${description}"
  else
    echo "[FAIL] ${description}" >&2
    FAILURES=$((FAILURES + 1))
  fi
}

require_evidence_snapshot_limit_block() {
  local edge_file="${ROOT_DIR}/deploy/nginx/pythontopia.conf"
  local api_block
  api_block="$(awk '
    /location[[:space:]]+\/api\// { capture=1 }
    capture { print }
    capture && /^[[:space:]]*}/ { exit }
  ' "${edge_file}")"
  if grep -Eq 'limit_req[[:space:]]+zone=pythontopia_evidence_snapshot[[:space:]]+burst=2[[:space:]]+nodelay;' <<<"${api_block}" \
      && grep -Eq 'limit_req_status[[:space:]]+429;' <<<"${api_block}"; then
    echo "[PASS] 证据快照入口按需限速并返回 429"
  else
    echo "[FAIL] 证据快照入口按需限速并返回 429" >&2
    FAILURES=$((FAILURES + 1))
  fi
}

require_registration_limit_block() {
  local edge_file="${ROOT_DIR}/deploy/nginx/pythontopia.conf"
  local register_block
  register_block="$(awk '
    /location[[:space:]]+=[[:space:]]+\/api\/auth\/register/ { capture=1 }
    capture { print }
    capture && /^[[:space:]]*}/ { exit }
  ' "${edge_file}")"
  if grep -Eq 'client_max_body_size[[:space:]]+4k;' <<<"${register_block}" \
      && grep -Eq 'limit_req[[:space:]]+zone=pythontopia_register[[:space:]]+burst=30[[:space:]]+nodelay;' <<<"${register_block}" \
      && grep -Eq 'limit_req_status[[:space:]]+429;' <<<"${register_block}"; then
    echo "[PASS] 学生注册限制 4 KiB 请求体、单独限速并返回 429"
  else
    echo "[FAIL] 学生注册限制 4 KiB 请求体、单独限速并返回 429" >&2
    FAILURES=$((FAILURES + 1))
  fi
}

require_pattern "${FRONTEND_NGINX}" \
  "前端 Nginx 从上游请求头解析外部协议" \
  'map[[:space:]]+\$http_x_forwarded_proto[[:space:]]+\$pythontopia_forwarded_proto[[:space:]]*\{'
require_pattern "${FRONTEND_NGINX}" \
  "API 代理保留外层 HTTPS 协议" \
  'proxy_set_header[[:space:]]+X-Forwarded-Proto[[:space:]]+\$pythontopia_forwarded_proto;'
require_pattern "${FRONTEND_NGINX}" \
  "前端 Nginx 请求体上限与 Spring 12 MB multipart 上限对齐" \
  'client_max_body_size[[:space:]]+12m;'
require_pattern "${BACKEND_CONFIG}" \
  "Spring 使用框架级转发头处理" \
  '^[[:space:]]+forward-headers-strategy:[[:space:]]+framework[[:space:]]*$'
require_pattern "${INSTALLER}" \
  "发布终验覆盖 HTTPS 公网入口" \
  'curl.*--resolve[[:space:]]+47\.120\.29\.62:443:127\.0\.0\.1.*https://47\.120\.29\.62/healthz'
require_pattern "${INSTALLER}" \
  "发布终验要求 HTTP 入口固定跳转 HTTPS" \
  'HTTP_REDIRECT=.*%\{http_code\}.*%\{redirect_url\}.*http://127\.0\.0\.1/healthz'
require_pattern "${ROOT_DIR}/deploy/nginx/pythontopia.conf" \
  "公开登录入口按来源 IP 限速" \
  'limit_req_zone[[:space:]]+\$binary_remote_addr[[:space:]]+zone=pythontopia_auth:10m[[:space:]]+rate=5r/s;'
require_pattern "${ROOT_DIR}/deploy/nginx/pythontopia.conf" \
  "公开注册入口按来源 IP 单独限为每分钟 5 次" \
  'limit_req_zone[[:space:]]+\$pythontopia_register_limit_key[[:space:]]+zone=pythontopia_register:10m[[:space:]]+rate=5r/m;'
require_pattern "${ROOT_DIR}/deploy/nginx/pythontopia.conf" \
  "注册限速只按 POST 注册请求计数" \
  '"POST:/api/auth/register"[[:space:]]+\$binary_remote_addr;'
require_pattern "${ROOT_DIR}/deploy/nginx/pythontopia.conf" \
  "公网入口请求体上限与前端代理 12 MiB 限额对齐" \
  '^[[:space:]]+client_max_body_size[[:space:]]+12m;'
require_pattern "${ROOT_DIR}/deploy/nginx/pythontopia.conf" \
  "登录和刷新匹配快速认证限速路由" \
  'location[[:space:]]+~[[:space:]]+\^/api/auth/\(login\|refresh\)\$[[:space:]]*\{'
require_pattern "${ROOT_DIR}/deploy/nginx/pythontopia.conf" \
  "登录和刷新实际使用来源 IP 限速" \
  'limit_req[[:space:]]+zone=pythontopia_auth[[:space:]]+burst=10[[:space:]]+nodelay;'
require_pattern "${ROOT_DIR}/deploy/nginx/pythontopia.conf" \
  "学生注册使用独立精确路由" \
  'location[[:space:]]+=[[:space:]]+/api/auth/register[[:space:]]*\{'
require_registration_limit_block
require_pattern "${ROOT_DIR}/deploy/nginx/pythontopia.conf" \
  "公开入口对班级加入码按来源 IP 限速" \
  'limit_req_zone[[:space:]]+\$binary_remote_addr[[:space:]]+zone=pythontopia_classroom_join:10m[[:space:]]+rate=5r/m;'
require_pattern "${ROOT_DIR}/deploy/nginx/pythontopia.conf" \
  "班级加入路由实际使用来源 IP 限速" \
  'limit_req[[:space:]]+zone=pythontopia_classroom_join[[:space:]]+burst=5[[:space:]]+nodelay;'
require_pattern "${ROOT_DIR}/deploy/nginx/pythontopia.conf" \
  "匿名下载计数入口配置来源 IP 限速区" \
  'limit_req_zone[[:space:]]+\$binary_remote_addr[[:space:]]+zone=pythontopia_download_hit:10m[[:space:]]+rate=5r/s;'
require_pattern "${ROOT_DIR}/deploy/nginx/pythontopia.conf" \
  "下载计数限流只匹配 hit 写入路径" \
  'location[[:space:]]+~[[:space:]]+\^/api/downloads/\[a-z0-9_\]\+/hit\$[[:space:]]*\{'
require_download_hit_limit_block
require_pattern "${ROOT_DIR}/deploy/nginx/pythontopia.conf" \
  "上传限速仅以 POST 上传请求的来源 IP 作为 key" \
  'map[[:space:]]+"\$request_method:\$uri"[[:space:]]+\$pythontopia_upload_limit_key[[:space:]]*\{'
require_pattern "${ROOT_DIR}/deploy/nginx/pythontopia.conf" \
  "上传限速 key 对 GET 等其他方法为空" \
  '"POST:/api/workshop/uploads"[[:space:]]+\$binary_remote_addr;'
require_pattern "${ROOT_DIR}/deploy/nginx/pythontopia.conf" \
  "上传限速区配置为每秒 2 次" \
  'limit_req_zone[[:space:]]+\$pythontopia_upload_limit_key[[:space:]]+zone=pythontopia_upload:10m[[:space:]]+rate=2r/s;'
require_pattern "${ROOT_DIR}/deploy/nginx/pythontopia.conf" \
  "上传限速作用于精确上传路由" \
  'location[[:space:]]+=[[:space:]]+/api/workshop/uploads[[:space:]]*\{'
require_upload_limit_block
require_pattern "${ROOT_DIR}/deploy/nginx/pythontopia.conf" \
  "Tutor 图片上传限速只匹配附件 POST" \
  '~\^POST:/api/tutor/workflow/sessions/\[\^/\]\+/attachments\$[[:space:]]+\$binary_remote_addr;'
require_tutor_workflow_upload_limit_block
require_save_slot_snapshot_limit_block
require_loop_runtime_body_limit_block \
  "${ROOT_DIR}/deploy/nginx/pythontopia.conf" \
  "边缘 Nginx 将 Loop Runtime JSON 写请求限制为 3 MiB 并正确代理" \
  "http://pythontopia_frontend"
require_loop_runtime_body_limit_block \
  "${FRONTEND_NGINX}" \
  "前端 Nginx 将 Loop Runtime JSON 写请求限制为 3 MiB 并正确代理" \
  "http://backend:18080"
require_body_limit_location_block \
  "${ROOT_DIR}/deploy/nginx/pythontopia.conf" \
  "location = /api/learning/events" \
  "16k" \
  "http://pythontopia_frontend" \
  "边缘 Nginx 将单条学习事件限制为 16 KiB 并正确代理"
require_body_limit_location_block \
  "${ROOT_DIR}/deploy/nginx/pythontopia.conf" \
  "location = /api/learning/events/batch" \
  "512k" \
  "http://pythontopia_frontend" \
  "边缘 Nginx 将学习事件批次限制为 512 KiB 并正确代理"
require_body_limit_location_block \
  "${FRONTEND_NGINX}" \
  "location = /api/learning/events" \
  "16k" \
  "http://backend:18080" \
  "前端 Nginx 将单条学习事件限制为 16 KiB 并正确代理"
require_body_limit_location_block \
  "${FRONTEND_NGINX}" \
  "location = /api/learning/events/batch" \
  "512k" \
  "http://backend:18080" \
  "前端 Nginx 将学习事件批次限制为 512 KiB 并正确代理"
require_body_limit_location_block \
  "${ROOT_DIR}/deploy/nginx/pythontopia.conf" \
  'location ~ ^/api/auth/(login|refresh)$' \
  "4k" \
  "http://pythontopia_frontend" \
  "边缘 Nginx 将登录/刷新请求体限制为 4 KiB 并正确代理"
require_body_limit_location_block \
  "${FRONTEND_NGINX}" \
  'location ~ ^/api/auth/(?:login|refresh|register)$' \
  "4k" \
  "http://backend:18080" \
  "前端 Nginx 将匿名认证请求体限制为 4 KiB 并正确代理"
require_pattern "${ROOT_DIR}/deploy/nginx/pythontopia.conf" \
  "证据快照限速只接收 POST 快照请求的来源 IP" \
  'map[[:space:]]+"\$request_method:\$uri"[[:space:]]+\$pythontopia_evidence_snapshot_limit_key[[:space:]]*\{'
require_pattern "${ROOT_DIR}/deploy/nginx/pythontopia.conf" \
  "证据快照限速对其它 API 请求使用空 key" \
  '"POST:/api/learner/evidence-memory/snapshots"[[:space:]]+\$binary_remote_addr;'
require_pattern "${ROOT_DIR}/deploy/nginx/pythontopia.conf" \
  "证据快照限速区每秒允许一次" \
  'limit_req_zone[[:space:]]+\$pythontopia_evidence_snapshot_limit_key[[:space:]]+zone=pythontopia_evidence_snapshot:10m[[:space:]]+rate=1r/s;'
require_evidence_snapshot_limit_block

if [ "${FAILURES}" -ne 0 ]; then
  echo "[RESULT] ${FAILURES} 项 HTTPS 转发链路契约未满足" >&2
  exit 1
fi

echo "[RESULT] HTTPS 转发链路契约通过"
