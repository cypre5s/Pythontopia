#!/usr/bin/env bash
# Ensure the public "quick experience" account matches the credentials shown by LoginPage.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_PROJECT="${PYT_COMPOSE_PROJECT:-deploy}"
COMPOSE_FILE="${PYT_COMPOSE_FILE:-${DEPLOY_DIR}/docker-compose.production.yml}"
PUBLIC_USERNAME="demo-stu-01"
PUBLIC_DISPLAY_NAME="演示学生 Alice"
# BCrypt(10) for the intentionally public quick-experience password P@ssw0rd1.
PUBLIC_PASSWORD_HASH='$2b$10$miHbw29MDm0KeFu09ZgSKefF2nB1/PyxC9KTpTX4vmeY5k0OmekSq'

if [[ ! -f "${COMPOSE_FILE}" ]]; then
  echo "[public_demo] Compose 文件不存在：${COMPOSE_FILE}" >&2
  exit 1
fi

docker compose -p "${COMPOSE_PROJECT}" -f "${COMPOSE_FILE}" exec -T \
  -e PUBLIC_USERNAME="${PUBLIC_USERNAME}" \
  -e PUBLIC_DISPLAY_NAME="${PUBLIC_DISPLAY_NAME}" \
  -e PUBLIC_PASSWORD_HASH="${PUBLIC_PASSWORD_HASH}" \
  postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "${POSTGRES_DB:-pythontopia}" \
    --set=username="$PUBLIC_USERNAME" \
    --set=display_name="$PUBLIC_DISPLAY_NAME" \
    --set=password_hash="$PUBLIC_PASSWORD_HASH"' <<'SQL'
INSERT INTO users (username, display_name, password_hash, role, status)
VALUES (:'username', :'display_name', :'password_hash', 'STUDENT', 'ACTIVE')
ON CONFLICT (username) DO UPDATE
SET password_hash = EXCLUDED.password_hash,
    role = 'STUDENT',
    status = 'ACTIVE',
    updated_at = now();
SQL

echo "[public_demo] 公共快速体验账号 ${PUBLIC_USERNAME} 已就绪"
