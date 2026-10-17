# Pythontopia

**面向高校 Python 初学者的 Web 像素学习小镇**

Pythontopia 把课程阅读、资料问答、学习资源、编程练习、错误调试、复习和教师班级教学放进同一条可追踪的学习流程。游戏场景承载教学入口；学习进展来自课程证据、真实提交和后端记录。

[功能概览](#功能概览) · [学习流程](#学习流程) · [系统架构](#系统架构) · [本地开发](#本地开发) · [Docker 演示部署](#docker-演示部署) · [使用方法](#使用方法) · [测试](#测试) · [项目结构](#项目结构)

## 项目概览

- **当前主线：Web。** 学生与教师通过浏览器使用 Vue 3 网页端；本文介绍 Web 主链路。其他客户端位于 clients/，各自有独立说明。
- **单一小镇 Hub。** 学习入口收敛到 town_hub，减少多地图奔波。农场、作物和货币不属于当前主线玩法。
- **单一课程底座。** 内置 packId=43《Python 语言基础》种子：7 份文档、561 页、7 章、65 个知识点（KC）、46 个示例和 39 道可见题。
- **先保存学习证据。** 正式判题、阅读、资源使用和任务完成由服务端处理；浏览器中的 Pyodide 预览不算正式提交。
- **模型职责有边界。** DeepSeek 处理导师的文字或图片输入，回复为文本；掌握度和正式判题由系统规则与服务端记录决定。

## 功能概览

| 模块 | 学生或教师能做什么 | 关键行为 |
| --- | --- | --- |
| 中央小镇 | 通过建筑、NPC、任务和导航进入学习功能 | Phaser 单实例渲染 Web 小镇 |
| 课程与资料问答 | 阅读章节、示例和题目；向导师提问，可附图片 | RAG 从课程资料检索证据；模型回复是文本 |
| 学习工坊 | 生成讲解、练习、思维导图等学习资源 | Graph 服务运行结构化的资源生成与审核流程 |
| 训练场与调试 | 预览代码、正式提交、查看结果；失败后进入错误调试流程 | 正式判题使用服务端隔离执行；预览结果不会计入掌握度 |
| 学习分身与复习 | 查看画像、知识状态、近期变化和建议行动 | 由学习事件和判题结果更新；数据不足时不应被当成真实掌握证据 |
| 无尽远征 | 按知识点推进练习节点与 Boss 战 | Boss 表现由实际练习结果驱动，学习结果回到服务端学习记录 |
| 教师与管理后台 | 创建班级、发布任务、查看班级进度；审核题目、管理用户 | 教师和管理员页面受角色权限保护 |

### 学习流程

~~~mermaid
flowchart LR
    A["进入中央小镇"] --> B["阅读课程 / 导师问答"]
    B --> C["工坊生成学习资源"]
    C --> D["预览与正式练习"]
    D --> E{"正式判题"}
    E -->|通过| F["知识状态与任务进度更新"]
    E -->|未通过| G["错误调试 / 复习"]
    G --> D
    F --> H["学习分身与下一步建议"]
    H --> B
    H --> I["无尽远征"]
    I --> E
~~~

## 系统架构

~~~mermaid
flowchart TB
    Browser["浏览器：Vue 3 + TypeScript + Phaser 3"]
    Preview["Pyodide：本地代码预览"]
    API["Spring Boot REST API：Java 21"]
    Graph["Graph：FastAPI + LangGraph + DeepSeek"]
    RAG["RAG：FastAPI + BM25 / 本地 hash 检索"]
    Judge["隔离判题：Piston"]
    DB[("PostgreSQL / pgvector")]
    Cache[("Redis")]
    Course["课程包 43：页面、章节、KC、示例、题目"]

    Browser --> API
    Browser --> Preview
    API --> Graph
    API --> RAG
    API --> Judge
    API --> DB
    API --> Cache
    Graph --> RAG
    RAG --> Course
    API --> Course
~~~

| 层 | 技术与责任 |
| --- | --- |
| Web 客户端 | Vue 3、Vite、TypeScript strict、Pinia、Vitest；Phaser 3 负责小镇场景，游戏对象通过 adapter 与业务代码隔离 |
| 业务 API | Java 21、Spring Boot、Maven、JWT、Flyway；处理账号、课程、画像、学习事件、任务、班级与判题记录 |
| Agent 服务 | Python、FastAPI、LangGraph；执行导师、资源生成、路径规划及安全/证据审查相关流程 |
| 课程检索 | FastAPI；以 BM25 检索为主，使用本地 hash 向量兼容混合排序，不要求独立在线 embedding 服务 |
| 数据与缓存 | PostgreSQL（含 pgvector 扩展）保存业务数据；Redis 提供缓存等运行时能力 |
| 代码练习 | Pyodide 供浏览器内即时预览；正式提交交由独立隔离的 Piston 执行环境 |
| 课程种子 | data/course-pack-43/；后端数据库结构和种子由 Flyway 迁移初始化 |

导师和资源生成使用 DeepSeek 的 OpenAI-compatible API 配置。图片是**输入**，不是模型生成的输出；导师回答为文本。没有配置有效 API key 时，相关 AI 功能不可用。调用会向所配置的 DeepSeek 服务发送请求，并受服务商账户额度和限流约束。

## 本地开发

### 环境要求

- Docker Engine 与 Docker Compose v2
- Java 21 与 Maven
- Node.js 20 与 npm
- 使用 Graph / RAG 测试时，需要 Python 3.11 或 3.12
- 使用真实代码判题时，需要 Piston 隔离执行环境；本地可由 --sandbox 启动

### 启动 Web 全栈

在仓库根目录运行：

~~~bash
cp deploy/.env.example deploy/.env
~~~

在 deploy/.env 中配置 DeepSeek 变量后启动：

~~~dotenv
PYT_OPENAI_API_KEY=填入你自己的密钥
PYT_OPENAI_BASE_URL=https://api.deepseek.com
PYT_OPENAI_MODEL=按账户可用模型配置
PYT_OPENAI_MODEL_VISION=按账户可用的视觉模型配置
~~~

不要把真实密钥写入 README、提交到 Git，或粘贴到 issue 和日志中。deploy/.env 已作为本地配置使用；提交前仍需检查 git status。

~~~bash
./dev.sh
~~~

首次启动会拉起 PostgreSQL、Redis、Graph 和 RAG 容器，在本机启动 Spring Boot 与 Vite；需要时会安装前端依赖。默认开发配置关闭应用侧的 AI 日请求计数，方便本地调试，但不会绕过 DeepSeek 账号额度或服务商限流。

| 服务 | 本地地址 | 用途 |
| --- | --- | --- |
| Web | http://localhost:5173 | 浏览器使用入口，Vite 将 /api 转发到后端 |
| Backend | http://127.0.0.1:18080 | Web API；健康检查 /healthz |
| Graph | http://127.0.0.1:18091 | Agent 服务；健康检查 /healthz |
| RAG | http://127.0.0.1:18092 | 课程检索；健康检查 /healthz |
| PostgreSQL | 127.0.0.1:15432 | 开发数据库 |
| Redis | 127.0.0.1:16379 | 缓存与运行时数据 |

启动后可检查服务：

~~~bash
curl -fsS http://127.0.0.1:18080/healthz
curl -fsS http://127.0.0.1:18091/healthz
curl -fsS http://127.0.0.1:18092/healthz
~~~

按 Ctrl+C 会停止前端和本机后端，依赖容器与数据库卷保留。运行 ./dev.sh --down 会停止开发脚本启动的后端和依赖容器，但仍保留数据卷。**不要加 docker compose down -v，除非确实要删除本地数据库数据。**

### 本地正式判题

普通 ./dev.sh 不启动代码执行沙箱。需要验证服务端判题时运行：

~~~bash
./dev.sh --sandbox
~~~

该模式额外启动仅绑定本机回环地址的 Piston 和 Python runtime。浏览器 Pyodide 预览与正式判题是两条不同路径；正式提交必须经过后端和隔离执行环境。

### 全 Docker 演示

全 Docker 模式适合演示或集成环境；前端入口为 http://localhost:8080。先设置强随机密钥和 DeepSeek 凭据，再构建启动：

~~~bash
cd deploy
cp .env.example .env
~~~

至少为以下变量配置独立的随机值；不要在不同用途间复用密钥：

| 变量 | 用途 |
| --- | --- |
| PYT_JWT_SECRET | 用户会话签名，至少 32 字节随机值 |
| PYT_TEACHER_INVITE_CODE | 教师注册邀请码 |
| PYT_RAG_ADMIN_TOKEN | Backend、Graph、RAG 服务间鉴权 |
| PYT_ROUTE_EXECUTION_SECRET | 路由执行签名 |
| PYT_OPENAI_API_KEY | DeepSeek API 调用 |

可在本机用 openssl rand -hex 32 生成随机值；教师邀请码可用 openssl rand -hex 12 生成。然后启动：

~~~bash
docker compose up -d --build
bash scripts/init_rag_index.sh
bash scripts/demo_smoke.sh
~~~

RAG 索引初始化通常只需在新环境或索引为空时执行。演示脚本会检查 Web、Backend、Graph、RAG 健康状态和课程包读取。检查服务状态与日志：

~~~bash
docker compose ps
docker compose logs -f backend pythontopia-graph pythontopia-rag frontend
~~~

全 Docker 默认编排不包含本地 Piston 沙箱。若需正式代码判题，还需接入单独隔离、鉴权的 Piston 服务并配置对应 PYT_PISTON_* 变量。生产环境的 HTTPS、备份、升级和回滚流程见 [deploy/README.md](./deploy/README.md)；不要将 Backend、Graph、RAG、PostgreSQL 或 Redis 端口直接暴露到公网。

## 使用方法

### 学生

1. 打开 Web 地址，创建学生账号或登录。学生注册后进入中央小镇。
2. 从小镇进入课程章节，阅读内容或向导师提问。可以在导师输入中附图片；回答以文本形式返回。
3. 在学习工坊生成与当前课程相关的讲解、练习或其他资源。
4. 在训练场先做浏览器预览；需要记录正式结果时提交到服务端判题。
5. 失败后查看错误反馈，按需进入调试或复习；通过学习分身查看已记录的学习状态与建议。
6. 从无尽远征进入 KC 练习与 Boss 节点；正式练习的结果由服务端处理。

知识掌握度来自后端认可的学习证据。自评和 AI 建议可用于偏好或后续建议，但不能代替正式判题结果。

### 教师与管理员

- 教师注册需要部署者配置的教师邀请码；随后可创建班级、邀请学生、发布班级任务并查看班级学习情况。
- 管理员页面提供账号管理和题目审核等权限受控功能。
- 本地演示数据由真实 API 写入数据库。需要时可运行演示 seed 脚本；脚本会在终端输出一次性测试账号信息。演示结束后使用 teardown 脚本清理。

~~~bash
cd deploy
bash scripts/seed_demo_classroom.sh
# 演示结束后
bash scripts/teardown_demo.sh
~~~

不要把共享测试账号、教师邀请码或一次性密码部署到公网演示环境。

## 测试与构建

按修改的模块选择验证命令。以下命令不会自动启动完整依赖栈：

~~~bash
# Web
cd frontend
npm ci
npm run typecheck
npm run lint
npm run test

# Spring Boot
cd ../backend
mvn clean compile -DskipTests
mvn test
~~~

Graph 和 RAG 使用 Python 3.11 或 3.12。分别在服务目录创建虚拟环境、安装开发依赖并运行测试：

~~~bash
cd services/pythontopia-graph
python3.11 -m venv .venv  # 也可使用 python3.12
.venv/bin/python -m pip install -e ".[dev]"
.venv/bin/python -m pytest -q

cd ../pythontopia-rag
python3.11 -m venv .venv  # 也可使用 python3.12
.venv/bin/python -m pip install -e ".[dev]"
.venv/bin/python -m pytest -q
~~~

涉及数据库迁移时还需要验证空库初始化与课程种子；涉及判题时分别验证通过、失败和运行错误。测试报告和覆盖边界见 [docs/testing/test_report.md](./docs/testing/test_report.md)，历史结果不代表当前 checkout 的新测试结果。

## 项目结构

~~~text
backend/                     Spring Boot Web API、数据库迁移与业务服务
frontend/                    Vue 3 + Phaser Web 客户端
services/pythontopia-graph/  FastAPI + LangGraph Agent 服务
services/pythontopia-rag/    课程资料检索服务
data/course-pack-43/         packId=43 课程种子与来源说明
data/npcs/                   NPC 静态配置
deploy/                      Docker Compose、环境变量样例与部署脚本
docs/                        架构、运行、测试、演示与合规文档
clients/                     Web 之外的客户端，各端独立维护
assets/                      像素素材来源与授权记录
~~~

## 深入文档

| 文档 | 内容 |
| --- | --- |
| [AGENTS.md](./AGENTS.md) | 项目约束、技术栈、目录约定与开发命令 |
| [docs/operations/dev-startup.md](./docs/operations/dev-startup.md) | 本地启动方式与数据库凭据排查 |
| [deploy/README.md](./deploy/README.md) | Compose 演示、生产部署、HTTPS、备份和回滚 |
| [docs/architecture/system_design.md](./docs/architecture/system_design.md) | 服务边界、数据流和关键时序 |
| [docs/presentations/demo_script.md](./docs/presentations/demo_script.md) | 七分钟演示流程与备用步骤 |
| [docs/testing/test_report.md](./docs/testing/test_report.md) | 测试范围、未覆盖项与复测命令 |
| [docs/clients/README.md](./docs/clients/README.md) | Web、Android、Windows、微信客户端概览 |
| [docs/clients/api-contract.md](./docs/clients/api-contract.md) | 多客户端共用的后端 API 契约 |
| [data/course-pack-43/export/README.md](./data/course-pack-43/export/README.md) | packId=43 种子统计与导出来源 |
| [LICENSE-ART.md](./LICENSE-ART.md) | 美术、音效、字体等素材来源和授权 |

## 安全与授权

- 不要提交 .env、API key、JWT 签名密钥、服务间令牌、邀请码或生产数据。
- DeepSeek 图片问答会把用户提供的图片和文字发送到配置的模型服务；仅上传适合发送给该服务的内容。
- 正式代码执行必须使用隔离、鉴权的判题服务，不要把本地开发沙箱暴露到公网。
- 课程资料、项目代码与第三方素材可能适用不同授权。当前根目录 [LICENSE](./LICENSE) 写明 Source-Available / All rights reserved；第三方素材按 LICENSE-ART.md 及各素材目录中的许可文件执行。项目对代码层 MIT 与发布包 CC-BY-SA 的目标记录尚未与根 LICENSE 对齐；在授权文件统一前，不应将项目整体描述为 MIT 开源。

欢迎先阅读架构与运行文档，再根据你的环境配置服务。
