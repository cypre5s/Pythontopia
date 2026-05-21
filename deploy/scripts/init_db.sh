#!/usr/bin/env bash
# Pythontopia: 等待 Postgres 就绪，然后让 backend 容器自动跑 Flyway。
# 本脚本不直接执行迁移；迁移由 backend 启动时的 Flyway 完成。
set -euo pipefail

cd "$(dirname "$0")/.."

echo "[init_db] 等待 Postgres ready..."
docker compose up -d postgres
for i in $(seq 1 30); do
  if docker compose exec -T postgres pg_isready -U "${PYT_DB_USERNAME:-pytopia}" -d pythontopia >/dev/null 2>&1; then
    echo "[init_db] Postgres ready"
    break
  fi
  sleep 2
done

echo "[init_db] 启动 backend 触发 Flyway 迁移..."
docker compose up -d backend

echo "[init_db] 等待 backend healthy..."
for i in $(seq 1 60); do
  if curl -fsS http://127.0.0.1:18080/healthz >/dev/null 2>&1; then
    echo "[init_db] backend healthy"
    break
  fi
  sleep 2
done

echo "[init_db] 完成。"
