# Pythontopia 执行状态实时记录

> 该文档与 `docs/project/CHANGELOG.md` 配合使用：CHANGELOG 记录“已完成”，本文档记录“正在做+下一步+卡点+最近 2 分钟动作”。
> 每完成一个关键节点都要追加；每 2 分钟最低汇报一次。
> **当前快照（2026-10-15）**：整体聚焦 Web 主线与其共享后端，HEAD 为 `99126642`；当前工作树包含多类敏感日志脱敏改动、配套回归和唯一清单/工作日志更新。最新本地提交包含 ReviewHallPanel 路由修复、`/examples` 静态重定向与退役页面删除、题库有效零题 KC 筛选修复，以及对应回归和跟踪文档；路由集成回归 27/27 通过。历史记录已有 DeepSeek 文本与 PNG 输入真实成功；后来也有 Tutor relay 超时、空 JSON 和 503，因此不重复通用探针。Graph 只允许 DeepSeek host/model；TTS、ASR、旧讯飞文生图/图片理解硬关闭。Tutor 使用固定阶段 AutoGen 和现有 SSE/API/checkpoint 合同；当前无须再做一次运行时替换。AI 题闭环已有 KC-010 判题、KC-004/002/006/011 主线生成与自动审查；KC-002、KC-006 另有共享题库取题和服务端正式判题记录，测试账号与学习证据已清理；KC-011 尚无正式学生判题或掌握度证据。KC-006 最新生成题由审核 ID 84 自动批准，第二名学生已正式判题通过，但 `kind=NULL`，不算远征题型覆盖。远征根 KC 的批准题仍不匹配普通战斗节点题型，地图生成不预检覆盖；活动 run 刷新恢复及正式先修证据仍待。Twin P0、管理台已有对应代码与定向/浏览器记录，Twin A/B、7 日延迟复测及剩余 Web 路由/动作矩阵未完成。题库有效零题深链已修复并经 320px Chromium 复验；窄屏抽样还覆盖主线阶段、题库、学习分身折叠区和图书馆。当前静态安全审查未确认高危代码问题；生产 Piston 若无私网 ACL 或鉴权代理仍有未授权执行风险，ECS/公网状态不访问、不宣称通过。全量 Maven 最近记录有 0 断言失败、7 个本地 Docker/PostgreSQL 环境错误；不要重复触发已通过的验证。性能最后测。唯一待办清单见 [总任务实施清单](../plans/2026-10-15-web-system-implementation-plan.md)。

## 当前清单摘要

- **已完成：** 敏感学习数据日志脱敏（上传解析 17/17、语料索引 1/1、调试轨迹 1/1、Piston 10/10、Mem0/Zep 7/7、Graph 重试 1/1、画像访谈 1/1、NPC 2/2、记忆向量 7/7、B站问答 4/4、Feynman 4/4、BROWSE 5/5、B站搜索 2/2、BossService 53/53、章测 Graph 2/2）；Python/Maven 当前运行时 OSV 扫描 0 项已知漏洞；BROWSE SSRF 回归 58/58；KC-006 跨学生共享题库正式判题；有效零题 KC 题库深链修复；320px 下题库零题筛选与学习分身“近期变化”展开可用；学习分身推荐主线动作、无效章节测评错误态与返回地图动作已由浏览器验证。临时账号均已清理。
- **未完成：** 远征题型兼容的端到端生成与活动 run 刷新恢复；剩余 Web 动作矩阵；扩展安全审计（其余异常日志类别仍待确认）；生产 Piston/ECS 运行态；Twin A/B 与 7 日复测。性能最后执行。Python 开发 venv 有旧包 advisory 待环境刷新；运行时 Python/Maven 的 OSV 版本查询均无已知漏洞。全仓 Spotless 仍有 600+ 既有格式违规，触及文件也存在既有格式差异；未做批量格式化。
- **下一步：** 先验证符合真实学习证据的远征活动 run 恢复与兼容题路径，再补剩余 Web 页面动作矩阵；继续审计其余异常日志类别。本轮 DeepSeek 调用 0 次，不重复通用探针。

## 2026-10-15 当前续接：运行时依赖与 SSRF 审计

- 新解析的 Python Graph/RAG 运行时依赖分别 72/28 项，OSV 扫描 0 项已知漏洞；Maven compile/runtime 共 95 个 JAR，OSV PURL batch 查询 0 项。Python 本机开发 venv 另有 25 个旧包名、212 条 advisory 行，未据此推断容器运行时，也未在服务运行期间升级环境。
- OWASP Dependency-Check 插件因缺 NVD API key 未能更新漏洞库，未产生报告；安全状态保持“未完成”，而非“通过”。
- BROWSE 是唯一接受学生目标并访问外部 URL 的主路径。服务端注入域白名单，Graph 端按请求/重定向执行公网 DNS、scheme、端口与 host 校验，并固定浏览器 host resolver；`tests/test_browse_allowlist.py` + `tests/test_browse_executor.py` 58/58 通过。
- 尚未验证所有日志路径、部署边缘限流和生产 Piston ACL；本轮没有 DeepSeek 请求。

## 2026-10-15 当前续接：日志隐私与 Java 格式收敛

- 上传解析/索引、学生 trace、Piston 错误响应、Mem0/Zep provider、Graph 重试、画像访谈、NPC 对话、RAG 记忆向量与 B 站问答日志均经 RED/GREEN 回归脱敏；其他直接 Graph/HTTP 客户端的异常日志仍待逐个核实。
- 全仓 Spotless 检查失败于 600+ 文件既有格式差异；定向检查也发现本轮触及文件存在既有格式差异，因此撤销了格式化带来的无关行改动，未声称格式检查通过，也未扩散为全项目格式化。
- 后续重点回到远征有效题型/活动 run 与未验收 Web 动作，性能仍最后。

## 2026-10-15 当前续接：外部 HTTP 错误日志脱敏

- Mem0/Zep 公共 HTTP 基类曾把异常原文（含远端 500 响应体）写入 warn 日志并拼入上层异常消息；合成学生记忆标记先 RED 复现，随后只记录异常类型并使用稳定异常消息。Mem0 4/4、Zep 3/3。
- Graph 重试助手曾把远端异常原文写入 retry DEBUG；合成响应标记先 RED 复现，修复后只记录异常类型。`GraphClientRetryHelperTest` 1/1。
- 这两组为本地隔离 HTTP/单元回归，没有 DeepSeek 请求。其他直接 Graph/HTTP 客户端仍有异常消息日志候选，完整安全审计未结束。

## 2026-10-15 当前续接：画像访谈日志脱敏

- `ProfileService` 向 Graph 发送学生自由文本；异常消息可能包含下游响应内容。模拟敏感响应标记先 RED 复现，修复后日志只写异常类型，稳定错误码和提示未变。`ProfileServiceFailureTest` 1/1。
- 本轮不发 DeepSeek 请求。其他直连客户端日志与完整安全审计仍未闭合。

## 2026-10-15 当前续接：其余用户输入日志脱敏

- NPC Graph 对话、RAG 记忆向量与 B 站视频问答的上游错误都能回显请求/响应文本；三个合成标记均先 RED 命中旧日志，再改为仅记异常类型。
- 定向 GREEN：`NpcDialogGraphClientTest` 2/2、`EmbeddingClientTest` 7/7、`BilibiliInsightServiceTest` 4/4。与前面画像访谈 1/1 一样，均未发起 DeepSeek 或产生业务写入。
- 尚未逐项检查的直连 Graph/HTTP 客户端、生产 Piston ACL 和完整安全边界仍在待办。

## 2026-10-15 当前续接：章节测评无效路由错误态

- 本地全栈 Chromium 使用临时学生打开 `/chapter-test/99`，看到明确的 1–7 章范围错误；点击“返回冒险地图”到达 `/mainline-map`。页面错误 0，测评 API 请求 0，浏览器实际模型/判题 API 请求 0；只发生一次认证刷新。
- 捕获到的 `/src/components/tutor/...` 是 Vite 本地源文件 GET，不是 Tutor API。该无效章节路径在挂载时跳过 `loadLatest/prepare`，没有生成题或提交答案。临时学生删除后数据库计数 0。
- 这是错误态与恢复动作抽样，Web 全页面矩阵仍未完成。

## 2026-10-15 当前续接：学习分身推荐主线动作

- 临时新生打开 `/profile-room`，服务端返回 0/65 已评估 KC 和真实推荐；点击“前往主线学习”后到达 `/mainline-map`，浏览器页面错误 0。
- 浏览器只发起认证刷新和学习状态读取，没有题目、Tutor 或判题请求。临时用户 688 删除并核对为 0；未创建学习证据。

## 2026-10-15 当前续接：官方工程规范对照

- 按 Google Java Style Guide 的 import 分组/排序规则，整理本轮新增测试中的 Java import；camelCase/PascalCase 继续遵循仓库 AGENTS 约定。
- 按 OWASP Logging Cheat Sheet 对源码、个人信息和凭据的日志限制，脱敏回归覆盖上传、记忆、访谈、NPC、RAG 与外部调用错误。
- Vue 与 TypeScript 命名规则已作为后续前端收尾基准；本轮没有 Vue/TS 业务改动或新增 lint 配置。全仓 Spotless 基线仍有既有违规，没有执行全仓格式化。

## 2026-10-15 当前续接：Graph 学习内容异常日志脱敏

- Feynman 对话/找茬、BROWSE、B 站检索、Boss 题目生成/判题的 RestClient 异常曾把远端错误正文写入日志；章测 Graph 客户端还把错误正文放进由预生成任务记录的异常消息。合成敏感标记均先 RED 命中，再改为仅记录异常类型或稳定错误消息。
- 定向 GREEN：`FeynmanGraphClientTest` 4/4、`BrowseGraphClientTest` 5/5、`BilibiliVideoServiceTest` 2/2、`BossServiceTest` 53/53、`ChapterTestGraphClientTest` 2/2。判题本地回退、BROWSE 单次调用、章测 fail-fast 行为未变；没有模型 API 请求。
- 其余内部服务异常日志、生产 Piston ACL 与公网运行态仍未核实；安全审计保持未完成。

## 2026-10-15 当前续接：KC 状态与远征题型链路复核

- 清单校正：KC-011 的较早生成失败记录之后已有一次 `APPROVED/AUTOMATED` 入库成功；仍没有正式学生判题或掌握度证据，不再重复生成。
- 源码核实：`RogueRunStore.beginChallenge()` 把节点 `challengeKind` 交给 `useChallengeJudge`，由前端作为 `preferredKind` 传入 Boss API；后端按 KC/题型读取已批准题，未命中时再将题型传给 Graph。当前缺的是兼容题库内容与合法活动 run 的真实验收样本，不是客户端漏传字段。
- Docker 中的 Pythontopia Graph 容器为 healthy；宿主机 `127.0.0.1:8000` 未映射，不能据此判断服务停止。本轮没有模型请求或远征数据写入。
- 320px Chromium 在 `/profile-room` 展开“近期变化”折叠区，展开前后文档宽度均为 320px，页面脚本错误 0。脚本的断言曾把 Vite 静态模块 `/src/api/tutor.ts` 误当作 AI API；它是本地源码 GET，不是模型调用。测试账号 685、686 均已删除，用户、学习事件、掌握事件、访谈记录计数均为 0。
- 下一步继续只挑清单里尚无真实证据的非模型 Web 动作；不重复做题库筛选、训练判题、Tutor/DeepSeek 探针或性能测试。

## 2026-10-15 当前续接：汇总只读审查

- Tutor 对照确认 Pythontopia 已有 AutoGen 与 run-scoped checkpoint；保留自身 SSE、附件、授权和持久化合同，不复制 Alethicode V124/thread dispatcher。DeepSeek 真实调用未重测。
- 远征审查确认服务端判题、结算、恢复路径已有实现；已批准题库缺普通战斗题型覆盖，地图生成不预检覆盖。待正常学习路径建立题型兼容题与先修证据后，完成活动 run 刷新恢复。
- 安全审查未发现确认的 P0/P1 代码缺陷；生产 Piston 无 Bearer 校验，真实私网 ACL/鉴权代理状态未核实。没有访问 ECS/云端。
- 当前接续顺序：先完成远征题型覆盖与活动 run 恢复；再补剩余 Web 页面动作矩阵；最后做工程规范收敛与性能采样。详见总清单。

## 2026-10-15 当前续接：图书馆 RAG 页面动作

- 本地 `/library` 使用临时学生账号查询「for循环」，Playwright 1/1 通过，显示带页码证据；学生权限下服务端 RAG 查询被禁止，页面按设计回退本地检索。没有调用 DeepSeek。
- 精确删除临时 QA 账号并确认用户记录为 0。总动作矩阵仍未完成；未重跑 Tutor、远征或性能测试。

## 2026-10-15 当前续接：公告板页签动作

- 新注册临时学生后打开 `/board?tab=classroom`，确认深链选中班级任务，再切换历史路径和今日任务；目标内容均可见，页面错误 0、模型请求 0。
- 临时账号精确删除并核对为 0。浏览器动作矩阵仍未完成。

## 2026-10-15 当前续接：画像自评证据隔离

- 临时学生在 `/profile` 调整“学习目标”自评。PUT 仅含 `scoreDelta`，服务端重新读取的 `evidenceCount` 与 `confidence` 均未变化；页面错误为 0。
- 临时 QA 用户已删除。该操作只验证自评路径，没有提交练习或改变学习能力证据。

## 2026-10-15 当前续接：题库 KC 深链筛选

- `/examples?kcCode=PY43-KC-001` 的重定向正常，但 KC-001 是有效知识点且当前题库没有编程题，旧逻辑将其误清空。有效零题 KC 现保留筛选并显示“0 题”；未知 KC 仍回退到全部。
- 回归先 RED 后 GREEN；题库页测试 10/10、typecheck、定向 ESLint 通过。修复后真实 Chromium 在 320px 下打开退役路由，确认跳到 `/problems?kcCode=PY43-KC-001`、下拉保留 KC、结果为 0 题，文档宽度为 320px，页面错误和模型请求均为 0。
- 同一轮 320px 抽样检查 `/mainline/stage/2`、`/problems`、`/profile-room`、`/library`，均无横向溢出、页面错误或 5xx；没有业务写入或模型请求，临时账号已清理。临时账号 678 查询计数为 0。
- KC-006 已走正常主线 AI 出题、自动审核、跨学生共享取题和正式判题，测试证据已清理；批准题题型为空。下一步是补远征兼容题型与合法 run 样本。随后继续剩余 Web 动作矩阵和工程收尾，性能最后；不重复通用模型探针。

## 2026-10-15 当前续接：KC-006 主线 AI 题入库

- 只读数据库确认 KC-006 在静态 `course_problem` 与审核表中均无现有题目。用新临时学生打开真实主线第 1 章“计算与计算科学”学习页，点击正常“生成 AI 编程练习”入口一次；页面得到 `PY43-KC-006`、难度 1、`AI · 5 重审查` 的题目，HTTP 200，浏览器错误 0。
- 题目是用 `print` 输出两句计算概念说明；后端审核表新记录 ID 84，`APPROVED/AUTOMATED`，存入共享 AI 审核题库。未提交答案，没有掌握度/学习事件写入。记录的 `kind` 为空，所以只算普通主线题覆盖，不算远征 `dual_track`/`closed_loop` 覆盖。
- 测试请求者 ID 679 因审核请求者外键保留在本地作审计；本轮不删除批准题、不重跑模型探针，也不做第二次生成。远征题型兼容和有效活动 run 恢复仍待。

## 2026-10-15 当前续接：KC-006 共享题库取题与正式判题

- 新临时学生通过相同主线页面取到 KC-006 已批准练习，随后提交两行 `print` 程序；服务端判题 HTTP 200，页面显示“通过”，浏览器错误 0。
- 判题前后共享审核表仍有 1 条 KC-006 记录。提交产生的 1 条学习事件和 1 条掌握度事件随临时账号一起删除；删除后用户、Boss attempt、学习事件、掌握度事件计数均为 0，审核 ID 84 保留。
- 本轮复用批准题，没有再次发起生成。它仍为 `kind=NULL`，不关闭远征题型缺口；下步继续合法的远征兼容题与活动 run 恢复。

## 2026-10-15 当前续接：执行器边界与远征可恢复样本核对

- Piston 当前配置核对：开发 Compose 仅发布到 `127.0.0.1:2000`，既有安全契约包含回环绑定断言；生产 Compose 没有 Piston 服务/宿主端口，外接 URL 限 HTTPS 或私网 HTTP。Piston 自身不校验 Bearer，生产代理/网络 ACL 只能由部署环境确认；遵守不访问 ECS 的约束，未做远端操作。
- 本地数据库只读聚合：21 条 `status=ACTIVE` 的 run 记录，phase 为 challenge 16、map 3、over 2；先修门槛满足第 2 章以上前沿的学生 1 个，且该学生当前没有活动 run。记录可能混有 QA/历史账号，不能解读为真实用户完成率；未查询或输出账号身份。
- 当前没有可直接用于刷新恢复的“合格学习状态 + 活动 run”样本。下一步需通过普通学习流程建立/找到可验证样本，再测 Web 刷新恢复；不写 SQL 伪造掌握度，也不触发 DeepSeek。

## 2026-10-15 当前续接：复盘大厅 Twin 推荐路由修复

- 新生 0/65 点击推荐 KC-001 后原先进入 `/expedition?focus=1&kcCode=PY43-KC-001`；这违反未评估 KC 返回主线的共享路由规则。
- 复用 `routeForTwinRecommendation` 后，`ReviewHallPanel` 定向回归 2/2 通过；typecheck 与触及文件 ESLint 通过。
- 修复后真实浏览器进入 `/mainline-map`，主线地图可见，页面错误和模型请求均为 0；临时账号清理为 0。没有 DeepSeek 请求或正式学习事件。

## 2026-10-15 当前续接：根 KC-004 AI 编程题入库与 Piston 重试修复

- 失败尝试复盘：从 Web 主线 KC-004 按钮触发的请求因 Graph 和 Java 缺少 `PYT_PISTON_URL`/版本配置而 503；一个业务请求消耗 6 次真实 DeepSeek completion（HTTP 200），六个候选都只因 `sandbox_unavailable` 被拒，未入库。该请求账号 661 没有学习/审核关联数据，已清理。
- 修复 Graph Boss 编排：配置不完整时在调用 `problem_factory` 前失败，模型调用数为 0；候选生成后若自测发现沙箱不可用，立即返回失败，不按内容质量策略继续重试。两条回归先 RED（各复现 6 次调用），修复后通过；Graph Boss 全套 257 项与 Ruff 通过。
- 标准重启 `./dev.sh --sandbox` 后，Graph 与 Java 均确认 Piston 配置存在、Python 3.12.0；后端/Graph/RAG/Piston 健康端点全为 HTTP 200。
- 修复后从正常 Web 主线学习页只发起一次 KC-004 “生成 AI 编程练习”请求。6 次 DeepSeek completion 中前 5 个候选 reference 输出自测不符，第 6 个通过五重审查；API HTTP 200，题目 ID `issue-ba2acfc6-ea8e-4572-955b-fa68a10abea7`，`PY43-KC-004`，`APPROVED/AUTOMATED`，数据库审核记录 ID 82。浏览器 `pageerror=0`。
- 题面是字符串重复输出计算思维定义的基础练习。本轮未提交学生答案，没有新增正式判题或 KC 掌握事件；QA 请求者 ID 663 因审核记录外键保留，不作为学习证据。
- 第一轮 UI 自动化点击被 Phaser 画布拦截，在生成按钮之前终止；无模型请求，临时账号 662 已清理。后续按现有 DOM click 验收路径继续，只有上述一次真实生成请求。
- 后续仍需按正常学习路径为 KC-001 取得主线兼容题、为 KC-002/006 补题，再正式判题并验证远征活动 run 恢复。不要重复 DeepSeek provider/Tutor 探针。

## 2026-10-15 当前续接：Tutor 真实 Web 链路与运行令牌同步

- 本轮先从 Playwright failure artifact 确认旧请求失败：工坊显示“路由服务暂时不可用”，Graph `POST /agents/intent/route` 为 401；未到 Tutor/DeepSeek。
- 运行态检查发现 Java 后端未持有 `PYT_RAG_ADMIN_TOKEN`，Graph 使用 `.env` 中的占位令牌。按标准 `./dev.sh` 重启后，Graph 与 Java 持有相同 64 字符临时令牌指纹，三服务 healthz 均为 200；不输出令牌明文。
- 只发起一次真实工坊文本问题。Chromium 1/1、7.2 秒通过；Graph `POST /agents/intent/route` 与 `/agents/tutor/ask/stream` 都是 HTTP 200，测试断言回复非空。没有重发 provider 探针或图片请求。
- 临时学生、Tutor 会话和工坊轮次已清理；按测试用户名查询剩余账号为 0。原失败报告保留作根因证据。
- 继续顺序以总清单为准：先补齐无编程题根 KC 的 AI 出题/审查和正式判题证据，再验证远征活动 run；其后补 Web 动作矩阵、工程收尾；性能最后。

## 2026-10-15 当前续接：班级加入码账号限速

- 公网 Nginx 已有按来源 IP 的班级加入限速（5 次/分钟，额外突发 5 次），并由 HTTPS 契约脚本覆盖；本次只增加后端按已认证学生账号的第二层限速，避免更换来源 IP 绕过账号层配额。
- 失败查码和成功入班请求都在查询前计入 30 次/10 分钟窗口；超限返回 429 `JOIN_CODE_RATE_LIMITED`。未修改既有 6 位码格式及已发放的邀请码，避免破坏现有班级邀请。
- RED/GREEN：加入服务用例先分别观察到错误码仍返回 404、重复/成功加入仍返回 409；实现后 `ClassroomServiceTest` 与 `JoinCodeAttemptThrottleTest` 通过。Nginx HTTPS 契约和双协议发布检查脚本均通过。
- 当前是单后端进程内账号窗口，适配项目单 worker 运行约定；若后续横向扩容，需迁移到共享限流存储。生产 Nginx/后端公网可达性未在 ECS 验证。
- 顺手关闭一项确认的 Tutor 安全问题：异常 SSE 现写服务端堆栈、向学生只回固定错误文案，不再暴露 `str(exc)`。RED/GREEN 后 Tutor workflow / AutoGen runtime / 附件 / image input 定向集成共 93 passed，Ruff 通过。
- Tutor 普通问答与 AutoGen 工作流均按认证用户 ID 在模型调用前预留账号额度，不按 `conversationId` 计数；部署默认 30 次/账号/上海自然日、全站 300 次/日。本地 `./dev.sh` 显式关闭应用日额度，本轮 `TutorDailyRequestLimitServiceTest` 新鲜通过。
- 本轮已停止重复 provider 探针：真实 DeepSeek 纯文本校验请求原样返回，合成 PNG 返回“蓝色圆形、黄色背景”，均为文本响应、无工具调用。该时段 Tutor Web SSE 曾超时；后续状态以本文件上方“Tutor 真实 Web 链路与运行令牌同步”记录为准。

## 2026-10-15 当前续接

### 最近续验（优先于下方较早快照）

- 第二章第 3 小节 `stage_id=6` 已在真实 Web 完成：视频 1× 播放至服务端确认 543/543 秒；课件依次翻至 19/19 页并完成标记；页面提交 4 道客观题全部答对；训练场圆面积题 `problem_id=476` 经 Piston 判题 AC、3/3，通过后再回到主线标记。PostgreSQL 确认 stage 完成、19 页、4 题、OJ 476、视频/课件消费记录；有一条 `practice_succeeded` 与 KC-016 `PRACTICE_PASS +0.140`。浏览器 `pageerror=0`。
- 随后在同一真实账号打开学习分身页，页面返回 5/65 个有练习证据的 KC、列出 mastery/证据充分度与服务端推荐原因；主行动实际导航至 `/expedition`，浏览器错误 0。客观题未加掌握度，最新 KC-016 掌握事件仅来自 OJ 正式判题。
- 所以第二章第 1～3 小节现已完成。此前从第 2 小节入口补题曾遇 DeepSeek 空内容/503，未入库；本轮之后只发起一次 KC-011 学生请求 API，返回 HTTP 200 并由自动审查入共享题库。没有正式学生作答，KC-011 掌握证据仍为 0；远征 KC-008/KC-011 先修门槛仍未验证通过。
- 空 JSON 行为已对照 [DeepSeek JSON Output 文档](https://api-docs.deepseek.com/guides/json_mode/)：供应商说明 JSON 模式偶尔返回空内容。应用按失败处理并重试，不将其当成题目；本次调用未核验供应商账单/token。
- 持续模型续验中，一次从 Pythontopia Graph provider 发起的极短纯文本请求超过 4 分钟仍无响应，我终止了该本地探针；没有可用响应，因此不计作成功。此前真实 Web Tutor 文本与 PNG 输入成功记录仍有效。
- 尝试补齐 Alethicode Java/Tutor 同输入联测时，本地 `start.sh` 的 Alethicode RAG 依赖在 15 秒 DeepSeek smoke probe 超时后退出；已停止其失败重启循环并恢复 `java-oj-tutor-graph-local`，当前 RAG/Java 后端未运行。没有把 Graph 边界桩当成 Java 全链路证据，待 DeepSeek smoke 恢复后再重启本地开发栈。
- 本轮续验：7892 代理在宿主机与 Pythontopia Graph 容器可连接；DeepSeek `/models` 使用项目凭证返回 HTTP 200。纯文本 completion 探针遇到连接超时、55 秒无结果超时与 HTTP 503 `Service is too busy`；默认 Pro 档调用 65 秒及最后一次 Flash 调用 40 秒均无响应。没有可用模型文本。当前 Pythontopia Graph/RAG 容器健康，Spring API 对未认证的 actuator 返回 401；Alethicode Graph `/health` 返回 200，RAG 容器为 exited，Java 后端未启动。
- 受影响验证新鲜复跑：前端全量 Vitest 275 文件 / 2604 项通过，`npm run typecheck`、全量 ESLint、production build 通过；build 仍有 Pyodide 的 Node 模块 externalize 警告。Graph Boss + 章测流水线 275 passed；改动相关 Java 与 PostgreSQL/Testcontainers 定向类 133/133 通过；Twin 聚合/策略/周报/掌握度与事件监听 46/46 通过。全量 Maven 1,428 项、0 assertion failures、7 errors；6 个测试容器动态端口绑定失败，长期仿真 JDBC 连接失败。隔离复跑其中 7 类共 9 cases 再现 5 个容器绑定错误，无 assertion failure。
- 当前 Web 工程审查已覆盖 README、Vite/TypeScript 构建配置、路由、主线学习组件、教师题库分页、Twin 页面和 Boss/学习 API；未发现能复现的新代码缺陷。管理台表头使用 `scope="col"`、分页当前项使用 `aria-current="page"`。扫描已跟踪文件与未暂存 diff 未发现 API key 格式字符串；没有跑性能测试。
- 总任务清单及执行顺序已集中更新到 `docs/plans/2026-10-15-web-system-implementation-plan.md`。当前代码检查点 `36bb7abe`、文档检查点 `c492f6ae`；没有推送或部署。
- 无登录态 Playwright 矩阵覆盖 `/world`、`/workshop`、`/expedition`、学习分身、教师页、审核页、管理台、Boss 和主线地图，共 9/9 正确重定向 `/login`；真实临时学生会话访问 4 个管理/教师路由均被送回 `/world`，随后账号 640 已删除。现有 E2E 两项登录页/未登录守卫用例也通过（2/2）。
- 远征 active-run 浏览器恢复仍未验证：本地 4 个无先修根 KC（001/002/004/006）当前无课程编程题或已批准 AI 编程题；新账号无法合法开局，当前也没有可用的合格学习账号会话。未伪造掌握事件。
- 生产 SSH 只读核查未执行：目标地址路由当前指向 TUN，添加仓库说明的 eth2 直连路由需要本机 sudo 密码，本会话不可用；ECS/Piston/Nginx 运行态继续未核实。
- 生产 Piston 静态合同核对：Compose 强制配置 Piston URL/版本/token，ECS 预检只接受 HTTPS 或契约识别的私网 URL；`bash deploy/scripts/test_piston_http_host_contract.sh` 通过。安全源码审查显示 Piston 自己不校验 Bearer token，生产必须由真实私网 ACL 或代理做鉴权；当前 ECS 运行态尚未核实。
- 局部工程扫描检查整个 `frontend/src/` 的显式 `any` / `as any` 和空命名 `void` 函数，未发现违反 strict TypeScript 的项；空箭头回调只命中 fullscreen 错误吞掉和测试待决 Promise。未做无依据的全库重命名/格式化。
- 核对工坊 `quiz_finished`：它是客户端来源的审计事件，不进入后端权威周报统计或画像掌握度监听；将过时 TODO 改成明确的信任边界说明，没有改变事件处理行为。

### 实时状态更新（本节优先于下方较早进度记录）

- 主线课件映射已按 pack 43 的 KC `page_refs` 修正并通过 V216；第一章 3 个阶段分别使用 PDF 原页 40–45、34–39、13–32。旧完成标记因页码证据无效而清除后，同一新生账号已在真实 Web 页面重新逐页阅读并完成每阶段 4 道题；数据库确认每阶段必读页均已覆盖、视频各 440 秒、问题全对且已完成。阶段 1 的 `pages_read` 还保留旧映射历史项（数组 21 项，其中当前必读 6 项），不会用于满足新页码要求。
- 章测第一次生成失败的根因是 Graph 没有连到本地 Piston，9 道编程题自测统一报沙箱不可用；后续一次临时 Graph 重建曾因内部令牌不一致而 401。现已按 `./dev.sh --sandbox` 的配置启动本地隔离 Piston（Python 3.12.0），从既有后端进程环境同步令牌且未输出令牌，Piston 求和自检通过。
- 真实 DeepSeek 章末卷 `test_id=15`、第 4 次考试已提交，成绩 98/100、2 星；9 道编程题均由服务端 Piston 判对，页面与地图显示第一章通关并解锁第二章。早先 `test_id=14` 的 55 分失败记录保留为审计历史（当时 Piston 未配置）。原卷曾发现一个选择题选项格式错误和一个填空题题干错误，因此本次画像事件只接收经 Piston 判定的编程题，不采信模型给出的客观题答案键。该成绩提交早于下方事件修复，尚未回填到 Twin。
- 当前开发环境已通过真实 DeepSeek 请求；本地应用请求限额仍关闭。若重新启动完整栈，使用 `./dev.sh --sandbox` 以便 Graph 连通本地 Piston。
- Twin P0 核验：自评 DTO 无客户端 evidence/metadata 字段；周报按同一上海时区区间统计总事件、最近记忆和真实 mastery delta；分身聚合按批查询。修复了 Bug 矿洞同次通过同时记 `PRACTICE_PASS` 和 `DEBUG_FIX` 的 mastery 重复；真实 PostgreSQL 联测与定向 110 项通过。
- Twin 章测回流：章测提交按 KC 汇总服务端判定的编程题，复用 `MICRO_CHECK_PASS/FAIL` 与现有 mastery/review-state；幂等键按章测 ID + KC 生成。真实 PostgreSQL 联测重放同一事件两次，掌握度事件仍为 1 条且分数大于 0。联测同时发现并修复 `application.yml` 漏配微测增量（原值实际为 0）。改动后真实 Web 小测 `test_id=16` 的 3 道编程题全部通过，数据库落 3 个 KC 事件（各 `+0.080`），分身 API 返回 3/65。
- 章测客观题结构防重：提示词要求单选唯一正确项、明确错误的干扰项及语义匹配的填空答案；结构校验拦截规范化后完全重复的选择项并触发现有重试。重复项回归 RED/GREEN，Graph 章测流水线 20/20、Ruff 通过。语义答案正确性仍未验证，客观题继续排除画像。
- 真实模型续验：本地 Graph `openai_compat` provider 的 base URL 为 `https://api.deepseek.com`。Web 文本提问 `range(3)` 返回 `0、1、2`；PNG 输入实际上传 1 张，识别出蓝色正方形、黄色圆形及 `range(3) -> 0, 1, 2`，回复为文本且没有图片元素。两次 `/api/tutor/ask` HTTP 200、等待流结束后读取；浏览器错误 0。清理本轮生成的 3 条 `tutor_session`，并确认 `workshop_chat_turn` / `tutor_session` 对联测账号均为 0。
- 第二章按序续验：使用已有合成学生（不写入或伪造掌握度）在 Web 完成第 2 章第 1 小节 `stage_id=4`。视频真实 1× 播放 570 秒，服务端确认 543/543；按下一页逐页浏览 29/29 KC 关联课件页并成功打点；4 道客观题通过 UI 提交，stage API 返回完成。Chromium `pageerror=0`；PostgreSQL 确认 `video_watched_sec=543`、29 页和完成时间，相关 4 个 KC 近 20 分钟无掌握度事件。第二章第 2/3 小节及 KC-008/KC-011 的编程评估仍待验证。
- KC-011 AI 补题联测发现 Web Boss 请求原超时为 15 秒，短于后端 Graph 的 180 秒上限；按后端窗口调整为 190 秒。另让 Boss 命题走已配置的 DeepSeek Flash 快档。RED/GREEN 已验证：Web `boss.spec.ts` 17/17、Graph Boss 255/255、typecheck、定向 ESLint、Ruff 与 diff 检查通过。
- 实际 KC-011 浏览器请求仍未返回题目：观察窗口内没有 API response，题库无 KC-011 审核记录。独立的 DeepSeek Flash 极短 JSON 探针两次均快速返回 HTTP 503 `service_unavailable_error` / `Service is too busy`；一次较早应用调用则返回空模型内容。故 KC-011 自动审核、题库写入与正式判题仍未验证，待服务可用后重试。
- 全量 Maven 当前快照（本轮串行新鲜复跑）：`mvn -q test` 共 1,446 项、断言失败 0、错误 7。6 个 PostgreSQL Testcontainers 类（`CoursePackServiceIntegrationTest`、`DecisionTraceControllerTest`、`EvidenceMemoryServiceTest`、`MemoryRetrievalSemanticIntegrationTest`、`LearningEvidenceGraphServiceTest`、`RecommendationServiceTest`）因 Docker 动态绑定端口时 `address already in use` 启动失败；第 7 个 `LongTermClassroomEffectSimulationTest` 有 JDBC 获取连接超时。单进程串行及临时尝试的 3 次启动重试都未消除冲突；冲突端口观察到 Docker bridge `TIME-WAIT`。7 个错误类后来逐项隔离复跑，共 21 项通过。尚无断言失败，但全量 Maven 仍未通过。

### 本次补充

- 补齐无课程编程题 KC-010 的 AI 出题链路：修正题面长度导致的难度误判；`fill_blank` 严格要求前端使用的 `___` 占位符，知识点名写入注释以满足接地审查。Graph Boss 255 tests、Ruff 通过；Web 填空组件 4 tests 通过。
- 经 Vite → Spring → Graph → DeepSeek 真实联测，KC-010 难度 1 题首候选通过，写入既有共享审核题库为 `APPROVED/AUTOMATED`；第二名学生的 Web 请求复用了这条批准题，没有再次请求 Graph/DeepSeek。清理了两轮失败测试账号和一名复用测试账号；保留题目请求者账号以满足审核来源外键。
- 教师审核队列已通过真实教师会话完成 Vite → Spring API 联测：分页接口 HTTP 200，页面读取 8 条；320px 无横向溢出、无浏览器运行错误。截图发现全局悬浮助教遮住队列卡片，审核路由已复用 `hideCompanion` 修复；复测确认隐藏。临时账号撤销会话并删除。
- 路由与 AppLayout 定向 Vitest 23/23、TypeScript 检查通过。主线地图进入 KC-010 补充练习、正式判题、Twin 投影与唯一数据库证据现已真实联测；仍待第二章按序阶段与远征先修证据闭环，以及其他既有安全/远征未验证项。性能仍后置。
- 持续真实模型验证：Web Tutor 文本与 `range(3)` PNG 输入各完成一次 DeepSeek 请求，均 HTTP 200 / `RUN_FINISHED`；图中数值识别正确，回答纯文本。Graph 日志确认同一时间窗内 6 个 DeepSeek completions 均为 200，浏览器 JS 错误 0。临时学生及 Tutor 记录清理，账号数为 0；本地 AI 请求限额仍关闭。
- 修复未评估 KC 的学习分身行动：依据服务端 Twin 状态，未评估或缺失 KC 现在显示“前往主线学习”并导航到 `/mainline-map`；到期复习仍进复习入口，已评估推荐仍按原行为进入远征。RED/GREEN 页面测试 4/4、TypeScript 与改动文件 ESLint 通过；用临时真实新生经 Vite → Spring 获取 Twin 状态 HTTP 200（0/65 KC 已评估），点击后到达主线页，浏览器错误 0，账号及会话已清理。
- 主线缺编程题 KC 的续接：旧的远征深链对该新生真实返回 422（KC010 还缺 KC008/KC011 先修掌握证据），所以没有放松远征策略；改成小节内按需调用现有 Boss AI 出题/审核与判题链路，只有同 KC 且服务端标记已审的题才显示，提交走正式判题并刷新掌握快照，不冒充小节课程题完成。TDD RED/GREEN；本轮重跑全前端 Vitest 275 files / 2604 tests、typecheck、全量 lint、production build 与 `git diff --check` 均通过。Build 保留现有 Pyodide Node 模块 externalize / eval 警告。真实 Web 直访第 2 章第 2 小节，KC010 问题 API HTTP 200、`reviewed=true`、`aiGenerated=true`，题面 112 字符并渲染成功，浏览器错误 0；复用既有已审题，无新增 DeepSeek 生成请求。随后通过主线地图第 2 章节点与第 5 小节入口进入同一页面，未绕过章节锁；提交通过后 Boss 判题 API HTTP 200、服务端沙箱通过，Twin 由 3/65 变 4/65，KC-010 从未评估变为 `0.14` / `confidence=0.05`，数据库新增唯一 `PRACTICE_PASS +0.140` 幂等事件与 `practice_succeeded`。第二章按序阶段与远征先修证据闭环仍待联测。
- DeepSeek 图片续验：在真实视频助教输入 PNG 并提问，Tutor API 和 Graph provider 日志均返回 HTTP 200。回答识别出图中 `range(3) -> 0, 1, 2` 及蓝方形、黄圆形；回答 DOM 含 0 个图片元素，浏览器 JS 错误 0。该测试新增 1 条合成学生 Tutor 问答记录，作为调用留痕。
- DeepSeek 文本 SSE 续验：同一 Web 会话经 Vite 代理请求 `/api/tutor/ask`，HTTP 200，流完整收到 `CUSTOM×1`、`STEP_STARTED×5`、`STEP_FINISHED×5`、`TEXT_MESSAGE_CONTENT×113`、`RUN_FINISHED×1`；数据库最新 Tutor 答案长 184 字符，包含 `0、1、2`，浏览器错误 0。该调用记录保留。
- Alethicode live 对照续验：隔离内存运行其真实 Graph `CHAT`，用空学习状态/卡片和成功投影桩替代不可用的 Java 服务；相同 `range(3)` 输入触发 DeepSeek HTTP 200，`TASK_QUEUED`、`TASK_STARTED`、`TASK_COMPLETED` 各 1 次。回答为先让学生预测的引导问题。没有调用 Alethicode 数据库；此结果仅覆盖 Graph + 模型，不覆盖真实 Java 证据、持久化和失败恢复。
- Web 工程标准小步应用：按 WCAG 2.2 2.4.11 让主线章节列表在焦点进入时展开，按钮获得 3px 深色焦点环；真实 Chromium 修复前断言为红（聚焦后仍 `position=absolute`），修复后 Tab、Enter 和地图进入学习旅程 E2E 2/2 绿。地图 Vitest 13/13、StageExamplePractice 14/14、typecheck、相关文件 ESLint 通过。将 `StageExamplePractice.vue` 的三个模糊 Set 名与短回调名收敛为 KC/题目实体名称；改动只涉及该组件局部状态，无接口变化。
- 继续统一主线课件、视频、地图和教师审核页面中的局部实体命名；相关 5 份 Vitest 文件 57/57、TypeScript、ESLint、`git diff --check` 通过。地图 E2E 首次因没有账号跳过，随后启用项目临时 QA 注册机制重跑，真实 Chromium 2/2 通过；刚创建的临时账号及其级联数据已清理。
- Maven 当前快照经默认串行新鲜复跑：1,446 项、0 断言失败、7 errors；6 项为 Testcontainers Docker host port `address already in use`，1 项为长周期仿真的 JDBC 获取连接超时。单进程串行未消除端口冲突，报告中无 assertion failure；全量结果仍未通过。
- 实走新生主线首跳时发现抽屉点击泄漏到 Phaser 窗口 `mouseup`，导致章节被意外改选；抽屉现同时拦截 mouse 与 pointer 事件。首节实际联测先暴露视频限频 no-op 被误报过线，播放器已按 TDD 改为 60 秒批次、校验服务端累计值并保留未计入秒数等待重试；回归先红后绿，定向 Vitest 9/9、typecheck、改动文件 ESLint 通过。真实复验同一账号续看 50 秒，服务端返回 440/440 且 `videoComplete=true`；PDF 逐页浏览 1–15 后批量标记 HTTP 200 / 15 页；4 道客观题在 Web 各提交并由后端判对，stage 完成标记落库、浏览器错误 0。之后同一账号已完成第一章第 2、3 小节与 98 分章测，当前第二章已解锁。
- 续做同一新生主线：overview 确认第 1/3 小节完成后，“继续学习”进入第 2 小节（stage API 200，KC-006/007、440 秒视频、15 页、4 题）。该小节视频以 1× 播放中，当前数据库累计 300/440 秒；课件和客观题还未提交，浏览器错误 0。

### 已完成

- 继续安全与远征闭环：失败判题和扣体力现由服务端同事务完成；失败后新签发同题可更新去重账本但不恢复体力；判题与结算均拒绝已过期的旧题。签名媒体链接会在 owner 停用/删除后立即失效，合成 SSE 不再向客户端泄露底层异常文本。
- 对应 RED/GREEN 验证完成：远征与 Boss、媒体授权/作业服务相关 Maven 定向集合 111/111 通过；Boss scene 与账号隔离 Vitest 4/4，前端 typecheck/lint 通过，`git diff --check` 通过。测试覆盖了旧题复活、失败漏扣体力、停用账号继续读资源、SSE 错误泄露。
- 工程格式复核：本次改动 Java 文件未出现在 Spotless 违规列表；全库 Spotless 仍被 612 个未修改的历史文件格式差异阻断，没有批量格式化无关文件。
- 清理旧讯飞密钥/代理的本地注入和开发手册操作指引；线上/本地实际 AI provider 继续固定 DeepSeek，`dev.sh` 仍将本地请求日限额设为关闭。
- 核实 Tutor AutoGen 的现有主链路与 Alethicode 最新未提交实现的协议差异；保留 Pythontopia 当前 SSE、Java 鉴权、数据库和私有附件约束。
- 将管理台 CSS token 名称对齐 Alethicode 语义词汇，保持原值；增加远征 Boss 场景的受击/失败体力/素材预载自动回归。
- 已跑真实 DeepSeek 文本与 PNG 输入各 1 次；Graph 106、Java 135、前端定向 25 项通过，前端 typecheck/lint、生产 AI readiness 契约、shell 语法通过。
- 补跑 Graph AutoGen runtime/SSE 23 tests、前端账号隔离回归 1 test；全仓 lint 的 5 条 Vue mock stub 结构提示已在测试文件显式标注，当前 0 errors / 0 warnings。
- 最新全量 Maven 串行：1413 tests、0 断言失败、7 errors；6 个 PostgreSQL Testcontainers 因 Docker 临时端口 `address already in use` 失败，长期仿真发生 PostgreSQL 连接 reset。7 类合并隔离复跑后，长期仿真 1/1 通过，另外 6 类仍因随机端口绑定错误。
- Tutor 对照：共享 17 个事件，Pythontopia 另有独立执行轨迹事件；卡片映射为 11 个 Pydantic 模型。新生远征浏览器尝试在第 1 章和全局范围均收到 422 `EXPEDITION_NO_PRACTICE_PROBLEMS`；测试账号已清理。
- 新增 V215 账号级登录退避；认证与 Testcontainers 空库迁移定向 19/19 通过，触及 Java 文件 Spotless 定向检查与 `git diff --check` 通过。
- 本地全栈经 Vite → Spring → Graph 完成两次真实 DeepSeek Tutor SSE 请求，文本校验词与 PNG 颜色识别正确，文字回答落库且响应无图片数据；临时用户和会话已清理，本地 AI 日限额确认关闭。
- 新生远征 422 起始页已提供直达 `/mainline-map` 的入口；Playwright 注册新生、重现 422、点击并导航通过，临时账号已清理。主线小节到首个可编程题仍未实走。
- 前端全量 Vitest 274 files / 2591 tests、ESLint、TypeScript 检查和 Vite production build 均通过。首轮暴露两个旧测试契约未跟进当前 requestId/CSS token 更新，测试桩与断言已对齐。构建保留 Pyodide Node builtin externalize 与 eval 警告。
- DeepSeek provider 真实文本/合成图片输入各 1 次，文字校验串和颜色形状识别通过；Graph 响应均为文本字符串且无图片块/工具调用。provider 级调用不覆盖 Web Tutor 持久化，费用/token 未核对。
- 账号登录退避饱和边界回归先失败后通过：20 次额外失败后计数固定为 7，指数输入也封顶；认证+空库迁移 19/19 通过。
- 教师审核页改用已存在的服务端分页 API（每页 100 条），增加翻页、刷新、失败页重试和明确错误状态；并发决定时保留新版本。相关 14 项定向测试通过，前端全量 275 files / 2596 tests、typecheck、触及文件 ESLint 与 production build 通过。真实 Spring → Graph → DeepSeek Tutor 文本调用返回完成事件；临时用户、Tutor session、chat turn 已清理并确认无残留。
- KC-010 已有 1 道 AI 生成的难度 1 `fill_blank` 题进入共享题库，真实样例为两个整数相加；Web 填空组件从 `___` 占位符构造出的代码已用组件测试验证。来源审计账号 625 保留，审核记录 `APPROVED/AUTOMATED`。
- KC-010 批准题由另一名临时学生正式判题，Web → Spring → Graph/Piston HTTP 200、3/3 通过；服务端写入 `practice_succeeded` 与 `PRACTICE_PASS +0.140`。判题学生及其进度/画像已清理；Twin 投影与 attempt 去重已在后续同账号重放中核实。
- 以相同 `attemptId` 再放同一解答，结果不变且只产生一条学习事件/掌握度增量；Twin API 对该 KC 返回 `assessed=true`、score `0.14`、confidence `0.05`。幂等联测账号及数据已清理。
- 最终 Boss 探测使用无学习记录的新账号，路由守卫按章测星级要求将其重定向到地图；此行为符合契约，不代表 Boss 加载失败。最终第 7 章多阶段浏览器旅程仍待以真实具备解锁条件的账号验证。

### 未完成 / 下一步

- Tutor 静态事件/卡片结构与两侧同输入 Graph/模型实时调用已对照；Pythontopia 发送 `CUSTOM`、步骤、文本片段和 `RUN_FINISHED`，Alethicode 发送 `TASK_QUEUED/STARTED/COMPLETED`。Alethicode 调用使用空 Java 边界桩，真实证据装配、Java 投影和失败/恢复行为仍待端到端对照。保留 Pythontopia Java/SSE/私有附件契约，不移植不兼容的 WebSocket/session 数据层。
- 远征三阶段 Boss Phaser 浏览器胜利链路已有合格先修账号的通过记录；当前学习账号完成第二章主线，但 KC-008/KC-011 仍缺正式判题证据，远征正确拒绝发题。KC-011 题目已自动审核入库；待该账号实际判题并补齐 KC-008 证据后，再确认服务端先修选择能自然放行。
- Surefire XML 已将记录的 Maven 全量 7 个 error 归因为 6 个 Testcontainers Docker 宿主端口绑定冲突和 1 个长期仿真 PostgreSQL 连接重置；断言失败为 0。环境稳定后再决定全量重跑；Twin A/B、7 日延迟效果、供应商用量/费用与远征性能继续待测，性能最后执行。
- Tutor SSE 超时的本地根因已定位：Graph 源码更新时间晚于运行容器，Compose 命令缺 `--reload`，导致旧进程未加载新心跳。开发 Compose 现在只监视 `/app/pythontopia_graph` 并使用 Uvicorn reload；先加契约断言看到预期失败，配置改完后脚本通过。Graph 容器已重建，临时 `.py` 文件确实触发 WatchFiles 重载并删除，日志确认目标目录、DeepSeek Pro/Flash provider 初始化，`/healthz` 200、容器 healthy。未发新模型请求；待做一次重启后的 Web Tutor 文本联测，provider 探针不重复。
- 本次验收三条非模型 Playwright 动作链：小镇 HUD/暂停菜单→工坊（1/1，6.7 秒）、主线章节键盘焦点/Enter 打开第 1 章（1/1，3 秒）、指南页目录锚点跳转（1/1，2.2 秒）。临时学生分别在测试后删除并确认用户行计数为 0；没有 DeepSeek 请求、题目生成或判题。第一次缺少账号变量造成的快速失败只用于确认前置配置，后续动作测试显式关闭 retries。
- KC-011 的页面补题入口此前触发过模型空响应/503；本轮通过同一学生请求 API 完成一次真实生成，题目以 `APPROVED/AUTOMATED` 写入共享题库。没有提交答案，KC-011 掌握证据仍为 0；保留随机匿名审核请求账号以满足审核记录外键。KC-010 的批准题、共享复用、正式判题、幂等事件和 Twin 回流已验证。
- Twin 章测事件的单测、真实 PostgreSQL 幂等联测及 Web → Spring → Piston → Twin 实测均已通过。旧考试不自动回填，避免事后伪造评分证据；新小测留下了 3 条真实服务端代码判题证据。
- 远征 Boss 胜利浏览器旅程已有真实通过记录；KC-011 现有可用编程题，但新生从主线评估到真实完成首题、画像增长与自然解锁的连续入口仍待验证，未伪造画像证据。
- 继续只做 Web 范围工程优化：地图键盘焦点与主线练习局部命名已按项目约定及官方 Vue/WCAG 指南修正；剩余页面仍需逐步审查。性能测试留到功能与工程审查收敛后。


## 2026-10-14 当前续接

## 2026-10-14 当前续接

### 本轮阶段记录

- Phase 1 完成：Boss 预加载和同步签发共用审核门槛；撤销批准的 AI 题不记远征分、排行榜或掌握度。定向 `BossServiceTest` + `EndlessPregenServiceTest`：64/64 通过。
- Phase 2 完成：`useNextSteps` 消费服务端 Twin 推荐；无评估状态不再由前端推断弱项；远征自动开局不发送画像 weak KC，显式 `focusKc` 仍有效。定向 Vitest 20/20，`npm run typecheck` 通过。
- Phase 3 完成：SSE/Graph 请求时限、断流取消、同会话活动锁和检查点恢复已核对，无需新增租约字段。修复 Tutor 图片附件 `Instant` PostgreSQL 参数绑定，并补充 emitter 超时会中止 Graph 流的回归测试。Tutor Maven 120/120、Graph Tutor/provider/附件 88 项通过；本地 DeepSeek 文本与相关蓝圆 PNG 输入各完成真实调用，响应为文本字符串，Tutor 持久化卡片与 checkpoint 未发现图像 payload。初始无关图片按课程相关性规则被拒答，改用圆面积相关图片后识别成功。
- Phase 4 完成：管理台三页真实浏览器/API 联测通过，运维、用户搜索筛选分页、账号停用/启用、审核搜索与驳回均命中本地后端；非法分页参数返回带 traceId 的统一 400。键盘焦点、窄屏 320px（无横向溢出）、控制台检查通过；临时管理员、学生、24 个分页夹具及审核记录已清理。定向 Vitest 10/10、typecheck、ESLint 通过。与 Alethicode 1440×900 截图对照；其旧快照含 `ResizeObserver` 错误覆盖层，Pythontopia 页面无该运行错误，无需视觉修复。
- Phase 5 Boss Phaser 多阶段浏览器闭环已对有合格先修证据的账号走通；新生可练题引导与 Web 最终回归仍未完成，性能测试后置。

### 本次补充

- Boss 题目重试会把上一轮审查错误传给下一次生成。非输出类 KC 的常量输出和“KC 术语只在故事题面、不进入参考程序”现被阻断；摘要中的 Python API 词也用于识别代码类 KC。
- Graph 审查失败不再无条件退回 Golden 题：若 AI 候选未通过，或没有同 KC 的可用课程编程题，返回明确 503。避免用无关题目给错误 KC 记掌握度。
- 真正的 Web 浏览器会话调用 `/api/world/boss/2/problem` 验证无静态编程题的 `PY43-KC-012`：DeepSeek 首轮候选通过审查，返回 AI 题且不泄露参考解/测试用例；正常流程会自动审核通过并写入现有共享题库。测试账号及临时审核记录已清理。
- 随后使用该 KC 的公开题面和样例编写解答，经 `/api/world/boss/2/attempt` 正式判题 3/3 通过；浏览器脚本错误为 0，未读取隐藏用例或参考答案。账号和关联数据已清理。该接口联测不代表 Phaser 最终 Boss 多阶段胜利流程。
- `PY43-KC-001` 真实质量复验共 6 个 DeepSeek 候选，分别触发回显、KC 接地或章节语法审查，最终 API 返回 503；无误题被发给学生。此 KC 暂时没有合格编程题，学生应改走其他练习入口。
- Graph Boss 回归 234/234 通过，改动 Python 文件 Ruff 通过；本地 Spring 进程的 AI 每日限制仍为 `false`。

- DeepSeek provider 的同步与 SSE 响应都只提取文本块；流式回归覆盖了上游混合文本/图片块，客户端只收到文本。项目不调用图片生成接口。
- Tutor 图片附件写入 PostgreSQL 时，`JdbcTemplate` 不支持直接绑定 `Instant`；现改为 `Timestamp.from(expiresAt)`，并有 Testcontainers 回归覆盖。新增 SSE emitter 超时测试，确认超时后会取消 Graph 上游并标记运行中断。
- 最新 Tutor 验证：后端 `Tutor*Test` 120/120 通过；Graph Tutor/provider/附件 88 passed、1 条既有 pytest 标记警告。真实本地 DeepSeek 文本回显与圆面积相关的蓝圆 PNG 识别均成功，响应 card content 类型为字符串；卡片/checkpoint 图像 payload 数为 0，测试用户清理成功。无关红底蓝方块曾被正确拒绝，本次改用相关圆图后才计为视觉验证通过。
- `git diff --check` 通过。全仓 Spotless 仍因 616 个其他文件格式差异失败；本阶段修改的三个 Java 文件均未列入格式违规。
- 真实 provider smoke：2 次 DeepSeek 请求成功，分别验证纯文本答复和合成 PNG 识别；响应均为文本。此次没有改动请求模型或提示词，因此未额外发送模型请求。
- 本次更正续验：再发 2 次真实请求（文本精确回显、红蓝 PNG 识别），两次都只返回 `content_text`；同步混合输出块回归现已明确包含图片块。DeepSeek 负责看图并文字回答，不承担图片生成。
- 修复额度预留早于校验/授权的问题：改为 Spring 方法拦截器，在 MVC 入参校验后再检查会话与允许角色并预留；移除相关控制器的 AOP `@Validated`，使用 Spring MVC 原生方法校验；补齐统一 400 响应。定向 Maven 35/35 通过。
- 新增章节级事务 advisory lock，串行化无尽远征/错题补星的限额检查与写入，并防止核心星级重算覆盖并发补星。PostgreSQL 回归先失败（两个请求都发放），修复后并发集成 + 补星/星级单元测试共 14/14 通过。
- 续验 `tests/test_provider_tool_calling.py tests/test_openai_compat_provider.py`：46 passed，1 条既有 pytest 标记警告。
- DeepSeek 本项目只使用图片输入 + 文本输出；未接入图像生成接口。前次 provider 回归复核未改动请求/解析代码；本次 provider 审计另发了两次真实验证请求，见下条。
- 对实际运行的 Graph 容器只打印脱敏 provider 摘要：`OpenAiCompatProvider`、`api.deepseek.com`、Spark key 缺失、fine-tuned 关闭。经项目 provider 新发 2 次真实 DeepSeek 请求，纯文本校验词和合成 PNG 颜色识别都返回文本；没有调用语音/图像生成服务。
- provider 审计回归：Graph 53、研究实验室 provider 32、RAG 本地 embedder 6、后端媒体禁用与路由 17 项通过；DeepSeek 生产 readiness/model contract 脚本通过。
- 复查先前记录的 Tutor Markdown 外链图片隐私风险：共享 `MarkdownView` 已在写入 DOM 前移除跨域图片并显示 alt 文本，保留同源图片；现有 `MarkdownView.spec.ts` 隔离复跑 6/6 通过，无需重复新增防护代码。
- 管理台 5 个 Vitest 文件 / 31 项、typecheck、定向 ESLint 通过；Playwright 模拟 API 检查 3 页的 1440px 与 320px 布局、窄屏抽屉和用户状态对话框，页面溢出 0、JS 错误 0。期间排除了全局像素按钮阴影对管理端的污染。
- 前端全量 Vitest 270 files / 2568 tests、typecheck、build 通过；ESLint 0 errors / 7 warnings。Graph 最新全量 1842 passed / 1 skipped；RAG 158 passed / 2 skipped。
- 远征随机节点集成夹具已修复，`ExpeditionRunPostgresIntegrationTest` 隔离 28/28 通过；此前一次 Maven 全量为 1393 tests、0 failures、6 errors。最新一次完整运行与隔离复验见下条。
- 本次重跑完整后端套件：1392 tests、0 assertion failures、7 errors。Surefire 报告显示 6 个 PostgreSQL Testcontainers 类遇 Docker 宿主临时端口 `address already in use`，长期仿真类在全量串跑中连接被拒；7 个受影响类逐类/小组隔离复跑共 20/20 通过。说明代码用例可单独通过，但全量 Docker 端口分配仍不稳定，不能标记 Maven 全量通过。
- 本地 320 CSS px 教师班级列表 Playwright 检查通过（模拟 API）；管理员活动会话、生产 Piston/ECS、Twin A/B 与 7 日延迟效果仍未验证。性能最后处理。

### 已完成

- 明确 DeepSeek 数据方向：视觉模型接收图片，返回文本；按需 Mermaid/SVG 是模型给出的文本源码，由 Web 本地渲染，不代表模型返回图片。
- Tutor 对带课件证据的强事实声明调用现有语义核验器，并沿用请求预算/熔断；修复 ADMIN 在教师屋班级总览与创建班级的权限闭环。
- 本轮通过实际 DeepSeek 请求验证文字、合成 PNG 识别、Tutor 文本答复与语义反例核验；宿主机直连图片请求 TLS 超时后，通过 Graph 容器代理链路成功复验。

### 当前验证

- Graph Tutor/provider/图片/AutoGen 定向回归：102 passed、1 条既有 pytest 标记警告。
- DeepSeek 文本/视觉 provider 回归：46 passed；真实文字请求及红蓝 PNG 图片输入各 1 次，返回内容均为文本。AI 配额授权/校验顺序的后端定向测试 35/35；五个全量失败容器类隔离 16/16，长期仿真隔离 1/1。
- 后端 `ClassroomServiceTest`、`ClassroomRepositoryTest`、`TutorDailyRequestLimitServiceTest` 通过；`git diff --check` 通过。
- 4 个本轮触及的 Java 文件通过定向 Spotless；全仓 Spotless 仍报告 614 个其他历史文件格式差异，未批量格式化。
- 最新全量 Maven：1393 tests、0 failures、6 errors；5 个 Testcontainers 测试类在全量串跑中遇到宿主动态端口绑定冲突，长期仿真类连接失败；相关测试类隔离复跑通过。
- 本地开发 Spring 保持 `PYT_AI_REQUEST_LIMITS_ENABLED=false`；此开关仅关闭应用级每日计数，不覆盖 DeepSeek 自身余额/限流。
- 生产 AI/readiness 与 Piston URL 合同脚本通过。ECS 运行时只读检查因本机直连路由需要 sudo 密码而未执行。

### 未完成或未验证

- Twin 学习效果 A/B 与 7 日延迟复测、DeepSeek 供应商 token/账单对账、稳定全量 Maven 复验、生产 Piston/ECS 实例验证。
- `PY43-KC-001` 的 AI 候选目前未通过质量门槛；没有为该知识点伪造编程题。尚未通过 Phaser 远征 UI 提交这次 AI 新题并观察完整受击/掌握度回流。
- 登录入口目前只有 Nginx 单 IP 限速（5 次/秒、突发 10 次）；多 IP 攻击不受账号级失败计数约束。未加账号锁定，避免形成可被攻击者利用的账户拒绝服务；跨实例账号级节流需共享状态与明确恢复策略。
- 本轮未做性能测试；按用户顺序在功能和工程任务完成后再处理。

## 2026-10-14 本轮增量（08:46 CST）

### 已完成

- `AuthService.refresh` 拒绝数据库角色与 JWT 角色不一致的旧 token；`/api/events` 只接受低信任前端反馈类型，source 固定为 `frontend`，importance 由后端计算。
- 本地 Piston Compose 去除 `privileged`，启用只读根文件系统、最小 capabilities、`no-new-privileges`、资源限制和禁网；当前运行容器已按新配置重建。
- Graph 沙箱修复 Piston signal-only 超时响应识别，SIGKILL/SIGXCPU 现在按 Linux 退出码进入现有判题处理。
- 依据真实随机地图奖励修正远征集成测试：事件节点在中点首次清除时会有额外 1 HP，测试按规则计算预期值。

### 当前验证

- 后端定向 47 tests 通过；`ExpeditionRunPostgresIntegrationTest` 28 tests、10 个全量失败类的单类重跑、Graph 沙箱/评估/自测 37 tests 及 Compose 安全契约均通过。
- 本地 Piston 实测正常判题、敏感文件与 socket 拒绝、500 ms 超时回收、256 MiB 内存请求封顶；无认证和无效 Bearer 都能访问 runtimes，已记录为生产接入前必须验证的边界。
- 全量 Maven（启用 Testcontainers 回收）仍为 1363 tests、1 failure、10 errors；失败的远征固定 HP 断言已修，集成类 28 项重跑通过。9 个 Testcontainers 容器/1 个长测数据库连接错误均单类通过，但全量环境仍不稳定。
- 收尾复核：Graph 沙箱/评估/自测重新运行 37 项通过，Compose 安全契约通过；当前 Piston 容器检查为非特权、只读根文件系统、capability 白名单、768 MiB/1 CPU/128 PID。

### 未完成或未验证

- 未重跑全量 Maven 直到全绿；当前全量失败仍作为环境/集成稳定性风险记录。
- 生产 Piston 的实际认证、网络隔离、资源限制与内核逃逸未验证；未访问 ECS 或公网。
- 完整 Boss 最终节点真实判题浏览器旅程与 Twin 学习效果对照仍待验证。缺少同 KC 课程题时返回 503 的保护已在真实服务中验证，仍需补足当前题库覆盖。真实 DeepSeek 文本/图片调用证据已在既有日志中，本轮没有新增调用。
- 本轮未重测性能；无尽远征上一轮本机建局采样数据见 CHANGELOG。

## 全局阶段进度

| 阶段 | 名称 | 状态 | 说明 |
| ---- | ---- | ---- | ---- |
| 00 | 仓库骨架 | 已完成 | 见 CHANGELOG |
| 01 | 课程包导出与种子数据 | 已完成 | 见 CHANGELOG |
| 02 | 账号、班级与小镇基础后端 | 已完成 | 见 CHANGELOG |
| 03 | 前端骨架与 Phaser 小镇 MVP | 已完成 | 见 CHANGELOG |
| 04 | 画像屋与 ProfileAgent | 已完成 | 见 CHANGELOG |
| 05 | 知识田与 KC 作物系统 | 已完成 | backend 26 / graph 11 / frontend 21 测试通过 |
| 06 | RAG 服务课件证据检索 | 已完成 | backend 29 / rag 18 测试通过 |
| 07 | 资源工坊与六类资源生成 | 已完成 | backend 41 / graph 31 / rag 18 / frontend 30 测试通过 |
| 08 | 学习路径、每日任务、公告板 | 已完成 | backend 52 / graph 40 / rag 18 / frontend 37 测试通过 |
| 09 | 训练场、Pyodide、服务端判题 | 已完成 | backend 72 / frontend 41 / graph 40 / rag 18 测试通过 |
| 10 | Bug 矿洞与 DebugAgent | 已完成 | backend 76 / frontend 44 / graph 45 / rag 18 测试通过 |
| 11 | 讯飞 Provider 接入 | 已完成 | graph 53 测试通过（含 8 Spark provider） |
| 12 | 班级架构与教师看板 | 已完成 | backend 87 / frontend 48 测试通过（见 CHANGELOG） |
| 13 | 学习效果评估与复盘大厅 | 已完成 | backend 100 / graph 59 / rag 18 / frontend 52 测试通过 |
| 14 | Docker Compose、视觉打磨、文档 | 已完成 | 6 service 编排 / 5 个脚本 / 6 类文档 / tokens.css / UX checklist |
| 15 | Beta 验收、视频、PPT | 已完成（文档/脚本/checklist） | 视频/PPT 本体需真人产出 |
| R-10 | 10 轮代码质量审查与性能优化 | 已完成 | 最终 234 测试通过 |
| R-11 | 根 README 重写为成熟教学游戏作品门面 | 已完成 | 单文件 30 → 147 行；R-0 baseline commit `59761c5` 建立 |
| R-12 | 星露谷改造（Web 端从 R-11 解冻） | 已完成 | Phaser 像素小镇改造；原 5 图分块 + 20 NPC（**已被 R-14 收缩推翻**，见 R-14 行） |
| R-13 | 主线 35 任务 + 章末 Boss + 竞技场 + AI 出题审核 | 已完成 | 详见 `docs/r13-*` |
| R-14 | 世界收缩方案 A：废弃 5 图大世界，收敛为单一中央 `town_hub` | 已完成 Phase 0 | 2 地图（`town_hub` 主玩图 + `mine_south` 支线）/ 12 NPC / 5 占位 building 已去路由；Phase 0 地基止血完成（双 V135 修复 + 跨服务韧性 + 判题沙箱加固 + 文档对齐） |
| Android-R0 | Android 客户端启动：10 subagent 调研 + ADR-001 + roadmap | 已完成 | 详见 `clients/native/android/docs/ADR-001-tech-selection.md` 与 `clients/native/android/docs/roadmap.md` |
| Android-R1 | 多模块 Gradle 骨架 + 真实可构建 + 28 测试通过 + 11MB APK 产出 | 已完成 | commit `8a49643`；本机已装 Android SDK 35 / JDK 17 toolchain / Gradle 8.11.1 |
| Android-R2 ~ R17 | Android 客户端从骨架到 Beta | 进行中 | 详见 `clients/native/android/docs/roadmap.md` |

## 当前状态：R-14 世界收缩（方案 A）进行中 — Web 端已从 R-11 解冻并收敛为单一中央小镇 `town_hub`（外围图弃用）；Android 侧 Android-R1 已交付（真实可构建 APK + 28/28 测试），R-2 待启

> 注：下方「最近进度汇报记录」为历史时间线（含 R-10/R-11 期一度暂停 Web、转 Android 的旧决策），R-12 起 Web 已解冻、R-14 收缩为单 hub，以本表与本行为准。

## 阶段 15 — Beta 验收、演示视频与 PPT 收尾（文档与脚本部分已完成）

### 计划任务清单（详见 `plan/阶段15-Beta验收视频与PPT收尾.md`）
1. Beta 验收 checklist：`docs/beta_acceptance_checklist.md` 按 `plan.md` 第 9 节 + AGENTS.md 硬要求逐项打勾。
2. 演示视频脚本扩展为 `docs/release/demo_v1.md`（剪辑时间轴 + 字幕清单）；视频本体不入 git。
3. PPT 大纲 → 终稿（pptx + pdf 仅放 `docs/release/`，本仓库只留 outline.md 与脚本）。
4. 提交包：`docs/release/README.md` 列源码 tarball / docs / images / images_tar / checksum / 演示清单。
5. 全量回归报告 `docs/release/regression_report.md`：4 套件 + demo_smoke 退出码 + 耗时。
6. 已知缺陷 `docs/release/known_issues.md`：分演示阻断 / 非阻断两类。

### 卡点 / 注意
- 比赛文件中不得包含真实 LLM 密钥；PPT/视频中不得包含真实学生姓名。
- 演示前 24 小时冻结代码，只允许文档与演示数据修改。

## 最近进度汇报记录

- 18:46 收到用户重申长期指令；继续阶段 07。修正 execution_status.md（阶段 06 已完成，进入 07）。
- 18:55 阶段 07 graph：V040/V041 + resource schema + 5 节点 + 6 agent + ResourceGraph + `/agents/resource/produce`；graph `pytest -q` -> `31 passed`。
- 19:02 阶段 07 backend：workshop 模块 + Stage07 Testcontainers 全过；backend `mvn test` -> `Tests run: 41`。
- 19:10 阶段 07 前端：`/workshop` + WorkshopPage + 9 个 workshop 组件 + workshop store/api/dto + 4 个 Vitest 全过；frontend `typecheck/lint/test` -> `17 files / 30 tests passed`；阶段 07 验收完成。
- 19:10 进入阶段 08：学习路径、每日任务与公告板。
- 19:20 阶段 08 graph：path schemas + PathPlannerAgent + PathGraph + `/agents/path/plan`；graph `pytest -q` -> `40 passed`（含 9 个 path planner 测试）。
- 19:30 阶段 08 backend：V050/V051/V052 + 三模块（path/quest/classroom-quest）+ 单测 + Stage08 Testcontainers；`mvn test` -> `Tests run: 52`。修复 classroom_members.student_id 列名后整体通过。
- 19:35 阶段 08 前端：`/board` BoardPage + BoardPanel + 4 个 tab + path/quests/classroom DTOs + path/quests API + store 子状态 + Town 入口 + 2 套 Vitest；`typecheck/lint/test` -> `Test Files 19 / Tests 37`；阶段 08 验收完成。
- 19:35 进入阶段 09：训练场、Pyodide、服务端判题。
- 19:42 阶段 09 backend：V060 + submission/judge 模块（SubprocessPythonSandbox + StaticCodeChecker + JudgeService + SubmissionService + Public/SubmissionController）+ 15 个单测全过。
- 19:45 阶段 09 集成测试 Stage09SubmissionAcceptanceIntegrationTest 5/5（含真实 python3 subprocess 跑出 AC/WA/CE/限频 429/DTO 无 hidden cases）；`mvn test` -> `Tests run: 72`。
- 19:50 阶段 09 frontend：`/training` TrainingPage + TrainingPanel + Pyodide adapter (CDN 0.25.0) + submissions/problems API + dto + store.training 子状态 + Vitest 4 个；`typecheck/lint/test` -> `Test Files 21 / Tests 41`；阶段 09 验收完成。
- 19:50 进入阶段 10：Bug 矿洞与 DebugAgent。
- 20:00 阶段 10 graph：debug schemas + DebugAgent + safety 降级 + `/agents/debug/analyze`；graph 测试 45/45。
- 20:05 阶段 10 backend：V070 + bug_mine 模块（PythonTraceCollector + CounterExampleSelector + DebugAgentClient + BugMineService + BugMineController）+ Stage10 集成测试 4/4；`mvn test` -> Tests run: 76。
- 20:10 阶段 10 frontend：`/bug-mine` + BugMinePage + BugMinePanel + api/bugMine + bugMine store + TrainingPanel 失败入口；frontend 44/44。
- 20:10 进入阶段 11：讯飞 Provider 接入。
- 20:15 阶段 11 完成：SparkProvider（OpenAI 兼容接口）+ LlmProviderError 分级 + provider_from_env/provider_label + 8 个 spark 测试 + docs/llm-provider-env.md 更新；graph 53/53。
- 20:15 进入阶段 12：班级架构与教师看板。
- 21:00 阶段 12 完成（见 CHANGELOG 2026-05-21）：V080/V081 + classroom-quest 写路径 + dashboard 5 聚合 + 教师 6 tabs；backend 87 / frontend 48 全过。
- 21:05 进入阶段 13：学习效果评估、路径重排与复盘大厅。计划并行实施：graph EvaluationNarrativeAgent + backend evaluation 服务 + frontend ReviewHallPanel。
- 22:10 阶段 13 完成：graph 修复 NUMBER/KC pattern 双计 bug + 6 单测；backend 新增 evaluation service/controller/listener + 13 单测/集成；frontend 复盘大厅 + 3 子组件 + 4 spec；全量 backend 100 / graph 59 / rag 18 / frontend 52 通过。
- 22:10 进入阶段 14：Docker Compose 部署、视觉打磨与文档。
- 22:25 阶段 14 完成：4 套 Dockerfile + nginx 反代 + 6 服务 compose；5 个 deploy 脚本 + demo seed.py；tokens.css；6 类核心文档 + UX checklist；前端 typecheck/lint/test 52/52 通过。
- 22:25 进入阶段 15：Beta 验收 + 演示视频脚本 + PPT 大纲 + 提交包目录。
- 22:35 阶段 15 文档完成：beta_acceptance_checklist / release/README / regression_report / known_issues / demo_v1；视频与 pptx 本体需真人录制制作（已写脚本与剪辑指南）。
- 22:35 启动 R-10：10 轮小步代码质量审查与性能优化，聚焦"让项目更像成熟教学游戏作品"。
- 22:50 R-10 完成：router 全量懒加载、教学闭环引导文案、复盘 store + planDraft 单测补强、文档闭环；最终 backend 101 / graph 59 / rag 18 / frontend 56 = 234 测试通过。
- 22:50 全部阶段交付完毕；视频与 PPT 物件需真人按 `docs/presentations/demo_script.md` 与 `docs/release/demo_v1.md` 录制制作。
- 23:50 R-0 baseline commit `59761c5`：把 R-10 完成态固化为首个 git commit（13032 文件 / 222589 行），建立 R-11 ~ R-100 可回滚锚点。
- 23:55 R-11 完成：根 `README.md` 重写为成熟教学游戏作品门面（30 → 147 行，11 节）；新建 `docs/release/r100_iterations.md`；同步本文件与 `docs/project/CHANGELOG.md`。本轮无代码改动，无需跑测试套件。
- 00:00 用户决策方向变更：在本项目下新增 `clients/native/android/` 文件夹做原生 Android 客户端；接受比赛 Beta 日期推迟；放弃 Web 端 R-12 ~ R-100 迭代（保留 R-11 完成态代码）；授权使用 subagent 并发。
- 00:05 Android-R0 启动：新建 `clients/native/android/` + `clients/native/android/README.md` + `clients/native/android/.gitignore` + `clients/native/android/docs/README.md`；启动 10 个并发 subagent 调研（Gradle / 像素引擎 / Python 沙箱 / 暗色主题 / 网络 / DI 状态 / JWT 安全 / 测试 / DTO 对齐 / CI/CD），输出到 `.work/thinking/android_research_{01..10}.md`。
- 00:08 10 个 subagent 全部完成，3951 行调研报告就位。
- 00:12 Android-R0 ADR-001 + roadmap.md 落地：18 项关键决策锁定（Kotlin 2.2.0 / AGP 8.7.3 / Compose BOM 2025.04.00 / LibGDX 1.13.1 主渲染 / 仅服务端判题 / Hilt + KSP / Retrofit 2.11 + OkHttp 4.12 / EncryptedSharedPreferences + BiometricPrompt / JUnit5 + Robolectric + Compose UI Test / OpenAPI Generator 7.22 / GitHub Actions ubuntu-latest）；roadmap 列 Android-R0 ~ R17 + R100 共 19 个阶段。
- 00:25 Android-R1 启动：6 个并发 subagent 写源码（M1 :app / M2 :core×4 / M3 :game / M4 :feature×2 / M5 :feature×7 / M6 CI+detekt+wrapper）。
- 00:35 105 个源码文件就位（含 15 模块 build.gradle.kts + Kotlin 类 + AndroidManifest + 单测）。
- 08:00 工具链准备完成（走代理 127.0.0.1:7892）：Android cmdline-tools 11076708 解压；sdkmanager 装 platform-tools / platforms;android-35 / build-tools;35.0.0；Gradle 8.11.1 二进制装；gradle wrapper 生成（gradlew + gradlew.bat + gradle-wrapper.jar 43.5KB）。
- 08:30 修 ADR-001 中 Kotlin 2.2.0 与 KSP 2.2 兼容性问题 → 实际锁定 Kotlin 2.0.21 + KSP 2.0.21-1.0.27 + Compose BOM 2024.12.01；settings.gradle.kts 加 foojay-resolver-convention 自动下载 JDK 17 toolchain。
- 08:43 `./gradlew :app:assembleDebug --no-daemon` BUILD SUCCESSFUL in 3m 45s（271 tasks），产出 `app-debug.apk` = 11 MB。
- 08:45 `./gradlew test --no-daemon` BUILD SUCCESSFUL in 1m 27s（613 tasks），**28 / 28 unit tests passed**（覆盖 15 模块）。
- 08:50 Android-R1 commit `8a49643` 落成（107 文件 / 3053 行新增）。- 08:50 R-100 阶段 1 启动：素材爬取 + 集成 + P0 优化。
- 09:00 阶段 1.1 License 热修完成：删 `sprout_lands_basic`(Cup Nooble 专有) + `simple_farm_tiles`(来源未知) + 2 个冗余 + 迁 `lpc_farm` 到 `opengameart_farming/`；sprout_lands 顶级目录已删除；`.gitignore` 加 sprout_lands/ + *.zip。Commit `0d167a4`。
- 09:05 阶段 1.2 10 个素材爬取子代理产出已完成 5/10（seasons_school 21M / characters_extra 5M / mines_extra 11M / sfx_extra 25M / bgm_extra 32M），剩余 5 个子代理因 worktree 切换中断后由主代理接管。
- 09:10 阶段 1.2B 主代理接管 #06-#10：补 icons_kc 19 张缺失类目至 60 张全覆盖；#10 effects_extra 删 zip 瘦身 105M→60M；#07 ark-pixel-font 13M 下载 + OFL-1.1；#08 Kenney UI 4 pack 36M；#09 itch.io 严过滤主动跳过(2 个 CC0 候选因 itch.io session token 限制不可自动下载)。
- 09:12 阶段 1.3 综合：md5 去重（仅清理 macOS `__MACOSX` 残留）+ `LICENSE-ART.md` 顶层声明就位（5.5KB / 全协议矩阵）。
- 09:13 Commit 2 `2ecf1af`: stage1 inbox expansion 10169 文件 / +194053 行。Commit 3 `faf6eac`: 补遗（相对路径符号链接 + icons/kenney_curated + buildings/kenney_1bit-pack + characters/armm1998_zelda_cc0 + oga_farming/lpc_farm + edu_diagrams 5 个新流程图）。
- 09:18 阶段 1.4 集成到前端：`frontend/public/assets/pixel/` 从 60KB 扩到 29MB；`tokens.css` 加 @font-face for Ark Pixel 12（zh_cn + latin × mono + prop）+ `.pyt-pixel-text` / `.pyt-pixel-code` 工具类；`BootScene.ts` 重写 preload：22 个 key 覆盖 tileset / KC icons / character spritesheet / SFX / BGM；前端 typecheck OK / 26 files 56 tests PASS / lint 0 错。Commit 4 `6d07383`。
- 09:25 阶段 2 P0 优化实施：`frontend/src/utils/sfx.ts`（66L，全局 SFX 服务 + localStorage 持久化开关）+ 5 单测 PASS；`frontend/src/pages/CreditsPage.vue`（200L，CC-BY-SA / OFL / CC-BY 强制署名页）+ `/credits` 路由。前端总计 27 files / 61 tests PASS / lint 0 错。Commit 5 `546152c`。
- 09:30 全套测试回归：backend mvn test = 101/101 PASS / BUILD SUCCESS；frontend = 61/61 PASS；graph pytest = 59/59 PASS；android = 28/28 PASS（用户 worktree 已 commit 8a49643）；**总计 249/249 通过**。rag pytest 因本机 Python 3.10 不满足 `>=3.11` 跳过。
- 09:35 `docs/r100_test_report.md` 测试报告就位（92 行，含合规验证 + 素材规模 + P0 实施 + backlog）。
- 09:35 R-100 阶段 1 + 阶段 2 P0 任务全部完成；剩余 P1/P2 优化、browser MCP 截图、judge e2e docker compose、RAG pytest 等列入 backlog 留待用户醒来推进。
- 09:50 R-100 阶段 2 P1 任务（"更像成熟游戏"优化）启动：5 个并发 subagent A/B/C/D/E。受 worktree 多次切换影响 subagent 反复 Aborted，主代理接管完成 4 个主题。
- 10:00 P1-A BGM 引擎落成：`utils/bgm.ts` (113L) + `utils/bgm.test.ts` (85L / 8 tests)；TownScene + FieldScene + BugMinePage + ReviewHallPage 接入；切场景 fade-in/out + localStorage `pyt:bgm` 持久化开关。
- 10:05 P1-B KC 收获 confetti 庆祝：`utils/celebrate.ts` (50L) + 3 tests；接入 `learning.loadField` 与 `refreshAfterEvent` 在 KC 状态 → MATURE 转换时触发 + 播 `jingleLevelUp`；tokens.css +68L celebrate keyframes。
- 10:10 P1-C 训练/工坊 SFX 反馈：TrainingPanel 提交成功 → `jingleComplete`，失败 → `error.ogg`；WorkshopPanel 资源生成成功 → `success + coin`，失败 → `error`。
- 10:15 P1 集合 commit `57bd410`：14 文件 / +479 -21 行；前端 29 files / 73 tests 全过 / lint 0 错。
- 10:18 P1-D 玩家 LPC Sara walk 动画：SaraFullSheet 832×1344 / 13×21 (LPC 标准)；BootScene preload `player_sara` spritesheet (64x64)；`sprite.ts` 加 LPC 4 方向 walk frame 表 (rows 8/9/10/11)；moveToTile 时 setFrame 切走路帧；LPC sheet 不可用时 fallback 到 `student_default`；sprite.spec.ts 改 2 测试覆盖 LPC + fallback 路径。
- 10:22 P1-D commit `3d54cfa`：前端 29 files / 74 tests 全过。
- 10:25 P1-E TownScene 真渲染（用 kenney_tiny_town spritesheet 替换 SVG 地面）跳过——子代理仅完成 BootScene spritesheet 注册与 sceneTypes 加 frame 参数（已并入 57bd410），实际 tilemap.ts 改造涉及 SceneLike 接口扩展（PhaserTileMap / TileSprite layer），风险大且无人 review，留 backlog。
- 10:30 R-100 阶段 1 + 阶段 2 (P0 + P1) 全部交付完成。本会话 main 分支新增 8 个 commit（不含用户 worktree 已 commit 的 2 个 Android-R1）。前端测试从 R-11 基线 52 → 74（+22 测试 + 18%）；后端 / graph / android 持平不破坏；总测试 262/262 通过（不含 rag 因环境）。
