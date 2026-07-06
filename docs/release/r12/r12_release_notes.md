# R-12 Release Notes — Pythontopia 星露谷改造

## 概览

R-12 把单图小镇升级为五分块开放世界：中央 hub + 北农场 + 南矿洞 + 东校园 + 西港口，引入 20 NPC 对话/友好度/礼物系统、真·RPG 存档（背包/技能/对话/日历/经济/多 slot）和 8 维画像直写。

## 主要变更

### 美术 / 资产

- 切换到 CC0 + LPC CC-BY-SA + OFL 全离线资产链；inbox 13000 文件、359 MB。
- Phaser Tilemap + TilemapLayer 替代手撸铺地。
- Ark Pixel 中文像素字体 + LPC Sara 4 向 walk anim + tween 平滑移动 + 阴影 ellipse。

### 关卡 / 玩法

- 五张地图分块（town_hub / farm_north / mine_south / campus_east / port_west）。
- 边缘 exit fade 过场切换 Scene。
- 玩家走过出口自动跳图，回到中央 hub 是核心循环。
- 8 方向移动（WASD/F 交互，原神风），后端 4 方向兼容降级。

### NPC

- 6 教育核心 + 6 学生 + 8 生活 = 20 NPC。
- 每 NPC 至少 5 个对话节点、portrait、周调度、生日、友好度 0-100。
- DialogTree 9-slice + 打字机 + 选项 + 友好度心形 UI。
- 友好度通过对话 effects 真改后端 npc_dialog_state.friendship。
- 送礼系统：item_catalog.gift_tier_map 决定 love/like/neutral/dislike/hate。

### 后端 RPG 存档

- V100-V109 + V110 共 11 个 migration。
- GameBootstrap 一次性返回 world + player + crops + quests + mastery + profile + path + inventory + skills + currency + npc_states + active_save_slot。
- 多存档槽（最多 6 slot），active 切换。
- 弱一日节奏：手动 sleep 推进 day_index + 天气随机 + NPC schedule 切换。
- 商人 shop：5 商品 + stock + weekday 限购。
- 8 维画像直写：练习/Debug/资源 4 事件订阅 + 学生自评 4 主观维度 PUT。

### 教育闭环强化

- LibraryPage 561 页课件阅读器 + RAG 检索。
- ProblemListPage 39 题题库（按 KC/难度/状态过滤）。
- ExampleListPage 46 示例库。
- 任务 evidence 强校验：必须传 submission_id 等证据。
- 学习路径 PathTreeTab 节点点击 deep link 到 KC 田地 / 题库 / 课件页。

### 测试 / 工程

- 前端 vitest 33+ 测试文件 / 85+ 用例全过。
- 后端 mvn compile 240 源文件 BUILD SUCCESS。
- frontend/tests/_helpers/phaserMock.ts 全局 mock Phaser 解决 jsdom canvas 问题。

## 已知遗留

- 章节导航（原 FieldPage 内）尚未迁移到 WorldScene farm_north UI 面板。
- Android OpenAPI 重生成 + hub demo 待 R-13。
- RAG 检索需要服务端 `pythontopia-rag` 真接通（目前前端可能 mock）。
- 班级任务深层 quest 创建表单（教师）待补。

## 演示路径（Beta 候选）

1. 登录（账号示例 student@demo）
2. 加载存档 → 默认进 town_hub spawn 位
3. 走北门 → 进 farm_north 看知识田
4. 走南门 → 进 mine_south
5. 走东门 → 进 campus_east，点图书馆 → /library
6. 走西门 → 进 port_west，与商人对话买道具
7. 与镇长 Py 对话 → 周复盘建议
8. 完成任务（提交 submission）→ 系统校验 evidence → 任务通过
9. 进自己房间睡觉 → 推进一天，第二天 NPC 在新位置

## 教师演示路径

1. 登录教师账号
2. 创建班级 → 邀请学生
3. 创建班级任务（绑定 KC + evidence_type）
4. 班长 NPC（port_west）展示班级公告板
5. 学生在公告板领取 → 完成 → 教师 KC 热力图实时更新

## R-12 子代理产出索引（共 23 个并发子代理）

> 详细文件清单见 `docs/release/r12/r12_subagent_files.md`；逐子代理交付细节与耗时见 `docs/wave_subagent_report.md`（Wave-1~3）+ `docs/sv-progress.md` Wave-4 收尾段。

### Wave-1（M1 资产 + 引擎）

| 代号 | 角色 | 产出概要 |
| --- | --- | --- |
| M1-A | 资产同步脚本 | `scripts/sync-assets.mjs` + `LICENSE.md` 重写 + `.gitattributes` LFS 规则 |
| M1-B | Phaser 引擎重构 | Game 单例 + Arcade Physics + Tilemap/TilemapLayer + tween + 阴影 ellipse + LPC 4 向 walk anim + BitmapFont |
| M1-C | 音频统一栈 | `composables/useAudio.ts` 统一 BGM/SFX 入口，删除散装 `bgm.ts`/`sfx.ts` |
| M1-D | Credits 页 | `CreditsPage.vue` + `data/credits.ts`，CC0/CC-BY/CC-BY-SA/OFL 四组分类渲染 |

### Wave-2（M2-M4 核心）

| 代号 | 角色 | 产出概要 |
| --- | --- | --- |
| W2-FIX | 八方向 + 测试修复 | `input.ts` 8 方向 + F 键 + 全局 `phaserMock.ts` 解决 jsdom canvas 错 |
| W2-MAPS | 5 张 Tiled 地图 | `scripts/generate-maps.mjs` 程序化生成 town_hub 50×40 / farm_north 40×40 / mine_south 30×30 / campus_east 40×30 / port_west 35×25（含 collision/exit/spawn 层） |
| W2-WORLD | WorldScene + 路由合并 | 495 行通用 WorldScene + WorldCanvas/WorldPage + `/town`/`/field`/`/bug-mine` → `/world` |
| W2-NPC | 20 NPC + 140 对话节点 | 20 个 `data/npcs/<key>/{meta,dialog}.json` + `index.json` + 21 portraits |
| W2-DIALOG | DialogTree.vue | 9-slice + 打字机 + portrait + ESC + 友好度心形；`sync-npc-data.mjs` 同步钩子 |
| W2-MIGRATE | V100-V109 | 10 个 SQL + 12 Java records/repos（npc/inventory/currency/skill/save） |
| W2-PROFILE | 8 维直写 | `PassiveProfileService` 监听 4 事件 + 学生 PUT 自评 |

### Wave-3（M4-M5 闭环）

| 代号 | 角色 | 产出概要 |
| --- | --- | --- |
| W3-CTRL | 后端 9 Controllers | GameBootstrap / NpcDialog / WorldAdvance / Inventory / Skill / Shop / Calendar / AuthRefresh / CourseExampleIndex |
| W3-STORES | 4 Pinia store + bootstrap | inventory / skill / calendar / npc / saveSlot store + `sleepUntilNextDay` + `bootstrapGame` |
| W3-EDU-LIB | Library + Problem + Example | 3 页接通 561 页 / 39 题 / 46 例真数据；按 KC/难度/状态/章节过滤 |
| W3-QUEST | evidence 校验 + deep link | QuestService 4 类 evidence 强校验 + `PathTreeTab` 节点 deep link 跳目标场景 |
| W3-POLISH | 天气 + 昼夜 + confetti | WorldScene ParticleEmitter + tween tint + `celebrate.ts` 接通 + BGM crossfade |
| W3-DIMS | V111 dimensions JSONB 列 | V111 + LearnerProfile entity 加 `Map<String,Double> dimensions` + GIN 索引（注：原计划编号 V099，最终落在 V111） |
| W3-DOC | AGENTS.md + r12 release notes | R-12 解冻条款 + 5 地图约束 + 8 维画像 / evidence 强约束写入 AGENTS.md |

### Wave-4（收尾）

| 代号 | 角色 | 产出概要 |
| --- | --- | --- |
| W4-ANDROID | Android R-12 demo | 手写最小 OpenAPI 3.0 spec（8 端点）+ 10 Kotlin world DTO；`PlayerStatePatch` 8 方向与后端 `@Pattern` 对齐 |
| W4-CHAPTER-NAV | 章节导航迁移 | `components/world/ChapterNavPanel.vue` 常驻 farm_north + 5 单测 |
| W4-LINT | ESLint 预存清零 | `no-restricted-imports` 拦截业务层 phaser import + `no-explicit-any` 业务层 error；R-12 末态 `npm run lint` 0 error |
| W4-E2E | beta 演示 checklist | `docs/beta_demo_checklist.md`（170 行 / 学生 9 步 + 教师 4 步）；预留 `scripts/r12-smoke-test.sh` 引用路径，shell 脚本本体已转入 R-13 遗留 TODO |
| W4-DOC | 本文档 | sv-progress 末段 + r12 release notes 末段 + CHANGELOG R-12 段 + `docs/release/r12/r12_subagent_files.md` |

## 数据指标

> 数字来源：仓库实际文件统计 + `@Test` / `it/test` 直接计数；inbox 资产数沿用 Wave-1 起既定的「13000+」量级描述（实际 inbox 计文件级 ≈ 21k，含子目录变体；frontend 入库后 `public/assets/pixel/` 约 1997 个文件）。

| 项 | 数量 |
| --- | --- |
| 资产 inbox 文件 | 13000+（含 LPC / Kenney / OGA / Ark Pixel 变体） |
| 资产 inbox 体积 | 约 359 MB |
| 前端 `public/assets/pixel/` 已入库文件 | 约 1997 |
| Tiled 地图 | 5 张（town_hub 50×40 / farm_north 40×40 / mine_south 30×30 / campus_east 40×30 / port_west 35×25） |
| NPC | 20 个 |
| 对话节点 | 140+（W2-NPC 单测口径） |
| 后端 migration | V001-V113，共 40 个（R-12 新增 V100-V113 共 14 个） |
| 后端 Java 源文件 | 288（R-12 前 240 → R-12 末 288） |
| 后端测试类 / `@Test` 数 | 34 类 / 140 用例 |
| 前端组件 + 页面（新增） | DialogTree / LibraryPage / ProblemListPage / ExampleListPage / ChapterNavPanel / WorldCanvas / WorldPage（外加 5 store + 11 DTO） |
| 前端测试文件 / 用例数 | 45 文件 / 162 `it/test` 顶层用例 |
| 子代理并发执行 | 23 个（Wave-1 4 + Wave-2 7 + Wave-3 7 + Wave-4 5） |
| 全部完成耗时 | 约 2 小时（并发墙钟），约 14 小时（串行累加） |
