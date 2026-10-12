#!/usr/bin/env bash
# =============================================================================
# Pythontopia ECS 部署预检（只读，不改动任何系统状态）
# =============================================================================
# 用法：
#   bash preflight_ecs.sh
#
# 可用环境变量覆盖：
#   PYT_ENV_FILE    远端 .env 路径（默认 /opt/pythontopia/shared/.env，
#                   缺省时回退探测 /opt/pythontopia/current/deploy/.env）
#   PYT_PUBLIC_IP   公网 IP（默认 47.120.29.62）
#
# 退出码：0 = 全部通过（允许 WARN）；1 = 存在 FAIL 项。
# 特殊输出：主机本地 80 可达而公网 80 不可达时，单独输出一行
#           SECURITY_GROUP_PORT_80_BLOCKED 并以非零退出。
# =============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYT_PUBLIC_IP="${PYT_PUBLIC_IP:-47.120.29.62}"
ENV_FILE="${PYT_ENV_FILE:-/opt/pythontopia/shared/.env}"
ENV_FILE_FALLBACK="/opt/pythontopia/current/deploy/.env"

# 生产编排必需的变量名。密钥值只做本地强度校验，绝不打印。
REQUIRED_VARS=(
  PYT_DB_USERNAME PYT_DB_PASSWORD PYT_JWT_SECRET PYT_TEACHER_INVITE_CODE
  PYT_RAG_ADMIN_TOKEN PYT_ROUTE_EXECUTION_SECRET
  PYT_JUDGE_PROVIDER PYT_PISTON_URL PYT_PISTON_PYTHON_VERSION PYT_PISTON_AUTH_TOKEN
  PYT_LLM_PROVIDER PYT_OPENAI_BASE_URL PYT_OPENAI_API_KEY PYT_OPENAI_MODEL
  PYT_OPENAI_MODEL_VISION PYT_RAG_EMBEDDER
)

PASS_COUNT=0
WARN_COUNT=0
FAIL_COUNT=0
SG_BLOCKED=0

pass() { PASS_COUNT=$((PASS_COUNT + 1)); echo "[通过] $*"; }
warn() { WARN_COUNT=$((WARN_COUNT + 1)); echo "[警告] $*"; }
fail() { FAIL_COUNT=$((FAIL_COUNT + 1)); echo "[失败] $*"; }
info() { echo "[信息] $*"; }

section() { echo; echo "===== $* ====="; }

# 只读取指定变量，不执行 .env 内容、不输出变量值；兼容一层单/双引号。
env_file_value() {
  local env_file="$1"
  local var_name="$2"
  local value
  value=$(sed -n "s/^[[:space:]]*${var_name}=//p" "${env_file}" 2>/dev/null | tail -n 1)
  value="${value%$'\r'}"
  case "${value}" in
    \"*\") value="${value#\"}"; value="${value%\"}" ;;
    \'*\') value="${value#\'}"; value="${value%\'}" ;;
  esac
  printf '%s' "${value}"
}

is_public_placeholder() {
  local value="${1,,}"
  case "${value}" in
    ""|*replace-with*|*replace_with*|*change-me*|*changeme*|*placeholder*\
      |*your-secret*|*your-key*|*your-token*|*example-token*) return 0 ;;
    *) return 1 ;;
  esac
}

require_env_value() {
  local env_file="$1"
  local var_name="$2"
  local value
  value="$(env_file_value "${env_file}" "${var_name}")"
  if is_public_placeholder "${value}"; then
    fail "${var_name} 为空或仍是公开占位值（值不输出）"
    return 1
  fi
  pass "${var_name} 已配置（值不输出）"
}

is_private_piston_http_url() {
  local url="$1"
  local authority host
  [[ "${url}" == http://* ]] || return 1
  authority="${url#http://}"
  authority="${authority%%/*}"
  if [[ "${authority}" == \[*\]* ]]; then
    host="${authority%%]*}]"
  else
    host="${authority%%:*}"
  fi
  case "${host}" in
    localhost|127.*|10.*|192.168.*|172.1[6-9].*|172.2[0-9].*|172.3[01].*\
      |\[::1\]|piston|piston-*|*.internal|*.local) return 0 ;;
  esac
  [[ "${host}" != *.* ]]
}

validate_production_ai_env() {
  local env_file="$1"
  local provider base_url rag_embedder
  provider="$(env_file_value "${env_file}" PYT_LLM_PROVIDER)"
  provider="${provider,,}"
  case "${provider}" in
    openai_compat|openai-compatible|openaicompat)
      pass "生产 LLM provider 为 DeepSeek"
      base_url="$(env_file_value "${env_file}" PYT_OPENAI_BASE_URL)"
      case "${base_url%/}" in
        https://api.deepseek.com|https://api.deepseek.com/v1)
          pass "生产模型端点指向 DeepSeek"
          ;;
        *) fail "PYT_OPENAI_BASE_URL 必须指向 https://api.deepseek.com" ;;
      esac
      require_env_value "${env_file}" PYT_OPENAI_BASE_URL || true
      require_env_value "${env_file}" PYT_OPENAI_API_KEY || true
      require_env_value "${env_file}" PYT_OPENAI_MODEL || true
      require_env_value "${env_file}" PYT_OPENAI_MODEL_VISION || true
      ;;
    ""|dryrun)
      fail "PYT_LLM_PROVIDER 未配置真实 provider；生产只使用 DeepSeek"
      ;;
    *)
      fail "PYT_LLM_PROVIDER 不受生产门禁支持（只允许 openai_compat）"
      ;;
  esac

  rag_embedder="$(env_file_value "${env_file}" PYT_RAG_EMBEDDER)"
  if [ "${rag_embedder,,}" = "hash" ]; then
    pass "生产 RAG 使用本地 hash + BM25 检索"
  else
    fail "PYT_RAG_EMBEDDER 必须为 hash；不允许调用外部 embedding API"
  fi
}

validate_production_judge_env() {
  local env_file="$1"
  local provider piston_url
  provider="$(env_file_value "${env_file}" PYT_JUDGE_PROVIDER)"
  if [ "${provider,,}" = "piston" ]; then
    pass "生产判题 provider 为 piston"
  else
    fail "PYT_JUDGE_PROVIDER 必须为 piston；生产禁止 subprocess"
  fi
  require_env_value "${env_file}" PYT_PISTON_URL || true
  require_env_value "${env_file}" PYT_PISTON_PYTHON_VERSION || true
  check_secret_strength PYT_PISTON_AUTH_TOKEN 32 fail "${env_file}"

  piston_url="$(env_file_value "${env_file}" PYT_PISTON_URL)"
  case "${piston_url}" in
    https://*) pass "外部 Piston 使用 HTTPS" ;;
    http://*)
      if is_private_piston_http_url "${piston_url}"; then
        warn "Piston 使用隔离内网 HTTP；请确保网络边界不可被公网访问"
      else
        fail "外部 Piston 地址必须使用 HTTPS"
      fi
      ;;
    "") ;;
    *) fail "PYT_PISTON_URL 必须是 http:// 或 https:// URL" ;;
  esac
}

# 秘密强度校验：只读取长度与占位特征，任何分支都不打印变量值本身。
# 用法：check_secret_strength <变量名> <最小长度> <fail|warn> [env 文件]
check_secret_strength() {
  local var_name="$1"
  local min_len="$2"
  local short_severity="$3"
  local env_file="${4:-${ENV_FILE}}"
  local value normalized
  value="$(env_file_value "${env_file}" "${var_name}")"
  normalized="${value,,}"
  case "${normalized}" in
    ""|pytopia-teacher|pytopia-rag-admin|pythontopia-route-secret\
      |*replace-with*|*replace_with*|*change-me*|*changeme*|*placeholder*\
      |*your-secret*|*your-key*|*your-token*|*example-token*)
      fail "${var_name} 为空或使用公开占位值（必须独立随机生成，且不会在此输出）"
      return
      ;;
  esac
  if [ "${#value}" -ge "${min_len}" ]; then
    pass "${var_name} 已配置独立高熵长度（值不输出）"
  elif [ "${short_severity}" = "warn" ]; then
    warn "${var_name} 长度不足 ${min_len}，建议加长（值不输出）"
  else
    fail "${var_name} 长度不足 ${min_len}（值不输出）"
  fi
  unset normalized value
}

echo "Pythontopia ECS 部署预检（只读） $(date '+%Y-%m-%d %H:%M:%S')"
echo "目标公网 IP：${PYT_PUBLIC_IP}"

# ---------------------------------------------------------------------------
section "1. 操作系统与权限"
# ---------------------------------------------------------------------------
if [ -r /etc/os-release ]; then
  # 子 shell 中加载，避免污染当前环境
  OS_DESC=$(. /etc/os-release && echo "${ID:-?} ${VERSION_ID:-?}")
  if [ "${OS_DESC}" = "ubuntu 22.04" ]; then
    pass "操作系统为 Ubuntu 22.04"
  else
    warn "操作系统为 ${OS_DESC}，部署脚本按 Ubuntu 22.04 设计，其他版本未验证"
  fi
else
  warn "无法读取 /etc/os-release，跳过系统版本检查"
fi

if [ "$(id -u)" = "0" ]; then
  pass "当前以 root 运行"
else
  warn "当前非 root（uid=$(id -u)）：部分检查受限，install_release.sh 必须以 root 执行"
fi

# ---------------------------------------------------------------------------
section "2. 磁盘与内存（2C4G 小机型基线）"
# ---------------------------------------------------------------------------
DISK_AVAIL_GB=$(df -BG --output=avail / 2>/dev/null | tail -n 1 | tr -dc '0-9' || echo "")
if [ -n "${DISK_AVAIL_GB}" ]; then
  if [ "${DISK_AVAIL_GB}" -ge 8 ]; then
    pass "根分区可用空间 ${DISK_AVAIL_GB}G（≥8G）"
  elif [ "${DISK_AVAIL_GB}" -ge 5 ]; then
    warn "根分区可用空间仅 ${DISK_AVAIL_GB}G：四个镜像 tar + docker load + 备份会吃紧，建议清理旧镜像/日志"
  else
    fail "根分区可用空间仅 ${DISK_AVAIL_GB}G（<5G），不足以解包发布包并导入镜像"
  fi
else
  warn "无法读取根分区可用空间"
fi

MEM_TOTAL_MB=$(free -m 2>/dev/null | awk '/^Mem:/{print $2}' || echo "")
if [ -n "${MEM_TOTAL_MB}" ]; then
  if [ "${MEM_TOTAL_MB}" -ge 3500 ]; then
    pass "物理内存 ${MEM_TOTAL_MB}MB（≥3.5G）"
  else
    warn "物理内存 ${MEM_TOTAL_MB}MB 低于 4G 基线，六容器 + 主机 Nginx 可能触发 OOM"
  fi
else
  warn "无法读取内存信息"
fi

# ---------------------------------------------------------------------------
section "3. Docker 与 Compose"
# ---------------------------------------------------------------------------
if command -v docker >/dev/null 2>&1; then
  pass "docker 已安装：$(docker --version 2>/dev/null | head -n 1)"
  if docker info >/dev/null 2>&1; then
    pass "docker daemon 可访问"
  else
    fail "docker daemon 不可访问（未启动或当前用户无权限）"
  fi
else
  fail "未找到 docker，请先安装 Docker Engine"
fi

if docker compose version >/dev/null 2>&1; then
  pass "docker compose（v2 插件）可用：$(docker compose version --short 2>/dev/null)"
else
  fail "docker compose v2 不可用（生产编排依赖 docker compose 子命令，不支持旧版 docker-compose v1）"
fi

# ---------------------------------------------------------------------------
section "4. 现有 Compose 项目 / 数据卷 / 基础镜像"
# ---------------------------------------------------------------------------
if docker info >/dev/null 2>&1; then
  COMPOSE_PROJECTS=$(docker compose ls -a 2>/dev/null | tail -n +2 || true)
  if [ -n "${COMPOSE_PROJECTS}" ]; then
    info "现有 Compose 项目："
    echo "${COMPOSE_PROJECTS}" | sed 's/^/        /'
  else
    info "未发现任何 Compose 项目（可能是全新机器）"
  fi

  if docker volume inspect deploy_pytopia_pg >/dev/null 2>&1; then
    pass "数据卷 deploy_pytopia_pg 存在，将以 -p deploy 复用（不会清空）"
  else
    warn "数据卷 deploy_pytopia_pg 不存在：首次 up 将新建空卷并由 Flyway 初始化全部表结构"
  fi

  for img in pgvector/pgvector:pg16 redis:7-alpine; do
    if docker image inspect "${img}" >/dev/null 2>&1; then
      pass "基础镜像 ${img} 已在本机"
    else
      warn "基础镜像 ${img} 不在本机：安装时需要联网拉取（发布包只携带四个应用镜像）"
    fi
  done

  # 旧拓扑暴露面检查（新编排将消除这两处公网暴露）
  if ss -ltn 2>/dev/null | awk '{print $4}' | grep -qE '(^|:)0\.0\.0\.0:8080$|\*:8080$'; then
    info "检测到 0.0.0.0:8080 正在监听（旧版前端公网直挂），切换新编排后将收敛为 127.0.0.1:8080"
  fi
  if ss -ltn 2>/dev/null | awk '{print $4}' | grep -qE '(^|:)0\.0\.0\.0:18091$|\*:18091$'; then
    info "检测到 0.0.0.0:18091 正在监听（旧版 graph host 网络），切换新编排后 graph 仅 bridge 网络内可达"
  fi
fi

# ---------------------------------------------------------------------------
section "5. 远端 .env 存在性与必需变量名"
# ---------------------------------------------------------------------------
if [ ! -f "${ENV_FILE}" ] && [ -f "${ENV_FILE_FALLBACK}" ]; then
  warn ".env 不在权威路径 ${ENV_FILE}，回退使用 ${ENV_FILE_FALLBACK}（建议迁移到权威路径，见 README）"
  ENV_FILE="${ENV_FILE_FALLBACK}"
fi

if [ -f "${ENV_FILE}" ]; then
  pass "找到 .env：${ENV_FILE}"

  PERM=$(stat -c '%a' "${ENV_FILE}" 2>/dev/null || echo "")
  case "${PERM}" in
    600|400) pass ".env 权限 ${PERM}（仅属主可读）" ;;
    "") warn "无法读取 .env 权限" ;;
    *) warn ".env 权限为 ${PERM}，建议 chmod 600 收紧（文件包含全部生产秘密）" ;;
  esac

  # 只提取「变量名」做集合比对；任何时候都不读取、不打印变量值。
  # 注意：只去除每行行首空白，不能用 tr -d 整体删空白（会把换行也删掉）。
  DEFINED_VARS=$(grep -oE '^[[:space:]]*[A-Za-z_][A-Za-z0-9_]*' "${ENV_FILE}" 2>/dev/null | sed 's/^[[:space:]]*//' | sort -u || true)

  MISSING_REQUIRED=()
  for v in "${REQUIRED_VARS[@]}"; do
    if ! printf '%s\n' "${DEFINED_VARS}" | grep -qx "${v}"; then
      MISSING_REQUIRED+=("${v}")
    fi
  done
  if [ "${#MISSING_REQUIRED[@]}" -eq 0 ]; then
    pass "必需变量名齐全：${REQUIRED_VARS[*]}"
  else
    fail "缺少必需变量名：${MISSING_REQUIRED[*]}（compose 渲染会因 \${VAR:?} 直接失败）"
  fi

  # 秘密类变量逐个做「占位值 + 长度」校验。只查变量名是否存在是不够的：.env.example 里的
  # 占位值是公开的，原样进生产等于把 JWT 签名密钥 / 教师邀请码 / 内部令牌直接公开。
  check_secret_strength PYT_JWT_SECRET 32 fail
  check_secret_strength PYT_ROUTE_EXECUTION_SECRET 32 fail
  check_secret_strength PYT_RAG_ADMIN_TOKEN 32 fail
  # 邀请码是人工分发的短串，长度只给建议值；但公开占位值同样一票否决。
  check_secret_strength PYT_TEACHER_INVITE_CODE 16 warn
  validate_production_ai_env "${ENV_FILE}"
  validate_production_judge_env "${ENV_FILE}"
else
  fail "未找到远端 .env：${ENV_FILE}（首次迁移请执行 install -D -m 600 <旧.env> /opt/pythontopia/shared/.env）"
fi

# ---------------------------------------------------------------------------
section "6. 课程运行时数据"
# ---------------------------------------------------------------------------
COURSE_DATA_ROOT="${PYT_SHARED_DATA_DIR:-/opt/pythontopia/shared/data}/course-pack-43"
if [ -f "${ENV_FILE}" ]; then
  CONFIGURED_SHARED_DATA="$(env_file_value "${ENV_FILE}" PYT_SHARED_DATA_DIR)"
  if [ -n "${CONFIGURED_SHARED_DATA}" ]; then
    COURSE_DATA_ROOT="${CONFIGURED_SHARED_DATA}/course-pack-43"
  fi
fi
if ! command -v python3 >/dev/null 2>&1; then
  fail "缺少 python3，无法校验课件/视频/字幕运行时数据"
elif [ ! -f "${SCRIPT_DIR}/validate_course_runtime_data.py" ]; then
  fail "发布包缺少课程数据校验器 validate_course_runtime_data.py"
else
  COURSE_DATA_RESULT="$(python3 "${SCRIPT_DIR}/validate_course_runtime_data.py" "${COURSE_DATA_ROOT}" 2>&1)"
  if [ "$?" -eq 0 ]; then
    pass "${COURSE_DATA_RESULT}"
  else
    fail "${COURSE_DATA_RESULT}"
  fi
fi

# ---------------------------------------------------------------------------
section "7. TCP 80 端口占用"
# ---------------------------------------------------------------------------
PORT80_LINES=$(ss -ltnp 2>/dev/null | awk '$4 ~ /(^|:)(\*|0\.0\.0\.0|\[::\]|127\.0\.0\.1):80$/' || true)
if [ -z "${PORT80_LINES}" ]; then
  pass "TCP 80 未被占用（主机 Nginx 可直接监听）"
else
  if echo "${PORT80_LINES}" | grep -q 'nginx'; then
    pass "TCP 80 已由 nginx 占用（再次部署属预期，install 脚本会 reload 而非重启）"
  elif echo "${PORT80_LINES}" | grep -q 'users:'; then
    fail "TCP 80 已被非 nginx 进程占用，请先处置：$(echo "${PORT80_LINES}" | head -n 2 | tr '\n' ' ')"
  else
    warn "TCP 80 已被占用但无法识别进程（需要 root 才能看到进程名），请人工确认：$(echo "${PORT80_LINES}" | head -n 1)"
  fi
fi

# ---------------------------------------------------------------------------
section "8. aliyun CLI 与安全组"
# ---------------------------------------------------------------------------
ALIYUN_OK=0
if command -v aliyun >/dev/null 2>&1; then
  pass "aliyun CLI 已安装：$(aliyun version 2>/dev/null | head -n 1)"
  REGION_ID=$(curl -fsS -m 3 http://100.100.100.200/latest/meta-data/region-id 2>/dev/null || true)
  INSTANCE_ID=$(curl -fsS -m 3 http://100.100.100.200/latest/meta-data/instance-id 2>/dev/null || true)
  if [ -n "${REGION_ID}" ] && [ -n "${INSTANCE_ID}" ]; then
    info "实例元数据：region=${REGION_ID} instance=${INSTANCE_ID}"
    SG_IDS=$(aliyun ecs DescribeInstanceAttribute --InstanceId "${INSTANCE_ID}" --RegionId "${REGION_ID}" 2>/dev/null \
      | python3 -c 'import json,sys
try:
    data = json.load(sys.stdin)
    print("\n".join(data.get("SecurityGroupIds", {}).get("SecurityGroupId", [])))
except Exception:
    pass' 2>/dev/null || true)
    if [ -n "${SG_IDS}" ]; then
      ALIYUN_OK=1
      pass "aliyun CLI 凭证可用，安全组：$(echo "${SG_IDS}" | tr '\n' ' ')"
      PORT80_ALLOWED=0
      for sg in ${SG_IDS}; do
        RESULT=$(aliyun ecs DescribeSecurityGroupAttribute --SecurityGroupId "${sg}" --RegionId "${REGION_ID}" --Direction ingress 2>/dev/null \
          | python3 -c 'import json,sys
try:
    data = json.load(sys.stdin)
    perms = data.get("Permissions", {}).get("Permission", [])
    for p in perms:
        if p.get("IpProtocol", "").upper() not in ("TCP", "ALL"):
            continue
        if p.get("Policy", "Accept") != "Accept":
            continue
        pr = p.get("PortRange", "")
        ok = pr in ("80/80", "1/65535", "-1/-1")
        if "/" in pr and not ok:
            try:
                lo, hi = pr.split("/")
                ok = int(lo) <= 80 <= int(hi)
            except ValueError:
                ok = False
        if ok:
            print("ALLOWED", p.get("SourceCidrIp", ""), pr)
except Exception:
    pass' 2>/dev/null || true)
        if echo "${RESULT}" | grep -q '^ALLOWED'; then
          PORT80_ALLOWED=1
          info "安全组 ${sg} 入方向已放行 80：$(echo "${RESULT}" | head -n 3 | tr '\n' '；')"
        fi
      done
      if [ "${PORT80_ALLOWED}" = "1" ]; then
        pass "至少一条安全组入方向规则覆盖 TCP 80"
      else
        warn "未在安全组入方向规则中找到覆盖 TCP 80 的放行（可能查询不全，以下方公网连通性实测为准）"
      fi
    else
      warn "aliyun CLI 已安装但查询实例属性失败（凭证/权限/网络问题），跳过安全组核对"
    fi
  else
    warn "无法访问 ECS 元数据服务 100.100.100.200（非阿里云环境或元数据被禁），跳过安全组核对"
  fi
else
  warn "未安装 aliyun CLI，跳过安全组查询（仅依赖下方公网连通性实测）"
fi

# ---------------------------------------------------------------------------
section "9. 本地 80 与公网 80 连通性比对"
# ---------------------------------------------------------------------------
LOCAL_80_OK=0
PUBLIC_80_OK=0
if curl -fsS -m 5 -o /dev/null http://127.0.0.1/healthz 2>/dev/null; then
  LOCAL_80_OK=1
  pass "本机 http://127.0.0.1/healthz 可达"
else
  info "本机 80 上的 /healthz 不可达（尚未部署主机 Nginx 时属预期，跳过公网阻断判定）"
fi

if [ "${LOCAL_80_OK}" = "1" ]; then
  if curl -fsS -m 8 -o /dev/null "http://${PYT_PUBLIC_IP}/healthz" 2>/dev/null; then
    PUBLIC_80_OK=1
    pass "公网 http://${PYT_PUBLIC_IP}/healthz 可达"
  else
    SG_BLOCKED=1
    echo "SECURITY_GROUP_PORT_80_BLOCKED"
    fail "主机本地 80 可达但公网 ${PYT_PUBLIC_IP}:80 不可达：大概率是安全组未放行 80（或公网 IP 回流受限），请在阿里云控制台核对入方向规则"
  fi
fi

# ---------------------------------------------------------------------------
section "预检结果汇总"
# ---------------------------------------------------------------------------
echo "通过 ${PASS_COUNT} 项 / 警告 ${WARN_COUNT} 项 / 失败 ${FAIL_COUNT} 项"
if [ "${FAIL_COUNT}" -gt 0 ] || [ "${SG_BLOCKED}" = "1" ]; then
  echo "结论：预检未通过，请先处理上述 [失败] 项再执行 install_release.sh。"
  exit 1
fi
if [ "${WARN_COUNT}" -gt 0 ]; then
  echo "结论：预检通过（含警告），请逐条确认 [警告] 项后再继续。"
else
  echo "结论：预检全部通过。"
fi
exit 0
