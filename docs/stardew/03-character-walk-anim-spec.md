---
title: Pythontopia 角色 Walk 动画与状态机 Sprite 规范
version: v1.0
status: Draft
owner: 像素美术 + Phaser 程序员
last_updated: 2026-05-24
audience: 前端工程师 / Phaser 场景作者 / 像素美术外包 / QA / NPC 策划
scope: 玩家 Sara + 20 NPC 的 walk/idle/run/interact/water/mine/talk × 8 方向 sprite
依赖文档:
  - /home/cypress/Pythontopia/docs/stardew/00-visual-system-overview.md
  - /home/cypress/Pythontopia/frontend/src/game/core/PlayerStateMachine.ts
  - /home/cypress/Pythontopia/frontend/src/game/adapters/sprite.ts
  - /home/cypress/Pythontopia/frontend/src/game/adapters/input.ts
  - /home/cypress/Pythontopia/data/npcs/index.json
  - /home/cypress/Pythontopia/LICENSE-ART.md
  - /home/cypress/Pythontopia/frontend/public/assets/pixel/LICENSE.md
---

# 0. 文档定位

本文是 Pythontopia 全端**角色 sprite 帧动画**的硬约束规范。所有玩家与 NPC 的 idle / walk / run / interact / water / mine / talk 七种状态、8 方向朝向、对应的 LPC sheet 行列、帧数、帧率、命名、license 都必须在本文找到唯一来源。

本文与下列文件直接挂钩，谁先谁后必须一致：

| 文件 | 关系 |
| --- | --- |
| `PlayerStateMachine.ts` | 状态枚举与转移；本文动画 key 必须 1:1 覆盖该枚举 |
| `sprite.ts` | LPC sheet 加载、anim 注册、tween；本文表 §1 / §7 是它的来源 |
| `input.ts` | 8 方向输入 → 4 方向 sheet 降级；本文 §3 是它的对照规范 |
| `data/npcs/<key>/meta.json` | NPC 的 `sprite` 字段 = 本文 §5 的 sheet 名（去 `.png`）|

四条硬约束（任何后续提案违反一条即拒绝）：

1. **帧数 / 时长 / 命名**全部由本文统一，不允许在组件层（WorldScene / NpcFramework）硬编码帧号。
2. **8 方向输入降级 4 方向 sheet**：LPC 标准只画 4 方向（N/W/S/E），8 方向走 §3 的降级规则。
3. **license 强制双簿记**：每张 sheet 必须同时进入 `LICENSE-ART.md` 与 `frontend/public/assets/pixel/LICENSE.md`，详 §10。
4. **不复制商业像素游戏 sprite**：所有角色必须由 LPC（CC-BY-SA 3.0+）/ Kenney（CC0）合成，禁止星露谷物语 / 牧场物语 / 莱莎工房等专有 sprite。

> 本文不规范角色立绘（portrait_*.png）、不规范对话气泡、不规范衣物着色 LUT。它们各自由 `04-dialog-bubble-spec.md`（待写）与 `00-visual-system-overview.md` §1 调色板管。

---

# 1. LPC sprite sheet 行列映射（标准布局，1 套 4 方向）

LPC（Liberated Pixel Cup）标准角色 sheet 尺寸 **832 × 1344 px**，每帧 **64 × 64 px**，**13 列 × 21 行**。每个动作占连续若干行（北/西/南/东 共 4 方向），每行有效帧数因动作不同（剩余列填空白透明）。

> **来源依据**：`sprite.ts` 第 6-18 行已经以 `LPC_FRAME_HEIGHT = 64` / `LPC_COLS = 13` 加载该尺寸；本文是它的"行号字典 + 帧号字典"的权威来源。

## 1.1 21 行总表（按 LPC 官方块顺序，0 索引）

| 行号 | 动作 | 方向 | 有效帧数（含第 0 帧） | 列 0 性质 | Pythontopia 用途 |
| --: | --- | --- | --: | --- | --- |
| 0 | spellcast | N（北 / 朝上） | 7 | 起手 | water（朝上）|
| 1 | spellcast | W（西 / 朝左） | 7 | 起手 | water（朝左）|
| 2 | spellcast | S（南 / 朝下） | 7 | 起手 | water（朝下）|
| 3 | spellcast | E（东 / 朝右） | 7 | 起手 | water（朝右）|
| 4 | thrust | N | 8 | 持械 | mine（朝上）|
| 5 | thrust | W | 8 | 持械 | mine（朝左）|
| 6 | thrust | S | 8 | 持械 | mine（朝下）|
| 7 | thrust | E | 8 | 持械 | mine（朝右）|
| 8 | walk | N | 9 | **静止 idle** | idle/walk/run/interact/talk（朝上）|
| 9 | walk | W | 9 | 静止 idle | idle/walk/run/interact/talk（朝左）|
| 10 | walk | S | 9 | 静止 idle | idle/walk/run/interact/talk（朝下）|
| 11 | walk | E | 9 | 静止 idle | idle/walk/run/interact/talk（朝右）|
| 12 | slash | N | 6 | 持械 | 预留（未来近战 Bug 战）|
| 13 | slash | W | 6 | 持械 | 预留 |
| 14 | slash | S | 6 | 持械 | 预留 |
| 15 | slash | E | 6 | 持械 | 预留 |
| 16 | shoot | N | 13 | 持弓 | 预留（远程交互彩蛋）|
| 17 | shoot | W | 13 | 持弓 | 预留 |
| 18 | shoot | S | 13 | 持弓 | 预留 |
| 19 | shoot | E | 13 | 持弓 | 预留 |
| 20 | hurt | S（仅向下倒地）| 6 | 站立 | 受击（KC 失败 / Bug 矿洞掉血）|

> **行 8 / 9 / 10 / 11 的列 0 是真正可用的静态 idle 帧**，与代码现状 `sprite.ts:21` `idleFrame(dir) = LPC_WALK_ROW[dir] * LPC_COLS` 完全一致。**严禁**把列 1 当 idle（它已经是迈步的第 1 帧）。

## 1.2 现网代码对照表（`sprite.ts`）

| 现网常量 | 本文行号 | 来源行 |
| --- | --: | --- |
| `LPC_WALK_ROW.UP = 8` | 8 | `frontend/src/game/adapters/sprite.ts:14` |
| `LPC_WALK_ROW.LEFT = 9` | 9 | `frontend/src/game/adapters/sprite.ts:15` |
| `LPC_WALK_ROW.DOWN = 10` | 10 | `frontend/src/game/adapters/sprite.ts:16` |
| `LPC_WALK_ROW.RIGHT = 11` | 11 | `frontend/src/game/adapters/sprite.ts:17` |
| `walkFrames(dir) = [1..8]` | 列 1–8 | `frontend/src/game/adapters/sprite.ts:24-27` |

---

# 2. Pythontopia 自定义动画映射（7 状态 × 4 方向 = 28 anim key）

`PlayerStateMachine` 定义了 7 个 state，每个 state 在 4 方向 sheet 上各登记一个 anim key，共 28 个 key。命名规则统一为 `<spriteKey>_<state>_<dir>`（dir 取小写 `up/down/left/right`）。

## 2.1 状态 → LPC 行块映射

| Pythontopia state | LPC 行块 | 行号区间（N/W/S/E）| 帧序列 | 触发场景 | 一次性 / 循环 |
| --- | --- | --- | --- | --- | --- |
| `idle` | walk 行块 | 8 / 9 / 10 / 11 | **仅列 0** | 默认停站 | 静态（不进 anims）|
| `walk` | walk 行块 | 8 / 9 / 10 / 11 | 列 1–8 | 移动（WASD / 摇杆 / 虚拟键）| 循环 |
| `run` | walk 行块 | 8 / 9 / 10 / 11 | 列 1–8 | 跑动（Shift / R2）| 循环（更高帧率，§4）|
| `interact` | walk 行块 | 8 / 9 / 10 / 11 | **仅列 0** + 上下浮 2 px | F 键交互（拾取 / 触发）| 一次性 tween |
| `water` | spellcast 行块 | 0 / 1 / 2 / 3 | 列 0–6 | 浇水（农场 KC 种子）| 一次性 |
| `mine` | thrust 行块 | 4 / 5 / 6 / 7 | 列 0–7 | 挖矿（Bug 矿洞 / 矿石）| 一次性 |
| `talk` | walk 行块 | 8 / 9 / 10 / 11 | **仅列 0**，朝 NPC | 与 NPC 对话中 | 静态（不进 anims）|

> 用户规范原文：water → spellcast 第 1 行块；mine → thrust 第 1 行块；talk → walk row 第 0 帧朝 NPC；interact → walk row 第 0 帧 + 上下浮 2px。**本文把"第 1 行块"具体化为该动作的 4 行（按方向选行），保证 4 方向都能浇水 / 挖矿**。

## 2.2 28 个 anim key 命名（以玩家 Sara `sara` 为例）

| state | up | down | left | right |
| --- | --- | --- | --- | --- |
| `idle` | `sara_idle_up` | `sara_idle_down` | `sara_idle_left` | `sara_idle_right` |
| `walk` | `sara_walk_up` | `sara_walk_down` | `sara_walk_left` | `sara_walk_right` |
| `run` | `sara_run_up` | `sara_run_down` | `sara_run_left` | `sara_run_right` |
| `interact` | `sara_interact_up` | `sara_interact_down` | `sara_interact_left` | `sara_interact_right` |
| `water` | `sara_water_up` | `sara_water_down` | `sara_water_left` | `sara_water_right` |
| `mine` | `sara_mine_up` | `sara_mine_down` | `sara_mine_left` | `sara_mine_right` |
| `talk` | `sara_talk_up` | `sara_talk_down` | `sara_talk_left` | `sara_talk_right` |

> 28 个 key 中 `idle` / `talk` 走 `setFrame` 静态切帧（不调用 `anims.create`），其余 16 个 walk/run/interact/water/mine 进 Phaser `anims` 管理。NPC 命名规则换前缀即可：`farmer_kc_walk_down` / `mayor_py_idle_left` …。

## 2.3 interact 浮动 tween 细节

interact 不是帧动画而是 tween：基于 walk 行列 0 的静态帧，对 Phaser sprite 的 `y` 做 ±2 px 浮动。

| 项 | 值 |
| --- | --- |
| 起点 frame | walk 行块列 0（按 dir 选行）|
| tween 目标 | `y: original - 2` → `y: original + 2` → `y: original` |
| 每段时长 | 100 ms |
| 总时长 | 300 ms |
| 缓动 | `Sine.easeInOut` |
| 触发后续 | tween 完成回调 `transition('idle')` 自动复位 |

---

# 3. 8 方向输入降级到 4 方向 sheet 规则

`input.ts` 给出 `Direction8 = N | NE | E | SE | S | SW | W | NW`，而 LPC sheet 只画 4 方向。本文规定降级规则：**保留主轴方向，对角线降到水平方向**，与 `input.ts` `toServerDirection` 一致。

## 3.1 降级矩阵

| Direction8（输入）| Direction4（sheet 行）| 含义 | 与 `toServerDirection` 是否一致 |
| --- | --- | --- | --- |
| `N` | `UP`（行 8）| 朝上 | 一致 |
| `S` | `DOWN`（行 10）| 朝下 | 一致 |
| `W` | `LEFT`（行 9）| 朝左 | 一致 |
| `E` | `RIGHT`（行 11）| 朝右 | 一致 |
| `NE` | `RIGHT`（行 11）| 右上斜：取右 | **本文新规则**（旧 `toServerDirection` 取 UP）|
| `NW` | `LEFT`（行 9）| 左上斜：取左 | **本文新规则**（旧 `toServerDirection` 取 UP）|
| `SE` | `RIGHT`（行 11）| 右下斜：取右 | **本文新规则**（旧 `toServerDirection` 取 DOWN）|
| `SW` | `LEFT`（行 9）| 左下斜：取左 | **本文新规则**（旧 `toServerDirection` 取 DOWN）|

> **二者职责不同**：`toServerDirection` 用于上报后端（4 方向枚举），后端把"上 + 右"算朝上更符合 KC 种子格子写入；而**本文降级用于客户端 sprite 显示**，把对角线偏到水平方向，避免玩家斜行时 sprite 一直背对镜头不好看。**两套降级互不污染**：client 用 §3.2 函数，server 用 `toServerDirection`。

## 3.2 priorityHint 防颤动

玩家高速斜行时输入会在 NE / E / N 之间 1 帧一抖，导致 sprite 行号反复切换、产生卡顿感。引入 `priorityHint` = 上一帧实际选用的 4 方向，作为"惯性"权重。

```ts
function spriteDirection(
  d8: Direction8,
  priorityHint: Direction4 | null,
): Direction4 {
  switch (d8) {
    case 'N': return 'UP';
    case 'S': return 'DOWN';
    case 'W': return 'LEFT';
    case 'E': return 'RIGHT';
    case 'NE':
    case 'SE':
    case 'NW':
    case 'SW': {
      const horizontalHint =
        priorityHint === 'LEFT' || priorityHint === 'RIGHT';
      if (horizontalHint) {
        return priorityHint!;
      }
      return d8.endsWith('E') ? 'RIGHT' : 'LEFT';
    }
  }
}
```

| 输入序列 | 无 priorityHint | 有 priorityHint | 效果 |
| --- | --- | --- | --- |
| NE → N → NE → N | RIGHT / UP / RIGHT / UP | RIGHT / RIGHT / RIGHT / RIGHT | 不抖 |
| SE → S → SE → S | RIGHT / DOWN / RIGHT / DOWN | RIGHT / RIGHT / RIGHT / RIGHT | 不抖 |
| E → NE | RIGHT / RIGHT | RIGHT / RIGHT | 自然 |
| N → NE | UP / RIGHT | UP / UP | 不抖（首选纵向延续）|

> priorityHint 由 WorldScene 在每帧 `update()` 末保存上一帧 sprite 方向，再传给下一帧降级。**完全空闲 1 秒后**（idle 状态超时）清空 hint，下一次起步以"角度优先"为准。

---

# 4. 帧率与时长

像素角色动画的金科玉律：**每个动作的帧数 × 单帧时长 = 角色"行为节奏"**。Pythontopia 沿用星露谷式克制节奏，详 `00-visual-system-overview.md` §5.1。

## 4.1 各 state 帧率详表

| state | 帧数 | 单帧时长 | 一周期总时长 | Phaser `frameRate` | 循环 |
| --- | --: | --: | --: | --: | --- |
| `idle` | 1 | — | — | — | 静态（含 §4.2 眨眼例外）|
| `walk` | 8 | **80 ms** | **640 ms** | `12.5`（≈ 1000/80）| 是 |
| `run` | 8 | **50 ms** | **400 ms** | `20`（1000/50）| 是 |
| `interact` | 1 + tween | tween 300 ms 总 | 300 ms | — | 一次性 |
| `water` | 7 | 120 ms | 840 ms | `8.33`（≈ 1000/120）| 一次性 |
| `mine` | 8 | 100 ms | 800 ms | `10`（1000/100）| 一次性 |
| `talk` | 1 | — | — | — | 静态 |

> 现网 `sprite.ts:56` 写死 `frameRate: 10`，对应 walk 实际 100 ms/帧。本文升级到 **80 ms/帧**（更接近星露谷的 ~75 ms 节奏），改动只需把 `frameRate: 10` → `frameRate: 12.5`（或保持现状由 §9 验收时统一调整）。

## 4.2 idle 眨眼例外

`idle` 是 1 帧静止，但**每 5 秒**眨一次眼，2 帧切换共 200 ms，避免角色"死鱼眼"。眨眼帧不在 LPC 标准 sheet 上（LPC 未提供眨眼），有 3 种实现选择：

| 方案 | 实现 | 工作量 | 推荐 |
| --- | --- | --: | --- |
| A | 美术为每个角色 sheet 额外画 1 帧"闭眼"（放 row 20 / 列 7）| 高 | 否（20 角色 × 4 方向 = 80 张图）|
| B | RenderTexture 在 idle 帧脸部位置（y=22 ± 1 px）盖 2 px 高的肤色矩形 200 ms | 中 | **是（Beta 首发）** |
| C | 完全跳过眨眼，纯静态 | 低 | 否（违反 §4.2 标准）|

> 方案 B 由 `AnimationDecorator.blink()` 实现，对所有角色统一生效；颜色取 sheet 中心像素采样的肤色，自适应。

## 4.3 prefers-reduced-motion 行为

| 用户偏好 | walk/run 帧率 | interact/water/mine | idle 眨眼 |
| --- | --- | --- | --- |
| 无 | 标准 | 标准 | 5s 一次 |
| `reduce` | **不变**（剥夺角色动画感会破坏"像素小镇"叙事）| **缩短到 1 帧 + 立即完成**（去除"挥铲" / "浇水"中间帧）| **关闭** |

> `00-visual-system-overview.md` §5 已说明"sprite 帧率不变，过渡时长 ×0.5"；本文针对 interact/water/mine 一次性动画收紧到 1 帧立即完成，对运动控制零延迟。

---

# 5. 20 NPC sprite sheet 配置总表

每个 NPC 必须有独立的合成 LPC sheet：以同一套 LPC body base 为底，叠加 hair / clothes / accessory 三层，导出为 832 × 1344 px 的 PNG。文件名固定 `char_<npcKey>.png`，与 `data/npcs/<key>/meta.json` 的 `sprite` 字段去掉 `lpc_npc_` 前缀后**一致映射**（详 §6.3 同步脚本）。

> 所有层均来自 [LPC Spritesheet Generator](https://sanderfrenken.github.io/Universal-LPC-Spritesheet-Character-Generator/)（CC-BY-SA 3.0 / GPL 3.0+，已在 `LICENSE-ART.md` 第 80-95 行登记）；本文严禁出现"星露谷 NPC"或任何商业像素游戏 NPC 直接套用。

## 5.1 20 NPC × 4 维（body / clothes / hair / accessory）配置

| # | npcKey | sheet 文件名 | body base | clothes | hair | accessory |
| --: | --- | --- | --- | --- | --- | --- |
| 01 | `mayor_py` | `char_mayor_py.png` | `body/male/light` | `formal/suit_brown` | `hair/short_gray_messy` | `accessory/glasses_round` |
| 02 | `farmer_kc` | `char_farmer_kc.png` | `body/male/tanned` | `farming/overalls_blue` | `hair/short_brown_cap` | `accessory/straw_hat` |
| 03 | `foreman_bug` | `char_foreman_bug.png` | `body/male/dark` | `mining/vest_orange` | `hair/bald` | `accessory/helmet_miner` |
| 04 | `librarian_doc` | `char_librarian_doc.png` | `body/female/light` | `scholar/robe_navy` | `hair/long_black_bun` | `accessory/glasses_square` |
| 05 | `coach_q` | `char_coach_q.png` | `body/female/light` | `sport/tracksuit_red` | `hair/ponytail_brown` | `accessory/whistle` |
| 06 | `sage_review` | `char_sage_review.png` | `body/male/light` | `scholar/robe_purple` | `hair/long_white_beard` | `accessory/staff_owl` |
| 07 | `student_diligent` | `char_student_diligent.png` | `body/female/light` | `student/uniform_navy` | `hair/short_black_neat` | `accessory/backpack_blue` |
| 08 | `student_procrast` | `char_student_procrast.png` | `body/male/light` | `student/hoodie_gray` | `hair/messy_brown_long` | `accessory/headphones` |
| 09 | `student_anxious` | `char_student_anxious.png` | `body/female/pale` | `student/uniform_navy` | `hair/braids_brown` | `accessory/notebook_clutch` |
| 10 | `student_late_starter` | `char_student_late_starter.png` | `body/male/tanned` | `student/uniform_navy` | `hair/buzz_cut_black` | `accessory/wristband_red` |
| 11 | `student_top` | `char_student_top.png` | `body/male/light` | `student/blazer_gold` | `hair/short_blond_neat` | `accessory/medal_gold` |
| 12 | `student_hobby` | `char_student_hobby.png` | `body/female/light` | `casual/sweater_pink` | `hair/twin_tail_pink` | `accessory/camera` |
| 13 | `craftsman` | `char_craftsman.png` | `body/male/tanned` | `crafting/apron_brown` | `hair/short_brown` | `accessory/tool_belt` |
| 14 | `class_president` | `char_class_president.png` | `body/female/light` | `student/blazer_navy` | `hair/long_black_clip` | `accessory/armband_red` |
| 15 | `merchant` | `char_merchant.png` | `body/male/light` | `merchant/vest_green` | `hair/short_brown` | `accessory/pouch_coin` |
| 16 | `nurse` | `char_nurse.png` | `body/female/light` | `medical/uniform_white` | `hair/short_black_cap` | `accessory/cross_red` |
| 17 | `postman` | `char_postman.png` | `body/male/light` | `service/uniform_blue` | `hair/short_black_cap` | `accessory/satchel_brown` |
| 18 | `gardener` | `char_gardener.png` | `body/female/tanned` | `farming/overalls_green` | `hair/braids_brown` | `accessory/watering_can` |
| 19 | `chef` | `char_chef.png` | `body/male/light` | `service/chef_white` | `hair/short_brown` | `accessory/chef_hat` |
| 20 | `guard` | `char_guard.png` | `body/male/dark` | `service/uniform_navy` | `hair/buzz_cut_black` | `accessory/baton` |

> 玩家自身 `sara` 单独一张 `char_sara.png`，body=female/light + casual/sweater_yellow + ponytail_brown + 无 accessory，已在 `frontend/public/assets/pixel/characters/` 用 SVG 占位（详 `student_default.svg`），R-12 替换为合成 PNG。

## 5.2 与 `meta.json` 字段对应

`meta.json` 现状 `sprite: "lpc_npc_<role>"` 对应本文文件名规则：

| meta.json `sprite` | 本文 sheet 文件名 | 同步脚本 |
| --- | --- | --- |
| `lpc_npc_mayor` | `char_mayor_py.png` | `npm run sync:npc-data` 时按 `<npcKey>` 重命名 |
| `lpc_npc_farmer` | `char_farmer_kc.png` | 同上 |
| `lpc_npc_miner` | `char_foreman_bug.png` | 同上 |
| `lpc_npc_<role>` | `char_<npcKey>.png` | 同上 |

> 命名差异原因：`meta.json` 字段历史上按"职业 role"命名以便复用，本文按 `npcKey` 命名以保证唯一性。同步脚本 `frontend/scripts/sync-npc-data.mjs` 必须负责把 sheet 文件名 normalize 到 `char_<npcKey>.png`，避免双重事实。

---

# 6. 资产生成流程

## 6.1 LPC Spritesheet Generator 网页生成

| 步骤 | 操作 | 验收 |
| --- | --- | --- |
| 1 | 打开 [https://sanderfrenken.github.io/Universal-LPC-Spritesheet-Character-Generator/](https://sanderfrenken.github.io/Universal-LPC-Spritesheet-Character-Generator/) | 网页正常加载 |
| 2 | 按 §5.1 表格 4 列选层：body / clothes / hair / accessory | 预览图与目标 NPC 性格一致 |
| 3 | 点击 "Download PNG" 下载 832×1344 PNG | 像素正确、无背景污染 |
| 4 | 改名为 `char_<npcKey>.png` | 命名遵守 snake_case |
| 5 | 放入 `frontend/public/assets/pixel/characters/` | 路径正确 |
| 6 | 同步更新 §10 license 双簿 | 两个 LICENSE 文件均含本 sheet |

## 6.2 命名校验脚本（伪代码）

```ts
import { readdirSync } from 'node:fs';
import { resolve } from 'node:path';
import npcIndex from '../../data/npcs/index.json';

const dir = resolve('frontend/public/assets/pixel/characters');
const files = new Set(readdirSync(dir));
const missing: string[] = [];
for (const npc of npcIndex.npcs) {
  const expected = `char_${npc.key}.png`;
  if (!files.has(expected)) {
    missing.push(expected);
  }
}
if (missing.length > 0) {
  throw new Error(`Missing NPC sheets: ${missing.join(', ')}`);
}
```

> 该脚本接入 `scripts/check-asset-integrity.mjs`（待建）的 lint 阶段，CI 跑 `npm run lint:assets` 时自动校验 20 + 1 张 sheet 全部就位。

## 6.3 NPC sheet 与 meta 双向校验

`sync-npc-data.mjs` 在 `predev/prebuild` 钩子里跑：

1. 读 `data/npcs/index.json` 取 20 个 `npcKey`；
2. 校验 `frontend/public/assets/pixel/characters/char_<npcKey>.png` 存在；
3. 把校验后的 NPC meta 复制到 `frontend/public/data/npcs/<key>/`；
4. 若有缺失，**fail fast**，禁止 `npm run dev` 启动（避免静默占位）。

---

# 7. Phaser anim 配置代码片段（TypeScript）

下列代码示意如何把 §1 / §2 / §4 落到 `sprite.ts` 升级版。**仅作规范示例，不替代真实实现**——真实实现以本文为唯一来源在 R-12 落地。

```ts
// frontend/src/game/adapters/sprite.ts（升级版示意）
import Phaser from 'phaser';
import type { Direction4 } from '@api/dto/Direction';
import type { PlayerState } from '@game/core/PlayerStateMachine';

type Dir4Lower = 'up' | 'down' | 'left' | 'right';
const LPC_COLS = 13;

const WALK_ROW: Record<Direction4, number> = { UP: 8, LEFT: 9, DOWN: 10, RIGHT: 11 };
const THRUST_ROW: Record<Direction4, number> = { UP: 4, LEFT: 5, DOWN: 6, RIGHT: 7 };
const SPELL_ROW: Record<Direction4, number> = { UP: 0, LEFT: 1, DOWN: 2, RIGHT: 3 };

const FRAME_RATE: Record<Exclude<PlayerState, 'idle' | 'talk'>, number> = {
  walk: 12.5,
  run: 20,
  interact: 1,
  water: 8.33,
  mine: 10,
};

function animKey(spriteKey: string, state: PlayerState, dir: Direction4): string {
  return `${spriteKey}_${state}_${dir.toLowerCase() as Dir4Lower}`;
}

function rowFrames(row: number, cols: number[]): number[] {
  return cols.map((c) => row * LPC_COLS + c);
}

export function registerCharacterAnims(
  scene: Phaser.Scene,
  spriteKey: string,
): void {
  const dirs: Direction4[] = ['UP', 'LEFT', 'DOWN', 'RIGHT'];
  for (const dir of dirs) {
    const walkCols = [1, 2, 3, 4, 5, 6, 7, 8];
    const thrustCols = [0, 1, 2, 3, 4, 5, 6, 7];
    const spellCols = [0, 1, 2, 3, 4, 5, 6];

    scene.anims.create({
      key: animKey(spriteKey, 'walk', dir),
      frames: rowFrames(WALK_ROW[dir], walkCols).map((f) => ({ key: spriteKey, frame: f })),
      frameRate: FRAME_RATE.walk,
      repeat: -1,
    });
    scene.anims.create({
      key: animKey(spriteKey, 'run', dir),
      frames: rowFrames(WALK_ROW[dir], walkCols).map((f) => ({ key: spriteKey, frame: f })),
      frameRate: FRAME_RATE.run,
      repeat: -1,
    });
    scene.anims.create({
      key: animKey(spriteKey, 'water', dir),
      frames: rowFrames(SPELL_ROW[dir], spellCols).map((f) => ({ key: spriteKey, frame: f })),
      frameRate: FRAME_RATE.water,
      repeat: 0,
    });
    scene.anims.create({
      key: animKey(spriteKey, 'mine', dir),
      frames: rowFrames(THRUST_ROW[dir], thrustCols).map((f) => ({ key: spriteKey, frame: f })),
      frameRate: FRAME_RATE.mine,
      repeat: 0,
    });
  }
}
```

> `idle` / `talk` 不调用 `anims.create`，由调用方 `sprite.setFrame(WALK_ROW[dir] * LPC_COLS)` 直接切静态帧。`interact` 由 §2.3 tween 实现，亦不进 anims。

---

# 8. 优化：sheet 合并 atlas + RenderTexture layer 叠加

20 NPC + 1 玩家 = 21 张独立 LPC sheet，每张 832 × 1344 × 4 bytes ≈ **4.4 MB**，总计 **92 MB** GPU 纹理预算。这对桌面端可以接受，但对移动端（GPU 预算 256 MB）会吃掉 1/3。因此提供两条优化路径。

## 8.1 sheet 合并 atlas（TexturePacker）

| 项 | 值 |
| --- | --- |
| 工具 | TexturePacker 7.x（CC0 free 模式可用）/ 等价开源 `free-tex-packer-cli` |
| 输出格式 | 2 个文件：`characters_atlas.png` + `characters_atlas.json` |
| atlas 尺寸 | 4096 × 4096 px（足够装 21 张 832×1344）|
| 命名 frame | 每帧命名为 `<spriteKey>/r<row>c<col>`，例如 `mayor_py/r10c1` |
| 加载方式 | `scene.load.atlas('characters', 'characters_atlas.png', 'characters_atlas.json')` |
| 节省 | GPU 上下文切换 21 → 1，draw call 减半 |

> atlas 模式下 §7 的 `rowFrames` 需重写为返回 `string[]`（frame 名）而非 `number[]`（frame 索引）；Phaser `anims.create` 兼容两种。

## 8.2 RenderTexture layer 叠加（动态换装，R-13）

未来"玩家自选发型 / 衣服"需把 body / clothes / hair / accessory 4 层独立 sheet 在运行时合成：`Phaser.GameObjects.RenderTexture` 4 层叠加输出 832 × 1344 临时纹理，触发时机为玩家进入捏脸界面 / 服装店购买后，同身组合 LRU 缓存 8 套（≈ 35 MB）。Beta 首发**仅启用 §8.1**；RenderTexture 留待 R-13 服装系统启动后再开启。

## 8.3 性能预算总表

| 模式 | 加载文件数 | 总纹理体积 | 适用 |
| --- | --: | --: | --- |
| **裸 sheet**（当前） | 21 PNG | ≈ 92 MB GPU | 桌面 dev |
| **atlas 合并**（推荐 Beta）| 2 文件 | ≈ 64 MB GPU（atlas 自动 trim 空白）| 全端 prod |
| **RenderTexture 动态合成**（R-13）| 4 层 sheet × 角色数 | 按缓存数 35 MB（LRU 8）| 服装系统启用后 |

---

# 9. 验收：vitest `sprite.spec.ts` 用例

新增测试文件 `frontend/tests/game/adapters/sprite.spec.ts`，断言每个 `state × direction` 都能产出有效 anim key 或静态 frame。

## 9.1 测试矩阵

| 用例 | 输入 | 期望 |
| --- | --- | --- |
| 21 角色 × 5 anim state（walk/run/interact/water/mine） × 4 方向 = **420 anim key** | 调用 `registerCharacterAnims(scene, spriteKey)` | `scene.anims.exists(key) === true` |
| 21 角色 × 2 static state（idle/talk）× 4 方向 = **168 静态 frame** | 调用 `idleFrame(dir)` / `talkFrame(dir)` | 返回值 = `WALK_ROW[dir] * 13` |
| 28 玩家 anim key 命名 | 用 `sara_<state>_<dir>` 全枚举 | key 格式正则 `^sara_(walk|run|interact|water|mine|idle|talk)_(up|down|left|right)$` 通过 |
| 8 方向降级 priorityHint | 输入序列 `NE, N, NE, N` + hint=`RIGHT` | 输出全部 `RIGHT` |
| 8 方向降级 无 hint | 输入 `NE` + hint=`null` | 输出 `RIGHT` |
| LPC sheet 加载 | `scene.load.spritesheet('char_mayor_py', ...)` | 832×1344, 13 列, 21 行 |

## 9.2 测试代码骨架

```ts
import { describe, it, expect } from 'vitest';
import { registerCharacterAnims, idleFrame } from '@game/adapters/sprite';
import { createMockScene } from '../testkit/phaserScene';

const NPC_KEYS = ['sara', 'mayor_py', /* ... 全 21 角色 */];
const STATES = ['walk', 'run', 'interact', 'water', 'mine'] as const;
const DIRS = ['UP', 'LEFT', 'DOWN', 'RIGHT'] as const;

describe('sprite animation registration', () => {
  it('registers 420 anim keys (21 × 5 × 4)', () => {
    const scene = createMockScene();
    NPC_KEYS.forEach((k) => registerCharacterAnims(scene, k));
    for (const k of NPC_KEYS) for (const s of STATES) for (const d of DIRS) {
      expect(scene.anims.exists(`${k}_${s}_${d.toLowerCase()}`)).toBe(true);
    }
  });

  it('idle / talk return walk row × 13 (frame col 0)', () => {
    const expected = { UP: 104, LEFT: 117, DOWN: 130, RIGHT: 143 };
    DIRS.forEach((d) => expect(idleFrame(d)).toBe(expected[d]));
  });
});
```

> `createMockScene` 返回最小化 `Phaser.Scene` mock，仅含 `anims.create` / `anims.exists`，无需启动 WebGL。

## 9.3 CI 接入

| 命令 | 触发 | 阈值 |
| --- | --- | --- |
| `npm run test -- sprite.spec.ts` | 每次 PR | 420 anim key 全通过 |
| `npm run lint:assets` | 每次 PR | 21 张 sheet 文件存在校验 |
| `npm run typecheck` | 每次 PR | `registerCharacterAnims` 签名 / `Direction4` 枚举类型一致 |

---

# 10. License 双簿登记（强制）

每张 NPC sheet 必须同时进入 2 个 license 文件：

| 文件 | 字段 |
| --- | --- |
| `/home/cypress/Pythontopia/LICENSE-ART.md` | 顶层资产总表，按"作品名 / 作者 / 来源 URL / 协议 / 接入 commit" 5 列登记 |
| `/home/cypress/Pythontopia/frontend/public/assets/pixel/LICENSE.md` | 运行时清单，按"路径 / 协议 / 简短致谢" 3 列登记 |

## 10.1 LPC sheet 通用 license 行（21 张共用一行模板）

```
| char_<npcKey>.png | LPC Spritesheet Generator | https://sanderfrenken.github.io/Universal-LPC-Spritesheet-Character-Generator/ | CC-BY-SA 3.0 | R-12-W3 |
```

> body / clothes / hair / accessory 的原画师署名由 LPC Generator 网页底部"Show credits"自动列出，必须在 `frontend/public/credits/lpc.md` 维护完整作者列表（参 `00-visual-system-overview.md` §12）。

## 10.2 LPC CC-BY-SA 3.0 传染合规摘要

| 条款 | Pythontopia 落地 |
| --- | --- |
| 同协议传播 | 21 张 NPC sheet 与最终游戏二进制以 **CC-BY-SA 3.0+** 公开发布（已在 `AGENTS.md` 接受）|
| 署名 | about 页面署名 LPC 项目 + 各层原作者 |
| 衍生作品 | RenderTexture 合成的临时纹理同样属于衍生作品，传染协议适用 |
| 代码层 | 代码（包括 `sprite.ts`）保持 **MIT**，与资产层分层 |

## 10.3 违规清单（严禁）

| 来源 | 后果 |
| --- | --- |
| 星露谷 / 牧场物语 / 莱莎工房 / RPG Maker 内置素材 | 商业版权，立删 + PR reject |
| itch.io 付费素材未购授权 / nightmaresprite | 立删 |
| AI 生成 sprite 未标注训练集 | 暂禁，待 R-13 政策更新 |

---

# 11. 验收与生效

| 项 | 验收标准 | 责任人 |
| --- | --- | --- |
| 本文档审阅 | 像素美术 + Phaser 程序员双签 | 提案人 |
| 21 张 LPC sheet 就位 | `frontend/public/assets/pixel/characters/char_*.png` 21 个 | 美术 |
| §6.2 命名校验脚本 | 接入 CI lint，缺失 fail fast | 前端工程师 |
| `registerCharacterAnims` 升级 | `sprite.ts` 改造为 §7 示意 | Phaser 程序员 |
| §9 vitest 用例 | 420 anim key + 168 静态 frame 全通过 | 前端工程师 |
| §3.2 priorityHint 防颤 | WorldScene 每帧应用 | Phaser 程序员 |
| §8.1 atlas 合并 | TexturePacker 跑通输出 2 文件 | 美术 + 前端工程师 |
| license 双簿 | `LICENSE-ART.md` + `pixel/LICENSE.md` 同步登记 | 美术 |

完成上述 8 项后，本文档由 Draft 升级为 Active（v1.0 → v1.0-released）。

---

> **本文是宪法，不是说明书**。任何新角色状态（如 R-13 教师视角"巡视" / 班长视角"点名"）必须先回到本文 §2 状态表新增条目并对齐 LPC 行块；新 NPC 必须先在 §5.1 表格新增一行并补 sheet 与 license；新方向需求（如 16 方向骑行）必须重写 §3。**严禁在 WorldScene / NpcFramework 组件层硬编码 LPC 行号、帧号、anim key。**
