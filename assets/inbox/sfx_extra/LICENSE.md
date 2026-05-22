# Pythontopia 扩展 SFX 素材清单（Kenney CC0 audio extras）

本目录补全了 `assets/inbox/sfx/` 已有的四个 Kenney 包，
新增 6 个 CC0 1.0 包，覆盖任务完成、KC 收获、答题正误、班级 PK、矿洞战斗、AI 助手、教室房间切换、Level Up 等
Pythontopia 教学交互场景所需音效与短乐句。

下载时间：2026-05-22。

## 已收录包

| Slug | 包名 | 来源 URL | 文件 | 大小 (extracted) | License |
| --- | --- | --- | --- | --- | --- |
| `casino-audio/` | Casino Audio v1.1 | https://kenney.nl/assets/casino-audio | 54 ogg + Preview + License | 1.1 MB | CC0 1.0 |
| `digital-audio/` | Digital Audio v1.0 | https://kenney.nl/assets/digital-audio | 62 ogg + Preview + License | 1.2 MB | CC0 1.0 |
| `music-jingles/` | Music Jingles v1.0 | https://kenney.nl/assets/music-jingles | 85 ogg + Preview + License | 1.6 MB | CC0 1.0 |
| `sci-fi-sounds/` | Sci-fi Sounds v1.0 | https://kenney.nl/assets/sci-fi-sounds | 73 ogg + License | 5.9 MB | CC0 1.0 |
| `voiceover-pack/` | Voiceover Pack v1.0 | https://kenney.nl/assets/voiceover-pack | 92 ogg (Female 46 + Male 46) + 3 Previews + License + Credits | 2.2 MB | CC0 1.0 |
| `voiceover-pack-fighter/` | Voiceover Pack — Fighter v1.0 | https://kenney.nl/assets/voiceover-pack-fighter | 46 ogg + Preview + License | 1.4 MB | CC0 1.0 |

**合计：419 个 ogg 文件（其中 7 个 `Preview*.ogg` 仅供试听，412 个为生产可用素材），13.4 MB extracted（含 12 MB zip 在 `_zips/`），整体下载 11.5 MB。**
配额：单源 ≤ 5.9 MB（sci-fi-sounds）/ 40 MB；整任务 11.5 MB / 120 MB。**全部在限额内。**

## 调研结果（已确认不存在 / 跳过）

| 候选 slug | 状态 |
| --- | --- |
| `ui-audio` | 已在 `assets/inbox/sfx/_zips/kenney_ui-audio.zip` → 跳过 |
| `interface-sounds` | 已在 `assets/inbox/sfx/_zips/kenney_interface-sounds.zip` → 跳过 |
| `impact-sounds` | 已在 `assets/inbox/sfx/_zips/kenney_impact-sounds.zip` → 跳过 |
| `rpg-audio` | 已在 `assets/inbox/sfx/_zips/kenney_rpg-audio.zip` → 跳过 |
| `music-loops-bone-rattle` | Kenney 现已 404，包已从 Audio 分类下架 |
| `music-loops-deep-meditation` | Kenney 现已 404 |
| `music-loops-mind-bender` | Kenney 现已 404 |
| `voiceover-pack-fighter` | 原任务未列，但属于 `voiceover-pack` 系列衍生包；CC0 同源，本次一并下载（用作"班级 PK"白名单子集，黑名单已标注） |

> Kenney 当前 Audio 分类共 9 个包：4 个已有 + 5 个本次新增 + 1 个新发现 fighter 包 = 全部 10 个均已落地。
> （`tag:music` 与 `category:Music` 当前为空，无更多候选；`series` 列表也无音频系列。）

## License 详情 — 全部 CC0 1.0

来源网站：https://kenney.nl
作者：Kenney Vleugels（Voiceover Pack 男声 / 女声分别为 Jeffrey M. Smith 与 Giselle，由 Kenney 统一打包 CC0）

每个子目录都自带 Kenney 官方 `License.txt`：

> License (Creative Commons Zero, CC0)
> http://creativecommons.org/publicdomain/zero/1.0/
>
> You may use these assets in personal and commercial projects.
> Credit (Kenney or www.kenney.nl) would be nice but is not mandatory.

`voiceover-pack/Credits.txt` 还列出了配音艺人（Kenney 已经获得授权并以 CC0 发布）。

## 归属（推荐）

按 CC0 规定，归属（attribution）并非强制，但仍按以下格式致谢：

```text
SFX & VO:
- Kenney Vleugels (https://kenney.nl) — Casino Audio, Digital Audio, Music Jingles,
  Sci-fi Sounds, Voiceover Pack, Voiceover Pack — Fighter (CC0)
- Voiceover Pack 男声: Jeffrey M. Smith
- Voiceover Pack 女声: Giselle
```

## 安全与教学合规

按 `AGENTS.md` 准则 "已剔除可能与课程主题不匹配的素材（如武器、暴力、过度卡通化）"，本批次 SFX：

1. **不删除任何 CC0 文件**（避免破坏 license 完整性），保留全部原始素材在 `sfx_extra/`。
2. 在每个包的 `README_USAGE.md` 中显式区分**白名单**（推荐映射到 Pythontopia 事件）与**黑名单**（不要 wire 到产品）。
3. 后续移交分类阶段，仅复制白名单文件到 `assets/inbox/sfx/<event>/` 或运行时 bundle；黑名单永远只保留在 `sfx_extra/` 备份层。

特别需要在产品端**禁用**的 39 个黑名单文件（详见各包 README_USAGE.md "不推荐使用"段）：

- `voiceover-pack`：所有 `war_*` 共 29 个（`Female/` 15 个，`Male/` 14 个；女声因有 `war_hold.ogg` 而比男声多 1）。
- `voiceover-pack-fighter`：10 个（`fight.ogg`、`kill_her.ogg`、`kill_him.ogg`、`kill_it.ogg`、`multi_kill.ogg`、`sudden_death.ogg`、`deathmatch.ogg`、`prepare_yourself.ogg`、`battle_mode.ogg`、`loser.ogg`）。

## 与 `assets/inbox/sfx/` 的关系

`sfx/` 是**已分类**目录（10 个事件子目录，60 个 .ogg 已完成挑选）；`sfx_extra/` 是**原始素材池**，待后续阶段：

1. 设计师/教师评审每个包的 `README_USAGE.md`。
2. 按白名单复制候选到 `sfx/<event>/`，文件名同 `sfx/click`、`sfx/coin` 风格（小写下划线）。
3. 评审通过的素材纳入 `frontend/public/audio/` 或 Android `assets/audio/` 打包。
4. 黑名单素材**仅留作 license 审计存证**。

## 限制与注意事项

- 全部 CC0，无 personal-only、无 NC、无 ND、无 SA。
- 不复制《星露谷物语》官方素材，本批次素材与该游戏无任何法律关联。
- 部分 `sci-fi-sounds/Audio/spaceEngineLarge_*.ogg` 单文件 ~200 KB，前端按需加载，不要全部塞进首屏。
- `music-jingles` 的 jingle 不要做 BGM 循环，仅在事件触发时短播。
- 任何 `war_*`、`kill_*`、`death*`、`fight` 命名的 ogg 在 production wire 前必须 review。
- 如果未来更换音效或某个 Kenney 包升级到新版本，请同步更新本文件、对应 `README_USAGE.md` 与 `_zips/` 原始压缩包。
