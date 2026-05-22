# Kenney Sci-fi Sounds → Pythontopia 事件映射

> 来源：https://kenney.nl/assets/sci-fi-sounds （v1.0）
> License：CC0 1.0（详见同目录 `License.txt`）
> 文件：73 个 `.ogg`（`Audio/`）。无 `Preview.ogg`（被 Kenney 删除），可直接用每个分类的 000 作试听。
> 风格：现代科幻打击/激光/引擎/门，可服务于 Bug 矿洞、AI 助手、教室房间切换等。

## 类目概览

| 类目 | 数量 | 文件命名前缀 |
| --- | --- | --- |
| 计算机噪音 | 4 | `computerNoise_000..003` |
| 门-关 | 3 | `doorClose_000..002` |
| 门-开 | 3 | `doorOpen_000..002` |
| 引擎-圆周 | 5 | `engineCircular_000..004` |
| 爆炸-酥脆 | 5 | `explosionCrunch_000..004` |
| 力场 | 5 | `forceField_000..004` |
| 金属冲击 | 5 | `impactMetal_000..004` |
| 大型激光 | 5 | `laserLarge_000..004` |
| 复古激光 | 5 | `laserRetro_000..004` |
| 小型激光 | 5 | `laserSmall_000..004` |
| 低频爆炸 | 2 | `lowFrequency_explosion_000..001` |
| 黏液 | 2 | `slime_000..001` |
| 大型飞船引擎 | 5 | `spaceEngineLarge_000..004` |
| 低音飞船引擎 | 5 | `spaceEngineLow_000..004` |
| 小型飞船引擎 | 5 | `spaceEngineSmall_000..004` |
| 通用飞船引擎 | 4 | `spaceEngine_000..003` |
| 推进器点火 | 5 | `thrusterFire_000..004` |

## 推荐映射

| 文件 | Pythontopia 事件 | 备注 |
| --- | --- | --- |
| `Audio/doorOpen_000.ogg`, `doorOpen_001.ogg` | 教室/实验室/宿舍/图书馆进入 | 适合切换房间 |
| `Audio/doorClose_000.ogg`, `doorClose_001.ogg` | 课堂结束、关卡退出 | 与 doorOpen 配对 |
| `Audio/computerNoise_000..003.ogg` | AI 助手 / 系统提示出现 | 比 RPG 配音柔和，可做 prompt 引导 |
| `Audio/forceField_000..004.ogg` | 状态护盾（专注度 buff、连胜保护） | 持续约 1.5s，适合短 loop |
| `Audio/impactMetal_000..004.ogg` | 矿洞挖掘补充音（与现有 `sfx/dig/` 区分） | 偏金属，扩展现有挖矿层次 |
| `Audio/laserSmall_000..004.ogg` | 矿洞中玩家短攻击 / 编辑器键入特效 | 比 digital `laser*` 更紧凑 |
| `Audio/laserRetro_000..004.ogg` | Bug 矿洞武器（玩家） | 怀旧感，适合像素调性 |
| `Audio/laserLarge_000..004.ogg` | 矿洞 Boss 出现 / 必杀技 | 谨慎，使用频率应低 |
| `Audio/explosionCrunch_000..004.ogg` | Bug 矿洞 Boss 倒下、关键 Bug 被解决 | 一次性事件 |
| `Audio/lowFrequency_explosion_000..001.ogg` | 严重错误（KernelError、系统崩溃模拟） | 仅出现于深度 Bug |
| `Audio/spaceEngineSmall_000..004.ogg` | 矿洞内 BGM 轮播底（短 loop） | 可做底噪 |
| `Audio/spaceEngineLow_000..004.ogg` | 实验室 / 工坊背景（低频 hum） | 适合学习深沉环境 |
| `Audio/thrusterFire_000..004.ogg` | 任务启动 / 路径推进过场 | "起飞"感 |
| `Audio/engineCircular_000..004.ogg` | 周/日任务循环刷新提示 | 圆周感 |
| `Audio/slime_000.ogg`, `slime_001.ogg` | Bug 黏液系怪物攻击（如果 Bug 矿洞做敌人分种） | 二号文件较长（159KB），可剪短 |

## 不推荐使用

- `Audio/spaceEngineLarge_*.ogg`、`spaceEngine_*.ogg` 单文件较大（~200KB），不建议在 web 客户端常驻；可改为按需加载或作为单次场景音。
- 持续不衰减的 `engineCircular_*.ogg` 不要做长 loop（>2 分钟）。

## 命名建议

```text
sfx/room_enter/        # doorOpen_000..002
sfx/room_exit/         # doorClose_000..002
sfx/ai_prompt/         # computerNoise_000..003
sfx/buff_shield/       # forceField_000..004
sfx/mine_dig_metal/    # impactMetal_000..004
sfx/mine_player_atk/   # laserSmall_*, laserRetro_*
sfx/mine_boss_atk/     # laserLarge_*
sfx/mine_boss_down/    # explosionCrunch_*
sfx/error_critical/    # lowFrequency_explosion_*
sfx/bgm_mine/          # spaceEngineSmall_*
sfx/bgm_workshop/      # spaceEngineLow_*
sfx/task_launch/       # thrusterFire_*
sfx/task_refresh/      # engineCircular_*
sfx/mine_slime/        # slime_*
```
