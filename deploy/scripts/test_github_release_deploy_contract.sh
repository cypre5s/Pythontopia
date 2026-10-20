#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORKFLOW="${ROOT_DIR}/.github/workflows/publish-ecs-release.yml"
PULLER="${ROOT_DIR}/deploy/scripts/install_github_release.sh"

require_fixed() {
  local file="$1" description="$2" expected="$3"
  if ! grep -Fq -- "$expected" "$file"; then
    echo "[FAIL] ${description}" >&2
    exit 1
  fi
  echo "[PASS] ${description}"
}

[[ -f "$WORKFLOW" ]] || { echo "[FAIL] GitHub release workflow missing" >&2; exit 1; }
[[ -f "$PULLER" ]] || { echo "[FAIL] ECS release pull script missing" >&2; exit 1; }

require_fixed "$WORKFLOW" "发布工作流仅由版本 tag 触发" 'web-v*'
require_fixed "$WORKFLOW" "发布工作流只使用现有 release builder" 'bash deploy/scripts/build_release_archive.sh'
require_fixed "$WORKFLOW" "GitHub runner 不使用本机代理地址" 'PYT_BUILD_PROXY: none'
require_fixed "$WORKFLOW" "GitHub runner 缺少的本地课程媒体校验交由 ECS 预检" 'PYT_BUILD_SKIP_COURSE_RUNTIME_DATA_VALIDATION: "true"'
require_fixed "$WORKFLOW" "发布流程拥有创建 GitHub Release 的权限" 'contents: write'
require_fixed "$WORKFLOW" "发布流程上传可公开下载的 release asset" 'gh release create'
require_fixed "$WORKFLOW" "release asset 小于 GitHub 2 GiB 限制" 'asset_size >= 2147483648'
require_fixed "$PULLER" "ECS 从 GitHub Release 下载 asset" '/releases/download/'
require_fixed "$PULLER" "大文件下载中断后可续传" '--continue-at -'
require_fixed "$PULLER" "续传使用跨进程稳定的临时文件" 'partial="${archive}.part"'
require_fixed "$PULLER" "下载前执行 ECS 预检" 'preflight_ecs.sh'
require_fixed "$PULLER" "下载后复用既有 release 安装器" 'install_release.sh'
require_fixed "$PULLER" "成功安装后移除下载包" 'rm -- "${archive}"'
require_fixed "$ROOT_DIR/deploy/scripts/build_release_archive.sh" "课程媒体校验默认仍开启" 'PYT_BUILD_SKIP_COURSE_RUNTIME_DATA_VALIDATION:-false'
require_fixed "$ROOT_DIR/deploy/README.md" "ECS bootstrap 使用可达的 GitHub API raw endpoint" 'application/vnd.github.raw+json'
require_fixed "$ROOT_DIR/deploy/release/README.md" "发布手册 bootstrap 使用 GitHub API raw endpoint" 'application/vnd.github.raw+json'

python3 - "$PULLER" <<'PY'
from pathlib import Path
import sys

script = Path(sys.argv[1]).read_text()
assert 'partial="${archive}.part"' in script
assert 'trap \'rm -f -- "$partial"\' EXIT' not in script
assert script.index('preflight_ecs.sh') < script.index('curl --fail --location')
assert script.index('curl --fail --location') < script.index('bash "${INSTALLER}"')
assert script.index('bash "${INSTALLER}"') < script.index('rm -- "${archive}"')
print('[PASS] ECS preflight/download/install/cleanup order')
PY

echo "[RESULT] GitHub release deployment contract passed"
