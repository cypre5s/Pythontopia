# 2026-06-11 游戏闯关主线巡检报告（WP7）

> 巡检范围：R-15 主线三件套（mainline stage / 章末大测试 / 星级）、闯关塔 7×10、无尽/错题补星、
> town_hub 入口与引导、深链路由、主线进度持久化。
> 验证手段：`npm run typecheck`、vitest 主线相关 21 个 spec、`mvn test`（mainline/quest/stage 相关 156 用例）。
> 并行禁区（workshop / intent / tutor / learner.profile / pythontopia-graph）只读核对，未改动。

---

## ① 结论：主线修复后可从头走到尾

学生视角主链路逐环判定（**括号内为本次巡检前的状态**）：

| 环节 | 判定 | 说明 |
|---|---|---|
| 注册 → /world → 「冒险地图」告示牌(22,20)/HUD 入口 → /mainline-map | ✅ | WorldScene 持久「!」标记 + trigger 注册齐全，路由存在 |
| overview 拉取 / 第 1 章解锁 / 章节抽屉 | ✅（⚠️ 缓存过期） | 解锁判定后端权威（前章 ≥1★），前端锁定文案与之一致；学习页打点后返回地图曾显示旧进度，已改 force 刷新 |
| 看：站内视频计时打点 | ✅（⚠️ 计时 3 倍慢） | 前端 10s/批 × 后端每分钟仅 2 次有效 ⇒ 服务端只记 1/3 时长、刷新即回退；已改 30s/批对齐 |
| 学：整章 PPT 阅读 + 批量 pages-read | ✅ | 翻到末页才可标记，幂等并集 |
| 练：客观题（选/判/填）+ OJ 例题 | ✅ | 题面/判分/打点/完成重算（recheckCompletion）闭合；CHOICE 提交选项原文与题库 answers 形态一致；V159 已按可用 OJ 回填 required_example_count（0/1），无不可达成要求 |
| stage 完成 → 星级重算 | ✅（⚠️ 假完成显示） | 后端 markCompleted + recalc 幂等；前端 `isStageComplete` 本地推导忽略「客观题」维度会显示假 ✓，已改为只信后端 `completed` |
| 测：章末大测试 prepare→start→submit | ✅（❌ **此前必挂**） | **前端提交体与后端契约断裂（数组 vs Map、integrity 字段命名）→ 交卷 100% 400 拒收，1★ 永远拿不到、第 2 章永远锁死**；已在前端 API 层修复（详见 ③-1） |
| 1★ → 下一章解锁 → … → 7 章 | ✅ | 60 分 = 1★；overview `prevStars >= 1` 逐章放行 |
| 补星：错题复盘 / 无尽挑战 | ✅（❌ 无尽断链） | 错题复盘端点+按钮闭合；**无尽小结上报无任何调用方，ENDLESS_RECOVERY 玩法上不可达**，已接通（③-3） |
| 闯关塔 70 关解锁/判分 | ✅（⚠️ 第 10 关断链） | 1–9 关经 /endless 回写 markCleared 闭合；**章 BOSS 通关从不回写 `{ch}-10`，每塔永远 9/10**，已修（③-4） |
| 毕业典礼（7 章 ≥1★） | ✅ | GraduationPage 经 mainline overview 校验，URL 直入有守卫 |
| 主线进度存档 | ✅ | R-15 起 mainline 进度全部后端按 user 独立表存储；save_slot.snapshot.mainQuest 已退役且不再读写，旧快照字段被自然忽略，无覆盖缺口。闯关塔进度仅 localStorage（见 ②-R7，计划内取舍） |
| 章测防作弊（路由守卫/禁粘贴/失焦计数） | ✅ | isExamActive 全局守卫 + 组件内守卫 + onpaste 拦截链路完整 |
| 章测预生成（backend ↔ graph） | ✅（只读核对） | `POST /agents/chapter-test/generate` 两端路径一致；FAILED → golden 兜底可考试 |

**总判定：修复前主线在「章末大测试交卷」处硬断（无法获得任何 1★，第 2 章起全部锁死）；
修复后全链可通，无尽补星与闯关塔第 10 关两条支线断链同步闭合。**

---

## ② 断点 / 风险清单（按严重度排序）

### 已修（详见 ③）

| # | 严重度 | 问题 | 位置 | 复现路径 |
|---|---|---|---|---|
| B1 | **P0 主线硬断** | 章测 submit 线格式断裂：前端发 `answers: [{qid,type,choice/blanks/...}]` 数组 + snake_case integrity；后端 `ChapterTestSubmitRequest` 要求 `Map<String,String>` + camelCase `IntegrityReport` ⇒ Jackson 反序列化失败，交卷恒 400。同时结果 DTO 字段名（`awardedScore/correctAnswer/stars/pasteBlocked`）与前端（`awarded/correctChoice/chapterStars/paste_blocked`）不一致，即便能交卷成绩页也渲染空 | `frontend/src/api/chapterTest.ts:123-132`（旧）vs `backend/.../mainline/dto/ChapterTestSubmitRequest.java:12-15`、`ChapterTestResultDto.java:12-39` | /chapter-test/1 → 开始考试 → 交卷 → 400「请求参数错误」 |
| B2 | **P1 进度损耗** | 视频打点限频错配：前端 10s/批（6 次/分钟）× 后端 `VideoProgressThrottle` 每 (user,stage) 每分钟仅 2 次有效且超频**静默 no-op 不报错**，前端把被丢弃的批当成功清零 pending ⇒ 服务端时长按 1/3 速累计，刷新后进度大幅回退，「看」过线时间×3 | `frontend/src/components/mainline-stage/StageVideoWatcher.vue:40`（旧 BATCH_SECONDS=10）vs `backend/.../mainline/VideoProgressThrottle.java:35-49` | 连续观看 60s → 刷新学习页 → 服务端只记 ~20s |
| B3 | **P1 玩法不可达** | 无尽补星断链：后端 `POST /api/mainline/endless/summary` 与 store `submitEndlessSummary` 均就绪，但**全前端无任何调用方**；地图「无尽挑战补星」按钮只是跳 /endless，打满也不会触发 ENDLESS_RECOVERY | `frontend/src/composables/useEndlessRun.ts`（旧无统计）、`frontend/src/pages/EndlessPage.vue`（旧无上报） | /endless?chapter=1 答对 ≥8 题（≥80%）→ 永远不加星 |
| B4 | **P2 支线断链** | 闯关塔第 10 关（章 BOSS）通关后 `BossPage` 只跳转，不回写 `arena.markCleared('{ch}-10')` ⇒ 每章塔永远 9/10，`totalCleared` 封顶 63/70 | `frontend/src/pages/BossPage.vue:93-103`（旧） | /arena/1 通关 1-9 → 第 10 关 BOSS 全胜 → 回塔仍显示未通关 |
| B5 | **P2 假完成显示** | `isStageComplete` 本地推导（video+pages+examples 三计数）忽略 overview 不下发的「客观题全对」维度 ⇒ 地图显示小节 ✓/「小节进度 n/n」，但后端 `completed=false`、2★ 永不发，学生无从排查 | `frontend/src/stores/mainline.ts:45-52`（旧） | 三件套计数达标但客观题没做 → 地图显示已完成、考满分也只有 1★ |
| B6 | **P2 数据过期** | 学习页三件套打点组件直连 API（不经 mainline store），返回 /mainline-map 时 `load()` 命中缓存不刷新 ⇒ 地图进度滞后刚完成的学习 | `frontend/src/pages/MainlineMapPage.vue:176`（旧 `store.load()`） | 学习页过线任意一件 → 返回冒险地图 → 进度条还是旧值 |

### 未修（记录为风险，不在本工作包动手）

> **2026-06-11 晚补记（主代理）**：R1-R5 已在巡检后的同日修复并通过三层全量回归
> （graph 521 / Java 658 / 前端 1674 全绿），实现方式见 `CHANGELOG.md` 当日条目：
> R1 按 pack 顺位动态解析文档 id（回退魔数+告警）；R2 无 submissionId 打点要求已有 PASSED
> 提交（400 `EXAMPLE_EVIDENCE_REQUIRED` + 前端 toast 引导）；R3 prepare 幂等轮询（3s×≤8 次）；
> R4 `IntegrityReport.overtime` 客户端自报并与服务端口径并集落库；R5 paper_designer FILL
> 单空 prompt 约束 + fail-fast 校验。R6 维持计划内取舍；R7 已随百晓工坊 WP5 解决。

| # | 严重度 | 风险 | 位置 | 说明 |
|---|---|---|---|---|
| R1 | P3 | `COURSE_DOC_BASE = 185` 硬编码假设课件文档 id 恰为 186..192；重灌库/seed 顺序漂移会让「学」维度（整章页集合）取错文档甚至取空 ⇒ 「学」环节卡死或误过线 | `backend/.../mainline/MainlineService.java:66` | 建议改为按 packId+chapter 查询 course_document；当前 seed 下验证为正确映射 |
| R2 | P3 | 「练-OJ」`标记完成` 不带 submissionId 时后端不校验（submissionId 可选）⇒ 学生可不判题直接打点完成 OJ 维度 | `frontend/.../StageExamplePractice.vue:86`、`backend/.../MainlineService.java:263-283` | 与「任务完成必须提供 evidence」原则冲突，但收紧会影响 UX（训练场回跳目前不带 submissionId），需产品决策：建议训练场判题通过后回传 submissionId 自动打点 |
| R3 | P3 | 章测 prepare 后无轮询：GENERATING 期间学生点「开始考试」会直接拿 golden 兜底卷，错过画像个性化卷（不卡死，但弱化个性化卖点） | `frontend/.../useChapterTestExam.ts:181-191` | 建议 prepare 后轮询 status 或 start 前 re-check 一次 |
| R4 | P3 | 客户端 `integrity.overtime` 标志不上送（后端 `IntegrityReport` 无该字段，仅在拒收超时提交时服务端记 overtime）⇒ 「踩点自动交卷」在教师端不可见 | `backend/.../ChapterTestSubmitRequest.java:15` | 需后端加字段，跨模块小改，列入后续 |
| R5 | P4 | FILL 多空题语义：后端 `judgeFill` 把整串归一化与候选集比对（单空语义），前端按 `__{2,}` 切多空 join 上送；golden 卷全部单空 ✓，需约束 graph `paper_designer` 不产多空填空 | `backend/.../ChapterTestJudge.java:89-101`、graph `paper_designer.py`（禁区） | 禁区根因，只记录：生成端 prompt/校验应强制单空或后端支持逐空比对 |
| R6 | P4 | 闯关塔进度仅 localStorage（换设备/清缓存丢失、教师端不可见）——R-15 蓝图聚焦 mainline 后端权威，塔为支线，属计划内取舍 | `frontend/src/stores/arena.ts` | 若后续要纳入教师看板需建表 |
| R7 | — | `npm run typecheck` 当前唯一报错在并行工作包禁区文件 `frontend/src/components/workshop/WorkshopChat.vue:391`（`FIX_BUG` 不在 `WorkshopResourceType`），与主线无关，本工作包基线开始时尚不存在，应由百晓工坊工作包修复 | 禁区 | 已按约定不触碰 |

---

## ③ 本次已修项（全部 ≤2 文件/项 + 对应测试）

1. **章测 submit/结果契约适配层（B1）** — `frontend/src/api/chapterTest.ts`
   - `submitTest`：判别联合答案数组 → 后端 `qid→string` Map（CHOICE 字母 / JUDGE "true|false" / FILL blanks join / CODING 源码，新增导出 `serializeAnswer`）；integrity → camelCase `{pasteBlocked, blurEvents}`。
   - `startTest`/`submitTest`/`getLatestTest`：新增后端线格式（`PaperWire`/`ResultWire`）→ 前端 DTO 映射（`awardedScore→awarded`、`maxScore→score`、`stars→chapterStars`、`correctAnswer` 按题型回填 `correctChoice/correctJudgement/acceptedAnswers/referenceSolution`、`passedCases/totalCases→passedTestCases/totalTestCases`、integrity camel→snake 镜像、BIGSERIAL testId → string）。
   - `ChapterTestPaperDto.startedAt/totalScore` 改 optional（后端 start 响应不下发）。
   - 测试：重写 `tests/api/chapterTest.spec.ts`（9 用例，fixture 改为后端真实线格式，断言序列化与映射）。
2. **视频打点批间隔对齐限频（B2）** — `frontend/src/components/mainline-stage/StageVideoWatcher.vue`：`BATCH_SECONDS` 10→30（=每分钟 2 批，与服务端 2 次/分钟有效配额精确对齐），注释说明静默 no-op 风险；更新 `StageVideoWatcher.spec.ts` 两个批量用例。
3. **无尽补星上报接通（B3）** — `frontend/src/composables/useEndlessRun.ts` 新增本轮 `passedCount`（startRun 重置 / passed 自增）；`frontend/src/pages/EndlessPage.vue` 监听锁章模式下 答对≥8 且正确率≥80%（与后端 `MainlineRecoveryService` 常量一致）即调 `mainline.submitEndlessSummary` 上报（每轮一次、失败可重试、补星成功 toast；服务端仍重新校验+每日防刷）。
4. **章 BOSS 回写闯关塔（B4）** — `frontend/src/pages/BossPage.vue` victory 时 `arena.markCleared('{chapter}-10', elapsedSec)`；扩展 `BossPage.spec.ts` 通关用例断言塔进度。
5. **小节完成显示只信后端（B5）** — `frontend/src/stores/mainline.ts` `isStageComplete` 删除本地三计数兜底推导，仅返回服务端 `completed`；更新 `tests/stores/mainline.spec.ts`。
6. **地图强制刷新（B6）** — `frontend/src/pages/MainlineMapPage.vue` onMounted 改 `store.load(true)`（有缓存时不闪 loading，仅静默换新）。

**验证结果：**
- `npm run typecheck`：本工作包涉及文件 0 错误（仅剩禁区 `WorkshopChat.vue` 1 个并行工作包错误，见 ②-R7）。
- `npx eslint`（7 个改动源文件 + 4 个 spec）：0 错误。
- `npx vitest run`（主线相关 21 个 spec：mainline/stage/chaptertest/arena/boss/endless/map/store）：**151/151 通过**。
- `cd backend && mvn test -Dtest='*Mainline*,*Stage*,*ChapterTest*,*ChapterStar*,*StarFormula*,*Quest*,*ChapterCatalog*,*VideoProgress*'`：**156/156 通过**（本次未改后端，回归确认）。
- 未验证：浏览器端到端真机走查（未起 dev 栈，按工作包约束）；空库 Flyway 重灌（无迁移改动）。

---

## ④ 建议后续项

1. **R1 文档 id 解耦（优先）**：`MainlineService` 的 `185 + chapter` 改为 `course_document` 按 packId+chapter 查询，消除重灌库卡死隐患。
2. **R2 OJ evidence 收紧**：训练场判题 PASSED 后带 submissionId 回跳自动打点，撤掉裸「标记完成」按钮（或仅教师视角保留）。
3. **R3 章测就绪轮询**：闸门页对 GENERATING 状态做 5s 轮询至 READY，保住个性化卷的演示价值。
4. **R4 integrity.overtime 上送**：后端 `IntegrityReport` 增加 overtime 字段并入库，教师看板可见踩点交卷。
5. **R5 graph 填空单空约束**：`paper_designer` 出题校验禁止多空 FILL（或后端 judgeFill 支持逐空），需在 graph 工作包排期。
6. **演示前真机冒烟**：注册→stage 三件套→章测（含 golden 兜底路径）→1★ 解锁→无尽补星 toast→塔 10/10→毕业，覆盖本次修复的四条断链。
