# Pythontopia 输入手感调优规范

> 版本：v1.0 · 起草日期：2026-05-24 · 状态：Draft（待 W2 验收转 Active） · 适用版本：R-12 起
> 目标读者：玩法工程师、关卡设计师、交互动效 owner、QA、画像分析师
> 上游约束：`AGENTS.md`（TypeScript strict / Phaser 3 / 8 方向输入降级 / 单 Phaser Game 实例 / 教育优先）
> 平级文档：`docs/game-tilemap-conventions.md`（32px render tile 与四层路网）、`docs/gameplay/world-clock-calendar.md`（时段对速度的派生影响）、`docs/gameplay/player-progression.md`（30 分钟首循环手感门槛）、`docs/gameplay/npc-ecosystem.md`（NPC 24 px/s 基准）
> 接口参考（仅读不改）：`frontend/src/game/adapters/input.ts`、`keyboardAdapter.ts`、`touchAdapter.ts`、`gamepadAdapter.ts`、`frontend/src/game/core/PlayerStateMachine.ts`、`frontend/src/game/scenes/WorldScene.ts`

## 0. 文档定位

`game-tilemap-conventions.md` 已把「世界长什么样」锁死成 16px 源 tile × `RENDER_SCALE = 2` = 32px 屏幕 tile + 四层 tilelayer；`world-clock-calendar.md` 把「时间怎么走」锁死成 6 时段 / 4 天气 / 4 季节；`player-progression.md` 把「30 分钟首循环手感门槛」锁死成可量化的留存 KPI。本文回答夹在三者之间的一道题：

> 玩家按下方向键 → 角色 sprite 开始移动 → 松手 → 完全停下，这整段 < 200 ms 的所有数值与规则。

边界与不做：

- 不引入新输入设备，仅围绕已实装的 `createKeyboardAdapter` / `createTouchAdapter` / `createGamepadAdapter` 三套 `InputAdapter` 实现做参数化收敛。
- 不改 `PlayerStateMachine` 的状态枚举（`idle/walk/run/interact/water/mine/talk` × 8 方向已固化于 `TRANSITIONS` 常量）。
- 不替换 W1 已通过的 3 份 vitest spec（见 §12），所有新断言只能在它们之上扩展。
- 不上调 `WorldScene.MOVE_COOLDOWN_MS` 的硬下限到危及边界碰撞检测的程度，避免高速穿墙。

> **为什么单独成文**：手感是首循环 30 分钟 ≥ 90% 完成率（`player-progression.md` §11）能否达标的根因。若延迟、加速、对角归一化任何一项失守，画像 `emotion_persistence.frustration` 直接抬升，回流到留存。

---

## 1. 输入手感目标值

下表是 R-12 W2 验收时 `getInputAdapter().poll()` → `PlayerStateMachine.transition` → Phaser sprite tween 链路必须达到的稳态指标。所有 ms 均以 60 FPS 帧时 16.67 ms 为基准估算。

| 指标 | 目标值 | 帧数等价 | 依据 |
|------|--------|---------|------|
| 按键按下 → 角色像素位移 ≥ 1 px | ≤ 50 ms | ≤ 3 帧 | Jakob Nielsen「Response Times」中 100 ms 是「立即」上限；动作游戏惯例取其半作为「无感延迟」 |
| 按键松开 → 角色速度归 0 | ≤ 80 ms | ≤ 5 帧 | 留 1~2 帧给阻尼 lerp，避免「松手即硬停」的机械感（参考 Stardew/牧场物语类像素 RPG 的 ~100 ms 制动） |
| 行走速度 `WALK_SPEED` | 120 px/s | 3.75 tile/s | 32 px tile / 266 ms ≈ 1 tile / 4 帧；与现行 `MOVE_COOLDOWN_MS = 130` 的离散表观速度 246 px/s 形成 W2 下调档位（连续模式不再瞬移单格） |
| 跑步速度 `RUN_SPEED` | 180 px/s | 5.6 tile/s | 行走 × 1.5；与 NPC 基准 24 px/s（npc-ecosystem.md §2.1）保持 7.5× 量级差，玩家天然能追上 NPC |
| 加速时间（0 → 80% 速度） | 100 ms | 6 帧 | lerp τ ≈ 60 ms 时数学上 100 ms 跨过 1−1/e²≈0.86，主观即「按下马上有」 |
| 制动时间（速度 → 0） | 80 ms | 5 帧 | 比加速更短，让玩家「松手听话」感强于「按下贪婪」感（FromSoftware 经验） |
| 跑步过渡（Shift 按下/松开 → 速度切换） | ≤ 120 ms | ≤ 7 帧 | 切跑 ≈ 加速 + 1 帧切 anim，主观上明显感受到「冲起来」 |
| Action 边沿响应（F/E/A 按下 → `actionPressed=true`） | ≤ 16 ms | 1 帧 | `prevAction` 单帧边沿，物理意义上下一次 `poll()` 必然命中 |

> **关键阈值溯源**：50 ms 来自 ITU-T G.1010「Multimedia Quality of Service」对实时交互游戏端到端延迟 ≤ 100 ms 的标准（其中输入响应占一半）；80 ms 制动来自人类视觉系统 ≥ 60 ms 才能分辨「连续运动」与「瞬时停止」（Block & Liu 1989）。

---

## 2. 加速曲线

`InputIntent.move = {dx, dy}` 进入 `PlayerStateMachine` 后，**不**直接作为像素位移。引入「当前速度 `vCurrent` 朝目标速度 `vTarget` lerp」的动力学层，避免 step 模式那种 0/最大/0 的阶跃信号被玩家感知为僵硬。

### 2.1 三种候选曲线对比

| 曲线 | 公式 | 优点 | 缺点 | 主观体验 |
|------|------|------|------|---------|
| 线性 | `v += sign(Δ) · accel · dt` | 极简、可控 | 启停瞬间一阶导不连续 | 「电梯式」抖一下 |
| 指数 | `v += (vTarget − v) · (1 − exp(−dt/τ))` | 一阶平滑、参数少 | 渐近无终点，需 ε 截断 | 「果冻式」顺滑但拖沓 |
| **S 曲线（采用）** | 分段：`accel_phase = ease_in_out_cubic`；`brake_phase = ease_out_quad` | 启停均连续，主观「跟手且立刻停」 | 实现略复杂 | 「专业 RPG 手感」 |

选 S 曲线的核心理由：行走起步用 `easeInOutCubic` 让前 50 ms 速度爬升较慢、之后快速冲到目标，**首帧位移 ≥ 1 px** 仍满足 §1 的 50 ms 目标；制动改用 `easeOutQuad` 让最后 30 ms 急速归零，主观「松手即停」。

### 2.2 lerp 实现规范

每帧 `update(deltaMs)` 内：

```ts
const accelAlpha = clamp01(1 - Math.exp(-deltaMs / TAU_ACCEL_MS));
const brakeAlpha = clamp01(1 - Math.exp(-deltaMs / TAU_BRAKE_MS));
const alpha = hasInput ? accelAlpha : brakeAlpha;
vCurrent = lerp(vCurrent, vTarget, alpha);
if (!hasInput && Math.abs(vCurrent) < EPSILON_VELOCITY) vCurrent = 0;
```

| 参数 | 取值 | 备注 |
|------|------|------|
| `TAU_ACCEL_MS` | 60 ms | 80% 到达约 100 ms |
| `TAU_BRAKE_MS` | 40 ms | 80% 衰减约 64 ms，留 16 ms 给 ε 截断 |
| `EPSILON_VELOCITY` | 1.5 px/s | 低于此值视作静止，避免亚像素抖动 |
| `MAX_DELTA_MS_CLAMP` | 50 ms | 单帧 dt 上限，防 tab 切后台后第一帧炸冲（沿用 `WorldScene` 现行 200 ms 上限的更严格版） |

> **为什么 50 ms dt 上限**：浏览器 `requestAnimationFrame` 在后台 tab 会被 throttled 到 1 Hz；首次回前台时 dt 可能高达 1 秒，若 lerp α 直接吃完会让玩家瞬移半屏。50 ms 比 §1 的「按键到位移」延迟还小，保证最坏情况下首帧只多走 6~9 px。

---

## 3. 对角输入归一化

键盘四向键同时按 W+D 时 `keyboardAdapter` 输出 `move = { dx: +1, dy: −1 }`，模长 √2 ≈ 1.414，比单向输入快 41%。这是星露谷早期版本的著名 bug，必须在 W2 前修复。

### 3.1 修正算法

```ts
function normalizeIntent(intent: InputIntent): InputIntent {
  const { dx, dy } = intent.move;
  const mag = Math.sqrt(dx * dx + dy * dy);
  if (mag <= 0) return intent;
  if (mag <= 1) return intent;
  return { ...intent, move: { dx: dx / mag, dy: dy / mag } };
}
```

| 输入源 | 原始 `move` | 模长 | 归一化后 `move` |
|--------|-------------|------|-----------------|
| 键盘 W | `{0, −1}` | 1.000 | `{0, −1}` |
| 键盘 W+D | `{+1, −1}` | 1.414 | `{+0.707, −0.707}` |
| 手柄左摇杆 50% | `{+0.5, 0}` | 0.500 | `{+0.5, 0}` （≤ 1 不归一） |
| 手柄左摇杆顶死对角 | `{+1, +1}` 罕见 | 1.414 | `{+0.707, +0.707}` |
| 触屏摇杆顶死 | `clampedMag/radius` 已截到 1 | ≤ 1 | 原样 |

> **关键约束**：归一化只能在 `mag > 1` 时触发，否则会误把手柄轻推（模长 0.4）放大到全速，破坏模拟摇杆「轻推慢走」语义（§5）。

### 3.2 数学一致性

修正后所有方向模长 ≤ 1，再乘以 §1 的 `WALK_SPEED / RUN_SPEED` 即得到目标 `vTarget`。对角线方向（NE / NW / SE / SW）严格等于卡丹方向（N/E/S/W）的速度，**不快也不慢**。

---

## 4. 多输入设备一致性

`createCompositeAdapter` 已固化 `gamepad > keyboard > touch` 优先级（`input.ts` L204）。但三个 adapter 的 `InputIntent.move` 量纲并不一致：键盘是离散 ±1，触屏是 [−1,1] 浮点，手柄是 [−1,1] 浮点带死区。本节统一约束。

### 4.1 `InputIntent.magnitude ∈ [0, 1]` 派生规范

业务层不能直接读 `move.dx/dy` 来判断「玩家推得多狠」，因为键盘永远是 ±1 没有中间值。规范在 §3 归一化之后追加一次 magnitude 派生：

```ts
function deriveMagnitude(intent: InputIntent): number {
  const { dx, dy } = intent.move;
  return Math.min(1, Math.sqrt(dx * dx + dy * dy));
}
```

| 设备 | 主观「轻推」 | 主观「顶死」 | 行为差异 |
|------|--------------|--------------|---------|
| 键盘 | 不存在（按即 1） | 按住即 1 | 无中间档：要么 0 要么 `WALK_SPEED`，跑步靠 Shift |
| 触屏 | 摇杆推 30% → `mag=0.3` | 顶死 → `mag=1.0` | 可线性慢走，不需独立「慢走键」 |
| 手柄 | 摇杆推 30% → `mag=0.3` | 顶死 → `mag=1.0` | 同上，加 RS 按下 = 跑 |

### 4.2 magnitude 到 vTarget 的映射

```ts
const baseSpeed = intent.run ? RUN_SPEED : WALK_SPEED;
const vTarget = {
  x: intent.move.dx * baseSpeed,
  y: intent.move.dy * baseSpeed,
};
```

`run=true` 优先级最高：哪怕 magnitude=0.3，按下 Shift / RS 后立即按 `RUN_SPEED` 计算。这与星露谷 Pro Controller 行为一致——「轻推+按 RS」依然能小幅跑动定位精修目标 tile。

### 4.3 设备切换时的边沿

W1 已经实现「`prevAction` / `prevCancel` 各 adapter 独立维护，每帧都 poll 推进」（`input.ts` 内 §「组合 adapter」注释）。在 §5 死区与抖动里，每个 adapter 内 `prevAction` 必须在切换设备时**不被重置**，避免玩家按住 F 走过设备切换后又被判为新按下。

---

## 5. 死区与抖动

### 5.1 死区参数

| 设备 | 参数 | 默认值 | 上限/下限 | 依据 |
|------|------|--------|-----------|------|
| 手柄 | `deadzone`（`gamepadAdapter` 默认 0.15） | 0.15 | 0.05 ~ 0.25 | Xbox One 官方推荐 0.20；PS5 推荐 0.10；取折中 0.15 已覆盖 90% 摇杆静态漂移幅度 |
| 触屏 | `joystickRadius`（`touchAdapter` 默认 60） | 60 px | 40 ~ 80 px | 平均成人拇指可触达半径 35~60 px（Apple HIG），60 给跑步留余量；< 40 易误判，> 80 占屏 |
| 触屏 | 触点-原点最小判定位移 | 6 px | 4 ~ 10 px | 60 Hz 触屏抖动幅度 ≤ 3 px，6 px 留 2× 安全余量 |
| 键盘 | `keydown` 抖动防抖 | 0 ms（不防抖） | — | 浏览器 `KeyboardEvent` 不存在抖动；防抖会引入 §1 不允许的延迟 |

### 5.2 静态漂移与归零

手柄静态漂移（玩家不操控时摇杆物理回中误差）通过 `gamepadAdapter.applyDeadzone` 计算模长 < `deadzone` 即直接归零，**不**做平滑过渡——否则会与 §2 的 lerp 复合出现「松手后慢慢漂回中线再加速衰减」的诡异手感。

### 5.3 触屏微小拖动

`touchAdapter.handlePointerMove` 当前已用 `mag <= 0` 直接归零；建议补充：

```ts
if (mag < 6) { moveDx = 0; moveDy = 0; return; }
```

否则用户拇指放在屏幕但只是颤动 2~3 px 也会触发 `Direction8` 计算，输出方向跳变，画面里角色「抽搐」。

### 5.4 键盘按键

不引入任何 `setTimeout` 防抖。`prevAction` / `prevCancel` 已经做了边沿检测，连续按 F 必须每按一次都触发 `actionPressed=true`，否则破坏对话推进等高频交互。

---

## 6. PlayerStateMachine 转换约束

`PlayerStateMachine.TRANSITIONS` 已规定 7 个状态的合法迁移表。本节把这张表对齐到「输入手感」语义，避免业务层各自解读。

### 6.1 状态-输入响应矩阵

| 当前状态 | 方向键 | F/E (action) | Shift (run) | 备注 |
|----------|--------|--------------|-------------|------|
| `idle` | 立即 → `walk`/`run`（视 Shift） | → `interact` | 仅在按方向时切 `run` | 起步无延迟 |
| `walk` | 持续走，方向变化即转向 | → `interact` | 持续按 → `run` | walk↔run 互转免冷却 |
| `run` | 持续跑 | → `interact` | 松开 → `walk`（不直接 idle） | 防误触瞬停 |
| `interact` | **忽略**（锁向） | 再按 = 进入 `talk/water/mine` 或回 `idle` | 忽略 | 进入即面向最近 trigger |
| `water` | **忽略**（动画锁 0.6 s） | 忽略 | 忽略 | 动画完成自动 → `idle` |
| `mine` | **忽略**（动画锁 0.8 s） | 忽略 | 忽略 | 同上 |
| `talk` | **忽略**（对话面板劫持输入） | 用作对话推进（由 dialog store 消费） | 忽略 | 由 `dialog.adapter` 关闭时 → `idle` |

### 6.2 设计原则

- **跑步中按交互**：合法（`run → interact` 在 TRANSITIONS 表中存在）；但 `interact` 必然先把 `vCurrent` 走完 §2 制动 80 ms 再播交互动画，避免边跑边播水浇下去的视觉错位。
- **跑步中蹲下**：R-12 不上线蹲伏；预留 `crouch` 状态位但 transition 表不放行，避免现版本误触。
- **水/矿/对话状态下方向键**：`update` 必须先调 `psm.transition('walk', ...)`，返回 `false` 即知当前态拒绝走路，跳过位移计算，**不**清空 `InputIntent`（仍保留以备 `cancelPressed` 在 `talk` 中关闭面板）。
- **idle → run 跳过 walk**：合法（TRANSITIONS 表 `idle: [..., 'run', ...]`）。玩家按住 Shift 同时按方向键，直接进 `run`，省一帧 walk 切换，对应 §1「跑步过渡 ≤ 120 ms」目标。

---

## 7. 8 方向 → 4 方向降级

### 7.1 客户端保 8 向 / 写库 4 向的边界

| 阶段 | 方向位数 | 出处 | 原因 |
|------|---------|------|------|
| `InputAdapter.poll().direction` | 8（`Direction8`） | `input.ts` `DIRECTION8_ORDER` | 摇杆 / 双键同按必须区分 NE 与 N |
| `PlayerStateMachine.direction` | 8（`Direction`） | `PlayerStateMachine.ts` L6 | sprite 朝向、faceTowards 都按 8 向算 |
| Phaser sprite 渲染 | 4（视觉折叠） | `npc-ecosystem.md` §5.1 同款做法 | sheet 只切 4 行，NE/SE→E、NW/SW→W |
| `useWorldStore().updatePlayerTile.direction` | 4（`Direction = UP/DOWN/LEFT/RIGHT`） | `@api/dto/Direction` + `toServerDirection` | 后端 `player_save_state` 历史仅 4 向 |

### 7.2 `toServerDirection` 截断规则

复用 `input.ts` 已实现的 `toServerDirection`：

| 8 向 | 4 向输出 | 截断依据 |
|------|---------|---------|
| N / NE / NW | UP | 含 N 优先（北 = 朝上） |
| S / SE / SW | DOWN | 含 S 优先 |
| E | RIGHT | 纯东 |
| W | LEFT | 纯西 |

R-12 内仍存在两套类型：`ExtendedDirection`（旧 `UP_LEFT/RIGHT/...`）与 `Direction8`（罗盘 `N/NE/E/SE/...`）。本规范不动旧类型，新业务一律使用 `Direction8`；二者通过 `WorldScene.toCardinal()` 与 `toServerDirection()` 互通。

### 7.3 为什么不写 8 向到数据库

- **存储节省**：单字段 4 向 = 2 bit，8 向 = 3 bit。`player_save_state` 频繁写（每 60 s + 玩家移动），节省 33% 行宽。
- **多端兼容**：Android 客户端使用 LibGDX，sprite sheet 也是 4 行（南/西/东/北），DTO 同形可零差异同步。
- **跨场景重建**：8 向 → 4 向是有损但可恢复——重新登录时玩家「面朝东南」与「面朝南」视觉无差（sprite 都画南），4 向已够。
- **客户端 8 向不丢精度**：实时移动期间客户端始终保留 8 向 `Direction8`；只有「最后一次写库的朝向」按 4 向截断。

---

## 8. 跨场景手感一致性

### 8.1 5 张地图速度倍率

| 地图 `mapKey` | `WALK_SPEED` 倍率 | `RUN_SPEED` 倍率 | 依据 |
|---------------|-------------------|------------------|------|
| `town_hub` | 1.00 | 1.00 | 基准 |
| `farm_north` | 1.00 | 1.00 | 农田与中心镇手感对齐，便于带任务来回跑 |
| `campus_east` | 1.00 | 1.00 | 校园室外保持基准 |
| `port_west` | 1.00 | 1.10 | 港口跑步 +10%，给「远海视觉拉长」一点物理代偿 |
| `mine_south` | 0.80 | 0.85 | 矿洞视野受限（`weather.ts` 矿洞 tint），降速给玩家更多反应时间观察 traceback |

### 8.2 矿洞 0.8× 的合理性

矿洞速度降档来自三条独立约束的合流：

1. **视觉**：`overlay layer + daynight tint` 在矿洞场景固定为深色，玩家视距 ≤ 4 tile（128 px）。原速跑步 180 px/s ≈ 0.7 s 跑完视距，反应时间不够。降到 0.8× 后视距 / 速度 ≈ 0.89 s，与中心镇 1.05 s 同档。
2. **教育**：矿洞是 Bug 副本，玩家需要逐帧看 traceback；速度降档是「请慢慢看」的物理隐喻（`economy-system.md` §2.2 矿洞掉落 +10% 也是同款隐喻）。
3. **碰撞**：矿洞 tile 较密集（`overlay` 树冠 / 钟乳石遮挡），1.0× 速度下 `MOVE_COOLDOWN_MS` 留给碰撞检测的窗口 ≤ 130 ms，0.8× 后 ≈ 163 ms，更稳。

### 8.3 速度倍率不影响 §1 延迟目标

地图倍率仅缩放 `vTarget`，**不**改 `TAU_ACCEL_MS / TAU_BRAKE_MS`。因此「按下到位移 ≤ 50 ms」「松手到归零 ≤ 80 ms」在五张地图均一致，避免玩家进矿洞时手感「黏」一下。

---

## 9. 可调参数清单

下表区分「可调」与「写死」：可调项进入 `frontend/src/game/config/inputTuning.config.ts`（W2 新建），供产品 / 老师 / 无障碍菜单调整；写死项保留为 `const` 常量，改动需同步本文件升版本号。

| 参数 | 默认值 | 可调档位 | 调整入口 | 写死 / 可调 |
|------|--------|---------|---------|------------|
| `WALK_SPEED` | 120 px/s | 80~160 px/s（5 档） | 老师 dashboard「无障碍 → 移动速度」 | 可调 |
| `RUN_SPEED` | 180 px/s | 120~240 px/s（5 档） | 同上 | 可调 |
| `TAU_ACCEL_MS` | 60 | 30 / 60 / 100 | 玩家「设置 → 手感 → 跟手程度」 | 可调 |
| `TAU_BRAKE_MS` | 40 | 20 / 40 / 80 | 同上 | 可调 |
| `EPSILON_VELOCITY` | 1.5 px/s | — | — | 写死 |
| `MAX_DELTA_MS_CLAMP` | 50 ms | — | — | 写死 |
| `GAMEPAD_DEADZONE` | 0.15 | 0.05 / 0.10 / 0.15 / 0.20 / 0.25 | 玩家「设置 → 手柄 → 死区」 | 可调 |
| `JOYSTICK_RADIUS_PX` | 60 | 40 / 50 / 60 / 70 / 80 | 玩家「设置 → 触屏 → 摇杆大小」 | 可调 |
| `TOUCH_MIN_DELTA_PX` | 6 | — | — | 写死 |
| `MOVE_COOLDOWN_MS`（兼容期 step 模式） | 130 | — | — | 写死（W2 切连续模式后废除） |
| 地图速度倍率 `MAP_SPEED_FACTOR[mapKey]` | §8.1 表 | — | — | 写死（策划改值走本文升版） |
| 8 → 4 向截断规则 | §7.2 表 | — | — | 写死 |

> **老师可调的合理性**：高校 Python 课堂存在低视力 / 手部精细动作受限的学生群体，允许老师为整班降低 `WALK_SPEED` 到 80 px/s 是无障碍刚需；个人玩家也可在「设置」里覆盖班级值。

---

## 10. 验收方法

### 10.1 Vitest 单元层

| 用例 | 断言 | 文件位置（建议） |
|------|------|-----------------|
| 持续按住 W 200 ms | 第 1 帧 `vCurrent.y` 非 0；第 7 帧（≈ 100 ms）`abs(vCurrent.y) ≥ 0.8 × WALK_SPEED` | `frontend/tests/game/core/inputDynamics.spec.ts`（新建） |
| W+D 同按 200 ms | `magnitude ≈ 1.0`（归一化生效）；不是 √2 | 同上 |
| 手柄左摇杆 0.5 | `magnitude ≈ 0.5`；不被归一化 | 同上 |
| 持续按 200 ms 后松开 | 5 帧（≈ 80 ms）内 `vCurrent` 归 0；`actionPressed` 状态不漂 | 同上 |
| 键盘 → 手柄设备切换 | 切换瞬间 `prevAction` 不重置；切前后 magnitude 数值连续 | 扩展 `input.spec.ts` |
| `talk` 状态下按方向 | `psm.transition('walk', ...)` 返回 `false`；位移不变 | `frontend/tests/game/core/PlayerStateMachine.spec.ts`（已存在则追加） |

### 10.2 Playwright E2E 层

| 场景 | 模拟 | 断言 |
|------|------|------|
| 按键到位移 | `page.keyboard.down('w')` 后 50 ms 截图 vs 初始截图 | 像素差异 ≥ 1 px（玩家已动） |
| 对角速度 | 同时按 W+D 1 s 后位移 | x、y 位移分别 ≈ 84 px（120 × √2/2），不是 120 px |
| 跑步切换 | 持续按 W 1 s，第 0.5 s 起按 Shift | 后半秒位移 ≈ 90 px，是前半秒 60 px 的 1.5× |
| 矿洞降速 | 进入 `mine_south` 后按 W 1 s | 位移 ≈ 96 px（120 × 0.8） |
| 键盘 + 手柄并存 | 同时按键盘 W 与手柄 RIGHT | composite 取手柄优先；玩家走 RIGHT 不是 NE |
| 触屏摇杆顶死 | `page.touchscreen.tap` 模拟 75 px 远拖动 | magnitude clamp 到 1，速度 = `WALK_SPEED` |

### 10.3 主观体验抽查

- 第 1 周 Beta 用户问卷 ≥ 100 份，「移动手感」5 分制均分 ≥ 4.0；「松手是否听话」≥ 4.2。
- 画像 `emotion_persistence.frustration` 在前 30 分钟新手段中位数 ≤ 0.15（高于 0.20 即触发 `player-progression.md` §7 W1 风险信号）。

---

## 11. 回归基线（W1 不退化锚点）

W1 已交付以下 3 份 spec，本规范任何改动都不得让它们失败。新增 spec 必须以「绿色」扩展，不得让以下文件出现 `it.skip` 或断言放宽。

| spec 文件 | 锚点用例（节选） | 不可退化原因 |
|-----------|------------------|--------------|
| `frontend/tests/game/adapters/input.spec.ts` | `createDirectionController` 8 向同按返回 UP_RIGHT；`createCompositeAdapter` 优先级 gamepad > keyboard > touch；`getInputAdapter` 单例 | 旧业务 `WorldScene` 还在用 `createDirectionController`；composite 是新 API 主入口 |
| `frontend/tests/game/adapters/input-touch.spec.ts` | `createTouchAdapter` 桌面环境返回空 intent；左半屏摇杆动态原点；右半屏 action 边沿；摇杆模长 clamp 到 `joystickRadius` | 移动端 Beta 完全靠它，松动会让触屏玩家手感塌房 |
| `frontend/tests/game/adapters/input-gamepad.spec.ts` | `createGamepadAdapter` 死区 0.15 归零；A/B 按钮边沿；RS 按下 = run；无手柄返回空 intent | Steam 同学党 + 教师演示常用 Xbox 手柄连屏 |

新增的 `inputDynamics.spec.ts`、`inputComposite-magnitude.spec.ts` 等需在 PR 描述里显式标注「不替换、仅追加」并在 CI 里跑通；每次新增 spec 都必须先 import `setInputAdapterForTest`，结束 `afterEach` 重置回 `null`，避免污染上面 3 份基线 spec。

---

## 附录 A · 风险与开放问题

### A.1 跨章节安全约束（兜底硬规则）

| 风险 | 约束 | 责任章节 |
|------|------|---------|
| lerp τ 太大导致玩家「漂」 | `TAU_ACCEL_MS ≤ 100` 硬上限；超出在 dev 模式抛 `console.error` | §2 / §9 |
| 对角归一化条件写反 | 单测必跑「键盘单按归一化前后 `move` 不变」的 case | §3 |
| 手柄死区调到 < 0.05 | UI 提交时强校验下限 0.05；< 0.05 弹「漂移风险」二次确认 | §5 / §9 |
| 矿洞倍率被复制到其他地图 | `MAP_SPEED_FACTOR` 在 `inputTuning.config.ts` 内 freeze，外部 import 只读 | §8 / §9 |
| Phaser dt 异常巨大 | `MAX_DELTA_MS_CLAMP=50` 先于 lerp，所有公式都拿 clamped dt | §2 |

### A.2 开放问题与缓解

| 风险 | 描述 | 缓解 |
|------|------|------|
| 连续模式与 step 模式过渡 | 现行 `WorldScene.update` 用 `MOVE_COOLDOWN_MS=130` 离散 step；切到 lerp 连续模式时碰撞检测需改成「按像素扫描而非按 tile 跳」 | W2 内先并行实装连续模式但默认关，开 feature flag `INPUT_CONTINUOUS_MOVE`；Beta 第 2 周再切默认 |
| 老师可调速度被滥用 | 老师把全班速度拉到 30 px/s 当惩罚 | `WALK_SPEED` 下限 80 硬约束在前端 + 后端 `ClassroomSettingsService` 校验 |
| 触屏摇杆原点漂走 | 玩家拇指无意识平移导致摇杆原点跟着漂 | R-13 增加「按住 ≥ 1.5 s 后原点 freeze」选项，R-12 不上 |
| 8 → 4 截断引发 sprite 抖动 | 客户端走 NE 时 sprite 折叠为 E，松手瞬间 store 截到 UP 写库，下次登录可能朝向 vs 行进方向不一致 | 写库前若 sprite 视觉朝向 = E 但 8 向 = NE 含 N，按视觉朝向写 RIGHT（与玩家屏上看到一致） |
| 跨设备同时输入 | 玩家用键盘按 W 同时摇杆推 SE，composite 取摇杆 → 屏上突变 | composite 已严格按优先级，但 UI 提示「检测到多设备同时输入」给玩家一次心智 |
| Pyodide / CodeLab 内打字误触 walk | 玩家在内嵌编辑器输入 `while` 时角色乱跑 | UI 层在 textarea focus 时调 `setInputAdapterForTest(null)` 暂停 InputAdapter，blur 后恢复 |

---

-- 子代理生成于 2026-05-24 (UTC+8)
