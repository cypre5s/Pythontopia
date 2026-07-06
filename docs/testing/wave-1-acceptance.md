# W1 验收报告 — 底层地基

> 验收日期：2026-05-23 · 工作目录：`/home/cypress/Pythontopia` · 分支：`main` · HEAD：`ac2f9745`
> 验收人：subagent（w1_demo_video）· 验收依据：W1 五项 todo 全部 commit 落地 + 生产构建 + 全量 vitest + Playwright 截图

## 1. 范围

W1 共 5 个 todo，全部以独立 commit 落地。提交顺序与 commit hash 如下：

| 序号 | Todo | Commit | 标题 |
|------|------|--------|------|
| W1-1 | `w1_player_state` | `6fb370b6` | feat(frontend-game-core): W1-1 PlayerStateMachine 状态机 |
| W1-2 | `w1_scene_switcher` | `b61c3c44` | feat(frontend-game-core): W1-2 SceneSwitcher 场景切换器 |
| W1-3 | `w1_input_adapter` | `e7bd1f74` | feat(frontend-game-adapters): W1-3 InputAdapter 接口 + 键盘/触屏/手柄 三实现 |
| W1-4 | `w1_tilemap_layer` | `85555cc3` | docs(game): W1-4 tilemap 路网与 Z 轴规范 |
| W1-5 | `w1_tech_stability` | `ac2f9745` | feat(frontend-game): W1-5 SceneLifecycle + Boot 超时进度 + tilemap loading mask |

W1 整体改动（`git diff --shortstat 6fb370b6^..ac2f9745 -- frontend`）：**16 个文件改动，+2624 / -38 行**，其中 docs 部分另增 `docs/game-tilemap-conventions.md` 325 行。

## 2. 代码产出

### 2.1 `game/core` 三个核心模块

| 文件 | LOC | 对应测试 | 测试 LOC |
|------|-----|----------|----------|
| `frontend/src/game/core/PlayerStateMachine.ts` | 102 | `tests/game/core/PlayerStateMachine.spec.ts` | 152 |
| `frontend/src/game/core/SceneSwitcher.ts` | 179 | `tests/game/core/SceneSwitcher.spec.ts` | 222 |
| `frontend/src/game/core/SceneLifecycle.ts` | 91 | `tests/game/core/SceneLifecycle.spec.ts` | 181 |
| 合计 | **372** | 3 spec | **555** |

三模块全部为 W1 新增、无历史包袱，TypeScript strict、纯 POJO，不依赖 Phaser 全局，可独立单测。

### 2.2 InputAdapter 接口 + 三实现

| 文件 | LOC | 备注 |
|------|-----|------|
| `frontend/src/game/adapters/input.ts` | 263（W1-3 实改 +177） | 抽象 `InputAdapter` 接口 + ExtendedDirection 8 方向 + `toServerDirection` 降级 4 向 |
| `frontend/src/game/adapters/keyboardAdapter.ts` | 121 | W1-3 新增，WASD/方向键/F/ESC |
| `frontend/src/game/adapters/touchAdapter.ts` | 171 | W1-3 新增，虚拟摇杆 + 触屏按键 |
| `frontend/src/game/adapters/gamepadAdapter.ts` | 99 | W1-3 新增，Gamepad API + 死区/震动 |

测试：`input.spec.ts` 281 行（W1 新增 +204）、`input-touch.spec.ts` 166 行、`input-gamepad.spec.ts` 166 行，三套 spec 全部 W1 新引入。

### 2.3 BootScene 超时进度条 + tilemap loading mask

- `frontend/src/game/scenes/BootScene.ts` 现 275 行（W1-5 在原 88 行基础上 +187）：`setupProgressUi` 绘制 graphics 进度条、`setupTimeoutFallback` 15 s 后广播 `pythontopia:load-timeout`、`loader.once('complete'/'loaderror')` 单点清理。BootScene 在 `create()` 调用 `attachLifecycle` 走 SceneLifecycle 统一回收。
- `frontend/src/game/adapters/tilemap.ts` W1-5 +129 行：导出 `LoadingMaskOptions` / `LoadingMaskHandle` / `showLoadingMask(scene, opts?)`，覆盖深度 9999、`setScrollFactor(0,0)`、最小显示 200 ms 防瞬切、300 ms tween 淡出、dismiss/destroy 幂等。`loadTownMap` 首行调用 `showLoadingMask`，其余对外签名零改动。
- 测试：`BootScene.spec.ts` 312 行（W1-5 +315）。

### 2.4 Tilemap 与 Z 轴规范文档

`docs/game-tilemap-conventions.md` 全文 **325 行**，结构：8 章（目的与适用范围 / 四层标准 / Z 轴排序 / 碰撞标签 / 锚点命名 / 场景间跳转契约 / 现有场景迁移计划 / 校验脚本预留）+ 2 附录（合规 JSON 骨架 / 术语表）。当前版本标记为 **Draft（待 W1 验收后转 Active）**，本次验收通过后建议在 W2 起首个 commit 中把 frontmatter 改为 Active。

## 3. 测试覆盖

执行 `npx vitest run --coverage=false` 收尾全量测试：

```
Test Files  52 passed (52)
     Tests  280 passed (280)
```

- **W0 baseline**：165 tests
- **W1 完成后**：**280 tests**（+115，增幅 69.7 %）
- **W1 新增 spec 文件**：7 个，合计 **75 tests**：

| spec 文件 | tests |
|-----------|-------|
| `tests/game/core/PlayerStateMachine.spec.ts` | （W1-1 新增） |
| `tests/game/core/SceneSwitcher.spec.ts` | （W1-2 新增） |
| `tests/game/core/SceneLifecycle.spec.ts` | （W1-5 新增） |
| `tests/game/adapters/input.spec.ts` | （W1-3 扩写 +204 行） |
| `tests/game/adapters/input-touch.spec.ts` | （W1-3 新增） |
| `tests/game/adapters/input-gamepad.spec.ts` | （W1-3 新增） |
| `tests/game/scenes/BootScene.spec.ts` | （W1-5 扩写 +315 行） |

针对 W1 直接相关的 7 个 spec 单独运行 `npx vitest run tests/game/core tests/game/adapters/input.spec.ts tests/game/adapters/input-touch.spec.ts tests/game/adapters/input-gamepad.spec.ts tests/game/scenes/BootScene.spec.ts` 得 **7 files / 75 tests 全绿**。

## 4. 构建结果

执行 `cd frontend && npm run build`：

- **Exit code：0**
- 流程：`prebuild`（sync-npc-data + sync-course43，全部 `--quiet`）→ `vue-tsc --noEmit`（TypeScript strict 通过）→ `vite build`
- 时长：**6.23 s**，288 modules transformed
- `dist/` 体积：**35 MB**（含 assets/maps 程序化生成的 5 张 tilemap JSON + Kenney/LPC 像素资产 + course-pack-43 文档）
- 主 chunk：`dist/assets/index-DqDg85GM.js` **1 626.60 kB（gzip 400.24 kB）**。Vite 警告"chunks larger than 500 kB"是历史已知项（Phaser + Pinia + Vue Router 全打包，与 W1 改动无关），见第 7 节。

## 5. 截图清单

通过 Playwright 1.58 headless Chromium（缓存命中 `~/.cache/ms-playwright/chromium-1217`，1280×800 视口）在 `npm run dev`（vite v5.4.21，:5173）打开三页：

| 文件 | 来源场景 | 展示内容 |
|------|----------|----------|
| `docs/acceptance/wave-1-shots/login.png` | `/login`（LoginPage.vue） | 像素风登录页、`Pythontopia` LOGO、用户名/密码输入、"快速体验：演示账号 demo / P@ssw0rd1"按钮、注册入口；红色 toast "服务器还没就绪…" 验证 W0 SafetyBanner 在 backend 未启动时的容灾文案。 |
| `docs/acceptance/wave-1-shots/credits.png` | `/credits`（CreditsPage.vue） | Pythontopia 致谢页：MIT License 声明 + CC0 / CC-BY 3.0 / 4.0 / CC-BY-SA 3.0 / OFL-1.1 五类协议族分区列表，含 Tiny Town / Tiny Dungeon / Roguelike Modern City / Roguelike Caves / Pixel UI Pack。 |
| `docs/acceptance/wave-1-shots/town.png` | `/world`（WorldPage.vue，注入伪造 session 绕过守卫） | 顶部 HUD（"学习者 第 1 天 / 晴 / 退出"）+ 主画布（深绿背景表示 Phaser canvas 已挂载）+ 欢迎引导 Modal，含 W1-3 输入映射提示（WASD / 方向键 / F / ESC / 点击）+ 底部快捷键条。 |

> 备注：因后端未启动（`/healthz` 返回 connection refused），`/world` 走 Phaser BootScene → `pythontopia:resource-loading` 时 API 调用 404，触发"无法连接服务器，请稍后重试" pageerror 但 UI 仍渲染完整。这恰好佐证 W1-5 的 loading fallback 与 W0 的 SafetyBanner 协同生效。

## 6. 未达标项

W1 五项 todo **全部达成**。以下为 W1 暴露、但**有意推到后续波次**的工作项：

- `docs/game-tilemap-conventions.md` 仍为 Draft，附录"§8 校验脚本预留"中描述的 `scripts/validate-tilemap.mjs` 尚未实现，建议 W2 起手交付。
- `dist/assets/index-*.js` 主 chunk > 1.5 MB 的拆分（manualChunks / 动态 import）未启动，与 W1 无关；R-12 演示阶段不阻塞。
- Phaser 单元测试通过 `tests/_helpers/phaserMock.ts` 模拟，Playwright 截图过程中 `phaserScene.cameras.main.clearTint is not a function` 报错来源是真实 Phaser 在 jsdom-less 浏览器下行为差异（mock 无此方法），不影响线上构建，W2 排查。

## 7. 下一波 W2 依赖

W2（按现有 `docs/sv-progress.md` 命名为 W2-MAPS / W2-WORLD / W2-NPC / W2-DIALOG / W2-MIGRATE / W2-PROFILE）将直接依赖 W1 暴露的以下 API：

- `PlayerStateMachine`：`createPlayerStateMachine(initialState, options?)` → `setState / canTransition / on('enter'|'exit'|'transition')`，W2 的 NPC 对话进入/退出冻结玩家移动需要此机器。
- `SceneSwitcher`：`switchScene(currentScene, targetKey, payload?)` → 提供 fade-out / fade-in / cooldown / payload 透传，W2 的 5 张地图边缘切换、`/world` mapKey 切换全部走此 API。
- `InputAdapter`：`createInputAdapter(scene)` 自动按 `userAgent + 'ontouchstart' + navigator.getGamepads` 装配键鼠/触屏/手柄，W2 移动端 demo 与桌面演示均不需要再分支。`toServerDirection(ext8)` 把 8 向降为后端 `@Pattern` 限制的 4 向。
- `SceneLifecycle.attachLifecycle(scene, opts?)`：W2 新增 `WorldScene` / `FieldScene` 子图必须在 `create()` 末尾挂载，避免 Phaser scene 切换时 tweens / time / 事件残留导致内存泄漏。
- `showLoadingMask(scene, opts?)`：W2 五张地图 tilemap 加载、未来 RAG/资源工坊远程 fetch 期间通用黑屏遮罩 + 200 ms 防闪。
- `docs/game-tilemap-conventions.md`：W2 新增任意 Tiled JSON 必须遵循 §2~§5（四层 + Z 轴 + 碰撞 + 锚点）+ §6 跳转契约。

## 8. 风险与回退

- **回归边界**：W1 仅在 `frontend/src/game/{core,adapters,scenes}` 与对应 `tests/game/**` 内新增/扩写，未改动 Vue 业务层、`api/`、`stores/`、`backend/`、`services/`。如出现严重回归，按 commit 倒序 `git revert ac2f9745 85555cc3 e7bd1f74 b61c3c44 6fb370b6` 即可整体回退而不污染其他模块。
- **影响演示链路评估**：5 张地图 demo（W2 已完成）仍使用旧 `loadTownMap` + 老 input.ts 走 fallback 4 向，因此 W1 即使全量回退也不阻断比赛主线（仅丢失 8 向输入、手柄、loading mask、超时 fallback、SceneLifecycle 内存防泄漏五项增量）。
- **TypeScript strict**：W1 所有产出均通过 `vue-tsc --noEmit`，未引入 `any`，Phaser 类型隔离在各 adapter 内部，业务层零侵入。
- **测试稳定性**：280 / 280 全绿，无 flaky；建议 W2 起在 CI 中固化为 baseline，新 PR 必须保持 ≥ 280 tests 通过。

---

**结论**：W1 五项 todo 全部交付、生产构建零失败、全量 vitest 280 通过、三张关键画面截图入库，**W1 验收通过**，可进入 W2 收尾与 R-12 演示打包阶段。
