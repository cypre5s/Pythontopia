# 项目代码优化记录（命名 + 注释，实时维护）

> 目标：按《命名约束 + 注释规范》优化整个项目所有源码文件。
> 注释力求简洁、尽量单行；命名全链路统一，无兼容别名。
> 每处理一个文件/批次就更新本表，避免遗漏。

## 执行依据（规范摘要）

### 命名

- Java：类名=文件名 `PascalCase`；方法/变量 `camelCase`；常量 `UPPER_SNAKE_CASE`；包名全小写。
- Vue/前端：组件文件 `PascalCase.vue`；工具模块 `camelCase.js`/`camelCase.ts`；变量/函数 `camelCase`；常量 `UPPER_SNAKE_CASE`；`i18n` 保留 `zh-CN.js` 区域格式。
- Python：文件/函数/变量 `snake_case`；类 `PascalCase`；常量 `UPPER_SNAKE_CASE`。
- 通用：同一语义只一种拼写；重命名全链路同步；不做兼容别名。

### 注释

- 只在代码无法自解释时写；优先靠命名/类型/结构表达。
- 应写：对外 API 契约、非显见业务规则、正则/复杂查询/协议映射/状态机、有删除条件的 workaround、SQL 迁移首部。
- 不写：复述代码行为、重复命名/类型信息、开发史/迭代号、无约束的「临时/后续优化」、过期 TODO。
- 默认中文；技术名词/字段/异常类保原文；公共 API 用 Javadoc/docstring/JSDoc；力求单行。

### 验证交付

- 改动后按范围跑 `typecheck`/`test`/`lint`/编译；无法运行须说明原因与风险。
- 代码改动须在 `docs/project/CHANGELOG.md` 用中文记录变更类型与影响范围。

## 命名审计结果（2026-06-05）

| 语言 | 文件数 | 文件名合规 | 备注 |
|---|---|---|---|
| Vue | 117 | ✅ 全部 `PascalCase.vue` | 无违规 |
| Java | 408 | ✅ 文件名=类名 `PascalCase` | 无违规 |
| Kotlin | 217 | ✅ | 无违规 |
| Python | 129 | ✅ 已修正 | 3 个连字符脚本已改名，见下 |

连字符脚本改名（已完成，全链路同步）：

- `scripts/build-cottages.py` → `scripts/build_cottages.py`
- `scripts/build-ulpc-characters.py` → `scripts/build_ulpc_characters.py`
- `scripts/crop-portrait-heads.py` → `scripts/crop_portrait_heads.py`

> 已 `git mv` 重命名，并同步约 20 处引用（`BootScene.ts`/`tilemap.ts`/`lpcAnimations.ts`/`DialogModal.vue` 及测试、`scripts/README.md`/`sync-assets.mjs`、`LICENSE-ART.md`/`LICENSE.md`/`CREDITS`/各 `README`）。三脚本 `py_compile` 通过；无残留旧名。

## 模块进度总览

| 模块 | 源码文件数 | 注释优化状态 |
|---|---|---|
| `frontend` | 290(src)+127(test) | ✅ src 全 290 完成（Wave 3，10 路并发；`npm run typecheck` exit 0）；上一轮 8 待审已并入；tests 127 未做 |
| `backend` | 346(main)+64(test) | ✅ main 全 346 完成（prev 24 + Wave1 119 + Wave2 198 + 入口类已审）；`mvn test-compile` exit 0；测试 64 未做 |
| `android` | 217 | 未开始 |
| `services/pythontopia-graph` | 65 src + 36 test | ✅ 完成（Wave 4；`pytest` 297 passed）|
| `services/pythontopia-rag` | 19 src + 6 test | ✅ 完成（Wave 4；`pytest` 21 passed）|
| `scripts` | 3 py + 2 mjs | py 3 ✅（Wave 4）；2 个 `.mjs` 与资产 WIP 交织，暂留 |

## 文件级明细

### 上一轮范围（多智能体答后审查 + Marvis + 聊天分组）

| 文件 | 状态 | 改了什么 |
|---|---|---|
| backend `db/migration/V127__tutor_session_conversation.sql` | ✅ 已优化 | 首部已含「目的/影响表/不可逆风险」，符合规范。 |
| backend `tutor/dto/TutorAskRequest.java` | ✅ 已优化 | 把考古式 javadoc 重写为字段契约清单（保留跨服务契约/安全约束），删编号叙事。 |
| backend `tutor/dto/TutorSessionDto.java` | ✅ 已优化 | 补类级契约注释 + 可空说明。 |
| backend `tutor/TutorSessionRepository.java` | ✅ 已审 | 无改动：CRUD 自解释。 |
| backend `tutor/TutorService.java` | ✅ 已审 | 无改动：命名清晰。 |
| graph `nodes/tutor_review.py` | ✅ 已审 | 无改动：docstring/正则注释合规。 |
| graph `routes/tutor.py` | ✅ 已审 | 无改动：协议映射注释属规范要求。 |
| graph `tests/test_tutor_review.py` | ✅ 已审 | 无改动：意图说明，无冗余。 |
| graph `tests/test_tutor_ask_stream.py` | ✅ 已审 | 无改动：意图说明 + 必要工具指令。 |
| frontend `components/workshop/MarvisPanel.vue` | 待审 | |
| frontend `components/workshop/ChatHistoryRail.vue` | 待审 | |
| frontend `components/workshop/WorkshopChat.vue` | 待审 | |
| frontend `pages/WorkshopPage.vue` | 待审 | |
| frontend `components/workshop/ResourceListPanel.vue` | 待审 | |
| frontend `stores/workshopActivity.ts` | 待审 | |
| frontend `api/tutor.ts` | 待审 | |
| frontend `components/tutor/TutorPanel.vue` | 待审 | |

### 全项目扩展（本轮）

> 按模块逐批推进，处理一批追加一批，记录文件 + 状态 + 改了什么。

#### backend / `common`（批次 1，14 文件）

| 文件 | 状态 | 改了什么 |
|---|---|---|
| `common/error/GlobalExceptionHandler.java` | ✅ 已优化 | 删 `// R-13 安全加固：` 迭代号前缀，保留「405 而非 500」映射规则。 |
| `common/sse/GraphSseConsumer.java` | ✅ 已优化 | 4 行 HTTP/1.1 workaround 注释压成 2 行中文，保留「为何强制 + 触发后果」契约。 |
| 其余 12 文件 | ✅ 已审 | 无改动：`ApiError`/`BusinessException`/`*Config`/`HealthController` 等命名+类型自解释，无冗余注释。 |

#### backend / `dialog`（批次 2，4 文件 · 子代理产出，已抽查）

| 文件 | 状态 | 改了什么 |
|---|---|---|
| `dialog/DialogContextService.java` | ✅ 已优化 | 去 R-12 迭代号；精简画像推断 Javadoc（留阈值/回退契约）；`rankBand` 注释与返回值对齐。 |
| `dialog/DialogContextDto.java` | ✅ 已优化 | 去 R-12 前缀；占位符「三类→四类」纠错；压缩 stats/Map 说明。 |
| `dialog/DialogContextRepository.java` | ✅ 已优化 | 英文注释转中文；纠正与 SQL 不符的注释（未解锁 KC、该用户 mastery、班级排序）；保留 owner-filter/V115 workaround 契约。 |
| `dialog/DialogContextController.java` | ✅ 已优化 | 去 R-12 前缀；`npcKey` 说明压成单句，保留兜底契约。 |

> 子代理（generalPurpose）产出，已用 `git diff` 抽查：翻译+去考古+纠错质量良好，ReadLints 干净。

#### backend / `world.travel`（批次 3，3 文件）

| 文件 | 状态 | 改了什么 |
|---|---|---|
| `world/travel/FastTravelService.java` | ✅ 已优化 | 英文 Javadoc 转中文、去「R-12 W5」与设计文档引用；保留「只算增量不持久化 + 单事务」契约与短路校验顺序。 |
| `world/travel/FastTravelRoute.java` | ✅ 已优化 | 转中文、去迭代号，保留 V119 迁移引用与「传送也耗游戏时间」语义。 |
| `world/travel/FastTravelRepository.java` | ✅ 已优化 | 转中文，保留「只读、变更只走迁移以保可复现」契约。 |

#### backend / `world.festival`（批次 4，3 文件）

| 文件 | 状态 | 改了什么 |
|---|---|---|
| `world/festival/WorldFestivalService.java` | ✅ 已优化 | 转中文、去「R-12 W5 skeleton/R-Live-Festival」叙事；两处冗长 TODO 压成单行「按用户事件待 dispatcher 落地」；保留同事务一致性契约。 |
| `world/festival/WorldFestivalRepository.java` | ✅ 已优化 | 转中文，保留「状态写入全日程级、无 user_id」契约与 `findUpcoming` 边界说明。 |
| `world/festival/WorldFestival.java` | ✅ 已优化 | 转中文、去文档章节引用，保留 `overridePayload` 语义。 |

## 全项目扩展 · 并发批次（Wave 1，2026-06-05）

> 10 路并发子代理（generalPurpose），文件集互不相交；逐文件改动见各子代理报告与 `git diff`。

| 批次 | 范围 | 文件 | 有改动 | 要点 |
|---|---|---|---|---|
| W1-A1 | learner/profile 核心（listener/controller/repository/service） | 6 | 4 | 去阶段号/设计文档引用；纠正与实现不符注释（主观维度数、`findDimensions` 异常语义、保留「不吞 `BadSqlGrammarException` 供降级」契约）|
| W1-A2 | learner/profile/dto | 21 | 1 | 仅 `DimensionUpdateResult` 删填充段收单行；其余 record 自解释 |
| W1-A3 | learner/path(+dto+repository) | 14 | 1 | `LearningPathService` 去「阶段13/Stage08」，保留 DRAFT 契约与魔法值 7 触发 `PROFILE_UPDATE` 说明 |
| W1-A4 | learner/quest + probe | 14 | 6 | 去迭代号/赛题叙事；保留 `QuestService` evidence 校验契约、探针「服务端可信不可篡改」安全约束 |
| W1-A5 | learner/evaluation + event | 15 | 1 | `LearningEvent` 英译中、去阶段号；保留事件→画像维度映射与不可篡改安全语义 |
| W1-A6 | learner/mastery + recommendation + worldstate | 15 | 5 | 去 Phase2/迭代序号；保留 mastery 阈值 / `decay` / `moodOf` 状态机 |
| W1-A7 | world/curriculum + dto | 18 | 2 | 英译中；保留 `game_bootstrap_view` 1:1 聚合契约；协议映射靠 `@JsonProperty` |
| W1-A8 | world/boss + save | 16 | 12 | 去 R-1x/Phase/W3；纠正分数公式/端点/`@JsonProperty` 说明；保留 snapshot JSONB / V108 / V114 契约 |

> `learner` 模块（85 文件）已全部完成。`world` 的 curriculum/dto/boss/save 完成；skill/calendar/domain/npc/root/controller/service/repository（32 文件）因子代理计费中断未完成，并入 Wave 2 重试。

## 全项目扩展 · 并发批次（Wave 2，2026-06-05）

> 10 路并发子代理覆盖 backend main 余下全部包；`mvn test-compile` 通过（exit 0），抽查 `git diff` 确认仅改注释。

| 批次 | 范围 | 文件 | 有改动 | 要点 |
|---|---|---|---|---|
| W2-B1 | world/skill+calendar+domain | 17 | 5 | 英译中；纠正 `CalendarMath.advanceSeason` 失效注释；保留换季数学/技能 JSONB schema；`V106` 因编码真实 `available_days` 契约保留 |
| W2-B2 | world/npc+root+controller+service+repository | 15 | 10 | 英译中；删「item-gifting 已移除」开发史；纠正与 R-14 不符的 crops/inventory/currency；补 `NpcTalkRequest` 状态机契约；保留好感度 `[0,100]`/V101 CHECK |
| W2-B3 | classroom（全 27） | 27 | 5 | 英译中、去「C2:」标记；保留 `ClassroomGuard` 鉴权、KC 热力图 + `classroom_kc_snapshot` 缓存、SSE 契约 |
| W2-B4 | coursepack（全 24） | 24 | 3 | 去 R-12/R-13/R-14 迭代号与 V125 溯源；保留 packId=43/PDF 流式/Range/页码/path-traversal 契约 |
| W2-B5 | submission（全 19） | 19 | 5 | 去「情绪化小镇②」；补 `Verdict` 枚举语义；保留判题双轨/沙箱安全/`BUG_MINE_RETRY` 契约 |
| W2-B6 | media（全 19） | 19 | 11 | 去演示/方案标号/抽取开发史；补整条 TTS 链 Javadoc；保留讯飞协议映射与降级 workaround |
| W2-B7 | auth（全 19） | 19 | 2 | `AuthService.refresh` 英译中去 Stage 02；`RegisterRequest` 去 R-13 留 XSS/字符集安全约束 |
| W2-B8 | workshop（全 17） | 17 | 3 | 去「学①扩展」；`WorkshopGraphClient`/`WorkshopRunRecorder` 英译中；保留 run/evidence/safety/grounding 契约 |
| W2-B9 | bugmine（14）+agent（2） | 16 | 5 | 去「情绪化小镇②」；补 BugMine 状态机、反例选取、agent run 持久化契约 Javadoc |
| W2-B10 | intent+orchestrator+rag+video+tutor 余 | 25 | 11 | rag 全补检索/`no_evidence_found` 防幻觉契约；video 去「方案 A」；tutor 英译中补 Controller/Session 契约；intent/orchestrator 已合规 |

> backend main 全 346 文件 + 入口类已覆盖；`mvn test-compile` exit 0。剩余：backend 测试（64）、`frontend`、`services/pythontopia-graph`、`services/pythontopia-rag`、`android`、`scripts`。

## 全项目扩展 · 并发批次（Wave 3 · frontend src，2026-06-05）

> 10 路并发子代理覆盖 `frontend/src` 全部 290 个 `.vue`/`.ts`；`npm run typecheck`（vue-tsc）通过（exit 0）。

| 批次 | 范围 | 文件 | 有改动 | 要点 |
|---|---|---|---|---|
| W3-F1 | components/workshop + tutor | 23 | 10 | 去 R-1x/P3/C1-C2 与外部产品叙事（腾讯 Marvis/Claude/Cursor）；保留 Marvis 活动条/SSE 节点映射/证据接地契约 |
| W3-F2 | components/hud+world+system+boss | 23 | 19 | 去 R-1x/Phase/P0-c/W4/①②③；纠正多处过期注释；保留 HUD 交互/a11y/Phaser 单实例 |
| W3-F3 | components/arena+ui+training+video/library/common/bugmine | 25 | 3 | 去 R-1x/方案A/A-8；保留 ui 设计系统契约、TrainingPanel 防泄答双护栏 |
| W3-F4 | components/teacher+game+board+review+profile+auth + App.vue/main.ts | 26 | 5 | 去 R-12/W2；保留 WorldCanvas 单 Game/ResizeObserver、DialogModal a11y；纠正立绘路径注释 |
| W3-F5 | api 根层 | 34 | 9 | 去 R-1x/方案A/§；保留端点/SSE/错误码/`saveSlot` jsonb 序列化契约 |
| W3-F6 | api/dto | 37 | 9 | 去 R-1x/V11x/W3-CTRL；删复述；纠正 crops/inventory 过期；保留 snake_case 对齐 |
| W3-F7 | game | 31 | 20 | 去 R-12/W1-W5/§/doc 路径；保留 Phaser 隔离/单实例/8 向降级/tilemap/场景生命周期；纠正 3 处失实注释（死区/keyDebounce/RENDER_SCALE）|
| W3-F8 | pages | 27 | 18 | 去 R-1x/M2/W4/§/Phase；保留路由/守卫/DOMPurify 安全/localStorage 契约 |
| W3-F9 | stores + data | 27 | 19 | 去 R-1x/W3-CTRL/P0-c；保留 Pinia 状态/双写持久化/mainQuest 单一源；data 保留 packId=43 grounding |
| W3-F10 | composables+utils+types+services+app(router.ts)+lib+i18n+test | 36 | 24 | 去 R-1x/§/决策日志；英文 catch 译中；保留副作用/清理/i18n key 不动 |

> `frontend/src` 全 290 文件覆盖（`App.vue`/`main.ts`/`AppLayout.vue` 均已审，AppLayout 无注释无改动）；`npm run typecheck` exit 0。
> ⚠️ 注意：`stores/mainQuest.ts` 编辑期间被**外部进程**并发改动（acceptQuest/completeSubQuest/defeatBoss 逻辑），子代理据当前代码仅调整了注释，建议复核合并。
> ⚠️ frontend 改动与未提交的工坊 feature WIP 在同文件内交织，无法像 backend 那样干净单独 commit。
> 剩余：`frontend/tests`（127）、`services/pythontopia-graph`、`services/pythontopia-rag`、`android`、`scripts`、backend 测试（64）。

## 全项目扩展 · 并发批次（Wave 4 · Python，2026-06-05）

> ⚠️ 本波 10 路子代理以默认模型派发时**全部因「API usage limit reached」未启动**；先用单探针确认 `composer-2.5-fast` 可启动后，改用该模型重新派发，全部成功。`pytest`：graph 297 passed、rag 21 passed。

| 批次 | 范围 | 文件 | 有改动 | 要点 |
|---|---|---|---|---|
| W4-P1 | graph/agents/boss | 11 | 11 | 去 R-13/Phase；补 docstring；强化 grounding 防幻觉 + 禁隐藏测试契约 |
| W4-P2 | graph/agents 顶层 | 16 | 15 | 11 个默认 Agent 补结构化输出/fail-fast/防泄答 docstring；英译中 |
| W4-P3 | graph/routes + 根 | 13 | 12 | 去迭代号；保留 SSE/协议映射/错误码跨服务契约 |
| W4-P4 | graph/nodes + graph | 13 | 11 | grounding/hallucination/safety/`no_evidence_found` 审查契约英译中保留 |
| W4-P5 | graph/schemas + llm | 12 | 9 | 去 stage 号；保留字段契约/snake_case 对齐/provider workaround |
| W4-P6 | rag 源码 | 19 | 15 | 补检索/`no_evidence_found` 防幻觉/安全过滤契约；英译中 |
| W4-P7 | graph/tests/agents（boss 单测） | 12 | 10 | 去迭代号、英译中、删冗余复述；不改断言 |
| W4-P8 | graph/tests 顶层 A | 12 | 9 | 同上；保留 TestClient/lifespan 等非显见测试约束 |
| W4-P9 | graph/tests 顶层 B | 12 | 4 | 同上；多数已合规 |
| W4-P10 | rag/tests + scripts(py×3) | 9 | 3 | rag 测试无注释合规；3 个 CLI 脚本去 R-1x、精简 docstring（探针批次）|

> Python 全模块覆盖（graph 65 src + 36 tests、rag 19 src + 6 tests、scripts 3 py）；`pytest` graph 297 / rag 21 全通过。
> 剩余：`android`（217）、`frontend/tests`（127）、backend 测试（64）、scripts 2 个 `.mjs`。
