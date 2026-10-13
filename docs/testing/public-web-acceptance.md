# 公网 Web 验收手册（public-web-acceptance）

> 适用范围：Pythontopia 网页端公网部署验收（部署目标 `https://47.120.29.62`；80 端口仅保留 ACME 并重定向到 HTTPS）
> 与本地集成预演（`http://127.0.0.1:5173`）。
>
> 配套产物：
>
> - 浏览器侧：`frontend/e2e/publicAcceptance.spec.ts`（Playwright）
> - 网络/部署侧：`deploy/scripts/public_acceptance.sh`（纯 shell + curl + nc）

---

## 1. 验收对象与分工

| 层 | 工具 | 覆盖内容 |
|----|------|----------|
| 部署/网络层 | `public_acceptance.sh` | HTTP 入口返回 308 HTTPS 且不向明文地址发送凭据、`/healthz`、静态资源缓存与类型头、内部端口公网封闭（8080/18080/18091/18092/15432/16379）、重定向不指回 `:8080`、SSE 连续请求无 502/504、本地包 sha256 与部署端校验和比对 |
| 浏览器/业务层 | `publicAcceptance.spec.ts` | 注册登录与认证跳转、town_hub 主城加载与 console 错误、`/guide` 导航/锚点/图片放大/双端布局、AI 向导多轮 SSE/澄清/计划/失败重试、向导→工坊深链与 KC 上下文、工坊真实响应、图书馆 RAG 三查询证据页码、训练场判题三态（AC/WA/RE）、复盘大厅与个人画像、深链直接刷新、错误渲染面不泄密 |

两层互补：**先跑 shell 脚本确认部署面健康，再跑 Playwright 验证业务闭环**。

## 2. 环境变量契约

| 变量 | 必填 | 含义 | 示例 |
|------|------|------|------|
| `BASE_URL` | 是（spec） | 目标地址；本地集成 `http://127.0.0.1:5173`，公网验收 `https://47.120.29.62` | `BASE_URL=https://47.120.29.62` |
| `PYT_E2E_USERNAME` | 二选一 | 演示账号用户名（凭证绝不写进仓库与脚本） | 由演示助教单独下发 |
| `PYT_E2E_PASSWORD` | 二选一 | 演示账号密码 | 同上 |
| `PYT_E2E_REGISTER` | 二选一 | `=1` 时通过 API 注册临时 QA 账号；与上面两项同给则注册指定用户名（已存在冲突自动容忍） | `PYT_E2E_REGISTER=1` |
| `PYT_QA_DIR` | 否 | 证据输出目录，默认仓库根 `qa-screens/`（已在 `.gitignore`） | |
| `PYT_SSE_ROUNDS` | 否 | shell 脚本 SSE 连续请求次数，默认 5 | |
| `PYT_SSE_MAX_TIME` | 否 | shell 脚本单次 SSE 最长等待秒数，默认 30 | |
| `PYT_REMOTE_SHA_URL` | 否 | 部署端校验和文件地址；缺省依次尝试 `<base>/release.sha256`、`<base>/build-info.json` | |

三个账号变量都缺省时：**Playwright 中所有需要登录态的用例整组跳过**（输出中文跳过原因），
仅「未登录重定向」「错误信息不泄密」等无凭证用例继续执行；shell 脚本的 SSE 检查退化为匿名
探测（401/403 视为代理层健康，重点排除 502/504）。

## 3. 执行方法

### 3.1 部署/网络层（shell）

```bash
# 语法自检（任何时候可跑）
bash -n deploy/scripts/public_acceptance.sh

# 公网验收（基础）
bash deploy/scripts/public_acceptance.sh https://47.120.29.62

# 公网验收（带演示账号，SSE 走认证链路；凭证从环境注入，不落盘）
PYT_E2E_USERNAME='<下发用户名>' PYT_E2E_PASSWORD='<下发密码>' \
  bash deploy/scripts/public_acceptance.sh https://47.120.29.62

# 部署一致性核对：第 2 个参数传本地构建包 sha256
bash deploy/scripts/public_acceptance.sh https://47.120.29.62 \
  "$(sha256sum dist.tar.gz | awk '{print $1}')"
```

退出码约定：全部通过（含 SKIP）退出 0；任一 FAIL 退出 1；参数错误退出 2。

**WSL2 TUN 代理注意（重要）**：本机若开启 TUN/fake-ip 代理，连「不存在的端口」也会假握手成功，
端口封闭性检查会失真。脚本内置哨兵端口探测，发现环境不可信时会把端口检查整组置为 SKIP 并提示；
此时请按仓库 `AGENTS.md`「阿里云 ECS 连接」一节为目标 IP 加直连路由后重测。

### 3.2 浏览器/业务层（Playwright）

前置：`frontend/` 内 Playwright 与浏览器内核已就绪（由集成负责人统一安装，本套件不做安装动作）。

```bash
cd frontend

# 本地集成预演（dev server 已起）
BASE_URL=http://127.0.0.1:5173 PYT_E2E_REGISTER=1 \
  npx playwright test e2e/publicAcceptance.spec.ts

# 公网验收（演示账号）
BASE_URL=https://47.120.29.62 \
PYT_E2E_USERNAME='<下发用户名>' PYT_E2E_PASSWORD='<下发密码>' \
  npx playwright test e2e/publicAcceptance.spec.ts

# 公网验收（临时 QA 账号：API 注册，跑完按 §5 清理）
BASE_URL=https://47.120.29.62 PYT_E2E_REGISTER=1 \
  npx playwright test e2e/publicAcceptance.spec.ts
```

按子集运行（`test.describe` 标题即过滤键，可独立跑任一子集）：

```bash
npx playwright test e2e/publicAcceptance.spec.ts -g "认证与会话"
npx playwright test e2e/publicAcceptance.spec.ts -g "主城 town_hub"
npx playwright test e2e/publicAcceptance.spec.ts -g "/guide 使用指南"
npx playwright test e2e/publicAcceptance.spec.ts -g "AI 向导 SSE"
npx playwright test e2e/publicAcceptance.spec.ts -g "向导→工坊深链"
npx playwright test e2e/publicAcceptance.spec.ts -g "工坊真实响应"
npx playwright test e2e/publicAcceptance.spec.ts -g "图书馆 RAG"
npx playwright test e2e/publicAcceptance.spec.ts -g "训练场判题三态"
npx playwright test e2e/publicAcceptance.spec.ts -g "复盘大厅与画像"
npx playwright test e2e/publicAcceptance.spec.ts -g "深链直接刷新"
npx playwright test e2e/publicAcceptance.spec.ts -g "错误信息不泄密"
```

并发说明：`frontend/playwright.config.ts` 有意默认单 worker 串行（保护并行开发中的后端、
保证截图套件顺序），公网验收沿用默认即可；确需提速可临时加 `--workers=2`，AI 编排/判题
链路对突发并发敏感，不建议更高。留存报告加 `--reporter=list,html`。

## 4. 证据收集规范

原则：**所有运行时证据只落本地、绝不入库；凭证与个人数据绝不写进任何证据文件。**

| 证据类型 | 收集方式 | 存放位置 |
|----------|----------|----------|
| 命令输出 | shell 脚本自动 `tee` 到带时间戳日志 | `qa-screens/public-acceptance-shell-<时间戳>.log` |
| 浏览器截图 | spec 在关键断言点自动截图（登录、主城、计划、深链、判题三态、RAG 等） | `qa-screens/public-acceptance-<时间戳>/<场景>.png` |
| console 错误 | spec 内收集器区分「致命/非致命」，非致命错误写入测试 annotations | Playwright 报告（`--reporter=html` 时在 `frontend/playwright-report/`） |
| 失败请求 | Playwright trace/报告中的网络面板；shell 日志中的状态码序列 | 同上 |
| 测试账号 ID | spec 自动写入每条用例的 annotations（`测试账号: qa-e2e-w<worker>-<随机>（本轮新注册：是/否）`） | Playwright 报告 |
| 本地报告 | 验收人汇总一份带时间戳的结论（建议命名 `acceptance-report-<日期>.md`） | `qa-screens/` 下，随目录一起被 gitignore |

要求：

1. `qa-screens/` 已在仓库根 `.gitignore` 中忽略；提交前用 `git status` 复核，**严禁 `git add` 证据目录**。
2. 截图与日志里不得出现明文密码、JWT、个人邮箱；spec 的泄密断言（`assertNoLeak`）只覆盖页面渲染面，证据文件由验收人自查一遍再归档。
3. 报告中引用证据时写相对路径 + 时间戳目录名，不要把图片内容粘进 git 跟踪的文档。
4. 失败用例必须附三件套：失败断言的中文消息、对应截图、相关请求状态码（或 shell 日志行号）。

### 4.1 临时账号清理步骤

`PYT_E2E_REGISTER=1` 产生的账号命名形如 `qa-e2e-w<worker>-<随机串>`，全部为 `STUDENT` 角色：

1. 从 Playwright 报告 annotations 中抄录本轮全部账号 ID 到验收报告；
2. 验收通过后，由持有运维权限的同学在数据库侧清理（用户表及其级联学习数据），或保留并在下一轮复用——两种选择都要写进验收报告；
3. 演示日前一天统一清扫一次 `qa-e2e-` 前缀账号，避免画像/班级统计被 QA 数据污染。

## 5. 部署后验收执行顺序（建议）

1. `bash -n deploy/scripts/public_acceptance.sh`（语法自检，秒级）；
2. `bash deploy/scripts/public_acceptance.sh https://47.120.29.62 <本地包sha256>` —— 全 PASS 再进下一步；
3. `BASE_URL=https://47.120.29.62 PYT_E2E_REGISTER=1 npx playwright test e2e/publicAcceptance.spec.ts -g "认证与会话"` 先验账号链路；
4. 跑全量 spec；AI 相关子集（向导/工坊）耗时最长，必要时单独跑并放宽 `--timeout`；
5. 汇总 `qa-screens/` 证据 + Playwright 报告，按 §4 写验收结论。

## 6. 已知前提与风险备注

- `/guide` 路由依赖使用指南页上线：本文档撰写时该路由尚未在 `frontend/src/app/router.ts` 注册（由并行开发流负责）。页面未上线时，`/guide` 相关用例会以「落到 404 兜底页」的中文断言失败——这是预期的验收拦截，不是用例缺陷；待页面合入后若锚点/放大层实现与通用选择器不匹配，只需在 spec 中收紧对应选择器。
- AI 向导/工坊依赖 LLM 真实出流，计划/澄清行为存在模型非确定性：spec 已为澄清分支做了「先答一轮再等计划」的兜底，仍偶发波动时优先重跑该子集并核对 graph 服务日志，而不是放宽断言。
- 训练场三态用例固定使用题目 `problemId=476`（圆面积计算，课程包 43 种子题）。若种子数据未导入或题目 ID 变化，先跑 `deploy/scripts/init_db.sh` 校验种子，再核对题库。
- RAG 检索对学生角色返回 403 时前端按设计回退本地关键字检索，UI 仍要求展示「第 N 页」证据页码；spec 把实际命中的服务端状态写进 annotations，验收人据此判断走的是服务端 RAG 还是前端回退。
- 端口封闭性检查在 TUN 代理环境下会整组 SKIP（见 §3.1），SKIP 不算通过——必须在可信网络下补测一次才能签收该项。
