# 本地开发启动备忘

> 避免 Agent/开发者重复踩坑。最后更新：2026-06-04。

## 推荐：一条命令开发栈

```bash
cd /home/cypress/Pythontopia
./dev.sh
```

| 组件 | 地址 | 说明 |
| --- | --- | --- |
| **前端（日常开发入口）** | http://localhost:5173 | Vite 热更新，`/api` 代理到后端 |
| 后端 | http://127.0.0.1:18080 | 本机 `mvn spring-boot:run` |
| Graph | http://127.0.0.1:18091 | Docker `pythontopia-graph` |
| RAG | http://127.0.0.1:18092 | Docker `pythontopia-rag` |
| Postgres | 127.0.0.1:15432 | Docker 数据卷 `pytopia_pg` |
| Redis | 127.0.0.1:16379 | Docker |

停止：`Ctrl+C`（关前端 + 本机后端）；彻底停依赖：`./dev.sh --down`。

## 与「全 Docker」的区别

| 模式 | 命令 | 适用 |
| --- | --- | --- |
| **开发** | `./dev.sh` | 改前端/后端代码、热更新 |
| **演示/集成** | `./start.sh` 或 `cd deploy && docker compose up -d` | 6 服务全容器，前端 :8080 |

**不要**在开发时只跑 `mvn spring-boot:run` 而忘记拉起 graph/rag/postgres/redis——答疑、工坊、RAG、Flyway 都会失败或降级。

## 踩坑记录：Postgres 密码与 `.env` 不一致

### 现象

`./dev.sh` 等待约 3 分钟后报错：

```text
FATAL: password authentication failed for user "pytopia"
```

Graph/RAG 容器可能仍健康（走 Docker 内网 + 旧密码），但**本机后端**连 `127.0.0.1:15432` 会用 `deploy/.env` 里的 `PYT_DB_PASSWORD`。

### 根因

- Postgres **数据卷**在首次 `docker compose up` 时按当时的 `POSTGRES_PASSWORD` 初始化；
- 之后若修改了 `deploy/.env` 中的 `PYT_DB_PASSWORD`，**不会**自动改卷内密码；
- `pg_isready` 只说明进程在听端口，**不能**证明密码正确。

### 修复（保留数据）

```bash
cd /home/cypress/Pythontopia/deploy
# 将 <密码> 换成 deploy/.env 里当前的 PYT_DB_PASSWORD（默认 pytopia_dev_password）
docker compose exec -T postgres psql -U pytopia -d pythontopia \
  -c "ALTER USER pytopia PASSWORD 'pytopia_dev_password';"
```

验证（宿主侧，需本机有 `psql` 或由 `./dev.sh` 自动校验）：

```bash
PGPASSWORD=pytopia_dev_password psql -h 127.0.0.1 -p 15432 -U pytopia -d pythontopia -c 'SELECT 1'
```

### 修复（清空库，仅本地可接受时）

```bash
cd /home/cypress/Pythontopia/deploy
docker compose down -v   # 删除 pytopia_pg 卷，Flyway 会重跑
cp .env.example .env     # 如需重置配置
cd .. && ./dev.sh
```

### 预防

- 改 `PYT_DB_PASSWORD` 后：要么执行上面的 `ALTER USER`，要么 `down -v` 重建卷；
- `./dev.sh` 在启动后端前会做一次**宿主密码探测**，失败则尝试 `ALTER USER` 与 `.env` 对齐（见 `dev.sh` 内 `verify_postgres_password_from_host`）。

## 踩坑记录：讯飞多模态连不通（fake-ip DNS）

### 现象

百晓工坊「合成短视频」一直降级：文生图出占位图（`media_asset.source=PLACEHOLDER`），
TTS 配音卡住，最终 `media_job` 失败或停在「配音师朗读」。但 `scripts/verify_xfyun.py`
（Python）却能成功合成音频/图片。

### 根因

部分本机网络用 **fake-ip DNS**（如 Clash/代理软件把讯飞域名解析到 `198.18.x.x`，
属基准测试保留段），直连这个假 IP 必然超时。Python 的 `urllib`/`websockets` 会自动读
`HTTPS_PROXY` 环境变量走代理，**但 Java `HttpClient` 默认不读环境代理**，于是
`XfyunImageService`(TTI) / `XfyunTtsService`(TTS) connect timeout → 恒降级。

判断方法：`getent ahosts spark-api.cn-huabei-1.xf-yun.com` 若返回 `198.18.x.x` 即 fake-ip。

### 修复

后端 JVM 必须带代理参数（讯飞走代理、本地服务直连）：

- `./dev.sh`：已内置，由 `PYT_XFYUN_PROXY`（默认回退 `PYT_BILI_PROXY`=7892）转成
  `-Dhttps.proxyHost/Port` 注入；直连网络可在 `deploy/.env` 设 `PYT_XFYUN_PROXY=`（空）。
- `docker compose`：backend 的 `JAVA_OPTS` 已内置，经 `host.docker.internal:7892` 走宿主代理
  （`PYT_XFYUN_PROXY_HOST/PORT` 可覆盖）；直连网络设 `PYT_XFYUN_PROXY_PORT=`（空）。

验证（拿到 token 后提交合成作业，应 `source=XFYUN/degraded=false`、最终 mp4）：

```bash
python3 scripts/verify_xfyun.py   # 独立验证讯飞凭据/服务（走系统代理）
```

## 踩坑记录：合成短视频需 ffmpeg

`media_job` 合成依赖 ffmpeg；缺失时作业置 `FAILED`(`VIDEO_COMPOSE_UNAVAILABLE`)，其它链路不受影响。

- compose：后端镜像已内置 ffmpeg（`backend/Dockerfile`）。
- `./dev.sh`：本机需自备。`sudo apt install ffmpeg`；无 root 时
  `pip install imageio-ffmpeg` 后 `ln -sf $(python3 -c 'import imageio_ffmpeg;print(imageio_ffmpeg.get_ffmpeg_exe())') ~/.local/bin/ffmpeg`。

## 可选初始化（首次或 RAG 为空）

```bash
cd /home/cypress/Pythontopia/deploy
bash scripts/init_rag_index.sh
bash scripts/seed_demo_classroom.sh
```

## 日志位置

- 本机后端：`/home/cypress/Pythontopia/.work/dev-backend.log`
- 容器：`cd deploy && docker compose logs -f backend|pythontopia-graph|pythontopia-rag`
