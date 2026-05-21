# Pythontopia 治愈风 SFX 音效素材清单

本目录收集了用于 Pythontopia 像素小镇 Web 端的游戏音效（UI、互动、环境）。所有素材均为
**CC0 公共领域** 授权，可在个人、教育、商业场景免费使用，不存在 personal-only 限制。

下载时间：2026-05-21。

## 类别 & 文件

| 类别 | 用途映射 | 文件数 | 主要来源 |
| --- | --- | --- | --- |
| `click/` | UI 按钮点击 | 10 | Kenney UI Audio + Interface Sounds |
| `hover/` | UI 按钮悬停/聚焦 | 6 | Kenney UI Audio |
| `water_can/` | 浇水、水流、水滴 | 7 | OGA `40 CC0 water/splash/slime SFX` |
| `dig/` | 挖土、矿洞敲击 | 5 | Kenney Impact Sounds (`impactMining_*`) |
| `plant/` | 种植、放置作物 | 4 | Kenney RPG Audio (`chop`) + Impact (`impactPlank_*`) |
| `harvest_pickup/` | 收获、入袋、确认 | 5 | Kenney Interface Sounds (`drop_*`、`confirmation_*`) |
| `bell_chime/` | 收获成功、奖励铃 | 6 | Kenney Impact (`impactBell_*`) + Interface (`glass_*`、`bong_*`) |
| `coin/` | 金币、奖励 | 4 | Kenney RPG Audio (`handleCoins`) + OGA `100 CC0 SFX #2` (`items_*`) |
| `footstep/` | 脚步声（草地、木地板、湿地） | 10 | Kenney Impact (`footstep_grass_*`、`footstep_wood_*`) + Kenney RPG + OGA `100 CC0 SFX #2` (`footstep_wet_*`) |
| `rain_ambient/` | 雨声、环境氛围循环 | 3 | OGA `40 CC0 water/splash/slime SFX` (`loop_rain`) + OGA `100 CC0 SFX #2` (`loop_ambient_*`) |

**总计 60 个 .ogg 音效文件，覆盖 10 个类别，来自 2 个独立来源（Kenney.nl + OpenGameArt.org）。**

## 来源 & 授权详情

### 1. Kenney.nl — CC0

来源网站：https://kenney.nl

- **UI Audio**（`kenney_ui_*`）— https://kenney.nl/assets/ui-audio
- **Interface Sounds**（`kenney_interface_*`）— https://kenney.nl/assets/interface-sounds
- **Impact Sounds**（`kenney_impact*`、`kenney_footstep_*`）— https://kenney.nl/assets/impact-sounds
- **RPG Audio**（`kenney_rpg_*`）— https://kenney.nl/assets/rpg-audio

> License (Creative Commons Zero, CC0)
> http://creativecommons.org/publicdomain/zero/1.0/
> "This content is free to use in personal, educational and commercial projects.
> Support us by crediting Kenney or www.kenney.nl (this is not mandatory)"

许可证全文随原 zip 附带（`Audio/License.txt`），原始 zip 保留在 `_zips/kenney_*.zip`。

### 2. OpenGameArt.org — CC0

- **40 CC0 water / splash / slime SFX** by **rubberduck** —
  https://opengameart.org/content/40-cc0-water-splash-slime-sfx
  - 我们使用：`splash_01..03/06/08.ogg`、`loop_water_02..03.ogg`、`loop_rain.ogg`
- **100 CC0 SFX #2** by **rubberduck** —
  https://opengameart.org/content/100-cc0-sfx-2
  - 我们使用：`sfx100v2_items_01..02.ogg`、`sfx100v2_loop_ambient_01..02.ogg`、`sfx100v2_footstep_wet_02..03.ogg`

> License: CC0（页面 License 区已勾选 "Creative Commons CC0"）

原始 zip 保留在 `_zips/oga_water-splash-slime-sfx.zip`、`_zips/oga_sfx_100_v2.zip`。

> 此外目录 `_zips/_extract/oga_krank/` 含 qubodup 的 `atmospheric-interaction-sound-pack`（CC0）作为
> 备份候选，但因风格偏抽象 puzzle，目前未进入正式分类。若后续 NPC 互动需要可启用。

## 归属（推荐）

按 CC0 规定，归属（attribution）并非强制，但我们仍在 README/credits 中按以下格式致谢：

```
SFX:
- Kenney Vleugels (https://kenney.nl) — UI / Interface / Impact / RPG Audio
- rubberduck (https://opengameart.org/users/rubberduck) — water/splash/slime SFX, 100 CC0 SFX #2
```

## 限制 / 注意事项

- 全部为 **CC0**，**无 personal-only**、无 NC、无 ND、无 SA 限制。
- 不复制任何《星露谷物语》官方素材，本批次素材与该游戏无任何法律关联。
- 已剔除可能与课程主题不匹配的素材（如武器、暴力、过度卡通化）。
- 如未来更换音效，请同步更新本文件，并保留原 zip 与本 LICENSE.md 的一一对应。
