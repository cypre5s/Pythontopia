# Pythontopia 安全与本地运行复核

- 初审日期：2026-10-13（Asia/Shanghai）；增量复核至 2026-10-14。
- 范围：Web/后端/部署源配置、凭据存放元数据、自动化回归及本地 Piston/DeepSeek 调用证据。
- 不包含：ECS 登录、公网主动测试、生产 Piston 内核/逃逸验证、密钥轮换后的计费调用。
- 结论类型：本地代码与运行记录；不代表生产环境已经部署或运行态通过。

## 已核对的防线

- `deploy/nginx/pythontopia.conf`：生产 HTTP 除 ACME challenge 外返回固定 HTTPS 地址的 308；配置 TLS 1.2/1.3、隐藏 Nginx 版本，并对登录/注册及班级加入入口限速。
- `backend/.../AgentStrategyController.java` 与 Tutor 内部附件端点：要求配置并匹配 `X-Internal-Token`，使用常数时间字节比较；附件读取还要求格式正确的 `X-Internal-Subject` 并按所有者读取。
- `SecurityConfig`：API 默认要求 JWT；生产发布契约关闭 OpenAPI/Swagger 匿名访问。JWT 当前由前端保存在 `localStorage`。
- Tutor 文本及工作流共用每账号、上海时区每日 30 次请求配额，conversationId 不会重置该额度。它限制请求数，不限制 token 数或实际费用；一次工作流请求仍可能触发多个模型调用。
- 判题代码采用独立 Piston 适配器；远程错误 fail closed，不回退应用进程内执行。固定 Piston API v3.1.0 的源码路径实际使用低权 uid、`prlimit` 和可选 `nosocket`，没有容器级 cgroup/namespace 隔离。临时受限容器的运行证据见下方；生产内核、逃逸与部署策略仍未验证。
- ASR、TTS、讯飞文生图和讯飞图片理解在属性类中固定关闭；Tutor 图片输入走 DeepSeek 视觉路径。此前已完成真实 Tutor 文本与合成图片输入请求，本次增量复核没有发起上游请求。
- `deploy/.env` 被 Git 忽略，文件权限为 `0600`；本复核只检查存在状态，没有读取或输出值。对话中出现过的 API key 尚未确认已轮换，后续真实请求前应在服务商控制台轮换。
- `npm audit --registry=https://registry.npmjs.org --audit-level=high` 返回 0 vulnerabilities。Python 服务生产依赖审计与临时镜像构建结果见下方 2026-10-14 部分；未运行 OS 层镜像扫描，也没有部署镜像。
- NPC dialog/greeting 路由现限制 key 长度为 1–40，并只接受小写字母、数字、`_`、`-`；超长 key 回归确认在到达服务层前返回 404。

## 未关闭的风险

- CSP 已覆盖 SPA 文档，并把 `unsafe-eval` 限定在 Pyodide worker；本地 Chromium 已验证 Pyodide 与 PDF.js。仍需在生产入口和更多浏览器复核；`style-src 'unsafe-inline'` 是 Vue 动态样式与像素字体注入所需的兼容例外。
- JWT 保存在 `localStorage`；若应用出现 XSS，令牌可被同源脚本读取。
- 每日 30 次请求上限不是成本上限，不能据此推断 DeepSeek 费用有硬上限。
- 聊天中出现过的 API key 尚未确认轮换；此风险仍在，当前增量没有使用密钥发起新请求。
- 本轮没有连接 ECS 或进行公网主动测试，因此 TLS、Nginx 限流、生产 Swagger 关闭和当前部署版本仍需从真实运行环境复核。
- 本地 Piston 探针不等同于生产容器内核/网络隔离验证；实际生产威胁模型仍需部署环境复核。Piston v3.1.0 不校验 Bearer token，生产需确保服务端或代理实际鉴权，并用私网 ACL 限制网络访问。

## 本地验证证据

- 后端相关安全、远征、Twin、判题及媒体测试：定向 224 tests，0 failures、0 errors；空库 Testcontainers 迁移至 V211。
- `deploy/scripts/test_deepseek_model_contract.sh`、`test_production_ai_readiness_contract.sh`、`test_piston_http_host_contract.sh`、`test_public_demo_account_contract.sh`、`test_https_forwarded_proto_contract.sh`、`test_dev_compose_security_contract.sh`、`test_install_release_secret_contract.sh` 均通过。
- Web 全量 Vitest：256 files / 2522 tests 通过；TypeScript typecheck 通过。
- 这些结果验证源代码与本地环境，不构成公网或生产运行态的安全证明。

## 2026-10-14 增量复核

- 费曼学伴的教学与找茬入口已纳入相同账号每日 30 次计数；额度检查在调用学习服务前执行。Tutor workflow 的编辑器代码由前端、Java 和 Graph 限制为最多 20,000 字符，并在 Tutor 提示中标为不可信学生输入。
- 工作坊上传先检查个人存储额度，再解析文件；生产 Compose 固定关闭 Graph 可选内部鉴权，ECS 预检和发布安装拒绝公开开发数据库密码。
- 验证：本轮后端相关单元及 PostgreSQL/Testcontainers 集成 192 项通过；Web 全量 260 文件 / 2506 项与 TypeScript typecheck 通过；Graph 全量 1816 passed / 1 skipped；生产 AI/readiness 与安装密钥契约脚本通过。
- 本次仍未连接 ECS、公网测试或启动 Piston 实际执行；对话中暴露过的 DeepSeek key 未用于请求，轮换前不进行计费性能采样。每日请求配额不是 token 或费用上限；JWT localStorage、CSP 来源限制及 Graph 依赖锁/漏洞扫描仍需后续处理。

## 2026-10-14 复核后修复

- Boss 判题要求 `attemptId`；题目需先确认属于当前用户，再建立幂等 claim，因此随机无效题目 ID 不再永久增长幂等表。重复 Boss 学习事件使用 `boss-attempt:<userId>:<UUID>` 同时保护掌握度和画像投影；画像 outbox 的幂等键是全局唯一，同一 UUID 被不同用户提交时仍各自产生投影事件。
- Graph 的 `sandbox_unavailable` 明确作为基础设施错误处理：调用现有 Java/Piston 兜底；若本地沙箱也不可用，返回 503，不扣远征判题额度、不更新学习画像。
- V212 新增全站每日 300 次 AI 入口请求上限，与每账号每日 30 次共同在一个事务中原子预留，覆盖匿名批量注册对单账号额度的绕过。该表以数据库唯一日行跨实例串行化；达到全站上限后当日停止所有标注端点。
- 账号与全站额度预留共用一次读取的上海日期，避免恰逢午夜时被分别计入相邻两天；模拟跨午夜用例已复现并回归通过。
- 验证：Boss Controller/Service、额度与画像监听器共 71 项单测通过；PostgreSQL/Testcontainers 3 项通过，包含 40 个账号并发提交 400 次时仅放行 300 次，以及幂等事件回放只更新一次画像/掌握度；Flyway 空库迁移至 V212 成功。
- 后续定向回归：BossService 与 AI 额度路由覆盖 45 项单测、画像 outbox PostgreSQL 集成 2 项通过；包含跨用户共享 attempt UUID 和图片生成/图片理解/TTS/ASR 保持关闭的断言。
- 剩余风险：全站限制计数的是标注入口操作，不是 DeepSeek provider 外发次数/token/金额；单次操作可在 Graph 内重试或多轮生成。Android/Windows Boss 调用尚未携带必填 `attemptId`，此次遵照 Web 优先范围未改客户端。未访问 ECS/公网，也未用真实 Piston 故障注入验证运行态行为。对话中暴露的 DeepSeek key 仍应轮换。

## 2026-10-14 Python 生产依赖与内部鉴权复核

- 生产依赖范围按 `services/pythontopia-graph/Dockerfile` 的 `.[browse]` 与 `services/pythontopia-rag/Dockerfile` 的 `.` 重建；不包含 Graph 的 memory/eval/optimize/observability extra，也不包含 RAG 的 dev/graph extra。修复前 pip-audit 报 Graph 15 条、RAG 31 条记录，分别涉及 2 和 6 个包；按包与公告 ID 去重后为 8 和 18 对。
- FastAPI 约束调整至 `>=0.143,<0.144`，隔离解析结果为 FastAPI 0.143.0 / Starlette 1.8.0；Graph `json-repair` 调整至 `>=0.60.1,<0.61`（[GHSA-xf7x-x43h-rpqh](https://github.com/advisories/GHSA-xf7x-x43h-rpqh)）；RAG 移除了全服务没有导入的 LangGraph，并将 `pyarrow` 调整至 `>=23.0.1,<24`（[PYSEC-2026-113](https://osv.dev/vulnerability/PYSEC-2026-113)）。修复后对两个干净 Python 3.11 生产依赖目录重新运行 `pip-audit`，均为 `No known vulnerabilities found`。
- Graph 内部鉴权曾用 `request.url.path` 判断公开白名单；恶意请求 `POST /agents/curriculum/map` 加 `Host: example.com/healthz?bypass=` 在受影响 Starlette 上曾返回 200，详见 [Starlette GHSA-86qp-5c8j-p5mr](https://github.com/Kludex/starlette/security/advisories/GHSA-86qp-5c8j-p5mr)。现在白名单和鉴权日志均使用 ASGI 原始 `scope["path"]`；回归在旧依赖及 FastAPI 0.143.0 / Starlette 1.8.0 下均返回 401。
- 验证命令：`uv pip install --python <fresh-python-3.11-env> 'services/pythontopia-graph[browse]'`、`uv pip install --python <fresh-python-3.11-env> services/pythontopia-rag`，移除本地项目元包后对对应 `site-packages` 执行 `uv tool run --from pip-audit pip-audit --strict --progress-spinner off --desc off --aliases off --path <site-packages> --format json`；两次均退出码 0。Graph 全量 pytest 为 1816 passed / 3 skipped，RAG 为 158 passed / 2 skipped；两侧 `ruff check .` 通过。
- 严格 Mypy 当前未通过：Graph 报 66 个类型错误、涉及 30 个文件；RAG 在 `routes/query.py` 报 2 个缺少返回类型标注。此次没有为依赖审计扩展到无关类型修复。
- 原始镜像路径的 `docker build` 在 apt 阶段受容器代理和 Aliyun 下载速度影响未完成。之后以仅存在于 `/tmp` 的 Dockerfile 变体重建：Python 索引改用官方 PyPI，apt 保留官方 Debian 源并通过 7892 代理；项目 Dockerfile 未改。Graph（`.[browse]`）与 RAG（`.`）镜像都构建成功，启动后 `/healthz` 均为 HTTP 200；Graph 的 Chromium 无头启动通过。容器内 FastAPI 0.143.0 / Starlette 1.8.0、Graph json-repair 0.60.1、RAG PyArrow 23.0.1 与隔离依赖审计一致；镜像配置中的大小写代理变量均为空。RAG 未连接数据库，按设计以空索引启动；Graph 未注入 DeepSeek 密钥，仅验证 healthz，未验证 readyz 或模型调用。
- 容器构建和启动不等于原始阿里云索引配置的构建，也不是 OS 层镜像漏洞扫描、生产发布或 ECS 核验；两套 Python 包依赖的 pip-audit 均无已知漏洞。

## 2026-10-14 Web CSP 收紧与浏览器复核

- `frontend/nginx.conf` 为 `/index.html` 实际文档响应设置 CSP：同源脚本与连接、禁止 object 与 iframe、限制表单/基础 URL/资源类型；图片仅允许同源、`data:`、`blob:` 和 B 站封面域。Vue 动态样式继续使用 `unsafe-inline`。
- Pyodide Emscripten worker 确实依赖 `eval()`，因此只有哈希 worker 脚本响应单独允许 `unsafe-eval` 与 WASM eval；主文档 CSP 没有通用 `unsafe-eval`。生产边缘 Nginx 移除重复的宽松 CSP，保留 `X-Frame-Options` 供非 SPA 响应使用。
- 新增 `deploy/scripts/test_dev_compose_security_contract.sh` 断言 SPA 文档、Pyodide worker 和边缘响应的 CSP 规则。脚本通过；临时 Nginx 容器对 `/`、`/admin/users`、`/index.html` 实测 HTTP 200 且带策略，worker 响应另带隔离策略。
- Chromium 在该 Nginx 配置下加载登录页、运行 Pyodide `print(2+3)` 得到 `5`，加载 PDF.js 并解析一页 PDF；浏览器 CSP violation 为 0。未做 ECS、公网或其他浏览器验证。
- 参考：[W3C CSP Level 3](https://www.w3.org/TR/CSP3/)、[NGINX add_header 继承规则](https://nginx.org/en/docs/http/ngx_http_headers_module.html)、[Chrome WASM CSP 指引](https://developer.chrome.com/docs/iwa/developer-policy)。

## 2026-10-14 最新增量

- 已有本地运行记录包含 DeepSeek 真实文本和合成 PNG 图片输入调用；两类调用都返回非空文本，视觉答复识别合成图中的黄色背景与蓝色图形。本次没有发起新的 DeepSeek 请求。密钥只核对过本地非空状态，未读取值；尚未确认其已轮换。
- 本地 Piston v3.1.0 曾以 uid 1001 实际运行学生样例；读取 `/etc/shadow` 与创建 socket 被拒绝，短时限程序由 SIGKILL 中止。Java 适配器已兼容该 signal-only 返回；该结果不是生产内核逃逸测试。
- AutoGen Tutor CHAT 增加完整 Python 函数、stdin/stdout 脚本及主入口代码的本地 AST 清理；Graph 全量回归 `1822 passed, 1 skipped`。这是结构启发式，不保证拦截所有等价答案表述；未调用模型进行红队复测。
- 每账号每日 30 次、全站每日 300 次限制的是 AI 入口操作，不是模型请求、token 或金额硬上限。安全范围内没有访问 ECS/公网，也没有生产 Piston 威胁验证。

## 2026-10-14 本地 Piston 容器收紧与边界实测

- 用固定的 Piston API v3.1.0 源码确认：执行器将子进程降至 uid/gid 1001，使用 `prlimit` 设置进程/文件/文件大小限制；禁网时通过 `nosocket`，超时由 API 进程发送 SIGKILL。它没有自行建立 Isolate、namespace 或 cgroup，Piston 的 API Bearer token 也没有验证逻辑。
- Compose 本地 sandbox 去掉 `privileged`，启用只读根文件系统、`no-new-privileges`，仅保留 `CHOWN/DAC_OVERRIDE/SETGID/SETUID/KILL` 五项 capabilities；容器限制 768 MiB 内存且禁 swap、1 CPU、128 PIDs，任务目录和 `/tmp` 各自 256 MiB tmpfs；Piston 同时封顶 2 个任务、每进程 256 MiB、10 秒运行时限并显式禁网。
- 临时探针容器用上述限制配置，另将 runtime 包目录挂为只读。实际运行 `print(42)` 成功；读取 `/etc/shadow`、创建 socket 均返回 `PermissionError`；500 ms 无限循环收到 `SIGKILL` 后 API 仍可继续判题；请求 1 GiB 运行内存被服务端拒绝，原因是超过 256 MiB 封顶。最小 capabilities 不包含 `SYS_ADMIN`。
- 当前 Compose Piston 的 `/api/v2/runtimes` 对无 Authorization 请求和伪造无效 Bearer 请求都返回 HTTP 200，确认本地 API 没有令牌鉴权。
- Compose 安全契约测试已覆盖 loopback 发布、特权关闭、capability 白名单、只读根文件系统和容器资源上限。
- 未验证：真实生产 Piston 的 API 鉴权、资源上限、容器/内核隔离和逃逸；生产仍必须私网 ACL 或由代理实际验证令牌。本地受限容器不是独立安全审计或生产证明。
