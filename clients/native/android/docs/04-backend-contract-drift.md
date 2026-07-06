# 04 · 后端契约漂移审计（Android 客户端 ↔ 真实后端）

> **性质**：审计文档，**只记录不修代码**。
> **审计日期**：2026-06-05。
> **比对对象**：Android 端手写 DTO（`clients/native/android/core/data/...`）+ 手写 OpenAPI 子集（`clients/native/android/openapi/pythontopia-api.json`） **对照** 真实后端控制器/服务（`backend/src/main/java/com/pythontopia/...`）。
> **结论先行**：Android 端目前存在若干**虚构的**端点/对象/字段，真实后端并不提供；另有两处对真实契约的**语义误解**。建议改用后端 `/v3/api-docs` 生成 DTO，并从客户端预期中移除虚构的 inventory/currency。

## 1. 漂移项一览

| 编号 | 漂移 | Android 端怎么写的 | 真实后端 | 严重度 |
| --- | --- | --- | --- | --- |
| D-1 | 虚构端点 `GET /api/world/inventory` | `openapi/pythontopia-api.json` 声明该 path + `world-inventory` tag | **无此端点**（world 控制器无 inventory 映射） | 高 |
| D-2 | 虚构对象 `PlayerCurrencyDto`（`coins`/`gems`） | `core/data/.../world/PlayerCurrencyDto.kt` + openapi schema | **无货币/商店任何 REST** | 高 |
| D-3 | `GameBootstrapResponse` 字段漂移 | `GameBootstrapResponseDto` 含虚构的 `inventory` + `currency`，且**缺失**多个真实字段 | 真实 record 无 inventory/currency | 高 |
| D-4 | `POST /api/auth/refresh` 语义误解 | ADR-001 §1 决策 13 称其「返回 501」；常被当作「过期后刷新」 | 端点**已实现**，但用**未过期**的 access token 重签，过期即 `401`；access token 兼作 refresh 源 | 中 |
| D-5 | 存档槽位范围 | 文档/预期中出现「0–2」 | 槽位为 **0–5**（`@Min(0) @Max(5)`，`MAX_SLOT_INDEX = 5`） | 中 |

## 2. 逐项佐证

### D-1 / D-2 · `/api/world/inventory` 与 `PlayerCurrencyDto` 是虚构的

`clients/native/android/openapi/pythontopia-api.json` 声明了 `GET /api/world/inventory` 与 `PlayerCurrencyDto`、`InventoryItemDto` schema；`core/data/.../world/` 下也有对应的手写 DTO。

但真实后端 **没有任何 inventory / currency / shop REST 端点**。`/api/world*` 实际只有（见各 `@RequestMapping`/`@*Mapping`）：

```text
POST   /api/game/bootstrap                         GameBootstrapController
GET    /api/world/session                          WorldController
PATCH  /api/world/player                           WorldController
POST   /api/world/sleep                            WorldAdvanceController
GET    /api/world/npcs                              NpcDialogController
GET    /api/world/npcs/{npcKey}/state              NpcDialogController
POST   /api/world/npcs/{npcKey}/talk               NpcDialogController
GET    /api/world/calendar/today                   CalendarController
GET    /api/world/calendar/events                  CalendarController
POST   /api/world/calendar/events/{eventId}/process CalendarController
GET    /api/world/skills                            SkillController
POST   /api/world/skills/{skillKey}/unlock         SkillController
GET    /api/world/saves                             SaveSlotController
POST   /api/world/saves/{slotIndex}                SaveSlotController
POST   /api/world/saves/{slotIndex}/activate       SaveSlotController
POST   /api/world/saves/{slotIndex}/snapshot       SaveSlotController
POST   /api/world/boss/{chapter}/problem           BossController
POST   /api/world/boss/{chapter}/attempt           BossController
```

**背包在后端只以 `save_slot.inventory_items` JSONB 存在**（迁移 `V114__player_save_state_extension.sql`，写入逻辑见 `SaveSlotService.updateExtension` 中的 `save_slot.inventory_items payload`）——它是存档快照的一部分，**不是**独立的 inventory 端点，也没有对应的顶层 DTO。

> 旁证：`clients/native/android/openapi/README.md` 自述这是「**手写**的最小 OpenAPI 3.0 静态规格」，其中把 `world/inventory` 列为「R-12 新增」端点——这正是虚构来源。该 README 还引用了并不存在的 `clients/native/android/docs/r12_demo.md`。

### D-3 · `GameBootstrapResponse` 字段双向漂移

Android `GameBootstrapResponseDto`（`core/data/.../world/GameBootstrapResponseDto.kt`）：

```kotlin
data class GameBootstrapResponseDto(
    val world: WorldSnapshotDto,
    val player: PlayerStateDto,
    val inventory: List<InventoryItemDto> = emptyList(),  // 虚构
    val currency: PlayerCurrencyDto? = null,              // 虚构
    val npcStates: List<NpcDialogStateDto> = emptyList(),
)
```

真实后端 `GameBootstrapResponse`（`backend/.../world/dto/GameBootstrapResponse.java`）：

```java
public record GameBootstrapResponse(
    WorldSnapshotDto world,
    PlayerStateDto player,
    LearnerProfileDto profile,
    List<QuestDto> quests,
    List<MasterySnapshotDto> mastery,
    List<LearningPathNodeDto> path,
    List<LearnerSkillDto> skills,
    List<NpcDialogStateDto> npcStates,
    SaveSlotDto activeSlot,
    List<WorldEventDto> pendingEvents) {}
```

漂移是**双向**的：

- **多出（虚构）**：`inventory`、`currency` —— 后端 record 里不存在，反序列化时会被忽略，但会让客户端误以为有背包/货币数据。
- **缺失（真实却没声明）**：`profile`、`quests`、`mastery`、`path`、`skills`、`activeSlot`、`pendingEvents` —— 这些是 bootstrap 真正承载的教学闭环数据，目前 Android 拿不到。
- **命名注意**：存档字段真实名为 `activeSlot`（`GameBootstrapResponseDto` 的 KDoc 把它写成 `saveSlot`，亦不准确）。

### D-4 · `/api/auth/refresh` 不是真正的 refresh token

`backend/.../auth/service/AuthService.refresh(String token)` 的真实语义（含原注释）：

> 「用现有**有效** token 重新签发 access token。当前契约有意把 access token 兼作 refresh 源，故调用方须回传**未过期**的 access token；非法或过期 token 抛 `401 UNAUTHORIZED`（`REFRESH_TOKEN_INVALID`）。」

要点：

- **没有独立 refresh token**；`/api/auth/refresh` 只是「拿未过期 access token 去换一个新的 access token」（续签，不是过期后恢复）。
- access token TTL = **720 分钟 = 12 小时**（`application.yml` → `pythontopia.security.jwt.ttlMinutes: 720`）。
- token 一旦过期，refresh 也会 401 → **客户端必须重新登录**。
- ADR-001 §1 决策 13 说「后端 `/api/auth/refresh` 仍返回 501」**已过时**：端点已实现（只是不是真 refresh）。

### D-5 · 存档槽位是 0–5，不是 0–2

`SaveSlotController` 三个写端点均 `@PathVariable @Min(0) @Max(5) int slotIndex`；`SaveSlotRepository` 定义 `private static final int MAX_SLOT_INDEX = 5;`。即**每个用户 6 个槽位（0..5）**，且 V108 partial unique index 保证至多一个 `is_active=true`。任何「0–2 / 3 槽」的客户端假设都是错的。

> 说明：Android 端目前**尚未**实现存档槽位的 DTO/API（`core/data` 无 SaveSlot 相关类型）。本条是为后续接线预先纠偏，避免照搬错误范围。

## 3. 影响

- 一旦真机联调，`/api/world/inventory` 会 404，背包/货币 UI 将拿不到数据或静默为空。
- `GameBootstrapResponseDto` 反序列化能跑通（缺失字段忽略），但教学闭环关键数据（画像/任务/掌握度/路径/技能/存档/事件）全部丢失，hub 之外的 feature 难以从单次 bootstrap 拿到真实状态。
- 把 `/api/auth/refresh` 当「过期后刷新」会导致 token 过期后陷入反复 401；正确做法是过期前续签、过期后重登。

## 4. 建议（落地时）

1. **以后端为单一真相源生成 DTO**：起后端后 `curl /v3/api-docs` 拉全量 spec，用 OpenAPI Generator 7.22 生成 Kotlin Retrofit2 client，替换 `core/data` 手写 DTO 与手写 `openapi/pythontopia-api.json`（ADR-001 §1 决策 14 的原计划，尚未接线）。
2. **从客户端预期中删除虚构的 inventory / currency**：移除 `PlayerCurrencyDto`、`GameBootstrapResponseDto.inventory/currency` 字段与 openapi 里的 `/api/world/inventory`。若端上确需背包，应解析 `/api/world/saves` 快照里的 `save_slot.inventory_items` JSONB，而非独立端点/顶层字段。
3. **补齐 `GameBootstrapResponseDto` 的真实字段**：`profile/quests/mastery/path/skills/activeSlot/pendingEvents`（命名用 `activeSlot`）。
4. **Auth 续签策略**：把 refresh 当「12h 内主动续签」；401 一律走重新登录；不要假设有长效 refresh token。
5. **存档槽位用 0–5**（6 槽）。
6. 顺带修正 `openapi/README.md` 对不存在文档 `r12_demo.md` 的引用。

## 5. 解决记录（2026-06-13 · Android 重构 M0）

> 本节为后续追加；上文审计原文保持不变以留痕。

| 漂移 | 状态 | 处理 |
| --- | --- | --- |
| D-1 虚构 `/api/world/inventory` | ✅ 已解决 | 从 `openapi/pythontopia-api.json` 删除该 path、`world-inventory` tag；删除 `CalendarApi.inventory()` |
| D-2 虚构 `PlayerCurrencyDto`/`InventoryItemDto` | ✅ 已解决 | 删除两个 DTO 文件与 openapi schema；移除 `HubScreen` 的「背包/金币/宝石」HUD chips |
| D-3 `GameBootstrapResponse` 字段双向漂移 | ✅ 已解决 | 重写 `GameBootstrapResponseDto`：去 inventory/currency，补 `profile/quests/mastery/path/skills/activeSlot/pendingEvents`（命名以后端为准）；在 `:core:data/dto/learner` 新增 `LearnerProfileDto`(+7 子 DTO)/`QuestDto`(+枚举)/`MasterySnapshotDto`/`LearningPathNodeDto`(+枚举)，`:core:data/dto/world` 新增 `SaveSlotDto`/`WorldEventDto`/`LearnerSkillDto`，并补全 `PlayerStateDto` |
| D-4 `/api/auth/refresh` 语义误解 | ⏳ 待处理 | 当前客户端无 refresh token，`refresh()` 直接降级为重新登录（与 api-contract 推荐策略一致），故行为可用；正式纠偏（用未过期 access token 续签）随会话层重构处理 |
| D-5 存档槽位 0–5 | ⏳ 待处理 | Android 尚未实现存档 UI/API；新增的 `SaveSlotDto` 已按 0–5 语义注释，待存档功能落地时接线 |

> 代码生成（OpenAPI Generator）路线：`:core:network` 仍保留可选 `openApiGenerate` 任务（不入常规构建）。
> 本轮按「外科手术式对齐」手写 DTO 以保持增量可验证；全量 codegen 替换留作后续选项。
