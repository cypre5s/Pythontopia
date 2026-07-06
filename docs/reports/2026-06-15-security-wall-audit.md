# Pythontopia 公网安全围墙审计报告

- 审计目标：`http://47.120.29.62`
- 审计时间：2026-06-14 至 2026-06-15（Asia/Shanghai）
- 范围：公网入口、认证鉴权、输入处理、上传、浏览器策略、判题沙箱、内部服务、HTTP 协议、部署与依赖
- 授权：项目所有者明确授权公网主动测试及受控暴力测试
- 原始证据：`.work/security/public-active-results-v3.jsonl`、`.work/security/login-burst-stable-route.json`、`.work/security/read-pressure-stable-route.json`、`.work/security/npm-audit.json`

## 执行摘要

确定性攻击目录共 1000 项，覆盖 10 个域、每域 100 项。公网终验结果为 898 项通过、102 项失败；失败项经去重后归并为 8 个确认问题和 3 个加固项。另执行两组有硬上限的暴力测试：

- 登录爆破：专用审计账号，500 请求，并发 25；500 次均返回 401，0 次 429，正确密码随后仍可登录。
- 只读压力：`GET /api/course/pack`，500 请求，并发 25；500 次均返回 200，293.1 req/s，中位延迟 81.35ms，结束后健康检查 76ms。

测试机 WSL TUN 曾自动撤销公网直连路由，造成部分请求超时。相关超时不计为服务端漏洞；暴力测试数据均在路由守护下重新取得。

## 确认问题

| ID | 等级 | 问题 | 证据 | 修复状态 |
|---|---|---|---|---|
| SW-01 | 严重 | 登录、JWT 和全部业务流量仅使用 HTTP，公网 443 未提供 TLS | 公网只监听 80；入口为 `http://47.120.29.62` | 待修复 |
| SW-02 | 高 | 登录接口无限流、无失败锁定，可持续消耗密码哈希资源 | 500/500 返回 401，0 个 429，中位 1.06s | 待修复 |
| SW-03 | 高 | 学生 JWT 可读取 `/api/internal/agent-strategy/{agentName}` | 多个载荷以学生身份返回 200 和策略 JSON | 待修复 |
| SW-04 | 高 | 前端生产依赖 `axios 1.7.9` 命中已知高危公告 | `npm audit`：生产直依赖可升级至 1.17.0 | 待修复 |
| SW-05 | 中 | OpenAPI/Swagger 在生产匿名公开，暴露完整 API 面与内部容器 URL | `/v3/api-docs` 返回 200 | 待修复 |
| SW-06 | 中 | 多类恶意或畸形输入被通用异常处理器映射为 500 | 枚举参数、路径 ID、缺少 multipart part、不可接受 `Accept` | 待修复 |
| SW-07 | 中 | 超长 NPC key 可触发 `DataIntegrityViolationException` 并返回 500 | `npc-key-path-injection` 长载荷返回 500 | 待修复 |
| SW-08 | 低 | Nginx 响应头及错误页泄露精确版本 `nginx/1.18.0 (Ubuntu)` | 前后端入口版本检查 20/20 失败 | 待修复 |

## 加固项

| ID | 等级 | 项目 | 说明 |
|---|---|---|---|
| SW-H01 | 中 | CSP 过于宽松 | 当前只设置 `frame-ancestors 'self'`；JWT 又存于 `localStorage`，一旦出现 XSS 影响较大。需在不破坏 Pyodide、Phaser、Bilibili 与 SSE 的前提下逐步收紧。 |
| SW-H02 | 中 | 共享演示账号 | 前端公开展示固定演示账号密码，任何访问者可共享和污染其学习数据。应改为可重置的隔离演示身份或取消公开密码。 |
| SW-H03 | 中 | 开发依赖漏洞 | `npm audit` 共报 2 critical、8 high、4 moderate；Vitest/Vite/jsdom 等多数为开发链依赖，需单独升级和回归，不与紧急生产依赖修复混为一谈。 |

## 已验证防线

- JWT 空算法、坏签名、过期和角色篡改均未绕过认证。
- 跨用户 submission/upload 访问与删除未成功。
- 教师端 dashboard/members 端点拒绝学生角色。
- 恶意 Origin、`null` Origin 和伪 localhost 子域未获得 CORS 放行。
- graph/rag 未直接暴露到公网，仅由反向代理返回 SPA/拒绝响应。
- `/.env`、`/.git/config`、source map、Actuator 未泄露真实秘密。
- 判题探针未观察到 `/etc/passwd`、环境变量或进程信息回传。
- 只读接口在 500 请求、并发 25 下无错误，服务与全部容器保持健康。

## 修复顺序

1. 开通 HTTPS 并将 HTTP 重定向到 HTTPS。
2. 为登录/注册设置入口限流和 429 响应。
3. 对内部策略接口实施内部令牌鉴权。
4. 生产关闭 Swagger/OpenAPI，关闭 Nginx 版本泄露。
5. 将输入类型、multipart 和内容协商异常映射为 4xx，并验证 NPC key 长度。
6. 升级 Axios，重新执行前端类型、测试与依赖审计。
7. 部署到 ECS，重跑针对性攻击、登录爆破和公网健康检查。
