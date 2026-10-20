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
ROLLBACK="${ROOT_DIR}/deploy/scripts/rollback_release.sh"
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
  "生产判题固定使用内网 Piston 服务" \
  'PYT_PISTON_URL: http://piston:2000'
require_fixed "${COMPOSE_FILE}" \
  "生产判题指定 Piston Python 版本默认值" \
  'PYT_PISTON_PYTHON_VERSION: ${PYT_PISTON_PYTHON_VERSION:-3.11.0}'
require_fixed "${COMPOSE_FILE}" \
  "生产编排使用发布包内固定 Piston 镜像" \
  'image: pythontopia/piston:release'
require_fixed "${COMPOSE_FILE}" \
  "Piston 执行服务不发布宿主端口" \
  'networks: [judge-execution]'
require_fixed "${COMPOSE_FILE}" \
  "首次启动自动安装并持久化 Python runtime" \
  'piston-runtime-init:'
require_fixed "${COMPOSE_FILE}" \
  "Piston runner 禁止用户代码联网" \
  'PISTON_DISABLE_NETWORKING: "true"'
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
require_fixed "${COMPOSE_FILE}" \
  "安装时等待 Piston Python runtime 初始化成功" \
  'service_completed_successfully'
require_fixed "${INSTALLER}" \
  "发布包包含 Piston 执行镜像" \
  'IMAGE_COMPONENTS=(frontend backend graph rag piston)'
require_fixed "${ROLLBACK}" \
  "回滚到不含 Piston 的旧 release 保持兼容" \
  "if grep -Fq 'image: pythontopia/piston:release'"
require_fixed "${ROLLBACK}" \
  "回滚新 release 时恢复 Piston 镜像" \
  'IMAGE_COMPONENTS+=(piston)'
require_fixed "${RELEASE_README}" \
  "发布文档禁止 dryrun" \
  '禁止 dryrun'
require_fixed "${ENV_EXAMPLE}" \
  ".env 样板声明 subprocess 仅限开发测试" \
  'subprocess 仅限本地开发/测试；生产 Compose 固定覆盖为 piston'
require_fixed "${RELEASE_README}" \
  "发布文档声明 Piston 绝不回退 subprocess" \
  '绝不回退 `subprocess`'
require_fixed "${RELEASE_README}" \
  "发布文档说明内置 Piston 的网络隔离边界" \
  'Piston API v3.1.0 不校验 Bearer token'
require_fixed "${ENV_EXAMPLE}" \
  ".env 样板指定 DeepSeek 文本模型" \
  'PYT_OPENAI_MODEL=deepseek-v4-pro'
require_fixed "${ENV_EXAMPLE}" \
  ".env 样板指定 DeepSeek 视觉模型" \
  'PYT_OPENAI_MODEL_VISION=deepseek-flash'
require_fixed "${COMPOSE_FILE}" \
  "Backend 固定挂载逐视频转写文件" \
  'PYT_COURSEPACK_VIDEO_TRANSCRIPTS: /data/course-pack-43/video_transcripts.json'
require_fixed "${COMPOSE_FILE}" \
  "RAG 固定挂载视频清单只读路径" \
  'PYT_COURSEPACK_VIDEO_MANIFEST: /data/course-pack-43/video_manifest.json'
require_fixed "${COMPOSE_FILE}" \
  "RAG 只读挂载共享课程数据目录" \
  '${PYT_SHARED_DATA_DIR:-/opt/pythontopia/shared/data}/course-pack-43:/data/course-pack-43:ro'
require_fixed "${PREFLIGHT}" \
  "ECS 预检执行课程 PDF/视频/字幕来源校验" \
  'validate_course_runtime_data.py" "${COURSE_DATA_ROOT}'
require_fixed "${INSTALLER}" \
  "安装前先校验包内候选字幕与共享 MP4" \
  '--transcript "${TRANSCRIPT_CANDIDATE}"'
require_fixed "${RELEASE_BUILDER}" \
  "默认发布构建前拒绝不完整课程运行时数据" \
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
