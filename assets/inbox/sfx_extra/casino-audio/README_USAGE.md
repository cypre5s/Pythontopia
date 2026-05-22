# Kenney Casino Audio → Pythontopia 事件映射

> 来源：https://kenney.nl/assets/casino-audio （v1.1）
> License：CC0 1.0（详见同目录 `License.txt`）
> 文件：54 个 `.ogg`（`Audio/` 下），另含 `Preview.ogg`
> 风格：扑克牌、筹码、骰子，节奏短促清脆，适合 UI 反馈和奖励反馈

## 类目概览

| 类目 | 数量 | 文件命名前缀 |
| --- | --- | --- |
| 翻牌/抽卡 | 22 | `card-fan-*`、`card-place-*`、`card-shove-*`、`card-slide-*`、`card-shuffle`、`cards-pack-open-*`、`cards-pack-take-out-*` |
| 筹码 | 19 | `chip-lay-*`、`chips-collide-*`、`chips-handle-*`、`chips-stack-*` |
| 骰子 | 14 | `dice-grab-*`、`dice-shake-*`、`dice-throw-*`、`die-throw-*` |
| 预览 | 1 | `Preview.ogg`（不建议入产品） |

## 推荐映射

| 文件 | Pythontopia 事件 | 备注 |
| --- | --- | --- |
| `Audio/card-fan-1.ogg`, `card-fan-2.ogg` | 抽取每日任务卡、班级公告板翻牌 | 短促快感，适合 UI 出现 |
| `Audio/card-place-1..4.ogg` | 任务卡放入背包、KC 卡片入卡组 | 比 fan 更"沉"，落定感 |
| `Audio/card-shove-1..4.ogg` | 投递作业、提交练习入栈 | 与 place 互补，可做"批量" |
| `Audio/card-shuffle.ogg` | 学习路径重排、班级随机组队 | 30 KB 较长，仅事件入口 |
| `Audio/card-slide-1..8.ogg` | 资源卡片切换、KC 横向滚动 | 多变体可随机播 |
| `Audio/cards-pack-open-1..2.ogg` | 开启新章节、解锁周任务包 | "撕封"感，仪式感强 |
| `Audio/cards-pack-take-out-1..2.ogg` | 抽取奖励卡 | open 的轻量版 |
| `Audio/chip-lay-1..3.ogg` | 单次正确答题 +1 coin | 干脆，避免与 RPG `coin` 冲突时只取一个 |
| `Audio/chips-collide-1..4.ogg` | 连答正确 +N coin 时的累积反馈 | 与 chip-lay 配合 |
| `Audio/chips-handle-1..6.ogg` | 背包/钱包整理、金币结算页 | 长一些 |
| `Audio/chips-stack-1..6.ogg` | 一次性结算多枚奖励、KC 收获堆叠 | 仪式感最强 |
| `Audio/dice-grab-1..2.ogg` | 随机抽题准备 | 提交前预备音 |
| `Audio/dice-shake-1..3.ogg` | 随机事件（矿洞遭遇 / 题目抽取）摇骰 | 0.5-1s 不建议 loop |
| `Audio/dice-throw-1..3.ogg`, `die-throw-1..4.ogg` | 随机事件落定、Bug 矿洞触发 | 比 RPG `dig` 更轻 |

## 不推荐使用

- `Preview.ogg`：含多个素材混剪，仅用于试听，不要直接打包到产品。

## 命名建议（移交分类时）

按 Pythontopia 既有 `assets/inbox/sfx/<event>/` 风格，建议将本包筛选后移入：

```text
sfx/coin_chip/        # chip-lay, chips-collide, chips-handle, chips-stack
sfx/card_flip/        # card-fan, card-place, card-shove, card-slide
sfx/card_shuffle/     # card-shuffle, cards-pack-*
sfx/dice_random/      # dice-*, die-throw-*
```
