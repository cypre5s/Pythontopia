# OpenAPI 规格与客户端代码生成（Android）

## 当前形态

`pythontopia-api.json` 是 **手写** 的最小 OpenAPI 3.0 静态规格（`version: 0.12.0-demo`），覆盖 Android 像素小镇 demo 链路使用的 7 个端点：

- `POST  /api/auth/login`
- `POST  /api/game/bootstrap`（单次聚合）
- `GET   /api/world/npcs/{npcKey}/state`
- `POST  /api/world/npcs/{npcKey}/talk`
- `GET   /api/world/calendar/today`
- `POST  /api/world/sleep`
- `PATCH /api/world/player`（8 方向玩家上报）

> R-14 世界收缩后**无**背包/货币/商店端点，原虚构的 `GET /api/world/inventory` 与
> `InventoryItemDto`/`PlayerCurrencyDto` 已从本规格与 `:core:data` 移除（详见 `../docs/04-backend-contract-drift.md`）。

真实后端通过 `springdoc-openapi-starter-webmvc-ui` 在 `GET http://localhost:18080/v3/api-docs`
暴露 **完整规格**（约 108 个端点）。本仓库这份 demo json 是它的**陈旧子集**，技能树/班级/
KC 掌握度/主线冒险/章末测试/复盘/矿洞副本等留待全量替换。

> ⚠️ **`pythontopia-api.json` 由根级 agent 统一维护为权威规格。** 本模块 / 子代理任务
> **不要**擅自启动后端，也**不要**覆盖这份已提交的 json——刷新由拥有规格的 agent 负责。

## 代码生成（已接线，可选，不参与常规构建）

OpenAPI Generator Gradle 插件（`org.openapi.generator` `7.22.0`，见
`gradle/libs.versions.toml`）已在 `:core:network/build.gradle.kts` 注册为**独立可选任务**
`openApiGenerate`：

| 项 | 值 |
| --- | --- |
| 输入 spec | `android/openapi/pythontopia-api.json` |
| 生成器 | `kotlin`，library `jvm-retrofit2` |
| 序列化 | `kotlinx_serialization`（日期 `kotlinx-datetime`，协程开启） |
| 输出目录 | `android/core/network/build/generated/openapi/` |
| 包名 | `com.pythontopia.core.network.generated`（`.api` / `.model`） |

**关键：该任务刻意不挂接到 `sourceSets`，常规 `build` / `assemble` / `test` 都不依赖它。**
生成产物落在 `build/` 下、默认不参与编译，因此对其它模块和并行 agent 的构建保持中立（绿）。
后续要正式启用生成代码时，再单独把生成目录加入 sourceSet 并补齐运行时依赖
（retrofit / kotlinx-serialization-converter 等），并对齐 / 替换 `:core:data` 的手写 DTO。

## 重新生成流程（spec 刷新 → 代码生成）

```bash
# 1) 启动后端（由拥有 backend 的 agent 或你本机手动执行；本模块不代起后端）
cd /home/cypress/Pythontopia/backend
mvn spring-boot:run            # 监听 18080

# 2) 抓取最新完整规格覆盖 demo json
#    （仅在被授权刷新权威规格时执行；默认由根级 agent 负责，子代理不要擅自覆盖）
curl -s http://localhost:18080/v3/api-docs > android/openapi/pythontopia-api.json

# 3) 生成 Kotlin + Retrofit2 客户端到 build/generated/openapi/
cd /home/cypress/Pythontopia/android
./gradlew :core:network:openApiGenerate
```

> 注意事项
> - 不要在本模块 / 子代理任务里启动或停止 backend（避免与后端 agent 冲撞）。
> - 不要擅自覆盖已提交的 `pythontopia-api.json`：它是根级 agent 维护的权威 demo 规格。
> - 生成产物在 `build/` 下，**不要**提交进 VCS。
> - `curl` 命令里的路径相对仓库根 `/home/cypress/Pythontopia`，按需替换为绝对路径。

## 字段对齐说明

`PlayerStatePatch.direction` 的 8 方向枚举与后端
`backend/src/main/java/com/pythontopia/world/dto/PlayerStatePatch.java` 的 `@Pattern`
保持一致（`UP/DOWN/LEFT/RIGHT/UP_LEFT/UP_RIGHT/DOWN_LEFT/DOWN_RIGHT`）。

`NpcDialogStateDto.flags` 在后端是 `JsonNode`（自由结构），demo 阶段把它收敛为
`Map<String,String>` 以避开 sealed union；遇到嵌套 JSON 时降级为忽略。
