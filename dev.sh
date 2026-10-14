#!/usr/bin/env bash
# Pythontopia 开发模式启动脚本
# ---------------------------------------------------------------------------
# 依赖（Postgres / Redis / pythontopia-graph / pythontopia-rag）用 Docker 拉起，
# 后端用原生 `mvn spring-boot:run`（:18080），前端用 Vite 开发服务器（:5173，热更新）。
#
# 设计目标：跑完脚本即可用 —— 全程健康门禁，任一核心依赖未就绪直接 fail-fast 并打印日志。
#
# 用法：
#   ./dev.sh            启动开发栈（前端在前台，Ctrl+C 退出并清理后端）
#   ./dev.sh --sandbox  额外启动本机 Piston/Isolate 并安装 Python runtime
#   ./dev.sh --down     停止本脚本拉起的一切（后端 + 前端 + docker 依赖）
#   ./dev.sh --no-deps  跳过 docker 依赖（依赖已在别处运行时）
#
# 打开：http://localhost:5173 （Vite 代理 /api → 后端 :18080）
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$ROOT_DIR/deploy"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"
WORK_DIR="$ROOT_DIR/.work"
BACKEND_LOG="$WORK_DIR/dev-backend.log"
BACKEND_PID_FILE="$WORK_DIR/dev-backend.pid"

DEP_SERVICES=(postgres redis pythontopia-graph pythontopia-rag)
STOP_SERVICES=(postgres redis pythontopia-graph pythontopia-rag piston)

c_info() { printf '\033[36m[INFO]\033[0m %s\n' "$*"; }
c_ok()   { printf '\033[32m[OK]\033[0m %s\n' "$*"; }
c_warn() { printf '\033[33m[WARN]\033[0m %s\n' "$*"; }
c_err()  { printf '\033[31m[ERROR]\033[0m %s\n' "$*" >&2; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { c_err "缺少命令: $1"; exit 1; }
}

wait_http_ok() {
  local url="$1" retries="${2:-90}" interval="${3:-2}" code
  for _ in $(seq 1 "$retries"); do
    code=$(curl --noproxy '*' -s -o /dev/null -w '%{http_code}' "$url" 2>/dev/null || true)
    [[ "$code" == "200" ]] && return 0
    sleep "$interval"
  done
  return 1
}

compose() { (cd "$DEPLOY_DIR" && docker compose "$@"); }

# Postgres 数据卷密码在首次创建时固定；仅 pg_isready 无法发现与 deploy/.env 不一致。
# 本机 spring-boot:run 走 127.0.0.1:15432，必须先验证 TCP 密码（详见 docs/dev-startup.md）。
verify_postgres_password_from_host() {
  local user="${PYT_DB_USERNAME:-pytopia}"
  local pass="${PYT_DB_PASSWORD:-pytopia_dev_password}"
  local db="${PYT_DB_NAME:-pythontopia}"

  _pg_ok() {
    compose exec -T -e PGPASSWORD="$pass" postgres \
      psql -h 127.0.0.1 -U "$user" -d "$db" -c 'SELECT 1' >/dev/null 2>&1
  }

  if _pg_ok; then
    return 0
  fi

  c_warn "Postgres :15432 密码与 deploy/.env 不一致（常见：改过 PYT_DB_PASSWORD 但未改数据卷）"
  c_info "尝试在容器内将用户 ${user} 密码同步为 .env 中的值…"
  compose exec -T postgres psql -U "$user" -d "$db" -v ON_ERROR_STOP=1 \
    -c "ALTER USER \"${user}\" PASSWORD '${pass}';" >/dev/null 2>&1 \
    || { c_err "无法同步密码，请手动执行 docs/dev-startup.md 中的 ALTER USER"; return 1; }

  if _pg_ok; then
    c_ok "Postgres 密码已与 deploy/.env 对齐"
    return 0
  fi
  c_err "同步后仍无法从宿主连接 Postgres，见 docs/dev-startup.md"
  return 1
}

stop_all() {
  c_info "停止开发栈…"
  if [[ -f "$BACKEND_PID_FILE" ]]; then
    local pid; pid="$(cat "$BACKEND_PID_FILE" 2>/dev/null || true)"
    if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
      # spring-boot:run 会派生子进程，杀整个进程组
      kill -- "-$pid" 2>/dev/null || kill "$pid" 2>/dev/null || true
    fi
    rm -f "$BACKEND_PID_FILE"
  fi
  # 兜底：按端口清理可能残留的后端
  pkill -f 'spring-boot:run' 2>/dev/null || true
  compose stop "${STOP_SERVICES[@]}" 2>/dev/null || true
  c_ok "已停止后端 + docker 依赖（数据卷保留）。前端 Vite 若在前台已随 Ctrl+C 退出。"
}

cleanup_on_exit() {
  # 仅清理后端（前端在前台，已自行退出）；docker 依赖保留，便于下次秒起。
  if [[ -f "$BACKEND_PID_FILE" ]]; then
    local pid; pid="$(cat "$BACKEND_PID_FILE" 2>/dev/null || true)"
    if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
      c_info "关闭后端 (pid=$pid)…"
      kill -- "-$pid" 2>/dev/null || kill "$pid" 2>/dev/null || true
    fi
    rm -f "$BACKEND_PID_FILE"
  fi
  c_info "docker 依赖仍在运行（./dev.sh --down 可一并停止）。"
}

# ---- 参数 ----
START_DEPS=1
START_SANDBOX=0
case "${1:-}" in
  --down) require_cmd docker; stop_all; exit 0 ;;
  --no-deps) START_DEPS=0 ;;
  --sandbox) START_SANDBOX=1 ;;
  "" ) ;;
  * ) c_err "未知参数: $1（支持: --down / --no-deps / --sandbox）"; exit 1 ;;
esac

require_cmd docker
require_cmd curl
require_cmd java
require_cmd mvn
require_cmd node
require_cmd npm

mkdir -p "$WORK_DIR"

# ---- deploy/.env ----
if [[ ! -f "$DEPLOY_DIR/.env" ]]; then
  cp "$DEPLOY_DIR/.env.example" "$DEPLOY_DIR/.env"
  c_warn "已从 .env.example 生成 deploy/.env（按需填 LLM / 讯飞 密钥）"
fi
set -a
# shellcheck disable=SC1090
source "$DEPLOY_DIR/.env"
set +a
# Local development has no application-level model request cap; deployment keeps the daily caps.
export PYT_AI_REQUEST_LIMITS_ENABLED=false

# Compose and the local backend must receive the same per-run service token.
configured_internal_token="${PYT_RAG_ADMIN_TOKEN:-}"
resolved_internal_token="$("$DEPLOY_DIR/scripts/resolve_dev_internal_token.sh" "$configured_internal_token")"
if [[ "$START_DEPS" == "0" && "$resolved_internal_token" != "$configured_internal_token" ]]; then
  c_err "--no-deps 需要 deploy/.env 配置与现有 Graph 共用的有效 PYT_RAG_ADMIN_TOKEN"
  exit 1
fi
export PYT_RAG_ADMIN_TOKEN="$resolved_internal_token"
if [[ "$PYT_RAG_ADMIN_TOKEN" != "$configured_internal_token" ]]; then
  c_warn "本次开发栈已生成临时服务间鉴权令牌（未写入磁盘）"
fi

if [[ "$START_SANDBOX" == "1" ]]; then
  DEP_SERVICES+=(piston)
  export PYT_PISTON_URL="http://127.0.0.1:2000"
  export PYT_PISTON_PYTHON_VERSION="${PYT_DEV_PISTON_PYTHON_VERSION:-3.12.0}"
fi

# ---- 1. 依赖（docker） ----
if [[ "$START_DEPS" == "1" ]]; then
  c_info "拉起依赖容器: ${DEP_SERVICES[*]} …"
  if [[ "$START_SANDBOX" == "1" ]]; then
    compose --profile sandbox up -d "${DEP_SERVICES[@]}"
    c_info "确保本地 Piston 已安装 Python ${PYT_PISTON_PYTHON_VERSION} …"
    node "$DEPLOY_DIR/scripts/ensure_dev_piston_runtime.mjs"
  else
    compose up -d "${DEP_SERVICES[@]}"
  fi

  c_info "等待 Postgres 就绪…"
  for _ in $(seq 1 60); do
    if compose exec -T postgres pg_isready -U "${PYT_DB_USERNAME:-pytopia}" -d pythontopia >/dev/null 2>&1; then
      break
    fi
    sleep 2
  done
  compose exec -T postgres pg_isready -U "${PYT_DB_USERNAME:-pytopia}" -d pythontopia >/dev/null 2>&1 \
    || { c_err "Postgres 未就绪"; compose ps; exit 1; }
  c_ok "Postgres ready (127.0.0.1:15432)"

  c_info "等待 graph / rag 健康…"
  wait_http_ok "http://127.0.0.1:18091/healthz" 60 2 || c_warn "graph 未就绪（答疑/工坊/路径会降级）"
  wait_http_ok "http://127.0.0.1:18092/healthz" 60 2 || c_warn "rag 未就绪（课件证据检索会降级）"
else
  c_info "--no-deps：跳过 docker 依赖启动"
fi

# ---- 2. 后端本地资源软链（课件 PDF / 视频 / 多模态产物目录） ----
if [[ ! -e "$BACKEND_DIR/data" ]]; then
  ln -s "$ROOT_DIR/data" "$BACKEND_DIR/data"
  c_info "已创建软链 backend/data -> ../data"
fi
mkdir -p "$ROOT_DIR/data/course-pack-43/generated-media"

# ---- 3. 后端（原生 mvn spring-boot:run） ----
export PYT_DB_URL="jdbc:postgresql://127.0.0.1:15432/pythontopia"
export PYT_DB_USERNAME="${PYT_DB_USERNAME:-pytopia}"
export PYT_DB_PASSWORD="${PYT_DB_PASSWORD:-pytopia_dev_password}"
configured_jwt_secret="${PYT_JWT_SECRET:-}"
resolved_jwt_secret="$("$DEPLOY_DIR/scripts/resolve_dev_jwt_secret.sh" "$configured_jwt_secret")"
if [[ "$resolved_jwt_secret" != "$configured_jwt_secret" ]]; then
  c_warn "本地 PYT_JWT_SECRET 缺失、过短或仍为公开占位值；本次启动已生成临时高熵密钥（未写入磁盘）"
fi
export PYT_JWT_SECRET="$resolved_jwt_secret"
teacher_invite_code="${PYT_TEACHER_INVITE_CODE:-}"
case "${teacher_invite_code,,}" in
  "" | "pytopia-teacher" | *replace-with* | *replace_with* | *change-me* | *changeme* | *placeholder* | *your-invite*)
    require_cmd openssl
    export PYT_TEACHER_INVITE_CODE="$(openssl rand -hex 24)"
    c_warn "本次开发栈已生成临时教师邀请码（未写入磁盘）；如需固定值，请配置 PYT_TEACHER_INVITE_CODE"
    ;;
esac
# 后端默认即 localhost:18091/18092，这里显式声明对齐 dockerized graph/rag
export PYT_GRAPH_URL="${PYT_GRAPH_URL:-http://127.0.0.1:18091}"
export PYT_RAG_URL="${PYT_RAG_URL:-http://127.0.0.1:18092}"
# 多 Agent RouteDecision 执行签名使用独立、每次开发栈启动随机的高熵密钥；不复用公开的
# graph/rag internal token。外部显式配置时保持原值，便于多后端实例共享验证。
if [[ -z "${PYT_ROUTE_EXECUTION_SECRET:-}" ]]; then
  require_cmd openssl
  export PYT_ROUTE_EXECUTION_SECRET="$(openssl rand -hex 32)"
fi
# 多模态：复用讯飞 PYT_SPARK_*（缺失则文生图/合成自动降级为本地占位，不影响可用）
export PYT_SPARK_APP_ID="${PYT_SPARK_APP_ID:-}"
export PYT_SPARK_API_KEY="${PYT_SPARK_API_KEY:-}"
export PYT_SPARK_API_SECRET="${PYT_SPARK_API_SECRET:-}"

# 讯飞代理（关键踩坑）：部分本机网络用 fake-ip DNS（如 198.18.x.x），Java HttpClient
# 默认不读 HTTPS_PROXY 环境变量，会导致文生图(TTI)/语音合成(TTS) connect timeout 而恒降级。
# 这里把 PYT_BILI_PROXY（默认 7892）转成 JVM 代理参数注入后端，并用 nonProxyHosts 排除
# 本地 PG/graph/rag，使「讯飞走代理、本地直连」。代理留空则不注入（直连网络无需代理）。
# 详见 docs/dev-startup.md「讯飞多模态连不通（fake-ip DNS）」。
XFYUN_PROXY="${PYT_XFYUN_PROXY:-${PYT_BILI_PROXY:-http://127.0.0.1:7892}}"
BACKEND_JAVA_PROXY_OPTS=""
if [[ -n "$XFYUN_PROXY" ]]; then
  proxy_hostport="${XFYUN_PROXY#http://}"; proxy_hostport="${proxy_hostport#https://}"
  proxy_host="${proxy_hostport%%:*}"; proxy_port="${proxy_hostport##*:}"
  BACKEND_JAVA_PROXY_OPTS="-Dhttps.proxyHost=${proxy_host} -Dhttps.proxyPort=${proxy_port} -Dhttp.proxyHost=${proxy_host} -Dhttp.proxyPort=${proxy_port} -Dhttp.nonProxyHosts=localhost|127.0.0.1|::1"
  c_info "后端讯飞调用经代理 ${proxy_host}:${proxy_port}（本地服务直连）"
fi

# ffmpeg：合成短视频(Track B media job)依赖。缺失则合成作业置 FAILED(VIDEO_COMPOSE_UNAVAILABLE)，
# 其它链路不受影响。仅提示，不强制（无 root 可 pip install imageio-ffmpeg 后软链进 PATH）。
if ! command -v ffmpeg >/dev/null 2>&1; then
  c_warn "未检测到 ffmpeg：百晓工坊「合成短视频」会降级为 VIDEO_COMPOSE_UNAVAILABLE。"
  c_warn "  安装：sudo apt install ffmpeg  或  pip install imageio-ffmpeg 后软链进 PATH。"
fi

verify_postgres_password_from_host || exit 1

trap cleanup_on_exit INT TERM EXIT

if wait_http_ok "http://127.0.0.1:18080/healthz" 1 1; then
  c_warn "后端 :18080 已在运行，复用之（跳过启动）"
else
  c_info "启动后端 (mvn spring-boot:run)，日志: $BACKEND_LOG"
  : > "$BACKEND_LOG"
  # exec 让 PID 文件记录新会话的进程组 ID，退出时能一并清理 Maven 与 Spring Boot 子进程。
  (
    cd "$BACKEND_DIR"
    export JAVA_TOOL_OPTIONS="$BACKEND_JAVA_PROXY_OPTS"
    exec setsid mvn -q -s .mvn/settings.build.xml spring-boot:run
  ) >>"$BACKEND_LOG" 2>&1 &
  echo "$!" > "$BACKEND_PID_FILE"

  c_info "等待后端健康 (:18080/healthz)…最长 ~3 分钟（首次编译较慢）"
  if ! wait_http_ok "http://127.0.0.1:18080/healthz" 90 2; then
    c_err "后端启动失败，最后 60 行日志："
    tail -n 60 "$BACKEND_LOG" >&2 || true
    exit 1
  fi
fi
c_ok "后端 ready (http://127.0.0.1:18080)"

# ---- 4. 前端依赖 ----
if [[ ! -d "$FRONTEND_DIR/node_modules" ]]; then
  c_info "安装前端依赖 (npm ci)…"
  ( cd "$FRONTEND_DIR" && npm ci )
fi

# ---- 就绪提示 ----
echo ""
c_ok "Pythontopia 开发栈已就绪"
echo "  前端(Vite):  http://localhost:5173   <- 在这里使用应用"
echo "  后端:        http://127.0.0.1:18080/healthz   (日志: $BACKEND_LOG)"
echo "  Graph:       http://127.0.0.1:18091/healthz"
echo "  RAG:         http://127.0.0.1:18092/healthz"
echo "  Postgres:    127.0.0.1:15432   Redis: 127.0.0.1:16379"
echo ""
echo "  停止：Ctrl+C 退出前端并关后端；彻底停止依赖容器：./dev.sh --down"
echo ""

# ---- 5. 前端开发服务器（前台；Ctrl+C 触发 cleanup_on_exit） ----
c_info "启动前端开发服务器 (npm run dev)…"
cd "$FRONTEND_DIR"
npm run dev
