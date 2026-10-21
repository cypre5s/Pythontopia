# Pythontopia Web 发布包部署指南

本压缩包是「Pythontopia 像素学习小镇」Web 端的离线发布包，面向目标服务器的部署人员。
包内自带四个应用与独立 Piston 判题服务的 Docker 镜像，目标机**不需要**从源码构建，也不需要 Node / Maven / Python 工具链。

产物命名：`pythontopia-web-<UTC时间戳>.tar.gz`（例如 `pythontopia-web-20260612T120000Z.tar.gz`）。

## 1. 包内结构

发布包顶层**恰好四项**（多一项少一项都视为包损坏）：

```text
pythontopia-web-<UTC>.tar.gz
├── README.md                  ← 本文件（部署入口文档）
├── backend/                   ← Spring Boot 后端源码（运行靠镜像；源码仅供审计与排障）
├── frontend/                  ← Vue 3 + Phaser 前端源码（构建产物已在镜像内，源码树不含 dist/）
└── deploy/                    ← 部署编排与运行时产物
    ├── docker-compose.yml             ← 开发态编排（从源码构建，目标机一般不用）
    ├── docker-compose.production.yml  ← 生产编排（使用包内 release 镜像，部署用这份）
    ├── .env.example                   ← 环境变量样板（首次部署复制为 .env 后填写）
    ├── initdb/                        ← Postgres 初始化挂载目录
    ├── nginx/                         ← 主机 Nginx 站点配置（对外 80/443 入口）
    ├── course-data/
    │   └── video_transcripts.json     ← 与本次代码绑定的 34 个课程视频逐段转写
    ├── scripts/                       ← 部署辅助脚本（预检/安装/验收、包校验器等）
    ├── release/                       ← 发布说明源文件与内容契约（archive-allowlist.txt）
    └── runtime/
        ├── images/
        │   ├── frontend.tar           ← 镜像 pythontopia/frontend:release
        │   ├── backend.tar            ← 镜像 pythontopia/backend:release
        │   ├── graph.tar              ← 镜像 pythontopia/graph:release
        │   ├── rag.tar                ← 镜像 pythontopia/rag:release
        │   └── piston.tar             ← 镜像 pythontopia/piston:release
        └── sha256sums.txt             ← 包内全部文件的 SHA-256 清单
```

说明：

- Graph（多智能体）与 RAG（课件检索）两个 Python 微服务**只随镜像交付**，源码目录不在包内。
- 镜像名固定为 `pythontopia/{frontend,backend,graph,rag,piston}:release`，生产编排按此名引用。
- 包内**不含**任何 `.env` 实值、密钥、课件 PDF/视频、`node_modules`、构建产物等本地文件；
  仅携带体积较小且经过源视频 SHA-256 校验的机器转写文本，安装器会在覆盖前备份旧字幕；
  内容契约见 `deploy/release/archive-allowlist.txt`。

## 2. 前置要求

| 项目 | 要求 |
| --- | --- |
| 操作系统 | Linux x86_64（Ubuntu 22.04 实测） |
| Docker | Docker Engine ≥ 24，含 Compose v2（`docker compose version` 可验证） |
| 资源 | 最低 2C4G（生产编排已按此设 mem_limit），建议 4C8G；磁盘空闲 ≥ 20 GB（镜像 + 解包暂存 + 数据库） |
| 端口 | 对外仅 `80/443`（主机 Nginx，配置见 `deploy/nginx/`）；容器侧只绑定回环 `127.0.0.1:8080`（frontend）与 `127.0.0.1:18080`（backend 诊断）；graph/rag/postgres/redis/Piston 不暴露宿主端口 |
| 基础镜像 | `pgvector/pgvector:pg16` 与 `redis:7-alpine` **不在包内**，目标机需可拉取（或由交付人另行提供镜像 tar 后 `docker load`） |
| 网络 | Piston 不映射宿主端口；首次启动需访问 Piston 官方 runtime 包源，Python runtime 写入持久卷后可离线运行 |

生产共享数据目录 `/opt/pythontopia/shared/data/course-pack-43/` 必须预置 7 份 PDF、
`video_manifest.json` 与清单中的全部 MP4。打包、预检和安装均会核验 PDF 数量、视频存在性、
字幕覆盖、object id 与每个 MP4 的 SHA-256；任何一项缺失或错版都会终止，而不是降级成假内容。

生产共享数据目录 `/opt/pythontopia/shared/data/course-pack-43/` 必须预置 7 份 PDF、
`video_manifest.json` 与清单中的全部 MP4。打包、预检和安装均会核验 PDF 数量、视频存在性、
字幕覆盖、object id 与每个 MP4 的 SHA-256；任何一项缺失或错版都会终止，而不是降级成假内容。

## 3. 部署步骤

### GitHub Release 直拉

将 `web-vYYYYMMDD.N` tag 推送到 `main` 会触发 GitHub Actions：运行本指南所用的 release builder，并把校验后的包上传为公开 GitHub Release asset。PDF 与 MP4 不在公开仓库中，托管 runner 会跳过本机课程媒体检查；ECS 的预检和安装器仍会对共享课程数据执行完整校验，失败时不会切换线上版本。ECS 不需要 GitHub token 或 deploy key。

```bash
# 首次使用；通过 GitHub API 读取脚本，把 tag 替换为已发布版本
curl -fsSL -H 'Accept: application/vnd.github.raw+json' \
  'https://api.github.com/repos/cypre5s/Pythontopia/contents/deploy/scripts/install_github_release.sh?ref=main' \
  | sudo bash -s -- web-v20261020.2

# 之后使用 current 里的脚本
sudo bash /opt/pythontopia/current/deploy/scripts/install_github_release.sh web-v20261020.2
```

脚本先执行 ECS 预检，再下载 release asset 并调用现有安装器；预检要求根分区至少有 5 GB 可用空间。成功安装后下载包会删除，失败时会保留供排查。

### 3.1 校验与解包

```bash
# ① 解包（解包目录磁盘占用约为包体积的 2~3 倍）
tar -xzf pythontopia-web-<UTC>.tar.gz -C /opt/pythontopia
cd /opt/pythontopia

# ② 用包内自带校验器对原始压缩包复验（核对顶层结构 / 违禁内容 / 镜像清单 / 全量 SHA-256）
bash deploy/scripts/verify_release_archive.sh /path/to/pythontopia-web-<UTC>.tar.gz
# 输出「结论：发布包校验通过」才继续；校验失败的包禁止部署。

# 同时验证目标机现有 PDF/MP4 与包内候选字幕是否完全匹配：
python3 deploy/scripts/validate_course_runtime_data.py \
  /opt/pythontopia/shared/data/course-pack-43 \
  --transcript deploy/course-data/video_transcripts.json

# 也可只做快速校验和核对：
sha256sum -c deploy/runtime/sha256sums.txt --quiet && echo 校验和一致
```

### 3.2 导入镜像

```bash
for f in deploy/runtime/images/*.tar; do docker load -i "$f"; done
docker images | grep pythontopia   # 应看到 frontend/backend/graph/rag/piston 五个 :release 镜像
```

### 3.3 配置环境变量

```bash
cd deploy
cp .env.example .env    # 首次部署；升级既有环境则沿用远端已有 .env，不要覆盖
vi .env
```

秘密不随包发布。生产编排对关键变量做了 `${VAR:?}` 强校验，缺失会在启动时直接报错。
至少修改以下项（其余按需）：

- `PYT_DB_PASSWORD`：数据库密码（务必改掉默认值）
- `PYT_JWT_SECRET`：至少 32 字符的随机串
- `PYT_TEACHER_INVITE_CODE`：教师注册邀请码
- `PYT_RAG_ADMIN_TOKEN`：RAG 管理接口令牌
- 生产 Compose 固定使用内部 `http://piston:2000`，`PYT_PISTON_PYTHON_VERSION` 默认为 `3.11.0`；Piston 与 runtime 初始化任务只加入 backend 共用的 `judge-execution` 网络，不映射宿主端口，生产绝不回退 `subprocess`。Piston API v3.1.0 不校验 Bearer token，因此访问控制依赖 Docker 网络成员限制；该网络保留出站能力供首次 runtime 安装，学生提交代码由 `nosocket` 沙箱禁网
- `PYT_LLM_PROVIDER=openai_compat`、`PYT_OPENAI_BASE_URL=https://api.deepseek.com`、`PYT_OPENAI_API_KEY`：只允许模型 `deepseek-v4-pro`（文本）和 `deepseek-flash`（文本/图片输入）；视觉模型必须为 `deepseek-flash`。生产禁止 dryrun、Spark 和其它兼容端点
- `PYT_RAG_EMBEDDER=hash`：RAG 使用本地 hash 向量与 BM25，不要求外部 embedding API；语义向量降级时 `/readyz` 标记 `ready_degraded`，仍要求 BM25 课程索引可用
- 配音、语音识别、语音输入和独立文生图入口关闭；导师图片通过 DeepSeek 视觉模型直接处理

### 3.4 启动

优先使用包内 `deploy/scripts/` 提供的预检/安装脚本（以包内实际脚本与
`docker-compose.production.yml` 文件头注释为准）。手动方式：

```bash
cd deploy
# 项目名必须固定为 deploy，以复用既有数据卷 deploy_pytopia_pg（详见生产编排文件头注释）
docker compose -p deploy -f docker-compose.production.yml config -q     # 先校验配置
docker compose -p deploy -f docker-compose.production.yml up -d --remove-orphans
```

首次启动时后端 Flyway 会自动建表并导入课程种子（`packId=43`，约 7 文档 / 561 页 / 65 KC），
首启等待 1~2 分钟属正常（生产编排已为 backend 预留 120s 健康检查启动期）。

可选初始化——重建课件向量索引（RAG 首次部署建议执行；生产编排不暴露 RAG 宿主端口，
需经容器网络调用）：

```bash
cd deploy
source .env
docker compose -p deploy -f docker-compose.production.yml exec pythontopia-rag \
  curl -fsS -X POST http://127.0.0.1:18092/rag/index/rebuild \
  -H "X-Internal-Token: ${PYT_RAG_ADMIN_TOKEN}"
```

## 4. 健康检查

```bash
cd deploy
docker compose -p deploy -f docker-compose.production.yml ps   # 七个运行服务 healthy；piston-runtime-init 退出码为 0

# 宿主回环口（生产编排只暴露这两个）
curl -sf http://127.0.0.1:8080/healthz   && echo " frontend ok"
curl -sf http://127.0.0.1:18080/readyz  && echo " backend ready"
curl -sS -o /dev/null -w '%{http_code} %{redirect_url}\n' http://127.0.0.1/healthz  # 预期 308 到 HTTPS
curl -sf --resolve 47.120.29.62:443:127.0.0.1 \
  https://47.120.29.62/healthz            && echo " nginx https ok"

# graph / rag 不绑定宿主端口，进容器网络检查
docker compose -p deploy -f docker-compose.production.yml exec pythontopia-graph \
  curl -sf http://127.0.0.1:18091/readyz && echo " graph ready"
docker compose -p deploy -f docker-compose.production.yml exec pythontopia-rag \
  curl -sf http://127.0.0.1:18092/readyz && echo " rag ready"
```

浏览器分别访问 `http://<服务器IP>/` 与 `https://<服务器IP>/`，能进入登录页并完成注册/登录即部署成功。
注意：AI 流式输出（SSE）经前端 nginx 的 `/api/` 反代，已配置关闭缓冲与 300s 长超时；
若外层还有自建网关/CDN，需同样对 `/api/` 关闭缓冲，否则打字机效果会变成一次性整段输出。

## 5. 备份

建议每日定时备份以下内容：

```bash
cd /opt/pythontopia/deploy

# ① 数据库（核心资产：账号、画像、提交记录、任务进度）
docker compose -p deploy -f docker-compose.production.yml exec -T postgres \
  pg_dump -U "${PYT_DB_USERNAME:-pytopia}" pythontopia > /backup/pythontopia-$(date -u +%Y%m%dT%H%M%SZ).sql

# ② 配置（含密钥，注意备份介质权限）
cp .env /backup/env-$(date -u +%Y%m%dT%H%M%SZ)

# ③ 共享数据目录（课件/生成媒体，如有）：/opt/pythontopia/shared/data/

# ④ 发布包原件留档（回滚依据，至少保留上一版与当前版）
```

恢复数据库：

```bash
docker compose -p deploy -f docker-compose.production.yml exec -T postgres \
  psql -U "${PYT_DB_USERNAME:-pytopia}" -d pythontopia < /backup/pythontopia-<时间戳>.sql
```

## 6. 回滚

前提：保留了上一版发布包与对应时间点的数据库备份。

```bash
# 若包内提供回滚脚本（见 deploy/scripts/），优先使用；手动回滚流程：

# ① 停当前版本（down 不带 -v，保留数据卷）
cd /opt/pythontopia/deploy
docker compose -p deploy -f docker-compose.production.yml down

# ② 导入上一版镜像（会覆盖 :release 标签，指回旧版本）
for f in /opt/pythontopia-prev/deploy/runtime/images/*.tar; do docker load -i "$f"; done

# ③ 用上一版包的 deploy/ 目录启动（项目名仍为 deploy，复用同一数据卷）
cd /opt/pythontopia-prev/deploy
docker compose -p deploy -f docker-compose.production.yml up -d --remove-orphans
```

**数据库注意事项**：后端使用 Flyway 前滚式迁移，新版启动后 schema 只进不退。
若新版已执行过迁移，回滚旧版应用前必须先把数据库还原到升级前的备份点
（否则旧版代码面对新 schema 行为未定义）。仅回滚镜像、不动数据库的做法只适用于
「新版未引入任何迁移」的场景。

## 7. 常见问题速查

- **服务起不来 / Flyway 报密码错误**：Postgres 数据卷里保存的是首次初始化时的密码，
  改 `.env` 不会改已有卷。要么改回原密码，要么 `docker volume rm` 清卷重建（数据会丢，先备份）。
- **`/api` 请求超时或 AI 回复不流式**：检查前端容器内 nginx 是否为包内版本
  （`proxy_buffering off; proxy_read_timeout 300s`），以及外层网关是否启用了响应缓冲。
- **DeepSeek 调用失败**：检查 `PYT_OPENAI_BASE_URL`、API key 和文本/视觉模型名；密钥只保存在部署端 `.env`，图片请求不会回退到其它图像服务。
- **重新校验包**：任何时候都可重跑 `bash deploy/scripts/verify_release_archive.sh <包路径>`；
  校验器与内容契约（`deploy/release/archive-allowlist.txt`）随包发布。
