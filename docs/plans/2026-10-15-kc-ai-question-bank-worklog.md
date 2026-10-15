# KC 无课程编程题的 AI 生成与入库工作日志

日期：2026-10-15
范围：Web + Spring + Graph；课程包 pack 43；本地 DeepSeek 联测。

## 目标

对课程 KC 没有可用编程题的情况，允许现有 Boss 出题链路调用 DeepSeek 生成题目。只有通过结构、KC 接地、语法范围、Piston 自测、难度和查重审查的题，才能自动批准并进入现有共享审核题库；不增加表、Agent 或服务。

## 改动

- Java 题目查重候选同时读取同 KC 课程编程题与跨阶段审核题，只给 Graph 传题面，不传参考答案和隐藏测试。
- 生成与 Golden 兜底限定目标 KC，避免将其他 KC 的题冒充匹配题。
- KC-010 的入门提示要求真实变量赋值和算术运算。难度估算仍保留题面长度特征，但其分值从每 100 字加 1 调低到每 1000 字加 1，避免输入/输出说明把三行基础代码误判为 3 档。
- `fill_blank` 使用 Web 组件实际解析的 `___` 标记；其结构审核拒绝缺少该标记或错误的 `___BLANK___` 标记。图片/输入输出样例保留在现有公开 DTO 字段，填空题 statement 只提供代码骨架。
- KC-010 填空题把知识点名称写进代码注释，而不是固定程序输出，满足 KC 接地规则且不污染判题输出。

## 验证

- RED/GREEN：提示词、难度误判和填空标记分别先失败后通过。
- Graph：`tests/agents/boss` 共 255 passed；相关 Ruff 检查通过。
- Web：`arenaBugCards.spec.ts` 4/4；新增用真实 KC-010 骨架验证 4 个 `___` 可编辑并还原成有效的两变量求和程序。
- 本地全栈真实请求：Vite `:5173` → Spring `:18080` → Graph `:18091` → DeepSeek。最终请求只用 1 次 DeepSeek completion 即通过，响应 HTTP 200、`aiGenerated=true`、`reviewed=true`、KC-010、难度 1；题面含知识点注释和 4 个填空标记，公开样例 `3\n5 → 8`，共 3 个测试用例。
- 数据库确认 review id `79` 为 `APPROVED/AUTOMATED`，kind `fill_blank`，chapter 2 / stage 1 / difficulty 1；请求题同时进入当前学生的 `CONSUMED` 记录。第二个学生再次经 Web API 请求后复用批准题，Graph 没有收到新的出题请求。
- 正式判题复验：第三名临时学生从批准题库复用 KC-010 题，提交 `a = int(input()); b = int(input()); print(a + b)`，服务端沙箱 3/3 通过；数据库出现 1 条 `practice_succeeded` 学习事件和 `PRACTICE_PASS +0.140` 掌握度事件。临时判题学生及其进度记录随后删除。
- 同一 `attemptId` 原样重放后仍返回相同通过结果；数据库核对只有 1 条成功事件和 1 条 mastery event。Twin API 将 KC-010 显示为 `assessed=true`、score `0.14`、confidence `0.05`。幂等联测账号及数据删除。
- 两轮真实出题各有 6 个候选未通过质量审查，均未入库。另一轮曾自动批准一条缺少填空标记的测试题；发现 Web 题型不匹配后已删除该题及联测账号。最终修正后第 1 个候选通过，复用测试账号已清理。

## 数据留存与风险

### 2026-10-15 续验：主线到章测预生成

- 第一章课程页码映射改为取 KC `page_refs`，V216 应用后重新完成三个真实 Web 小节：PDF 原页分别为 40–45、34–39、13–32；服务端每阶段记录 440 秒视频、全部必读页和 4/4 正确题目。
- 真实章测预生成最初失败的原因是本地 Graph 缺少 Piston URL，导致参考解自测返回 `sandbox_unavailable`。按既有 `./dev.sh --sandbox` 配置启动本地沙箱并对齐 runtime 后，Graph 的 Python 求和自检通过；修正一次开发容器内部令牌失配后，真实 DeepSeek 章测记录 `test_id=14` 达到 `READY`，类型数量为选择 30、判断 30、填空 30、编程 9。
- `start` 返回 99 道考试题，去答案校验未发现答案、解析、隐藏用例或参考解字段。考试尚未提交；因此本轮仍未验证章测通过、KC-010 解锁或首个编程 KC 的新生闭环。旧的“仅完成 1/3 小节”记录是续验前快照，以本节状态为准。

- 保留 requester id `625` 的随机联测账号，因为审核表通过 `ON DELETE RESTRICT` 保留请求来源；该账号随机密码只在本地进程内生成，没有写入文件或日志。题库审核记录保持可追溯。
- 本轮真实验证了 KC-010 难度 1 `fill_blank` 生成、自动审核、跨用户复用、前端四个占位符重建、正式判题、重复 attempt 去重和 Twin API 状态刷新。新生主线第一章三个阶段已完成，但章测未提交，KC-010 解锁和后续掌握度回流仍未验证。
- DeepSeek 本地应用级日限额按用户授权关闭；本地请求日志只能确认 HTTP 成功，不能核验供应商账单或 token 费用。

### 2026-10-15 续验：KC-011 请求窗口与 DeepSeek 服务状态

- 修复 Web Boss 出题请求 15 秒客户端超时短于 Spring → Graph 180 秒读取上限的问题，客户端窗口改为 190 秒；Boss 命题切到配置中的 `deepseek-flash` 快档。先将 API 超时断言改为 190 秒并确认 RED，再实现并确认 GREEN。
- Web Boss API 测试 17/17，Graph Boss 测试 255/255；Graph 改动文件 Ruff、前端 typecheck、Boss API 与测试文件 ESLint、`git diff --check` 通过。
- 真实浏览器点击 KC-011 补题入口，但未在观察窗口收到 Boss API response；Graph 调用没有完成为可用题目，PostgreSQL 审核题库仍无 KC-011 新记录。随后两次从同一 Graph 容器直连 `deepseek-flash` 的短 JSON 探针均在约 0.5 秒返回 HTTP 503 `service_unavailable_error: Service is too busy`。较早一次应用调用曾返回空 `content_json/content_text`，也未通过题目工厂。
- 所以本轮只完成超时/模型档位代码修复及定向回归，未完成 KC-011 AI 题审核、入库、正式判题和学习画像回流。服务可用后再做一次 Web 闭环验证；不把 HTTP 200 header、超时或 503 记成生成成功。

### 2026-10-15 续验：第二章第 3 小节与 KC-011 Web 重试

- `stage_id=6` 按正常 Web 流程完成：浏览器在可见页面以 1× 播放视频，服务端累计并确认 543/543 秒；课件从第 1 页依次翻到第 19 页并标记；页面里的 4 道客观题均经服务端判对。
- 从主线 OJ 链接打开共享题 476（圆面积，π=3.1415、保留 4 位小数），在训练场输入 Python 程序，先用 Pyodide 预览，再正式提交；Piston 返回 AC、3/3。之后返回主线完成 OJ 标记。
- PostgreSQL 确认 stage 完成、视频 543 秒、19 页、客观题 ID 21–24、`examples_done=[476]`，视频与课件消费时间存在；提交 229 为 `PASSED` / `TRAINING`，只产生一条 `practice_succeeded` 与 KC-016 `PRACTICE_PASS +0.140`。Chromium `pageerror=0`。
- 从 KC-011 所在的小节真实点击 AI 补题入口。Graph 确认 KC-011 请求到达 DeepSeek；调用中有 HTTP 200 空内容、503 和重试，页面最终显示“暂不可用”，PostgreSQL 审核题库无 KC-011 新记录。官方 [JSON Output 文档](https://api-docs.deepseek.com/guides/json_mode/)说明 JSON 模式偶尔可能返回空内容。
- 结论：第二章三个主线小节已完成；KC-011 AI 题自动审核、入库、正式判题与画像回流仍未完成，远征先修 KC-008/KC-011 也未验证。待供应商服务恢复后再有限重试。

## 下一步

1. DeepSeek 服务恢复后完成 KC-011 AI 题生成、审核、共享题库复用、正式判题及 Twin 回流；随后验证 KC-008/KC-011 远征先修闭环。
2. 继续 Tutor/Alethicode 逐事件 live 对照、稳定全量 Maven、生产 Piston/ECS、Twin 学习效果对照与最终 Boss 实战。
3. 功能和工程验证完成后再做 Web 性能采样。
