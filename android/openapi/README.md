# OpenAPI 静态规格（Android-R12 demo）

## 当前形态

`pythontopia-api.json` 是 **手写** 的最小 OpenAPI 3.0 静态规格，仅覆盖 Android-R12 demo 链路使用的端点：

- `POST /api/auth/login`（与既有 R-11 Auth 一致）
- `POST /api/game/bootstrap`（R-12 新增，单次聚合）
- `GET  /api/world/npcs/{npcKey}/state`（R-12 新增）
- `POST /api/world/npcs/{npcKey}/talk`（R-12 新增）
- `GET  /api/world/calendar/today`（R-12 新增）
- `GET  /api/world/inventory`（R-12 新增）
- `POST /api/world/sleep`（R-12 新增）
- `PATCH /api/world/player`（R-12 新增，8 方向玩家上报）

未覆盖、留给 Android-R13+ 的端点：技能树、商店、班级、KC 掌握度、复盘、矿洞副本、礼物等。后端完整规格随 `springdoc-openapi-starter-webmvc-ui` 自动暴露在 `/v3/api-docs`，届时由生成器全量覆盖。

## 为什么不直接接 `springdoc /v3/api-docs`？

1. 当前后端尚未启动 dev server，且本子代理任务范围内不能起停 backend（避免冲撞 W3-CTRL）。
2. 完整 spec 牵涉 60+ 端点 + JSONB 联合体 + JsonNode 自由结构，Generator 7.22 对 sealed union 的处理需要手工补丁层，超出 demo 范围。
3. 手写静态 spec 让 demo 链路落地成本最低（DTO/API 5 个文件），后续替换为自动生成时仅需对齐字段命名。

## 后续替换计划（Android-R13+）

```bash
cd /home/cypress/Pythontopia/backend
mvn spring-boot:run -Dspring-boot.run.profiles=demo &
sleep 30
curl -s http://localhost:18080/v3/api-docs \
  -o /home/cypress/Pythontopia/android/openapi/pythontopia-api.json
kill %1
# 然后在 :core:network 里加 OpenAPI Generator 7.22 task，把生成的 DTO/API 落到
# :core:network/build/generated/openapi/ 并替换 :core:data 里的手写 DTO。
```

## 字段对齐说明

`PlayerStatePatch.direction` 的 8 方向枚举与后端
`backend/src/main/java/com/pythontopia/world/dto/PlayerStatePatch.java` 的 `@Pattern`
保持一致（`UP/DOWN/LEFT/RIGHT/UP_LEFT/UP_RIGHT/DOWN_LEFT/DOWN_RIGHT`）。

`NpcDialogStateDto.flags` 在后端是 `JsonNode`（自由结构），demo 阶段把它收敛为
`Map<String,String>` 以避开 sealed union；遇到嵌套 JSON 时降级为忽略。
