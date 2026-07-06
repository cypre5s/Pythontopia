# Pythontopia Beta v1 全量回归报告

> 演示前最后一次回归记录。每次发布前重跑并刷新本文件。

## 1. 单元 / 集成测试

| 套件 | 命令 | 结果 | 耗时 |
| --- | --- | --- | --- |
| Backend (Spring Boot, JDK 21) | `cd backend && mvn -s .mvn/settings.xml test` | **100 passed, 0 failed, 0 errors** | ~55s |
| Graph (FastAPI + LangGraph) | `cd services/pythontopia-graph && pytest -q` | **59 passed** | ~0.3s |
| RAG (FastAPI + pgvector) | `cd services/pythontopia-rag && pytest -q` | **18 passed** | ~0.4s |
| Frontend (Vue 3 + Vitest) | `cd frontend && npm run typecheck && npm run lint && npm run test` | typecheck OK / lint 0 error / **52 passed** | ~6s |

合计 **229 / 229 通过 / 0 失败**。最近一次执行时间 `2026-05-21 22:10 +08:00`。

## 2. 端到端冒烟

| 步骤 | 命令 | 期望结果 | 实际 |
| --- | --- | --- | --- |
| 启动 | `cd deploy && docker compose up -d --build` | 6 service 全 healthy | PASS |
| 课件索引 | `bash scripts/init_rag_index.sh` | chunk ≥ 1500 | PASS |
| 演示数据 | `bash scripts/seed_demo_classroom.sh` | 1 班级 + 3 学生 + 1 任务 | PASS |
| 健康检查 | `bash scripts/demo_smoke.sh` | 4 healthcheck + course pack 43 可读 | PASS |

> 注：Docker 拉取本地受网络影响时使用 `docker tag redis:7 redis:7-alpine` 回退（详见 `deploy/README.md` 镜像源章节）。

## 3. 性能基线抽样（可选，演示前再跑）

| 接口 | P95 | 备注 |
| --- | --- | --- |
| `/api/auth/login` | < 200ms | BCrypt cost 10 |
| `/api/learner/curriculum/bootstrap` | < 600ms | 65 KC upsert |
| `/api/workshop/resources` | < 5s | 含 RAG + Graph |
| `/api/submissions` | < 2s | subprocess sandbox |
| `/api/learner/evaluations` | < 2s | 6 SQL 聚合 + Graph |

## 4. 不通过项 / 修复记录

无。

## 5. 复盘备注

- 演示主链路依赖讯飞 Spark；如演示日 API 不可用，按 `docs/presentations/demo_script.md` 备用计划切换 `openai_compat`。
- Pyodide 首加载约 6~8s，建议演示前在演示机预跑一次 `/training` 热身。
- 后端单 worker 启动；若评审机器 CPU < 4 核，建议提前 5 分钟把 backend / graph / rag 预热。
