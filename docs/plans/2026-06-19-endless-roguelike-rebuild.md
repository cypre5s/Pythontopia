# 无尽闯关重构：知识前沿 · Roguelike 远征（2026-06-19）

## 1. 背景与目标

### 现状
- `frontend/src/pages/ArenaPage.vue`（`/arena/:chapter`）= 7 章 × 10 关，关卡数据 **硬编码**在
  `frontend/src/data/arenaLevels.ts`（70 关）；解锁靠「上一关通过」，进度存 `useArenaStore`(localStorage)。
- 每关本质只是带参跳进 `frontend/src/pages/EndlessPage.vue`（`/endless?chapter&kc&kind&arenaLevel`）。
- `EndlessPage` + `composables/useEndlessRun.ts` 已能：连续 AI 出题（5 重审查 + golden 兜底）、
  `computeAdaptiveDifficulty` 自适应难度、`useProfileSignals` 画像偏好弱项 KC、真判题入库。

### 问题
「一大章一小章」是**写死的网格**：所有学生看到同一棵 70 关树，与个人掌握度 / 画像无关，
与「无尽 / 个性化」名不副实。

### 目标（本次拍板）
1. 把刷题真正变成 **基于学生进度 + 画像** 的无尽模式（知识前沿 / ZPD 自适应编排，含间隔重复）。
2. 用 **2D 游戏独特玩法** 承载：Roguelike 分支地图远征（类杀戮尖塔）。
3. **温和 HP**：连错扣血（按韧性减档），HP 归零结束本局，但**掌握度收益持久保留**。
4. **章节降级为可选「聚焦章节」过滤器**，默认按画像全局自适应。
5. 先写详细计划（本文），再按计划分阶段实现。

### 不破的硬约束（AGENTS.md）
- 题目仍走 5 重审查 + golden 兜底 + **真判题**，不做纯前端假数据冒充业务数据。
- 掌握度 / 达标 **后端权威**；无尽是「练」，`mainline` 章末测试仍是正式「测」闸（不动）。
- Phaser 单 Game 实例（`game/adapters/phaserGame.ts`），不另起 `new Phaser.Game`。
- 不做无教育目的的装饰系统；meta 进度绑定 65 KC 知识图谱本身。

## 2. 复用清单（不重造轮子）
- 题型渲染：`components/arena/KindRouter.vue` + 9 张 kind 卡。
- 出题/判题：`api/boss.ts`（`requestBossProblem` / `submitBossAttempt` / `prefetchBossProblem`，已带 golden 兜底 + 画像信号）。
- 画像信号：`composables/useProfileSignals.ts`（weakKcs / successRate / resilience / pythonSelfRatedLevel）+ `computeAdaptiveDifficulty`。
- 掌握度快照：`learning.field.masterySnapshot`（每 KC `masteryScore`）。
- KC 元数据：`data/kcNames.ts`、`data/bossCatalog.ts`（7 像素 boss + 每章 KC range）。
- 补星回流：`mainline.submitEndlessSummary`（`postEndlessSummary`）。
- 单题判题闭环：`useEndlessRun` 的 `_runPyodide` + `submitBossAttempt` 逻辑（抽出复用）。

## 3. 核心模型：知识前沿选择器（ZPD）
维护每生 KC 掌握度向量（来自 `masterySnapshot`）+ 画像。每次要「下一题」时：
- **候选池**：按「聚焦章节」过滤（无聚焦 = 全 65 KC）。
- **前沿优先级**（高→低）：
  1. 画像弱项 KC（`weakKcs`）且掌握度 < 0.8；
  2. 掌握度处于中带（0.2–0.8，够得着的 ZPD）；
  3. 间隔重复：掌握度曾达标但**久未练**（用 run 历史时间戳）需巩固；
  4. 其余未掌握 KC。
- **难度**：`computeAdaptiveDifficulty(signals, baseDifficulty=该 KC 当前档, recentFailures, recentStreak)`。
- **题型**：节点类型决定（见 §4），并参考画像易错模式（bug 型多给 `debug`）。
- MVP 在**前端**用上述规则选 KC；后续阶段把选择器下沉后端（带间隔重复持久化、防刷）。

## 4. 玩法：Roguelike 分支远征
### 地图（类杀戮尖塔）
- 一局 run = 一张 **分层 DAG**：`L` 层（MVP 8–12 层），每层 2–4 个节点，相邻层按边连通；
  起点单节点，终点为 boss 节点。
- 玩家从当前层沿出边选择下一个可达节点（**分支 = 自主性**）。
- 节点类型（→ 复用 kind）：
  | 节点 | 含义 | 复用 kind / 行为 |
  |---|---|---|
  | 战斗 battle | 常规判题 | dual_track / fill_blank / closed_loop（按画像/KC 选） |
  | 精英 elite | 高难判题 | 同上，难度 +1，奖励更高 |
  | 事件 event | 概念考点 | pdf_question / short_answer / teach_back |
  | 改错 bug | 抓 bug | debug / bug_mine（画像 bug 型偏多） |
  | 宝箱 chest | 领资源 | 跳百晓工坊生成本 KC 资源（或小额掌握度巩固） |
  | 营地 rest | 复盘恢复 | 回血 + 知识点复盘卡（导学 KNOWLEDGE_REVIEW） |
  | boss | 知识闸 | `bossCatalog` 像素 boss；过关回流 mainline 补星 |
- 节点的 KC/题型/难度由 §3 选择器在**生成地图时**确定（profile 决定类型分布权重）。

### HP / 气势 / 计分（温和）
- HP 初始（如 5）。判题**失败**：HP − `resilience < 0.4 ? 1 : 2`?（温和：低韧性扣得**更少**，避免逼玩家）→
  采用 `loss = resilience < 0.4 ? 1 : 2` 的反向？澄清为：**低韧性 → 扣 1，高韧性 → 扣 2** 让脆弱学生更耐打。营地可回血。
- 连对攒「气势 momentum」：连胜拉高下一题难度与得分系数（沿用 streak 思路）。
- HP 归零 → 本局结束（结算页：本局深度、击败 KC、得分、掌握度变化）。**掌握度/判题结果已实时入库，永不回滚**。

### 跨局 meta：知识星座
- 65 KC 画成一张星座/技能树（就是 KC 图）。掌握度达阈值的 KC 点亮。每局把星座推进一点点，
  是可见的、空间化的掌握地图（替代「70 关进度条」）。MVP 用 DOM 网格，阶段 3 用 Phaser。

## 5. 架构与文件
### 前端（MVP，本轮）
- `types/rogue.ts`：`RogueNodeKind`、`RogueNode`、`RogueMap`、`RogueRunState`。
- `data/rogueConfig.ts`：节点类型 → kind 映射、层数/宽度、类型权重（受画像调制）。
- `composables/useKcFrontier.ts`：从 `masterySnapshot` + `useProfileSignals` + 聚焦章节产出候选 KC + 优先级。
- `composables/useRogueRun.ts`（或 `stores/rogueRun.ts` Pinia）：生成地图、推进、判题结果应用 HP/气势/计分、localStorage 持久化、补星上报。
- `composables/useChallengeJudge.ts`：抽出 `useEndlessRun` 的单题「出题→Pyodide 预检→`submitBossAttempt` 真判题」闭环，run 与旧 endless 共用。
- 页面：`pages/EndlessExpeditionPage.vue`（新「无尽远征」）——地图视图 + 进入节点时复用 `KindRouter` 渲染 + 结算。
- 路由：`/arena/:chapter` → 重定向 `/endless?focus=:chapter`（兼容旧入口）；World HUD「无尽闯关」指向新页。
- 旧 `EndlessPage` 保留为「快速刷题」简洁模式（可从远征页「随便练练」进入），不删。

### 后端 / graph（阶段 2）
- 新「下一题选择器」端点：吃学生状态 + 画像 → 返回下一节点（KC/kind/难度）+ 间隔重复调度，扩展现有 boss/endless 接口。
- run 持久化 + 防刷（每日每章上限，沿用 `MainlineRecoveryService` 常量）。

## 6. 分阶段交付
- **阶段 1（本轮）· 前端 Roguelike MVP**：types + 配置 + KC 前沿（前端规则）+ run store + 地图 DOM UI + 节点判题（复用 KindRouter + 真判题）+ HP/气势/结算 + 章节聚焦过滤 + localStorage + 旧 `/arena` 重定向。带单测。
- **阶段 2 · 后端选择器 + 持久化**：下沉选择器、间隔重复、run 入库、防刷。
- **阶段 3 · 像素化**：Phaser 单例画分支地图 / 远征塔 + 知识星座可视化。
- **阶段 4 · 每日挑战 + 退役静态 Arena**：种子化每日弱项 run；删 `arenaLevels.ts` 70 关与 `ArenaPage` 残留。

## 7. 验收（阶段 1）
- 进入 `/endless`（或新远征路由）能生成一张分支地图，节点类型按画像分布。
- 选节点 → 出题（5 重审查/golden）→ 真判题入库；对 → 气势+得分+掌握度，错 → 按韧性温和扣血。
- HP 归零结算；掌握度结果不回滚；达标触发 mainline 补星。
- 章节聚焦过滤生效；无聚焦时按画像全局自适应。
- `npm run typecheck` / `eslint` / `vitest` 全绿；窄屏可用。

## 8. 风险与开放问题
- 前端选择器是临时方案，掌握度快照可能滞后（后端权威，阶段 2 下沉解决）。
- 地图 DAG 生成与连通性需保证「每条路可达 boss」。
- HP 温和规则的具体数值需打磨（首版给保守默认，后续按数据调）。
- 与 `mainline`「测」闸的回流口径需与后端对齐（沿用 `submitEndlessSummary` 门槛）。

## 9. 实施进度（2026-06-19 更新）
- **阶段 1（前端 Roguelike MVP）✅**：types + 配置 + KC 前沿 + run store + 地图 DOM UI + 节点真判题 + HP/气势/结算 + 章节聚焦 + localStorage + `/arena` 重定向 + 单测。
- **阶段 2（后端选择器 + 间隔重复）✅**：选择器下沉 graph `agents/boss/expedition_selector` + `POST /agents/boss/select-frontier`；后端 `BossController/BossService/BossGraphClient` 读权威掌握度透传；前端 `api/expedition` + `rogueRun.startRun` 后端优先 + 本地兜底。
- **阶段 3（像素化）✅**：知识星座 DOM 可视化（`KnowledgeConstellation.vue`）；**Phase 3b** Phaser 像素远征塔 `game/scenes/ExpeditionScene.ts` + `components/expedition/ExpeditionCanvas.vue`（单 Game 单例渐进增强，DOM 地图保留为交互真源/降级；点像素节点 = `run.enterNode`；过 BOSS 庆祝；零第三方素材）。
- **阶段 4（每日挑战 + 退役静态 Arena）✅**：每日弱项种子 run；删 `arenaLevels.ts`/`ArenaPage`，`/arena` 重定向 `/expedition`。
- **Phase 2d（run 跨会话 DB 持久化 + 间隔重复持久化）✅**：迁移 `V173__expedition_run.sql`（user 单活跃 run）；`ExpeditionRunController/Service/Dto`（GET/PUT/DELETE，学生限定）；`stores/rogueRun` 后端优先 + localStorage 兜底 + 续局/弃局 + 跨设备 `resumeFromBackend`；间隔重复改用持久化 `kc_mastery.last_event_at`（`BossService` → `BossGraphClient.lastPracticed` → graph `select_frontier(last_practiced=...)`）。
- **测试**：前端 `npm run test` 2172 passed（含 ExpeditionScene 6 / expedition api 5 / rogueRun 续局+兜底）；后端 `BossServiceTest`+`ExpeditionRunControllerTest` 18 passed；graph `test_expedition_selector` 7 passed。浏览器对 demo-stu-01 完成像素塔可视化验收（render→自适应一屏→点节点进真判题）。
- **后续**：空库初始化迁移与全链路 E2E 在 ECS 部署专场验；像素塔 avatar 攀爬/通关动画当前为状态重建驱动（已单测），后续可加补间动画打磨。

## 10. 玩法 + 健壮性补强（2026-06-21）

在四阶段已交付基础上做的外科手术式增强（不动「真判题/掌握度后端权威」红线，纯前端层）：

- **G1 健壮性**：`stores/rogueRun` 新增 `isValidRogueNode/isValidSnapshot` 逐节点结构校验，`loadPersisted`/`isUsableSavedRun` 走深校验——损坏/半截存档（缺 `next`、`state` 非法等）直接丢弃回退新局，避免恢复即崩；`applySnapshot` 显式归零 `bossPhase`；清理死参 `startRun(opts.fresh)` 与死码 `resumeFromBackend`。
- **G2 局内自适应**：新增纯函数 `data/rogueConfig.computeRunDifficulty(base, momentum, recentFailures)`（气势 ≥3 +1、≥6 +2；最近两题皆错 -1；clamp 1..5），`beginChallenge` 据此出题、`scoreFor` 以实际难度计分，对齐计划 §4「连胜抬难度+得分系数」；页面 header 展示实际难度 + 「↑气势/↓回稳」标签。
- **G3 策略深度**：宝箱/精英通关改为 `reward` 阶段「三选一」学习增益（`openReward/chooseReward` + `sampleN`）；新增 2 个正向 relic `quick_thinker`（30s 内通关 +50% 分）、`first_aid`（本局首次答错不扣血，从持久化 history 派生→续局不重复触发）；`markCleared` 半程层首次到达小回 1 血。
- **G4 UX**：地图图例条、列表节点 `title` tooltip、结算页「本局练习小结」（按 KC 聚合 history 对错，弱项高亮）。
- **验证**：`npm run typecheck` / `eslint` 干净；`npm run test` **223 文件 2201 通过**（新增 computeRunDifficulty 4 + rogueRun 三选一/急救包/急智/半程回血/坏档拒绝/连胜抬难度 6 用例，并更新宝箱用例为三选一流）。
