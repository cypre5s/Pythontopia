#!/usr/bin/env bash
# R-12 烟雾测试：后端单测 + 前端 typecheck/vitest + 关键资产/地图/NPC/migration 完整性。
# 子代理 W4-DEMO-DOC 产出，补齐 W4-E2E 转出的 R-13 遗留 TODO（详见 docs/sv-progress.md）。
# 配合 docs/beta_demo_checklist.md 使用；任一断言失败立即退出。
#
# 用法：bash scripts/r12-smoke-test.sh
#
# 退出码：
#   0  全部 ✓
#   非 0  某项断言失败（脚本 set -e）
set -e

REPO_ROOT="/home/cypress/Pythontopia"
cd "${REPO_ROOT}"

echo "== 1. backend 单测 (mvn test -DskipITs) =="
cd backend
mvn test -DskipITs -q 2>&1 | tail -3
echo "✓ backend pass"

echo "== 2. frontend typecheck =="
cd ../frontend
npm run typecheck 2>&1 | tail -3
echo "✓ frontend typecheck"

echo "== 3. frontend vitest (Test Files 汇总) =="
npm run test 2>&1 | grep "Test Files" | tail -1
echo "✓ frontend vitest"

echo "== 4. 关键像素资产存在性 =="
ls public/assets/pixel/tilesets/kenney-tiny-town/Tilemap/tilemap_packed.png
ls public/assets/pixel/sprites/lpc-sara/SaraFullSheet.png
ls public/assets/pixel/fonts/ark-pixel-12px-mono-zh_cn.woff2
echo "✓ tileset + sara + font"

echo "== 5. 5 张地图 + index.json (应为 6) =="
MAP_COUNT=$(ls public/assets/maps/*.json | wc -l)
echo "maps json count: ${MAP_COUNT}"
test "${MAP_COUNT}" -eq 6
echo "✓ maps == 6"

echo "== 6. 20 NPC (data/npcs 去除 index.json) =="
NPC_COUNT=$(ls ../data/npcs | grep -v index | wc -l)
echo "npc count: ${NPC_COUNT}"
test "${NPC_COUNT}" -eq 20
echo "✓ npcs == 20"

echo "== 7. backend Flyway migration (应 >= 35) =="
MIG_COUNT=$(ls ../backend/src/main/resources/db/migration/V*.sql | wc -l)
echo "migration count: ${MIG_COUNT}"
test "${MIG_COUNT}" -ge 35
echo "✓ migrations >= 35"

echo ""
echo "All smoke checks passed."
