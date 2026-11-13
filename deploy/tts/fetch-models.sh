#!/usr/bin/env bash
# 只拉最小模型集合到命名卷，不拉整个 9.3GB 的 gguf/。
# 在 master 上执行（需要 huggingface_hub 的 hf CLI）：
#   pipx install huggingface_hub            # 或用你已有的 hf
#   bash deploy/tts/fetch-models.sh
#
# 幂等：已存在且大小正确的文件跳过；不覆盖、不删除。
set -euo pipefail

REPO="Zyphra/ZONOS3-Mini"
DEST="${DEST:-/srv/pythontopia-tts/models}"
VOLUME="${VOLUME:-}"

usage() { echo "usage: DEST=/srv/... bash $0   (或 VOLUME=pythontopia-reborn-tts-models 走 docker run -v)"; }

# 必需 + 可选（克隆/语气控制）
REQUIRED=(
  "gguf/backbone-ptq.gguf"     # 0.66 GB 骨干（Q4_0 experts / Q8_0 mixers）
  "gguf/zac-keep2.gguf"        # 默认解码器
  "gguf/spk-encoder.gguf"      # 说话人编码器（默认音色已带 embedding，可不装；先拉上备用）
)
OPTIONAL=(
  "gguf/steer.gguf"            # 语气/情绪/语速 controls 需要
)
VOICES_INCLUDE='gguf/voices/*'   # 27 个默认音色 + .codes.npy / .embedding.npy

command -v hf >/dev/null 2>&1 || { echo "缺少 hf CLI：pipx install huggingface_hub（或 pip install 'huggingface_hub[cli]'）"; exit 1; }

mkdir -p "$DEST"
echo "== 目标目录：$DEST"

fetch() { # fetch <path>
  echo "-- $1"
  hf download "$REPO" "$1" --local-dir "$DEST" >/dev/null
}

for item in "${REQUIRED[@]}"; do fetch "$item"; done
if [[ "${WITH_OPTIONAL:-}" == "1" ]]; then
  for item in "${OPTIONAL[@]}"; do fetch "$item"; done
  echo "-- $VOICES_INCLUDE"
  hf download "$REPO" --include "$VOICES_INCLUDE" --local-dir "$DEST" >/dev/null
fi

# 基本校验：骨干必须 ~0.66GB 量级，太小说明下载被截断
size=$(stat -c %s "$DEST/gguf/backbone-ptq.gguf")
if (( size < 600000000 )); then
  echo "错误：backbone-ptq.gguf 只有 $size 字节，疑似下载不完整"; exit 1
fi

echo
echo "== 完成。目录占用："
du -sh "$DEST"/* 2>/dev/null || du -sh "$DEST"
echo
echo "可选：WITH_OPTIONAL=1 bash $0   # 再拉 steer.gguf 与 voices/"
