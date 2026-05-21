# Pythontopia 像素素材许可清单（buildings_npc）

本目录下所有素材均为 **CC0 / CC-BY-SA 3.0+** 范围内的免费可商用资源，符合 Pythontopia `AGENTS.md` 中"仅 CC0 / CC-BY / CC-BY-SA"的约束。所有素材为 `inbox/`（待归一化、整理后再入 `assets/` 主目录）。

| 包目录 | 来源 | 作者 | 许可证 | 用途 | 关键文件 |
| --- | --- | --- | --- | --- | --- |
| `kenney_medieval-rts/` | [kenney.nl/assets/medieval-rts](https://kenney.nl/assets/medieval-rts) | Kenney Vleugels (Kenney.nl) | CC0 1.0 | 建筑物（Structure×23）+ NPC/单位（Unit×24）+ 环境 + 整图集 | `PNG/Default size/Structure/medievalStructure_01..23.png`<br>`PNG/Default size/Unit/medievalUnit_01..24.png`<br>`Spritesheet/medievalRTS_spritesheet.png` |
| `kenney_roguelike-rpg-pack/` | [kenney.nl/assets/roguelike-rpg-pack](https://kenney.nl/assets/roguelike-rpg-pack) | Kenney Vleugels (Kenney.nl), Lynn Evers | CC0 1.0 | NPC/角色精灵 + 室内/室外建筑 + 道具（1700 资产，16×16 整图集） | `Spritesheet/roguelikeSheet_transparent.png`<br>`Spritesheet/spritesheetInfo.txt` |
| `lpc_medieval-village-decorations/` | [opengameart.org/content/lpc-medieval-village-decorations](https://opengameart.org/content/lpc-medieval-village-decorations) | bluecarrot16, Lanea Zimmerman (Sharm), Reemax (Tuomo Untinen), Xenodora, Johann C, Johannes Sjölund, Casper Nilsson, Daniel Cook, Rayane Félix (RayaneFLX), Wolthera van Hövell tot Westerflier (TheraHedwig), Hyptosis, mold, Zachariah Husiar (Zabin), Clint Bellanger, Jetrel, Nemisys, Guido Bos, Curt, Bertram, Daniel Eddeland (daneeklu) | CC-BY-SA 3.0+ | 中世纪村庄装饰物 + 围栏 + 集市/教堂/墓地 tile（LPC 32×32 风格） | `decoration_medieval/decorations-medieval.png`<br>`decoration_medieval/fence_medieval.png`<br>`decoration_medieval/CREDITS-decorations-medieval.txt`（**分发时必须保留**） |
| `lpc_thatched-roof-cottage/` | [opengameart.org/content/lpc-thatched-roof-cottage](https://opengameart.org/content/lpc-thatched-roof-cottage) | bluecarrot16 | CC-BY-SA 3.0 / GPL 3.0+ | 中世纪茅草屋小屋（LPC 32×32） | `cottage.png` (512×512)<br>`thatched-roof.png` (512×512)<br>`preview.png` |

## 验收对照

- 建筑物素材包数量：**3** 套（Kenney medieval-rts Structure + LPC Medieval Village Decorations + LPC Thatched-roof Cottage） ≥ 2 ✅
- NPC 精灵包数量：**2** 套（Kenney medieval-rts Unit + Kenney Roguelike/RPG pack 角色行） ≥ 1 ✅
- license 明确，全部位于允许清单内（CC0 / CC-BY-SA） ✅

## 重要合规说明

1. **Kenney 包（CC0）**：可在个人和商业项目中自由使用，无需署名。但建议在游戏内"鸣谢"中注明 `Kenney.nl`，作为对作者的尊重。
2. **LPC Medieval Village Decorations（CC-BY-SA 3.0+）**：
   - 分发或发布游戏时，**必须**附带 `decoration_medieval/CREDITS-decorations-medieval.txt` 文件，且不得删改其中信息。
   - 任何基于此素材的二次创作必须以同等或兼容的 CC-BY-SA 3.0+ 协议公开。
3. **LPC Thatched-roof Cottage（CC-BY-SA 3.0 / GPL 3.0+）**：
   - 双授权，Pythontopia 选用 CC-BY-SA 3.0 路径。
   - 必须署名："LPC Thatched-roof Cottage" by bluecarrot16 (https://opengameart.org/content/lpc-thatched-roof-cottage), CC-BY-SA 3.0。
   - 基于本素材的衍生作品必须同样以 CC-BY-SA 3.0（或兼容）协议公开。
4. **LPC 与 Kenney 风格不可直接混用**：LPC 是 32×32 像素 + 西方 RPG 视角；Kenney medieval-rts 是 64×64 顶视角；Kenney Roguelike 是 16×16 顶视角。后续 `services` 整理素材时需按 tile size 分目录管理，并在 Phaser 加载阶段做尺寸校验。

## 验证元数据

- 下载时间：2026-05-21
- 下载代理：`http://127.0.0.1:7892`
- 校验：所有关键 PNG 已用 `file` 命令验证为合法 RGBA PNG 文件。

## 后续操作建议

- 移动到 `assets/buildings/` 与 `assets/npcs/` 时，按 license 分子目录（cc0/、cc-by-sa/）。
- LPC 素材的 CREDITS txt 与 LICENSE 必须一并保留并在游戏运行时可访问（例如 `frontend/public/credits/`）。
- 不复制《星露谷物语》官方素材；本 inbox 内素材**均无星露谷血统**，符合 Pythontopia 硬约束。
