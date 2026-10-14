#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/deploy/docker-compose.production.yml"
PREFLIGHT="${ROOT_DIR}/deploy/scripts/preflight_ecs.sh"
INSTALLER="${ROOT_DIR}/deploy/scripts/install_release.sh"
ENV_EXAMPLE="${ROOT_DIR}/deploy/.env.example"
RELEASE_README="${ROOT_DIR}/deploy/release/README.md"
RELEASE_BUILDER="${ROOT_DIR}/deploy/scripts/build_release_archive.sh"
FAILURES=0

require_fixed() {
  local file="$1"
  local description="$2"
  local expected="$3"
  if grep -Fq -- "${expected}" "${file}"; then
    echo "[PASS] ${description}"
  else
    echo "[FAIL] ${description}" >&2
    FAILURES=$((FAILURES + 1))
  fi
}

require_absent() {
  local file="$1"
  local description="$2"
  local unexpected="$3"
  if grep -Fq -- "${unexpected}" "${file}"; then
    echo "[FAIL] ${description}" >&2
    FAILURES=$((FAILURES + 1))
  else
    echo "[PASS] ${description}"
  fi
}

require_fixed "${COMPOSE_FILE}" \
  "Backend 生产健康检查使用 readiness" \
  'curl -fsS http://127.0.0.1:18080/readyz'
require_fixed "${COMPOSE_FILE}" \
  "生产判题固定使用 Piston" \
  'PYT_JUDGE_PROVIDER: piston'
require_fixed "${COMPOSE_FILE}" \
  "生产判题强制 Piston URL" \
  'PYT_PISTON_URL: ${PYT_PISTON_URL:?缺少 PYT_PISTON_URL}'
require_fixed "${COMPOSE_FILE}" \
  "生产判题强制 Piston Python 版本" \
  'PYT_PISTON_PYTHON_VERSION: ${PYT_PISTON_PYTHON_VERSION:?缺少 PYT_PISTON_PYTHON_VERSION}'
require_fixed "${COMPOSE_FILE}" \
  "生产判题强制 Piston 鉴权令牌" \
  'PYT_PISTON_AUTH_TOKEN: ${PYT_PISTON_AUTH_TOKEN:?缺少 PYT_PISTON_AUTH_TOKEN}'
require_fixed "${COMPOSE_FILE}" \
  "Graph 生产健康检查使用 readiness" \
  'curl -fsS http://127.0.0.1:18091/readyz'
require_fixed "${COMPOSE_FILE}" \
  "生产 Graph 强制关闭可选内部鉴权" \
  'PYT_INTERNAL_AUTH_OPTIONAL: "0"'
require_fixed "${COMPOSE_FILE}" \
  "RAG 生产健康检查使用 readiness" \
  'curl -fsS http://127.0.0.1:18092/readyz'
require_fixed "${COMPOSE_FILE}" \
  "生产必须显式选择 LLM provider" \
  'PYT_LLM_PROVIDER: ${PYT_LLM_PROVIDER:?缺少 PYT_LLM_PROVIDER}'
require_fixed "${COMPOSE_FILE}" \
  "生产强制启用 AI 日限额" \
  'PYT_AI_REQUEST_LIMITS_ENABLED: "true"'
require_fixed "${COMPOSE_FILE}" \
  "生产 RAG 固定使用本地 hash 向量" \
  'PYT_RAG_EMBEDDER: hash'
require_fixed "${PREFLIGHT}" \
  "部署门禁限定 DeepSeek API 地址" \
  'https://api.deepseek.com'
require_fixed "${PREFLIGHT}" \
  "部署门禁要求视觉模型配置" \
  'PYT_OPENAI_MODEL_VISION'
require_absent "${COMPOSE_FILE}" \
  "生产编排不再配置 Spark API" \
  'PYT_SPARK_APP_ID'
require_absent "${COMPOSE_FILE}" \
  "生产编排不再配置外部 embedding API" \
  'EMBEDDING_API_KEY'
require_fixed "${PREFLIGHT}" \
  "ECS 预检执行生产 AI 配置门禁" \
  'validate_production_ai_env "${ENV_FILE}"'
require_fixed "${PREFLIGHT}" \
  "ECS 预检执行生产判题配置门禁" \
  'validate_production_judge_env "${ENV_FILE}"'
require_fixed "${INSTALLER}" \
  "安装前执行生产 AI 配置门禁" \
  'validate_production_ai_env "${ENV_FILE}"'
require_fixed "${INSTALLER}" \
  "安装前执行生产判题配置门禁" \
  'validate_production_judge_env "${ENV_FILE}"'
require_fixed "${PREFLIGHT}" \
  "ECS 预检拒绝公开开发数据库密码" \
  'pytopia_dev_password'
require_fixed "${INSTALLER}" \
  "安装拒绝公开开发数据库密码" \
  'pytopia_dev_password'
require_fixed "${PREFLIGHT}" \
  "ECS 预检对数据库密码执行值校验" \
  'require_env_value "${ENV_FILE}" PYT_DB_PASSWORD'
require_fixed "${INSTALLER}" \
  "安装前对数据库密码执行值校验" \
  'require_env_value "${ENV_FILE}" PYT_DB_PASSWORD'
require_fixed "${PREFLIGHT}" \
  "ECS 预检强制 Piston token 至少 32 字符" \
  'check_secret_strength PYT_PISTON_AUTH_TOKEN 32 fail "${env_file}"'
require_fixed "${INSTALLER}" \
  "安装强制 Piston token 至少 32 字符" \
  'check_env_secret PYT_PISTON_AUTH_TOKEN 32 "${env_file}"'
require_fixed "${PREFLIGHT}" \
  "ECS 预检拒绝外部 Piston 明文 HTTP" \
  '外部 Piston 地址必须使用 HTTPS'
require_fixed "${INSTALLER}" \
  "安装拒绝外部 Piston 明文 HTTP" \
  '外部 Piston 地址必须使用 HTTPS'
require_fixed "${RELEASE_README}" \
  "发布文档禁止 dryrun" \
  '禁止 dryrun'
require_fixed "${ENV_EXAMPLE}" \
  ".env 样板声明 subprocess 仅限开发测试" \
  'subprocess 仅限本地开发/测试，生产预检与安装会拒绝'
require_fixed "${RELEASE_README}" \
  "发布文档声明 Piston 绝不回退 subprocess" \
  '绝不回退 `subprocess`'
require_fixed "${RELEASE_README}" \
  "发布文档要求外部 Piston 使用 HTTPS" \
  '公网/跨主机 URL 必须用 HTTPS'
require_fixed "${ENV_EXAMPLE}" \
  ".env 样板指定 DeepSeek 文本模型" \
  'PYT_OPENAI_MODEL=deepseek-v4-pro'
require_fixed "${ENV_EXAMPLE}" \
  ".env 样板指定 DeepSeek 视觉模型" \
  'PYT_OPENAI_MODEL_VISION=deepseek-flash'
require_fixed "${COMPOSE_FILE}" \
  "Backend 固定挂载逐视频转写文件" \
  'PYT_COURSEPACK_VIDEO_TRANSCRIPTS: /data/course-pack-43/video_transcripts.json'
require_fixed "${PREFLIGHT}" \
  "ECS 预检执行课程 PDF/视频/字幕来源校验" \
  'validate_course_runtime_data.py" "${COURSE_DATA_ROOT}'
require_fixed "${INSTALLER}" \
  "安装前先校验包内候选字幕与共享 MP4" \
  '--transcript "${TRANSCRIPT_CANDIDATE}"'
require_fixed "${RELEASE_BUILDER}" \
  "发布构建前拒绝不完整课程运行时数据" \
  'python3 "$COURSE_VALIDATOR" "$COURSE_DATA_ROOT"'
require_fixed "${RELEASE_BUILDER}" \
  "发布包携带版本化视频字幕" \
  'deploy/course-data/video_transcripts.json'
require_fixed "${RELEASE_README}" \
  "发布文档声明字幕与源视频哈希门禁" \
  '每个 MP4 的 SHA-256'

if [ "${FAILURES}" -ne 0 ]; then
  echo "[RESULT] ${FAILURES} 项生产 AI/readiness 契约未满足" >&2
  exit 1
fi

echo "[RESULT] 生产 AI/readiness 契约通过"
