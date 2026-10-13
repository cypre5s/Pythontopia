#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
HELPER="${SCRIPT_DIR}/deepseek_model_contract.sh"
PREFLIGHT="${SCRIPT_DIR}/preflight_ecs.sh"
INSTALLER="${SCRIPT_DIR}/install_release.sh"
PROVIDER="${ROOT_DIR}/services/pythontopia-graph/pythontopia_graph/llm/provider.py"

if ! source "${HELPER}"; then
  echo "[FAIL] DeepSeek model contract helper is missing or cannot be loaded" >&2
  exit 1
fi

assert_model() {
  local purpose="$1" model="$2" expected="$3"
  if deepseek_model_is_supported "${purpose}" "${model}"; then
    actual=0
  else
    actual=1
  fi
  if [ "${actual}" -ne "${expected}" ]; then
    echo "[FAIL] ${purpose} model contract rejected unexpected result for ${model}" >&2
    exit 1
  fi
}

assert_model chat deepseek-flash 0
assert_model chat deepseek-v4-pro 0
assert_model fast deepseek-flash 0
assert_model fast deepseek-v4-pro 0
assert_model fast gpt-4o 1
assert_model vision deepseek-flash 0
assert_model vision deepseek-v4-pro 1
assert_model chat gpt-4o 1
assert_model unknown deepseek-flash 1

for script in "${PREFLIGHT}" "${INSTALLER}"; do
  grep -Fq 'source "${SCRIPT_DIR}/deepseek_model_contract.sh"' "${script}"
  grep -Fq 'deepseek_model_is_supported chat' "${script}"
  grep -Fq 'deepseek_model_is_supported fast' "${script}"
  grep -Fq 'deepseek_model_is_supported vision' "${script}"
  grep -Fq 'PYT_OPENAI_MODEL_FAST' "${script}"
done

# Keep the deployment allowlist synchronized with provider.py's runtime contract.
grep -Fq 'DEEPSEEK_CHAT_MODELS = frozenset({"deepseek-flash", "deepseek-v4-pro"})' "${PROVIDER}"
grep -Fq 'DEEPSEEK_VISION_MODELS = frozenset({"deepseek-flash"})' "${PROVIDER}"

echo "[RESULT] DeepSeek deployment model contract passed"
