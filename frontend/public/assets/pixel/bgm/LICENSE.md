# Pythontopia BGM — License & Attribution

存放路径: `assets/inbox/bgm/`
用途: 治愈风像素小镇背景音乐 (BGM)，循环播放
下载日期: 2026-05-21
代理来源: OpenGameArt.org（CC0 / CC-BY 3.0 资产库）

所有曲目均经过 OpenGameArt 页面 license 字段二次确认，license 明确，可商用、可在 Pythontopia 项目（含比赛演示与开源发布）中使用。

---

## 曲目清单

| 文件 | 时长 | 大小 | 比特率 | 原标题 | 作者 | License | 来源 URL |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `track_01.mp3` | 1:45 | 2.5 MB | 192 kbps MP3 | Medieval: The Old Tower Inn | RandomMind | CC0 1.0 (Public Domain) | https://opengameart.org/content/medieval-the-old-tower-inn |
| `track_02.ogg` | 1:20 | 1.3 MB | 160 kbps Ogg Vorbis | A Warm Fireplace (from "Snowy Cozyness") | MouthlessGames | CC0 1.0 / CC-BY 3.0 (双许可) | https://opengameart.org/content/snowy-cozyness-calm-pianoguitar |
| `track_03.ogg` | 2:10 | 4.0 MB | 256 kbps Ogg Vorbis | Cozy Puzzle In-Game 1 (bpm 118, loopable) | MintoDog | CC0 1.0 (Public Domain) | https://opengameart.org/content/cozy-puzzle-in-game-1 |
| `track_04.mp3` | 1:40 | 4.0 MB | 320 kbps MP3 | [Chiptune] Medieval: The Old Tower Inn (NES 改编) | RandomMind | CC0 1.0 (Public Domain) | https://opengameart.org/content/chiptune-medieval-the-old-tower-inn |

直接 mp3/ogg 下载链接 (CDN by OpenGameArt):

- `track_01.mp3` ← https://opengameart.org/sites/default/files/The_Old_Tower_Inn.mp3
- `track_02.ogg` ← https://opengameart.org/sites/default/files/A%20Warm%20Fireplace_0.ogg
- `track_03.ogg` ← https://opengameart.org/sites/default/files/cozy_puzzle_in-game_1_bpm118_0.ogg
- `track_04.mp3` ← https://opengameart.org/sites/default/files/NES_TheOldTowerInn_0.mp3

---

## License 摘要

### CC0 1.0 Universal (Public Domain Dedication)

适用：`track_01.mp3`, `track_02.ogg`（也可按 CC-BY 3.0）, `track_03.ogg`, `track_04.mp3`

- 可自由使用、修改、再发布、商用，无需署名。
- 推荐仍保留作者列表（见上表）作为致谢。

### CC-BY 3.0 Unported

适用：`track_02.ogg`（双许可，可任选其一）

- 自由使用、修改、再发布、商用。
- 须在产品 credits 中注明原作者 MouthlessGames + OpenGameArt 链接。
- 衍生作品无须强制开放，license 不强制传染。

---

## 在 Pythontopia 中的使用约束

1. 入库前需经过音量归一化（建议 -16 LUFS）和无缝循环裁切，由前端 `assets/audio/bgm/` 目录引用。
2. `track_01.mp3` 与 `track_04.mp3` 同名同源，建议作为同一场景（小镇白天 / 像素夜版）的 A/B 主题；不要在同一场景同时播放。
3. `track_02.ogg` 钢琴吉他暖炉感，适合学习/RAG/课件阅读页 BGM。
4. `track_03.ogg` 谜题 loopable，适合练习/判题 / Bug 矿洞页。
5. 发布时 `docs/credits.md`（或游戏内 About 页）须列出：曲名、原作者、原 URL、license。
6. 严禁删除本 LICENSE.md；若做衍生剪辑，新增条目需注明 "Derived from <原曲名>, original by <作者>, <license>"。

---

## 候选源验证记录

- ✅ OpenGameArt.org — 通过页面 HTML `<*-license>` 字段二次抓取确认 license。
- ⛔ FreePD (`freepd.com`) — 站点已于 2026 年关停 (Site Closed)，无法下载。
- ⏳ Incompetech (`incompetech.com`) — Kevin MacLeod 全曲库，但需 CC-BY 4.0（任务约束为 CC-BY 3.0/CC0），本次未取用。
- ⏳ Pixabay Music — Pixabay Content License 虽然可商用，但非 CC0/CC-BY 3.0/CC-BY-SA，本次未取用以严守任务 license 约束。

如需扩充曲库，可继续从 OpenGameArt 的 calm / pastoral / cozy / acoustic 标签下载，license 筛选必须勾选 CC0 / CC-BY 3.0 / CC-BY-SA 3.0/4.0。

---

## 校验快照 (2026-05-21)

```text
track_01.mp3  2,535,801 bytes  MPEG ADTS layer III v1 192 kbps 44.1 kHz JntStereo
track_02.ogg  1,352,906 bytes  Ogg Vorbis stereo 44100 Hz ~160 kbps
track_03.ogg  4,036,007 bytes  Ogg Vorbis stereo 44100 Hz ~256 kbps
track_04.mp3  4,038,600 bytes  MPEG ADTS layer III v1 320 kbps 44.1 kHz JntStereo
```

通过 `python3 -c "from mutagen import File; ..."` 读取 `info.length` 二次确认时长，全部 ≤ 130 秒。
