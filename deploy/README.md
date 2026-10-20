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
- Python 服务默认通过官方 PyPI 索引安装依赖，保留上游包哈希校验；可用 `PYT_PIP_INDEX_URL` 覆盖。

## 开发模式（推荐日常改代码）

```bash
cd /home/cypress/Pythontopia
./dev.sh            # Docker: postgres/redis/graph/rag；本机: mvn + Vite :5173
./dev.sh --down     # 停止依赖与本机后端
```

故障排查（**Postgres 密码与 `.env` 不一致**等）：见 [`docs/operations/dev-startup.md`](../docs/operations/dev-startup.md)。

## 一键启动（全 Docker 演示）

```bash
cd /home/cypress/Pythontopia/deploy
cp .env.example .env            # 按需填入 DeepSeek/JWT/Invite/RAG token
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

公网不创建共享演示账号；学生通过登录页注册，避免访客共用学习记录。
本地联动演示可运行 `bash scripts/seed_demo_classroom.sh`，脚本会把一次性密码打印到终端，**不会写入 .env、不会落盘**。
演示后可用 `bash scripts/teardown_demo.sh` 清理 `demo-*` 账号。

## 健康检查

```bash
curl -s http://127.0.0.1:18080/healthz
curl -s http://127.0.0.1:18091/healthz
curl -s http://127.0.0.1:18092/healthz
curl -s http://127.0.0.1:8080/healthz
```

`docker compose ps` 应展示 6 个 service 全部 `healthy`。

## 可观测性（可选）：Langfuse 自托管

Langfuse 是**可选** profile，**不影响默认 6 服务起停**。默认 `docker compose up -d` 不含任何
`langfuse-*` 容器；只有显式启用 `observability` profile 才拉起 Langfuse 全链路 trace + 评测看板。
全链路 trace 走 **OTLP-native**：graph 微服务把既有 OpenTelemetry span 导出到 Langfuse 的
`/api/public/otel`（观测层 fail-soft，未配 endpoint 时 graph no-op，主链路零影响）。

```bash
cd /home/cypress/Pythontopia/deploy
cp .env.example .env                                  # 已含全部 Langfuse/OTLP 变量样例
docker compose --profile observability up -d          # 起 langfuse-web/worker + 专用 pg/clickhouse/redis/minio
curl -fsS http://127.0.0.1:3000/api/public/health     # langfuse-web 健康
# 浏览器开 http://127.0.0.1:3000 → 注册 → 建项目 → 复制 pk-lf-.../sk-lf-...
```

拿到 `pk:sk` 后，在 `.env` 配置 graph 的 OTLP 上报并重建 graph：

```bash
# echo -n "pk-lf-xxxx:sk-lf-xxxx" | base64   # 生成下面的 base64
# .env 内：
#   OTEL_EXPORTER_OTLP_ENDPOINT=http://127.0.0.1:3000/api/public/otel
#   OTEL_EXPORTER_OTLP_HEADERS=Authorization=Basic <上面的 base64>
docker compose up -d pythontopia-graph                 # 重建 graph 使 OTLP env 生效
# 触发一次答疑/工坊后，回 Langfuse UI 的 Tracing 应看到 execute_tool span。
docker compose --profile observability down            # 停 Langfuse（加 -v 连数据卷一起删）
```

设计要点：

- 依赖按 Langfuse 官方 v3 compose 精简，全部加 `langfuse-` 前缀（`langfuse-postgres` /
  `langfuse-clickhouse` / `langfuse-redis` / `langfuse-minio`），与项目业务 `postgres`/`redis`
  **服务名与数据卷均隔离**，互不影响。
- 内部依赖不暴露宿主端口，仅 `langfuse-web` 绑回环 `127.0.0.1:3000`（UI + OTLP 接收口）。
- 开发态 graph 走 `network_mode: host`，故 OTLP endpoint 用 `http://127.0.0.1:3000/...`。
- `TELEMETRY_ENABLED=false`：关闭 Langfuse 自身匿名用量上报。
- **生产（2C4G）不内置 Langfuse 重栈**：`docker-compose.production.yml` 只给 graph 加了
  `OTEL_EXPORTER_OTLP_*` / `LANGFUSE_*` env 接线（留空 no-op），如需观测请把
  `OTEL_EXPORTER_OTLP_ENDPOINT` 指向**外部** Langfuse（Cloud 或独立机）。可换性见主契约 §8.8。
- 校验编排（无需真起容器）：`docker compose config >/dev/null && echo COMPOSE_CONFIG_OK`。

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

---

## 生产部署（阿里云 ECS · HTTP + HTTPS）

生产环境为阿里云 ECS（Ubuntu 22.04，2C4G，公网 `47.120.29.62`），
**80（HTTP）仅供 ACME 验证并将其他请求永久跳转到 443（HTTPS）**。
与开发编排（`docker-compose.yml`）不同，生产用 `docker-compose.production.yml` +
主机 Nginx（`nginx/pythontopia.conf`）+ 三个运维脚本（`scripts/preflight_ecs.sh`、
`scripts/install_release.sh`、`scripts/rollback_release.sh`）。

### 拓扑

```mermaid
flowchart LR
    U[浏览器] -->|"公网 80 / 443（安全组放行）"| N["主机 Nginx<br/>deploy/nginx/pythontopia.conf"]
    N -->|127.0.0.1:8080| F[frontend 容器<br/>内置 nginx]
    F -->|"/api/ → backend:18080"| B[backend 容器]
    subgraph Docker bridge 网络（-p deploy）
        F
        B -->|pythontopia-graph:18091| G[graph 容器]
        B -->|pythontopia-rag:18092| R[rag 容器]
        G -->|pythontopia-rag:18092| R
        B --> P[(postgres<br/>卷 deploy_pytopia_pg)]
        B --> RD[(redis)]
        R --> P
    end
```

要点：

- 对外只有主机 Nginx 的 80/443；`frontend` 绑 `127.0.0.1:8080`、`backend` 绑 `127.0.0.1:18080`
  （仅主机诊断），graph / rag / postgres / redis **不绑任何宿主端口**。
- HTTP 入口不代理应用/API：除 ACME HTTP-01 challenge 外，所有路径固定 308 跳转到
  `https://47.120.29.62`，避免密码、JWT 和业务数据经明文链路传输。
- HTTPS 使用 Let’s Encrypt 为 `47.120.29.62` 签发的可信 IP 短期证书。证书位于
  `/etc/letsencrypt/live/47.120.29.62/`，由
  `pythontopia-certbot-renew.timer` 每日检查两次，续期成功后自动 reload Nginx。
- 旧部署的两处公网暴露（frontend `0.0.0.0:8080`、graph host 网络 `0.0.0.0:18091`）在新拓扑下消除：
  graph 迁入 Compose bridge 网络，backend 通过服务名 `http://pythontopia-graph:18091` 访问。
- Compose 项目名**固定 `-p deploy`**，从而复用现有数据卷 `deploy_pytopia_pg`（卷声明名 `pytopia_pg`）。
- 流式 API（导师答疑 / 课堂直播 / 媒体合成进度等 SSE）在主机 Nginx 的 `/api/` 路径禁用代理缓冲。

### 目录约定（ECS 上）

```text
/opt/pythontopia/
├── releases/<时间戳>/        # 每次安装一个不可变 release（发布包解包产物）
├── current -> releases/...   # 当前生效版本（原子切换的符号链接）
├── backups/<时间戳>/          # 每次安装前的 pg_dump、.env、Nginx/Compose 配置备份
├── shared/.env               # 生产秘密唯一来源（600 权限；发布包内绝无秘密）
├── shared/data/course-pack-43/    # 课件 PDF / 视频（只读挂载进 backend 容器 /data/course-pack-43）
├── shared/data/generated-media/   # 多模态生成产物（读写挂载，可重生成）
└── .previous-release          # 上一生效版本路径（rollback_release.sh 默认目标）
```

### 发布包冻结契约

- 镜像名：`pythontopia/{frontend,backend,graph,rag,piston}:release`
- 镜像 tar：发布包内 `deploy/runtime/images/{frontend,backend,graph,rag,piston}.tar`
- 校验和：`deploy/runtime/sha256sums.txt`
- 发布包顶层目录：`backend/ deploy/ frontend/ README.md`

### 首次迁移（从旧部署切换到本拓扑，一次性）

```bash
# 1. 把现有线上 .env 落位到权威路径（之后所有版本共用，不再随发布包分发）
install -D -m 600 <旧部署目录>/deploy/.env /opt/pythontopia/shared/.env

# 2. 主线闯关必需：课件 PDF / 视频落位。缺失时虽然服务仍可启动，
#    但「看 / 学」资源为空，学生无法完成主线小节。
#    /opt/pythontopia/shared/data/course-pack-43/{pdfs,videos,video_manifest.json}

# 3. 预检 + 安装（见下）
```

旧部署容器与新编排同属 `-p deploy` 项目：`install_release.sh` 执行 `up -d --remove-orphans`
时会按新配置原地重建同名服务，自动收敛旧的端口暴露，不需要人工 `docker compose down`。

### 安装 / 升级

```bash
# 1. 只读预检（Ubuntu/root/磁盘/内存/Docker/卷/.env 变量名/80 端口/安全组）
sudo bash /opt/pythontopia/current/deploy/scripts/preflight_ecs.sh   # 或发布包内同路径脚本
# 预检输出 SECURITY_GROUP_PORT_80_BLOCKED 时：本机 80 通而公网 80 不通，
# 去阿里云控制台安全组放行 TCP 80 后重跑。
# HTTPS 还要求安全组放行入方向 TCP 443（0.0.0.0/0）。

# 2. 事务式安装（备份 → 校验 → 导镜像 → 起服务 → 装 Nginx → 原子切换）
sudo bash <发布包内>/deploy/scripts/install_release.sh /path/to/pythontopia-release.tar.gz
```

安装脚本任何一步失败都会立即停止并输出中文错误；`current` 符号链接只在七个运行服务
全部健康、`nginx -t` 通过之后才切换。

### 健康检查

```bash
curl -sS -o /dev/null -w '%{http_code} %{redirect_url}\n' http://127.0.0.1/healthz  # 预期 308 到 HTTPS
curl --resolve 47.120.29.62:443:127.0.0.1 -s https://47.120.29.62/healthz
curl -s http://127.0.0.1:8080/healthz     # frontend 容器直连
curl -s http://127.0.0.1:18080/healthz    # backend 容器直连（主机诊断口）
docker compose -p deploy ps               # 七个运行服务应全部 healthy；runtime init 退出码为 0
curl -sS -o /dev/null -w '%{http_code} %{redirect_url}\n' http://47.120.29.62/healthz  # 预期 308
curl -s https://47.120.29.62/healthz      # 公网 HTTPS + 可信 IP 证书
```

graph / rag 不绑宿主端口，诊断走容器内：
`docker exec deploy-pythontopia-graph-1 curl -fsS http://127.0.0.1:18091/healthz`（rag 同理，18092）。

### HTTPS 证书续期

Let’s Encrypt IP 证书是约 160 小时的短期证书，必须保持自动续期：

```bash
systemctl is-enabled pythontopia-certbot-renew.timer
systemctl is-active pythontopia-certbot-renew.timer
systemctl list-timers pythontopia-certbot-renew.timer
systemctl status pythontopia-certbot-renew.service
openssl x509 -in /etc/letsencrypt/live/47.120.29.62/fullchain.pem \
  -noout -dates -ext subjectAltName
```

timer 每日检查两次；renewal 配置使用 `shortlived` + `webroot`，成功续期后执行
`systemctl reload nginx`。HTTP-01 challenge 依赖公网 TCP 80，不能关闭 80。

### 备份策略

- 每次 `install_release.sh` 在切换前自动落 `/opt/pythontopia/backups/<时间戳>/`：
  `pythontopia-<时间戳>.sql.gz`（pg_dump 压缩全库）、`env.backup`、Nginx 站点配置、当前版本 Compose 文件。
- 备份目录权限 700，数据库备份与 `env.backup` 权限 600；**请定期把 sql.gz 拷贝下载到 ECS 之外**。
- 日常手动备份：

```bash
CID=$(docker ps -q -f label=com.docker.compose.project=deploy -f label=com.docker.compose.service=postgres)
docker exec "$CID" sh -c 'pg_dump -U "$POSTGRES_USER" -d pythontopia' | gzip > /opt/pythontopia/backups/manual-$(date +%Y%m%d-%H%M%S).sql.gz
```

### 回滚

```bash
sudo bash /opt/pythontopia/current/deploy/scripts/rollback_release.sh                 # 回滚到上一版本
sudo bash .../rollback_release.sh /opt/pythontopia/releases/<时间戳>                  # 回滚到指定版本
```

- 镜像 tag 固定为 `:release`，回滚脚本会重新 `docker load` 目标版本自带的镜像 tar
  （先 sha256 校验），再 `--force-recreate` 重建容器并回切 Nginx 配置与 `current` 链接。
- 回滚**绝不删除 / 重建 Postgres 卷**，数据库保持回滚时刻的内容。
- 任何 release 目录都不会被脚本自动删除；磁盘紧张时人工清理最旧版本，至少保留最近 2 个。

### 数据库手动恢复（仅在确认需要时人工执行）

回滚脚本不自动恢复数据库。若新版本的 Flyway 迁移与旧代码不兼容、必须把数据恢复到
安装前状态，按以下步骤**人工**操作（有数据丢失风险，先二次备份再动手）：

```bash
# 0) 先停应用写入，再对当前库做一次保险备份（命令见上「备份策略」）
cd /opt/pythontopia/current/deploy
docker compose -p deploy -f docker-compose.production.yml stop backend pythontopia-rag pythontopia-graph frontend

# 1) 进入 postgres 容器，重建数据库并导入安装前备份
CID=$(docker ps -q -f label=com.docker.compose.project=deploy -f label=com.docker.compose.service=postgres)
gunzip -c /opt/pythontopia/backups/<时间戳>/pythontopia-<时间戳>.sql.gz | docker exec -i "$CID" sh -c 'psql -U "$POSTGRES_USER" -d postgres -c "DROP DATABASE pythontopia;" -c "CREATE DATABASE pythontopia;" && psql -U "$POSTGRES_USER" -d pythontopia'

# 2) 重新拉起应用
docker compose -p deploy -f docker-compose.production.yml up -d
```

### 常见故障

| 现象 | 原因与处置 |
| --- | --- |
| 预检输出 `SECURITY_GROUP_PORT_80_BLOCKED` | 本机 80 通、公网 80 不通：阿里云控制台 → 安全组 → 入方向放行 TCP 80（0.0.0.0/0） |
| 本机 HTTPS 正常但公网 443 超时 | 阿里云控制台 → 安全组 → 入方向放行 TCP 443（0.0.0.0/0），不要放行内部服务端口 |
| compose 渲染报「缺少 PYT_DB_USERNAME/...」 | `/opt/pythontopia/shared/.env` 缺必需变量名；先跑预检第 5 节核对（只比对变量名，不读值） |
| backend 一直不健康，日志见数据库认证失败 | 复用 `deploy_pytopia_pg` 卷时凭据以**建卷时**为准，`.env` 中 `PYT_DB_USERNAME/PYT_DB_PASSWORD` 必须与卷内一致（同开发踩坑 `docs/operations/dev-startup.md`） |
| rag 启动报数据库连接串解析错误 | `PYT_DB_PASSWORD` 含 `@ : /` 等 URL 保留字符时拼接 `postgresql://` 会解析失败；更换无保留字符的密码，或自行 URL 转义 |
| backend 健康但等待超时（2C4G 冷启动慢） | 安装脚本预算 8 分钟；确认 `docker logs deploy-backend-1` 中 Flyway 正常推进即可重跑安装 |
| frontend 容器反复重启 | 镜像内 nginx 上游写死 `backend:18080`，backend 容器不存在时 DNS 解析失败即退出；先看 backend 健康状态 |
| 80 被非 nginx 进程占用 | 预检第 6 节会点名进程；处置后重跑 |
| 公网访问慢 / SSE 断流 | 确认走的是主机 Nginx `/api/`（已禁缓冲、读超时 1h）；若中间另有代理/CDN，需同样禁缓冲 |
| 媒体文生图 / TTS 不可用 | 这些讯飞服务已固定关闭，不配置 Spark 凭据；图片输入由 DeepSeek 视觉模型处理，配音不启用 |
| 磁盘告警 | 容器日志已限 10m×3；大头通常是 releases 旧版本与 backups，按上文清理（至少保留最近 2 个 release） |

### 生产与开发编排接线差异（速查）

| 项 | 开发 `docker-compose.yml` | 生产 `docker-compose.production.yml` |
| --- | --- | --- |
| 镜像 | 本地 build `*:dev` | 冻结 `pythontopia/*:release` + `pull_policy: never` |
| graph 网络 | `network_mode: host`（0.0.0.0:18091） | bridge，仅服务名可达，不绑宿主端口 |
| graph → rag | `http://127.0.0.1:18092` | `http://pythontopia-rag:18092` |
| backend → graph/rag | `PYTHONTOPIA_SERVICES_*` 服务名 | 同名变量 + `PYT_GRAPH_URL`/`PYT_RAG_URL` 双写覆盖，防 .env 残留旧地址 |
| frontend 端口 | `8080:80`（全网卡） | `127.0.0.1:8080:80`，对外由主机 Nginx 80/443 代理 |
| postgres/redis 端口 | `15432`/`16379` | 不绑宿主端口 |
| 秘密 | compose 内 `${VAR:-默认值}` | `env_file` 指向 `/opt/pythontopia/shared/.env`，必需项 `${VAR:?}` 硬校验 |
| JAVA_OPTS | 默认带宿主代理 `host.docker.internal:7892` | 默认直连，可用 `PYT_BACKEND_JAVA_OPTS` 覆盖 |
| 源码挂载 | graph/rag 源码只读挂载热更 | 无（镜像自含） |
| 资源护栏 | 无 | 各服务 `mem_limit` + 日志 10m×3（适配 2C4G/40G） |
