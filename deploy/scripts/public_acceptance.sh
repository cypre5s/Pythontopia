#!/usr/bin/env bash
# =============================================================================
# Pythontopia 公网部署验收脚本（纯 shell + curl + nc）
#
# 用法：
#   bash deploy/scripts/public_acceptance.sh <base_url> [本地包sha256]
#
# 示例：
#   bash deploy/scripts/public_acceptance.sh http://47.120.29.62
#   bash deploy/scripts/public_acceptance.sh http://47.120.29.62 \
#     "$(sha256sum dist.tar.gz | awk '{print $1}')"
#
# 可选环境变量：
#   PYT_E2E_USERNAME / PYT_E2E_PASSWORD  演示账号（SSE 检查带令牌走真实链路）
#   PYT_E2E_REGISTER=1                   先用 API 注册临时 QA 账号再登录
#   PYT_SSE_ROUNDS                       SSE 连续请求次数（默认 5）
#   PYT_SSE_MAX_TIME                     单次 SSE 最长等待秒数（默认 30）
#   PYT_REMOTE_SHA_URL                   部署端校验和文件地址（默认依次尝试
#                                        <base>/release.sha256、<base>/build-info.json）
#   PYT_QA_DIR                           证据日志目录（默认仓库根 qa-screens/）
#
# 输出：每项中文 [PASS]/[FAIL]/[SKIP]，结尾汇总；任一 FAIL 以非零码退出。
# 本脚本只读不写远端，不做任何 git 操作。
# =============================================================================

set -u

# ----------------------------------------------------------------------------
# 参数解析
# ----------------------------------------------------------------------------
BASE_URL="${1:-}"
LOCAL_SHA256="${2:-}"

if [ -z "${BASE_URL}" ]; then
  echo "用法：bash deploy/scripts/public_acceptance.sh <base_url> [本地包sha256]" >&2
  echo "示例：bash deploy/scripts/public_acceptance.sh http://47.120.29.62" >&2
  exit 2
fi

# 去掉结尾斜杠，方便拼接路径
BASE_URL="${BASE_URL%/}"

# 从 base_url 提取主机名（去协议、去端口、去路径）
AUTHORITY="$(printf '%s' "${BASE_URL}" | sed -E 's#^[a-zA-Z]+://##; s#/.*$##')"
HOST="${AUTHORITY%%:*}"

PASS_COUNT=0
FAIL_COUNT=0
SKIP_COUNT=0

# 证据日志：默认落仓库根 qa-screens/（已 gitignore）
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
QA_DIR="${PYT_QA_DIR:-${SCRIPT_DIR}/../../qa-screens}"
STAMP="$(date +%Y%m%d-%H%M%S)"
LOG_FILE="${QA_DIR}/public-acceptance-shell-${STAMP}.log"
if mkdir -p "${QA_DIR}" 2>/dev/null && touch "${LOG_FILE}" 2>/dev/null; then
  :
else
  LOG_FILE="/tmp/public-acceptance-shell-${STAMP}.log"
  touch "${LOG_FILE}" || LOG_FILE="/dev/null"
fi

log() {
  # 同时打到终端与证据日志
  printf '%s\n' "$*" | tee -a "${LOG_FILE}"
}

pass() { PASS_COUNT=$((PASS_COUNT + 1)); log "[PASS] $*"; }
fail() { FAIL_COUNT=$((FAIL_COUNT + 1)); log "[FAIL] $*"; }
skip() { SKIP_COUNT=$((SKIP_COUNT + 1)); log "[SKIP] $*"; }

# curl 包装：只取 HTTP 状态码，连接失败时输出 000
# 注意：curl 失败时 -w 也会自行打印 000，这里只兜底「完全无输出」的情况，
# 不能用 `|| printf '000'`（会重复拼成 000000）。
http_code() {
  # $1=url，其余参数透传给 curl
  local url="$1" code; shift
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 "$@" "${url}" 2>/dev/null)" || true
  printf '%s' "${code:-000}"
}

# Never send QA credentials to a remote HTTP endpoint. Probe only a harmless GET,
# then use the HTTPS destination for every acceptance request.
if [ "${HOST}" != "localhost" ] && [[ ! "${HOST}" =~ ^127\. ]]; then
  HTTP_PROBE="$(curl -sS -o /dev/null -w '%{http_code}\n%{redirect_url}' --max-time 20 \
    "http://${AUTHORITY}/healthz" 2>/dev/null || true)"
  HTTP_CODE="${HTTP_PROBE%%$'\n'*}"
  HTTP_LOCATION="${HTTP_PROBE#*$'\n'}"
  if [ "${HTTP_CODE}" != "308" ] || [[ "${HTTP_LOCATION}" != https://* ]]; then
    fail "HTTP 入口未安全返回 308 HTTPS 跳转；停止验收以避免明文发送账号凭据"
    log "结果：通过 ${PASS_COUNT} 项 / 失败 ${FAIL_COUNT} 项 / 跳过 ${SKIP_COUNT} 项"
    exit 1
  fi
  pass "公网 HTTP 入口返回 308 HTTPS 跳转"
  if [[ "${BASE_URL}" == http://* ]]; then
    BASE_URL="${HTTP_LOCATION%/healthz}"
    log "后续验收使用 HTTPS：${BASE_URL}"
  fi
else
  skip "本机开发地址不要求公网 HTTP→HTTPS 入口"
fi

log "==============================================================="
log " Pythontopia 公网验收：${BASE_URL}（主机 ${HOST}）"
log " 时间：$(date '+%F %T')  证据日志：${LOG_FILE}"
log "==============================================================="

# ----------------------------------------------------------------------------
# 1. 首页最终返回 200；公网 HTTP 入口已先单独验证 308 到 HTTPS
# ----------------------------------------------------------------------------
ROOT_CODE="$(http_code "${BASE_URL}/")"
if [ "${ROOT_CODE}" = "200" ]; then
  pass "01 首页 GET / 返回 200"
else
  fail "01 首页 GET / 返回 ${ROOT_CODE}（期望 200）"
fi

# ----------------------------------------------------------------------------
# 2. /healthz 返回 200
# ----------------------------------------------------------------------------
HEALTH_CODE="$(http_code "${BASE_URL}/healthz")"
if [ "${HEALTH_CODE}" = "200" ]; then
  pass "02 健康检查 /healthz 返回 200"
else
  fail "02 健康检查 /healthz 返回 ${HEALTH_CODE}（期望 200）"
fi

# ----------------------------------------------------------------------------
# 3. 静态资源缓存 / 类型头
#    - index.html：Content-Type 为 text/html，且不得是 immutable 长缓存
#    - 构建产物 /assets/*.js|*.css：Content-Type 正确，且带可缓存头
#      （Cache-Control max-age / Expires / ETag / Last-Modified 任一）
# ----------------------------------------------------------------------------
INDEX_HTML="$(curl -s --max-time 20 "${BASE_URL}/" 2>/dev/null || true)"
INDEX_HEADERS="$(curl -sI --max-time 20 "${BASE_URL}/" 2>/dev/null || true)"

if printf '%s' "${INDEX_HEADERS}" | grep -qi '^content-type:.*text/html'; then
  pass "03a index.html Content-Type 为 text/html"
else
  fail "03a index.html Content-Type 异常：$(printf '%s' "${INDEX_HEADERS}" | grep -i '^content-type' | head -1)"
fi

if printf '%s' "${INDEX_HEADERS}" | grep -i '^cache-control' | grep -qi 'immutable'; then
  fail "03b index.html 被设置为 immutable 长缓存（发版后用户拿不到新版本）"
else
  pass "03b index.html 未被设置为 immutable 长缓存"
fi

check_asset_headers() {
  # $1=资源路径 $2=期望 Content-Type 正则 $3=序号标签
  local asset_path="$1" ct_re="$2" tag="$3"
  local headers
  headers="$(curl -sI --max-time 20 "${BASE_URL}${asset_path}" 2>/dev/null || true)"
  local code
  code="$(printf '%s' "${headers}" | head -1 | grep -oE '[0-9]{3}' | head -1)"
  if [ "${code}" != "200" ]; then
    fail "${tag} 静态资源 ${asset_path} 返回 ${code:-无响应}（期望 200）"
    return
  fi
  if printf '%s' "${headers}" | grep -qiE "^content-type:.*(${ct_re})"; then
    pass "${tag} 静态资源 ${asset_path} Content-Type 正确"
  else
    fail "${tag} 静态资源 ${asset_path} Content-Type 异常：$(printf '%s' "${headers}" | grep -i '^content-type' | head -1)"
  fi
  if printf '%s' "${headers}" | grep -qiE '^(cache-control:.*max-age=[1-9]|expires:|etag:|last-modified:)'; then
    pass "${tag} 静态资源 ${asset_path} 带缓存/校验头（Cache-Control/Expires/ETag/Last-Modified）"
  else
    fail "${tag} 静态资源 ${asset_path} 缺少任何缓存/校验头"
  fi
}

JS_ASSET="$(printf '%s' "${INDEX_HTML}" | grep -oE '/assets/[A-Za-z0-9._-]+\.js' | head -1)"
CSS_ASSET="$(printf '%s' "${INDEX_HTML}" | grep -oE '/assets/[A-Za-z0-9._-]+\.css' | head -1)"

if [ -n "${JS_ASSET}" ]; then
  check_asset_headers "${JS_ASSET}" 'javascript' "03c"
else
  fail "03c 未能从 index.html 解析出 /assets/*.js 构建产物（可能不是生产构建）"
fi

if [ -n "${CSS_ASSET}" ]; then
  check_asset_headers "${CSS_ASSET}" 'text/css' "03d"
else
  skip "03d index.html 中未发现 /assets/*.css（样式可能内联，跳过）"
fi

# ----------------------------------------------------------------------------
# 4. 内部端口公网封闭性：8080 / 18080 / 18091 / 18092 / 15432 / 16379
#    注意：本机若有 TUN/fake-ip 代理，任意端口都会"假握手成功"，
#    先用一个理应关闭的哨兵端口探测环境是否可信。
# ----------------------------------------------------------------------------
INTERNAL_PORTS="8080 18080 18091 18092 15432 16379"

tcp_probe() {
  # $1=host $2=port；可达返回 0
  local h="$1" p="$2"
  if command -v nc >/dev/null 2>&1; then
    nc -z -w 3 "${h}" "${p}" >/dev/null 2>&1
  else
    # 没有 nc 时退化到 bash /dev/tcp
    timeout 3 bash -c "exec 3<>/dev/tcp/${h}/${p}" >/dev/null 2>&1
  fi
}

case "${HOST}" in
  127.* | localhost | ::1)
    for p in ${INTERNAL_PORTS}; do
      skip "04 本地环回地址（${HOST}）跳过端口 ${p} 公网封闭性检查（仅公网验收时有意义）"
    done
    ;;
  *)
    SENTINEL_PORT=59997
    if tcp_probe "${HOST}" "${SENTINEL_PORT}"; then
      for p in ${INTERNAL_PORTS}; do
        skip "04 端口 ${p}：哨兵端口 ${SENTINEL_PORT} 也能连通，疑似本机 TUN/fake-ip 代理劫持，结果不可信（请按 AGENTS.md 为目标 IP 加直连路由后重测）"
      done
    else
      for p in ${INTERNAL_PORTS}; do
        if tcp_probe "${HOST}" "${p}"; then
          fail "04 内部端口 ${p} 公网可达（必须仅 80 对外，请检查安全组/防火墙）"
        else
          pass "04 内部端口 ${p} 公网不可达（封闭符合预期）"
        fi
      done
    fi
    ;;
esac

# ----------------------------------------------------------------------------
# 5. 重定向不得指回 :8080（Location 头与最终落点都检查）
# ----------------------------------------------------------------------------
REDIRECT_PATHS="/ /login /guide /workshop /mainline-map /healthz /swagger-ui/index.html"
REDIRECT_BAD=0
for rp in ${REDIRECT_PATHS}; do
  headers="$(curl -sI --max-time 20 "${BASE_URL}${rp}" 2>/dev/null || true)"
  loc="$(printf '%s' "${headers}" | grep -i '^location:' | head -1 | tr -d '\r')"
  if printf '%s' "${loc}" | grep -q ':8080'; then
    fail "05 路径 ${rp} 的 Location 重定向指回 :8080（${loc}）"
    REDIRECT_BAD=1
    continue
  fi
  effective="$(curl -s -o /dev/null -w '%{url_effective}' -L --max-time 20 "${BASE_URL}${rp}" 2>/dev/null || true)"
  if printf '%s' "${effective}" | grep -q ':8080'; then
    fail "05 路径 ${rp} 跟随重定向后最终落到 :8080（${effective}）"
    REDIRECT_BAD=1
  fi
done
if [ "${REDIRECT_BAD}" -eq 0 ]; then
  pass "05 抽检路径（${REDIRECT_PATHS}）均无指回 :8080 的重定向"
fi

# ----------------------------------------------------------------------------
# 6. 连续多次 SSE 请求无 502/504
#    有凭证：登录拿令牌，期待 200；无凭证：匿名打到端点，401/403 也算
#    代理层健康（重点是不得出现 502/504/000）。
# ----------------------------------------------------------------------------
SSE_ROUNDS="${PYT_SSE_ROUNDS:-5}"
SSE_MAX_TIME="${PYT_SSE_MAX_TIME:-30}"
SSE_URL="${BASE_URL}/api/orchestrator/turn/stream"
TOKEN=""

extract_json_field() {
  # $1=json $2=字段名（仅适配本项目扁平字符串字段，避免引入 jq）
  printf '%s' "$1" | grep -o "\"$2\":\"[^\"]*\"" | head -1 | cut -d'"' -f4
}

if [ -n "${PYT_E2E_USERNAME:-}" ] && [ -n "${PYT_E2E_PASSWORD:-}" ]; then
  if [ "${PYT_E2E_REGISTER:-}" = "1" ]; then
    reg_code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 \
      -H 'Content-Type: application/json' \
      -d "{\"username\":\"${PYT_E2E_USERNAME}\",\"password\":\"${PYT_E2E_PASSWORD}\",\"displayName\":\"QA验收脚本\",\"role\":\"STUDENT\"}" \
      "${BASE_URL}/api/auth/register" 2>/dev/null)" || true
    reg_code="${reg_code:-000}"
    log "      （注册临时 QA 账号：HTTP ${reg_code}，409=已存在可忽略）"
  fi
  login_body="$(curl -s --max-time 20 -H 'Content-Type: application/json' \
    -d "{\"username\":\"${PYT_E2E_USERNAME}\",\"password\":\"${PYT_E2E_PASSWORD}\"}" \
    "${BASE_URL}/api/auth/login" 2>/dev/null || true)"
  TOKEN="$(extract_json_field "${login_body}" 'token')"
  if [ -n "${TOKEN}" ]; then
    log "      （已取得演示账号令牌，SSE 检查走认证链路）"
  else
    log "      （登录未取得令牌，SSE 检查退化为匿名探测；登录响应片段：$(printf '%s' "${login_body}" | head -c 120)）"
  fi
else
  log "      （未提供 PYT_E2E_USERNAME/PYT_E2E_PASSWORD，SSE 检查走匿名探测，401/403 视为代理层健康）"
fi

SSE_FAILED=0
SSE_CODES=""
i=1
while [ "${i}" -le "${SSE_ROUNDS}" ]; do
  if [ -n "${TOKEN}" ]; then
    code="$(curl -s -N -o /dev/null -w '%{http_code}' --max-time "${SSE_MAX_TIME}" \
      -H 'Content-Type: application/json' -H 'Accept: text/event-stream' \
      -H "Authorization: Bearer ${TOKEN}" \
      -d "{\"sessionId\":\"qa-shell-${STAMP}-${i}\",\"message\":\"验收连通性检查 ${i}\",\"history\":[]}" \
      "${SSE_URL}" 2>/dev/null)" || true
  else
    code="$(curl -s -N -o /dev/null -w '%{http_code}' --max-time "${SSE_MAX_TIME}" \
      -H 'Content-Type: application/json' -H 'Accept: text/event-stream' \
      -d "{\"sessionId\":\"qa-shell-${STAMP}-${i}\",\"message\":\"验收连通性检查 ${i}\",\"history\":[]}" \
      "${SSE_URL}" 2>/dev/null)" || true
  fi
  code="${code:-000}"
  SSE_CODES="${SSE_CODES} ${code}"
  case "${code}" in
    502 | 504 | 000) SSE_FAILED=1 ;;
  esac
  i=$((i + 1))
done

if [ "${SSE_FAILED}" -eq 0 ]; then
  if [ -n "${TOKEN}" ] && printf '%s' "${SSE_CODES}" | grep -qE '\b(4[0-9]{2}|5[0-9]{2})\b'; then
    fail "06 SSE 连续 ${SSE_ROUNDS} 次（认证链路）出现非 200 状态：${SSE_CODES}"
  else
    pass "06 SSE 连续 ${SSE_ROUNDS} 次请求无 502/504（状态序列：${SSE_CODES} ）"
  fi
else
  fail "06 SSE 连续请求出现 502/504/连接失败（状态序列：${SSE_CODES} ），代理或后端流式链路异常"
fi

# ----------------------------------------------------------------------------
# 7. 本地包 sha256 与部署端校验和比对（可选）
# ----------------------------------------------------------------------------
if [ -z "${LOCAL_SHA256}" ]; then
  skip "07 未传入本地包 sha256（第 2 个参数），跳过部署一致性校验"
else
  LOCAL_NORM="$(printf '%s' "${LOCAL_SHA256}" | tr 'A-F' 'a-f' | grep -oE '[0-9a-f]{64}' | head -1)"
  if [ -z "${LOCAL_NORM}" ]; then
    fail "07 传入的本地 sha256 非法（应为 64 位十六进制）：${LOCAL_SHA256}"
  else
    REMOTE_RAW=""
    REMOTE_SRC=""
    if [ -n "${PYT_REMOTE_SHA_URL:-}" ]; then
      REMOTE_RAW="$(curl -s --max-time 20 "${PYT_REMOTE_SHA_URL}" 2>/dev/null || true)"
      REMOTE_SRC="${PYT_REMOTE_SHA_URL}"
    else
      for cand in "/release.sha256" "/build-info.json"; do
        REMOTE_RAW="$(curl -s --max-time 20 "${BASE_URL}${cand}" 2>/dev/null || true)"
        if printf '%s' "${REMOTE_RAW}" | grep -qiE '[0-9a-f]{64}'; then
          REMOTE_SRC="${BASE_URL}${cand}"
          break
        fi
        REMOTE_RAW=""
      done
    fi
    REMOTE_NORM="$(printf '%s' "${REMOTE_RAW}" | tr 'A-F' 'a-f' | grep -oE '[0-9a-f]{64}' | head -1)"
    if [ -z "${REMOTE_NORM}" ]; then
      fail "07 未能从部署端获取校验和（尝试了 PYT_REMOTE_SHA_URL、/release.sha256、/build-info.json），无法核对部署一致性"
    elif [ "${REMOTE_NORM}" = "${LOCAL_NORM}" ]; then
      pass "07 部署端校验和与本地包一致（${REMOTE_SRC}：${REMOTE_NORM}）"
    else
      fail "07 部署端校验和与本地包不一致：本地 ${LOCAL_NORM} ≠ 部署端 ${REMOTE_NORM}（${REMOTE_SRC}）"
    fi
  fi
fi

# ----------------------------------------------------------------------------
# 汇总
# ----------------------------------------------------------------------------
log "---------------------------------------------------------------"
log " 验收汇总：通过 ${PASS_COUNT} 项；失败 ${FAIL_COUNT} 项；跳过 ${SKIP_COUNT} 项"
log " 证据日志：${LOG_FILE}"
if [ "${FAIL_COUNT}" -gt 0 ]; then
  log " 结论：存在失败项，公网验收不通过。"
  exit 1
fi
log " 结论：全部检查通过（跳过项见上方说明）。"
exit 0
