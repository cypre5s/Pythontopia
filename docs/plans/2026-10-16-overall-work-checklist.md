# Web 收尾工作清单

更新时间：2026-10-16
范围：Web、Spring Boot、Graph/RAG 与本地开发栈；不含 Android、Electron、微信或 ECS。

## 已完成

- DeepSeek provider 已统一到 DeepSeek；既有真实 Tutor 文本与 PNG 图片输入记录显示返回内容为文本。`./dev.sh` 关闭 AI 请求计数，TTS、ASR、图片生成与图片理解 provider 保持关闭。本轮仅为修复 KC-004 出题沙箱后做了 1 次定向真实生成确认，没有重复跑泛化文本/图片探针。
- Pythontopia 的 Tutor 已使用固定阶段 AutoGen。Alethicode 工作树包含大量未提交改动，且其 endpoint、DTO、事件和 checkpoint 与本项目不同；本次对照日志与代码后没有整套覆盖或复制协议。
- 管理台已有运维、题目审核、用户管理三个真实 API 页面。当前全栈巡检以临时 ADMIN 会话访问三页，并经 UI 完成学生账号“停用→恢复”；320px 用户表页面宽度仍为 320px。测试账号均已删除。
- 安全只读审计未确认当前本地源码存在 P0/P1 越权；本地 Piston 隔离烟测确认非 root、宿主临时文件/loopback 隔离、超时和外网 socket 拒绝。生产 Piston ACL 未检查。
- 本轮新增修复：普通 Boss 题通过事件改为按用户与题目指纹幂等，复用现有 `kc_mastery_event` 唯一索引；Twin 最近三次练习与最近练习时间统一包含 `PRACTICE_PASS/FAIL`、`MICRO_CHECK_PASS/FAIL` 和 `DEBUG_FIX`。没有新增表、模型或 Agent。
- 本地 320px 路由巡检覆盖 26 个页面/别名，均返回应用文档并呈现非空内容；预期角色路由和旧入口按规则重定向。`pageerror=0`、HTTP 5xx=0、横向溢出=0。测试阻断了除登录刷新与管理员状态切换外的写请求，且未触发 Tutor/出题模型。
- 复核 Twin 数据可信边界：`SelfDimensionRequest` 只接受主观分数与标签，访谈证据不会提升可信 evidence；掌握度账本对有服务端 idempotency key 的判题事件跳过重复写入。当前快照后 fresh Maven 定向回归 19/19（画像 DTO 2、控制器 5、掌握度账本 12），无代码改动。
- 修正旧 `/lecture/:chapter` 页面误报：视频进度明确标为会话内、不声称写入画像；B站外链点击不再冒充已观看；讲义失败时不亮完成标记。TDD 在旧实现上复现 4 项失败，修复后相关回归 22/22、typecheck、定向 ESLint、`git diff --check` 通过；未调用模型或写业务库。
- 追踪并恢复本地 KC-004 AI 出题链路：Graph 需要隔离 Piston 和当前开发栈的临时内部令牌；按现有 `dev.sh --sandbox` 配置后，Graph `/readyz` 正常、令牌匹配，Piston 沙箱执行 `print(2 + 2)` 返回 4。最终一次 KC-004 请求由 DeepSeek 真实生成并返回 HTTP 200、`aiGenerated=true`、`reviewed=true`。排查中裸 `docker compose` 重建曾造成一次临时 401，已用运行中的令牌恢复。生成审查记录与临时学生账号已精确删除；该题未保留到本地题库。重启 Graph 时必须通过 `./dev.sh --sandbox` 或注入同一运行令牌，裸 `docker compose up` 会绕过临时令牌同步。
- Boss 互动素材/场景已落地：Phaser Boss 场景加载 Kenney CC0 冲击音效，素材记录同步在 `LICENSE-ART.md` 与像素资源 license；场景回归 4/4、窄屏整页回归 1/1。现已支持审核题的选择、判断、填空输入，并在 KC 缺少编程题时复用课程目标题；不合适写代码的 KC 可继续做客观题练习。
- 工程规范轻量复核：对照 [Google Java Style](https://google.github.io/styleguide/javaguide.html)、[PEP 8](https://peps.python.org/pep-0008/) 与 [typescript-eslint 命名规则](https://typescript-eslint.io/rules/naming-convention/)；触及代码遵守项目 Java/TS/Python 命名约定，既有定向 lint/format/typecheck 通过。本轮不做全仓格式化；历史文件命名与格式基线仍未全量盘点。
- 本轮快速只读安全续审未确认新的本地 P0/P1。公网 Piston ACL、ECS/Nginx 运行态及全 API 逐项审计仍未验证。

## 附加任务核对（2026-10-16）

- **Tutor / Alethicode 对齐：** 当前 Pythontopia 已有固定阶段 AutoGen、检查点恢复、代码上下文和私有图片输入。Alethicode 工作树中的事件、WebSocket、状态和 V124 存储协议不同且未提交；未发现必须迁移的功能缺口，因此不复制其运行时或改写现有 API。
- **DeepSeek / 图片 / 语音：** Web 模型出口固定为 DeepSeek，Tutor 图片走 vision 档并输出文本；TTS、ASR、图片生成/理解入口关闭。已有真实文本与 PNG 输入记录，本次不重复发模型请求。隔离的 Qwen 研究脚本未接入 Web 路由。
- **管理台对齐：** 运维、题目审核、用户管理三页已有真实 API、分页、筛选、状态反馈与窄屏布局，与 Alethicode 的可用视觉模式接近；Alethi 当前管理台有大量未提交改动，且多项菜单在本项目无对应契约，不复制。
- **远征安全源码复核：** 未发现可复现的 P0/P1 越权。远征身份、HP/进度、节点动作、判题结算和排行榜分数以服务端状态为准；Boss 失败反馈真实体力，成功触发受击演出。2026-10-16 的合法学生浏览器实跑已完成 KC-008 Boss 三阶段，并核实 Twin 回流与账号清理。
- **审计边界：** 安全结论基于源码与既有测试阅读，不代表全 API、生产网络或屏幕阅读器运行态验证；安全复核未调用模型，也未访问 ECS/GitHub。

## 本轮验证证据

- RED/GREEN：最新 `BossServiceTest` 62/62、`BossControllerTest` 18/18、`BossAttemptIdempotencyPostgresTest` 6/6、`MainlinePracticeServiceTest` 21/21；V217/V218 已在本地开发库应用，schema version 为 218。
- 修复 Mermaid SVG 内联样式在 SVG CSSOM 缺失环境抛错的问题；原全量运行中相关图示用例失败。校正 Tutor 过程测试的按钮选择及颜色序列化断言后，聚焦测试 25/25、全量 Vitest 276 文件/2620 用例通过；typecheck、ESLint、`git diff --check` 均通过。
- 实际 Boss 题目/判题 API 使用临时学生跑通：重启 Java 服务加载当前构建后，日志确认选中已审核题 `review=85/version=2`；公开题答案由服务端判题 3/3 通过。`aiGenerated=true` 是已审核题的来源标记，不是本次生成；没有调用 DeepSeek。每个临时账号都在脚本 `finally` 中按精确用户名删除。
- **真实远征浏览器闭环（已完成）：** 通过普通注册表单建立临时 STUDENT 账号；KC-003、KC-004 各完成 4 道课程审核选择/判断/填空题，服务端判题通过，Twin 掌握度均到 0.78。10 层服务端远征中，4 道浏览器编程挑战通过；KC-008 Boss 的 3 个阶段分别以判断、填空、选择题通过。浏览器确认 Phaser 场景和每阶段受击反馈；数据库核对 3 条 Boss 阶段结算；Twin 页面将 KC-008 从“尚未评估”更新为掌握度 1，证据来源 `PRACTICE_PASS`。全程 `page_errors=0`、HTTP 5xx=0。脚本 finally 删除隔离账号并通过外键残留审计；本次没有调用 DeepSeek。
- **历史首次续测（已被上项取代）：** 当时 KC-008 没有可用审核题，生成服务也不可用，未能通关；临时账号已清理。之后增加了客观题路径与课程题库后，已由上项成功完成同一主链路。
- Playwright 路由巡检：26 个路径，320px viewport，无横向溢出、前端运行错误或 5xx；管理员三页可打开，用户状态按钮真实 API 往返成功。
- 学生状态切换后旧 JWT 被拒并重定向登录；重新登录后巡检通过，符合账号状态变化吊销旧会话的预期。
- 临时账号在脚本 `finally` 中删除。此路由巡检没有提交章节试卷、启动远征节点或执行生成/判题写操作。

## 仍未完成或未验证

- **Boss 主链路已完成；仍未验证 Boss 刷新恢复。** 本次浏览器实跑覆盖三阶段判题、受击、结算及 Twin 增量，没有在 Boss 阶段中途刷新页面，因此刷新后的恢复行为仍需单独测。
- 章节测验 prepare、正式判题、实际画像回流、Twin→主线完成闭环未在本次路由巡检中验证；巡检刻意拦截了相关写请求。
- 管理台题目审核的真实通过/驳回写操作没有可安全复用的一次性审核 fixture；本次验证了列表页与搜索/用户状态路径，不代表题目审核写闭环。
- Twin 新旧选题 A/B、独立答题率与 7 日延迟复测需要真实学习样本及时间跨度，当前无结果。BKT/未来路线概率按计划暂缓。
- DeepSeek 供应商账单/token 需要从控制台核对；本地请求上限已关闭，但不代表供应商无额度或无费用。
- 生产 Piston 私网 ACL/代理鉴权、生产 CORS/Nginx 运行态及公网安全未验证；本轮不访问 ECS。
- 全仓历史格式债务与命名一致性尚未做破坏性批量整理；仅清理经过验证且本任务触及的范围。
- 性能仍后置；既有本地采样仅覆盖建局 API/渲染，不覆盖完整 Graph 生成、Piston 判题与实际远征通关 P50/P95。
- 当前这次隔离学生实跑已通过 Boss 与 KC-008 Twin 回流；账号及其关联的临时学习记录已清理。保留在课程题库的审核题属于 V217/V218 迁移种子数据，不是临时账号数据。

## 下一步顺序

1. 验证 Boss 中途刷新后的恢复；然后验证自然可达的课程测验结果回流 Twin。
2. 为管理台审核通过/驳回准备可清理的写操作 fixture，并完成真实 UI 往返。
3. 跑剩余相关 Web 回归和工程检查；只修可复现的功能、可访问性、命名或格式问题。
4. 7 日效果对照到期后再评估个性化效果；最后补远征生成、正式判题与通关性能样本。
