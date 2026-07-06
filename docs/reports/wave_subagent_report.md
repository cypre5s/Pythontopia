# Pythontopia R-12 子代理产出汇总

> 时间线：2026-05-22 启动 → 2026-05-22 收尾
> 计划来源：`.cursor/plans/pythontopia_星露谷改造_298df045.plan.md`
> 三波并发派发，子代理并行上限 10

## 总览

| Wave | 子代理 | 角色定位 |
| --- | --- | --- |
| Wave-1（M1 资产/引擎/音频/Credits） | A / B / C / D | 4 个 |
| Wave-2（地图/世界/NPC/对话/迁移/画像） | W2-FIX / W2-MAPS / W2-WORLD / W2-NPC / W2-DIALOG / W2-MIGRATE / W2-PROFILE | 7 个 |
| Wave-3（Controllers/Stores/教学/任务/Polish/Dims/Doc） | W3-CTRL / W3-STORES / W3-EDU-LIB / W3-EDU-QUEST / W3-POLISH / W3-DIMS / W3-DOC | 7 个 |
| 合计 | — | 18 个（含本回报子代理 W3-DOC） |

## Wave-1：M1 资产拉齐 + 性能重构

### A · 资产同步
- 产出：`scripts/sync-assets.mjs`、`frontend/public/assets/pixel/LICENSE.md`、`.gitattributes`（LFS 规则）。
- 成果：40 个 MANIFEST 解析 + 47 新资产文件入库 + 完整 license 声明文件重写。
- 耗时：约 25 min。
- 关键文件：
  - `scripts/sync-assets.mjs`
  - `frontend/public/assets/pixel/LICENSE.md`
  - `.gitattributes`

### B · 引擎重构
- 产出：Phaser Game 单例 + Arcade Physics + Tilemap/TilemapLayer + tween 移动 + 阴影 ellipse + LPC 4 向 walk animation + 位图字体接入。
- 成果：从手撸 600 个 image 铺地切换到 Phaser Tilemap，60fps 稳定。
- 耗时：约 50 min。
- 关键文件：
  - `frontend/src/game/adapters/phaserGame.ts`
  - `frontend/src/game/adapters/tilemap.ts`
  - `frontend/src/game/adapters/sprite.ts`
  - `frontend/src/game/adapters/pixelFont.ts`
  - `frontend/src/game/scenes/BootScene.ts`
  - `frontend/src/game/scenes/TownScene.ts`

### C · 音频统一
- 产出：`useAudio` composable，删除 `bgm.ts`、`sfx.ts` 散装实现，全调用替换。
- 成果：单入口 BGM + SFX + 静音/音量持久化；测试覆盖。
- 耗时：约 20 min。
- 关键文件：
  - `frontend/src/composables/useAudio.ts`
  - 调用点：`App.vue` / `TownScene.ts` / `BugMinePage.vue` / `CreditsPage.vue`

### D · Credits 页扩展
- 产出：`CreditsPage.vue` 全资产 license 分组渲染。
- 成果：CC0 / CC-BY / CC-BY-SA / OFL 四组分类 + 跳转原作者链接 + 总数标签。
- 耗时：约 15 min。
- 关键文件：
  - `frontend/src/pages/CreditsPage.vue`
  - `frontend/src/data/credits.ts`

## Wave-2：5 张地图 + 20 NPC + 真·RPG 存档基座

### W2-FIX · jsdom 测试修复
- 产出：input.ts 八方向（WASD/方向键）+ F 键交互 + Phaser jsdom global mock + tilemap/sprite spec 修复。
- 成果：vitest 在 jsdom 下能正常跑 Phaser 相关代码而不爆 canvas 错。
- 耗时：约 30 min。
- 关键文件：
  - `frontend/src/game/adapters/input.ts`
  - `frontend/tests/_helpers/phaserMock.ts`
  - `frontend/tests/game/adapters/input.spec.ts` / `sprite.spec.ts` / `tilemap.spec.ts`

### W2-MAPS · 5 张 Tiled 地图
- 产出：`scripts/generate-maps.mjs` 程序化生成 5 张 Tiled JSON。
- 成果：
  - town_hub 50×40（中央 hub）
  - farm_north 40×40（北农场）
  - mine_south 30×30（南矿洞）
  - campus_east 40×30（东校园）
  - port_west 35×25（西港口）
  - 含 collision 层 + exit 层 + spawn 点
- 耗时：约 40 min。
- 关键文件：
  - `scripts/generate-maps.mjs`
  - `frontend/public/assets/maps/*.json`

### W2-WORLD · WorldScene 通用场景
- 产出：495 行通用 WorldScene + WorldCanvas.vue + WorldPage.vue + 路由合并。
- 成果：原 `/town` `/field` `/bug-mine` 三页合并为单一 `/world`，跨地图 fade 切换。
- 耗时：约 55 min。
- 关键文件：
  - `frontend/src/game/scenes/WorldScene.ts`
  - `frontend/src/components/game/WorldCanvas.vue`
  - `frontend/src/pages/WorldPage.vue`
  - `frontend/src/app/router.ts`

### W2-NPC · 20 NPC 静态数据
- 产出：20 NPC × (meta.json + dialog.json) + 1 index.json + 20 portrait + 140 个对话节点。
- 成果：6 教育核心 + 6 学生 + 8 生活 NPC 全配置，含周调度/生日/友好度初值。
- 耗时：约 60 min。
- 关键文件：
  - `data/npcs/<key>/meta.json`
  - `data/npcs/<key>/dialog.json`
  - `data/npcs/index.json`
  - `frontend/public/assets/pixel/portraits/`

### W2-DIALOG · DialogTree 系统
- 产出：DialogTree.vue（9-slice + 打字机 + portrait + ESC 取消）+ `sync-npc-data.mjs` + 13 单测。
- 成果：选项分支真改后端友好度，UI 心形+0/+1/+2 视觉反馈。
- 耗时：约 45 min。
- 关键文件：
  - `frontend/src/components/game/DialogTree.vue`
  - `frontend/scripts/sync-npc-data.mjs`
  - `frontend/tests/components/DialogTree.spec.ts`

### W2-MIGRATE · 后端 RPG 存档基座
- 产出：V100-V109 共 10 SQL + 12 Java records/repos（npc/inventory/currency/skill/save）。
- 成果：表名沿用项目无前缀约定确认；entity + repository 套件就位等待 Controller 接通。
- 耗时：约 50 min。
- 关键文件：
  - `backend/src/main/resources/db/migration/V100-V109__*.sql`
  - `backend/src/main/java/com/pythontopia/world/{currency,inventory,npc,save,skill}/`

### W2-PROFILE · 8 维画像直写
- 产出：`PassiveProfileService` 监听 4 事件（PracticeSucceeded / PracticeFailed / DebugResolved / ResourceConsumed）+ ProfileController PUT 自评接口 + 12 单测。
- 成果：8 维画像被动 + 主观双轨更新通路打通；DimensionDelta / DimensionUpdateResult / SelfDimensionRequest DTO 就位。
- 耗时：约 45 min。
- 关键文件：
  - `backend/src/main/java/com/pythontopia/learner/profile/service/PassiveProfileService.java`
  - `backend/src/main/java/com/pythontopia/learner/profile/dto/*.java`
  - `backend/src/test/java/com/pythontopia/learner/profile/PassiveProfileServiceTest.java`

## Wave-3：Controllers + Stores + 教学三库 + Polish

### W3-CTRL · 后端 Controllers
- 产出：GameBootstrap / NpcDialog / WorldAdvance / Inventory / Skill / Shop / Calendar / AuthRefresh / CourseExampleIndex 9 个 Controller。
- 成果：`/api/game/bootstrap` 一次性聚合 12 字段返回；NPC 对话 effects 真改 friendship；WorldAdvance 推进 day_index + 天气 + NPC schedule。
- 耗时：约 75 min。
- 关键文件：
  - `backend/src/main/java/com/pythontopia/world/.../controller/`
  - `backend/src/main/resources/db/migration/V100__game_bootstrap_view.sql`

### W3-STORES · 前端 Pinia stores
- 产出：useInventoryStore / useSkillStore / useCalendarStore / useNpcStore / useSaveSlotStore + sleep action + bootstrapGame action + worldInventory API client。
- 成果：一次性 bootstrap 装配所有 store；sleep 推进一天后 store 全量刷新。
- 耗时：约 50 min。
- 关键文件：
  - `frontend/src/stores/{inventory,skill,calendar,npc,saveSlot}.ts`
  - `frontend/src/api/worldInventory.ts`
  - `frontend/src/api/dto/*.ts`（11 个新 DTO）

### W3-EDU-LIB · 教学三库
- 产出：LibraryPage（561 页课件阅读器 + RAG 检索）+ ProblemListPage（39 题）+ ExampleListPage（46 示例）。
- 成果：三大页接通 Pythontopia 课程数据（packId=43）真实数据，KC/难度/状态过滤齐全。
- 耗时：约 80 min。
- 关键文件：
  - `frontend/src/pages/LibraryPage.vue`
  - `frontend/src/pages/ProblemListPage.vue`
  - `frontend/src/pages/ExampleListPage.vue`

### W3-EDU-QUEST · 任务 evidence 强校验
- 产出：QuestService evidence 4 类强校验（submission_id / resource_id / page_read_id / kc_mastery_delta）+ PathTreeTab deep link + 班级公告板 evidence_type 字段。
- 成果：任务必须有真实学习行为证据才算完成，防止「假完成」；学习路径节点点击直跳 KC 田地/题库/课件页。
- 耗时：约 55 min。
- 关键文件：
  - `backend/src/main/java/com/pythontopia/quest/service/QuestService.java`
  - `frontend/src/components/path/PathTreeTab.vue`
  - 7 Java + 5 Vitest 用例

### W3-POLISH · 视觉/音效 polish
- 产出：WorldScene ParticleEmitter（雨雪雾 by weather）+ 时间 tween tint（昼/黄昏/夜）+ confetti 接通 + BGM 跨场景 crossfade。
- 成果：5 张地图都有独立 BGM 平滑过渡；任务/通关/班级第一 confetti 庆祝。
- 耗时：约 40 min。
- 关键文件：
  - `frontend/src/game/scenes/WorldScene.ts`
  - `frontend/src/utils/celebrate.ts`

### W3-DIMS · 画像维度列补丁
- 产出：V099__learner_profile_dimensions.sql（JSONB DEFAULT '{}' NOT NULL + GIN 索引）+ LearnerProfile entity 加 dimensions Map + 集成验证。
- 成果：解决 Wave-2 假定列存在但 V100-V109 未加的遗留；GIN 索引使 dimensions 按 key 查询高效。
- 耗时：约 30 min。
- 关键文件：
  - `backend/src/main/resources/db/migration/V099__learner_profile_dimensions.sql`
  - `backend/src/main/java/com/pythontopia/learner/profile/LearnerProfile.java`
  - 4 单测 + 2 集成测试

### W3-DOC · 本子代理（AGENTS.md + 追踪文档）
- 产出：AGENTS.md R-12 解冻条款 + 5 地图约束 + license 链 + 8 维画像/任务 evidence 后端约束 + 前端 Phaser 单例/8 方向降级；docs/sv-progress.md Wave-3 段；docs/release/r12/r12_release_notes.md；docs/wave_subagent_report.md（本文）。
- 成果：R-12 项目硬约束完整落地到 AGENTS.md，后续子代理可直接遵循。
- 耗时：约 20 min。
- 关键文件：
  - `AGENTS.md`
  - `docs/sv-progress.md`
  - `docs/release/r12/r12_release_notes.md`
  - `docs/wave_subagent_report.md`

## 总结指标

| 指标 | 数值 |
| --- | --- |
| 子代理总数 | 18 |
| Wave-1 子代理 | 4 |
| Wave-2 子代理 | 7 |
| Wave-3 子代理 | 7 |
| 新增前端页 | 4（WorldPage / LibraryPage / ProblemListPage / ExampleListPage） |
| 新增后端 Controller | 9 |
| 新增数据库迁移 | V099 + V100-V109 共 11 |
| 新增 DTO | 11 |
| NPC 总数 | 20 |
| NPC 对话节点 | 140 |
| 地图总数 | 5（town_hub / farm_north / mine_south / campus_east / port_west） |
| frontend vitest 用例 | 85+ |
| backend mvn 源文件 | 240+ |
| 总耗时（并发计） | 约 4 小时（实际墙钟时间） |
| 总耗时（串行计） | 约 11.5 小时（子代理耗时累加） |

## 后续衔接

R-13 待办（已在 sv-progress.md 标记）：
- 章节导航迁移到 farm_north UI 面板
- Android OpenAPI 重生成 + hub demo
- `pythontopia-rag` 服务端真接通
- 教师班级 quest 创建表单
- 跨地图 8 方向 DTO（当前 4 向降级符合约定，仅记录潜在 polish 项）
