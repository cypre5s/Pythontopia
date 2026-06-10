# 阶段 03 — 前端骨架与 Phaser 小镇 MVP

## 1. 时间窗口与定位

- 建议日期：2026-05-24 ~ 2026-05-27（第 1 周第 4-7 天，4 天，可与阶段 02 后半部分并行）。
- 角色：1 名前端 + 1 名 Phaser/像素美术辅助；后端负责人需配合联调登录与世界会话。
- 定位：前端项目的**地基阶段**。必须一次性把 TypeScript strict、Pinia 分片、Vitest、API 客户端、Phaser adapter 隔离等"工程债红线"写进代码，让后续阶段无法越界。

## 2. 本阶段目标

- 在 `/home/cypress/Pythontopia/frontend/` 把阶段 00 的脚手架升级为可用 SPA：
  - 路由：登录、注册、小镇主场景、画像屋占位、教师面板占位。
  - 状态：分 store 管理（`session`、`world`、`learning`、`agent`），不混成一个全局对象。
  - API client：所有 `/api/**` 调用经 `frontend/src/api/` 下的 typed client，DTO 类型放 `frontend/src/api/dto/`，**禁止**散装字段。
- 实现 Phaser 小镇 MVP：
  - 一张瓦片地图（自绘像素或合规免费素材，**不复制星露谷官方素材**）。
  - 可控玩家行走（4 方向，简单帧动画）。
  - 至少 3 个 NPC 站位，能触发对话框（对话内容为占位文案）。
  - 至少 5 个建筑可交互（画像屋、图书馆、知识田、训练场、教师屋），交互后跳路由或弹空白模态框。
  - 玩家坐标通过 `PATCH /api/world/player` 周期回传后端。
- Phaser 全部对象必须封装在 `frontend/src/game/adapters/`、`frontend/src/game/scenes/`，业务层禁止直接 import Phaser。

## 3. 上下游关系

- 上游：阶段 00（脚手架）、阶段 02（鉴权与世界会话）。
- 下游：
  - 阶段 04 画像屋会扩展画像屋场景与新的对话 store。
  - 阶段 05 知识田会扩展知识田场景、作物 sprite。
  - 阶段 08 公告板、复盘大厅会复用 NPC 对话框组件。
  - 阶段 09 训练场会接入 Pyodide adapter（本阶段先留空 adapter）。
  - 阶段 10 Bug 矿洞会扩展矿洞场景。
  - 阶段 12 教师屋会扩展教师面板。

## 4. Alethicode 借用来源

仅作为**结构灵感**，**不直接复制**：

- `/home/cypress/Alethicode/frontend/src/pages/oj/views/languagepack/LanguagePackQaPage.vue`（页面布局参考）。
- `/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue`（侧边面板参考）。
- `/home/cypress/Alethicode/game/src/engine/SaveManager.js`（存档机制思路）。
- `/home/cypress/Alethicode/game/src/components/DialogueBox.vue`（对话框 UX 参考）。
- `/home/cypress/Alethicode/game/src/components/CodingChallenge.vue`（编码弹窗 UX 参考）。

**抽取约束**：

- 不复制 `frontend/`（Alethicode）下任何 `.vue` 文件源码到 Pythontopia。
- 不引入 `Vuex`、`Vue 2`、`element-ui` 等老技术债依赖。
- 重写为 Vue 3 SFC + `<script setup lang="ts">`，所有 props/emits 类型显式声明。

## 5. 任务清单

### 5.1 工程基础确认

- T03-01 重新确认 `tsconfig.json` strict 全开；新增 `verbatimModuleSyntax: true`。
- T03-02 在 `frontend/vite.config.ts` 中：
  - 注册 `@vitejs/plugin-vue`、路径别名（同阶段 00）。
  - 配置开发代理：`/api → http://localhost:18080`、`/healthz → http://localhost:18080`。
- T03-03 `vitest.config.ts`：
  - `environment: 'jsdom'`。
  - 覆盖率默认开启，门槛先不强制。
- T03-04 `.eslintrc.cjs`：
  - 业务层 `@typescript-eslint/no-explicit-any: 'error'`。
  - `frontend/src/game/adapters/**` 仅 `warn`。
  - 禁止从业务文件 import `phaser`（`no-restricted-imports`）。

### 5.2 路由与布局

- T03-10 `frontend/src/app/router.ts`：
  - `/login`、`/register`：未登录可访问；已登录跳 `/town`。
  - `/town`：默认进入小镇主场景。
  - `/profile-room`、`/library`、`/training`、`/bug-mine`、`/board`、`/review-hall`、`/teacher` 路由占位，对应组件存在但渲染空白页。
  - 路由守卫：未携带有效 token 重定向到 `/login`；token 失效自动登出并提示。
- T03-11 `frontend/src/app/AppLayout.vue`：极简顶部 bar，仅显示用户名 + 退出按钮 + 当前日/天气（数据来自 `useWorldStore`）。

### 5.3 状态管理（Pinia 分片）

- T03-20 `frontend/src/stores/session.ts`：
  - state：`token`、`user`、`expiresAt`。
  - actions：`login(req)`、`register(req)`、`logout()`、`hydrateFromStorage()`、`refreshIfNeeded()`。
  - 持久化：仅 token + 过期时间写入 `localStorage`，**不**写入个人敏感字段。
- T03-21 `frontend/src/stores/world.ts`：
  - state：`session`（WorldSessionDto）、`player`（PlayerStateDto）、`npcStates`。
  - actions：`loadSession()`、`updatePlayerTile({tileX, tileY, direction})`（节流，每 500ms 一次）、`refreshWeather()`。
- T03-22 `frontend/src/stores/learning.ts`：占位 store，预留 `kcs`、`profile`、`path` 等字段供后续阶段填充。
- T03-23 `frontend/src/stores/agent.ts`：占位 store，预留 `runs`、`activeRun`、`logs` 字段。
- T03-24 各 store 之间禁止互相 `import` state；必须通过 actions/getters 通信。

### 5.4 API Client 与 DTO

- T03-30 `frontend/src/api/http.ts`：
  - 基于 `axios.create`，`baseURL: '/'`。
  - 请求拦截：注入 `Authorization: Bearer <token>`。
  - 响应拦截：401 → 触发 `useSessionStore().logout()` + 跳 `/login`；其他错误统一抛 `ApiClientError`（包含 `code`、`message`）。
- T03-31 `frontend/src/api/dto/`：
  - `ApiError.ts`、`UserDto.ts`、`LoginRequest.ts`、`LoginResponse.ts`、`RegisterRequest.ts`、`ClassroomDto.ts`、`ClassroomMemberDto.ts`、`WorldSessionDto.ts`、`PlayerStateDto.ts`、`NpcStateDto.ts`、`CoursePackDto.ts`、`ChapterDto.ts`、`KcDto.ts`、`PageDto.ts`、`ProblemDto.ts`。
  - 所有 DTO 字段类型与阶段 01、02 后端契约完全一致。
- T03-32 `frontend/src/api/auth.ts`、`classroom.ts`、`world.ts`、`course.ts`、`learner.ts`：每个文件导出 typed 函数：
  - 例：`async function login(req: LoginRequest): Promise<LoginResponse>`。
  - 不允许在函数内部使用 `any` 或 `unknown` 后立刻 cast 成业务类型，必须用 DTO 类型直接接收。

### 5.5 Phaser Adapter 层

- T03-40 `frontend/src/game/adapters/phaserGame.ts`：
  - 暴露 `createPhaserGame(rootEl: HTMLElement, scenes: Phaser.Types.Scenes.SettingsConfig[]): PhaserGameHandle`。
  - 内部 `import Phaser from 'phaser'`；业务层 ESLint 禁止 import phaser。
- T03-41 `frontend/src/game/adapters/tilemap.ts`：
  - 暴露 `loadTownMap(scene, mapKey)`，封装 tileset/layer 创建过程。
- T03-42 `frontend/src/game/adapters/input.ts`：
  - 暴露键盘控制工厂，返回 `{ getDirection(): Direction }`。
- T03-43 `frontend/src/game/adapters/sprite.ts`：
  - 暴露 `createPlayerSprite(scene, spec)`，包装动画注册、移动、转向。
- T03-44 `frontend/src/game/adapters/dialog.ts`：
  - 暴露 `openNpcDialog(npcKey)`：触发 Vue 端事件总线（通过 `useDialogStore`，见 T03-22 扩展）显示对话框。
  - **不**在 Phaser 内自己绘制对话 UI（用 Vue 组件覆盖）。
- T03-45 所有 adapter 文件顶部添加注释：

```ts
// 隔离原因：保持业务层无 Phaser 依赖，禁止业务代码直接 import phaser。
```

### 5.6 场景实现

- T03-50 `frontend/src/game/scenes/BootScene.ts`：加载 tileset、玩家精灵、NPC 精灵；进度回调显示加载条（通过 Vue 组件）。
- T03-51 `frontend/src/game/scenes/TownScene.ts`：
  - 地图至少 30×20 瓦片。
  - 玩家初始位置从 `useWorldStore().player` 读取；若 `tileX/tileY` 为 0 则用地图默认出生点。
  - 5 个建筑标记交互区，触发对应路由跳转。
  - 3 个 NPC 触发对话框，对话内容来自 `frontend/src/game/data/npcDialog.ts`（**占位文案**，禁止伪造 KC 引用）。
  - 玩家移动节流后调用 `useWorldStore().updatePlayerTile`。
- T03-52 `frontend/src/game/scenes/PlaceholderScene.ts`：用于后续阶段（画像屋、知识田、训练场、Bug 矿洞）即时切换，本阶段只渲染场景标题与"敬请期待"。

### 5.7 关键 Vue 组件

- T03-60 `frontend/src/components/auth/LoginForm.vue`、`RegisterForm.vue`：使用 `<script setup lang="ts">`、`v-model`、明确 DTO 类型；表单校验在前端（基础）+ 后端（权威）。
- T03-61 `frontend/src/components/game/GameCanvas.vue`：挂载 Phaser game，组件 `onMounted` 调 adapter，`onUnmounted` 销毁。
- T03-62 `frontend/src/components/game/DialogModal.vue`：基于 `useDialogStore`（在 `world.ts` 中新增子 store 或 `dialog.ts` 单独建）展示 NPC 对话，**不**渲染未确认的课件内容。
- T03-63 `frontend/src/components/system/AppHeader.vue`、`AppToast.vue`：顶部 bar 与全局 toast。

### 5.8 像素素材

- T03-70 在 `/home/cypress/Pythontopia/assets/` 下：
  - `pixel/tilesets/`、`pixel/characters/`、`pixel/buildings/`：放置自绘或合规免费素材，附 `LICENSE.md`（每个素材逐条标 license 与来源）。
  - **禁止**放置星露谷官方素材。
- T03-71 在 `frontend/public/assets/` 软链接或复制 `assets/pixel/` 内允许的子集，并在 `vite.config.ts` 中确保打包包含。

### 5.9 单元测试

- T03-80 `frontend/tests/stores/session.spec.ts`：登录成功保存 token、登出清空 token、token 过期触发 logout。
- T03-81 `frontend/tests/stores/world.spec.ts`：`updatePlayerTile` 节流策略、坐标更新触发 API。
- T03-82 `frontend/tests/api/auth.spec.ts`：mock axios 验证请求体、错误码处理。
- T03-83 `frontend/tests/components/LoginForm.spec.ts`：表单校验、提交逻辑（mock store）。
- T03-84 `frontend/tests/game/adapters/sprite.spec.ts`：用 mock Phaser 验证 `createPlayerSprite` 调用顺序（保证 adapter 输入输出契约稳定）。

## 6. 数据输入与输出

- 输入：
  - 阶段 02 提供的 `/api/auth/**`、`/api/world/**`、`/api/course/**`。
  - 像素素材文件。
- 输出：
  - 一个可登录、可注册、可进入小镇并行走、可触发 NPC 对话的前端 SPA。
  - 玩家位置变更通过 `PATCH /api/world/player` 持久化到数据库。
- 不输出：画像问答、KC 作物、资源生成、判题。

## 7. 验收标准

### 7.1 功能验收

- 新注册学生登录后自动进入 `/town`；地图加载完成后玩家显示在出生点。
- 玩家用方向键能 4 向移动；NPC 接近后触发对话框，对话内容是占位文案，明确标 `[占位]` 字样。
- 5 个建筑可点击/交互，正确跳到占位路由。
- 关闭浏览器再打开，玩家位置自动恢复为上次记录的坐标。
- 退出登录后跳回 `/login`，所有 token 信息从 `localStorage` 清空。

### 7.2 工程验收

- `npm run typecheck` 通过且 0 个 `error`。
- `npm run lint` 通过且业务层 0 个 `any`。
- `npm run test` 通过且至少 8 个 spec 文件。
- `git grep -nE "from ['\"]phaser['\"]" frontend/src | grep -v "src/game/adapters"` 输出为空（业务层无 phaser 直接 import）。
- 每个 Pinia store 都有独立 spec；store 之间无循环依赖。
- DTO 与后端字段一致；如发现差异以后端为准并立即修复 DTO。

### 7.3 素材验收

- `/home/cypress/Pythontopia/assets/pixel/LICENSE.md` 列出所有素材的来源、作者、license（CC0、CC-BY、自绘等），**无**星露谷官方素材。
- `assets/pixel/` 文件名为 `snake_case`，便于跨平台。

## 8. 验证命令

```bash
cd /home/cypress/Pythontopia/backend
mvn spring-boot:run

cd /home/cypress/Pythontopia/frontend
npm ci
npm run typecheck
npm run lint
npm run test
npm run dev

# 浏览器访问
xdg-open http://localhost:5173

# 静态扫描：业务层不得 import phaser
cd /home/cypress/Pythontopia/frontend
grep -RnE "from ['\"]phaser['\"]" src | grep -v "src/game/adapters" || echo "OK: 业务层无 phaser 直接 import"
```

## 9. 风险与回滚

- 风险：Phaser 与 Vue 生命周期冲突，HMR 时多次创建实例；缓解：在 `GameCanvas.vue` 的 `onUnmounted` 中务必 `phaserGame.destroy(true)`；并在 adapter 中防止重复初始化。
- 风险：节流过激导致玩家位置回传丢失最后一次；缓解：使用 `trailing: true` 节流，并在 `beforeunload` 强制 flush 一次。
- 风险：tilemap 资源加载失败导致黑屏；缓解：BootScene 必须显示加载错误并允许重试；CI 中包含一份固定测试 tilemap。
- 风险：素材 license 遗漏导致比赛风险；缓解：素材入库 PR 必须附 license 文件审核。
- 回滚：本阶段产出全部可独立删除 `frontend/src/game/`、`frontend/src/api/`、`frontend/src/stores/`、`frontend/src/components/` 后回到阶段 00 的空骨架。

## 10. 不做事项

- 不实现画像问答、KC 作物、Pyodide、判题、Bug 矿洞、班级看板。
- 不引入 Vuex、Element Plus、`pinia-plugin-persistedstate` 等额外依赖（除非阶段 14 评估后允许）。
- 不在 Phaser 内绘制 Markdown / 富文本，所有学习内容必须用 Vue DOM 渲染（保证可读、可复制、可引用）。
- 不渲染未确认 license 的素材。
- 不在前端做任何假数据 fallback：API 失败时显示错误状态，不显示假 KC 列表或假任务。
- 不允许 NPC 对话泄露未实现的功能细节（统一标注 `[占位]`）。

