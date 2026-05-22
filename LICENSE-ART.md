# Pythontopia 美术资产 License 总声明

> 本文件覆盖 `assets/inbox/` 与最终集成到 `frontend/public/assets/` 的全部美术、音效、字体资源。
> 代码层 license 见仓库根 `LICENSE` 文件。

## License 分布矩阵（2026-05-22 更新）

| 协议族 | 子源数 | 涉及目录 | 商用 | 署名 | 传染 |
| --- | ---: | --- | :---: | :---: | :---: |
| **CC0 1.0** | 30+ | bgm/, kenney_characters/, kenney_tilemaps/, kenney_tilesets/, pixel_ui/, sfx/, mines_extra/, sfx_extra/, ui_extra/, weather_fx/ 多数 | ✅ | 推荐 | 无 |
| **CC-BY 3.0** | 5 个作者 | icons_kc/ (game-icons.net Lorc/Delapouite/Skoll/sbed/Carl Olsen) | ✅ | **强制** | 无 |
| **CC-BY 4.0** | ~10 子源 | crops/farming-game-basics, opengameart_farming/lpc_farm, weather_fx/oga_pixel_lightning, characters_extra/peppercarrot_24x32, bgm_extra/*, sfx_extra/voiceover-pack-fighter | ✅ | **强制** | 无 |
| **CC-BY-SA 3.0+ / GPL 3.0+** | 5 子源 | buildings_npc/lpc_*, crops/lpc-*, opengameart_farming/daneeklu, opengameart_farming/lpc_farm, seasons_school/lpc_* | ✅ | **强制** | **传染** |
| **OFL-1.1** | 1 | github_extra/ark_pixel_font | ✅ | **强制** | 仅字体内 |

## 项目级 SA 传染决策

**接受 CC-BY-SA 3.0 传染**：因 LPC 系列素材教育价值高，无 CC0 等价替代物。本项目最终二进制 / 包装作品同时以 **CC-BY-SA 3.0 / 4.0 (or compatible) 公开发布**，与代码层（MIT 推荐）分层授权：
- 代码层：MIT
- 资产层：CC-BY-SA 3.0+ / OFL-1.1 / CC-BY 4.0 / CC-BY 3.0 / CC0（分文件标识）

## 强制署名清单（CC-BY / CC-BY-SA 必读）

部署到 `frontend/public/credits/` 与运行时 about 页面：

### game-icons.net (CC-BY 3.0)
> Game icons by **Lorc**, **Delapouite**, **Skoll**, **sbed**, **Carl Olsen** — https://game-icons.net — licensed CC BY 3.0

### LPC (Liberated Pixel Cup, CC-BY-SA 3.0+)
> Tilesets & characters from LPC project, including contributions by **bluecarrot16, Sharm, Reemax, daneeklu (Daniel Eddeland), Jetrel, J. Taylor, Ivan Voirol, TheraHedwig**.
> Source: https://opengameart.org/ — licensed CC BY-SA 3.0+
> Full CREDITS files preserved under each `<subdir>/CREDITS-*.txt`

### CC-BY 4.0 contributors
- **Jana Ochse (2DPIXX)** — farming-game-basics (https://opengameart.org/content/farming-game-basics)
- **Marco L.** — pixel-lightning (https://opengameart.org/content/pixel-lightning)
- **bluecarrot16 / TheraHedwig / Ivan Voirol** — lpc-farm (https://opengameart.org/content/lpc-farm)
- **David Revoy / Pepper&Carrot 24x32** — characters_extra/peppercarrot_24x32

### OFL-1.1 — Ark Pixel Font
> Ark Pixel Font by **TakWolf** — https://github.com/TakWolf/ark-pixel-font — SIL OFL-1.1

### Kenney CC0 (推荐致谢，非强制)
> Many UI / tile / character / SFX / icon assets by **Kenney Vleugels** (https://kenney.nl) — released as CC0.

## 已删除的违规素材（不在本仓库分发）

| 路径 | 原因 |
| --- | --- |
| `assets/inbox/sprout_lands/sprout_lands_basic/` | Cup Nooble 专有 Non-commercial |
| `assets/inbox/sprout_lands/simple_farm_tiles/` | 来源 license 不可考 |
| `assets/inbox/sprout_lands/daneeklu_farming/` | 与 oga_farming/daneeklu/ 重复 |
| `assets/inbox/opengameart_farming/bluecarrot16/` | 与 crops/lpc-crops/ 重复 |

`assets/inbox/sprout_lands/` 整个顶级目录已删除，剔除 Cup Nooble 商标词。已加入 `.gitignore` 防止意外恢复。

## 资产盘点（截止 2026-05-22）

| 子目录 | 文件数 | 大小 | 协议主体 | 集成优先级 |
| --- | ---: | ---: | --- | :---: |
| bgm/ | 5 | 12M | CC0 + CC0/CC-BY 3.0 双协议 | P1 |
| bgm_extra/ | 20 | 32M | CC0 + CC-BY 4.0 | P1 |
| buildings/ | ~? | — | Kenney CC0 + LPC | P0 |
| buildings_npc/ | 292 | 4.1M | Kenney CC0 + LPC CC-BY-SA | P0 |
| characters/ | ~? | — | 含 armm1998_zelda_cc0 (CC0) | P0 |
| characters_extra/ | 255 | 5M | LPC + CC-BY 4.0 + CC0 | P0 |
| crops/ | 65 | 7.5M | LPC + CC0 + CC-BY 4.0 | P1 |
| edu_diagrams/ | 10 | <100K | 自制 mermaid | P0 |
| effects_extra/ | ~700 | 60M | CC0 + CC-BY 4.0 | P1 |
| fonts/ | 1 | — | (待补) | P2 |
| github_extra/ark_pixel_font/ | 7 | 13M | OFL-1.1 | P0 |
| icons/ | ~? | — | Kenney CC0 (kenney_curated) | P0 |
| icons_kc/ | 60 | 372K | CC-BY 3.0 (game-icons.net) | P0 |
| kenney_characters/ | 709 | 13M | CC0 | P0 |
| kenney_tilemaps/ | 2813 | 23M | CC0 | P0 |
| kenney_tilesets/ | 286 | 1.6M | CC0 | P0 |
| mines/ | ~? | — | CC0 (kenney_pixel-platformer) | P1 |
| mines_extra/ | 1380 | 11M | CC0 | P1 |
| opengameart_farming/ | ~75 | 1.5M+ | CC0 + LPC | P1 |
| pixel_ui/ | 613 | 2.8M | CC0 | P0 |
| seasons_school/ | 260 | 21M | CC0 + LPC + CC-BY 4.0 | P0 |
| sfx/ | 68 | 18M | CC0 | P1 |
| sfx_extra/ | 439 | 25M | CC0 | P1 |
| tilesets/ | ~? | — | (待审计) | P1 |
| ui/ | ~? | — | Kenney CC0 | P0 |
| ui_extra/ | 1400+ | 36M | Kenney CC0 | P0 |
| weather_fx/ | 223 | 30M | CC0 + CC-BY 4.0 | P1 |

**总计**：约 13000+ 文件 / 359 MB，覆盖小镇 + 4 季 + 校园 + 矿洞 + 角色（多套） + UI + 音效 + BGM + 粒子特效 + 中文像素字体。

## 待办（backlog）

1. `assets/inbox/tilesets/` 与 `assets/inbox/icons/`、`assets/inbox/buildings/`、`assets/inbox/characters/`、`assets/inbox/ui/`、`assets/inbox/mines/` 待补 LICENSE_AUDIT 二次确认
2. 醒来后可选：手动下载 ansimuz/sunny-land-pixel-game-art + ansimuz/fantasy-weapons-set（itch.io CC0，需浏览器 session）
3. 真实部署后 `frontend/public/credits/` 全量署名页生成（CC-BY-SA 3.0 + OFL-1.1 必备）
