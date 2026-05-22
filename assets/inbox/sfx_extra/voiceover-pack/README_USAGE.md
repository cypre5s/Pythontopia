# Kenney Voiceover Pack → Pythontopia 事件映射

> 来源：https://kenney.nl/assets/voiceover-pack （v1.0）
> License：CC0 1.0（详见同目录 `License.txt`）
> 配音：Jeffrey M. Smith（男声）、Giselle（女声），详见 `Credits.txt`
> 文件：92 个 `.ogg`（`Female/` 46 + `Male/` 46，每侧基本一一对应），另含 3 个 `Preview*.ogg`
>
> 实际差异：`Female/` 有 `war_hold.ogg`、`war_supressing_fire.ogg`（拼写有缺），`Male/` 有 `hold.ogg`、`war_suppressing_fire.ogg`（拼写完整）。两边数量相同但词条不完全一致。
> 风格：英语清晰播报，适合"班级 PK""答题""每日任务""倒计时"等需要"播音员"语境的场景。

## 类目概览

| 子目录 | 数量 | 说明 |
| --- | --- | --- |
| `Female/` | 46 | 女声完整集，含 1-10、ready/set/go、correct/wrong、mission_*、war_* 等 |
| `Male/` | 46 | 男声完整集，对应同样语料 |

两个目录基本对称（差异：Male 多 `hold.ogg`，Female 无；Female 多若干 war_* 单词）。

## 推荐映射（教学正向词）

| 文件（Female/Male 都有） | Pythontopia 事件 | 备注 |
| --- | --- | --- |
| `correct.ogg` | 答题正确 | 与 `digital-audio/pepSound` 互补；用配音强调单次正确 |
| `wrong.ogg` | 答题错误（轻） | 不带负面情绪，适合教学 |
| `ready.ogg` | 提交前倒计时 3 | 与 `set.ogg`+`go.ogg` 组合 |
| `set.ogg` | 提交前倒计时 2 | 同上 |
| `go.ogg` | 提交前倒计时 1 / 启动 | 触发后立刻开始 |
| `1.ogg` … `10.ogg` | 计时器整数刻度（"还剩 5 秒"） | 可用于限时答题 |
| `round.ogg` | "第 X 回合"前缀 | 与 round digits 拼接 |
| `final_round.ogg` | 周任务最后一题 / 章节最后一题 | |
| `level.ogg`, `level_up.ogg` | Pythontopia Level Up 通知 | Level Up 是核心事件 |
| `power_up.ogg` | KC buff 激活、连胜加成 | |
| `mission_completed.ogg` | 主线任务完成 | 比 jingle 更"播报感" |
| `mission_failed.ogg` | 主线任务失败 | 替代 jingle 的负反馈 |
| `objective_achieved.ogg` | KC 收获、阶段目标达成 | |
| `new_highscore.ogg` | 新成就解锁、KC 掌握度新高 | |
| `congratulations.ogg` | 章节通关、班级排行第 1 | |
| `you_win.ogg`, `you_lose.ogg` | 班级 PK / 知识竞赛对决结果 | 仅 PK 场景 |
| `game_over.ogg` | 班级 PK 结束（中性） | |
| `its_a_tie.ogg` | 班级 PK 平局 | |
| `time_over.ogg` | 限时答题结束 | |
| `hurry_up.ogg` | 限时答题剩余 5s 内 | 谨慎，可能制造焦虑 |

## ⚠️ 不建议在教育场景使用（war_* 系列）

下列文件主题为军事/战斗指令，与 Pythontopia 教育调性不符。**保留原文件不删，但产品里不要 wire 到任何事件**：

| 文件 | 原意 |
| --- | --- |
| `Female/war_call_for_backup.ogg`, `Male/war_call_for_backup.ogg` | 呼叫支援 |
| `Female/war_cover_me.ogg`, `Male/war_cover_me.ogg` | 掩护我 |
| `Female/war_fire_in_the_hole.ogg`, `Male/war_fire_in_the_hole.ogg` | 注意爆炸 |
| `Female/war_get_down.ogg`, `Male/war_get_down.ogg` | 趴下 |
| `Female/war_go_go_go.ogg`, `Male/war_go_go_go.ogg` | 上上上（军事冲锋） |
| `Female/war_hold.ogg`, `Male/hold.ogg` | 待命/守住 |
| `Female/war_look_out.ogg`, `Male/war_look_out.ogg` | 当心 |
| `Female/war_medic.ogg`, `Male/war_medic.ogg` | 医疗兵 |
| `Female/war_reloading.ogg`, `Male/war_reloading.ogg` | 换弹 |
| `Female/war_rpg.ogg`, `Male/war_rpg.ogg` | RPG（火箭筒） |
| `Female/war_sniper.ogg`, `Male/war_sniper.ogg` | 狙击手 |
| `Female/war_supressing_fire.ogg`, `Male/war_suppressing_fire.ogg` | 压制射击 |
| `Female/war_target_destroyed.ogg`, `Male/war_target_destroyed.ogg` | 目标已摧毁 |
| `Female/war_target_engaged.ogg`, `Male/war_target_engaged.ogg` | 目标已交火 |
| `Female/war_watch_my_back.ogg`, `Male/war_watch_my_back.ogg` | 看我身后 |

> 已遵循 AGENTS.md 中 "已剔除可能与课程主题不匹配的素材（如武器、暴力、过度卡通化）" 原则。
> 这些 war_* 文件不删除（CC0 不可撤回完整性），仅打 ⚠ 标签禁止接入产品。

## 性别选择策略

为避免性别偏见，推荐：
- 班级老师 NPC：Male（默认）
- AI 助教（Pythontopia "Assistant" 角色）：Female
- 答题 NPC（学生角色）：随机
- 班级 PK 主持：循环 Male/Female

产品端实现一个 `voicePack: 'female' | 'male' | 'random'` 偏好键，由用户在 Profile 设置中切换。

## 命名建议

```text
sfx/vo/correct_f.ogg <- Female/correct.ogg
sfx/vo/correct_m.ogg <- Male/correct.ogg
sfx/vo/wrong_f.ogg
sfx/vo/wrong_m.ogg
sfx/vo/level_up_f.ogg, level_up_m.ogg
sfx/vo/mission_complete_f.ogg, mission_complete_m.ogg
sfx/vo/countdown_3_f.ogg <- Female/ready.ogg
sfx/vo/countdown_2_f.ogg <- Female/set.ogg
sfx/vo/countdown_1_f.ogg <- Female/go.ogg
sfx/vo/digit_1_f.ogg <- Female/1.ogg
... (其余按推荐映射照搬)
```

不要把 `war_*` 复制到 `sfx/vo/`，仅保留在原始 `sfx_extra/voiceover-pack/` 内供 license 审计。
