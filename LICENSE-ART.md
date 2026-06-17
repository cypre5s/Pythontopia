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

## Android 客户端打包资产（2026-06-02 新增）

R-12 原生安卓化把 `:game`（LibGDX）从「色块占位」升级为真实瓦片+精灵渲染，所用资产
**全部是上表已审计来源的副本**（与 `frontend/public/assets/` 字节一致），打包进
`android/app/src/main/assets/`，无新增外部素材，license 不变：

| 安卓路径 | 来源 | License |
| --- | --- | --- |
| `tilesets/town.png` | Kenney Tiny Town `tilemap_packed.png` | CC0 1.0 |
| `tilesets/dungeon.png` | Kenney Tiny Dungeon `tilemap_packed.png` | CC0 1.0 |
| `maps/{town_hub,farm_north,mine_south,campus_east,port_west}.json` | 项目程序化生成（`frontend/scripts/generate-maps.mjs`） | 项目源码（CC-BY-SA 3.0+ 包装版本） |
| `sprites/player.png` | LPC walk9 主角行走表（9×4，64px），由 Universal-LPC-Spritesheet-Character-Generator 图层合成（body+pants+longsleeve+cowlick） | CC-BY-SA 3.0+ / GPL 3.0+（传染） |
| `sprites/player_sara2.png` | ULPC「Expanded」主角整表（13×42，合成自 Universal-LPC 库） | CC-BY-SA 3.0+ / GPL 3.0+（传染） |
| `sprites/npc/npc_*.png`（16 个） | LPC walk9 NPC 合成图（9×4，64px，按角色配色合成自 Universal-LPC 库） | CC-BY-SA 3.0+ / GPL 3.0+（传染） |

> LPC 合成角色署名：Universal-LPC-Spritesheet-Character-Generator（LiberatedPixelCup）及其部件作者
> （Stephen Challener "Redshrike"、Johannes Sjölund "wulax"、bluecarrot16、JaidynReiman、ElizaWy 等），
> 源 https://github.com/LiberatedPixelCup/Universal-LPC-Spritesheet-Character-Generator ，CC-BY-SA 3.0+。
> 兜底安全网：缺上述任一二进制图集时，`:game/ProceduralArt` + `GameAssets` 在运行时**程序化生成**
> 等价配色瓦片与简易像素角色（纯算法产物，CC0），保证 5 张地图永不因缺图崩溃，真素材在场时自动优先。
> 程序化软阴影 / 1×1 纯白像素（`GameAssets` 内 `Pixmap` 运行时生成）同属 CC0，不计外部素材。

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

## Agentic Loop 六框架融合新增依赖与素材（2026-06-17）

「Agentic Loop 六框架融合」工程（见 `docs/plans/2026-06-17-agentic-loop-framework-integration.md` 与
配套 worklog）为闭环步骤前端动效引入 Rive / Motion。**Rive 运行时与 Motion 均为代码层 JS 依赖
（MIT），不属于本文档涵盖的"资产层"**，受代码层 MIT 控制；为完整性记录如下：

| 库 | 版本 | License | 用途 | 层级 |
| --- | --- | --- | --- | --- |
| `@rive-app/canvas` | ^2.38.1 | MIT | 吉祥物矢量动画运行时（按 critic 结果反馈表情），隔离在 `frontend/src/game/adapters/anim/riveMascot.ts` | **代码层（非素材）** |
| `motion` | ^12.40.0 | MIT | 闭环步骤 DOM 过渡动效，隔离在 `frontend/src/game/adapters/anim/domMotion.ts` | **代码层（非素材）** |
| `gsap` | 3.13 | "GSAP No-Charge" license（教育免费，见上节） | HUD/路由过渡动效，沿用 R-13 既有登记，本期不新增 | **代码层（非素材）** |

> GSAP 本期沿用上节「代码层音视觉库（R-13）」已有登记，不重复列入素材 license。Motion / Rive 运行时同属代码层，
> 业务层禁止直接 `import gsap/motion/@rive-app`，必须经 `adapters/anim/**` 隔离（§3.7 类型纪律）。

### Rive 吉祥物动画资产（`.riv`）— 占位 / 待补

Rive `.riv` 二进制动画属**素材层**，**只允许 CC0 / CC-BY / CC-BY-SA / OFL**，且须同步登记本文件与
`frontend/public/assets/pixel/LICENSE.md`。当前**尚无具体 `.riv` 资产**，先登记占位条目供后续替换：

| 运行时路径（计划） | 来源 | License | 用途 | 状态 |
| --- | --- | --- | --- | --- |
| `frontend/public/assets/pixel/rive/mascot_loop.riv` | 待定（须为 CC0 / CC-BY / CC-BY-SA / OFL 审计来源；或项目原创 AI 辅助生成 + 人工审核，以 CC-BY-SA 3.0+ 发布） | **待补（占位）** | 闭环吉祥物表情反馈（按 critic 结果 idle / celebrate / fail） | ⬜ 待补 |

**引入时必办**：①确认许可 ∈ {CC0/CC-BY/CC-BY-SA/OFL}；②落盘上述路径；③把本表与
`frontend/public/assets/pixel/LICENSE.md` 的占位条目改为正式条目（补作者 / 来源链接 / 署名）；
④若选 CC-BY-SA，包装作品须同以 CC-BY-SA 3.0+ 发布（项目已接受 LPC 传染，见下节，沿用即可）；
⑤CC-BY / CC-BY-SA / OFL 强制署名须进 `frontend/public/credits/`。
**缺 `.riv` 时**：`adapters/anim/riveMascot.ts` 须 fail-soft 降级（Motion/Phaser tween 或静态表情），
不得硬依赖该二进制——故占位缺失为**非阻塞**项，不影响演示链路。

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

## R-14 HUD 美化：Dock 图标 + RPG UI 面板（2026-05-28）

| 运行时路径 | 来源 | License | 用途 |
| --- | --- | --- | --- |
| `frontend/public/assets/pixel/ui/kenney-rpg/*.png` (86 张) | Kenney "UI Pack RPG Expansion" (https://kenney.nl/assets/ui-pack-rpg-expansion) | **CC0-1.0** | RPG 风面板、按钮、进度条、箭头、光标、勾叉图标，HUD 面板纹理备选 |
| `frontend/public/assets/pixel/icons/dock/star.png` | Kenney "Game Icons" (https://kenney.nl/assets/game-icons) | **CC0-1.0** | 底部 Dock 技能槽备用星标 |
| `frontend/public/assets/pixel/icons/dock/gear.png` | Kenney "Game Icons" | **CC0-1.0** | 底部 Dock 设置槽备用齿轮 |
| `frontend/public/assets/pixel/icons/dock/home.png` | Kenney "Game Icons" | **CC0-1.0** | 底部 Dock 首页 / 回城备用 |
| `frontend/public/assets/pixel/icons/dock/exit.png` | Kenney "Game Icons" | **CC0-1.0** | 底部 Dock 退出备用 |
| `frontend/public/assets/pixel/icons/dock/trophy.png` | Kenney "Game Icons" | **CC0-1.0** | 底部 Dock 闯关塔备用奖杯 |
| `frontend/public/assets/pixel/icons/dock/exclamation.png` | Kenney "Game Icons" | **CC0-1.0** | 通知 / 任务警告标记备用 |
| `frontend/public/assets/pixel/icons/dock/information.png` | Kenney "Game Icons" | **CC0-1.0** | 信息提示备用 |
| `frontend/public/assets/pixel/icons/dock/target.png` | Kenney "Game Icons" | **CC0-1.0** | 目标 / 准心备用 |
| `frontend/public/assets/pixel/icons/dock/save.png` | Kenney "Game Icons" | **CC0-1.0** | 存档备用 |
| `frontend/public/assets/pixel/icons/dock/checkered-flag.png` | game-icons.net Lorc | **CC-BY 3.0** | 底部 Dock 任务槽（替换 ✦） |
| `frontend/public/assets/pixel/icons/dock/speech-bubble.svg` | game-icons.net Lorc | **CC-BY 3.0** | 底部 Dock 对话槽（替换 ✉） |
| `frontend/public/assets/pixel/icons/dock/swap-bag.svg` | game-icons.net Lorc | **CC-BY 3.0** | 底部 Dock 背包槽（替换 ⛃） |
| `frontend/public/assets/pixel/icons/dock/treasure-map.svg` | game-icons.net Lorc | **CC-BY 3.0** | 底部 Dock 地图槽（替换 ◰） |
| `frontend/public/assets/pixel/icons/dock/star-formation.png` | game-icons.net Delapouite | **CC-BY 3.0** | 底部 Dock 技能槽（替换 ★） |
| `frontend/public/assets/pixel/icons/dock/book-cover.png` | game-icons.net Lorc | **CC-BY 3.0** | 底部 Dock 课程槽（替换 📖） |
| `frontend/public/assets/pixel/icons/dock/crossed-swords.png` | game-icons.net Lorc | **CC-BY 3.0** | 底部 Dock 闯关槽（替换 ⚔） |
| `frontend/public/assets/pixel/icons/dock/teleport.svg` | game-icons.net Lorc | **CC-BY 3.0** | 底部 Dock 传送槽（替换 ➤） |
| `frontend/public/assets/pixel/icons/dock/night-sleep.svg` | game-icons.net Delapouite | **CC-BY 3.0** | 底部 Dock 休息槽（替换 ☾） |
| `frontend/public/assets/pixel/icons/dock/gears.png` | game-icons.net Lorc | **CC-BY 3.0** | 底部 Dock 设置槽（替换 ⚙） |
| `frontend/public/assets/pixel/icons/dock/compass-rose.png` | game-icons.net Lorc | **CC-BY 3.0** | 传送/导航备用 |
| `frontend/public/assets/pixel/icons/dock/scroll-unfurled.png` | game-icons.net Lorc | **CC-BY 3.0** | 任务/课程备用卷轴 |
| `frontend/public/assets/pixel/icons/dock/locked-chest.png` | game-icons.net Lorc | **CC-BY 3.0** | 背包/宝箱备用 |
| `frontend/public/assets/pixel/icons/dock/school-bag.svg` | game-icons.net Delapouite | **CC-BY 3.0** | 背包备选书包 |
| `frontend/public/assets/pixel/icons/dock/chat-bubble.svg` | game-icons.net Delapouite | **CC-BY 3.0** | 对话备选气泡 |
| `frontend/public/assets/pixel/icons/dock/moon.svg` | game-icons.net Lorc | **CC-BY 3.0** | 休息/夜晚备选月亮 |
| `frontend/public/assets/pixel/icons/dock/portal.svg` | game-icons.net Lorc | **CC-BY 3.0** | 传送备选传送门 |

**集成状态**：Dock 10 个图标已在 `HudShell.vue` 中由 emoji 替换为 `<img>` 像素图标。RPG UI 面板已复制到运行时但尚未接入 CSS。

## R-13 §-1 新增运行时素材（2026-05-25）

| 运行时路径 | 来源 | License | 用途 |
| --- | --- | --- | --- |
| `frontend/public/assets/pixel/ui/login-hero-stardew.png` | Pythontopia 原创（AI 辅助生成 + 人工审核） | CC-BY-SA 3.0+ | LoginPage 星露谷风暖色像素小镇 hero；NPC 与建筑均为原创，不复制任何商业像素游戏 |
| `frontend/public/assets/pixel/bosses/boss-{1..7}-*.png` (7 张) | Pythontopia 原创（AI 辅助生成 + 人工审核 + 原创主题设计） | CC-BY-SA 3.0+ | BossPage / Endless / Arena 第 10 关 Boss 立绘；7 章主题：算盘灵 / 解释器学徒 / 二进制独眼 / 流程钟楼 / 集合棱镜 / 文档守护神 / 归纳之镜；风格统一星露谷暖色像素，与 hero 同调 |
| `frontend/public/assets/pixel/vendor/ansimuz-mountain-dusk/{5×PNG,LICENSE-CC0.txt}` | ansimuz "Mountain at Dusk Background" (https://opengameart.org/content/mountain-at-dusk-background) | **CC0-1.0** | 备用：4 层 parallax 远山日落剪影，预留 boss 终局 / 章末过场使用 |
| `frontend/public/assets/pixel/portraits/heads/portrait_*_head.png` (29 张) | `frontend/public/assets/pixel/portraits/portrait_*.png` 子裁切；9 个 R-14 demo NPC 从 `sprites/npc-composites/npc_*.png` 的 down idle 正面帧裁切，由 `scripts/crop_portrait_heads.py` 输出 | 继承原 portrait / composite（CC0 / CC-BY 4.0 / OGA-BY 3.0 分文件） | DialogModal / DialogTree 单帧头像，每张 32×48 |
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
| `sfx/jump/kenney_jump_soft_000.ogg`, `sfx/jump/kenney_land_wood_000.ogg` | Kenney "Impact Sounds" (https://kenney.nl/assets/impact-sounds)，通过本机代理 7892 下载；源文件与协议保留在 `assets/inbox/sfx_jump/kenney_impact-sounds/` | CC0-1.0 | 低障碍跳跃起跳/落地反馈音效 |
| `props/fantastic-fence/fence_00.png` | InThePixel "fantastic fence 16x16" (https://opengameart.org/content/fantastic-fence-16x16)，通过本机代理 7892 下载；源文件保留在 `assets/inbox/fantastic-fence-16x16/` | CC0-1.0 | 低障碍小路矮栅栏 / 木桩 spritesheet（16×16，含单木桩、横栏、竖栏） |
| `props/fantastic-fence/stump_agnaldo.png` | Agnaldo "Tree(Arvore) & stump" (https://opengameart.org/content/treearvore-stump)，通过本机代理 7892 下载；源文件保留在 `assets/inbox/tree-stump/` | CC0-1.0 | 备用树桩 sprite，当前未接入 BootScene |
| `effects/weather/` | Weather FX 审计池 (`assets/inbox/weather_fx/LICENSE.md`) + Pythontopia 粒子占位 | CC0-1.0 | 云、雨滴、雪花来自已审计 CC0；confetti 为运行时占位 |
| `portraits/` | isaiah658 Pixel Pack #1 + Pepper&Carrot 24x32 + LPC Medieval Fantasy npc-composites | CC0-1.0 / CC-BY-4.0 / OGA-BY-3.0 | 29 NPC 对话立绘；Pepper&Carrot 必须署名 Cabbit 与 David Revoy，LPC composite 必须署名 Johannes Sjolund ("wulax") 与 base contributors |
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
| 29 NPC 统一头像裁切 | isaiah658 / Pepper&Carrot / LPC Medieval Fantasy composite | CC0-1.0 / CC-BY-4.0 / OGA-BY-3.0 | portraits/heads/ 统一尺寸 32×48 | 已完成 |

## R-14 新增软件依赖

| 依赖 | 版本 | License | 用途 |
|------|------|---------|------|
| reveal.js | 5.x | MIT | PPT 幻灯片渲染（前端） |
| 讯飞 Spark TTS API | v2 WebSocket | [讯飞服务条款](https://www.xfyun.cn/doc/tts/online_tts/API.html) | 语音合成（后端中继，失败降级 speechSynthesis） |
| [bilibili-api-python](https://pypi.org/project/bilibili-api-python/) | >=17.4,<18 | **GPL-3.0-or-later** | 「B站精选讲解」方案 A：在 `services/pythontopia-graph` 微服务内检索 B 站视频（取数），后端 Spring 仅 HTTP 代理 |

> reveal.js MIT License 不影响项目 CC-BY-SA 3.0 分层授权。
> 讯飞 TTS 为商用 API 调用，不包含可再分发代码，仅需遵守讯飞服务条款。

### 方案 A · B站精选讲解（2026-06-04 R-14 新增）

- **GPL 隔离**：`bilibili-api-python`（GPL-3.0-or-later）**仅**作为 Python 微服务
  `services/pythontopia-graph` 的依赖（`routes/video.py` 内**惰性 import**），不进入 Spring 后端
  与 Vue 前端代码层；后端 `com.pythontopia.video.*` 只通过 HTTP 代理调用该微服务。代码层分层授权
  （后端/前端 MIT，资产层 CC-BY-SA 3.0+）不受影响。
- **B 站缩略图 / 外链为外部资源，不随仓库分发**：前端「B站精选讲解」卡片仅展示 B 站返回的封面
  缩略图（`<img referrerpolicy="no-referrer">` 实时加载，规避防盗链）并跳转
  `https://www.bilibili.com/video/{bvid}` 官方页面——**不下载、不转存、不打包**任何 B 站视频或图片，
  亦**不**作为课件 grounding 引用。版权归原 UP 主 / 哔哩哔哩所有。

## R-12 智能体事务所像素场景（2026-05-29）

P3-a2「智能体事务所」执行动画复用既有 NPC 行走帧（无新增二进制美术）。这批合成帧此前已在
`BootScene` 预载但未单独登记，本次随事务所场景（`AgentOfficeScene.vue` 以 CSS background 引用）
补齐双文件登记。

| 运行时路径 | 来源 | License | 用途 |
| --- | --- | --- | --- |
| `frontend/public/assets/pixel/sprites/npc-composites/npc_*.png`（16 张） | 由 LPC walkcycle 图层合成（Johannes Sjölund "wulax" + bluecarrot16 + LPC base contributors），源 [LPC Medieval Fantasy Character Sprites](https://opengameart.org/content/lpc-medieval-fantasy-character-sprites) | **CC-BY-SA-3.0** | 智能体事务所 Agent / 主管 NPC 行走帧（9×4 walkcycle，64×64）；事务所动画 + 世界 NPC 复用 |

> LPC CC-BY-SA 3.0 传染已接受：包装作品同时以 CC-BY-SA 3.0+ 发布，代码层保持 MIT。
> 已同步登记到 `scripts/sync-assets.mjs` MANIFEST（noSync）与 `frontend/public/assets/pixel/LICENSE.md` 自动索引。

## R-12 ULPC 全动作角色（动作模组方案A，2026-05-29 修订）

主角与世界 NPC 改用**官方 Universal-LPC-Spritesheet-Character-Generator** 的整套素材库
合成「Expanded」全动作角色表（13×42：base 6 动作 + climb/idle/jump/sit/emote/run）。
由 `scripts/build_ulpc_characters.py` 忠实合成（仅用官方像素，非手绘）。源 zip/credits 留存于
`assets/inbox/characters_extra/ulpc_generated/`（`README_SOURCE.md` + `CREDITS-ulpc-used.txt` +
`LICENSE-ulpc.txt`）。早前 `scripts/build-npc-action-sheets.py` 的本地合成套装（npcact_*）已废弃删除。

| 运行时路径 | 来源 | License | 用途 |
| --- | --- | --- | --- |
| `frontend/public/assets/pixel/sprites/ulpc/{player_sara2,ulpc_villager_f,ulpc_villager_m,ulpc_scholar,ulpc_worker,ulpc_lady,ulpc_youth,ulpc_elder,ulpc_guard}.png`（9 张） | [Universal-LPC-Spritesheet-Character-Generator](https://github.com/LiberatedPixelCup/Universal-LPC-Spritesheet-Character-Generator)，部件作者见 `CREDITS-ulpc-used.txt`（bluecarrot16 / ElizaWy / JaidynReiman / wulax / Redshrike 等） | **CC-BY-SA-3.0 / OGA-BY-3.0 / CC-BY-3.0+ / CC0 / GPL（分文件混合）** | 主角 + 8 个变体 NPC 全动作精灵表（64×64，含 run/jump/idle/sit）；16 个城镇 NPC 按角色映射复用 |

> 合成产物为构建产物（build artifact）：源 = 官方 ULPC 素材库 + 合成脚本；二者均已审计。
> 接受 CC-BY-SA 3.0 传染：以 CC-BY-SA 3.0+ 发布。

## R-12 手持工具叠加层（动作模组 C 阶段，联网爬取，2026-05-29）

为主角与 NPC 的「浇水 / 锄地」动作添加星露谷味的手持工具，从 OpenGameArt 爬取
**[LPC] Hand Tools**（与 LPC 通用表同帧布局，可直接叠加在任意 LPC 角色之上）。
源 zip 留存于 `assets/inbox/characters_extra/lpc_hand_tools/`（含 `CREDITS-hand-tools.txt`）。

| 运行时路径 | 来源 | License | 用途 |
| --- | --- | --- | --- |
| `frontend/public/assets/pixel/sprites/lpc-tools/{watering-can,watering-can-bg,hoe,hoe-bg}.png`（4 张） | [\[LPC\] Hand Tools](https://opengameart.org/content/lpc-hand-tools) by **bluecarrot16, Pierre Vigier, Tuomo Untinen, Johannes Sjölund (wulax), Inboxninja** | **CC-BY-SA-3.0**（浇水壶另可 OGA-BY-3.0+/CC-BY-4.0+/GPL-2.0+） | 主角/NPC 浇水(thrust)动作的手持工具叠加层（universal 13×21，前景 + 身后两层）；浇水壶由 SWRG 委托、锄头由 SWRG 委托 |

> 仅取 thrust 系工具（与基础表 thrust 帧对齐）；smash/fishing 系工具因主角缺对应扩展动画暂未采用。
> 完整署名见运行时目录内 `CREDITS-hand-tools.txt`；CC-BY-SA 3.0 传染同上。

## R-12 茅草屋建筑（替换方块拼接，2026-05-29）

世界场景的建筑由「方块瓦片拼接」升级为 LPC 茅草屋整屋 sprite（四坡茅草顶 + 半木墙体），
由 `scripts/build_cottages.py` 从 kit 裁切合成，按建筑区位底沿对齐贴在旧瓦片之上。

| 运行时路径 | 来源 | License | 用途 |
| --- | --- | --- | --- |
| `frontend/public/assets/pixel/sprites/cottages/cottage_{tan,cream,stone}.png`（3 张） | [\[LPC\] Thatched-roof Cottage](https://opengameart.org/content/lpc-thatched-roof-cottage) by **bluecarrot16, Daniel Eddeland (daneeklu), Lanea Zimmerman (Sharm)** | **CC-BY-SA-3.0** | 世界场景建筑整屋 sprite（142×220），按 route 哈希取色 |

> 源 kit 留存于 `assets/inbox/buildings_npc/lpc_thatched-roof-cottage/`；CC-BY-SA 3.0 传染同上。

## R-12 砍伐工具叠加（斧头，2026-05-30）

主角砍树/挖矿用「斧头挥砍」：身体播标准 slash，斧头用 ULPC 192px oversize `attack_slash`
叠加层同步挥出（取自 `weapon_blunt_waraxe`）。fishing/pickaxe 专用动画因 ULPC 缺对应
身体+衣物帧暂不可用（见 ULPC README）。

| 运行时路径 | 来源 | License | 用途 |
| --- | --- | --- | --- |
| `frontend/public/assets/pixel/sprites/weapons/weapon_axe{,_bg}.png`（2 张，192×192） | [Universal-LPC-Spritesheet-Character-Generator](https://github.com/LiberatedPixelCup/Universal-LPC-Spritesheet-Character-Generator) 的 `weapon_blunt_waraxe`（LPC contributors） | **CC-BY-SA-3.0 / OGA-BY-3.0 / GPL** | 主角砍树/挖矿斧头挥砍叠加（与 slash 同步） |

## 待办（backlog）

1. `assets/inbox/tilesets/` 与 `assets/inbox/icons/`、`assets/inbox/buildings/`、`assets/inbox/characters/`、`assets/inbox/ui/`、`assets/inbox/mines/` 待补 LICENSE_AUDIT 二次确认
2. 醒来后可选：手动下载 ansimuz/sunny-land-pixel-game-art + ansimuz/fantasy-weapons-set（itch.io CC0，需浏览器 session）
3. 真实部署后 `frontend/public/credits/` 全量署名页生成（CC-BY-SA 3.0 + OFL-1.1 必备）
4. B6 LPC Lamp Posts 下载后集成到 town_hub.json / campus_east.json 的 tileset 层
5. B6 NPC portraits 批量统一：scripts/crop_portrait_heads.py 已覆盖 29 NPC；后续如新增 NPC，需同步 meta/index 与 heads 裁切
