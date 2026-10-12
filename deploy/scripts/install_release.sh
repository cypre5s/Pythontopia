#!/usr/bin/env bash
# =============================================================================
# Pythontopia 生产发布包事务式安装（阿里云 ECS · root 执行）
# =============================================================================
# 用法：
#   bash install_release.sh /path/to/pythontopia-release-<版本>.tar.gz
#
# 目录约定：
#   /opt/pythontopia/releases/<时间戳>/   每次安装一个不可变 release 目录
#   /opt/pythontopia/current              指向当前生效 release 的符号链接（原子切换）
#   /opt/pythontopia/backups/<时间戳>/    切换前的 pg_dump、.env、Nginx/Compose 配置备份
#   /opt/pythontopia/shared/.env          生产秘密唯一来源（发布包内绝无秘密）
#   /opt/pythontopia/shared/data/         课件 PDF/视频、生成媒体等持久数据
#   /opt/pythontopia/.previous-release    上一生效 release 的绝对路径（回滚用）
#
# 流程（任何一步失败立即终止并输出中文错误）：
#   备份 → 解包 → sha256 校验 → docker load → compose config 校验
#   → 起依赖（postgres/redis）→ 起应用并等健康 → 安装主机 Nginx 并 nginx -t
#   → 原子切换 current → reload nginx → 终验 /healthz
#
# 回滚：bash <上一release>/deploy/scripts/rollback_release.sh（详见 README）
# =============================================================================
set -Eeuo pipefail

# ---------------------------------------------------------------------------
# 常量与全局状态
# ---------------------------------------------------------------------------
BASE_DIR="/opt/pythontopia"
RELEASES_DIR="${BASE_DIR}/releases"
BACKUPS_DIR="${BASE_DIR}/backups"
SHARED_DIR="${BASE_DIR}/shared"
CURRENT_LINK="${BASE_DIR}/current"
PREV_FILE="${BASE_DIR}/.previous-release"
ENV_FILE="${SHARED_DIR}/.env"

COMPOSE_PROJECT="deploy"
COMPOSE_FILE_NAME="docker-compose.production.yml"
IMAGE_COMPONENTS=(frontend backend graph rag)

NGINX_SITE_SRC_REL="deploy/nginx/pythontopia.conf"
NGINX_LOG_FORMAT_SRC_REL="deploy/nginx/pythontopia-log-format.conf"
NGINX_AVAIL="/etc/nginx/sites-available/pythontopia.conf"
NGINX_ENABLED="/etc/nginx/sites-enabled/pythontopia.conf"
NGINX_LOG_FORMAT_AVAIL="/etc/nginx/conf.d/pythontopia-log-format.conf"

TS="$(date '+%Y%m%d-%H%M%S')"
RELEASE_DIR="${RELEASES_DIR}/${TS}"
BACKUP_DIR="${BACKUPS_DIR}/${TS}"

STEP="参数检查"
CONTAINERS_TOUCHED=0

on_error() {
  local line="${1:-?}"
  echo "" >&2
  echo "[安装失败] 步骤「${STEP}」出错（脚本行 ${line}），已立即停止。" >&2
  echo "  - 本次 release 目录：${RELEASE_DIR}（保留现场，便于排查）" >&2
  echo "  - 本次备份目录：${BACKUP_DIR}" >&2
  if [ "${CONTAINERS_TOUCHED}" = "1" ]; then
    echo "  - 注意：容器编排已被改动。若需回到上一版本，执行：" >&2
    echo "      bash ${CURRENT_LINK}/deploy/scripts/rollback_release.sh" >&2
    echo "    （current 链接尚未切换时，它仍指向上一可用版本）" >&2
  else
    echo "  - 容器编排尚未被改动，线上服务不受影响。" >&2
  fi
  exit 1
}
trap 'on_error ${LINENO}' ERR

step() {
  STEP="$1"
  echo ""
  echo "==> ${STEP}"
}

# 等待指定 compose 服务全部 healthy；超时则打印诊断并失败。
wait_healthy() {
  local timeout_sec="$1"; shift
  local services=("$@")
  local deadline=$(( $(date +%s) + timeout_sec ))
  local svc cid status
  for svc in "${services[@]}"; do
    while :; do
      cid="$(docker ps -q \
        --filter "label=com.docker.compose.project=${COMPOSE_PROJECT}" \
        --filter "label=com.docker.compose.service=${svc}" | head -n 1)"
      if [ -n "${cid}" ]; then
        status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "${cid}" 2>/dev/null || echo "missing")"
        if [ "${status}" = "healthy" ]; then
          echo "    服务 ${svc} 已健康"
          break
        fi
      else
        status="容器未创建"
      fi
      if [ "$(date +%s)" -ge "${deadline}" ]; then
        echo "[诊断] 等待服务 ${svc} 健康超时（当前状态：${status}），最近日志：" >&2
        [ -n "${cid}" ] && docker logs --tail 50 "${cid}" >&2 || true
        docker compose -p "${COMPOSE_PROJECT}" -f "${COMPOSE_FILE}" ps >&2 || true
        return 1
      fi
      sleep 5
    done
  done
}

# 只读取指定变量，不执行 .env 内容、不输出变量值；兼容一层单/双引号。
env_file_value() {
  local env_file="$1"
  local var_name="$2"
  local value
  value="$(sed -n "s/^[[:space:]]*${var_name}=//p" "${env_file}" 2>/dev/null | tail -n 1)"
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
    echo "[安装失败] ${var_name} 缺失或仍是公开占位值（值不会输出）。" >&2
    return 1
  fi
  echo "    ${var_name} 已配置（值不输出）"
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

# 从生产 .env 校验秘密存在性、公开占位特征与最小长度；任何分支都不打印值。
check_env_secret() {
  local var_name="$1"
  local min_len="$2"
  local env_file="${3:-${ENV_FILE}}"
  local value normalized
  value="$(env_file_value "${env_file}" "${var_name}")"
  normalized="${value,,}"
  case "${normalized}" in
    ""|pytopia-rag-admin|pythontopia-route-secret\
      |*replace-with*|*replace_with*|*change-me*|*changeme*|*placeholder*\
      |*your-secret*|*your-key*|*your-token*|*example-token*)
      echo "[安装失败] ${var_name} 缺失或仍是公开占位值；请写入独立随机密钥（值不会输出）。" >&2
      return 1
      ;;
  esac
  if [ "${#value}" -lt "${min_len}" ]; then
    echo "[安装失败] ${var_name} 长度不足 ${min_len}；请写入独立高熵密钥（值不会输出）。" >&2
    return 1
  fi
  unset normalized value
  echo "    ${var_name} 强度校验通过（值不输出）"
}

validate_production_ai_env() {
  local env_file="$1"
  local provider base_url rag_embedder
  provider="$(env_file_value "${env_file}" PYT_LLM_PROVIDER)"
  provider="${provider,,}"
  case "${provider}" in
    openai_compat|openai-compatible|openaicompat)
      echo "    生产 LLM provider：DeepSeek"
      base_url="$(env_file_value "${env_file}" PYT_OPENAI_BASE_URL)"
      case "${base_url%/}" in
        https://api.deepseek.com|https://api.deepseek.com/v1) ;;
        *) echo "[安装失败] PYT_OPENAI_BASE_URL 必须指向 https://api.deepseek.com。" >&2; return 1 ;;
      esac
      require_env_value "${env_file}" PYT_OPENAI_BASE_URL || return 1
      require_env_value "${env_file}" PYT_OPENAI_API_KEY || return 1
      require_env_value "${env_file}" PYT_OPENAI_MODEL || return 1
      require_env_value "${env_file}" PYT_OPENAI_MODEL_VISION || return 1
      ;;
    ""|dryrun)
      echo "[安装失败] PYT_LLM_PROVIDER 必须为 openai_compat；生产只使用 DeepSeek。" >&2
      return 1
      ;;
    *)
      echo "[安装失败] PYT_LLM_PROVIDER 仅允许 openai_compat。" >&2
      return 1
      ;;
  esac

  rag_embedder="$(env_file_value "${env_file}" PYT_RAG_EMBEDDER)"
  if [ "${rag_embedder,,}" != "hash" ]; then
    echo "[安装失败] PYT_RAG_EMBEDDER 必须为 hash，禁止外部 embedding API。" >&2
    return 1
  fi
  echo "    RAG 使用本地 hash + BM25 检索"
}

validate_production_judge_env() {
  local env_file="$1"
  local provider piston_url
  provider="$(env_file_value "${env_file}" PYT_JUDGE_PROVIDER)"
  if [ "${provider,,}" != "piston" ]; then
    echo "[安装失败] PYT_JUDGE_PROVIDER 必须为 piston；生产禁止 subprocess。" >&2
    return 1
  fi
  require_env_value "${env_file}" PYT_PISTON_URL || return 1
  require_env_value "${env_file}" PYT_PISTON_PYTHON_VERSION || return 1
  check_env_secret PYT_PISTON_AUTH_TOKEN 32 "${env_file}" || return 1

  piston_url="$(env_file_value "${env_file}" PYT_PISTON_URL)"
  case "${piston_url}" in
    https://*) ;;
    http://*)
      if is_private_piston_http_url "${piston_url}"; then
        echo "    Piston 使用隔离内网 HTTP；请确保网络边界不可被公网访问"
      else
        echo "[安装失败] 外部 Piston 地址必须使用 HTTPS。" >&2
        return 1
      fi
      ;;
    *)
      echo "[安装失败] PYT_PISTON_URL 必须是 http:// 或 https:// URL。" >&2
      return 1
      ;;
  esac
  echo "    Piston 独立判题配置校验通过"
}

# ---------------------------------------------------------------------------
step "参数与运行环境检查"
# ---------------------------------------------------------------------------
ARCHIVE="${1:-}"
if [ -z "${ARCHIVE}" ]; then
  echo "[安装失败] 用法：bash install_release.sh <发布包路径，如 pythontopia-release.tar.gz>" >&2
  exit 1
fi
if [ ! -f "${ARCHIVE}" ]; then
  echo "[安装失败] 发布包不存在：${ARCHIVE}" >&2
  exit 1
fi
if [ "$(id -u)" != "0" ]; then
  echo "[安装失败] 必须以 root 执行（需要 docker、/etc/nginx、/opt/pythontopia 写权限）。" >&2
  exit 1
fi
command -v docker >/dev/null 2>&1 || { echo "[安装失败] 未找到 docker。" >&2; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "[安装失败] docker compose v2 不可用。" >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo "[安装失败] docker daemon 不可访问。" >&2; exit 1; }

DISK_AVAIL_GB=$(df -BG --output=avail / | tail -n 1 | tr -dc '0-9')
if [ -n "${DISK_AVAIL_GB}" ] && [ "${DISK_AVAIL_GB}" -lt 5 ]; then
  echo "[安装失败] 根分区可用空间仅 ${DISK_AVAIL_GB}G（<5G），不足以完成解包与镜像导入，请先清理磁盘。" >&2
  exit 1
fi
echo "    发布包：${ARCHIVE}"
echo "    本次 release：${RELEASE_DIR}"

# ---------------------------------------------------------------------------
step "准备目录结构"
# ---------------------------------------------------------------------------
mkdir -p "${RELEASES_DIR}" "${BACKUPS_DIR}" "${SHARED_DIR}" \
  "${SHARED_DIR}/data/course-pack-43" "${SHARED_DIR}/data/generated-media" \
  "${SHARED_DIR}/data/npcs"
chmod 700 "${SHARED_DIR}"

if [ ! -f "${ENV_FILE}" ]; then
  echo "[安装失败] 缺少生产秘密文件 ${ENV_FILE}。" >&2
  echo "  首次迁移请把现有线上 .env 落位（之后所有版本共用）：" >&2
  echo "    install -D -m 600 <旧部署目录>/deploy/.env ${ENV_FILE}" >&2
  exit 1
fi
chmod 600 "${ENV_FILE}"
check_env_secret PYT_JWT_SECRET 32
check_env_secret PYT_RAG_ADMIN_TOKEN 32
check_env_secret PYT_ROUTE_EXECUTION_SECRET 32
validate_production_ai_env "${ENV_FILE}"
validate_production_judge_env "${ENV_FILE}"
echo "    秘密文件就绪：${ENV_FILE}（权限 600，仅做门禁校验且不输出值）"

# ---------------------------------------------------------------------------
step "切换前备份（pg_dump / .env / Nginx / Compose 覆盖文件）"
# ---------------------------------------------------------------------------
mkdir -p "${BACKUP_DIR}"
chmod 700 "${BACKUP_DIR}"

# 1) 数据库备份：直接用运行中的 postgres 容器自身凭据（不读 .env 的值）
PG_CID="$(docker ps -q \
  --filter "label=com.docker.compose.project=${COMPOSE_PROJECT}" \
  --filter "label=com.docker.compose.service=postgres" | head -n 1)"
if [ -n "${PG_CID}" ]; then
  DUMP_FILE="${BACKUP_DIR}/pythontopia-${TS}.sql.gz"
  docker exec "${PG_CID}" sh -c 'pg_dump -U "${POSTGRES_USER}" -d "${POSTGRES_DB:-pythontopia}"' | gzip > "${DUMP_FILE}"
  if [ ! -s "${DUMP_FILE}" ]; then
    echo "[安装失败] pg_dump 产物为空：${DUMP_FILE}，中止安装。" >&2
    exit 1
  fi
  chmod 600 "${DUMP_FILE}"
  echo "    数据库已备份：${DUMP_FILE}（$(du -h "${DUMP_FILE}" | cut -f1)）"
else
  echo "    未发现运行中的 postgres 容器（项目 ${COMPOSE_PROJECT}），跳过 pg_dump（首次部署属预期）"
fi

# 2) .env 备份
install -m 600 "${ENV_FILE}" "${BACKUP_DIR}/env.backup"
echo "    .env 已备份：${BACKUP_DIR}/env.backup"

# 3) 主机 Nginx 站点配置备份
if [ -f "${NGINX_AVAIL}" ]; then
  install -m 644 "${NGINX_AVAIL}" "${BACKUP_DIR}/nginx-pythontopia.conf.backup"
  echo "    Nginx 站点配置已备份"
else
  echo "    主机尚无 Pythontopia Nginx 站点配置，跳过备份（首次部署属预期）"
fi
if [ -f "${NGINX_LOG_FORMAT_AVAIL}" ]; then
  install -m 644 "${NGINX_LOG_FORMAT_AVAIL}" "${BACKUP_DIR}/nginx-pythontopia-log-format.conf.backup"
fi

# 4) 当前 release 的 Compose 文件与覆盖文件备份
if [ -L "${CURRENT_LINK}" ] && [ -d "${CURRENT_LINK}/deploy" ]; then
  mkdir -p "${BACKUP_DIR}/compose"
  find "${CURRENT_LINK}/deploy" -maxdepth 1 -name 'docker-compose*.yml' -exec cp -a {} "${BACKUP_DIR}/compose/" \;
  echo "    当前版本 Compose 文件已备份到 ${BACKUP_DIR}/compose/"
fi

# ---------------------------------------------------------------------------
step "解包发布包并校验目录结构"
# ---------------------------------------------------------------------------
mkdir -p "${RELEASE_DIR}"
tar -xf "${ARCHIVE}" -C "${RELEASE_DIR}"

# 兼容打包时多包了一层顶级目录的情况：归一化为 RELEASE_DIR 直接含 deploy/
if [ ! -d "${RELEASE_DIR}/deploy" ]; then
  TOP_ENTRIES=("${RELEASE_DIR}"/*)
  if [ "${#TOP_ENTRIES[@]}" -eq 1 ] && [ -d "${TOP_ENTRIES[0]}/deploy" ]; then
    echo "    检测到发布包多包了一层目录（$(basename "${TOP_ENTRIES[0]}")），自动归一化"
    shopt -s dotglob
    mv "${TOP_ENTRIES[0]}"/* "${RELEASE_DIR}/"
    shopt -u dotglob
    rmdir "${TOP_ENTRIES[0]}"
  fi
fi

for required in \
  "deploy/${COMPOSE_FILE_NAME}" \
  "${NGINX_SITE_SRC_REL}" \
  "${NGINX_LOG_FORMAT_SRC_REL}" \
  "deploy/course-data/video_transcripts.json" \
  "deploy/scripts/validate_course_runtime_data.py" \
  "deploy/runtime/sha256sums.txt" \
  "deploy/runtime/images/frontend.tar" \
  "deploy/runtime/images/backend.tar" \
  "deploy/runtime/images/graph.tar" \
  "deploy/runtime/images/rag.tar"; do
  if [ ! -f "${RELEASE_DIR}/${required}" ]; then
    echo "[安装失败] 发布包缺少必需文件：${required}（不符合发布包冻结契约）。" >&2
    exit 1
  fi
done
COMPOSE_FILE="${RELEASE_DIR}/deploy/${COMPOSE_FILE_NAME}"
echo "    发布包结构符合契约（deploy/ + runtime 镜像 + 校验和）"

# ---------------------------------------------------------------------------
step "按 sha256sums.txt 校验全部发布文件完整性"
# ---------------------------------------------------------------------------
# 校验和行内路径可能相对 runtime/ 或相对发布包根目录，两种写法都兼容。
if (cd "${RELEASE_DIR}/deploy/runtime" && sha256sum -c sha256sums.txt --quiet) >/dev/null 2>&1; then
  echo "    校验通过（路径相对 deploy/runtime/）"
elif (cd "${RELEASE_DIR}" && sha256sum -c deploy/runtime/sha256sums.txt --quiet) >/dev/null 2>&1; then
  echo "    校验通过（路径相对发布包根目录）"
else
  echo "[安装失败] sha256 校验未通过：镜像 tar 可能在传输中损坏，请重新分发发布包。" >&2
  (cd "${RELEASE_DIR}/deploy/runtime" && sha256sum -c sha256sums.txt 2>&1 | grep -v ': OK$' | head -n 10) >&2 || true
  exit 1
fi

# ---------------------------------------------------------------------------
step "校验并安装课程视频字幕"
# ---------------------------------------------------------------------------
COURSE_DATA_DIR="${SHARED_DIR}/data/course-pack-43"
TRANSCRIPT_CANDIDATE="${RELEASE_DIR}/deploy/course-data/video_transcripts.json"
COURSE_VALIDATOR="${RELEASE_DIR}/deploy/scripts/validate_course_runtime_data.py"
command -v python3 >/dev/null 2>&1 || {
  echo "[安装失败] 缺少 python3，无法校验课件/视频/字幕数据。" >&2
  exit 1
}
python3 "${COURSE_VALIDATOR}" "${COURSE_DATA_DIR}" --transcript "${TRANSCRIPT_CANDIDATE}"
if [ -f "${COURSE_DATA_DIR}/video_transcripts.json" ]; then
  install -m 600 "${COURSE_DATA_DIR}/video_transcripts.json" \
    "${BACKUP_DIR}/video_transcripts.json.backup"
fi
install -m 644 "${TRANSCRIPT_CANDIDATE}" "${COURSE_DATA_DIR}/video_transcripts.json.next"
mv -f "${COURSE_DATA_DIR}/video_transcripts.json.next" \
  "${COURSE_DATA_DIR}/video_transcripts.json"
python3 "${COURSE_VALIDATOR}" "${COURSE_DATA_DIR}"
echo "    7 份 PDF、视频清单/文件与逐视频字幕来源校验通过"

# ---------------------------------------------------------------------------
step "docker load 导入四个冻结镜像"
# ---------------------------------------------------------------------------
for comp in "${IMAGE_COMPONENTS[@]}"; do
  echo "    导入 pythontopia/${comp}:release ..."
  docker load -i "${RELEASE_DIR}/deploy/runtime/images/${comp}.tar" >/dev/null
  if ! docker image inspect "pythontopia/${comp}:release" >/dev/null 2>&1; then
    echo "[安装失败] 导入 ${comp}.tar 后未找到镜像 pythontopia/${comp}:release，镜像 tar 与冻结契约不符。" >&2
    exit 1
  fi
done
echo "    四个镜像全部导入并核对完成"

# ---------------------------------------------------------------------------
step "接线 .env 并校验 Compose 渲染"
# ---------------------------------------------------------------------------
# 发布包内绝无秘密：release 中 deploy/.env 一律以软链指向共享秘密文件
ln -sfn "${ENV_FILE}" "${RELEASE_DIR}/deploy/.env"
( cd "${RELEASE_DIR}/deploy" && docker compose -p "${COMPOSE_PROJECT}" -f "${COMPOSE_FILE_NAME}" config -q )
echo "    docker compose config 校验通过"

# NPC 人格/对话种子同步到共享数据目录（生产 compose 以只读挂到容器 /app/data/npcs）。
# 后端镜像只 COPY jar，容器内没有这份数据时 NpcPersonaService.seedFromMeta 静默 no-op，
# npc_persona 表恒空 → 所有 NPC 寒暄塌缩成同一段通用人设，且服务端不打任何日志。
if [ -d "${RELEASE_DIR}/deploy/npc-seed" ]; then
  rm -rf "${SHARED_DIR}/data/npcs"
  mkdir -p "${SHARED_DIR}/data/npcs"
  cp -a "${RELEASE_DIR}/deploy/npc-seed/." "${SHARED_DIR}/data/npcs/"
  chmod -R a+rX "${SHARED_DIR}/data/npcs"
  echo "    NPC 人格种子已同步：$(find "${SHARED_DIR}/data/npcs" -name meta.json | wc -l) 个 NPC"
else
  echo "    [警告] 发布包缺少 deploy/npc-seed/，NPC 人格将退化为通用人设（请用新版打包脚本重出包）" >&2
fi

# 生成媒体目录授权给 backend 容器内用户（uid 在镜像构建时分配，运行时查询）
BACKEND_UID="$(docker run --rm "pythontopia/backend:release" id -u 2>/dev/null | tr -dc '0-9' || true)"
if [ -n "${BACKEND_UID}" ]; then
  chown -R "${BACKEND_UID}" "${SHARED_DIR}/data/generated-media"
  echo "    生成媒体目录已授权给容器用户 uid=${BACKEND_UID}"
else
  chmod 777 "${SHARED_DIR}/data/generated-media"
  echo "    无法查询容器用户 uid，生成媒体目录退化为 777（仅存放可重生成产物）"
fi

# ---------------------------------------------------------------------------
step "启动依赖服务（postgres / redis）并等待健康"
# ---------------------------------------------------------------------------
CONTAINERS_TOUCHED=1
( cd "${RELEASE_DIR}/deploy" && docker compose -p "${COMPOSE_PROJECT}" -f "${COMPOSE_FILE_NAME}" up -d --remove-orphans postgres redis )
wait_healthy 180 postgres redis

# ---------------------------------------------------------------------------
step "启动应用服务（rag → graph → backend → frontend）并等待健康"
# ---------------------------------------------------------------------------
( cd "${RELEASE_DIR}/deploy" && docker compose -p "${COMPOSE_PROJECT}" -f "${COMPOSE_FILE_NAME}" up -d --remove-orphans )
# backend 冷启动含 Flyway 迁移，整体预算 8 分钟
wait_healthy 480 pythontopia-rag pythontopia-graph backend frontend

# ---------------------------------------------------------------------------
step "校准公共快速体验账号"
# ---------------------------------------------------------------------------
PYT_COMPOSE_PROJECT="${COMPOSE_PROJECT}" \
PYT_COMPOSE_FILE="${COMPOSE_FILE}" \
  bash "${RELEASE_DIR}/deploy/scripts/ensure_public_demo_account.sh"

# ---------------------------------------------------------------------------
step "安装主机 Nginx 配置并 nginx -t"
# ---------------------------------------------------------------------------
if ! command -v nginx >/dev/null 2>&1; then
  echo "    主机未安装 Nginx，开始 apt 安装（需要外网/阿里云源可用）..."
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -qq
  apt-get install -y -qq nginx
fi

install -m 644 "${RELEASE_DIR}/${NGINX_SITE_SRC_REL}" "${NGINX_AVAIL}"
install -m 644 "${RELEASE_DIR}/${NGINX_LOG_FORMAT_SRC_REL}" "${NGINX_LOG_FORMAT_AVAIL}"
ln -sfn "${NGINX_AVAIL}" "${NGINX_ENABLED}"
# 停用 Ubuntu 自带 default 站点，避免与本站点的 default_server 冲突
# （sites-available/default 原文件保留，可随时恢复）
rm -f /etc/nginx/sites-enabled/default

if ! nginx -t; then
  echo "[安装失败] nginx -t 校验未通过，开始恢复旧 Nginx 配置..." >&2
  if [ -f "${BACKUP_DIR}/nginx-pythontopia-log-format.conf.backup" ]; then
    install -m 644 "${BACKUP_DIR}/nginx-pythontopia-log-format.conf.backup" "${NGINX_LOG_FORMAT_AVAIL}"
  else
    rm -f "${NGINX_LOG_FORMAT_AVAIL}"
  fi
  if [ -f "${BACKUP_DIR}/nginx-pythontopia.conf.backup" ]; then
    install -m 644 "${BACKUP_DIR}/nginx-pythontopia.conf.backup" "${NGINX_AVAIL}"
    nginx -t >&2 || true
    echo "  已恢复备份的站点配置；容器已是新版本，如需整体回退请执行 rollback_release.sh。" >&2
  else
    rm -f "${NGINX_ENABLED}" "${NGINX_AVAIL}"
    echo "  已移除新装站点配置（此前主机无本站点配置）。" >&2
  fi
  exit 1
fi
echo "    Nginx 配置校验通过"

# ---------------------------------------------------------------------------
step "原子切换 current 符号链接"
# ---------------------------------------------------------------------------
PREV_RELEASE="$(readlink -f "${CURRENT_LINK}" 2>/dev/null || true)"
if [ -n "${PREV_RELEASE}" ] && [ "${PREV_RELEASE}" != "${RELEASE_DIR}" ]; then
  echo "${PREV_RELEASE}" > "${PREV_FILE}"
  echo "    已记录上一版本：${PREV_RELEASE}"
fi
TMP_LINK="${CURRENT_LINK}.tmp.$$"
ln -s "${RELEASE_DIR}" "${TMP_LINK}"
mv -T "${TMP_LINK}" "${CURRENT_LINK}"
echo "    current -> ${RELEASE_DIR}"

# ---------------------------------------------------------------------------
step "reload 主机 Nginx"
# ---------------------------------------------------------------------------
systemctl enable nginx >/dev/null 2>&1 || true
if systemctl is-active --quiet nginx; then
  systemctl reload nginx
else
  systemctl start nginx
fi
echo "    Nginx 已生效（reload/start）"

# ---------------------------------------------------------------------------
step "终验：HTTP/HTTPS 全链路与容器直连健康检查"
# ---------------------------------------------------------------------------
sleep 2
curl -fsS -m 5 -o /dev/null http://127.0.0.1:8080/healthz \
  || { echo "[安装失败] 容器直连 127.0.0.1:8080/healthz 不可达。" >&2; exit 1; }
curl -fsS -m 5 -o /dev/null http://127.0.0.1/healthz \
  || { echo "[安装失败] 主机 Nginx 80 端口 /healthz 不可达（容器层正常，问题在主机 Nginx）。" >&2; exit 1; }
curl -fsS --connect-timeout 5 --max-time 10 --resolve 47.120.29.62:443:127.0.0.1 -o /dev/null https://47.120.29.62/healthz \
  || { echo "[安装失败] 主机 Nginx 443 端口 HTTPS /healthz 不可达或证书校验失败。" >&2; exit 1; }
echo "    本机 HTTP + HTTPS 全链路健康"

# ---------------------------------------------------------------------------
# 收尾报告
# ---------------------------------------------------------------------------
echo ""
echo "============================================================"
echo "安装完成。"
echo "  生效版本：${RELEASE_DIR}"
[ -n "${PREV_RELEASE:-}" ] && echo "  上一版本：${PREV_RELEASE}（保留，可回滚）"
echo "  本次备份：${BACKUP_DIR}"
echo "  HTTP 入口：http://47.120.29.62/"
echo "  HTTPS 入口：https://47.120.29.62/"
echo "  回滚命令：bash ${CURRENT_LINK}/deploy/scripts/rollback_release.sh"
RELEASE_COUNT="$(find "${RELEASES_DIR}" -mindepth 1 -maxdepth 1 -type d | wc -l)"
if [ "${RELEASE_COUNT}" -gt 3 ]; then
  echo "  提示：releases 目录已有 ${RELEASE_COUNT} 个版本，可人工清理最旧版本释放磁盘"
  echo "       （脚本不自动删除任何 release；至少保留最近 2 个以保证可回滚）"
fi
echo "============================================================"
