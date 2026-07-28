# Pythontopia ECS 公网工作流详细验收报告

> 生成时间：2026年7月28日 18:46:47
> 运行开始：2026年7月28日 17:40:22
> 公网入口：`https://47.120.29.62`
> 生效 release：`/opt/pythontopia/releases/20260728-173503`
> 发布包 SHA-256：`32d1717dc75e92eb3d8828f3c63a208f80fd5872a23b13ba7299fcdc96d9e1bc`
> 最终结论：**PASS**

## 1. 验收口径

- 使用真实公网入口、真实 Nginx、真实 Spring/Graph/RAG/PostgreSQL 与无头 Chromium；不 mock 业务 API。
- 单 worker 串行执行，避免共享学习状态互相污染。
- 最终矩阵使用 `--retries=0`；任何偶发失败都会原样计入，不由自动重试掩盖。
- 本报告把每个 Playwright test 视为一个可复核工作流样例或工作流检查点，并逐项列出。

## 2. 总体结果

| 样例总数 | PASS | FAIL | FLAKY | SKIP | 总耗时 | 结论 |
|---:|---:|---:|---:|---:|---:|---|
| 227 | 227 | 0 | 0 | 0 | 31.0 min | PASS |

## 3. 发布与运行态证据

| 检查项 | 最终证据 |
|---|---|
| 生效版本 | `/opt/pythontopia/releases/20260728-173503` |
| 发布包 | `output/packages/pythontopia-web-20260728T093105Z.tar.gz`（723,071,498 bytes） |
| SHA-256 | `32d1717dc75e92eb3d8828f3c63a208f80fd5872a23b13ba7299fcdc96d9e1bc`，上传前后相同 |
| 包内容门禁 | 4,090 个常规文件；allowlist、逐文件哈希、秘密扫描、顶层结构与 4 个镜像 tar 检查通过 |
| 回滚点 | 上一 release `/opt/pythontopia/releases/20260728-170619`；备份 `/opt/pythontopia/backups/20260728-173503` |
| 容器 | frontend、backend、graph、RAG、PostgreSQL、Redis 共 6 个，全部 `healthy` |
| 健康检查 | 公网 `http://47.120.29.62/healthz` 与 `https://47.120.29.62/healthz` 均返回 `ok` |
| 数据库迁移 | Flyway 最新 `V190__drop_retired_economy_and_fast_travel`，`success=true` |
| pack 43 种子 | 7 文档 / 561 页 / 7 章 / 65 KC / 46 示例 / 39 题 |
| HTTPS 证书 | Let's Encrypt `YE1`；有效期 `2026-07-27 03:41:34Z` 至 `2026-08-02 19:41:33Z` |
| 关键错误扫描 | 最终发布后 frontend/backend/graph/RAG 的 `ERROR/FATAL`、`Read timed out`、`WORKSHOP_GRAPH_UNAVAILABLE`、`FEYNMAN_TUTEE_UNAVAILABLE` 命中均为 0 |

## 4. 双协议与矩阵外真实闭环

| 协议 / 专项 | 样例 | 结果 | 重试 | 耗时 / 说明 |
|---|---:|---:|---:|---|
| HTTPS 最终公网矩阵 | 227 | 227 PASS | 0 | 31.0 分钟；本报告逐项列出 |
| HTTPS 认证 | 4 | 4 PASS | 0 | 未登录守卫、注册后表单登录、guestOnly 回跳、快速体验登录；包含在 227 项内 |
| HTTP 公共核心对照 | 27 | 27 PASS | 0 | 13.0 分钟；与 HTTPS 公共核心使用同一测试合同 |
| HTTP 故障恢复定点 | 1 | 1 PASS | 0 | 2.3 分钟；首请求断网、失败态可见、第二次 SSE 200 并完成 |
| HTTPS 费曼真闭环 | 1 | PASS | 0 | 新学生正式 WA → 真实误区 → 找茬 → 教学产码 → 沙箱判题通过 → 掌握度回流 |

HTTP 首轮公共核心为 26/27：唯一未通过项在业务重试已经返回完整助手回复和学习计划后，
因测试总预算 240 秒先于其允许的单回合 300 秒预算结束。Trace 显示第二次 SSE 为 200，
输入框在 runner 宣告超时约 8 秒后恢复；服务器对应请求均为 200，容器保持健康。修复只把该
“首屏准备 + 一次故障注入 + 一次真实 Agent 重试”的总观察预算调为 360 秒，未修改业务代码、
故障注入或断言。随后定点 1/1、完整 HTTP 27/27 均在 `--retries=0` 下通过；首轮失败 JSON/HTML
仍单独保留，未覆盖或计入最终结果。

费曼专项最终账号为 `qa-feynman-1785235444`，页面结果为
“教学成功：学弟代码通过沙箱运行，掌握度已回流。”；课件外自由概念保持 `NA` 且判题按钮禁用，
未用通识内容冒充课件掌握度。

覆盖范围包括：认证与会话、`town_hub` 主城、主线地图、指南桌面/移动交互、AI 向导多轮 SSE、
网络失败可见化与恢复、向导到工坊深链、百晓意图路由与 C3 受限候选、Workshop 资源生成、
RAG 页码证据、AC/WA/RE 判题、资源读测判闭环、补救/换资源/重规划、Bug Mine、画像与复盘、
深链刷新、安全错误面、教师建班与学生证据回流。

## 5. 文件级覆盖

| 测试文件 | 样例数 | PASS | FAIL | FLAKY | SKIP |
|---|---:|---:|---:|---:|---:|
| `agenticDeepLoop.spec.ts` | 32 | 32 | 0 | 0 | 0 |
| `agenticDeepMatrix.spec.ts` | 153 | 153 | 0 | 0 | 0 |
| `baixiaoAgentRouting.spec.ts` | 5 | 5 | 0 | 0 | 0 |
| `deliveryClickRobustness.spec.ts` | 6 | 6 | 0 | 0 | 0 |
| `publicAcceptance.spec.ts` | 27 | 27 | 0 | 0 | 0 |
| `studentLearningLoop.spec.ts` | 3 | 3 | 0 | 0 | 0 |
| `teacherStudentFullFlow.spec.ts` | 1 | 1 | 0 | 0 | 0 |

## 6. 逐项工作流样例

| # | 测试文件:行 | 工作流样例 / 检查点 | 标签 | 状态 | 耗时 |
|---:|---|---|---|---|---:|
| 1 | `agenticDeepLoop.spec.ts:334` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 01 bootstrap 使用聚合启动接口返回世界、画像和学习集合 | agentic-deep | PASS | 12 ms |
| 2 | `agenticDeepLoop.spec.ts:343` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 02 legacy world session 仍可兼容但 bootstrap 更完整 | agentic-deep | PASS | 161 ms |
| 3 | `agenticDeepLoop.spec.ts:352` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 03 loop run 审计接受有界 run 并保留 trace | agentic-deep | PASS | 385 ms |
| 4 | `agenticDeepLoop.spec.ts:374` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 04 loop run 可按本人读取且工具审计不丢失 | agentic-deep | PASS | 187 ms |
| 5 | `agenticDeepLoop.spec.ts:383` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 05b loop runtime artifacts 可写入并恢复快照 | agentic-deep | PASS | 702 ms |
| 6 | `agenticDeepLoop.spec.ts:436` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 05 loop run 拒绝超过上限的自由循环 | agentic-deep | PASS | 181 ms |
| 7 | `agenticDeepLoop.spec.ts:451` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 06 loop run 拒绝伪造他人 userId | agentic-deep | PASS | 89 ms |
| 8 | `agenticDeepLoop.spec.ts:466` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 07 runtime summary 暴露 AG-UI 和证据 gate | agentic-deep | PASS | 501 ms |
| 9 | `agenticDeepLoop.spec.ts:474` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 07b runtime operations 暴露五类 loop 与治理项 | agentic-deep | PASS | 295 ms |
| 10 | `agenticDeepLoop.spec.ts:493` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 08 memory brief 返回深层个性化字段 | agentic-deep | PASS | 189 ms |
| 11 | `agenticDeepLoop.spec.ts:510` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 09 evidence graph 可聚合真实学习事件 | agentic-deep | PASS | 186 ms |
| 12 | `agenticDeepLoop.spec.ts:523` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 10 resource list 包含本轮真实生成资源 | agentic-deep | PASS | 89 ms |
| 13 | `agenticDeepLoop.spec.ts:534` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 11 resource detail 保留 grounding/safety 与证据页 | agentic-deep | PASS | 91 ms |
| 14 | `agenticDeepLoop.spec.ts:544` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 12 resource consumption 记录阅读并要求下一步证据 | agentic-deep | PASS | 181 ms |
| 15 | `agenticDeepLoop.spec.ts:558` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 13 micro quiz 命中资源内容后通过闭环 | agentic-deep | PASS | 383 ms |
| 16 | `agenticDeepLoop.spec.ts:573` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 13b resource effectiveness 暴露发布后反馈与效果信号 | agentic-deep | PASS | 92 ms |
| 17 | `agenticDeepLoop.spec.ts:590` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 14 micro quiz 首次失败进入补救而不是假装通过 | agentic-deep | PASS | 176 ms |
| 18 | `agenticDeepLoop.spec.ts:603` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 15 micro quiz 连续失败升级到换资源 | agentic-deep | PASS | 293 ms |
| 19 | `agenticDeepLoop.spec.ts:616` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 16 operation evidence 无正式提交时不写入掌握度 | agentic-deep | PASS | 192 ms |
| 20 | `agenticDeepLoop.spec.ts:633` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 17 failed formal submission drives replan | agentic-deep | PASS | 188 ms |
| 21 | `agenticDeepLoop.spec.ts:651` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 18 passed formal submission closes the loop | agentic-deep | PASS | 191 ms |
| 22 | `agenticDeepLoop.spec.ts:668` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 19 submission evidence 不能串到不匹配 KC 的资源 | agentic-deep | PASS | 83 ms |
| 23 | `agenticDeepLoop.spec.ts:683` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 20 submission list 可按 PASSED 过滤到正式通过提交 | agentic-deep | PASS | 93 ms |
| 24 | `agenticDeepLoop.spec.ts:694` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 21 submission detail 返回正式判题结果 | agentic-deep | PASS | 191 ms |
| 25 | `agenticDeepLoop.spec.ts:706` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 22 failed submission can open Bug Mine debug loop | agentic-deep | PASS | 603 ms |
| 26 | `agenticDeepLoop.spec.ts:716` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 23 Bug Mine card reveal is layered rather than pre-revealed | agentic-deep | PASS | 84 ms |
| 27 | `agenticDeepLoop.spec.ts:729` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 24 Bug Mine retry with AC code records recovery attempt | agentic-deep | PASS | 11.7 s |
| 28 | `agenticDeepLoop.spec.ts:736` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 25 quests today endpoint stays evidence-shaped | agentic-deep | PASS | 67 ms |
| 29 | `agenticDeepLoop.spec.ts:750` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 26 recommendations endpoint remains readable after evidence events | agentic-deep | PASS | 123 ms |
| 30 | `agenticDeepLoop.spec.ts:756` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 27 workshop browser rail is compact and action-first | agentic-deep | PASS | 11.9 s |
| 31 | `agenticDeepLoop.spec.ts:778` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 28 workshop progress drawer stays student-facing | agentic-deep | PASS | 10.3 s |
| 32 | `agenticDeepLoop.spec.ts:795` | 下一代 Agentic 深度闭环 E2E @agentic-deep › 29 workshop browser can submit micro-check from compact rail | agentic-deep | PASS | 12.5 s |
| 33 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M001 Session: 真实学生 token 存在 | agentic-matrix | PASS | 2 ms |
| 34 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M002 Session: 学生用户 id 有效 | agentic-matrix | PASS | 2 ms |
| 35 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M003 Session: 学生角色正确 | agentic-matrix | PASS | 2 ms |
| 36 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M004 Bootstrap: 聚合启动返回 world | agentic-matrix | PASS | 4 ms |
| 37 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M005 Bootstrap: 玩家主地图为 town_hub | agentic-matrix | PASS | 2 ms |
| 38 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M006 Bootstrap: 玩家 tileX 为数字 | agentic-matrix | PASS | 2 ms |
| 39 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M007 Bootstrap: 玩家 tileY 为数字 | agentic-matrix | PASS | 1 ms |
| 40 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M008 Bootstrap: 画像载入成功 | agentic-matrix | PASS | 1 ms |
| 41 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M009 Bootstrap: 任务集合为数组 | agentic-matrix | PASS | 2 ms |
| 42 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M010 Bootstrap: 掌握度集合为数组 | agentic-matrix | PASS | 1 ms |
| 43 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M011 Bootstrap: 路径集合为数组 | agentic-matrix | PASS | 2 ms |
| 44 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M012 Bootstrap: 技能集合为数组 | agentic-matrix | PASS | 1 ms |
| 45 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M013 Bootstrap: NPC 状态已载入 | agentic-matrix | PASS | 1 ms |
| 46 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M014 Bootstrap: activeSlot 字段存在 | agentic-matrix | PASS | 2 ms |
| 47 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M015 Bootstrap: pendingEvents 字段为数组 | agentic-matrix | PASS | 1 ms |
| 48 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M016 World: legacy world session 兼容 | agentic-matrix | PASS | 1 ms |
| 49 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M017 Runtime: loop run id 有效 | agentic-matrix | PASS | 1 ms |
| 50 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M018 Runtime: loop run 归属当前学生 | agentic-matrix | PASS | 1 ms |
| 51 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M019 Runtime: loop run maxSteps 有界 | agentic-matrix | PASS | 1 ms |
| 52 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M020 Runtime: loop run stepsUsed 记录 | agentic-matrix | PASS | 1 ms |
| 53 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M021 Runtime: toolsUsed 包含 RAG | agentic-matrix | PASS | 2 ms |
| 54 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M022 Runtime: toolsUsed 包含 judge | agentic-matrix | PASS | 1 ms |
| 55 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M023 Runtime: toolsUsed 包含 workshop | agentic-matrix | PASS | 1 ms |
| 56 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M024 Runtime: grounding gate 通过 | agentic-matrix | PASS | 1 ms |
| 57 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M025 Runtime: safety gate 通过 | agentic-matrix | PASS | 1 ms |
| 58 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M026 Runtime: traceId 保留 | agentic-matrix | PASS | 1 ms |
| 59 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M027 Runtime Snapshot: snapshot 指向同一 run | agentic-matrix | PASS | 1 ms |
| 60 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M028 Runtime Snapshot: checkpoint 已持久化 | agentic-matrix | PASS | 1 ms |
| 61 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M029 Runtime Snapshot: checkpoint cursor 可恢复 | agentic-matrix | PASS | 1 ms |
| 62 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M030 Runtime Snapshot: checkpoint stepIndex 正确 | agentic-matrix | PASS | 1 ms |
| 63 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M031 Runtime Snapshot: checkpoint 状态记录 | agentic-matrix | PASS | 1 ms |
| 64 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M032 Runtime Snapshot: tool call 已记录 | agentic-matrix | PASS | 1 ms |
| 65 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M033 Runtime Snapshot: tool call 名称正确 | agentic-matrix | PASS | 1 ms |
| 66 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M034 Runtime Snapshot: tool call 状态成功 | agentic-matrix | PASS | 1 ms |
| 67 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M035 Runtime Snapshot: agent run 已记录 | agentic-matrix | PASS | 1 ms |
| 68 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M036 Runtime Snapshot: agent run 名称正确 | agentic-matrix | PASS | 1 ms |
| 69 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M037 Runtime Snapshot: agent run 状态成功 | agentic-matrix | PASS | 1 ms |
| 70 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M038 Runtime Snapshot: eval result 已记录 | agentic-matrix | PASS | 1 ms |
| 71 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M039 Runtime Snapshot: eval result loopType 正确 | agentic-matrix | PASS | 1 ms |
| 72 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M040 Runtime Snapshot: eval result 通过 | agentic-matrix | PASS | 1 ms |
| 73 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M041 Replay: replay 指向同一 run | agentic-matrix | PASS | 1 ms |
| 74 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M042 Replay: replay loopType 正确 | agentic-matrix | PASS | 1 ms |
| 75 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M043 Replay: replay cursor 正确 | agentic-matrix | PASS | 1 ms |
| 76 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M044 Replay: replay nextAction 指向微测 | agentic-matrix | PASS | 2 ms |
| 77 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M045 Replay: replay evidence 包含资源 | agentic-matrix | PASS | 1 ms |
| 78 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M046 Replay: replay 标记可恢复 | agentic-matrix | PASS | 1 ms |
| 79 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M047 Summary: AG-UI 事件数量有效 | agentic-matrix | PASS | 1 ms |
| 80 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M048 Summary: 工具 evidence 字段达标 | agentic-matrix | PASS | 1 ms |
| 81 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M049 Summary: 协议 evidence gate 存在 | agentic-matrix | PASS | 1 ms |
| 82 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M050 Summary: 证据图节点数有效 | agentic-matrix | PASS | 1 ms |
| 83 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M051 Summary: 证据完整度有效 | agentic-matrix | PASS | 2 ms |
| 84 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M052 Summary: Twin memory 计数有效 | agentic-matrix | PASS | 1 ms |
| 85 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M053 Summary: Twin failed practice 计数有效 | agentic-matrix | PASS | 1 ms |
| 86 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M054 Summary: Twin succeeded practice 计数有效 | agentic-matrix | PASS | 1 ms |
| 87 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M055 Operations: 五类 loop 数量正确 | agentic-matrix | PASS | 1 ms |
| 88 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M056 Operations: 五类 loop 顺序正确 | agentic-matrix | PASS | 2 ms |
| 89 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M057 Operations: loop label 非空 | agentic-matrix | PASS | 1 ms |
| 90 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M058 Operations: loop status 合法 | agentic-matrix | PASS | 4 ms |
| 91 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M059 Operations: loop trace 字段存在 | agentic-matrix | PASS | 4 ms |
| 92 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M060 Operations: loop nextAction 非空 | agentic-matrix | PASS | 1 ms |
| 93 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M061 Operations: governance 数量正确 | agentic-matrix | PASS | 1 ms |
| 94 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M062 Operations: governance key 正确 | agentic-matrix | PASS | 1 ms |
| 95 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M063 Operations: governance status 合法 | agentic-matrix | PASS | 3 ms |
| 96 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M064 Operations: governance action 非空 | agentic-matrix | PASS | 1 ms |
| 97 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M065 Operations: governance reviewRoute 非空 | agentic-matrix | PASS | 1 ms |
| 98 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M066 Memory: facts 为数组 | agentic-matrix | PASS | 1 ms |
| 99 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M067 Memory: weak_kcs 为数组 | agentic-matrix | PASS | 1 ms |
| 100 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M068 Memory: style_notes 为数组 | agentic-matrix | PASS | 2 ms |
| 101 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M069 Memory: as_of 为 ISO 时间 | agentic-matrix | PASS | 1 ms |
| 102 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M070 Memory: stable_preferences 为数组 | agentic-matrix | PASS | 1 ms |
| 103 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M071 Memory: struggle_patterns 为数组 | agentic-matrix | PASS | 1 ms |
| 104 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M072 Memory: recent_wins 为数组 | agentic-matrix | PASS | 1 ms |
| 105 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M073 Memory: intervention_hints 为数组 | agentic-matrix | PASS | 1 ms |
| 106 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M074 Evidence: evidence nodes 为数组 | agentic-matrix | PASS | 1 ms |
| 107 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M075 Evidence: evidence edges 为数组 | agentic-matrix | PASS | 1 ms |
| 108 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M076 Evidence: evidence nodeCount 有效 | agentic-matrix | PASS | 1 ms |
| 109 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M077 Evidence: evidence edgeCount 有效 | agentic-matrix | PASS | 1 ms |
| 110 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M078 Evidence: evidence touchedKcCount 有效 | agentic-matrix | PASS | 1 ms |
| 111 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M079 Resource: 主资源 id 有效 | agentic-matrix | PASS | 1 ms |
| 112 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M080 Resource: 失败分支资源 id 有效 | agentic-matrix | PASS | 1 ms |
| 113 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M081 Resource: 资源列表包含主资源 | agentic-matrix | PASS | 1 ms |
| 114 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M082 Resource: 资源列表包含目标 KC | agentic-matrix | PASS | 1 ms |
| 115 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M083 Resource: 资源详情 id 匹配 | agentic-matrix | PASS | 1 ms |
| 116 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M084 Resource: 资源详情绑定 KC | agentic-matrix | PASS | 1 ms |
| 117 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M085 Resource: 资源 type 非空 | agentic-matrix | PASS | 1 ms |
| 118 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M086 Resource: 资源 title 非空 | agentic-matrix | PASS | 1 ms |
| 119 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M087 Resource: 资源 summary 非空 | agentic-matrix | PASS | 1 ms |
| 120 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M088 Resource: 资源 evidencePageIds 为数组 | agentic-matrix | PASS | 1 ms |
| 121 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M089 Resource: groundingStatus 合法 | agentic-matrix | PASS | 1 ms |
| 122 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M090 Resource: safetyStatus 合法 | agentic-matrix | PASS | 1 ms |
| 123 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M091 Consumption: 阅读记录指向主资源 | agentic-matrix | PASS | 1 ms |
| 124 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M092 Consumption: 阅读记录绑定 KC | agentic-matrix | PASS | 1 ms |
| 125 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M093 Consumption: 阅读已记录 | agentic-matrix | PASS | 1 ms |
| 126 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M094 Consumption: 阅读后进入 micro_check | agentic-matrix | PASS | 1 ms |
| 127 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M095 Consumption: 阅读后 loopState 正确 | agentic-matrix | PASS | 1 ms |
| 128 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M096 Consumption: 阅读后要求 micro evidence | agentic-matrix | PASS | 1 ms |
| 129 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M097 MicroCheck: 命中内容微测通过 | agentic-matrix | PASS | 1 ms |
| 130 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M098 MicroCheck: 通过后 nextAction pass | agentic-matrix | PASS | 1 ms |
| 131 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M099 MicroCheck: 通过后 loopState PASSED | agentic-matrix | PASS | 1 ms |
| 132 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M100 MicroCheck: 通过后 masteryDelta 正向 | agentic-matrix | PASS | 1 ms |
| 133 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M101 Effectiveness: 效果信号指向资源 | agentic-matrix | PASS | 1 ms |
| 134 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M102 Effectiveness: 效果 hook 正确 | agentic-matrix | PASS | 1 ms |
| 135 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M103 Effectiveness: checkCount 已累计 | agentic-matrix | PASS | 1 ms |
| 136 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M104 Effectiveness: passedCheckCount 已累计 | agentic-matrix | PASS | 1 ms |
| 137 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M105 Effectiveness: passRate 正向 | agentic-matrix | PASS | 1 ms |
| 138 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M106 Effectiveness: masteryDeltaSum 正向 | agentic-matrix | PASS | 1 ms |
| 139 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M107 Effectiveness: signal 为 EFFECTIVE | agentic-matrix | PASS | 1 ms |
| 140 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M108 Remediation: 首次失败进入 FAILED | agentic-matrix | PASS | 1 ms |
| 141 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M109 Remediation: 首次失败进入 remediate | agentic-matrix | PASS | 1 ms |
| 142 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M110 Remediation: 首次失败 loopState 补救 | agentic-matrix | PASS | 1 ms |
| 143 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M111 Remediation: 首次失败 masteryDelta 负向 | agentic-matrix | PASS | 1 ms |
| 144 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M112 Remediation: 连续失败仍 FAILED | agentic-matrix | PASS | 1 ms |
| 145 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M113 Remediation: 连续失败换资源 | agentic-matrix | PASS | 1 ms |
| 146 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M114 Remediation: 连续失败 loopState 换资源 | agentic-matrix | PASS | 1 ms |
| 147 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M115 Remediation: 连续失败补救类型 CodeLab | agentic-matrix | PASS | 2 ms |
| 148 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M116 Formal Judge: 无正式提交不通过 | agentic-matrix | PASS | 1 ms |
| 149 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M117 Formal Judge: 无正式提交要求补救 | agentic-matrix | PASS | 1 ms |
| 150 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M118 Formal Judge: 无正式提交 masteryDelta 负向 | agentic-matrix | PASS | 2 ms |
| 151 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M119 Formal Judge: 失败正式提交触发 replan | agentic-matrix | PASS | 2 ms |
| 152 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M120 Formal Judge: 失败正式提交绑定 submissionId | agentic-matrix | PASS | 1 ms |
| 153 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M121 Formal Judge: 通过正式提交闭环通过 | agentic-matrix | PASS | 1 ms |
| 154 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M122 Formal Judge: 通过正式提交 nextAction pass | agentic-matrix | PASS | 1 ms |
| 155 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M123 Formal Judge: 通过正式提交绑定 submissionId | agentic-matrix | PASS | 1 ms |
| 156 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M124 Formal Judge: KC mismatch 被拒绝 | agentic-matrix | PASS | 1 ms |
| 157 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M125 Formal Judge: KC mismatch 错误码明确 | agentic-matrix | PASS | 1 ms |
| 158 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M126 Submission: 失败提交不是 PASSED | agentic-matrix | PASS | 1 ms |
| 159 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M127 Submission: 通过提交 PASSED | agentic-matrix | PASS | 1 ms |
| 160 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M128 Submission: 通过提交 problemId 正确 | agentic-matrix | PASS | 1 ms |
| 161 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M129 Submission: PASSED 列表包含通过提交 | agentic-matrix | PASS | 1 ms |
| 162 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M130 Submission: PASSED 列表状态全通过 | agentic-matrix | PASS | 1 ms |
| 163 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M131 Submission: 提交详情 id 正确 | agentic-matrix | PASS | 1 ms |
| 164 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M132 Submission: 提交详情状态通过 | agentic-matrix | PASS | 1 ms |
| 165 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M133 Submission: 提交详情含 judgeResult | agentic-matrix | PASS | 2 ms |
| 166 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M134 BugMine: Bug Mine session id 有效 | agentic-matrix | PASS | 1 ms |
| 167 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M135 BugMine: Bug Mine 绑定失败提交 | agentic-matrix | PASS | 1 ms |
| 168 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M136 BugMine: Bug Mine 卡片存在 | agentic-matrix | PASS | 1 ms |
| 169 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M137 BugMine: Bug Mine 初始卡片未全揭示 | agentic-matrix | PASS | 1 ms |
| 170 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M138 BugMine: 深度锁定返回 409 | agentic-matrix | PASS | 1 ms |
| 171 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M139 BugMine: 深度锁定错误码明确 | agentic-matrix | PASS | 1 ms |
| 172 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M140 BugMine: Bug Mine retry 创建提交 | agentic-matrix | PASS | 1 ms |
| 173 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M141 BugMine: Bug Mine retry 状态合法 | agentic-matrix | PASS | 1 ms |
| 174 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M142 Quest: 今日任务为数组 | agentic-matrix | PASS | 1 ms |
| 175 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M143 Quest: 今日任务 id 字段存在 | agentic-matrix | PASS | 0 ms |
| 176 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M144 Quest: 今日任务 evidence 字段存在 | agentic-matrix | PASS | 0 ms |
| 177 | `agenticDeepMatrix.spec.ts:716` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M145 Recommendation: 推荐接口为数组 | agentic-matrix | PASS | 1 ms |
| 178 | `agenticDeepMatrix.spec.ts:721` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M146 Browser: 工坊页面可打开 | agentic-matrix | PASS | 7.9 s |
| 179 | `agenticDeepMatrix.spec.ts:734` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M147 Browser: 进度抽屉展示用户可读状态 | agentic-matrix | PASS | 7.6 s |
| 180 | `agenticDeepMatrix.spec.ts:749` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M148 Browser: 进度抽屉不暴露 runtime summary | agentic-matrix | PASS | 7.8 s |
| 181 | `agenticDeepMatrix.spec.ts:764` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M149 Browser: 书房抽屉可打开 | agentic-matrix | PASS | 8.4 s |
| 182 | `agenticDeepMatrix.spec.ts:778` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M150 Browser: 资源列表包含本轮资源 | agentic-matrix | PASS | 10.2 s |
| 183 | `agenticDeepMatrix.spec.ts:792` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M151 Browser: 资源详情弹窗可打开 | agentic-matrix | PASS | 9.6 s |
| 184 | `agenticDeepMatrix.spec.ts:807` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M152 Browser: 资源闭环 rail 展示读测判 | agentic-matrix | PASS | 10.3 s |
| 185 | `agenticDeepMatrix.spec.ts:824` | Agentic Runtime 100+ 深链验收矩阵 @agentic-matrix › M153 Browser: 资源微测输入框可用 | agentic-matrix | PASS | 14.7 s |
| 186 | `baixiaoAgentRouting.spec.ts:174` | 百晓工坊 Agent 路由真实联测 @baixiao-routing › 修报错经策略门进入 BugMine/DebugAgent，且不会误入 Tutor SSE | baixiao-routing | PASS | 13.1 s |
| 187 | `baixiaoAgentRouting.spec.ts:288` | 百晓工坊 Agent 路由真实联测 @baixiao-routing › 总结后出题经签名 RouteDecision 进入单次顺序 TaskPlan | baixiao-routing | PASS | 1.7 min |
| 188 | `baixiaoAgentRouting.spec.ts:438` | 百晓工坊 Agent 路由真实联测 @baixiao-routing › 典型 C3 复合请求编译三条受限候选并按 A* 选中路径真实执行 | baixiao-routing | PASS | 2.3 min |
| 189 | `baixiaoAgentRouting.spec.ts:615` | 百晓工坊 Agent 路由真实联测 @baixiao-routing › 同一复合请求会按真实失败历史从并行改为脚手架式顺序执行 | baixiao-routing | PASS | 21.7 s |
| 190 | `baixiaoAgentRouting.spec.ts:747` | 百晓工坊 Agent 路由真实联测 @baixiao-routing › 自由文本诊断后补救不因服务端隐式补全 interaction 而验签失败 | baixiao-routing | PASS | 1.3 min |
| 191 | `deliveryClickRobustness.spec.ts:171` | 上线交付深链点击鲁棒性 @delivery-clicks › 小镇 HUD 与暂停菜单可反复开关，并能从菜单进入工坊 | delivery-clicks | PASS | 13.8 s |
| 192 | `deliveryClickRobustness.spec.ts:202` | 上线交付深链点击鲁棒性 @delivery-clicks › 主线地图章节抽屉可反复打开关闭，进入学习后返回仍稳定 | delivery-clicks | PASS | 10.0 s |
| 193 | `deliveryClickRobustness.spec.ts:254` | 上线交付深链点击鲁棒性 @delivery-clicks › 指南页桌面翻页与移动目录反复点击不白屏不溢出 | delivery-clicks | PASS | 7.9 s |
| 194 | `deliveryClickRobustness.spec.ts:295` | 上线交付深链点击鲁棒性 @delivery-clicks › 工坊历史/书房/进度抽屉与资源弹窗可反复开关 | delivery-clicks | PASS | 42.5 s |
| 195 | `deliveryClickRobustness.spec.ts:346` | 上线交付深链点击鲁棒性 @delivery-clicks › 训练、公告板、复盘、画像、题库和闯关深链刷新后仍可用 | delivery-clicks | PASS | 41.4 s |
| 196 | `deliveryClickRobustness.spec.ts:378` | 上线交付深链点击鲁棒性 @delivery-clicks › 教师首页和班级看板多标签点击稳定 | delivery-clicks | PASS | 12.9 s |
| 197 | `publicAcceptance.spec.ts:419` | 公网验收 · 认证与会话 @auth › 未登录访问受保护页应被带去登录页 | auth | PASS | 9.2 s |
| 198 | `publicAcceptance.spec.ts:428` | 公网验收 · 认证与会话 @auth › 注册（可选）后通过登录表单进入小镇 | auth | PASS | 9.4 s |
| 199 | `publicAcceptance.spec.ts:447` | 公网验收 · 认证与会话 @auth › 已认证状态访问 /login 应自动跳回小镇（guestOnly 守卫） | auth | PASS | 7.6 s |
| 200 | `publicAcceptance.spec.ts:459` | 公网验收 · 认证与会话 @auth › 公网快速体验账号可直接进入小镇 | auth | PASS | 10.9 s |
| 201 | `publicAcceptance.spec.ts:483` | 公网验收 · 主城 town_hub @world › 主城画布加载完成且无致命 console 错误 | world | PASS | 15.3 s |
| 202 | `publicAcceptance.spec.ts:517` | 公网验收 · 主线地图 @mainline › 右侧进入学习不穿透到底层章节节点 | mainline | PASS | 8.3 s |
| 203 | `publicAcceptance.spec.ts:552` | 公网验收 · /guide 使用指南 @guide › 桌面布局 › /guide 可导航、锚点跳转生效 | guide | PASS | 8.9 s |
| 204 | `publicAcceptance.spec.ts:595` | 公网验收 · /guide 使用指南 @guide › 桌面布局 › /guide 图片可点击放大并可关闭 | guide | PASS | 11.7 s |
| 205 | `publicAcceptance.spec.ts:637` | 公网验收 · /guide 使用指南 @guide › 移动布局 › /guide 移动端无横向溢出且核心内容可见 | guide | PASS | 11.6 s |
| 206 | `publicAcceptance.spec.ts:678` | 公网验收 · AI 向导 SSE @guide-ai › 多轮 SSE 对话 + 计划渲染（含澄清分支） | guide-ai | PASS | 3.1 min |
| 207 | `publicAcceptance.spec.ts:742` | 公网验收 · AI 向导 SSE @guide-ai › 模糊目标触发澄清追问或可执行计划 | guide-ai | PASS | 36.9 s |
| 208 | `publicAcceptance.spec.ts:775` | 公网验收 · AI 向导 SSE @guide-ai › 注入一次网络失败后，可见失败态并重试成功 | guide-ai | PASS | 2.6 min |
| 209 | `publicAcceptance.spec.ts:825` | 公网验收 · 向导→工坊深链 @deeplink › 计划步骤「去工坊」深链落地且 KC 上下文保留 | deeplink | PASS | 4.7 min |
| 210 | `publicAcceptance.spec.ts:897` | 公网验收 · 工坊真实响应 @workshop › 提问走真实后端流式链路并产出非空回复 | workshop | PASS | 22.7 s |
| 211 | `publicAcceptance.spec.ts:950` | 公网验收 · 图书馆 RAG @library › RAG 查询「input函数」返回带页码的证据 | library | PASS | 20.5 s |
| 212 | `publicAcceptance.spec.ts:950` | 公网验收 · 图书馆 RAG @library › RAG 查询「for循环」返回带页码的证据 | library | PASS | 20.7 s |
| 213 | `publicAcceptance.spec.ts:950` | 公网验收 · 图书馆 RAG @library › RAG 查询「列表」返回带页码的证据 | library | PASS | 20.2 s |
| 214 | `publicAcceptance.spec.ts:1066` | 公网验收 · 训练场判题三态 @training › 正确解法判 AC（通过） | training | PASS | 11.5 s |
| 215 | `publicAcceptance.spec.ts:1074` | 公网验收 · 训练场判题三态 @training › 错误输出判 WA（答错） | training | PASS | 11.8 s |
| 216 | `publicAcceptance.spec.ts:1081` | 公网验收 · 训练场判题三态 @training › 抛异常判 RE（运行错误）且错误信息不泄密 | training | PASS | 12.5 s |
| 217 | `publicAcceptance.spec.ts:1097` | 公网验收 · 复盘大厅与画像 @review-profile › 复盘大厅面板可见 | review-profile | PASS | 9.8 s |
| 218 | `publicAcceptance.spec.ts:1110` | 公网验收 · 复盘大厅与画像 @review-profile › 个人画像页可见且渲染画像维度 | review-profile | PASS | 11.1 s |
| 219 | `publicAcceptance.spec.ts:1142` | 公网验收 · 深链直接刷新 @refresh › 直接打开并刷新 /guide 不白屏不 404 | refresh | PASS | 10.7 s |
| 220 | `publicAcceptance.spec.ts:1142` | 公网验收 · 深链直接刷新 @refresh › 直接打开并刷新 /workshop 不白屏不 404 | refresh | PASS | 12.5 s |
| 221 | `publicAcceptance.spec.ts:1142` | 公网验收 · 深链直接刷新 @refresh › 直接打开并刷新 /mainline-map 不白屏不 404 | refresh | PASS | 11.4 s |
| 222 | `publicAcceptance.spec.ts:1181` | 公网验收 · 错误信息不泄密 @security-surface › 登录失败提示友好且不含内部细节 | security-surface | PASS | 11.1 s |
| 223 | `publicAcceptance.spec.ts:1197` | 公网验收 · 错误信息不泄密 @security-surface › API 错误响应体不含堆栈与内部主机名 | security-surface | PASS | 549 ms |
| 224 | `studentLearningLoop.spec.ts:123` | 学生学习闭环 E2E @learning-loop › 微测两轮失败后从补救升级到换资源 | learning-loop | PASS | 31.3 s |
| 225 | `studentLearningLoop.spec.ts:141` | 学生学习闭环 E2E @learning-loop › 最近失败训练提交会把闭环推进到重规划 | learning-loop | PASS | 35.0 s |
| 226 | `studentLearningLoop.spec.ts:155` | 学生学习闭环 E2E @learning-loop › 最近通过训练提交会完成通过闭环 | learning-loop | PASS | 39.8 s |
| 227 | `teacherStudentFullFlow.spec.ts:80` | 教师创建班级、发布任务，学生完成证据后教师看板同步 |  | PASS | 40.9 s |

## 7. 非干净结果明细

最终无失败或 flaky 样例。
## 8. 原始证据

- Playwright JSON：`.work/ecs-public-227-final.json`
- Playwright HTML：`.work/ecs-public-227-final-html/`
- HTTP 最终 JSON / HTML：`.work/ecs-http-public-27-final.json`、`.work/ecs-http-public-27-final-html/`
- HTTP 定点恢复 JSON / HTML：`.work/ecs-http-recovery-targeted-final.json`、`.work/ecs-http-recovery-targeted-final-html/`
- HTTP 首轮预算失败证据：`.work/ecs-http-public-27-prebudget-failure.json`、`.work/ecs-http-public-27-prebudget-failure-html/`
- 费曼截图：`qa-screens/feynman-local-1785235444/`
- ECS 报告副本：`/opt/pythontopia/reports/2026-07-28-ecs-public-workflow-acceptance.md`
- JSON 汇总字段与本报告逐项统计已由生成脚本机械核对。

## 9. 已知非阻塞告警

- `MemoryEmbeddingWriter` 在部分学习事件上记录 `expected=2048 actual=1024` 并跳过向量写入；
  不影响本轮确定性证据、画像、判题和闭环断言，但可能降低后续语义记忆召回覆盖。
- `WeeklyReportService` 曾对 `resource_run` 查询记录 `bad SQL grammar`；周报其余内容仍可生成，
  但该资源运行片段可能缺失。
- 两项均为协议无关的既有后端告警，不构成 HTTPS 相对 HTTP 的回归；本轮没有在缺少独立 RED/GREEN
  与迁移影响分析的情况下顺手修改。建议后续分别作为“Embedding 维度契约”和“周报资源查询契约”
  的独立任务处理。
