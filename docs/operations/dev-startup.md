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

`./dev.sh` 默认关闭 Tutor/AI 入口的账号级与全站每日请求计数，便于本地连续调试模型；部署配置默认保留每账号 30 次、全站 300 次/日。开发栈无限请求仍受 DeepSeek 账号额度和供应商限流约束。

通过 Graph 生成代码题时需要 Piston 自测环境，使用 `./dev.sh --sandbox` 启动并同步本地 Piston URL 与 Python 版本到 Graph/Java；普通 `./dev.sh` 不配置该沙箱。Graph 出题在未配置时会直接返回 `sandbox_unavailable`，不会为同一基础设施故障重复调用模型。

开发脚本可能为当前运行生成未写入磁盘的临时 `PYT_RAG_ADMIN_TOKEN`，并同步给 Java、Graph 和 RAG。开发栈运行期间不要单独用 `docker compose` 重建 Graph/RAG，否则容器会重新读取 `.env`，造成服务间令牌失配；需要重启时使用 `./dev.sh` 统一重启。失配会让工坊意图路由返回 401 并进入不可用提示。

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

## 历史记录：讯飞多媒体代理（已停用）

早期故事板曾通过讯飞生成图片和配音，因此需要 JVM 代理。当前项目只调用 DeepSeek 的文本与视觉输入接口，导师响应仅输出文本；图片生成、语音识别和配音服务保持关闭。`./dev.sh` 会清除旧的 `PYT_SPARK_*` 与 `PYT_XFYUN_PROXY*` 环境变量，不需要配置讯飞凭据或代理。

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
