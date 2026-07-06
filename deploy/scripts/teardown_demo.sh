#!/usr/bin/env bash
# Pythontopia: 演示后清理演示班级与学生数据。
# 注意：此脚本会删除 username 以 'demo-' 开头的账号与相关班级。
set -euo pipefail

cd "$(dirname "$0")/.."

docker compose exec -T postgres psql -U "${PYT_DB_USERNAME:-pytopia}" -d pythontopia <<'SQL'
DELETE FROM classroom_quest WHERE created_by_user_id IN (
  SELECT id FROM users WHERE username LIKE 'demo-%'
);
DELETE FROM classroom_members WHERE student_id IN (
  SELECT id FROM users WHERE username LIKE 'demo-%'
);
DELETE FROM classrooms WHERE teacher_user_id IN (
  SELECT id FROM users WHERE username LIKE 'demo-%'
);
DELETE FROM users WHERE username LIKE 'demo-%';
SQL

echo "[teardown_demo] 演示账号已清理。"
