#!/usr/bin/env bash
# ==============================================================================
# Pythontopia Web 发布包打包器
# ------------------------------------------------------------------------------
# 用法：
#   bash deploy/scripts/build_release_archive.sh
#
# 流程（冻结契约见 deploy/release/archive-allowlist.txt）：
#   1. docker build 四个应用镜像，按固定名打 tag：
#        pythontopia/frontend:release   ← frontend/Dockerfile
#        pythontopia/backend:release    ← backend/Dockerfile
#        pythontopia/graph:release      ← services/pythontopia-graph/Dockerfile
#        pythontopia/rag:release        ← services/pythontopia-rag/Dockerfile
#   2. docker save 导出到暂存区 deploy/runtime/images/{frontend,backend,graph,rag}.tar；
#   3. 只暂存 frontend/、backend/、deploy/ 三棵源码树，
#      根 README.md 由 deploy/release/README.md 拷贝而来；
#   4. 按 allowlist [deny] 清理本地专用文件（node_modules、target、.env、pem、
#      qa-screens、e2e 报告、课件 PDF/视频 等），并做一遍违禁内容自审；
#   5. 生成 deploy/runtime/sha256sums.txt（覆盖包内全部常规文件）；
#   6. 打包到 output/packages/pythontopia-web-<UTC时间戳>.tar.gz；
#   7. 自动调用 verify_release_archive.sh 复验，复验不过即失败退出。
#
# 环境变量：
#   PYT_BUILD_PROXY  构建代理，默认 http://127.0.0.1:7892（与 build-with-proxy.sh
#                    一致；脚本会同时注入 HTTP(S)_PROXY 和 Maven JVM 代理参数，
#                    因此构建始终使用 --network host）。设为 none 表示不注入代理
#                    build-arg（仅适用于容器内可直连外网的环境）。
#   TMPDIR           暂存目录所在盘（镜像 tar 体积大，默认 /tmp，可指向大盘）。
#
# 安全性：全程使用 mktemp 暂存目录 + trap 清理，绝不改动源码目录本身；
#         产物只落在 output/packages/ 下。本脚本不做任何 git 操作。
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ALLOWLIST="$ROOT/deploy/release/archive-allowlist.txt"
RELEASE_README="$ROOT/deploy/release/README.md"
VERIFIER="$SCRIPT_DIR/verify_release_archive.sh"

# 冻结契约：短名 → 构建上下文（镜像名 = pythontopia/<短名>:release，
# 导出文件 = deploy/runtime/images/<短名>.tar）
IMAGE_SPECS=(
  "frontend|$ROOT/frontend"
  "backend|$ROOT/backend"
  "graph|$ROOT/services/pythontopia-graph"
  "rag|$ROOT/services/pythontopia-rag"
)

die() {
  echo "✗ $*" >&2
  exit 1
}

# ---------- 0. 前置检查 ----------
command -v docker >/dev/null 2>&1 || die "未安装 docker，无法构建镜像"
docker info >/dev/null 2>&1 || die "docker daemon 不可用，请先启动 Docker"
command -v rsync >/dev/null 2>&1 || die "未安装 rsync（用于暂存源码树），请先 apt install rsync"
command -v sha256sum >/dev/null 2>&1 || die "缺少 sha256sum"
[[ -f "$ALLOWLIST" ]] || die "内容契约文件缺失：$ALLOWLIST"
[[ -f "$RELEASE_README" ]] || die "发布包根 README 源文件缺失：$RELEASE_README"
[[ -f "$VERIFIER" ]] || die "校验脚本缺失：$VERIFIER"
for spec in "${IMAGE_SPECS[@]}"; do
  ctx="${spec#*|}"
  [[ -f "$ctx/Dockerfile" ]] || die "Dockerfile 缺失：$ctx/Dockerfile"
done

# ---------- 解析 allowlist（与 verify_release_archive.sh 同一契约格式）----------
glob_to_ere() {
  local g="$1" out="" ch i
  for ((i = 0; i < ${#g}; i++)); do
    ch="${g:i:1}"
    case "$ch" in
      '*') out+='[^/]*' ;;
      '?') out+='[^/]' ;;
      '.' | '[' | ']' | '(' | ')' | '{' | '}' | '^' | '$' | '+' | '|' | '\\') out+="\\$ch" ;;
      *) out+="$ch" ;;
    esac
  done
  printf '%s' "$out"
}

TOPLEVEL=()
DENY_SEGMENT=()
DENY_PATH=()
section=""
while IFS= read -r line || [[ -n "$line" ]]; do
  line="${line%$'\r'}"
  line="${line#"${line%%[![:space:]]*}"}"
  line="${line%"${line##*[![:space:]]}"}"
  [[ -z "$line" || "$line" == \#* ]] && continue
  case "$line" in
    '[toplevel]') section="toplevel"; continue ;;
    '[deny]') section="deny"; continue ;;
    \[*\]) die "allowlist 含未知段：$line" ;;
  esac
  case "$section" in
    toplevel) TOPLEVEL+=("$line") ;;
    deny)
      if [[ "$line" == */* ]]; then
        DENY_PATH+=("${line%/}")
      else
        DENY_SEGMENT+=("$line")
      fi
      ;;
    *) die "allowlist 段头之前出现内容：$line" ;;
  esac
done <"$ALLOWLIST"
[[ ${#TOPLEVEL[@]} -gt 0 ]] || die "allowlist [toplevel] 为空"

# 需要整棵暂存的源码树 = [toplevel] 里以 / 结尾的目录项
TREES=()
for t in "${TOPLEVEL[@]}"; do
  [[ "$t" == */ ]] && TREES+=("${t%/}")
done

# ---------- 1. 构建代理探测 ----------
PROXY="${PYT_BUILD_PROXY:-http://127.0.0.1:7892}"
BUILD_ARGS=()
if [[ "$PROXY" != "none" ]]; then
  if ! curl -sf --connect-timeout 3 -x "$PROXY" https://registry.npmmirror.com/ -o /dev/null; then
    die "构建代理 $PROXY 不可用。请先启动本机代理（默认 7892），或设 PYT_BUILD_PROXY=<代理地址>；容器内可直连外网时可设 PYT_BUILD_PROXY=none（注意 backend 的 Maven 步骤仍硬编码 127.0.0.1:7892）。"
  fi
  BUILD_ARGS=(
    --build-arg "HTTP_PROXY=$PROXY"
    --build-arg "HTTPS_PROXY=$PROXY"
    --build-arg "NO_PROXY=localhost,127.0.0.1"
  )
  PROXY_ENDPOINT="${PROXY#*://}"
  PROXY_HOST="${PROXY_ENDPOINT%%:*}"
  PROXY_PORT="${PROXY_ENDPOINT##*:}"
  BUILD_ARGS+=(
    --build-arg "MAVEN_PROXY_OPTS=-Dhttp.proxyHost=${PROXY_HOST} -Dhttp.proxyPort=${PROXY_PORT} -Dhttps.proxyHost=${PROXY_HOST} -Dhttps.proxyPort=${PROXY_PORT}"
  )
  echo "==> 构建代理：$PROXY（build-arg 注入，host 网络）"
else
  echo "==> 构建代理：none（不注入代理 build-arg）"
fi
# 勿向 docker 客户端环境导出代理：拉取基础镜像走 daemon 自身配置即可
unset HTTP_PROXY HTTPS_PROXY http_proxy https_proxy ALL_PROXY all_proxy NO_PROXY no_proxy || true
# backend Dockerfile 使用 RUN --mount=type=cache，需要 BuildKit
export DOCKER_BUILDKIT=1

# ---------- 2. 构建四个镜像（冻结 tag）----------
echo "==> [1/7] docker build 四个应用镜像"
for spec in "${IMAGE_SPECS[@]}"; do
  short="${spec%%|*}"
  ctx="${spec#*|}"
  image="pythontopia/${short}:release"
  echo "    -> 构建 $image（上下文 $ctx）"
  docker build --network host "${BUILD_ARGS[@]+"${BUILD_ARGS[@]}"}" \
    -t "$image" -f "$ctx/Dockerfile" "$ctx" \
    || die "镜像构建失败：$image"
done

# ---------- 3. 暂存区（mktemp + trap，绝不写源码树）----------
STAGE="$(mktemp -d "${TMPDIR:-/tmp}/pythontopia-release.XXXXXX")"
cleanup() { rm -rf "$STAGE"; }
trap cleanup EXIT
echo "==> 暂存目录：$STAGE"

echo "==> [2/7] 暂存源码树：${TREES[*]}（按 allowlist 预排除）"
for tree in "${TREES[@]}"; do
  [[ -d "$ROOT/$tree" ]] || die "源码树缺失：$ROOT/$tree"
  EXCLUDES=()
  for p in "${DENY_SEGMENT[@]}"; do
    EXCLUDES+=(--exclude "$p")
  done
  for p in "${DENY_PATH[@]}"; do
    # 形如 backend/data 的路径模式，仅作用于对应的树，锚定到树根
    if [[ "$p" == "$tree/"* ]]; then
      EXCLUDES+=(--exclude "/${p#"$tree"/}")
    fi
  done
  mkdir -p "$STAGE/$tree"
  rsync -a "${EXCLUDES[@]}" "$ROOT/$tree/" "$STAGE/$tree/"
done

echo "==> [3/7] docker save 导出镜像到 deploy/runtime/images/"
IMAGES_DIR="$STAGE/deploy/runtime/images"
mkdir -p "$IMAGES_DIR"
for spec in "${IMAGE_SPECS[@]}"; do
  short="${spec%%|*}"
  image="pythontopia/${short}:release"
  echo "    -> 导出 $image -> deploy/runtime/images/${short}.tar"
  docker save -o "$IMAGES_DIR/${short}.tar" "$image" || die "镜像导出失败：$image"
done

# 根 README.md 由发布说明拷贝而来
cp "$RELEASE_README" "$STAGE/README.md"

# ---------- 4. 按 allowlist 清理 + 自审 ----------
echo "==> [4/7] 清理本地专用文件并自审"
# 4a. 名称模式：匹配任意层级同名文件/目录（rsync 预排除的兜底）
for p in "${DENY_SEGMENT[@]}"; do
  find "$STAGE" -depth -mindepth 1 -name "$p" -exec rm -rf {} + 2>/dev/null || true
done
# 4b. 路径模式：锚定到暂存区根
shopt -s nullglob
for p in "${DENY_PATH[@]}"; do
  for f in "$STAGE"/$p; do
    rm -rf "$f"
  done
done
shopt -u nullglob

# 4c. 自审一：暂存区顶层必须与契约完全一致
EXPECTED_TOP="$(for t in "${TOPLEVEL[@]}"; do echo "${t%/}"; done | LC_ALL=C sort -u)"
ACTUAL_TOP="$(ls -A "$STAGE" | LC_ALL=C sort -u)"
if [[ "$EXPECTED_TOP" != "$ACTUAL_TOP" ]]; then
  echo "期望顶层：" && echo "$EXPECTED_TOP" | sed 's/^/    /'
  echo "实际顶层：" && echo "$ACTUAL_TOP" | sed 's/^/    /'
  die "暂存区顶层与冻结契约不一致，中止打包"
fi

# 4d. 自审二：违禁模式复扫（与校验器同一正则口径）
SEG_ALT=""
for p in "${DENY_SEGMENT[@]}"; do SEG_ALT+="${SEG_ALT:+|}$(glob_to_ere "$p")"; done
PATH_ALT=""
for p in "${DENY_PATH[@]}"; do PATH_ALT+="${PATH_ALT:+|}$(glob_to_ere "$p")"; done
LISTING="$(cd "$STAGE" && find . -mindepth 1 | sed 's|^\./||')"
HITS=""
if [[ -n "$SEG_ALT" ]]; then
  HITS+="$(echo "$LISTING" | grep -E "(^|/)(${SEG_ALT})(/|\$)" || true)"
fi
if [[ -n "$PATH_ALT" ]]; then
  HITS+="$(echo "$LISTING" | grep -E "^(${PATH_ALT})(/|\$)" || true)"
fi
if [[ -n "$HITS" ]]; then
  echo "$HITS" | head -20 | sed 's/^/    ✗ /'
  die "清理后仍残留违禁内容，中止打包"
fi

# 4e. 自审三：符号链接不得指向暂存区之外（防带出仓库本地路径/悬空链接）
while IFS= read -r link; do
  [[ -z "$link" ]] && continue
  resolved="$(realpath -m "$link")"
  case "$resolved" in
    "$STAGE"/*) ;;
    *) die "符号链接指向暂存区之外，发布包不可携带：$link -> $(readlink "$link")" ;;
  esac
done < <(find "$STAGE" -type l)

# 4f. 自审四：文件名不得含换行/反斜杠（会破坏 sha256sums 清单格式）
BAD_NAMES="$(find "$STAGE" \( -name '*\\*' -o -name $'*\n*' \) -print 2>/dev/null | head -5 || true)"
if [[ -n "$BAD_NAMES" ]]; then
  echo "$BAD_NAMES" | sed 's/^/    ✗ /'
  die "存在含反斜杠或换行的文件名，无法生成可靠校验和清单"
fi

# ---------- 5. 生成 sha256sums.txt ----------
echo "==> [5/7] 生成 deploy/runtime/sha256sums.txt"
SUMS_TMP="$STAGE/.sha256sums.tmp"
(
  cd "$STAGE"
  find . -type f ! -name '.sha256sums.tmp' -print0 | sed -z 's|^\./||' \
    | LC_ALL=C sort -z | xargs -0 sha256sum
) >"$SUMS_TMP"
mv "$SUMS_TMP" "$STAGE/deploy/runtime/sha256sums.txt"
# 清单自身不列入清单；校验时以解包后实际文件为准逐一核对
echo "    -> 共 $(wc -l <"$STAGE/deploy/runtime/sha256sums.txt" | tr -d ' ') 个文件"

# ---------- 6. 打 tar.gz ----------
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT_DIR="$ROOT/output/packages"
mkdir -p "$OUT_DIR"
ARCHIVE="$OUT_DIR/pythontopia-web-${STAMP}.tar.gz"
echo "==> [6/7] 打包：$ARCHIVE"
TOP_ITEMS=()
for t in "${TOPLEVEL[@]}"; do TOP_ITEMS+=("${t%/}"); done
tar --sort=name --owner=0 --group=0 --numeric-owner \
  -czf "$ARCHIVE" -C "$STAGE" "${TOP_ITEMS[@]}"

# ---------- 7. 自动复验 ----------
echo "==> [7/7] 调用校验器复验"
if ! bash "$VERIFIER" "$ARCHIVE" "$ALLOWLIST"; then
  mv "$ARCHIVE" "${ARCHIVE}.rejected"
  die "复验未通过，产物已改名为 ${ARCHIVE}.rejected，禁止分发"
fi

echo
echo "==> 发布包构建完成："
ls -lh "$ARCHIVE" | awk '{print "    大小：" $5 "  路径：" $NF}'
echo "    包指纹：$(sha256sum "$ARCHIVE" | cut -c1-64)"
echo "    （收包方可用 deploy/scripts/verify_release_archive.sh 复验）"
