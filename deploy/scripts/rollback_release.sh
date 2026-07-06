#!/usr/bin/env bash
# =============================================================================
# Pythontopia 生产回滚（回切上一 release 与主机 Nginx 配置 · root 执行）
# =============================================================================
# 用法：
#   bash rollback_release.sh                # 回滚到 /opt/pythontopia/.previous-release 记录的版本
#   bash rollback_release.sh <release目录>  # 回滚/切换到指定 release，如 /opt/pythontopia/releases/20260610-120000
#
# 行为说明：
#   - 镜像 tag 固定为 pythontopia/*:release，因此回滚会重新 docker load 目标
#     release 自带的镜像 tar（先做 sha256 校验），把 tag 指回旧镜像。
#   - 回滚只回切「应用与配置」，绝不删除、绝不重建 Postgres 数据卷
#     （deploy_pytopia_pg），数据库内容保持回滚时刻的状态。
#   - 若新版本携带的 Flyway 迁移已写入数据库且与旧代码不兼容，需要按
#     deploy/README.md「数据库手动恢复」用 pg_dump 备份人工恢复，本脚本不自动执行。
# =============================================================================
set -Eeuo pipefail

BASE_DIR="/opt/pythontopia"
CURRENT_LINK="${BASE_DIR}/current"
PREV_FILE="${BASE_DIR}/.previous-release"
ENV_FILE="${BASE_DIR}/shared/.env"

COMPOSE_PROJECT="deploy"
COMPOSE_FILE_NAME="docker-compose.production.yml"
IMAGE_COMPONENTS=(frontend backend graph rag)

NGINX_AVAIL="/etc/nginx/sites-available/pythontopia.conf"
NGINX_ENABLED="/etc/nginx/sites-enabled/pythontopia.conf"

STEP="参数检查"
on_error() {
  echo "" >&2
  echo "[回滚失败] 步骤「${STEP}」出错（脚本行 ${1:-?}），已立即停止。" >&2
  echo "  请人工检查容器状态：docker compose -p ${COMPOSE_PROJECT} ps" >&2
  echo "  数据卷 deploy_pytopia_pg 未被本脚本改动。" >&2
  exit 1
}
trap 'on_error ${LINENO}' ERR

step() {
  STEP="$1"
  echo ""
  echo "==> ${STEP}"
}

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
        return 1
      fi
      sleep 5
    done
  done
}

# ---------------------------------------------------------------------------
step "确定回滚目标"
# ---------------------------------------------------------------------------
if [ "$(id -u)" != "0" ]; then
  echo "[回滚失败] 必须以 root 执行。" >&2
  exit 1
fi
docker info >/dev/null 2>&1 || { echo "[回滚失败] docker daemon 不可访问。" >&2; exit 1; }

TARGET="${1:-}"
if [ -z "${TARGET}" ]; then
  if [ ! -f "${PREV_FILE}" ]; then
    echo "[回滚失败] 未指定目标且 ${PREV_FILE} 不存在（可能从未完成过两次安装）。" >&2
    echo "  可手动指定：bash rollback_release.sh /opt/pythontopia/releases/<时间戳>" >&2
    exit 1
  fi
  TARGET="$(cat "${PREV_FILE}")"
fi
TARGET="$(readlink -f "${TARGET}")"

if [ ! -d "${TARGET}" ]; then
  echo "[回滚失败] 目标 release 目录不存在：${TARGET}" >&2
  exit 1
fi
for required in \
  "deploy/${COMPOSE_FILE_NAME}" \
  "deploy/nginx/pythontopia.conf" \
  "deploy/runtime/sha256sums.txt" \
  "deploy/runtime/images/frontend.tar" \
  "deploy/runtime/images/backend.tar" \
  "deploy/runtime/images/graph.tar" \
  "deploy/runtime/images/rag.tar"; do
  if [ ! -f "${TARGET}/${required}" ]; then
    echo "[回滚失败] 目标 release 缺少必需文件：${required}，无法回滚到该版本。" >&2
    exit 1
  fi
done
if [ ! -f "${ENV_FILE}" ]; then
  echo "[回滚失败] 缺少生产秘密文件 ${ENV_FILE}，无法启动服务。" >&2
  exit 1
fi

FROM_RELEASE="$(readlink -f "${CURRENT_LINK}" 2>/dev/null || true)"
echo "    当前版本：${FROM_RELEASE:-（current 链接不存在）}"
echo "    回滚目标：${TARGET}"
if [ "${FROM_RELEASE}" = "${TARGET}" ]; then
  echo "    注意：目标与当前版本相同，将按「重新应用该版本」继续执行（幂等）。"
fi

# ---------------------------------------------------------------------------
step "校验并重新导入目标版本镜像（tag 指回旧镜像）"
# ---------------------------------------------------------------------------
if (cd "${TARGET}/deploy/runtime" && sha256sum -c sha256sums.txt --quiet) 2>/dev/null; then
  echo "    校验通过（路径相对 deploy/runtime/）"
elif (cd "${TARGET}" && sha256sum -c deploy/runtime/sha256sums.txt --quiet) 2>/dev/null; then
  echo "    校验通过（路径相对发布包根目录）"
else
  echo "[回滚失败] 目标 release 的镜像 tar sha256 校验未通过，文件可能已损坏。" >&2
  exit 1
fi

for comp in "${IMAGE_COMPONENTS[@]}"; do
  echo "    导入 pythontopia/${comp}:release（旧版本）..."
  docker load -i "${TARGET}/deploy/runtime/images/${comp}.tar" >/dev/null
  docker image inspect "pythontopia/${comp}:release" >/dev/null 2>&1 \
    || { echo "[回滚失败] 导入后未找到镜像 pythontopia/${comp}:release。" >&2; exit 1; }
done

# ---------------------------------------------------------------------------
step "以目标版本编排重建服务并等待健康"
# ---------------------------------------------------------------------------
ln -sfn "${ENV_FILE}" "${TARGET}/deploy/.env"
COMPOSE_FILE="${TARGET}/deploy/${COMPOSE_FILE_NAME}"
( cd "${TARGET}/deploy" && docker compose -p "${COMPOSE_PROJECT}" -f "${COMPOSE_FILE_NAME}" config -q )
# --force-recreate：镜像 tag 未变（内容已变），必须强制重建容器才能用回旧镜像
( cd "${TARGET}/deploy" && docker compose -p "${COMPOSE_PROJECT}" -f "${COMPOSE_FILE_NAME}" up -d --remove-orphans --force-recreate )
wait_healthy 480 postgres redis pythontopia-rag pythontopia-graph backend frontend

# ---------------------------------------------------------------------------
step "回切主机 Nginx 配置"
# ---------------------------------------------------------------------------
install -m 644 "${TARGET}/deploy/nginx/pythontopia.conf" "${NGINX_AVAIL}"
ln -sfn "${NGINX_AVAIL}" "${NGINX_ENABLED}"
if ! nginx -t; then
  echo "[回滚失败] 目标版本 Nginx 配置未通过 nginx -t，主机 Nginx 配置保持当前内容未 reload。" >&2
  exit 1
fi
if systemctl is-active --quiet nginx; then
  systemctl reload nginx
else
  systemctl start nginx
fi
echo "    Nginx 已回切并 reload"

# ---------------------------------------------------------------------------
step "回切 current 符号链接"
# ---------------------------------------------------------------------------
TMP_LINK="${CURRENT_LINK}.tmp.$$"
ln -s "${TARGET}" "${TMP_LINK}"
mv -T "${TMP_LINK}" "${CURRENT_LINK}"
# 记录被回滚掉的版本，保留「再切回去」（roll forward）的能力
if [ -n "${FROM_RELEASE}" ] && [ "${FROM_RELEASE}" != "${TARGET}" ]; then
  echo "${FROM_RELEASE}" > "${PREV_FILE}"
fi
echo "    current -> ${TARGET}"

# ---------------------------------------------------------------------------
step "终验：80 全链路健康检查"
# ---------------------------------------------------------------------------
sleep 2
curl -fsS -m 5 -o /dev/null http://127.0.0.1:8080/healthz \
  || { echo "[回滚失败] 容器直连 127.0.0.1:8080/healthz 不可达。" >&2; exit 1; }
curl -fsS -m 5 -o /dev/null http://127.0.0.1/healthz \
  || { echo "[回滚失败] 主机 Nginx 80 端口 /healthz 不可达。" >&2; exit 1; }

echo ""
echo "============================================================"
echo "回滚完成。"
echo "  生效版本：${TARGET}"
[ -n "${FROM_RELEASE}" ] && echo "  被回滚版本：${FROM_RELEASE}（目录保留，可再切回）"
echo "  数据卷 deploy_pytopia_pg 未被改动；如需把数据库恢复到安装前备份，"
echo "  请按 deploy/README.md「数据库手动恢复」操作（pg_dump 备份在 /opt/pythontopia/backups/）。"
echo "============================================================"
