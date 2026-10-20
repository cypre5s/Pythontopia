#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="/opt/pythontopia"
CURRENT_DIR="${ROOT_DIR}/current"
SHARED_DIR="${ROOT_DIR}/shared"
INCOMING_DIR="${ROOT_DIR}/incoming"
ENV_FILE="${SHARED_DIR}/.env"
REPOSITORY="cypre5s/Pythontopia"
TAG="${1:-}"

if [[ ! "${TAG}" =~ ^web-v[0-9]{8}\.[0-9]+$ ]]; then
  echo "用法：sudo bash install_github_release.sh web-vYYYYMMDD.N" >&2
  exit 2
fi
if [[ "$(id -u)" != "0" ]]; then
  echo "[安装失败] 必须以 root 执行。" >&2
  exit 1
fi

PREFLIGHT="${CURRENT_DIR}/deploy/scripts/preflight_ecs.sh"
INSTALLER="${CURRENT_DIR}/deploy/scripts/install_release.sh"
command -v curl >/dev/null 2>&1 || { echo "[安装失败] 未找到 curl。" >&2; exit 1; }
for required in "$PREFLIGHT" "$INSTALLER"; do
  if [[ ! -f "$required" ]]; then
    echo "[安装失败] 当前 release 缺少脚本：$required" >&2
    exit 1
  fi
done

PYT_ENV_FILE="${ENV_FILE}" bash "${PREFLIGHT}"

asset_name="pythontopia-${TAG}.tar.gz"
asset_url="https://github.com/${REPOSITORY}/releases/download/${TAG}/${asset_name}"
archive="${INCOMING_DIR}/${asset_name}"
partial="${archive}.part"
umask 077
mkdir -p "${INCOMING_DIR}"

echo "==> 从 GitHub 下载 ${TAG}"
curl --fail --location --silent --show-error --retry 3 --retry-delay 2 \
  --continue-at - --connect-timeout 20 --output "${partial}" "${asset_url}"
[[ -s "${partial}" ]] || { echo "[安装失败] GitHub release asset 为空。" >&2; exit 1; }
mv -f -- "${partial}" "${archive}"

echo "==> 校验并安装 release 包"
if bash "${INSTALLER}" "${archive}"; then
  rm -- "${archive}"
  echo "[完成] ${TAG} 已部署；下载包已清理。"
else
  echo "[安装失败] 保留 ${archive} 供排查；当前 release 未由本脚本清理。" >&2
  exit 1
fi
