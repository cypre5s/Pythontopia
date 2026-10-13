# Pythontopia 安全源代码复核

- 日期：2026-10-13（Asia/Shanghai）
- 范围：当前工作树的 Web/后端/部署源配置、凭据存放元数据、相关自动化回归
- 不包含：ECS 登录、公网主动扫描、真实密码爆破、Piston 实际执行、真实 DeepSeek 调用
- 结论类型：代码与本地契约复核；不代表生产环境已经部署或运行时通过

## 已核对的防线

- `deploy/nginx/pythontopia.conf`：生产 HTTP 除 ACME challenge 外返回固定 HTTPS 地址的 308；配置 TLS 1.2/1.3、隐藏 Nginx 版本，并对登录/注册及班级加入入口限速。
- `backend/.../AgentStrategyController.java` 与 Tutor 内部附件端点：要求配置并匹配 `X-Internal-Token`，使用常数时间字节比较；附件读取还要求格式正确的 `X-Internal-Subject` 并按所有者读取。
- `SecurityConfig`：API 默认要求 JWT；生产发布契约关闭 OpenAPI/Swagger 匿名访问。JWT 当前由前端保存在 `localStorage`。
- Tutor 文本及工作流共用每账号、上海时区每日 30 次请求配额，conversationId 不会重置该额度。它限制请求数，不限制 token 数或实际费用；一次工作流请求仍可能触发多个模型调用。
- 判题代码采用独立 Piston 适配器；远程错误 fail closed，不回退应用进程内执行。此次仅跑了 Piston 适配器回归，没有对实际 Piston 服务执行学生代码。
- ASR、TTS、讯飞文生图和讯飞图片理解在属性类中固定关闭；Tutor 图片输入由 DeepSeek 视觉路径处理。该结论来自源代码和测试，未发起上游请求。
- `deploy/.env` 被 Git 忽略，当前权限为 `0600`。本复核没有读取或输出其中内容。提供给助手的 API key 已出现在对话中，真实模型调用前应在服务商控制台轮换。
- `npm audit --registry=https://registry.npmjs.org --audit-level=high` 返回 0 vulnerabilities；生产依赖审计亦为 0 vulnerabilities。
- NPC dialog/greeting 路由现限制 key 长度为 1–40，并只接受小写字母、数字、`_`、`-`；超长 key 回归确认在到达服务层前返回 404。

## 未关闭的风险

- CSP 仍只有 `frame-ancestors 'self'`，没有限制脚本、样式或连接来源；需结合 Phaser、Pyodide、Bilibili 和 SSE 做浏览器回归后再收紧。
- JWT 保存在 `localStorage`；若应用出现 XSS，令牌可被同源脚本读取。
- 每日 30 次请求上限不是成本上限，不能据此推断 DeepSeek 费用有硬上限。
- 聊天中出现过的 API key 尚未轮换；当前没有使用它发起真实请求。
- 本轮没有连接 ECS 或进行公网主动测试，因此 TLS、Nginx 限流、生产 Swagger 关闭和当前部署版本仍需从真实运行环境复核。
- Piston 只验证了适配器错误处理和 fail-closed 行为；实际隔离、网络阻断、超时和资源限制没有在本轮重测。

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
