#!/usr/bin/env bash
# ==============================================================================
# Pythontopia Web 发布包校验器
# ------------------------------------------------------------------------------
# 用法：
#   bash verify_release_archive.sh <发布包.tar.gz> [allowlist文件]
#
# 校验项（全部通过才退出 0，任一不通过退出 1）：
#   1. 包可读：必须是合法 tar.gz，且不含绝对路径 / ".." 等越界成员；
#   2. 顶层结构：顶层条目必须与 allowlist [toplevel] 完全一致
#      （冻结契约：backend/ deploy/ frontend/ README.md，恰好四项）；
#   3. 违禁内容：包内任何位置不得命中 allowlist [deny] 模式
#      （.env、密钥、node_modules、target、dist、coverage、.git、PDF、视频、
#       生成媒体、clients/、docs/references/ 等）；
#   4. 镜像产物：deploy/runtime/images/ 下四个镜像 tar 齐全、非空，
#      且为 docker save 导出形态（含 manifest.json 或 OCI index.json）；
#   5. 校验和：deploy/runtime/sha256sums.txt 存在，逐文件核对一致，
#      且覆盖包内全部常规文件（防篡改、防夹带）。
#
# allowlist 缺省取脚本同仓库相对位置 ../release/archive-allowlist.txt，
# 发布包内 deploy/scripts 与 deploy/release 保持同样布局，收包方可直接复验。
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

ARCHIVE="${1:-}"
ALLOWLIST="${2:-$SCRIPT_DIR/../release/archive-allowlist.txt}"

# 冻结契约：四个应用镜像在包内的固定路径
REQUIRED_IMAGES=(
  "deploy/runtime/images/frontend.tar"
  "deploy/runtime/images/backend.tar"
  "deploy/runtime/images/graph.tar"
  "deploy/runtime/images/rag.tar"
)
SUMS_PATH="deploy/runtime/sha256sums.txt"

usage() {
  echo "用法：bash $(basename "$0") <发布包.tar.gz> [allowlist文件]" >&2
}

if [[ -z "$ARCHIVE" ]]; then
  usage
  exit 1
fi
if [[ ! -f "$ARCHIVE" ]]; then
  echo "✗ 发布包不存在：$ARCHIVE" >&2
  exit 1
fi
if [[ ! -f "$ALLOWLIST" ]]; then
  echo "✗ 内容契约文件不存在：$ALLOWLIST" >&2
  exit 1
fi

# ---------- 工具函数 ----------

# 把 shell glob 转成扩展正则（ERE）：转义正则元字符，* / ? 不跨路径段
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

# 解析 allowlist：填充 TOPLEVEL / DENY_SEGMENT / DENY_PATH 三个数组
TOPLEVEL=()
DENY_SEGMENT=()
DENY_PATH=()
parse_allowlist() {
  local line section=""
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    # 去掉首尾空白
    line="${line#"${line%%[![:space:]]*}"}"
    line="${line%"${line##*[![:space:]]}"}"
    [[ -z "$line" || "$line" == \#* ]] && continue
    case "$line" in
      '[toplevel]') section="toplevel"; continue ;;
      '[deny]') section="deny"; continue ;;
      \[*\]) echo "✗ allowlist 含未知段：$line" >&2; exit 1 ;;
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
      *) echo "✗ allowlist 段头之前出现内容：$line" >&2; exit 1 ;;
    esac
  done <"$ALLOWLIST"

  if [[ ${#TOPLEVEL[@]} -eq 0 ]]; then
    echo "✗ allowlist [toplevel] 为空，契约文件损坏" >&2
    exit 1
  fi
}
parse_allowlist

# ---------- 临时工作区 ----------
WORK="$(mktemp -d "${TMPDIR:-/tmp}/pyt-verify.XXXXXX")"
cleanup() { rm -rf "$WORK"; }
trap cleanup EXIT

FAIL_COUNT=0
fail() {
  FAIL_COUNT=$((FAIL_COUNT + 1))
  echo "✗ $*"
}
pass() {
  echo "✓ $*"
}

echo "==> 校验发布包：$ARCHIVE"
echo "==> 内容契约：  $ALLOWLIST"
echo

# ---------- 1. 包可读 + 路径安全 ----------
ENTRIES_RAW="$WORK/entries.raw"
ENTRIES="$WORK/entries.txt"
if ! tar -tzf "$ARCHIVE" >"$ENTRIES_RAW" 2>"$WORK/tar-err.log"; then
  fail "无法读取包内容（不是合法 tar.gz？）：$(head -1 "$WORK/tar-err.log" 2>/dev/null)"
  echo
  echo "结论：发布包校验失败（${FAIL_COUNT} 项不通过）。"
  exit 1
fi
# 归一化：去掉前导 ./，丢弃单独的 "." 条目
sed 's|^\./||' "$ENTRIES_RAW" | grep -v -E '^\.?/?$' >"$ENTRIES" || true

if grep -E '^/' "$ENTRIES" >/dev/null 2>&1 \
  || grep -E '(^|/)\.\.(/|$)' "$ENTRIES" >/dev/null 2>&1; then
  fail "包内含绝对路径或 \"..\" 越界成员，存在解包逃逸风险"
else
  pass "包可读，无越界路径成员"
fi

# ---------- 2. 顶层结构 ----------
# 期望集合：去掉目录的尾斜杠后排序
EXPECTED_TOP="$WORK/expected_top.txt"
ACTUAL_TOP="$WORK/actual_top.txt"
for t in "${TOPLEVEL[@]}"; do echo "${t%/}"; done | LC_ALL=C sort -u >"$EXPECTED_TOP"
awk -F/ 'NF { print $1 }' "$ENTRIES" | LC_ALL=C sort -u >"$ACTUAL_TOP"

if cmp -s "$EXPECTED_TOP" "$ACTUAL_TOP"; then
  pass "顶层结构恰好为：$(paste -sd' ' "$EXPECTED_TOP")"
else
  EXTRA="$(comm -13 "$EXPECTED_TOP" "$ACTUAL_TOP" | paste -sd' ' -)"
  MISSING="$(comm -23 "$EXPECTED_TOP" "$ACTUAL_TOP" | paste -sd' ' -)"
  [[ -n "$EXTRA" ]] && fail "顶层出现契约之外的条目：$EXTRA"
  [[ -n "$MISSING" ]] && fail "顶层缺少契约要求的条目：$MISSING"
fi

# 契约中声明为文件的顶层条目（不带 /），不允许在包里是目录
for t in "${TOPLEVEL[@]}"; do
  if [[ "$t" != */ ]]; then
    if grep -E "^$(glob_to_ere "$t")/" "$ENTRIES" >/dev/null 2>&1; then
      fail "顶层 $t 应为文件，但包内是目录"
    fi
  fi
done

# 根 README.md 单独点名（部署入口文档，缺了不可用）
if grep -x 'README.md' "$ENTRIES" >/dev/null 2>&1; then
  pass "根 README.md 存在"
else
  fail "缺少根 README.md"
fi

# ---------- 3. 违禁内容扫描 ----------
SEG_ALT=""
for p in "${DENY_SEGMENT[@]}"; do
  SEG_ALT+="${SEG_ALT:+|}$(glob_to_ere "$p")"
done
PATH_ALT=""
for p in "${DENY_PATH[@]}"; do
  PATH_ALT+="${PATH_ALT:+|}$(glob_to_ere "$p")"
done

DENY_HITS="$WORK/deny_hits.txt"
: >"$DENY_HITS"
if [[ -n "$SEG_ALT" ]]; then
  grep -E "(^|/)(${SEG_ALT})(/|$)" "$ENTRIES" >>"$DENY_HITS" || true
fi
if [[ -n "$PATH_ALT" ]]; then
  grep -E "^(${PATH_ALT})(/|$)" "$ENTRIES" >>"$DENY_HITS" || true
fi

if [[ -s "$DENY_HITS" ]]; then
  HIT_TOTAL="$(sort -u "$DENY_HITS" | wc -l | tr -d ' ')"
  fail "命中违禁内容 ${HIT_TOTAL} 条（.env/密钥/构建产物/媒体/多端目录等），示例："
  sort -u "$DENY_HITS" | head -20 | sed 's/^/    ✗ /'
  [[ "$HIT_TOTAL" -gt 20 ]] && echo "    …（其余 $((HIT_TOTAL - 20)) 条略）"
else
  pass "违禁内容扫描通过（${#DENY_SEGMENT[@]} 个名称模式 + ${#DENY_PATH[@]} 个路径模式）"
fi

# ---------- 4. 镜像产物清单 ----------
MISSING_IMAGES=()
for img in "${REQUIRED_IMAGES[@]}"; do
  if ! grep -x "$img" "$ENTRIES" >/dev/null 2>&1; then
    MISSING_IMAGES+=("$img")
  fi
done
if [[ ${#MISSING_IMAGES[@]} -gt 0 ]]; then
  fail "缺少应用镜像 tar：${MISSING_IMAGES[*]}"
else
  pass "四个应用镜像 tar 均在 deploy/runtime/images/ 下"
fi

if ! grep -x "$SUMS_PATH" "$ENTRIES" >/dev/null 2>&1; then
  fail "缺少校验和清单 $SUMS_PATH"
fi

# ---------- 5. 解包核对校验和与镜像形态 ----------
# 前面已有失败时仍继续解包核对，尽量一次报全所有问题；
# 但若包本身越界则跳过解包，避免逃逸。
if grep -E '^/' "$ENTRIES" >/dev/null 2>&1 \
  || grep -E '(^|/)\.\.(/|$)' "$ENTRIES" >/dev/null 2>&1; then
  fail "因存在越界路径，跳过解包核对"
else
  EXTRACT="$WORK/extract"
  mkdir -p "$EXTRACT"
  if ! tar -xzf "$ARCHIVE" -C "$EXTRACT" 2>"$WORK/extract-err.log"; then
    fail "解包失败：$(head -1 "$WORK/extract-err.log" 2>/dev/null)"
  else
    # 5a. sha256sums.txt 逐文件核对
    if [[ -f "$EXTRACT/$SUMS_PATH" ]]; then
      if (cd "$EXTRACT" && sha256sum -c "$SUMS_PATH" >"$WORK/sums-out.log" 2>&1); then
        pass "sha256sums.txt 逐文件核对一致（$(wc -l <"$EXTRACT/$SUMS_PATH" | tr -d ' ') 个文件）"
      else
        fail "sha256sums.txt 与实际内容不符，差异示例："
        grep -v ': OK$' "$WORK/sums-out.log" | head -10 | sed 's/^/    ✗ /'
      fi

      # 5b. 覆盖率核对：包内每个常规文件都必须列入清单（防夹带未登记文件）
      (cd "$EXTRACT" && find . -type f ! -path "./$SUMS_PATH" | sed 's|^\./||' | LC_ALL=C sort) >"$WORK/actual_files.txt"
      cut -c67- "$EXTRACT/$SUMS_PATH" | LC_ALL=C sort >"$WORK/listed_files.txt"
      UNLISTED="$(comm -23 "$WORK/actual_files.txt" "$WORK/listed_files.txt" || true)"
      if [[ -n "$UNLISTED" ]]; then
        fail "以下文件未列入 sha256sums.txt（疑似夹带）："
        echo "$UNLISTED" | head -10 | sed 's/^/    ✗ /'
      else
        pass "sha256sums.txt 覆盖包内全部常规文件"
      fi
    else
      fail "解包后未找到 $SUMS_PATH，无法核对校验和"
    fi

    # 5c. 镜像 tar 形态核对：非空，且形如 docker save 导出（classic 或 OCI 布局）
    IMG_BAD=0
    for img in "${REQUIRED_IMAGES[@]}"; do
      f="$EXTRACT/$img"
      [[ -f "$f" ]] || continue # 缺失已在第 4 步报告
      if [[ ! -s "$f" ]]; then
        fail "镜像 tar 为空文件：$img"
        IMG_BAD=1
        continue
      fi
      if ! tar -tf "$f" >"$WORK/img-list.txt" 2>/dev/null; then
        fail "镜像 tar 不可读（非 tar 格式）：$img"
        IMG_BAD=1
        continue
      fi
      if ! grep -qxE '(manifest\.json|index\.json|oci-layout)' "$WORK/img-list.txt"; then
        fail "镜像 tar 缺少 manifest.json / index.json，不是 docker save 导出产物：$img"
        IMG_BAD=1
      fi
    done
    if [[ $IMG_BAD -eq 0 && ${#MISSING_IMAGES[@]} -eq 0 ]]; then
      pass "镜像 tar 形态核对通过（docker save 导出格式）"
    fi
  fi
fi

# ---------- 结论 ----------
echo
if [[ $FAIL_COUNT -eq 0 ]]; then
  echo "结论：发布包校验通过，可进入分发/部署环节。"
  exit 0
else
  echo "结论：发布包校验失败（${FAIL_COUNT} 项不通过），禁止分发。"
  exit 1
fi
