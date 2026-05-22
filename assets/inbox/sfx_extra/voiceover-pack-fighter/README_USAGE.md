# Kenney Voiceover Pack — Fighter → Pythontopia 事件映射

> 来源：https://kenney.nl/assets/voiceover-pack-fighter （v1.0）
> License：CC0 1.0（详见同目录 `License.txt`）
> 文件：46 个 `.ogg`（`Audio/`），另含 `Preview.ogg`
> 风格：**格斗游戏配音**（英文男声播报），冠以"choose your character""arcade mode""flawless victory""sudden death"等 fighter genre 术语。
> 与 `voiceover-pack` 是姐妹包，**整体调性偏战斗**，本次连同下载是因 sibling 关系与 Kenney 同源；**仅推荐少量适配项**用于 Pythontopia 班级 PK 与回合制答题。

## 类目概览

| 类目 | 数量 | 例 |
| --- | --- | --- |
| 数字 | 10 | `1.ogg` … `10.ogg` |
| 回合 | 6 | `round_1..5.ogg`, `final_round.ogg` |
| 比赛流程 | 8 | `ready`, `begin`, `fight`, `time`, `tie`, `it's_a_tie`, `tie_breaker`, `game_over` |
| 模式 | 6 | `arcade_mode`, `battle_mode`, `championship_mode`, `story_mode`, `survival_mode`, `deathmatch` |
| 角色 | 3 | `choose_your_character`, `player_1`, `player_2` |
| 战斗结果 | 6 | `winner`, `you_win`, `you_lose`, `loser`, `flawless_victory`, `multi_kill` |
| 战斗动作 | 5 | `combo`, `combo_breaker`, `sudden_death`, `prepare_yourself`, `kill_her`, `kill_him`, `kill_it` |

## 推荐映射（教学正向词，**白名单**）

| 文件 | Pythontopia 事件 | 备注 |
| --- | --- | --- |
| `Audio/1.ogg` … `10.ogg` | 限时答题倒计时整数 | 与 `voiceover-pack` 男声数字风格更激烈，可用于"对决模式" |
| `Audio/round_1.ogg` … `round_5.ogg` | 班级 PK / 知识竞赛 回合播报 | |
| `Audio/final_round.ogg` | 最终回合 | |
| `Audio/ready.ogg` | PK 开始前倒计时 3 | |
| `Audio/begin.ogg` | PK 正式开始（替代"fight"） | 教学场景请用 begin，不用 fight |
| `Audio/time.ogg` | 计时器到 | |
| `Audio/tie.ogg`, `it's_a_tie.ogg`, `tie_breaker.ogg` | 班级 PK 平局 / 加赛 | |
| `Audio/game_over.ogg` | PK 结束（中性） | |
| `Audio/winner.ogg` | 胜者播报 | |
| `Audio/you_win.ogg`, `you_lose.ogg` | 个人 PK 结果 | |
| `Audio/flawless_victory.ogg` | 完美通关（全对） | 教学正向激励 |
| `Audio/combo.ogg` | 连答正确 ≥3 | "连胜"语义 |
| `Audio/combo_breaker.ogg` | 连胜中断 | 提示，非贬义 |
| `Audio/arcade_mode.ogg`, `story_mode.ogg`, `championship_mode.ogg`, `survival_mode.ogg` | 不同学习模式播报（练习/挑战/对决/生存复习） | 玩法包装 |
| `Audio/player_1.ogg`, `player_2.ogg` | 班级 PK 玩家轮换 | |

## ⚠️ 不建议使用（**黑名单**，禁止 wire 到事件）

下列文件含"杀戮""死斗"等暴力语义，与 Pythontopia 教育调性冲突。**保留文件不删（CC0 完整性要求），仅打 ⚠ 标签**：

| 文件 | 原意 | 屏蔽原因 |
| --- | --- | --- |
| `Audio/fight.ogg` | 开打 | 替代品：用 `begin.ogg` |
| `Audio/kill_her.ogg` | "干掉她" | 性别+暴力，绝对屏蔽 |
| `Audio/kill_him.ogg` | "干掉他" | 同上 |
| `Audio/kill_it.ogg` | "干掉它" | 暴力 |
| `Audio/multi_kill.ogg` | "连杀" | 暴力 |
| `Audio/sudden_death.ogg` | "突然死亡赛" | 死亡语义 |
| `Audio/deathmatch.ogg` | "死斗模式" | 死亡语义 |
| `Audio/prepare_yourself.ogg` | "准备战斗" | 战斗威胁感 |
| `Audio/battle_mode.ogg` | "战斗模式" | "battle" 在教学环境易引起争议，可用 `arcade_mode` 替代 |
| `Audio/loser.ogg` | "失败者" | 贬义标签，不要在学生身上播 |
| `Audio/choose_your_character.ogg` | "选择你的角色" | 单看无问题，但与本包整体战斗语境强绑定，谨慎使用 |

## 使用建议

1. **默认全部禁用**：本包黑名单较多，集成时建议在配置层显式 opt-in 白名单文件。
2. **仅在 PK 模式启用**：白名单文件仅在班级 PK / 知识竞赛 模式播放，不在日常学习中触发。
3. **配合女声**：当与 `voiceover-pack/Female/` 混用时，可让 fighter 男声做"主持"，女声做"选手"或反之，避免单一性别绑定。

## 命名建议

```text
# 只搬白名单
sfx/vo_pk/digit_1.ogg <- 1.ogg
...
sfx/vo_pk/round_1.ogg
...
sfx/vo_pk/begin.ogg
sfx/vo_pk/winner.ogg
sfx/vo_pk/combo.ogg
sfx/vo_pk/flawless_victory.ogg
sfx/vo_pk/mode_arcade.ogg <- arcade_mode.ogg
sfx/vo_pk/mode_story.ogg <- story_mode.ogg
```

黑名单文件**不要**复制到 `sfx/`，仅保留在 `sfx_extra/voiceover-pack-fighter/Audio/` 内供 license 审计。
