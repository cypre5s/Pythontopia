# Kenney Digital Audio → Pythontopia 事件映射

> 来源：https://kenney.nl/assets/digital-audio （v1.0）
> License：CC0 1.0（详见同目录 `License.txt`）
> 文件：62 个 `.ogg`（`Audio/`），另含 `Preview.ogg`
> 风格：8-bit / chiptune 电子合成，bleep 与 zap 风，适合 UI 反馈、答题正误、矿洞战斗

## 类目概览

| 类目 | 数量 | 文件命名前缀 |
| --- | --- | --- |
| 升降音（短提示） | 4 | `highUp`, `highDown`, `lowDown`, `lowRandom` |
| 二段/三段音 | 5 | `twoTone1..2`, `threeTone1..2`, `lowThreeTone` |
| 单音 | 1 | `tone1` |
| Pep（短促正反馈） | 5 | `pepSound1..5` |
| Power Up | 12 | `powerUp1..12` |
| Phase Jump | 5 | `phaseJump1..5` |
| Phaser Down | 3 | `phaserDown1..3` |
| Phaser Up | 7 | `phaserUp1..7` |
| Laser | 9 | `laser1..9` |
| Zap | 6 | `zap1..2`, `zapThreeToneDown`, `zapThreeToneUp`, `zapTwoTone`, `zapTwoTone2` |
| Space Trash（杂音/故障） | 5 | `spaceTrash1..5` |

## 推荐映射

| 文件 | Pythontopia 事件 | 备注 |
| --- | --- | --- |
| `Audio/pepSound1.ogg` … `pepSound5.ogg` | 答题正确（短）、按钮 hover 强反馈 | 5 个变体避免重复 |
| `Audio/highUp.ogg` | 答题正确（升调） | 与 pep 二选一 |
| `Audio/twoTone1.ogg`, `twoTone2.ogg` | 答题正确（带确认） | 课件页跳转可用 |
| `Audio/threeTone1.ogg`, `threeTone2.ogg` | KC 掌握达标、连答 +3 | 节奏感更明显 |
| `Audio/highDown.ogg` | 答题错误（轻） | 比 zap 弱，不打击信心 |
| `Audio/lowDown.ogg` | 提交失败、API 报错 | 与系统级错误对应 |
| `Audio/lowRandom.ogg` | 网络异常、Token 失效 | 不刺耳 |
| `Audio/lowThreeTone.ogg` | 任务失败（递降提示） | 比 RPG `mission_failed` 短 |
| `Audio/powerUp1.ogg` … `powerUp12.ogg` | KC 收获、学习路径解锁、Level Up | 12 个变体足够全程使用，建议固定 1-3 个映射 |
| `Audio/phaseJump1.ogg` … `phaseJump5.ogg` | 章节切换、教室跳转 | "传送"感 |
| `Audio/phaserUp1.ogg` … `phaserUp7.ogg` | 状态增益（专注、连胜 buff） | 上扬感 |
| `Audio/phaserDown1.ogg` … `phaserDown3.ogg` | 状态衰减（遗忘曲线、buff 失效） | 下降感 |
| `Audio/laser1.ogg` … `laser9.ogg` | Bug 矿洞战斗（玩家攻击） | 与 RPG `coin` 区分 |
| `Audio/zap1.ogg`, `zap2.ogg` | Bug 矿洞 / 错误警示 | 较刺激，谨慎使用 |
| `Audio/zapThreeToneDown.ogg` | 严重错误（编译错误） | 比 lowThreeTone 重 |
| `Audio/zapThreeToneUp.ogg` | 关键正反馈（成就解锁） | 与 powerUp 互补 |
| `Audio/zapTwoTone.ogg`, `zapTwoTone2.ogg` | 提示窗口出现 / 关闭 | UI 元素 |
| `Audio/spaceTrash1..5.ogg` | Bug 矿洞背景杂音、故障节点 | 不要 loop，需做衰减 |
| `Audio/tone1.ogg` | 课件页确认、轻提示 | 最克制的一项 |

## 不推荐使用

- 在同一界面里同时混用 `laser*` 和 `zap*`，听感会过密。建议先选 `pep`+`twoTone`+`powerUp` 三类做 MVP。
- `spaceTrash*` 类杂音若做 BGM 会过于嘈杂，仅作事件触发。

## 命名建议

```text
sfx/correct/          # pepSound*, highUp, twoTone*, threeTone*
sfx/wrong/            # highDown, lowDown, lowThreeTone, zapThreeToneDown
sfx/level_up/         # powerUp1..12, zapThreeToneUp
sfx/teleport/         # phaseJump*
sfx/buff/             # phaserUp*
sfx/debuff/           # phaserDown*
sfx/mine_attack/      # laser1..9
sfx/mine_warn/        # zap1..2, zapTwoTone*
sfx/glitch/           # spaceTrash*
```
