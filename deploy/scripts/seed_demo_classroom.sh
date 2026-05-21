#!/usr/bin/env bash
# Pythontopia: 创建演示班级、教师、3 名学生与 1 个班级任务。
# 演示数据走真实 API，不写 SQL seed，可幂等重跑。
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ -f .env ]]; then
  set -a
  # shellcheck source=/dev/null
  source .env
  set +a
fi

API_BASE="${PYT_API_BASE:-http://127.0.0.1:18080}"
INVITE="${PYT_TEACHER_INVITE_CODE:-pytopia-teacher}"

require_python() {
  command -v python3 >/dev/null 2>&1 || {
    echo "[seed_demo] 需要 python3" >&2
    exit 1
  }
}

require_python
PYTHONIOENCODING=utf-8 \
PYT_API_BASE="$API_BASE" \
PYT_TEACHER_INVITE_CODE="$INVITE" \
python3 "$(dirname "$0")/../../data/demo/seed.py"
