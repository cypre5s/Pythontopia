# Pythontopia / Bug 矿洞 (Mines) 素材清单

> 目录：`assets/inbox/mines/`
> 关联文档：`SOURCES.md`（来源与 license）
> 目标场景：Pythontopia 调试副本「Bug 矿洞」——学生提交代码失败 / 抛异常时进入，击败 bug 怪物 = 修复对应错误。
> License 政策：**严格 CC0 / CC-BY 4.0**。CC-BY 3.0 / CC-BY-SA / GPL 一律不收。

## 0. 套件总览

| 套件 | License | 风格 | 主要用途 |
|------|---------|------|----------|
| `kenney_tiny-dungeon/` | CC0 | 暖色木 + 灰石 dungeon | 主玩法 tile / 5 种 bug 怪物 / 玩家备选 |
| `kenney_roguelike-caves-dungeons/` | CC0 | 灰色洞穴 + 棕矿洞 | 第 2-3 层矿洞地形 / 矿石 / 蘑菇病害 |
| `kenney_pixel-platformer/` | CC0 | 卡通平台 + 蓝绿洞穴 | 备用 cave tile / 备用 bug 怪物 / 玩家平面动作素材 |
| `oga_buch_dungeon/` | CC0 | 深色阴森地牢 | 矿洞主地图 + 火把（橙色火焰）+ Boss 关卡氛围 |

**4 套全部 CC0**，无需 attribution（推荐保留作者署名）。

---

## 1. 矿洞地图 (Map / Tileset)

「Bug 矿洞」分 3 层，深度递增，地形主题不同：

### 1.1 Layer 1 — 入口矿区（黄铜色石头，温暖）

- **主 tileset**：`kenney_tiny-dungeon/Tilemap/tilemap_packed.png`（12×11 tile，无 padding）
- **可拆分**：`kenney_tiny-dungeon/Tiles/tile_0XXX.png`（132 个独立 16×16 tile）
- **核心 tile（按 row, col 顺序，0-based）**：
  - 地面石板：`tile_0040.png`（灰石）、`tile_0048.png` / `tile_0049.png` / `tile_0050.png`（橙土）
  - 墙体：`tile_0012.png` ~ `tile_0023.png`（row 1 各角度石墙）
  - 楼梯：`tile_0024.png`、`tile_0025.png`（上 / 下）
  - 入口红门（bug 房间标记）：`tile_0029.png`
  - 木门 / 开启 / 关闭：`tile_0020.png` ~ `tile_0023.png`、`tile_0031.png` ~ `tile_0035.png`

### 1.2 Layer 2 — 深矿巷道（灰色洞穴 + 矿石点）

- **主 tileset**：`kenney_roguelike-caves-dungeons/Spritesheet/roguelikeDungeon_transparent.png`
- **风格**：偏写实洞穴，自带矿石与水域，适配矿坑叙事
- **可用素材区**：
  - 灰色洞穴墙体（左上区）
  - 棕色矿洞墙体（右上 / 右下区）
  - 水池 / 毒池（中下区，可作陷阱）
  - 小型矿石 / 骨头 / 蘑菇（中上区，可作 KC 掉落物或病害）
  - 灰色 / 蓝色房间地砖（左下区）

### 1.3 Layer 3 — 终局深渊（深色石板 + 火把）

- **主 tileset**：`oga_buch_dungeon/dungeon_tiles_0.png`
- **带网格版本（便于切片）**：`oga_buch_dungeon/tilesheet_1.png`
- **预览参考（完整 dungeon 布局示例）**：`oga_buch_dungeon/dungeon_pre.png`
- **风格**：深灰石板 + 阴影，氛围最强（适合 boss 关卡）
- **关键区**：
  - 大型石板房间（左上）
  - 凹陷地面（楼梯井）
  - 拱门入口
  - 木门 + 铁栅栏门
  - 火把（橙色火焰，详见 §2）

### 1.4 备用 — 平台动作 / 蓝绿洞穴风格（轻松关 / 教学副本）

- **主 tileset**：`kenney_pixel-platformer/Tilemap/tilemap.png`（pack 拼合）
- **拆分 tile**：`kenney_pixel-platformer/Tiles/tile_0XXX.png`（182 个独立 18×18 tile）
- **背景拼合**：`kenney_pixel-platformer/Tilemap/tilemap-backgrounds.png`（蓝色洞穴背景）
- **风格**：卡通色块平台 + 蓝绿洞穴 + 蘑菇，色彩明快
- **用途**：低难度教学关 / 复习副本（不像 §1.3 那么阴森，适合初学者）

---

## 2. 火把 / 灯笼 / 走廊光效 (Torches / Light)

| 类型 | 文件 | 尺寸 | 说明 |
|------|------|------|------|
| 主火把（首选）| `oga_buch_dungeon/dungeon_tiles_0.png` 中部小型橙色火把 | 16×16 | 深色场景反差强，氛围最佳；从 tilesheet 切片，坐标约 (col 11, row 6) |
| 主火把（带网格）| `oga_buch_dungeon/tilesheet_1.png` 中部小型橙色火把 | 16×16 | 同位置，带 1px 网格便于切片 |
| Kenney 火盆 / 烛台 | `kenney_tiny-dungeon/Tiles/tile_0070.png` ~ `tile_0083.png` 区域 | 16×16 | 风格与 Kenney 整体一致，可挂墙也可落地 |
| Kenney 灯笼 | `kenney_tiny-dungeon/Tiles/tile_0084.png` ~ `tile_0087.png` 区域 | 16×16 | 弱光源，背包道具 |
| 矿洞蘑菇灯（备）| `kenney_roguelike-caves-dungeons/Spritesheet/roguelikeDungeon_transparent.png` 蘑菇区 | 16×16 | 自发光蘑菇，营造矿洞生物光 |
| 蜡烛 / 烛台 | `oga_buch_dungeon/dungeon_tiles_0.png` 中部小烛 | 16×16 | 弱光源，可作 KC 进度提示 |
| 走廊光效 | Phaser radial gradient + 火焰 sprite 组合 | shader | 建议用 Phaser Light2D pipeline 渲染，火把为光源点 |

> 实现建议：火把使用 2-4 帧动画（从 `oga_buch_dungeon/dungeon_tiles_0.png` 与 `tilesheet_1.png` 同位置切出近似帧，或用 Phaser tween 模拟摇曳）。光源半径 48-64 px，配合 `oga_buch_dungeon` 的深色背景能强化矿洞阴森感。

---

## 3. 矿石 (Ore / Crystals)

Bug 矿洞核心比喻：**每种 bug = 一种矿石**。学生提交失败时，相应矿石脉露出；正确修复后，矿石变为「掌握度宝石」。

| 矿石类型 | 对应 bug | 主来源 | 备用来源 |
|----------|----------|--------|----------|
| 紫水晶（NameError）| 未定义名 | `kenney_roguelike-caves-dungeons/Spritesheet/roguelikeDungeon_transparent.png` 紫色小矿点 | `kenney_tiny-dungeon/Tiles/tile_0095.png`（紫色魔法石） |
| 绿翡翠（TypeError）| 类型不匹配 | `kenney_roguelike-caves-dungeons` 绿色矿点 / 蘑菇区 | `kenney_tiny-dungeon/Tiles/tile_0126.png`（绿瓶） |
| 红宝石（IndexError）| 索引越界 | `kenney_roguelike-caves-dungeons` 红色矿点 | `kenney_tiny-dungeon/Tiles/tile_0127.png`（红药水） |
| 金锭（KeyError）| 钥匙缺失 | `kenney_roguelike-caves-dungeons` 金色矿点（金币堆 / 金骷髅区）| `kenney_tiny-dungeon/Tiles/tile_0096.png`（钥匙）|
| 蓝晶石（ValueError）| 值不合法 | `kenney_roguelike-caves-dungeons` 蓝色矿点 | `kenney_tiny-dungeon/Tiles/tile_0125.png`（蓝瓶） |

> 这些是初版候选；R-101 阶段可由 ResourceDesignerAgent 进一步从 spritesheet 切片并统一风格。

---

## 4. Bug 怪物 5 种映射 (Core Bug Monsters)

每种怪物 = 一类 Python 错误。怪物从矿石脉里出现，击败方式 = 修复对应类型的 bug 题目。Pythontopia 学生完成相应 KC 练习后，怪物变为可收集的「Bug 标本」。

**所有怪物 sprite 严格 CC0（来源：Kenney Tiny Dungeon / Buch Dungeon / Kenney Pixel Platformer）。**

| Bug 类型 | 中文 | 怪物形象 | 主 sprite (CC0) | 备用 sprite (CC0) | 叙事 |
|---------|------|----------|----------|------------|------|
| **NameError** | 变量未定义 | 紫色幽灵 / 无名亡灵 | `kenney_tiny-dungeon/Tiles/tile_0121.png`（紫幽灵）| `kenney_tiny-dungeon/Tiles/tile_0117.png`（小灰幽灵）/ `oga_buch_dungeon/dungeon_tiles_0.png` 中部小幽灵 | 你引用了一个不存在的「名字」，被无主灵魂质问。修复方式：先 `=` 定义再使用。 |
| **TypeError** | 类型不匹配 | 绿色史莱姆 / 变形黏液 | `kenney_tiny-dungeon/Tiles/tile_0108.png`（绿黏液）| `kenney_pixel-platformer/Tiles/Characters/tile_0009.png`（蓝史莱姆）/ `oga_buch_dungeon/dungeon_tiles_0.png` 史莱姆 | 你把 `int` 当 `str` 用，史莱姆形态不定。修复方式：`int(...)` / `str(...)` 显式转换。 |
| **IndexError** | 索引越界 | 红色小恶魔 / 越界跳脱 | `kenney_tiny-dungeon/Tiles/tile_0110.png`（红恶魔）| `kenney_pixel-platformer/Tiles/Characters/tile_0024.png`（带刺生物）| 索引超出列表边界，小恶魔从洞穴边缘跳出。修复方式：检查 `len()` 与索引范围。 |
| **KeyError** | 字典键缺失 | 棕色老鼠 / 偷钥匙小贼 | `kenney_tiny-dungeon/Tiles/tile_0109.png`（棕老鼠）| `kenney_tiny-dungeon/Tiles/tile_0123.png`（棕脸生物）/ `kenney_pixel-platformer/Tiles/Characters/tile_0018.png` | 字典里没有这个 key，老鼠四处偷你的钥匙。修复方式：`dict.get(k, default)` 或先 `if k in d`。 |
| **ValueError** | 值不合法 | 红色蝙蝠 / 病害飞虫 | `kenney_tiny-dungeon/Tiles/tile_0111.png`（红蝙蝠）| `kenney_pixel-platformer/Tiles/Characters/tile_0019.png`（飞行生物）| 传入的值不在合法范围（如 `int("abc")`），蝙蝠携带病害扑来。修复方式：先 `try/except` 或校验。 |

### 4.1 备用 / 第二批 bug 怪物（Beta 之后扩展）

| Bug 类型 | 怪物形象 | 主 sprite (CC0) |
|---------|----------|----------|
| **AttributeError** | 棕色蜘蛛 / 织错网 | `kenney_tiny-dungeon/Tiles/tile_0120.png` |
| **ZeroDivisionError** | 棕色兽人 / 巨型敌人 | `kenney_tiny-dungeon/Tiles/tile_0123.png` |
| **ImportError** | 红法师 / 模块咒师 | `kenney_tiny-dungeon/Tiles/tile_0122.png` |
| **SyntaxError**（关卡 boss）| 阴影骷髅 | `oga_buch_dungeon/dungeon_tiles_0.png` 中部骷髅 |
| **IndentationError**（关卡 boss）| 巨型坩埚 / 黑色守卫 | `oga_buch_dungeon/dungeon_tiles_0.png` 中下部大型黑色生物 |

### 4.2 Bug 标本背包图标（收集后展示）

学生击败 bug 后获得标本，背包格显示小尺寸图标，建议尺寸 16×16，直接复用上表 sprite。R-103 阶段需要 PracticeAgent 在 KC 关联表里写入：

```
kc_id -> bug_type -> sprite_path
```

---

## 5. 玩家 / 矿工角色（备用 Player Sprite）

「Bug 矿洞」是 Pythontopia 的副本场景，玩家形象通常沿用 R-100 任务 #1 的 character 套件。如果矿洞内需要特殊矿工 / 法师装扮：

| 角色 | 来源 | 备注 |
|------|------|------|
| 法师（紫袍）| `kenney_tiny-dungeon/Tiles/tile_0084.png` | 默认调试者形象 |
| 战士（红袍）| `kenney_tiny-dungeon/Tiles/tile_0085.png` | 备选 |
| 探险者（绿）| `kenney_tiny-dungeon/Tiles/tile_0086.png` ~ `tile_0088.png` | 备选 |
| 公主 / 学者 | `kenney_tiny-dungeon/Tiles/tile_0089.png` ~ `tile_0091.png` | 备选 |
| Buch 法师 / 骑士 / 僧侣 | `oga_buch_dungeon/dungeon_tiles_0.png` 中部小角色 | 风格与矿洞最贴合（深色氛围）|
| Pixel Platformer 角色组 | `kenney_pixel-platformer/Tiles/Characters/tile_0000.png` ~ `tile_0026.png`（27 个）| 卡通向，多色多职业，可用于动作型关卡 |

---

## 6. 装饰 / 道具

| 类型 | 文件 | 用途 |
|------|------|------|
| 宝箱（关卡奖励）| `kenney_tiny-dungeon/Tiles/tile_0089.png` 附近 / `oga_buch_dungeon/dungeon_tiles_0.png` 宝箱区 | KC 完成奖励 |
| 钥匙 | `kenney_tiny-dungeon/Tiles/tile_0096.png` 区 | KeyError 修复证明 |
| 卷轴（提示）| `kenney_tiny-dungeon/Tiles/tile_0093.png` ~ `tile_0094.png` | 显示错误提示 / hint |
| 药水（血量）| `kenney_tiny-dungeon/Tiles/tile_0125.png` ~ `tile_0127.png` | 调试次数回复 |
| 蘑菇 / 病害 | `kenney_roguelike-caves-dungeons` 绿色蘑菇区 / `kenney_pixel-platformer/Tiles/tile_0150.png` 附近蘑菇 | Bug 蔓延视觉 |
| 骨头 / 警示 | `kenney_roguelike-caves-dungeons` 骨头 / 头骨区 | 失败提交标记 |
| 矿洞背景 | `kenney_pixel-platformer/Tilemap/tilemap-backgrounds.png` | 远景蓝色洞穴 / 山岩 |

---

## 7. 统计

- **套件数**：4
- **License 分布**：4 CC0（0 CC-BY 3.0 / 0 CC-BY-SA / 0 GPL）
- **mines/ 目录内容**（含 symlink 目标递归后真实文件数）：
  - `mines/oga_buch_dungeon/`：4 png（4 真实文件，88 KB）
  - `mines/kenney_tiny-dungeon/`：1 symlink → Kenney tiny-dungeon（142 文件，636 KB）
  - `mines/kenney_roguelike-caves-dungeons/`：1 symlink → Kenney roguelike-caves-dungeons（8 文件，248 KB）
  - `mines/kenney_pixel-platformer/`：1 symlink → Kenney pixel-platformer（252 文件，1.2 MB）
- **mines/ 总文件数（symlink 展开后）**：408（含 MANIFEST.md + SOURCES.md + 406 素材文件）
- **mines/ 自身磁盘占用**：~128 KB（仅 `oga_buch_dungeon/` 真实 png + MANIFEST/SOURCES + 3 个 symlink）
- **mines/ 全引用资产合计**：~2.2 MB（含 4 套 Kenney/Buch 真实数据，symlink 展开统计）
- **bug 怪物 sprite 已确认 5 + 备用 5**：见 §4

---

## 8. License 审计 (License Audit)

| 路径 | License 来源 | 文件证据 |
|------|--------------|----------|
| `mines/kenney_tiny-dungeon/License.txt` | Kenney CC0 1.0 Universal | `Creative Commons Zero, CC0` + 链接 |
| `mines/kenney_roguelike-caves-dungeons/License.txt` | Kenney CC0 1.0 Universal | `Creative Commons Zero, CC0` + 链接 |
| `mines/kenney_pixel-platformer/License.txt` | Kenney CC0 1.0 Universal | `Creative Commons Zero, CC0` + 链接 |
| `mines/oga_buch_dungeon/` | OpenGameArt 页面 license 标识 CC0 | `opengameart.org/content/dungeon-tileset` taxonomy term: CC0 |

**已删除（不符合 license 政策）**：
- `mines/oga_calciumtrice_dungeon/`（CC-BY 3.0）—— 已彻底删除，原始下载副本 `.work/assets_dl/mines/oga_calciumtrice_dungeon/` 同步清理。

---

## 9. R-101 后续 TODO（不在本任务范围）

- ResourceDesignerAgent 把 4 套 spritesheet 切成统一 16×16 Pythontopia 命名空间 tile（`mine.floor.gray.01`、`mine.bug.name_error` 等）
- 用 Phaser 3 Tiled JSON 描述 3 层矿洞布局
- 火焰 / 火把 sprite 接 Phaser AnimatedSprite，2-4 帧 / 100 ms（手工从 Buch tilesheet 切两帧近似抖动 + tween）
- KC 表新增 `bug_type` 字段，关联到本表中的 sprite
- 班级公告板「Bug 矿洞副本」入口
- 评估是否补充 OGA-BY 4.0 的额外角色资源（仍属允许 license，但目前 4 套 CC0 已足够）
