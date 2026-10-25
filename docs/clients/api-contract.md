# Pythontopia 客户端集成契约（Client API Contract）

> 本文是 **四端客户端**（Web / Android / Windows / 微信）接入同一套 Spring Boot 后端的**唯一权威契约**。
> 任何客户端实现都以本文为准，不要凭空发明端点或字段。后端源在 `backend/`，本文随后端演进同步更新。

适用客户端：

- `frontend/`（Vue 3 + Phaser 3 Web）
- `clients/native/android/`（Kotlin + Compose + LibGDX）
- `clients/native/windows/`（.NET 8 WinUI 3 + MonoGame）
- `clients/wechat/miniprogram/` + `clients/wechat/minigame/`（原生微信小程序 / 小游戏 TS）

---

## 1. Base URL 与端口

后端单进程对外，所有业务路径统一前缀 `/api`。

| 客户端运行环境 | Base URL |
| --- | --- |
| Web（浏览器，本机） | `http://localhost:18080` |
| Windows 原生（本机） | `http://localhost:18080` |
| Android 模拟器（AVD） | `http://10.0.2.2:18080`（`10.0.2.2` = 模拟器回环到宿主机） |
| Android 真机 / 局域网 | `http://<后端局域网 IP>:18080` |
| 微信开发者工具 | `http://localhost:18080`（需勾选「不校验合法域名」）|
| 微信线上 | `https://<已备案 HTTPS 域名>`（须加入服务器域名白名单，见 §6）|

约定：

- 本文表格中的路径**已含 `/api` 前缀**，直接拼到 Base URL 之后即可（如 `POST {base}/api/auth/login`）。
- 请求/响应默认 `application/json; charset=utf-8`；例外：课件页图片为 `image/png`、视频为 `video/mp4`（支持 Range）、SSE 为 `text/event-stream`。
- 时间字段为 ISO-8601（`Instant` / `OffsetDateTime`）。

---

## 2. 认证（JWT Bearer）

无状态 JWT，不使用 Cookie/Session。除少数匿名端点外，所有请求都须带：

```
Authorization: Bearer <token>
```

### 2.1 登录 / 注册 / 续签

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | 已关闭 | 默认返回 `401`；学生账号由管理员导入名单创建 |
| POST | `/api/auth/login` | 匿名 | 登录 → `LoginResponse` |
| POST | `/api/auth/refresh` | 匿名（须带未过期 token） | 用**仍有效**的 token 换发新 token |

### 2.2 管理员学生名单导入

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/admin/student-imports` | ADMIN | `multipart/form-data`，字段 `file`，首个工作表需包含“学号”和“姓名”列；成功返回新建账号的一次性凭据清单 |

仅接受 `.xlsx`。系统在首个工作表前 20 行按完整表头匹配学号列（`学号`、`学生学号`、`Student ID`、`studentid`、`student_id`）和姓名列（`姓名`、`学生姓名`、`Name`），忽略其他列且不要求固定列顺序；同类候选列多于一列时拒绝文件，避免误选。学号须为 8–64 位字母、数字、下划线、点或连字符；用户名为学号，密码为学号倒序，邮箱为空，角色为 `STUDENT`。已存在账号会跳过且不重置密码；文件内重复学号、缺列或无效行会拒绝整份文件，不写入部分账号。单次最多 1000 条学生记录。

`LoginResponse`：

```json
{
  "token": "<jwt>",
  "expiresAt": "2026-06-06T09:39:00Z",
  "user": { "id": 1, "username": "student1", "displayName": "学生", "role": "STUDENT" }
}
```

### 2.3 Token 生命周期（重要）

- **TTL = 12 小时**（后端 `ttlMinutes: 720`）。
- **没有真正的 refresh token**：`/api/auth/refresh` 把 access token 兼作续签源——**必须回传未过期的 token** 才能换新；一旦 token 过期，`refresh` 也会返回 `401`。
- **客户端策略**：收到 `401` 一律跳登录页重新登录，不要尝试静默刷新过期 token。可选地在 token 临近 `expiresAt` 时主动调一次 `refresh` 续命，但这不是必须。
- 角色 `role ∈ {STUDENT, TEACHER, ADMIN}`。**面向学生的学习端点绝大多数仅 `STUDENT` 可访问**（非学生角色命中返回 `403 FORBIDDEN`）。

---

## 3. 错误返回（统一信封）

所有错误（含鉴权失败、校验失败、业务异常）返回统一结构，HTTP 状态码与之对应：

```json
{
  "code": "FORBIDDEN",
  "message": "仅学生可访问公告板任务",
  "details": [],
  "traceId": "",
  "timestamp": "2026-06-05T13:39:00Z"
}
```

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `code` | string | 机器可读错误码（见下表） |
| `message` | string | 人类可读说明（可能中文） |
| `details` | string[] | 字段级校验明细（校验失败时非空） |
| `traceId` | string | 链路追踪 id（可能为空串） |
| `timestamp` | string | 服务端时间（ISO-8601） |

常见 `code` / 状态：`UNAUTHORIZED`(401)、`FORBIDDEN`(403)、`BAD_CREDENTIALS`(401)、`REFRESH_TOKEN_INVALID`(401)、`*_NOT_FOUND`(404)、`VALIDATION_ERROR`(400)、`BAD_REQUEST`(400)。客户端应基于 `code` 分支处理，基于 `message` 兜底展示。

**两个 400 的适用范围不同**（`GlobalExceptionHandler`）：

| `code` | 触发条件 | `details` |
| --- | --- | --- |
| `VALIDATION_ERROR` | 报文能解析，但字段级校验不过（`@Valid` body 的 `MethodArgumentNotValidException`、`@PathVariable`/`@RequestParam` 上的 `ConstraintViolationException`） | 非空，每项形如 `字段: 说明` |
| `BAD_REQUEST` | 报文本身不可解析 / 参数缺失或类型不符（JSON 语法错误、缺必填 query 参数、`IllegalArgumentException` 等） | 通常为空 |

客户端做字段级表单回填时应匹配 `VALIDATION_ERROR`（旧文档误写成 `BAD_REQUEST`）。

---

## 4. 客户端可见端点总览（CLIENT-FACING）

> 仅列出客户端直接消费的端点。教师后台 / 看板 / 内部运维端点不在此列。

### 4.1 进入小镇（聚合启动）

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/game/bootstrap` | Bearer | 登录后/章节重置后调用一次，聚合返回 world、player、profile、quests、mastery、learning path、skills、NPC 状态、当前存档槽、待处理日历事件 |

### 4.2 学习画像与掌握度

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/learner/profile` | Bearer | 当前用户画像；无画像时 `204 No Content` |
| GET | `/api/learner/profile/revisions?limit=10` | Bearer | 画像修订历史 |
| GET | `/api/learner/profile/dimensions` | Bearer | 8 维画像快照（雷达图 + 自评 UI 用） |
| PUT | `/api/learner/profile/dimensions/{dimensionKey}` | Bearer | 自评直写单维；学生仅可改 4 个主观维度，越权改客观维度 → `403` |
| POST | `/api/learner/profile/interview` | STUDENT | 创建或恢复当前学生的 OPEN 画像访谈 |
| POST | `/api/learner/profile/interview/{sessionId}/turn` | STUDENT | 同步 ProfileAgent 回合；当前 Web 画像屋使用此端点 |
| POST | `/api/learner/profile/interview/{sessionId}/turn/stream` | STUDENT | SSE ProfileAgent 回合；成功终态为 legacy `result` 或 canonical `RUN_FINISHED` |
| POST | `/api/learner/profile/interview/{sessionId}/finish` | STUDENT | 主动结束本人访谈 |
| GET | `/api/learner/mastery` | Bearer | KC 掌握度快照列表 |

`/api/learner/mastery` 的快照包含 `assessed`：只有正式练习、微测或调试证据存在时为 `true`；`confidence` 不是评估状态标志。

### 4.3 任务（公告板，仅学生）

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/quests/today` | STUDENT | 今日任务 |
| GET | `/api/quests?status=&source=&limit=30&offset=0` | STUDENT | 任务列表（可按状态/来源过滤） |
| POST | `/api/quests/{id}/start` | STUDENT | 领取/开始任务 |
| POST | `/api/quests/{id}/complete` | STUDENT | 完成任务（body 可选，需 evidence：submission/resource/page-read/mastery delta） |
| POST | `/api/quests/{id}/skip` | STUDENT | 跳过任务（body 可选） |

### 4.4 课程包与课件资产（匿名可读）

`/api/course/**` 全部匿名放行：课件浏览、证据页图片、视频 `<img>/<video>` 标签不会带 `Authorization`，故无需登录即可拉取。`kcCode` 形如 `PY43-KC-001`。

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/course/pack` | 匿名 | 课程包元信息（packId 43：文档/页/章/KC/示例/题 计数） |
| GET | `/api/course/chapters` | 匿名 | 章节列表 |
| GET | `/api/course/kcs?chapterId=` | 匿名 | KC 列表（可按章过滤） |
| GET | `/api/course/pages?kcCode=PY43-KC-001` | 匿名 | 某 KC 的课件页（含 markdown 正文/摘要） |
| GET | `/api/course/problems?kcCode=` | 匿名 | 题目列表（可按 KC 过滤） |
| GET | `/api/course/problems/{id}` | 匿名 | 题目详情（题面，不含隐藏用例） |
| GET | `/api/course/pages/{pageId}/image` | 匿名 | 课件页渲染为 PNG（证据图，`image/png`，缓存 24h） |
| GET | `/api/course/videos?chapterId=` | 匿名 | 任务点视频清单（当前内置课程为空数组 `[]`） |
| GET | `/api/course/videos/{id}` | 匿名 | 视频流式分发，**支持 HTTP Range**（`206 Partial Content` + `Content-Range` + `Accept-Ranges: bytes`，供 `<video>` 拖动；无 Range 回 `200` 全量，越界回 `416`） |

### 4.5 训练场题目（公开视图，仅学生）

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/problems/{id}/public` | STUDENT | 训练场公开题目视图（**隐藏测试用例不下发**），非学生 → `403` |

### 4.6 提交与判题（仅学生）

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/submissions` | STUDENT | 提交代码判题 → `SubmissionDto` |
| GET | `/api/submissions/{id}` | STUDENT | 提交详情（含判题结果），**轮询直到终态** |
| GET | `/api/submissions/{id}/performance` | STUDENT | 本人 AC 提交的同题同语言运行/内存百分位；低于 10 条有效 AC 提交时对应字段为 `null` |
| GET | `/api/submissions?problemId=&status=&limit=30&offset=0` | STUDENT | 提交历史列表 |

提交体 `SubmissionCreateRequest`：

```json
{ "problemId": 101, "code": "print(1)", "questId": 55, "source": "TRAINING" }
```

- `problemId` 必填、`code` 必填（≤ 8192 字符）；`questId`、`source` 可选。
- `source ∈ {TRAINING, PATH_QUEST, BUG_MINE_RETRY, BUG_MINE_ECHO, BUG_MINE_VARIANT, CLASSROOM}`。

**判题轮询模型**：`status` 由 `PENDING → RUNNING` 流转到终态之一 `{PASSED, FAILED, ERROR}`。终态后 `GET /api/submissions/{id}` 的 `judgeResult` 给出 verdict：

```json
{
  "submission": { "id": 9, "status": "FAILED", "runtimeMs": 12, "memoryKb": 5120, "...": "..." },
  "judgeResult": {
    "verdict": "WA", "passedCases": 2, "totalCases": 5,
    "failureReason": "输出与期望不一致", "trace": null, "createdAt": "2026-06-05T13:39:00Z"
  }
}
```

`verdict ∈ {AC, WA, RE, TLE, CE, MLE, SYSTEM_ERROR}`（AC=全部通过；WA=答案错误；RE=运行时错误；TLE=超时；CE=未过静态检查/超长/为空；MLE=超内存；SYSTEM_ERROR=判题系统异常）。轮询建议固定间隔（如 1s）+ 上限退避，命中终态即停。

`SubmissionPerformanceDto` 的运行时间与内存分别按 `problemId + language` 统计 PASSED 提交记录；同一用户的多次提交分别计数。每项只比较已返回该指标的 AC 提交，样本数达到 10 后才返回 `runtimeBeatsPercent` / `memoryBeatsPercent` 与对应 `*SampleCount`，未达门槛时该项两个字段均为 `null`。该接口仅允许读取本人提交，百分位不包含失败提交。

### 4.7 Bug 矿洞（仅学生）

Bug 矿洞是错题复习支线：已有失败提交可进入矿洞；遗忘回声则由后端用 **FSRS-lite 复习引擎**（`kc_review_state`，见 `docs/mastery_formula.md`）召回**当下到期**（`now > due_at`）的 KC，并写入真实 `practice_submission(source=BUG_MINE_ECHO)` 后生成同一套分层卡片。`decayRisk = 1 − R`（R 为可提取性），不再用全局 14 天半衰期。

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/bug-mine/errorbook` | STUDENT | 当前未关闭矿洞 session 列表，含 `minePriority`、`decayRisk`、`recommendationReason` |
| GET | `/api/bug-mine/echoes` | STUDENT | 遗忘回声候选 KC 列表（仅 FSRS 到期项，按入矿优先级降序） |
| POST | `/api/bug-mine/echoes` | STUDENT | body `{ "kcCode": "PY43-KC-016" }`，打开遗忘回声矿洞 session |
| POST | `/api/bug-mine/sessions` | STUDENT | body `{ "submissionId": 123 }`，由失败提交打开矿洞 |
| GET | `/api/bug-mine/sessions/{id}` | STUDENT | 矿洞 session 详情与卡片 |
| POST | `/api/bug-mine/sessions/{id}/cards/{cardId}/reveal` | STUDENT | 解锁当前深度允许的卡片 |
| POST | `/api/bug-mine/sessions/{id}/retry-submit` | STUDENT | 原题重试，body `{ "code": "..." }` |
| POST | `/api/bug-mine/sessions/{id}/variant-submit` | STUDENT | L5 同 KC 变式提交，body `{ "code": "..." }` |
| POST | `/api/bug-mine/sessions/{id}/close?status=ABANDONED` | STUDENT | 关闭 session；`status` 可为 `RESOLVED` / `ABANDONED` |

`BugMineEchoCandidateDto` 核心字段：

```json
{
  "kcCode": "PY43-KC-016",
  "problemId": 476,
  "problemTitle": "圆面积调试",
  "masteryScore": 0.72,
  "confidence": 0.25,
  "decayRisk": 0.86,
  "minePriority": 0.71,
  "recommendationReason": "遗忘回声 · 置信度低 · 建议入矿"
}
```

### 4.8 班级（学生侧）

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/classrooms/mine` | Bearer | 我加入/创建的班级 |
| POST | `/api/classrooms/join` | Bearer | 用 `joinCode` 加入班级（body `{ "joinCode": "ABC123" }`） |
| GET | `/api/classroom/quests/mine` | STUDENT | 我的班级任务列表 |

> 教师侧端点（创建班级、发布班级任务、KC 热力图/看板等）存在于后端，但属 Web 教师视角，不在客户端学习链路范围内。

---

### 4.9 主线冒险地图与章末测试（since R-15，仅学生）

> **R-15「主线完全重构」**：旧前端权威 `mainQuest` 存档（写 `save_slot.snapshot.mainQuest`）已**整体废弃**，
> 主线进度改为**后端权威 mainline 域**（按 `user_id` 独立表 `mainline_stage_progress` / `chapter_star` /
> `chapter_test`，迁移 V156/V157）。客户端只读 `overview` 渲染冒险地图，并通过下列写接口打点；星级公式、
> 解锁、补星判定全在后端。**客户端不应再实现** mainQuest 子任务 / 任务面板 / 背包 / 货币 / 商店。

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/mainline/overview` | STUDENT | 冒险地图一次拉齐 `MainlineOverviewDto`：`{ totalStars, chapters:[{ chapter,title,unlocked,stars,bestTestScore, stages:[{ id,index,title,videoWatchedSec,requiredVideoSec,pagesRead,requiredPages,examplesDone,requiredExamples,completed }], testStatus, starBreakdown, recovery }] }` |
| GET | `/api/mainline/stages/{stageId}` | STUDENT | 单个小节学习页详情（静态定义 + 当前用户三件套已完成 id 集合）→ `StageDetailDto` |
| POST | `/api/mainline/stages/{stageId}/video-progress` | STUDENT | 「看」打点，body `{ "deltaSec": <int> }`；`0` 仅建立计时基准，正增量按相邻服务端上报间隔限速、单次 ≤120s、每分钟 ≤2 次。它限制加速虚报，不证明视频实际播放或页面持续可见 → `StageProgressDto` |
| POST | `/api/mainline/stages/{stageId}/page-read` | STUDENT | 「学」打点，body `{ "pageId": <int> }`（幂等，重复同页只记一次）→ `StageProgressDto` |
| POST | `/api/mainline/stages/{stageId}/example-done` | STUDENT | 「练」打点，body `{ "problemId": <int>, "submissionId"?: <int> }`（带 submissionId 则须本人且 PASSED）→ `StageProgressDto` |
| POST | `/api/mainline/chapters/{chapter}/test/prepare` | STUDENT | 触发章末大测试**异步预生成**（画像驱动、考试期零 LLM 调用）→ `{ status }` |
| POST | `/api/mainline/chapters/{chapter}/test/start` | STUDENT | 开考：下发**去答案**题面 + 截止时间（同章未提交只允许一份）→ `ChapterTestPaperDto` |
| POST | `/api/mainline/chapters/{chapter}/test/{testId}/submit` | STUDENT | 交卷判分（选/判/填服务端比对，编程走判题沙箱跑隐藏用例），body `{ answers, integrity }` → `ChapterTestResultDto` |
| GET | `/api/mainline/chapters/{chapter}/test/latest` | STUDENT | 本章最近一次测试结果（解析仅提交后返回）→ `ChapterTestResultDto?` |
| POST | `/api/mainline/endless/summary` | STUDENT | 远征补星申请，body `{ chapter, runId }` → `{ starAwarded }`；后端只统计本人活动 run 中已发放且属于该章题目的正式判题记录，答对 ≥8 且正确率 ≥80% 才达标，每日每章至多发放 1 次 |
| POST | `/api/mainline/chapters/{chapter}/recovery/errorbook` | STUDENT | 申领错题复盘补星（校验本章 bug-mine RESOLVED ≥ 3，幂等）→ `{ starAwarded }` |

#### 无尽远征 / Boss（学生玩法）

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/world/boss/select-frontier` | Bearer | 服务端以掌握度和 learner profile 选择真实 KC 前沿；失败或空结果返回 `503 EXPEDITION_ADAPTATION_UNAVAILABLE`，客户端不得用本地默认 KC 冒充画像结果 |
| POST | `/api/world/boss/{chapter}/problem` | Bearer | 按 KC、题型、纵深难度取题；远征必须同时传服务端签发的 `expeditionRunId` 与 `expeditionNodeId`，题目只绑定当前 run 中可挑战的节点；远征客户端只接受 `aiGenerated=true && reviewed=true` |
| POST | `/api/world/boss/{chapter}/prefetch` | Bearer | 创建/复用同 `chapter + stage + kc + kind + arenaLevel + difficulty + expeditionRunId + expeditionNodeId` 的预加载任务，返回 `202` + `BossPrefetchStatusDto` |
| GET | `/api/world/boss/{chapter}/prefetch` | Bearer | 用 `preferredKc`、`preferredKind`、`arenaLevel`、`difficulty` 和可选 `stage`、`expeditionRunId`、`expeditionNodeId` 查询状态；省略 stage 兼容旧客户端并按其他维度查询 |
| POST | `/api/world/boss/{chapter}/attempt` | Bearer | body 必须包含 UUID `attemptId`；服务端按用户/ID/请求体幂等保存判题结果，同 ID 改内容返回 `409`。题目必须由服务端签发；公开样例的本地预览不能代替该接口 |
| GET | `/api/world/expedition/run` | STUDENT | 取本人活动存档；返回的 `score` 和 `verifiedDepth` 由服务端判题账本计算，空存档返回 `204` |
| POST | `/api/world/expedition/run` | STUDENT | body `{ focusChapter: 1..7|null, isDaily, preferredKcCode? }`；可选 `preferredKcCode` 只表达导航偏好，服务端先校验课程 KC 与先修条件，再将合格 KC 排在远征第一题；服务端生成地图并返回带 `runId` 的快照 |
| PUT | `/api/world/expedition/run` | STUDENT | 兼容旧客户端的校验入口；只校验活动 `runId`、章节和地图拓扑并返回服务端快照，不写入客户端提交的 phase、HP、节点状态、气势、遗物或历史，也不能创建新局 |
| POST | `/api/world/expedition/run/actions` | STUDENT | 执行单个服务端校验的远征动作并返回 canonical 快照。`action` 为 `ENTER_NODE`、`EVENT_REVIEW`、`EVENT_STUDY`、`EVENT_STEADY`、`CLAIM_RELIC`、`SETTLE_CHALLENGE`、`FINISH`；按动作传 `nodeId`、`relicId` 或 `problemId`。结算会核对该 run、题目、节点绑定及本人正式判题账本；客户端不提交 HP、分数或通关结果 |
| DELETE | `/api/world/expedition/run` | STUDENT | 弃局并结束本人活动存档 |

`BossPrefetchStatusDto = { jobId, status, attempts, errorMessage, retryable, updatedAt }`，其中
`status ∈ { IDLE, PENDING, READY, FAILED }`。只有审查通过且带隐藏测试的 AI 题可进入 `READY`；
`FAILED` 以相同维度再次 `POST` 会增加 attempts 并重试。题目正文与隐藏测试从不进入预加载状态响应。
远征题目预生成记录按 `runId + nodeId` 绑定；远征 SETTLE 只接受同一绑定下已入库的正式判题结果。同一题的结算请求可安全重试，重复题不会跨节点结算。

`attemptId` 是远征与 Boss 判题的必填字段。Web 会在同一次提交的重试期间复用 UUID；未更新请求 DTO 的 Android/Windows 客户端在调用该接口前需要补充此字段。

> **星级（`chapter_star` 权威，0–3★）**：1★ = 章末测试最高分 ≥ 60；2★ = 1★ 且本章全部 stage 三件套（看+学+练）过线；
> 3★ = 2★ 且（本章 KC 平均掌握度 ≥ 0.80 且测试最高分 ≥ 85）。补星每章每途径至多 +1、封顶 3★、幂等、只升不降。

#### NPC 记忆个性化寒暄（since R-15，仅学生）

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/npc/{npcKey}/greeting` | STUDENT | 打开 NPC 对话时拉取「记忆感知 + 画像」个性化寒暄（1–2 句，插入脚本对话首节点之前）→ `NpcGreetingResponse`；agent 失败时返回降级语义（不伪造个性化内容） |

#### 教师看板（since R-15，TEACHER/ADMIN + 班级归属校验；属 Web 教师视角，非学生客户端链路）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/teacher/classrooms/{classroomId}/dashboard/mainline-stars` | 每章 0/1/2/3★ 人数矩阵 + 总星 TopN |
| GET | `/api/teacher/classrooms/{classroomId}/dashboard/chapter-tests` | 每章参试数 / 通过率 / 分数分布 |
| GET | `/api/teacher/classrooms/{classroomId}/dashboard/endless-activity` | 近 7 日无尽轮次 / 正确率 / 补星数 |
| GET | `/api/teacher/classrooms/{classroomId}/dashboard/mainline-progress` | 成员 × 章节 stage 完成度矩阵 |

#### 管理题目审核（TEACHER/ADMIN）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/teacher/problem-reviews?limit=100` | 兼容列表，`limit` 1–200，返回公开题面与审核状态 |
| GET | `/api/teacher/problem-reviews/page?page=0&size=20&decision=PENDING&q=` | 搜索和分页；`page` 从 0 开始，`size` 1–100，`decision` 可选为 `PENDING` / `APPROVED` / `REJECTED`，`q` 最长 100 字，匹配编号、KC、章节、申请人和题面；返回 `{ content, page, size, totalElements, totalPages }` |
| POST | `/api/teacher/problem-reviews` | 提交候选题进入审核，返回公开审核 DTO |
| PATCH | `/api/teacher/problem-reviews/{reviewId}` | 保存审核决定、必填理由及乐观并发 `version` |

审核 DTO 不包含隐藏测试、参考答案或内部生成提示词。

---

## 5. SSE 流式端点（多智能体）

三个长流式端点均为 **`POST` + `produces: text/event-stream`**，**仅 `STUDENT`**，须带 `Authorization` 头。后端把请求转发到 Python graph 服务并把事件原样回放。

> **⚠️ 实测 2026-06-13（重要，历史事件名已不准确）**：线上 SSE 已迁移到 **AG-UI 语义事件协议**，
> 实际事件名为 `RUN_STARTED / RUN_FINISHED / RUN_ERROR / STEP_STARTED / STEP_FINISHED /
> TEXT_MESSAGE_START / TEXT_MESSAGE_CONTENT / TEXT_MESSAGE_END / TOOL_CALL_* /
> STATE_SNAPSHOT / STATE_DELTA / CUSTOM`，**而非**下表里的历史名 `plan/trace/token/result/error`
> （历史名仅作兼容，后端可能不再发送）。客户端须做「双语归一」：`TEXT_MESSAGE_CONTENT.delta` 拼答案、
> `STEP_*` 当思考链、`RUN_FINISHED.result`（常再包一层 `{ "result": { ... } }`）为终态、`RUN_ERROR` 为错误、
> `CUSTOM{name:"plan"}` 为工位清单。参考实现：`frontend/src/composables/agui.ts`、
> `clients/wechat/miniprogram/miniprogram/utils/agui.ts`。
> `/api/orchestrator/turn/stream` 的 canonical 成功生命周期还要求 `RUN_STARTED` 与
> `RUN_FINISHED` 同时携带相同的 `threadId/runId`：`threadId` 等于请求的长期 `session_id`，
> `runId` 是每次流请求唯一的 `orchestrator:<uuid>`。历史端点在滚动迁移期间可能缺少这两个字段，
> 但客户端不得把同一 thread 的多轮请求当作同一个 run。
> 另：资源工坊流 `/api/workshop/resources/stream` 的 `RUN_FINISHED.result` 只含 graph 内联
> `artifact`（**无 `resourceId`**，`status` 可能为 `PARTIAL`）；要拿可渲染的**持久化**资源，请用非流式
> `POST /api/workshop/resources`（返回 `resourceId`）→ `GET /api/workshop/resources/{id}`（返回 `LearningResource`）。

| 方法 | 路径 | 事件序列 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/tutor/ask` | `plan` → `trace`* → `token`* → `result` → (`error`) | 导师答疑流（含课件证据 + 防幻觉/安全审查） |
| POST | `/api/orchestrator/turn/stream` | `trace`* → `token`* → `result` → (`error`) | 学习总管多智能体编排流 |
| POST | `/api/workshop/resources/stream` | `plan` → `trace`* → `result` → (`error`) | 资源工坊生成流（`*` 表示可重复多次） |

> **学习总管的记忆矿脉增强（Phase 2）**：`/api/orchestrator/turn/stream` 由后端**服务端注入** `decaying_kcs`
> （当下 FSRS 到期、最该复盘的 KC，取自 Bug 矿洞遗忘回声），客户端**无需也不应**自己上送；总管据此把
> 看/学/练/测计划优先锚定到待复习知识点。取数失败时降级为空、不阻断对话。

SSE 帧格式（标准）：

```
event: <name>
data: <json>

```

帧解释遵循 WHATWG SSE 规则：同一事件的多个 `data:` 字段以单个 LF（`\n`）连接，`: ...`
注释心跳与未知字段忽略，缺省 `event` 为 `message`，只有空行才分发完整事件；连接在空行前结束时，
未完成尾帧必须丢弃。Java 后端的旧 Graph consumer 与公共
`common.sse.GraphStreamTransport` 共用 `common.sse.SseFrameDecoder` 执行该合同；终态识别、读超时、
客户端断开与 fallback 仍由各 transport/业务 adapter 负责。跨端客户端自行解析时也必须保留上述
语义，不能直接拼接多行 data 或分发 EOF 半帧。

Java 内部 `/agents/tutor-workflow/run/stream` 中继已采用公共 `GraphStreamTransport`：连接超时复用
Graph HTTP 配置，读不活动上限为 180 秒（上游每 10 秒注释 heartbeat 会续活）；上游无终态关闭时
保留已到达事件并补唯一 `error`（`AI_STREAM_INTERRUPTED`），读超时为 `AI_SERVICE_TIMEOUT`，客户端
断开会关闭上游。正常 `RUN_FINISHED/RUN_ERROR` 仍原样透传，不额外生成第二个终态。

事件语义（并集 = `plan / trace / token / result / error`，并非每个流都发全部）：

| event | data 形态 | 含义 |
| --- | --- | --- |
| `plan` | `{...}` | 本轮计划/步骤预告（首帧） |
| `trace` | `GraphTraceStep` | 每个 agent/node 的执行轨迹（可多次累加） |
| `token` | `{ "delta": "..." }` | 流式文本增量（逐字/逐段拼接） |
| `result` | 结构化结果对象 | 终态完整结果（含 evidence / grounding / safety 等） |
| `error` | `{ "code": "...", "message": "..." }` | 失败终止（如 `TUTOR_FAILED` / `ORCHESTRATOR_FAILED`） |

**客户端实现注意**：这些流是 `POST` 且需自定义 `Authorization` 头，**浏览器原生 `EventSource` 不适用**（只支持 GET、不能加头）。

- Web：用 `fetch` + `ReadableStream` 手动解析 SSE。
- Android / Windows 原生：用 OkHttp / `HttpClient` 的流式响应按行解析 `event:` / `data:`。
- 微信小程序/小游戏：用 `wx.request({ enableChunked: true })` 接收分块，自行按 SSE 帧切分。

非流式配套端点（同模块，按需使用）：`POST /api/tutor/style-engaged`（上报采纳讲法，`204`）、`GET /api/tutor/sessions?limit=20`、`POST /api/workshop/resources`（非流式生成）、`GET /api/workshop/resources?type=&kcCode=&limit=&offset=`、`GET /api/workshop/resources/{id}`、`POST /api/workshop/resources/{id}/feedback`。

### 5.1 意图路由（百晓工坊，非流式）

工坊对话框把一句话先送意图路由分类，客户端据 `intent` 分派到对应管线（非流式，单次调用）。

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/intent/route` | STUDENT | 单句意图分类 + KC 锚定，返回 `IntentRouteResponse` |

请求体 `IntentRouteRequest`（除 `message` 外均可空）：

```json
{
  "message": "我想复习一下 for 循环",
  "manualKcCodes": ["PY43-KC-001"],
  "courseKcs": [{ "code": "PY43-KC-001", "name": "for 循环" }],
  "currentChapter": 3,
  "recentKcCodes": ["PY43-KC-001"]
}
```

响应 `IntentRouteResponse`：

```json
{ "intent": "REVIEW", "kcCodes": ["PY43-KC-001"], "focusHint": "复盘 for 循环", "confidence": 0.7, "reason": "用户想复习巩固" }
```

`intent ∈ {ASK, VIDEO, REVIEW, HANDOUT, MINDMAP, PRACTICE, CODE_LAB, READING, STORYBOARD}`，客户端分派：

- `ASK` → 导师答疑（`POST /api/tutor/ask` SSE）。
- `VIDEO` → B站精选讲解视频卡片（外部检索，非生成）。
- `REVIEW` → **记忆矿脉回声复习**：引导去 Bug 矿洞按 FSRS 到期 KC 复盘（非生成，不调工坊生成管线）。
- 其余 6 个 = 资源类型 → 工坊生成（`POST /api/workshop/resources/stream` SSE）。

### 5.2 训练场导学工作流

`POST /api/tutor/workflow/runs` 以 SSE 推进训练场导学事件；`GET /api/tutor/workflow/sessions/active?problemId=...` 恢复当前题目的会话。仅学生可用。

活动会话快照在有正式提交反馈时附带 `latestSubmissionId`、`latestSubmissionProblemId` 和 `latestVerdict`。AC 后客户端以该提交触发 `AC_REVIEW`，再由 Tutor Graph 根据复盘方向生成迁移题；服务端要求草稿知识点匹配源题、Python3 参考解通过安全检查，并在隔离判题器中通过公开样例和隐藏用例后，才创建绑定学生、会话和 run 的私有题目。迁移题卡、正常题目详情和正式提交均走既有接口；私有题仅其所有者可读取题面和判题用例，参考解与隐藏用例不会进入卡片、SSE 或回放数据，也不会出现在公共课程题单。WA、RE、TLE、CE、MLE 进入 `ERROR_FEEDBACK`，`SYSTEM_ERROR` 不作为学生错误反馈。两种反馈事件均须绑定本人正式提交，服务端校验提交所属题目与判定结果。

对 `CHAT`、`EXECUTION_TRACE` 和 `VISUALIZE` 事件，`eventData.code` 可选，最多 20,000 个字符。`CHAT` 会将当前编辑器代码作为学生提供的上下文交给导师；代码中的注释和字符串均按不可信内容处理，不作为系统指令或课程证据。图片必须先上传到会话私有附件端点，再通过 `eventData.attachment_ids` 引用，图片字节不得放入事件 JSON。

---

## 6. CORS 与跨端注意事项

- **CORS 仅对本机 Web 源放行**：`http://localhost:8080`、`:5173`、`:5174` 及对应 `127.0.0.1`（允许凭据，暴露 `Authorization` 头）。这是为浏览器端跨域准备的。
- **原生客户端不受 CORS 限制**：Android / Windows / MonoGame 直接发 HTTP，不经浏览器同源策略，CORS 配置与它们无关。
- **微信端需自行配置域名白名单**：小程序/小游戏的 `wx.request` 不走 CORS，但受微信「服务器域名」白名单约束——
  - 开发：微信开发者工具勾选「不校验合法域名、web-view…」即可连 `http://localhost:18080`。
  - 线上：后端须为**已备案 HTTPS 域名**并加入小程序后台 request 合法域名白名单（明文 `http`/`localhost` 线上不可用）。
- 全部客户端走 **Bearer token**，不依赖 Cookie；`allowCredentials` 仅服务于 Web，原生/微信端只需带 `Authorization` 头。

---

## 7. 明确不存在的能力（不要据此开发）

R-14「世界收缩」后，本项目**没有**以下端点/概念，客户端不要为其建 UI 或调用：

- ❌ **背包 / 物品（inventory）**
- ❌ **货币 / 经济（currency）**
- ❌ **商店 / 购买（shop）**

游戏化语义已收敛为「看 → 学 → 练 → 测」学习闭环（课件/视频/RAG → 资源工坊 → 训练场判题 → 掌握度），Bug 矿洞为支线。任何「商店/金币/道具」均无后端支撑。

---

## 8. 端到端最小链路（参考）

1. `POST /api/auth/login` → 存 `token`（12h）。
2. `POST /api/game/bootstrap`（Bearer）→ 拉小镇与学习态。
3. 浏览课程：`/api/course/pack` → `/api/course/chapters` → `/api/course/kcs` → `/api/course/pages?kcCode=...`（证据页图 `/api/course/pages/{id}/image`）。
4. 答疑：`POST /api/tutor/ask`（SSE）。
5. 练习：`GET /api/problems/{id}/public` → `POST /api/submissions` → 轮询 `GET /api/submissions/{id}` 至终态。
6. 任务/班级：`/api/quests/today`、`/api/classrooms/mine`、`/api/classroom/quests/mine`。
7. 任意请求 `401` → 回到第 1 步重新登录。
