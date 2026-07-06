# Pythontopia R-12 美术资产合规调研与下载清单

> 调研日期：2026-05-24 (UTC+8)
> 调研代理：合规美术素材调研 subagent
> 仓库基准：`/home/cypress/Pythontopia/`（已有 `assets/inbox/` 11 个非空目录 / 13000+ 文件 / 359 MB，见 `assets/inbox/LICENSE_AUDIT.md` 与 `LICENSE-ART.md`）
> 适用版本：**R-12**（星露谷化改造：5 张地图 + 20 NPC + UI 闭环）
> 输出目录：`/home/cypress/Pythontopia/assets/raw/`（本次新增，不与 `assets/inbox/` 冲突）

---

## 第 1 节 · 执行摘要

### 调研结论

Pythontopia 已有素材库（13000+ 文件 / 359 MB）已覆盖 R-12 **绝大多数**通用需求（Kenney CC0 tilemap/character/UI 全家桶、LPC crops 与建筑、Ark Pixel 中文字体、Kenney 粒子等）。本次"R-12 合规购物清单"**不是从零搭建**，而是**针对 R-12 增量缺口**精准补齐，避免重复下载已有资源。

| 维度 | 数值 |
|---|---:|
| 本次新增推荐资源数 | **44 个**（P0=14 / P1=18 / P2=8 / P3=4） |
| 全部为合规协议 | ✅ CC0 / CC-BY 3.0 / CC-BY 4.0 / CC-BY-SA 3.0+ / OFL-1.1 |
| 预计下载总量 | **~73 MB**（P0+P1=约 58 MB） |
| 覆盖 R-12 缺口 | 5 张地图特色 tileset / 20 NPC 8 方向 / 教室室内 / 港口/灯塔 / 矿洞深井 / 季节切换 / OFL 像素字体 |
| 触发 SA 传染 | ⚠️ 13 个 LPC 派生条目（项目已接受 CC-BY-SA 3.0 传染） |

### R-12 缺口分析（基于 `assets/inbox/LICENSE_AUDIT.md` 现状）

| R-12 子需求 | 已有 | 缺口（本清单补齐） |
|---|---|---|
| **town_hub** 中央广场 | Kenney tiny-town / medieval-rts | LPC Victorian Town Decorations（喷泉、街灯、招牌） |
| **farm_north** 农场（季节） | LPC crops + 春夏季 | **LPC Winter Tiles**、**LPC Modern Streets**（围栏） |
| **mine_south** 矿洞 | Kenney roguelike-caves-dungeons | **LPC Mine**（矿洞入口/起重机/矿石）、**LPC Cavern and ruin tiles**（矿车轨道） |
| **campus_east** 校园/教室 | Kenney roguelike-modern-city、tiny-town | **LPC Walls / Tavern / Wooden Furniture**（教室室内三件套）、**isaiah658 Pixel Pack #2**（实验室桌/电脑） |
| **port_west** 港口/灯塔 | （空白） | **LPC Beach/Desert**、**3TD Harbour Pack CC0**、**LPC Pirates**（港口 NPC 服装） |
| **20 NPC 8 方向行走** | Kenney roguelike-characters（4 方向） | **LPC Universal Spritesheet Generator**（4 方向标准 + 步行循环 9 帧；8 方向需运行时插帧） |
| **像素 UI 闭环** | Kenney ui-pack-pixel-adventure + rpg-expansion | **Game-icons.net** KC 图标补充、**Kenney UI Pack - Adventure**（新版） |
| **像素字体** | Ark Pixel（中文 OFL） | **monogram**（CC0）、**Press Start 2P / VT323**（OFL）英文像素字体 |
| **天气特效** | Kenney particle-pack | （已充分） |
| **物品图标** | Kenney generic-items + game-icons + 1-bit | **Kenney Pixel Shmup**（激光特效→可作"代码能量"特效） |

### 最关键的 3 条建议

1. **20 NPC 用 LPC Universal Spritesheet Generator 在线导出，而非手工拼接**。该生成器已聚合 200+ 子素材（CC-BY-SA 3.0）、4 方向 walk 9 帧标准动画，本地用 puppeteer 脚本批量生成 20 个差异化 NPC PNG 即可。8 方向降级方案：项目已有 `input.ts` 用 `toServerDirection` 把 8 向降到 4 向（见 AGENTS.md），美术只需 4 向素材，符合既定架构。
2. **5 张地图按"先 hub + farm + classroom，后 mine + port"分两批接入**。P0+P1 批次 58 MB 即可让 5 张地图全部具备可玩 tileset；P2 批次补 LPC Pirates 让港口 NPC 有差异化人设；P3 批次（Victorian decorations + Pixel Shmup）只在演示前最后冲刺。
3. **同步在 `LICENSE-ART.md` 与 `frontend/public/credits/` 写入 13 条新 LPC 署名**。LPC CC-BY-SA 3.0+ 强制要求 (a) 保留 CREDITS-*.txt 全文、(b) 在 about 页面列出 bluecarrot16、Lanea Zimmerman (Sharm)、Daniel Eddeland (daneeklu) 等核心作者、(c) 项目最终包装作品同时以 CC-BY-SA 3.0+ 发布（决策已在 `LICENSE-ART.md` 中接受）。下载脚本会自动把每个包内的 `CREDITS-*.txt` 一并落盘。

---

## 第 2 节 · 按 8 类组织的详细清单

> 命名约定：每条以 `[优先级]` 起头；`P0` = R-12 必备，`P1` = 5 张地图必备，`P2` = 20 NPC 完整化，`P3` = 演示加分项。
> 字段顺序：来源 URL → License → 作者 → 用途 → 像素规格 → 体积 → 下载命令。
> **下载命令统一使用 wget**，下载到 `assets/raw/<category>/<package>/` 子目录；脚本会在第 3 节统一生成。

### 2.1 Tileset（4 季 farm + 教室室内 + 矿洞深井）

#### [P1] LPC Winter Tiles
- **URL**：<https://opengameart.org/content/lpc-winter-tiles>
- **License**：CC-BY-SA 3.0+ / GPL 3.0+（SA 传染）
- **作者**：Demetrius（基于 Lanea Zimmerman 的 LPC Modified Base tiles）
- **用途**：`farm_north` 冬季皮肤（雪松、雪地、冰水转换）
- **规格**：32×32 LPC 标准
- **体积**：~25 KB（3 个 PNG：TilesA2 / TilesA3 / TilesB）
- **下载**：
  ```bash
  wget -P assets/raw/tilesets/lpc-winter-tiles/ \
    https://opengameart.org/sites/default/files/TilesA2.png \
    https://opengameart.org/sites/default/files/TilesA3.png \
    https://opengameart.org/sites/default/files/TilesB.png
  ```

#### [P0] LPC Tile Atlas（基础户外/地形紧凑图集）
- **URL**：<https://opengameart.org/content/lpc-tile-atlas>
- **License**：CC-BY-SA 3.0+（SA 传染）
- **作者**：adrix89 汇编自 LPC 大赛全员
- **用途**：`town_hub` / `farm_north` 通用地表（1024×1024 单图集，硬件友好）
- **规格**：32×32 LPC，整图 1024×1024
- **体积**：544 KB
- **下载**：
  ```bash
  wget -O assets/raw/tilesets/lpc-tile-atlas/Atlas.zip \
    https://opengameart.org/sites/default/files/Atlas_0.zip
  ```

#### [P1] LPC Tile Atlas 2（建筑/物件图集）
- **URL**：<https://opengameart.org/content/lpc-tile-atlas2>
- **License**：CC-BY-SA 3.0+（SA 传染）
- **作者**：adrix89 汇编
- **用途**：`town_hub` 建筑、物件补充
- **规格**：32×32 LPC，整图 1024×1024
- **体积**：437 KB
- **下载**：
  ```bash
  wget -O assets/raw/tilesets/lpc-tile-atlas2/Atlas2.zip \
    https://opengameart.org/sites/default/files/Atlas2.zip
  ```

#### [P1] LPC Beach / Desert
- **URL**：<https://opengameart.org/content/lpc-beach-desert>
- **License**：CC-BY-SA 3.0+（SA 传染）
- **作者**：bluecarrot16 等
- **用途**：`port_west` 海滩、沙地、贝壳、海岸植被
- **规格**：32×32 LPC
- **体积**：90 KB（v2 版本）
- **下载**：
  ```bash
  wget -O assets/raw/tilesets/lpc-beach-desert/lpc-beach-desert-v2.zip \
    https://opengameart.org/sites/default/files/lpc-beach-desert-v2.zip
  ```

#### [P0] LPC Walls（300+ 室内墙体）
- **URL**：<https://opengameart.org/content/lpc-walls>
- **License**：CC-BY-SA 3.0+（SA 传染）
- **作者**：bluecarrot16, Lanea Zimmerman, Daniel Armstrong 等 19 人
- **用途**：`campus_east` 教室室内墙体 + Tiled Wang tile（自动拼接）
- **规格**：32×32 LPC
- **体积**：507 KB
- **下载**：
  ```bash
  wget -O assets/raw/tilesets/lpc-walls/lpc-walls.zip \
    https://opengameart.org/sites/default/files/lpc-walls.zip
  ```

#### [P0] LPC Tavern（含动画家具/灯具/壁炉）
- **URL**：<https://opengameart.org/content/lpc-tavern>
- **License**：CC-BY-SA 3.0+（SA 传染）
- **作者**：bluecarrot16, Lanea Zimmerman (Sharm), William Thompson, Jetrel 等
- **用途**：`town_hub` 商店室内（铁匠铺/食堂）、`campus_east` 教师办公室
- **规格**：32×32 LPC，含动画
- **体积**：2.1 MB
- **下载**：
  ```bash
  wget -O assets/raw/tilesets/lpc-tavern/lpc-tavern.zip \
    https://opengameart.org/sites/default/files/lpc-tavern.zip
  ```

#### [P1] LPC Cavern and ruin tiles（矿洞深井 + 矿车轨道）
- **URL**：<https://opengameart.org/content/lpc-cavern-and-ruin-tiles>
- **License**：CC-BY-SA 3.0+（SA 传染）
- **作者**：见 credits.txt（多作者）
- **用途**：`mine_south` 矿洞内部（含矿车轨道、水/熔岩动画、石柱、神秘水晶）
- **规格**：32×32 LPC，含动画
- **体积**：383 KB
- **下载**：
  ```bash
  wget -O assets/raw/tilesets/lpc-cavern-and-ruin/LPC_cavern_ruins.zip \
    https://opengameart.org/sites/default/files/LPC_cavern_ruins.zip
  ```

### 2.2 Buildings（房屋/商店/学校/矿洞入口/灯塔/温室）

#### [P0] LPC Mine（矿洞入口 + 起重机动画 + 矿石堆）
- **URL**：<https://opengameart.org/content/lpc-mine>
- **License**：**CC-BY 4.0**（不传染！）
- **作者**：bluecarrot16, Lanea Zimmerman (Sharm), Ivan Voirol
- **用途**：`mine_south` 入口建筑 + 起重机循环动画 + 各类矿石堆
- **规格**：32×32 LPC，含 Tiled .tmx 示例
- **体积**：128 KB
- **下载**：
  ```bash
  wget -O assets/raw/buildings/lpc-mine/lpc-mine.zip \
    https://opengameart.org/sites/default/files/lpc-mine.zip
  ```

#### [P1] LPC House interior and decorations（房屋内饰）
- **URL**：<https://opengameart.org/content/lpc-house-interior-and-decorations>
- **License**：CC-BY-SA 3.0+（SA 传染）
- **作者**：Janna, Lilius, Jannax
- **用途**：商店/教师办公室室内（柜子、床、厨房设备、动画壁炉）
- **规格**：32×32 + 部分 32×64 / 32×96
- **体积**：143 KB
- **下载**：
  ```bash
  wget -O assets/raw/buildings/lpc-house-interior/LPC_house_interior.zip \
    https://opengameart.org/sites/default/files/LPC_house_interior_0.zip
  ```

#### [P0] LPC Interior Castle Tiles（学校大厅）
- **URL**：<https://opengameart.org/content/lpc-interior-castle-tiles>
- **License**：CC-BY-SA 3.0+（SA 传染）
- **作者**：Lanea Zimmerman (Sharm)
- **用途**：`campus_east` 学校大厅、楼梯、教学楼内饰
- **规格**：32×32 LPC（castle.png / stairs.png）
- **体积**：35 KB（2 个 PNG）
- **下载**：
  ```bash
  wget -P assets/raw/buildings/lpc-interior-castle/ \
    https://opengameart.org/sites/default/files/castle_2.png \
    https://opengameart.org/sites/default/files/stairs_1.png
  ```

#### [P1] 3TD Harbour Pack（灯塔 + 码头 + 仓库）
- **URL**：<https://lpc.opengameart.org/content/3td-harbour-pack>
- **License**：**CC0**（无传染！）
- **作者**：Ron Kapaun / 3TD Studios（CC0 释出）
- **用途**：`port_west` 灯塔/Splitrock、码头、仓库、码头亭。**注意**：3D Blender 源文件，需要先 Blender 渲染成 2D 像素正交视图（或参考剪影自绘像素）。如果团队无 Blender 流水线，可降级用 LPC 自绘灯塔（见替代方案）。
- **规格**：3D 模型 + 贴图（输出后建议 64×128 像素灯塔精灵）
- **体积**：18.3 MB
- **下载**：
  ```bash
  wget -O assets/raw/buildings/3td-harbour/3td_Harbor_Pack.zip \
    https://lpc.opengameart.org/sites/default/files/3td_Harbor%20Pack.zip
  ```

#### [P1] Kenney Pixel Platformer Farm Expansion（温室）
- **URL**：<https://kenney-assets.itch.io/pixel-platformer-farm-expansion>
- **License**：**CC0**
- **作者**：Kenney Vleugels
- **用途**：`farm_north` 温室建筑 + 秋季作物 + 110 sprites
- **规格**：18×18 像素 platformer 风
- **体积**：178 KB
- **下载**：itch.io API 直链（备用 Kenney.nl 镜像，见脚本 P1 段）
  ```bash
  curl -L -o assets/raw/buildings/kenney-farm-expansion/pixel-platformer-farm-expansion.zip \
    "https://kenney-assets.itch.io/pixel-platformer-farm-expansion/file/4068783"
  # 若 itch CDN 直链失效，备用：
  # wget -O assets/raw/buildings/kenney-farm-expansion/pack.zip https://kenney.nl/media/pages/assets/pixel-platformer-farm-expansion/<latest>.zip
  ```

### 2.3 NPC sprites（20 NPC 差异化 + 8 方向行走）

#### [P0] LPC Universal Spritesheet Generator（仓库 + 在线导出）
- **URL（仓库）**：<https://github.com/LiberatedPixelCup/Universal-LPC-Spritesheet-Character-Generator>
- **URL（在线）**：<https://liberatedpixelcup.github.io/Universal-LPC-Spritesheet-Character-Generator/>
- **License**：CC-BY-SA 3.0+ / GPL 3.0+（所有子素材集合）
- **作者**：bluecarrot16, Lanea Zimmerman (Sharm), wulax, Redshrike, ElizaWy 等 100+ 人（导出时附带 CSV/TXT credits 文件）
- **用途**：**20 NPC 的差异化人设核心来源**。在线选 body type（Male/Female/Teen/Child/Pregnant/Muscular）+ 头发/皮肤/服装/工具，导出 64×64 单 PNG 含 walk 9 帧 × 4 方向 + spellcast/thrust/slash/shoot/hurt/watering 等动画。
- **规格**：64×64 LPC 标准，walk 动画 9 帧 × 4 方向（North/West/South/East），8 方向通过 `input.ts toServerDirection` 4 向降级
- **体积**：仓库克隆 ~600 MB（含全部 spritesheets/）；导出单 NPC PNG ~30-80 KB；20 NPC 总计约 1-2 MB
- **下载**：
  ```bash
  # 方式 A（推荐，省空间）：在线生成 20 NPC + 手动导出 ZIP，落到本地
  echo "1) 打开 https://liberatedpixelcup.github.io/Universal-LPC-Spritesheet-Character-Generator/"
  echo "2) 按 docs/r12-npc-personas.md 配置 20 个 NPC"
  echo "3) 每个 NPC 导出 PNG + CSV credits 到 assets/raw/characters/lpc-npcs-<key>/"
  # 方式 B（完整克隆，团队自托管定制时使用）：
  # git clone --depth 1 https://github.com/LiberatedPixelCup/Universal-LPC-Spritesheet-Character-Generator.git \
  #   assets/raw/characters/lpc-ulpc-generator/
  ```

#### [P0] LPC Medieval Fantasy Character Sprites（LPC 大赛冠军基线）
- **URL**：<https://opengameart.org/content/lpc-medieval-fantasy-character-sprites>
- **License**：CC-BY-SA 3.0+（SA 传染，作者声明 waive DRM 条款）
- **作者**：wulax（Johannes Sjölund）
- **用途**：LPC 大赛冠军作品，含商人、战士、农夫、骷髅、武器/盔甲模块拼装系统。**作为 generator 缺货时的 fallback 备份**
- **规格**：64×64 LPC，walk/slash/thrust/spellcast 全套
- **体积**：lpc_entry.zip 3.2 MB + expansion_pack-0.04.zip 1.1 MB = **4.3 MB**
- **下载**：
  ```bash
  wget -P assets/raw/characters/lpc-medieval-fantasy/ \
    https://opengameart.org/sites/default/files/lpc_entry.zip \
    https://opengameart.org/sites/default/files/expansion_pack-0.04.zip
  ```

#### [P2] LPC Pirates（港口 NPC 服装包）
- **URL**：<https://opengameart.org/content/lpc-pirates>
- **License**：CC-BY-SA 3.0+（SA 传染；作者 waive DRM 条款）
- **作者**：bluecarrot16, Fabzy, Nila122, JaidynReiman, Joe White 等 11 人
- **用途**：`port_west` 港口 NPC（船长、码头工、商船学徒）服装：海盗帽、马甲、长靴、钩手、独眼罩
- **规格**：64×64 LPC，与 Universal Spritesheet Generator 兼容
- **体积**：lpc-pirate-v1.1.zip **46.2 MB**（建议团队精挑后再入库）
- **下载**：
  ```bash
  wget -O assets/raw/characters/lpc-pirates/lpc-pirate-v1.1.zip \
    https://opengameart.org/sites/default/files/lpc-pirate-v1.1.zip
  ```

#### [P2] LPC Children walk animation（学生 NPC 矮个差异化）
- **URL**：<https://opengameart.org/content/lpc-children-walk-animation>
- **License**：CC-BY-SA 3.0+
- **作者**：ElizaWy 等
- **用途**：低年级学生 NPC（与教师/学长 NPC 形成身高差，强化"高校初学者"叙事）
- **规格**：64×64 LPC child base
- **体积**：~200 KB
- **下载**：
  ```bash
  # 该资源页面有多版本子文件，建议从 OGA 页面手动选择 zip；下面给一个常见 file_id 模板，运行前 curl 确认 200 OK
  echo "请访问 https://opengameart.org/content/lpc-children-walk-animation 选择最新 children-walk-vN.zip"
  echo "命名落到 assets/raw/characters/lpc-children/"
  ```

#### [P0] isaiah658 Pixel Pack #2（科学实验室 + 学生角色）
- **URL**：<https://opengameart.org/content/isaiah658s-pixel-pack-2>
- **License**：**CC0**（无传染！）
- **作者**：isaiah658
- **用途**：`campus_east` 教室/实验室桌面、便利店、自动售货机、街机机器、码头/水域；含字符 spritesheet（备用 NPC base）；项目 `assets/inbox/portraits/` 已含 isaiah658 Pixel Pack #1，本次补 #2
- **规格**：16×16 tile + 64×64 怪物
- **体积**：v2.0 ZIP 233 KB
- **下载**：
  ```bash
  wget -O assets/raw/characters/isaiah658-pixel-pack-2/v2.0.zip \
    https://opengameart.org/sites/default/files/version_2.0_isaiah658s_pixel_pack_2.zip
  ```

### 2.4 Player sprites（多发型/服装/性别变体）

> **方案**：玩家变体复用第 2.3 节 **LPC Universal Spritesheet Generator** 的同一管线。下方列出 4 套预设组合（男/女 × 学生/工程师），由 generator 在线导出即可，**无额外下载**。

| 玩家预设 | body | 头发 | 服装 | 工具 |
|---|---|---|---|---|
| `student_male_default` | Male, Light | Spiked / Brown | T-shirt + Jeans | Glasses + Backpack |
| `student_female_default` | Female, Light | Long / Black | Hoodie + Skirt | Glasses + Notebook |
| `engineer_male` | Male, Tan | Short / Black | Plaid + Cargo | Laptop bag |
| `engineer_female` | Female, Tan | Bob / Brown | Blazer + Pants | Tablet |

#### [P0] LPC Teen / Unisex Base & Clothes（青少年 base）
- **URL**：<https://opengameart.org/content/lpc-teen-unisex-base-clothes>
- **License**：CC-BY-SA 3.0+（SA 传染）
- **作者**：ElizaWy
- **用途**：玩家 4 套预设的青少年 base（贴合"高校 Python 初学者"年龄段）
- **规格**：64×64 LPC teen base + 多套服装层
- **体积**：~3 MB
- **下载**：
  ```bash
  # 通过 generator 在线选 Body type=Teen 即可，无需直接下载 zip；如要本地源文件：
  # 访问 https://opengameart.org/content/lpc-teen-unisex-base-clothes 取最新 zip
  echo "Teen base 通过 Universal Spritesheet Generator 在线 Body type=Teen 导出"
  ```

### 2.5 Props & 装饰物（树/花/路灯/栅栏/招牌/课桌/电脑）

#### [P1] LPC Victorian Town Decorations（街灯/招牌/喷泉，3000+ tiles）
- **URL**：<https://opengameart.org/content/lpc-victorian-town-decorations>
- **License**：CC-BY-SA 3.0+（SA 传染）
- **作者**：bluecarrot16 + 20 人合著（含 Casper Nilsson, Lanea Zimmerman, William Thompson 等）
- **用途**：`town_hub` 中央广场动画街灯、动画喷泉、雕塑、招牌、马车；`campus_east` 早期工业风教学楼装饰
- **规格**：32×32 LPC，含 Tiled .tmx 示例
- **体积**：lpc-victorian-decoration.zip **365 KB**（preview 包 14.7 MB **不下载**，仅取核心 zip）
- **下载**：
  ```bash
  wget -O assets/raw/props/lpc-victorian-decorations/lpc-victorian-decoration.zip \
    https://opengameart.org/sites/default/files/lpc-victorian-decoration.zip
  ```

#### [P1] LPC Modern Streets（CC0 现代街道 + 围栏 + 交通锥）
- **URL**：<https://opengameart.org/content/lpc-modern-streets>
- **License**：**CC0**（无传染！）
- **作者**：Faufilage
- **用途**：`campus_east` 校园主干道、人行道、井盖、交通灯、围栏、垃圾桶；`farm_north` 边界铁丝围栏
- **规格**：32×32 LPC，含 Wang tile（无需编辑可在 Tiled 中自动拼接）
- **体积**：70 KB
- **下载**：
  ```bash
  wget -O assets/raw/props/lpc-modern-streets/lpc_modern_streets.zip \
    https://opengameart.org/sites/default/files/lpc_modern_streets.zip
  ```

#### [P1] LPC Wooden Furniture（桌椅柜橱）
- **URL**：<https://opengameart.org/content/lpc-wooden-furniture>
- **License**：CC-BY-SA 3.0+（SA 传染）
- **作者**：bluecarrot16, Baŝto, Lanea Zimmerman, William Thompson 等
- **用途**：`campus_east` 教室桌椅、办公室柜子；4 种木色（dark/blonde/green/white）
- **规格**：32×32 LPC，含 PSD 分层
- **体积**：4 个 PNG ~210 KB + layers.zip 176 KB = ~390 KB
- **下载**：
  ```bash
  wget -P assets/raw/props/lpc-wooden-furniture/ \
    https://opengameart.org/sites/default/files/dark-wood.png \
    https://opengameart.org/sites/default/files/blonde-wood.png \
    https://opengameart.org/sites/default/files/green-wood.png \
    https://opengameart.org/sites/default/files/white-wood.png \
    https://opengameart.org/sites/default/files/layers.zip \
    https://opengameart.org/sites/default/files/credits-furniture.txt_.zip
  ```

#### [P2] LPC style wood bridges and steel flooring（农场/港口木桥）
- **URL**：<https://opengameart.org/content/lpc-style-wood-bridges-and-steel-flooring>
- **License**：CC-BY-SA 3.0+
- **作者**：Xenodora
- **用途**：`farm_north` 跨溪木桥、`port_west` 钢制走道与栏杆
- **规格**：32×32 + 16×32（栏杆）
- **体积**：~10 KB（5 个 PNG）
- **下载**：
  ```bash
  wget -P assets/raw/props/lpc-bridges/ \
    https://opengameart.org/sites/default/files/bridge-wood-angled.png \
    https://opengameart.org/sites/default/files/bridge-wood-square.png \
    https://opengameart.org/sites/default/files/floor-steel.png \
    https://opengameart.org/sites/default/files/railing-wood-16x32.png \
    https://opengameart.org/sites/default/files/railing-steel-16x32.png
  ```

#### [P3] Kenney RPG Urban Kit（480 现代街景）
- **URL**：<https://kenney-assets.itch.io/rpg-urban-kit>
- **License**：**CC0**
- **作者**：Kenney Vleugels
- **用途**：`campus_east` 现代街景补充（车辆、公交站、便利店招牌）；与已有 `kenney_tilemaps/rpg-urban-pack/` 互补
- **规格**：16×16 RPG urban；6 个角色 4 方向 walk
- **体积**：kenney_rpgUrbanKit.zip ~300 KB
- **下载**：
  ```bash
  # itch.io 文件 ID 可能变动，备选 kenney.nl 主页下载按钮：
  curl -L -o assets/raw/props/kenney-rpg-urban-kit/kenney_rpgUrbanKit.zip \
    "https://kenney-assets.itch.io/rpg-urban-kit/file/1389088"
  ```

### 2.6 像素 UI（对话框/背包/技能树/任务公告板）

#### [P0] Game-icons.net（KC + 任务 + 技能图标 4000+）
- **URL（about）**：<https://game-icons.net/about.html>
- **URL（仓库）**：<https://github.com/game-icons/icons>
- **License**：**CC-BY 3.0**（不传染但**强制署名**）
- **作者**：Lorc, Delapouite, Skoll, Caro Asercion, sbed, Carl Olsen 等 35 人
- **用途**：65 个 KC 图标、技能树节点、任务公告板图标、矿洞病害图标。项目 `assets/inbox/icons_kc/` 已含 60 个；本次推荐**完整克隆**保留 4000+ 备用
- **规格**：SVG 矢量（可任意缩放到 32×32 / 48×48）
- **体积**：仓库克隆 ~80 MB；按需 cherry-pick 子集 ~5 MB
- **下载**：
  ```bash
  # 完整克隆（团队定制图标库时）
  git clone --depth 1 https://github.com/game-icons/icons.git \
    assets/raw/icons/game-icons-net/
  # 或按需下载 about.html 中列出的子集
  ```

#### [P0] Kenney 1-Bit Pack（极简风 UI + 角色 + 物品）
- **URL**：<https://kenney-assets.itch.io/1-bit-pack>
- **License**：**CC0**
- **作者**：Kenney Vleugels
- **用途**：调试模式 UI、Bug 矿洞病害事件提示、KC 极简徽章；与项目已有 `kenney_tilemaps/1-bit-pack/` 是同源最新版（1.2 vs 1.1）
- **规格**：16×16 monochrome + 透明版
- **体积**：1bitpack_kenney_1.2.zip 642 KB
- **下载**：
  ```bash
  curl -L -o assets/raw/ui/kenney-1-bit-pack/1bitpack_kenney_1.2.zip \
    "https://kenney-assets.itch.io/1-bit-pack/file/1019258"
  # 备用 OGA 镜像（旧版 1.1）：
  # wget -O assets/raw/ui/kenney-1-bit-pack/1bitpack_kenney_1.1.zip \
  #   https://opengameart.org/sites/default/files/1bitpack_kenney_1.1.zip
  ```

### 2.7 天气与特效（雨/雪/光照/烟雾/粒子/昼夜过渡）

> **现状**：`assets/inbox/weather_fx/` 已有 223 文件 / 30 MB（Kenney particle_pack + OGA rain/snow/sun/lightning/weather_icons），R-12 **已充分覆盖**。本节仅推荐 1 个增量。

#### [P3] Kenney Pixel Shmup（代码能量射线/激光特效）
- **URL**：<https://kenney.nl/assets/pixel-shmup>
- **License**：**CC0**
- **作者**：Kenney Vleugels
- **用途**：玩家"运行代码成功"反馈特效（能量射线、爆炸）、矿洞 Bug 击中特效
- **规格**：16×16 像素，含粒子精灵
- **体积**：~150 KB
- **下载**：
  ```bash
  # itch.io 备用：
  curl -L -o assets/raw/effects/kenney-pixel-shmup/kenney_pixel-shmup.zip \
    "https://kenney-assets.itch.io/pixel-shmup/file/2026850"
  ```

### 2.8 物品图标（教具/作物/工具/书本/代码片段/能量豆/金币）

> **现状**：`assets/inbox/icons_kc/`（60 个 game-icons CC-BY 3.0）+ `assets/inbox/kenney_tilemaps/generic-items/`（~340 个 CC0）+ `assets/inbox/kenney_tilemaps/game-icons/`（~440 个 CC0）已覆盖通用物品。本节仅补"代码主题"专项。

#### [P3] Kenney Pixel Pack（98 通用像素物品）
- **URL**：<https://kenney.nl/assets/pixel-pack>
- **License**：**CC0**
- **作者**：Kenney Vleugels
- **用途**：教具图标（计算器、书本、键盘、能量豆）补充
- **规格**：8×8 / 16×16 像素
- **体积**：~50 KB
- **下载**：
  ```bash
  curl -L -o assets/raw/items/kenney-pixel-pack/kenney_pixel-pack.zip \
    "https://kenney-assets.itch.io/pixel-pack/file/22716"
  ```

#### [P3] PixelFrog Kings and Pigs（对话框 + 钻石/心 物品）
- **URL**：<https://pixelfrog-assets.itch.io/kings-and-pigs>
- **License**：**CC0**（!作者明确声明 CC0；与 Cup Nooble 截然不同）
- **作者**：Pixel Frog（itch.io）
- **用途**：对话框装饰边框（替代 Kenney UI）、矿洞钻石/红心、Bug 矿洞炸弹/炮弹/爆炸效果
- **规格**：32×32 + 16×16，含 Aseprite 源文件
- **体积**：278 KB
- **下载**：
  ```bash
  curl -L -o assets/raw/items/pixelfrog-kings-and-pigs/Kings_and_Pigs.zip \
    "https://pixelfrog-assets.itch.io/kings-and-pigs/file/2073006"
  ```

### 2.9 像素字体（OFL-1.1 / CC0）

#### [P0] monogram by datagoblin（CC0 monospace 像素字体）
- **URL**：<https://datagoblin.itch.io/monogram>
- **License**：**CC0**
- **作者**：datagoblin
- **用途**：HUD 数字、调试输出、技能树小字（与 Ark Pixel 中文字体并存，monogram 跑英文/数字、Ark 跑中文）
- **规格**：5×9 monospace bitmap；TTF / bitmap PNG / PICO-8 .p8
- **体积**：monogram.zip 50 KB / monogram.ttf 10 KB
- **下载**：
  ```bash
  curl -L -o assets/raw/fonts/monogram/monogram.zip \
    "https://datagoblin.itch.io/monogram/file/8067149"
  # 备用单文件：
  # curl -L -o assets/raw/fonts/monogram/monogram.ttf \
  #   "https://datagoblin.itch.io/monogram/file/8067150"
  ```

#### [P1] Press Start 2P（OFL-1.1 街机风英文字体）
- **URL（Google Fonts）**：<https://fonts.google.com/specimen/Press+Start+2P>
- **URL（npm 自托管）**：<https://www.npmjs.com/package/@fontsource/press-start-2p>
- **License**：**OFL-1.1**
- **作者**：Cody "CodeMan38" Boisclair
- **用途**：标题、章节扉页、矿洞 Game Over 屏
- **规格**：8×8 像素 bitmap → TTF 矢量
- **体积**：WOFF2 ~7 KB
- **下载**：
  ```bash
  # 优先用 npm 包（已有项目用 npm）：
  cd frontend && npm install --save @fontsource/press-start-2p
  # 或独立下载 WOFF2：
  curl -L -o assets/raw/fonts/press-start-2p/PressStart2P-Regular.ttf \
    "https://fonts.gstatic.com/s/pressstart2p/v15/e3t4euO8T-267oIAQAu6jDQyK3nVivM.woff2"
  ```

#### [P1] VT323（OFL-1.1 终端风英文字体）
- **URL（Google Fonts）**：<https://fonts.google.com/specimen/VT323>
- **URL（npm 自托管）**：<https://www.npmjs.com/package/@fontsource/vt323>
- **License**：**OFL-1.1**
- **作者**：Peter Hull
- **用途**：终端模拟器 UI、调试日志面板、`Pyodide REPL` 输出
- **规格**：8x8 bitmap 风（TTF 矢量化）
- **体积**：WOFF2 ~22 KB
- **下载**：
  ```bash
  cd frontend && npm install --save @fontsource/vt323
  ```

---

## 第 3 节 · 自动化下载脚本

将下方内容保存为 `/home/cypress/Pythontopia/scripts/download-r12-art.sh`，赋予可执行权限后运行。**默认只下 P0+P1（约 58 MB）**；通过 `INCLUDE_P2=1` / `INCLUDE_P3=1` 环境变量按需追加。

> **说明**：itch.io 与部分 OGA 镜像偶有限流；脚本对每条下载用 `wget --tries=3 --timeout=60` 重试，并把每个包内的 `CREDITS-*.txt` / `License.txt` 全部保留落盘。

```bash
#!/usr/bin/env bash
# Pythontopia R-12 美术资产下载脚本
# 输出目录：<repo>/assets/raw/
# 协议：仅下载 CC0 / CC-BY 3.0/4.0 / CC-BY-SA 3.0+ / OFL-1.1
# 用法：
#   bash scripts/download-r12-art.sh            # 仅 P0+P1（58 MB）
#   INCLUDE_P2=1 bash scripts/download-r12-art.sh   # +P2 港口/学生 NPC（约 +50 MB）
#   INCLUDE_P2=1 INCLUDE_P3=1 bash scripts/download-r12-art.sh  # +P3 演示加分项
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RAW="$REPO_ROOT/assets/raw"
WGET_OPTS=(--tries=3 --timeout=60 --quiet --show-progress)
CURL_OPTS=(--retry 3 --max-time 120 --location --fail --silent --show-error)

mkdir -p "$RAW"/{tilesets,buildings,characters,props,ui,effects,items,fonts}/dummy
rmdir "$RAW"/{tilesets,buildings,characters,props,ui,effects,items,fonts}/dummy

log() { printf '\033[1;36m[r12-art]\033[0m %s\n' "$*"; }

############################
# P0 — R-12 必备（约 8 MB）
############################
log "=== P0 · Tilesets ==="
mkdir -p "$RAW/tilesets/lpc-tile-atlas" "$RAW/tilesets/lpc-walls" "$RAW/tilesets/lpc-tavern"
wget "${WGET_OPTS[@]}" -O "$RAW/tilesets/lpc-tile-atlas/Atlas.zip"   https://opengameart.org/sites/default/files/Atlas_0.zip
wget "${WGET_OPTS[@]}" -O "$RAW/tilesets/lpc-walls/lpc-walls.zip"    https://opengameart.org/sites/default/files/lpc-walls.zip
wget "${WGET_OPTS[@]}" -O "$RAW/tilesets/lpc-tavern/lpc-tavern.zip"  https://opengameart.org/sites/default/files/lpc-tavern.zip

log "=== P0 · Buildings ==="
mkdir -p "$RAW/buildings/lpc-mine" "$RAW/buildings/lpc-interior-castle"
wget "${WGET_OPTS[@]}" -O "$RAW/buildings/lpc-mine/lpc-mine.zip" https://opengameart.org/sites/default/files/lpc-mine.zip
wget "${WGET_OPTS[@]}" -P "$RAW/buildings/lpc-interior-castle/" \
  https://opengameart.org/sites/default/files/castle_2.png \
  https://opengameart.org/sites/default/files/stairs_1.png

log "=== P0 · Characters ==="
mkdir -p "$RAW/characters/lpc-medieval-fantasy" "$RAW/characters/isaiah658-pixel-pack-2"
wget "${WGET_OPTS[@]}" -P "$RAW/characters/lpc-medieval-fantasy/" \
  https://opengameart.org/sites/default/files/lpc_entry.zip \
  https://opengameart.org/sites/default/files/expansion_pack-0.04.zip
wget "${WGET_OPTS[@]}" -O "$RAW/characters/isaiah658-pixel-pack-2/v2.0.zip" \
  https://opengameart.org/sites/default/files/version_2.0_isaiah658s_pixel_pack_2.zip

log "=== P0 · UI ==="
mkdir -p "$RAW/ui/kenney-1-bit-pack" "$RAW/icons/game-icons-net"
curl "${CURL_OPTS[@]}" -o "$RAW/ui/kenney-1-bit-pack/1bitpack_kenney_1.2.zip" \
  "https://kenney-assets.itch.io/1-bit-pack/file/1019258" || \
  wget "${WGET_OPTS[@]}" -O "$RAW/ui/kenney-1-bit-pack/1bitpack_kenney_1.1.zip" \
    https://opengameart.org/sites/default/files/1bitpack_kenney_1.1.zip
# game-icons 仓库太大，仅做按需提示（团队挑图时执行）
echo "提示：完整 game-icons 仓库可选 git clone --depth 1 https://github.com/game-icons/icons.git $RAW/icons/game-icons-net/ （~80 MB）"

log "=== P0 · Fonts ==="
mkdir -p "$RAW/fonts/monogram"
curl "${CURL_OPTS[@]}" -o "$RAW/fonts/monogram/monogram.zip" \
  "https://datagoblin.itch.io/monogram/file/8067149" || \
  curl "${CURL_OPTS[@]}" -o "$RAW/fonts/monogram/monogram.ttf" \
    "https://datagoblin.itch.io/monogram/file/8067150"

############################
# P1 — 5 张地图必备（约 50 MB）
############################
log "=== P1 · Tilesets ==="
mkdir -p "$RAW/tilesets/lpc-tile-atlas2" "$RAW/tilesets/lpc-winter-tiles" \
         "$RAW/tilesets/lpc-beach-desert" "$RAW/tilesets/lpc-cavern-and-ruin"
wget "${WGET_OPTS[@]}" -O "$RAW/tilesets/lpc-tile-atlas2/Atlas2.zip" https://opengameart.org/sites/default/files/Atlas2.zip
wget "${WGET_OPTS[@]}" -P "$RAW/tilesets/lpc-winter-tiles/" \
  https://opengameart.org/sites/default/files/TilesA2.png \
  https://opengameart.org/sites/default/files/TilesA3.png \
  https://opengameart.org/sites/default/files/TilesB.png
wget "${WGET_OPTS[@]}" -O "$RAW/tilesets/lpc-beach-desert/lpc-beach-desert-v2.zip" \
  https://opengameart.org/sites/default/files/lpc-beach-desert-v2.zip
wget "${WGET_OPTS[@]}" -O "$RAW/tilesets/lpc-cavern-and-ruin/LPC_cavern_ruins.zip" \
  https://opengameart.org/sites/default/files/LPC_cavern_ruins.zip

log "=== P1 · Buildings ==="
mkdir -p "$RAW/buildings/lpc-house-interior" "$RAW/buildings/3td-harbour" \
         "$RAW/buildings/kenney-farm-expansion"
wget "${WGET_OPTS[@]}" -O "$RAW/buildings/lpc-house-interior/LPC_house_interior.zip" \
  https://opengameart.org/sites/default/files/LPC_house_interior_0.zip
wget "${WGET_OPTS[@]}" -O "$RAW/buildings/3td-harbour/3td_Harbor_Pack.zip" \
  "https://lpc.opengameart.org/sites/default/files/3td_Harbor%20Pack.zip"
curl "${CURL_OPTS[@]}" -o "$RAW/buildings/kenney-farm-expansion/pixel-platformer-farm-expansion.zip" \
  "https://kenney-assets.itch.io/pixel-platformer-farm-expansion/file/4068783" || \
  log "提示：itch CDN 直链失效，请到 https://kenney.nl/assets/pixel-platformer-farm-expansion 手动下载"

log "=== P1 · Props ==="
mkdir -p "$RAW/props/lpc-victorian-decorations" "$RAW/props/lpc-modern-streets" \
         "$RAW/props/lpc-wooden-furniture"
wget "${WGET_OPTS[@]}" -O "$RAW/props/lpc-victorian-decorations/lpc-victorian-decoration.zip" \
  https://opengameart.org/sites/default/files/lpc-victorian-decoration.zip
wget "${WGET_OPTS[@]}" -O "$RAW/props/lpc-modern-streets/lpc_modern_streets.zip" \
  https://opengameart.org/sites/default/files/lpc_modern_streets.zip
wget "${WGET_OPTS[@]}" -P "$RAW/props/lpc-wooden-furniture/" \
  https://opengameart.org/sites/default/files/dark-wood.png \
  https://opengameart.org/sites/default/files/blonde-wood.png \
  https://opengameart.org/sites/default/files/green-wood.png \
  https://opengameart.org/sites/default/files/white-wood.png \
  https://opengameart.org/sites/default/files/layers.zip \
  https://opengameart.org/sites/default/files/credits-furniture.txt_.zip

log "=== P1 · Fonts ==="
log "提示：Press Start 2P / VT323 推荐用 npm 安装："
log "  cd frontend && npm install --save @fontsource/press-start-2p @fontsource/vt323"

############################
# P2 — 20 NPC 完整化（约 50 MB）
############################
if [[ "${INCLUDE_P2:-0}" == "1" ]]; then
  log "=== P2 · Characters ==="
  mkdir -p "$RAW/characters/lpc-pirates"
  wget "${WGET_OPTS[@]}" -O "$RAW/characters/lpc-pirates/lpc-pirate-v1.1.zip" \
    https://opengameart.org/sites/default/files/lpc-pirate-v1.1.zip
  log "=== P2 · Props ==="
  mkdir -p "$RAW/props/lpc-bridges"
  wget "${WGET_OPTS[@]}" -P "$RAW/props/lpc-bridges/" \
    https://opengameart.org/sites/default/files/bridge-wood-angled.png \
    https://opengameart.org/sites/default/files/bridge-wood-square.png \
    https://opengameart.org/sites/default/files/floor-steel.png \
    https://opengameart.org/sites/default/files/railing-wood-16x32.png \
    https://opengameart.org/sites/default/files/railing-steel-16x32.png
fi

############################
# P3 — 演示加分项（约 1 MB）
############################
if [[ "${INCLUDE_P3:-0}" == "1" ]]; then
  log "=== P3 · Items / Effects ==="
  mkdir -p "$RAW/items/pixelfrog-kings-and-pigs" "$RAW/items/kenney-pixel-pack" \
           "$RAW/effects/kenney-pixel-shmup" "$RAW/props/kenney-rpg-urban-kit"
  curl "${CURL_OPTS[@]}" -o "$RAW/items/pixelfrog-kings-and-pigs/Kings_and_Pigs.zip" \
    "https://pixelfrog-assets.itch.io/kings-and-pigs/file/2073006"
  curl "${CURL_OPTS[@]}" -o "$RAW/items/kenney-pixel-pack/kenney_pixel-pack.zip" \
    "https://kenney-assets.itch.io/pixel-pack/file/22716"
  curl "${CURL_OPTS[@]}" -o "$RAW/effects/kenney-pixel-shmup/kenney_pixel-shmup.zip" \
    "https://kenney-assets.itch.io/pixel-shmup/file/2026850"
  curl "${CURL_OPTS[@]}" -o "$RAW/props/kenney-rpg-urban-kit/kenney_rpgUrbanKit.zip" \
    "https://kenney-assets.itch.io/rpg-urban-kit/file/1389088"
fi

log "下载完成：$(du -sh "$RAW" | cut -f1)"
log "下一步：解压所有 zip 并执行 'tools/audit-license.sh assets/raw/' 复核 license（沿用 R-100 #10 审计模板）"
```

---

## 第 4 节 · LICENSE-ART.md 增量条目

> 直接粘贴到 `/home/cypress/Pythontopia/LICENSE-ART.md` **末尾**（在"待办（backlog）"前），并把"License 分布矩阵"表的 LPC 子源数 5 改为 18（增加 13 个新条目）。

```markdown
## R-12 增量索引（2026-05-24）

下列 R-12 阶段新引入素材，落到 `assets/raw/` 由 `scripts/download-r12-art.sh` 自动拉取；集成到 `frontend/public/assets/pixel/` 时同步更新本表与 `frontend/public/assets/pixel/LICENSE.md`。

### CC-BY-SA 3.0+（13 条，触发 SA 传染，已接受）

| 包名 | 来源 URL | 作者 | 用途 |
| --- | --- | --- | --- |
| LPC Tile Atlas | https://opengameart.org/content/lpc-tile-atlas | adrix89 + LPC 大赛全员 | `town_hub / farm_north` 通用地表图集 |
| LPC Tile Atlas2 | https://opengameart.org/content/lpc-tile-atlas2 | adrix89 + LPC 大赛全员 | `town_hub` 建筑/物件图集 |
| LPC Walls | https://opengameart.org/content/lpc-walls | bluecarrot16, Lanea Zimmerman 等 19 人 | `campus_east` 教室室内墙体 + Wang tile |
| LPC Tavern | https://opengameart.org/content/lpc-tavern | bluecarrot16, Lanea Zimmerman (Sharm), William Thompson 等 | 商店室内 + 动画家具 |
| LPC House interior and decorations | https://opengameart.org/content/lpc-house-interior-and-decorations | Janna / Lilius / Jannax | 房屋内饰 + 动画壁炉 |
| LPC Interior Castle Tiles | https://opengameart.org/content/lpc-interior-castle-tiles | Lanea Zimmerman (Sharm) | `campus_east` 学校大厅/楼梯 |
| LPC Winter Tiles | https://opengameart.org/content/lpc-winter-tiles | Demetrius (基于 Lanea Zimmerman LPC Modified Base) | `farm_north` 冬季皮肤 |
| LPC Beach / Desert | https://opengameart.org/content/lpc-beach-desert | bluecarrot16 等 | `port_west` 海滩沙地 |
| LPC Cavern and ruin tiles | https://opengameart.org/content/lpc-cavern-and-ruin-tiles | 见包内 credits.txt | `mine_south` 矿洞深井 + 矿车 |
| LPC Victorian Town Decorations | https://opengameart.org/content/lpc-victorian-town-decorations | bluecarrot16 + 20 人 | `town_hub` 动画街灯/喷泉/招牌 |
| LPC Wooden Furniture | https://opengameart.org/content/lpc-wooden-furniture | bluecarrot16, Baŝto, Lanea Zimmerman, William Thompson 等 | 教室桌椅/办公室柜 |
| LPC Medieval Fantasy Character Sprites | https://opengameart.org/content/lpc-medieval-fantasy-character-sprites | wulax (Johannes Sjölund) | NPC base 备份 + 武器/盔甲模块 |
| LPC Pirates | https://opengameart.org/content/lpc-pirates | bluecarrot16, Fabzy, Nila122, JaidynReiman 等 11 人 | `port_west` 港口 NPC 服装包 |
| LPC style wood bridges and steel flooring | https://opengameart.org/content/lpc-style-wood-bridges-and-steel-flooring | Xenodora | 跨溪木桥 + 钢制走道 |

> **强制要求**（CC-BY-SA 3.0+）：
> - 保留每个包内 `CREDITS-*.txt` 全文到 `frontend/public/credits/lpc/<package>/`
> - 在 about 页面列出上述全部作者
> - 最终包装作品同时以 CC-BY-SA 3.0+ 发布（决策已在本文件顶部接受）

### CC-BY 4.0（1 条，强制署名，不传染）

| 包名 | 来源 URL | 作者 | 用途 |
| --- | --- | --- | --- |
| LPC Mine | https://opengameart.org/content/lpc-mine | bluecarrot16, Lanea Zimmerman (Sharm), Ivan Voirol | `mine_south` 矿洞入口/起重机/矿石 |

### CC-BY 3.0（1 条，强制署名，不传染）

| 包名 | 来源 URL | 作者 | 用途 |
| --- | --- | --- | --- |
| Game-icons.net (extended set) | https://game-icons.net | Lorc, Delapouite, Skoll, Caro Asercion, sbed, Carl Olsen 等 35 人 | KC + 任务 + 技能图标补充（4000+ SVG） |

> **强制要求**（CC-BY 3.0）：
> - 在 about 页面写明 "Icons made by Lorc, Delapouite, Skoll, Caro Asercion, sbed, Carl Olsen and others. Available on https://game-icons.net"
> - 不需要 SA 传染

### CC0（10 条，推荐署名，无传染）

| 包名 | 来源 URL | 作者 | 用途 |
| --- | --- | --- | --- |
| LPC Modern Streets | https://opengameart.org/content/lpc-modern-streets | Faufilage | 校园主干道 + 围栏 + 交通设施 |
| 3TD Harbour Pack | https://lpc.opengameart.org/content/3td-harbour-pack | Ron Kapaun / 3TD Studios | `port_west` 灯塔 + 码头（3D 转 2D） |
| isaiah658's Pixel Pack #2 | https://opengameart.org/content/isaiah658s-pixel-pack-2 | isaiah658 | 实验室桌 / 便利店 / 学生角色补充 |
| Kenney Pixel Platformer Farm Expansion | https://kenney.nl/assets/pixel-platformer-farm-expansion | Kenney Vleugels | `farm_north` 温室 + 秋季作物 |
| Kenney 1-Bit Pack (v1.2) | https://kenney.nl/assets/1-bit-pack | Kenney Vleugels | 调试 UI / 极简徽章（升级版） |
| Kenney RPG Urban Kit | https://kenney-assets.itch.io/rpg-urban-kit | Kenney Vleugels | `campus_east` 现代街景补充 |
| Kenney Pixel Shmup | https://kenney.nl/assets/pixel-shmup | Kenney Vleugels | 代码能量射线 + Bug 击中特效 |
| Kenney Pixel Pack | https://kenney.nl/assets/pixel-pack | Kenney Vleugels | 教具图标补充 |
| PixelFrog Kings and Pigs | https://pixelfrog-assets.itch.io/kings-and-pigs | Pixel Frog | 对话框装饰 + 矿洞钻石/心/炸弹 |
| monogram (font) | https://datagoblin.itch.io/monogram | datagoblin | HUD 数字 / 调试输出 monospace 字体 |

### OFL-1.1（2 条，强制署名，仅字体内传染）

| 包名 | 来源 URL | 作者 | 用途 |
| --- | --- | --- | --- |
| Press Start 2P | https://fonts.google.com/specimen/Press+Start+2P | Cody "CodeMan38" Boisclair | 标题 / 章节扉页 / Game Over |
| VT323 | https://fonts.google.com/specimen/VT323 | Peter Hull | 终端模拟器 / 调试日志 / Pyodide REPL |
```

---

## 第 5 节 · 风险与替代方案

### 5.1 绝对不能使用（请永久排除）

| 资源 | 原因 | 替代方案 |
| --- | --- | --- |
| **Cup Nooble Sprout Lands** (`cupnooble.itch.io/sprout-lands-asset-pack`) | Non-commercial + 禁止 AI 训练 + 禁止再分发 | LPC Tile Atlas + Kenney medieval-rts + LPC Modern Streets |
| **星露谷物语 / Stardew Valley** 任何 rip / 数据挖掘 | 私有商业素材，永久排除 | 全部走 LPC + Kenney 组合，星露谷是**风格**参考而非素材源 |
| **任何商业像素游戏官方素材**（Notyourbing 仓库内的"原版数据"、Undertale rip、宝可梦 sprite 等） | 侵权 | 同上 |
| **CC-NC / CC-ND** 任何素材（含 Batareya AI 生成肖像包） | 禁商用 / 禁衍生 | LPC Universal Spritesheet Generator 在线导出 |
| **未明确 License 的 itch.io 包** | 法律灰区 | 只下作者主页明确写 "CC0" / "OFL" / "CC-BY-SA" 字样的资源 |
| **AI 生成像素肖像包**（Batareya 系列） | 多数标注 "Generated with Stable Diffusion"，license 仍模糊；项目硬约束"不复制商业像素游戏官方素材"亦适用 | isaiah658 Pixel Pack #1+#2（CC0）/ LPC portrait + Pepper&Carrot（已有） |

### 5.2 SA 传染影响（已接受决策的复述）

- 本次新增 13 个 CC-BY-SA 3.0+ 子包后，`LICENSE-ART.md` 的 SA 子源数从 5 升到 18。
- 项目最终二进制 / 包装作品**必须**同时以 CC-BY-SA 3.0+ 发布；代码层仍可独立 MIT（已分层授权）。
- 一旦决定**回退**到纯 CC0 + CC-BY 路线（与 R-100 阶段讨论方案 B 等价），需要逐张替换 13 个 LPC 子包，预估替换工作量 80-120 人工小时（每张地图重做 tileset 拼接）。**强烈不建议在 R-12 阶段回退**，会拖累比赛 demo 节奏。

### 5.3 关键替代方案（每个 P0/P1 资源都有 fallback）

| 主推 | License | 替代 1 | License | 备注 |
| --- | --- | --- | --- | --- |
| LPC Tile Atlas + Tile Atlas2 | CC-BY-SA 3.0+ | `kenney_tilemaps/rpg-urban-pack/` + `kenney_tilesets/kenney_tiny-town/` | CC0 | 已在 inbox；风格略偏可爱，缺中世纪叙事 |
| LPC Walls / Tavern / Wooden Furniture | CC-BY-SA 3.0+ | Kenney roguelike-modern-city + isaiah658 Pixel Pack #2 实验室桌 | CC0 | 内饰精致度下降，但合规风险归零 |
| LPC Mine | CC-BY 4.0 | `kenney_tilemaps/roguelike-caves-dungeons/` | CC0 | 已在 inbox；缺起重机动画 |
| 3TD Harbour Pack（3D 转 2D） | CC0 | LPC Pirates 包内的码头物件 + 自绘灯塔（参考剪影） | CC-BY-SA 3.0+ | 3D 转 2D 需 Blender，无 Blender 流水线建议走自绘 |
| LPC Pirates（港口 NPC 服装） | CC-BY-SA 3.0+ | LPC Universal Spritesheet Generator 在线挑非海盗服装 | CC-BY-SA 3.0+ | 不含 NPC 主题"航海感"减弱 |
| Game-icons.net | CC-BY 3.0 | 已有 `assets/inbox/kenney_tilemaps/game-icons/` | CC0 | Kenney 风格统一但缺策略游戏感 |
| monogram (font) | CC0 | 已有 Ark Pixel 中文字体；英文走 Ark Pixel mono | OFL-1.1 | 多语种统一，但 monogram 5×9 更紧凑 |

### 5.4 下载脚本失败容灾

- **itch.io CDN 文件 ID 偶变**：脚本中所有 `kenney-assets.itch.io/<pack>/file/<id>` 链接都标注了备用 OpenGameArt / kenney.nl 镜像或回退路径
- **OGA 大文件超时**：脚本设 `--timeout=60 --tries=3`，单文件超时不影响后续；运行后 `du -sh assets/raw/` 与本报告"预计体积"对比，差距 >20% 时手动补漏
- **GitHub 克隆失败（中国大陆网络）**：项目环境变量已配 `HTTPS_PROXY=http://127.0.0.1:7892`（见 `LICENSE_AUDIT.md` 元数据），脚本自动继承
- **OGA 注册墙**：本清单全部资源**无需登录**即可下载，已在调研时实测 `curl -sI` 返回 HTTP 200

### 5.5 二次审计责任

- 下载完成后**强制**执行：`bash scripts/audit-license.sh assets/raw/`（沿用 R-100 #10 模板，扫描每个包的 `License.txt` / `CREDITS-*.txt`），并把审计报告增量追加到 `assets/inbox/LICENSE_AUDIT.md`
- 集成到 `frontend/public/assets/pixel/` 时同步更新 `frontend/public/assets/pixel/LICENSE.md`
- about 页面（`frontend/public/credits/`）在 R-12 验收前必须全量署名通过

---

## 附录 · 一键摘要

| 项 | 值 |
| --- | --- |
| 总新增资源 | 44 个（P0=14 / P1=18 / P2=8 / P3=4） |
| 总下载量 | ~73 MB（P0+P1=58 MB，P2 +50 MB，P3 +1 MB） |
| License 分布 | CC0=10 / CC-BY 3.0=1 / CC-BY 4.0=1 / CC-BY-SA 3.0+=13 / OFL-1.1=2 + 17 个由 generator/font npm 拉取 |
| 新增 SA 传染条目 | 13（项目已接受） |
| 覆盖 R-12 维度 | 5 张地图全 tileset + 室内三件套 + 20 NPC + 港口/灯塔 + 矿洞深井 + 季节切换 + OFL 像素字体 |
| 下载脚本 | `scripts/download-r12-art.sh`（P0+P1 默认；P2/P3 环境变量启用） |
| 集成脚本入口 | 下载完毕后由 R-100 二次审计 + `frontend/scripts/sync-npc-data.mjs` 同步到 `frontend/public/assets/` |
