#!/usr/bin/env bash
# =============================================================================
# 上传 Android release APK 到 ECS 的 Nginx 下载目录（guide 页「下载 Android App」对应位置）。
# =============================================================================
# 背景：
#   - ECS 主机 Nginx 已配 `location /downloads/ { alias /opt/pythontopia/downloads/; }`
#     （见 deploy/nginx/pythontopia.conf），把文件丢进该目录即可经
#     http://47.120.29.62/downloads/<file> 直出（.apk 浏览器自动下载）。
#   - WSL2 TUN 代理（默认网关 198.18.0.2）会劫持所有 TCP 但不转发 SSH 的 22 端口，
#     故上传前必须为 ECS IP 加一条直连路由（需 sudo）。
#
# 用法：
#   bash deploy/upload-android-apk.sh                 # 用 release APK 默认路径
#   bash deploy/upload-android-apk.sh path/to/app.apk # 指定 APK
# 需要：本机已配置 ~/.ssh/config 的 ecs-pythontopia 别名 + ~/.ssh/ecs-pythontopia.pem。
# =============================================================================
set -euo pipefail

ECS_IP="47.120.29.62"
APK_SRC="${1:-android/app/build/outputs/apk/release/app-release.apk}"
REMOTE_PATH="/opt/pythontopia/downloads/pythontopia.apk"

[[ -f "$APK_SRC" ]] || { echo "APK 不存在：$APK_SRC（先在 android/ 跑 ./gradlew :app:assembleRelease）"; exit 1; }

# 1) 绕过 TUN：为 ECS 单 IP 加直连路由（自动选非 198.18.0.2 的真实默认网关）。
GW="$(ip route show default | awk '$3 != "198.18.0.2" {print $3; exit}')"
DEV="$(ip route show default | awk '$3 != "198.18.0.2" {print $5; exit}')"
if [[ -z "${GW:-}" || -z "${DEV:-}" ]]; then
  echo "未找到非 TUN 的真实默认网关；请手动 ip route show default 确认后改本脚本。"
  exit 1
fi
echo "real gateway: $GW dev $DEV"
sudo ip route replace "${ECS_IP}/32" via "$GW" dev "$DEV"
ip route get "$ECS_IP"

# 2) scp APK 到下载目录（覆盖同名稳定文件名）。
scp -o ConnectTimeout=15 "$APK_SRC" "ecs-pythontopia:${REMOTE_PATH}"
ssh ecs-pythontopia "chmod 644 ${REMOTE_PATH} && ls -la ${REMOTE_PATH}"

echo "✅ 已上传 → http://${ECS_IP}/downloads/pythontopia.apk"
echo "   注意：guide 页「下载按钮 + 次数统计」还需前后端重新部署到 ECS 才会出现（见提交说明）。"
