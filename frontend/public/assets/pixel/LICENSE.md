# Pythontopia Web 端集成像素资产 License

> 本目录 (`frontend/public/assets/pixel/`) 是 Pythontopia 网页端运行所需像素资产的集成视图。
> 源仓库 `assets/inbox/` 是已审计的离线镜像，由 `scripts/sync-assets.mjs` 增量复制到此处；
> 少量自绘占位与手工挑选运行时素材以 manifest-only 方式登记。
> 全仓库资产 license 总声明见 `/LICENSE-ART.md`。
> Pythontopia 原创代码、文档、品牌、原创美术与集成编排见仓库根 `LICENSE` 文件；当前阶段不开放复用。

## 项目级 License 分层

- **Pythontopia 原创层**：All rights reserved / Source-Available Preview（未经书面许可不得复用）
- **第三方资产层**：CC-BY-SA 3.0+ / OFL-1.1 / CC-BY 4.0 / CC-BY 3.0 / CC0 1.0（分文件标识）
- **SA 注意**：LPC 等 CC-BY-SA 素材及其衍生文件仍按上游 share-alike 条款执行；这不等于授权 Pythontopia 代码、品牌、原创美术、课程集成或产品编排

## 必读署名清单（CC-BY / CC-BY-SA / OFL 强制）

### game-icons.net (CC-BY 3.0)
> Game icons by **Lorc**, **Delapouite**, **Skoll**, **sbed**, **Carl Olsen** — https://game-icons.net — licensed CC BY 3.0

### LPC: Liberated Pixel Cup (CC-BY-SA 3.0+)
> Tilesets & characters from LPC project, including contributions by **bluecarrot16, Sharm, Reemax, daneeklu (Daniel Eddeland), Jetrel, J. Taylor, Ivan Voirol, TheraHedwig**.
> Source: https://opengameart.org/ — licensed CC BY-SA 3.0+
> 完整 CREDITS 文件保留在各子目录的 `CREDITS-*.txt` 中。

### CC-BY 4.0 contributors
- **Jana Ochse (2DPIXX)** — farming-game-basics (https://opengameart.org/content/farming-game-basics)
- **Marco L.** — pixel-lightning (https://opengameart.org/content/pixel-lightning)
- **bluecarrot16 / TheraHedwig / Ivan Voirol** — lpc-farm (https://opengameart.org/content/lpc-farm)
- **Svetlana Kushnariova ("Cabbit") / David Revoy** — Pepper&Carrot 24x32 character tribute

### OFL-1.1 字体
> Ark Pixel Font & Fusion Pixel Font by **TakWolf** — https://github.com/TakWolf — SIL OFL-1.1

### Kenney CC0（推荐致谢，非强制）
> Many UI / tile / character / SFX / icon assets by **Kenney Vleugels** (https://kenney.nl) — released as CC0 1.0.


<!-- AUTO-GENERATED LICENSE INDEX BEGIN -->
## 按目录的 License 索引（由 `scripts/sync-assets.mjs` 自动生成，请勿手工编辑）

| 目标路径 (dest) | License | 作者 | 来源 | 备注 |
| --- | --- | --- | --- | --- |
| `tilesets/kenney-tiny-town` | CC0-1.0 | Kenney Vleugels | [link](https://kenney.nl/assets/tiny-town) | 小镇 16x16 像素 tileset，BootScene 主用 |
| `tilesets/kenney-tiny-dungeon` | CC0-1.0 | Kenney Vleugels | [link](https://kenney.nl/assets/tiny-dungeon) | Bug 矿洞地牢 16x16 像素 tileset |
| `tilesets/kenney-roguelike-modern-city` | CC0-1.0 | Kenney Vleugels | [link](https://kenney.nl/assets/roguelike-modern-city) | 现代城市 tileset（可选场景） |
| `tilesets/kenney-roguelike-caves` | CC0-1.0 | Kenney Vleugels | [link](https://kenney.nl/assets/roguelike-caves-dungeons) | 洞穴/地牢 tileset |
| `tilesets/town_tiles.svg` | Pythontopia-Proprietary | Pythontopia project | [link](assets/pixel/LICENSE.md) | 🛈 manifest-only（已手工挑选，脚本不同步）；自绘小镇 tileset 占位 SVG；源文件为项目早期占位清单 |
| `buildings/town_buildings.svg` | Pythontopia-Proprietary | Pythontopia project | [link](assets/pixel/LICENSE.md) | 🛈 manifest-only（已手工挑选，脚本不同步）；自绘建筑占位 SVG；源文件为项目早期占位清单 |
| `characters/student_default.svg` | Pythontopia-Proprietary | Pythontopia project | [link](assets/pixel/LICENSE.md) | 🛈 manifest-only（已手工挑选，脚本不同步）；自绘默认学生占位 SVG；LPC Sara 不可用时的 fallback |
| `sprites/lpc-sara` | CC-BY-SA-3.0 | Sharm / LPC Project | [link](https://opengameart.org/content/lpc-sara) | LPC Sara 全身像，BootScene player_sara 帧 64x64 |
| `sprites/lpc-medieval-fantasy` | CC-BY-SA-3.0 | bluecarrot16 / TheraHedwig / Reemax / Ivan Voirol 等 | [link](https://opengameart.org/content/lpc-medieval-fantasy-character-sprites) | LPC 中世纪风 NPC，校园老师/同学候选 |
| `sprites/peppercarrot-24x32` | CC-BY-4.0 | Svetlana Kushnariova ("Cabbit") / David Revoy | [link](https://www.peppercarrot.com) | 24x32 矮人角色，可作"小作物"NPC 候选 |
| `sprites/npc-composites` | CC-BY-SA-3.0 | Johannes Sjölund (wulax) / bluecarrot16 / LPC base contributors | [link](https://opengameart.org/content/lpc-medieval-fantasy-character-sprites) | 🛈 manifest-only（已手工挑选，脚本不同步）；16 个 Agent / 主管 NPC walkcycle 合成帧（npc_*.png），智能体事务所动画 + 世界 NPC 复用 |
| `sprites/ulpc` | CC-BY-SA-3.0 | LPC contributors (bluecarrot16, ElizaWy, JaidynReiman, wulax, Redshrike, …，见 CREDITS-ulpc-used.txt) | [link](https://github.com/LiberatedPixelCup/Universal-LPC-Spritesheet-Character-Generator) | 🛈 manifest-only（已手工挑选，脚本不同步）；官方 ULPC 全动作角色表（player_sara2 + ulpc_*，13×42 expanded，run/jump/idle/sit/emote + 6 基础），scripts/build_ulpc_characters.py 合成 |
| `sprites/lpc-tools` | CC-BY-SA-3.0 | bluecarrot16, Pierre Vigier, Johannes Sjölund (wulax), Tuomo Untinen, Inboxninja | [link](https://opengameart.org/content/lpc-hand-tools) | 🛈 manifest-only（已手工挑选，脚本不同步）；浇水壶/锄头手持工具叠加层（watering-can/hoe + bg，universal），主角/NPC 浇水=thrust 动作叠加；含 CREDITS-hand-tools.txt |
| `sprites/cottages` | CC-BY-SA-3.0 | bluecarrot16 / Daniel Eddeland (daneeklu) / Lanea Zimmerman (Sharm) | [link](https://opengameart.org/content/lpc-thatched-roof-cottage) | 🛈 manifest-only（已手工挑选，脚本不同步）；茅草屋整屋 sprite（cottage_tan/cream/stone），scripts/build_cottages.py 从 kit 合成，世界场景建筑 |
| `sprites/weapons` | CC-BY-SA-3.0 | LPC contributors（weapon_blunt_waraxe，见 ULPC CREDITS） | [link](https://github.com/LiberatedPixelCup/Universal-LPC-Spritesheet-Character-Generator) | 🛈 manifest-only（已手工挑选，脚本不同步）；斧头超尺寸挥砍叠加（weapon_axe + bg，192px oversize attack_slash），主角砍树/挖矿动作；取自 ULPC weapon_blunt_waraxe |
| `portraits` | CC0-1.0 / CC-BY-4.0 | isaiah658 / Svetlana Kushnariova ("Cabbit") / David Revoy | [link](portraits/README.md) | 🛈 manifest-only（已手工挑选，脚本不同步）；20 NPC 对话立绘；来自 isaiah658 CC0 与 Pepper&Carrot 24x32 CC-BY 4.0 |
| `vendor/opengameart/tileset_farm.png` | CC-BY-3.0 | Marionline | [link](https://opengameart.org/content/farm-tileset) | 农场主 tileset，BootScene tileset_farm 引用 |
| `vendor/opengameart/cereals.png` | CC0-1.0 | Scratchio | [link](https://opengameart.org/content/cereals) | 谷物像素图，BootScene cereals 引用（注意目标小写） |
| `vendor/opengameart/vegetables.png` | CC0-1.0 | Scratchio | [link](https://opengameart.org/content/vegetables) | 蔬菜像素图，BootScene vegetables 引用（注意目标小写） |
| `vendor/opengameart/crates_and_sacks.png` | CC0-1.0 | ScratchIO | [link](https://opengameart.org/content/crates-and-sacks) | 🛈 manifest-only（已手工挑选，脚本不同步）；货箱与麻袋像素图；当前 public 文件为手工集成，源页 CC0 已核对 |
| `fonts/ark-pixel-10px-proportional-latin.woff2` | OFL-1.1 | TakWolf | [link](https://github.com/TakWolf/ark-pixel-font) | Ark Pixel 10px Latin（HUD 小数字） |
| `fonts/ark-pixel-10px-proportional-zh_cn.woff2` | OFL-1.1 | TakWolf | [link](https://github.com/TakWolf/ark-pixel-font) | Ark Pixel 10px 简中（备用字体） |
| `fonts/ark-pixel-12px-proportional-latin.woff2` | OFL-1.1 | TakWolf | [link](https://github.com/TakWolf/ark-pixel-font) | Ark Pixel 12px Latin（备用，主推 prop 系列已手工压缩入库） |
| `fonts/ark-pixel-12px-proportional-zh_cn.woff2` | OFL-1.1 | TakWolf | [link](https://github.com/TakWolf/ark-pixel-font) | Ark Pixel 12px 简中（备用，主推 prop 系列已手工压缩入库） |
| `fonts/LICENSE-ark-pixel-OFL.txt` | OFL-1.1 | TakWolf | [link](https://github.com/TakWolf/ark-pixel-font) | Ark Pixel OFL 协议原文（OFL-1.1 强制保留） |
| `fonts/OFL.txt` | OFL-1.1 | TakWolf | [link](https://github.com/TakWolf/ark-pixel-font) | Ark Pixel OFL 协议原文（旧运行时路径） |
| `fonts/ark-pixel-12px-mono-latin.woff2` | OFL-1.1 | TakWolf | [link](https://github.com/TakWolf/ark-pixel-font) | Ark Pixel 12px monospaced Latin（运行时短文件名） |
| `fonts/ark-pixel-12px-mono-zh_cn.woff2` | OFL-1.1 | TakWolf | [link](https://github.com/TakWolf/ark-pixel-font) | Ark Pixel 12px monospaced 简中（运行时短文件名） |
| `fonts/ark-pixel-12px-prop-latin.woff2` | OFL-1.1 | TakWolf | [link](https://github.com/TakWolf/ark-pixel-font) | Ark Pixel 12px proportional Latin（运行时短文件名） |
| `fonts/ark-pixel-12px-prop-zh_cn.woff2` | OFL-1.1 | TakWolf | [link](https://github.com/TakWolf/ark-pixel-font) | Ark Pixel 12px proportional 简中（运行时短文件名） |
| `fonts/fusion-pixel-12px-proportional-zh_hans.woff2` | OFL-1.1 | TakWolf | [link](https://github.com/TakWolf/fusion-pixel-font) | Fusion Pixel 12px 简中（主推中文像素字体，GB2312 覆盖 93.92%） |
| `fonts/fusion-pixel-10px-proportional-latin.woff2` | OFL-1.1 | TakWolf | [link](https://github.com/TakWolf/fusion-pixel-font) | Fusion Pixel 10px Latin（HUD 数字小字号） |
| `fonts/LICENSE-fusion-pixel-OFL.txt` | OFL-1.1 | TakWolf | [link](https://github.com/TakWolf/fusion-pixel-font) | Fusion Pixel OFL 协议原文 |
| `bgm/track_01.mp3` | CC0-1.0 | RandomMind | [link](https://opengameart.org/content/medieval-the-old-tower-inn) | 小镇白天主题 |
| `bgm/track_02.ogg` | CC0-1.0 | MouthlessGames | [link](https://opengameart.org/content/snowy-cozyness-calm-pianoguitar) | 学习/RAG 阅读 BGM（CC0 + CC-BY 3.0 双授权，取 CC0 路径） |
| `bgm/track_03.ogg` | CC0-1.0 | MintoDog | [link](https://opengameart.org/content/cozy-puzzle-in-game-1) | 练习/Bug 矿洞 BGM |
| `bgm/track_04.mp3` | CC0-1.0 | RandomMind | [link](https://opengameart.org/content/chiptune-medieval-the-old-tower-inn) | 小镇夜晚主题（chiptune 改编） |
| `bgm/LICENSE.md` | CC0-1.0 | Pythontopia inbox 审计 | [link](assets/inbox/bgm/LICENSE.md) | BGM 完整 license 与归属（运行时引用） |
| `sfx/click` | CC0-1.0 | Kenney Vleugels | [link](https://kenney.nl/assets/ui-audio) | UI 按钮点击 10 个音效 |
| `sfx/hover` | CC0-1.0 | Kenney Vleugels | [link](https://kenney.nl/assets/ui-audio) | UI 按钮悬停 6 个音效 |
| `sfx/coin` | CC0-1.0 | Kenney Vleugels / rubberduck | [link](https://kenney.nl/assets/rpg-audio) | 金币奖励 4 个音效 |
| `sfx/dig` | CC0-1.0 | Kenney Vleugels | [link](https://kenney.nl/assets/impact-sounds) | 挖矿/敲击 5 个音效 |
| `sfx/plant` | CC0-1.0 | Kenney Vleugels | [link](https://kenney.nl/assets/impact-sounds) | 种植/放置 4 个音效 |
| `sfx/water` | CC0-1.0 | rubberduck | [link](https://opengameart.org/content/40-cc0-water-splash-slime-sfx) | 浇水 7 个音效（inbox 用 water_can，dest 用 water 与 BootScene 命名一致） |
| `sfx/footstep` | CC0-1.0 | Kenney Vleugels / rubberduck | [link](https://kenney.nl/assets/impact-sounds) | 脚步声 10 个音效 |
| `sfx/boss/impactSoft_medium_000.ogg` | CC0-1.0 | Kenney Vleugels | [link](https://kenney.nl/assets/impact-sounds) | 无尽远征 Boss 命中反馈音效 |
| `sfx/harvest` | CC0-1.0 | Kenney Vleugels | [link](https://kenney.nl/assets/interface-sounds) | 收获/掉落 5 个音效（重命名 harvest_pickup → harvest） |
| `sfx/bell` | CC0-1.0 | Kenney Vleugels | [link](https://kenney.nl/assets/impact-sounds) | 收获完成铃声 6 个音效（重命名 bell_chime → bell） |
| `sfx/jingle-complete.ogg` | CC0-1.0 | Kenney Vleugels | [link](https://kenney.nl/assets/music-jingles) | 任务完成 jingle（NES 风），BootScene sfx_jingle_complete |
| `sfx/jingle-level-up.ogg` | CC0-1.0 | Kenney Vleugels | [link](https://kenney.nl/assets/music-jingles) | 等级提升 jingle（NES 风），BootScene sfx_jingle_levelup |
| `sfx/success.ogg` | CC0-1.0 | Kenney Vleugels | [link](https://kenney.nl/assets/digital-audio) | 答题/资源生成成功提示音，BootScene sfx_success |
| `sfx/error.ogg` | CC0-1.0 | Kenney Vleugels | [link](https://kenney.nl/assets/digital-audio) | 答题/资源生成失败提示音，BootScene sfx_error |
| `icons_kc` | CC-BY-3.0 | Lorc, Delapouite, Skoll, sbed, Carl Olsen | [link](https://game-icons.net) | 60+ KC 主题图标，按 Pythontopia 知识点分组 |
| `effects/codemanu-pixel-fx` | CC0-1.0 | CodeManu | [link](https://opengameart.org/content/pixel-fx-pack) | 通用粒子特效 10 帧 |
| `effects/jellyfizh-confetti` | CC0-1.0 | Jellyfizh | [link](https://opengameart.org/content/confetti) | 完成奖励彩纸 |
| `effects/jellyfizh-poof` | CC0-1.0 | Jellyfizh | [link](https://opengameart.org/content/poof) | 消失/淡出动画 |
| `effects/john2k-heal` | CC-BY-4.0 | john2k | [link](https://opengameart.org/content/heal-effect) | 治疗/复习成功特效 |
| `effects/weather` | CC0-1.0 / Pythontopia-Proprietary | Yevhen Babiichuk (DustDFG) / donte / alxl / Pythontopia project | [link](assets/inbox/weather_fx/LICENSE.md) | 🛈 manifest-only（已手工挑选，脚本不同步）；天气粒子运行时精选：云、雨滴、雪花来自已审计 CC0；confetti 为项目运行时粒子占位 |
| `ui/kenney-adventure` | CC0-1.0 | Kenney Vleugels | [link](https://kenney.nl/assets/ui-pack-adventure) | 🛈 manifest-only（已手工挑选，脚本不同步）；冒险风 UI 9-slice 元素，public 已挑选 Default/Double 子集，本脚本不再同步全量 |

> 编辑方式：修改 `scripts/sync-assets.mjs` 的 MANIFEST 数组后重新运行 `node scripts/sync-assets.mjs`。
<!-- AUTO-GENERATED LICENSE INDEX END -->

## Agentic Loop 闭环动效素材（2026-06-17 · 手工登记）

「Agentic Loop 六框架融合」工程为闭环吉祥物表情反馈预留 Rive 动画。说明：
- `@rive-app/canvas`、`motion`、`gsap` 均为代码层 JS 依赖（MIT / GSAP No-Charge），非素材；项目代码授权仍以仓库根 `LICENSE` 为准。
- 仅 Rive `.riv` 二进制动画属素材层。当前仓库尚无 `.riv` 资产，下表为占位登记。

| 运行时路径（计划） | 来源 | License | 用途 | 状态 |
| --- | --- | --- | --- | --- |
| `rive/mascot_loop.riv` | 待定：优先使用可审计第三方素材，或由 Pythontopia 原创并按根 `LICENSE` 保留所有权利 | **待补（占位）** | 闭环吉祥物表情反馈（idle / celebrate / fail，按 critic 结果） | 待补 |

> 引入真实素材时：确认许可与作者来源 → 落盘 `frontend/public/assets/pixel/rive/mascot_loop.riv` →
> 同步更新本文件与 `/LICENSE-ART.md` → CC-BY / CC-BY-SA / OFL 等强制署名进 `frontend/public/credits/`。
> 缺 `.riv` 时动效 adapter 须 fail-soft 降级（Motion/Phaser tween / 静态表情），不硬依赖。
