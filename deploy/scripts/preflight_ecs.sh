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

PYT_PUBLIC_IP="${PYT_PUBLIC_IP:-47.120.29.62}"
ENV_FILE="${PYT_ENV_FILE:-/opt/pythontopia/shared/.env}"
ENV_FILE_FALLBACK="/opt/pythontopia/current/deploy/.env"

# 生产编排必需的变量名。密钥值只做本地强度校验，绝不打印。
REQUIRED_VARS=(PYT_DB_USERNAME PYT_DB_PASSWORD PYT_JWT_SECRET PYT_TEACHER_INVITE_CODE PYT_RAG_ADMIN_TOKEN PYT_ROUTE_EXECUTION_SECRET)
# 建议配置的变量名（缺失仅警告：相关功能会降级而不会阻塞主链路）
RECOMMENDED_VARS=(PYT_LLM_PROVIDER PYT_OPENAI_BASE_URL PYT_OPENAI_API_KEY PYT_OPENAI_MODEL PYT_RAG_EMBEDDER PYT_RAG_EMBEDDING_FALLBACK EMBEDDING_BASE_URL EMBEDDING_MODEL EMBEDDING_DIM PYT_SPARK_APP_ID PYT_SPARK_API_KEY PYT_SPARK_API_SECRET)

PASS_COUNT=0
WARN_COUNT=0
FAIL_COUNT=0
SG_BLOCKED=0

pass() { PASS_COUNT=$((PASS_COUNT + 1)); echo "[通过] $*"; }
warn() { WARN_COUNT=$((WARN_COUNT + 1)); echo "[警告] $*"; }
fail() { FAIL_COUNT=$((FAIL_COUNT + 1)); echo "[失败] $*"; }
info() { echo "[信息] $*"; }

section() { echo; echo "===== $* ====="; }

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

  ROUTE_SECRET=$(sed -n 's/^[[:space:]]*PYT_ROUTE_EXECUTION_SECRET=//p' "${ENV_FILE}" 2>/dev/null | tail -n 1)
  case "${ROUTE_SECRET}" in
    ""|pytopia-rag-admin|pythontopia-route-secret|*change-me*|*placeholder*)
      fail "PYT_ROUTE_EXECUTION_SECRET 为空或使用公开占位值（必须独立随机生成，且不会在此输出）"
      ;;
    *)
      if [ "${#ROUTE_SECRET}" -ge 32 ]; then
        pass "PYT_ROUTE_EXECUTION_SECRET 已配置独立高熵长度（值不输出）"
      else
        fail "PYT_ROUTE_EXECUTION_SECRET 长度不足 32（值不输出）"
      fi
      ;;
  esac

  MISSING_RECOMMENDED=()
  for v in "${RECOMMENDED_VARS[@]}"; do
    if ! printf '%s\n' "${DEFINED_VARS}" | grep -qx "${v}"; then
      MISSING_RECOMMENDED+=("${v}")
    fi
  done
  if [ "${#MISSING_RECOMMENDED[@]}" -eq 0 ]; then
    pass "建议变量名齐全"
  else
    warn "缺少建议变量名：${MISSING_RECOMMENDED[*]}（LLM/RAG/多模态相关功能将按既定策略降级）"
  fi
else
  fail "未找到远端 .env：${ENV_FILE}（首次迁移请执行 install -D -m 600 <旧.env> /opt/pythontopia/shared/.env）"
fi

# ---------------------------------------------------------------------------
section "6. TCP 80 端口占用"
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
section "7. aliyun CLI 与安全组"
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
section "8. 本地 80 与公网 80 连通性比对"
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
