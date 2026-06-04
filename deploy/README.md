# Pythontopia Deploy

阶段 14 起，Docker Compose 一键拉起 Postgres + Redis + Backend + Graph + RAG + Frontend 六项服务。
Beta 演示路径以本目录为准。

## 受限网络重建（代理 7892 + 国内镜像）

当 Maven Central 403 或容器内无法直连时，使用 host 网络 + 本地代理重建：

```bash
cd /home/cypress/Pythontopia/deploy
bash scripts/build-with-proxy.sh
# 或手动：
# docker compose -f docker-compose.yml -f docker-compose.build.yml up -d --build
```

构建覆盖项（`docker-compose.build.yml`）：

- Docker build 使用 `network: host`，使容器内可访问 `127.0.0.1:7892`
- Maven：`backend/.mvn/settings.build.xml`（腾讯云镜像 + 7892 代理）
- npm：`registry.npmmirror.com`
- pip：`mirrors.aliyun.com/pypi/simple/`

## 开发模式（推荐日常改代码）

```bash
cd /home/cypress/Pythontopia
./dev.sh            # Docker: postgres/redis/graph/rag；本机: mvn + Vite :5173
./dev.sh --down     # 停止依赖与本机后端
```

故障排查（**Postgres 密码与 `.env` 不一致**等）：见 [`docs/dev-startup.md`](../docs/dev-startup.md)。

## 一键启动（全 Docker 演示）

```bash
cd /home/cypress/Pythontopia/deploy
cp .env.example .env            # 按需填入 Spark/JWT/Invite/RAG token
docker compose up -d --build    # 首次构建约需 5~10 分钟
```

或项目根目录：`./start.sh`（不重建镜像时更快）。

启动后端口约定：

| 服务 | 容器端口 | 宿主端口 | 说明 |
| --- | --- | --- | --- |
| `frontend` (nginx) | 80 | `8080` | 演示主入口（已开启 gzip + `/api` 反向代理） |
| `backend` (Spring Boot) | 18080 | `127.0.0.1:18080` | 仅本地访问 |
| `pythontopia-graph` (FastAPI) | 18091 | `127.0.0.1:18091` | LangGraph agent 入口 |
| `pythontopia-rag` (FastAPI) | 18092 | `127.0.0.1:18092` | 课件 RAG |
| `postgres` (pgvector) | 5432 | `15432` | |
| `redis` | 6379 | `16379` | |

## 演示脚手架

```bash
bash scripts/init_db.sh             # 等待 postgres + 启动 backend，让 Flyway 跑完所有迁移
bash scripts/init_rag_index.sh      # 调 /rag/index/rebuild 重建课件向量索引
bash scripts/seed_demo_classroom.sh # 注册 1 教师 + 3 学生 + 1 班级 + 1 班级任务（幂等）
bash scripts/demo_smoke.sh          # 4 健康检查 + course pack 43 可读冒烟
```

`seed_demo_classroom.sh` 会随机生成一次性密码并打印到终端，**不会写入 .env、不会落盘**，演示后请用
`bash scripts/teardown_demo.sh` 一键清理 `demo-*` 账号。

## 健康检查

```bash
curl -s http://127.0.0.1:18080/healthz
curl -s http://127.0.0.1:18091/healthz
curl -s http://127.0.0.1:18092/healthz
curl -s http://127.0.0.1:8080/healthz
```

`docker compose ps` 应展示 6 个 service 全部 `healthy`。

## 课程种子导入

`postgres` 容器通过 `POSTGRES_DB=pythontopia` 创建本地数据库，并挂载 `deploy/initdb/` 作为初始化入口。
课程表结构与 `packId=43` 种子数据由后端启动时的 Flyway 迁移 `V001` 到 `V006` 自动导入。

一次性导出 Alethicode 种子（仅在新环境需要）：

```bash
cd /home/cypress/Pythontopia/data/course-pack-43/export
ALETHICODE_DB_URL='postgresql://<user>:<password>@127.0.0.1:5436/alethicode' \
  uv run --with 'psycopg[binary]' python export_from_alethicode.py

cd /home/cypress/Pythontopia/data/course-pack-43
python3 build_seed_sql.py
```

## 镜像源

`docker-compose.yml` 保留官方镜像名：

- `pgvector/pgvector:pg16`
- `redis:7-alpine`
- `eclipse-temurin:21-jre`、`maven:3.9-eclipse-temurin-21`
- `node:20-alpine`、`nginx:1.27-alpine`
- `python:3.11-slim`

本机 Docker daemon 已配置镜像源时会自动使用 daemon 配置。当前环境中 `docker.m.daocloud.io` 对
`redis:7-alpine` 出现 TLS handshake timeout，`dockerproxy.com` 也出现 EOF；不要把这两个不稳定前缀写死进
compose。临时验收可在本机已有 `redis:7` 时执行：

```bash
docker tag redis:7 redis:7-alpine
docker compose up -d postgres redis
```

这只是本地镜像源故障回退，不改变项目镜像约定。

## 依赖源

后端 Maven 使用项目内 `backend/.mvn/settings.xml` 指向 Maven Central。不要把个人全局 mirror、代理地址
或超时源写入仓库；网络受限时在本机用 `mvn -s <local-settings.xml>` 覆盖即可。

Docker Compose 同样保留官方镜像名，镜像加速交给本机 Docker daemon 配置。
