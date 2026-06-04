# 画像闭环三连 · 实施工作日志（实时）

> 本文件实时记录实现进度：每完成一个任务追加「做了什么 / 改了哪些文件 / 验证结果」。
> 配套：design / plan / framework / detailed-design 四份文档。

## 范围（本轮）
阶段 0 地基（confidence + decay）+ 阶段 1 ①助教因材施教（T1–T5）。做完跑完整测试。

## 进度

### 阶段 0 · 画像引擎地基（confidence + decay）
- 状态：✅ 完成
- 做了什么：`ProfileDimensionDto` 增 `confidence`；`PassiveProfileService` 增常量 `EVIDENCE_SATURATION=8`/`DECAY_HALF_LIFE_DAYS=14`，`getDimensions` 读时派生 `confidence=min(1,evidence_count/8)` 并对 score 做半衰期 14 天 decay（`applyDecay`，raw 不入库；last_updated 缺失/不可解析则不衰减）；前端 `learner.ts` 的 `ProfileDimensionDto` 补 `confidence`。
- 改动文件：`backend/.../profile/dto/ProfileDimensionDto.java`、`backend/.../profile/service/PassiveProfileService.java`、`frontend/src/api/learner.ts`、`backend/.../profile/PassiveProfileServiceTest.java`(+3 用例)。
- 验证：`mvn -Dtest=PassiveProfileServiceTest test` → Tests run: 15, Failures: 0, BUILD SUCCESS。

### 阶段 1 · ① 助教因材施教
- 状态：✅ 已落地（本日志先前「T1 进行中」为过期记录，经全仓核对 T1–T4 均已实现且有测试）。
- T1 profile_brief 注入：`TutorService.resolveProfileBrief/buildProfileBrief`（confidence≥0.3 门控）→ `TutorGraphClient` 请求体 `profile_brief` → graph `schemas/tutor.py` + `routes/tutor.py`。测试 `TutorProfileBriefTest`。
- T2 认知风格分支：`tutor_agent.py::_build_messages/_cognitive_style_directive`（visual/logical/trial）。测试 `test_tutor_agent_cognitive_style.py`。
- T3 采纳回写：`POST /api/tutor/style-engaged` → `TutorService.recordStyleEngaged` → `TutorStyleEngaged` → `PassiveProfileRevisionListener.onTutorStyleEngaged` → cognitive_style。测试 `PassiveProfileCognitiveStyleTest`。
- T4 前端因材施教 UI：`TutorCrew.vue`（cognitiveStyle/errorTags props + 换讲法）、`TutorPanel.vue`（loadProfileSignals/onPickStyle），嵌入 `LecturePage`(看/学) 与 `TrainingPanel`(练)。
- 已知体验缺口（非本轮阻塞）：「换讲法」只回写未即时改写当前答案；学生若无 cognitive_style 种子则 brief 为空降级为纯文本——本缺口正由阶段 2「③探针」补齐。

### 阶段 2 · ②+③ 融入看学练测主链路（R-14 单 town_hub）
- 决策（2026-06-04）：采用「全量融入」方向；情绪化小镇②默认取**轻量+可反应**（mood tint + 助教台阶，背景级、可关闭/纠偏，经 `WorldClock` 反应式刷新），后续可升级粒子。
- 关键架构事实（经 6 路只读子代理核对）：~35 主线在**前端** `frontend/src/data/mainQuests.ts` + `stores/mainQuest.ts` + `save_slot` 快照；看/学/练/测 phase 由 `mainSubQuestPhase(evidenceRequired.type)` 派生。融入主链路=改前端数据/store/对话层 + 画像事件监听器，**不动后端 QuestService**。
- 子任务：T6 ③探针(看阶段播种 cognitive_style) · T7 ②情绪信号(练阶段 retry/abandon/comeback→emotion_persistence)+world-state · T8 ②mood 渲染(MoodOverlay+WorldClock) · T9 学阶段 workshop 画像下传 · T10 全量验证。

#### T6 · ③ 玩中建画像（探针 → 播种 cognitive_style）— ✅ 完成
- 后端（本体亲自实现，触碰共享文件以防冲突）：
  - `LearningEvent.java` 新增 sealed record `ProbeChoiceMade(userId, probeKey, choiceId)`。
  - `learner/probe/ProbeCatalog.java`（新）：服务端权威目录（静态不可变），`cog_intro→cognitive_style(visual/logical/trial,+0.03)`、`fail_react→emotion_persistence(retry+0.02/hint 0/skip-0.02)`、`media_pref→learning_preference(+0.01)`；source 复用 `RESOURCE_FEEDBACK` 故无新迁移。`lookup`/`isValid` 静态共用（控制器校验 + 监听器映射），故监听器构造函数不变、两处旧测试不破。
  - `learner/probe/ProbeController.java`（新）：`POST /api/learner/probe`（STUDENT），校验失败 `400 PROBE_INVALID`，否则发 `ProbeChoiceMade`。`dto/ProbeRequest.java`（新，@NotBlank/@Size）。
  - `PassiveProfileRevisionListener.onProbeChoiceMade`：经 ProbeCatalog 解析 → `applyDimensionUpdate`（evidence+1、tag 累积、score 增量）。
  - 测试：`ProbeCatalogTest`(3) + `PassiveProfileProbeTest`(3) 全绿；`PassiveProfileCognitiveStyleTest`(2)/`PassiveProfileRevisionListenerTest`(6) 无回归。
- 前端（并行子代理实现，本体复核 + 补缺）：
  - `api/learner.ts` 增 `submitProbe(probeKey, choiceId)`；`stores/probe.ts`（新）`useProbeStore`（shown/answered + localStorage `pyt_probe_answered`，submit 失败静默降级绝不阻塞玩家）；`components/world/ProbeCard.vue`（新）中性可跳过卡，watch 主线 `currentSubQuest` 在「看」阶段且未答时弹出；挂载于 `WorldPage.vue`。
  - 顺手修复 ① 遗留类型门红：`TutorCrew.vue` 在 `exactOptionalPropertyTypes` 下 `errorTags` 可能 undefined → 收敛为 `errorTagList` 派生。
  - 验证：`npm run typecheck` exit 0；eslint 7 文件 0 error；`vitest`（probe store + ProbeCard）9/9。
- 闭环效果：看阶段探针 → cognitive_style 播种 → 此前「种子饥饿」的 ① 因材施教在 LecturePage(看/学)+TrainingPanel(练) 真正触发。

#### T7 · ② 情绪信号（练阶段 → emotion_persistence）+ world-state — ✅ 完成（后端）
- 事件：`LearningEvent` 新增 `PracticeRetried(userId,problemId,kcCode)` / `PracticeAbandoned(...)` / `ComebackSolved(...,priorFails)`。
- 派生点（练阶段真实业务点）：
  - `SubmissionService.handleCompletion`：新增 `PracticeSubmissionRepository.countPriorFailures(userId,problemId,beforeId)`（id< 当前，status=FAILED）；AC 且 priorFails≥2 → `ComebackSolved`；失败且 priorFails≥1 → `PracticeRetried`。
  - `BugMineService.close(ABANDONED)` → `PracticeAbandoned`。
- 监听器：`onPracticeRetried`(+0.02,retry,PRACTICE_FAIL) / `onPracticeAbandoned`(-0.03,abandon,PRACTICE_FAIL) / `onComebackSolved`(+0.04,comeback,PRACTICE_PASS) → emotion_persistence；更新过期 Javadoc。
- world-state：`learner/worldstate/`（新）`WorldStateController`(GET /api/learner/world-state, STUDENT) + `WorldStateService`（mood 派生：连胜/连挫优先，再看 emotion_persistence 慢分；失败降级中性）+ `dto/WorldStateDto(mood,streak,emotionScore)`。
- 测试：`PassiveProfileEmotionTest`(3)+`WorldStateServiceTest`(4)+`SubmissionEmotionSignalTest`(3) 全绿；`SubmissionServiceTest`(4) 无回归。

#### T8 · ② 情绪化小镇前端（mood 渲染 + 助教台阶）— ✅ 完成（并行子代理 + 本体全量验证）
- `api/learner.ts` 增 `WorldStateDto`+`fetchWorldState()`；`stores/mood.ts`（新）`useMoodStore`（refresh→world-state，失败降级 sunny，派发 `pythontopia:mood-changed`；`关闭氛围` 持久化 `pyt_mood_disabled`）。
- `game/adapters/mood.ts`（新）`applyMood`：全视口深度 150 矩形叠加（cloudy 冷灰 0.16 / rainy 暗蓝紫 0.26 / sunny 无），不加第三个 setTint（避免与 weather@80/daynight 抢相机 tint）；受 `showParticles`+reduced-motion 门控（仅关淡入 tween，静态 tint 保留以保「画像改了世界」可见）。
- `WorldScene.ts` 外科手术接线：state.mood 生命周期镜像 weather（init/shutdown destroy）+ `pythontopia:mood-changed` 监听反应式重刷（补 weather 「只在 create 应用一次」的反应式缺口）。
- `components/world/MoodOverlay.vue`（新）：rainy 时弹背景级、可关闭「需要我搭把手吗？」助教台阶（→/workshop）；挂于 `WorldPage.vue`。`learning.ts.submitToServer` 服务端判题落地后 fire-and-forget `useMoodStore().refresh()`（练后心情即时变）。
- 验证（本体亲自全量复跑）：`npm run typecheck` exit 0；eslint 9 文件 0 error；**整套 vitest 123 文件 / 1416 测试全过**（含 mood store 5 + MoodOverlay 5 新测试，WorldScene/weather/daynight/learning 无回归）。
- 闭环效果：练阶段重试/放弃/逆袭 → emotion_persistence → world-state mood → 小镇 tint + 助教台阶，30 秒内屏幕可见「画像改了世界」。

#### T9 · 学阶段集成（workshop 资源生成下传画像）— 后端 ✅ / 图侧进行中
- 后端（本体）：`ResourceProduceRequestDto.ProfileSnapshotDto` 增 `@JsonProperty("cognitive_style")`+`@JsonProperty("error_patterns")`；`ResourceWorkshopService` 注入 `PassiveProfileService`，`profileSnapshot(profile, dims)` 经静态 `cognitiveStyleTag`(置信度≥0.3 主 tag)/`errorTags`(≤3) 下传；读画像失败降级空（不阻断资源生成）。create + createStream 两路均下传。
- 后端测试：`ResourceWorkshopServiceTest` 增 `createPassesCognitiveStyleAndErrorTagsFromDimensions`（9/9）；顺手修 `WorkshopGraphClientTest` 旧 3 参构造 → 5 参（2/2）。
- 图侧（并行子代理）✅：`schemas/resource.py` ProfileSnapshot 增 `cognitive_style`/`error_patterns`；新增 `agents/personalization.py`（`resource_personalization_directive`/`personalization_messages`，镜像 tutor_agent 的 visual/logical/trial 映射 + 易错点预警）；注入全部 6 个资源 agent（handout/mind_map/code_lab/reading/practice/storyboard）的 prompt（user 消息前一条 system 指令）；新增 `tests/test_resource_personalization.py`。pytest 全套 264 通过。
- 闭环效果：学阶段 workshop 生成讲义/导图/示例时按 cognitive_style 改变呈现（视觉→图解、逻辑→推理链、试错→最小例）并主动预警 error_pattern 老坑。

### 阶段 2/3 收尾 · 全量验证（本体亲自全跑）
- 后端：`mvn test` 全套 **Tests run: 322, Failures: 0, Errors: 0, BUILD SUCCESS**（含 Stage* Testcontainers 集成测试；尾部 Hikari connection-refused WARN 为 QuestExpirationScheduler 与容器拆除竞态的良性日志，非测试失败）。
- 图：`.venv/bin/python -m pytest -q` 全套 **264 passed**。
- 前端：`npm run typecheck` exit 0；eslint 改动文件 0 error；`vitest` 全套 **123 文件 / 1416 测试通过**。
- 并发执行：③前端 / ②前端 / 学图侧各由子代理并行实现，本体亲自做共享后端 + 全量复验；每个子代理启动均经 transcript 实时核验。

### 本轮交付边界（诚实记录·未做/留后）
- ① 体验润色「换讲法即时改写当前答案」「看阶段 NPC 对话 3-way 个性化（DialogContextService binary→3way）」：本轮未做（用户选「全量 ②③」，此二项属 ① 深化，已在问答框作为后续选项列出）。
- ③ 第二/第三探针（fail_react@练 / media_pref）：后端 ProbeCatalog 已支持并测试，前端仅接 cog_intro@看；其余探针待后续按落点接入。
- ② 情绪化小镇取「轻量+反应式」（tint + 助教台阶）；重量级（天气粒子/NPC 动作）按设计 §13 #3 留后。
- 新增 REST（/api/learner/probe、/api/learner/world-state）以单测覆盖控制器/服务/监听器；未单独加 @SpringBootTest 端到端集成（全套 322 集成测试通过，未回归）。

### 附加 · 前端「降低学生认知负担」优化（对齐 docs/frontend-ui-standard.md）
- 先本地提交整个项目检查点（commit 3803bb23，553 文件；.tmp-preview 加 .gitignore 排除）。
- 经只读审计 → 6 集群 C1–C6，按「不相交文件域」拆 4 个并行子代理实现（均实时核验启动）：
  - WS-1 壳/HUD/引导（C1+C4）：一屏唯一主按钮（向导脉冲→安静次级、LOP 默认折叠+去呼吸动画）、首次进入 Help 精简三条+折叠、防 Help→镇长对话→探针 三连堆叠（ProbeCard hold + dialog.visible 门控）、向导首句具体化、统一「AI 助教」。
  - WS-2 菜单/导航/残留（C3+C5）：PauseMenu 17→4 组、QuickWheel 降级、FestivalRibbon 仅节日、FastTravel 去金币、questGuidance 去农场/5图死路+修 Pythonopia 拼写。
  - WS-3 工坊（C2+C6）：欢迎一句话+「更多用法」折叠、slash 去密集、防幻觉→已核对教材来源、AI 助教命名。
  - WS-4 练/看/画像（C2+C6+D3）：Pyodide/服务端判题/practice_submission→本机试跑(不记分)/正式提交(评分)、KC→知识点、Lecture 单主按钮、Arena 去金币、画像「场依存/序列型」→口语。
- 验证（本体亲自全量复跑）：`npm run typecheck` 全绿；`vitest` 125 文件 / 1426 测试全过；eslint 24 改动文件 0 error（仅历史格式 warning）。
- ⚠ 发现并发工作流：仓库存在另一会话在并行开发（docs/plans/2026-06-04-bilibili-cards-and-agent-studio-plan.md + video/bilibili/agent-studio 新文件），与本轮 UX 在 `AgentOfficeScene.vue` 有文件级交叠，一度令全局 typecheck 瞬时变红（其 WIP，已自行恢复）。本轮未提交 UX 改动，待与用户协调（避免把对方半成品一并入库）。

### 附加 · ①体验闭环 + ③探针补全 + #1 纠偏（4 并行子代理，避开并发会话文件域）
- 先读并发会话 plan 做文件分区（其拥有 video/bilibili/agent-studio + useSseStream + resource_graph.run_streaming + LecturePage B站区；其声明不动 Tutor/Orchestrator）。本轮只取与之不相交、且对方未碰的 tutor/dialog/probe/profile。
- ①a 换讲法即时改写：`TutorAskRequest` 增可选 `style`；`TutorService.buildProfileBrief(dims, styleOverride)` 显式选择**绕过 0.3 置信门**；`TutorPanel.onPickStyle` 用上次问题带 style 重问并重渲染（仍写回 styleEngaged）。graph 不动（override 注入 profile_brief，既有 `_cognitive_style_directive` 解析）。测试 `TutorProfileBriefTest` 7。
- ①b 看阶段对话 3-way：`DialogContextService.inferCognitiveStyle`（读 cognitive_style.tags 取主 tag，降级 unknown）+ `DialogContextDto.cognitiveStyle`（保留旧 binary profileType）；前端 `dialogTemplate.ts` 新增 `{{styleAdaptive:visual|logical|trial}}` token（旧 token 不变）；mayor_py/professor 各 1 行示范。测试 `DialogContextServiceTest` 9 + dialogTemplate 80。
- ③ fail_react 探针：新 `data/probeCatalog.ts`（cog_intro + fail_react）；`probe.ts` 增 `requested` 会话态；`ProbeCard` 数据驱动；`learning.submitToServer` 失败分支 fire-and-forget `probe.request('fail_react')`（Pinia 单例跨路由可达，胜过 window 事件）。media_pref 跳过（后端未确认 + 落点争用）。测试 probe 26。
- #1 cognitive_style 纠偏：`SELF_EDITABLE_DIMENSIONS` 加 `cognitive_style`；自评（source=INTERVIEW）时所选 tag **置顶为主**（仅此路径，passive 不变，附回归测试）；`learner.ts selfUpdateDimension` 支持 tagsAdd；`ProfilePage` 三选纠偏控件。测试 `PassiveProfileServiceTest` 17。
- 全量验证：后端 `mvn test` **342/0 BUILD SUCCESS**；前端 typecheck 干净 + vitest **127 文件/1451 测试** + eslint 12 改动文件 0 error。与并发会话无文件冲突，合并工作树全绿。
- #4 PM 调度助教：经核，既有 `orchestrator`（后端/graph/路由）+ `GuideOverlay`(/api/orchestrator/turn/stream) + 并发会话 `AgentOfficeScene` 工作室已覆盖「多智能体可视化」，再做会撞车，故不重复实现。
- 仍未提交（用户「等两会话都完成再统一提交」）。
