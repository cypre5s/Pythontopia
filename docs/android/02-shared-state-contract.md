# Pythontopia Web / Android 共享状态契约

> 版本：v1.0 · 起草日期：2026-05-24 · 适用版本：R-12 起 · 状态：Draft（待 Android-R0 + W3 联合验收转 Active）
> 目标读者：后端 owner、Web 前端、Android 客户端、QA、教学策划
> 上游约束：`AGENTS.md`（后端是唯一真相 / 8 维画像 snake_case / RPG 存档无表名前缀 / 业务层禁 any）
> 平级文档：`docs/android/01-android-overview.md`（占位，由别的子代理产出）、`docs/gameplay/world-clock-calendar.md`（§8 后端时间对齐）、`docs/profile_schema.md`、`docs/field_mapping.md`
> 接口参考（只读不改）：`frontend/src/api/dto/*.ts`、`backend/src/main/java/com/pythontopia/**/dto/*.java`、`android/openapi/pythontopia-api.json`

## 0. 文档定位

R-12 启动后端是 Web (Vue 3 + Phaser) 与 Android (Compose + LibGDX) 双端共用。本文回答一道题：

> 同一个学生在 Web 与 Android 上同时登录、轮流操作、断网恢复时，**什么数据必须看到同一份事实**，**什么数据可以各自缓存**？

边界与不做：

- 不重新设计任何 DTO 字段；只**对齐**已有 30 份 `frontend/src/api/dto/*.ts` 与后端 Java records / Android `core/network` 生成产物。
- 不为 Android 复制一份 `tilemap.ts` / Phaser scene；Android 走 LibGDX + TmxMapLoader（见 `android/docs/ADR-001-tech-selection.md` §1.5）。
- 不引入第三方实时框架；事件下推走 SSE / WebSocket，与后端已有 `application.yml` 配置对齐。
- 不替代 `docs/gameplay/world-clock-calendar.md` §8 已固化的时间真相；本文只对其做 **Android 适配映射**。
- 不替代 `docs/profile_schema.md`；本文只把 8 维 key 在双端的呈现路径列出来。

本文写完后，所有违反契约的双端实现应该在 PR 阶段被 ReviewerBot 直接拒。

---

## 1. 状态层级 L1-L4

| 层 | 介质 | 寿命 | 真相强度 | 承载数据示例 |
|----|------|------|----------|--------------|
| **L1** 服务端权威 DB | PostgreSQL | 永久 | 唯一真相 | `user`、`learner_profile`、`submission`、`kc_mastery`、`world_session`、`save_slot`、`classroom`、`npc_dialog_state.friendship` |
| **L2** 服务端缓存 | Redis | 秒级~小时级 | 派生真相 | 实时排行榜、PVP 房间状态、班级在线人数、限频计数（rate limit） |
| **L3** 客户端持久 | Web IndexedDB / Android Room | 重启不丢 | 离线兜底 | 最近 10 次 bootstrap 响应、课件分页缓存、未发出去的 `quest/complete` 请求、最近 50 条 `world_event` 已读标记 |
| **L4** 客户端瞬时 | Web Pinia / Android `StateFlow` + ViewModel | 进程内 | UI 体验层 | `WorldClock.currentHour` 浮点小时、滚动位置、模态弹窗状态、Toast 队列、未提交的草稿代码 |

**层间硬约束**：

1. L4 任何写入只要影响业务（不是纯 UI 状态），**必须**在同一个 action 内发起 L1 endpoint 请求；不允许只写 L4。
2. L4 → L1 失败时：UI 立即回滚（Pinia `$patch` 反向 / StateFlow `update` 反向），并把请求体压入 L3 的 outbox 队列。
3. L1 推送（SSE `/api/world/events/stream`、WS `/api/classroom/{id}/live`）必须**先写 L3 再发 L4**，避免 UI 已显示但缓存未落盘导致刷新丢更新。
4. L2 只服务**派生只读**视图，不允许 L4 直接写 L2；客户端读 L2 必须经过 L1 endpoint（如 `GET /api/classroom/{id}/ranking`，后端聚合 Redis 返回）。
5. L1 与 L3 的字段命名必须严格一致：后端 `snake_case` 列名 → REST 出参 `camelCase` → 客户端缓存按 `camelCase` 存。中间转换发生在 Spring Jackson 默认配置，**不允许客户端再次 rename**。

---

## 2. 同步规则

### 2.1 写路径（client → server）

```
[UI 操作] → L4 乐观更新 → 发起 L1 请求
            ├── 成功：L1 返回最终态 → 覆盖 L4 → 写 L3
            └── 失败：L4 回滚 → 入 L3 outbox → toast 提示
```

| 场景 | 实现位置 | 失败回滚策略 |
|------|----------|--------------|
| 玩家移动上报 | `frontend/src/stores/world.ts` `flushPlayerUpdate` / Android `WorldRepository.patchPlayer` | 60s 节流；失败保留 `pendingUpdate` 下一节拍重试，**不回滚 UI 位置**（角色已动） |
| 背包消费 | `inventory.ts` `consumeItem` / Android `InventoryRepository` | 立即扣 L4 qty；失败 toast + 回滚 qty + 重新拉 `GET /api/world/inventory` |
| 提交代码 | `submissions.ts` `createSubmission` | 不乐观；按真实 `PENDING→RUNNING→PASSED/FAILED` 状态机渲染 |
| NPC 对话推进 | `worldNpc.ts` `talkToNpc` | 不乐观；先等 `NpcTalkResponseDto` 回到 L4 |
| 自评画像 | `learner.ts` `updateProfileDimension` | 立即更新 L4 `learnerProfile`；失败回滚 + toast |
| 完成 quest | `quests.ts` `completeQuest` | 不乐观；后端 evidence 校验必须先过（详见 `QuestEvidenceVerifier`） |

### 2.2 读路径（server → client）

| 通道 | 触发时机 | 影响层 |
|------|----------|--------|
| `POST /api/game/bootstrap` | 登录后首次进入世界、切换存档槽、Android 冷启 | 一次性灌满 L4 + 写入 L3 快照 |
| 60s 节拍 `PATCH /api/world/player` 响应 | 玩家每 60s 心跳 | 服务端可返回 `dayIndexChanged: true`，触发 L4 重拉 `calendar` |
| `GET /api/world/events?since={id}` | 进入世界 / 切换前台 | 增量拉 L3 + L4 |
| SSE `/api/world/events/stream`（R-13 计划） | 长连接 | 推送 `world_event` 与 `classroom_quest_published` |
| WS `/api/classroom/{id}/live`（R-13 计划） | 教师端进入班级直播 | 实时班级 KC 热力图 |

> 当前 R-12 Beta 仅落地 bootstrap + 60s 节拍 + 事件增量轮询；SSE/WS 留给 R-13。Android 端 R-12 接入计划与 Web 保持一致：先轮询，再升级长连接。

---

## 3. WorldClock 双端同步

### 3.1 真相边界

| 数据 | 真相 | 字段大小写约定 |
|------|------|----------------|
| `dayIndex` | 后端 `world_session.day_index` | 整数，无大小写问题 |
| `season` | 后端 `world_session.season` | DB / REST 出参：**小写**（`spring/summer/autumn/winter`） |
| `timeOfDay` | 后端 `world_session.time_of_day` | DB / REST 出参：**小写**（`dawn/morning/noon/afternoon/dusk/evening/night`） |
| `weather` | 后端 `world_session.weather`（每天 5:00 由 `WorldAdvanceService.sleep` 掷骰） | REST 出参当前已为**大写**枚举（`SUNNY/RAINY/CLOUDY/WINDY`），见 `WorldSessionDto.weather` |
| `currentHour`（小时浮点） | 客户端 `WorldClock.currentHour` | 不入库；离线重新登录默认 `08:00` |

> 完整推导规则见 `docs/gameplay/world-clock-calendar.md` §3 / §8.1。本节只把双端枚举如何对齐这一点写死。

### 3.2 双端枚举对齐

**Web**（`frontend/src/game/core/WorldClock.ts`）大写常量：

```ts
type Season = 'SPRING' | 'SUMMER' | 'AUTUMN' | 'WINTER';
type TimeOfDay = 'DAWN' | 'MORNING' | 'NOON' | 'AFTERNOON' | 'EVENING' | 'NIGHT';
type Weather = 'SUNNY' | 'RAINY' | 'CLOUDY' | 'WINDY';
```

**Android**（`core/common/src/main/kotlin/com/pythontopia/core/common/world/WorldClockState.kt`，待 R-12 Android W1 落地）：

```kotlin
enum class Season { SPRING, SUMMER, AUTUMN, WINTER }
enum class TimeOfDay { DAWN, MORNING, NOON, AFTERNOON, EVENING, NIGHT }
enum class Weather { SUNNY, RAINY, CLOUDY, WINDY }
```

**后端→客户端转换层**：

| 字段 | 后端出参 | Web 适配 | Android 适配 |
|------|----------|----------|--------------|
| `season` | `"spring"` | `stores/world.ts` `normalizeSeason(s)` 转大写 | `WorldClockAdapter.parseSeason(raw)` 走 `Season.valueOf(raw.uppercase())` |
| `timeOfDay` | `"morning"` | `normalizeTimeOfDay(t)` 转大写 | `parseTimeOfDay(raw)` 同上 |
| `weather` | `"SUNNY"`（当前已大写） | 直通 | 直通 |

> Web 端在 `WorldCalendarDto` 与 `WorldSessionDto` 之间存在大小写不一致（前者 lower、后者 upper），见 `docs/gameplay/world-clock-calendar.md` §8.2。Android 必须复用同一套适配逻辑：**收到任何 weather/season/timeOfDay 字符串都先 `uppercase()`，再 `valueOf`**。

### 3.3 60s 同步节拍跨端约束

- 双端在线时，每个 60 真实秒都 POST `PATCH /api/world/player`，载荷仅 `{tileX, tileY, direction}`，**不传 hour / day**。
- Web `Direction` ∈ 4 向；Android 沿用 4 向写后端（8 方向输入降级见 `AGENTS.md` 前端硬约束）。
- 双端同时在线：先 sleep 的设备推日，后登录的设备在下一次 60s 心跳响应里看到 `world.dayIndex` 跳变，触发 `WorldClock.syncFromServer({ dayIndex, weather })`。
- 不做"主备设备锁"，与 Web 保持一致。

---

## 4. 关键 DTO 字段对齐表（20 行）

| # | DTO | 关键字段 | TS 类型 | 后端 source | Web 引用路径 | Android 引用路径 | 备注 |
|---|-----|----------|---------|-------------|--------------|------------------|------|
| 1 | `UserDto` | `role` | `'STUDENT' \| 'TEACHER'` | `user.role` | `stores/auth.ts` | `core/data/auth/User.kt` | R-13 计划新增 `'ADMIN'`，双端 sealed 枚举需同步扩展 |
| 2 | `LoginResponse` | `token`, `expiresAt` | `string`, `ISO-8601` | `auth.security.JwtService.IssuedToken` | `stores/auth.ts` `login()` | `feature/auth/AuthRepository.kt` | Android 必须把 `expiresAt` 存进 `EncryptedSharedPreferences`，过期前 5 分钟强制重新登录 |
| 3 | `LearnerProfileDto` | `userId`, `narrativeSummary` | `number`, `string \| null` | `learner_profile` | `stores/profile.ts` | `feature/profile/ProfileRepository.kt` | 8 维子对象命名差异见 §10 |
| 4 | `LearnerProfileDto.errorPatterns` | `frequentKcCodes` | `string[]` | `dimensions.error_pattern`（**单数**） | `LearnerProfileDto.errorPatterns` | `ProfileDto.errorPatterns` | 后端 dimension key 是 `error_pattern`，REST 出参是 `errorPatterns`，Android **不要**再改成 `errorPattern` |
| 5 | `LearnerProfileDto.practiceCapability` | `successRate` | `number` | `dimensions.practical_ability` | `practiceCapability` | `practiceCapability` | 后端 dimension key 是 `practical_ability`，REST 出参是 `practiceCapability`，命名差异属历史债，**保持现状** |
| 6 | `WorldSessionDto` | `dayIndex`, `weather` | `number`, `'SUNNY'\|...` | `world_session.day_index/weather` | `stores/world.ts` `session` | `core/data/world/WorldSession.kt` | `weather` REST 已大写；Android `Weather` enum 直通 |
| 7 | `WorldCalendarDto` | `season`, `timeOfDay`, `weather` | `'spring'\|...`, `string`, `string` | `world_session.*` | `stores/calendar.ts` | `core/data/world/CalendarSnapshot.kt` | **小写！**双端 adapter 转大写见 §3.2 |
| 8 | `PlayerStateDto` | `tileX`, `tileY`, `direction` | `number`, `number`, `Direction` | `player_state` | `stores/world.ts` `player` | `core/data/world/PlayerState.kt` | 服务端只接受 4 向；Android 8 向输入降级在 client 层完成 |
| 9 | `NpcStateDto` | `npcKey`, `mood` | `string`, `string` | `world_session_npc` 派生 | `stores/world.ts` `npcStates` | `core/data/world/NpcState.kt` | 仅瞬时态；不写 L1，进入 bootstrap 时由后端重算 |
| 10 | `NpcDialogStateDto` | `friendship`, `flags` | `number`, `Record<string,unknown>` | `npc_dialog_state` | `stores/npc.ts` | `feature/dialog/NpcDialogRepository.kt` | `friendship` 是 L1 真相，**严禁** L4 单独累加；走 `POST /api/world/npcs/{key}/talk` |
| 11 | `SaveSlotDto` | `slotIndex`, `sceneName`, `unlockedBuildings`, `accumulatedPlaySeconds`, `inventoryItems` | 5 字段 | `save_slot` + V114 扩展列 | `stores/world.ts` `activeSlot` | `feature/hub/SaveSlotRepository.kt` | 见 §11 |
| 12 | `InventoryItemDto` | `itemKey`, `category`, `qty` | `string`, `ItemCategory`, `number` | `inventory_item` | `stores/inventory.ts` | `feature/hub/InventoryRepository.kt` | `category` 是 sealed enum，双端必须互斥枚举对齐 |
| 13 | `PlayerCurrencyDto` | `coins`, `gems` | `number`, `number` | `player_currency` | `stores/world.ts` `currency` | `core/data/world/Currency.kt` | 服务端唯一可改；客户端只读 |
| 14 | `ItemCatalogEntryDto` | `itemKey`, `basePrice` | `string`, `number` | `item_catalog` | 启动时拉一次 | 启动时拉一次 | 全局静态，可缓存 24h |
| 15 | `ClassroomDto` | `classroomId`, `joinCode`, `status` | `number`, `string\|null`, `'ACTIVE'\|'ARCHIVED'` | `classroom` | `stores/classroom.ts` | `feature/classroom/ClassroomRepository.kt` | `joinCode` 仅教师视角可见 |
| 16 | `QuestDto` | `id`, `status`, `requiredEvidenceType`, `payload` | `number`, `QuestStatus`, ..., `Record<string,unknown>` | `learner_quest` | `stores/learning.ts` `quests` | `feature/path/QuestRepository.kt` | `payload` 是 free-form JSON，双端均按 `Map<String,Any?>` 处理，不做 schema 校验 |
| 17 | `SubmissionDto` + `JudgeResultDto` | `status`, `verdict`, `trace` | `SubmissionStatus`, `Verdict`, `Record<string,unknown>` | `submission` + `judge_result` | `stores/training.ts` | `feature/workshop/SubmissionRepository.kt` | `trace` 是 sandbox 原始输出，双端按字符串呈现，**不解析** |
| 18 | `KcDto` + `MasterySnapshotDto` | `kcCode`, `masteryScore` | `string`, `number` | `kc` + `kc_mastery` | `stores/learning.ts` `mastery` | `feature/fields/MasteryRepository.kt` | `masteryScore ∈ [0,1]`，双端 UI 转 % 显示 |
| 19 | `WorldEventDto` | `id`, `eventType`, `payload`, `isProcessed` | `number`, `string`, `Record<string,unknown>`, `boolean` | `world_event` | `stores/calendar.ts` `events` | `feature/hub/CalendarRepository.kt` | 客户端 `isProcessed=true` 后**必须** POST `/api/world/events/{id}/process`，幂等 |
| 20 | `GameBootstrapResponseDto` | 17 个子字段 | 见 `frontend/src/api/dto/GameBootstrapResponseDto.ts` | 后端 `GameBootstrapService` 聚合 | `stores/world.ts` `setBootstrap()` | `feature/hub/HubRepository.kt` `bootstrap()` | 唯一聚合接口；双端不得自己拆成多个并行请求 |

---

## 5. OpenAPI 自动生成流程

### 5.1 后端契约源

- 后端通过 `springdoc-openapi-starter-webmvc-ui` 自动暴露 `GET /v3/api-docs`（JSON）与 `/swagger-ui.html`（人查）。
- 所有 controller 的 `@RestController` + `@RequestMapping` + DTO records 必须 100% 进入 spec；不允许"手写但不暴露"的端点。
- 后端 DTO 字段命名按 Spring Jackson 默认 `LOWER_CAMEL_CASE`；DB 列 `snake_case` 由 JdbcTemplate / Hibernate 转换。

### 5.2 Web 同步策略

R-12 Web 端**手写** TypeScript DTO（`frontend/src/api/dto/*.ts`），不走自动生成；理由：

1. Web 端 R-11 已沉淀 30 份手写 DTO，重构成本高。
2. Vitest 抽样校验（详见 §13）已足以拦截 schema drift。
3. R-13 计划引入 `openapi-typescript` 4.x 做差量校验（生成参考 spec → 与手写 DTO diff，CI 红线告警），不强制覆盖。

### 5.3 Android 同步策略

- Android 端使用 **OpenAPI Generator 7.22.0**，配置在 `android/openapi/` 与 `core/network/build.gradle.kts`。
- 当前 R-12 Beta 使用**手写静态 spec** `android/openapi/pythontopia-api.json`（仅覆盖 7 个 demo 端点，详见 `android/openapi/README.md`）。
- R-13 切换为后端 `/v3/api-docs` 自动生成流水线：

```bash
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=demo &
sleep 30 && curl -s http://localhost:18080/v3/api-docs > android/openapi/pythontopia-api.json
cd android && ./gradlew :core:network:openApiGenerate
```

- 生成产物路径：`android/core/network/build/generated/openapi/`，包含 Retrofit2 接口 + Kotlin data class。
- sealed union 类型（如 `WorkshopContent`、`BugMineCardDto.payload`）走手写补丁层 `android/core/network/src/main/kotlin/com/pythontopia/core/network/manual/`，并在 `.openapi-generator-ignore` 列出。
- snake→camel 转换由 Generator 内置 `modelNameMappings` + `inlineSchemaOptions` 完成；**禁止**在 Kotlin data class 上再加 `@SerialName` 手动 rename，避免与后端 drift。

### 5.4 命名一致性硬约束

| 层 | 风格 | 转换 |
|----|------|------|
| DB 列 | `snake_case`（`day_index`, `is_active`） | Spring JdbcTemplate `BeanPropertyRowMapper` |
| Java DTO 字段 | `camelCase`（`dayIndex`, `isActive`） | Jackson 默认 |
| REST JSON | `camelCase` | Jackson `PropertyNamingStrategy.LOWER_CAMEL_CASE` |
| TS DTO | `camelCase` | 手写 |
| Kotlin DTO | `camelCase` | OpenAPI Generator |

---

## 6. 离线策略

### 6.1 离线分级

| 等级 | 触发条件 | UI 表现 | 业务能力 |
|------|----------|---------|----------|
| 在线 | 60s 心跳 200 OK | 顶栏无标识 | 全功能 |
| 弱网 | 心跳 > 3s 或 4xx (≠401) | 顶栏黄色 chip "弱网" | 写操作仍走，但 toast 提示 "可能延迟" |
| 断网 | 心跳超时连续 ≥ 2 次 | 顶栏红色 chip "离线" + "上次同步：X 分钟前" | 仅只读 L3 缓存 + 写入 outbox |
| 长期离线 | 断网 ≥ 1 真实天 | BootScene 弹"主动确认睡眠"弹窗（同 Web） | 进入前必须重新 sleep；禁止累计 30 天 mastery decay |

### 6.2 Android Room 缓存约束

- Room 数据库名 `pythontopia_offline.db`，版本由 `Database.version` 管理，**不与后端 Flyway 版本对齐**（Android 独立演进）。
- 缓存表：`bootstrap_snapshot`（最近 10 条，按 `userId + savedAt` 主键）、`world_event_cache`、`course_page_cache`、`outbox_request`。
- 缓存条目过期：bootstrap 24h、course page 7d、world event 永久（直到 `isProcessed=true` 上报后删除）。
- 写 Room 必须在 IO 线程（`Dispatchers.IO`）；读可在 main（Room 自动支持挂起协程）。
- `outbox_request` 表结构：`{id, endpoint, method, bodyJson, queuedAt, retryCount, lastError}`，由 `OfflineSyncWorker`（WorkManager）每 5 分钟尝试发出。
- 显示 "上次同步：X 分钟前"：取 `bootstrap_snapshot.savedAt` 与当前时间差，超过 24h 显示 "请重新登录"。

### 6.3 恢复后增量同步

- 恢复网络后**第一时间**调用 `POST /api/game/bootstrap`，拉最新 L1 状态覆盖 L3 + L4。
- 同时把 `outbox_request` 队列按 `queuedAt` 升序串行发出；失败的回 outbox（最多 3 次后放弃 + 错误 toast）。
- 不允许并行发 outbox：Quest 完成顺序错乱会破坏 evidence 链。

---

## 7. 冲突解决

### 7.1 服务端胜出原则

- 任何 L4 与 L1 不一致时，以 L1 为准。客户端**不存在** "本地优先" 模式。
- bootstrap 响应到达时：客户端用响应数据**整段覆盖**对应 store / ViewModel，不做字段级 merge（避免新增字段被旧 L4 状态遮蔽）。
- 60s 心跳响应若包含 `world.dayIndex` 跳变，客户端**必须**重拉 `GET /api/world/calendar/today` + `GET /api/world/events?since={lastEventId}`，不允许仅刷一个字段。

### 7.2 多端冲突典型场景

| 场景 | 后端真相 | 客户端表现 |
|------|----------|-----------|
| Android 端 sleep 推日 + Web 同时在线 | `world_session.day_index +1`，写 `world_event(SLEEP_ADVANCE, payload)` | Web 端下一次 60s 心跳收到 `dayIndex` 跳变 → 自动 syncFromServer → 弹 "另一端推进了一天" toast |
| Web 端买入种子 + Android 端同步背包打开中 | `inventory_item.qty` 已 +1，`player_currency.coins` 已 -50 | Android 当前帧仍显示旧值；下一次 `GET /api/world/inventory`（5 分钟节拍）或主动刷新时同步 |
| 两端同时 PUT 同一画像维度 | 后端按到达顺序串行 `applyDimensionUpdate`；`profile_revision` 表保留双份审计 | 双端均收到 200 + 各自的 `dimension` 最新值；后到的覆盖先到的（last-write-wins） |
| 一端 quest 完成 + 另一端正在做 | 后端 `learner_quest.status = COMPLETED`，evidence 写入；幂等 | 另一端下一次 bootstrap 看到 `status` 已变；如该端正在编辑代码，本地草稿保留但 "完成" 按钮变 disabled |

### 7.3 不做的妥协

- **不做 CRDT / 向量时钟**：双端写频极低（学生不会在两台设备上每秒同时操作），last-write-wins + 服务端审计已够。
- **不做客户端选主**：避免增加复杂度；服务端永远是 leader。
- **不做版本号字段（`If-Match` / `ETag`）**：R-13 评估，R-12 不做。

---

## 8. 认证统一

### 8.1 当前实现 vs 契约目标

| 维度 | R-11 现状 | R-12 契约目标 | R-13 计划 |
|------|-----------|---------------|----------|
| Access token TTL | 720 分钟（12h，见 `application.yml: pythontopia.security.jwt.ttlMinutes`） | **7 天**（10080 分钟） | 维持 7 天 |
| Refresh token | `POST /api/auth/refresh` 接口存在但返回 501（见 ADR-001 §1.13） | **30 天 refresh token**，存 `refresh_token` 表 | 维持 |
| Web 存储 | `localStorage`（`stores/auth.ts`） | `localStorage`（保持） | `httpOnly` cookie + SameSite=Lax + CSRF token |
| Android 存储 | 未实现 | `EncryptedSharedPreferences` + `MasterKey.setUserAuthenticationRequired(true)` + Keystore | + BiometricPrompt `BIOMETRIC_STRONG` 解锁 |
| 过期前提醒 | 无 | 过期前 5 分钟双端弹 toast "登录即将过期" | 改为 refresh 自动续期 |

### 8.2 双端 token 流转

```
[Login] POST /api/auth/login { username, password }
        → { token, expiresAt, user }
        → Web: localStorage.setItem('pyt_token', token)
        → Android: EncryptedSharedPreferences.edit { putString('jwt', token); putLong('exp', epochMs) }

[每个 API 请求] Authorization: Bearer <token>

[过期前 5 分钟] 双端检测 expiresAt - now < 5min
        → R-12: 弹 toast 提示重新登录
        → R-13: POST /api/auth/refresh { refreshToken } → 拿新 token 覆盖

[401 响应] 双端立即清空 token + 跳登录页 + toast "请重新登录"
```

### 8.3 双端共享的安全硬约束

- token 不允许通过 query string 传递（防 referer 泄漏）。
- token 不允许写入日志（双端 `console.log` / `Log.d` 均要过滤）。
- 多账号登录：Android 单设备同一时间只允许一个学生账号在线；切换账号必须先 logout 清缓存。
- 教师 token 与学生 token 共用同一 JWT 结构；`role` 字段在 payload 内，双端解析后用于路由门禁。

---

## 9. 错误码契约

### 9.1 编码规范

后端 `ApiError.code` 当前是描述性字符串（如 `SAVE_SLOT_NOT_FOUND`）。R-12 契约**新增 numeric prefix** 用于客户端路由判断：

| 区间 | 类别 | 客户端处理建议 |
|------|------|----------------|
| **4001-4019** | 通用业务错误（参数、权限、限频） | 表单字段红线 + toast |
| **4020-4039** | 学习域（画像、KC、Quest、Path） | 教学面板内联错误 |
| **4040-4059** | 世界域（存档、背包、货币、商店、世界状态） | toast + 局部回滚 |
| **4060-4079** | 教学协作域（班级、教师视角） | 教师 dashboard 内联 |
| **4080-4099** | 提交与判题域（Submission、BugMine、Evidence） | 训练面板内联 |
| **5001-5009** | 服务端故障 | 全屏错误页 + 自动重试 1 次 |
| **5010-5029** | 客户端网络 | 顶栏弱网 chip，不打断当前页 |
| **5030-5049** | 同步冲突 | 静默重拉 bootstrap |
| **5050-5099** | 预留 | — |

> 后端实现路径：保留现有字符串 `code` 不变；新增 `numericCode: int` 字段（向后兼容 nullable）。客户端按 `numericCode` 选择 UI 行为；展示文案仍用 `code` 经 `ERROR_CODE_ZH` 表查得（Web `frontend/src/api/http.ts`、Android `core/network/ErrorMessages.kt`）。

### 9.2 常用 20 个错误码

| numericCode | code | HTTP | 中文文案 | 触发场景 |
|-------------|------|------|----------|----------|
| 4001 | `VALIDATION_ERROR` | 400 | 输入内容不符合要求 | DTO `@Valid` 失败 |
| 4002 | `UNAUTHORIZED` | 401 | 请重新登录 | JWT 缺失/过期 |
| 4003 | `FORBIDDEN` | 403 | 没有权限执行该操作 | 角色不匹配 |
| 4004 | `NOT_FOUND` | 404 | 请求的资源不存在 | 通用 404 |
| 4005 | `CONFLICT` | 409 | 操作与当前数据状态冲突 | 通用乐观锁冲突 |
| 4006 | `RATE_LIMITED` | 429 | 请求过于频繁，请稍后再试 | Redis 限频 |
| 4007 | `BAD_CREDENTIALS` | 401 | 用户名或密码错误 | 登录失败 |
| 4008 | `USERNAME_CONFLICT` | 409 | 用户名已被占用 | 注册冲突 |
| 4020 | `PROFILE_DIMENSION_INVALID` | 400 | 画像维度 key 不合法 | PUT `/dimensions/{key}` 未在 8 维清单 |
| 4021 | `PROFILE_DIMENSION_NOT_SELF_EDITABLE` | 403 | 该维度只能由系统被动更新 | 学生改非自评维度 |
| 4022 | `EVIDENCE_REQUIRED_SUBMISSION` | 400 | 必须附带 submissionId 证据 | Quest 完成缺 evidence |
| 4023 | `WRONG_PROBLEM` | 400 | 提交所属题目与任务要求不符 | Evidence 校验 |
| 4040 | `WORLD_SESSION_MISSING` | 409 | 世界状态未初始化，请先 bootstrap | 心跳早于 bootstrap |
| 4041 | `SAVE_SLOT_NOT_FOUND` | 404 | 存档槽不存在 | 切槽路径错 |
| 4042 | `SAVE_SLOT_INDEX_INVALID` | 400 | 存档槽编号不合法（需 0-2） | 超 3 槽 |
| 4043 | `INVENTORY_NOT_ENOUGH` | 409 | 背包内该物品数量不足 | 消费多于持有 |
| 4044 | `CURRENCY_NOT_ENOUGH` | 409 | 金币或宝石不足 | 购买失败 |
| 4060 | `CLASSROOM_QUEST_KC_UNKNOWN` | 400 | 班级任务引用的 KC 未在课程包内 | 教师发布错 KC |
| 4080 | `SUBMISSION_CODE_EMPTY` | 400 | 代码不能为空 | 空提交 |
| 5001 | `INTERNAL_ERROR` | 500 | 服务器开小差了，请稍后再试 | 未捕获异常 |

> 完整 ~60 条错误码清单由 `backend/src/main/java/com/pythontopia/common/error/ErrorCodeRegistry.java`（R-12 W4 待新增）维护，本表保留高频 20 条作为契约最小集。

---

## 10. 画像 8 维同步

### 10.1 维度权威清单

权威源：`PassiveProfileService.DIMENSION_KEYS`，**snake_case**：

| # | dimension key（snake_case） | 中文名 | REST 出参子对象（camelCase） | 可学生自评 |
|---|------------------------------|--------|-----------------------------|-----------|
| 1 | `professional_background` | 专业背景 | （顶层 `major`） | ✓ |
| 2 | `knowledge_base` | 知识基础 | `baseLevel` | ✗ |
| 3 | `learning_goal` | 学习目标 | `learningGoal` | ✓ |
| 4 | `cognitive_style` | 认知风格 | `cognitiveStyle` | ✗ |
| 5 | `learning_preference` | 学习偏好 | `learningPreference` | ✓ |
| 6 | `error_pattern`（单数） | 易错模式 | `errorPatterns`（**复数**） | ✗ |
| 7 | `practical_ability` | 实践能力 | `practiceCapability`（命名不同） | ✗ |
| 8 | `emotion_persistence` | 情绪与坚持度 | `emotionPersistence` | ✓ |

> 行 6、7 的 dimension key 与 REST 字段名不一致属历史债（W2 锁定不动）。Android 端**只对 REST 字段命名**，dimension key 通过 `PUT /api/learner/profile/dimensions/{dimensionKey}` URL path 直接传 snake_case。

### 10.2 自评 API

- 端点：`PUT /api/learner/profile/dimensions/{dimensionKey}`
- 请求体（`SelfDimensionRequest`）：`{ scoreDelta?: number, evidenceIncrement?: number, tagsAdd?: string[], metadata?: Record<string, unknown> }`
- 学生只能改 `SELF_EDITABLE_DIMENSIONS = {professional_background, learning_goal, learning_preference, emotion_persistence}`；其余 403。
- 教师 / ADMIN 可改自身全部 8 维。
- 双端 UI：在 `ProfileScreen` / `ProfilePanel.vue` 仅渲染 4 个可自评维度的滑杆/选择器；只读维度灰显并标注"由系统更新"。

### 10.3 被动更新

- `PassiveProfileService` 监听 `LearningEvent`（练习提交、Debug 修复、资源反馈、班级任务完成）自动调 `applyDimensionUpdate`。
- 双端**不调用**被动 API；只读 `LearnerProfileDto`。
- 双端在收到 quest 完成 200 响应后，必须主动重拉 `GET /api/learner/profile`（或等下一次 bootstrap）刷新 L4 画像。

---

## 11. 保存槽契约

### 11.1 表结构

`save_slot` 表（V108 建立 + V114 扩展）：

| 列 | 类型 | 约束 | 用途 |
|----|------|------|------|
| `id` | BIGINT | PK | 主键 |
| `user_id` | BIGINT | FK → user.id | 单用户最多 3 行 |
| `slot_index` | INT | 0-2，UNIQUE(user_id, slot_index) | 槽位编号 |
| `display_name` | VARCHAR(64) | NOT NULL | 用户起的存档名 |
| `is_active` | BOOLEAN | DEFAULT false | 同 user 仅 1 行 true |
| `created_at` / `last_played_at` | TIMESTAMPTZ | NOT NULL | 时间戳 |
| `snapshot` | JSONB | 老字段，自由结构 | 兼容用，新增数据走下列 4 列 |
| `scene_name` (V114) | VARCHAR(64) | DEFAULT 'WorldScene' | 当前 Phaser scene / LibGDX screen |
| `unlocked_buildings` (V114) | TEXT[] | DEFAULT '{}' | 已解锁建筑 key 列表 |
| `accumulated_play_seconds` (V114) | BIGINT | DEFAULT 0 | 累计游玩秒 |
| `inventory_items` (V114) | JSONB | DEFAULT '[]' | 背包快照 `[{itemId, qty, slot}]` |

### 11.2 双端契约

- **单用户最多 3 槽**：`slot_index ∈ {0,1,2}`；超 3 触发 `SAVE_SLOT_INDEX_INVALID` (4042)。
- **同时只能 1 个 active**：切换槽通过 `POST /api/world/save-slots/{slotIndex}/activate`，后端事务内把旧 active 置 false + 新槽置 true。
- `snapshot` 字段**不再扩展新数据**；所有新数据进入 V114 的 4 列。
- `inventory_items` 仅作**存档恢复用**，不替代 `inventory_item` 表（运行时背包）。bootstrap 时优先信任 `inventory_item` 表；`inventory_items` 用于"读取旧存档时回灌一次性"。
- Web `stores/world.ts` 字段：`sceneName`、`unlockedBuildings`、`accumulatedPlaySeconds`、`inventoryItems`，全部 camelCase。
- Android `feature/hub/SaveSlotRepository.kt` 字段同名 camelCase。
- 切换 scene / 解锁建筑后**必须** PATCH 一次 `/api/world/save-slots/{slotIndex}/extension` 持久化，避免崩溃丢进度。
- `accumulated_play_seconds` 不在 60s 心跳里上报（防篡改）；由后端在 sleep / bootstrap 时统一累加。

---

## 12. 测试

### 12.1 Web Vitest

- 新增 `frontend/tests/api/dtoContract.spec.ts`，抽样 5 个核心 DTO 对每个字段断言"运行时实例 → 反序列化 → 字段命名、类型、可选性与 TS interface 完全一致"。
- 抽样清单：`LoginResponse`、`GameBootstrapResponseDto`、`WorldSessionDto`、`SaveSlotDto`、`LearnerProfileDto`。
- 数据源：从 `backend/src/test/resources/contract/*.json` 取真实响应快照（由 `SpringDocApiTest` 同步刷新）。
- CI 红线：snapshot 与 spec drift 即 fail。

### 12.2 Android Kotest

- 新增 `android/core/network/src/test/kotlin/.../DtoContractTest.kt`，使用 Kotest `should` matcher + MockWebServer 灌入相同 JSON 快照，断言生成的 Kotlin data class 与 JSON 双向反序列化无字段丢失。
- 抽样清单与 Web 完全一致（5 DTO），共享同一份 `backend/src/test/resources/contract/*.json`。
- Roborazzi 截图测试覆盖 `HubScreen` 在三种状态（在线 / 弱网 / 离线）下的顶栏 chip 样式。

### 12.3 后端 SpringDoc

- 新增 `backend/src/test/java/com/pythontopia/contract/SpringDocApiTest.java`，启动 MockMvc + springdoc，断言：
  1. `/v3/api-docs` 包含本文 §4 列出的全部 20 个 DTO schema。
  2. 每个错误码（§9.2 的 20 个）在某个端点 responses 中至少出现一次。
  3. 8 维画像 key 在 `PUT /api/learner/profile/dimensions/{dimensionKey}` 的 path enum 中完整列出。
- 每次后端 PR 必须跑通该测试，未列出的新错误码 / 新 DTO 必须先更新本文档再合入。

---

## 13. 验收 checklist

| # | 项 | 验证手段 | Owner |
|---|----|----------|-------|
| 1 | Web `npm run typecheck` 0 错 | CI | Web |
| 2 | Android `./gradlew :core:network:test :core:network:openApiGenerate` 通过 | CI | Android |
| 3 | 后端 `mvn test -Dtest=SpringDocApiTest` 通过 | CI | Backend |
| 4 | 双端登录同一账号，60s 内 day_index 同步漂移 ≤ 1 | E2E | QA |
| 5 | 一端 sleep 推日，另一端弹 "另一端推进了一天" toast | E2E | QA |
| 6 | Android 断网 30 分钟 → 恢复 → outbox 自动发出 → bootstrap 同步成功 | 手动 | QA |
| 7 | 双端均能正确显示 8 维画像，4 个可自评维度的 UI 一致 | 视觉 | UX |
| 8 | 错误码 `INVENTORY_NOT_ENOUGH` 在双端均提示"背包内该物品数量不足"中文文案 | 手动 | QA |

---

## 附录 A. 完整 DTO 清单（30 行）

> 顺序与 `frontend/src/api/dto/` 字母序对齐；Android Owner 列指责任 feature 模块。

| # | DTO 文件 | 主要 interface | 后端 Java 对应 | Android Owner |
|---|----------|----------------|----------------|---------------|
| 1 | `ApiError.ts` | `ApiError` | `common.error.ApiError` | `core/network` |
| 2 | `BugMine.ts` | `BugMineSessionDto`, `BugMineCardDto` | `bugmine.dto.*` | `feature/mine` |
| 3 | `ChapterDto.ts` | `ChapterDto` | `coursepack.dto.ChapterDto` | `core/data` |
| 4 | `ClassroomDto.ts` | `ClassroomDto` | `classroom.dto.ClassroomDto` | `feature/classroom` |
| 5 | `ClassroomMemberDto.ts` | `ClassroomMemberDto` | `classroom.dto.ClassroomMemberDto` | `feature/classroom` |
| 6 | `ClassroomQuest.ts` | `ClassroomQuestDto`, ...Request | `classroom.quest.dto.*` | `feature/classroom` |
| 7 | `CoursePackDto.ts` | `CoursePackDto` | `coursepack.dto.CoursePackDto` | `core/data` |
| 8 | `CropTileDto.ts` | `CropTileDto` | `world.field.dto.CropTileDto` | `feature/fields` |
| 9 | `CurriculumBootstrapResult.ts` | `CurriculumBootstrapResult` | `world.dto.CurriculumBootstrapResult` | `feature/hub` |
| 10 | `Direction.ts` | `Direction` | `world.player.Direction` | `core/data` |
| 11 | `Evaluation.ts` | `EvaluationDto`, `EvaluationMetricDto` | `learner.evaluation.dto.*` | `feature/retro` |
| 12 | `GameBootstrapResponseDto.ts` | `GameBootstrapResponseDto`, `WorldSleepResponseDto` | `world.dto.GameBootstrapResponseDto` | `feature/hub` |
| 13 | `InventoryItemDto.ts` | `InventoryItemDto`, `ItemCategory` | `world.inventory.dto.InventoryItemDto` | `feature/hub` |
| 14 | `ItemCatalogEntryDto.ts` | `ItemCatalogEntryDto` | `world.item.dto.ItemCatalogEntryDto` | `core/data` |
| 15 | `KcDto.ts` | `KcDto` | `coursepack.dto.KcDto` | `core/data` |
| 16 | `LearnerProfileDto.ts` | `LearnerProfileDto` | `learner.profile.dto.LearnerProfileDto` | `feature/profile` |
| 17 | `LearnerSkillDto.ts` | `LearnerSkillDto` | `world.skill.dto.LearnerSkillDto` | `feature/profile` |
| 18 | `Library.ts` | `CourseDocumentSummary`, `LibrarySearchHit`, ... | `coursepack.library.dto.*` | `feature/workshop` |
| 19 | `LoginRequest.ts` | `LoginRequest` | `auth.dto.LoginRequest` | `feature/auth` |
| 20 | `LoginResponse.ts` | `LoginResponse` | `auth.dto.LoginResponse` | `feature/auth` |
| 21 | `MasterySnapshotDto.ts` | `MasterySnapshotDto` | `learner.mastery.dto.MasterySnapshotDto` | `feature/fields` |
| 22 | `NpcDialogStateDto.ts` | `NpcDialogStateDto`, `NpcTalkRequest`, ...Gift | `world.npc.dto.*` | `feature/dialog` |
| 23 | `NpcStateDto.ts` | `NpcStateDto` | `world.npc.dto.NpcStateDto` | `core/data` |
| 24 | `PageDto.ts` | `PageDto` | `coursepack.dto.PageDto` | `feature/workshop` |
| 25 | `Path.ts` | `LearningPathDto`, `LearningPathNodeDto` | `learner.path.dto.*` | `feature/path` |
| 26 | `PlayerCurrencyDto.ts` | `PlayerCurrencyDto` | `world.currency.dto.PlayerCurrencyDto` | `feature/hub` |
| 27 | `PlayerStateDto.ts` | `PlayerStateDto` | `world.player.dto.PlayerStateDto` | `core/data` |
| 28 | `ProblemDto.ts` | `ProblemDto` | `coursepack.dto.ProblemDto` | `feature/workshop` |
| 29 | `Quest.ts` | `QuestDto`, `QuestCompleteRequest`, `QuestSkipRequest` | `learner.quest.dto.*` | `feature/path` |
| 30 | `RegisterRequest.ts` | `RegisterRequest` | `auth.dto.RegisterRequest` | `feature/auth` |

> 余下文件 `SaveSlotDto.ts` / `ShopDto.ts` / `SkillNodeDto.ts` / `Submission.ts` / `TeacherDashboard.ts` / `UserDto.ts` / `Workshop.ts` / `WorldCalendarDto.ts` / `WorldEventDto.ts` / `WorldSessionDto.ts` 已在 §4 关键对齐表逐项展开，不在此重复。如未来 DTO 数量增长，本附录维持"已枚举到 30 + 关键 10"模式，超过 40 时拆分到 `docs/android/02-shared-state-contract-appendix.md`。

---

## 附录 B. 变更日志

| 日期 | 版本 | 变更 | Owner |
|------|------|------|-------|
| 2026-05-24 | v1.0 | 首版起草，覆盖 30 DTO + 20 错误码 + 8 维画像 + V114 存档扩展 | Backend + Android 联合架构 |
