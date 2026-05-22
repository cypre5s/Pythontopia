# Pythontopia R-100 接力 - 阶段 2 剩余 + 阶段 4 测试补全

> 直接复制本文件全文粘贴给新 agent。它自包含、不需要任何额外上下文。
> **要求**：高效率完成全部 5 个 Task；每完成一个立即 commit；不询问 / 不等用户确认（除非出现"叫醒"条件）。

## 项目背景

- 仓库：`/home/cypress/Pythontopia`（main 分支，**不要去 worktree**）
- 项目类型：基于 Alethicode 抽取重构的高校 Python 初学者像素学习小镇
- 关键约束：见 `/home/cypress/Pythontopia/AGENTS.md`（教育优先、不复制星露谷素材、不伪造课件引用、不启用 ReAct）
- 全局规则：用 7892 端口代理上网 `export HTTP_PROXY=http://127.0.0.1:7892 HTTPS_PROXY=http://127.0.0.1:7892`
- Git user 不要污染 global config，用 `git -c user.name="Pythontopia Dev" -c user.email="dev@pythontopia.local" commit ...`
- Write 工具被工作区限制时改用 Shell heredoc 写文件（`cat > /abs/path <<'EOF' ... EOF`）

## 已完成（截止 R-100 阶段 2 P1）

main 分支已新增 9 个 commit（不要 amend，全部 ok 待 push 或留 working tree）：

```
6927b22 docs(r100): P1 增量同步
3d54cfa feat(frontend-r100): P1-D LPC Sara walk 动画
57bd410 feat(frontend-r100): P1 BGM + KC confetti + Training/Workshop SFX
2efd40d docs(r100): 测试报告 + execution_status 同步
546152c feat(frontend-r100): P0 全局 SFX + /credits 致谢页
6d07383 feat(frontend-r100): 集成 R-100 素材到 public/assets — 29MB
faf6eac chore(assets): 补遗
2ecf1af feat(assets): R-100 stage1 inbox expansion — 10 sources / ~200MB
0d167a4 chore(assets): R-100 stage1 license hotfix
─── 用户 worktree 已交（不重做） ───
5ce74e2 docs(android-r1)
8a49643 feat(android-r1): Android 多模块骨架 (15 模块 / 28 测试通过 / 11MB APK)
```

### 已落地核心能力

- 素材：`assets/inbox/` 27 个非空源 / 359 MB / 11000+ 文件（含中文像素字体 Ark Pixel 12 OFL-1.1 / KC 60 张图标 / Kenney UI / LPC 角色 / OGA BGM SFX 特效 / 4 季节场景）
- License 合规：sprout_lands_basic 已删 + LICENSE-ART.md + /credits 致谢页就位
- Web 前端集成：`frontend/public/assets/pixel/` 29 MB / 11 子目录
- 字体：tokens.css 加 @font-face for `Ark Pixel 12` + `.pyt-pixel-text` / `.pyt-pixel-code` 工具类
- BootScene preload：22 个 key（tileset + KC icons + char spritesheet + SFX + BGM）
- 全局 SFX 服务：`frontend/src/utils/sfx.ts` + 5 单测
- BGM 引擎：`frontend/src/utils/bgm.ts` + 8 单测，TownScene/FieldScene/BugMinePage/ReviewHallPage 接入 fade-in/out
- KC 收获 confetti：`frontend/src/utils/celebrate.ts` + 3 单测，learning.ts 在 KC 转 MATURE 触发
- TrainingPanel + WorkshopPanel SFX 反馈
- 玩家 LPC Sara walk 动画：4 方向 frame 切换 + fallback
- Android-R1：15 模块 Gradle 骨架 / Hilt+KSP / Compose+LibGDX / 11 MB APK / 28 测试

### 测试基线（必须不破坏）

| 套件 | 通过 |
| --- | ---: |
| Frontend vitest | 74 / 74 (29 files) |
| Frontend typecheck (vue-tsc) | 0 errors |
| Frontend lint (eslint) | 0 errors |
| Backend mvn test (skipITs) | 101 / 101 |
| Services / graph pytest | 59 / 59 |
| Android gradlew test | 28 / 28 |
| **合计** | **262 / 262** |

(rag pytest 因本机 Python 3.10 ≠ 要求 ≥3.11 跳过，环境问题非代码问题)

## 待完成任务（按优先级）

### Task 1: TownScene 真 tileset 渲染（P1-E backlog，最高视觉价值）

**目标**：让 TownScene 不再用 SVG/rectangle 渲染地面，改用 Kenney tiny-town spritesheet (16×16 tile / 12 cols × 11 rows / 132 frames) 平铺真实像素小镇地面 + 建筑用 tile 拼接而非 SVG。

**已就位的素材**：
- `frontend/public/assets/pixel/tilesets/kenney-tiny-town/Tilemap/tilemap_packed.png` (192×176)
- BootScene 已用 `loader.spritesheet('kenney_tiny_town', url, {frameWidth:16, frameHeight:16})` preload
- `sceneTypes.ts` 的 `ImageLike` 接口已加 `setFrame?: (frame: number | string) => ImageLike`（commit 57bd410）
- `sceneTypes.ts` 的 `SceneAddLike.image` 与 `tileSprite` 都已加 `frame?: number | string` 参数

**如何完成**：
1. 探测 tilemap_packed.png 实际 frame 数：`python3 -c "from PIL import Image; im=Image.open('/home/cypress/Pythontopia/frontend/public/assets/pixel/tilesets/kenney-tiny-town/Tilemap/tilemap_packed.png'); print(im.size)"`
2. 改 `frontend/src/game/adapters/tilemap.ts` 的 `loadTownMap`：
   - 增加可选第 3 参数 `pixelMode: boolean`（默认 true）
   - pixelMode=true：用 `scene.add.image(x, y, 'kenney_tiny_town', frameIndex)` 渲染每个地面 tile（按 width × height 平铺，frame 0 是草地，frame 13~24 范围常见房屋角块）
   - pixelMode=false：保留原 SVG/rectangle 渲染逻辑作为 fallback
   - 保留 TownMapData 接口（spawn, tileSize, buildings, npcs, width, height）
3. 不动 `TownScene.ts` 本身（仅靠 loadTownMap 升级生效）
4. 写测试 `frontend/tests/game/adapters/tilemap.spec.ts`（如存在则扩展），mock scene.add 验证 frame 参数被正确传入

**验收标准**：
- `cd /home/cypress/Pythontopia/frontend && npm run typecheck && npm run lint && npm test -- --run --coverage=false` 全过
- 测试总数从 74 → ≥77（新增至少 3 个 tilemap 测试）
- `git diff` 仅触碰：tilemap.ts、tilemap.spec.ts（新增）
- TownScene.ts 一行不动
- 不破坏现有 buildings/npcs/spawn 数据
- commit message: `feat(frontend-r100): P1-E TownScene 真 tileset 渲染 - Kenney tiny-town spritesheet 平铺地面 + frame-based 建筑`

### Task 2: FieldPage header KC 章节图标导航

**目标**：在 `frontend/src/pages/FieldPage.vue` 的 header 上方加一排 7 个章节图标（用 icons_kc 已就位的 64x64 PNG），点击可滚动定位到对应章节的 KC，让画面更教育化。

**已就位的素材**：
- `frontend/public/assets/pixel/icons_kc/chapter_basics/lorc__brain.png` 等 7 章对应图标
- 7 章映射（icon 路径 / 章节 slug）：
  - chapter_basics: `icons_kc/chapter_basics/lorc__brain.png`
  - variable_assignment: `icons_kc/variable_assignment/lorc__padlock.png`
  - data_types_number_string_bool: `icons_kc/data_types_number_string_bool/lorc__light-bulb.png`
  - list_tuple_set_dict: `icons_kc/list_tuple_set_dict/delapouite__chemical-tank.png`
  - control_flow_if_else: `icons_kc/control_flow_if_else/delapouite__choice.png`
  - loop_for_while: `icons_kc/loop_for_while/lorc__cycle.png`
  - function_def_args_return: `icons_kc/function_def_args_return/lorc__gears.png`

**如何完成**：
1. 改 `frontend/src/pages/FieldPage.vue`，在 `.field-header` 上方加 `<nav class="chapter-bar">`
2. 7 个 chapter icon button，每个用 `<img src="/assets/pixel/icons_kc/<chapter>/<icon>.png" />` + 标题文字（中文章节名）
3. 点击时调用 `learning.selectKc(firstKcCodeOfChapter)`（需要从 `learning.field.crops` 中按 chapter 前缀分组找第一个）
4. 加 hover 时 `playSfx('hover')`（300ms 节流）
5. 用 .pyt-pixel-text 类（已在 tokens.css）

**验收标准**：
- typecheck + lint + test 全过（FieldPage 现有测试不破坏）
- 不破坏现有 FieldPage 功能（bootstrapField / loadField / KcDetailPanel 等）
- KC 章节定位逻辑正确
- 新增至少 1 个 FieldPage 单测覆盖 chapter-bar
- commit message: `feat(frontend-r100): FieldPage header KC 章节图标导航 + hover SFX`

### Task 3: RAG pytest 跑通（环境补救）

**目标**：让 rag 服务测试也能跑（当前因本机 Python 3.10 跳过）。

**如何完成**：
- 方案 A（推荐）：用 docker
  ```bash
  cd /home/cypress/Pythontopia/services/pythontopia-rag
  docker run --rm -v $(pwd):/work -w /work python:3.11-slim sh -c "pip install -q -e '.[dev]' && python -m pytest -q"
  ```
- 方案 B：在本机装 pyenv + Python 3.11
- 把测试结果（passed 数）写入 `docs/r100_test_report.md`，更新 262 → 262+rag_count

**验收标准**：
- rag pytest 跑通（预期 ~18 passed）
- 测试报告同步更新
- commit message: `docs(r100): rag pytest 补跑通 (262 → 280 测试通过)`

### Task 4: judge e2e docker compose（可选，半小时）

**目标**：跑全套 docker compose 起 6 服务，e2e 验证：账号 → 进班 → 领任务 → 提交代码 → 看判题 → 触发 Bug 矿洞

**如何完成**：
```bash
cd /home/cypress/Pythontopia/deploy
docker compose up -d
sleep 60
ls /home/cypress/Pythontopia/deploy/*.sh
# 找到 demo_smoke.sh 或类似脚本，跑通完整链路
docker compose logs --tail 100 backend frontend
```

**验收标准**：
- 6 个 service 状态 healthy
- 5 条主线 API 全部 2xx
- 写 e2e 报告到 `docs/r100_e2e_report.md`
- commit message: `docs(r100): judge e2e docker compose 验证 - 6 service / 5 主线全通`

### Task 5: itch.io 手动补充素材（极低优先）

ansimuz/sunny-land-pixel-game-art + ansimuz/fantasy-weapons-set 都是 CC0，但 itch.io 自动下载受 session token 限制。需要浏览器登录后手动下载到 `assets/inbox/itch_extra/`，然后写 LICENSE.md + commit。

## 硬约束（不要破坏）

- 不复制星露谷素材
- 不伪造课件引用、不暴露答案
- 不启用 ReAct 循环
- 不污染 git global config
- 不 push 到 origin（除非用户明确要求）
- 不 amend 已发的 commit
- 不动 R-11 决策主躯（profile→field→workshop→training→bug_mine→review 链路）
- 不重新爬 sprout_lands 系列任何素材
- Task subagent 在 worktree 切换时会被 Aborted，如果失败请主代理串行接管
- Write 工具被工作区限制时改用 Shell heredoc 写文件
- 跑测试用 `npm test -- --run --coverage=false`（coverage 会有 .tmp 不存在错）

## 叫醒用户的条件（仅当遇到才停下）

- 架构级颠覆性改动需要决策
- 测试大面积红线 >20 失败
- 任何破坏比赛演示链路的判断
- Android assembleDebug 反复失败 >3 次

## 上下文文件清单（必读）

- `/home/cypress/Pythontopia/AGENTS.md` — 工作准则
- `/home/cypress/Pythontopia/LICENSE-ART.md` — 素材协议总声明
- `/home/cypress/Pythontopia/docs/r100_test_report.md` — 已完成的测试报告
- `/home/cypress/Pythontopia/docs/execution_status.md` — 进度时间线
- `/home/cypress/Pythontopia/.work/R-100/assets_crawl/0[1-9]*.md` — 10 个素材爬取子代理日志

## 工作模式

- 每完成一个 Task 立即 commit（细粒度），不要积累
- 每 2 分钟在对话中输出一次进度文本
- 主仓库工作目录是 `/home/cypress/Pythontopia/`，不要去 worktree
- 不询问 / 不需要 user 确认，直接干
- 完成所有 Task 后输出最终汇报：commit 列表 + 测试总数 + 已落地优化清单 + backlog
