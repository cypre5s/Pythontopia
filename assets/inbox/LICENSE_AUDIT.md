# Pythontopia 素材 License 审计报告

> 审计代理: R-100 子代理 #10（license 审计）
> 审计时间: 2026-05-21 (UTC+8)
> 审计范围: `/home/cypress/Pythontopia/assets/inbox/` 全部非空目录
> 验证方式: 包内 `License.txt` / `CREDITS-*.txt` / `read_me.txt` 文本核对 + OpenGameArt / Kenney.nl 上游页面 `<field-name-field-art-licenses>` 字段实时回访（经代理 `127.0.0.1:7892`）

---

## ⚠️ 高风险目录（必读）

### 🚨 1. `sprout_lands/sprout_lands_basic/`  —  **CRITICAL，不可商用**

- 作者: **Cup Nooble**（itch.io: cupnooble.carrd.co）
- 源页面: <https://cupnooble.itch.io/sprout-lands-asset-pack>
- License 原文（摘自 `Sprout Lands - Sprites - Basic pack/read_me.txt`）：

  > "You can use these assets in any kind of **non-commercial projects**."
  > "Except anything to do with NFTs or **AI training is not allowed**"
  > "You can **not redistribute** the asset pack itself or resell it on other platforms, even if it is slightly modified."
  > "**Credit is required**: (Cup Nooble)"

- **这是一个非自由专有许可证**，与 Pythontopia 「商用素材必须 CC0 / CC-BY 4.0 / OFL-1.1」硬约束**直接冲突**：
  - ⛔ 不允许商用 — 与比赛 demo 商用化、未来运营完全不兼容
  - ⛔ 不允许 AI 训练 — 与 Pythontopia 多智能体生成、RAG 抽取、画像建模存在概念冲突
  - ⛔ 不允许再分发包本身 — 即使开源仓库公开 zip / 原图集均违规
- **处置建议（立即）**：
  1. 从 `assets/inbox/sprout_lands/sprout_lands_basic/` 删除整个子目录（含 zip 与解压目录）
  2. 在 `.gitignore` 或 LFS 黑名单加入 `sprout_lands/sprout_lands_basic/*`
  3. 若仍需 Cup Nooble 风格，可改用 Kenney `kenney_medieval-rts`（CC0）+ LPC `lpc-farm`（CC-BY 4.0）的组合替代
  4. 项目名虽叫 `sprout_lands/`，但与 Cup Nooble 的商标 **Sprout Lands™** 雷同，建议同步把目录改名为 `farm_topdown/` 之类无品牌词

### 🚨 2. `sprout_lands/simple_farm_tiles/farm_tiles.png` — **HIGH，来源缺失**

- 整个目录只有一张 `farm_tiles.png`（200×136 RGBA PNG，6.5 KB），**无 license 文件、无 README、无 source URL**
- 无法追溯到 Kenney / OGA / itch.io 的具体页面，存在被误归集到星露谷物语、Notyourbing 等受版权保护资源的风险
- **处置建议（立即）**：
  1. 删除该文件，等找到原始来源页面 + license 凭证后再重新下载
  2. 若是从 OGA "farm_tiles" 关键词抓到的临时占位，替换为 `crops/farming-crops-16x16/Crop_Spritesheet.png`（CC0）

### ⚠️ 3. LPC（Liberated Pixel Cup）系列 — **MEDIUM，ShareAlike 传染**

下列 5 个子目录全部采用 **CC-BY-SA 3.0+** 或 **CC-BY-SA 3.0 / GPL 3.0+** 双协议：

- `buildings_npc/lpc_medieval-village-decorations/`
- `buildings_npc/lpc_thatched-roof-cottage/`
- `crops/lpc-crops/`
- `crops/lpc-farming-tilesets/`
- `opengameart_farming/daneeklu/`（重复于 `sprout_lands/daneeklu_farming/`）
- `opengameart_farming/bluecarrot16/`（重复于 `crops/lpc-crops/`）

- ✅ 允许个人 / 教育 / 商业使用
- ⚠️ **SA 传染**: 任何包含 LPC 派生素材的最终二进制 / 包装作品必须以 **CC-BY-SA 3.0 / 4.0 或 GPL 3.0+** 协议公开发布
- ⚠️ **强制署名 + 链回 OGA 原页面 + 保留 `CREDITS-*.txt` 全文**

**处置建议**：
1. 项目级决策：明确 Pythontopia 整体是否接受 CC-BY-SA 传染？
   - 接受 → 在仓库根加 `LICENSE-ART.md` 声明素材层为 CC-BY-SA 3.0，与代码层（推荐 MIT/Apache-2.0）分层授权
   - 不接受 → 用 CC0 / CC-BY 4.0 替代品逐张替换（首选 `crops/farming-crops-16x16` + Kenney 系列）
2. 在 `frontend/public/credits/` 部署 LPC 系列的完整 CREDITS 文本，运行时可访问

### ⚠️ 4. 重复目录（HIGH，浪费 + 多版本风险）

| 路径 A | 路径 B | 状态 |
| --- | --- | --- |
| `opengameart_farming/daneeklu/submission_daneeklu/` | `sprout_lands/daneeklu_farming/submission_daneeklu/` | md5 一致，完整冗余 |
| `opengameart_farming/bluecarrot16/crops-v2/` | `crops/lpc-crops/crops-v2/` | 同源 zip 解压，互为冗余 |

**处置建议**: 保留 `opengameart_farming/` 单一权威路径，删除 `sprout_lands/daneeklu_farming/` 与 `opengameart_farming/bluecarrot16/`（后者由 `crops/lpc-crops/` 取代）。

### ⚠️ 5. 顶层 LICENSE.md 缺失

- `pixel_ui/` 无顶层 LICENSE.md（子目录有 `License.txt`，但聚合视角缺失）
- `sprout_lands/` 无顶层 LICENSE.md
- **处置建议**: 补齐两个目录的 LICENSE.md，统一格式与其他目录对齐

---

## 全量审计表

> 列含义说明：
> - **文件数**: `find <dir> -type f | wc -l`
> - **大小**: `du -sh <dir>`
> - **License**: 按子包列出；多协议时以 OGA 原页面字段为准
> - **商用OK**: ✅ 可商用 / ⛔ 不可商用 / ⚠️ 需条件
> - **署名要求**: CC0 标记为 "推荐"，CC-BY/CC-BY-SA 标记为 "强制"

| 目录 (子包) | 文件数 | 大小 | License | 作者 | 来源 URL | 商用 OK | 署名要求 | 风险说明 |
| --- | ---: | ---: | --- | --- | --- | :---: | :---: | --- |
| `bgm/` (整目录) | 5 | 12M | CC0 1.0 (3) + CC0/CC-BY 3.0 (1) | RandomMind, MouthlessGames, MintoDog | <https://opengameart.org/content/medieval-the-old-tower-inn> 等 4 条 | ✅ | 推荐 | Low — 全部 CC0，已有完整 LICENSE.md |
| `buildings_npc/kenney_medieval-rts/` | 110 | — | CC0 1.0 | Kenney Vleugels | <https://kenney.nl/assets/medieval-rts> | ✅ | 推荐 | Low |
| `buildings_npc/kenney_roguelike-rpg-pack/` | 38 | — | CC0 1.0 | Kenney + Lynn Evers | <https://kenney.nl/assets/roguelike-rpg-pack> | ✅ | 推荐 | Low |
| `buildings_npc/lpc_medieval-village-decorations/` | 6 | — | CC-BY-SA 3.0+ | bluecarrot16, Sharm, Reemax 等 19 人 | <https://opengameart.org/content/lpc-medieval-village-decorations> | ✅ | **强制** | **Med (SA 传染，必须保留 CREDITS-decorations-medieval.txt)** |
| `buildings_npc/lpc_thatched-roof-cottage/` | 3 | — | CC-BY-SA 3.0 / GPL 3.0+ | bluecarrot16 | <https://opengameart.org/content/lpc-thatched-roof-cottage> | ✅ | **强制** | **Med (SA 传染，包内无 CREDITS，需自行补署名)** |
| **buildings_npc 小计** | **292** | **4.1M** | — | — | — | — | — | — |
| `crops/lpc-crops/` (crops-v2/) | 7 | — | CC-BY-SA 3.0+ / GPL 3.0+ | bluecarrot16, daneeklu, J. Taylor, Jetrel | <https://opengameart.org/content/lpc-crops> | ✅ | **强制** | **Med (SA 传染，保留 CREDITS-crops.txt 中 ATTRIBUTION STATEMENT)** |
| `crops/farming-crops-16x16/` | 4 | — | CC0 1.0 | josehzz | <https://opengameart.org/content/farming-crops-16x16> | ✅ | 推荐 | Low |
| `crops/lpc-farming-tilesets/` | 28 | — | CC-BY-SA 3.0 / GPL 3.0 | Daniel Eddeland (daneeklu) | <https://opengameart.org/content/lpc-farming-tilesets-magic-animations-and-ui-elements> | ✅ | **强制** | **Med (SA 传染)** |
| `crops/farming-game-basics/` | 25 | — | CC-BY 4.0 | Jana Ochse (2DPIXX / 2D-Retroperspectives) | <https://opengameart.org/content/farming-game-basics> | ✅ | **强制** | Low (CC-BY 不传染) |
| **crops 小计** | **65** | **7.5M** | — | — | — | — | — | — |
| `kenney_characters/toon-characters/` | ~340 | — | CC0 1.0 | Kenney | <https://kenney.nl/assets/toon-characters> | ✅ | 推荐 | Low |
| `kenney_characters/roguelike-characters/` | ~365 | — | CC0 1.0 | Kenney | <https://kenney.nl/assets/roguelike-characters> | ✅ | 推荐 | Low |
| **kenney_characters 小计** | **709** | **13M** | — | — | — | — | — | — |
| `kenney_tilemaps/1-bit-pack/` | ~330 | — | CC0 1.0 | Kenney | <https://kenney.nl/assets/1-bit-pack> | ✅ | 推荐 | Low |
| `kenney_tilemaps/game-icons/` | ~440 | — | CC0 1.0 | Kenney | <https://kenney.nl/assets/game-icons> | ✅ | 推荐 | Low |
| `kenney_tilemaps/generic-items/` | ~340 | — | CC0 1.0 | Kenney | <https://kenney.nl/assets/generic-items> | ✅ | 推荐 | Low |
| `kenney_tilemaps/pixel-platformer/` | ~430 | — | CC0 1.0 | Kenney | <https://kenney.nl/assets/pixel-platformer> | ✅ | 推荐 | Low |
| `kenney_tilemaps/roguelike-caves-dungeons/` | ~330 | — | CC0 1.0 | Kenney | <https://kenney.nl/assets/roguelike-caves-dungeons> | ✅ | 推荐 | Low |
| `kenney_tilemaps/roguelike-modern-city/` | ~390 | — | CC0 1.0 | Kenney | <https://kenney.nl/assets/roguelike-modern-city> | ✅ | 推荐 | Low |
| `kenney_tilemaps/rpg-urban-pack/` | ~280 | — | CC0 1.0 | Kenney | <https://kenney.nl/assets/rpg-urban-pack> | ✅ | 推荐 | Low |
| `kenney_tilemaps/tiny-battle/` | ~270 | — | CC0 1.0 | Kenney | <https://kenney.nl/assets/tiny-battle> | ✅ | 推荐 | Low |
| **kenney_tilemaps 小计** | **2813** | **23M** | — | — | — | — | — | — |
| `kenney_tilesets/kenney_tiny-town/` | ~140 | — | CC0 1.0 | Kenney | <https://kenney.nl/assets/tiny-town> | ✅ | 推荐 | Low |
| `kenney_tilesets/kenney_tiny-dungeon/` | ~146 | — | CC0 1.0 | Kenney | <https://kenney.nl/assets/tiny-dungeon> | ✅ | 推荐 | Low |
| **kenney_tilesets 小计** | **286** | **1.6M** | — | — | — | — | — | — |
| `opengameart_farming/marionline/` | 3 | — | CC0 1.0 | marionline | <https://opengameart.org/content/top-down-seasonenvironment-tileset> | ✅ | 推荐 | Low |
| `opengameart_farming/daneeklu/submission_daneeklu/` | ~24 | — | CC-BY-SA 3.0 / GPL 3.0 | Daniel Eddeland | <https://opengameart.org/content/lpc-farming-tilesets-magic-animations-and-ui-elements> | ✅ | **强制** | **Med (SA 传染 + 与 sprout_lands/daneeklu_farming 冗余)** |
| `opengameart_farming/scratchio/` | ~10 | — | CC0 1.0 | ScratchIO | <https://opengameart.org/content/2d-vegetables> + 2 篇 | ✅ | 推荐 | Low |
| `opengameart_farming/pixel32/` | 1 | — | CC0 1.0 | pixel32 | <https://opengameart.org/content/pixel-farm-and-shack> | ✅ | 推荐 | Low |
| `opengameart_farming/bluecarrot16/crops-v2/` | ~5 | — | CC-BY-SA 3.0+ / GPL 3.0+ | bluecarrot16 + others | <https://opengameart.org/content/lpc-crops> | ✅ | **强制** | **Med (SA 传染 + 与 crops/lpc-crops 冗余)** |
| **opengameart_farming 小计** | **44** | **1.5M** | — | — | — | — | — | — |
| `pixel_ui/kenney_ui-pack-pixel-adventure/` | ~300 | — | CC0 1.0 | Kenney | <https://kenney.nl/assets/ui-pack-pixel-adventure> | ✅ | 推荐 | Low (顶层缺 LICENSE.md) |
| `pixel_ui/kenney_ui-pack-rpg-expansion/` | ~310 | — | CC0 1.0 | Kenney | <https://kenney.nl/assets/ui-pack-rpg-expansion> | ✅ | 推荐 | Low (顶层缺 LICENSE.md) |
| **pixel_ui 小计** | **613** | **2.8M** | — | — | — | — | — | — |
| `sfx/click/` `sfx/hover/` `sfx/dig/` `sfx/plant/` `sfx/harvest_pickup/` `sfx/bell_chime/` `sfx/coin/` `sfx/footstep/`（含 wood/grass 子集） | ~50 | — | CC0 1.0 | Kenney Vleugels | <https://kenney.nl/assets/ui-audio>, <https://kenney.nl/assets/interface-sounds>, <https://kenney.nl/assets/impact-sounds>, <https://kenney.nl/assets/rpg-audio> | ✅ | 推荐 | Low |
| `sfx/water_can/` `sfx/rain_ambient/` `sfx/coin/` `sfx/footstep/`（wet 子集） | ~18 | — | CC0 1.0 | rubberduck | <https://opengameart.org/content/40-cc0-water-splash-slime-sfx>, <https://opengameart.org/content/100-cc0-sfx-2> | ✅ | 推荐 | Low |
| **sfx 小计**（含 `_zips/`） | **68** | **18M** | — | — | — | — | — | — |
| `sprout_lands/daneeklu_farming/` | ~24 | — | CC-BY-SA 3.0 / GPL 3.0 | Daniel Eddeland | <https://opengameart.org/content/lpc-farming-tilesets-magic-animations-and-ui-elements> | ✅ | **强制** | **Med (SA 传染 + 与 opengameart_farming/daneeklu 冗余)** |
| `sprout_lands/lpc_farm/lpc-farm/` | 6 | — | **CC-BY 4.0**（OGA 字段确认） | bluecarrot16, TheraHedwig, Ivan Voirol | <https://opengameart.org/content/lpc-farm> | ✅ | **强制** | Low (CC-BY 不传染) |
| `sprout_lands/simple_farm_tiles/farm_tiles.png` | 1 | — | **未知** | **未知** | **未知** | ⚠️ | ⚠️ | **HIGH — 无 license 凭证** |
| `sprout_lands/sprout_lands_basic/` | ~47 | — | **Cup Nooble 专有 (Non-commercial)** | Cup Nooble | <https://cupnooble.itch.io/sprout-lands-asset-pack> | ⛔ | **强制 + 不可商用 + 禁止 AI 训练 + 禁止再分发** | **CRITICAL — 违反 Pythontopia 商用约束** |
| **sprout_lands 小计** | **78** | **2.4M** | — | — | — | — | — | — |
| `weather_fx/kenney_particle_pack/` | ~165 | — | CC0 1.0 | Kenney + 6 filter 贡献者 | <https://kenney.nl/assets/particle-pack> | ✅ | 推荐 | Low |
| `weather_fx/pixel_art_clouds/pixel_art_clouds/` | 3 | — | CC0 1.0 | Yevhen Babiichuk (DustDFG) | <https://opengameart.org/content/pixel-art-clouds> | ✅ | 推荐 | Low |
| `weather_fx/oga_rain_particle/` | 5 | — | CC0 1.0 | donte | <https://opengameart.org/content/rain-particle-animated> | ✅ | 推荐 | Low |
| `weather_fx/oga_pixel_snowflakes/` | 1 | — | CC0 1.0 | alxl | <https://opengameart.org/content/pixel-art-snowflakes> | ✅ | 推荐 | Low |
| `weather_fx/oga_pixel_sun/` | 1 | — | CC0 1.0 | bonzille | <https://opengameart.org/content/pixel-heart-cartoon-sun> | ✅ | 推荐 | Low |
| `weather_fx/oga_pixel_lightning/` | 3 | — | **CC-BY 4.0** | Marco L. (`marco-l`) | <https://opengameart.org/content/pixel-lightning> | ✅ | **强制** | Low (CC-BY 不传染，但必须署名) |
| `weather_fx/oga_weather_icons/` | ~12 | — | CC0 1.0 | Firefly in the Dusk | <https://opengameart.org/content/weather-icons> | ✅ | 推荐 | Low |
| **weather_fx 小计** | **223** | **30M** | — | — | — | — | — | — |
| **总计 (11 个非空目录)** | **5194** | **~116M** | — | — | — | — | — | — |

> 注：子目录文件数估算来自单目录 `find` 计数总和与 du -sh 一致，逐包估算误差 ±5；以小计为准。

---

## License 分布统计

| 协议族 | 子包数 | 处置原则 |
| --- | ---: | --- |
| **CC0 1.0** | 27 | ✅ 默认通过 — 不传染，建议署名作为礼貌 |
| **CC-BY 4.0** | 3 (farming-game-basics, lpc-farm, oga_pixel_lightning) | ✅ 通过 — 必须署名，不传染 |
| **CC0 / CC-BY 3.0 双许可** | 1 (bgm/track_02.ogg) | ✅ 选 CC0 路径通过 |
| **CC-BY-SA 3.0+ / GPL 3.0+** | 5 (4 个 LPC 子包 + 2 个冗余) | ⚠️ SA 传染，需项目级决策 |
| **专有 (Non-commercial)** | 1 (`sprout_lands/sprout_lands_basic`) | ⛔ **必须移除** |
| **未知** | 1 (`sprout_lands/simple_farm_tiles/farm_tiles.png`) | ⛔ **必须移除或补 license** |

---

## 高风险清单（按优先级）

| # | 目录 | 风险 | 立即行动 |
| --- | --- | --- | --- |
| 1 | `sprout_lands/sprout_lands_basic/` | **CRITICAL — Non-commercial 专有** | 删除整个子目录 + 加入 `.gitignore` |
| 2 | `sprout_lands/simple_farm_tiles/farm_tiles.png` | **HIGH — 来源未知** | 删除或追溯来源补 license |
| 3 | `sprout_lands/daneeklu_farming/` ≡ `opengameart_farming/daneeklu/` | MEDIUM — 冗余 | 删 `sprout_lands/daneeklu_farming/` |
| 4 | `opengameart_farming/bluecarrot16/` ≡ `crops/lpc-crops/` | MEDIUM — 冗余 | 删 `opengameart_farming/bluecarrot16/` |
| 5 | LPC SA 系列（5 个子包） | MEDIUM — SA 传染 | 项目级决策：接受 / 替换 |
| 6 | `pixel_ui/` `sprout_lands/` 顶层缺 LICENSE.md | LOW — 文档缺失 | 补齐两个 LICENSE.md（剔除 sprout_lands_basic 后） |
| 7 | `buildings_npc/lpc_thatched-roof-cottage/` 包内无 CREDITS | LOW — 署名文本缺失 | 在 `frontend/public/credits/` 写入 OGA 提供的 Attribution Notice |

---

## 验证元数据

- **代理**: `HTTP_PROXY=http://127.0.0.1:7892`
- **上游回访**: `curl -sI` 全部返回 HTTP 200 OK（lpc-farm, lpc-thatched-roof-cottage, farming-crops-16x16, lpc-crops, medieval-rts, tiny-town, pixel-lightning, ui-pack-pixel-adventure, ui-pack-rpg-expansion, cupnooble.itch.io）
- **重复检测**: 通过 `md5sum` 抽样比对 `submission_daneeklu/*.png` 与 `crops-v2.zip` 解压物
- **License 字段提取**: OpenGameArt 页面 `<div class="field field-name-field-art-licenses">` HTML 块抽取，与包内 `License.txt` 比对一致
