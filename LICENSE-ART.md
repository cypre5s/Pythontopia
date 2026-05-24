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

## 代码层音视觉库（2026-05-24 R-13 新增，不属于资产层）

R-13 Phase 3 引入 3 个前端音视觉 JavaScript 库，与本文档涵盖的"资产层"
license 矩阵无关（它们是 JS 代码，受代码层 MIT 控制），但为完整性记录：

| 库 | 版本 | License | 用途 |
| --- | --- | --- | --- |
| `tone` | 15.x | MIT | 程序化合成 SFX（脚步声 / UI 点击 / 金币 / 任务完成 / chime），零音频文件依赖 |
| `howler` | 2.2.4 | MIT | 预留：未来接入 CC0 BGM 时取代 Phaser sound manager 做高级 crossfade |
| `gsap` | 3.13.x | "GSAP No-Charge" license（个人/教育免费，包含本项目场景） | HUD 数字 tween、路由 fade transition、未来 KC 公告动画 |

**GSAP 商用注意**：GSAP 3 主分发 license（"GreenSock No-Charge License"）对教育与非营利产品免费，
本项目作为高校 Python 教学游戏在本分类内。若未来商业化（付费课程 / 闭源 SaaS 等），需购买 GSAP Business 许可证。

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
- **Svetlana Kushnariova ("Cabbit") / David Revoy** — Pepper&Carrot 24x32 character sprites (`characters_extra/peppercarrot_24x32`)

### OFL-1.1 — Ark Pixel Font
> Ark Pixel Font by **TakWolf** — https://github.com/TakWolf/ark-pixel-font — SIL OFL-1.1

### Kenney CC0 (推荐致谢，非强制)
> Many UI / tile / character / SFX / icon assets by **Kenney Vleugels** (https://kenney.nl) — released as CC0.

## R-13 §-1 新增运行时素材（2026-05-25）

| 运行时路径 | 来源 | License | 用途 |
| --- | --- | --- | --- |
| `frontend/public/assets/pixel/ui/login-hero-stardew.png` | Pythontopia 原创（AI 辅助生成 + 人工审核） | CC-BY-SA 3.0+ | LoginPage 星露谷风暖色像素小镇 hero；NPC 与建筑均为原创，不复制任何商业像素游戏 |
| `frontend/public/assets/pixel/bosses/boss-{1..7}-*.png` (7 张) | Pythontopia 原创（AI 辅助生成 + 人工审核 + 原创主题设计） | CC-BY-SA 3.0+ | BossPage / Endless / Arena 第 10 关 Boss 立绘；7 章主题：算盘灵 / 解释器学徒 / 二进制独眼 / 流程钟楼 / 集合棱镜 / 文档守护神 / 归纳之镜；风格统一星露谷暖色像素，与 hero 同调 |
| `frontend/public/assets/pixel/vendor/ansimuz-mountain-dusk/{5×PNG,LICENSE-CC0.txt}` | ansimuz "Mountain at Dusk Background" (https://opengameart.org/content/mountain-at-dusk-background) | **CC0-1.0** | 备用：4 层 parallax 远山日落剪影，预留 boss 终局 / 章末过场使用 |
| `frontend/public/assets/pixel/portraits/heads/portrait_*_head.png` (20 张) | `frontend/public/assets/pixel/portraits/portrait_*.png` 子裁切，由 `scripts/crop-portrait-heads.py` 输出 | 继承原 portrait（CC0 / CC-BY-SA 3.0 / CC-BY 4.0 分文件） | DialogModal 单帧头像，每张为对应 portrait 的左上 idle-down 16×24 帧 2x 放大 |
| `assets/inbox/r13-overworld/pastoral-tileset-db32.png` | Ragnar Random Hat (https://opengameart.org/content/pastoral-overworld) | **CC0-1.0** | 备用：DawnBringer db32 32×32 乡村瓦片，未集成到运行时 |
| `assets/inbox/r13-lpc-lamp/lpc_lamp_posts_rework-1.0.zip` | Curt, Sharm, William.Thompsonj, mold (LPC) | CC-BY 3.0 / OGA-BY 3.0 / GPL 3.0 | 备用：32×32 路灯柱多色，未集成到运行时；引入时需署名 4 位作者 |

**AI 原创素材声明**：`login-hero-stardew.png` 由项目以 GenerateImage 工具生成，prompt 与生成结果由维护者人工审核确认不包含任何受版权保护角色 / 建筑布局，作为本项目自有资产以 CC-BY-SA 3.0+ 公开发布。

## R-12 Web 运行时补充索引（2026-05-24）

以下条目补齐 `frontend/public/assets/pixel/` 中未由常规 inbox 同步覆盖、但已用于 BootScene / NPC 对话 / 字体加载的运行时素材；对应索引已同步到 `frontend/public/assets/pixel/LICENSE.md`。

| 运行时路径 | 来源 | License | 备注 |
| --- | --- | --- | --- |
| `tilesets/town_tiles.svg`, `buildings/town_buildings.svg`, `characters/student_default.svg` | Pythontopia 自绘占位（见 `assets/pixel/LICENSE.md`） | CC0-1.0 | 早期占位与 fallback，不含商业像素游戏素材 |
| `fonts/ark-pixel-12px-{mono,prop}-*.woff2`, `fonts/OFL.txt` | TakWolf Ark Pixel Font (`assets/inbox/github_extra/ark_pixel_font/`) | OFL-1.1 | 运行时短文件名，与 OFL 原文一并分发 |
| `sfx/success.ogg`, `sfx/error.ogg` | Kenney Digital Audio (`assets/inbox/sfx_extra/digital-audio/`) | CC0-1.0 | 正误反馈提示音 |
| `effects/weather/` | Weather FX 审计池 (`assets/inbox/weather_fx/LICENSE.md`) + Pythontopia 粒子占位 | CC0-1.0 | 云、雨滴、雪花来自已审计 CC0；confetti 为运行时占位 |
| `portraits/` | isaiah658 Pixel Pack #1 + Pepper&Carrot 24x32 | CC0-1.0 / CC-BY-4.0 | 20 NPC 对话立绘；Pepper&Carrot 必须署名 Cabbit 与 David Revoy |
| `vendor/opengameart/crates_and_sacks.png` | ScratchIO "Crates And Sacks" on OpenGameArt | CC0-1.0 | 港口/农场货箱与麻袋装饰 |

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

## R-13 B6 计划集成素材

| 素材 | 来源 | License | 用途 | 状态 |
|---|---|---|---|---|
| LPC Lamp Posts Rework v1.0 | OpenGameArt @bluecarrot16 | CC-BY 3.0 | town_hub / campus_east 路灯 tile | 待下载 |
| Pastoral 32x32 Tileset | OpenGameArt CC0 | CC0 | farm_north 田地装饰 | 待下载 |
| 20 NPC 统一风格头像 | 自绘 / AI 辅助 | CC-BY-SA 3.0 | portraits/heads/ 统一尺寸 32×48 | 部分完成 |

## 待办（backlog）

1. `assets/inbox/tilesets/` 与 `assets/inbox/icons/`、`assets/inbox/buildings/`、`assets/inbox/characters/`、`assets/inbox/ui/`、`assets/inbox/mines/` 待补 LICENSE_AUDIT 二次确认
2. 醒来后可选：手动下载 ansimuz/sunny-land-pixel-game-art + ansimuz/fantasy-weapons-set（itch.io CC0，需浏览器 session）
3. 真实部署后 `frontend/public/credits/` 全量署名页生成（CC-BY-SA 3.0 + OFL-1.1 必备）
4. B6 LPC Lamp Posts 下载后集成到 town_hub.json / campus_east.json 的 tileset 层
5. B6 NPC portraits 批量统一：scripts/crop-portrait-heads.py 需要跑一遍确保 20 NPC 全覆盖
