#!/usr/bin/env bash
# 安装 ECS 下载验证码门控：静态页 + Python 校验服务 + nginx secure_link。
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GATE_SRC="${ROOT}/download-gate"
GATE_DST="/opt/pythontopia/download-gate"
SECRET_FILE="/opt/pythontopia/shared/download-sign.secret"

if [[ "${EUID}" -ne 0 ]]; then
  echo "请使用 root 执行：sudo bash $0" >&2
  exit 1
fi

mkdir -p /opt/pythontopia/shared "${GATE_DST}"
install -m 755 "${GATE_SRC}/server.py" "${GATE_DST}/server.py"
install -m 644 "${GATE_SRC}/index.html" "${GATE_DST}/index.html"

if [[ ! -s "${SECRET_FILE}" ]]; then
  openssl rand -hex 32 > "${SECRET_FILE}"
  chmod 600 "${SECRET_FILE}"
  echo "已生成下载门控密钥：${SECRET_FILE}"
fi

install -m 644 "${ROOT}/systemd/pythontopia-download-gate.service" /etc/systemd/system/pythontopia-download-gate.service
systemctl daemon-reload
systemctl enable --now pythontopia-download-gate.service

echo "下载门控服务已启动：$(systemctl is-active pythontopia-download-gate.service)"
echo "请更新 nginx 站点配置后执行：nginx -t && systemctl reload nginx"
echo "门控密钥文件：${SECRET_FILE}"
