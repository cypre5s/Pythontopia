# Pythontopia 建筑素材来源清单（`assets/inbox/buildings/`）

本目录新增 3 套像素建筑 tileset，全部为 **CC0 / CC-BY 3.0 / CC-BY-SA 3.0+** 许可，符合 Pythontopia `AGENTS.md` 中"仅 CC0 / CC-BY / CC-BY-SA"的资产合规要求。

另外两套现有建筑/装饰资产位于 `assets/inbox/buildings_npc/` 与 `assets/inbox/sprout_lands/`，本目录的 `MANIFEST.md` 同时引用它们以避免重复下载。

## 本目录新增包

| 包目录 | 来源 | 作者 | 许可证 | tile size | 用途 | 关键文件 |
| --- | --- | --- | --- | --- | --- | --- |
| `kenney_tiny-town/` | [opengameart.org/content/tiny-town](https://opengameart.org/content/tiny-town)<br>镜像自 [kenney.nl/assets/tiny-town](https://kenney.nl/assets/tiny-town) | Kenney Vleugels (Kenney.nl) | **CC0 1.0** | 16×16 | 像素小镇外观：房屋（灰顶/红顶/储物棚）、城堡门楼、围栏、招牌、工具、桶、钥匙、靶子（132 个独立 tile + 整 tilemap） | `Tiles/tile_0000..0131.png`<br>`Tilemap/tilemap.png` (203×186)<br>`Tilemap/tilemap_packed.png` |
| `tiny16_basic/` | [opengameart.org/content/tiny-16-basic](https://opengameart.org/content/tiny-16-basic) | Lanea Zimmerman (AKA Sharm) | **CC-BY 3.0** (anti-DRM clause waived) | 16×16 | Dawnbringer-16 palette 基础 RPG 砖：石塔/木屋外观、门、墙、招牌、宝藏、墓碑、传送门状黑方块（可作 Bug 矿洞入口） | `basictiles_2.png` (128×240)<br>`things_0.png` (192×128)<br>`characters_1.png` (9.9KB)<br>`dead_1.png` (2.3KB) |
| `lpc_house_interior/` | [opengameart.org/content/lpc-house-interior-and-decorations](https://opengameart.org/content/lpc-house-interior-and-decorations) | Tuomo Untinen (Reemax)、Lanea Zimmerman、Hyptosis、Daniel Eddeland、Manuel Riecke (MrBeast)、William Thompson、caeles、Matthew Krohn、Johannes Sjölund 等（详见 `credits.txt`） | **CC-BY-SA 3.0+** | 32×32（LPC 标准） | 室内装饰：橱柜/书架/灶台/壁炉/蒸馏器/酒瓶/铠甲架/武器架/桌椅/木地板/石地板（512×512 一张大图） | `interior.png` (512×512)<br>`preview.png`<br>`credits.txt`（**分发时必须保留**） |

## 与现有目录的关系（复用，不重新下载）

| 已有目录 | 关键素材 | 许可证 | 复用场景 |
| --- | --- | --- | --- |
| `assets/inbox/buildings_npc/kenney_medieval-rts/` | `PNG/Default size/Structure/medievalStructure_01..23.png`（23 栋中世纪建筑顶视图）+ `Environment/medievalEnvironment_*.png` | CC0 1.0 | 图书馆、教师屋、复盘大厅等大型石质/塔楼建筑外观候选 |
| `assets/inbox/buildings_npc/kenney_roguelike-rpg-pack/` | `Spritesheet/roguelikeSheet_transparent.png`（968×526 整图集，含矿洞入口、营帐、大宅、墓地、招牌、栅栏） | CC0 1.0 | Bug 矿洞入口、复盘大厅、训练场围栏；附 `Map/sample_indoor.tmx` 室内 Tiled 模板 |
| `assets/inbox/buildings_npc/lpc_medieval-village-decorations/` | `decoration_medieval/decorations-medieval.png`（含告示牌、市集摊位、铁砧、武器架、推车、营帐、井） | CC-BY-SA 3.0+ | 公告板、训练场器具、资源工坊外景 |
| `assets/inbox/buildings_npc/lpc_thatched-roof-cottage/` | `cottage.png`、`thatched-roof.png`（512×512 LPC 茅草屋多变体） | CC-BY-SA 3.0 / GPL 3.0+ | 画像屋、教师屋的小型住宅外观 |
| `assets/inbox/sprout_lands/sprout_lands_basic/Sprout Lands - Sprites - Basic pack/` | `Tilesets/Wooden_House_Roof_Tilset.png`、`Tilesets/Wooden_House_Walls_Tilset.png`、`Tilesets/Doors.png`、`Tilesets/Tilled_Dirt_Wide_v2.png`、`Tilesets/Fences.png`、`Objects/Basic Plants.png`、`Objects/Free_Chicken_House.png` | CC-BY 4.0（Sprout Lands by Cup Nooble） | 知识田 + 木质小屋外观（与小镇 16×16 风格兼容） |
| `assets/inbox/sprout_lands/lpc_farm/lpc-farm/` | `barn.png`、`water-wheel.png`、`blade.png` | CC-BY-SA 3.0+（CREDITS-farm.txt 保留） | 资源工坊（谷仓即作坊）、知识田旁的水车装饰 |

## 包合规说明

1. **Kenney 系列（Tiny Town、medieval-rts、roguelike-rpg-pack；CC0）**：可商用、可修改、无需署名。建议在游戏内"鸣谢"中标注 `Kenney.nl`。
2. **Tiny 16: Basic（CC-BY 3.0）**：必须署名作者 **Lanea Zimmerman (Sharm)**，且保留来源链接。anti-DRM 条款已被作者豁免。
3. **LPC House Interior（CC-BY-SA 3.0+）**：
   - 分发时必须附带 `lpc_house_interior/credits.txt`，且不得删改。
   - 衍生物必须以 **同等或兼容的 CC-BY-SA 3.0+ 协议** 公开。
   - 此包内的木地板、橱柜部分基于 Lanea Zimmerman / Sharm 的 LPC 底图修改，需在署名中带上。
4. **LPC Thatched-Roof Cottage（CC-BY-SA 3.0 / GPL 3.0+，双授权）**：Pythontopia 选 CC-BY-SA 3.0 路径；署名 `"LPC Thatched-roof Cottage" by bluecarrot16` + 来源链接。
5. **Sprout Lands（CC-BY 4.0）**：作者 **Cup Nooble**（itch.io）。Free 包允许商业使用，但要署名。注意：Pythontopia 主线已禁止复制《星露谷物语》官方素材，本包是独立社区像素作者作品，并非星露谷血统。
6. **风格混用警告**：Kenney medieval-rts 是 64×64 顶视角；Kenney Tiny Town / Tiny 16 / roguelike-rpg-pack 是 16×16 顶视角；LPC 是 32×32 西方 RPG 视角；Sprout Lands 是 16×16 像素艺术。后续在 `services/` 或 `frontend/src/game/` 整理素材时需要按 tile size 分目录管理，并在 Phaser 加载阶段做尺寸校验。Beta 版本建议主用 16×16 系（Kenney Tiny Town + Sprout Lands）作为小镇主体，LPC 32×32 仅作为室内场景。

## 验证元数据

- 下载时间：**2026-05-21**
- 代理：`http://127.0.0.1:7892`
- 校验：所有 PNG 已用 `file` 命令验证为合法 8-bit RGBA / palette PNG 文件
- 整体磁盘占用：见 `MANIFEST.md` 末尾"统计"段

## 后续建议

- 进入 `assets/buildings/` 主目录前，应按 license 分子目录（`cc0/`、`cc-by/`、`cc-by-sa/`）。
- LPC、Sprout Lands、Tiny 16 的 `CREDITS.txt` / `credits.txt` 必须保留并在游戏运行时可访问（建议放 `frontend/public/credits/`）。
- 本目录所有素材**无星露谷物语官方血统**，符合 Pythontopia 硬约束。
