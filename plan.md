# Pythontopia 独立项目完整计划

## 1. 已锁定边界
- 项目形态：独立新仓库，默认路径 `/home/cypress/Pythontopia`。
- 技术栈：Java 21 + Spring Boot、Vue 3 + TypeScript strict + Pinia + Vitest、Phaser 3、Python FastAPI + LangGraph、PostgreSQL + pgvector、Redis。
- 项目目标：比赛可交付 + 可长期演进，Beta 目标日期为 **2026-06-21**，随后到 **2026-06-30** 做视频、PPT、文档、打包和修复。
- 开发资源：3 人 + AI 协作。
- 课程底座：内置 `packId=43`，`Python语言基础`，作为唯一知识底座。
- 游戏定位：教育优先，游戏承载；不是“游戏套壳”，每个玩法都必须服务画像、路径、资源生成、练习、评估。
- 技术债治理：只在 Pythontopia 新仓库中解决，不同步改 Alethicode 老项目。
- 数据真实度：Beta 中全部核心数据真实落库，不做纯前端假数据。
- 账号体系：真实登录 + 学生/教师角色。
- Python 执行：双轨，Pyodide 做即时预览，服务端判题做正式提交和统计。
- 班级架构：轻量班级，保留班级、成员、教师任务、班级进度/误区看板。

## 2. 产品主线
Pythontopia 是一座网页版像素学习小镇。学生进入小镇后，通过 NPC 对话构建画像，在知识田种植 KC，在图书馆和工坊生成学习资源，在训练场写 Python，在 Bug 矿洞修复错误，最后在复盘大厅更新学习路径。

核心场景：
- 画像屋：对话式学生画像，8 维画像包括专业背景、知识基础、学习目标、认知风格、学习偏好、易错模式、实践能力、情绪/坚持度。
- 图书馆：基于 `packId=43` 的课件 RAG，生成有引用的个性化讲义。
- 知识田：65 个 KC 映射为知识种子，掌握度、遗忘、易错、补救映射为作物状态。
- 资源工坊：多智能体生成讲义、思维导图、练习题、代码案例、拓展阅读、动画脚本。
- 训练场：Pyodide 即时运行 + 服务端正式判题。
- Bug 矿洞：正式提交失败后进入调试副本，展示反例、变量轨迹、错误假设和分层提示。
- 公告板：个人路径、每日任务、班级任务。
- 复盘大厅：学习日志、资源推送、路径重排。
- 教师屋：轻量班级管理与聚合看板。

## 3. 新仓库结构
- `/home/cypress/Pythontopia/backend`：Spring Boot 后端。
- `/home/cypress/Pythontopia/frontend`：Vue 3 + TypeScript strict + Phaser 3。
- `/home/cypress/Pythontopia/services/pythontopia-graph`：多智能体工作流服务。
- `/home/cypress/Pythontopia/services/pythontopia-rag`：课件 RAG 服务。
- `/home/cypress/Pythontopia/data/course-pack-43`：完整课程种子数据。
- `/home/cypress/Pythontopia/assets`：开源像素素材与 license。
- `/home/cypress/Pythontopia/deploy`：Docker Compose、一键启动、环境变量模板。
- `/home/cypress/Pythontopia/docs`：系统设计、测试说明、开源说明、AI Coding 说明、PPT 大纲、演示脚本。

## 4. 借用 Alethicode 的模块
课程包与课件结构：
- `/home/cypress/Alethicode/backend/src/main/java/com/alethicode/service/languagepack`
- `/home/cypress/Alethicode/backend/src/main/resources/db/migration/V22__bootstrap_language_pack_core.sql`
- `/home/cypress/Alethicode/backend/src/main/resources/db/migration/V23__language_pack_documents.sql`
- `/home/cypress/Alethicode/backend/src/main/resources/db/migration/V24__language_pack_pages.sql`
- `/home/cypress/Alethicode/backend/src/main/resources/db/migration/V25__language_pack_knowledge.sql`
- `/home/cypress/Alethicode/backend/src/main/resources/db/migration/V26__language_pack_examples.sql`
- `/home/cypress/Alethicode/backend/src/main/resources/db/migration/V40__language_pack_course_enrichment.sql`

RAG、防幻觉、证据引用：
- `/home/cypress/Alethicode/services/alethicode-rag/app/routes/query.py`
- `/home/cypress/Alethicode/services/alethicode-rag/app/schemas.py`
- `/home/cypress/Alethicode/services/alethicode-rag/app/rag/builder.py`
- `/home/cypress/Alethicode/services/qa-graph/app/nodes/retrieval.py`
- `/home/cypress/Alethicode/services/qa-graph/app/nodes/grounding_critic.py`
- `/home/cypress/Alethicode/services/qa-graph/app/nodes/hallucination_detector.py`
- `/home/cypress/Alethicode/services/qa-graph/app/nodes/token_grounding_verifier.py`

画像与学习路径：
- `/home/cypress/Alethicode/backend/src/main/java/com/alethicode/service/aitutor/profile`
- `/home/cypress/Alethicode/backend/src/main/java/com/alethicode/service/aitutor/path/MasteryAdaptiveProblemSelector.java`
- `/home/cypress/Alethicode/backend/src/main/resources/db/migration/V41__learner_course_progress.sql`
- `/home/cypress/Alethicode/backend/src/main/resources/db/migration/V63__add_learner_narrative_summary.sql`

多智能体工作流：
- `/home/cypress/Alethicode/services/tutor-graph/app/graph/builder.py`
- `/home/cypress/Alethicode/services/tutor-graph/app/graph/state.py`
- `/home/cypress/Alethicode/services/tutor-graph/app/nodes/evidence.py`
- `/home/cypress/Alethicode/services/tutor-graph/app/nodes/diagnosis.py`
- `/home/cypress/Alethicode/services/tutor-graph/app/nodes/output_sanitization.py`

判题与调试：
- `/home/cypress/Alethicode/backend/src/main/java/com/alethicode/service/submission`
- `/home/cypress/Alethicode/backend/src/main/java/com/alethicode/service/aitutor/execution/PythonExecutionTraceService.java`
- `/home/cypress/Alethicode/backend/src/main/java/com/alethicode/service/aitutor/visualize/VisualizeCapabilityService.java`

轻量班级：
- `/home/cypress/Alethicode/backend/src/main/java/com/alethicode/service/classroom`
- `/home/cypress/Alethicode/backend/src/main/java/com/alethicode/controller/classroom`
- `/home/cypress/Alethicode/backend/src/main/resources/db/migration/V10__bootstrap_m9_classroom_core.sql`
- `/home/cypress/Alethicode/backend/src/main/resources/db/migration/V30__classroom_single_language_pack.sql`

前端/游戏灵感，不直接照搬：
- `/home/cypress/Alethicode/frontend/src/pages/oj/views/languagepack/LanguagePackQaPage.vue`
- `/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue`
- `/home/cypress/Alethicode/game/src/engine/PythonRunner.js`
- `/home/cypress/Alethicode/game/src/engine/SaveManager.js`
- `/home/cypress/Alethicode/game/src/components/DialogueBox.vue`
- `/home/cypress/Alethicode/game/src/components/CodingChallenge.vue`

## 5. 工程债硬要求
- 前端新代码 100% TypeScript strict。
- 禁止业务层 `any`；Phaser/第三方动态对象只能隔离在 adapter 层。
- 状态管理使用 Pinia，不引入 Vuex。
- 单元测试使用 Vitest。
- 前后端 DTO 使用明确契约，前端 API client 不手写散装字段。
- 资源生成、画像、任务、班级、判题结果都要有可测试接口。
- 前端组件使用 `.vue` 单文件组件，组件名 PascalCase。
- 游戏状态、学习状态、AI 生成状态分 store 管理，不混成一个全局对象。

## 6. 数据模型
后端核心表：
- `users`、`roles`、`classrooms`、`classroom_members`
- `course_pack`、`course_document`、`course_page`、`course_chapter`、`course_kc`、`course_example`、`course_problem`
- `learner_profile`、`profile_interview`、`profile_revision`
- `world_session`、`player_state`、`npc_state`、`weather_state`、`crop_tile_state`
- `learning_path`、`learning_path_node`
- `quest`、`quest_progress`
- `resource_run`、`learning_resource`、`agent_event`
- `practice_submission`、`judge_result`、`debug_case`
- `classroom_quest`、`classroom_kc_snapshot`

导入验收：
- `course_pack.id=43`
- 7 份文档
- 561 页
- 7 章
- 65 KC
- 46 示例
- 39 题

## 7. Agent 设计
- `ProfileAgent`：画像访谈。
- `CurriculumMapperAgent`：课程/KC 到小镇地图映射。
- `ResourceDesignerAgent`：个性化讲义。
- `MindMapAgent`：Mermaid 思维导图。
- `PracticeAgent`：练习题。
- `CodeLabAgent`：代码实操案例。
- `StoryboardAgent`：动画脚本/短视频脚本。
- `PathPlannerAgent`：学习路径。
- `DebugAgent`：Bug 矿洞调试。
- `GroundingCriticAgent`：课件证据审查。
- `SafetyAgent`：内容安全与泄题审查。

主 LLM Provider：
- 演示主链路必须接科大讯飞相关模型/工具。
- 其他 Provider 只能作为开发期 fallback 或可插拔说明。

## 8. 一个月实施排期
第 1 周，2026-05-21 到 2026-05-27：
- 建独立仓库骨架。
- 导出并内置 `packId=43`。
- 完成后端课程、账号、班级、画像、小镇基础 schema。
- 完成前端 Phaser 小镇 MVP：地图、移动、NPC、建筑交互。
- 完成 strict TS、Pinia、Vitest、DTO 基础设施。

第 2 周，2026-05-28 到 2026-06-03：
- 完成画像屋和 `ProfileAgent`。
- 完成知识田、KC 种子、作物状态。
- 完成 RAG 服务和课件证据检索。
- 完成资源工坊第一版：讲义、思维导图、练习题。
- 完成真实天气、NPC 状态、任务落库。

第 3 周，2026-06-04 到 2026-06-10：
- 完成资源工坊六类资源。
- 完成学习路径、每日任务、资源背包。
- 完成 Pyodide 即时运行。
- 完成服务端正式判题。
- 完成 Bug 矿洞轻量闭环。
- 完成讯飞 Provider 接入。

第 4 周，2026-06-11 到 2026-06-21：
- 完成轻量班级：创建、加入、教师任务、班级看板。
- 完成学习效果评估和路径重排。
- 完成全流程视觉打磨。
- 完成 Docker Compose 一键启动。
- 完成测试报告、系统设计、开源说明、AI Coding 工具说明。
- 完成 Beta 验收。

收尾，2026-06-22 到 2026-06-30：
- 录制 7 分钟演示视频。
- 完成 PPT。
- 清理提交包。
- 跑全量回归。
- 修复评审演示路径问题。

## 9. 验收标准
功能：
- 学生能注册/登录并进入小镇。
- 教师能创建班级并发布任务。
- 学生能通过画像访谈生成 8 维画像。
- 学生能种植至少 10 个真实 KC。
- 系统能生成至少 6 类资源。
- 每个资源带课件引用或明确超纲说明。
- 学生能在 Pyodide 中即时运行代码。
- 学生能正式提交并得到服务端判题结果。
- 失败提交能进入 Bug 矿洞。
- 班级看板能展示真实聚合数据。

工程：
- `frontend` TypeScript strict 通过。
- `frontend` Vitest 通过。
- `backend` Maven test 通过。
- `pythontopia-graph` pytest 通过。
- Docker Compose 可一键启动。
- 无未授权素材。
- 无伪造课件引用。
- 无纯前端假数据冒充真实业务数据。

## 10. 明确不做
- 不复制《星露谷物语》官方素材。
- 不复制 `Notyourbing/StardewValley` 的 C++/Cocos2d-x 实现。
- 不在 Beta 做完整开放世界。
- 不做完整 Alethicode 课堂协作。
- 不做多语言 OJ。
- 不做真实长视频渲染。
- 不启用 ReAct 循环。
- 不把 Alethicode 老前端技术债带进新仓库。
