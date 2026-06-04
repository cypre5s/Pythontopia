# 画像闭环三连 Implementation Plan

> 设计依据：`docs/plans/2026-06-04-profile-loop-trilogy-design.md`（已与用户确认方向）。
> 执行原则：DRY / YAGNI / TDD / 小步提交；每个游戏化元素必须可见地服务因材施教。

**Goal:** 把 8 维画像做成「建（玩中建）→ 用（助教因材施教）→ 境（情绪化小镇）」的动态闭环，强化软件杯 A3 的创新(35%)与功能(45%)。

**Architecture:** 后端 Spring 取画像 → 注入 graph 的 TutorAgent 按认知风格分支讲法；学习/探针事件经 `PassiveProfileService` 回写维度并审计；前端 `TutorCrew` / 小镇可视化消费画像。全部复用现有 LangGraph + SSE + Phaser + 讯飞多模态，可降级。

**Tech Stack:** Java 21 / Spring Boot（JUnit5+AssertJ+Mockito）、FastAPI + LangGraph（pytest）、Vue3 + TS strict（vue-tsc + ESLint + Vitest）。

---

## 阶段总览（里程碑）

1. **MVP = ① 助教小队因材施教**（本文件详列任务 T1–T5）。
2. **③ 玩中建画像**（探针 → 维度回写）——任务 outline，落点待开放问题 #2。
3. **② 情绪化小镇**（氛围级天气/心情 + 情绪行为信号）——outline，范围待开放问题 #3。
4. **④ 自适应脚手架**——二期 outline。

> 开放问题（需先拍板，见设计文档 §13）：①cognitive_style 是否可纠偏 ②探针落点 ③情绪范围轻/重 ④是否本轮接「调度 PM 助教」。其中 #1/#4 影响阶段 1 收尾，#2/#3 影响阶段 2/3。

> **实现状态（2026-06-04 收尾，详见 worklog）**：阶段 1①（早已落地，本轮校正文档+修类型门）、阶段 2③（看阶段探针→播种 cognitive_style）、阶段 3②（练阶段情绪信号→emotion_persistence→情绪化小镇 mood，取轻量+反应式）、学阶段 workshop 画像下传，**均已实现并全量自测通过**（后端 mvn 322/0、graph pytest 264、前端 vitest 1416、typecheck/eslint 全绿）。开放问题落地：**#2 探针落点 = 看阶段章首 NPC 对话**；**#3 情绪范围 = 轻量（tint+助教台阶，可关闭/纠偏）**。**#1（cognitive_style 纠偏）、#4（PM 调度助教）、阶段 4 自适应脚手架**仍留后。

---

## 阶段 1：① 助教小队因材施教（MVP）

现状回顾：Web 答疑链路 `TutorController → TutorService → TutorGraphClient（SSE 中继）→ graph routes/tutor.py → TutorAgent`。graph 的 `TutorAskRequest` 已有 `profile_brief` 字段，但后端目前**没传**（只传 question/kc_codes/context/images）。`TutorAgent._build_messages` 已有「根据学生画像调整语言难度」但无认知风格分支。`PassiveProfileService` 已支持维度增量 + evidence_count + revision 审计。

### Task T1：后端把「认知风格 + 易错点」摘要注入 graph 答疑请求

**Files:**
- Modify: `backend/.../tutor/TutorGraphClient.java`（请求体加 `profile_brief`）
- Modify: `backend/.../tutor/TutorService.java`（取画像并下传）
- 复用: `learner/profile/service/PassiveProfileService.getDimensions(userId)`
- Test: `backend/.../tutor/TutorProfileBriefTest.java`（新建）

**Step 1（失败测试）:** 给定某 userId 的 dimensions（cognitive_style.tags=["visual"]、error_pattern.tags=["缩进"]），`TutorService` 组装的 profile_brief 字符串应含「认知风格: visual」「易错: 缩进」。
**Step 2:** 运行测试，预期 FAIL（方法未实现）。
**Step 3（实现）:** 在 `TutorService.ask` 取 `passiveProfileService.getDimensions(userId)`，提炼 cognitive_style 主 tag + error_pattern top tags，拼成简短 `profileBrief`，经 `TutorGraphClient.askStream(..., profileBrief, ...)` 放入请求体 `profile_brief`。无画像时为空串（降级=原行为）。
**Step 4:** 运行测试，预期 PASS；并 `mvn -Dtest=TutorProfileBriefTest test`。
**Step 5（提交）:** `feat(tutor): 注入认知风格/易错点摘要到答疑请求`（仅用户确认提交后）。

### Task T2：graph 的 TutorAgent 按认知风格分支「首屏讲法」

**Files:**
- Modify: `services/pythontopia-graph/pythontopia_graph/agents/tutor_agent.py`
- Test: `services/pythontopia-graph/tests/test_tutor_agent_cognitive_style.py`（新建）

**Step 1（失败测试）:** `_build_messages(..., profile_brief="认知风格: visual ...")` 的 system 文本应含视觉型指令（先图解/mermaid）；"logical" → 先推理链；"trial" → 先最小可跑例；缺省 → 不强制。
**Step 2:** pytest 预期 FAIL。
**Step 3（实现）:** 在 SYSTEM_PROMPT 后追加「认知风格自适应」规则；解析 profile_brief 命中 visual/logical/trial 时注入对应首屏呈现指令；保留防泄答与「[页 X]」引用规则不变。
**Step 4:** `python -m pytest tests/test_tutor_agent_cognitive_style.py -q` 预期 PASS。
**Step 5（提交）:** `feat(graph): TutorAgent 按认知风格自适应首屏讲法`。

### Task T3：定义「讲法采纳」事件并回写 cognitive_style（闭环）

**Files:**
- Modify: `backend/.../learner/event/LearningEvent.java`（新增 `TutorStyleEngaged{userId, style, kcCode}`）
- Modify: `backend/.../learner/profile/PassiveProfileRevisionListener.java`（监听 → cognitive_style delta，tags+=style，小增量，source 走允许枚举如 RESOURCE_FEEDBACK）
- Modify: 触发点（答疑流：学生采纳/切换某讲法时 publish 事件）
- Test: `backend/.../learner/profile/PassiveProfileCognitiveStyleTest.java`（新建）

**Step 1（失败测试）:** 处理 `TutorStyleEngaged(style="visual")` 后，`getDimensions` 的 cognitive_style.tags 含 "visual" 且 evidence_count 增加。
**Step 2:** FAIL。
**Step 3（实现）:** 监听器映射事件 → `applyDimensionUpdate(userId,"cognitive_style", delta)`；evidence_count+1；多次累积才稳（不一次定型）。
**Step 4:** `mvn -Dtest=PassiveProfileCognitiveStyleTest test` PASS。
**Step 5（提交）:** `feat(profile): 讲法采纳回写 cognitive_style 闭环`。

### Task T4：前端 TutorCrew 体现「因材施教」+ 易错预警

**Files:**
- Modify: `frontend/src/components/tutor/TutorCrew.vue`（导师行显示当前讲法；校对/识图行显示 error_pattern 预警）
- Modify: `frontend/src/components/tutor/TutorPanel.vue`（把 cognitive_style/error_pattern 传入 crew；提供"换个讲法"按钮 → 触发采纳事件上报）
- Modify: `frontend/src/api/learner.ts` 或 tutor api（上报讲法采纳）
- Test: 复用 `npm run typecheck` + `npm run lint` + 现有 Vitest（如新增交互再补 spec）

**Step 1:** typecheck/lint 基线绿。
**Step 2-3（实现）:** crew 增 props（cognitiveStyle/errorTags）；导师行如「按你偏好的图解先讲」；"换个讲法"按钮切换并上报采纳。
**Step 4:** `npm run typecheck` exit 0、`npx eslint <改动文件>` 0 error。
**Step 5（提交）:** `feat(tutor-ui): 助教小队体现因材施教与易错预警`。

### Task T5：端到端自检 + 文档/演示锚点

- 后端 `mvn -q -DskipTests compile` + 相关单测；前端 typecheck/lint；（有网时）按 §9 脚本走一遍视觉型 demo。
- 在设计文档勾掉阶段 1，补一张「画像→讲法」前后对比图锚点（供 7 分钟视频）。

---

## 阶段 2（outline）：③ 玩中建画像
- 新事件 `ProbeChoiceMade{userId, probeKey, choice}` → 监听映射到 cognitive_style/emotion/learning_preference 小增量（复用 PassiveProfileService）。
- 前端：世界内中性探针卡（落点待 #2）；可跳过；写 evidence_count。
- 测试：监听器映射单测 + 前端 typecheck/lint。

## 阶段 3（outline）：② 情绪化小镇
- 情绪行为信号：重试/放弃/逆袭 → emotion_persistence（连击 N 次触发，多信号）。
- 前端：Phaser 氛围 tint/天气（范围待 #3）+ 助教主动台阶 + 关闭/纠偏入口。
- 测试：情绪聚合逻辑单测 + 降级（无信号=晴天）。

## 阶段 4（outline）：④ 自适应脚手架
- 训练场难度/提示粒度策略读 practical_ability+error_pattern+emotion；NPC「提示卷轴」；回灌。

---

## 验证基线（每阶段收尾必跑）
- 后端：`mvn -Dtest=<新增测试> test` + `mvn -q -DskipTests compile`
- 前端：`npm run typecheck` + `npx eslint <改动文件>`
- Graph：`python -m pytest <新增测试> -q`
- 防幻觉/降级：无 key/无网时答疑回退纯文本、画像断言附 evidence_count
