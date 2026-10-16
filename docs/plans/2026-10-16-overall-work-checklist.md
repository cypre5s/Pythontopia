# Web 收尾工作清单

更新时间：2026-10-17
范围：Web、Spring Boot、Graph/RAG 与本地开发栈；不含 Android、Electron、微信或 ECS。

## 已完成

- 当前快照定向安全回归新运行 12 个 Java 测试类，共 168/168 通过（JWT/账号状态、媒体和上传 owner、Boss 题型/判题幂等、远征快照与结算）；其中非编程 KC 的选择/判断/填空题池耗尽 fail-closed 回归通过。该轮未改业务代码、未调用模型或重跑浏览器通关。
- 远征存档 `PUT` 现于 MVC JSON 绑定前限制为 64 KiB，`PUT`/`DELETE` 复用既有账号级写入限流；边缘与前端 Nginx 同步限制。旧行为回归先复现超限体返回 200、两个写操作未受限；修复后过滤器与控制器回归 21/21、定向 Spotless、HTTPS/Nginx 契约、Shell 语法及两份 Nginx 1.27 配置语法通过。未重跑 Boss 浏览器流程或访问生产代理。
- 续对齐实际存在的管理台：共享面板页脚留白、分页控件尺寸、审核表格列对齐与操作列宽；同步更新过期的页面断言。Agent 定向管理台回归 13 文件/54 项通过，本轮新运行覆盖改动文件的 8 文件/37 项通过，typecheck 与 ESLint 通过。API/DTO/权限未动；未做浏览器全栈截图。
- 管理台运维长标签可换行、证据引用可键盘/触屏展开，用户长用户名/显示名不依赖悬浮提示；组件回归 9/9、typecheck 和定向 ESLint 通过。Tutor Transfer 计划已停止把原题提交记作迁移尝试，改为明确的思路反思；服务端判题的变式题尚未接入。
- DeepSeek provider 已统一到 DeepSeek；既有真实 Tutor 文本与 PNG 图片输入记录显示返回内容为文本。`./dev.sh` 关闭 AI 请求计数，TTS、ASR、图片生成与图片理解 provider 保持关闭。本轮仅为修复 KC-004 出题沙箱后做了 1 次定向真实生成确认，没有重复跑泛化文本/图片探针。
- Pythontopia 的 Tutor 已使用固定阶段 AutoGen。Alethicode 工作树包含大量未提交/未跟踪实验改动；双方已使用同版本 AutoGen 固定阶段模式，但运行接口、DTO、事件和 checkpoint 不同。本次对照代码与 Codex 日志后确认无需重复迁移阶段运行时，也没有覆盖现有契约。
- 管理台已有运维、题目审核、用户管理三个真实 API 页面。当前全栈巡检以临时 ADMIN 会话访问三页，并经 UI 完成学生账号“停用→恢复”及审核题“通过/驳回”；320px 用户表页面宽度仍为 320px。审核详情现采用与 Alethicode 相近的预览弹窗，保留本项目 DTO 和审核 API。当前轮没有登录态浏览器截图；像素级 parity 仍未核验。测试账号均已删除。
- 管理台前端布局续对齐：用户名表格居中但长文本可读；审核队列行内驳回/撤下复用版本与意见 API；分页移除跳页输入并对齐现有 total/sizes/pager；面板底栏和预览宽度收敛。管理台 Vitest 5 个文件/17 项、typecheck、定向 ESLint、diff 检查通过。未添加本项目 DTO/API 不支持的邮箱、创建时间、最近登录、编辑/删除字段或按钮；本次未做管理员浏览器截图。
- 安全只读审计未确认已检查的本地高风险路径存在 P0/P1 越权；远征、判题执行和所查认证/IDOR 路径未发现学生可伪造结果。当前开发容器 Piston 端口只绑定 `127.0.0.1:2000`，使用独立执行网络；既有本地隔离烟测覆盖非 root、宿主临时文件/loopback 隔离、超时和外网 socket 拒绝。生产 Piston ACL/代理鉴权及全 API 逐路由审计未验证。
- 修补 Graph 共用 AST 安全扫描器漏查 `from builtins import __import__ as alias` 的问题，避免学生代码或参考解绕过禁用导入检查后才进入 Piston；这是纵深防御修复，Piston 仍是代码执行的隔离边界。Boss 与章测集成测试 295/295、Ruff、`git diff --check` 通过；动态导入变体的完整绕过测试和生产 Piston ACL 未验证。
- 修复五类游戏写入口允许 TEACHER 修改自身游戏记录的问题；拒绝发生在限流和业务服务调用前，STUDENT/ADMIN 沿用原逻辑。存档快照在 MVC 绑定前及两层 Nginx 限制为 256 KiB，超限 413、服务不调用。5 个 MVC 测试类 16 项通过，HTTPS/双协议合同检查与两份 Nginx 语法检查通过。
- 学习事件单条与批量写入现分别限制原始请求体 16 KiB / 512 KiB，复用 MVC 绑定前过滤器并同步到两层 Nginx；保留既有每条 payload 4 KiB、批次 100 条规则。测试先复现超限请求穿过安全链（200），修复后超限 413、边界请求放行；`PublicReadAccessTest`、HTTPS/Nginx 契约脚本、脚本语法与 `git diff --check` 通过。未做 Nginx 运行态或性能压测。
- 匿名登录、注册、刷新请求现在在 MVC 绑定前限制为 4 KiB；登录字段与刷新 token 另有长度校验，超限不会进入 BCrypt/JWT 处理。边缘与前端 Nginx 同步限额；`PublicReadAccessTest`、`AuthControllerTest`、HTTPS/Nginx 契约及两层 Nginx 1.27 容器语法检查通过。生产代理部署态未验证。
- Tutor 非聊天事件拒绝内联图片别名、base64/bytes 字段与图片 data URI；正式 CHAT 的私有 `attachment_ids` 通道保留。旧实现回归先证实可接受别名，修复后 Tutor 四组定向 Graph 测试 72/72、Ruff 和格式检查通过；未调用模型。
- Courseware 问答的 Java 与 Graph 输入契约已对齐：`kcCodes` 最多 65 项、每项最多 32 字符；Java `topK` 限定 1–12，与 Graph 一致，并保留空值默认值。TDD 先复现超长列表/元素被接受；Java 定向回归 3/3、Graph 回归 4/4、Ruff 与格式检查通过。未调用模型或真实 API。
- 本轮新增修复：普通 Boss 题通过事件改为按用户与题目指纹幂等，复用现有 `kc_mastery_event` 唯一索引；Twin 最近三次练习与最近练习时间统一包含 `PRACTICE_PASS/FAIL`、`MICRO_CHECK_PASS/FAIL` 和 `DEBUG_FIX`。没有新增表、模型或 Agent。
- 本地 320px 路由巡检覆盖 26 个页面/别名，均返回应用文档并呈现非空内容；预期角色路由和旧入口按规则重定向。`pageerror=0`、HTTP 5xx=0、横向溢出=0。测试阻断了除登录刷新与管理员状态切换外的写请求，且未触发 Tutor/出题模型。
- 修复公告板空数据掩盖加载/错误的问题；修复课件页码映射 pending/error 无状态提示或重试、标记已读失败静默的问题。课件阅读器与任务板定向测试共 7/7、typecheck、定向 ESLint 通过；失败时不会伪造本地已读进度。
- 复核 Twin 数据可信边界：`SelfDimensionRequest` 只接受主观分数与标签，访谈证据不会提升可信 evidence；掌握度账本对有服务端 idempotency key 的判题事件跳过重复写入。当前快照后 fresh Maven 定向回归 19/19（画像 DTO 2、控制器 5、掌握度账本 12），无代码改动。
- 修正旧 `/lecture/:chapter` 页面误报：视频进度明确标为会话内、不声称写入画像；B站外链点击不再冒充已观看；讲义失败时不亮完成标记。TDD 在旧实现上复现 4 项失败，修复后相关回归 22/22、typecheck、定向 ESLint、`git diff --check` 通过；未调用模型或写业务库。
- 修复主线视频进度可瞬间虚报整批秒数：播放开始用 `deltaSec=0` 建立服务端时间基准，正增量最多按两次上报的服务端时间差累计，保留单次 120 秒与每分钟 2 次限频；V221 只新增 nullable 时间戳列、不回填旧记录。完整 Flyway + 仓库集成 2/2、服务 22/22、请求校验 5/5、视频组件 10/10，前端 typecheck / 定向 ESLint / `git diff --check` 通过。该校验限制加速虚报，不能证明视频真实播放或标签页持续可见。
- 追踪并恢复本地 KC-004 AI 出题链路：Graph 需要隔离 Piston 和当前开发栈的临时内部令牌；按现有 `dev.sh --sandbox` 配置后，Graph `/readyz` 正常、令牌匹配，Piston 沙箱执行 `print(2 + 2)` 返回 4。最终一次 KC-004 请求由 DeepSeek 真实生成并返回 HTTP 200、`aiGenerated=true`、`reviewed=true`。排查中裸 `docker compose` 重建曾造成一次临时 401，已用运行中的令牌恢复。生成审查记录与临时学生账号已精确删除；该题未保留到本地题库。重启 Graph 时必须通过 `./dev.sh --sandbox` 或注入同一运行令牌，裸 `docker compose up` 会绕过临时令牌同步。
- Boss 互动素材/场景已落地：Phaser Boss 场景加载 Kenney CC0 冲击音效，素材记录同步在 `LICENSE-ART.md` 与像素资源 license；场景回归 4/4、窄屏整页回归 1/1。按 KC 是否有可用代码判题题目选择题型；不适合代码判题的 KC 复用同 KC 审核客观题（选择、判断、填空），答案由服务端判定；用户再次确认该替代规则。浏览器真实通关已覆盖三种客观题。
- **事务修复之前已完成完整 Boss 浏览器闭环：** 普通注册的 STUDENT 完成 10 层服务端远征，11 道题均为已审核课程选择/判断/填空题；KC-004 Boss 三阶段通过，Phaser 画布显示受击反馈，DB 有 3 条通过结算，排行榜 `cleared=true` / depth 11。Twin KC-004 为掌握度 0.48、证据 `PRACTICE_PASS`，分身页加载成功；浏览器 `pageerror=0`、HTTP 5xx=0。临时账号及 57 个直接外键引用均清理为 0。预生成请求在浏览器侧隔离为 IDLE；本次没有调用 DeepSeek。本轮之后的事务改动由 PostgreSQL 故障注入回归覆盖，未重复跑浏览器 Boss 通关。
- 修复远征判题已落账、Twin 学习事件随后失败时造成的证据丢失窗口：验证 attempt、掌握度事件与复习状态现同事务提交，失败回滚后同一 attempt 可重试。PostgreSQL 故障注入确认重试只留下 1 条掌握度证据、1 条复习记录，并重放已保存结果；Boss、计分、远征 PostgreSQL 集成与应用上下文测试共 108/108 通过，定向 Spotless 通过。
- 修复 Twin 对未评估 KC 的主线推荐丢失章节/KC 目标：推荐 URL 携带 `chapter`/`kcCode`，地图只选中有效且已解锁章节并展示推荐 KC。相关四组前端测试 35/35、typecheck、定向 ESLint 通过。
- 非 AI 写入路由续审发现同步 `/api/media/storyboard/video` 绕过既有每账号每分钟 3 次的合成额度。现与异步 job 端点共享 `StudentWriteThrottle`；旧代码下新回归先因缺少预算依赖失败，修复后媒体控制器授权、预算覆盖和新限流测试 14/14 通过。前端目前仅保留 API helper、没有页面调用者；全路由与多行日志审计仍未完成。
- 日志续审发现图片生成失败时会原样输出上游 `message`；本地 HTTP 假响应先让脱敏回归失败，改成只记异常类型后 `XfyunImageServiceTest` 3/3 通过。没有访问讯飞。后续静态复核确认 TTS、ASR、旧讯飞图片理解服务的开关固定为关闭，相关上游错误日志分支当前不可达；未为不可达 provider 改代码。
- 安全边界续审：匿名下载统计 `POST /api/downloads/{key}/hit` 仅用于展示，Nginx 对来源 IP 限制 5 次/秒、突发 10；Tutor 内部 checkpoint 与附件读取要求常量时间比较的 `X-Internal-Token`，并校验 `user:<id>` 主体。源码未发现这三项的新 P0/P1；这不代表全 API 限流与日志审计已经结束。
- 补齐世界 NPC 状态路由的角色授权：原 `GET /api/world/npcs`、`GET /{npcKey}/state` 和 `POST /{npcKey}/talk` 未限制角色，且 GET 首次访问会建存档；现与世界状态接口一致，只允许 STUDENT/ADMIN，TEACHER 在限流/业务服务前收到 403。RED 先观察到 TEACHER 请求返回 200；修复后 NPC controller/service 与路由键回归 17/17 通过。无业务数据或模型请求。
- 补齐 22 个不适合代码判题的概念/操作型 KC 的选择、判断、填空覆盖；新增 V220 的 25 道课件有据备用题，包含前次遗漏的 V217/V218 备用题后，题库清单为 140 道（96 道主线必答 + 44 道备用），不改变主线要求。KC-018/023/039/063 仍按可编程主题走 AI 编程题生成路径。
- **4 个可编程 KC 的同 KC Golden 兜底已补齐：** KC-018/023/039/063 在 AI 出题或审查失败时返回经过接地审查的编程题，保留目标 KC、章节与请求难度，并使用独立题目 ID；KC-039 所需的 `import` 语法限定在第 5 章白名单。真实本地 Piston 执行 12/12 用例通过，Graph Boss 回归 270/270 通过，Ruff lint 与 `git diff --check` 通过；没有调用 DeepSeek。
- **章测→Twin 缺口已修复：** 普通学生经 Web 完成一场第一章章测，33 题得分 100、pageerror=0、HTTP 5xx=0；旧实现只把 KC-006、KC-007 两个编程题 KC 回流。TDD 复现后改为所有带 KC 的题型按服务端判分发事件。修复后服务、listener、memory mirror、PostgreSQL 逐 KC 写入与提交幂等共 34/34 通过，选择、判断、填空、编程均有覆盖；没有重复发起 DeepSeek 请求。临时账号及关联记录均核实清理为 0。
- **修复后全题型浏览器复验未做。** 全题型提交到 PostgreSQL 掌握度账本已有服务集成测试；真实浏览器章测发生在修复前。为避免重复的模型生成，本轮没有再生成试卷。
- 工程规范复核：对照 [Google Java Style](https://google.github.io/styleguide/javaguide.html)、[PEP 8](https://peps.python.org/pep-0008/)、[typescript-eslint 命名规则](https://typescript-eslint.io/rules/naming-convention/) 与 [Vue 风格指南](https://vuejs.org/style-guide/)。本轮触及代码遵守项目既定命名约定；完整前端 ESLint/typecheck、Graph 与 RAG 的 Ruff 检查通过，修改 Java 文件定向 Spotless 通过。整库 Spotless 检查报告 638 个 Java 文件格式不符；没有批量格式化这些历史文件。试运行全仓 TypeScript 命名规则产生 43 个合法 PascalCase 组件绑定/测试辅助名称误报，因此未启用会误伤现有约定的全局规则。全仓历史格式债务仍未清理。
- 本轮快速只读安全续审未确认新的本地 P0/P1。公网 Piston ACL、ECS/Nginx 运行态及全 API 逐项审计仍未验证。
- 注册额度审计后，为公网 `POST /api/auth/register` 增加独立每来源 IP 每分钟 5 次、突发 30 次的 Nginx 限速；登录和刷新仍保留原 5 次/秒规则。注册限速契约先 RED 后 GREEN，HTTPS/Nginx 契约脚本通过，Nginx 1.27 `nginx -t` 通过。未发注册请求或访问 ECS。
- 收紧剩余已确认请求与日志边界：Graph 422 只返回错误位置/消息/类型，不回显原始输入；前端 Nginx 与 RAG Uvicorn 关闭 access log，避免第二层记录签名 query；费曼解释限 2,000 字，意图路由的 KC 列表/元素限为 65 项/32 字符，B 站条数限 1–8。RED 确认旧行为可接收超限解释、KC 列表和 `limit=9`；定向 Java 回归 73/73、Graph 回归 1/1、Ruff、Spotless、日志与 HTTPS 契约、`git diff --check` 通过。
- 修复两个事务竞态：评估接受/保留先通过 `user_id + PENDING` 条件更新原子认领状态，认领失败返回 409 且不改路径；班级加入把成员写入和任务补发收进同一事务。评估服务与 PostgreSQL 接受路径回归、班级服务回归均通过；没有并发压力测试或班级补发故障注入。
- ASR 已禁用，因此在 Spring multipart 解析前由安全过滤器返回既有 unavailable JSON；公网和前端 Nginx 将该路由限制为 1 KiB，公网复用上传 IP 限速 key。安全链回归确认匿名请求仍 401、认证请求不进入 MVC multipart 解析；前端与主机 Nginx 1.27 `nginx -t` 均通过。
- 补齐两处 MVC 绑定前的原始请求体上限：`POST /api/events` 限 16 KiB；内部 Tutor AutoGen checkpoint `PUT` 限 3 MiB，避免共享 token 在 Controller 校验前先解析任意大 JSON。边缘/前端 Nginx 同步限额；Java 定向 21/21、HTTPS/Nginx 契约、两层 Nginx 1.27 语法检查及三个 Java 文件定向 Spotless 通过。checkpoint 3 MiB 上限尚未用最大真实 AutoGen 快照验证。
- 日志边界首轮静态扫描未找到应用自建的请求/响应体日志 filter、`RequestBodyAdvice`/`ResponseBodyAdvice` 或日志 aspect；Graph、RAG 和前端容器 access log 均关闭，公网 Nginx 安全格式只记录 `$uri`。全量多行日志调用点与生产运行日志仍未完整核对。

## 附加任务核对（2026-10-16）

- 工坊聊天历史完整 JSON 现于 Spring MVC 绑定前限制为 128 KiB，保留原有 64 KiB payload 校验；过滤器回归 5/5、HTTPS/Nginx 契约、两层 Nginx 1.27 `nginx -t`、定向 Spotless 与 `git diff --check` 通过。
- 正式提交、Boss 判题与 Bug 矿洞重试/变式提交现于 Spring MVC 绑定前统一限制原始 body 为 64 KiB，DTO 的 8,192 字符代码限制不变；旧过滤链下的超限回归为 200，修复后 5 个过滤器测试全绿，Nginx 契约与两层配置语法通过。

- **Tutor / Alethicode 对齐：** fresh 对照确认 Pythontopia 已用同版本 AutoGen 固定阶段运行，并覆盖 Alethicode 的主线/辅助/PLAN 事件及图片隔离、取消和 checkpoint 恢复；保留 Pyth API 与存储更安全。一个用户可见差异仍开放：Alethicode 的 TRANSFER 会物化成可判题临时题，Pyth 目前只展示卡片；补齐需接通可信题目创建、正式判题和提交回流，不能把客户端草稿当作权威题目。Alethicode 对应 runtime 处于未提交工作树，不直接复制。
- **DeepSeek / 图片 / 语音：** Web 模型出口固定为 DeepSeek，Tutor 图片走 vision 档并输出文本；源码复扫确认 Graph 在线路由共用 `provider_from_env`，客户端只接受 `api.deepseek.com` 与允许的 DeepSeek 模型，Mem0/Zep 记忆固定本地；TTS、ASR、图片生成/理解 provider 在属性层固定关闭，前端没有语音合成调用。已有真实文本与 PNG 输入记录，本次不重复发模型请求。隔离的 Qwen 研究脚本未接入 Web 路由。
- **管理台对齐：** 运维、题目审核、用户管理三页已有真实 API、分页、筛选、状态反馈与窄屏布局，与 Alethicode 的可用视觉模式接近；Alethi 当前管理台有大量未提交改动，且多项菜单在本项目无对应契约，不复制。
- **远征安全源码复核：** 未发现可复现的 P0/P1 越权。远征身份、HP/进度、节点动作、判题结算和排行榜分数以服务端状态为准；Boss 失败反馈真实体力，成功触发受击演出。2026-10-16 的合法学生浏览器实跑已完成 KC-008 Boss 三阶段，并核实 Twin 回流与账号清理。
- **审计边界：** 前端 SVG 外部资源引用已用恶意输入回归及拦截式 Chromium 探针验证并修复；脚本载荷、全应用 XSS、CSP/Trusted Types 仍未动态验证。其余安全结论基于源码与既有测试阅读，不代表全 API、生产网络或屏幕阅读器运行态验证；安全复核未调用模型，也未访问 ECS/GitHub。

## 本轮验证证据
- **KC 客观题优先级与 Twin 最新实跑：** 浏览器发现 KC-002 有同 KC 审核客观题却返回预生成 CODE。TDD 先复现，再改为没有课程编程题时先发同 KC 客观题，覆盖活动代码预生成优先级；已有客观题仍能刷新恢复。第一章 KC 只在有同 KC 客观题时允许开局。最新普通学生 Web 账号创建 10 层远征，KC-001 判断题刷新恢复、真实判题 1/1；Twin 记录 PRACTICE_PASS（掌握度 0.14），真实分身页正常；pageerror=0、HTTP 5xx=0。BossServiceTest 65/65、BossControllerTest 18/18、定向 Spotless 通过。临时账号及关联记录已清理；使用审核题，没有调用 DeepSeek。完整三阶段 KC-008 路线不在此补丁后重跑，见下方范围说明。

- RED/GREEN：最新 `BossServiceTest` 62/62、`BossControllerTest` 18/18、`BossAttemptIdempotencyPostgresTest` 6/6、`MainlinePracticeServiceTest` 21/21；V217/V218 已在本地开发库应用，schema version 为 218。
- 修复 Mermaid SVG 内联样式在 SVG CSSOM 缺失环境抛错的问题；原全量运行中相关图示用例失败。校正 Tutor 过程测试的按钮选择及颜色序列化断言后，聚焦测试 25/25、全量 Vitest 276 文件/2620 用例通过；typecheck、ESLint、`git diff --check` 均通过。
- 实际 Boss 题目/判题 API 使用临时学生跑通：重启 Java 服务加载当前构建后，日志确认选中已审核题 `review=85/version=2`；公开题答案由服务端判题 3/3 通过。`aiGenerated=true` 是已审核题的来源标记，不是本次生成；没有调用 DeepSeek。每个临时账号都在脚本 `finally` 中按精确用户名删除。
- **真实远征浏览器闭环（已完成）：** 通过普通注册表单建立临时 STUDENT 账号；KC-003、KC-004 各完成 4 道课程审核选择/判断/填空题，服务端判题通过，Twin 掌握度均到 0.78。10 层服务端远征中，4 道浏览器编程挑战通过；KC-008 Boss 的 3 个阶段分别以判断、填空、选择题通过。浏览器确认 Phaser 场景和每阶段受击反馈；数据库核对 3 条 Boss 阶段结算；Twin 页面将 KC-008 从“尚未评估”更新为掌握度 1，证据来源 `PRACTICE_PASS`。全程 `page_errors=0`、HTTP 5xx=0。脚本 finally 删除隔离账号并通过外键残留审计；本次没有调用 DeepSeek。
- **继续收口本轮发现的两个数据正确性边界：** 自动选择 KC 客观题时，即使有同 KC READY 编程题，客观题池用尽也会明确返回 `BOSS_OBJECTIVE_BANK_EMPTY`，不回退成错误题型；学习事件批量接口写到第 N 条遇数据库错误时整批回滚。填空 Boss 题新增服务端归一化判分与 `PRACTICE_PASS` 回流测试。PostgreSQL 故障注入 RED 证实旧行为留下第 1 条事件，GREEN 后事件数为 0；`BossServiceTest`、`LearningEventControllerTest`、批量原子性 PostgreSQL 测试合跑 75/75，4 个 Java 文件定向 Spotless 与 `git diff --check` 通过。未重跑已通过的浏览器通关，也未调用模型。
- **历史首次续测（已被上项取代）：** 当时 KC-008 没有可用审核题，生成服务也不可用，未能通关；临时账号已清理。之后增加了客观题路径与课程题库后，已由上项成功完成同一主链路。
- Playwright 路由巡检：26 个路径，320px viewport，无横向溢出、前端运行错误或 5xx；管理员三页可打开，用户状态按钮真实 API 往返成功。
- 客观题补充按 TDD 先在旧实现上复现 KC-001 缺少选择/填空题，再加 V220；Testcontainers 从空库全量应用 135 个 Flyway 迁移后，迁移覆盖测试 2/2 通过。22 个非编程 KC 均有 CHOICE/JUDGE/FILL，主线必答仍为 96；`BossServiceTest` 62/62、`MainlinePracticeServiceTest` 21/21 通过。题库 JSON 与 V217–V220 补充项核对为 140 道（96 + 44）。本次未调用 DeepSeek；本地开发库已升至 schema 220，SQL 核对 22/22 KC 全覆盖。
- 学生状态切换后旧 JWT 被拒并重定向登录；重新登录后巡检通过，符合账号状态变化吊销旧会话的预期。
- **管理台审核写入闭环（已完成）：** 临时注册一个申请学生与一个本地提升的 ADMIN，直接写入两条带随机标记的待审兼容题 fixture；Playwright 登录真实管理页面，按题目 ID 搜索后分别通过与驳回。搜索 API 各返回唯一目标记录，PATCH 均为 HTTP 200；页面状态、PostgreSQL 的 `decision/review_source/version/decider_id/reason` 一致，`pageerror=0`。未调用 DeepSeek。脚本按精确题目 ID 清理后再删账号，数据库核验题目与账号残留均为 0；这是本次临时脚本闭环，未新增长期测试账号或工具。
- **缺题 KC AI 生成定向实测：** 每个 KC 只请求一次真实 `/api/teacher/problem-reviews`。KC-018/023/063 均返回匹配 KC/章节的 `CODE` 题，`reviewed=true`，有参考解和 3–4 个测试（各有样例与隐藏用例）；KC-039 最终候选因参考输出不一致未通过审查并返回 503，之前的模型生成尝试有连接超时。Graph 日志确认 DeepSeek 请求。临时审核账号关联的候选、用量记录和账号已全部清理，未重复重试；通过候选未进入长期题库，因入库需要稳定的实际审核主体。
- 临时账号在脚本 `finally` 中删除。此路由巡检没有提交章节试卷、启动远征节点或执行生成/判题写操作。

## 仍未完成或未验证

- **AI 生成候选尚未进入长期题库。** KC-018/023/063 的单次 DeepSeek 候选通过 Graph 审查、自测，但因审核账号临时且关联外键不可删除，清理时一并删除；KC-039 的 AI 生成仍返回 HTTP 503（`reference_output_mismatch`，之前有 `ConnectTimeout`）。四个 KC 现均有经过审查的同 KC Golden 兜底，学习流程不再依赖 AI 生成成功。若要把 AI 候选入库，仍需稳定、真实的审核主体。
- 管理台审核真实通过/驳回与一次性 fixture 的清理已验证；未新增可复用的账号清理脚本，因为本轮精确 finally 清理和外键残留核验已覆盖所需闭环。
- Twin 新旧选题 A/B、独立答题率与 7 日延迟复测需要真实学习样本及时间跨度，当前无结果。BKT/未来路线概率按计划暂缓。
- 非 AI 写接口账号限额与多行/包装器日志尚未完成全量审计；本轮粗盘 112 个写路由，确认 `POST /api/events` 已有 4 KiB payload 校验与每账号 100 次/分钟预算，并补上整个 JSON 请求在 MVC 绑定前 16 KiB 上限；内部 Tutor checkpoint `PUT` 在共享 token Controller 校验前可解析请求体，本次补上 3 MiB 后端与双 Nginx 限额。日志首轮静态搜索未发现通用请求/响应体转储 wrapper；Graph、RAG、前端容器日志关闭，公网 Nginx 只记不含 query 的 `$uri`，但多行调用点和生产日志未逐项验证。此前已续核 Bug 矿洞判题、学习事件/班级预算、存档快照与 Loop Runtime 请求体；同步媒体合成额度、工坊上传额度及 Xfyun 图片生成日志也已修复。相关上传/服务/检索回归 32/32，本轮请求体 Java 定向回归 21/21。TTS/ASR/图片生成/图片理解固定关闭；剩余逐路由审计、生产 Piston ACL、公网 CORS/Nginx 运行态仍未验证。课件阅读完成仍由客户端报告已读页 ID；服务端只校验 KC 页码白名单，不能证明页面被实际阅读。
- 本轮只读续审再次检查未显式标注限流的写入口：班级加入有每账号 30 次/10 分钟及边缘 IP 限速，媒体合成在服务层共用每账号额度；20 个 Java Controller 方法的 `@AiBudgeted` 均进入共享预算拦截器。Graph 资源失败消息为固定文本，Tutor provider 异常映射为固定错误；没有确认到可达路径把原始 provider 异常返回给学生。该轮仍是定向源码扫描，非全 112 路由与所有日志包装器审计。
- ASR multipart 解析前风险已在本地收口：认证后 Spring 过滤器直接返回 unavailable，公网/前端 Nginx 限 1 KiB，公网复用每秒 2 次、突发 5 次上传限速。两层 Nginx 语法和静态限额契约通过；生产部署态未核验。
- 匿名注册的同 IP 滥用已由独立边缘限速收窄，但代理池/多 IP 仍可持续创建账号；邮箱没有验证。该 Nginx 改动尚未部署到生产验证，本地直连开发 API 不经过此规则。生产共享 AI 日预算仍是每账号 30 次、全站 300 次，本地按用户要求关闭应用级请求限额。
- DeepSeek 供应商账单/token 需要从控制台核对；本地请求上限已关闭，但不代表供应商无额度或无费用。
- **依赖扫描已覆盖 Web 锁文件、Graph 本地 venv 与 Maven 树，但仍有未修复条件风险：** npm 官方审计为 0 项；Graph OSV 扫描 220 个精确版本发现 `diskcache 5.6.3`、`ragas 0.4.3` 两个可选评测依赖受影响，它们不进 Docker 运行镜像且目前无上游修复版。Maven 初始扫描发现多项运行依赖受影响，已把 Spring Boot/springdoc 及 Jackson、Tomcat、PostgreSQL JDBC、Log4j、Commons Lang 升至已核对的补丁版本；更新后 140 个 Maven 坐标仅剩 Spring MVC SSE + view fragment 条件告警，本项目没有 view fragment 用法。Spring 官方只把 Spring 6.2.20 修复版提供给 Enterprise，当前 Maven Central 最新 OSS 构件为 6.2.19；因此不为该不满足利用条件的告警跨 Boot 主版本迁移。Maven 全量测试 1647 项、0 断言失败但 6 项有 Testcontainers 端口/生命周期错误；因此测试套件未全绿，不能宣称依赖全无漏洞。
- 生产 Piston 私网 ACL/代理鉴权、生产 CORS/Nginx 运行态及公网安全未验证；本轮不访问 ECS。
- 全仓历史格式债务与命名一致性尚未做破坏性批量整理；仅清理经过验证且本任务触及的范围。
- 课件 PDF.js 实际渲染、任务板/课件真实后端点击闭环与键盘操作未做本轮浏览器验证；教师补救包长证据页在 320/375px 下的潜在裁切尚未复现。
- 管理台本次 CSS/审核快捷操作改动尚未做浏览器截图和真实管理员账号联测；已有此前的真实审核通过/驳回 API 闭环。
- 存档超限过滤器已有隔离 MVC 测试及 `PublicReadAccessTest` 完整 SecurityFilterChain 联测（测试主体经 mock JWT parser 解析）；签名校验本身未在该联测覆盖。全量 Maven suite 未运行，生产 Nginx 部署态仍未核验。
- Loop Runtime 原始请求体曾可到 12 MiB，Jackson 先绑定成 `JsonNode`，Controller 才做单字段 1 MiB 检查，且账号限流更晚；现复用绑定前过滤器，按 endpoint 聚合限制为 3 MiB（保留 tool-call 两个各 1 MiB 字段），两层 Nginx 同值。旧行为回归先对 3 MiB+ 请求返回 200，修复后安全链对 Agent Runtime/Loop Runtime 路由在 MVC 前返回 413；相关 Maven 8/8、Nginx 契约、两份 Nginx 1.27 语法检查通过。最大 12 MiB 的实际内存影响未压测。
- 性能仍后置；既有本地采样仅覆盖建局 API/渲染，不覆盖完整 Graph 生成、Piston 判题与实际远征通关 P50/P95。
- 最新完整路线目标 KC 为 KC-004；之前 KC-008 三阶段、KC-001 刷新恢复记录仍作为独立历史证据。题库审核题属于课程迁移种子，不是临时账号数据。

## 下一步顺序

1. 普通学生浏览器 Boss 三阶段通关与 Twin 回流已完成，此后事务修复由 PostgreSQL 故障注入测试覆盖；不要重复注册账号或重跑通关。Boss 预生成隔离为 IDLE，实时 Graph 预生成未在该浏览器验收中覆盖。
2. 继续剩余非 AI 写路由及包装器/多行日志审计。当前已修复已确认的请求体超限、远征写限流、访问日志与 ASR multipart 解析问题；112 个写路由的全量逐项审计仍未完成。公网注册同 IP 限速已加，代理池与生产运行态不在本地可验证范围。
3. 若继续补齐 Tutor 的 Alethicode TRANSFER 行为，必须先打通服务端可信题目、正式判题和提交回流；不能把前端草稿当题目或证据。现有卡片只支持思路反思，不宣称完成了变式题判题。
4. 不重复发起 DeepSeek 生成。AI 候选入库需有稳定审核主体；KC-039 仅在约束或模型状态变化后再单次复核。
5. 等待足够真实样本完成 Twin 7 日延迟对照；BKT/未来路线概率按精简方案暂缓。完整远征生成、判题和通关性能最后测。
