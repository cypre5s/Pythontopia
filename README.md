# Pythontopia

Pythontopia 是面向高校 Python 初学者的网页版像素学习小镇，围绕 `packId=43`《Python语言基础》构建画像、路径、资源生成、练习评估与轻量班级教学闭环。

## 目录

```text
backend/                    # Spring Boot 后端
frontend/                   # Vue 3 + Phaser 3 网页端
services/
  pythontopia-graph/        # 多智能体资源生成与路径规划
  pythontopia-rag/          # 课件 RAG 服务
data/course-pack-43/        # Python语言基础完整课程种子数据
assets/                     # 像素素材与 license
docs/                       # 系统设计、测试说明、比赛文档
deploy/                     # Docker Compose 与启动脚本
plan/                       # 分阶段实施计划
```

## 开发命令

完整约束以 [AGENTS.md](./AGENTS.md) 为准。

```bash
cd backend && mvn clean compile -DskipTests && mvn test
cd frontend && npm ci && npm run typecheck && npm run lint && npm run test
cd services/pythontopia-graph && python -m pip install -e ".[dev]" && python -m pytest -q
cd services/pythontopia-rag && python -m pip install -e ".[dev]" && python -m pytest -q
cd deploy && docker compose up -d postgres redis
```
