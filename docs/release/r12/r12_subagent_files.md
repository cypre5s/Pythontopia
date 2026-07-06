# R-12 子代理产出文件索引

> 配套 `docs/project/CHANGELOG.md` R-12 段、`docs/release/r12/r12_release_notes.md`、`docs/sv-progress.md`、`docs/wave_subagent_report.md` 使用。
>
> 本清单只列**核心交付物**（落库 commit 的关键源码/资源/文档），不重复列每个 spec/Vitest 测试文件。完整 commit 历史可在 git log 中按子代理代号反查。

---

## Wave-1 · M1 资产 + 引擎（4 子代理）

### M1-A · 资产同步脚本

- `scripts/sync-assets.mjs`（资产入库与 MANIFEST 解析）
- `frontend/public/assets/pixel/LICENSE.md`（license 完整重写）
- `.gitattributes`（LFS 规则；后续被收敛为常规 git）
- `LICENSE-ART.md`（顶层 license 索引）

### M1-B · Phaser 引擎重构

- `frontend/src/game/adapters/phaserGame.ts`（Game 单例 + Arcade Physics）
- `frontend/src/game/adapters/tilemap.ts`（Tilemap/TilemapLayer 替代手撸 image 铺地）
- `frontend/src/game/adapters/sprite.ts`（LPC Sara 4 向 walk anim + tween 平滑移动 + 阴影 ellipse）
- `frontend/src/game/adapters/pixelFont.ts`（BitmapFont 接入）
- `frontend/src/game/scenes/BootScene.ts`（预加载 5 地图 + sprite + audio）
- `frontend/src/game/scenes/TownScene.ts`（R-12 中后期被 WorldScene 取代但保留兼容）
- `frontend/public/assets/maps/town_main.json`（已被 W2-MAPS 的 5 张地图替代）

### M1-C · 音频统一栈

- `frontend/src/composables/useAudio.ts`（统一 BGM/SFX 入口）
- 删除：`frontend/src/utils/bgm.ts` + `bgm.test.ts` + `sfx.ts` + `sfx.test.ts`
- 调用点替换：`App.vue` / `TownScene.ts` / `BugMinePage.vue` / `CreditsPage.vue`

### M1-D · Credits 页扩展

- `frontend/src/pages/CreditsPage.vue`（CC0/CC-BY/CC-BY-SA/OFL 四组分类渲染）
- `frontend/src/data/credits.ts`（`LICENSE_GROUPS` 分组数据）

---

## Wave-2 · 5 地图 + 20 NPC + RPG 存档基座（7 子代理）

### W2-FIX · 八方向 + jsdom 测试修复

- `frontend/src/game/adapters/input.ts`（8 方向 `ExtendedDirection` + F 键 + `toServerDirection` 降级 4 向）
- `frontend/tests/_helpers/phaserMock.ts`（全局 `vi.mock('phaser')`，jsdom 跑 Phaser 不爆 canvas 错）
- `frontend/vitest.config.ts`（`setupFiles` 加载 phaserMock）
- `frontend/tests/game/adapters/input.spec.ts` / `sprite.spec.ts` / `tilemap.spec.ts`

### W2-MAPS · 5 张 Tiled 地图

- `scripts/generate-maps.mjs`（程序化生成入口）
- `frontend/public/assets/maps/town_hub.json`（50×40 中央 hub）
- `frontend/public/assets/maps/farm_north.json`（40×40 北农场）
- `frontend/public/assets/maps/mine_south.json`（30×30 南矿洞）
- `frontend/public/assets/maps/campus_east.json`（40×30 东校园）
- `frontend/public/assets/maps/port_west.json`（35×25 西港口）
- `frontend/public/assets/maps/index.json` + `README.md`

### W2-WORLD · WorldScene 通用场景 + 路由合并

- `frontend/src/game/scenes/WorldScene.ts`（495 行通用 scene，跨地图 fade）
- `frontend/src/components/game/WorldCanvas.vue`（Phaser 单例挂载点）
- `frontend/src/pages/WorldPage.vue`（顶部 HUD + Phaser canvas + ChapterNavPanel slot）
- `frontend/src/app/router.ts`（`/town` `/field` `/bug-mine` → `/world` 合并）
- 删除：`frontend/src/game/scenes/PlaceholderScene.ts`

### W2-NPC · 20 NPC 静态数据 + 140 对话节点

- `data/npcs/<key>/meta.json`（20 份）：mayor_py / farmer_kc / librarian_doc / sage_review / coach_q / foreman_bug（教育核心 6）+ class_president / student_top / student_diligent / student_anxious / student_procrast / student_hobby（学生 6）+ chef / merchant / gardener / postman / craftsman / guard / nurse / student_late_starter（生活 8）
- `data/npcs/<key>/dialog.json`（20 份，至少 5 节点/NPC）
- `data/npcs/index.json`（汇总索引）
- `frontend/public/assets/pixel/portraits/portrait_*.png`（20 portrait + 占位）

### W2-DIALOG · DialogTree 系统

- `frontend/src/components/game/DialogTree.vue`（9-slice + 打字机 + portrait + ESC + 友好度心形）
- `frontend/src/components/game/DialogModal.vue`（DialogTree 在 WorldPage 内的挂件）
- `frontend/scripts/sync-npc-data.mjs`（`data/npcs/` → `frontend/public/data/npcs/` 同步，npm predev/prebuild 钩子）
- `frontend/src/stores/dialog.ts`（dialog state + 友好度内存缓存）
- `frontend/tests/components/DialogTree.spec.ts`（7 用例）

### W2-MIGRATE · 后端 RPG 存档基座（V100-V109）

- `backend/src/main/resources/db/migration/V100__game_bootstrap_view.sql`
- `V101__npc_dialog_state.sql` / `V102__npc_gift_log.sql`
- `V103__inventory.sql` / `V104__skill_tree.sql` / `V105__currency.sql`
- `V106__shop_listing.sql` / `V107__world_calendar.sql` / `V108__save_slot.sql`
- `V109__course_example_index.sql`
- `backend/src/main/java/com/pythontopia/world/{npc,inventory,currency,skill,save,shop,calendar}/`（12 records/repos）

### W2-PROFILE · 8 维画像直写

- `backend/src/main/java/com/pythontopia/learner/profile/service/PassiveProfileService.java`
- `backend/src/main/java/com/pythontopia/learner/profile/PassiveProfileRevisionListener.java`（事件订阅）
- `backend/src/main/java/com/pythontopia/learner/profile/dto/DimensionDelta.java`
- `backend/src/main/java/com/pythontopia/learner/profile/dto/DimensionUpdateResult.java`
- `backend/src/main/java/com/pythontopia/learner/profile/dto/SelfDimensionRequest.java`
- `backend/src/main/java/com/pythontopia/learner/profile/controller/ProfileController.java`（PUT 自评接口）
- `backend/src/test/java/com/pythontopia/learner/profile/PassiveProfileServiceTest.java`（12 单测）

---

## Wave-3 · Controllers + Stores + 教学三库 + Polish（7 子代理）

### W3-CTRL · 9 个后端 Controller

- `backend/src/main/java/com/pythontopia/world/GameBootstrapController.java` + `GameBootstrapService.java` + `repository/GameBootstrapRepository.java`
- `backend/src/main/java/com/pythontopia/world/npc/`（NpcDialogController/Service/Repository）
- `backend/src/main/java/com/pythontopia/world/WorldAdvanceController.java` + `WorldAdvanceService.java`
- `backend/src/main/java/com/pythontopia/world/inventory/`（Inventory Controller）
- `backend/src/main/java/com/pythontopia/world/skill/`（Skill Controller）
- `backend/src/main/java/com/pythontopia/world/shop/`（Shop Controller，weekday 限购）
- `backend/src/main/java/com/pythontopia/world/calendar/`（Calendar Controller）
- `backend/src/main/java/com/pythontopia/auth/controller/AuthController.java`（追加 refresh token rotate）
- `backend/src/main/java/com/pythontopia/auth/dto/RefreshRequest.java`
- `backend/src/main/java/com/pythontopia/coursepack/controller/CourseExampleController.java` + `dto/CourseExampleDto.java`（46 示例分页）

### W3-STORES · 4 Pinia store + bootstrapGame

- `frontend/src/stores/inventory.ts` / `skill.ts` / `calendar.ts` / `npc.ts` / `saveSlot.ts`
- `frontend/src/stores/world.ts`（追加 `sleepUntilNextDay()` 与 `bootstrapGame()`）
- `frontend/src/api/game.ts`（GameBootstrap client）
- `frontend/src/api/worldInventory.ts`（inventory/skill/shop/calendar API client）
- `frontend/src/api/dto/`（11 个新 DTO：GameBootstrapResponseDto / InventoryItemDto / ItemCatalogEntryDto / LearnerSkillDto / NpcDialogStateDto / PlayerCurrencyDto / SaveSlotDto / ShopDto / SkillNodeDto / WorldCalendarDto / WorldEventDto）

### W3-EDU-LIB · 教学三库页

- `frontend/src/pages/LibraryPage.vue`（561 页课件阅读器 + 7 章树 + RAG 检索）
- `frontend/src/pages/ProblemListPage.vue`（39 题 + KC/难度/状态过滤）
- `frontend/src/pages/ExampleListPage.vue`（46 示例 + KC 分组）
- `frontend/src/api/courseLibrary.ts` + `frontend/src/api/dto/Library.ts`
- `frontend/src/api/course.ts`（追加 example/problem 接口）
- `frontend/src/app/router.ts`（追加 `/library` `/problems` `/examples`）

### W3-QUEST · evidence 强校验 + deep link

- `backend/src/main/java/com/pythontopia/learner/quest/service/QuestService.java`（4 类 evidence 强校验）
- `backend/src/main/java/com/pythontopia/learner/quest/evidence/`（evidence type 校验器）
- `backend/src/main/java/com/pythontopia/learner/quest/dto/QuestCompleteRequest.java`（含 evidence 字段）
- `backend/src/main/java/com/pythontopia/learner/quest/dto/QuestDto.java`（含 required_evidence_type）
- `backend/src/main/java/com/pythontopia/classroom/quest/dto/ClassroomQuestDto.java`（追加 evidence_type）
- `frontend/src/components/board/PathTreeTab.vue`（节点点击 deep link）
- `frontend/src/components/board/ClassroomQuestsTab.vue`（evidence 选择面板）
- `frontend/src/api/dto/Quest.ts` + `dto/ClassroomQuest.ts`
- `backend/src/test/java/com/pythontopia/learner/quest/QuestCompleteWithEvidenceTest.java`（9 用例）
- `frontend/tests/components/PathTreeTab.deeplink.spec.ts` + `ClassroomQuestsTab.evidence.spec.ts`

### W3-POLISH · 天气 + 昼夜 + confetti + BGM crossfade

- `frontend/src/game/scenes/WorldScene.ts`（追加 ParticleEmitter / tween tint / BGM crossfade）
- `frontend/src/utils/celebrate.ts`（confetti 接通任务完成/课程通关/班级第一）
- `frontend/src/utils/celebrate.test.ts`（10 用例）
- `frontend/tests/game/adapters/weather.spec.ts`（12 用例）
- `frontend/tests/game/adapters/daynight.spec.ts`（10 用例）

### W3-DIMS · dimensions JSONB 列（V111）

- `backend/src/main/resources/db/migration/V111__learner_profile_dimensions.sql`（JSONB DEFAULT '{}' NOT NULL + GIN 索引）
- 注：原计划编号 V099，因 V100-V109 已先落而最终落在 V111；功能等价。
- `backend/src/test/java/com/pythontopia/stage04/Stage04PassiveDimensionAcceptanceIntegrationTest.java`（5 用例）

### W3-DOC · AGENTS.md + r12 release notes

- `AGENTS.md`（R-12 解冻条款 + 5 地图约束 + license 链 + 8 维画像 + evidence 强约束）
- `docs/sv-progress.md`（Wave-2/3 段）
- `docs/release/r12/r12_release_notes.md`（首版）
- `docs/wave_subagent_report.md`（Wave-1~3 子代理报告）

---

## Wave-4 · 收尾（5 子代理）

### W4-ANDROID · Android R-12 demo

- `clients/native/android/openapi/pythontopia-api.json`（手写最小 OpenAPI 3.0 spec，8 端点）
- `clients/native/android/openapi/README.md`（替换为自动生成的步骤）
- `clients/native/android/core/data/src/main/kotlin/com/pythontopia/core/data/dto/world/`（10 Kotlin DTO）：
  - `GameBootstrapResponseDto.kt` / `WorldSnapshotDto.kt` / `PlayerStateDto.kt`
  - `PlayerStatePatch.kt`（8 方向枚举与后端 `@Pattern` 对齐）
  - `NpcDialogStateDto.kt` / `NpcDialogEffectDto.kt` / `NpcTalkRequest.kt`
  - `CalendarTodayResponse.kt` / `InventoryItemDto.kt` / `PlayerCurrencyDto.kt`

### W4-CHAPTER-NAV · 章节导航迁移

- `frontend/src/components/world/ChapterNavPanel.vue`（常驻 farm_north UI 面板）
- `frontend/src/data/chapters.ts`（CHAPTERS 数据 + findChapterByKcCode/parseKcNumber/formatKcNumber 工具）
- `frontend/src/pages/WorldPage.vue`（追加 `<ChapterNavPanel v-if="isOnFarm" />`）
- `frontend/tests/components/ChapterNavPanel.spec.ts`（5 用例，含 fake timer hover 节流测试）

### W4-LINT · ESLint 预存清零

- `frontend/.eslintrc.cjs`（`no-restricted-imports` phaser + `@typescript-eslint/no-explicit-any` error；adapter/scene/useAudio 例外）
- 配合删除：`frontend/src/game/scenes/PlaceholderScene.ts`（残留 unused import 来源）

### W4-E2E · Beta 演示 checklist

- `docs/beta_demo_checklist.md`（170 行：前置条件 + 学生 A1-A10 + 教师 B1-B4 + 验证通过标准 + 签名格）
- 预留引用路径：`scripts/r12-smoke-test.sh`（shell 脚本本体本轮未交付，已转入 R-13 遗留 TODO，见 `docs/sv-progress.md`）

### W4-DOC · 最终汇总（本子代理）

- `docs/sv-progress.md`（追加 Wave-4 收尾报告 + 自测表 + R-13 遗留 TODO）
- `docs/release/r12/r12_release_notes.md`（追加 23 子代理索引 + 数据指标表）
- `docs/project/CHANGELOG.md`（顶部插入 R-12 段，含视觉/玩法/RPG 存档/教育闭环/Android/工程/遗留 6 大类）
- `docs/release/r12/r12_subagent_files.md`（本文档）

---

## 跨子代理共用基础设施

下列文件不属于任何单一子代理，是多 Wave 共用基础设施：

- `frontend/tests/_helpers/phaserMock.ts`（W2-FIX 引入，被全部 game adapter / scene 测试 setup 加载）
- `frontend/vitest.config.ts`（统一 vitest 配置，含 phaserMock setupFiles + path alias）
- `data/npcs/index.json` / `frontend/public/data/npcs/`（W2-NPC 产出，W2-DIALOG/W3-CTRL/W4-ANDROID 共用）
- `backend/src/main/resources/db/migration/V100__game_bootstrap_view.sql`（W2-MIGRATE 写视图基座 + W3-CTRL 注入 Repository）
- `AGENTS.md`（W3-DOC 写 R-12 解冻条款 + W4-DOC 保持现状不再追加）
- `docs/wave_subagent_report.md`（W3-DOC 写 Wave-1~3 逐子代理详情，含耗时统计）
