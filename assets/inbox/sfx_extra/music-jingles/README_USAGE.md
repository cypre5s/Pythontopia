# Kenney Music Jingles → Pythontopia 事件映射

> 来源：https://kenney.nl/assets/music-jingles （v1.0）
> License：CC0 1.0（详见同目录 `License.txt`）
> 文件：85 个 `.ogg`（`Audio/<风格>/`），另含 `Preview.ogg`
> 风格：5 个乐器风格，每个 17 个 1-3 秒短乐句（jingle）。用于"事件完成""任务里程碑"等需要乐感的反馈。

## 类目概览

| 子目录 | 风格 | 数量 | Pythontopia 主推场景 |
| --- | --- | --- | --- |
| `Audio/8-Bit jingles/` | 8-bit NES（jingles_NES00..16） | 17 | 怀旧任务完成、KC 收获，配合像素 UI 最契合 |
| `Audio/Hit jingles/` | 打击乐（jingles_HIT00..16） | 17 | 关键提交（正式答题）、矿洞 Boss 出现 |
| `Audio/Pizzicato jingles/` | 拨弦弦乐（jingles_PIZZI00..16） | 17 | 轻量提示、教室进入、NPC 出现 |
| `Audio/Sax jingles/` | 萨克斯（jingles_SAX00..16） | 17 | 周任务完成、班级日报推送 |
| `Audio/Steel jingles/` | 钢片琴（jingles_STEEL00..16） | 17 | 资源生成完成、AI 答辩通过 |

> 每个子目录 17 个文件，序号 `00` 通常较"完整"，`01..16` 偏短促 stinger。建议挑选 2-3 个作为主力。

## 推荐映射（默认 MVP 选择）

| 文件 | Pythontopia 事件 | 备注 |
| --- | --- | --- |
| `Audio/8-Bit jingles/jingles_NES00.ogg` | 主线任务完成（章节通关） | ~3s，最长版 |
| `Audio/8-Bit jingles/jingles_NES03.ogg` | 日常任务完成（短） | ~0.7s |
| `Audio/8-Bit jingles/jingles_NES07.ogg` | KC 首次掌握 | ~0.6s |
| `Audio/Pizzicato jingles/jingles_PIZZI00.ogg` | 章节切换 / 教室进入 | 较轻 |
| `Audio/Pizzicato jingles/jingles_PIZZI04.ogg` | NPC 招呼 | 拨弦 |
| `Audio/Sax jingles/jingles_SAX00.ogg` | 周任务完成、班级排行揭晓 | 暖色 |
| `Audio/Sax jingles/jingles_SAX05.ogg` | 一次正确答题（庆祝） | 略中性 |
| `Audio/Steel jingles/jingles_STEEL00.ogg` | AI 资源生成完成 | 钢琴空灵感 |
| `Audio/Steel jingles/jingles_STEEL03.ogg` | 学习路径推荐生成 | 较冷 |
| `Audio/Hit jingles/jingles_HIT11.ogg` | 矿洞 Boss 出现（Bug 上演） | 重音 |
| `Audio/Hit jingles/jingles_HIT00.ogg` | 严重错误（编译失败） | 低沉 |

## 使用建议

1. **一个事件锁定一个风格**：例如"任务完成"长期固定用 `NES00`，避免多 jingle 抢戏。
2. **不要 BGM 化**：本包不是循环音乐，不要在背景里反复播。
3. **音量控制**：jingle 比 SFX 响，建议产品端在事件播放前把 SFX 通道音量降低 0.3。
4. **AI 提示场景**：`Steel jingles` 的 STEEL00/03 适合给 AI Agent 输出绑定，区别于学生自身行为。

## 不推荐使用

- `Audio/Hit jingles/` 中较为暴力的 HIT12-HIT16，不建议用于答题正误（与教育调性冲突）。
- 任意子目录的 `*16` 末位文件，多为奇怪结尾，使用前请试听。

## 命名建议

```text
sfx/jingle_task_complete/     # NES00, NES03
sfx/jingle_kc_mastery/        # NES07, SAX05
sfx/jingle_weekly_done/       # SAX00
sfx/jingle_chapter_enter/     # PIZZI00, PIZZI04
sfx/jingle_ai_resource/       # STEEL00, STEEL03
sfx/jingle_bug_boss/          # HIT11
sfx/jingle_critical_error/    # HIT00
```
