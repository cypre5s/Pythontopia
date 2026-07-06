# R-15 主线完全重构 · 高并发实施计划

> 状态：已确认（2026-06-10，用户拍板）
> 决策记录：后端权威 + 旧 mainQuest 存档作废重开；关卡地图 = 独立 Phaser 像素场景；
> 章末大测试 = 画像驱动预生成入库、考试期间零 AI 调用；星级公式 = 下文 §5（已确认）；
> 允许新增 Flyway 迁移并重灌本地库；NPC 接入 memory-aware 个性化。
> 范围：Web 端 + backend + pythontopia-graph。Android/Windows/微信端本期不动，
> 仅更新 `docs/clients/api-contract.md` 契约（各端自行跟进）。

---

## 1. 目标（用户需求 → 系统能力）

| # | 需求 | 落点 |
|---|------|------|
| 1 | 独立地图显示闯关程度 | 新 Phaser「关卡地图」场景（星露谷风）：7 章节点 + 星星牌匾 + 小章节支线点 |
| 2 | 无尽模式 | 保留现有 EndlessPage 机制，改白色主题（练习场 tokens 配色），接入补星 |
| 3 | 大章节按掌握度给星（3★） | `chapter_star` 后端权威 + 星级公式 §5 + 补星途径（错题复盘/无尽挑战） |
| 4 | 小章节完成度 = 视频时长 + 课件 + 做题（例题可用 AI 导学） | `mainline_stage` + `mainline_stage_progress`，三件套打点 |
| 5 | 章末无 AI 大测试（选/判/填/编程，画像驱动 AI 生成） | 预生成入库 `chapter_test`，考试期间零 AI 调用 + 防作弊锁 |
| 6 | 完全重构主线 | 废弃前端权威 `mainQuests.ts`/`mainQuest` store，新建后端权威 mainline 域 |
| 7 | 教师看板联动 | 看板新增：星级分布 / 章测通过率与分布 / 无尽活跃度 / 成员主线进度 |

不变约束：单一 `town_hub`（R-14 方案 A）、packId=43（7 章 / 65 KC / 39 题）、
Pyodide 仅预览、正式判题入库、防幻觉证据审查、不做无教育目的装饰。

---

## 2. 现状基线（勘探结论，引用为准）

- 旧主线：`frontend/src/data/mainQuests.ts`（35 子任务 + 7 Boss），进度存
  `save_slot.snapshot.mainQuest`（JSONB），无后端表/接口 → 教师端无法聚合。
- 服务端判题仅编程题（`practice_submission`/`judge_result`，verdict AC/WA/RE/TLE/CE/MLE）。
  **不存在**选择/判断/填空的服务端题型。
- 预生成基建已存在：`V150__pregenerated_problem.sql` + `EndlessPregenService`（boss/无尽题预生成）。
- 掌握度：`kc_mastery`（0..1）+ `kc_mastery_event`，`MasteryEvaluator` 唯一写入方。
- 错题：Bug Mine errorbook（`bug_mine_session.depth_layer` L1–L5）。
- 无尽模式：`EndlessPage.vue`（深蓝主题）+ `useEndlessRun` + graph PracticeAgent 生成卡片。
- 教师看板：7 个 Tab，JDBC 聚合（`ClassroomDashboardRepository`），CSS/SVG 手绘图表。
- NPC：`npc_memory`/`npc_persona` + `MemoryRetrievalService` + memory-aware `NpcDialogAgent`
  已存在，但游戏内脚本对话未接；感叹号一次性绘制 bug 已在 Wave 0 修复。

---

## 3. 领域模型与课程映射

### 3.1 章节结构（packId=43）

- **大章节** = `course_chapter` 7 章（ch1 KC001–007 … ch7 KC054–065，对齐 `chapters.ts`）。
- **小章节（stage）** = 章内 KC 分组，每章 3–5 个，共 **24 个 stage**（seed 时按 KC 主题分组，
  覆盖全部 65 KC；分组写进迁移 seed，评审时人工核对）。
- 每个 stage 的「三件套」：
  - **看**：站内嵌入 B 站播放器（`player.bilibili.com` iframe）按可见性计时，
    达到 `required_video_sec`（默认 240s，可被 stage 覆盖）即过线；视频源 = stage 的
    `video_keyword` 实时检索的精选短视频（≤10min）。
  - **学**：读完 `courseware_page_ids` 里的课件页（复用图书馆阅读打点）。
  - **练**：完成 `required_example_count` 道例题（来自 `course_example` + 该 stage KC 的
    `course_problem`；**允许** AI 导学助手陪练）。

### 3.2 新表（Flyway V156 起，snake_case，无前缀沿用项目约定）

```sql
-- V156__mainline_core.sql
CREATE TABLE mainline_stage (            -- 静态定义（seed），全局共享
  id BIGSERIAL PRIMARY KEY,
  chapter INT NOT NULL CHECK (chapter BETWEEN 1 AND 7),
  stage_index INT NOT NULL,              -- 章内序号 1..5
  title VARCHAR(128) NOT NULL,
  kc_codes TEXT[] NOT NULL,
  video_keyword VARCHAR(120) NOT NULL,   -- B站检索词（Python 前缀）
  courseware_page_ids BIGINT[] NOT NULL DEFAULT '{}',
  example_problem_ids BIGINT[] NOT NULL DEFAULT '{}',
  required_video_sec INT NOT NULL DEFAULT 240,
  required_pages INT NOT NULL DEFAULT 0,        -- 0=按 courseware_page_ids 全读
  required_example_count INT NOT NULL DEFAULT 2,
  UNIQUE (chapter, stage_index)
);

CREATE TABLE mainline_stage_progress (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  stage_id BIGINT NOT NULL REFERENCES mainline_stage(id),
  video_watched_sec INT NOT NULL DEFAULT 0,
  pages_read BIGINT[] NOT NULL DEFAULT '{}',
  examples_done BIGINT[] NOT NULL DEFAULT '{}',
  completed_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, stage_id)
);

CREATE TABLE chapter_star (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  chapter INT NOT NULL CHECK (chapter BETWEEN 1 AND 7),
  stars SMALLINT NOT NULL DEFAULT 0 CHECK (stars BETWEEN 0 AND 3),
  best_test_score INT,
  breakdown JSONB NOT NULL DEFAULT '{}',  -- {test:1,stage:1,mastery:0,recovered:["ENDLESS"]}
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, chapter)
);

CREATE TABLE star_award_event (          -- 审计 + 教师端聚合
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  chapter INT NOT NULL,
  delta SMALLINT NOT NULL,
  source VARCHAR(24) NOT NULL CHECK (source IN
    ('TEST','STAGE_COMPLETE','MASTERY','ERRORBOOK_RECOVERY','ENDLESS_RECOVERY','ADMIN')),
  reason VARCHAR(256),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- V157__chapter_test.sql
CREATE TABLE chapter_test (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  chapter INT NOT NULL CHECK (chapter BETWEEN 1 AND 7),
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN
    ('PENDING','GENERATING','READY','IN_PROGRESS','SUBMITTED','EXPIRED','FAILED')),
  attempt_no INT NOT NULL DEFAULT 1,
  questions JSONB,                  -- 服务端持有完整题面+答案+解析+证据页id（绝不下发答案）
  profile_snapshot JSONB,           -- 生成时的画像摘要（可追溯个性化依据）
  generator_meta JSONB,             -- run_id/agent/grounding/safety 结果（防幻觉审计）
  time_limit_sec INT NOT NULL DEFAULT 1800,
  started_at TIMESTAMPTZ, deadline_at TIMESTAMPTZ, submitted_at TIMESTAMPTZ,
  score INT, detail JSONB,          -- 每题判分明细
  integrity JSONB NOT NULL DEFAULT '{}',  -- {paste_blocked:N, blur_events:N, overtime:bool}
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_chapter_test_user_ch ON chapter_test(user_id, chapter, created_at DESC);
```

题面 JSON 契约（`questions`，生成端 = graph，判分端 = backend）：

```jsonc
{ "items": [
  { "qid":"c1","type":"CHOICE","stem":"...","options":["A...","B...","C...","D..."],
    "answer":"B","explain":"...","kc_code":"PY43-KC-012","evidence_page_ids":[123],"score":8 },
  { "qid":"j1","type":"JUDGE","stem":"...","answer":true,"explain":"...","kc_code":"...","score":5 },
  { "qid":"f1","type":"FILL","stem":"... ____ ...","answers":["range","range()"],
    "match":"normalized","explain":"...","kc_code":"...","score":5 },
  { "qid":"p1","type":"CODING","title":"...","statement_markdown":"...",
    "samples":[{"input":"...","output":"..."}],
    "hidden_test_cases":[{"input":"...","output":"..."}],
    "reference_solution":"...","kc_code":"...","score":30 }
]}
```

构成与分值（默认）：选择×5×8 + 判断×3×5 + 填空×3×5 + 编程×1×30 = **100 分**，限时 30 分钟。

---

## 4. 章末大测试（无 AI）——生成、判分、防作弊

### 4.1 预生成（高并发关键路径）

1. 触发：学生完成本章**全部 stage** 时（或教师端手动触发），后端投递生成任务
   （`chapter_test` 置 `GENERATING`，`@Async` + 信号量节流，复用 `EndlessPregenService` 模式）。
2. graph `POST /agents/chapter-test/generate`：输入 = 章 KC 列表 + 8 维画像摘要 +
   errorbook 高频错因 + 例题/课件证据页；输出 = 上述题面 JSON。
   - 个性化：易错模式 → 针对性出题；知识基础弱 → 降低编程题复杂度（但覆盖相同 KC）。
   - 防幻觉：客观题必须带 `evidence_page_ids`（RAG 校验）；编程题跑参考解通过全部用例才入库
     （沙箱内自测，复用判题沙箱）。失败 → `FAILED` + 重试 ≤2 次 → 兜底 golden 题组（每章人工
     备一套，存 seed）。
3. 就绪：`READY`。考试 `start` 时若仍未就绪 → 同步生成（单次限流）或直接发 golden 兜底，
   **考试过程零 LLM 调用**。

### 4.2 考试会话与判分

- `start`：下发**去答案**题面 + `deadline_at = now()+time_limit`；同一章未提交的 IN_PROGRESS
  只允许一份。
- `submit`：服务端判分——CHOICE/JUDGE 精确比对；FILL 归一化比对（trim/全半角/大小写/候选集）；
  CODING 走现有 `JudgeCaseEvaluator` 沙箱跑 `hidden_test_cases`（按通过用例比例给分）。
  超时提交：按 `deadline_at` 拒收并记 `integrity.overtime`。
- 重考：不限次数，冷却 10 分钟，`attempt_no` 递增，重新预生成（避免背答案）；取**最高分**计星。

### 4.3 防 AI / 防作弊（尽力而为，明示限度）

- 考试页隐藏百晓工坊/AI 导学/Bug 矿洞入口；路由守卫禁止考试中跳转 AI 页面。
- 禁用粘贴（编程题输入框 onpaste 拦截 + 计数上报 `integrity.paste_blocked`）。
- `visibilitychange` 失焦计数上报（仅记录供教师参考，不自动判零）。
- 服务端时限硬校验；题面不含答案；解析仅在提交后返回。
- 明示：无法阻止学生用第二台设备求助——以「个性化题面 + 限时 + 失焦记录」降低收益。

---

## 5. 星级系统（已确认公式）

每章 0–3★，`chapter_star` 唯一权威，全部变更走 `star_award_event`：

- **1★**：章末大测试最高分 ≥ 60。
- **2★**：1★ 且本章**所有 stage 完成**（视频 + 课件 + 例题三件套全过线）。
- **3★**：2★ 且（本章 KC 平均掌握度 ≥ 0.80 **且** 大测试最高分 ≥ 85）。

补星（每章每途径至多 +1，封顶 3★，幂等）：

- **错题复盘补星**：本章 KC 的 bug-mine session `RESOLVED` ≥ 3 个 → +1（`ERRORBOOK_RECOVERY`）。
- **无尽挑战补星**：无尽模式「本章范围」单轮答对 ≥ 8 题且正确率 ≥ 80% → +1（`ENDLESS_RECOVERY`）。

星级重算时机：测试提交后 / stage 完成时 / 掌握度刷新事件 / 补星事件，由
`ChapterStarService.recalc(userId, chapter)` 统一收口（幂等、只升不降；breakdown 记录构成）。

---

## 6. 关卡地图（独立 Phaser 场景，星露谷风）

- 新场景 `ChapterMapScene`（`frontend/src/game/scenes/`），路由 `/mainline-map`，
  复用现有 Phaser 单例（`phaserGame.ts` + `WorldCanvas.vue` 模式，**不**新建 Game 实例）。
- 视觉：羊皮卷底 + 像素小路串联 7 个章节节点（已审计 LPC/CC0 tile 资产；新素材登记
  `LICENSE-ART.md` + `frontend/public/assets/pixel/LICENSE.md`）。
- 节点状态：锁定（灰）/ 进行中（高亮 + 玩家头像站立）/ 已通关（星星牌匾 0–3★）。
  节点展开显示本章 stage 列表（三件套进度条）+「进入学习 / 参加大测试 / 去补星」按钮。
- 入口：town_hub 新「冒险地图」告示牌 + HUD 按钮；地图数据 = `GET /api/mainline/overview` 一次拉取。
- 无尽模式入口与「补星」徽章也在地图上呈现（第 N 章缺星 → 显示补星途径引导）。

---

## 7. 无尽模式（白色主题 + 补星）

- 机制保留 `useEndlessRun`（AI 生成卡片 + Pyodide 预检 + graph 评估）。
- **主题**：深蓝 → 白色/羊皮纸亮色，全面对齐练习场 token（`--pyt-color-bg-primary #fef7e3`、
  边框 `#c9a86b`、文字 `#4d3613`；代码/终端块保留深色例外）。范围：`EndlessPage.vue` +
  `components/arena/*.vue` 卡片。
- 轮次结束上报 `POST /api/mainline/endless/summary {chapter, correct, total, streak}` →
  后端校验后触发 `ENDLESS_RECOVERY` 补星判定（防刷：每日每章至多记 1 次有效补星判定）。

---

## 8. NPC 记忆个性化（主页 NPC）

- 打开 NPC 对话时并行请求 `GET /api/npc/{key}/greeting`（新端点）→ 后端组装
  `MemoryRetrievalService` 摘要 + 画像 brief → graph `NpcDialogAgent` 生成 1–2 句
  个性化寒暄（如「上次 for 循环错了 3 次，今天要不要去矿洞复盘？」），插入脚本对话首节点之前。
- 失败语义（遵守 fail-fast 规则）：agent 失败 → 对话框显示「（记忆服务暂不可用）」+ 走脚本对话，
  不伪造个性化内容。
- 缓存：每 NPC×学生 30 分钟内复用上次 greeting（`npc_memory` 已有基建）。
- 感叹号修复（Wave 0 已完成）：quest marker 改为响应式重绘 + 完成即清除。

---

## 9. REST API 契约（新增，全部 JWT + STUDENT）

```
GET  /api/mainline/overview                      → MainlineOverviewDto
     { totalStars, chapters:[{ chapter,title,unlocked,stars,bestTestScore,
       stages:[{ id,index,title,videoWatchedSec,requiredVideoSec,
                 pagesRead,requiredPages,examplesDone,requiredExamples,completed }],
       testStatus, starBreakdown, recovery:{errorbookDone,endlessDone} }] }
POST /api/mainline/stages/{stageId}/video-progress { deltaSec }        → StageProgressDto
POST /api/mainline/stages/{stageId}/page-read      { pageId }          → StageProgressDto
POST /api/mainline/stages/{stageId}/example-done   { problemId, submissionId? } → StageProgressDto
POST /api/mainline/chapters/{ch}/test/prepare                          → { status }      # 触发预生成
POST /api/mainline/chapters/{ch}/test/start                            → ChapterTestPaperDto(无答案)
POST /api/mainline/chapters/{ch}/test/{testId}/submit { answers, integrity } → ChapterTestResultDto
GET  /api/mainline/chapters/{ch}/test/latest                           → ChapterTestResultDto?
POST /api/mainline/endless/summary { chapter, correct, total, streak } → { starAwarded }
POST /api/mainline/chapters/{ch}/recovery/errorbook                    → { starAwarded } # 校验后发放

教师端（TEACHER/ADMIN + ClassroomGuard）：
GET /api/teacher/classrooms/{id}/dashboard/mainline-stars     → 每章 0/1/2/3★人数矩阵 + 总星 TopN
GET /api/teacher/classrooms/{id}/dashboard/chapter-tests      → 每章参试数/通过率/分数分布(箱线)
GET /api/teacher/classrooms/{id}/dashboard/endless-activity   → 7日无尽轮次/正确率/补星数
GET /api/teacher/classrooms/{id}/dashboard/mainline-progress  → 成员×章节 stage 完成度矩阵
```

校验要点：`video-progress` deltaSec ≤ 真实流逝时间（服务端节流，单次 ≤120s、每分钟 ≤2 次）；
`example-done` 若带 submissionId 必须 PASSED 且属本人；所有写接口幂等。

---

## 10. graph 智能体（services/pythontopia-graph）

- 新增 `routes/chapter_test.py`：`POST /agents/chapter-test/generate`
  （输入 user_id/chapter/kc_codes/profile_brief/errorbook_brief/counts；输出题面 JSON）。
  节点链：`paper_designer → grounding_critic(客观题证据页校验) → sandbox_self_test(编程题) →
  safety_critic`；任一环节失败 → 结构化 502（backend 落 `FAILED` 并重试/兜底）。
- 复用：`ResourceDesignerAgent`/`PracticeAgent` 的出题基建、RAG 证据检索、防幻觉三审。
- 产物审计：保存 run_id、agent、input summary、output、evidence_page_ids、safety/grounding
  结果（写 `chapter_test.generator_meta`，符合 AGENTS.md 资源生成审计要求）。
- 教师审查：生成的章测题进入现有 `TeacherReviewPage` 审查流（抽样）。

---

## 11. 运行时高并发设计

1. **预生成解耦**：章测生成是唯一重 LLM 路径 → 全部走异步预生成队列（DB 状态机
   PENDING→GENERATING→READY/FAILED + `@Scheduled` 补偿扫描），考试 start 永不等 LLM。
2. **节流**：backend→graph 信号量（默认并发 2，配置 `pythontopia.mainline.pregen-concurrency`）；
   graph 单 worker 不变（项目约束），靠队列削峰。
3. **判分本地化**：客观题判分纯内存；编程题走 `JudgeWorkerPool`（已有并发上限）。
4. **overview 缓存**：`/api/mainline/overview` 单查询聚合（CTE 一次取齐），avoid N+1；
   教师端聚合走快照表模式（复用 `classroom_kc_snapshot` 思路，新增 5 分钟 TTL 内存缓存）。
5. **打点写入**：video-progress 高频 → 单行 UPSERT + 服务端限频；前端 10s 批量上报一次。
6. **无尽模式**：题面继续用 `pregenerated_problem` 预生成池，运行时只取不生成。

---

## 12. 并行实施批次（开发高并发：子代理分工与文件所有权）

> 原则：每个子代理有**独占文件清单**；共享文件（`router.ts`、`AppLayout` 导航、
> `tokens.css`、`api-contract.md`、`AGENTS.md`）只由主代理在集成段修改。
> 每批结束跑对应验证（§13）后才进入下一批。

### Wave 0（本回合，已执行）
- W0-1 恢复 MarvisPanel（多智能工作室）面板；W0-2 修复 B 站搜索链路（代理注入 + 渐进放宽 +
  错误可见化）；W0-3 修复 NPC 感叹号；W0-4 工坊 ASCII/SVG 图支持；W0-5 无尽模式白色主题。

### Wave 1（5 个子代理并行）
| 代理 | 任务 | 独占文件 | 验收 |
|---|---|---|---|
| A1 backend-core | V156/V157 迁移 + `com.pythontopia.mainline` 域（entity/repo/service/controller，stage seed 24 组）+ 单测 | `backend/.../mainline/**`，`db/migration/V156*,V157*` | `mvn test` 新增用例绿；空库可初始化 |
| A2 graph-paper | `/agents/chapter-test/generate` 全链 + golden 兜底 + pytest | `services/pythontopia-graph/.../chapter_test*`，`agents/paper_*` | `python -m pytest -q` 绿；dry-run 出合规 JSON |
| A3 fe-map | `ChapterMapScene` + `stores/mainline.ts` + `api/mainline.ts` + 组件 | `frontend/src/game/scenes/ChapterMapScene.ts`，`components/mainline/**`，`stores/mainline.ts`，`api/mainline.ts`，`pages/MainlineMapPage.vue` | vitest 组件测试绿；mock overview 可渲染 |
| A4 fe-exam | `ChapterTestPage` 四题型 UI + 防作弊锁 + `api/chapterTest.ts` | `frontend/src/pages/ChapterTestPage.vue`，`components/chaptertest/**`，`api/chapterTest.ts` | vitest 绿；倒计时/禁粘贴/提交流可测 |
| A5 fe-stage | stage 学习页（站内 B 站播放计时 + 课件打点 + 例题完成）| `frontend/src/pages/StageLearnPage.vue`，`components/mainline-stage/**` | vitest 绿；三件套打点调用正确 |

### Wave 2（4 个子代理并行，依赖 Wave 1 落库）
| 代理 | 任务 | 独占文件 |
|---|---|---|
| B1 backend-star | `ChapterStarService` 重算 + 补星端点 + 无尽 summary 校验 + 预生成队列接 graph | `backend/.../mainline/star/**`，`.../mainline/test/**` |
| B2 fe-rewire | 旧主线退役：删 `mainQuests.ts`/`MainQuestPanel`/`mainQuest` store，WorldScene 标记/引导切到 `stores/mainline`，HUD 改星星条 | `frontend/src/data/mainQuests.ts`(删)，`stores/mainQuest.ts`(删)，`components/hud/**`，`utils/questGuidance.ts`，`game/scenes/WorldScene.ts` |
| B3 teacher-dash | 4 个看板新端点（JDBC 聚合）+ 前端 4 个新 Tab | `backend/.../classroom/dashboard/**`，`frontend/src/components/teacher/**`，`api/teacher.ts` |
| B4 npc-memory | `/api/npc/{key}/greeting` + 对话框首句个性化 + 30min 缓存 | `backend/.../npc/**`，`frontend/src/stores/dialog.ts`，`components/dialog/**` |

### Wave 3（主代理集成 + 全量验证）
- 注册路由/导航/告示牌入口；`docs/clients/api-contract.md` 增补；删除残留死代码；
- 全量验证：`npm run typecheck && npm run lint && npm run test`、`mvn test`、两个 Python 服务
  `pytest -q`、空库 Flyway 初始化 + seed 校验（7 文档/561 页/7 章/65 KC/46 例/39 题）、
  演示链路冒烟（注册→stage 三件套→预生成→考试→星级→地图→无尽补星→教师看板）。

预估规模：后端 ~2.5k 行、前端 ~4k 行、graph ~1k 行、迁移 2 个、新增测试 ~60 个。

---

## 13. 验证矩阵

| 改动 | 验证 |
|---|---|
| 迁移 | 空库 `docker compose` 初始化 + seed 计数断言 |
| 判分 | 三类提交（通过/失败/运行错误）+ 客观题边界（全对/部分/超时拒收） |
| 预生成 | graph 宕机 → FAILED → golden 兜底可考试；并发 10 学生同时 prepare 不超信号量 |
| 星级 | 公式 8 分支单测（含补星幂等、只升不降） |
| 地图 | overview 大payload 渲染 < 100ms；锁定/解锁/星级三态截图比对 |
| 防作弊 | 超时提交拒收；粘贴拦截计数；考试中路由守卫 |
| 教师端 | 50 人班聚合查询 < 300ms（EXPLAIN 验证索引） |
| RAG/引用 | 章测客观题 evidence_page_ids 命中课件页（抽查 input函数/for循环/列表） |

---

## 14. 风险与回滚

| 风险 | 缓解 |
|---|---|
| 章测生成质量不稳 | 三审 + 沙箱自测 + golden 兜底 + 教师审查抽样；失败 fail-fast 不发烂题 |
| B 站 iframe 计时可被挂机刷 | 服务端限频 + visibility 才计时 + 上限封顶；星级仍以测试为锚 |
| 旧存档作废引发演示断档 | 演示账号脚本一键灌新进度（seed 脚本提供 demo 数据） |
| WorldScene 改动回归 | B2 仅改标记/引导读数来源，保留场景测试；Wave 3 冒烟覆盖 |
| 多端契约漂移 | 本期仅 Web；contract 文档同步标注 `since R-15`，其他端按文档跟进 |

回滚：mainline 全部为**新增**表/端点/页面，旧路径在 Wave 2 才删除——Wave 1 期间可随时中止；
Wave 2 删除以单独 commit 提交，可 revert。
